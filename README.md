<div align="center">

# 📸 PhotoBook

**Private, Secure, and 100% Offline Photo Search**

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](#)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-blue)](#)
[![Privacy](https://img.shields.io/badge/Privacy-Offline_First-success)](#)
[![Database](https://img.shields.io/badge/Storage-Room_FTS4-orange)](#)

*Like a phone book, but for your photos.*

</div>

---

## 🎯 The Vision

In a world where our personal memories are constantly uploaded and analyzed by the cloud, **PhotoBook** offers a sanctuary. 

PhotoBook provides an incredibly fast, intelligent photo search experience that happens **entirely on your device**. No cloud dependencies, no internet connection required, and zero data leaves your phone. It's your personal, secure, and minimalistic photo directory.

## ✨ Key Features

*   **🔒 Uncompromising Privacy:** Zero network calls. No telemetry. Your photos and metadata stay on your device.
*   **⚡ Instant Search:** Powered by **SQLite FTS4 (Full-Text Search)** for lightning-fast querying across thousands of photos.
*   **🧠 On-Device Intelligence:** Local ML Kit analysis for Image Labeling, Face Detection, and OCR (Text-in-Image).
*   **🌍 Offline Geocoding:** Converts GPS coordinates into searchable locations using a local geographic database.
*   **🔍 Hybrid Search Engine:** Combines natural language parsing (e.g., "last 2 weeks") with fuzzy text matching.
*   **🔄 Incremental Sync:** High-performance scanning using Android's `MediaStore` generations to detect changes instantly with minimal battery impact.
*   **🎨 Minimalistic UI:** A fluid, distraction-free experience built with Jetpack Compose.

## 🏗️ Technical Architecture

PhotoBook is built with a production-grade, modern Android stack:

*   **Persistence:** Room Database with **FTS4 integration** for high-performance text search.
*   **UI Toolkit:** Jetpack Compose (100% Declarative UI).
*   **Concurrency:** Kotlin Coroutines & Reactive StateFlow.
*   **Dependency Injection:** Dagger Hilt.
*   **Background Tasks:** WorkManager for energy-efficient ML tagging.
*   **Intelligence:** Google ML Kit (Vision API) for on-device analysis.

## 🚀 Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/photobook.git
   ```
2. Open in **Android Studio (Latest Version)**.
3. Build and run on Android 8.0 (API 26) or higher.

## 📄 License

This project is licensed under the MIT License.

---
<div align="center">
  <i>Your memories. Your privacy. Offline.</i>
</div>
