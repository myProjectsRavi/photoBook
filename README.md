<div align="center">

<img src="https://raw.githubusercontent.com/tandpfun/skill-icons/main/icons/AndroidStudio-Dark.svg" width="80" height="80" alt="Android Studio" />
<br/>

# 📸 PhotoBook
**Private, Secure, and 100% Offline Photo Search Engine**

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-blue?style=for-the-badge)](#)
[![Privacy](https://img.shields.io/badge/Privacy-Offline_First-success?style=for-the-badge)](#)
[![Database](https://img.shields.io/badge/Storage-Room_FTS4-orange?style=for-the-badge)](#)

*Like a phone book, but for your photos.*

[**Explore Documentation**](docs/README.md) • [**Security**](docs/security.md) • [**Performance**](docs/performance.md)

</div>

---

## 🎯 The Vision

In a world where our personal memories are constantly uploaded and analyzed by the cloud, **PhotoBook** offers a sanctuary. 

PhotoBook provides an incredibly fast, intelligent photo search experience that happens **entirely on your device**. No cloud dependencies, no internet connection required, and zero data leaves your phone. It's your personal, secure, and minimalistic photo directory.

---

## ✨ Key Features

| Feature | Description |
|:---:|:---|
| **🔒 Uncompromising Privacy** | Zero network calls. No telemetry. Your photos and metadata stay on your device. Period. |
| **⚡ Instant Search** | Powered by **SQLite FTS4 (Full-Text Search)** for lightning-fast querying across thousands of photos. |
| **🧠 On-Device Intelligence** | Local ML Kit analysis for Image Labeling, Face Detection, and OCR (Text-in-Image). |
| **🌍 Offline Geocoding** | Converts GPS coordinates into searchable locations using a blazing-fast local `cities_min.csv` database. |
| **🔍 Hybrid Search Engine** | Combines natural language parsing (e.g., "last 2 weeks") with fuzzy text matching and token classification. |
| **🔄 Incremental Sync** | High-performance scanning using Android's `MediaStore` generations to detect changes instantly. |
| **📄 PDF Export** | Generate beautiful, shareable PDFs of your photo collections or search results instantly. |
| **🔗 Offline QR Share** | Securely share photos peer-to-peer using high-density QR payload hashing and transfer protocol. |
| **👯 Duplicate Finder** | Free up space with our intelligent, perceptual hash-based duplicate photo detection. |
| **📋 Extract & Copy Text** | Point, shoot, and seamlessly extract text from images directly to your clipboard using on-device ML. |
| **🎨 Minimalistic UI** | A fluid, distraction-free experience built 100% with Jetpack Compose. |

---

## 🏗️ Technical Architecture

PhotoBook is engineered to be a production-grade, modern Android application showcasing the pinnacle of Android development standards:

### 🧩 Core Stack
- **UI Toolkit:** Jetpack Compose (100% Declarative UI)
- **Concurrency:** Kotlin Coroutines & Reactive StateFlow
- **Dependency Injection:** Dagger Hilt
- **Persistence:** Room Database with **FTS4 integration**
- **Background Tasks:** WorkManager for energy-efficient background ML tagging
- **Intelligence:** Google ML Kit (Vision API) for on-device analysis

### 📂 Feature Modules
The architecture is cleanly separated into specialized feature packages under `com.photobook.app.feature`:
- `copytext` - OCR and Text formatting coordinator.
- `duplicates` - Fast duplicate photo detection algorithms.
- `pdf` - PDF generation service.
- `qrshare` - P2P QR encoded payload sharing.
- `search` - Advanced FTS engine, query parsing, and suggestion engine.
- `ml` - ML tagging orchestration and worker logic.

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** (Latest Stable Version)
- **JDK 17+**
- Android device or emulator running **API 26 (Android 8.0)** or higher.

### Installation
1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/photobook.git
   cd photobook
   ```
2. **Open the Project:**
   Open the root directory in Android Studio.
3. **Build & Run:**
   Sync the Gradle project and hit the 🟢 **Run** button.

---

## 🔐 Release Signing

To build a signed release bundle (AAB):

1. Copy `keystore.properties.example` to `keystore.properties`.
2. Fill in the real values. **Ensure you use an absolute path for `storeFile`.**
3. Execute the release build:
   ```bash
   ./gradlew bundleRelease
   ```
*Note: If you use Gradle injected signing properties (`android.injected.signing.*`), the keystore path must remain absolute.*

---

## 📚 Documentation Reference

Dive deeper into PhotoBook's engineering and philosophies:

*   [**Main Docs Index**](docs/README.md)
*   [**Security Philosophy**](docs/security.md)
*   [**Performance Tuning**](docs/performance.md)
*   **AI Contexts:** [Gemini](docs/gemini.md) | [Claude](docs/claude.md) | [Jules](docs/jules.md)

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

<br/>

<div align="center">
  <i>Your memories. Your privacy. Offline.</i>
  <br/><br/>
  <b>Built with ❤️ by passionate engineers.</b>
</div>