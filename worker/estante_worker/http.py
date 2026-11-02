"""A small JSON-over-HTTP helper that tells "not found" apart from "try again later"."""

from __future__ import annotations

from typing import Any, Protocol

import requests

USER_AGENT = "Estante/0.1 (personal second-hand book catalogue)"


class SourceUnavailable(Exception):
    """A source failed in a way that may pass (rate limit, server error, network)."""


class JsonGetter(Protocol):
    def get_json(self, url: str, params: dict[str, str] | None = None) -> dict[str, Any] | None:
        """The decoded JSON, or None when the resource doesn't exist."""


class Http:
    def __init__(self, session: requests.Session | None = None, timeout: float = 20) -> None:
        self.session = session or requests.Session()
        self.session.headers.setdefault("User-Agent", USER_AGENT)
        self.timeout = timeout

    def get_json(self, url: str, params: dict[str, str] | None = None) -> dict[str, Any] | None:
        try:
            response = self.session.get(url, params=params, timeout=self.timeout)
        except requests.RequestException as error:
            raise SourceUnavailable(f"{url}: {error}") from error
        if response.status_code == 404:
            return None
        if response.status_code == 429 or response.status_code >= 500:
            raise SourceUnavailable(f"{url}: HTTP {response.status_code}")
        if response.status_code != 200:
            return None
        try:
            return response.json()
        except ValueError:
            return None
