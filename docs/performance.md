# ⚡ Performance Engineering in PhotoBook

Delivering a "lightning-fast" experience on mobile requires aggressive optimization at the database, memory, and UI layers. PhotoBook handles libraries of 100,000+ photos with sub-second search times.

---

## 1. 🗄️ Database & FTS4

Standard `LIKE '%query%'` SQL queries are strictly forbidden in this app as they require full table scans.

*   **SQLite FTS4 (Full-Text Search):** 
    *   We utilize Room's `@Fts4` annotation (`PhotoFtsEntity.kt`) to create a virtual table.
    *   This creates an inverted index, allowing text searches against generated ML labels, location names, and dates in `O(1)` or `O(log N)` time.
    *   We utilize the `MATCH` operator in `PhotoDao.kt`.

*   **Index Builder:** 
    *   `IndexBuilder.kt` compiles multiple sources of metadata (EXIF, Geocoder, ML tags) into a single optimized search string to insert into the FTS table.

## 2. 🔄 Incremental MediaStore Sync

Scanning the user's entire photo library every time the app opens is terrible for battery and startup time.

*   **Generation Tracking:** 
    *   Since Android 11 (API 30), MediaStore supports `MediaStore.getGeneration()`.
    *   `MediaStoreScanner.kt` records the generation integer after a successful sync. On the next launch, we query `WHERE generation_added > last_known_generation`.
    *   This reduces launch sync times from seconds to single-digit milliseconds.

## 3. 🧠 Asynchronous ML Tagging

Running ML Kit on thousands of photos is CPU-intensive.

*   **WorkManager Integration:** 
    *   `TaggingWorker.kt` schedules inference in the background. 
    *   We use constraints: `RequiresBatteryNotLow` and `RequiresDeviceIdle` (optional) to ensure tagging doesn't drain the battery or cause jank while the user is actively using the app.
*   **Batching:** 
    *   We process photos in chunks to manage memory overhead and allow the worker to checkpoint its progress.

## 4. 🌍 Geocoder Memory Optimization

Loading a worldwide list of cities into memory could cause an `OutOfMemoryError` (OOM).

*   **Asset Streaming:** 
    *   Instead of parsing `cities_min.csv` fully into memory, `OfflineGeocoder.kt` streams the file line-by-line during the initial database seed, transferring the spatial data into an indexed SQLite table.

## 5. 🎨 Jetpack Compose UI

*   **Lazy Grids:** 
    *   The `PhotoGrid.kt` uses `LazyVerticalGrid`. We carefully avoid passing complex lambdas or unstable objects to the item composables to prevent unnecessary recompositions.
*   **Image Caching:** 
    *   We utilize Coil (or Glide) with optimized bitmap pooling and memory caching, requesting downsampled thumbnails based on the exact density of the `PhotoThumbnail.kt` composable.
*   **State Management:** 
    *   `MainViewModel.kt` emits immutable data classes wrapped in `StateFlow`. UI state is heavily debounced in the `SearchBar.kt` to prevent over-querying the database as the user types.