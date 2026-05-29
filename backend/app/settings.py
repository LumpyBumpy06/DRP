import typing
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        extra="ignore",
    )

    database_type: str = "sqlite"

    postgres_user: str = ""
    postgres_password: str = ""
    postgres_host: str = ""
    postgres_port: int = 5440
    postgres_db: str = ""

    sqlite_file: str = ""

    db_echo: bool = False

    firebase_credentials_json: typing.Any = None

    @property
    def database_url(self) -> str:
        if self.database_type == "sqlite":
            return f"sqlite:///./{self.sqlite_file}"

        return f"postgresql+psycopg://{self.postgres_user}:{self.postgres_password}@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"


@lru_cache
def get_settings() -> Settings:
    return Settings()
