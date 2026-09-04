# Mise

[![CI](https://github.com/sppidy/mise/actions/workflows/ci.yml/badge.svg)](https://github.com/sppidy/mise/actions/workflows/ci.yml)

Mise is a self-hosted recipe box with a web app and a native Android client. It can import ordinary recipe pages, parse recipe text shared from social apps, scan photos and PDFs, and turn the result into ingredients and cooking steps.

Created by Ramshouriesh ([sppidy](https://github.com/sppidy)).

The project is usable today, but it is still pre-1.0 software built for one household. The server has no accounts or authentication. Keep it on a private network, behind a VPN, or behind an authenticated reverse proxy.

## What it does

- Saves recipes, favorites, collections, and grocery items in one JSON data store
- Imports Schema.org recipe pages and recipe-like social captions
- Tries to cache public cover images so saved recipes do not depend on the original page forever
- Scans recipe photos on the web and Android clients
- Imports PDF, DOCX, text, Markdown, and CSV files on Android
- Picks a likely dish photo from a PDF, with controls to change or remove it before saving
- Works locally when the server is unavailable and syncs again when connected
- Includes a focused cook mode, serving controls, a timer, and grocery-list shortcuts
- Supports light, dark, and system themes

The starter recipes and bundled food artwork are demo content. Delete or replace them once you have added your own recipes.

## Quick start with Docker

You need Docker with the Compose plugin.

```sh
git clone https://github.com/sppidy/mise.git
cd mise
docker compose up -d --build
```

Open <http://localhost:8787>. Docker stores the recipe box and cached images in the `mise-data` volume.

Check the service with:

```sh
curl http://localhost:8787/api/health
docker compose logs -f mise
```

To update an existing checkout:

```sh
git pull --ff-only
docker compose up -d --build
```

Back up the data volume before an update or migration:

```sh
docker compose stop mise
mkdir -p mise-backup
docker compose cp mise:/app/data/. ./mise-backup/
docker compose start mise
```

## Local development

The web app and server require Node.js 22 or newer.

```sh
npm ci
npm run dev
```

Vite serves the client at <http://localhost:5173> and proxies `/api` to the Node server on port 8787.

Useful commands:

| Command | Purpose |
| --- | --- |
| `npm run check` | Type-check the web client |
| `npm test` | Run parser and importer tests |
| `npm run build` | Create the production web bundle |
| `npm run ci` | Run all three checks above |
| `npm audit --omit=dev` | Check shipped npm dependencies for known vulnerabilities |
| `npm start` | Serve the production bundle and API |
| `npm run android:apk` | Build the web bundle and Android debug APK |

For a production run without Docker:

```sh
npm run build
npm start
```

## Configuration

| Variable | Default | Used by |
| --- | --- | --- |
| `PORT` | `8787` | Node server listen port |
| `DATA_DIR` | `./data` | Directory containing `state.json` and cached images |
| `OLLAMA_URL` | unset | Optional Ollama endpoint for a second pass over ambiguous fraction glyphs |
| `OLLAMA_MODEL` | `qwen3.5:4b` | Vision model used at `OLLAMA_URL` |
| `CORS_ORIGIN` | unset | Comma-separated extra browser origins allowed to call the API |
| `VITE_SOURCE_URL` | This GitHub repository | Build-time source link shown in the web client |

Copy [.env.example](.env.example) if you want a local reference. The server does not need an Ollama instance for normal imports or OCR.

If you publish a modified server, set `VITE_SOURCE_URL` to the corresponding source for the version you run. The source link is part of the AGPL network-use notice.

For an Android build from a fork, set the same source repository with `MISE_SOURCE_URL`:

```sh
cd android
MISE_SOURCE_URL=https://github.com/you/your-fork ./gradlew assembleRelease
```

## Android

The Android project lives in [`android/`](android/). It uses Kotlin, Jetpack Compose, and on-device ML Kit text recognition.

Install JDK 21 and Android SDK Platform 36, then either open the project in Android Studio or run:

```sh
cd android
./gradlew lintDebug testDebugUnitTest assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. Install it over an existing debug build with:

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

On first launch, open server settings and enter the HTTPS URL of your Mise server. You can leave it disconnected and keep recipes only on the device. Android also accepts recipe links, text, and photos through the system share sheet.

Cover images chosen from Android storage stay on that device; the current sync API does not upload local image files.

Release builds need your own signing configuration. Keep keystores and `keystore.properties` out of Git; the repository ignores both.

## Import behavior

Recipe websites work best when they publish Schema.org `Recipe` data. Social imports depend on public page metadata, captions, or pinned comments. They can stop working when a platform changes its markup or blocks automated requests.

Mise rejects imports that contain neither ingredients nor directions. When it finds a cover but not a usable recipe, it keeps the source link and offers a text-paste fallback. Sharing the same source again updates the saved copy instead of creating a duplicate. If the server cannot cache a cover, the saved recipe may keep the remote image URL.

The server rejects private-network import targets, limits redirects and response sizes, and times out slow requests. These checks reduce server-side request forgery risk; they do not turn the server into a safe public multi-user service.

## Data and privacy

Mise has no project-operated cloud service. Your own server stores its state in `DATA_DIR`, while the browser keeps a fallback copy in local storage. Importing a link contacts the source site. Displaying a cover that could not be cached may contact the image host from your browser or Android device. Optional Ollama refinement sends selected image crops to the Ollama endpoint you configured.

The Android app uses Google ML Kit. Recipe images and recognized text stay on the device, but the SDK may send Google app, device, and performance metrics. Read [PRIVACY.md](PRIVACY.md) for the full data-flow summary.

## Repository map

- `src/`: React and TypeScript web client
- `server/`: Express API, persistence, image cache, and guarded URL fetcher
- `shared/`: recipe parser shared by the server and Android fallback logic
- `public/`: PWA files, fonts, icons, and demo artwork
- `android/`: native Android app and share receiver
- `docs/ARCHITECTURE.md`: component boundaries and data flow
- `docs/RELEASING.md`: maintainer checklist for tagged releases
- `LICENSING.md`: what the AGPL and Android exception mean for this repository
- `CHANGELOG.md`: user-visible changes by release

Runtime data, generated bundles, APKs, local Android settings, OCR caches, and test artifacts are ignored by Git.

## Contributing

Start with [CONTRIBUTING.md](CONTRIBUTING.md). Security problems should follow [SECURITY.md](SECURITY.md), not the public issue tracker.

## License

Copyright (C) 2026 Mise contributors.

Mise is licensed under the [GNU Affero General Public License, version 3 or later](LICENSE). The Android client carries a narrow [linking exception](LICENSE-EXCEPTION.md) for Google ML Kit and its required binary dependencies. Imported recipes and images remain the property of their respective owners and are not relicensed by Mise. [LICENSING.md](LICENSING.md) explains the repository layout and the obligations that matter when you run or redistribute a modified version.

Bundled font notices and other dependency notes are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), with reusable license texts under [`THIRD_PARTY_LICENSES/`](THIRD_PARTY_LICENSES/).

Mise is an independent project. It is not affiliated with or endorsed by Osta Ltd.
