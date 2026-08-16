# Phase 0 Verification Contract

Phase 0 exists to measure and protect PhotoBook before any core runtime architecture is replaced. It intentionally avoids Search v2, PhotoIndex v2, startup behavior changes, Reels pipeline changes, Vault crypto migration, Archive classifier behavior changes, or other user-visible production refactors.

## Non-negotiable invariants

- PhotoBook remains fully usable without `android.permission.INTERNET`.
- No account, cloud storage, telemetry, remote inference, deferred model download, or analytics dependency is introduced.
- Existing release limits remain hard gates: APK <= 30 MB and release AAB <= 20 MB.
- Test, fixture, benchmark, and stress tooling must not enter the shipped artifact.
- Originals are never intentionally modified by verification tooling.
- A reproducible crash, ANR, OOM, data-loss regression, privacy regression, or unexplained behavior mismatch blocks later optimization phases.
- Archive safety is precision-first: uncertainty means do not archive. Food-positive acceptance is limited to cooked/prepared/served food and FMCG packaged food; livestock, wildlife, raw ingredients, favorites, sensitive documents, corrupt media, and uncertain media are negative/protected fixtures.

## CI verification

`Android Verification` is designed to perform host-tool self-tests, unit tests, lint, debug APK assembly, debug instrumentation-source assembly, a release-like benchmark APK, unsigned release-bundle verification when no production keystore is supplied, APK/AAB size gates, benchmark-source compilation, deterministic fixture generation, Room schema export verification, artifact-size composition reporting, and static inspection that the release-like APK does not request `android.permission.INTERNET`.

Production signing is unchanged when `keystore.properties` is present. An unsigned CI bundle is a verification artifact only and must never be distributed as a production release.

Current repository CI execution must not be described as green until GitHub can start hosted runners successfully. A runner/billing failure is infrastructure evidence, not a passing or failing Android build.

## Room schema safety

Room schema export is enabled and the schema directory is `app/schemas`.

The current schema becomes the starting point for version-controlled migration evidence. Historical schemas that predate Phase 0 were not previously exported, so they must be backfilled before any future database schema change is approved. No database version bump is permitted until migration tests can validate every supported upgrade path that PhotoBook intends to preserve.

`MigrationTestHelper` is wired against the exported database schema so future version changes can add explicit `runMigrationsAndValidate` coverage instead of relying on upgrade-by-assumption.

## Deterministic scale fixtures

Generate metadata-only 10k/50k/100k corpora:

```bash
python3 tools/benchmark/generate_media_fixtures.py
```

Generate a smaller device-seed corpus containing tiny valid PNGs plus corrupt/zero-byte cases:

```bash
python3 tools/benchmark/generate_media_fixtures.py \
  --count 1000 \
  --output build/phase0-fixtures \
  --write-media
```

The default seed is fixed (`20260816`) so before/after runs see identical ordering and scenario distribution. Change the seed only when intentionally creating a second corpus.

Each archive-relevant record also carries an explicit semantic ground-truth subject. Livestock negatives include cow, buffalo, goat, sheep, lamb, cattle, bull, horse, hen, rooster, live chicken, and generic livestock cases. Positive Food subjects are restricted to cooked/prepared/served meals and FMCG packaged food examples.

Synthetic pixels are for scale/lifecycle testing, not ML accuracy claims. Food/payment accuracy certification must also use a separately curated real-image corpus with explicit ground truth. That corpus must include livestock and ambiguous negatives and must not be shipped inside the app.

Host tooling is self-tested with:

```bash
python3 -m unittest tools/benchmark/test_phase0_tools.py
```

Those tests protect deterministic scenario coverage, large/corrupt/zero-byte reachability, livestock ground-truth coverage, narrow Food-positive expectations, and artifact model-size categorization.

## Macrobenchmarks

`PhotoBookMacrobenchmark` records:

- cold startup timing;
- warm startup timing;
- grid frame timing;
- search typing/result frame timing;
- Reels vertical-swipe frame timing.

Interaction benchmarks wait for PhotoBook's existing enabled Reel Browsing action before measurement so loading/welcome state cannot be mistaken for a ready gallery. Search uses the exposed editable text node rather than placeholder text, and Reels resolves the clickable action ancestor rather than assuming a label node itself is clickable.

Use a release-like `benchmark` app variant signed with the debug key solely so benchmark devices can install it. The benchmark variant is `profileable` and non-debuggable; the benchmark test process itself remains debuggable.

The benchmark/Baseline Profile tooling is pinned to stable `1.4.1`. ProfileInstaller `1.4.1` is added only to the benchmark variant for Macrobenchmark profile/shader-cache control; it is intentionally excluded from production release artifacts to protect shipped size.

Run benchmarks on physical devices for release decisions. Emulators are useful for compilation/smoke verification but are not sufficient evidence for the 2 GB-class-device promise.

Recommended physical matrix:

- constrained: 2-3 GB RAM, 60 Hz;
- mainstream: 4-6 GB RAM;
- high-end: 8+ GB RAM;
- Android 8/9 for minimum-era behavior;
- Android 13;
- Android 14 selected/partial photo access;
- Android 15;
- Android 16 / API 36.

For startup comparisons, use at least 20 measured iterations in the release certification run and store raw Macrobenchmark JSON/traces. Phase-0 source defaults are intentionally shorter for developer iteration.

## Device lifecycle / low-memory stress

On a dedicated test device with the benchmark/debug build installed:

```bash
ITERATIONS=50 tools/benchmark/phase0_device_stress.sh
```

The harness exercises repeated cold launches, foreground/background recovery, Android trim-memory pressure, process death, permission revoke/regrant, and captures crash-buffer, logcat, memory, graphics, and exit-reason evidence.

Do not run destructive low-storage simulations on a personal device. Low-storage, MediaStore process-death, and pending-output fault injection require dedicated test-device scenarios and are release blockers once their operation-specific harnesses are introduced.

## Artifact size evidence

After building:

```bash
python3 tools/benchmark/report_artifact_sizes.py \
  --root app/build/outputs \
  --output build/reports/phase0/artifact-sizes.json
```

The report tracks total bytes, compressed/uncompressed component groups, largest entries, optional baseline deltas, and bundled model bytes as a distinct category rather than hiding model growth inside generic assets. Any runtime dependency addition in later phases requires explicit size justification and before/after evidence.

## Archive safety baseline

Phase 0 does not change Archive decisions. It records and expands characterization tests and adversarial fixtures.

Current code already canonicalizes several live-subject labels such as cattle, bull, horse, bird, animal, people, and pet into veto categories. The adversarial corpus also names livestock vocabulary that is not yet guaranteed by the current mapping, including cow, goat, sheep, buffalo, and the generic term livestock. These are explicit Phase-1 correctness blockers rather than assumptions.

Before Food Archive can be described as production-certified, a real-image ground-truth evaluation must report at minimum:

- false-positive count for livestock/wildlife: 0 in the certification corpus;
- false-positive count for protected/ambiguous non-food: 0 in the certification corpus;
- cooked/prepared/served-food precision and recall;
- FMCG packaged-food precision and recall;
- complete list of false negatives and false positives, not only aggregate accuracy.

A false negative is preferable to a dangerous Food false positive.

## Baseline result format

Every before/after performance report should include the same fields:

- git SHA;
- device model, Android/API version, RAM class, refresh rate;
- fixture count and seed;
- build variant and minification state;
- cold/warm startup raw results and p50/p95 where enough samples exist;
- search latency/frame results;
- grid and Reels frame/jank results;
- peak/stabilized heap and long-session trend;
- crash/ANR/OOM count;
- APK/AAB bytes and component deltas;
- test/lint status;
- offline/permission invariant status.

Do not turn aspirational numerical targets into release claims until this Phase-0 physical-device baseline exists.
