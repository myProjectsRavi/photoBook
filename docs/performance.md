# PhotoBook performance contract

Performance is a measured contract, not a promise of a fixed latency on every Android device. The design target is smooth browsing on low-RAM devices and bounded work for 10k, 50k, and 100k-photo libraries.

## Room and search

- Room remains authoritative for indexed photo state. The `photo_fts` FTS4 table stores normalized filename, folder, location, OCR, notes, and tag text.
- FTS eligibility is not capped at 1,200 rows. IDs and records are fetched in bounded pages and ranked deterministically; valid matches are not dropped because of an arbitrary prefilter limit.
- Archive metadata uses indexed boolean eligibility flags instead of unbounded wildcard metadata scans. The MIME `image/%` prefix filter is a bounded type filter, not a substitute for search ranking.
- Search remains case-insensitive and stable-ID based. UI Paging owns visible windows; viewer/reels own only adjacent windows.

## MediaStore and Archives

- Incremental MediaStore generations are used when available; permission changes and revoked URIs trigger reconciliation.
- A user-requested Archive refresh performs one synchronized index snapshot followed by a keyset-paged Room scan. It commits bounded decision batches, emits partial progress, observes coroutine cancellation, and reconciles candidate rows not seen in the completed scan.
- Archive retention is battery/charging constrained but never requires device idle. Background retention only marks due rows; foreground Android confirmation owns destructive media operations.

## Intelligence maintenance

- Bitmap decoding is sampled and recycled immediately. Maintenance processes bounded batches, checkpoints after commits, records failures locally, and retries only retryable states.
- The current size-constrained offline backend uses bundled semantic image labeling, compact local image heuristics, Android's local face detector, ZXing QR decoding, LiteRT 1.4.1, and bundled Latin OCR. Its bundled native libraries are 16 KB ELF-aligned for Android 15 devices. Latin OCR and in-photo text search run locally with bounded decoding and no network fallback. Archive Food eligibility is stored as an indexed flag after tagging, so archive refreshes do not decode an additional bitmap.
- Food eligibility is computed once during tagging from semantic food plus prepared/served/packaged context and a live-subject veto, then persisted as the indexed `isArchiveFoodCandidate` flag. Existing photos are reopened by migration 11-to-12; tags and OCR remain available while the stricter decision is recomputed.

## UI and measurement

- Compose grids use Paging, stable keys, adjacent-page prefetch, bounded Coil memory, and Lite-tier limits on low-RAM devices.
- Required benchmark scenarios are 10k/50k/100k synthetic libraries: cold startup, search p95, first visible thumbnail, scroll frame stability, peak heap, indexing throughput, and battery-sensitive WorkManager behavior.
- The hosted API-35 100k search certification is a regression gate: each representative OCR, filename, date, synonym, and private-note query must remain at or below 300 ms p95 while returning exact complete results. This CI ceiling is not a universal latency guarantee for every device.
- Build/package gates are hard and must be reported in raw bytes: preserve the existing per-ABI APK gate of 30 MiB and the release AAB gate of 20 MiB (20,971,520 bytes). Blueprint 01 adds a stricter 30,000,000-byte delivered-APK ceiling for final certification; it does not replace or relax the existing 30 MiB repository gate. Record emulator/device model, API level, RAM tier, library size, and offline state with every benchmark.
- Release packaging enables R8 optimization and resource shrinking. Performance and size claims must come from an exact release-like/minified artifact; signing/distribution verification is a separate final-release fact and must not be inferred from a debug build or an unsigned CI artifact.


## Release metadata source of truth

Do not copy versionCode/versionName into this document. Read `app/build.gradle.kts` or run `./gradlew :app:printReleaseMetadata` at the exact commit under test. This avoids stale release examples across contributor guides.
