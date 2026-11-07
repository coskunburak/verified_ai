# Sprint 7.3 functional completion matrix

Implementation: SOURCE_COMPLETE. Owner local acceptance: VERIFIED_BY_OWNER / LOCAL_ACCEPTANCE_PASSED
for full run 20261107-025110-XrMKJK (2026-11-07). Agent execution: NOT_EXECUTED_BY_AGENT.
Owner run 20261106-180138-PBc65W passed A/B/C/E/G/cleanup and failed D/F; these prior-source results are retained, not promoted to repaired-source acceptance.
ALREADY_IMPLEMENTED denotes reused source, never a new claim of passing tests.

| ID | Requirement and implementation | Test sources | Acceptance gate | Status |
|---|---|---|---|---|
| A | Existing attempt owner; AttemptEvaluation independently versions evidence; captured StudentAttempt and structured work remain unchanged | AttemptEvaluationPreparerTest; durableIndependentEvidencePrivacyRollbackAndImmutability | A/B | SOURCE_COMPLETE; VERIFIED_BY_OWNER |
| B | Explicit POST/GET, strict request, identity/revision, BOLA, default-off capability | AttemptEvaluationControllerTest; sourceChangeSupersessionAndDeletedOwnerBlockLatePublication | B/C/E | SOURCE_COMPLETE; VERIFIED_BY_OWNER |
| C | Existing bounded real-domain v2 engine, stable step UUID/index mapping, uncertainty stops sequence | test_attempt_evaluation_golden; existing test_attempt_v2_product; AttemptEvaluationPreparerTest | B/G | ALREADY_IMPLEMENTED engine + SOURCE_COMPLETE adapter; VERIFIED_BY_OWNER |
| D | Independent final proof; solved form plus original-task equivalence; missing/unsupported/review-required states | 15 shared math cases, completionRequiresTaskAndSolvedFormNotLocalEquivalence, strictSharedEvidenceRetainsStableIdsAndNeverClaimsMissingFinal | B/C/G | SOURCE_COMPLETE; VERIFIED_BY_OWNER |
| E | Prepare/execute/publish without transaction across HTTP; durable lease, same-key replay, payload conflict, execution fence and recoverable failures | concurrencyReplayAndDifferentPayloadConflictNeverDuplicateArtifact; failuresAreRecoverableAndLateExecutionCannotOverwriteNewLease; unexpiredOrAlreadyReclaimedLeaseCannotBeStolen | B | SOURCE_COMPLETE; VERIFIED_BY_OWNER |
| F | V035 immutable derived evidence, exact owner/source/work linkage, unique publication, bounds, cascade deletion and export | durableIndependentEvidencePrivacyRollbackAndImmutability; upgradeFromV034DoesNotBackfillAssessments; prior V032/V033 upgrade regressions; sessionDeletionUsesParentCascadeAndRemovesPendingAndPublishedEvidence | B/E | SOURCE_COMPLETE; VERIFIED_BY_OWNER |
| G | iOS real transport and explicit action; independently presented final status, non-color verdicts, historical state, EN/TR, Dynamic Type | AttemptEvaluationTests; two evaluation UI/AX cases | C/D | SOURCE_COMPLETE; VERIFIED_BY_OWNER |
| H | Read-only reopening/lost acknowledgement recovery; protected exact retry identity; cancellation/account fences; no inheritance by revised work | testLostAcknowledgementRecoversByReadWithoutSecondComputation; testCancelledAndAccountSwitchedResponsesCannotPublish; testExplicitActionAndResumeReadOnly; testSupersededWorkDisplaysHistoricalEvaluation; testEvaluationReadFailureCannotResurrectAcknowledgedCapture | D | SOURCE_COMPLETE; VERIFIED_BY_OWNER |
| I | Privacy-safe bounded metrics, zero paid calls, training excluded, no mistake/mastery publication | durableIndependentEvidencePrivacyRollbackAndImmutability; fresh backend security prerequisites | B/E | SOURCE_COMPLETE; VERIFIED_BY_OWNER |
| J | Fresh source-aware A–G runner, current 7.2 regressions, docs/manifest, rollback-compatible reads | test_sprint_7_3_evidence; check_attempt_evaluation_contracts; docs_check | A/C/F/G | SOURCE_COMPLETE; VERIFIED_BY_OWNER |

## Supported mathematical boundary

Existing v2 real-domain equivalence establishes local proof or disproof; unsupported notation,
calculus capture, missing domains/assumptions and ambiguous transcription remain uncertain.
Final completion is supported for numeric EVALUATE answers and isolated equation/inequality
answers or explicit real solution sets with scalar endpoints. Algebraic irrational endpoints
and general SIMPLIFY completion remain conservatively inconclusive, even if locally equivalent.
Original natural domains and explicit restrictions are preserved by the existing DomainConstraintGuard.
No sampling-based universal proof, guessed domains, paid fallback, automatic mastery or training labels.
A correct final answer does not imply a correct reasoning chain. Original indices include final-role
steps; independent final evidence has its own one-step comparison, bound to finalStepId.

## External gates

| Condition | Status | Required evidence |
|---|---|---|
| Build, tests, PostgreSQL migration/upgrade and simulator | VERIFIED_BY_OWNER | Full run 20261107-025110-XrMKJK; all A–G gates PASS |
| Hosted CI, physical-device VoiceOver | BLOCKED_BY_EXTERNAL_GATE | Owner current-source CI/device evidence |
| ADR-011 / 50-student 30-day TestFlight | BLOCKED_BY_EXTERNAL_GATE | Actual cohort Go/No-Go evidence |
| Production migration/rollout and paid inference | BLOCKED_BY_EXTERNAL_GATE | Separate explicit owner authorization |

Sprint 7.2 remains SOURCE_COMPLETE with repaired-source PENDING_OWNER_TEST. Prior runs cannot
substitute for this runner's current-source regression evidence. Rows A–J are VERIFIED_BY_OWNER for the final full-run fingerprint recorded below.

Explicit source restrictions: v2 proves equivalence within scope, which alone does not prove that
the submitted final set excludes all out-of-scope values. Final correctness therefore stays
INCONCLUSIVE for these tasks; a dedicated containment proof is outside the current engine contract.
Natural denominator/root/log domains embedded in the original syntax remain independently checked.
The shared explicit-scope golden protects against turning scoped equivalence into false completion.

2026-11-07 owner targeted rerun 20261107-023130-xMZKfM passed documentation and the Turkish evaluation
accessibility method. English failed at history navigation before evaluation/audit. The bounded navigation
repair is pending owner rerun; historical passes do not establish FULL current-source acceptance.

2026-11-07 targeted repair 20261107-024129-C3PE05: A/D/cleanup PASS, only the English evaluation
UI/accessibility method executed and passed. That repair is VERIFIED_BY_OWNER for the recorded
fingerprint; the original D/F failures have targeted passing evidence across the two repair runs.
Do not combine those runs into full acceptance: currentSourceAcceptance remains NOT_VERIFIED,
and full current-source acceptance is PENDING_OWNER_TEST.

Final full owner run 20261107-025110-XrMKJK supersedes the earlier pending local-acceptance entries:
A–G/cleanup PASS, FULL, unchanged source, 164 backend / 305 verifier / 271 iOS cases, no skipped or
missing cases. Fingerprint: 22ade738bf366d69fb01463619f5c2211ee9ac946520759df712936eb0bb008e.
Acceptance-record edits after the run are documentation-only, not newly executed evidence.
External release gates remain unverified; no production rollout is authorized.
