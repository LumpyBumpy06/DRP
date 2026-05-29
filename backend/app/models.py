from datetime import datetime
from typing import Optional

from sqlmodel import SQLModel, Field, UniqueConstraint


class User(SQLModel, table=True):
    user_id: int = Field(primary_key=True)
    token: Optional[str] = None


class UserLink(SQLModel, table=True):
    __table_args__ = (UniqueConstraint("user_id", "linked_user_id"),)
    user_id: int = Field(index=True)
    linked_user_id: int = Field(index=True)


class OkayEvent(SQLModel, table=True):
    user_id: int = Field(index=True)
    timestamp: datetime = Field(default_factory=datetime.utcnow, index=True)