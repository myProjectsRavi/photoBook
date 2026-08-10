# Gemini Agent Guidelines for PhotoBook

PhotoBook is a local, free, private Android gallery. Optimize for the real user journey: fast startup, complete search, smooth scrolling, safe cleanup, secure Vault, predictable failures, and no connectivity requirement.

## Required behavior

- No `android.permission.INTERNET`, cloud service, telemetry, account, ad, crash uploader, remote model, or deferred model installation.
- API 36 is the current compile/target baseline. Android 14 selected-photo access, revoked access, and empty/partial libraries must remain first-class states.
- Use Room/FTS, Paging, indexed archive flags, keyset pagination, bounded bitmaps, stable IDs, WorkManager checkpoints, cancellation, and explicit Room migrations.
- Archive retention only marks due items. Any trash/delete is a foreground Android-confirmed operation. Preserve favorites, Vault protection, grace periods, sensitive-document exclusion, duplicate category handling, and independent Payments/Food switches.
- Search must be case-insensitive and complete: no 1,200-result eligibility cap, no full-library heap materialization for ordinary paging, deterministic ranking, zero-result/partial-index explanations, and index recovery.
- Vault must be transaction-safe and privacy-safe: distinguish newly added from already protected IDs, remove partial ciphertext, sanitize names/MIME pairs, lazy-load previews, and clear decrypted material on lock/background/dismissal/trim/error.
- PDF must report skipped/unreadable images, preserve orientation, stream one bitmap at a time, clean MediaStore pending rows, and propagate cancellation.
- QR must enforce transfer/session/chunk/byte/MIME/hash/name bounds, reject mismatches and late frames, validate image bytes before MediaStore writes, and release camera resources on every dismissal.

## Offline intelligence and size

The shared `OnDeviceIntelligence` boundary uses the bundled semantic image-labeling model, compact local image heuristics, Android's local face detector, and ZXing QR decoding. Latin OCR is an explicit local capability-unavailable/permanent failure until a compact model fits the hard gates; it never downloads or lies about a processed result. Keep every generated APK <=30 MB and the release AAB <=20 MB.

Archive Food must remain conservative and purpose-specific: require semantic food evidence with prepared, served, or packaged context, and reject live people, animals, birds, pets, wildlife, and other live-subject labels. Do not restore the old generic-tag-only behavior. Migration 11-to-12 reprocesses existing photos while preserving their tags/OCR and invalidating stale Food decisions.

## Release truth and workflow

`app/build.gradle.kts` is authoritative for checked-in metadata: `versionCode = 16`, `versionName = "2.0.9"`, `targetSdk = 36`. Play Console state is external and must be verified at upload time, not documented as fact. `finish_release.sh` only builds and verifies; it does not push, open browsers, or upload.

The signed bundle intended for Play upload is `app/build/outputs/bundle/release/app-release.aab`. Confirm its package, version code/name, signature, size gate, and bundled model asset from the exact built file.

Run the sequential gate:

```bash
./gradlew clean testDebugUnitTest assembleDebug bundleRelease verifyApkSize verifyReleaseBundleSize lintDebug
```

Never call the app production-ready from a build alone. Record physical-device offline replay separately, including low-RAM and no-Play-Services cases.
