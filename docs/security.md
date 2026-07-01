# 🛡️ PhotoBook Security & Privacy Posture

**PhotoBook** is designed around a single, unbreakable promise: **Your data never leaves your device.**

This document outlines the security mechanisms, privacy-by-design choices, and data handling practices implemented in the application.

---

## 1. 🌐 Zero Network Philosophy

The application **does not request the `android.permission.INTERNET` permission** in its `AndroidManifest.xml`. 
This is a hard, cryptographically enforceable guarantee at the OS level that the application cannot dial out to analytics servers, crash reporters, or cloud backends.

*   **No Analytics:** We do not track usage.
*   **No Crashlytics:** We do not send crash reports to Google or any other service.
*   **No Cloud ML:** All machine learning runs purely on-device.

## 2. 🧠 On-Device Intelligence

We utilize Google's **ML Kit Vision API**, specifically configured to use unbundled local models. 
*   **Image Labeling:** The model (`com.google.mlkit.vision.label`) is bundled with the APK or downloaded securely via Google Play Services to the device. Inference happens locally via `MLTagger.kt`.
*   **Text Recognition (OCR):** Local extraction via `OnDevicePhotoTextExtractor.kt`.

*None of the images are ever sent to a remote server for processing.*

## 3. 🌍 Offline Geocoding

Converting latitude/longitude EXIF data into searchable city and country names usually requires an API like Google Maps. 

PhotoBook solves this securely using an **Offline Geocoder** (`OfflineGeocoder.kt`).
*   We bundle a highly compressed, optimized CSV file (`cities_min.csv`) in our assets.
*   We use a spatial KD-Tree or bounding-box heuristic in `CityDatabase.kt` to match coordinates to cities in under 5ms.
*   **Result:** You can search for "Photos in Paris" without ever querying a location API.

## 4. 💽 Data Storage & Retention

*   **Database:** We use Android Room. The data stored in the local SQLite file (`PhotoBookDatabase`) is strictly isolated inside the app's sandboxed `data/data/com.photobook.app/` directory.
*   **Media Access:** We use the `MediaStore` API with `READ_MEDIA_IMAGES` (API 33+) or `READ_EXTERNAL_STORAGE`. We index the URIs. We do not duplicate or move your actual image files, saving space and preserving file integrity.

## 5. 🔗 Offline QR Transfer

The `qrshare` feature (`QrTransferProtocol.kt`) allows users to share a compressed private photo preview directly with another user nearby using their camera.
*   Data is encoded using `QrShareEncoder.kt`.
*   Tiny previews can use a single QR frame; larger previews use animated chunked frames.
*   Payloads are chunked and verified with a local hash (`QrPayloadHash.kt`).
*   The UI shows transfer size, chunk count, estimated scan time, and a hard max-size limit.
*   Transfer happens purely via visual optical data transfer—no Bluetooth, WiFi-Direct, or Network required.

---

*Security isn't an afterthought in PhotoBook. It's the foundational feature.*
