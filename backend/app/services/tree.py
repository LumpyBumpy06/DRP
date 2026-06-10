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
DEATH_WINDOWS = 6


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
