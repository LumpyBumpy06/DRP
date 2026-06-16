from collections.abc import Callable, Generator

from sqlalchemy import inspect, text
from sqlalchemy.engine import Engine
from sqlmodel import Session, SQLModel, create_engine

from app.settings import Settings


def create_engine_from_settings(settings: Settings) -> Engine:
    url = settings.database_url

    connect_args = {}

    if url.startswith("sqlite"):
        connect_args = {"check_same_thread": False}

    return create_engine(
        url,
        echo=settings.db_echo,
        pool_pre_ping=True,
        connect_args=connect_args,
    )


def init_db(engine: Engine) -> None:
    SQLModel.metadata.create_all(engine)
    _migrate_caption_created_at(engine)


def _migrate_caption_created_at(engine: Engine) -> None:
    """Add ThreadCaption.created_at to a pre-existing table.

    create_all() never ALTERs an existing table, so a DB created before this
    column existed would be missing it. Add it idempotently (works on both
    SQLite and Postgres) so /threads can sort caption-only threads by age.
    """
    inspector = inspect(engine)
    if "threadcaption" not in inspector.get_table_names():
        return
    columns = {col["name"] for col in inspector.get_columns("threadcaption")}
    if "created_at" in columns:
        return
    with engine.begin() as conn:
        conn.execute(text("ALTER TABLE threadcaption ADD COLUMN created_at TIMESTAMP"))


def get_session(engine: Engine) -> Callable[[], Generator[Session]]:
    def _get_session() -> Generator[Session]:
        with Session(engine) as session:
            yield session

    return _get_session
