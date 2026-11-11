# Sprint 7.7 implementation map

The dedicated sprint contract and the sprint master plan govern this change. Roadmap 87 assigns Passport to the same number. Its dashboard, queue and drill assignment are deferred. Sprint 7.6 source is available at 900d0b0. The owner confirmed completion on 2026-11-11. The 7.7 suite runs current-source regressions without a historical report dependency.

| Requirement | Existing component | Implementation target | Owner test source | Verification |
|---|---|---|---|---|
| WP-01–03, temporal estimates | EvidenceStratifiedMasteryPolicy, DOC-86 | HalfLifeRetentionPolicy and the existing BKT update | HalfLifeRetentionCalculatorTest, EvidenceStratifiedMasteryPolicyTest | LOCAL_ACCEPTANCE_PASSED |
| WP-04, immutable history | V038 and the owner lock | MasteryApplicationService separates query from projection writes | EvidenceStratifiedMasteryIntegrationTest | LOCAL_ACCEPTANCE_PASSED |
| WP-05, due decision | DOC-85, Sprint 8.3 §4 and G-PED-02 | SkillReassessmentTriggerService | MasteryDecayReassessmentTest | LOCAL_ACCEPTANCE_PASSED |
| WP-06, delayed confirmation | V031 twins, V037 profiles, accepted attempt adapter | MistakeRetentionResolutionListener, owner-scoped audit in V039 | MistakeRetentionResolutionTest, EvidenceStratifiedMasteryIntegrationTest | LOCAL_ACCEPTANCE_PASSED |
| WP-07, authenticated read | MasteryController and mastery-skills-v1 | Optional temporal fields on existing route | check_mastery_contracts.py, MasteryControllerTest | LOCAL_ACCEPTANCE_PASSED |
| WP-08, dual rings | AttemptAnalysis and ProblemReview | Shared SwiftUI rendering with backend values, EN/TR labels | MasteryPresentationTests, AttemptAnalysisUITests | LOCAL_ACCEPTANCE_PASSED |

Apply additive V039 before enabling the default-off local/test feature. Disable the flag to roll back presentation and resolution; retain source evidence and history for replay. Owner deletion/export includes new audit rows. Counters use fixed decision/state labels. All calculations use local CPU and existing verifier artifacts, with zero new inference calls. No new training eligibility. Owner acceptance requires disposable PostgreSQL and an iOS simulator. Full owner run `run-QcZF0C` passed locally on 2026-11-11. Production authorization is outstanding.
