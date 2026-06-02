# 📸 PhotoBook — The Smarter, Private Way to Relive Your Memories

> **The world's fastest, 100% private photo gallery — powered by on-device AI.**
> Find any photo in milliseconds, clean up clutter, and lock away private moments.
> **Zero internet. Zero tracking. Zero compromise.**

---

## ✨ What's New in v2.0.0 — Glass UI Redesign

This is the **biggest update yet**. PhotoBook v2.0.0 introduces a completely redesigned interface with a stunning glassmorphism design language, immersive new browsing modes, and major under-the-hood performance improvements.

🎨 **Redesigned Glass UI** — Premium HSL-curated mesh gradients, frosted-glass overlays, and warm translucent surfaces throughout every screen.

📱 **Photo Reels Mode** — Browse your gallery like Instagram Stories — swipe up/down through photos in full-screen immersive mode with quick actions and info overlay.

🔍 **Google Photos-Style Viewer** — Completely overhauled photo viewer with fluid swipe-to-dismiss, pinch-to-zoom up to 8×, and double-tap zoom with spring physics.

⚡ **Instant QR Share** — Single-frame Magic QR that transfers photos instantly without data, Wi-Fi, or Bluetooth. Just scan and done.

🔐 **Biometric Vault** — Lock any photo behind fingerprint or face authentication with encrypted local storage. Auto-backup disabled to guarantee vault contents never touch Google Drive.

🧹 **Smart Declutter Swipes** — Tinder-style swipe gestures to quickly clean up duplicates, blurry shots, and burst groups.

📝 **Searchable Private Notes** — Attach secret notes to any photo and find them later via natural language search.

🏎️ **2× Faster Launch** — Baseline profiles, precomputed blur scores, Room WAL mode, and aggressive memory optimization for buttery-smooth 2GB-RAM performance.

---

## 🌟 Why PhotoBook is Different

### 🛡️ Absolute Privacy — No Internet Required
Unlike every other gallery app, PhotoBook **never** uploads your photos to the cloud. We don't even request Internet permission. Everything — AI search, face detection, OCR, barcode scanning — runs **100% on your device**. Auto-backup of private databases is completely disabled so your vault and private notes never touch Google Drive.

### ⚡ Lightning-Fast AI Search
Stop scrolling for hours. Search your photos like you're talking to a friend:

| Try searching for... | What it finds |
|---|---|
| *"Photos of my dog"* | 🐕 Object recognition matches |
| *"Receipts from last week"* | 🧾 OCR text inside photos |
| *"Selfies in Paris"* | 📍 Location-aware results |
| *"Screenshots with 'Order Confirmed'"* | 📱 Text-in-image matches |
| *"Notes about warranty"* | 🔖 Private notes you attached |

---

## 🚀 Full Feature List

### 🔍 Search Inside Your Life
- **Object Recognition** — Instantly find photos of food, sunsets, beaches, cars, pets, and 400+ categories
- **Text Search (OCR)** — Find that receipt, Wi-Fi password, or whiteboard photo by typing what's written on it
- **Map Search** — Find memories by city, state, or country — even if you were offline when you took them
- **Private Notes Search** — Attach private notes to any photo and surface them later via search
- **Date & Timeline Scrubbing** — Haptic-feedback scrub track for fast-scrolling through your timeline

### 🧹 One-Tap Gallery Cleanup
Reclaim your storage with the intelligent **De-clutter** engine:
- **Find Duplicates** — Identify exact copies wasting space
- **Similar Shots** — Pick the best from a series, delete the rest
- **Blurry Photo Finder** — Automatically detect and remove shots that didn't turn out
- **Burst Organizer** — Group rapid-fire shots with automatic hero-shot scoring
- **Swipe-to-Decide** — Tinder-style swipe left/right to keep or discard

### 📱 Immersive Photo Reels
Browse your gallery like Instagram Stories — swipe up/down through full-screen photos with quick-action overlays for favorite, share, delete, and photo info.

### 🪄 Magic QR Share
Share high-quality photos with friends nearby **without Data, Wi-Fi, or Bluetooth**. A single QR code encodes the entire photo — they scan, and it's transferred instantly.

### 📖 Auto-Curated Memories
PhotoBook's **Memory Curator** automatically finds your best trips, weekends, and holidays, and creates beautiful **Stories** and an **On-This-Day** widget to resurface your favorite moments.

### 🔐 Biometric Vault
Lock any photo behind fingerprint or face authentication. Secured with `androidx.security:security-crypto` and encrypted local Room storage. Auto-backup fully disabled.

### 🗑️ Secure Trash
Deleted photos go to a secure local trash bin with a configurable TTL (time-to-live) daily purge. Recover accidentally deleted photos before they're gone.

---

## 💎 Premium Experience & Design

### ✨ Butter-Smooth Gestures
Navigating your gallery should feel alive — like a MacBook trackpad or iPhone:
- **Swipe-to-Dismiss** — Close the fullscreen viewer with a vertical swipe. Background fades, photo shrinks under your finger, snaps back with spring physics if released early
- **Pinch-to-Zoom & Double-Tap** — Zoom up to 8× with fluid spring animation, or double-tap to zoom into the exact tap point
- **Tactile Timeline Scrubbing** — Fast-scroll through dates with custom haptic feedback

### 🎨 Glassmorphic Design Language
Every screen features a premium, cohesive look:
- HSL-curated warm mesh gradients
- Frosted-glass overlay sheets and translucent surfaces
- Rich micro-animations and smooth state transitions
- Refined typography and icon system
- PRO badge indicators on premium features

---

## ⚡ Engineered for Performance

| Optimization | Impact |
|---|---|
| **2GB RAM Target** | Smart 256px thumbnail cache, sequential ML model execution |
| **Lean APK (~48MB)** | Aggressive R8/ProGuard tree-shaking, removed redundant Compose resources |
| **Baseline Profiles** | Precompiled hot paths for instant cold-start rendering |
| **Room WAL Mode** | Write-ahead logging for concurrent DB reads during indexing |
| **Precomputed Blur Scores** | Zero runtime blur decoding — scores cached at index time |
| **ANR-Proof** | Thread-safe background Room transactions and safe migrations |
| **Memory Trim Handling** | Records flow backpressure and aggressive bitmap recycling |

---

## 🛠️ Tech Stack

- **Language:** Kotlin · Jetpack Compose · Material 3
- **Architecture:** MVVM · Hilt DI · Room · Paging 3
- **AI/ML:** ML Kit (Image Labeling, Face Detection, OCR, Barcode Scanning) — all on-device
- **Camera:** CameraX
- **Performance:** Baseline Profiles · R8/ProGuard · ABI splits (arm64-v8a, armeabi-v7a)

---

## ❤️ Free Forever. Private Forever.

PhotoBook is built for users who want the power of modern AI search without sacrificing their data.

**No subscriptions. No ads. Zero tracking. No cloud.**

> **Download PhotoBook today and see your photos in a whole new light.**
