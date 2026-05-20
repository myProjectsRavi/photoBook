# 🤖 Gemini Agent Guidelines for PhotoBook

Welcome, Gemini. You are analyzing the **PhotoBook** Android application. 

This document provides specialized guidelines for you to understand the architecture, style, and rules when suggesting modifications or writing code for this repository.

---

## 🏛️ Project Identity
*   **App:** PhotoBook
*   **Stack:** Kotlin, Android SDK, Jetpack Compose, Room (FTS4), Coroutines/Flow, Dagger Hilt, Coil (image loading), ML Kit (labeling, faces, text, barcodes), WorkManager, ZXing (QR generation).
*   **Core Principle:** 100% Offline, Zero network calls. High performance on 2GB RAM devices. APK size ~48MB.

## 🛑 Strict Directives

1.  **NO INTERNET:** Never suggest adding network calls, external APIs (like Google Maps API, OpenAI API, Firebase Crashlytics), or adding the `<uses-permission android:name="android.permission.INTERNET" />`.
2.  **Idiomatic Kotlin:** Use concise, idiomatic Kotlin. Favor `Flow` over `LiveData`. Use Coroutines for all async operations.
3.  **Compose First:** All UI MUST be written in Jetpack Compose. Do not use XML layouts unless absolutely necessary (e.g., specific Android manifest or theme requirements).
4.  **Database Changes:** Any changes to `PhotoEntity` or `PhotoFtsEntity` MUST be accompanied by a database migration strategy in `PhotoBookDatabase.kt`.
5.  **MVVM Architecture:** Respect the boundaries. Views (`ui/screen`) observe StateFlows from ViewModels (`ui/viewmodel`). ViewModels interact with Repositories/Use Cases. Data classes reside in `data/model`.
6.  **Crash Resilience:** The app uses a global uncaught exception handler. Wrap risky code in `runCatching`. Never allow unhandled exceptions to force-close the app.
7.  **Low-RAM Awareness:** Use `ActivityManager.isLowRamDevice` to adapt cache sizes and thumbnail resolutions. Coil uses 8% heap on low-RAM, 15% otherwise.

## 📁 Key Files to Know

*   `app/src/main/java/com/photobook/app/data/db/PhotoBookDatabase.kt` - The source of truth for local data.
*   `app/src/main/java/com/photobook/app/search/QueryParser.kt` - Complex NLP and tokenization logic for our hybrid search engine.
*   `app/src/main/java/com/photobook/app/ui/screen/MainScreen.kt` - The primary entry point for the Jetpack Compose UI (tabs: Photos, Screenshots, Search).
*   `app/src/main/java/com/photobook/app/ui/screen/PhotoViewerScreen.kt` - Full-screen viewer with 6x zoom, gesture handling, prominent share button.
*   `app/src/main/java/com/photobook/app/ui/screen/PhotoReelsScreen.kt` - Instagram-style vertical pager for immersive photo browsing.
*   `app/src/main/java/com/photobook/app/ml/TaggingWorker.kt` - Background ML indexing via WorkManager expedited work (no battery constraints on expedited).
*   `app/src/main/java/com/photobook/app/feature/qrshare/QrShareEncoder.kt` - Single-frame QR sharing (`PB1|` protocol, ≤2KB compressed payload).

## 💬 Response Format

*   When providing code snippets, always include the relevant imports.
*   If you modify a Compose file, ensure you consider State hoisting and recomposition performance.
*   Always be concise and direct. We are optimizing for speed and correctness.
*   The Vault UI has been removed. Do not re-add vault-related buttons or screens.
*   "Utilities" tab is now labeled "Screenshots" in the UI.