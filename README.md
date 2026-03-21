# PhotoBook Android App

PhotoBook is a fully offline Android photo search app implemented from the `PhotoBook_Implementation_Document.docx` specification.

## Highlights

- Private-by-design: no `INTERNET` permission
- On-device indexing from `MediaStore` + EXIF
- Temporal, folder, property, location, and ML-tag search
- Debounced in-memory search pipeline with suggestion engine
- Compose UI with onboarding, search grid, and full-screen viewer
- Background ML tagging with `WorkManager` + ML Kit
- Index persistence for fast relaunch

## Tech stack

- Kotlin + Coroutines + Flow
- Jetpack Compose + Material 3
- Hilt DI + Hilt Worker integration
- WorkManager
- Coil
- ML Kit (image labeling + face detection)

## Project structure

Core package: `com.photobook.app`

- `data/` scanner, EXIF, geocoding, index, persistence
- `search/` parser, classifier, filters, suggestions
- `ml/` label mapping, tagger, worker
- `ui/` theme, components, screens, viewmodels
- `di/` Hilt modules
- `util/` constants, date and permission utilities

## Build

1. Open in Android Studio (Hedgehog or later recommended).
2. Let Gradle sync.
3. Run the `app` configuration on a device/emulator with photo access.

## Test

Run unit tests:

```bash
./gradlew test
```

## Notes

- `cities_min.csv` is a minimal offline geocoding seed. Replace with a full GeoNames-derived dataset for broader coverage.
- `near me`, `home`, and `office` search contexts are wired through shared preferences keys (`home_lat`, `home_lon`, `office_lat`, `office_lon`, `home_country`).
