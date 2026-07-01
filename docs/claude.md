# Claude Agent Guidelines for PhotoBook

PhotoBook is an offline-first Android photo manager. The product vision is private, local, free, lightweight, and fast on low-memory devices. Users should never need an account, cloud service, or app-level internet permission to use core features.

## Non-Negotiable Rules

1. No internet permission, telemetry, analytics, crash uploaders, cloud APIs, account systems, or remote model calls.
2. Keep the app small. Do not add dependencies when Android, Room, WorkManager, Coil, Compose, ML Kit Play Services, or existing helpers can do the job.
3. Treat 2 GB RAM devices as a hard design target. Prefer database prefiltering, bounded windows, sampled bitmaps, immediate bitmap recycling, and sequential background work in Lite mode.
4. Do not silently trash or permanently delete user media. Android MediaStore trash/delete requests must use the system confirmation flow.
5. Every Room schema change must include an explicit migration.
6. Do not swallow crashes. Use structured `runCatching`, WorkManager failures/retries, and local diagnostics in app-private storage.

## Current Architecture

- Stack: Kotlin, Jetpack Compose, Material 3, Room/FTS, Paging 3, Coroutines/Flow, Hilt, Coil, WorkManager, ML Kit Play Services, AndroidX Security Crypto, AndroidX Biometric.
- Main UI: `app/src/main/java/com/photobook/app/ui/screen/MainScreen.kt`
- Main state owner: `app/src/main/java/com/photobook/app/ui/viewmodel/MainViewModel.kt`
- Search: `app/src/main/java/com/photobook/app/search/`
- ML/OCR: `app/src/main/java/com/photobook/app/ml/`
- Archives: `app/src/main/java/com/photobook/app/feature/archive/`
- PDF export: `app/src/main/java/com/photobook/app/feature/pdf/`
- QR transfer: `app/src/main/java/com/photobook/app/feature/qrshare/`
- Vault: `app/src/main/java/com/photobook/app/feature/vault/`

## Feature Facts

- The manifest intentionally has no `android.permission.INTERNET`.
- `PhotoBookApplication` records local diagnostics and delegates uncaught exceptions to Android's default handler. Do not restore background crash swallowing.
- ML/OCR processing has explicit status values. Do not mark a photo processed unless the relevant analyzer actually completed.
- Search ranking may improve ordering, but search eligibility should remain stable unless the task explicitly asks to change matching behavior.
- Smart Albums are virtual filters/actions over existing metadata. Do not add a Smart Albums table or duplicate media files unless a future task explicitly requires persistent custom albums.
- Android 14 limited photo access is valid but must be visible in UX because search only covers accessible media.
- The full-screen viewer should not materialize huge visible result lists; it uses a bounded window around the active photo.
- Vault UI is active. Opening, adding, exporting, and deleting vault items must remain behind biometric or device credential authentication. Vault UI should use `FLAG_SECURE` and lock/clear on background.
- Archives is local detection plus user-confirmed trash/delete requests, not silent destructive cleanup.
- PDF sharing uses Android `PdfDocument` and FileProvider cache output. Do not add a PDF library for the current one-tap export workflow.
- QR transfer is for compressed previews and small private transfers. It may use single-frame or animated chunked frames with size limits; do not claim universal full-quality photo transfer.

## Verification

Prefer one sequential verification run for broad Android changes:

```bash
./gradlew testDebugUnitTest assembleDebug bundleRelease verifyApkSize verifyReleaseBundleSize lintDebug
```

Size gates:

- Generated APKs must be <= 30 MB.
- Release AAB must be <= 20 MB.

If a task is narrow, a focused `./gradlew testDebugUnitTest` pass is acceptable during iteration, but do not claim release readiness without the full gate.
