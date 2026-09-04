## What changed

Describe the user-visible behavior and the reason for the change.

## Checks

- [ ] `npm run ci`
- [ ] `npm audit --omit=dev`
- [ ] `cd android && ./gradlew lintDebug testDebugUnitTest assembleDebug` when Android code changed
- [ ] Phone and desktop/tablet layouts checked when UI changed
- [ ] Light and dark themes checked when UI changed
- [ ] Documentation updated when setup, storage, privacy, or network behavior changed

## Notes for reviewers

Call out migrations, new outbound requests, parser edge cases, or anything that needs manual verification.
