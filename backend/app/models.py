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


class EmergencyAlert(SQLModel, table=True):
    # One row per sender while their SOS is unacknowledged; deleted when a carer
    # taps "All good". Persisting it (rather than relying only on the push) lets
    # the carer's poll show the popup even if the notification was missed.
    sender_id: int = Field(primary_key=True)
    raised_at: datetime = Field(default_factory=lambda: datetime.now(UTC))


# ---------- THREADS (conversations anchored to a memory) ----------


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
