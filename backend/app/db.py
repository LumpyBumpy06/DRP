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
    _migrate_foresttree_moments(engine)


def _migrate_foresttree_moments(engine: Engine) -> None:
    """Add ForestTree.period_start/period_end/moment_count to a pre-existing table.

    These record which moments each frozen tree captured (so its sub-gallery and
    montage show them) plus the count. create_all() never ALTERs, so add them
    idempotently; existing rows default to 0 (an empty gallery, acceptable for
    trees frozen before this change)."""
    inspector = inspect(engine)
    if "foresttree" not in inspector.get_table_names():
        return
    columns = {col["name"] for col in inspector.get_columns("foresttree")}
    to_add = [c for c in ("period_start", "period_end", "moment_count") if c not in columns]
    if not to_add:
        return
    with engine.begin() as conn:
        for column in to_add:
            conn.execute(text(f"ALTER TABLE foresttree ADD COLUMN {column} INTEGER DEFAULT 0"))


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
