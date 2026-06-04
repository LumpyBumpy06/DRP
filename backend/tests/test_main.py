from datetime import UTC, datetime, timedelta

import app.main as main_module
from app.main import app
from app.services.tree import DAYS_TO_MATURE, compute_tree_state
from fastapi.testclient import TestClient

client = TestClient(app)

DAY = 30


def _days_ago(now: datetime, n: float) -> datetime:
    return now - timedelta(seconds=n * DAY)


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


# ---------- TREE ----------


def test_tree_fresh_sapling() -> None:
    now = datetime.now(UTC)
    state = compute_tree_state(now, [], [], DAY)
    assert state["growth"] == 0.0
    assert state["leafiness"] == 1.0
    assert state["treeType"] == 0


def test_tree_grows_with_active_days() -> None:
    now = datetime.now(UTC)
    # Checked in on 3 distinct, recent days (incl. now) -> some growth, full leaves.
    ts = [_days_ago(now, 0), _days_ago(now, 1), _days_ago(now, 2)]
    state = compute_tree_state(now, ts, ts, DAY)
    assert 0.0 < state["growth"] < 1.0
    assert state["leafiness"] == 1.0


def test_tree_sheds_leaves_when_norman_misses() -> None:
    now = datetime.now(UTC)
    # Last check-in was several days ago -> leaves drop, but tree has grown.
    ts = [_days_ago(now, 5), _days_ago(now, 6), _days_ago(now, 7)]
    state = compute_tree_state(now, ts, ts, DAY)
    assert state["leafiness"] < 1.0


def test_tree_resets_to_new_species_at_maturity() -> None:
    now = datetime.now(UTC)
    # One full maturity cycle of distinct active days -> species advances, growth resets low.
    ts = [_days_ago(now, i) for i in range(DAYS_TO_MATURE)]
    state = compute_tree_state(now, ts, ts, DAY)
    assert state["treeType"] == 1
    assert state["growth"] < 0.2


def test_tree_endpoint(monkeypatch) -> None:
    monkeypatch.setattr(main_module, "get_okay_timestamps", lambda session, user_id: [])
    response = client.get("/tree")
    assert response.status_code == 200
    body = response.json()
    assert body["growth"] == 0.0
    assert body["leafiness"] == 1.0
