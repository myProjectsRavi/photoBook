# Blueprint 01 Canonical Backlog

Authorized implementation branch: `autopilot/epics-features-user-stories`  
Baseline: `d693acd7c52f285b6ba475fdd3712a10e419d4e1`  
Execution rule: select the first incomplete story whose dependencies pass. Do not skip an IN_PROGRESS/BLOCKED story. One stable story at a time; large stories use numbered checkpoints under the same story ID.

| ID | Epic | Feature | Depends on | Status | Scope / acceptance anchor |
|---|---|---|---|---|---|
| S01 | E01 Execution safety | Baseline and CI | none | ACCEPTED | Canonical checkpoint files, full review-coverage ledger, correct stale guidance, exact baseline/no-main-change proof. |
| S02 | E01 Execution safety | Baseline and CI | S01 | ACCEPTED | Add `autopilot/**` CI routing, exact-ref hosted verification/evidence manifest, fail missing evidence. |
| S03 | E02 Safe media writes | Editor publication | S02 | ACCEPTED | Move edited-copy provider I/O off Main; journal pending outputs; fail/cancel without partial public media. |
| S04 | E01 Execution safety | Durable state | S02 | ACCEPTED | Real historical Room upgrade fixtures/migration execution; preserve user data; no destructive fallback. |
| S05 | E03 Gallery experience | Navigation | S02 | NOT_STARTED | Photos-first shell with Photos/Albums/Tools, no automatic keyboard, no PRO badge, preserved journeys. |
| S06 | E03 Gallery experience | Album organization | S05 | NOT_STARTED | One typed album/source catalog; source appears once; route filters into Photos with clear scope. |
| S07 | E03 Gallery experience | Design system | S05 | NOT_STARTED | 48dp targets, theme roles, 200% font/accessibility/insets, simplify selection actions. |
| S08 | E03 Gallery experience | Local personalization | S06,S07 | NOT_STARTED | Max six local pinned album descriptors; hide Memories locally; no duplicated membership. |
| S09 | E04 Browsing performance | Decode budget | S02 | NOT_STARTED | Measure viewport-sized thumbnail buckets/cache/resource policy before changing decode behavior. |
| S10 | E04 Browsing performance | Grid continuity | S09 | NOT_STARTED | Stable feed identity/anchor, bounded paging, cancellation-correct scrub and return continuity. |
| S11 | E04 Browsing performance | Progressive startup | S04 | NOT_STARTED | Separate permission/basic/enrichment readiness; safely browse first page before enrichment completes. |
| S12 | E04 Browsing performance | Durable scanning | S11 | NOT_STARTED | Batch/cancellation-aware MediaStore ingestion and access-safe reconciliation at 10k/50k/100k. |
| S13 | E04 Browsing performance | Resource scheduling | S10,S12 | NOT_STARTED | Reduce rebuild work, type revisions, coordinate heavy optional analysis with foreground browsing. |
| S14 | E05 Search and intelligence | Search correctness | S13 | NOT_STARTED | Preserve complete search semantics with truth-table oracle, deterministic ordering and parity/cancellation tests. |
| S15 | E05 Search and intelligence | Search feedback | S14 | NOT_STARTED | Explicit readiness/failure states; no stale-query results; truthful partial/limited/failed UX. |
| S16 | E05 Search and intelligence | In-photo search | S15 | NOT_STARTED | Preserve one OCR layout/session, geometry/insets/reveal behavior; reject stale lifecycle results. |
| S17 | E05 Search and intelligence | Offline maintenance | S16 | NOT_STARTED | Resumable TaggingWorker and local Index status; bounded retries; first-use offline packaged inference. |
| S18 | E06 Viewer and exports | Editing | S03,S07 | NOT_STARTED | Shared EditTransform; preview/export pixel/geometry parity across crop/rotation/tone/orientation. |
| S19 | E06 Viewer and exports | Safe sharing | S18 | NOT_STARTED | Fail-closed privacy preparation, bounded/cancellable sharing, read-only scoped URI grants, no original fallback. |
| S20 | E06 Viewer and exports | PDF export | S19 | NOT_STARTED | Recoverable PDF publication, progress/cancel, checked destination, truthful partial results. |
| S21 | E07 Privacy and cleanup | Vault privacy | S02 | NOT_STARTED | Central Vault session lifecycle cleanup; no stale decrypted previews/search state after lock/background/failure. |
| S22 | E07 Privacy and cleanup | Vault operations | S21 | NOT_STARTED | Transaction-safe add/move-out with per-item results, integrity checks, durable recovery and no data loss. |
| S23 | E07 Privacy and cleanup | Trash management | S22 | NOT_STARTED | Complete paged Trash, explicit unsupported/error states, truthful expiry and confirmation recovery. |
| S24 | E07 Privacy and cleanup | Duplicate analysis | S13 | NOT_STARTED | Fix/prove near-duplicate candidate semantics; exact SHA verification; adversarial completeness tests. |
| S25 | E07 Privacy and cleanup | Cleanup experience | S24 | NOT_STARTED | Reviewable cleanup categories/reasons, nothing destructively preselected, paged groups and confirmation reconciliation. |
| S26 | E07 Privacy and cleanup | Archives | S25 | NOT_STARTED | Conservative Archives, revision-scoped results, bounded scans, no surprise deletion or stale publication. |
| S27 | E08 Useful local features | Private notes | S15,S21 | NOT_STARTED | Restore viewer More note entry; encrypted local notes, stable identity, no plaintext fallback/log/export leakage. |
| S28 | E08 Useful local features | Declutter review | S25 | NOT_STARTED | Restore bounded draft Keep/Trash/Undo review; no media changes until explicit Apply + Android confirmation. |
| S29 | E08 Useful local features | Offline QR transfer | S20 | NOT_STARTED | Harden replay/conflict/byte/lifecycle behavior before exposing Receive/Send preview entry points. |
| S30 | E08 Useful local features | Local memories | S06,S17 | NOT_STARTED | Move discovery to Albums, respect access/hide preference, validate widget IDs and calm story controls. |
| S31 | E09 Compatibility and completion | Compatibility | S01-S30 | NOT_STARTED | Package/privacy/ABI/API matrix, 30,000,000-byte APK + 20 MiB AAB, no INTERNET, dependency/native provenance. |
| S32 | E09 Compatibility and completion | Final verification | S31 | NOT_STARTED | Freeze candidate, whole-branch evidence packet, accumulated diff review, reopen failures, independent-review handoff. |

## Fixed scheduling order

S01 → S02 → S03 → S04 → S05 → S06 → S07 → S08 → S09 → S10 → S11 → S12 → S13 → S14 → S15 → S16 → S17 → S18 → S19 → S20 → S21 → S22 → S23 → S24 → S25 → S26 → S27 → S28 → S29 → S30 → S31 → S32.

## Universal definition of done

A story is ACCEPTED only when its changed behavior satisfies the blueprint examples; regression tests exercise production code; required visual evidence exists for touched screens; no confirmed privacy/data-loss/crash/size/performance regression remains in the tested scope; the final diff is reviewed; exact-commit CI evidence is linked where required; and durable state records limitations/next action. Unavailable gates are BLOCKED/limited evidence, never an invented PASS.
