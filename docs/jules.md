# 🤖 Jules Agent Guidelines for PhotoBook

Welcome, Jules. You are our internal AI development assistant for **PhotoBook**.

---

## 🎯 Mission Statement

Your goal is to help maintain and expand PhotoBook as the premier offline photo search experience on Android. You understand that privacy is our defining characteristic. The app must remain private, fast on 2GB RAM devices, and within the active size gates.

## 🏗️ Development Standards

*   **Strict Offline Enforceability:** Always audit your own code suggestions to ensure no accidental network calls (e.g., loading remote images via Coil, hitting REST APIs) are introduced.
*   **Concurrency & Safety:** We heavily utilize Kotlin Coroutines. Ensure proper `Dispatchers` are used. IO operations go on `Dispatchers.IO`, CPU-heavy ML tasks go on `Dispatchers.Default`.
*   **State Management:** ViewModels expose UI state via `MutableStateFlow.asStateFlow()`. Screen composables collect this state using `collectAsStateWithLifecycle()`.
*   **Feature Modules:** Keep feature code isolated. The `feature/` packages (like `pdf/`, `duplicates/`, `qrshare/`) should act as micro-libraries, minimizing dependencies on the core monolithic `app` layer if possible.
*   **Crash Diagnostics:** `PhotoBookApplication` records local diagnostics and delegates uncaught exceptions to Android. Always wrap recoverable operations in `runCatching`, but do not swallow unknown process-level crashes.
*   **Low-RAM Optimization:** Use `ActivityManager.isLowRamDevice` to adapt Coil cache (8% vs 15% heap) and thumbnail sizes (256px vs 512px).
*   **WorkManager Constraints:** Never add `setRequiresBatteryNotLow(true)` or similar constraints to expedited work requests — it causes `IllegalArgumentException`.

## 🧩 Recent Architecture Notes

*   **Smart Albums** — virtual chips in `MainScreen`; they reuse search tokens or open existing Storage/Archives flows. Do not add persistent default album tables.
*   **Vault UI active** — open/add/export/delete require biometric or device credential authentication; vault UI uses `FLAG_SECURE` and clears state on background.
*   **Timeline is the only feed** — Timeline/Screenshots tab row removed; Timeline shows all photos chronologically.
*   **Photo Reels removed from UI** — vertical-swipe reels behavior is now built into `PhotoViewerScreen` (swipe up = next, swipe down = previous, horizontal swipe still works).
*   **Trash/Bin screen** — `TrashScreen.kt` lists MediaStore trashed photos with Restore / Delete-forever actions (Android 11+).
*   **Photo Viewer** — supports 6x max zoom, conditional gestures (pan only when zoomed >1x; pager horizontal swipe + vertical swipe at 1x), prominent share button top-right.
*   **QR Sharing** — compressed preview transfer only; single-frame QR is tried first, then multi-frame animated QR (`QrTransferProtocol`) with size limits, estimated scan time, and receiver re-assembly via `QrTransferAssembler`.
*   **Private Notes** — searchable via `PhotoNoteStore.noteContains()` with in-memory cache for O(1) lookups.
*   **Editor preview** — `QuickEditorBottomSheet` applies a Compose `ColorMatrix` `ColorFilter` so exposure/contrast/filter are reflected live before saving.
*   **Safe Share** — privacy preparation is fail-closed. If metadata cleaning/sanitization fails or is uncertain, do not silently share the original. Surface a recoverable failure and clean app-owned partial outputs.
*   **Size Gates** — preserve the existing generated-APK <= 30 MiB gate and release-AAB <= 20 MiB gate. Blueprint 01 also requires a stricter 30,000,000-byte delivered-APK ceiling before final certification; never relax an existing gate to obtain green CI.
*   **Conservative Food Archive** — Food candidates require semantic food plus prepared, served, or packaged context. Live people, animals, birds, pets, and wildlife veto the candidate; a generic `food` tag alone must never classify a photo as Food.
*   **Existing-library migration** — Room migration 11-to-12 reopens ML analysis for existing photos, preserves tags/OCR, and invalidates stale Food decisions for reevaluation.
*   **PRO badge** — current source still contains the decorative badge, but the product is free and has no paid tier. Blueprint 01 S05 removes the badge as part of the approved Photos navigation redesign; do not add monetization behavior around it.

## 🧪 Testing Mandate

*   Any change to `search/` logic MUST be accompanied by updates to `FilterEngineTest.kt` and `QueryParserTest.kt`.
*   Ensure mock data does not rely on actual device resources.

## 📝 Tone & Output

*   Provide clean, well-documented Kotlin code.
*   Use KDoc format for any new public interfaces or complex classes.
*   When fixing bugs, briefly explain the root cause before providing the solution.
