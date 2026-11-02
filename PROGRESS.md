# Progress

## Done
- Milestone 1: guided photo capture (cover, back cover, title page, copyright page, then any other type),
  condition grade, title entry, file naming, offline queue, and upload to Google Drive and the Sheet through a
  script inside the Sheet (`backend/apps-script/Code.gs`). Build 1 on GitHub Actions is green; the APK is under
  Releases.
- Sheet script version 2: the worker can list and update books; Language and Pages columns; ISBN and date
  columns kept as plain text.
- Milestone 2, Python part (`worker/`): fills in authors, publisher, edition date, original year, language and
  pages from the ISBN, using Google Books and Open Library. 11 tests.
- Shared naming logic (`core/`): 6 tests.

## Next
- On the phone: install, connect the Sheet, photograph a book, and check the Books and Photos tabs, including
  that the Drive thumbnails display (`IMAGE()` with `drive.google.com/thumbnail`).
- Add `SHEET_URL` and `SHEET_TOKEN` as repository secrets and run the worker on a real book.
- Milestone 2, phone part: read the ISBN barcode and the text on the phone (ML Kit) and send the ISBN to the Sheet.
- Start the worker automatically when a book's photos finish uploading, instead of by hand.

## Decisions
- Native Android app (Kotlin, Jetpack Compose), Android 8 and later, no iPhone or iPad.
- Everything after the photos (book details, prices, descriptions, publishing) is Python in `worker/`, run on
  GitHub Actions. The same code will back the Claude Desktop (MCP) connection.
- The app and the worker reach Google Sheets and Drive through an Apps Script web app inside the Sheet, so no
  Google Cloud project or OAuth client is needed. The token is encrypted on the phone with the Android Keystore.
- The worker only fills empty cells, so corrections typed in the Sheet survive. Books without an ISBN get the
  status "Needs identification"; the AI identifies them from the photos in milestone 3.
- Photos are uploaded at most 2000 px on the long side (JPEG quality 88); full-size originals stay on the phone.
- File names: `<title-slug>_<yyyy-mm-dd>_<page-type>[-n].jpg`, with `-2`, `-3` and so on from the second photo of a type.
- Sheet tabs: Books (one row per book) and Photos (one row per photo). Columns are found by header text.
- Uploaded photos are shared view-only by link, because thumbnails, OLX and WordPress need public links.
- APKs are built on GitHub Actions and published as releases; the APK or build log is also pushed to the
  `ci-result` branch so a Claude session can read it through git. A fixed debug signing key is kept in the
  repository so each build installs over the last one.
- Dependency versions are pinned to a known-good set from late 2024 (AGP 8.7.3, Kotlin 2.0.21,
  Compose BOM 2024.12.01), to be upgraded later.
- WordPress: plain pages, one per book.
- App text is in English for now.
