from __future__ import annotations

import re
import time


_CREDENTIAL_ID = re.compile(r"^[A-Z][A-Z0-9_]{0,31}$")
_SESSION_ID = re.compile(r"^[A-Za-z0-9_-]{1,128}$")


class CredentialApprovalRegistry:
    """Short-lived, one-use human approvals for a session/credential pair."""

    def __init__(self, ttl_seconds: int = 90) -> None:
        self.ttl_seconds = max(15, min(300, int(ttl_seconds)))
        self._approvals: dict[tuple[str, str], float] = {}

    @staticmethod
    def _validate(session_id: str, credential_id: str) -> tuple[str, str]:
        if not isinstance(session_id, str) or not _SESSION_ID.fullmatch(session_id):
            raise ValueError("A valid browser session ID is required.")
        if not isinstance(credential_id, str) or not _CREDENTIAL_ID.fullmatch(credential_id):
            raise ValueError("Credential ID must use uppercase letters, digits and underscores.")
        return session_id, credential_id

    def approve(self, session_id: str, credential_id: str) -> dict[str, int | bool]:
        key = self._validate(session_id, credential_id)
        now = time.monotonic()
        self._approvals = {item: expiry for item, expiry in self._approvals.items() if expiry > now}
        self._approvals[key] = now + self.ttl_seconds
        return {"approved": True, "expires_in_seconds": self.ttl_seconds, "uses": 1}

    def consume(self, session_id: str, credential_id: str) -> bool:
        key = self._validate(session_id, credential_id)
        expiry = self._approvals.pop(key, None)
        return expiry is not None and expiry > time.monotonic()
