<div align="center">

# 📚 PhotoBook Documentation

Welcome to the internal documentation hub for the **PhotoBook** project. This directory contains detailed guides on the architecture, security practices, performance benchmarks, and instructions tailored for AI assistants contributing to the codebase.

</div>

---

## 📖 Table of Contents

### 🛡️ Core Engineering
*   **[Security Guidelines (`security.md`)](security.md)** 
    *   Understand our uncompromising 100% offline-first approach, local geocoding, and secure QR transfer protocols.
*   **[Performance Details (`performance.md`)](performance.md)**
    *   Deep dive into our implementation of SQLite FTS4, MediaStore generations, memory management, and Compose optimizations.

### 🤖 AI Contributor Contexts
These files contain specific system prompts, architectural context, and rules for various AI coding assistants.

*   **[Gemini Context (`gemini.md`)](gemini.md)** - Guidelines optimized for Google Gemini.
*   **[Claude Context (`claude.md`)](claude.md)** - Guidelines optimized for Anthropic Claude.
*   **[Jules Context (`jules.md`)](jules.md)** - Guidelines for our internal Jules assistant.

---

## 🏗️ Directory Overview

If you are a new developer or an AI assistant navigating the project, here is the high-level mapping of the `app/src/main/java/com/photobook/app` package:

| Package | Purpose |
| :--- | :--- |
| `data/` | Everything related to data persistence, SQLite/Room, FTS indexing, MediaStore, and Geocoding. |
| `di/` | Dagger Hilt module definitions. |
| `feature/` | Self-contained feature logic (`copytext`, `duplicates`, `pdf`, `qrshare`). |
| `ml/` | On-device ML Kit wrappers and background workers. |
| `search/` | Hybrid natural language parsing and filtering engine. |
| `ui/` | Jetpack Compose screens, components, and MVVM viewmodels. |
| `util/` | Constants, Date parsing, and Permissions. |

<br/>
<div align="center">
  <i>Return to <a href="../README.md">Main Repository</a></i>
</div>