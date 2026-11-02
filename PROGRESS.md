# Progress

## Done
- Milestone 1 written: guided photo capture (cover, back, title page, copyright page, then any other type),
  condition grade, title entry, file naming, offline queue, and upload to Google Drive and the Sheet
  through a script inside the Sheet (`backend/apps-script/Code.gs`).
- Shared naming logic (`core/`) has 6 passing unit tests.

## Next
- First GitHub Actions build of the APK, fix any compile errors, then install on the OnePlus 6T (Android 11) and
  test capture to Sheet.
- Check that the Drive thumbnails display in the Sheet (`IMAGE()` with `drive.google.com/thumbnail`).
- Milestone 2: on-device text recognition and ISBN barcode, then book details from Google Books and Open Library.

## Decisions
- Native Android app (Kotlin, Jetpack Compose), Android 8 and later, no iPhone or iPad.
- The app reaches Google Sheets and Drive through an Apps Script web app inside the Sheet, so no Google Cloud
  project or OAuth client is needed. The token is encrypted on the phone with the Android Keystore.
- Photos are uploaded at most 2000 px on the long side (JPEG quality 88); full-size originals stay on the phone.
- File names: `<title-slug>_<yyyy-mm-dd>_<page-type>[-n].jpg`, with `-2`, `-3` and so on from the second photo of a type.
- Sheet tabs: Books (one row per book) and Photos (one row per photo). Columns are found by header text.
- Uploaded photos are shared view-only by link, because thumbnails, OLX and WordPress need public links.
- APKs are built on GitHub Actions, because this cloud session can't download the Android SDK.
  A fixed debug signing key is kept in the repository so each build installs over the last one.
- Dependency versions are pinned to a known-good set from late 2024 (AGP 8.7.3, Kotlin 2.0.21,
  Compose BOM 2024.12.01), to be upgraded once the build is green.
- WordPress: plain pages, one per book.
- App text is in English for now.
