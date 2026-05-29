from collections.abc import Generator

from sqlalchemy.engine import Engine
from sqlmodel import Session, SQLModel, create_engine

from app.settings import Settings


def create_engine_from_settings(settings: Settings) -> Engine:
    connect_args = {}

    if settings.database_url.startswith("sqlite"):
        connect_args = {"check_same_thread": False}

    return create_engine(
        settings.database_url,
        echo=settings.database_echo,
        pool_pre_ping=True,
        connect_args=connect_args,
    )


def init_db(engine: Engine) -> None:
    SQLModel.metadata.create_all(engine)


def get_session(engine: Engine):
    def _get_session() -> Generator[Session, None, None]:
        with Session(engine) as session:
            yield session

    return _get_session