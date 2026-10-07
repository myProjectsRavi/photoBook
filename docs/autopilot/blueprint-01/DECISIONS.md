# Blueprint 01 Decisions

## D001 — Authorized implementation branch

**Decision:** Execute Blueprint 01 only on `autopilot/epics-features-user-stories`. `main` is read-only.

**Reason:** The printed blueprint names `autopilot/photobook-blueprint-01`, but Ravi explicitly designated `autopilot/epics-features-user-stories` for this autopilot implementation before execution. At S01 start the authorized branch was exactly the blueprint baseline SHA, so this changes only the implementation branch name, not the source baseline or product contract.

Never merge, reset, force-push, release or upload as a side effect of this cycle.

## D002 — Product and size contract

The following contract is copied from Blueprint 01 and is binding for this cycle.

### Nonnegotiable product behavior

PhotoBook must not request INTERNET, download models, create accounts, show advertisements, collect telemetry, upload crashes, or require cloud services. Build machines may download pinned dependencies; the installed application must work on first launch with the device offline. The system sharesheet is an explicit user export boundary: another chosen app may transmit a copy, so privacy copy must not claim exports can never leave the device.

Room remains authoritative for committed indexed metadata. Preserve original media during edits and exports. Reconcile limited/revoked access without treating hidden rows as deleted. Destructive media actions require explicit foreground confirmation; background workers may identify candidates or mark retention dates, never delete media. Do not weaken Vault authentication, encryption, backup exclusions, or source-integrity checks for speed.

### Size and compatibility contract

| Item | Binding rule |
|---|---|
| User size ceiling | Treat 30 MB conservatively as 30,000,000 bytes per delivered APK; document the measurement |
| Existing repository APK gate | Keep the existing 30 MiB gate and add the stricter decimal-byte check; never relax either |
| Existing AAB gate | Preserve at most 20 MiB, or 20,971,520 bytes |
| Installed footprint | Report separately from APK/AAB; it grows with runtime, indexes, caches, and user Vault data |
| Android support | Preserve API 26 minimum and API 36 target; runtime matrix includes APIs 26, 29, 30, 33, 34, 35, 36 |
| Production ABI | Preserve arm64-v8a and armeabi-v7a; x86 emulator translation is separate evidence |
| Memory | Preserve constrained-device support; 4 GB physical RAM does not imply a 4 GB app heap |

Never promise that an installed gallery, its index, and user-owned media together occupy less than 30 MB. The enforceable download/package budget is separate from storage management. If a stricter APK gate fails, reduce dependency/resource cost and stop release advancement.

### Scope exclusions for this cycle

No video indexing or playback, server/API integration, new language model, generative editing, automatic face naming, cloud backup, remote maps, subscriptions, or app-wide architecture rewrite. Do not lower minSdk, change encryption formats, delete legacy data, or remove functioning features without a separately reviewed need. No feature exists merely to fill a menu. Each accepted addition must solve a named user task with bounded CPU, memory, storage, and an understandable failure state.

“Best complexity” means a justified bound for this workload, including I/O and bitmap cost. Do not call a full scan O(1), or describe hash lookup as making substring search constant time. Favor correctness and completeness over an unproven speed claim.

## D003 — Evidence language

Use “observed in source” for directly inspected behavior, “candidate risk” for a path requiring reproduction, “proposed” for a design choice, and “measured” only with a command, artifact, environment and exact commit. A simulated 4 GB AVD is not a physical 4 GB phone. Never label a skipped or unavailable gate PASS.

## D004 — Failure behavior must fail safe

A readable image with missing metadata remains viewable. A denied operation leaves the rest of the app usable. A failed scan is not an empty library. Unsupported system Trash is not an invitation to permanently delete. A failed privacy transformation does not share the original. A failed Vault export does not delete the encrypted source. A corrupt model does not cause a network download.

Original bytes, favorites and notes are protected user data. Derived tags, thumbnails and indexes may be rebuilt only with explicit scoped recovery. Never erase the database or reset encryption keys as a generic crash fix.

## D005 — Story advancement rule

Exactly one story is the active unit of work. Resume an IN_PROGRESS or BLOCKED story from its durable checkpoint. Mark it ACCEPTED only after its Blueprint acceptance criteria and required reproducible evidence pass. The next hourly run then selects the next eligible story in the fixed S01→S32 schedule. A dependency-safe narrow blocker fix belongs to the active story; broad parallel rewrites are not allowed.


## D006 — S02 targeted emulator evidence is correctness/privacy evidence

The reusable S02 hosted-emulator route deliberately excludes performance-only instrumentation from its correctness/privacy suite. The API-35 runner is x86_64 while PhotoBook production APKs are ARM; translated execution is useful for correctness, offline inference, package-permission, Room and access-safety checks but is not native ARM timing evidence.

For offline first-use verification, build/resolve host tooling before isolation, install the already-built app/test APKs, then disable emulator Wi-Fi/data, enable airplane mode, block the hosted runner user's outbound traffic while preserving loopback, and invoke AndroidJUnitRunner directly. This prevents Gradle/UTP dependency resolution from being confused with app network behavior. Dedicated performance workflows remain unchanged until their own stories.


## S24 duplicate completeness

- Near-duplicate indexing may optimize candidate generation only when it preserves the mathematical completeness of the final Hamming-distance predicate. For threshold 8, PhotoBook uses 9 disjoint bands so every pair with distance <= 8 shares at least one band.
- Exact-duplicate performance filters are never identity proof. File size and partial MD5 may reduce work, but full SHA-256 equality is required before an Exact group is published.
- Room-based exact-candidate pruning is permitted only when database photo IDs exactly match the in-memory analysis snapshot. Equal counts alone are insufficient. Any uncertainty or DAO failure fails open to analyzing all supplied records rather than risking a false negative.
- Duplicate result presentation remains non-destructive. Cleanup/deletion selection policy belongs to S25 and later destructive confirmation boundaries.


## S25 cleanup safety

- Cleanup discovery is advisory. New candidates must never become destructive selections automatically.
- Refreshes may retain only an explicit user selection that still exists in the current candidate revision.
- Review surfaces must expose all valid group members through lazy/paged UI rather than fixed member/group caps that silently hide candidates.
- Cleanup reasons must be visible before action so users can understand why an item or group was suggested.
- Android system confirmation remains the destructive boundary. Local cleanup state is reconciled only after confirmed success; cancellation preserves the review state.
- Confirmed cleanup reconciliation must remove stale references, including burst hero IDs that no longer exist after deletion.


## S26 Archive publication revisions

- Archive scan work may continue to safe local persistence, but visible Archive state is latest-request-wins.
- Publication revisions are captured when the user action is requested, not when a coroutine eventually reaches publication.
- Partial and final full-scan summaries must carry the same revision and are discarded after any newer refresh, category/configuration mutation, confirmed action, or sheet dismissal.
- Full Archive scans use bounded keyset pages and honor coroutine cancellation between pages.
- Retention workers may mark records due but must never delete media; destructive deletion remains a foreground Android confirmation.


## S26 Archive publication safety

- Archive scans may do safe local Room work after becoming stale, but stale revisions must never publish UI state.
- Publication revision is captured when the user action begins, not after long synchronization work.
- Full scans remain bounded by keyset pagination and must honor coroutine cancellation between pages.
- Retention workers may mark due state only. Media deletion remains a foreground Android-confirmed action.
