import app.main as main_module
from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)


def test_root() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_voice_uploads_to_storage(monkeypatch) -> None:
    captured = {}

    def fake_upload(settings, data, object_name, content_type="audio/mp4"):
        captured["bytes"] = len(data)
        captured["object"] = object_name

    # Avoid hitting a real MinIO in the test/CI environment.
    monkeypatch.setattr(main_module, "upload_audio", fake_upload)

    audio = b"\x00\x01\x02fake-audio"
    files = {"file": ("voice.m4a", audio, "audio/mp4")}
    response = client.post("/voice", files=files)

    assert response.status_code == 200
    body = response.json()
    assert body["bytes"] == len(audio)
    assert body["object"] == "voice.m4a"
    assert captured["bytes"] == len(audio)


def test_voice_latest_streams_audio(monkeypatch) -> None:
    monkeypatch.setattr(main_module, "latest_recent_object_name", lambda settings, prefix, max_age: "1/123.m4a")
    monkeypatch.setattr(main_module, "download_audio", lambda settings, name: b"AUDIO-BYTES")

    response = client.get("/voice/latest", params={"user_id": 1})

    assert response.status_code == 200
    assert response.content == b"AUDIO-BYTES"
    assert response.headers["content-type"] == "audio/mp4"


def test_voice_latest_404_when_expired_or_missing(monkeypatch) -> None:
    # latest_recent_object_name returns None for both "nothing there" and "too old".
    monkeypatch.setattr(main_module, "latest_recent_object_name", lambda settings, prefix, max_age: None)

    response = client.get("/voice/latest", params={"user_id": 99})

    assert response.status_code == 404
