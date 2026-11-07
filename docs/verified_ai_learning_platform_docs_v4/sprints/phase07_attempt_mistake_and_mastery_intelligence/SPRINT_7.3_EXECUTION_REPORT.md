# Sprint 7.3 execution report

Date: 2026-11-06, Europe/Istanbul. Baseline: main / 5e248fe1c62140809ea974b81eaba0913a6a18eb.
Initial working tree: clean. Implementation status: SOURCE_COMPLETE.
Agent test execution: NOT_EXECUTED_BY_AGENT. Owner acceptance: VERIFIED_BY_OWNER / LOCAL_ACCEPTANCE_PASSED
for full run 20261107-025110-XrMKJK on 2026-11-07. Hosted CI and production authorization remain unverified.
Earlier pending/failure entries below are historical; the final owner acceptance entry supersedes their local acceptance status.

## Delivered source

- attempt owns AttemptEvaluation and its strict request, thin API, preparer, service and repository.
  StructuredWork and StudentAttempt evidence are unchanged; legacy analysis/verifier contracts remain intact.
- POST evaluates a stored owned work revision explicitly. GET never computes and remains available when
  evaluation is disabled. Source/work changes mark reads historical; publication requires fresh lineage.
- A unique durable operation uses a 20-second lease. Same-key concurrent requests observe IN_PROGRESS;
  a crash can be recovered by explicit same-key retry after expiry. Each claim has a new execution UUID;
  the conditional update rejects late publication. One operation per immutable work revision; a different
  key for the same work is rejected. Source/revision mismatch cannot reuse an operation.
- Existing bounded HTTP v2 verifier and DomainConstraintGuard are reused unchanged. At most two calls:
  ordered sequence plus independent original-to-final comparison. There is no PostgreSQL transaction or
  owner lock across HTTP. Infrastructure failures release the lease and return RETRYABLE, never proof.
- Independent final status is VERIFIED/DISPROVEN/INCONCLUSIVE/NOT_PROVIDED/REVIEW_REQUIRED. Completion
  needs a supported task/form and deterministic equivalence. General simplification, calculus and
  unsupported final forms remain inconclusive. Valid steps alone never claim a correct complete attempt.
- V035 records immutable owner/work/source linkage, policy/execution identity, timestamp and bounded
  evidence. Unique indexes, composite owner FKs, step-binding and update/delete guards protect provenance.
  Account/source cascades reach evaluations; privacy export includes evidence. Training is always excluded.
- iOS real transport, explicit action, per-step/final display, historical badge, safe GET recovery,
  protected retry storage, generation/account fences, EN/TR and accessible non-color labels are integrated.
  Existing capture-only source is never rewritten and evaluation does not open a mastery/learning route.
- Privacy-safe outcome/timing metrics contain bounded status tags only. No new inference provider,
  external model call, dependency installation or production operation.

## Authored evidence, not executed

Backend: AttemptEvaluationPreparerTest, AttemptEvaluationIntegrationTest and AttemptEvaluationControllerTest.
Coverage includes solved-form scope, strict schema/authority rejection, stable IDs, immutable evidence,
privacy cascade/export, disabled reads, BOLA, concurrent replay/conflicts, failure retry, no transaction
at verifier boundary, stale source, work supersession, deletion races and migration from V034.

Python: 15 shared synthetic math cases cover sign errors, rational domain holes, lost roots, inequalities,
absolute branches, sqrt/log extraneous roots, empty sets, unknown domains, ambiguous/unsupported steps,
and incomplete final forms. The existing v2 corpus and supervisor tests are required regressions.
No second math implementation was introduced; Python production behavior remains unchanged.

iOS: nine AttemptEvaluationTests cover shared decoding, false-final rejection, protected pending identity,
explicit action/read-only resume, lost acknowledgement, cancellation/account switch, revised work,
historical state and EN/TR strings. Two UI tests exercise explicit evaluation and accessibility audits.
Existing Sprint 7.2 tests remain in the full acceptance suite.

Contracts: additive evaluation request/artifact/view schemas, shared fixtures, public OpenAPI and
check_attempt_evaluation_contracts. Existing schemas/migrations are not rewritten.

## Owner acceptance and evidence integrity

Entry point: scripts/quality/sprint_7_3_owner_acceptance.sh, full mode.
Outputs: .generated/sprint-7-3-owner/<run-id>/ with source-before/after, gate logs/status/exit codes,
summary.json/summary.md, failure-inventory.json, execution metadata, tool probes and cleanup evidence.
A source/prerequisites; B Java/PostgreSQL; C strict contracts/OpenAPI; D iOS unit/UI/AX;
E fresh security/privacy/abuse prerequisites; F docs/manifest; G Python/goldens/evidence integrity.
Missing tools are BLOCKED. Missing/skipped mandatory tests do not pass. Source mutation invalidates
acceptance. Targeted repair cannot certify FULL. Prior 7.1/7.2 evidence is never imported as a pass.

Static source/diff review and git diff --check are the only verification performed by the agent.
Maven, pytest, Xcode, XCTest, XCUITest, migrations, Docker, acceptance runners and CI simulations
were not executed. No commit, push, reset, clean, deploy, production mutation or secrets read occurred.

## Rollout, rollback and operational ownership

Apply V035 through the owner's migration process before enabling creation. It adds no backfill and
leaves V030–V034 checksums unchanged. ATTEMPT_EVALUATION_ENABLED defaults false and also requires
existing attempt capability; non-local environments retain production authorization checks. The internal
verifier's existing authorization/enablement remains independently required. iOS learns creation capability
from GET evaluation; historical reads/export/deletion remain available with creation disabled.

Rollback disables creation and preserves V035 and compatible readers. Never remove applied migrations
or delete accepted evidence to roll back. A pending lease can be retried explicitly after recovery;
reopening/resume does not automatically spend computation. Invalid/timeout/admission responses do not
publish mathematical evidence. Monitor attempt.evaluation.outcome and attempt.evaluation.duration for
recoverable_failure, persistence_failure and stale_source increases; investigate dependency health using
execution identity without raw learner content. There are no measured SLO or runtime claims yet.

Hosted CI, real-device VoiceOver, ADR-011 commercial/TestFlight acceptance and production authorization
remain BLOCKED_BY_EXTERNAL_GATE. No independent in-scope source work is intentionally deferred.

Explicit source restrictions: v2 proves equivalence within scope, which alone does not prove that
the submitted final set excludes all out-of-scope values. Final correctness therefore stays
INCONCLUSIVE for these tasks; a dedicated containment proof is outside the current engine contract.
Natural denominator/root/log domains embedded in the original syntax remain independently checked.
The shared explicit-scope golden protects against turning scoped equivalence into false completion.

Real-domain scope: canonical v1/v3 normalization currently records UNKNOWN variable domains. The optional learner action
is explicit in the UI and mandatory as a boolean in the wire request. realDomainConfirmed=true reuses REVIEWED_REAL_TASK,
never changes original source/structured evidence, and is frozen into the operation and accepted artifact. Different scope
on the same key/work conflicts; a new work revision is needed after an immutable assessment. Integer/assumption restrictions
cannot be overridden by this flag. The UI labels reviewed scope and defaults confirmation to false for each new work.

## Continued static hardening — 2026-11-06

- Accepted capture cannot be restored as a rejected draft when the following evaluation GET fails.
  testEvaluationReadFailureCannotResurrectAcknowledgedCapture preserves the saved work and recovers by read.
- V035 session membership is deferred; deletion has a single cascade path through structured work.
  This prevents a competing session cascade from hitting the immutable-child guard before parent removal.
  sessionDeletionUsesParentCascadeAndRemovesPendingAndPublishedEvidence covers both artifact states.
- Lease reclaim compares execution identity and expiration in SQL as well as holding the owner lock.
  unexpiredOrAlreadyReclaimedLeaseCannotBeStolen covers active and previously replaced leases.
- iOS evidence counts distinct test identities, so repeated log lines cannot satisfy missing tests.
  Runner sanitization explicitly retains MATH_VERIFIER_ATTEMPT_PRODUCTION_AUTHORIZED=false.
- All added regressions are source-only; NOT_EXECUTED_BY_AGENT / PENDING_OWNER_TEST still applies.

## Owner failure evidence and repair — 2026-11-06

Owner full run 20261106-180138-PBc65W: A/B/C/E/G/cleanup PASS, D/F FAIL, source unchanged.
Fingerprint: 3b515f12477c1ecec5762c70e1e947435459ee1356c2c5aa48c202b636738aa6.
iOS recorded 269 passed cases and two failed evaluation accessibility methods (English/Turkish).
The failures at AttemptAnalysisUITests.swift:162 were Contrast failed, identifying the unsupported-syntax
label in English and tutor navigation in Turkish. Capture/evaluation cards now pair explicit semantic
primary text with opaque semantic surfaces; the screen uses matching design-system background/action
colors, including explicit tutor-link foreground. Accessibility audit remains fully enabled, with no
ignored findings or weakened test assertions. Runtime confirmation is pending owner rerun.

F reported duplicate requirement definitions REQ-PRIV-001 and REQ-EVAL-001. Sprint-local rows now
reference Existing requirements, consistent with the existing traceability convention. Manifest refreshed.

--failed-from now supports F alongside backend/iOS failure selection. For this run it selects A (source
inventory only), D (exactly the two recorded failed methods), and F (documentation checks). B/C/E/G and
passed iOS methods are not rerun or imported as passing current-source evidence. New selector regressions
are authored but not executed. Targeted success is not FULL acceptance. Repaired source remains
SOURCE_COMPLETE / PENDING_OWNER_TEST; agent test/build execution remains NOT_EXECUTED_BY_AGENT.

## Second owner repair run — 2026-11-07

Run 20261107-023130-xMZKfM: TARGETED, source unchanged; A/F/cleanup PASS, D FAIL.
B/C/E/G were NOT_RUN. The Turkish evaluation accessibility case passed including the unchanged
accessibility audit. Only testEvaluationEnglishExplicitActionAndAccessibility failed, before entering
Analyze My Work: AttemptAnalysisUITests.swift:122 could not find history.solve after tapping the history
row on cold launch. This is not a recorded contrast failure; English contrast remains unverified in this run.

The structured UI entry now follows the existing PrimarySolveUITests bounded navigation recovery:
wait for destination, re-deliver the local navigation tap once only if the source row is still present,
enabled and hittable, then require the destination. No evaluation/submission action is retried. Failure
captures hierarchy/screenshot diagnostics; no assertion, Dynamic Type size or accessibility audit is removed.
The repair selector regression covers targeted-run chaining: this run selects A inventory and D with only
the remaining English method. Passed Turkish/F and other suites are not rerun or inherited as current PASS.
Source repairs and the new selector regression are NOT_EXECUTED_BY_AGENT / PENDING_OWNER_TEST.

## Owner-confirmed targeted repair — 2026-11-07

Run 20261107-024129-C3PE05: TARGETED_REPAIR_PASSED, source unchanged.
Fingerprint: 6c2496e6f88211b76a7a5ff0f5939f235e2d9d45e433d5cd805277c79abe8bf0.
A/D/cleanup PASS; B/C/E/F/G NOT_RUN. The sole selected method,
VerifiedAIUITests.AttemptAnalysisUITests.testEvaluationEnglishExplicitActionAndAccessibility,
passed with no failed cases, including the unmodified accessibility audit.
This particular repair is VERIFIED_BY_OWNER at the recorded source fingerprint.

Across the owner runs all originally reported failures now have targeted passing evidence:
documentation and Turkish accessibility in 20261107-023130-xMZKfM, English accessibility in
20261107-024129-C3PE05. These runs are not merged into FULL acceptance. The evidence writer
correctly retains currentSourceAcceptance=NOT_VERIFIED. Full current-source A–G acceptance,
including new evidence-selector regressions, remains PENDING_OWNER_TEST. This documentation-only
record changes the source fingerprint; prior run artifacts remain untouched. Agent executed no tests.
Production/hosted CI/device/TestFlight gates remain unverified and unauthorized.

## Final owner full acceptance — 2026-11-07

Status: VERIFIED_BY_OWNER / LOCAL_ACCEPTANCE_PASSED.
Run: 20261107-025110-XrMKJK. Mode: FULL. Source unchanged: true.
Source fingerprint: 22ade738bf366d69fb01463619f5c2211ee9ac946520759df712936eb0bb008e.
Evidence: .generated/sprint-7-3-owner/20261107-025110-XrMKJK/summary.json.
A/B/C/D/E/F/G and cleanup all PASS; no prior results imported.
Backend: 164 executed cases; verifier: 305 executed cases; iOS: 271 passed cases.
All three reports contain no failures, missing mandatory cases or skipped tests.
The current-source Sprint 7.2 regressions required by this runner were included; its historical
standalone execution reports remain untouched.

This acceptance is bound to the tested fingerprint, not a claim about future changes. The subsequent
acceptance-record/manifest edits are documentation-only and were not part of that run. No runtime,
contract, migration, test or runner source changed while recording this result. The agent ran no tests.
Hosted CI, physical-device accessibility, the ADR-011 50-student TestFlight gate and production
migration/rollout authorization remain unverified. Local acceptance grants no production authorization.
