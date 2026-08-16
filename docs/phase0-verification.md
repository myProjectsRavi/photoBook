# Phase 0 Verification Contract

Phase 0 exists to measure and protect PhotoBook before any core runtime architecture is replaced. It intentionally avoids Search v2, PhotoIndex v2, startup behavior changes, Reels runtime changes, Storage Optimizer runtime changes, Vault crypto migration, Safe Share behavior fixes, Archive classifier behavior changes, or other user-visible production refactors.

## Non-negotiable invariants

- PhotoBook remains fully usable without `android.permission.INTERNET`.
- No account, cloud storage, telemetry, remote inference, deferred model download, or analytics dependency is introduced.
- Release limits remain hard gates: APK <= 30 MiB and release AAB <= 20 MiB.
- Test, fixture, benchmark, and stress tooling must not enter the production release artifact.
- Originals are never intentionally modified by verification tooling.
- A reproducible crash, ANR, OOM, data-loss regression, privacy regression, or unexplained behavior mismatch blocks later optimization phases.
- Archive safety is precision-first: uncertainty means do not archive. Food-positive acceptance is limited to cooked/prepared/served food and FMCG packaged food; livestock, wildlife, raw ingredients, favorites, sensitive documents, corrupt media, and uncertain media are negative/protected fixtures.

## Single verification entry point

The same script is used locally and by GitHub Actions:

```bash
bash tools/benchmark/run_phase0_local.sh
```

Host-only tooling validation is available when an Android SDK is unavailable:

```bash
bash tools/benchmark/run_phase0_local.sh --host-only
```

Full mode is the Android build gate. It runs host-tool self-tests, unit tests, lint, debug and instrumentation-source assembly, benchmark and release assembly, release APK/AAB size gates, benchmark-source compilation, Room schema verification, deterministic fixture generation, release-APK permission inspection, and artifact-size reporting.

## Authoritative Android build evidence — 2026-08-16

A full GitHub-hosted Android verification run completed successfully for Phase 0 commit `e5f719dba3174c1b015bd186449bce6bc0a6e495` (run `31952683210`). This run used the same `run_phase0_local.sh` entry point documented above.

Verified results:

- Gradle: `BUILD SUCCESSFUL`; 238 actionable tasks executed.
- JVM unit tests: 55 tests, 0 failures, 0 ignored.
- Android lint: 0 errors; 136 non-blocking warnings remain as engineering debt.
- Debug APK assembly: passed.
- Debug Android-test APK assembly: passed; this proves instrumentation sources compile, not that device instrumentation executed.
- Release-like benchmark APK assembly: passed.
- Macrobenchmark producer/test APK compilation: passed via `:baselineprofile:assembleBenchmarkBenchmark`.
- Release APK assembly: passed for arm64-v8a and armeabi-v7a.
- Release AAB assembly: passed.
- Room schema v12 export: present and packaged as verification evidence.
- Host Phase-0 self-tests: 12/12 passed.
- Deterministic 303-record smoke corpus generation: passed.
- Both generated release APKs request no `android.permission.INTERNET`.

Release artifact sizes from that run:

- arm64-v8a release APK: 11,075,898 bytes.
- armeabi-v7a release APK: 9,327,498 bytes.
- release AAB: 17,471,358 bytes.

All release artifacts are within the <=30 MiB APK and <=20 MiB AAB gates. The AAB is the tighter current constraint and should continue to be monitored on every later runtime dependency or model change.

The evidence artifact for the successful run was uploaded by GitHub Actions with SHA-256 digest `b18ddb2f1d210c505a7f465601c4be6bb1569bbf54f420e0aa42c5adee1e40d2`.

## Room schema safety

Room schema export is enabled and the schema directory is `app/schemas`.

The current v12 schema is the version-controlled migration baseline. Historical schemas that predate Phase 0 were not previously exported and must not be fabricated. No future database version bump is approved until every upgrade path PhotoBook intends to preserve has explicit migration evidence.

`MigrationTestHelper` is wired against the exported schema so future version changes can add `runMigrationsAndValidate` coverage rather than relying on upgrade-by-assumption.

## Deterministic scale fixtures

Generate metadata-only 10k/50k/100k corpora:

```bash
python3 tools/benchmark/generate_media_fixtures.py
```

Generate device-seed media:

```bash
python3 tools/benchmark/generate_media_fixtures.py \
  --count 1000 \
  --output build/phase0-fixtures \
  --write-media
```

The default seed is fixed (`20260816`). The weighted scenario cycle is 101 records; smaller custom counts are valid partial corpora.

Archive-relevant records carry explicit semantic ground truth. Livestock negatives include cow, buffalo, goat, sheep, lamb, cattle, bull, horse, hen, rooster, live chicken, and generic livestock. Positive Food subjects are restricted to cooked/prepared/served meals and FMCG packaged food examples.

Favorite protection has higher precedence than category-positive expectations: if any generated Food or Payment fixture is also a favorite, its expected Archive outcome is `never_archive`.

Synthetic tiny PNGs are for deterministic plumbing, MediaStore seeding, lifecycle testing, and scale testing. They are not evidence for real-photo decode throughput, Reels performance, Storage Optimizer throughput, or ML accuracy.

Host tooling is self-tested with:

```bash
python3 -m unittest tools/benchmark/test_phase0_tools.py
```

The suite protects scenario distribution, valid partial counts, large/corrupt/zero-byte reachability, PNG structure/CRC, livestock coverage, favorite precedence, narrow Food-positive expectations, deterministic generation, and artifact/model-size classification.

## Macrobenchmarks

`PhotoBookMacrobenchmark` provides measurement coverage for:

- cold startup;
- warm startup;
- grid frame timing;
- search typing/result frame timing;
- Reels vertical-swipe frame timing.

Interaction benchmarks wait for PhotoBook's existing enabled Reel Browsing action so loading/welcome state cannot be mistaken for a ready gallery. Search uses the exposed editable node, and Reels resolves the clickable action ancestor instead of assuming a label node is clickable.

The benchmark app is release-derived, profileable, non-debuggable, and debug-signed only for test-device installation. Macrobenchmark runtime tooling uses `androidx.benchmark:benchmark-macro-junit4:1.4.1`; the production-facing Baseline Profile Gradle plugin remains at the repository's existing `1.3.3` during Phase 0. ProfileInstaller `1.4.1` is benchmark-only.

## Physical-device boundary

The successful Android build validates compilation, JVM tests, lint, release packaging, size gates, Room schema export, benchmark source compilation, and offline release-manifest invariants. It does **not** certify runtime behavior on a physical device.

Still required before making performance or zero-crash claims:

- Android instrumentation execution on representative devices/emulators;
- constrained 2-3 GB RAM device testing;
- mainstream 4-6 GB and high-end 8+ GB coverage;
- Android 8/9 compatibility stress (Macrobenchmark producer currently has minSdk 28, so API 26-27 require compatibility/stress coverage rather than Macrobenchmark numbers);
- Android 13, Android 14 selected/partial photo access, Android 15, and Android 16/API 36 coverage;
- long-session Reels soak and heap-stability evidence;
- lifecycle/process-death/permission-volatility stress;
- representative full-resolution real-photo performance data;
- separately curated real-image Food/Payment ground-truth certification.

Do not convert provisional performance targets into product claims until those measurements exist.

## Device lifecycle / low-memory stress

On a dedicated test device:

```bash
ITERATIONS=50 bash tools/benchmark/phase0_device_stress.sh
```

The harness exercises repeated launches, foreground/background restoration, trim-memory pressure, process death, permission revoke/regrant, and captures crash-buffer, logcat, memory, graphics, and exit-reason evidence.

Do not run destructive low-storage simulations on a personal device. Low-storage, MediaStore process-death, and pending-output fault injection should use dedicated test devices.

## Artifact-size evidence

After building:

```bash
python3 tools/benchmark/report_artifact_sizes.py \
  --root app/build/outputs \
  --output build/reports/phase0/artifact-sizes.json
```

The report tracks total bytes, compressed/uncompressed component groups, largest entries, optional baseline deltas, and bundled model bytes separately from generic assets. The <=30 MiB APK hard gate scans release APKs only; benchmark, debug, and instrumentation APKs remain observable but cannot falsely fail the shipped-app contract.

## Archive safety baseline

Phase 0 does not alter Archive decisions. It records characterization tests and adversarial fixtures.

Current production mapping canonicalizes several live-subject labels such as cattle, bull, horse, bird, animal, people, and pet into veto categories. The corpus deliberately includes additional vocabulary not yet guaranteed by production mapping, including cow, goat, sheep, buffalo, and generic livestock. Those remain explicit Phase-1 correctness blockers rather than being silently changed in Phase 0.

Before Food Archive can be described as production-certified, a real-image ground-truth evaluation must report at minimum:

- livestock/wildlife false positives: 0 in the certification corpus;
- protected/ambiguous non-food false positives: 0 in the certification corpus;
- cooked/prepared/served-food precision and recall;
- FMCG packaged-food precision and recall;
- every false positive and false negative, not only aggregate accuracy.

A false negative is preferable to a dangerous Food false positive.

## Phase 0 exit interpretation

The host-tooling and Android build/package gates now have reproducible passing evidence. This is sufficient to establish a trustworthy implementation baseline and review the Phase 0 PR for merge.

Physical-device performance, soak, and crash/ANR evidence remains a prerequisite for making device-performance claims and should be collected before approving production architecture cutovers that depend on those measurements. Phase 0 passing does not by itself certify PhotoBook as zero-crash, 2 GB-optimized, or Food/Payment-accuracy certified.
