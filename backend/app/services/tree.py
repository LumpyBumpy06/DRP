"""Shared "watering" tree.

Growth is driven by the **total number of waterings** (an OkayEvent = an "I'm
okay"/water tap or a voice message) from both people combined, against rising
thresholds. Neglect kills it: `deathLevel` rises with time since the last
watering, which the client uses to wilt the tree and regress its growth stage.
Pure function of the event history, so Norman and Sadie see the same tree.
"""

from datetime import UTC, datetime

# Cumulative total-waterings needed to reach each growth stage (increasing).
# Stages 0..5 grow the tree to full size; stages 6..9 are the "bird" stages,
# where the fully-grown tree gains 1, 2, 3 then 4 birds circling it. Stage 10
# adds a squirrel peeking from a hollow in the trunk, and stage 11 grows oranges
# in the canopy that the birds snatch.
GROWTH_THRESHOLDS = [0, 1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 66]

# No watering for this many "day" windows => fully dead (deathLevel == 1.0).
DEATH_WINDOWS = 12

# Length of one "forest" week. Each elapsed week becomes a frozen tree.
# TEST VALUE: 60s so new trees appear ~every minute (matches the compressed
# 10s "day"). For production use 7 * 24 * 3600. MUST match the client's WEEK_SECONDS.
WEEK_SECONDS = 120

# Cap how many recent weeks the forest returns, so a long history (especially
# with the short test week) doesn't produce hundreds of trees to render.
MAX_FOREST_WEEKS = 16


def _epoch(ts: datetime) -> float:
    """Unix seconds for a timestamp, assuming UTC when it's naive."""
    if ts.tzinfo is None:
        ts = ts.replace(tzinfo=UTC)
    return ts.timestamp()


def compute_tree_state(
    norman_ts: list[datetime],
    sadie_ts: list[datetime],
    now: datetime,
    day_seconds: int,
) -> dict:
    all_ts = sorted(norman_ts + sadie_ts)
    total = len(all_ts)

    # Highest growth stage whose threshold the combined waterings have reached.
    stage = max(0, sum(1 for threshold in GROWTH_THRESHOLDS if total >= threshold) - 1)

    if all_ts:
        last = all_ts[-1]
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


def compute_current_week_state(
    norman_ts: list[datetime],
    sadie_ts: list[datetime],
    now: datetime,
    day_seconds: int,
) -> dict:
    """The live tree for the CURRENT week only.

    Each week is its own tree: it grows from the waterings that happen *this* week
    and resets to stage 0 when the week rolls over (the finished tree is kept in
    the forest).
    """
    now_epoch = now.timestamp()
    week_start = (int(now_epoch) // WEEK_SECONDS) * WEEK_SECONDS
    n = [t for t in norman_ts if week_start <= _epoch(t) <= now_epoch]
    s = [t for t in sadie_ts if week_start <= _epoch(t) <= now_epoch]
    return compute_tree_state(n, s, now, day_seconds)


def compute_forest(
    norman_ts: list[datetime],
    sadie_ts: list[datetime],
    now: datetime,
    day_seconds: int,
) -> list[dict]:
    """One frozen tree per elapsed week, oldest first.

    Each week's tree is the *real* shared-tree state at that week's end (the live
    state for the in-progress week), computed from the event history up to that
    instant — so neglect/deathLevel is captured, and both users see an identical
    forest with no stored snapshots needed.
    """
    combined = norman_ts + sadie_ts
    if not combined:
        return []

    now_epoch = now.timestamp()
    first_week = int(min(_epoch(t) for t in combined)) // WEEK_SECONDS
    current_week = int(now_epoch) // WEEK_SECONDS
    # Only the most recent MAX_FOREST_WEEKS weeks, so the forest stays bounded.
    first_week = max(first_week, current_week - (MAX_FOREST_WEEKS - 1))

    weeks: list[dict] = []
    for w in range(first_week, current_week + 1):
        # Freeze at the week's end, or "now" for the still-running current week.
        week_start_epoch = float(w * WEEK_SECONDS)
        eval_epoch = min(float((w + 1) * WEEK_SECONDS), now_epoch)
        eval_dt = datetime.fromtimestamp(eval_epoch, UTC)
        # Each week is its own tree: only THIS week's waterings count toward it.
        n = [t for t in norman_ts if week_start_epoch <= _epoch(t) <= eval_epoch]
        s = [t for t in sadie_ts if week_start_epoch <= _epoch(t) <= eval_epoch]
        # A week with no waterings never grew a tree — don't plant a phantom
        # stage-0 sapling for it (otherwise idle weeks keep adding saplings).
        if not n and not s:
            continue
        state = compute_tree_state(n, s, eval_dt, day_seconds)
        weeks.append(
            {
                "weekStart": w * WEEK_SECONDS,
                "stage": state["stage"],
                "deathLevel": state["deathLevel"],
            }
        )
    return weeks
