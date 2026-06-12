from datetime import UTC, datetime, timedelta

from sqlmodel import Session, col, desc, select

from app.models import EmergencyAlert, MemoryTag, OkayEvent, ThreadMessage, User, UserLink

# One "day" in the current simulation. A check-in (or voice message) is only
# considered current for this long.
CHECK_IN_WINDOW_SECONDS = 20

# ---------- USERS ----------


def upsert_user_token(session: Session, user_id: int, token: str) -> User:
    user = session.get(User, user_id)

    if user is None:
        user = User(user_id=user_id, token=token)
    else:
        user.token = token

    session.add(user)
    session.commit()
    session.refresh(user)
    return user


# ---------- LINKS ----------


def _get_related_users(session: Session, user_id: int) -> list[int]:
    """Return every partner linked to `user_id`, regardless of which side stored it."""
    outgoing = session.exec(select(UserLink.linked_user_id).where(UserLink.user_id == user_id)).all()
    incoming = session.exec(select(UserLink.user_id).where(UserLink.linked_user_id == user_id)).all()

    related: list[int] = []
    for linked_id in [*outgoing, *incoming]:
        if linked_id != user_id and linked_id not in related:
            related.append(linked_id)

    return related


def get_linked_users(session: Session, user_id: int) -> list[int]:
    return _get_related_users(session, user_id)


def get_linking_users(session: Session, target_id: int) -> list[int]:
    """Inverse of [get_linked_users]: users whose alerts `target_id` should see."""
    return _get_related_users(session, target_id)


# ---------- EMERGENCY ----------


def raise_emergency(session: Session, sender_id: int) -> None:
    """Mark `sender_id` as having an active, unacknowledged emergency."""
    alert = session.get(EmergencyAlert, sender_id)
    if alert is None:
        alert = EmergencyAlert(sender_id=sender_id)
    else:
        alert.raised_at = datetime.now(UTC)
    session.add(alert)
    session.commit()


def active_emergency_sender_for(session: Session, target_id: int) -> int | None:
    """Id of someone with an unacknowledged emergency that `target_id` should see."""
    senders = get_linking_users(session, target_id)
    if not senders:
        return None
    stmt = select(EmergencyAlert.sender_id).where(col(EmergencyAlert.sender_id).in_(senders)).limit(1)
    return session.exec(stmt).first()


def clear_emergencies_for(session: Session, target_id: int) -> None:
    """Acknowledge: drop every active emergency that `target_id` can see."""
    for sender_id in get_linking_users(session, target_id):
        alert = session.get(EmergencyAlert, sender_id)
        if alert is not None:
            session.delete(alert)
    session.commit()


# ---------- OKAY EVENTS ----------


def create_okay_event(session: Session, user_id: int) -> OkayEvent:
    event = OkayEvent(user_id=user_id)
    session.add(event)
    session.commit()
    session.refresh(event)
    return event


def reset_tree(session: Session) -> int:
    """Delete every check-in so the shared tree returns to its initial state."""
    events = list(session.exec(select(OkayEvent)).all())
    for event in events:
        session.delete(event)
    session.commit()
    return len(events)


def get_latest_okay_event(session: Session, user_id: int) -> OkayEvent | None:
    stmt = select(OkayEvent).where(OkayEvent.user_id == user_id).order_by(desc(OkayEvent.timestamp)).limit(1)
    return session.exec(stmt).first()


def get_okay_timestamps(session: Session, user_id: int) -> list[datetime]:
    """Every watering time for `user_id` — drives the shared tree's stage."""
    stmt = select(OkayEvent.timestamp).where(OkayEvent.user_id == user_id)
    return list(session.exec(stmt).all())


def is_okay_within_6h(event: OkayEvent | None) -> bool:
    if not event:
        return False
    event_timestamp = event.timestamp.replace(tzinfo=UTC)

    return event_timestamp >= datetime.now(UTC) - timedelta(seconds=CHECK_IN_WINDOW_SECONDS)


# ---------- THREADS (conversations anchored to a memory) ----------


def add_thread_message(
    session: Session,
    anchor: str,
    sender_id: int,
    kind: str,
    text: str = "",
    media_object: str | None = None,
) -> ThreadMessage:
    """Append one message to the conversation hanging off `anchor`."""
    message = ThreadMessage(
        anchor=anchor,
        sender_id=sender_id,
        kind=kind,
        text=text,
        media_object=media_object,
    )
    session.add(message)
    session.commit()
    session.refresh(message)
    return message


def get_thread_messages(session: Session, anchor: str) -> list[ThreadMessage]:
    """Every message in one conversation, oldest first."""
    stmt = select(ThreadMessage).where(ThreadMessage.anchor == anchor).order_by(col(ThreadMessage.created_at))
    return list(session.exec(stmt).all())


def get_all_thread_messages(session: Session) -> list[ThreadMessage]:
    """Every thread message across all conversations, oldest first (for summaries)."""
    stmt = select(ThreadMessage).order_by(col(ThreadMessage.created_at))
    return list(session.exec(stmt).all())


# ---------- TAGS (labels shared across both partners) ----------


def get_tags_for(session: Session, object_name: str) -> list[str]:
    """Every tag on one memory, alphabetical."""
    stmt = select(MemoryTag.tag).where(MemoryTag.object_name == object_name).order_by(col(MemoryTag.tag))
    return list(session.exec(stmt).all())


def get_tags_map(session: Session, object_names: list[str]) -> dict[str, list[str]]:
    """Tags for many memories at once: {object_name: [tag, ...]}."""
    if not object_names:
        return {}
    stmt = select(MemoryTag).where(col(MemoryTag.object_name).in_(object_names))
    out: dict[str, list[str]] = {}
    for row in session.exec(stmt).all():
        out.setdefault(row.object_name, []).append(row.tag)
    for tags in out.values():
        tags.sort(key=str.lower)
    return out


def set_tags_for(session: Session, object_name: str, tags: list[str]) -> list[str]:
    """Replace the whole tag set on a memory. De-dupes case-insensitively and
    drops blanks; both partners see the result (tags hang off the object)."""
    existing = session.exec(select(MemoryTag).where(MemoryTag.object_name == object_name)).all()
    for row in existing:
        session.delete(row)

    seen: set[str] = set()
    for raw in tags:
        name = raw.strip()
        if not name or name.lower() in seen:
            continue
        seen.add(name.lower())
        session.add(MemoryTag(object_name=object_name, tag=name))

    session.commit()
    return get_tags_for(session, object_name)


def all_tag_names(session: Session) -> list[str]:
    """Distinct tag names across all memories, alphabetical (case-insensitive)."""
    names = list(session.exec(select(MemoryTag.tag).distinct()).all())
    names.sort(key=str.lower)
    return names
