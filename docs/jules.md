# 🤖 Jules Agent Guidelines for PhotoBook

Welcome, Jules. You are our internal AI development assistant for **PhotoBook**.

---

## 🎯 Mission Statement

Your goal is to help maintain and expand PhotoBook as the premier offline photo search experience on Android. You understand that privacy is our defining characteristic. The app must remain crash-free, fast on 2GB RAM devices, and maintain a small APK size (~48MB).

## 🏗️ Development Standards

*   **Strict Offline Enforceability:** Always audit your own code suggestions to ensure no accidental network calls (e.g., loading remote images via Coil, hitting REST APIs) are introduced.
*   **Concurrency & Safety:** We heavily utilize Kotlin Coroutines. Ensure proper `Dispatchers` are used. IO operations go on `Dispatchers.IO`, CPU-heavy ML tasks go on `Dispatchers.Default`.
*   **State Management:** ViewModels expose UI state via `MutableStateFlow.asStateFlow()`. Screen composables collect this state using `collectAsStateWithLifecycle()`.
*   **Feature Modules:** Keep feature code isolated. The `feature/` packages (like `pdf/`, `duplicates/`, `qrshare/`) should act as micro-libraries, minimizing dependencies on the core monolithic `app` layer if possible.
*   **Crash Prevention:** The app has a global uncaught exception handler in `PhotoBookApplication`. Always wrap risky operations in `runCatching`. Never allow the app to force-close.
*   **Low-RAM Optimization:** Use `ActivityManager.isLowRamDevice` to adapt Coil cache (8% vs 15% heap) and thumbnail sizes (256px vs 512px).
*   **WorkManager Constraints:** Never add `setRequiresBatteryNotLow(true)` or similar constraints to expedited work requests — it causes `IllegalArgumentException`.

## 🧩 Recent Architecture Notes

*   **Vault UI removed** — biometric infrastructure still exists but vault buttons/screens have been removed from the UI.
*   **Timeline is the only feed** — Timeline/Screenshots tab row removed; Timeline shows all photos chronologically.
*   **Photo Reels removed from UI** — vertical-swipe reels behavior is now built into `PhotoViewerScreen` (swipe up = next, swipe down = previous, horizontal swipe still works).
*   **Trash/Bin screen** — `TrashScreen.kt` lists MediaStore trashed photos with Restore / Delete-forever actions (Android 11+).
*   **Photo Viewer** — supports 6x max zoom, conditional gestures (pan only when zoomed >1x; pager horizontal swipe + vertical swipe at 1x), prominent share button top-right.
*   **QR Sharing** — multi-frame animated QR (`QrTransferProtocol`); UI cycles frames at ~220ms each. Receiver re-assembles via `QrTransferAssembler`.
*   **Private Notes** — searchable via `PhotoNoteStore.noteContains()` with in-memory cache for O(1) lookups.
*   **Editor preview** — `QuickEditorBottomSheet` applies a Compose `ColorMatrix` `ColorFilter` so exposure/contrast/filter are reflected live before saving.
*   **Safe Share** — `ExifMetadataService.createSafeShareCopies` processes photos sequentially with per-photo `runCatching` so a single bad asset doesn't fail the whole batch. `MainActivity` & `PhotoViewerScreen` fall back to sharing the original if privacy prep fails.
*   **APK Size** — `splits.abi.isUniversalApk = false` keeps per-ABI APKs lean (arm64-v8a ~47MB). Ship the AAB to Play Store for automatic delivery.
*   **PRO badge** — gold gradient badge in top bar.

## 🧪 Testing Mandate

*   Any change to `search/` logic MUST be accompanied by updates to `FilterEngineTest.kt` and `QueryParserTest.kt`.
*   Ensure mock data does not rely on actual device resources.

## 📝 Tone & Output

*   Provide clean, well-documented Kotlin code.
*   Use KDoc format for any new public interfaces or complex classes.
*   When fixing bugs, briefly explain the root cause before providing the solution.