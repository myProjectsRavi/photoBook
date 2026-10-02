# Blueprint 01 Validation Log

This file records reproducible evidence only. Historical evidence is labeled historical; unavailable gates are never recorded as PASS.

## Baseline before S01 edits

- Authorized implementation branch: `autopilot/epics-features-user-stories`.
- Blueprint source baseline: `d693acd7c52f285b6ba475fdd3712a10e419d4e1`.
- GitHub compare from baseline to the authorized branch before S01 edits: **identical**, ahead 0, behind 0, total commits 0.
- Exact baseline Git tree: `08139d86d49c4f955e26ae4ffa02a989648b7125`.
- Exact baseline tree inventory: 256 blobs, including 224 text candidates, 30 PNG files, 1 DOCX and 1 JAR.
- Gradle metadata read from the exact baseline branch: `versionCode = 26`, `versionName = "2.0.19"`, `minSdk = 26`, `targetSdk = 36`.

## Blueprint-provided historical evidence

The authoritative Blueprint 01 records the following evidence for its reviewed baseline:

- `bash tools/benchmark/run_phase0_local.sh --host-only` passed 16 tests and produced a deterministic 303-record fixture corpus.
- GitHub Actions Android Verification run `36228376607` reported success for the exact reviewed source commit.
- Local Android execution, current visual replay and physical-device execution were unavailable.

These are useful baseline facts, but they are **not fresh S01 runtime execution by the current autopilot run** and must not be promoted to new runtime proof.

## Current-run environment evidence

- GitHub connector access succeeded for repository/branch inspection, exact Git-tree inspection, source/document reads and Git object preparation.
- A direct sandbox clone was not available because the sandbox had no external GitHub DNS/network route at that time. This is an environment limitation, not a repository failure.
- No physical Android device is available. Emulator/hosted evidence must be labeled separately from physical/OEM evidence.

## S01 validation required before ACCEPTED

1. Fill exact per-file line counts for all text candidates in `REVIEW_COVERAGE.md`.
2. Preserve distinct inventory/static-scan/semantic-review/runtime-coverage statuses.
3. Verify all five canonical checkpoint files exist and agree on branch, baseline and current story.
4. Verify S01-S32 uniqueness, dependency references and fixed scheduling order.
5. Verify documented repository paths and commands exist or are explicitly identified as proposed future work.
6. Review the final S01 diff and confirm it changes documentation/coverage only.
7. Re-read the authorized branch and `main` refs; confirm the autopilot work did not modify main.
8. Record the exact S01 source/checkpoint commit after validation.

## Evidence record format for future stories

Each story record must include: story ID; expected behavior; changed files; source commit; command and exit code; test names/counts; environment; fixture hashes; artifact hashes; CI run URLs; screenshot paths when applicable; comparison baseline; known limitations; and reviewer decision.


## S01 checkpoint 0bd6ea79d41fcdd740c3e4214a7466d0ebb8405c

- Branch ref after commit: `autopilot/epics-features-user-stories` → `0bd6ea79d41fcdd740c3e4214a7466d0ebb8405c`.
- Baseline compare: ahead 1, behind 0.
- Changed paths: exactly 10 documentation/coverage files. No `app/**`, `.github/workflows/**`, Gradle, resource, source or test file changed.
- `main` ref at the same verification point remained `d693acd7c52f285b6ba475fdd3712a10e419d4e1`.
- S01 remains **IN_PROGRESS** because exact per-file line counts and final documentation consistency checks are still open.


## S01 final acceptance evidence

Evidence source commit: `2ed75bfb50770000815accfd820197d9d783f036`

- Coverage ledger: **224/224** baseline text candidates have exact line counts.
- Bounded static screen: **224/224** text candidates screened for private-key markers, common hard-coded credential/token signatures, and unresolved merge markers; **0 unresolved flags**.
- Canonical checkpoint set: STATE.md, BACKLOG.md, VALIDATION.md, DECISIONS.md and REVIEW_COVERAGE.md all exist.
- Backlog validation: **32/32** stories present exactly once; IDs are S01 through S32 in fixed order; dependency references resolve to known story IDs.
- Command/path validation: the S01-referenced Phase-0 script exists; Gradle defines printReleaseMetadata, verifyApkSize and verifyReleaseBundleSize. Every one of the 224 inventory paths was fetched from the immutable baseline by exact path/blob identity while completing the ledger.
- Stale-guidance scan of the ten changed Markdown files found no duplicated old `versionCode = 24`, no duplicated old `versionName = 2.0.17`, and no guidance instructing an unsafe original-share fallback after privacy preparation failure.
- Markdown-link validation found no broken relative Markdown links in the changed S01 documents.
- Final baseline compare at the evidence source: branch ahead 19, behind 0; changed paths are exactly the five canonical S01 files plus docs/claude.md, docs/gemini.md, docs/jules.md, docs/performance.md and docs/security.md. No `app/**`, workflow, Gradle, resource, production-source or test path changed.
- Ref check at final validation: `main` = `d693acd7c52f285b6ba475fdd3712a10e419d4e1`; authorized branch = `2ed75bfb50770000815accfd820197d9d783f036`.
- Fresh Android runtime execution was not run because S01 changes documentation/coverage only. Blueprint-provided CI run 36228376607 remains historical baseline evidence, not a fresh claim.
- Sandbox GitHub DNS remained unavailable; connector reads/writes supplied the repository evidence. This did not block S01 completion.
- Binary/model/dependency provenance, packaged-artifact inspection and physical/OEM properties remain explicitly deferred to their later blueprint gates, especially S31.

**Decision: S01 ACCEPTED.** No confirmed S01 acceptance blocker remains in the tested documentation/inventory scope.


## S02 start checkpoint

- Story: S02 — Make branch verification authoritative.
- Starting branch HEAD: `717a9ef69cd6289d1bc649ce3b666e4d18c10dd3`.
- S01 dependency: ACCEPTED.
- Observed source risk: `.github/workflows/android-verify.yml` push branches omit `autopilot/**`.
- Observed hosted-emulator limitation: current emulator workflows use implicit current-branch checkout and branch-specific push triggers; no reusable exact-ref/evidence-manifest contract exists for the authorized autopilot branch.
- Hypothesis: adding branch routing plus an exact-ref targeted verification workflow and fail-closed evidence validation will make branch evidence attributable to the tested source rather than to “latest green”.
- No workflow/application mutation had been made at this checkpoint.


## S02 implementation evidence — tested source 03b63c544e016f809618c4509d2cf5f9c6788a4b

### Failed attempts retained as evidence

- Targeted run **36396212144** failed after the new fail-closed manifest detected one failing instrumentation result. Root cause: the initially broad suite included the 100k search p95 timing test on a hosted x86_64 emulator executing the ARM app under translation. The route was narrowed to correctness/privacy instrumentation rather than weakening the 300 ms performance assertion.
- Targeted run **36396959374** failed because `connectedDebugAndroidTest --offline` required an uncached Gradle UTP host plugin. The route was changed to build APKs before network isolation and invoke AndroidJUnitRunner directly after host/emulator network cut. No product assertion was weakened.

### Passing exact-source evidence

- **Autopilot Targeted Emulator Verification** run **36397730810**: SUCCESS for exact source `03b63c544e016f809618c4509d2cf5f9c6788a4b`.
  - checkout SHA: `03b63c544e016f809618c4509d2cf5f9c6788a4b`
  - workflow blob: `eb9edb1387970de77e6b21d0f4eb0055b7380e47`
  - API: 35
  - emulator ABI: x86_64
  - configured AVD RAM: 2048 MiB; observed MemTotal: 2,532,420 kB
  - airplane mode: 1
  - targeted instrumentation: **19 tests, 0 failures, 0 errors**
  - evidence manifest: VALID
  - fixture SHA-256: `6bc4301485bac8f480ea96d711a2294e42ee9dd9d937fcc4a26a5e351c33b5bc`
  - instrumentation output SHA-256: `3dfdb7b7242e4c8fb802fd9adcb80d35d6f822e13362a0c8ff9f24ee6d3ba742`
  - debug ARM64 APK SHA-256: `de3ff0f9de8f1bee9310e5e7d46049b90d463161a05ab5462b8a8060e5071eb8`
  - debug armeabi-v7a APK SHA-256: `3e2fd29a5f784019cbc47c3e578c4a690264c3d0d84b64eef2f991e9c868970d`
  - artifact: `autopilot-targeted-36397730810`, ID `10959695052`, SHA-256 `f9fefb5140cc46a4017aebfcc3392d081a9295613818e1be59e6de31a180b24e`
  - merged manifest, packaged debug APK permissions and installed package were checked for INTERNET; no INTERNET request was found.
- **Android Verification** run **36397730695**: SUCCESS for exact source `03b63c544e016f809618c4509d2cf5f9c6788a4b`.
  - host evidence tests: **20 passed**
  - full Phase-0 Gradle gate: BUILD SUCCESSFUL, 238 actionable tasks
  - release arm64 APK: **22,429,005 bytes**
  - release armeabi-v7a APK: **16,387,999 bytes**
  - release AAB: **20,606,184 bytes**
  - both release APKs: no `android.permission.INTERNET`
  - artifact: `phase0-verification-36397730695`, ID `10959551441`, SHA-256 `e5e3efe3edc4042a70de18aa866a17df97029595524cce5f414fea71696c40f9`

### Remaining S02 acceptance check

This checkpoint is documentation-only and exists specifically to demonstrate that `autopilot/**` routing works without altering runtime behavior. Require Android Verification to run and pass for this exact documentation-only commit. The targeted emulator path filter should not schedule a new targeted run.


## S05 final acceptance evidence

Evidence source commit: `a286b10c306d56a14a24e7e9bece7d3d4ef9f578`

- Production shell behavior under test includes the S05 changes from `82922885bfe76334f05c3d7a6b8646cef2f08e45`, the missing saveable import fix from `aceb439e274eb6f518eb5540771acadd9824fac6`, and the focused emulator regression harness finalized at the evidence source.
- Autopilot Targeted Emulator Verification run `37030491199`: **SUCCESS** on exact head `a286b10c306d56a14a24e7e9bece7d3d4ef9f578`. API-35 hosted x86_64 emulator, 2 GB configured AVD RAM, offline/network-isolation gate preserved. The targeted suite includes the real `MainNavigationInstrumentedTest` journey: post-permission launch exposes Photos/Albums/Tools, PRO is absent, no automatic IME is shown, Albums is reachable, Tools exposes Vault and scroll-reachable Trash, and Photos is reachable again. Artifact ID `11237730046`, digest `sha256:e0a26840da0de45e4a9c5b9f04d8c7f809f617463a60e415ae812bed38ceaed4`.
- Android Verification run `37030491217`: **SUCCESS** on the same exact head. Artifact ID `11238015741`, digest `sha256:28b07900ae75da3c8c1ae273eec1435156eb064a466d0922a116f324c60d7c2c`.
- Earlier failed S05 runs were retained and diagnosed rather than hidden: missing `rememberSaveable` import; test-only Truth dependency misuse; instrumentation self-termination by `am force-stop`; fresh-install onboarding due absent media permission; and an off-screen Trash assertion that was corrected to verify horizontal-row reachability. None of those failures was waived or converted to PASS.
- Final branch comparison before documentation acceptance: 66 commits ahead / 0 behind `main`; base and merge-base remain `d693acd7c52f285b6ba475fdd3712a10e419d4e1`. Main was not modified.
- No physical-device/OEM/camera/battery/thermal claim is made; hosted emulator evidence is labeled as such.

**Decision: S05 ACCEPTED.** The Photos-first shell, Albums/Tools reachability, no automatic keyboard, no PRO badge, and preserved tested journeys have reproducible exact-source evidence.
