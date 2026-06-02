import uuid
from datetime import UTC, datetime

from fastapi import Depends, FastAPI, HTTPException, Response, UploadFile
from sqlmodel import Session

from app.crud import (
    create_okay_event,
    get_latest_okay_event,
    get_linked_users,
    is_okay_within_6h,
    upsert_user_token,
)
from app.db import create_engine_from_settings, get_session, init_db
from app.models import User
from app.services.firebase import init_firebase
from app.services.notifications import send_notification
from app.services.storage import download_audio, latest_object_name, upload_audio
from app.settings import get_settings

app = FastAPI()


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
    event = create_okay_event(session, user_id)

    linked_users = get_linked_users(session, user_id)

    for linked_id in linked_users:
        linked_user = session.get(User, linked_id)
        if linked_user and linked_user.token:
            send_notification(linked_user.token, f"User {user_id} checked in OK")

    return {"ok": True, "timestamp": event.timestamp.isoformat()}


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

    _notify_linked_of_voice(session, object_name)

    return {"object": object_name, "bytes": len(data)}


def _notify_linked_of_voice(session: Session, object_name: str) -> None:
    """Push a voice-message alert to the sender's linked users (e.g. Sadie)."""
    try:
        sender_id = int(object_name.split("/")[0])
    except ValueError:
        return

    for linked_id in get_linked_users(session, sender_id):
        linked_user = session.get(User, linked_id)
        if linked_user and linked_user.token:
            send_notification(linked_user.token, "Norman sent a voice message", message_type="VOICE_MESSAGE")


@app.get("/voice/latest")
def get_latest_voice(user_id: int) -> Response:
    """Stream the most recent voice clip from `user_id` (for the linked listener)."""
    object_name = latest_object_name(settings, f"{user_id}/")
    if object_name is None:
        raise HTTPException(status_code=404, detail="No voice message found")

    data = download_audio(settings, object_name)
    return Response(content=data, media_type="audio/mp4")
