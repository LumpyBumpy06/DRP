from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)


def test_root() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_voice_accepts_multipart_m4a() -> None:
    files = {"file": ("voice.m4a", b"\x00\x01\x02fake-audio", "audio/mp4")}
    response = client.post("/voice", files=files)
    assert response.status_code == 200
