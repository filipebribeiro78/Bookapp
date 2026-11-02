from estante_worker.__main__ import main
from estante_worker.pipeline import DETAILS_FOUND, NEEDS_IDENTIFICATION, PHOTOS_UPLOADED, find_details

from fakes import MEMORIAL, PRIDE, FakeHttp, FakeSheet


def quiet(_: str) -> None:
    pass


def test_details_fill_empty_cells_and_keep_corrections():
    sheet = FakeSheet([
        {"Book ID": "b1", "Book name": "Pride and Prejudice", "ISBN": "0141439513", "Status": PHOTOS_UPLOADED,
         "Publisher": "Penguin Classics"},
    ])
    summary = find_details(sheet, FakeHttp(), log=quiet)
    book = sheet.book("b1")
    assert summary.found == ["Pride and Prejudice"]
    assert book["Status"] == DETAILS_FOUND
    assert book["Authors"] == "Jane Austen"
    assert book["Original year"] == "1813"
    assert book["Publisher"] == "Penguin Classics"  # typed in the Sheet, so left alone
    assert book["ISBN"] == "0141439513"  # already filled, so left alone


def test_books_without_isbn_wait_for_identification_from_photos():
    sheet = FakeSheet([{"Book ID": "b1", "Book name": "Sem ISBN", "Status": PHOTOS_UPLOADED}])
    summary = find_details(sheet, FakeHttp(), log=quiet)
    assert summary.needs_identification == ["Sem ISBN"]
    assert sheet.book("b1")["Status"] == NEEDS_IDENTIFICATION


def test_an_isbn_added_later_in_the_sheet_is_picked_up():
    sheet = FakeSheet([{"Book ID": "b1", "Book name": "Memorial", "ISBN": MEMORIAL, "Status": NEEDS_IDENTIFICATION}])
    find_details(sheet, FakeHttp(), log=quiet)
    assert sheet.book("b1")["Status"] == DETAILS_FOUND
    assert sheet.book("b1")["Authors"] == "José Saramago"


def test_unreachable_sources_leave_the_book_for_the_next_run():
    sheet = FakeSheet([{"Book ID": "b1", "Book name": "Unknown", "ISBN": "9789723800258", "Status": PHOTOS_UPLOADED}])
    summary = find_details(sheet, FakeHttp(google_down=True), log=quiet)
    assert summary.retry_later == ["Unknown"]
    assert sheet.updates == []


def test_other_statuses_are_not_touched():
    sheet = FakeSheet([
        {"Book ID": "b1", "Book name": "Uploading", "ISBN": PRIDE, "Status": "Photos uploading"},
        {"Book ID": "b2", "Book name": "Done", "ISBN": PRIDE, "Status": DETAILS_FOUND},
    ])
    find_details(sheet, FakeHttp(), log=quiet)
    assert sheet.updates == []


def test_command_line_without_sheet_settings_does_nothing(monkeypatch, capsys):
    monkeypatch.delenv("SHEET_URL", raising=False)
    monkeypatch.delenv("SHEET_TOKEN", raising=False)
    assert main(["details"]) == 0
    assert "nothing to do" in capsys.readouterr().out
