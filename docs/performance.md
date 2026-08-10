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
- The current size-constrained offline backend uses bundled semantic image labeling, compact local image heuristics, Android's local face detector, and ZXing QR decoding. Latin OCR reports an explicit permanent capability failure until a compact bundled model meets the hard size gates; no network fallback is allowed. Archive Food eligibility is stored as an indexed flag after tagging, so archive refreshes do not decode an additional bitmap.
- Food eligibility is computed once during tagging from semantic food plus prepared/served/packaged context and a live-subject veto, then persisted as the indexed `isArchiveFoodCandidate` flag. Existing photos are reopened by migration 11-to-12; tags and OCR remain available while the stricter decision is recomputed.

## UI and measurement

- Compose grids use Paging, stable keys, adjacent-page prefetch, bounded Coil memory, and Lite-tier limits on low-RAM devices.
- Required benchmark scenarios are 10k/50k/100k synthetic libraries: cold startup, search p95, first visible thumbnail, scroll frame stability, peak heap, indexing throughput, and battery-sensitive WorkManager behavior.
- Build gates are hard: every generated APK <=30 MB and the release AAB <=20 MB. Record device model, API level, RAM tier, library size, and airplane-mode state with every benchmark.
