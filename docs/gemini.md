# Gemini Agent Guidelines for PhotoBook

PhotoBook is a private, offline-first Android photo manager. The app must stay local, free, lightweight, and smooth on low-memory Android devices.

## Project Identity

- Stack: Kotlin, Android SDK, Jetpack Compose, Room/FTS, Coroutines/Flow, Hilt, Paging 3, Coil, WorkManager, ML Kit Play Services, AndroidX Security Crypto, AndroidX Biometric.
- Core principle: no accounts, no cloud dependency, no app-level internet permission, no tracking.
- Size discipline: generated APKs <= 30 MB and release AAB <= 20 MB.

## Strict Directives

1. Do not add `android.permission.INTERNET`.
2. Do not add telemetry, analytics, cloud crash reporting, Firebase, external APIs, or remote model calls.
3. Do not add large bundled models or heavy libraries for features Android can perform locally.
4. Use idiomatic Kotlin, coroutines, Flow, and Hilt boundaries.
5. Use Compose for UI.
6. Include explicit Room migrations for schema changes.
7. Do not swallow uncaught exceptions. Use local diagnostics, WorkManager result states, and targeted `runCatching`.
8. Keep 2 GB RAM devices in mind: use paging, database prefiltering, sampled bitmap decoding, bounded viewer windows, and Lite performance settings.
9. Keep destructive media actions behind Android system confirmation flows.

## Key Files

- `app/src/main/AndroidManifest.xml` - privacy-sensitive permissions. No internet permission.
- `app/src/main/java/com/photobook/app/PhotoBookApplication.kt` - app startup and local crash diagnostics.
- `app/src/main/java/com/photobook/app/data/db/PhotoBookDatabase.kt` - Room schema and migrations.
- `app/src/main/java/com/photobook/app/ui/viewmodel/MainViewModel.kt` - main state owner.
- `app/src/main/java/com/photobook/app/ui/screen/MainScreen.kt` - primary Compose screen.
- `app/src/main/java/com/photobook/app/ui/screen/PhotoViewerScreen.kt` - full-screen viewer and photo actions.
- `app/src/main/java/com/photobook/app/search/` - local query parsing, filtering, and ranking.
- `app/src/main/java/com/photobook/app/ml/TaggingWorker.kt` - background ML/OCR indexing.
- `app/src/main/java/com/photobook/app/feature/archive/` - Archives cleanup candidate detection.
- `app/src/main/java/com/photobook/app/feature/pdf/PdfExportService.kt` - offline photo-to-PDF export.
- `app/src/main/java/com/photobook/app/feature/qrshare/` - compressed offline QR preview transfer.
- `app/src/main/java/com/photobook/app/feature/vault/VaultService.kt` - encrypted vault storage.

## Current Behavior to Preserve

- Android 14 selected-photo access is supported and surfaced in UI as limited library mode.
- ML/OCR processing uses explicit status fields and must not mark unavailable-model empty results as processed.
- Search ranking improves order but must not silently broaden or shrink eligibility.
- Smart Albums are virtual local filters/actions over existing metadata. Do not add persistent album storage for default Smart Albums.
- Full-screen viewer opens a bounded photo window around the active item for large libraries.
- Vault UI is active. Open, add, export, and delete operations require biometric or device credential authentication; vault UI also uses `FLAG_SECURE` and clears state on background.
- Archives detects likely temporary transaction screenshots locally, but trash and delete still require Android confirmation.
- Share as PDF uses Android `PdfDocument`, FileProvider cache output, EXIF orientation handling, and sampled one-bitmap-at-a-time rendering.
- QR transfer is a compressed preview/small-transfer feature with single-frame and animated chunked modes, hash verification, estimated scan time, and hard size limits. Do not describe it as universal full-quality photo transfer.

## Verification

Use focused tests while iterating, then run the full gate before release or final readiness claims:

```bash
./gradlew testDebugUnitTest assembleDebug bundleRelease verifyApkSize verifyReleaseBundleSize lintDebug
```
