from fastapi import Depends, FastAPI
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
from app.services.notifications import send_notification
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
            send_notification(
                linked_user.token,
                f"User {user_id} checked in OK",
            )

    return {"ok": True, "timestamp": event.timestamp.isoformat()}
