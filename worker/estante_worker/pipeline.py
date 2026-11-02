"""Steps that move books along the Sheet's Status column."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any, Protocol

from .books import BookDetails, lookup
from .http import JsonGetter

PHOTOS_UPLOADED = "Photos uploaded"
DETAILS_FOUND = "Details found"
NEEDS_IDENTIFICATION = "Needs identification"


class Sheet(Protocol):
    def list_books(self, status: str | None = None) -> list[dict[str, str]]: ...

    def update_book(self, book_id: str, fields: dict[str, Any]) -> None: ...


@dataclass
class Summary:
    found: list[str] = field(default_factory=list)
    needs_identification: list[str] = field(default_factory=list)
    retry_later: list[str] = field(default_factory=list)


def find_details(
    sheet: Sheet,
    http: JsonGetter,
    google_api_key: str | None = None,
    log: Callable[[str], None] = print,
) -> Summary:
    """
    Fill in authors, publisher and dates from the ISBN for books whose photos are uploaded, and for books
    waiting for identification that now have an ISBN. Cells that already have a value are left as they are,
    so corrections made in the Sheet survive.
    """
    summary = Summary()
    for book in sheet.list_books():
        status = book.get("Status", "")
        has_isbn = bool(str(book.get("ISBN", "")).strip())
        if not (status == PHOTOS_UPLOADED or (status == NEEDS_IDENTIFICATION and has_isbn)):
            continue
        book_id, title = book["Book ID"], book.get("Book name", "")
        details = lookup(http, book.get("ISBN", ""), google_api_key) if has_isbn else None

        if details is not None and details.found:
            sheet.update_book(book_id, _fields_to_fill(book, details) | {"Status": DETAILS_FOUND})
            summary.found.append(title)
            log(f"Details found: {title} ({details.isbn13})")
        elif details is not None and not details.complete:
            summary.retry_later.append(title)
            log(f"A book source was unreachable, will retry: {title}")
        elif status != NEEDS_IDENTIFICATION:
            sheet.update_book(book_id, {"Status": NEEDS_IDENTIFICATION})
            summary.needs_identification.append(title)
            log(f"Needs identification from the photos: {title}")
    return summary


def _fields_to_fill(book: dict[str, str], details: BookDetails) -> dict[str, Any]:
    candidates: dict[str, Any] = {
        "Authors": ", ".join(details.authors),
        "Publisher": details.publisher,
        "Date of publication": details.published,
        "Original year": details.original_year,
        "ISBN": details.isbn13,
        "Language": details.language,
        "Pages": details.pages,
    }
    return {
        header: value
        for header, value in candidates.items()
        if value not in (None, "") and header in book and not str(book[header]).strip()
    }
