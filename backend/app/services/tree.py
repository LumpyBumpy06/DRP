"""Shared "watering" tree.

Growth is driven by the **total number of waterings** (an OkayEvent = an "I'm
okay"/water tap or a voice message) from both people combined, against rising
thresholds. Neglect kills it: `deathLevel` rises with time since the last
watering, which the client uses to wilt the tree and regress its growth stage.

Each week grows its own tree from that week's waterings. When the week elapses
the tree's final state is frozen into the ForestTree table (see crud.py) and
the live tree starts again from stage 0 — so the forest gains exactly one tree
per active week, and frozen trees never change or disappear afterwards.
"""

from datetime import UTC, datetime

# Cumulative total-waterings needed to reach each growth stage (increasing).
# Stages 0..5 grow the tree to full size (+1, +2, +3, +4, +5 moments per stage);
# stages 6..9 are the "bird" stages, where the fully-grown tree gains 1, 2, 3 then
# 4 birds — each bird costs just ONE more moment. Stage 9 (4 birds) is the highest.
GROWTH_THRESHOLDS = [0, 1, 3, 6, 10, 15, 16, 17, 18, 19]

# No watering for this many "day" windows => fully dead (deathLevel == 1.0).
# DEMO VALUE: kept large so a tree you set with the demo stage buttons stays
# healthy throughout a session instead of wilting ~60s after the last watering.
# With the 20s check-in window, 1000 windows ≈ 5.5h to fully wilt, so deathLevel
# stays ~0 across a 10-minute month (a tree only "dies" if genuinely neglected
# for hours). Lower this if you want to demo the wilting/dying state itself.
DEATH_WINDOWS = 1000

# Length of one "forest" week. Each elapsed week becomes a frozen tree and the
# live tree restarts at stage 0.
# DEMO VALUE: deliberately huge (~100 years) so a week never elapses during a
# demo — the live tree only ever accumulates waterings (it can only GROW, never
# auto-reset), and nothing is auto-frozen into the forest. The only way to shrink
# the tree is the /debug/setStage endpoint. MUST match the client's WEEK_SECONDS.
# For real weekly behaviour use 7 * 24 * 3600.
WEEK_SECONDS = 3_153_600_000


def _epoch(ts: datetime) -> float:
    """Unix seconds for a timestamp, assuming UTC when it's naive."""
    if ts.tzinfo is None:
        ts = ts.replace(tzinfo=UTC)
    return ts.timestamp()


def week_start_of(epoch_seconds: float) -> int:
    """The aligned week-start (Unix seconds) containing `epoch_seconds`."""
    return (int(epoch_seconds) // WEEK_SECONDS) * WEEK_SECONDS


def compute_tree_state(
    norman_ts: list[datetime],
    sadie_ts: list[datetime],
    now: datetime,
    day_seconds: int,
    revive_ts: list[datetime] | None = None,
) -> dict:
    revive_ts = revive_ts or []
    all_ts = sorted(norman_ts + sadie_ts)
    total = len(all_ts)

    # Highest growth stage whose threshold the combined waterings have reached.
    stage = max(0, sum(1 for threshold in GROWTH_THRESHOLDS if total >= threshold) - 1)

    last_activity = sorted(all_ts + revive_ts)
    if last_activity:
        last = last_activity[-1]
        if last.tzinfo is None:
            last = last.replace(tzinfo=UTC)
        seconds_since = (now - last).total_seconds()
        death_level = max(0.0, min(1.0, seconds_since / (day_seconds * DEATH_WINDOWS)))
    else:
        death_level = 0.0

    return {
        "stage": stage,
        "deathLevel": round(death_level, 3),
        "totalWaterings": total,
    }


def _events_in_week(timestamps: list[datetime], week_start: float, end_exclusive: float) -> list[datetime]:
    """Events in [week_start, end_exclusive) — half-open so a boundary event
    belongs to exactly one week and is never double-counted."""
    return [t for t in timestamps if week_start <= _epoch(t) < end_exclusive]


def compute_current_week_state(
    norman_ts: list[datetime],
    sadie_ts: list[datetime],
    now: datetime,
    day_seconds: int,
    revive_ts: list[datetime] | None = None,
) -> dict:
    """The live tree for the CURRENT week only.

    Each week is its own tree: it grows from the waterings that happen *this*
    week and resets to stage 0 when the week rolls over (the finished tree is
    frozen into the forest).
    """
    now_epoch = now.timestamp()
    week_start = float(week_start_of(now_epoch))
    week_end = week_start + WEEK_SECONDS
    n = _events_in_week(norman_ts, week_start, week_end)
    s = _events_in_week(sadie_ts, week_start, week_end)
    r = _events_in_week(revive_ts or [], week_start, week_end)
    return compute_tree_state(n, s, now, day_seconds, r)


def compute_week_snapshot(
    norman_ts: list[datetime],
    sadie_ts: list[datetime],
    week_start: int,
    day_seconds: int,
    revive_ts: list[datetime] | None = None,
) -> dict:
    """The frozen tree state for an ELAPSED week, evaluated at the week's end.

    A week with no waterings still plants a tree — a fully-neglected stage-0
    sapling — so the forest gains exactly one tree per week, no matter what.
    """
    week_end = float(week_start + WEEK_SECONDS)
    n = _events_in_week(norman_ts, float(week_start), week_end)
    s = _events_in_week(sadie_ts, float(week_start), week_end)
    r = _events_in_week(revive_ts or [], float(week_start), week_end)
    if not n and not s and not r:
        return {"stage": 0, "deathLevel": 1.0, "totalWaterings": 0}
    eval_dt = datetime.fromtimestamp(week_end, UTC)
    return compute_tree_state(n, s, eval_dt, day_seconds, r)
