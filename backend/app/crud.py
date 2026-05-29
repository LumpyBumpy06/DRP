from datetime import datetime, timedelta, timezone

from sqlmodel import Session, select

from app.models import OkayEvent, User, UserLink


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


# ---------- OKAY EVENTS ----------

def create_okay_event(session: Session, user_id: int) -> OkayEvent:
    event = OkayEvent(user_id=user_id)
    session.add(event)
    session.commit()
    session.refresh(event)
    return event


def get_latest_okay_event(session: Session, user_id: int) -> OkayEvent | None:
    stmt = (
        select(OkayEvent)
        .where(OkayEvent.user_id == user_id)
        .order_by(OkayEvent.timestamp.desc())
        .limit(1)
    )
    return session.exec(stmt).first()


def is_okay_within_6h(event: OkayEvent | None) -> bool:
    if not event:
        return False
    event_timestamp = event.timestamp.replace(tzinfo=timezone.utc)

    return event_timestamp >= datetime.now(timezone.utc) - timedelta(seconds=30)