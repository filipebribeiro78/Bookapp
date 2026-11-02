# Bookapp (Estante)

An Android app that photographs second-hand books and catalogues them in a Google Sheet, on the way to
publishing each book to OLX.pt and a WordPress site.

- `app/`: the Android app (Kotlin). Builds come from GitHub Actions; see Releases for the latest APK.
- `core/`: shared logic such as photo file naming, with unit tests.
- `backend/apps-script/Code.gs`: the script that runs inside the Google Sheet.
- `SETUP.md`: how to connect the Sheet and install the app.
- `PROGRESS.md`: what's done, what's next, and the decisions made so far.
