import asyncio
import json
import pytest

import server
from credential_vault import (
    BrowserCredential,
    CredentialConfigurationError,
    list_browser_credentials,
    load_browser_credential,
    normalize_https_origin,
)


def _env():
    return {
        "NANOU_CREDENTIAL_GITHUB_ORIGIN": "https://github.com/",
        "NANOU_CREDENTIAL_GITHUB_USERNAME": "nanou-user@example.test",
        "NANOU_CREDENTIAL_GITHUB_PASSWORD": "never-return-this-password",
        "NANOU_CREDENTIAL_GITHUB_USERNAME_SELECTOR": "input[name='login']",
        "NANOU_CREDENTIAL_GITHUB_PASSWORD_SELECTOR": "input[name='password']",
    }


def test_credential_metadata_never_contains_secret_values():
    env = _env()
    metadata = list_browser_credentials(env)
    serialized = json.dumps(metadata)
    assert metadata == [
        {
            "credential_id": "GITHUB",
            "configured": True,
            "origin": "https://github.com",
            "kind": "web-login",
        }
    ]
    assert "nanou-user@example.test" not in serialized
    assert "never-return-this-password" not in serialized
    assert "input[name=" not in serialized


def test_credential_repr_does_not_expose_password_or_username():
    credential = load_browser_credential("GITHUB", _env())
    rendered = repr(credential)
    assert "never-return-this-password" not in rendered
    assert "nanou-user@example.test" not in rendered
    assert credential.credential_id == "GITHUB"

def test_credential_values_preserve_intentional_whitespace():
    env = _env()
    env["NANOU_CREDENTIAL_GITHUB_PASSWORD"] = "  password with spaces  "
    credential = load_browser_credential("GITHUB", env)
    assert credential.password == "  password with spaces  "


def test_credential_requires_all_values_and_safe_origin():
    env = _env()
    env.pop("NANOU_CREDENTIAL_GITHUB_PASSWORD")
    with pytest.raises(CredentialConfigurationError, match="incomplete"):
        load_browser_credential("GITHUB", env)


@pytest.mark.parametrize(
    "origin",
    [
        "http://example.com",
        "https://example.com/login",
        "https://user:pass@example.com",
        "https://localhost",
        "https://127.0.0.1",
        "https://169.254.169.254",
        "https://example.com?token=abc",
        "https://example.com#fragment",
    ],
)
def test_rejects_unsafe_credential_origins(origin):
    with pytest.raises(CredentialConfigurationError):
        normalize_https_origin(origin)


def test_normalizes_https_origin_and_default_port():
    assert normalize_https_origin("https://GitHub.com:443/") == "https://github.com"
    assert normalize_https_origin("https://example.com:8443") == "https://example.com:8443"


def test_incomplete_credentials_are_not_reported_as_configured():
    env = _env()
    env.pop("NANOU_CREDENTIAL_GITHUB_PASSWORD")
    assert list_browser_credentials(env) == [
        {"credential_id": "GITHUB", "configured": False}
    ]


def test_rejects_non_css_or_unbounded_selectors():
    env = _env()
    env["NANOU_CREDENTIAL_GITHUB_PASSWORD_SELECTOR"] = "javascript:alert(1)"
    with pytest.raises(CredentialConfigurationError, match="CSS"):
        load_browser_credential("GITHUB", env)
    env["NANOU_CREDENTIAL_GITHUB_PASSWORD_SELECTOR"] = "x" * 301
    with pytest.raises(CredentialConfigurationError, match="short"):
        load_browser_credential("GITHUB", env)


def test_saved_login_fill_requires_exact_origin_and_never_submits(monkeypatch):
    credential = BrowserCredential(
        credential_id="GITHUB",
        origin="https://github.com",
        username="user-for-test",
        password="password-for-test",
        username_selector="input[name='login']",
        password_selector="input[name='password']",
    )
    monkeypatch.setattr(server, "load_browser_credential", lambda _credential_id: credential)

    class FakeLocator:
        def __init__(self, field_type):
            self.field_type = field_type
            self.values = []

        async def count(self):
            return 1

        async def evaluate(self, _script):
            return {"tag": "input", "type": self.field_type}

        async def is_visible(self):
            return True

        async def is_enabled(self):
            return True

        async def fill(self, value, timeout):
            assert timeout == 8000
            self.values.append(value)

    class FakePage:
        url = "https://github.com/login"

        def __init__(self):
            self.username = FakeLocator("text")
            self.password = FakeLocator("password")

        def locator(self, selector):
            if selector == credential.username_selector:
                return self.username
            if selector == credential.password_selector:
                return self.password
            raise AssertionError("Unexpected selector")

    class FakeSession:
        def __init__(self):
            self.lock = asyncio.Lock()
            self.page = FakePage()

    session = FakeSession()
    async def assert_public(_url):
        return _url

    monkeypatch.setattr(server, "_assert_public_url", assert_public)
    monkeypatch.setattr(server.pool, "get", lambda _session_id: session)
    result = asyncio.run(server._fill_saved_login("session-1", "GITHUB"))

    assert result["username_filled"] is True
    assert result["password_filled"] is True
    assert result["submitted"] is False
    assert session.page.username.values == ["user-for-test"]
    assert session.page.password.values == ["password-for-test"]
    safe_output = json.dumps(result)
    assert "user-for-test" not in safe_output
    assert "password-for-test" not in safe_output


def test_saved_login_fill_refuses_another_origin(monkeypatch):
    credential = BrowserCredential(
        credential_id="GITHUB",
        origin="https://github.com",
        username="user-for-test",
        password="password-for-test",
        username_selector="input[name='login']",
        password_selector="input[name='password']",
    )
    monkeypatch.setattr(server, "load_browser_credential", lambda _credential_id: credential)

    class FakePage:
        url = "https://evil.example/login"
        def locator(self, _selector):
            pytest.fail("Credential must not be filled on another origin.")

    class FakeSession:
        def __init__(self):
            self.lock = asyncio.Lock()
            self.page = FakePage()

    monkeypatch.setattr(server.pool, "get", lambda _session_id: FakeSession())
    with pytest.raises(ValueError, match="allowlisted origin"):
        asyncio.run(server._fill_saved_login("session-1", "GITHUB"))
