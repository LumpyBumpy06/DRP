from datetime import UTC, datetime, timedelta

from sqlmodel import Session, col, desc, select

from app.models import EmergencyAlert, ForestTree, MemoryTag, OkayEvent, PromptAnnouncement, ReviveEvent, ThreadCaption, ThreadMessage, User, UserLink
from app.services.tree import WEEK_SECONDS, compute_week_snapshot, week_start_of

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


def get_user_tokens(session: Session) -> list[str]:
    """Every known device token (both partners) — for broadcasting to all sides."""
    return [user.token for user in session.exec(select(User)).all() if user.token]


# ---------- PROMPT ANNOUNCEMENTS ----------


def mark_prompt_announced(session: Session, prompt_key: str) -> bool:
    """Claim the one-time announcement for `prompt_key`.

    Returns True if this call is the first to announce it (so the caller should
    send the push), or False if it was already announced (so the caller stays
    silent). Idempotent under repeated polling from either side.
    """
    if session.get(PromptAnnouncement, prompt_key) is not None:
        return False
    session.add(PromptAnnouncement(prompt_key=prompt_key))
    session.commit()
    return True


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


def seed_okay_events(session: Session, user_id: int, count: int) -> None:
    """DEMO HELPER: insert `count` waterings for `user_id`, so the shared tree jumps
    straight to a chosen growth stage. Events are spaced 1ms apart ending now: that
    keeps each row's timestamp distinct (required by the (user_id, timestamp) key)
    AND packed into a tiny window right at `now`, so they all fall inside the
    current week (never the previous one — which would wrongly freeze a forest
    tree) and the tree reads as freshly watered (alive)."""
    now = datetime.now(UTC)
    for i in range(count):
        session.add(OkayEvent(user_id=user_id, timestamp=now - timedelta(milliseconds=i)))
    session.commit()


def create_revive_event(session: Session, user_id: int) -> ReviveEvent:
    event = ReviveEvent(user_id=user_id)
    session.add(event)
    session.commit()
    session.refresh(event)
    return event


def reset_tree(session: Session) -> int:
    """Delete every check-in AND every frozen forest tree — a full restart."""
    events = list(session.exec(select(OkayEvent)).all())
    # for event in events:
    #     session.delete(event)
    # for event in session.exec(select(ReviveEvent)).all():
    #     session.delete(event)
    for event in events:
        session.delete(event)

    for revive in session.exec(select(ReviveEvent)).all():
        session.delete(revive)

    for tree in session.exec(select(ForestTree)).all():
        session.delete(tree)
    session.commit()
    return len(events)


# ---------- FOREST (frozen weekly trees) ----------


def get_forest_trees(session: Session) -> list[ForestTree]:
    """Every frozen weekly tree, oldest first."""
    return list(session.exec(select(ForestTree).order_by(col(ForestTree.week_start))).all())


def freeze_elapsed_weeks(session: Session, now: datetime) -> None:
    """Snapshot any elapsed weeks that aren't yet in the forest.

    Idempotent and cheap: called on every /tree and /forest read, it looks at
    weeks between the last frozen one (or the first watering) and the current
    week, and stores one ForestTree per week that had at least one watering.
    Once a row exists it is never touched again, so the forest only ever grows.
    """
    current_week = week_start_of(now.timestamp())

    existing = get_forest_trees(session)
    frozen_weeks = {t.week_start for t in existing}
    next_index = max((t.week_index for t in existing), default=0) + 1

    norman = get_okay_timestamps(session, 1)
    sadie = get_okay_timestamps(session, 2)
    revives = get_revive_timestamps(session, 1) + get_revive_timestamps(session, 2)
    combined = norman + sadie + revives
    if not combined:
        return

    def _epoch(ts: datetime) -> float:
        return (ts.replace(tzinfo=UTC) if ts.tzinfo is None else ts).timestamp()

    first_week = week_start_of(min(_epoch(t) for t in combined))
    # Resume after the newest frozen week so we never re-evaluate old weeks.
    if frozen_weeks:
        first_week = max(first_week, max(frozen_weeks) + WEEK_SECONDS)

    added = False
    for week_start in range(first_week, current_week, WEEK_SECONDS):
        if week_start in frozen_weeks:
            continue
        # Every elapsed week plants a tree — an idle week just plants a
        # fully-neglected sapling rather than nothing.
        snapshot = compute_week_snapshot(norman, sadie, week_start, CHECK_IN_WINDOW_SECONDS, revives)
        session.add(
            ForestTree(
                week_start=week_start,
                week_index=next_index,
                stage=snapshot["stage"],
                death_level=snapshot["deathLevel"],
            )
        )
        next_index += 1
        added = True
    if added:
        session.commit()


def get_latest_okay_event(session: Session, user_id: int) -> OkayEvent | None:
    stmt = select(OkayEvent).where(OkayEvent.user_id == user_id).order_by(desc(OkayEvent.timestamp)).limit(1)
    return session.exec(stmt).first()


def get_latest_revive_event(session: Session, user_id: int) -> ReviveEvent | None:
    stmt = select(ReviveEvent).where(ReviveEvent.user_id == user_id).order_by(desc(ReviveEvent.timestamp)).limit(1)
    return session.exec(stmt).first()


def get_latest_check_in_event(session: Session, user_id: int) -> OkayEvent | ReviveEvent | None:
    okay_event: OkayEvent | None = get_latest_okay_event(session, user_id)
    revive_event: ReviveEvent | None = get_latest_revive_event(session, user_id)
    if okay_event is None:
        return revive_event
    if revive_event is None:
        return okay_event
    okay_epoch = okay_event.timestamp.replace(tzinfo=UTC) if okay_event.timestamp.tzinfo is None else okay_event.timestamp
    revive_epoch = revive_event.timestamp.replace(tzinfo=UTC) if revive_event.timestamp.tzinfo is None else revive_event.timestamp
    return okay_event if okay_epoch >= revive_epoch else revive_event


def get_revive_timestamps(session: Session, user_id: int) -> list[datetime]:
    stmt = select(ReviveEvent.timestamp).where(ReviveEvent.user_id == user_id)
    return list(session.exec(stmt).all())


def get_okay_timestamps(session: Session, user_id: int) -> list[datetime]:
    """Every watering time for `user_id` — drives the shared tree's stage."""
    stmt = select(OkayEvent.timestamp).where(OkayEvent.user_id == user_id)
    return list(session.exec(stmt).all())


def is_okay_within_6h(event: OkayEvent | ReviveEvent | None) -> bool:
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


def set_thread_caption(session: Session, anchor: str, caption: str) -> bool:
    """Set (or update) the user-given title of a conversation.

    Returns True if this created the thread's title row for the first time (i.e.
    the conversation was just started), False if it renamed an existing one.
    """
    row = session.get(ThreadCaption, anchor)
    created = row is None
    if row is None:
        row = ThreadCaption(anchor=anchor)
    row.caption = caption.strip()
    session.add(row)
    session.commit()
    return created


def get_thread_captions(session: Session) -> dict[str, str]:
    """{anchor: caption} for every titled conversation."""
    return {row.anchor: row.caption for row in session.exec(select(ThreadCaption)).all() if row.caption}


def get_thread_caption_rows(session: Session) -> list[ThreadCaption]:
    """Full caption rows (anchor, caption, created_at) for every titled thread —
    used to surface caption-only threads and sort them by when they were named."""
    return [row for row in session.exec(select(ThreadCaption)).all() if row.caption]


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


def add_tag_for(session: Session, object_name: str, tag: str) -> list[str]:
    """Add one tag to a memory (no-op if it already has it, case-insensitively).

    Atomic per tag — unlike a replace-all, two devices adding different tags at
    once can never wipe each other's tags out.
    """
    name = tag.strip()
    if name:
        existing = {t.lower() for t in get_tags_for(session, object_name)}
        if name.lower() not in existing:
            session.add(MemoryTag(object_name=object_name, tag=name))
            session.commit()
    return get_tags_for(session, object_name)


def remove_tag_for(session: Session, object_name: str, tag: str) -> list[str]:
    """Remove one tag (case-insensitively) from a memory."""
    rows = session.exec(select(MemoryTag).where(MemoryTag.object_name == object_name)).all()
    target = tag.strip().lower()
    removed = False
    for row in rows:
        if row.tag.lower() == target:
            session.delete(row)
            removed = True
    if removed:
        session.commit()
    return get_tags_for(session, object_name)


def all_tag_names(session: Session) -> list[str]:
    """Distinct tag names across all memories, alphabetical (case-insensitive)."""
    names = list(session.exec(select(MemoryTag.tag).distinct()).all())
    names.sort(key=str.lower)
    return names
