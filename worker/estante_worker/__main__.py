"""Command line: `python -m estante_worker details`."""

from __future__ import annotations

import argparse
import os
import sys

from .http import Http
from .pipeline import find_details
from .sheet import SheetClient


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="estante_worker")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("details", help="Fill in book details from the ISBN for newly uploaded books.")
    parser.parse_args(argv)

    url, token = os.environ.get("SHEET_URL"), os.environ.get("SHEET_TOKEN")
    if not url or not token:
        print("SHEET_URL and SHEET_TOKEN aren't set, so there's nothing to do yet.")
        return 0

    summary = find_details(
        SheetClient(url, token),
        Http(),
        google_api_key=os.environ.get("GOOGLE_BOOKS_API_KEY") or None,
    )
    print(
        f"{len(summary.found)} found, {len(summary.needs_identification)} need identification, "
        f"{len(summary.retry_later)} to retry."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
