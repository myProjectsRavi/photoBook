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


## S08 Local personalization — ACCEPTED

- Tested source: `b58e09c901a5559d0c80136a5283d37b6a15813a`.
- Android Verification run `37135944789`: SUCCESS. Artifact `11278023491`, digest `sha256:9b51fb39ad99d174d46c1fe6527f3591d85f787e6d7501a8b0cdca50f55e1609`.
- Offline API-35 targeted run `37135944729`: SUCCESS. Artifact `11278472290`, digest `sha256:fb13cc7b558958a996ffb3adf9205e7f0f2afca9438171a0b8ed0be8f1e93eac`.
- Production behavior persists only typed album descriptor keys locally, removes stale/duplicate keys, preserves stable order, caps pins at six, and partitions pinned/unpinned albums without duplicate membership.
- Local Memories visibility is persisted independently and suppresses home presentation without deleting or mutating curated memory data.
- The previously failing 200%-font navigation evidence was fixed by bounded UI synchronization rather than weakening the accessibility assertion; the corrected targeted suite is green.
- No account, network, telemetry, cloud, storage-permission, Vault, destructive-action, original-media, or cancellation contract was broadened.
- No physical/OEM/camera/battery/thermal evidence is claimed.


## S09 Decode budget — ACCEPTED

- Tested source: `564efdd847db9c11c7740adc00fe55be78a9d2cd`.
- Android Verification run `37137095760`: SUCCESS. Artifact `11278987191`, digest `sha256:fd6641c72b35b9b22a6defa2f005a594adf3a1155a280180773fa9f1a5149b19`.
- Offline API-35 targeted run `37137095732`: SUCCESS. Artifact `11278774921`, digest `sha256:375c804632d5598dc705df9c7d443e53caa42f0599fd9a45e0280729c63f9e5f`.
- Gallery thumbnail requests now use stable viewport/column decode buckets with a 5% overscan and the existing device-tier cap. A standard 1080px/3-column viewport resolves to 384px instead of a fixed 512px request; a 720px/4-column viewport resolves to 192px; lite devices remain capped at 256px.
- Unit evidence covers request dimensions, estimated ARGB byte budgets, invalid inputs, and the existing lite/standard image-cache caps. Existing Coil memory/disk caching, hardware bitmaps, and application low-memory cache trimming remain in place.
- The first S09 test expectation exposed a real bucket mismatch and failed Android Verification; the policy/test was corrected based on the actual geometry rather than waiving the failure.
- Hosted emulator evidence is correctness/privacy evidence, not native-ARM decode timing certification. No physical/OEM/camera/battery/thermal evidence is claimed.


## S10 Grid continuity — ACCEPTED

- Tested source: `296aa0370bce2c08b3fd8464ec422d67c9c99559`.
- Android Verification run `37139032192`: SUCCESS. Artifact `11279743597`, digest `sha256:55483966db272391f71601ff4720a27e029e072cf00ce0650d5be71f06fe5e4a`.
- Offline API-35 targeted run `37139032249`: SUCCESS. Artifact `11279462227`, digest `sha256:6c8eecad10e82e32af8a92098c2363054ff6dc8dbc8ff3e38a6cfe0e300d8e21`.
- The Photos shell now owns one saveable `LazyGridState`, so transient loading/empty-result branches and viewer overlays do not recreate the grid anchor. Stable photo IDs remain the loaded-item keys; Paging remains bounded by the existing 60-page size / 20 prefetch / 300 max loaded-item policy.
- Fast scrub uses deterministic target mapping, cancels a superseded scroll before launching the latest target, and cancels an in-flight scrub when the result item count changes. Gesture cancellation also cancels the pending scroll.
- Unit evidence covers deterministic edge/midpoint mapping, out-of-range clamping and fail-closed empty/invalid tracks.
- The first exact-source validation correctly failed because two imports were missing after the refactor. Commit `296aa037...` fixed only those compile imports; the continuity behavior was unchanged and both authoritative gates then passed.
- No unbounded retained-photo list, network behavior, privacy boundary, Vault behavior, destructive-action contract or original-media behavior was changed. No physical/OEM/camera/battery/thermal evidence is claimed.


## S11 Progressive startup — ACCEPTED

- Tested source: `7efea3564b3aa513c9097de28e3e71755dba6c72`.
- Android Verification run `37140788377`: SUCCESS. Artifact `11280840914`, digest `sha256:557a5839e44569660a28734b1a0e6b38361b9f6ec1b481ece61734d99352c9ec`.
- Offline API-35 targeted run `37140788469`: SUCCESS. Artifact `11280671264`, digest `sha256:099a0cdc9cf71a1894b8b651278c3f57feacd549a478f2201987acd15548171a`.
- Startup readiness now distinguishes permission, safe basic browsing, base-sync/search readiness, and whether optional enrichment was merely scheduled. Access-filtered persisted rows may be browsed while base reconciliation continues; no ungranted persisted rows are published.
- Empty first launch still waits for base sync before browsing; optional enrichment is never reported as complete merely because browsing is available.
- Main remains untouched. No physical/OEM/camera/battery/thermal evidence is claimed.


## S12 Durable scanning — ACCEPTED

- Tested source: `19714f05851e4d75af589033c0167beb0c66a3a3`.
- Android Verification `37141371232`: SUCCESS, artifact `11280413100`, digest `sha256:476d7e90c522bdb8454dbfb01b1c95e2982570e8eb2335431e605805b7164299`.
- Offline API-35 targeted `37141371142`: SUCCESS, artifact `11280372822`, digest `sha256:2221d39f10fb69d3a574f7966d19d959c53186eb5d24d3a976b95ebc4b7c8fa4`.
- Full and generation-delta MediaStore scans are now cooperative-cancellation aware every 256 rows; index construction checks cancellation between bounded record-build batches.
- Deterministic batching tests cover 10k/50k/100k libraries (40/196/391 checkpoints at 256 rows). Existing Room persistence remains bounded at 200-row batches and access-generation commit gates remain unchanged.
- No claim is made that manifest-only scale tests equal physical-device scan timing. No physical/OEM/battery/thermal evidence is claimed.


## S13 Resource scheduling — ACCEPTED

- Tested source: `30e914e112d5c7c1ca77d4781b14d2b1c13647c9`.
- Android Verification `37142161794`: SUCCESS, artifact `11280439276`, digest `sha256:155e96e143fad5411bc07982681ad5361edd577450716b2fb8bd81e8655e2ecf`.
- Offline API-35 targeted `37142161840`: SUCCESS, artifact `11280364149`, digest `sha256:5065418f56877adcffaf45e55da0dae4b45440264cff6ececa7f1f958d13c802`.
- PhotoIndex now exposes typed structural/intelligence/favorite revisions while retaining the full revision stream used by search correctness.
- Memory curation invalidates only on structural/access-generation changes, avoiding intelligence-only re-curation.
- Whole-library optional intelligence maintenance yields while the app process is foreground; focused-photo work retains its higher-priority path.
- No search revision was suppressed and no privacy/offline/Vault/media-safety gate was weakened.


## S14 Search correctness — ACCEPTED

- Tested source: `d15586ae3151412a4fd40724216e6a43519cea81`.
- Android Verification `37143132914`: SUCCESS, artifact `11281965345`, digest `sha256:76c1099e3f10eeccf2c70c265424d93619624c0aed26b330d14bebf936432908`.
- Offline API-35 targeted `37143132942`: SUCCESS, artifact `11281855266`, digest `sha256:a3f80e60ce11619c4700cb1ec13db374640c85518c7ffa14c244535fb48cae25`.
- Added a hard-coded truth-table oracle covering temporal, source, favorites, location, OCR, semantic tag, compound, recent and oldest semantics with exact eligibility/order assertions against both legacy and v2.
- Existing scale parity covers 10k/50k/100k deterministic corpora; existing cancellation tests prove bounded cooperative abort; stale generation and missing-candidate paths fail closed rather than publish partial results.
- Production search semantics were not narrowed or optimized through an unproven candidate subset.


## S16 In-photo search — ACCEPTED

- Tested source: `4b98efdc89a6fafe1a7d011de116af8ad0e3eff3`.
- Android Verification `37166303720` attempt 2: SUCCESS. Artifact `11290940803`, digest `sha256:55772f9c318a53378c04ba40533cf7e8e8907518f71d45baced691c9d5aaa640`.
- Offline API-35 targeted verification `37166303796`: SUCCESS. Artifact `11289513787`, digest `sha256:98ba92c45d37ca9a0f6d19c4065eaec8de5790ec95e1f57bdf0d8f3e9525bf4e`.
- Lifecycle regression coverage proves an in-flight layout cannot publish after explicit close or disposal.
- Existing one-layout-per-session query reuse, stale source/request/query fencing, geometry/insets/reveal behavior and authenticated Vault input boundary were preserved.
- No physical/OEM/camera/battery/thermal evidence is claimed.


## S17 Offline maintenance — ACCEPTED

- Production source: `427f575f28d2a99b87608c776b64143d0104f73a`.
- Offline API-35 targeted run `37174290335`: SUCCESS, artifact `11292293982`, digest `sha256:56ae40f04ce1ca3d39ba36d3bf2e36bc66ce8cca3bd63214c00967e00d461108`.
- Android Verification run `37174324230`: SUCCESS on immediate descendant `56ebd10fa9f73efc2f6d6271ef781c49e869aee2`, artifact `11293035617`, digest `sha256:8f36e0df011070fb38ea6a11e169192850a0417d3094048a4fdc20e7d60da79c`.
- GitHub compare proves the sole intervening change is STATE.md, so both gates exercise the same production/test tree.
- Retry exhaustion fails truthfully; completed work succeeds; processable work retries only within the existing bounded attempt budget.


## S18 Editing — ACCEPTED

- Exact source: `2a4e6c5e7bbc37ed9f6f938b3ca26cd8828e0973`.
- Android Verification `37177565922`: SUCCESS, artifact `11293519337`, digest `sha256:e6301a5fbcc889b5a403ed146fdd4fcb8c9dcdf8a47f77e491e5240e35f51b45`.
- Offline API-35 targeted verification `37177565860`: SUCCESS, artifact `11293968337`, digest `sha256:b2156c2c3191e0a03a6fb389992151af5c20c7f4b353e8083a46ade5a22568c6`.
- Shared EditTransform normalizes rotation/crop and tone/filter state. The viewer preview is rendered through the same bounded bitmap transform path as export, preserving EXIF normalization and custom-crop position.
- The earlier S18 test failure was a test-oracle mismatch with the existing 0.45 preview aspect lower bound; the expectation was corrected without changing product behavior.


## S19 Safe sharing — ACCEPTED

- Exact source: `868902d596b5e3904642ec22e88503331cfb2f4c`.
- Android Verification `37177989158`: SUCCESS, artifact `11294056125`, digest `sha256:3877b02fcc012947eaad7a52cca70819c629d39a965f42547762dc03ed76034d`.
- Offline API-35 targeted verification `37177989167`: SUCCESS, artifact `11294101251`, digest `sha256:753ad7d53cb96079315401f62d9192480c8762f879118e0804d3a5bc2e89b77b`.
- Oversized batches fail before output creation; cancellation propagates and deletes partial safe-share files; a later asset failure deletes earlier prepared outputs. Read-only ClipData grants are centralized and instrumented with an explicit no-write-grant assertion.


## S23 final paged acceptance evidence

- Final tested source: `d3d9257e4b8caf5d24ca0164956b5e30c530ee01`.
- S23 final production behavior now satisfies the full acceptance anchor, including **bounded paged Trash** rather than one unbounded provider materialization.
- `TrashService.listTrashed(offset, pageSize)` uses MediaStore query limit/offset with a one-item lookahead, stable `DATE_EXPIRES DESC, _ID DESC` ordering, bounded page size, and an explicit next offset only when another item exists.
- `MainActivity` maintains the next offset, appends additional pages without duplicate IDs, continues filtering archive-managed entries, and preserves already-loaded data if a later page query fails.
- `TrashScreen` exposes an explicit Load more action and loading state. Unsupported Android, provider failure, genuinely empty Trash, and successful populated Trash remain distinct.
- Provider `DATE_EXPIRES` is shown when present; no synthetic expiry is invented.
- Restore and Delete Forever still use Android system-managed confirmation intents. The shared confirmation result path refreshes Trash afterward; no direct destructive bypass was introduced.
- Focused host regressions cover READY/UNSUPPORTED/ERROR state mapping plus page-boundary lookahead semantics and exact offset advancement.
- An intermediate paged source `715371371211f0899ad833ecb74bde3ad90566be` failed Android compilation because of an invalid explicit Compose `weight` import. The cause was diagnosed from CI and fixed without changing behavior in `d3d9257e...`.
- Android Verification run `37584049388`: **SUCCESS** on exact source `d3d9257e...`. Artifact ID `11466207770`, digest `sha256:6d190cf0d84f327502522cda08ab6ad7fd439e3f423e130a4838bd2cddbff851`.
- Offline API-35 targeted run `37584049334`: **SUCCESS** on exact source `d3d9257e...`. Network isolation passed; **29/29 instrumentation tests** passed; fail-closed evidence manifest was valid for the exact checkout. Artifact ID `11465687691`, digest `sha256:04b4037a363835042acb8d73328a40c375c688af042cb129f06a69140e7634d3`.
- No physical/OEM/camera/battery/thermal evidence is claimed. Hosted API-35 evidence remains correctness/privacy evidence.
- `main` remained untouched.

**Decision: S23 ACCEPTED.**


## S24 final acceptance

- Tested source commit: `baf9e1fb1f72fae8f2b4e3543c1470bbd2bbbaf4`.
- Near-duplicate candidate generation now uses `maxDistance + 1` disjoint perceptual-hash bands. For the configured Hamming threshold of 8, any accepted pair must share at least one candidate band; the final Hamming-distance check remains authoritative.
- Exact duplicate candidate grouping no longer requires matching dimensions. File size is only the first prefilter, the partial MD5 is only a second prefilter, and byte identity is accepted only after full SHA-256 equality.
- The database exact-duplicate prefilter no longer trusts row-count equality alone. It is used only when Room photo IDs exactly match the in-memory duplicate-analysis snapshot; otherwise analysis fails open to the full supplied record set.
- Removed the final global duplicate-group truncation so valid groups are not silently omitted.
- Focused host regressions exercise adversarial distance-8 patterns, all single-bit positions, equal partial-prefix/different-tail SHA-256 rejection, identical-content SHA-256 equality, and database/snapshot identity mismatch cases.
- Android Verification run `37587447134`: **SUCCESS** on exact source `baf9e1fb...`. Artifact ID `11466913277`, digest `sha256:a88373eb2d0f576afe5af7df5ad2a0cd0860bd8dddcab20b501bacdd9125cb62`.
- Offline API-35 targeted run `37587447136`: **SUCCESS** on exact source `baf9e1fb...`; host verification, APK/manifest no-INTERNET gates, emulator isolation, 29/29 instrumentation tests, and fail-closed evidence-manifest validation passed. Artifact ID `11467322134`, digest `sha256:f0408f7f5e88fc1a5f698104d3b073815b20a4a3c5bc11d416aff797f58bdd57`.
- No physical/OEM/camera/battery/thermal evidence is claimed. Hosted API-35 is correctness/privacy evidence, not native-ARM performance certification.
- `main` remained untouched.

**Decision: S24 ACCEPTED.**


## S25 final acceptance

- Tested source commit: `3c21073283e39776751aa26f9d078d691c05910f`.
- Cleanup groups are fully reviewable: each group exposes every member through a lazy horizontal list rather than truncating to four thumbnails, and category-specific reasons are shown before any action.
- Hidden burst-group and blurry-member caps were removed so valid cleanup candidates are not silently omitted.
- Archive cleanup no longer preselects destructive candidates. Refresh/category/enable flows retain only explicit user selections that still belong to the current candidate set; newly discovered candidates start unselected.
- Confirmed-trash reconciliation is isolated in `CleanupGroupPolicy`: confirmed IDs are removed, groups with fewer than two survivors are dropped, and a deleted burst hero cannot remain as a stale hero reference.
- Android's system confirmation remains the destructive boundary. Cancellation does not reconcile/delete cleanup state; successful confirmation reconciles the confirmed IDs.
- Focused host regressions cover explicit cleanup selection retention and cleanup-group reconciliation, including removed hero handling.
- First targeted attempt `37589482404` failed only in the unchanged large-font navigation harness at the first post-HOME relaunch assertion. The same test had passed on the immediately preceding exact source. The harness was hardened to relaunch through explicit shell `am start -W` while preserving all 200% font reachability assertions.
- Offline API-35 targeted run `37590387971`: **SUCCESS** on exact source `3c210732...`; 29/29 instrumentation tests passed, including `mainShell_largeFont_keepsPrimaryNavigationReachable`. Fail-closed evidence manifest validated the exact checkout. Artifact ID `11468004866`, digest `sha256:5be9cb24b31c1a5ac919f50a4fc347c54a467dcab668ea6ff73dc61c4c2307b5`.
- Android Verification run `37590387987`: **SUCCESS** on exact source `3c210732...`. Artifact ID `11469125995`, digest `sha256:da75c2fabf4f3e06d4fbc4b5dd314c08fa3866a282874a9e349ffe434f1c2e90`.
- No physical/OEM/camera/battery/thermal evidence is claimed. `main` remained untouched.

**Decision: S25 ACCEPTED.**


## S26 final acceptance

- Tested source commit: `0bb57c236a345f3f6f55df1c99171c1efc151a9b`.
- Archive candidate classification remains conservative: favorites, fresh media, non-images, sensitive-document cues, weak payment evidence, weak/legacy food evidence, and live-subject food photos are rejected.
- Full scans remain bounded by independent keyset pages and now explicitly check coroutine cancellation between pages.
- Archive UI publication is revision-scoped. Every refresh/toggle/keep/confirmed-trash/due-delete request is ordered by a monotonic publication revision captured at request time; stale partial/final summaries and dismissed-sheet work cannot overwrite a newer request.
- Background retention only marks decisions due. Actual deletion remains a foreground MediaStore system-confirmation flow, and local state is marked deleted only after RESULT_OK.
- The targeted suite exposed two unrelated navigation-fixture races during S26. Both were fixed without weakening assertions: large-font relaunch uses explicit shell launch, and the common @Before fixture now asserts deterministic launch readiness.
- Offline API-35 run `37604161299`: **SUCCESS**, 29/29 instrumentation tests, exact checkout evidence manifest valid. Artifact `11474401280`, digest `sha256:19ab132351493b6b160ffdfdc8831deb06c2aebe1ea1415ac018e690cd8230ff`.
- Android Verification `37604161533`: **SUCCESS**. Artifact `11474766342`, digest `sha256:0034a7ea3800e94b822c5cccaef95631e921a5c2a30fd12a4707fbe4d7924dda`.
- No physical/OEM/camera/battery/thermal evidence is claimed. `main` remained untouched.

**Decision: S26 ACCEPTED.**


## S27 final acceptance

- Tested source commit: `c19384f2a523df6c31b161e8b68c1dbcab076803`.
- Viewer More exposes the private-note entry and binds async load/save/delete to the active `PhotoRecord` identity.
- Production notes use `EncryptedSharedPreferences` only. Storage failure is fail-closed; there is no plaintext fallback path.
- Stable identity is MediaStore ID + `dateAdded`: URI, path, filename, and folder changes do not orphan the note, while a reused MediaStore ID with a different `dateAdded` cannot inherit it.
- Encrypted-at-rest instrumentation verifies neither note plaintext nor stable key plaintext appears in the preferences XML; the new identity regression verifies retrieval after URI/path/filename/folder changes.
- Offline API-35 run `37608695583`: **SUCCESS**, exact checkout evidence valid. Artifact `11477275330`, digest `sha256:250ec383aef50f793d7fa0007045f9dc550f4f46ec23e84d2ccd10d752366f5f`.
- Android Verification run `37608695584`: **SUCCESS**. Artifact `11476811942`, digest `sha256:32353911f4cd0c3aba3c3f4d9cf96c57bc9467918d22b997a91546e9f6ff0b6a`.
- No physical-device evidence is claimed. `main` remained untouched.

**Decision: S27 ACCEPTED.**


## S28 final acceptance

- Tested source commit: `124cfc35dcb527c39b91faac86ff9d6be410d285`.
- Declutter review is restored in the live Tools surface and remains bounded to the existing 300-candidate draft.
- Keep/Trash/Undo mutate only `DeclutterSession`; no media API is called during review.
- Explicit Apply resolves the marked IDs and routes through the existing Android system trash confirmation.
- Cancellation leaves the draft and media unchanged. Confirmed success reconciles candidates, marked/kept sets, and current position.
- Reconciliation now preserves the same current candidate when earlier reviewed trash items disappear and keeps completed sessions complete.
- Existing Vault/Trash/Archive Tools reachability was preserved by placing Declutter after the established primary actions.
- Offline API-35 run `37611005386`: **SUCCESS**. Artifact `11477907657`, digest `sha256:4583741ed9dceb9b933d1d39954e4c31d49128b6a6c47fb30b584b881eb02785`.
- Android Verification run `37611005344`: **SUCCESS**. Artifact `11477358716`, digest `sha256:346154a56bfd6dabb6cf895b3fbbd1083f73cbb5374da71dfee023fb5d5d9306`.
- No physical-device evidence is claimed. `main` remained untouched.

**Decision: S28 ACCEPTED.**
