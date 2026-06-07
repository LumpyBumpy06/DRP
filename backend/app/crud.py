from datetime import UTC, datetime, timedelta

from sqlmodel import Session, col, desc, select

from app.models import EmergencyAlert, OkayEvent, User, UserLink

# One "day" in the current simulation. A check-in (or voice message) is only
# considered current for this long.
CHECK_IN_WINDOW_SECONDS = 10

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


def get_linked_users(session: Session, user_id: int) -> list[int]:
    stmt = select(UserLink.linked_user_id).where(UserLink.user_id == user_id)
    return list(session.exec(stmt).all())


def get_linking_users(session: Session, target_id: int) -> list[int]:
    """Inverse of [get_linked_users]: users whose alerts `target_id` should see."""
    stmt = select(UserLink.user_id).where(UserLink.linked_user_id == target_id)
    return list(session.exec(stmt).all())


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
