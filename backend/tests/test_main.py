from app.main import app
from fastapi.testclient import TestClient

client = TestClient(app)


def test_root() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_voice_prints_binary() -> None:
    audio = b"\x00\x01\x02hello"
    response = client.post("/voice", content=audio)
    assert response.status_code == 200
    assert response.json() == {"received_bytes": len(audio)}
