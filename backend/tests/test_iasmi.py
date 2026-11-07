"""Iasmi gratuit (fără cheie AI): răspunsuri din datele aplicației și din ghidul pentru Arad."""
import pytest


@pytest.fixture(autouse=True)
def _no_ai(monkeypatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)
    monkeypatch.delenv("ANTHROPIC_AUTH_TOKEN", raising=False)


def _ask(client, auth, text, **extra):
    r = client.post("/iasmi", json={"messages": [{"role": "user", "content": text}], **extra}, headers=auth)
    assert r.status_code == 200, r.text
    return r.json()


def test_iasmi_gratuit_intrebari(client, auth):
    assert "ghiseul.ro" in _ask(client, auth, "Cum plătesc impozitul pe casă?")["reply"]
    assert "112" in _ask(client, auth, "e un accident, cineva e rănit")["reply"]
    cafe = _ask(client, auth, "unde beau o cafea buna?", lat=46.18, lng=21.31)
    assert cafe["places"] and all(p["category"] == "Cafenele" for p in cafe["places"]) and cafe["ai"] is False
    drum = _ask(client, auth, "Cum ajung la Cetatea Aradului?", lat=46.18, lng=21.31)
    assert drum["places"][0]["name"] == "Cetatea Aradului" and "Du-mă acolo" in drum["reply"]
    assert "Semnalează o problemă" in _ask(client, auth, "cum semnalez o groapă?")["reply"]
    assert "semnal" in _ask(client, auth, "ce probleme s-au semnalat azi?")["reply"]
    assert "Bună" in _ask(client, auth, "salut")["reply"]


def test_iasmi_validare(client, auth):
    assert client.post("/iasmi", json={"messages": [{"role": "user", "content": "salut"}]}).status_code == 401
    bad = client.post("/iasmi", json={"messages": [{"role": "assistant", "content": "x"}]}, headers=auth)
    assert bad.status_code == 400
