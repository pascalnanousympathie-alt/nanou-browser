import pytest
from starlette.testclient import TestClient

from server import app, _high_impact_target, validate_public_url


def test_accepts_public_http_urls():
    assert validate_public_url("https://example.com/path?q=1") == "https://example.com/path?q=1"
    assert validate_public_url("http://example.org/") == "http://example.org/"


@pytest.mark.parametrize(
    "url",
    [
        "file:///etc/passwd",
        "javascript:alert(1)",
        "http://localhost:9223/json",
        "https://sub.localhost/",
        "http://127.0.0.1/",
        "http://192.168.1.10/",
        "http://169.254.169.254/latest/meta-data/",
        "http://[::1]/",
        "https://user:password@example.com/",
        "https://example.com/" + ("x" * 2050),
    ],
)
def test_rejects_unsafe_urls(url):
    with pytest.raises(ValueError):
        validate_public_url(url)


def test_flags_consequential_click_targets():
    assert _high_impact_target("Pay now")
    assert _high_impact_target("Delete account")
    assert _high_impact_target("Confirm transfer")
    assert not _high_impact_target("Open account settings")
    assert not _high_impact_target("Read documentation")


def test_health_is_public_but_control_api_fails_closed(monkeypatch):
    monkeypatch.delenv("NANOU_AGENT_API_KEY", raising=False)
    with TestClient(app) as client:
        assert client.get("/healthz").status_code == 200
        assert client.get("/").status_code == 200
        response = client.post(
            "/api/agent/runs",
            json={"goal": "find a contact email", "start_url": "https://example.com"},
        )
        assert response.status_code == 503
        assert "NANOU_AGENT_API_KEY" in response.json()["error"]


def test_control_api_requires_bearer_auth(monkeypatch):
    monkeypatch.setenv("NANOU_AGENT_API_KEY", "test-only-token")
    with TestClient(app) as client:
        response = client.post(
            "/api/agent/runs",
            json={"goal": "find a contact email", "start_url": "https://example.com"},
        )
        assert response.status_code == 401
        assert response.headers["www-authenticate"] == "Bearer"
        response = client.post(
            "/api/agent/runs",
            headers={"Authorization": "Bearer test-only-token"},
            json={"goal": "find a contact email", "start_url": "https://example.com"},
        )
        # Authentication passes; autonomous execution is then rejected because no
        # LLM key is configured, proving that the API is not accidentally open.
        assert response.status_code == 503
        assert "NANOU_LLM_API_KEY" in response.json()["error"]

def test_mcp_control_endpoint_fails_closed_without_token(monkeypatch):
    monkeypatch.delenv("NANOU_AGENT_API_KEY", raising=False)
    with TestClient(app) as client:
        response = client.get("/mcp")
        assert response.status_code == 503
        assert "NANOU_AGENT_API_KEY" in response.json()["error"]


def test_mcp_control_endpoint_rejects_wrong_bearer_token(monkeypatch):
    monkeypatch.setenv("NANOU_AGENT_API_KEY", "test-only-token")
    with TestClient(app) as client:
        response = client.get(
            "/mcp",
            headers={"Authorization": "Bearer incorrect-token"},
        )
        assert response.status_code == 401
        assert response.headers["www-authenticate"] == "Bearer"
