# Phase 5 — physical-device and real-world certification plan

Phase 5 starts from merged `main` commit `7bdcace691299b05aaebcd0c82ab892f381245a0` (Phase 4B).

Phase 4 established constrained Android 15 KVM-emulator evidence through 100k photos and merged the bounded-two EXIF record-build optimization. That evidence is intentionally not described as physical-device proof.

## Objective

Close the remaining runtime-evidence gap without changing production behavior unless a physical-device failure demonstrates a concrete defect.

Phase 5 is certification-first. The initial branch must not change indexing, search, Reels, Archive, Vault, Safe Share, storage behavior, ML decisions, permissions, or release architecture.

## Non-negotiable invariants

- No `android.permission.INTERNET`, accounts, telemetry, analytics, remote inference, deferred model download, or cloud dependency.
- APK <=30 MiB and release AAB <=20 MiB.
- Room/FTS remains authoritative.
- User-confirmed destructive operations remain foreground-only.
- Originals must not be modified by test tooling.
- Physical-device evidence must record device model, Android/API level, RAM tier, library size, airplane-mode state, charging state, exact commit SHA, and whether Play Services is usable.
- Emulator evidence and physical-device evidence must remain separately labelled.
- A reproducible crash, ANR, OOM, data-loss event, privacy regression, permission-state mismatch, or unexplained correctness mismatch blocks Phase 5 completion.

## Phase 5A — physical runtime replay

### API 29+ devices

Reuse the existing certified scale runner; do not create a second indexing benchmark path:

```bash
REQUIRE_PHYSICAL=1 \
LIBRARY_SIZE=10000 \
STRESS_ITERATIONS=12 \
bash tools/benchmark/run_phase3_device.sh
```

Run 50k/100k only where the device has sufficient free storage for deterministic seeding. Do not force destructive low-storage conditions on a personal phone.

The runner must fail if the selected target is an emulator when `REQUIRE_PHYSICAL=1`.

### Coverage matrix

Minimum coverage before claiming broad physical-device readiness:

1. Android 13 / API 33 — normal full-library permission path.
2. Android 14 / API 34 — full access plus selected/limited access, revoke/regrant, and reconciliation.
3. Android 15 / API 35 — low-RAM or constrained representative device; priority target for 10k/50k/100k scale replay.
4. Android 16 / API 36 — target-SDK behavior and permission/lifecycle replay.
5. At least one device/test state with airplane mode enabled.
6. At least one device without usable Play Services, confirming local capability boundaries and explicit failure states.

Android 8/9 compatibility remains required, but the Macrobenchmark producer cannot cover API 26-27. Use compatibility/lifecycle stress there rather than presenting unsupported Macrobenchmark numbers.

### API 26-27 compatibility path

Use the existing lifecycle stress harness on a dedicated test device after installing a release-compatible build:

```bash
ITERATIONS=50 \
bash tools/benchmark/phase0_device_stress.sh
```

Required evidence: launch/restore, process death, trim-memory behavior, permission changes supported by that API, crash buffer, logcat, memory snapshots, graphics evidence, and exit reasons where available.

Do not claim 10k/50k/100k Macrobenchmark throughput on API 26-27 from this path.

## Phase 5B — representative real-photo evidence

Synthetic PNGs are valid for deterministic scale/plumbing but are not proof of full-resolution decode, EXIF diversity, Reels soak, or ML/archive accuracy.

Use a private, local-only representative corpus. Do not commit personal media or derived sensitive metadata to GitHub.

Required checks:

- mixed JPEG/PNG/WebP and realistic resolutions/orientations;
- EXIF-present and EXIF-missing photos;
- location-present and location-absent photos;
- corrupt/zero-byte/unreadable media;
- long-session grid/Reels browsing with heap observation;
- process death and permission volatility;
- offline/airplane-mode operation;
- selected-photo access on Android 14+;
- representative search, favorites, Vault, Archive, edit/export, and share flows without modifying originals.

Only aggregate/redacted evidence may be checked into the repository. Never commit photo contents, file paths containing personal information, GPS coordinates, or identifiable filenames.

## Phase 5C — Archive Food/Payment ground-truth certification

This is a separate accuracy gate from performance certification.

Use a deliberately curated, non-sensitive real-image corpus and record every false positive/negative. Required minimum reporting:

- livestock/wildlife false positives: 0 in the certification corpus;
- protected/ambiguous non-food false positives: 0 in the certification corpus;
- cooked/prepared/served-food precision and recall;
- FMCG packaged-food precision and recall;
- Payment-category precision/recall where applicable;
- exact false-positive and false-negative inventory.

A false negative remains preferable to a dangerous Archive false positive.

## Acceptance gate

Phase 5 may be called complete only when:

1. Exact `main` descendant SHA is recorded for every device run.
2. Normal Android Verification remains green.
3. Release permission and APK/AAB size gates remain green.
4. Required physical-device OS coverage is documented.
5. Android 14 limited/revoked access replay passes.
6. At least one low-RAM physical-device run completes without crash/ANR/OOM.
7. Airplane-mode and no-usable-Play-Services scenarios behave explicitly and offline.
8. Representative real-photo replay completes without data loss or privacy regression.
9. Archive real-image accuracy is reported separately from performance evidence.
10. Any production fix, if one becomes necessary, is isolated into one evidence-backed change and recertified before merge.

If Phase 5A/B/C reveal no production defect, stop without a production patch.

## Current status

- Phase 0: merged and verified.
- Phase 1: merged and verified.
- Phase 2: merged and verified.
- Phase 3: merged; 10k/50k/100k constrained Android 15 KVM-emulator certification passed.
- Phase 4: merged; bounded-two EXIF record construction improved first-index-ready latency while preserving correctness/stability.
- Phase 5: certification planning only; no production behavior change approved yet.
