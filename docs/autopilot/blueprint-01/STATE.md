# Blueprint 01 State

cycle: blueprint-01  
authorized_branch: autopilot/epics-features-user-stories  
blueprint_printed_branch: autopilot/photobook-blueprint-01  
baseline_sha: d693acd7c52f285b6ba475fdd3712a10e419d4e1  
current_epic: E03 Gallery experience  
current_feature: Navigation  
current_story: S05 Photos-first navigation shell  
status: IN_PROGRESS  
source_commit_tested: 46a65c99201f3a490203563f9c32a793a87ee721 (S05 checkpoint candidate; CI pending)
latest_checkpoint_commit: 46a65c99201f3a490203563f9c32a793a87ee721

## Source blueprint

Authoritative planning source for this cycle: user-provided **PhotoBook_Implementation_Blueprint_01.docx**, 45 pages, dated 27 September 2026. Its mandatory execution order is S01 through S32. The uploaded blueprint is the planning authority; these repository files are the durable execution state.

## S01 completed work

S01 is ACCEPTED. See VALIDATION.md and REVIEW_COVERAGE.md for the complete 224/224 inventory/static-screen evidence.

## S02 design hypothesis

Android Verification currently omits `autopilot/**` from push routing, so commits on the authorized implementation branch can exist without the normal Phase-0 source/build gate. Hosted emulator workflows also do not provide a reusable exact-ref dispatch/evidence contract for this branch.

Expected observable result: every push to `autopilot/**` receives Android Verification; targeted hosted emulator verification can be dispatched against an explicit commit/ref and records checkout/workflow/APK/ABI/API/RAM/fixture/test-count evidence; missing required evidence fails instead of uploading a misleading partial report.

## Validation boundary

S01 is documentation/inventory work. No fresh Android runtime result is claimed or required for this documentation-only story. Blueprint-provided baseline CI remains historical evidence only. No physical Android device is available, so physical/OEM/camera/battery/thermal properties remain unverified.

The static screen is deliberately bounded and is not a substitute for semantic review. Before accepting any later story that changes a subsystem, that story must close the relevant REVIEW_GAP entries for its changed production files/direct callers/tests.

## Completed S02 implementation and evidence

- Android Verification now includes `autopilot/**` push routing, explicit read-only permissions, exact-event-SHA checkout, checkout/workflow identity recording, cancellation of superseded runs, and fail-closed evidence upload.
- Added `.github/workflows/autopilot-targeted-verify.yml` with an exact 40-character source-SHA dispatch contract and an `autopilot/**` runtime/tooling push route.
- Added `tools/benchmark/build_ci_evidence_manifest.py` and regression tests. The manifest requires exact checkout/workflow identity, APK hashes, API, ABI, AVD RAM, deterministic fixture hash and a non-zero green instrumentation count. Missing fixtures/zero tests are negative tests.
- Phase-0 host verification compiles and runs the evidence-manifest tests.
- First targeted attempt, run `36396212144`, correctly failed because the full emulator suite included the 100k timing-only test under x86_64-hosted ARM translation; this is not valid native ARM performance evidence.
- Second targeted attempt, run `36396959374`, correctly failed after host-network isolation because Gradle UTP attempted to resolve an uncached host plugin. The failure was an evidence-route/tooling dependency, not a product test result.
- Fixed the targeted route to build while tooling is available, install the prebuilt APK/test APKs, then cut emulator and host outbound network and invoke AndroidJUnitRunner directly. Performance-only instrumentation stays in dedicated certification gates.
- Exact source commit `03b63c544e016f809618c4509d2cf5f9c6788a4b` passed targeted hosted emulator run `36397730810`: API 35, x86_64 emulator, configured AVD RAM 2048 MiB, airplane mode on, host/emulator outbound-network checks blocked, merged/debug APK/installed-package INTERNET checks passed, and **19/19 targeted correctness/privacy instrumentation tests passed**.
- Targeted evidence manifest status: VALID. Fixture SHA-256 `6bc4301485bac8f480ea96d711a2294e42ee9dd9d937fcc4a26a5e351c33b5bc`; instrumentation-output SHA-256 `3dfdb7b7242e4c8fb802fd9adcb80d35d6f822e13362a0c8ff9f24ee6d3ba742`; artifact ID `10959695052`, artifact digest `sha256:f9fefb5140cc46a4017aebfcc3392d081a9295613818e1be59e6de31a180b24e`.
- Exact source commit `03b63c544e016f809618c4509d2cf5f9c6788a4b` passed Android Verification run `36397730695`: 20 host tests, full Phase-0 Gradle gate, release APK/AAB size gates, and packaged release no-INTERNET checks. Release APK sizes: arm64 22,429,005 bytes; armeabi-v7a 16,387,999 bytes. AAB: 20,606,184 bytes. Artifact ID `10959551441`, digest `sha256:e5e3efe3edc4042a70de18aa866a17df97029595524cce5f414fea71696c40f9`.
- No production application code, resources, database schema, or release metadata changed in S02.

## Limitations

- The API-35 hosted emulator is x86_64 and may translate the ARM64 app. Its results are correctness/privacy evidence only, not native ARM performance evidence.
- No physical/OEM device evidence is claimed.
- S31/S32 retain the full compatibility/performance/package matrix.

## S02 final acceptance

- Documentation-only checkpoint `c4b6c2a3689b2e5fcb8db8a4527d7bdb05817888` automatically triggered Android Verification run `36399573069`, which completed successfully.
- No Autopilot Targeted Emulator Verification run was scheduled for this documentation-only commit, as required by its runtime/tooling path filter.
- S02 is ACCEPTED. Its tested implementation source remains `03b63c544e016f809618c4509d2cf5f9c6788a4b`; the later checkpoint is documentation-only routing evidence.

## S03 start hypothesis

Observed source defect: `PhotoViewerScreen.saveEditedCopyToDevice` performs MediaStore insert, stream copy, publication update and cleanup synchronously from the UI coroutine after rendering. Publication can expose a pending row before copy success is known, does not verify read-back/nonzero destination before publishing, has no durable operation journal, and does not model cancellation/access/storage outcomes.

Expected observable result: edited-copy publication moves into a suspend editor/output service using `Dispatchers.IO`, captures immutable invocation state, publishes only a validated complete output, cleans app-owned partial outputs on failure/cancellation, and records enough durable state to reconcile interrupted app-owned pending outputs without touching unrelated media.

## Blockers

None at S03 start. No physical device is available; S03 must rely on unit/static checks plus authoritative GitHub Android/emulator evidence where applicable.

## Next action

Inspect the complete editor save path, direct callers/tests and Android storage/API compatibility boundaries. Add a regression-testable publication abstraction/journal before replacing the UI-owned synchronous MediaStore copy. Preserve original media and do not broaden storage permissions.
## S03 final acceptance

- Tested source commit: `607353b9fbee37e7dfb683a99627b5f222332e13`.
- Changed S03 scope since the S02 checkpoint: editor publication workflow/test routing, `EditorOutputPublisherInstrumentedTest.kt`, `PhotoBookApplication.kt`, `EditorOutputPublisher.kt`, `PhotoViewerScreen.kt`, editor strings, and `EditorPublicationCoordinatorTest.kt`; intermediate S03 checkpoint docs are also in the seven-commit range.
- Android Verification run `36509867274`: SUCCESS on the exact tested source; Phase-0/build/size/no-INTERNET gates passed.
- Targeted API-35 emulator run `36509867285`: SUCCESS on the exact tested source; 20/20 targeted instrumentation tests passed, including production edited-copy publication while preserving the original source bytes.
- Publication provider work is off the caller thread; immutable invocation state, API-29+ pending publication, validation before exposure, cancellation/failure cleanup, durable app-owned recovery and fail-closed legacy behavior are preserved.
- No physical/OEM/camera/battery/thermal evidence is claimed. Hosted API-35 x86_64 evidence is correctness/privacy evidence, not native-ARM performance certification.
- Final S03 source diff/evidence review found no reason to weaken privacy, original-media integrity, cancellation, offline, access-safety, size or destructive-operation gates.

## S03 decision

S03 is **ACCEPTED**. Do not reopen or rerun it unless later evidence reveals a regression.

## Next action

On the next run, select S04 (Durable state) as the first incomplete story in fixed order. Before editing, inspect the Room database configuration, schema/export history, migrations, historical schema fixtures, database callers and migration tests. Do not use destructive migration fallback and do not modify `main`.


## S04 checkpoint — historical schema provenance

- S04 is active. Production `PhotoBookDatabase` is version 12 with `exportSchema = true`; `AppModule` explicitly registers migrations 1→2 through 11→12 and does not configure destructive fallback.
- Actual migration test path is `app/src/androidTest/java/com/photobook/app/verification/RoomMigrationInfrastructureTest.kt`. It currently proves only fresh v12 schema creation plus `PRAGMA integrity_check`; it does not execute a historical upgrade.
- Actual exported schema path is `app/schemas/com.photobook.app.data.db.PhotoBookDatabase/12.json`.
- Repository history confirms commit `fb8602a784b2871cfed7746fafddec9761ac7427` introduced migration 6→7/database v7, but no v7 exported Room schema exists at that commit. Phase-0 commit `048ef1aa6f38567755a8f00f415f089d40ef85fd` contains v12 but no v7 fixture.
- Existing `docs/phase0-verification.md` explicitly states historical schemas predating Phase 0 were not exported and **must not be fabricated**. Therefore S04 cannot truthfully manufacture Room JSON fixtures from migration SQL and call them historical exports.

### S04 next action

Recover a trustworthy historical database artifact/schema from repository or CI history if one exists. If none exists, design an evidence path that executes the real migration chain against a provenance-backed historical SQLite database fixture rather than fabricating Room schema JSON; preserve durable photo/Vault/archive records and validate the resulting v12 schema. Keep S04 IN_PROGRESS until executable upgrade evidence passes.


## S04 checkpoint — production-chain test design

- Branch relationship at this checkpoint: `autopilot/epics-features-user-stories` is 36 commits ahead and 0 behind `main`; `main` was not modified.
- Historical provenance was re-read from commit `09c35afc15f3bfa087b0b78ab826f04598555c9f`: Room v1 used `PhotoEntity` + `PhotoFtsEntity`, `version = 1`, and `exportSchema = false`.
- Current production `AppModule.providePhotoBookDatabase` remains the authoritative migration caller and installs migrations 1→2 through 11→12 with no destructive fallback.
- Smallest safe test design: create a test-only SQLite v1 fixture from the exact historical entity schema, seed durable photo/FTS data, set `user_version = 1`, then open that file through `AppModule.providePhotoBookDatabase`. This exercises the real production migration registration and current Room schema validation without inventing a historical Room JSON export.
- The intended assertions preserve URI/name/location/favorite/tags/OCR/FTS data, verify v9→10 derived OCR status, verify the intentional v11→12 ML reset only, and require `PRAGMA integrity_check = ok`.
- Two authenticated GitHub write attempts for this test (existing-test update and isolated new instrumentation test) were rejected by the connector safety layer before mutation. No source/test file changed and no lower-level Git-object bypass was used.

### Exact next action

Retry the isolated instrumentation-test write through the normal GitHub contents API. If accepted, let branch CI compile it, diagnose any schema-validation mismatch from the genuine v1 fixture, and run the exact-head targeted hosted emulator route. Keep S04 IN_PROGRESS until executable migration evidence passes; do not start S05.


## S04 checkpoint — executable test mutation still blocked

- Exact branch HEAD inspected: `a8237af686f3fdfc2481ffee17e5d185f07e1512`; branch is 38 commits ahead and 0 behind `main`. Main was not modified.
- Re-read canonical STATE/BACKLOG/VALIDATION/DECISIONS/REVIEW_COVERAGE, production `AppModule`, existing Room migration infrastructure test, the S04 scaffold, and historical v1 `PhotoEntity`/`PhotoFtsEntity`/`PhotoBookDatabase` at provenance commit `09c35afc15f3bfa087b0b78ab826f04598555c9f`.
- Current S04 scaffold is only 14 lines and is not executable migration evidence.
- Prepared the smallest executable instrumentation test design: create `photobook.db` with the exact provenance-backed v1 photos + FTS4 shape, seed durable photo/FTS data, set `user_version=1`, then open via `AppModule.providePhotoBookDatabase` so production migrations 1→12 and Room's current schema validation execute. Assertions cover durable metadata/tags/OCR/FTS preservation, v9→10 OCR derivation, intentional v11→12 ML reopening only, final version 12, and `PRAGMA integrity_check=ok`.
- The normal authenticated GitHub contents-API update of `HistoricalRoomMigrationInstrumentedTest.kt` was blocked by the connector safety layer before mutation. No lower-level Git-object bypass was attempted.
- GitHub reports no workflow runs or combined statuses for exact HEAD `a8237af...`; no CI PASS is claimed.
- S04 remains IN_PROGRESS; S05 was not started. No physical/OEM/camera/battery/thermal evidence is claimed.

### Exact next action

Retry only the normal GitHub contents-API replacement of `HistoricalRoomMigrationInstrumentedTest.kt`. Once accepted, inspect the exact diff, allow Android Verification to compile it, diagnose any genuine schema mismatch, then obtain exact-head targeted hosted-emulator migration execution before considering S04 ACCEPTED.


## S04 checkpoint — automation retry

- Re-read canonical S04 state, backlog, validation/decision/review ledgers, production AppModule migration registration, current migration tests, and the provenance scaffold through the connected GitHub integration.
- Branch comparison at start: 39 commits ahead and 0 behind main; main was not modified.
- Re-read the genuine v1 definitions at provenance commit 09c35afc15f3bfa087b0b78ab826f04598555c9f. PhotoBookDatabase was version 1 with exportSchema=false; PhotoEntity and PhotoFtsEntity definitions provide the historical SQLite shape.
- Retried the normal authenticated contents-API replacement of HistoricalRoomMigrationInstrumentedTest.kt with the executable v1 fixture -> production AppModule 1-to-12 migration test. The connector safety layer rejected the mutation before repository change.
- No test/CI PASS is claimed for the uncommitted test body. S04 remains IN_PROGRESS and S05 was not started.

### Exact next action

Retry only the normal GitHub contents-API update of HistoricalRoomMigrationInstrumentedTest.kt. Do not fabricate historical Room JSON. Once the executable test is committed, inspect its diff and obtain exact-head Android Verification plus targeted hosted-emulator execution before accepting S04.


## S04 final acceptance

- Tested source commit: `a362b6315b6d716b39dfa281974597d042adeb15`.
- Historical provenance: v1 schema from `09c35afc15f3bfa087b0b78ab826f04598555c9f`, with `exportSchema=false`; no historical Room JSON was fabricated.
- Android Verification run `36866615361`: SUCCESS on exact tested source; artifact ID `11163808933`, digest `sha256:e69ce69e9d2f1042ee192c70b822ee3589ab6066d5fc7feadbd68c75c0dacf86`.
- Targeted API-35 emulator run `36866615388`: SUCCESS on exact tested source; artifact ID `11163587883`, digest `sha256:332ff2f0d0916f7485d0d579e87a68843fdf0bf5e0bc6b0549fd5ed4a370d373`.
- Executable evidence opens a provenance-backed SQLite v1 fixture through production `AppModule.providePhotoBookDatabase`, exercising migrations 1→12 and Room v12 validation while preserving durable photo/FTS data and checking the intentional v11→12 ML reset plus SQLite integrity.
- No destructive migration fallback was added. No physical/OEM/camera/battery/thermal evidence is claimed.
- S04 is ACCEPTED. Next eligible story is S05 Navigation.


## S05 checkpoint 1 — no automatic keyboard

- Active story: S05 Navigation. Dependencies are satisfied (S02 ACCEPTED); S01-S04 are ACCEPTED.
- Inspected production `MainScreen.kt`, its `MainActivity.kt` caller, `SearchBar.kt`, and repository search for direct SearchBar/MainScreen callers before editing.
- Confirmed defect: `MainScreen` supplied `autoFocus = searchReady && query.isBlank()`, causing `SearchBar` to request focus/show the IME when search became ready.
- Smallest coherent change committed at `46a65c99201f3a490203563f9c32a793a87ee721`: remove the caller's automatic-focus request. Explicit user focus/search behavior is preserved; no search semantics, privacy, storage, destructive action, Vault, offline, or original-media behavior changed.
- The decorative PRO badge and Photos/Albums/Tools shell remain open S05 work. Existing search/viewer, source, Vault, Trash, Archives, duplicates, memories and selection journeys must be preserved.
- CI for this exact checkpoint is pending; no PASS is claimed yet. No physical/OEM/camera/battery/thermal evidence is claimed.

### Exact next action

Inspect CI for `46a65c99201f3a490203563f9c32a793a87ee721`. If green, remove the decorative PRO badge as the next narrow change, then introduce the state-preserving Photos/Albums/Tools shell with regression/visual evidence. Keep S05 IN_PROGRESS until all S05 acceptance criteria pass.


## S05 checkpoint 2 — decorative PRO badge removed

- Production commit: `88c1298fd22d20eae8764b7e54b5105e9b7687a2`.
- Re-read canonical STATE/BACKLOG/VALIDATION/DECISIONS/REVIEW_COVERAGE, branch relationship, production `MainScreen.kt`, direct `MainActivity.kt` caller, and current S05 checkpoint before editing.
- Smallest coherent change removed only the decorative header PRO surface/badge from `MainScreen.kt`. No search, media, storage, Vault, privacy, offline, selection, destructive-action, or original-media behavior was changed.
- Prior no-auto-keyboard production checkpoint remains `46a65c99201f3a490203563f9c32a793a87ee721`.
- Pre-change branch relationship was 47 commits ahead / 0 behind `main`; merge base/main remained `d693acd7c52f285b6ba475fdd3712a10e419d4e1`. Main was not modified.
- Exact-source CI for `88c1298f...` is pending; no PASS is claimed. No physical/OEM/camera/battery/thermal evidence is claimed.

### Exact next action

Inspect exact-source Android Verification for `88c1298fd22d20eae8764b7e54b5105e9b7687a2`. If green, design and implement the smallest state-preserving Photos/Albums/Tools shell in `MainScreen.kt`, keeping existing search/viewer/source/Vault/Trash/Archives/duplicates/memories/selection journeys reachable. Add regression/visual evidence before accepting S05.


## S05 checkpoint 3 — Photos / Albums / Tools shell

- Production commit: `82922885bfe76334f05c3d7a6b8646cef2f08e45`.
- Re-read canonical STATE/BACKLOG/VALIDATION/DECISIONS/REVIEW_COVERAGE, branch comparison, current production `MainScreen.kt`, and prior S05 checkpoints before editing.
- Added a local, saveable three-destination shell: Photos, Albums and Tools. Photos remains the default and keeps the photo feed/search visible; Albums contains the existing smart-album journeys; Tools contains the existing Duplicates/Favorites/Reels/Archives/Vault/Trash journeys. Selecting a smart album routes back to Photos after applying the existing query/action.
- Existing selection overlay, duplicate sheet, limited-access management, photo source filtering, viewer callbacks and search callbacks were preserved. No media/storage/Vault/privacy/offline/destructive-action/original-media semantics were changed.
- Branch comparison before this mutation was 49 commits ahead / 0 behind `main`; main/merge-base remained `d693acd7c52f285b6ba475fdd3712a10e419d4e1` and was not modified.
- Exact-source Android Verification for `82922885...` is pending; no CI PASS or visual/emulator PASS is claimed yet. No physical/OEM/camera/battery/thermal evidence is claimed.

### Exact next action

Inspect exact-source Android Verification for `82922885bfe76334f05c3d7a6b8646cef2f08e45`. Diagnose/fix any compile or regression failure rather than rerunning blindly. If green, add focused navigation/visual regression evidence for Photos default, Albums/Tools reachability, preserved journeys and no automatic IME before considering S05 ACCEPTED.
