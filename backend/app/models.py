from datetime import datetime, timezone
from typing import Optional

from sqlmodel import SQLModel, Field, UniqueConstraint


class User(SQLModel, table=True):
    user_id: int = Field(primary_key=True)
    token: Optional[str] = None


class UserLink(SQLModel, table=True):
    __table_args__ = (UniqueConstraint("user_id", "linked_user_id"),)
    user_id: int = Field(index=True, primary_key=True)
    linked_user_id: int = Field(index=True, primary_key=True)


class OkayEvent(SQLModel, table=True):
    user_id: int = Field(index=True, primary_key=True)
    timestamp: datetime = Field(default_factory=lambda: datetime.now(timezone.utc), index=True, primary_key=True)