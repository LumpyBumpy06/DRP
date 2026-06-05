import uuid
from datetime import UTC, datetime

from fastapi import Depends, FastAPI, HTTPException, Response, UploadFile
from sqlmodel import Session

from app.crud import (
    CHECK_IN_WINDOW_SECONDS,
    active_emergency_sender_for,
    clear_emergencies_for,
    create_okay_event,
    get_latest_okay_event,
    get_linked_users,
    get_okay_timestamps,
    is_okay_within_6h,
    raise_emergency,
    upsert_user_token,
)
from app.db import create_engine_from_settings, get_session, init_db
from app.models import User
from app.services.firebase import init_firebase
from app.services.notifications import send_notification
from app.services.storage import download_audio, latest_recent_object_name, upload_audio
from app.services.tree import compute_tree_state
from app.settings import get_settings

app = FastAPI()

# Display names for push messages (demo: two fixed users).
USER_NAMES = {1: "Norman", 2: "Sadie"}

# How long a voice clip stays playable. Decoupled from the (tiny) tree day-window
# so clips don't expire before the listener can open them.
VOICE_TTL_SECONDS = 60

# How long a snap stays viewable.
PHOTO_TTL_SECONDS = 120


# ---------- ENGINE (stateless per deployment) ----------

settings = get_settings()
engine = create_engine_from_settings(settings)
SessionDep = get_session(engine)
SessionDependency = Depends(SessionDep)


@app.on_event("startup")
def startup() -> None:
    init_db(engine)
    init_firebase(settings)


# ---------- ROUTES ----------


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


# ---------- TOKEN ----------


@app.post("/token")
def update_token(
    payload: User,
    session: Session = SessionDependency,
) -> User:
    return upsert_user_token(session, payload.user_id, payload.token or "")


# ---------- OKAY CHECK ----------


@app.get("/okay")
def get_okay(user_id: int, session: Session = SessionDependency) -> dict[str, bool]:
    event = get_latest_okay_event(session, user_id)
    return {"okay": is_okay_within_6h(event)}


# ---------- OKAY EVENT + NOTIFY ----------


@app.post("/okay")
def tap_okay(user_id: int, session: Session = SessionDependency) -> dict:
    """Water the shared tree. Notifies the partner with a teamwork nudge."""
    event = create_okay_event(session, user_id)
    _notify_watering(session, user_id, "CHECKED_IN")
    return {"ok": True, "timestamp": event.timestamp.isoformat()}


# ---------- TREE (shared "watering" state) ----------


@app.get("/tree")
def get_tree(session: Session = SessionDependency) -> dict:
    """The shared tree state — same for Norman and Sadie (a pure function of history)."""
    return compute_tree_state(
        get_okay_timestamps(session, 1),
        get_okay_timestamps(session, 2),
        datetime.now(UTC),
        CHECK_IN_WINDOW_SECONDS,
    )


def _notify_watering(session: Session, sender_id: int, message_type: str) -> None:
    """Tell the partner that `sender_id` just watered, nudging them to join in."""
    state = compute_tree_state(
        get_okay_timestamps(session, 1),
        get_okay_timestamps(session, 2),
        datetime.now(UTC),
        CHECK_IN_WINDOW_SECONDS,
    )
    sender_name = USER_NAMES.get(sender_id, "Someone")

    for linked_id in get_linked_users(session, sender_id):
        linked_user = session.get(User, linked_id)
        if linked_user and linked_user.token:
            peer_name = USER_NAMES.get(linked_id, "your partner")
            message = f"🌱 {sender_name} watered the tree (stage {state['stage']}) — {peer_name}, keep it growing together!"
            send_notification(linked_user.token, message, message_type=message_type)


# ---------- EMERGENCY ----------


@app.post("/emergency")
def trigger_emergency(user_id: int, session: Session = SessionDependency) -> dict:
    """Immediately alert everyone linked to `user_id` that they need help.

    Unlike a check-in this carries no "I'm okay" meaning — it's a one-way SOS
    that pushes a high-priority notification so the carer is alerted at once.
    The alert is also persisted so a carer who missed the push (app backgrounded)
    still sees it on their next poll.
    """
    raise_emergency(session, user_id)

    sender_name = USER_NAMES.get(user_id, "Someone")

    for linked_id in get_linked_users(session, user_id):
        linked_user = session.get(User, linked_id)
        if linked_user and linked_user.token:
            send_notification(
                linked_user.token,
                f"🚨 {sender_name} needs help right now!",
                message_type="EMERGENCY",
            )

    return {"ok": True}


@app.get("/emergency/active")
def get_active_emergency(user_id: int, session: Session = SessionDependency) -> dict:
    """Whether `user_id` (the carer) has an unacknowledged emergency to respond to.

    Drives the popup even when the push was missed (e.g. the app was backgrounded
    and the carer arrived by tapping the notification).
    """
    sender_id = active_emergency_sender_for(session, user_id)
    if sender_id is None:
        return {"active": False, "sender": None}
    return {"active": True, "sender": USER_NAMES.get(sender_id, "Someone")}


@app.post("/emergency/ack")
def acknowledge_emergency(user_id: int, session: Session = SessionDependency) -> dict:
    """`user_id` (the carer) marks the emergency handled, so it stops re-appearing."""
    clear_emergencies_for(session, user_id)
    return {"ok": True}


# ---------- VOICE ----------


@app.post("/voice")
async def receive_voice(file: UploadFile, session: Session = SessionDependency) -> dict:
    data = await file.read()

    # The client uploads with filename "<user_id>/<timestamp>.m4a" so each
    # sender's clips live under their own prefix (e.g. Norman -> "1/...").
    object_name = file.filename or f"{datetime.now(UTC):%Y%m%dT%H%M%S}-{uuid.uuid4().hex}.m4a"

    upload_audio(
        settings,
        data,
        object_name,
        content_type=file.content_type or "audio/mp4",
    )

    sender_id = _sender_id_from(object_name)
    if sender_id is not None:
        # A voice message also "waters" the shared tree.
        create_okay_event(session, sender_id)
        _notify_watering(session, sender_id, "VOICE_MESSAGE")

    return {"object": object_name, "bytes": len(data)}


def _sender_id_from(object_name: str) -> int | None:
    try:
        return int(object_name.split("/")[0])
    except ValueError:
        return None


@app.get("/voice/latest")
def get_latest_voice(user_id: int) -> Response:
    """Stream the latest clip from `user_id`, only within the current day window.

    A voice message expires with the check-in window, so once a "day"
    (CHECK_IN_WINDOW_SECONDS) has passed the listener can no longer play it.
    """
    object_name = latest_recent_object_name(settings, f"{user_id}/", VOICE_TTL_SECONDS)
    if object_name is None:
        raise HTTPException(status_code=404, detail="No current voice message")

    data = download_audio(settings, object_name)
    return Response(content=data, media_type="audio/mp4")


# ---------- PHOTO (snaps) ----------


@app.post("/photo")
async def receive_photo(file: UploadFile, session: Session = SessionDependency) -> dict:
    data = await file.read()

    # Stored under "photos/<sender_id>/..." — a separate namespace from voice
    # clips so the two never collide in a "latest" lookup.
    object_name = file.filename or f"photos/0/{datetime.now(UTC):%Y%m%dT%H%M%S}-{uuid.uuid4().hex}.jpg"

    upload_audio(settings, data, object_name, content_type=file.content_type or "image/jpeg")

    sender_id = _photo_sender_from(object_name)
    if sender_id is not None:
        # Sending a snap also "waters" the shared tree.
        create_okay_event(session, sender_id)

        sender_name = USER_NAMES.get(sender_id, "Someone")
        for linked_id in get_linked_users(session, sender_id):
            linked_user = session.get(User, linked_id)
            if linked_user and linked_user.token:
                send_notification(linked_user.token, f"📸 {sender_name} sent you a snap!", message_type="PHOTO_MESSAGE")

    return {"object": object_name, "bytes": len(data)}


def _photo_sender_from(object_name: str) -> int | None:
    parts = object_name.split("/")  # "photos/<sender_id>/<file>"
    if len(parts) >= 2:
        try:
            return int(parts[1])
        except ValueError:
            return None
    return None


@app.get("/photo/latest")
def get_latest_photo(user_id: int) -> Response:
    """Stream the latest snap from `user_id`, within the snap viewing window."""
    object_name = latest_recent_object_name(settings, f"photos/{user_id}/", PHOTO_TTL_SECONDS)
    if object_name is None:
        raise HTTPException(status_code=404, detail="No current snap")

    data = download_audio(settings, object_name)
    return Response(content=data, media_type="image/jpeg")
