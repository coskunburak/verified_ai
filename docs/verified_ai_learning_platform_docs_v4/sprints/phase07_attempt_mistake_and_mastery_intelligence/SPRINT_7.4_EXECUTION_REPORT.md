# Sprint 7.4 execution report

Date: 2026-11-07, Europe/Istanbul.
Baseline: main / 5e248fe1c62140809ea974b81eaba0913a6a18eb.
Initial tree contained owner-owned Sprint 7.3 source, tests and documentation changes; these were preserved.
Implementation: SOURCE_COMPLETE. Agent execution: NOT_EXECUTED. Acceptance: PENDING_OWNER_TEST.
Production: PRODUCTION_NOT_AUTHORIZED. No commit, push, deployment or migration execution occurred.

## Sprint 7.3 dependency audit

The actual working tree contains AttemptEvaluation, AttemptEvaluationService/Preparer/Repository,
V035 and strict request/artifact/view contracts. Accepted evidence is immutable and server-owned;
ordered steps stop at the first disproof or uncertainty, keeping NOT_ASSESSED distinct. Execution UUID,
verifier/taxonomy version, source identity/revision, work revision and stable step UUIDs are retained.
Publication validates the trusted verifier response and source freshness under the owner lock.
Learner capture, model labels and final-only disagreement cannot manufacture causal verification.

The local owner evidence exists at .generated/sprint-7-3-owner/20261107-025110-XrMKJK/summary.json:
FULL, sourceUnchanged=true, A–G/cleanup PASS, fingerprint
22ade738bf366d69fb01463619f5c2211ee9ac946520759df712936eb0bb008e.
This is prior-source Sprint 7.3 evidence, not acceptance of the present Sprint 7.4 tree. The agent did
not reproduce that run. No prerequisite integration interface is missing; fresh regression remains required.

## Delivered behavior and ownership

mistake module exposes pure MistakeTaxonomy and MistakeClassificationDecision semantics. attempt owns
the accepted evaluation adapter, thin API, bounded transaction, immutable artifact, repository and privacy
lifecycle. No dependency from mistake back into attempt internals is introduced.

All 17 specific codes remain unchanged. mistake-category-v1 maps each to a defensible pedagogical
category; generic and legacy invalid transformations have no pedagogical category. Structural disproof
is an evidence band, never a probability, calibrated accuracy claim or inference about mental intent.
Canonical skills are assigned only by five explicit mapping rules and active CurriculumTaxonomyCatalog
membership. No legacy attempt-skill-v1 identifier is repurposed as a canonical ontology identity.

Python reuses the bounded structural predicates and existing exact disproof/domain engine. It collects
supported matches and falls back to INVALID_TRANSFORMATION when different signatures compete. Source
holes, lost/added roots, calculus limitations and optional rational witnesses remain verifier-owned.
A mathematical disproof need not have a rational counterexample: the linked accepted evaluation is
retained even when counterexampleStatus is COUNTEREXAMPLE_UNAVAILABLE.

The public classification POST consumes evaluationId/source/workRevision, never caller labels or owner IDs.
It reauthorizes ownership, validates accepted evidence, checks revision/supersession and performs a short
single transaction under the existing owner lock. There is no HTTP/provider work inside that transaction.
A unique evaluation/policy publication and unique owner/problem key prevent double writes; exact replay
survives rollout disablement. Conflicting key/evidence pairs fail with IDEMPOTENCY_KEY_REUSED. Missing
accepted evaluation returns ATTEMPT_CLASSIFICATION_NOT_READY. Source change returns ATTEMPT_STALE_SOURCE.
GET never writes and historical evidence remains accessible. Legacy attempts without structured work
remain on their original read contracts. No legacy backfill or mastery/learning event is emitted.

V036 binds owner/evaluation/attempt with a composite FK and validates source/execution/work/step evidence
in a trigger. Accepted classification rows cannot be updated or independently deleted. Parent deletion
cascades through evaluation; privacy export includes classifications. JSON is bounded to 8192 bytes and
training_eligible=false is enforced in contract, domain and database. Existing migration files are unchanged.
History reads and privacy export revalidate each classification against its original accepted evaluation.
Future policy versions require additive migration/compatible readers, retaining old evidence rather than
updating it. Current rollout does not expose a reclassification/review-write API.

The existing Swift Analyze My Work screen now offers explicit classification only after accepted evaluation.
It displays category, first wrong step, existing evaluation witness, precise reason, review and evidence band,
canonical skill where supported and evidence-grounded correction text. Unsupported/uncertain results stay
explicit. A stable evaluation-bound retry key survives relaunch without new persisted learner data. Reopen
uses GET; account/generation/evaluation fences reject late responses. Evaluation remains visible on transient
classification failure. EN/TR and semantic non-color accessibility labels are included.

## Quality sources — not execution evidence

- Java: AttemptMistakeClassifierTest, AttemptMistakeControllerTest, AttemptMistakeIntegrationTest;
  existing ModularityTest plus current 7.1–7.3 backend/authz/privacy/regression suites remain mandatory.
  The V035 upgrade case copies synthetic accepted work/evaluation into an isolated schema, compares complete
  parent/evaluation rows after V036, reads the old artifacts and checks that no classification was backfilled.
- Python: seventeen positive signature requests with evidence justifications; identity/unknown-domain
  guards for every signature; near-match, valid alternative syntax, explicit hole restriction, unconfirmed
  line, unsupported period, input bound and ambiguous-cause tests. Existing verifier supervisor tests remain.
- Contracts: strict request/artifact/view schemas, fixtures, category mapping, OpenAPI, Java/Swift mapping
  consistency, malformed authority/confidence/skill/training/version/evidence negatives.
- iOS: nine AttemptMistakeTests for positive shared decoding, malformed evidence, explicit unsupported
  state, lost acknowledgement, offline retry, account switch, revision isolation, stale-source recovery and localization;
  two EN/TR classification UI accessibility cases, plus full relevant existing suites.
- Acceptance evidence: independent Sprint 7.4 writer regression sources, missing/partial/skipped evidence,
  source mutation, run identity, current gate artifacts and prior-sprint isolation.

All new and changed tests are NOT_EXECUTED / PENDING_OWNER_TEST. Only source inspection, patch review,
file comparisons and git diff --check are performed by the agent. No Maven, pytest, Xcode, simulator,
contract checker, migration, Docker, evaluation harness or owner acceptance runner was executed.

## Owner acceptance and recovery

Entry point: scripts/quality/sprint_7_4_owner_acceptance.sh --full.
Fresh outputs: .generated/sprint-7-4-owner/<run-id>/summary.json, summary.md, failure-inventory.json,
gate-A through gate-G records, source-before/after, raw logs and owned simulator cleanup evidence.
A preflight/migration/prerequisites; B Java/PostgreSQL; C strict contracts/OpenAPI; D full iOS and UI/AX;
E fresh security/privacy/authorization; F docs/manifest; G Python/goldens and evidence integrity.
No earlier sprint result is imported. Missing tools/tests and unexpected skips cannot pass. New source
fingerprints invalidate prior acceptance. Targeted gates/failed-method repairs never constitute FULL.
Known non-Linux resource-enforcement skips remain independently visible as platform-specific pending
validation; they do not grant Linux production resource evidence.

ATTEMPT_CLASSIFICATION_ENABLED defaults false and also requires existing attempt capability; non-local
environments require existing explicit production authorization. Rollback disables this flag, preserves
V036 and compatible readers and keeps accepted history/export/deletion operational. Publication failure
rolls back fully; read then explicitly retry the same key. No production settings were changed.

DERIVED_LEARNING retention follows the owning evaluation/account, without new raw images/prompts or
provider copies. Account deletion removes live rows; encrypted backups follow existing backup expiry and
restore deletion reconciliation. No immediate backup erasure or new retention duration is claimed.

Metrics: attempt.classification.outcome with bounded outcome/authority/policy/taxonomy, and
attempt.classification.duration. No learner IDs, attempt IDs, math content or evidence IDs are metric tags.
Observe evidence_rejected, persistence_failure, review/unsupported outcomes and replay; investigate via
existing restricted request correlation, not raw learner-content logs. SLO/precision/latency are unmeasured.
New verifier calls per classification: 0. New paid model calls: 0. Expected incremental inference cost: 0.
MISTAKE_CLASSIFY capability remains dormant; no route/provider authorization or model-quality claim is made.

## External gates and scope limitations

Hosted CI, staging migration/rollout, physical-device VoiceOver, ADR-011/TestFlight and real-student precision
remain independently unverified. Source completion does not authorize production or certify acceptance.
Calculation slips, dimensional units and notation errors have no new predicates. Incomplete valid work is
not an error. OTHER_REVIEWED requires a future human-review workflow. Arbitrary word-problem semantics
are unsupported beyond explicit TOTAL omission. Unknown taxonomy versions fail closed; old v1/v2 read
contracts stay intact. Mistake Book, mastery recalculation, planner and other 7.5–7.10 work are out of scope.

### Signature-policy compatibility

New deterministic diagnoses optionally carry classifierPolicyVersion=structural-signatures-v1. Java/Python/Swift
and embedded evaluation schemas accept this additive field and continue reading old diagnoses without it.
Sprint 7.4 requires this marker for a specific structural category. A historical specific code without the
marker retains its accepted disproof but produces generic INVALID_TRANSFORMATION / REQUIRED with no category
or skill. Original evaluation evidence is never rewritten. Unknown policy markers fail closed.
Operational rollout must install compatible API readers before the Python producer, then validate owner
evidence before enabling classification. An older strict API reader fails closed on new producer output;
rolling back the API also requires restoring the compatible producer. Flag-only classifier rollback retains
current readers and producer and is the preferred forward-only recovery. No production rollout was performed.

## Owner-run failure repair — 20261107-114912-CFdjVI

The owner executed FULL acceptance on unchanged source: A/C/F/G and cleanup passed, B/D failed,
and E was blocked by missing successful backend evidence. This run is retained unchanged. It does not
accept the repaired source. No test, build or migration was executed by the agent during repair.

All 25 reported backend errors occurred in fixture setup with relation users missing. The V034 upgrade
test changed a pooled connection's schema without restoring it, then dropped that schema; inherited test
execution contaminated subsequent classification/work/evaluation tests. The connection now restores its
previous schema in finally before returning to the pool, with explicit schema/table visibility assertions.

Two classification UI failures were traced to the saved XCTest accessibility hierarchy: every card child,
including the visible explanation, inherited attempt.classification.card. The card now declares a containing
accessibility element so its explanation retains its own identity. Two evaluation UI failures reported
contrast on the leftover empty manual composer's help text; the saved failure screenshot shows this text
clipped under the navigation bar. Accepted structured work now uses its existing read/revise flow without
reopening the unrelated empty composer. Explicit revision restores the editing controls. UI assertions
cover composer absence, available revision, unique explanation and removal of the completed action.
Full accessibility audits remain enabled without issue filtering or exclusions.

The existing --failed-from mode selects exactly the 25 failed Java methods and four failed XCTest methods
from this run, with gate A preflight. It does not rerun C/F/G or claim that E is satisfied by a partial backend
run. New evidence is written in a fresh run directory with TARGETED scope and current-source fingerprints.
Repair status: SOURCE_COMPLETE / PENDING_OWNER_TEST. Production remains PRODUCTION_NOT_AUTHORIZED.
