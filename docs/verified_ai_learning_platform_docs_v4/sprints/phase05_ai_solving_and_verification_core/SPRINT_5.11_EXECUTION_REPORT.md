# Sprint 5.11 — execution report

## A. Baseline and working-tree protection

Date: 2026-10-30 (Europe/Istanbul). Branch: codex/sprint-5-10-composite-verification.
HEAD and existing local origin/main: 66f00200be48f24f5f690ff32fc0bd376ad952e2 (merged PR #35).
The starting tree was dirty with Sprint 5.10 tracked edits and untracked implementation,
contracts, tests and reports. No branch change, reset, stash, clean, commit, push, merge,
rebase, PR or deployment occurred. Initial status, tracked patch and untracked inventory
are preserved under `.generated/sprint-5-11-baseline/`. No applicable AGENTS.md was found.

Actual local Sprint 5.10 code provides composite-evidence-v1, bounded numeric diagnostics,
source feasibility/domain preservation, contradiction detection, immutable exact run
provenance and capability gating. Its report records owner checkpoints (Linux 283 passed;
backend 616 passed and two paid-model skips; quality gate passed). These are historical
owner-reported checkpoints, not agent execution or acceptance of these changes. Its final
metadata refresh and hosted CI are not established by this session. Local refs do not
confirm the owner's reported newer remote commit; no network fetch or branch mutation was
performed. Existing local implementation was reused and preserved.

Initial dependency: calculus composition was absent. On 2026-10-31, after the owner's
explicit request to complete it, a separate composite-calculus-evidence-v1 adapter and
scoped decision integration were authored. Current state: OWNER_ACCEPTANCE_PASSED.
The complete owner run 20261031-014130-mAGAKm passed A–H, calculus_composite and
integration_dependency with exit 0, including the fixture-network repair. Earlier
generic-only, blocked and failed checkpoints below are retained as history.
Hosted CI is NOT_RUN and rollout is DISABLED.

## B. Implemented behavior and boundaries

A deterministic VerificationPolicy assembles scoped VERIFIED, PARTIALLY_VERIFIED or UNVERIFIED
from validated immutable mathematical evidence and authoritative source/solution/plan metadata.
Exact disproof remains visible regardless of unrelated successful checks or solver agreement.
Partial requires a meaningful supported mathematical subset, not only a domain certificate.
Final-answer proof never claims explanation-step verification. Missing domain/completeness,
operational failure and lack of evidence remain distinct from mathematical contradiction.
Known secondary comparison versions and approved independence are checked through solving's
owned application read. Agreement never votes; unresolved disagreement, incompatible
assumptions, invalid independence or stale/pending corroboration blocks promotion.

V023 publishes separate immutable decisions with exact run/evidence/source/solution provenance,
unique owner/session/request-key binding, insertion validation, append-only triggers and
owned privacy cascades. The application completes canonical reads before acquiring owner
locks, evaluates without a transaction, then rechecks the snapshot before publication.
New source revisions, account deletion, primary replacement, later evidence or changed
secondary signals reject publication/current projection. Current reads never create work.
Same-key reuse returns the original matching decision and rejects changed scope/freshness.

Additive authenticated GET by primary job and POST evaluation of an existing owned run use
stable Problem Details codes. A public allowlist excludes internal keys, owner identity,
secondary payloads, raw math and private reasoning. iOS displays the server verdict separately
from generation, with checked/unchecked methods, uncertainty, conflict and recovery copy in
Turkish/English. Offline/background/refresh/cancelled responses cannot retain an old badge.

Recovery is bounded advice: three runs, a five-minute window, explicit terminal invalid-input,
unsupported, review, contradiction, reconciliation, budget/rate and retry classifications.
No automatic mathematical retry, provider arbitration, unconditional secondary generation,
AI usage reservation, new solver job or paid invocation is introduced. Budget/rate types
are pure classifications; no new paid action is enabled. Existing inference controls remain.

## C. Exact task-local changed-file inventory

This inventory excludes untouched pre-existing Sprint 5.10 files. Files already dirty and
extended here retain their earlier content. Paths are repository-relative.

### Verification

- `services/api/src/main/java/com/verifiedai/verification/application/VerificationArbitrationService.java`
- `services/api/src/main/java/com/verifiedai/verification/domain/model/VerificationDecision.java`
- `services/api/src/main/java/com/verifiedai/verification/domain/policy/VerificationPolicy.java`
- `services/api/src/main/java/com/verifiedai/verification/domain/policy/VerificationRecoveryPolicy.java`

### Solving

- `services/api/src/main/java/com/verifiedai/solving/application/SecondaryCorroborationQuery.java`
- `services/api/src/main/java/com/verifiedai/solving/infrastructure/persistence/SecondarySolveStore.java`

### Persistence and configuration

- `services/api/src/main/java/com/verifiedai/verification/domain/repository/VerificationDecisionRepository.java`
- `services/api/src/main/java/com/verifiedai/verification/domain/repository/VerificationRunRepository.java`
- `services/api/src/main/java/com/verifiedai/verification/infrastructure/persistence/JdbcVerificationDecisionRepository.java`
- `services/api/src/main/java/com/verifiedai/verification/infrastructure/persistence/JdbcVerificationRunRepository.java`
- `services/api/src/main/java/com/verifiedai/verification/infrastructure/privacy/VerificationLifecycleContributor.java`
- `services/api/src/main/resources/application.yml`
- `services/api/src/main/resources/db/migration/platform/V023__add_verification_decisions.sql`

### API and contracts

- `packages/contracts/openapi/public-api.yaml`
- `packages/schemas/verification-decision.schema.json`
- `packages/test-fixtures/verification/decision-v1/contradiction.json`
- `packages/test-fixtures/verification/decision-v1/partial.json`
- `packages/test-fixtures/verification/decision-v1/unverified.json`
- `packages/test-fixtures/verification/decision-v1/verified.json`
- `services/api/src/main/java/com/verifiedai/verification/api/VerificationDecisionController.java`
- `services/api/src/main/java/com/verifiedai/verification/api/VerificationDecisionView.java`

### iOS

- `apps/ios/VerifiedAI/Features/ProblemSolving/Data/Debug/FixturePrimarySolveAPI.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Data/Remote/PrimarySolveAPI.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Domain/Model/SolutionCandidate.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Domain/Port/PrimarySolveServicing.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveView.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveViewModel.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`

### Tests and owner acceptance

- `apps/ios/VerifiedAITests/ProblemSolving/PrimarySolveContractTests.swift`
- `apps/ios/VerifiedAITests/ProblemSolving/PrimarySolveViewModelTests.swift`
- `apps/ios/VerifiedAIUITests/PrimarySolveUITests.swift`
- `scripts/quality/check_contracts.py`
- `scripts/quality/sprint_5_11_owner_acceptance.sh`
- `services/api/src/test/java/com/verifiedai/solving/application/secondary/SecondaryCorroborationQueryTest.java`
- `services/api/src/test/java/com/verifiedai/architecture/VerificationArbitrationArchitectureTest.java`
- `services/api/src/test/java/com/verifiedai/integration/verification/VerificationDecisionIntegrationTest.java`
- `services/api/src/test/java/com/verifiedai/integration/verification/VerificationDecisionMigrationIntegrationTest.java`
- `services/api/src/test/java/com/verifiedai/verification/api/VerificationDecisionControllerTest.java`
- `services/api/src/test/java/com/verifiedai/verification/application/VerificationArbitrationServiceTest.java`
- `services/api/src/test/java/com/verifiedai/verification/domain/VerificationPolicyTest.java`
- `services/api/src/test/java/com/verifiedai/verification/infrastructure/VerificationLifecycleContributorTest.java`
- `services/math-verifier/tests/contract/test_decision_contract.py`

### Documentation

- `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/ai/27_VERIFICATION_ENGINE.md`
- `docs/verified_ai_learning_platform_docs_v4/architecture/14_API_DESIGN_AND_CONTRACTS.md`
- `docs/verified_ai_learning_platform_docs_v4/architecture/51_ERROR_TAXONOMY_AND_RECOVERY_CONTRACT.md`
- `docs/verified_ai_learning_platform_docs_v4/backend/20_BACKEND_MODULE_CONTRACTS.md`
- `docs/verified_ai_learning_platform_docs_v4/data/22_POSTGRESQL_DATA_MODEL.md`
- `docs/verified_ai_learning_platform_docs_v4/data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md`
- `docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `docs/verified_ai_learning_platform_docs_v4/quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `docs/verified_ai_learning_platform_docs_v4/roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.11_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.11_IMPLEMENTATION_MAP.md`

## D. Security, concurrency and correctness review

Source inspection only, not executable validation. Ownership is derived from JWT and checked
again through canonical/source services. Foreign source/run substitution is guarded at
application and PostgreSQL FK/trigger boundaries. The new public read returns null for
absence/staleness and never equates UNVERIFIED with incorrect. Latest evidence invalidation
prevents an older proof hiding a later contradiction. Current projection recomputes and
compares the entire typed decision while preserving immutable history. Release/algorithm
capability changes cannot silently reuse a current verdict. Unsupported versions fail closed.

No long network/provider call occurs under owner locks. Primary/secondary reads share the
existing owner-lock ordering. Evaluation runs between short transactions; tests author
real PostgreSQL barriers for concurrent parse correction, deletion and solution replacement.
Immutable records use <=16 KiB JSON and <=5 mathematical method/evidence references;
secondary snapshots cap at 16, with overflow detected by a limit-17 query. Attempts counting
caps at three. Labels are finite enums; no expressions, UUIDs, student answers or prompts are
metric labels. Account export adds decisions, and deletion cascades through owned evidence.
No model-training eligibility, mastery update or protected dataset mutation occurs.

## E. Deterministic policy matrix and authored tests

| Input | Decision / recovery |
|---|---|
| Complete accepted exact arithmetic/algebra/equation obligations, eligible release | VERIFIED / FINAL_ANSWER; steps explicitly unchecked |
| Meaningful proof with missing domain or completeness obligations | PARTIALLY_VERIFIED / checked method claims only |
| Only domain evidence, numeric support or solver agreement | UNVERIFIED / additional evidence |
| Exact disproof or conflicting exact/numeric evidence | UNVERIFIED / review contradiction; no partial concealment |
| Secondary disagreement, invalid independence, incompatible assumptions, stale/pending signal | UNVERIFIED / reconciliation, not mathematical disproof |
| Timeout/network/resource/admission failure | No proof promotion; bounded retry or rate/limit advice |
| Invalid response or evidence/version mismatch | UNVERIFIED / invalid input; no infrastructure retry laundering |
| Ambiguous/review-required source, stale solution/source | UNVERIFIED or rejected publication; review/fresh calculation |
| Unaccepted release | UNVERIFIED / release blocked |
| Source-bound exact calculus certificate | VERIFIED only within OPEN_INTERVAL, ANTIDERIVATIVE_REPRESENTATIVE or PUNCTURED_NEIGHBORHOOD; otherwise UNVERIFIED |

All authored/extended tests: NOT_RUN_BY_AGENT. Owner acceptance passed in run
20261031-014130-mAGAKm, including the calculus closure and fixture-network regressions.
Paid-model cases remain SKIPPED_BY_POLICY. Exact counts and evidence are recorded below.

- VerificationPolicyTest: generic arithmetic/algebra/equation scope, completeness versus one
  root, disproof, secondary matrix, numerical non-proof/conflict, version/review/stale guards,
  stable ordering, immutable lists, release gates, calculus dependency and bounded recovery.
- SecondaryCorroborationQueryTest: owned port arguments, comparison versions, independence,
  false proof flags, stale lineage, pending state and bounded overflow.
- VerificationArbitrationServiceTest: lock ordering, read without publication, account/source
  reauthorization failure, solution/secondary changes before publication, production flag guard.
- VerificationDecisionIntegrationTest: immutable publication/reuse, new-run invalidation,
  key scope conflict, foreign/wrong session, concurrent publication, current-source replacement,
  export/deletion, rollback, insertion provenance and real evaluation/publication races.
- VerificationDecisionMigrationIntegrationTest: isolated V022→V023, historical parse retention,
  immutable trigger preservation and repeat migration idempotence.
- VerificationDecisionControllerTest: real authenticated HTTP, absent decision, foreign owner,
  UUID key validation, no read-side jobs/decisions/AI ledger rows.
- VerificationArbitrationArchitectureTest: no AI/billing/verifier dispatch dependencies in
  policy/read/controller boundaries; no solving→verification cycle; controllers have no policy.
- VerificationLifecycleContributorTest: updated constructor for the additive decision export.
- Python test_decision_contract.py: shared JSON schema fixtures, every required field, unknown
  versions/enums, forbidden private content, incompatible verified scope and numeric-only badges.
  This is schema validation, not duplicated Python arbitration.
- PrimarySolveContractTests / PrimarySolveViewModelTests / PrimarySolveUITests: shared fixtures,
  statuses/scopes, strict vocabularies, nullable read semantics, GET-only transport, unavailable
  refresh, offline clearing, late response suppression, recovery eligibility and accessible
  localized conflict/status presentation. Existing generated-unverified behavior is retained.

No test, compiler, build, lint, formatter, docs/schema validator, secret scanner, Docker test
build, evaluation runner, XCTest or CI simulation was executed by the agent. Source inspection
alone cannot establish that these tests compile or pass. Passing evidence comes exclusively
from the owner-run checkpoint below. No full-sprint or PRODUCTION_READY claim.

## F. Owner-only acceptance entry point

Run `bash scripts/quality/sprint_5_11_owner_acceptance.sh` from the repository, or use the
absolute-path command in the chat handoff. The script locates the root from any working
directory. Prerequisites: Java 21, Maven, Docker engine, Python 3.11/3.12 verifier environment
with the repository's declared dev dependencies, ripgrep, full Xcode and an available iPhone
simulator runtime >=iOS 17. It installs no host dependencies. Test images are built from the
existing repository Dockerfiles; MinIO uses verified-ai/minio-test:2024-07-16-3535197.

A unique `.generated/sprint-5-11-owner/` directory preserves toolchain/Git inventory, gate
logs, Maven reports, Linux JUnit XML, XCTest result bundle and summary.json. An isolated
simulator is selected by runtime/device compatibility and used by exact UDID. Only its own
container/simulator are cleaned up. Required skipped backend suites fail; Qwen paid tests
remain SKIPPED_BY_POLICY. No paid model credentials are requested and QWEN_OWNER_RUN is unset.
All source is retained; no format command, commit, push or deployment is performed.

The 2026-10-31 script replaces the former unconditional dependency BLOCKED exit with an
explicit required-suite and named-calculus-case audit. It reports calculus_composite=PASS
only after successful backend execution and integration_dependency=PASS only after A–H.
Missing suites/skips fail acceptance; missing tools remain BLOCKED. On a genuine
failed gate, provide acceptance.log, summary.json and the named gate log; the next repair
must target the first actual failure and preserve existing owner work.

## G. Release state, limitations and handoff

OWNER_ACCEPTANCE_PASSED for the complete local acceptance scope, including calculus closure
and the fixture-network repair (20261031-014130-mAGAKm, overall PASS / exit 0).
No hosted CI was queried or run; status NOT VERIFIED. Both arbitration flags default false,
and enabling arbitration outside local/test is rejected. Production rollout is DISABLED.
CAP-VERIFY-002 is Partial, not Complete. No code commit/push/PR/deployment occurred.

CalculusCompositePolicy now validates the exact run/plan/source/candidate and full calculus
metadata before composition. Exact domain obligations are supplied by bounded-calculus-v1;
no generic domain certificate, all-roots check or integral-family completeness is invented.
The three public calculus scopes prevent broader claims. Local owner acceptance passed.
Optional numeric diagnostics remain ephemeral and are accepted only by the pure policy;
durable publication currently consumes exact runs. No automatic numeric execution, secondary
inference or new public retry command is introduced. Public publication is primary-only.
Explanation-step verification remains unsupported and always visible as unchecked.

Measured latency, status distributions and incremental runtime cost are pending owner
measurement. By code boundary, deterministic arbitration introduces no provider invocation;
no monetary or performance figures are fabricated. Rollback disables arbitration, preserves
V023 and historical decisions, and retains account export/deletion. Do not downgrade a
schema containing published decisions. Sprint 5.12 must not treat this unvalidated or
pending calculus closure as release acceptance.

## Final source-review checkpoint

Resumed static review tightened secondary evidence decoding: every required field must exist,
boolean and count fields retain their JSON types, counts stay within the accepted candidate
step bound, and unknown fields/versions reject. A GENERATED_UNVERIFIED secondary row missing
its agreement payload is invalid evidence, not absent corroboration; a stale row remains stale.
New regression cases cover all missing fields and missing completed evidence. Invalid verifier
responses retain INVALID_INPUT classification even after the retry window/attempt bound.
No executable validation was performed during this review. All acceptance remains owner-only.

## Owner checkpoint — 2026-10-30 12:33–12:34 Europe/Istanbul

Evidence directory: `.generated/sprint-5-11-owner/20261030-123320-knDZiL`.
Owner prerequisites passed. Linux verifier completed 317 tests, zero skips/failures,
one dependency deprecation warning, in 14.71 seconds. This is owner-supplied execution
evidence for the unchanged Python/schema scope, not an agent-run check.

First failing gate: backend / Maven testCompile. Production Java compilation completed,
but VerificationDecisionIntegrationTest lines 128 and 141 used a generic TransactionTemplate
execute expression directly inside overloaded AssertJ assertThat. Java could not choose
between assertion overloads and reported incompatible inferred bounds. Classification:
test-source compilation defect. Backend tests did not execute; iOS and Gate H were NOT_RUN.

Repair: bind each transaction result to an explicit Optional<VerificationDecision> local,
then assert that the stored immutable decision contains the original value. Assertions,
transaction semantics and production code are unchanged. The existing acceptance script
remains the complete rerun entry point and will execute all required suites again. Prior
Linux evidence is retained but does not validate the repaired Java tests. All repair
validation remains NOT_RUN_BY_AGENT; no compilation, test or validator was executed here.
Calculus dependency, hosted CI and disabled rollout state remain unchanged.

## Owner checkpoint — 2026-10-30 12:50–12:51 Europe/Istanbul

Evidence: `.generated/sprint-5-11-owner/20261030-125041-xThc5z` and owner-pasted output.
Prerequisites and Linux passed; Linux ran 317 cases without skips (one dependency warning).
Both production Java and test compilation completed, confirming the previous compile repair.
Backend stopped at the first runtime error in
`VerificationDecisionIntegrationTest.immutableRowsAndOwnedCascadeSupportRollbackExportAndAccountDeletion`.
Surefire reported 623 tests, zero assertion failures, one error and 578 skipped under
`skipAfterFailureCount=1`; this is not a backend pass. iOS and later gates remain NOT_RUN.

Root cause: the V023 insertion trigger combined JSON extraction (`->`) and containment
(`@>`) without parentheses around the extracted evidence IDs. In `refs @>
NEW.decision_jsonb->'evidenceIds'`, PostgreSQL applied extraction to the boolean containment
result, producing `operator does not exist: boolean -> unknown`. Classification:
production persistence SQL expression defect, not a test assertion or environment failure.

Repair: parenthesize the JSON extraction operand in both containment directions. The
two-way containment, array-length comparison and fail-closed lineage guard are preserved.
V023 is an unreleased, untracked migration in this worktree; released migrations are unchanged.
Existing integration cases cover valid insertion and rejected forged provenance; they remain
the regression coverage for this repair. No agent-run compilation, test, SQL execution or
validator was performed. Rerun the complete owner acceptance script; the previous Linux
pass does not validate this SQL repair. Calculus remains blocked by Sprint 5.10, hosted CI
is NOT_RUN and production rollout is disabled.

## Owner checkpoint — 2026-10-30 13:13–13:21 Europe/Istanbul

Evidence: `.generated/sprint-5-11-owner/20261030-131313-2nTdrU`, including the existing
`VerifiedAI.xcresult`. Linux passed all 317 tests. Backend completed clean verify with
654 tests, zero failures/errors and two paid-model cases skipped by policy; the required
suite audit passed. This confirms the V023 insertion repair in the owner-run integration
suite. Gates A/B/C/D/F passed. iOS built and all 110 unit tests passed. Of 11 UI tests,
one failed: `PrimarySolveUITests.testHistoryQueuedRunningUnverifiedStepsAndReopen` at
the RUNNING checkpoint (line 106). Gate E failed with exit 65; G/H remain NOT_RUN.

Existing xcresult attachments were exported for inspection only; no new test or build ran.
The screenshot and accessibility hierarchy show QUEUED with polling busy and no error
after XCTest tapped the enabled synthetic advance control in the navigation toolbar.
The same run passed both controlled-fixture ViewModel tests, including held phases,
acknowledgement, cancellation and relaunch. This isolates the observed failure to the UI
stimulus path; the attachments do not prove the exact UIKit/SwiftUI event-dispatch cause.

Targeted repair: move the DEBUG-only controlled advance button into the scroll content,
with a full-width minimum 44-point hit area and explicit capture of the displayed model.
Remove the crowded leading-toolbar placement. Scope the two UI test taps to scroll-view
buttons. The synthetic server still releases the waiting GET; the UI never assigns a job
state. QUEUED, RUNNING, candidate, steps and reopen assertions and their original timeouts
remain intact. Ordinary fixture/live behavior and polling logic are unchanged.

Repair validation is NOT_RUN_BY_AGENT. Prior backend/Linux evidence is retained, but the
changed UI requires the complete owner acceptance rerun. No timeout extension, skipped
test or weakened assertion was used. Calculus remains BLOCKED_BY_SPRINT_5_10, hosted CI
is NOT_RUN and rollout stays disabled.

## Owner checkpoint — 2026-10-30 14:05–14:13 Europe/Istanbul

Latest evidence: `.generated/sprint-5-11-owner/20261030-140545-EIykOa` and the owner-pasted
acceptance output. Implemented scope is OWNER_VALIDATED_IMPLEMENTED_SCOPE; all A–H gates,
prerequisites, Linux and backend are PASS. This checkpoint supersedes earlier pending/failing
checkpoints for the tested implementation while retaining their repair history.

- Linux: 317 passed, no skips, one dependency deprecation warning.
- Backend: 654 reported cases, zero failures/errors, two paid-model cases SKIPPED_BY_POLICY;
  Maven clean verify and the required-suite audit passed.
- iOS: 110 unit tests and 11 UI tests passed; xcodebuild TEST SUCCEEDED. The previously
  failing controlled QUEUED-to-RUNNING/reopen UI case now passed after the content-button repair.
- Quality: lint, documentation (302 Markdown files), contracts, secret scan, five scanner
  tests, seven prompt tests, released artifact immutability and Git diff checks passed.

The first non-PASS required outcome is integration_dependency=BLOCKED, not a failed test.
Overall BLOCKED / exit 2 correctly records missing Sprint 5.10 calculus composition. No
runtime, test or orchestration change is warranted by this result. Repeating the unchanged
command cannot supply the missing composite domain/completeness/provenance contract and
adapter. That prerequisite and its policy integration fixtures must be completed and
owner-accepted before claiming full Sprint 5.11 integration.

This follow-up changes documentation and its manifest only. The owner's Gate H pass covers
the documentation as it existed during that run; these subsequent evidence-recording edits
have not been rerun or validated by the agent. No tests, builds, validators, CI, paid model,
commit, push or deployment were run here. Hosted CI remains NOT_RUN, production rollout is
DISABLED, and full sprint state remains PARTIAL_BLOCKED_BY_SPRINT_5_10. The reproducible
owner entry point remains `bash scripts/quality/sprint_5_11_owner_acceptance.sh`.

## Calculus dependency closure authored — 2026-10-31

Owner authorization: after two generic-scope A–H passing runs, the owner explicitly asked
to complete the remaining calculus dependency and then to continue this implementation.
Current state is IMPLEMENTED_PENDING_OWNER_VALIDATION. The earlier 2026-10-30 BLOCKED
summaries are retained as historical results; their passes do not cover this changed code.

CalculusCompositePolicy is a separate pure adapter tagged composite-calculus-evidence-v1.
CompositeEvidencePolicy dispatches by typed source/solution variant; generic numeric
sampling cannot be attached to calculus. The adapter recomputes the authoritative plan,
requires one planned calculus method, matches all immutable run/solution/source/capability
identities and compares the full CalculusEvidence metadata reconstructed from the selected
source: variable/domain, open interval, target/direction, integral scope, restriction IDs
and restriction ASTs. The original proof and typed diagnostic remain in their immutable run.
VerificationEvidence adds only a variant-neutral outcome accessor; its persisted shape is
unchanged. CompositeEvidenceAssessment accepts a tagged calculus variant without weakening
its generic-v1 evidence requirements or exact-support invariants.

The existing bounded-calculus-v1 worker checks original expression domains and typed source
restrictions before emitting exact proof or disproof. Its exact certificate therefore
establishes the domain only in the declared interval/approach. Numeric limit agreement,
nonfinite limit, unsupported algebra, domain obstruction, timeout and malformed response
never become proof. Domain obstruction advises source review rather than labelling the
answer mathematically false. Exact contradiction wins over solver agreement; secondary
conflict and release gates continue to prevent promotion. Infrastructure recovery retains
the existing attempt/deadline limits. No verifier/provider execution is added to arbitration.

Verified calculus decisions carry one method and its specific public scope:

| Method | Public scope | Excluded claims |
|---|---|---|
| DERIVATIVE_BACKCHECK | OPEN_INTERVAL | Endpoints, a larger domain, explanation steps |
| INTEGRAL_DIFFERENTIATION_BACKCHECK | ANTIDERIVATIVE_REPRESENTATIVE | All antiderivatives, definite integrals, explanation steps |
| FINITE_LIMIT | PUNCTURED_NEIGHBORHOOD | Another target/direction, value at the target, explanation steps |

Generic FINAL_ANSWER is rejected for calculus. Mixed generic/calculus obligations and scope
substitution are rejected in the domain model, shared public schema and iOS decoder.
The newly authored, unreleased verification-decision-v1 schema/fixtures are extended in
place; released canonical, math executor and calculus schemas and Flyway migrations are not
changed. No new migration is needed: V023 already retains solutionVersion and immutable
run/evidence references. Stored earlier decisions are immutable; current reads re-evaluate
against the new policy and suppress incompatible historical results.

iOS displays a scoped verified heading and explicit Turkish/English limitations, separate
from generation state. The DEBUG presentation fixtures exercise the scope copy only; they
are not evidence of live v2 candidate rendering or real solver quality. Existing live solver
and calculus activation restrictions remain in force. Verification does not widen candidate
admission or introduce calculus inference. Existing generic candidates and absent decisions
keep their established presentation.

New/extended authored regression coverage:

- CalculusCompositePolicyTest: all three exact scopes, disproof versus secondary signals,
  numeric agreement/nonfinite/unsupported/domain/failure nonproof, invalid-response/rate/
  bounded retry, interval/direction/target/restriction-AST substitution, stale lineage,
  capability rollback and release flags.
- CalculusProductionIntegrationTest: real authenticated Python worker through source-backed
  canonical v2 admission to composite assessment, durable decisions, owned HTTP projection,
  idempotency, no added execution or AI usage, account export/deletion, latest-run invalidation,
  capability rollback, real domain obstruction and source correction during lock-free
  evaluation with rejection before publication.
- Existing VerificationPolicyTest now rejects mixed generic/calculus evidence as invalid;
  VerificationArbitrationArchitectureTest includes both composite policies in no-inference rules.
- Three shared public calculus decision fixtures; Python schema rejection of widened/mismatched
  scope; iOS shared fixture decoding and negative scope contracts; UI scope-disclosure scenario.
  The synthetic contradiction fixture now includes its contradicted root in unchecked methods,
  matching the tightened iOS contract and the existing shared contradiction fixture.

Additional implementation paths beyond the inventory above:

- services/api/src/main/java/com/verifiedai/verification/domain/policy/CalculusCompositePolicy.java
- services/api/src/main/java/com/verifiedai/verification/domain/model/VerificationEvidence.java
- services/api/src/test/java/com/verifiedai/verification/domain/CalculusCompositePolicyTest.java
- services/api/src/test/java/com/verifiedai/integration/verification/CalculusProductionIntegrationTest.java
- packages/test-fixtures/verification/decision-v1/calculus-derivative.json
- packages/test-fixtures/verification/decision-v1/calculus-integral.json
- packages/test-fixtures/verification/decision-v1/calculus-limit.json

The owner acceptance script requires CalculusCompositePolicyTest and all four new named
calculus integration cases, rejects skipped/missing evidence, then runs the full iOS and
quality gates. Only after A–H succeed does integration_dependency become PASS. The old
unconditional BLOCKED exit is removed; calculusComposite is derived from actual owner-run
backend evidence. Hosted CI remains separate and NOT_RUN; rollout remains DISABLED.

No tests, builds, compilers, linters, formatters, validators, Docker runs, paid models or
CI simulations were executed by the agent. Work was source/log reading and file authoring
only. The complete owner entry point is unchanged; after this change it must be rerun before
any acceptance claim. No commit, push, branch mutation, PR or deployment occurred.

## Owner checkpoint and fixture-network repair — 2026-10-31 01:27–01:35 Europe/Istanbul

Evidence: `.generated/sprint-5-11-owner/20261031-012727-vjmltT` and its existing xcresult
attachments. Linux passed 338 tests without skips (one dependency warning). Backend clean
verify passed 663 reported cases, zero failures/errors, two paid-model policy skips. The
required calculus suite and named integration-case audit passed: calculus_composite=PASS.
A/B/C/D/F passed. iOS built and 111 unit tests passed. The new calculus scope UI scenario
passed, but one of 12 UI tests failed in
`testServerVerificationStatesAndConflictHaveAccessibleHonestPresentation`, on the initial
VERIFIED scenario. E failed with exit 65; G/H and integration_dependency are NOT_RUN.

The existing screenshot/accessibility hierarchy shows `solve.error` with the offline message,
no job snapshot and no verification badge. The local fixture transport was still injected
with the real NWPathMonitor, so a host/simulator path change stopped the synthetic request.
Classification: environment-sensitive test dependency wiring, not calculus policy failure
or a missing/hidden VERIFIED label. The exact host connectivity event is not inferred.

Targeted repair: AppDependencies selects a constant reachable monitor only in DEBUG when
solveFixture is explicitly enabled AND both solve/history transports have the concrete
local fixture types. RootView injects this monitor into those two flow models. All other
features and live services retain the real monitor; the ViewModel offline guards are
unchanged. No timeouts, assertions or test coverage are weakened.

New authored regressions in PrimarySolveViewModelTests run the same dependency selector
with a simulated offline host through queued/running/generated/verified, and prove that a
fixture flag alone cannot bypass a non-fixture service's offline guard. Disabled fixture
selection also preserves the supplied offline monitor. Existing offline verdict-clearing
and no-submit tests remain intact. Additional changed paths: apps/ios/VerifiedAI/App/
AppDependencies.swift and RootView.swift; the fixture monitor remains in the existing
registered FixturePrimarySolveAPI.swift file. No Xcode project changes are needed.

The repair and documentation refresh are NOT_RUN_BY_AGENT. Existing backend/calculus/Linux
owner passes remain evidence for their unchanged code; they do not validate this iOS repair.
Full owner acceptance rerun is required. No agent test, build or validator ran; xcresult
inspection/export only read existing owner evidence. Hosted CI remains NOT_RUN and rollout
DISABLED. Current overall implementation still awaits complete owner acceptance.


## Complete owner acceptance — 2026-10-31 01:41–01:50 Europe/Istanbul

Current local acceptance state: OWNER_ACCEPTANCE_PASSED. Owner evidence directory:
`.generated/sprint-5-11-owner/20261031-014130-mAGAKm/`. The agent read the existing
summary.json and logs; it did not execute acceptance or any validation command.

- Overall PASS, exitCode 0. Prerequisites, A–H, linux, backend, calculus_composite and
  integration_dependency are all PASS.
- Linux math verifier: 338 passed, zero skips, one deprecation warning (15.04 seconds).
- Backend: 663 reported, 661 passed, zero failures/errors, two intentional paid Qwen skips.
- iOS: 113 unit tests and 12 UI tests passed with zero failures; TEST SUCCEEDED.
- The previously failing verification-status/conflict UI case passed. The calculus-scope
  UI case and both fixture-network regressions passed, including preservation of live
  service offline guards.
- Lint, documentation inventory (302 Markdown files), contracts, secret scan, five scanner
  tests, seven prompt/schema tests and released-artifact immutability all passed.
- Paid models: SKIPPED_BY_POLICY. No real-provider acceptance is claimed.
- Hosted CI: NOT_RUN. Production rollout: DISABLED. No release approval is implied.

This run supersedes the earlier pending/blocked/failed local acceptance checkpoints.
After reading it, the agent changed only this acceptance record, the implementation map,
master index and documentation manifest. Those post-run documentation edits have not been
validated by the agent or by this recorded run. Implementation and acceptance script are
unchanged after the passing run. No agent tests, commit, push, PR or deployment occurred.
