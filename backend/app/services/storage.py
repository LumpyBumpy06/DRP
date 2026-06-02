import io
from datetime import UTC, datetime, timedelta

from minio import Minio

from app.settings import Settings

_client: Minio | None = None


def get_storage(settings: Settings) -> Minio:
    """Return a shared MinIO client, creating it (and the bucket) on first use.

    Initialisation is lazy so the app can boot in environments where the block
    store isn't reachable yet — only `/voice` fails, not the whole service.
    """
    global _client

    if _client is None:
        client = Minio(
            settings.minio_endpoint,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            secure=False,
        )

        if not client.bucket_exists(settings.minio_bucket):
            client.make_bucket(settings.minio_bucket)

        _client = client

    return _client


def upload_audio(
    settings: Settings,
    data: bytes,
    object_name: str,
    content_type: str = "audio/mp4",
) -> None:
    client = get_storage(settings)
    client.put_object(
        bucket_name=settings.minio_bucket,
        object_name=object_name,
        data=io.BytesIO(data),
        length=len(data),
        content_type=content_type,
    )


def latest_recent_object_name(settings: Settings, prefix: str, max_age_seconds: int) -> str | None:
    """Newest object under `prefix`, but only if stored within `max_age_seconds`.

    Returns None when nothing is there or the newest clip has expired, so callers
    can treat an old message as gone.
    """
    client = get_storage(settings)
    objects = [o for o in client.list_objects(settings.minio_bucket, prefix=prefix, recursive=True) if o.object_name and o.last_modified]
    if not objects:
        return None

    latest = max(objects, key=lambda o: o.last_modified)
    if datetime.now(UTC) - latest.last_modified > timedelta(seconds=max_age_seconds):
        return None

    name: str | None = latest.object_name
    return name


def download_audio(settings: Settings, object_name: str) -> bytes:
    client = get_storage(settings)
    response = client.get_object(settings.minio_bucket, object_name)
    try:
        return response.read()
    finally:
        response.close()
        response.release_conn()
