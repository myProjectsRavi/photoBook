# PhotoBook

PhotoBook is a private, offline-first Android photo manager for fast local search, cleanup, secure storage, and lightweight sharing. The product goal is simple: no accounts, no cloud dependency, no tracking, no app-level internet permission, and smooth behavior on low-memory Android devices.

## Privacy Position

- The app does not request `android.permission.INTERNET`.
- Photo indexing, OCR, image labels, face signals, search ranking, Archives detection, PDF export, QR sharing, and vault operations run on-device.
- `android:allowBackup="false"` is set for the application.
- Vault files are stored in app-private encrypted storage; vault metadata is stored in Room.
- Local diagnostics are written only to app-private storage and are not uploaded.

## Current Feature Set

### Local Search

- Room + FTS-backed local index for filenames, folders, location text, OCR text, ML tags, and private notes.
- Local ranking improves result order without changing search eligibility. OCR phrase matches, filename/folder matches, ML tags, location, favorites, token coverage, and recency all contribute to ordering.
- Android 14 limited photo access is treated as a first-class mode. The UI shows when only selected media is accessible and offers a way to add more photos.

### Smart Albums

- Smart Albums are virtual, instant filters built from existing local metadata. They do not create duplicate files, new media folders, or a new database table.
- Current albums include Screenshots, Receipts, Documents, Payment screenshots, Food, Selfies, Group photos, Blurry, Duplicates, Large files, WhatsApp media, Camera photos, Downloads, Photos with text, Photos with location, and Photos without location.
- Duplicates opens the existing Storage optimizer; Payment screenshots opens Archives so cleanup remains strict and user-confirmed.

### ML and OCR

- ML Kit image labeling, face detection, OCR, and barcode scanning use on-device Play Services models to keep the app small.
- Indexing tracks ML/OCR state explicitly: pending, model preparing, processed, retryable failure, or permanent failure.
- A photo is not marked processed just because a model returned no result before it was ready.

### Archives

- Archives is a lightweight, local cleanup queue for likely temporary transaction screenshots.
- Detection is strict: candidates must look like screenshots, be older than the grace period, not be favorites, not be protected by Vault, and contain high-confidence payment/UPI evidence from metadata or OCR.
- Android media trash/delete operations still use Android's system confirmation flow. PhotoBook does not silently trash or permanently delete user media.
- Users can choose Archives retention of 7, 14, or 30 days for Archives-managed trashed items.

### Share as PDF

- Full-screen viewer includes one-tap Share as PDF for the current photo.
- Multi-select Create PDF is still available from the grid.
- PDF generation uses Android's built-in `PdfDocument`, cache-backed FileProvider sharing, sampled bitmap decoding, EXIF orientation, and fit-center white pages. No new PDF dependency is required.

### Offline QR Transfer

- QR transfer is a private optical transfer for small compressed photo previews.
- The sender first tries an instant single-frame QR; larger previews use animated chunked QR frames with hash verification on the receiver.
- The UI shows chunk count, transfer size, estimated scan time, and a hard max-size error instead of claiming universal full-quality transfer.

### Vault

- Vault is available from the home action row and selected-photo action bar.
- Opening, adding, exporting, and deleting vault items require strong biometric authentication, with device credential fallback on Android versions that support the combined authenticator flow.
- Vault screens use `FLAG_SECURE` and clear vault UI state when the app leaves the foreground.
- Importing to Vault does not delete the original media automatically; destructive media changes remain explicit.

### Viewer and Performance

- Timeline uses Paging and fixed-size thumbnail requests.
- Full-screen viewer opens a bounded photo window around the selected image and recenters as the user swipes, avoiding a full in-memory viewer list for very large libraries.
- Low-RAM devices use the Lite performance tier for smaller thumbnails, smaller intelligence bitmaps, lower image-cache percentage, and sequential ML work.
- Duplicate prefiltering and archive candidate queries are database-driven where possible to reduce heap pressure.

## Size Discipline

PhotoBook must stay lightweight. The current Gradle gates are:

- `verifyApkSize`: every generated APK must be <= 30 MB.
- `verifyReleaseBundleSize`: release AAB output must be <= 20 MB.

CI runs the same verification command used locally:

```bash
./gradlew testDebugUnitTest assembleDebug bundleRelease verifyApkSize verifyReleaseBundleSize lintDebug
```

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- Room + FTS
- Paging 3
- WorkManager
- Hilt
- Coil
- ML Kit Play Services on-device APIs
- Android `PdfDocument`
- AndroidX Security Crypto
- AndroidX Biometric

## Engineering Rules

- Do not add internet permission, analytics, telemetry, crash uploaders, cloud APIs, model downloads bundled into the APK, or account systems.
- Do not add dependencies for work Android already supports locally.
- Prefer Room/SQLite prefiltering before Kotlin heap processing.
- Keep destructive media operations behind Android system confirmation.
- Preserve explicit migrations for Room schema changes.
- Verify changes with unit tests, debug build, lint, and size gates before release.
