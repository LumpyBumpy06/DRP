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

    # Local
    firebase_credentials_file: str | None = None
    # Kubernetes
    firebase_credentials_json: str | None = None

    # ---------- BLOCK STORAGE (MinIO / S3) ----------

    # Which block storage to talk to: "local" (dockerised MinIO on this machine)
    # or "deployment" (the cluster's MinIO). Lets you run the backend locally
    # against either store.
    storage_target: str = "local"

    minio_local_endpoint: str = "localhost:9008"
    minio_deployment_endpoint: str = ""

    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minioadmin"
    # S3/MinIO bucket names must be lowercase (no uppercase allowed), so the
    # requested "audioFiles" becomes "audio-files".
    minio_bucket: str = "audio-files"

    @property
    def database_url(self) -> str:
        if self.database_type == "sqlite":
            return f"sqlite:///./{self.sqlite_file}"

        return f"postgresql+psycopg://{self.postgres_user}:{self.postgres_password}@{self.postgres_host}:{self.postgres_port}/{self.postgres_db}"

    @property
    def minio_endpoint(self) -> str:
        if self.storage_target == "deployment":
            return self.minio_deployment_endpoint

        return self.minio_local_endpoint


@lru_cache
def get_settings() -> Settings:
    return Settings()
