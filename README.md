# PhotoBook

PhotoBook is a private, offline-first Android photo manager for fast local search, cleanup, secure storage, and lightweight sharing. The product goal is simple: no accounts, no cloud dependency, no tracking, no app-level internet permission, and smooth behavior on low-memory Android devices.

## Privacy Position

- The app does not request `android.permission.INTERNET`.
- Photo indexing, bundled semantic image labels, compact face signals, search ranking, Archives detection, PDF export, QR sharing, and vault operations run on-device. OCR has an explicit local-unavailable state until a Latin model fits the hard size budget; it never downloads a model or reports a false success.
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

### Offline intelligence

- The bundled ML Kit image-labeling model provides semantic Food/live-subject signals offline; compact local image heuristics, Android's local face detector, and the ZXing QR-only decoder remain bundled in the app. There is no Play Services model installation, deferred download, or ML manifest metadata.
- Archive Food is deliberately conservative: a photo must have semantic food evidence plus prepared, served, or packaged-food context, and live people, animals, birds, pets, wildlife, and similar subjects veto the candidate. A generic legacy `food` tag is not sufficient by itself.
- Latin OCR currently fails deterministically as a local capability-unavailable result because the available bundled vendor model exceeds the hard APK/AAB gates. This limitation is visible to callers and never falls back to network delivery.
- Indexing tracks ML/OCR state explicitly: pending, model preparing, processed, retryable failure, or permanent failure.
- A photo is not marked processed when an analyzer is unavailable or fails.

### Archives

- Archives is a lightweight, local cleanup queue for likely temporary transaction screenshots.
- Detection is strict: candidates must look like screenshots, be older than the grace period, not be favorites, not be protected by Vault, and contain high-confidence payment/UPI evidence from metadata or OCR.
- Candidate discovery uses indexed archive flags and bounded Room/MediaStore pages. A complete scan reconciles stale decisions and supports cancellation without allowing an older scan to overwrite newer choices.
- The Room 11-to-12 migration reopens ML analysis for existing photos, preserves tags and OCR, and stales old Food decisions so the stricter semantic gate can be applied without requiring a library re-import.
- Retention workers only mark items due. Android media trash/delete operations still use Android's system confirmation flow; PhotoBook does not silently trash or permanently delete user media.
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
- Importing to Vault does not delete the original media automatically. Move-out distinguishes newly protected IDs from already-protected IDs, deletes failed partial ciphertext, sanitizes export names/MIME pairs, and clears decrypted previews on lock, background, memory pressure, dismissal, and error.

### Viewer and Performance

- Timeline uses Paging and fixed-size thumbnail requests.
- Full-screen viewer opens a bounded photo window around the selected image and recenters as the user swipes, avoiding a full in-memory viewer list for very large libraries.
- Low-RAM devices use the Lite performance tier for smaller thumbnails, smaller intelligence bitmaps, lower image-cache percentage, and sequential ML work.
- Duplicate prefiltering and archive candidate queries are database-driven where possible to reduce heap pressure.
- Room is authoritative for search and archive state. FTS IDs are loaded in bounded pages; full-library snapshots are reserved for visible UI/state that genuinely needs them.

## Size Discipline

PhotoBook must stay lightweight. The current Gradle gates are:

- `verifyApkSize`: every generated APK must be <= 30 MB.
- `verifyReleaseBundleSize`: release AAB output must be <= 20 MB.
- `compileSdk` and `targetSdk` are 36 for the current Play requirement. Source-controlled release truth is `versionCode = 16`, `versionName = "2.0.9"`; Play Console consumption is an external preflight, not a repository fact.

CI runs the same verification command used locally:

```bash
./gradlew clean testDebugUnitTest assembleDebug bundleRelease verifyApkSize verifyReleaseBundleSize lintDebug
```

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- Room + FTS
- Paging 3
- WorkManager
- Hilt
- Coil
- Compact local image/face heuristics and ZXing QR decoding
- Android `PdfDocument`
- AndroidX Security Crypto
- AndroidX Biometric

## Engineering Rules

- Do not add internet permission, analytics, telemetry, crash uploaders, cloud APIs, remote/deferred model downloads, or account systems.
- Do not add dependencies for work Android already supports locally.
- Prefer Room/SQLite prefiltering before Kotlin heap processing.
- Keep destructive media operations behind Android system confirmation.
- Preserve explicit migrations for Room schema changes.
- Verify changes with unit tests, debug build, lint, and size gates before release.
- For Play upload, use the signed release bundle at `app/build/outputs/bundle/release/app-release.aab` only after verifying its package, version code/name, signature, size, and bundled local model asset.
