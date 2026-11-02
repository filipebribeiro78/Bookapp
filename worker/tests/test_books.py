from estante_worker.books import lookup, normalize_isbn

from fakes import MEMORIAL, PRIDE, FakeHttp


def test_isbns_are_normalised_to_isbn13():
    assert normalize_isbn("978-0-14-143951-8") == PRIDE
    assert normalize_isbn("0 14 143951 3") == PRIDE
    assert normalize_isbn("9780141439519") is None  # wrong check digit
    assert normalize_isbn("") is None
    assert normalize_isbn(None) is None


def test_google_books_edition_is_combined_with_open_library_first_year():
    details = lookup(FakeHttp(), "0141439513")
    assert details is not None and details.found and details.complete
    assert details.isbn13 == PRIDE
    assert details.title == "Pride and Prejudice"  # not the study guide that also matched the query
    assert details.authors == ["Jane Austen"]
    assert details.publisher == "Penguin"
    assert details.published == "2003-04-29"
    assert details.original_year == 1813
    assert details.language == "en"
    assert details.pages == 480


def test_open_library_alone_is_enough_for_books_google_lacks():
    details = lookup(FakeHttp(), MEMORIAL)
    assert details is not None and details.found
    assert details.authors == ["José Saramago"]
    assert details.publisher == "Caminho"
    assert details.published == "1982"
    assert details.original_year == 1982
    assert details.language == "pt"
    assert details.pages == 357


def test_a_source_that_is_down_marks_the_result_incomplete():
    details = lookup(FakeHttp(google_down=True), PRIDE)
    assert details is not None and details.found and not details.complete
    assert details.publisher == "Penguin Books"
    assert details.published == "2003"  # "April 29, 2003" reduced to the year
    assert details.original_year == 1813


def test_invalid_isbns_are_not_looked_up():
    http = FakeHttp()
    assert lookup(http, "12345") is None
    assert http.requests == []
