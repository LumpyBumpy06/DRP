import io

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
            secure=settings.minio_secure,
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


def latest_object_name(settings: Settings, prefix: str) -> str | None:
    """Newest object stored under `prefix` (keys are timestamp-ordered)."""
    client = get_storage(settings)
    objects = client.list_objects(settings.minio_bucket, prefix=prefix, recursive=True)
    names: list[str] = [o.object_name for o in objects if o.object_name]
    if not names:
        return None
    return max(names)


def download_audio(settings: Settings, object_name: str) -> bytes:
    client = get_storage(settings)
    response = client.get_object(settings.minio_bucket, object_name)
    try:
        return response.read()
    finally:
        response.close()
        response.release_conn()
