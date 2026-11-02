"""Client for the Apps Script web app inside the Estante Google Sheet (backend/apps-script/Code.gs)."""

from __future__ import annotations

from typing import Any

import requests


class SheetError(Exception):
    pass


class SheetClient:
    def __init__(self, url: str, token: str, session: requests.Session | None = None, timeout: float = 90) -> None:
        self.url = url
        self.token = token
        self.session = session or requests.Session()
        self.timeout = timeout

    def list_books(self, status: str | None = None) -> list[dict[str, str]]:
        """Every book row as {column header: displayed value}, optionally only those with this Status."""
        return self._call("listBooks", status=status)["books"]

    def update_book(self, book_id: str, fields: dict[str, Any]) -> None:
        """Write these columns for one book. Keys are column headers, as in list_books."""
        self._call("updateBook", bookId=book_id, fields=fields)

    def _call(self, action: str, **payload: Any) -> dict[str, Any]:
        # Apps Script answers a POST with a redirect to the result, which requests follows as a GET.
        response = self.session.post(
            self.url, json={"token": self.token, "action": action, **payload}, timeout=self.timeout
        )
        response.raise_for_status()
        try:
            data = response.json()
        except ValueError as error:
            raise SheetError(
                "The Sheet URL didn't answer like the Estante script. Check SHEET_URL, and that the "
                "deployment's access is set to Anyone."
            ) from error
        if not data.get("ok"):
            raise SheetError(data.get("error") or "The Sheet script reported an error.")
        return data
