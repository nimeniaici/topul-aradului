"""Iasmi: fără cheie AI pe server răspunde în modul simplu (fără să cheme API-ul)."""


def _ask(client, auth, text, **extra):
    return client.post("/iasmi", json={"messages": [{"role": "user", "content": text}], **extra}, headers=auth)


def test_iasmi_mod_simplu(client, auth, monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_AUTH_TOKEN", raising=False)
    r = _ask(client, auth, "Cum plătesc impozitul pe casă?")
    assert r.status_code == 200 and r.json()["ai"] is False and "ghiseul.ro" in r.json()["reply"]
    r = _ask(client, auth, "vreau o pizza", lat=46.18, lng=21.31)
    assert r.json()["places"] and "Uite ce am găsit" in r.json()["reply"]
    assert "112" in _ask(client, auth, "e un accident, pericol").json()["reply"]


def test_iasmi_validare(client, auth):
    assert client.post("/iasmi", json={"messages": [{"role": "user", "content": "salut"}]}).status_code == 401
    bad = client.post("/iasmi", json={"messages": [{"role": "assistant", "content": "x"}]}, headers=auth)
    assert bad.status_code == 400
