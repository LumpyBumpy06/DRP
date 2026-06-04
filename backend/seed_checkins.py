"""Dev helper to test the motivation tree without waiting real time.

Replaces a user's check-in history with `active_days` check-ins spread across
distinct, recent "day" buckets, so the tree jumps to:

    growth   = (active_days % DAYS_TO_MATURE) / DAYS_TO_MATURE
    leafiness = full   (most recent check-in is ~1 day ago)

Run it against the SAME database your backend uses (local sqlite by default),
then watch the app's tree poll catch up within a few seconds.

    python seed_checkins.py            # 7 active days for Norman (user 1)
    python seed_checkins.py 14         # 14 -> matures into the next species
    python seed_checkins.py 5 2        # 5 active days for Sadie (user 2)
"""

import sys
from datetime import UTC, datetime, timedelta

from sqlmodel import Session, select

from app.crud import CHECK_IN_WINDOW_SECONDS
from app.db import create_engine_from_settings, init_db
from app.models import OkayEvent
from app.settings import get_settings


def main() -> None:
    active_days = int(sys.argv[1]) if len(sys.argv) > 1 else 7
    user_id = int(sys.argv[2]) if len(sys.argv) > 2 else 1

    settings = get_settings()
    engine = create_engine_from_settings(settings)
    init_db(engine)

    now = datetime.now(UTC)
    with Session(engine) as session:
        # Clear this user's history so growth is exactly active_days / mature.
        for event in session.exec(select(OkayEvent).where(OkayEvent.user_id == user_id)).all():
            session.delete(event)

        # One check-in per distinct recent bucket; newest ~1 day ago = full leaves.
        for i in range(active_days):
            ts = now - timedelta(seconds=(i + 1) * CHECK_IN_WINDOW_SECONDS)
            session.add(OkayEvent(user_id=user_id, timestamp=ts))

        session.commit()

    print(f"Seeded {active_days} active days for user {user_id}. Refresh the app to watch the tree.")


if __name__ == "__main__":
    main()
