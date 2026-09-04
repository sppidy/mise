# Releasing

Mise does not have an automated publishing pipeline yet. Releases are built and signed by a maintainer after the default branch is green.

## Prepare the release

1. Update `version` in `package.json` and `package-lock.json`.
2. Set Android `versionName` to the same version and increment `versionCode` in `android/app/build.gradle`. `versionCode` must never decrease, even though it does not need to match the release version.
3. Move the finished entries from `Unreleased` into a dated section in `CHANGELOG.md`.
4. Review README setup steps, `PRIVACY.md`, `SECURITY.md`, and `THIRD_PARTY_NOTICES.md` for the changes in the release.
5. Check that `VITE_SOURCE_URL` and `MISE_SOURCE_URL` point to the public corresponding source for the build.

Run a clean verification:

```sh
npm ci
npm run ci
npm audit --omit=dev
cd android
./gradlew clean lintDebug testDebugUnitTest assembleDebug
```

Build the Docker image as a separate smoke test:

```sh
docker compose build --pull
docker compose up -d
curl --fail http://localhost:8787/api/health
docker compose down
```

Open the web app once from the built image. Check the source, license, privacy, and third-party notice links in server settings. Also confirm that a new data volume initializes after the first client save.

## Android artifact

Create the signed APK or app bundle with a maintainer-owned key. Keep the keystore, passwords, and `keystore.properties` outside Git. Set `MISE_SOURCE_URL` if the build comes from a fork. Install the signed artifact on a real device and test import, local persistence, server sync, dark mode, and Android share intents.

The Android build copies the AGPL, linking exception, privacy policy, and third-party notices into the APK assets under `legal/`.

For local signing, either place an ignored `keystore.properties` file in `android/` or provide these environment variables:

```properties
storeFile=/absolute/path/to/mise-release.p12
storePassword=...
keyAlias=mise-release
keyPassword=...
```

```sh
MISE_KEYSTORE_PATH=/absolute/path/to/mise-release.p12 \
MISE_KEYSTORE_PASSWORD=... \
MISE_KEY_ALIAS=mise-release \
MISE_KEY_PASSWORD=... \
./gradlew assembleRelease bundleRelease
```

The `Android release` workflow uses the repository secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. A published GitHub release triggers a signed build and attaches the verified APK, AAB, and `SHA256SUMS`. A manual workflow run builds the same files as workflow artifacts without modifying a GitHub release. Keep a tested offline backup of the keystore and its credentials; losing them prevents compatible updates signed with the same key.

## Publish

Create a signed Git tag using the same version as the package metadata. Publish from a clean checkout, then attach:

- a short list of user-visible changes;
- migration or backup notes, if storage behavior changed;
- the signed Android artifact, when one is being distributed; and
- a SHA-256 checksum for every attached binary.

Verify the tag and checksum after GitHub finishes processing the release. The CI workflow must be green on the tagged commit.

Do not attach debug builds that contain local server addresses or test data. Do not upload Docker volumes, `state.json`, cached recipe images, or OCR caches.
