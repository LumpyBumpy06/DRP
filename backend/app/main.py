import logging
import uuid
from datetime import UTC, datetime

from fastapi import Depends, FastAPI, HTTPException, Response, UploadFile
from pydantic import BaseModel
from sqlmodel import Session

from app.crud import (
    CHECK_IN_WINDOW_SECONDS,
    active_emergency_sender_for,
    add_tag_for,
    add_thread_message,
    all_tag_names,
    clear_emergencies_for,
    create_okay_event,
    create_revive_event,
    freeze_elapsed_weeks,
    get_all_thread_messages,
    get_forest_trees,
    get_latest_check_in_event,
    get_linked_users,
    get_okay_timestamps,
    get_revive_timestamps,
    get_tags_for,
    get_tags_map,
    get_thread_captions,
    get_thread_messages,
    is_okay_within_6h,
    raise_emergency,
    remove_tag_for,
    set_tags_for,
    set_thread_caption,
    upsert_user_token,
)
from app.crud import (
    reset_tree as reset_tree_data,
)
from app.db import create_engine_from_settings, get_session, init_db
from app.models import ThreadMessage, User
from app.services.firebase import init_firebase
from app.services.notifications import send_notification
from app.services.storage import download_audio, latest_recent_object_name, list_objects, upload_audio
from app.services.tree import WEEK_SECONDS, compute_current_week_state, compute_tree_state
from app.settings import get_settings

app = FastAPI()

# Display names for push messages (demo: two fixed users).
USER_NAMES = {1: "Norman", 2: "Sadie"}

# How long a voice clip stays playable. Decoupled from the (tiny) tree day-window
# so clips don't expire before the listener can open them.
VOICE_TTL_SECONDS = 60

# How long a snap stays viewable.
PHOTO_TTL_SECONDS = 120

# Object-storage prefix for media attached to a thread message. Kept out of the
# memory board (the /memories listing skips it) so chat snaps/clips don't show
# up twice.
THREAD_PREFIX = "threads/"

# Object-storage prefix for reshared copies of existing memories. Also kept out
# of the memory board: resharing only re-delivers the moment to the partner, it
# must NOT add a duplicate to the top of the gallery.
RESHARE_PREFIX = "reshares/"


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
    event = get_latest_check_in_event(session, user_id)
    return {"okay": is_okay_within_6h(event)}


# ---------- OKAY EVENT + NOTIFY ----------


@app.post("/okay")
def tap_okay(user_id: int, session: Session = SessionDependency) -> dict:
    """Water the shared tree. Notifies the partner with a teamwork nudge."""
    event = create_okay_event(session, user_id)
    _notify_tree_action(session, user_id, "CHECKED_IN", "watered")
    return {"ok": True, "timestamp": event.timestamp.isoformat()}


@app.post("/revive")
def revive_tree(user_id: int, session: Session = SessionDependency) -> dict:
    """Revive the shared tree without increasing its growth stage."""
    event = create_revive_event(session, user_id)
    _notify_tree_action(session, user_id, "CHECKED_IN", "revived")
    return {"ok": True, "timestamp": event.timestamp.isoformat()}


@app.get("/resetTree")
@app.post("/resetTree")
def reset_tree(session: Session = SessionDependency) -> dict:
    """Delete every check-in from the database and reset the shared tree state."""
    deleted = reset_tree_data(session)
    return {"ok": True, "deleted": deleted}


# ---------- TREE (shared "watering" state) ----------


@app.get("/tree")
def get_tree(session: Session = SessionDependency) -> dict:
    """The shared tree state — this week's tree, which resets each week boundary."""
    now = datetime.now(UTC)
    # Bank any week that just finished before reporting the fresh one.
    freeze_elapsed_weeks(session, now)
    return compute_current_week_state(
        get_okay_timestamps(session, 1),
        get_okay_timestamps(session, 2),
        now,
        CHECK_IN_WINDOW_SECONDS,
        get_revive_timestamps(session, 1) + get_revive_timestamps(session, 2),
    )


@app.get("/forest")
def get_forest(session: Session = SessionDependency) -> dict:
    """The frozen weekly trees, oldest first. Stored permanently in the DB —
    the live (current-week) tree is NOT included; it joins once its week ends."""
    freeze_elapsed_weeks(session, datetime.now(UTC))
    return {
        "weeks": [
            {
                "weekStart": tree.week_start,
                # Numbered by position (oldest = Week 1) so labels are always
                # a clean 1, 2, 3… sequence regardless of stored indexes.
                "weekIndex": position,
                "stage": tree.stage,
                "deathLevel": tree.death_level,
            }
            for position, tree in enumerate(get_forest_trees(session), start=1)
        ]
    }


def _notify_tree_action(session: Session, sender_id: int, message_type: str, verb: str) -> None:
    """Tell the partner that `sender_id` just acted on the tree."""
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
            message = f"🌱 {sender_name} {verb} the tree (stage {state['stage']}) — {peer_name}, keep it growing together!"
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
                f"💛 {sender_name} would love to talk with you",
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
        _notify_tree_action(session, sender_id, "VOICE_MESSAGE", "watered")

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
    object_name = latest_recent_object_name(settings, [f"{user_id}/", f"{RESHARE_PREFIX}{user_id}/"], VOICE_TTL_SECONDS)
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
    object_name = latest_recent_object_name(settings, [f"photos/{user_id}/", f"{RESHARE_PREFIX}photos/{user_id}/"], PHOTO_TTL_SECONDS)
    if object_name is None:
        raise HTTPException(status_code=404, detail="No current snap")

    data = download_audio(settings, object_name)
    return Response(content=data, media_type="image/jpeg")


# ---------- RESHARE (re-deliver an existing memory) ----------


@app.post("/reshare")
def reshare_memory(user_id: int, object_name: str, session: Session = SessionDependency) -> dict:
    """Re-deliver an existing memory to the partner.

    Copies the stored object under the reshares/ prefix (so the partner's
    "latest snap/clip" popup picks it up) and pushes a notification. It does
    NOT create a new gallery entry and does NOT water the tree — the original
    memory stays where it is on the board.
    """
    data = download_audio(settings, object_name)

    is_photo = object_name.startswith("photos/")
    stamp = f"{datetime.now(UTC):%Y%m%dT%H%M%S}-{uuid.uuid4().hex}"
    if is_photo:
        copy_name = f"{RESHARE_PREFIX}photos/{user_id}/{stamp}.jpg"
        content_type = "image/jpeg"
    else:
        copy_name = f"{RESHARE_PREFIX}{user_id}/{stamp}.m4a"
        content_type = "audio/mp4"
    upload_audio(settings, data, copy_name, content_type=content_type)

    sender_name = USER_NAMES.get(user_id, "Someone")
    kind_label = "snap" if is_photo else "voice memo"
    message_type = "PHOTO_MESSAGE" if is_photo else "VOICE_MESSAGE"
    for linked_id in get_linked_users(session, user_id):
        linked_user = session.get(User, linked_id)
        if linked_user and linked_user.token:
            send_notification(
                linked_user.token,
                f"💌 {sender_name} reshared a {kind_label} with you!",
                message_type=message_type,
            )

    return {"ok": True, "object": copy_name}


# ---------- MEMORIES (everything ever sent) ----------


# Thread-anchor prefix for prompt conversations. Each prompt gets its own
# anchor ("prompt/<day-index>/<object_name>") so every new prompt starts a
# FRESH chat, even when the same memory is resurfaced again later.
PROMPT_ANCHOR_PREFIX = "prompt/"


def _anchor_media_object(anchor: str) -> str:
    """The storage object behind a thread anchor (strips the prompt wrapper)."""
    if anchor.startswith(PROMPT_ANCHOR_PREFIX):
        parts = anchor.split("/", 2)  # "prompt/<id>/<object_name>"
        if len(parts) == 3:
            return parts[2]
    return anchor


def _memory_meta(object_name: str) -> tuple[str, str]:
    """(kind, sender display-name) for a stored object, mirroring /memories.
    Accepts thread anchors too (prompt anchors resolve to their memory)."""
    object_name = _anchor_media_object(object_name)
    if object_name.startswith("photos/"):
        sender_id = _photo_sender_from(object_name)
        return "photo", (USER_NAMES.get(sender_id, "Someone") if sender_id is not None else "Someone")
    sender_id = _sender_id_from(object_name)
    return "voice", (USER_NAMES.get(sender_id, "Someone") if sender_id is not None else "Someone")


def _board_objects() -> list[tuple[str, datetime]]:
    """Every memory-board object (voice + snaps), newest first, excluding thread
    media and reshared copies."""
    objects = sorted(list_objects(settings), key=lambda o: o[1], reverse=True)
    return [(name, lm) for name, lm in objects if not name.startswith(THREAD_PREFIX) and not name.startswith(RESHARE_PREFIX)]


@app.get("/memories")
def get_memories(session: Session = SessionDependency) -> dict:
    """Every voice memo + snap ever sent, newest first — the shared memory board."""
    try:
        objects = _board_objects()
    except Exception:
        # Object storage (MinIO) unavailable — degrade to an empty board instead
        # of a 500 so the rest of the app (tree, forest) keeps working.
        logging.exception("Memory storage unavailable; returning empty board")
        return {"memories": []}

    items: list[dict] = []
    for object_name, last_modified in objects:
        kind, sender = _memory_meta(object_name)
        items.append(
            {
                "objectName": object_name,
                "type": kind,
                "sender": sender,
                "epoch": int(last_modified.timestamp()),
            }
        )

    tags_map = get_tags_map(session, [item["objectName"] for item in items])
    for item in items:
        item["tags"] = tags_map.get(item["objectName"], [])

    return {"memories": items}


@app.get("/media")
def get_media(object_name: str) -> Response:
    """Stream any stored object by name (a voice memo or snap) for the memory board."""
    data = download_audio(settings, object_name)
    media_type = "image/jpeg" if object_name.endswith(".jpg") else "audio/mp4"
    return Response(content=data, media_type=media_type)


# ---------- TAGS (shared labels on a memory) ----------


class MemoryTagsRequest(BaseModel):
    tags: list[str] = []


@app.get("/tags")
def list_all_tags(session: Session = SessionDependency) -> dict:
    """Every distinct tag name anyone has created — powers the filter chips."""
    return {"tags": all_tag_names(session)}


@app.get("/memory/tags")
def get_memory_tags(object_name: str, session: Session = SessionDependency) -> dict:
    """The tags currently on one memory."""
    return {"tags": get_tags_for(session, object_name)}


@app.post("/memory/tags")
def set_memory_tags(
    object_name: str,
    payload: MemoryTagsRequest,
    session: Session = SessionDependency,
) -> dict:
    """Replace the full tag set on one memory (shared with the partner)."""
    return {"tags": set_tags_for(session, object_name, payload.tags)}


@app.post("/memory/tags/add")
def add_memory_tag(object_name: str, tag: str, session: Session = SessionDependency) -> dict:
    """Add ONE tag to a memory. Atomic — can never clobber other tags, unlike
    the replace-all endpoint, so concurrent edits and stale clients are safe."""
    return {"tags": add_tag_for(session, object_name, tag)}


@app.post("/memory/tags/remove")
def remove_memory_tag(object_name: str, tag: str, session: Session = SessionDependency) -> dict:
    """Remove ONE tag from a memory (case-insensitive). Atomic, like add."""
    return {"tags": remove_tag_for(session, object_name, tag)}


# ---------- THREADS (conversations anchored to a memory) ----------


class ThreadTextRequest(BaseModel):
    anchor: str
    user_id: int
    text: str


def _message_dict(message: ThreadMessage) -> dict:
    return {
        "id": message.id,
        "anchor": message.anchor,
        "senderId": message.sender_id,
        "sender": USER_NAMES.get(message.sender_id, "Someone"),
        "kind": message.kind,
        "text": message.text,
        "mediaObject": message.media_object,
        "epoch": int(message.created_at.replace(tzinfo=UTC).timestamp()),
    }


@app.get("/threads")
def get_threads(user_id: int, session: Session = SessionDependency) -> dict:
    """One summary row per conversation (anchor) — the WhatsApp-style list.

    `incoming` counts messages from the partner, a soft unread hint for the
    badge (there are no read receipts in this demo).
    """
    captions = get_thread_captions(session)
    summaries: dict[str, dict] = {}
    for message in get_all_thread_messages(session):
        kind, sender = _memory_meta(message.anchor)
        summary = summaries.setdefault(
            message.anchor,
            {
                "anchor": message.anchor,
                "memoryType": kind,
                "memorySender": sender,
                # The storage object behind the anchor (for thumbnails/playback).
                "memoryObject": _anchor_media_object(message.anchor),
                "isPrompt": message.anchor.startswith(PROMPT_ANCHOR_PREFIX),
                "caption": captions.get(message.anchor, ""),
                "count": 0,
                "incoming": 0,
            },
        )
        summary["count"] += 1
        if message.sender_id != user_id:
            summary["incoming"] += 1
        # get_all_thread_messages is oldest-first, so the last write wins as "latest".
        summary["lastKind"] = message.kind
        summary["lastText"] = message.text
        summary["lastSenderId"] = message.sender_id
        summary["lastSender"] = USER_NAMES.get(message.sender_id, "Someone")
        summary["lastEpoch"] = int(message.created_at.replace(tzinfo=UTC).timestamp())

    threads = sorted(summaries.values(), key=lambda s: s.get("lastEpoch", 0), reverse=True)
    return {"threads": threads}


@app.get("/thread")
def get_thread(anchor: str, session: Session = SessionDependency) -> dict:
    """Every message in one conversation, oldest first."""
    return {"messages": [_message_dict(m) for m in get_thread_messages(session, anchor)]}


@app.post("/thread/caption")
def post_thread_caption(anchor: str, caption: str, session: Session = SessionDependency) -> dict:
    """Persist the user-given title of a conversation (shared, survives restarts)."""
    set_thread_caption(session, anchor, caption)
    return {"ok": True}


@app.post("/thread/text")
def post_thread_text(payload: ThreadTextRequest, session: Session = SessionDependency) -> dict:
    """Reply to a thread with words."""
    message = add_thread_message(session, anchor=payload.anchor, sender_id=payload.user_id, kind="text", text=payload.text)
    _notify_thread(session, payload.user_id, "💬")
    return _message_dict(message)


@app.post("/thread/voice")
async def post_thread_voice(
    anchor: str,
    user_id: int,
    file: UploadFile,
    session: Session = SessionDependency,
) -> dict:
    """Reply to a thread with a voice note (stored under the threads/ prefix)."""
    data = await file.read()
    object_name = f"{THREAD_PREFIX}{user_id}/{datetime.now(UTC):%Y%m%dT%H%M%S}-{uuid.uuid4().hex}.m4a"
    upload_audio(settings, data, object_name, content_type=file.content_type or "audio/mp4")
    message = add_thread_message(session, anchor=anchor, sender_id=user_id, kind="voice", media_object=object_name)
    _notify_thread(session, user_id, "🎙")
    return _message_dict(message)


@app.post("/thread/photo")
async def post_thread_photo(
    anchor: str,
    user_id: int,
    file: UploadFile,
    session: Session = SessionDependency,
) -> dict:
    """Reply to a thread with a snap (stored under the threads/ prefix)."""
    data = await file.read()
    object_name = f"{THREAD_PREFIX}{user_id}/{datetime.now(UTC):%Y%m%dT%H%M%S}-{uuid.uuid4().hex}.jpg"
    upload_audio(settings, data, object_name, content_type=file.content_type or "image/jpeg")
    message = add_thread_message(session, anchor=anchor, sender_id=user_id, kind="photo", media_object=object_name)
    _notify_thread(session, user_id, "📸")
    return _message_dict(message)


def _notify_thread(session: Session, sender_id: int, glyph: str) -> None:
    """Nudge the partner that a reply landed in a shared thread."""
    sender_name = USER_NAMES.get(sender_id, "Someone")
    for linked_id in get_linked_users(session, sender_id):
        linked_user = session.get(User, linked_id)
        if linked_user and linked_user.token:
            send_notification(
                linked_user.token,
                f"{glyph} {sender_name} replied in a memory thread",
                message_type="THREAD_MESSAGE",
            )


# ---------- PROMPT (a memory the tree resurfaces when things go quiet) ----------


@app.get("/prompt")
def get_prompt() -> dict:
    """Pick one memory to gently resurface — deterministic per "week"
    (WEEK_SECONDS, the same window as the forest) so BOTH partners are offered
    the same one ("sends to both"), and a NEW prompt (with its own fresh chat)
    arrives each week. The client decides whether to show it (only when prompts
    are on and the tree is quiet)."""
    try:
        objects = _board_objects()
    except Exception:
        logging.exception("Memory storage unavailable; no prompt")
        return {"prompt": None}

    if not objects:
        return {"prompt": None}

    # Resurface older moments: drop the few most-recent, then pick stably per week.
    pool = objects[3:] or objects
    prompt_index = int(datetime.now(UTC).timestamp() // WEEK_SECONDS)
    object_name, last_modified = pool[prompt_index % len(pool)]
    kind, sender = _memory_meta(object_name)
    return {
        "prompt": {
            "objectName": object_name,
            "type": kind,
            "sender": sender,
            "epoch": int(last_modified.timestamp()),
            # A per-prompt thread anchor: replies to today's prompt live in their
            # own chat, separate from yesterday's (and from the memory's own thread).
            "threadAnchor": f"{PROMPT_ANCHOR_PREFIX}{prompt_index}/{object_name}",
        }
    }
