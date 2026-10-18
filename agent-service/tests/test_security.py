import asyncio
import socket

import pytest
import server
from starlette.testclient import TestClient

from server import (
    LLM_TOOLS,
    app,
    _high_impact_target,
    validate_fetch_limit,
    validate_local_cdp_url,
    validate_fetch_urls,
    validate_public_url,
    validate_search_limit,
    validate_search_options,
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
    client = TestClient(app)
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
    client = TestClient(app)
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
    client = TestClient(app)
    response = client.get("/mcp")
    assert response.status_code == 503
    assert "NANOU_AGENT_API_KEY" in response.json()["error"]


def test_mcp_control_endpoint_rejects_wrong_bearer_token(monkeypatch):
    monkeypatch.setenv("NANOU_AGENT_API_KEY", "test-only-token")
    client = TestClient(app)
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


@pytest.mark.parametrize(
    "urls",
    [
        [],
        ["https://example.com"] * 11,
        "https://example.com",
        ["file:///etc/passwd"],
        ["http://127.0.0.1"],
        ["https://user:pass@example.com"],
    ],
)
def test_fetch_many_rejects_invalid_url_lists(urls):
    with pytest.raises(ValueError):
        validate_fetch_urls(urls)


def test_fetch_many_accepts_one_to_ten_public_http_urls():
    urls = ["https://example.com", "https://example.org/path"]
    assert validate_fetch_urls(urls) == urls


@pytest.mark.parametrize(
    "url",
    [
        "https://127.0.0.1:9223",
        "http://192.168.1.8:9223",
        "http://0.0.0.0:9223",
        "http://user@localhost:9223",
        "http://localhost",
        "http://localhost:99999",
        "http://localhost:9223/json",
        "http://localhost:9223/?token=secret",
    ],
)
def test_cdp_url_rejects_nonlocal_or_ambiguous_endpoints(url):
    with pytest.raises(ValueError):
        validate_local_cdp_url(url)


def test_cdp_url_accepts_loopback_only():
    assert validate_local_cdp_url("http://127.0.0.1:9223") == "http://127.0.0.1:9223"
    assert validate_local_cdp_url("http://localhost:9223/") == "http://localhost:9223"
    assert validate_local_cdp_url("http://[::1]:9223") == "http://[::1]:9223"


def test_autonomous_runner_exposes_batch_fetch():
    names = {tool["function"]["name"] for tool in LLM_TOOLS}
    assert "fetch_many" in names


@pytest.mark.parametrize(
    ("recency", "category"),
    [
        ("minute", "web"),
        ("any", "social"),
        ("", "news"),
        ("year", ""),
    ],
)
def test_search_options_reject_unsupported_filters(recency, category):
    with pytest.raises(ValueError):
        validate_search_options(recency, category)


def test_search_options_normalize_recency_and_category():
    assert validate_search_options(" WEEK ", "News") == ("week", "news")
    assert validate_search_options("any", "research") == ("any", "research")


def test_public_dns_decision_is_not_cached(monkeypatch):
    answers = []

    def fake_getaddrinfo(host, _port, _family, _socktype):
        answers.append(host)
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("8.8.8.8", 0))]

    monkeypatch.setattr(server.socket, "getaddrinfo", fake_getaddrinfo)
    server.HOST_CACHE.pop("nanou-test-public.invalid", None)
    assert asyncio.run(server._host_is_public("nanou-test-public.invalid")) is True
    assert asyncio.run(server._host_is_public("nanou-test-public.invalid")) is True
    assert answers == ["nanou-test-public.invalid", "nanou-test-public.invalid"]
    assert "nanou-test-public.invalid" not in server.HOST_CACHE


def test_close_all_disconnects_without_closing_attached_cdp_browser():
    from server import BrowserPool

    class FakeBrowser:
        closed = False

        def is_connected(self):
            return True

        async def close(self):
            self.closed = True

    class FakePlaywright:
        stopped = False

        async def stop(self):
            self.stopped = True

    pool = BrowserPool()
    browser = FakeBrowser()
    playwright = FakePlaywright()
    pool._browser = browser
    pool._playwright = playwright
    pool._using_cdp = True
    asyncio.run(pool.close_all())
    assert browser.closed is False
    assert playwright.stopped is True
    assert pool._using_cdp is False


def test_fetch_uses_ephemeral_context_without_evicting_live_sessions(monkeypatch):
    import server

    class FakePage:
        url = "https://example.com/"
        async def goto(self, *_args, **_kwargs):
            return None
        async def wait_for_timeout(self, _ms):
            return None

    class FakeContext:
        def __init__(self):
            self.closed = False
            self.page = FakePage()
        async def route(self, *_args, **_kwargs):
            return None
        async def new_page(self):
            return self.page
        async def close(self):
            self.closed = True

    class FakeBrowser:
        def __init__(self, context):
            self.context = context
        async def new_context(self, **_kwargs):
            return self.context

    context = FakeContext()
    async def fake_assert(url):
        return url
    async def fake_snapshot(_page):
        return {
            "url": "https://example.com/",
            "title": "Example Domain",
            "text": "x" * 1000,
            "elements": [
                {"tag": "a", "text": "Example", "href": "https://example.com/"},
            ],
            "truncated": False,
        }
    async def fake_browser():
        return FakeBrowser(context)

    monkeypatch.setattr(server, "_assert_public_url", fake_assert)
    monkeypatch.setattr(server.pool, "_ensure_browser", fake_browser)
    monkeypatch.setattr(server.pool, "_snapshot_page", fake_snapshot)
    monkeypatch.setattr(
        server.pool,
        "create",
        lambda _url: pytest.fail("Fetch must not evict/use a persistent browser session."),
    )

    result = asyncio.run(server.fetch_public_page("https://example.com/", 500))

    assert result["title"] == "Example Domain"
    assert len(result["content"]) == 500
    assert result["truncated"] is True
    assert result["links"][0]["url"] == "https://example.com/"
    assert context.closed is True


def test_press_blocks_high_impact_link_without_form(monkeypatch):
    from server import pool

    class FakePage:
        async def wait_for_timeout(self, _milliseconds):
            return None

    class FakeLocator:
        async def evaluate(self, script):
            # Simulate the actual DOM script: the old implementation returned
            # an empty label when the activated link was not inside a form.
            return "Pay now" if "const label = [" in script else ""

        async def press(self, *_args, **_kwargs):
            pytest.fail("Enter must not activate a high-impact link.")

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

    result = asyncio.run(pool.press("test-session", "css:a.buy", "Enter"))

    assert result["blocked"] is True
    assert "consequential action" in result["reason"]


def test_search_uses_temporary_context_without_evicting_live_sessions(monkeypatch):
    import server

    class FakeLocator:
        async def evaluate_all(self, _script):
            return [
                {
                    "title": "Example result",
                    "url": "https://example.org/article",
                    "snippet": "A source snippet.",
                },
            ]

    class FakePage:
        async def goto(self, *_args, **_kwargs):
            return None
        async def wait_for_timeout(self, _milliseconds):
            return None
        def locator(self, _selector):
            return FakeLocator()

    class FakeContext:
        closed = False
        def __init__(self):
            self.page = FakePage()
        async def route(self, *_args, **_kwargs):
            return None
        async def new_page(self):
            return self.page
        async def close(self):
            self.closed = True

    class FakeBrowser:
        def __init__(self, context):
            self.context = context
        async def new_context(self, **_kwargs):
            return self.context

    context = FakeContext()
    browser = FakeBrowser(context)

    async def fake_assert(url):
        return url

    async def fake_browser():
        return browser

    monkeypatch.setattr(server, "_assert_public_url", fake_assert)
    monkeypatch.setattr(server.pool, "_ensure_browser", fake_browser)
    monkeypatch.setattr(
        server.pool,
        "create",
        lambda _url: pytest.fail("Search must not allocate/evict a persistent browser session."),
    )

    result = asyncio.run(server.search_public_web("test query", 3))

    assert result["result_count"] == 1
    assert result["results"][0]["url"] == "https://example.org/article"
    assert context.closed is True


def test_max_sessions_setting_is_bounded(monkeypatch):
    from server import _max_sessions

    monkeypatch.setenv("NANOU_MAX_SESSIONS", "2")
    assert _max_sessions() == 2

    monkeypatch.setenv("NANOU_MAX_SESSIONS", "99")
    assert _max_sessions() == 8

    monkeypatch.setenv("NANOU_MAX_SESSIONS", "not-an-int")
    assert _max_sessions() == 8
