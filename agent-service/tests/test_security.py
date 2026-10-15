import pytest
from starlette.testclient import TestClient

from server import (
    LLM_TOOLS,
    app,
    _high_impact_target,
    validate_fetch_limit,
    validate_public_url,
    validate_search_limit,
    validate_search_query,
)


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


def test_click_blocks_high_impact_element_even_when_css_selector_hides_label(monkeypatch):
    import asyncio

    from server import pool

    class FakePage:
        async def wait_for_timeout(self, _milliseconds):
            return None

    class FakeLocator:
        async def evaluate(self, _script):
            return "Pay now"

        async def click(self, **_kwargs):
            pytest.fail("A consequential button must not be clicked.")

    class FakeSession:
        def __init__(self):
            self.lock = asyncio.Lock()
            self.page = FakePage()

    session = FakeSession()
    locator = FakeLocator()

    async def fake_locator(_page, _target, _action):
        return locator

    monkeypatch.setattr(pool, "get", lambda _session_id: session)
    monkeypatch.setattr(pool, "_locator", fake_locator)

    result = asyncio.run(pool.click("test-session", "css:button.primary"))

    assert result["blocked"] is True
    assert "consequential action" in result["reason"]


def test_press_blocks_enter_when_form_contains_high_impact_submit(monkeypatch):
    import asyncio

    from server import pool

    class FakePage:
        async def wait_for_timeout(self, _milliseconds):
            return None

    class FakeLocator:
        async def evaluate(self, _script):
            return "button Pay now"

        async def press(self, *_args, **_kwargs):
            pytest.fail("Enter must not submit a consequential form.")

    class FakeSession:
        def __init__(self):
            self.lock = asyncio.Lock()
            self.page = FakePage()

    session = FakeSession()
    locator = FakeLocator()

    async def fake_locator(_page, _target, _action):
        return locator

    monkeypatch.setattr(pool, "get", lambda _session_id: session)
    monkeypatch.setattr(pool, "_locator", fake_locator)

    result = asyncio.run(pool.press("test-session", "Email", "Enter"))

    assert result["blocked"] is True
    assert "consequential action" in result["reason"]



def test_search_query_is_trimmed_and_bounded():
    assert validate_search_query("  Madagascar news  ") == "Madagascar news"
    with pytest.raises(ValueError):
        validate_search_query("   ")
    with pytest.raises(ValueError):
        validate_search_query("x" * 501)


@pytest.mark.parametrize("value", [0, 11, True, "5"])
def test_search_result_limit_rejects_invalid_values(value):
    with pytest.raises(ValueError):
        validate_search_limit(value)


def test_search_result_limit_accepts_supported_values():
    assert validate_search_limit(1) == 1
    assert validate_search_limit(10) == 10


@pytest.mark.parametrize("value", [499, 12001, True, "12000"])
def test_fetch_text_limit_rejects_invalid_values(value):
    with pytest.raises(ValueError):
        validate_fetch_limit(value)


def test_fetch_text_limit_accepts_supported_values():
    assert validate_fetch_limit(500) == 500
    assert validate_fetch_limit(12000) == 12000


def test_autonomous_runner_has_search_and_fetch_tools():
    names = {
        tool["function"]["name"]
        for tool in LLM_TOOLS
    }
    assert {"search", "fetch", "navigate", "snapshot", "click", "fill"} <= names
