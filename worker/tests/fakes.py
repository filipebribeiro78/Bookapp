"""In-memory stand-ins for the book sources and the Sheet."""

from __future__ import annotations

from typing import Any

from estante_worker.books import GOOGLE_BOOKS_URL, OPEN_LIBRARY_URL
from estante_worker.http import SourceUnavailable

PRIDE = "9780141439518"
MEMORIAL = "9789722101394"

GOOGLE = {
    PRIDE: {
        "totalItems": 2,
        "items": [
            {"volumeInfo": {"title": "Pride and Prejudice: a study guide", "industryIdentifiers": [
                {"type": "ISBN_13", "identifier": "9781234567897"}]}},
            {"volumeInfo": {
                "title": "Pride and Prejudice",
                "authors": ["Jane Austen"],
                "publisher": "Penguin",
                "publishedDate": "2003-04-29",
                "industryIdentifiers": [
                    {"type": "ISBN_10", "identifier": "0141439513"},
                    {"type": "ISBN_13", "identifier": PRIDE},
                ],
                "pageCount": 480,
                "language": "en",
            }},
        ],
    },
    MEMORIAL: {"totalItems": 0},
}

OPEN_LIBRARY_EDITIONS = {
    PRIDE: {"title": "Pride and Prejudice", "publishers": ["Penguin Books"], "publish_date": "April 29, 2003"},
    MEMORIAL: {
        "title": "Memorial do Convento",
        "publishers": ["Caminho"],
        "publish_date": "1982",
        "number_of_pages": 357,
        "languages": [{"key": "/languages/por"}],
    },
}

OPEN_LIBRARY_WORKS = {
    PRIDE: {"title": "Pride and Prejudice", "author_name": ["Jane Austen"], "first_publish_year": 1813},
    MEMORIAL: {"title": "Memorial do Convento", "author_name": ["José Saramago"], "first_publish_year": 1982},
}


class FakeHttp:
    def __init__(self, google_down: bool = False, open_library_down: bool = False) -> None:
        self.google_down = google_down
        self.open_library_down = open_library_down
        self.requests: list[str] = []

    def get_json(self, url: str, params: dict[str, str] | None = None) -> dict[str, Any] | None:
        self.requests.append(url)
        if url == GOOGLE_BOOKS_URL:
            if self.google_down:
                raise SourceUnavailable("Google Books: HTTP 429")
            return GOOGLE.get(params["q"].removeprefix("isbn:"), {"totalItems": 0})
        if self.open_library_down:
            raise SourceUnavailable("Open Library: HTTP 503")
        if url.startswith(f"{OPEN_LIBRARY_URL}/isbn/"):
            return OPEN_LIBRARY_EDITIONS.get(url.rsplit("/", 1)[-1].removesuffix(".json"))
        if url == f"{OPEN_LIBRARY_URL}/search.json":
            work = OPEN_LIBRARY_WORKS.get(params["isbn"])
            return {"numFound": 1 if work else 0, "docs": [work] if work else []}
        raise AssertionError(f"unexpected request {url}")


SHEET_COLUMNS = [
    "Book ID", "Book name", "Authors", "Date of publication", "Original year", "Publisher", "ISBN", "Language",
    "Pages", "Status",
]


class FakeSheet:
    def __init__(self, books: list[dict[str, str]]) -> None:
        self.books = [{column: "" for column in SHEET_COLUMNS} | book for book in books]
        self.updates: list[tuple[str, dict[str, Any]]] = []

    def list_books(self, status: str | None = None) -> list[dict[str, str]]:
        return [dict(book) for book in self.books if status is None or book["Status"] == status]

    def update_book(self, book_id: str, fields: dict[str, Any]) -> None:
        self.updates.append((book_id, fields))
        book = next(book for book in self.books if book["Book ID"] == book_id)
        book.update({key: "" if value is None else str(value) for key, value in fields.items()})

    def book(self, book_id: str) -> dict[str, str]:
        return next(book for book in self.books if book["Book ID"] == book_id)
