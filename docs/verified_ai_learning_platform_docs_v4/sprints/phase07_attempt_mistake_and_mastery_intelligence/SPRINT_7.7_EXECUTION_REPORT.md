# Sprint 7.7 execution report

2026-11-11 · `IMPLEMENTED` · `STATICALLY_REVIEWED` · `LOCAL_ACCEPTANCE_PASSED` · `PRODUCTION_NOT_AUTHORIZED`

## Baseline and scope

Work started on a clean `main` at `79bea4185846a42d0859a08d99614c02a8a7a912`. Sprint 7.6 shipped its source at `900d0b0`; the intervening commit changed documentation. The owner confirmed 7.6 completion during this implementation. This report records that confirmation independently of archived run statuses. Sprint 7.7 acceptance applies only to the current source.

The dedicated 7.7 specification agrees with the Sprint Master Plan on confidence, history, decay and reassessment. Roadmap 87’s alternate Passport numbering is superseded for this delivery. DOC-83 supplies the same-hazard remediation evidence rules. DOC-86 defines the temporal mathematics. DOC-85 and the detailed spaced-review rule in Sprint 8.3 specify the 0.72 threshold. This sprint exposes due status. Scheduling stays in the later planning sprint.

## Semantics and write boundaries

- **WP-01–03.** Mastery is the posterior at the last accepted observation. Retention uses the floor 0.05 and the factor `2^(-elapsedDays/H)`. Confidence reflects independent and hint evidence mass with the existing confidence clock. Repeated queries use the same evidence anchor. H starts at 3 days and stays in [1,90]. Independent successes at or beyond half of H use the spaced factor. More recent successes use 1.20. Causal errors shrink H by 0.55. The V038 treatment of revealed solutions as failed retrieval is preserved inside BKT. It grants no causal-error or retention-confirmation authority to the DNA module.
- **WP-04.** The original V038 audit preserves before/after score and H, source identity, effective UTC time, processing time and version. V038 identifies each replay generation by its evidence-set fingerprint. History rows from different generations must never be summed as observations. Duplicate deliveries collapse by evidence ID. The event path still holds the active owner lock and commits derived rows with the learning action. Queries calculate from surviving immutable inputs without replacing profiles, appending audit, or advancing stored time. Future evidence is excluded until its event time. Missing assistance context is unsupported, and v1 mastery rows gain no synthetic history.
- **WP-05.** A skill with last posterior above 0.72 becomes due when its published retention reaches or falls below 0.72. An outstanding seven-day trap check takes precedence. Codes are `RETENTION_THRESHOLD`, `DELAYED_TRAP_CHECK`, and `NOT_DUE`. Confidence remains a separate displayed estimate. There is no additional confidence cutoff in this contract.
- **WP-06.** Effective DNA exposures establish historical recurrence. Two consecutive qualifying independent twin successes on the exact mapped hazard establish pending retention. A distinct independent, correct twin completion at least 168 elapsed hours later establishes stable resolution. A subsequent confirmed same-code error causes relapse. A wrong option alone cannot identify the cause of a mistake. Replay orders by source time, then exposure before drill, then UUID for ties. An assisted completion interrupts the initial independent streak and cannot confirm a pending check.
- **Persistence.** Additive V039 records `trap-retention-v1` transitions. Source foreign keys include the owner. The insert guard checks the hazard, first and second drills, assistance snapshots, correct options and effective timestamps. A null-safe unique key suppresses retries. Dependency deletion cascades and the privacy contributor exports and erases audit rows. Replayed current state reflects disputes, OCR corrections and supersession; earlier decisions remain auditable while their sources are retained. Existing rows require no invented backfill. Historical eligible inputs can be queried immediately after enabling; the next authorized mutation/refresh records their reconstructed decisions.
- **WP-07–08.** The JWT-authenticated skill route defaults to the original representation. Version 2 adds the retention/confidence aliases, due flag, reason and supported outcome’s skill code when enabled. Optional extension fields are omitted on older/disabled responses. Unsupported correctness remains explicitly nullable. AttemptAnalysis links an outcome to the exact skill. ProblemReview labels the account-wide skill detail separately from the parse, which lacks an authoritative skill identity. Both use one localized, accessible dual-ring view. The UI renders server values and offers retry on transport failure.
- **Observability.** `skill_retention_evaluated` uses bounded reasons. New persisted transitions emit `mistake_dna_state_transitioned` after commit. Existing mastery duration/failure metrics and HTTP correlation remain available. No new model invocation, provider input, training consent or logging of learner content.

## Owner verification and delivery checklist

| Definition-of-done items | Source disposition | Execution |
|---|---|---|
| Baseline, authoritative scope, prior policy reuse | DONE | Owner-confirmed entry dependency |
| Mathematical behavior, distinct confidence and retention, due decisions | DONE | LOCAL_ACCEPTANCE_PASSED |
| Durable history, idempotency, mistake integration, new migration | DONE | LOCAL_ACCEPTANCE_PASSED |
| API, schemas, Swift models and both screens | DONE | LOCAL_ACCEPTANCE_PASSED |
| Ownership, privacy, rollback, concurrency, dispute invalidation | DONE | LOCAL_ACCEPTANCE_PASSED |
| Synthetic tests, regression coverage and acceptance runner | DONE | LOCAL_ACCEPTANCE_PASSED |
| Sprint documents, manifest, controlled scope | DONE | Static source review |
| Agent execution restrictions, truthful status, no commit/push/deployment | DONE | Source edits and read-only inspection only |
| Manual command supplied | DONE | LOCAL_ACCEPTANCE_PASSED |
| Release authorization and later-sprint delivery | DEFERRED | Later-sprint delivery and production rollout remain separate decisions |

The [implementation map](SPRINT_7.7_IMPLEMENTATION_MAP.md) identifies the test suites. The [file manifest](SPRINT_7.7_FILE_MANIFEST.md) inventories the source changes. Static review covered time units, record serialization, Swift fixture target membership, SQL owner/lineage constraints, transaction boundaries and owner-runner exit paths.

The owner runs `bash scripts/quality/sprint_7_7_owner_acceptance.sh --full` from the repository. The runner requires macOS, JDK 21, Maven, Docker with disposable PostgreSQL 16, the verifier development dependencies and an iOS 17+ iPhone simulator. Each run has a fresh directory under `.generated/sprint-7-7-owner/`. It checks the authored unit/integration sources, API and JSON contracts, synthetic causal-evidence regressions, Swift decoding/recovery, EN/TR UI accessibility, documentation and source integrity.

## Rollout and remaining evidence

`verifiedai.mastery.decay-reassessment-enabled` defaults to false. Apply V039 before local/test activation and enable the existing evidence-stratified mastery flag. The DNA integration also requires the existing mistake and attempt flags. Disable the temporal flag to roll back the extension. Retain V039, its history and the source events for reactivation. Future migration plans referencing the formerly unallocated V039 must select the next available version.

The owner run exercised compilation, the migration upgrade, concurrent replay and simulator accessibility. Read latency at capacity, physical-device accessibility, hosted CI and production rollout require separate evidence.

## Owner prerequisite run

Run `run-hCZDLl` stopped before the first suite with exit code 2. Its Java log identifies Homebrew OpenJDK 17.0.16. The runner previously stopped after checking the first selected runtime. It now checks both the Java runtime and compiler, continues to other installed candidates until it finds JDK 21, and supplies that JDK to Maven. The subsequent full owner run completed on JDK 21.0.12.

## Local owner acceptance on 2026-11-11

The owner executed the full suite in `.generated/sprint-7-7-owner/run-QcZF0C/`. The final line of `acceptance.log` records a local pass. Git HEAD was `79bea4185846a42d0859a08d99614c02a8a7a912` with the sprint changes in the working tree. The before/after source inventories match at SHA-256 `1376f34037c529650871096017fece05a436c60a4c5ac68780f0108c8d28ad15`. This fingerprint identifies the tested snapshot before the documentation handoff.

| Evidence | Owner result |
|---|---|
| Maven / PostgreSQL | 74 tests, zero failures, errors or skips (`backend-summary.json`) |
| Python causal-evidence regression | 40 passed (`verifier-reports/verifier.xml`) |
| iOS 18.4, iPhone 16e simulator | 11 unit tests and both English/Turkish accessibility UI tests passed (`VerifiedAI.xcresult`) |
| Contracts and documentation | Contract checks passed and documentation check covered 385 Markdown files (`acceptance.log`) |
