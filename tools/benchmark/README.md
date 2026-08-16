# Phase 0 verification tooling

Run the same Phase 0 verification entrypoint used by GitHub Actions:

```bash
bash tools/benchmark/run_phase0_local.sh
```

For host-only fixture/tooling validation when an Android SDK is unavailable:

```bash
bash tools/benchmark/run_phase0_local.sh --host-only
```

The full mode is the merge-gate path. It runs host self-tests, the Android Gradle verification task graph, release APK/AAB size gates, release APK offline-permission inspection, deterministic fixture generation, Room schema verification, and artifact-size reporting. Host-only mode is useful for tooling development but is not a substitute for the Android build gate.
