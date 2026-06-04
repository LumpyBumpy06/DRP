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
