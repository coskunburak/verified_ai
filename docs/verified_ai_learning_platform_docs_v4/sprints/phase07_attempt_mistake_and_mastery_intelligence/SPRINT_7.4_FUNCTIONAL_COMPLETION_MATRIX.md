# Sprint 7.4 functional completion matrix

Source implementation: SOURCE_COMPLETE. Every new or changed test is NOT_EXECUTED / PENDING_OWNER_TEST.
No Sprint 7.4 owner run, hosted CI or production authorization is claimed.

| ID | Requirement / implementation | Authored evidence | Gate | Status |
|---|---|---|---|---|
| A | 17 immutable specific signatures; versioned pedagogical mapping; legacy generic remains readable | AttemptMistakeClassifierTest; check_attempt_mistake_contracts | B/C | SOURCE_COMPLETE; PENDING_OWNER_TEST |
| B | Owned accepted evaluation, execution/source/work binding; first causal disproof only | classifier unit tests; forgedForeignMissingEvidenceAndStaleSourceAreRejected; Python causal goldens | B/G | SOURCE_COMPLETE; PENDING_OWNER_TEST |
| C | Evidence strength, explicit authority and review; unknown/unsupported/incomplete never become confirmed errors; active canonical skill only | strictContractRejectsAuthorityProbabilityTrainingAndMissingEvidence; inactiveOrAmbiguousSkillIsNeverInvented; Swift malformed decoding | B/C/D | SOURCE_COMPLETE; PENDING_OWNER_TEST |
| D | Append-only V036; parent-owner composite FK, exact key conflict, concurrency and atomic rollback | AttemptMistakeIntegrationTest concurrentIdenticalRequestsAndConflictingKeys, transactionRollbackAndSessionCascadeDoNotLeavePartialPublication, databaseRejectsMismatchedEvaluationAndForgedProof, upgradeFromV035AddsNoBackfillAndPreservesOldEvidence | B | SOURCE_COMPLETE; PENDING_OWNER_TEST |
| E | Authenticated versioned POST/GET, strict bounded request; BOLA, rate limits and disabled historical read/replay | AttemptMistakeControllerTest; rollbackDisablementPreservesHistoryAndExactReplay; prior 7.1–7.3 security regressions | B/C/E | SOURCE_COMPLETE; PENDING_OWNER_TEST |
| F | Existing Analyze My Work slice: explicit action, category/reason/first step, existing witness, review, skill/correction; account/revision fences | AttemptMistakeTests; EN/TR classification UI accessibility cases | D | SOURCE_COMPLETE; PENDING_OWNER_TEST |
| G | Seventeen synthetic positive families plus identity/unknown-domain/near-match/ambiguity/domain-hole/unconfirmed-line negatives | attempt-mistake-golden-v1.json; test_attempt_mistake_classification; test_attempt_v2_product; prior evaluation goldens | C/G | SOURCE_COMPLETE; PENDING_OWNER_TEST |
| H | Export/deletion and no training/mastery writes; privacy-safe bounded metrics, zero paid calls | classificationIsImmutableExportableAndDeletedWithoutMasterySideEffects; classificationTelemetryIsBoundedAndProductionFlagNeedsAuthorization; security gate | B/E | SOURCE_COMPLETE; PENDING_OWNER_TEST |
| I | Fresh independent full A–G owner runner; fingerprints, missing/skipped/partial rejection, selective repairs never certify FULL | sprint_7_4_owner_acceptance; sprint_7_4_evidence; test_sprint_7_4_evidence | A–G | SOURCE_COMPLETE; PENDING_OWNER_TEST |
| J | Canonical scope, ownership, retention, rollback, category limitations and traceability | implementation map, execution report, docs_check and manifest | F | SOURCE_COMPLETE; PENDING_OWNER_TEST |

Coverage limits are intentional truth-preserving policy, not claims of universal classification:
CALCULATION_ERROR, UNIT_ERROR, NOTATION_ERROR, INCOMPLETE_SOLUTION and OTHER_REVIEWED lack an automatic
signature predicate. Missing final work is INCOMPLETE_WORK. WORD_VARIABLE_MISASSIGNMENT supports only the
explicit TOTAL operand pattern, not arbitrary prose. Product/quotient combined signatures cannot select
one canonical derivative skill. Disputes/model suggestions/human reviews cannot enter this deterministic
publication route; review workflows belong to Sprint 7.5. Existing v1/v2 attempts are not backfilled.
