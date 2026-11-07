import os
import sys
import tempfile
from pathlib import Path

import pytest

_tmp = Path(tempfile.mkdtemp(prefix="topul_test_"))
os.environ["DATABASE_URL"] = f"sqlite:///{_tmp / 'test.db'}"
os.environ["UPLOAD_DIR"] = str(_tmp / "uploads")
os.environ["SKIP_DISTANCE_CHECK"] = "false"
os.environ["CHECKIN_COOLDOWN_MIN"] = "30"
os.environ["CTP_LIVE"] = "0"
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402


@pytest.fixture(scope="session")
def client():
    with TestClient(app) as c:
        yield c


_counter = 0


@pytest.fixture
def auth(client):
    """Creează un utilizator nou și returnează headerele de autentificare."""
    global _counter
    _counter += 1
    r = client.post("/auth/register", json={
        "name": f"tester{_counter}", "email": f"t{_counter}@test.ro", "password": "parola123"})
    assert r.status_code == 201, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}
