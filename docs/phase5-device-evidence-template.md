# Phase 5 — privacy-safe device evidence template

Use this template only for Phase 5 physical-device, compatibility, representative-real-photo, and Archive accuracy certification.

This file is a reporting structure, not evidence by itself. Never mark a gate PASS without the corresponding local run/artifact.

## Privacy rules before recording anything

Do **not** commit any of the following:

- photo or video contents;
- personal or identifiable filenames;
- absolute/local filesystem paths containing user or machine names;
- GPS coordinates or raw EXIF location;
- contact/account identifiers;
- screenshots containing personal media, notifications, accounts, or device identifiers;
- serial numbers, Android IDs, advertising IDs, Wi-Fi/Bluetooth identifiers, phone numbers, or other unique device identifiers;
- raw log lines that contain any of the above.

Prefer counts, aggregate timings, pass/fail results, coarse device class, Android/API level, RAM tier, and redacted failure descriptions.

Before committing evidence, review every text artifact for sensitive strings. Keep raw media, raw EXIF, and unredacted logs local only.

## Run identity

- Evidence date: `<YYYY-MM-DD>`
- Exact Git commit SHA: `<40-char SHA>`
- Test mode: `<deterministic-scale | compatibility | representative-real-photo | archive-ground-truth>`
- Physical device: `<yes/no>`
- Emulator: `<yes/no>`
- Manufacturer/model family: `<non-unique model name only>`
- Android version / API: `<e.g. Android 15 / API 35>`
- ABI: `<arm64-v8a | armeabi-v7a | x86_64>`
- RAM: `<aggregate total RAM or RAM tier>`
- Charging state: `<unplugged | charging>`
- Airplane mode: `<on/off>`
- Play Services usable: `<yes/no/not-applicable>`
- Visible library size: `<aggregate count only>`

Do not record device serial numbers or other unique identifiers.

## Preflight safety gate

- [ ] Exact SHA recorded.
- [ ] Normal Android Verification is green for this SHA or its exact production tree.
- [ ] Release APK permission check confirms no `android.permission.INTERNET`.
- [ ] APK <= 30 MiB and release AAB <= 20 MiB.
- [ ] No personal media is present for deterministic `PBENCH_*` scale seeding.
- [ ] Deterministic scale device/profile is dedicated or wiped.
- [ ] `Pictures/PhotoBookBenchmark/` contains no foreign/non-`PBENCH_*` media.
- [ ] Sufficient free storage exists for the requested fixture count.
- [ ] Raw evidence will remain local until redacted.

Any failed preflight item stops deterministic scale certification.

## Phase 5A — API 29+ deterministic physical scale replay

Use only on a dedicated/wiped physical test device or isolated test profile with no non-benchmark image rows visible to MediaStore.

```bash
REQUIRE_PHYSICAL=1 \
LIBRARY_SIZE=10000 \
STRESS_ITERATIONS=12 \
bash tools/benchmark/run_phase3_device.sh
```

Repeat at 50k/100k only when the dedicated device has sufficient free storage.

### Result summary

- Requested library size: `<10k | 50k | 100k>`
- Actual verified library size: `<count>`
- KVM/emulator flag: `<must prove physical; emulator must fail>`
- Seven Macrobenchmarks: `<PASS/FAIL>`
- Lifecycle stress: `<N/N completed>`
- Crash: `<none | redacted summary>`
- ANR: `<none | redacted summary>`
- OOM: `<none | redacted summary>`
- Data loss: `<none | redacted summary>`
- Permission mismatch: `<none | redacted summary>`
- Benchmark-owned fixture cleanup: `<PASS/FAIL>`

### Performance summary

Record only aggregate benchmark outputs needed for comparison. Do not include raw user/media paths.

- Initial index-ready: `<ms>`
- Cold startup: `<aggregate result>`
- Warm startup: `<aggregate result>`
- First visible thumbnail: `<p50/p95/max ms>`
- Grid frame timing: `<aggregate result>`
- Search frame timing: `<aggregate result>`
- Reels frame timing: `<aggregate result>`
- Peak memory observations: `<aggregate result>`

## Android 14 selected/limited-access replay

- Full access launch: `<PASS/FAIL>`
- Change to selected/limited access: `<PASS/FAIL>`
- UI reflects limited access correctly: `<PASS/FAIL>`
- Reconciliation after access change: `<PASS/FAIL>`
- Revoke access: `<PASS/FAIL>`
- App returns to permission-safe state without stale inaccessible media: `<PASS/FAIL>`
- Regrant access and reconcile: `<PASS/FAIL>`
- Crash/ANR/OOM/data loss: `<none | redacted summary>`

## Android 8/9 compatibility path

Do not report Macrobenchmark throughput for API 26-27.

```bash
ITERATIONS=50 bash tools/benchmark/phase0_device_stress.sh
```

- Android/API: `<version>`
- 50 lifecycle iterations: `<PASS/FAIL>`
- Process-death/restore: `<PASS/FAIL>`
- Trim-memory behavior: `<PASS/FAIL>`
- Supported permission transitions: `<PASS/FAIL>`
- Crash/ANR/OOM: `<none | redacted summary>`

## Phase 5B — representative real-photo replay

Use a private local-only corpus. Never use the replacing deterministic benchmark seeder on this corpus.

### Corpus description — aggregate only

- Total images: `<count>`
- JPEG: `<count>`
- PNG: `<count>`
- WebP: `<count>`
- EXIF present / absent: `<counts>`
- Location metadata present / absent: `<counts only; never coordinates>`
- Portrait / landscape / rotated: `<counts>`
- Corrupt/zero-byte/unreadable fixtures: `<counts>`
- Approximate resolution buckets: `<aggregate counts>`

### Functional replay

- Grid browsing: `<PASS/FAIL>`
- Long Reels soak: `<duration/iterations + PASS/FAIL>`
- Search: `<PASS/FAIL>`
- Favorites: `<PASS/FAIL>`
- Vault: `<PASS/FAIL>`
- Archive review: `<PASS/FAIL>`
- Edit/export: `<PASS/FAIL>`
- Safe share: `<PASS/FAIL>`
- Originals unchanged: `<PASS/FAIL>`
- Process death: `<PASS/FAIL>`
- Permission volatility: `<PASS/FAIL>`
- Airplane mode: `<PASS/FAIL>`
- No usable Play Services state: `<PASS/FAIL>`
- Crash/ANR/OOM/data loss/privacy regression: `<none | redacted summary>`

## Phase 5C — Archive Food/Payment ground truth

Use only curated, non-sensitive images.

### Food

- Total ground-truth food images: `<count>`
- True positives: `<count>`
- False positives: `<count>`
- False negatives: `<count>`
- Precision: `<value>`
- Recall: `<value>`
- Livestock/wildlife false positives: `<must be 0>`
- Protected/ambiguous non-food false positives: `<must be 0>`
- Cooked/prepared/served-food result: `<aggregate metrics>`
- FMCG packaged-food result: `<aggregate metrics>`

### Payment

- Total ground-truth payment images: `<count>`
- True positives: `<count>`
- False positives: `<count>`
- False negatives: `<count>`
- Precision: `<value>`
- Recall: `<value>`

For false positives/negatives, record only a non-identifying category description unless the test image is synthetic/non-sensitive and explicitly safe to publish.

## Final Phase 5 gate

- [ ] Every physical run identifies an exact main-descendant SHA.
- [ ] Required Android/API physical coverage is complete.
- [ ] At least one low-RAM physical run completes without crash/ANR/OOM.
- [ ] Android 14 limited/revoked/regrant replay passes.
- [ ] Airplane-mode path passes.
- [ ] No-usable-Play-Services behavior is explicit and offline.
- [ ] Representative real-photo replay passes without data loss/privacy regression.
- [ ] Archive ground-truth results are reported separately.
- [ ] Raw personal media/EXIF/logs remain uncommitted.
- [ ] Any production defect discovered during certification is isolated into its own evidence-backed candidate and recertified.

If certification reveals no production defect, Phase 5 should complete without a production patch.
