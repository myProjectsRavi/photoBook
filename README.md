<div align="center">

# 📸 PhotoBook

**Private, Secure, and 100% Offline Photo Search**

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](#)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-blue)](#)
[![Privacy](https://img.shields.io/badge/Privacy-Offline_First-success)](#)

*Like a phone book, but for your photos.*

</div>

---

## 🎯 The Vision

In a world where our personal memories are constantly uploaded, analyzed, and monetized by the cloud, **PhotoBook** takes a different approach. 

PhotoBook is designed with a single, uncompromising goal: to provide an incredibly fast, intelligent photo search experience that happens **entirely on your device**. No cloud dependencies, no internet connection required, and zero data leaves your phone. It's your personal, secure, and minimalistic photo directory.

## ✨ Key Features

*   **🔒 Uncompromising Privacy:** Zero network calls for analysis or geocoding. Your photos stay yours.
*   **🧠 On-Device Intelligence:** Powered by on-device ML Kit for real-time Image Labeling, Face Detection, and Optical Character Recognition (OCR).
*   **🌍 Offline Geocoding:** Converts raw GPS coordinates into searchable city and state names using a completely offline, local geographic database.
*   **🔍 Advanced Search Engine:** A custom-built, natural language search pipeline. Search by location, dates ("last 2 weeks"), objects, text within images, or technical properties ("hdr", "large").
*   **🔋 Battery Efficient:** Intelligent background processing via WorkManager ensures ML tagging happens without draining your battery.
*   **🎨 Minimalistic UI:** Built with Jetpack Compose for a fluid, intuitive, and distraction-free user experience.

## 🏗️ Technical Architecture

PhotoBook is built using the latest and greatest modern Android development standards:

*   **UI Toolkit:** Jetpack Compose
*   **Language:** Kotlin
*   **Asynchrony:** Coroutines & Flow (StateFlow)
*   **Dependency Injection:** Dagger Hilt
*   **Background Processing:** WorkManager
*   **Machine Learning:** Google ML Kit (Vision API)
*   **Architecture Pattern:** Clean MVVM (Model-View-ViewModel)

## 🚀 Getting Started

To build and run PhotoBook locally:

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/photobook.git
   ```
2. Open the project in **Android Studio (Latest Version)**.
3. Sync the Gradle project.
4. Build and run on an emulator or physical device running Android 8.0 (API level 26) or higher.

## 🤝 Contributing

While this is a personal project driven by a specific vision, feedback and contributions that align with the core tenets of **privacy, security, and offline-first performance** are always welcome.

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---
<div align="center">
  <i>Built with privacy in mind.</i>
</div>
