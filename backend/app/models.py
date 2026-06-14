from datetime import UTC, datetime

from sqlmodel import Field, SQLModel, UniqueConstraint


class User(SQLModel, table=True):
    user_id: int = Field(primary_key=True)
    token: str | None = None


class UserLink(SQLModel, table=True):
    __table_args__ = (UniqueConstraint("user_id", "linked_user_id"),)
    user_id: int = Field(index=True, primary_key=True)
    linked_user_id: int = Field(index=True, primary_key=True)


class OkayEvent(SQLModel, table=True):
    user_id: int = Field(index=True, primary_key=True)
    timestamp: datetime = Field(default_factory=lambda: datetime.now(UTC), index=True, primary_key=True)


class ReviveEvent(SQLModel, table=True):
    user_id: int = Field(index=True, primary_key=True)
    timestamp: datetime = Field(default_factory=lambda: datetime.now(UTC), index=True, primary_key=True)


class ForestTree(SQLModel, table=True):
    """One frozen weekly tree in the shared forest.

    When a week elapses, the live tree's final state is snapshotted into this
    table exactly once and never recomputed — so the forest is stable history:
    trees can't disappear or change retroactively as events age out or are
    deleted. `week_index` is the human label ("Week 1", "Week 2", …), assigned
    sequentially as trees are frozen.
    """

    week_start: int = Field(primary_key=True)  # Unix seconds, aligned to WEEK_SECONDS
    week_index: int
    stage: int
    death_level: float


class EmergencyAlert(SQLModel, table=True):
    # One row per sender while their SOS is unacknowledged; deleted when a carer
    # taps "All good". Persisting it (rather than relying only on the push) lets
    # the carer's poll show the popup even if the notification was missed.
    sender_id: int = Field(primary_key=True)
    raised_at: datetime = Field(default_factory=lambda: datetime.now(UTC))


class MemoryTag(SQLModel, table=True):
    """A label a user has stuck on a memory (photo or voice note).

    Tags are shared between both partners — they hang off the memory's storage
    key (`object_name`), so a memory can carry several tags and a tag can sit on
    many memories. Tag names are free-form strings (e.g. "Favourites", "Family",
    or anything custom the user types); de-duplication is case-insensitive and
    handled when tags are written.
    """

    __table_args__ = (UniqueConstraint("object_name", "tag"),)
    id: int | None = Field(default=None, primary_key=True)
    object_name: str = Field(index=True)
    tag: str = Field(index=True)


# ---------- THREADS (conversations anchored to a memory) ----------


class ThreadCaption(SQLModel, table=True):
    """The user-given title of a conversation, keyed by its anchor.

    Set when a thread is started from the gallery ("Start a conversation"
    asks for a caption). Persisted so the title survives app restarts and is
    shared by both partners.
    """

    anchor: str = Field(primary_key=True)
    caption: str = ""


class ThreadMessage(SQLModel, table=True):
    """One message inside a conversation that hangs off a memory.

    A "thread" isn't its own row — it's simply every ThreadMessage that shares
    the same `anchor` (the object_name of the photo/voice memo the chat is
    about). Both partners read and write the same rows, so a conversation is
    shared by design (the "sends to both" behaviour).

    `kind` is "text" | "voice" | "photo". For text the words live in `text`;
    for voice/photo the clip/snap is uploaded to object storage under a
    "threads/" prefix and its name kept in `media_object` (streamed back via
    the existing /media endpoint), with `text` used as an optional caption.
    """

    id: int | None = Field(default=None, primary_key=True)
    anchor: str = Field(index=True)
    sender_id: int = Field(index=True)
    kind: str = "text"
    text: str = ""
    media_object: str | None = None
    created_at: datetime = Field(default_factory=lambda: datetime.now(UTC), index=True)
