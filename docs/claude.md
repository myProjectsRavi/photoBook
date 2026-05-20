# 🤖 Claude Agent Guidelines for PhotoBook

Welcome, Claude. You are assisting with the **PhotoBook** Android application.

This document serves as your system prompt and context anchor for this repository.

---

## 🧭 System Context

PhotoBook is an offline-first Android application designed to index, analyze, and search through a user's local photo library without ever sending data to the cloud. Privacy and speed are paramount. The app is optimized for **2GB RAM devices** with adaptive caching and small APK size (~48MB).

## 📜 Architectural Rules

1.  **Privacy by Design:** You must never introduce telemetry, tracking, or network API calls. The app is entirely self-contained.
2.  **Performance Focus:** When writing SQL or Room queries, remember the scale. The app handles 100,000+ photos. Utilize FTS4. Avoid `LIKE`. Use in-memory caches (e.g., `PhotoNoteStore`) for O(1) lookups during search.
3.  **Dependency Injection:** We use Dagger Hilt. All ViewModels must be annotated with `@HiltViewModel`. All Repositories should be injected as Singletons where appropriate.
4.  **Jetpack Compose:** Build declarative UIs. Keep composables stateless where possible. Use `Modifier` effectively.
5.  **Crash Resilience:** The app has a global uncaught exception handler in `PhotoBookApplication`. Wrap risky operations in `runCatching`. The app must never force-close.
6.  **Low-RAM Optimization:** Use `ActivityManager.isLowRamDevice` to adapt image cache sizes (8% vs 15% of heap) and thumbnail resolutions (256px vs 512px).

## 🔍 Code Base Mapping

*   **ML & OCR:** Look in `com.photobook.app.ml` and `com.photobook.app.feature.copytext`.
*   **Search Engine:** Look in `com.photobook.app.search`. Note how `FilterEngine` and `QueryParser` interact to resolve complex text into SQLite matches. Private notes are also searchable via `PhotoNoteStore.noteContains()`.
*   **P2P QR Sharing:** Look in `com.photobook.app.feature.qrshare`. Uses single-frame compressed QR (`PB1|` protocol) for instant transfer without network.
*   **Photo Reels:** `com.photobook.app.ui.screen.PhotoReelsScreen` — Instagram-style vertical pager for immersive browsing.
*   **Photo Viewer:** `PhotoViewerScreen` supports 6x zoom, conditional gesture handling (pan only when zoomed, swipe at 1x), prominent share button.
*   **Background Tagging:** `TaggingWorker` uses WorkManager expedited work. Never add battery constraints to expedited requests.

## 🛠️ Interaction Guidelines

*   If tasked with a bug fix, prioritize reading the `test/` directory to see if a unit test covers the scenario.
*   If implementing a new UI feature, adhere to the `MaterialTheme` colors and typography defined in `ui/theme/`.
*   When proposing large refactoring, suggest an iterative plan first.
*   Maintain the pristine, minimalistic nature of the codebase. Do not over-engineer solutions.
*   The Vault feature UI has been removed (biometric unlock still exists for future use). Do not re-add Vault buttons.
*   The "Utilities" tab is now labeled "Screenshots" in the UI.