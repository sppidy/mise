# Architecture

Mise has two clients and one small persistence server. The web client talks to the server that served it. The Android client can use the same API or remain local-only.

```text
browser ──────────────┐
                     ├── Express API ── state.json
Android (connected) ─┘        │
                              └── cached source images

Android (offline) ───── Android app storage
```

## Web client

`src/` is a React and TypeScript application built by Vite. It keeps a local-storage copy of the recipe box so the UI remains usable during a server outage. Changes are sent to `PUT /api/state` after a short debounce.

Photo OCR runs in the browser with Tesseract.js. Its language model is downloaded and cached by Tesseract.js rather than stored in this repository.

## Android client

`android/` is a native Kotlin and Jetpack Compose application. The app stores state in its private files directory and keeps its server URL and theme choice in DataStore preferences. When a server is configured, local mutations are also sent to that server. Cover files copied into Android's private storage are device-local and are not uploaded by the state API.

Android uses ML Kit for on-device text recognition. `DocumentAnalyzer` handles PDF, DOCX, and text files. PDF pages are scored for photo-like regions so the review screen can start with a useful cover instead of a title page or block of text.

## Server

`server/index.mjs` serves the production web bundle and a deliberately small JSON API:

- `GET /api/health` reports whether the process is alive.
- `GET /api/state` returns the complete recipe box.
- `PUT /api/state` replaces the complete recipe box using a temporary file and rename.
- `POST /api/import` fetches a public page, extracts a recipe, and caches usable source images.

There is no database and no per-user state. `DATA_DIR/state.json` is the source of truth for a server installation.

## Parser

`shared/recipe-parser.js` contains the web/server parser. The Android parser follows the same output model in Kotlin. Both produce a recipe draft with title, source metadata, ingredients, directions, tags, and optional media.

The parser favors explicit recipe structure over prose. Schema.org data wins when available. Social captions and OCR text use conservative heuristics and are rejected when no useful ingredients or directions remain.

## Trust boundaries

The URL importer makes outbound requests to untrusted sites. It rejects non-public address ranges and pins each connection to the DNS result that passed the check. It also limits redirects, protocols, response types, body sizes, and request time. Cached images go through the same path.

The API itself has no authentication. A caller that can reach it can read or replace the entire recipe box. Deployment must provide the missing access-control boundary with a VPN, private network, or authenticated reverse proxy.

The app treats imported text and images as untrusted data. React escapes displayed text, and the server never executes recipe content. Source URLs still leave the installation when a user opens them or asks the importer to fetch them.
