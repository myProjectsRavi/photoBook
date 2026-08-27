# PhotoBook verification tooling

## Phase 0 — deterministic merge gate

Run the same Phase 0 verification entrypoint used by GitHub Actions:

```bash
bash tools/benchmark/run_phase0_local.sh
```

For host-only fixture/tooling validation when an Android SDK is unavailable:

```bash
bash tools/benchmark/run_phase0_local.sh --host-only
```

The full mode is the merge-gate path. It runs host self-tests, the Android Gradle verification task graph, release APK/AAB size gates, release APK offline-permission inspection, deterministic fixture generation, Room schema verification, and artifact-size reporting. Host-only mode is useful for tooling development but is not a substitute for the Android build gate.

## Phase 3 — scale/device certification

`run_phase3_device.sh` drives the benchmark build against an attached Android device. The benchmark test APK creates an isolated deterministic MediaStore corpus under `Pictures/PhotoBookBenchmark/`; that seeding code is test-only and is never packaged into the production app.

Supported scale gates are 10k, 50k, and 100k photos. The Macrobenchmark suite records cold/warm startup, initial index-ready latency and throughput, first-visible-thumbnail latency, grid/search/Reels frame timing, and maximum process memory. The runner then reuses the Phase-0 lifecycle harness for process death, trim-memory pressure, permission revoke/regrant, crash/ANR/OOM checks, and captures meminfo/gfxinfo/cpu/battery/exit-info diagnostics.

For an attached emulator or development device:

```bash
LIBRARY_SIZE=10000 bash tools/benchmark/run_phase3_device.sh
```

For release-grade Phase 5 physical-device evidence, use the physical-only wrapper. It forces `REQUIRE_PHYSICAL=1`, refuses conflicting overrides, and delegates to the same certified scale runner:

```bash
LIBRARY_SIZE=10000 bash tools/benchmark/run_phase5_physical_device.sh
LIBRARY_SIZE=50000 bash tools/benchmark/run_phase5_physical_device.sh
LIBRARY_SIZE=100000 bash tools/benchmark/run_phase5_physical_device.sh
```

Recordings are written under `build/reports/phase3/`. GitHub-hosted emulator results are useful for deterministic regression detection and gross scale failures, but they are not a substitute for physical-device latency, thermals, battery, or OEM-storage certification.
