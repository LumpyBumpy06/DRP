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


def latest_recent_object_name(settings: Settings, prefix: str | list[str], max_age_seconds: int) -> str | None:
    """Newest object under `prefix` (or any of several prefixes), but only if
    stored within `max_age_seconds`.

    Returns None when nothing is there or the newest clip has expired, so callers
    can treat an old message as gone.
    """
    client = get_storage(settings)
    prefixes = [prefix] if isinstance(prefix, str) else prefix
    objects = [o for p in prefixes for o in client.list_objects(settings.minio_bucket, prefix=p, recursive=True) if o.object_name and o.last_modified]
    if not objects:
        return None

    latest = max(objects, key=lambda o: o.last_modified)
    if datetime.now(UTC) - latest.last_modified > timedelta(seconds=max_age_seconds):
        return None

    name: str | None = latest.object_name
    return name


def recent_object_names(settings: Settings, prefix: str | list[str], max_age_seconds: int) -> list[str]:
    """Every object under `prefix` (or any of several prefixes) stored within
    `max_age_seconds`, newest first.

    Like [latest_recent_object_name] but returns the whole recent run, so the
    viewer can page back through snaps/clips that arrived back-to-back, not just
    the single newest one.
    """
    client = get_storage(settings)
    prefixes = [prefix] if isinstance(prefix, str) else prefix
    now = datetime.now(UTC)
    objects = [
        o
        for p in prefixes
        for o in client.list_objects(settings.minio_bucket, prefix=p, recursive=True)
        if o.object_name and o.last_modified and now - o.last_modified <= timedelta(seconds=max_age_seconds)
    ]
    objects.sort(key=lambda o: o.last_modified, reverse=True)
    return [o.object_name for o in objects]


def list_objects(settings: Settings) -> list[tuple[str, datetime]]:
    """Every stored object (voice clips + snaps) with its upload time."""
    client = get_storage(settings)
    return [(o.object_name, o.last_modified) for o in client.list_objects(settings.minio_bucket, recursive=True) if o.object_name and o.last_modified]


def download_audio(settings: Settings, object_name: str) -> bytes:
    client = get_storage(settings)
    response = client.get_object(settings.minio_bucket, object_name)
    try:
        return response.read()
    finally:
        response.close()
        response.release_conn()
