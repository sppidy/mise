# Contributing

Small, focused changes are easiest to review. If you are planning a new storage format, authentication system, or importer for a brittle social platform, open an issue before writing the whole thing.

## Set up the project

You need Node.js 22 or newer. Android work also needs JDK 21 and Android SDK Platform 36.

```sh
git clone https://github.com/sppidy/mise.git
cd mise
npm ci
npm run dev
```

The web client runs at <http://localhost:5173>; its API proxy expects the Node server on port 8787.

## Before opening a pull request

Run the checks that cover your change:

```sh
npm run ci
npm audit --omit=dev
cd android && ./gradlew lintDebug testDebugUnitTest assembleDebug
```

Parser fixes should include a small fixture or regression test. Keep real account names, private recipe collections, auth headers, and downloaded social media out of fixtures.

For UI changes, check a narrow phone layout and a desktop or tablet layout. Check dark mode as well. A screenshot in the pull request is useful when the visual difference is not obvious from the code.

Do not commit `data/`, `dist/`, APKs, Gradle output, Android signing files, OCR caches, or files from `tmp/`.

## Pull requests

- Explain the behavior that changed and why.
- Call out storage migrations, new outbound requests, or changes to import limits.
- Update the README or architecture note when setup or data flow changes.
- Keep unrelated formatting out of the diff.

Be direct and considerate in issues and reviews. Critique the implementation, not the person who wrote it.

## Licensing contributions

By submitting a contribution, you agree to license it under AGPL-3.0-or-later and the repository's applicable exceptions, without additional terms. Read [LICENSING.md](LICENSING.md) for the repository's license layout. Do not submit code, fonts, images, test data, or generated output unless you have the right to license or redistribute it.

Report vulnerabilities through the process in [SECURITY.md](SECURITY.md).
