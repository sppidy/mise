# Third-party notices

Mise depends on packages installed through npm and Gradle. Those packages keep their own licenses; AGPL-3.0-or-later does not replace them. `package-lock.json` records the resolved npm tree, while the Gradle build files declare the Android dependencies.

## Direct npm runtime dependencies

These are the versions resolved for the 0.1.0 release. Their license files are copied verbatim under `THIRD_PARTY_LICENSES/npm/`.

| Package | Version | License | License copy |
| --- | --- | --- | --- |
| Express | 5.2.1 | MIT | [`express.txt`](THIRD_PARTY_LICENSES/npm/express.txt) |
| ipaddr.js | 2.5.0 | MIT | [`ipaddr.js.txt`](THIRD_PARTY_LICENSES/npm/ipaddr.js.txt) |
| Lucide React | 1.39.0 | ISC | [`lucide-react.txt`](THIRD_PARTY_LICENSES/npm/lucide-react.txt) |
| React | 19.2.8 | MIT | [`react.txt`](THIRD_PARTY_LICENSES/npm/react.txt) |
| React DOM | 19.2.8 | MIT | [`react-dom.txt`](THIRD_PARTY_LICENSES/npm/react-dom.txt) |
| sharp | 0.35.4 | Apache-2.0 | [`sharp.txt`](THIRD_PARTY_LICENSES/npm/sharp.txt) |
| Tesseract.js | 7.0.0 | Apache-2.0 | [`tesseract.js.txt`](THIRD_PARTY_LICENSES/npm/tesseract.js.txt) |
| Undici | 7.29.0 | MIT | [`undici.txt`](THIRD_PARTY_LICENSES/npm/undici.txt) |

Transitive npm packages remain listed in `package-lock.json`. An installed tree also retains the license file shipped inside each package.

The following files are copied into the repository and need their notices kept with distributions.

## DM Sans

Copyright 2014 The DM Sans Project Authors.

DM Sans is distributed under the SIL Open Font License 1.1. The complete text is stored in:

- `public/assets/FONT-LICENSE.txt`
- `android/app/src/main/assets/fonts/DM_SANS_LICENSE.txt`

## DM Serif Display

Copyright 2014-2018 Adobe, with Reserved Font Name "Source". Copyright 2019 Google LLC.

DM Serif Display is distributed under the SIL Open Font License 1.1. The complete text is stored in:

- `public/assets/FONT-LICENSE.txt`
- `android/app/src/main/assets/fonts/DM_SERIF_LICENSE.txt`

## Tesseract.js language data

Tesseract.js downloads English OCR data from `@tesseract.js-data/eng` when a runtime first needs it. The downloaded cache is not tracked by this repository. Package metadata for `@tesseract.js-data/eng` version 1.0.0 identifies the data as MIT-licensed and points to <https://github.com/naptha/tessdata>.

## Android and Apache-licensed components

The Gradle Wrapper, Kotlin tooling, Jetpack Compose, AndroidX libraries, sharp, and Tesseract.js are distributed under the Apache License 2.0. The license text is stored in `THIRD_PARTY_LICENSES/Apache-2.0.txt`. Individual package notices remain with their packages and source repositories.

## Google ML Kit

The Android client links to Google ML Kit Text Recognition. ML Kit is supplied under Google's own terms and is not relicensed under the AGPL. Review the [ML Kit terms](https://developers.google.com/ml-kit/terms) and the Android linking exception in [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md) before redistributing an APK.
