from __future__ import annotations

import ipaddress
import os
import re
from dataclasses import dataclass, field
from urllib.parse import urlsplit


_CREDENTIAL_ID = re.compile(r"^[A-Z][A-Z0-9_]{0,31}$")
_ENV_KEY = re.compile(r"^NANOU_CREDENTIAL_([A-Z][A-Z0-9_]*)_(ORIGIN|USERNAME|PASSWORD|USERNAME_SELECTOR|PASSWORD_SELECTOR)$")


class CredentialConfigurationError(ValueError):
    """A saved browser credential is absent or unsafe; never includes secret values."""


@dataclass(frozen=True)
class BrowserCredential:
    credential_id: str
    origin: str
    username: str = field(repr=False)
    password: str = field(repr=False)
    username_selector: str = field(repr=False)
    password_selector: str = field(repr=False)


def normalize_https_origin(value: str) -> str:
    """Return a strict HTTPS origin; paths, credentials, query strings and fragments are forbidden."""
    if not isinstance(value, str) or not value or len(value) > 512:
        raise CredentialConfigurationError("Credential origin must be a valid HTTPS origin.")
    parts = urlsplit(value.strip())
    host = (parts.hostname or "").lower().rstrip(".")
    if (
        parts.scheme.lower() != "https"
        or not host
        or parts.username
        or parts.password
        or parts.path not in ("", "/")
        or parts.query
        or parts.fragment
    ):
        raise CredentialConfigurationError("Credential origin must be a strict HTTPS origin without a path or credentials.")
    if host in {"localhost", "localhost.localdomain"} or host.endswith((".localhost", ".local", ".internal", ".test")):
        raise CredentialConfigurationError("Local and internal credential origins are not allowed.")
    try:
        ip = ipaddress.ip_address(host)
    except ValueError:
        pass
    else:
        if not ip.is_global:
            raise CredentialConfigurationError("Private and reserved credential origins are not allowed.")
    try:
        port = parts.port
    except ValueError as exc:
        raise CredentialConfigurationError("Credential origin has an invalid port.") from exc
    if port is not None and not 1 <= port <= 65535:
        raise CredentialConfigurationError("Credential origin has an invalid port.")
    rendered_host = f"[{host}]" if ":" in host else host
    return f"https://{rendered_host}" + (f":{port}" if port and port != 443 else "")


def _read_required(env: dict[str, str] | os._Environ[str], key: str) -> str:
    value = str(env.get(key, ""))
    if not value.strip():
        raise CredentialConfigurationError("Saved credential is incomplete or not configured.")
    return value.strip() if key.endswith(("_ORIGIN", "_USERNAME_SELECTOR", "_PASSWORD_SELECTOR")) else value


def load_browser_credential(
    credential_id: str,
    environ: dict[str, str] | os._Environ[str] | None = None,
) -> BrowserCredential:
    """Load one environment-backed credential. Secret fields are never part of public metadata."""
    if not isinstance(credential_id, str) or not _CREDENTIAL_ID.fullmatch(credential_id):
        raise CredentialConfigurationError("Credential ID must use uppercase letters, digits and underscores.")
    env = os.environ if environ is None else environ
    prefix = f"NANOU_CREDENTIAL_{credential_id}_"
    origin = normalize_https_origin(_read_required(env, prefix + "ORIGIN"))
    username = _read_required(env, prefix + "USERNAME")
    password = _read_required(env, prefix + "PASSWORD")
    username_selector = _read_required(env, prefix + "USERNAME_SELECTOR")
    password_selector = _read_required(env, prefix + "PASSWORD_SELECTOR")
    for selector in (username_selector, password_selector):
        if len(selector) > 300 or "\n" in selector or "\r" in selector:
            raise CredentialConfigurationError("Credential field selectors must be short, single-line CSS selectors.")
        if selector.lower().startswith(("javascript:", "xpath=", "text=", "role=")):
            raise CredentialConfigurationError("Credential selectors must be CSS selectors.")
    return BrowserCredential(
        credential_id=credential_id,
        origin=origin,
        username=username,
        password=password,
        username_selector=username_selector,
        password_selector=password_selector,
    )


def list_browser_credentials(environ: dict[str, str] | os._Environ[str] | None = None) -> list[dict[str, str | bool]]:
    """Return safe metadata only; never return usernames, passwords or selector values."""
    env = os.environ if environ is None else environ
    identifiers = sorted(
        {
            match.group(1)
            for key in env
            if (match := _ENV_KEY.fullmatch(key))
        }
    )
    result: list[dict[str, str | bool]] = []
    for credential_id in identifiers:
        try:
            credential = load_browser_credential(credential_id, env)
        except CredentialConfigurationError:
            result.append({"credential_id": credential_id, "configured": False})
        else:
            result.append(
                {
                    "credential_id": credential.credential_id,
                    "configured": True,
                    "origin": credential.origin,
                    "kind": "web-login",
                }
            )
    return result
