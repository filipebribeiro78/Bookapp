"""Book details by ISBN, combining Google Books (edition data) and Open Library (first publication year)."""

from __future__ import annotations

import re
from dataclasses import dataclass, field

import isbnlib

from .http import JsonGetter, SourceUnavailable

GOOGLE_BOOKS_URL = "https://www.googleapis.com/books/v1/volumes"
OPEN_LIBRARY_URL = "https://openlibrary.org"

# Open Library uses MARC language codes; the Sheet uses the two-letter codes Google Books returns.
MARC_TO_ISO = {"por": "pt", "eng": "en", "spa": "es", "fre": "fr", "ger": "de", "ita": "it", "lat": "la"}


@dataclass
class BookDetails:
    isbn13: str
    title: str = ""
    subtitle: str = ""
    authors: list[str] = field(default_factory=list)
    publisher: str = ""
    published: str = ""
    """This edition's publication date, as precise as the sources give it: YYYY, YYYY-MM or YYYY-MM-DD."""
    original_year: int | None = None
    language: str = ""
    pages: int | None = None
    complete: bool = True
    """False when a source couldn't be reached, so a missing detail may still exist."""

    @property
    def found(self) -> bool:
        return bool(self.title)


def normalize_isbn(raw: object) -> str | None:
    """The ISBN-13 for any valid ISBN-10 or ISBN-13, with or without hyphens and spaces."""
    digits = isbnlib.canonical(str(raw or ""))
    if isbnlib.is_isbn13(digits):
        return digits
    if isbnlib.is_isbn10(digits):
        return isbnlib.to_isbn13(digits)
    return None


def lookup(http: JsonGetter, raw_isbn: object, google_api_key: str | None = None) -> BookDetails | None:
    """Details for the book with this ISBN, or None if the ISBN isn't valid."""
    isbn13 = normalize_isbn(raw_isbn)
    if isbn13 is None:
        return None
    details = BookDetails(isbn13=isbn13)

    try:
        google = _google_books(http, isbn13, google_api_key)
    except SourceUnavailable:
        google, details.complete = {}, False
    try:
        edition, work = _open_library(http, isbn13)
    except SourceUnavailable:
        edition, work, details.complete = {}, {}, False

    details.title = google.get("title") or edition.get("title") or work.get("title") or ""
    details.subtitle = google.get("subtitle") or edition.get("subtitle") or ""
    details.authors = list(google.get("authors") or work.get("author_name") or [])
    details.publisher = google.get("publisher") or _first(edition.get("publishers")) or ""
    details.published = _clean_date(google.get("publishedDate")) or _clean_date(edition.get("publish_date"))
    details.language = google.get("language") or _open_library_language(edition)
    details.pages = google.get("pageCount") or edition.get("number_of_pages")

    first_year = work.get("first_publish_year")
    edition_year = _year(details.published)
    # A first edition can't come after this one; when the sources disagree, trust the earlier year.
    if isinstance(first_year, int) and edition_year:
        first_year = min(first_year, edition_year)
    details.original_year = first_year if isinstance(first_year, int) else None
    return details


def _google_books(http: JsonGetter, isbn13: str, api_key: str | None) -> dict:
    params = {"q": f"isbn:{isbn13}"}
    if api_key:
        params["key"] = api_key
    data = http.get_json(GOOGLE_BOOKS_URL, params) or {}
    wanted = {isbn13, isbnlib.to_isbn10(isbn13)}
    for item in data.get("items", []):
        info = item.get("volumeInfo", {})
        identifiers = {entry.get("identifier") for entry in info.get("industryIdentifiers", [])}
        if identifiers & wanted:
            return info
    return {}


def _open_library(http: JsonGetter, isbn13: str) -> tuple[dict, dict]:
    edition = http.get_json(f"{OPEN_LIBRARY_URL}/isbn/{isbn13}.json") or {}
    search = http.get_json(
        f"{OPEN_LIBRARY_URL}/search.json",
        {"isbn": isbn13, "fields": "title,author_name,first_publish_year", "limit": "1"},
    ) or {}
    work = (search.get("docs") or [{}])[0]
    return edition, work


def _first(values: object) -> str:
    return str(values[0]) if isinstance(values, list) and values else ""


def _clean_date(value: object) -> str:
    """Keep ISO-like dates (YYYY, YYYY-MM, YYYY-MM-DD); reduce free text such as "May 1982" to the year."""
    text = str(value or "").strip()
    if re.fullmatch(r"\d{4}(-\d{2}(-\d{2})?)?", text):
        return text
    year = re.search(r"\b(1[4-9]\d{2}|20\d{2})\b", text)
    return year.group(1) if year else ""


def _year(date: str) -> int | None:
    return int(date[:4]) if re.match(r"\d{4}", date) else None


def _open_library_language(edition: dict) -> str:
    for language in edition.get("languages") or []:
        code = str(language.get("key", "")).rsplit("/", 1)[-1]
        if code:
            return MARC_TO_ISO.get(code, code)
    return ""
