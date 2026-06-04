"""Derives a shared "motivation tree" from the check-in history.

Nothing is stored: the tree state is a pure function of past OkayEvents, so both
Norman and Sadie always see the same tree.

A "day" is one check-in window (CHECK_IN_WINDOW_SECONDS). The tree:
  * grows with each distinct active day (any check-in),
  * matures and resets to a new species every DAYS_TO_MATURE active days,
  * sheds leaves as Norman's misses pile up (a 1-day grace, then linear to bare).
"""

from datetime import UTC, datetime

# Tunables, expressed in "days" (= check-in windows).
DAYS_TO_MATURE = 14  # active days before the tree renews into a new species
DAYS_TO_BARE = 4  # missed days (after a grace day) until leaves are gone
NUM_TREE_TYPES = 3


def _bucket(t: datetime, day_seconds: int) -> int:
    if t.tzinfo is None:
        t = t.replace(tzinfo=UTC)
    return int(t.timestamp() // day_seconds)


def compute_tree_state(
    now: datetime,
    norman_timestamps: list[datetime],
    activity_timestamps: list[datetime],
    day_seconds: int,
) -> dict:
    active_days = len({_bucket(t, day_seconds) for t in activity_timestamps})

    # Brand-new, untouched: a healthy little sapling.
    if active_days == 0:
        return {
            "growth": 0.0,
            "leafiness": 1.0,
            "treeType": 0,
            "activeDays": 0,
            "daysSinceCheckIn": 0,
        }

    epoch = active_days // DAYS_TO_MATURE
    growth = (active_days % DAYS_TO_MATURE) / DAYS_TO_MATURE
    tree_type = epoch % NUM_TREE_TYPES

    current_bucket = _bucket(now, day_seconds)
    norman_buckets = {_bucket(t, day_seconds) for t in norman_timestamps}
    if norman_buckets:
        days_since = max(0, current_bucket - max(norman_buckets))
    else:
        days_since = active_days  # there's activity, but Norman has never checked in

    missed = max(0, days_since - 1)  # one day of grace before leaves drop
    leafiness = max(0.0, 1.0 - missed / DAYS_TO_BARE)

    return {
        "growth": round(growth, 3),
        "leafiness": round(leafiness, 3),
        "treeType": tree_type,
        "activeDays": active_days,
        "daysSinceCheckIn": days_since,
    }
