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

- Kotlin, Compose/Material 3, Room/FTS, Paging 3, WorkManager, Hilt, Coil, AndroidX Security Crypto/Biometric, Android `PdfDocument`, bundled semantic image labeling, bundled Latin OCR, compact local intelligence, and ZXing QR decoding.
- `OnDeviceIntelligence` is the shared local readiness boundary. Bundled image labeling, bundled Latin OCR, and compact image/face/QR capabilities are offline; LiteRT 1.4.1 supplies the 16 KB-aligned local interpreter. OCR must never depend on remote or deferred model delivery.
- Archives uses indexed eligibility flags, keyset-paged Room scans, bounded decision batches, stale-candidate reconciliation, independent Payments/Food toggles, favorites/Vault/fresh-photo/sensitive-document safeguards, and foreground confirmation semantics. Food additionally requires semantic food plus prepared/served/packaged context; live-subject labels veto it, so generic legacy tags cannot create Food candidates.
- Room migration 11-to-12 reopens ML analysis for existing photos, preserves tags/OCR, and invalidates prior Food decisions for conservative reevaluation.
- Search evaluates one immutable in-memory index revision for complete eligibility and deterministic ranking; FTS remains synchronized durable support rather than an exclusive candidate source. Limited Android 14 access is visible and never represented as a complete library.
- Vault distinguishes newly added IDs from already protected IDs, removes partial ciphertext, sanitizes MIME/extension pairs, and clears decrypted previews on every lifecycle/security path.

## Release truth

- `app/build.gradle.kts` is the only checked-in release truth: `versionCode = 23`, `versionName = "2.0.16"`, `targetSdk = 36`.
- Release uses R8 optimization and resource shrinking with `proguard-android-optimize.txt`; keep the release shrinker enabled and validate the signed output after any keep-rule change.
- Play Console consumption, track state, and upload eligibility are external preflight facts and must not be written as repository facts.
- `finish_release.sh` is reproducible build-only: it discovers the repository root, derives metadata from Gradle, builds release artifacts, checks existence, merged manifest, sizes, and lint, and never pushes, opens a browser, or uploads.
- The signed Play upload artifact is `app/build/outputs/bundle/release/app-release.aab`; verify package/version, signing, size, and the bundled local semantic-label model before upload.

## Verification

```bash
./gradlew clean testDebugUnitTest assembleDebug bundleRelease verifyApkSize verifyReleaseBundleSize lintDebug
```

Separate source/build proof from device proof. For the current project, no physical Android device is available: use GitHub-hosted Android emulators and sandbox/static checks, label emulator evidence honestly, and keep OEM/physical-only behavior explicitly unverified rather than inventing proof.
