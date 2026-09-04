# Licensing

Mise's original code, documentation, and artwork are available under the [GNU Affero General Public License, version 3 or later](LICENSE). Copyright stays with the people who contributed the work.

The short version is simple: you may use, study, copy, and modify Mise. If you distribute it, you must provide the corresponding source under the same license. If people use your modified version over a network, AGPL section 13 also requires you to offer them the corresponding source for the version you are running. The license text, not this summary, controls.

The web app includes a source link. Set `VITE_SOURCE_URL` when building a fork so that link points to the exact source you offer. Android builds use `MISE_SOURCE_URL` for the same purpose:

```sh
VITE_SOURCE_URL=https://github.com/you/mise docker build .
cd android && MISE_SOURCE_URL=https://github.com/you/mise ./gradlew assembleRelease
```

## Android exception

The Android client uses Google ML Kit Text Recognition. [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md) permits the AGPL-covered Android code to link with ML Kit and the independent binary modules it needs. The exception is limited to that combination. It does not change the license of Mise, the server, or any modified Mise code.

You may keep the exception in a modified Android client, extend it to your own changes, or remove it. Redistributors still need to follow Google's terms for ML Kit.

## Files from other projects

Dependencies and bundled fonts keep their own licenses. [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) identifies the direct runtime dependencies and the font files. Complete license texts shipped with copied assets live under `THIRD_PARTY_LICENSES/`, `public/assets/`, and `android/app/src/main/assets/fonts/`. Packages installed by npm and Gradle also include their own metadata and notices.

Recipes, photos, PDFs, captions, and other material imported by a user are not part of Mise and are not relicensed under the AGPL. Anyone importing or sharing that material is responsible for having the right to do so.

## Contributions

By submitting a contribution, you license it under AGPL-3.0-or-later and the repository's applicable exceptions. Sign-off is not required, but do not submit work you do not have permission to license.
