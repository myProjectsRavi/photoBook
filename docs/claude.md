# 🤖 Claude Agent Guidelines for PhotoBook

Welcome, Claude. You are assisting with the **PhotoBook** Android application.

This document serves as your system prompt and context anchor for this repository.

---

## 🧭 System Context

PhotoBook is an offline-first Android application designed to index, analyze, and search through a user's local photo library without ever sending data to the cloud. Privacy and speed are paramount.

## 📜 Architectural Rules

1.  **Privacy by Design:** You must never introduce telemetry, tracking, or network API calls. The app is entirely self-contained.
2.  **Performance Focus:** When writing SQL or Room queries, remember the scale. The app handles 100,000+ photos. Utilize FTS4. Avoid `LIKE`. 
3.  **Dependency Injection:** We use Dagger Hilt. All ViewModels must be annotated with `@HiltViewModel`. All Repositories should be injected as Singletons where appropriate.
4.  **Jetpack Compose:** Build declarative UIs. Keep composables stateless where possible. Use `Modifier` effectively.

## 🔍 Code Base Mapping

*   **ML & OCR:** Look in `com.photobook.app.ml` and `com.photobook.app.feature.copytext`.
*   **Search Engine:** Look in `com.photobook.app.search`. Note how `FilterEngine` and `QueryParser` interact to resolve complex text into SQLite matches.
*   **P2P QR Sharing:** Look in `com.photobook.app.feature.qrshare`. We use dense QR payloads to bypass network requirements.

## 🛠️ Interaction Guidelines

*   If tasked with a bug fix, prioritize reading the `test/` directory to see if a unit test covers the scenario.
*   If implementing a new UI feature, adhere to the `MaterialTheme` colors and typography defined in `ui/theme/`.
*   When proposing large refactoring, suggest an iterative plan first.
*   Maintain the pristine, minimalistic nature of the codebase. Do not over-engineer solutions.