# PhotoBook security and privacy posture

PhotoBook is local-first by construction. The app has no app-level `INTERNET` permission, no accounts, telemetry, cloud APIs, crash uploader, or remote model fallback. This permission boundary does not by itself prove that every bundled library feature works offline; the bundled/local backend and airplane-mode replay are verified separately.

## Local intelligence

- The bundled ML Kit image-labeling model, compact image heuristics, Android's local face detector, and LiteRT 1.4.1 run in-process. LiteRT's bundled native libraries are 16 KB ELF-aligned; the Food Archive gate requires semantic food plus prepared/served/packaged context and applies a live-subject veto; color or a generic legacy tag cannot classify a person, animal, bird, pet, or wildlife photo as Food.
- Room migration 11-to-12 resets only the derived ML/Food decision state for reevaluation while retaining user-visible tags and OCR, avoiding stale or overly broad archive classifications after upgrade.
- QR transfer uses ZXing's QR-only decoder and validates transfer ID, frame count, chunk length, byte size, SHA-256, MIME type, filename, duplicate/late frames, four-session limit, and two-minute session lifetime.
- Latin OCR is bundled and verified offline. In-photo search reuses that application-scoped OCR boundary, keeps geometry/query state in memory only, serializes native OCR admission, and rejects stale request/photo/access results. It never contacts a service or downloads a model.
- Authenticated Vault in-photo search decodes a bounded upright bitmap directly from encrypted input in memory. It does not use a persistent plaintext export, and its search controller/query/layout are destroyed when the Vault preview or Vault session closes.

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
- Every release must verify the merged manifest contains no `INTERNET`, package/version metadata matches the exact Gradle source, signatures are valid when signing is part of the tested artifact, the existing 30 MiB APK and 20 MiB AAB gates pass, the Blueprint 01 stricter 30,000,000-byte delivered-APK ceiling passes before final certification, target SDK remains 36, and the exact artifact receives the available offline emulator/device replay. Build success alone is not release readiness.
- The Play upload must use the exact signed `app/build/outputs/bundle/release/app-release.aab` after package/version, signing, size, and bundled-model checks pass.


## Release metadata source of truth

Do not duplicate versionCode/versionName in security guidance. Read `app/build.gradle.kts` or `./gradlew :app:printReleaseMetadata` from the exact branch commit. Play Console track state and signed-distribution state are external evidence, not repository facts.
