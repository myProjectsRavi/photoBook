# Claude Agent Guidelines for PhotoBook

PhotoBook is a free, private, offline-first Android gallery. Preserve the local architecture, low-RAM behavior, explicit failure states, and user-confirmed destructive actions.

## Non-negotiable rules

1. No `INTERNET`, accounts, telemetry, analytics, cloud APIs, crash uploaders, remote models, or deferred model downloads.
2. Keep generated APKs <=30 MB and the release AAB <=20 MB. If a model exceeds the budget, replace it with a compact local implementation; never relax the gate.
3. Target Android API 36, preserve Android 14 selected-photo access, and test revoked/partial permissions.
4. Room/FTS is authoritative. Use bounded cursor/batch processing, stable IDs, cancellation, checkpoints, and explicit migrations.
5. Never silently trash or permanently delete media. Archive retention marks due rows; foreground Android confirmation performs deletion.
6. Preserve originals during editing/export and clean all pending/partial outputs on failure.

## Architecture facts

- Kotlin, Compose/Material 3, Room/FTS, Paging 3, WorkManager, Hilt, Coil, AndroidX Security Crypto/Biometric, Android `PdfDocument`, bundled semantic image labeling, compact local intelligence, and ZXing QR decoding.
- `OnDeviceIntelligence` is the shared local readiness boundary. Bundled image labeling and compact image/face/QR capabilities are offline; Latin OCR returns an explicit unavailable/permanent failure until a model fits the size budget.
- Archives uses indexed eligibility flags, keyset-paged Room scans, bounded decision batches, stale-candidate reconciliation, independent Payments/Food toggles, favorites/Vault/fresh-photo/sensitive-document safeguards, and foreground confirmation semantics. Food additionally requires semantic food plus prepared/served/packaged context; live-subject labels veto it, so generic legacy tags cannot create Food candidates.
- Room migration 11-to-12 reopens ML analysis for existing photos, preserves tags/OCR, and invalidates prior Food decisions for conservative reevaluation.
- Search uses FTS IDs without a 1,200 eligibility cap and preserves case-insensitive deterministic ranking. Limited Android 14 access is visible and never represented as a complete library.
- Vault distinguishes newly added IDs from already protected IDs, removes partial ciphertext, sanitizes MIME/extension pairs, and clears decrypted previews on every lifecycle/security path.

## Release truth

- `app/build.gradle.kts` is the only checked-in release truth: `versionCode = 16`, `versionName = "2.0.9"`, `targetSdk = 36`.
- Play Console consumption, track state, and upload eligibility are external preflight facts and must not be written as repository facts.
- `finish_release.sh` is reproducible build-only: it discovers the repository root, derives metadata from Gradle, builds release artifacts, checks existence, merged manifest, sizes, and lint, and never pushes, opens a browser, or uploads.
- The signed Play upload artifact is `app/build/outputs/bundle/release/app-release.aab`; verify package/version, signing, size, and the bundled local semantic-label model before upload.

## Verification

```bash
./gradlew clean testDebugUnitTest assembleDebug bundleRelease verifyApkSize verifyReleaseBundleSize lintDebug
```

Separate source/build proof from physical-device proof. Required device replay includes Android 8+, Android 13, Android 14 limited/revoked access, Android 15/16 target behavior, low RAM, airplane mode, and a device without usable Play Services.
