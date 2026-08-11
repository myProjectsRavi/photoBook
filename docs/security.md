# PhotoBook security and privacy posture

PhotoBook is local-first by construction. The app has no app-level `INTERNET` permission, no accounts, telemetry, cloud APIs, crash uploader, or remote model fallback. This permission boundary does not by itself prove that every bundled library feature works offline; the bundled/local backend and airplane-mode replay are verified separately.

## Local intelligence

- The bundled ML Kit image-labeling model, compact image heuristics, Android's local face detector, and LiteRT 1.4.1 run in-process. LiteRT's bundled native libraries are 16 KB ELF-aligned; the Food Archive gate requires semantic food plus prepared/served/packaged context and applies a live-subject veto; color or a generic legacy tag cannot classify a person, animal, bird, pet, or wildlife photo as Food.
- Room migration 11-to-12 resets only the derived ML/Food decision state for reevaluation while retaining user-visible tags and OCR, avoiding stale or overly broad archive classifications after upgrade.
- QR transfer uses ZXing's QR-only decoder and validates transfer ID, frame count, chunk length, byte size, SHA-256, MIME type, filename, duplicate/late frames, four-session limit, and two-minute session lifetime.
- Latin OCR currently has a deterministic capability-unavailable result because the available bundled model exceeds the hard size gates. It never contacts a service or marks a photo processed when unavailable.

## Media and destructive actions

- MediaStore access is permission-scoped, including Android 14 selected-photo access. Revoked or partial access is surfaced and reconciled rather than treated as full-library access.
- No destructive media operation bypasses Android confirmation. Archive retention marks due items; it does not call `ContentResolver.delete()`.
- PDF, QR receive, metadata-clean copies, and failed MediaStore writes remove pending/partial output on every failure path.

## Vault and previews

- Vault ciphertext and metadata are app-private and protected by biometric/device credential authentication.
- Vault move-out is transaction-safe: already-protected IDs are not treated as newly added, failed copies remove partial ciphertext, and database state is committed only after the encrypted file is complete.
- Preview material is bounded, lazily generated, short-lived, and cleared on lock, background, dismissal, trim-memory, and errors. Export names are sanitized and MIME/extension pairs are preserved.

## Diagnostics and release checks

- Diagnostics are local, redacted for paths/URIs, bounded in size/count, and never uploaded.
- Every release must verify the merged manifest contains no `INTERNET`, signatures are valid, APK/AAB size gates pass, target SDK is 36, and the exact artifacts pass an airplane-mode device replay. Build success alone is not release readiness.
- The Play upload must use the exact signed `app/build/outputs/bundle/release/app-release.aab` after package/version, signing, size, and bundled-model checks pass.
