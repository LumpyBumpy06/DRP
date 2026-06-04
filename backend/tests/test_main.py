import app.main as main_module
from app.main import app
from app.models import User
from fastapi.testclient import TestClient

client = TestClient(app)


class _FakeSession:
    """Minimal stand-in for the request session — only `.get` is used here."""

    def get(self, model, key):
        return User(user_id=key, token=f"token-{key}")


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


def test_emergency_alerts_linked_users(monkeypatch) -> None:
    sent: list[tuple[str, str, str | None]] = []
    raised: list[int] = []

    monkeypatch.setattr(main_module, "raise_emergency", lambda session, sender_id: raised.append(sender_id))
    monkeypatch.setattr(main_module, "get_linked_users", lambda session, user_id: [2])
    monkeypatch.setattr(
        main_module,
        "send_notification",
        lambda token, message, message_type=None: sent.append((token, message, message_type)),
    )

    app.dependency_overrides[main_module.SessionDep] = lambda: _FakeSession()
    try:
        response = client.post("/emergency", params={"user_id": 1})
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200
    assert response.json() == {"ok": True}

    # The SOS is persisted for the poll fallback...
    assert raised == [1]
    # ...and Norman's (user 1) carer is alerted exactly once, with the EMERGENCY type.
    assert len(sent) == 1
    token, message, message_type = sent[0]
    assert token == "token-2"
    assert message_type == "EMERGENCY"
    assert "Norman" in message


def test_emergency_active_status(monkeypatch) -> None:
    monkeypatch.setattr(main_module, "active_emergency_sender_for", lambda session, user_id: 1)
    response = client.get("/emergency/active", params={"user_id": 2})
    assert response.status_code == 200
    assert response.json() == {"active": True, "sender": "Norman"}

    monkeypatch.setattr(main_module, "active_emergency_sender_for", lambda session, user_id: None)
    response = client.get("/emergency/active", params={"user_id": 2})
    assert response.status_code == 200
    assert response.json() == {"active": False, "sender": None}


def test_emergency_ack_clears(monkeypatch) -> None:
    cleared: list[int] = []
    monkeypatch.setattr(main_module, "clear_emergencies_for", lambda session, user_id: cleared.append(user_id))

    response = client.post("/emergency/ack", params={"user_id": 2})

    assert response.status_code == 200
    assert response.json() == {"ok": True}
    assert cleared == [2]
