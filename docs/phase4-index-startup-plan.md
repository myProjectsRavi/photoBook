# Phase 4 — index/startup optimization plan

Phase 4 starts from merged `main` at `c8e0b67e452467beae5ad8d1c1e07c8c68b6052b` after the fully green Phase 3 10k/50k/100k certification.

## Why this phase

Phase 3 proved PhotoBook remains stable at 100k photos on the constrained Android 15 emulator, but first-build indexing is still expensive. The 100k certification reached index-ready in roughly 25 minutes while remaining approximately linear from 50k to 100k. This is the clearest measured optimization target after Phase 3.

The current first-build path is intentionally conservative:

1. scan MediaStore;
2. build records serially, including EXIF extraction and optional offline reverse geocoding;
3. persist the complete Room/FTS replacement in bounded batches;
4. publish the immutable PhotoIndex snapshot;
5. mark search/UI ready.

Phase 3 already bounded Room persistence allocations, so Phase 4 must measure where the remaining wall-clock time is spent before changing concurrency or publication semantics.

## Non-negotiable invariants

- No `INTERNET`, telemetry, accounts, cloud APIs, remote/deferred models, or crash uploaders.
- Preserve Room/FTS as authoritative persistent state and PhotoIndex immutable-generation semantics.
- Preserve Android 14 limited/revoked photo access behavior.
- Preserve originals and all existing destructive-action confirmation semantics.
- Keep generated APKs <= 30 MB and release AAB <= 20 MB.
- Do not weaken Phase 0/1/2/3 tests, rollback paths, or fail-closed behavior.
- Do not trade low-RAM stability for throughput; 2 GB constrained-device certification remains mandatory.

## Step 4A — measurement only

Before production optimization, capture separate timings for:

- MediaStore scan;
- record construction;
- EXIF extraction / location enrichment contribution;
- Room + FTS first-build persistence;
- PhotoIndex publication to UI/search readiness;
- persisted-index warm startup versus first-build startup.

Measurements must run on deterministic 10k/50k/100k fixtures and record library size, API level, RAM, emulator/physical-device status, and the exact commit SHA.

## Step 4B — choose one optimization

Only after Step 4A evidence, select the dominant cost. Candidate directions are deliberately hypotheses, not pre-approved changes:

- bounded parallel record construction if EXIF work dominates and memory remains bounded;
- cheaper/lazy enrichment if location/EXIF fields dominate and user-visible semantics can be preserved;
- persistence pipeline improvements if Room/FTS remains dominant;
- early publication of an already-persisted snapshot on warm launch while reconciliation continues, if warm-start readiness is the dominant user-facing delay.

Do not combine multiple production changes in one candidate.

## Certification gate

A Phase 4 production candidate may merge only if:

1. normal Android Verification is green;
2. deterministic parity/correctness tests are green;
3. 10k/50k/100k benchmark evidence shows an actual improvement in the selected target;
4. 2 GB Android 15 stress certification completes all lifecycle iterations with no ANR/OOM/fatal crash;
5. privacy/offline and APK/AAB size gates remain green;
6. the final diff is narrow enough to attribute the measured improvement to the selected change.

If Step 4A shows no meaningful bottleneck worth changing, Phase 4 should stop without a production patch rather than optimize speculatively.
