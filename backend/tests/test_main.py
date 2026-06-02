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
    response = client.post("/voice", content=audio)

    assert response.status_code == 200
    body = response.json()
    assert body["bytes"] == len(audio)
    assert body["object"].endswith(".m4a")
    assert captured["bytes"] == len(audio)
