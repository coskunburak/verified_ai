# Sprint 4.8 Production Implementation Plan
## User-Correctable Parse Review, Immutable Revision History, and Selected-Parse Authority

**Project:** Verified AI Learning Platform  
**Phase:** Phase 4 — Problem Capture and Canonicalization  
**Sprint:** 4.8  
**Status at Start:** Sprint 4.7 COMPLETE  
**Primary Objective:** Introduce a production-grade user correction lifecycle for `ProblemParse` without weakening provenance, immutability, authorization, privacy, or downstream canonical/classification authority.

---

# 1. Executive Summary

Sprint 4.8 is not a simple “edit parsed text” feature.

The sprint introduces a new authoritative workflow in which a learner can inspect an AI-generated parse, correct semantic mistakes, and persist the correction as a **new immutable user-owned parse revision**.

The sprint must preserve the entire historical chain:

```text
RecognitionEvidence
        ↓
AI ProblemParse R1
        ↓
User review
        ↓
USER ProblemParse R2
        ↓
Selected/current parse authority
        ↓
CanonicalProblem C2
        ↓
ProblemClassification for C2
```

The critical principle is:

```text
Existing ProblemParse revision
        ≠
Mutable working document
```

A correction must never mutate the existing revision.

Instead:

```text
AI R1
  ↓
USER R2
  ↓
USER R3
```

Each revision is immutable, attributable, auditable, and linked to its parent.

The second major objective is to make:

```text
problem_sessions.current_parse_id
```

the authoritative parse selection pointer.

This matters because “latest parse” and “selected parse” are not always the same thing.

Example:

```text
AI R1
  ↓
USER R2   ← selected
  ↓
AI retry R3
```

The most recent database row is R3, but R2 must remain authoritative because the user explicitly corrected and selected it.

Sprint 4.8 therefore establishes the exact downstream contract needed before Phase 5 solving:

```text
Solve must operate from
selected parse
    ↓
selected canonical representation
    ↓
selected classification context
```

---

# 2. Sprint Goals

Sprint 4.8 must deliver all of the following.

## 2.1 Functional goals

- Allow the authenticated learner to review the current parse.
- Allow the learner to edit supported semantic fields.
- Persist every correction as a new immutable `ProblemParse` revision.
- Preserve AI and user provenance separately.
- Preserve parent-child revision lineage.
- Store and enforce the currently selected parse.
- Provide revision history.
- Detect stale editing conflicts.
- Provide idempotent correction creation.
- Ensure canonicalization uses the selected parse.
- Ensure old canonical/classification results become historical after a correction.
- Add iOS review/edit/recovery UX.

## 2.2 Non-functional goals

- No silent historical mutation.
- No fake AI provenance for user-created revisions.
- No raw student content in generic logs or analytics.
- No correction content automatically classified as training data.
- Strong object-level authorization.
- Concurrency-safe revision allocation.
- Retry-safe mutation semantics.
- Stable OpenAPI contract.
- Full Testcontainers coverage.
- Swift ViewModel, repository, DTO, UI, and accessibility coverage.
- Production documentation synchronized before closing the sprint.

---

# 3. Explicit Non-Goals

Sprint 4.8 must not expand into later roadmap scope.

The following are explicitly out of scope:

- Solution generation.
- Mathematical verification.
- Mastery updates.
- Attempt recording.
- Mistake classification.
- Adaptive learning.
- Exam functionality.
- Model fine-tuning.
- Training pipeline creation.
- Automatic model retraining from user corrections.
- Treating user corrections as verified ground truth.
- Automatic AI re-parse after every manual correction.
- Full session history/recovery UX beyond parse revision history.
- “Restore arbitrary old revision as current” UX.
- Broad problem history.
- Calculus canonicalization expansion.
- New microservice extraction.
- New standalone correction service.
- New standalone revision service.

Those belong to later sprints.

---

# 4. Core Domain Invariants

These invariants are mandatory and should be encoded in code, database constraints, and tests.

## INV-4.8-01 — Parse revisions are immutable

Once persisted, a parse revision must not be semantically updated.

Forbidden:

```text
UPDATE problem_parses
SET normalized_problem_jsonb = ...
WHERE id = existing_parse_id
```

Required:

```text
existing revision remains unchanged
+
new revision is inserted
```

---

## INV-4.8-02 — User correction creates a new revision

Every successful correction creates:

- a new parse UUID,
- a new session-scoped revision number,
- source `USER`,
- a parent parse reference,
- correction metadata,
- a new `created_at`.

---

## INV-4.8-03 — User correction must have a parent

Every `USER` parse must point to the revision the user edited.

Example:

```text
R1 AI
 ↓
R2 USER
 ↓
R3 USER
```

Lineage:

```text
R3.parent = R2
R2.parent = R1
R1.parent = null
```

---

## INV-4.8-04 — AI and USER provenance are different

An AI revision must have AI operational provenance.

A USER revision must not fabricate AI operational provenance.

Forbidden user revision metadata:

```text
provider = "USER"
model = "human"
prompt_id = "manual"
request_units = 0
```

These values would falsely imply AI execution.

Instead a USER revision should carry:

```text
source = USER
parent_parse_id
user_id
problem_session_id
recognition_evidence_id
recognition_evidence_revision
correction_reason
corrected_fields
created_at
```

---

## INV-4.8-05 — Selected parse is authoritative

The selected parse must be determined from:

```text
problem_sessions.current_parse_id
```

Not:

```text
MAX(problem_parses.revision)
```

Not:

```text
latest created_at
```

Not:

```text
latest AI parse
```

---

## INV-4.8-06 — AI retry cannot silently override a user correction

If the current selected parse is USER-owned, a later AI retry may create history but must not change the selected pointer automatically.

---

## INV-4.8-07 — Correction is server-validated

The mobile client cannot mark arbitrary content as valid.

The backend must validate the corrected document using the same trusted semantic contract used for accepted parses.

---

## INV-4.8-08 — Correction is not ground truth

A user correction is useful product data, but it is not automatically:

- verified mathematical truth,
- evaluation gold data,
- training data,
- model fine-tuning data,
- taxonomy truth.

Default policy:

```text
trainingEligible = false
```

---

## INV-4.8-09 — Downstream authority follows selected parse

Canonicalization must derive from the selected parse.

Classification must derive from the canonical problem produced from that selected parse.

---

## INV-4.8-10 — User edits are ownership-scoped

A user cannot correct:

- another user’s session,
- another user’s parse,
- another session’s parse,
- a parse that does not belong to the target session.

---

# 5. Target End-to-End Lifecycle

The final runtime lifecycle should be:

```text
Capture
  ↓
Private Asset Upload
  ↓
Preprocessing
  ↓
RecognitionEvidence
  ↓
ProblemParse AI R1
  ↓
problem_sessions.current_parse_id = R1
  ↓
iOS Parse Review
  ↓
User edits
  ↓
POST correction
  ↓
Validate base revision
  ↓
Validate corrected semantic document
  ↓
Create USER R2
  ↓
problem_sessions.current_parse_id = R2
  ↓
old canonical becomes historical
  ↓
canonicalize R2
  ↓
new CanonicalProblem
  ↓
classification required for new canonical
  ↓
Phase 5 solver-ready state
```

---

# 6. Package A — Database Foundation & Persistence Contract

## Package A Goal

Create a schema that can represent AI and user revisions correctly without fake provenance and without mutating Sprint 4.5/4.6/4.7 history.

## A1. Create V014 migration

Create:

```text
services/api/src/main/resources/db/migration/platform/
V014__create_user_correctable_parse_revision_lifecycle.sql
```

Do not edit V010, V012, or V013.

Migration history must remain append-only.

---

## A2. Extend `problem_parses`

Recommended new columns:

```sql
parent_parse_id UUID NULL,
correction_idempotency_key VARCHAR(128) NULL,
correction_request_hash CHAR(64) NULL,
correction_reason VARCHAR(32) NULL,
corrected_fields_jsonb JSONB NULL
```

Optional but recommended:

```sql
correction_schema_version VARCHAR(64) NULL
```

Purpose:

- `parent_parse_id`: immutable revision lineage.
- `correction_idempotency_key`: retry-safe mutation.
- `correction_request_hash`: detect key reuse with different payload.
- `correction_reason`: low-cardinality product signal.
- `corrected_fields_jsonb`: which semantic fields changed.
- `correction_schema_version`: future request contract evolution.

---

## A3. Make AI-only fields nullable where required

Current V010 is AI-centric.

USER revisions should not require fake values.

Fields that may need nullable semantics for USER revisions include:

```text
parse_job_id
raw_output_jsonb
provider
model
route_policy_version
prompt_id
prompt_version
provider_request_id
provider_response_id
fallback_used
input_tokens
output_tokens
image_units
request_units
provider_latency_ms
total_latency_ms
estimated_cost_micros
currency
pricing_version
raw_output_retention_until
```

Do not simply relax everything without replacement constraints.

Instead, create source-aware database checks.

---

## A4. Source-aware constraints

### AI constraint

If:

```text
source = AI
```

then:

- `parse_job_id IS NOT NULL`
- `raw_output_jsonb IS NOT NULL`
- provider/model/prompt provenance required
- correction fields must be null
- parent must normally be null for the existing parser lifecycle

### USER constraint

If:

```text
source = USER
```

then:

- `parent_parse_id IS NOT NULL`
- `parse_job_id IS NULL`
- raw provider output must be null
- provider/model/prompt fields must be null
- AI usage/cost fields must be null
- correction idempotency fields required
- normalized problem JSON required

---

## A5. Parent parse ownership integrity

A child revision cannot reference a parse in another session.

Recommended design:

Create or reuse a unique key that allows a composite FK on:

```text
(id, user_id, problem_session_id)
```

Then:

```text
(parent_parse_id, user_id, problem_session_id)
```

references the same tuple.

This guarantees:

- same user,
- same session,
- valid parse.

---

## A6. Revision uniqueness

Keep:

```text
UNIQUE(problem_session_id, revision)
```

Revision remains session-scoped.

Revision allocation must be transaction-safe in application logic.

---

## A7. Correction idempotency uniqueness

Recommended:

```text
UNIQUE(user_id, problem_session_id, correction_idempotency_key)
WHERE correction_idempotency_key IS NOT NULL
```

This allows the same external key format in unrelated users/sessions.

---

## A8. Corrected fields JSON validation

If `corrected_fields_jsonb` is used:

```sql
jsonb_typeof(corrected_fields_jsonb) = 'object'
```

or array, depending on selected contract.

Recommended structure:

```json
{
  "fields": [
    "expressions[0].normalizedText",
    "variables"
  ]
}
```

Do not store a full duplicate raw payload here.

---

## A9. Add selected parse foreign key

`problem_sessions.current_parse_id` already exists in the entity model.

Sprint 4.8 should formalize it with a DB-level relationship if not already constrained.

Selected parse must belong to:

- same session,
- same owner.

Composite FK is preferred.

---

## A10. Backfill existing sessions

Existing Phase 4 data may have:

```text
current_parse_id = NULL
```

Backfill strategy:

For each session with parses:

```text
select the latest accepted existing parse revision
```

and populate:

```text
problem_sessions.current_parse_id
```

The migration must be deterministic.

Do not select unsupported/failed lifecycle rows as current without a clear rule.

---

## A11. Update `ProblemParseJpaEntity`

Replace the AI-only constructor pattern with explicit factories.

Recommended:

```java
ProblemParseJpaEntity.fromAi(...)
```

and:

```java
ProblemParseJpaEntity.fromUserCorrection(...)
```

This makes illegal states difficult to construct.

---

## A12. Update repository methods

Recommended queries:

```text
findByIdAndUserIdAndProblemSessionId(...)
findByProblemSessionIdAndRevision(...)
findFirstByProblemSessionIdOrderByRevisionDesc(...)
findAllByProblemSessionIdAndUserIdOrderByRevisionDesc(...)
findByCorrectionIdempotencyKey(...)
maxRevisionForSession(...)
```

Add pessimistic lock methods where needed.

---

## A13. Package A Tests

### Migration tests

Test:

- fresh DB migration through V014
- Hibernate schema validation
- AI row accepted
- USER row accepted
- USER without parent rejected
- USER with AI provenance rejected
- AI without provider metadata rejected
- cross-session parent rejected
- cross-user parent rejected
- duplicate session revision rejected
- duplicate correction idempotency key rejected
- selected parse FK enforced
- deletion cascade works
- backfill works

### Package A Exit Gate

Must pass:

```text
mvn clean compile
ApplicationContextTest
FlywayMigrationTest
```

No API or iOS work should begin before Package A is stable.

---

# 7. Package B — Correction Domain Engine

## Package B Goal

Create deterministic business rules for user corrections independently from HTTP and Swift.

---

## B1. Correction command model

Create:

```text
ProblemParseCorrectionCommand
```

Recommended fields:

```text
userId
problemSessionId
baseParseId
baseRevision
idempotencyKey
correctionReason
correctedProblem
```

---

## B2. Correction request hash

Create a deterministic SHA-256 over a canonical serialization of:

```text
sessionId
baseParseId
baseRevision
correctionReason
correction schema version
corrected normalized document
```

Do not hash nondeterministic JSON ordering.

Use canonical JSON serialization.

---

## B3. Correction reason enum

Recommended v1 values:

```text
OCR_TEXT_ERROR
MATH_EXPRESSION_ERROR
VARIABLE_ERROR
CONSTRAINT_ERROR
ASSUMPTION_ERROR
TASK_TYPE_ERROR
PROBLEM_TYPE_ERROR
OTHER
```

Reason may be optional.

Reason is product metadata, not semantic authority.

---

## B4. Supported editable fields

Initial production scope should include only safe user-editable semantic fields.

Recommended:

- problem type
- task type
- primary expression text
- normalized expression representation
- display representation
- variable list
- explicit constraints
- explicit assumptions

Do not expose internal verifier AST.

---

## B5. Server-owned fields

Client must not control:

```text
revision
source
supportStatus
reviewRequired
schemaVersion
recognitionEvidenceId
recognitionEvidenceRevision
provider
model
promptId
promptVersion
routePolicyVersion
AI usage
AI cost
classification
difficulty
confidence
canonical AST
verifier input
```

---

## B6. Shared parse validation

Do not duplicate Sprint 4.5 semantic validation.

Refactor only if necessary so that:

```text
AI normalized parse
and
USER corrected parse
```

pass through the same trusted semantic validation layer.

Potential extracted component:

```text
ProblemParseDocumentValidator
```

Responsibilities:

- schema validation
- enum validation
- relation semantics
- variable declaration rules
- constraint rules
- explicit assumption rules
- task/problem pair validation
- supported scope validation
- size limits

Avoid generic `Utils`.

---

## B7. Support status computation

Client must not send authoritative:

```text
SUPPORTED
REVIEW_REQUIRED
UNSUPPORTED
```

Backend computes it after validation.

---

## B8. Revision allocation policy

Within a short transaction:

```text
lock session
read current selected parse
validate base
read max revision
allocate max + 1
insert USER revision
update current_parse_id
commit
```

No long-running work should occur inside this transaction.

No AI calls are needed.

---

## B9. Selection policy

Create:

```text
ProblemParseSelectionPolicy
```

Rules:

1. First accepted AI parse may become selected if no selected parse exists.
2. New USER correction becomes selected.
3. Later AI retry must not replace an already selected USER parse automatically.
4. Explicit future restore behavior is out of scope.
5. Selection must be owner/session scoped.

---

## B10. Diff calculation

Create a small deterministic diff component.

Purpose:

- identify which logical fields changed
- produce privacy-safe metadata
- support UX summary
- support correction-rate analysis

Examples:

```text
EXPRESSION
VARIABLES
CONSTRAINTS
ASSUMPTIONS
TASK_TYPE
PROBLEM_TYPE
```

Do not store full old/new raw student content in metrics.

---

## B11. Package B Unit Tests

Test:

- valid equation correction
- valid inequality correction
- valid arithmetic correction
- valid algebra correction
- invalid problem/task pair
- missing variable
- undeclared variable
- invalid relation
- invalid constraint
- false explicit assumption
- unsupported structure
- oversized expression
- malformed document
- deterministic request hash
- diff categories
- selection policy
- revision allocator behavior

### Package B Exit Gate

All deterministic domain tests green.

---

# 8. Package C — Correction Application Service

## Package C Goal

Implement the production transaction boundary, authorization, idempotency, and history lifecycle.

---

## C1. Create application service

Recommended:

```text
ProblemParseCorrectionApplicationService
```

Public operations:

```text
getParseReview(...)
createCorrection(...)
getRevisionHistory(...)
```

---

## C2. Parse review retrieval

`getParseReview(userId, sessionId)`:

1. Validate active account.
2. Validate entitlement/capability if required.
3. Load session with owner check.
4. Resolve `current_parse_id`.
5. Load exact selected parse.
6. Return safe review model.
7. Return revision count.
8. Return whether correction is currently allowed.

---

## C3. Create correction flow

Expected flow:

```text
authenticate
↓
authorize session
↓
check idempotency replay
↓
lock session
↓
load current selected parse
↓
validate baseParseId/baseRevision
↓
validate corrected document
↓
calculate semantic diff
↓
allocate revision
↓
persist USER revision
↓
update current_parse_id
↓
commit
↓
return revision
```

---

## C4. Stale base conflict

Request must include:

```text
baseParseId
baseRevision
```

If current selected parse no longer matches:

```text
409 PARSE_REVISION_CONFLICT
```

Do not silently merge.

---

## C5. Idempotent replay

Same:

```text
Idempotency-Key
+
same request hash
```

must return the existing created revision.

It must not create a second revision.

---

## C6. Idempotency conflict

Same key with changed payload:

```text
409 PARSE_CORRECTION_IDEMPOTENCY_CONFLICT
```

---

## C7. Concurrency model

Critical scenario:

```text
Current R3
Device A edits R3
Device B edits R3
```

Expected:

```text
one request → R4 created
second request → 409 conflict
```

Not:

```text
R4 and R5 both created from R3
```

unless future branching is explicitly designed.

Sprint 4.8 should remain linear.

---

## C8. History retrieval

Return ordered revisions with safe metadata:

```text
id
revision
source
parentParseId
selected
supportStatus
reviewRequired
correctionReason
correctedFieldCategories
createdAt
```

Do not expose raw provider output.

---

## C9. BOLA protection

For every operation verify:

```text
session.userId == authenticated user
parse.userId == authenticated user
parse.problemSessionId == target session
```

Do not rely on UUID secrecy.

---

## C10. Error contract

Recommended stable API codes:

```text
PROBLEM_PARSE_NOT_FOUND
PARSE_CORRECTION_INVALID
PARSE_CORRECTION_SCHEMA_INVALID
PARSE_CORRECTION_SEMANTIC_INVALID
PARSE_CORRECTION_NOT_ALLOWED
PARSE_CORRECTION_TOO_LARGE
PARSE_REVISION_CONFLICT
PARSE_CORRECTION_IDEMPOTENCY_CONFLICT
```

---

## C11. Metrics

Create:

```text
problem.parse.correction.request.total
problem.parse.correction.success.total
problem.parse.correction.failure.total
problem.parse.correction.conflict.total
problem.parse.correction.idempotent_replay.total
problem.parse.correction.invalid.total
problem.parse.selection.changed.total
```

Labels must remain low-cardinality.

Allowed:

```text
problem_type
outcome
reason
source
```

Forbidden:

```text
userId
sessionId
raw expression
problem text
```

---

## C12. Package C Integration Tests

Test:

- get review
- valid correction
- USER source persisted
- parent correct
- revision increments
- current_parse_id changes
- previous revision unchanged
- invalid correction creates no revision
- stale base returns conflict
- idempotent replay returns same revision
- same key/different payload rejected
- two concurrent edits create only one new revision
- BOLA concealment
- history ordering

### Package C Exit Gate

`ProblemParseCorrectionApplicationServiceTest` green with PostgreSQL/Testcontainers.

---

# 9. Package D — Canonicalization and Classification Authority Integration

## Package D Goal

Prevent downstream systems from accidentally using stale or merely latest parse data.

---

## D1. Canonicalization input policy

`CanonicalProblemApplicationService` must resolve:

```text
session.current_parse_id
```

and load that exact parse.

Do not use:

```text
latest revision query
```

as authority.

---

## D2. Corrected parse invalidates old authority

Example:

```text
Parse R1
  ↓
Canonical C1
  ↓
Classification K1
```

User creates:

```text
Parse R2
```

At that moment:

- C1 stays stored.
- K1 stays stored.
- C1/K1 are history.
- Current selected parse has no authoritative canonical until R2 is canonicalized.

---

## D3. Canonicalization result

After:

```text
canonicalize R2
```

create new canonical problem:

```text
C2
```

linked to:

```text
problem_parse_id = R2
problem_parse_revision = 2
```

---

## D4. Classification behavior

Before classifying C2:

```text
GET classification
```

should behave as not started for the current canonical.

Old K1 must not be returned as current.

Sprint 4.7 current-canonical authority rules should already help here.

---

## D5. AI retry after USER correction

Example:

```text
R1 AI
R2 USER selected
R3 AI retry
```

Canonicalization must still consume R2.

---

## D6. Package D Tests

Test:

- canonicalization uses current selected parse
- latest parse mismatch does not change authority
- old canonical becomes historical
- corrected parse creates new canonical
- classification before reclassification returns NOT_STARTED for new canonical
- new classification attaches to new canonical
- old classification remains immutable history
- AI retry cannot steal selection

### Package D Exit Gate

Canonical and classification regression suites green.

---

# 10. Package E — HTTP API and OpenAPI Contract

## Package E Goal

Expose a safe, stable correction API that is explicit about revisions and concurrency.

---

## E1. GET parse review

Endpoint:

```http
GET /api/v1/problem-sessions/{sessionId}/parse-review
```

Response example:

```json
{
  "problemSessionId": "uuid",
  "currentParse": {
    "problemParseId": "uuid",
    "revision": 2,
    "source": "USER",
    "supportStatus": "SUPPORTED",
    "reviewRequired": false,
    "normalizedProblem": {}
  },
  "revisionCount": 2,
  "canCorrect": true
}
```

---

## E2. POST correction

Endpoint:

```http
POST /api/v1/problem-sessions/{sessionId}/parse-revisions
```

Required header:

```text
Idempotency-Key
```

Request example:

```json
{
  "baseParseId": "uuid",
  "baseRevision": 2,
  "correctionReason": "MATH_EXPRESSION_ERROR",
  "problem": {
    "schemaVersion": "problem-parse-v1"
  }
}
```

Response:

```text
201 Created
```

Example:

```json
{
  "problemSessionId": "uuid",
  "problemParseId": "uuid",
  "revision": 3,
  "source": "USER",
  "parentParseId": "uuid",
  "selected": true,
  "supportStatus": "SUPPORTED",
  "reviewRequired": false,
  "canonicalizationRequired": true,
  "createdAt": "..."
}
```

---

## E3. GET revision history

Endpoint:

```http
GET /api/v1/problem-sessions/{sessionId}/parse-revisions
```

Response should provide revision metadata and selected state.

---

## E4. HTTP status contract

Recommended:

```text
200 review/history
201 correction created
400 malformed request
401 unauthenticated
404 concealed ownership/not found
409 revision conflict
409 idempotency conflict
422 semantic correction invalid
429 mutation rate limit
```

---

## E5. OpenAPI schemas

Add:

```text
ProblemParseReviewResponse
ProblemParseRevisionSummary
ProblemParseRevisionHistoryResponse
CreateProblemParseCorrectionRequest
CreateProblemParseCorrectionResponse
```

Reuse `NormalizedProblemParse` where appropriate.

---

## E6. OpenAPI privacy boundary

Public contract must not expose:

```text
raw_output_jsonb
provider_request_id
provider_response_id
input_tokens
output_tokens
AI cost
pricing version
request hash
idempotency key
system prompt
internal verifier AST
```

---

## E7. Contract tests

Validate:

- OpenAPI parses
- required fields
- nullable semantics
- enum synchronization
- HTTP controller serialization
- no internal field leakage

### Package E Exit Gate

`make contracts-check` green.

---

# 11. Package F — iOS ProblemReview Feature

## Package F Goal

Build the first production-grade learner-facing correction workflow.

---

## F1. Feature hierarchy

Recommended:

```text
apps/ios/VerifiedAI/Features/ProblemReview/

Domain/
  ProblemReviewModels.swift
  ProblemReviewState.swift
  ProblemParseRevision.swift
  ProblemParseCorrection.swift
  ProblemReviewRepositoryProtocol.swift

Data/
  ProblemReviewAPI.swift
  ProblemReviewRepository.swift
  ProblemReviewDTO.swift
  ProblemReviewMapper.swift

Presentation/
  ProblemReviewView.swift
  ProblemReviewViewModel.swift
  ProblemReviewSummaryView.swift
  ProblemExpressionEditor.swift
  ProblemVariableEditor.swift
  ProblemConstraintEditor.swift
  ProblemAssumptionEditor.swift
  ProblemRevisionHistoryView.swift
  ProblemReviewConflictView.swift
```

---

## F2. State machine

Avoid boolean state explosion.

Recommended:

```swift
enum ProblemReviewState {
    case idle
    case loading
    case reviewing(ProblemReview)
    case editing(ProblemReviewDraft)
    case saving(ProblemReviewDraft)
    case saved(ProblemReview)
    case conflict(
        draft: ProblemReviewDraft,
        latest: ProblemReview
    )
    case offlineDraft(ProblemReviewDraft)
    case recoverableError(ProblemReviewError)
    case fatalError(ProblemReviewError)
}
```

---

## F3. Review UX

Show:

- normalized problem
- task
- problem type
- expressions
- variables
- constraints
- assumptions
- review-required warning if applicable
- source badge (`AI` or `Edited`)
- current revision

Do not expose technical internal JSON.

---

## F4. Editing UX

Provide structured editors.

Do not provide a raw JSON editor.

Recommended:

- expression text editor
- problem/task selector
- variable chips/editor
- constraint editor
- explicit assumption editor

---

## F5. Save flow

On save:

1. preserve local draft
2. create stable idempotency key
3. POST correction
4. transition to saving
5. on success update review state
6. clear persisted draft
7. surface revision number
8. continue to canonicalization flow when appropriate

---

## F6. Conflict UX

For:

```text
409 PARSE_REVISION_CONFLICT
```

show recoverable UX:

```text
This problem changed while you were editing.
```

Actions:

```text
Review Latest
Keep My Draft
```

Do not discard local edits.

---

## F7. Offline behavior

Allowed offline:

- show cached review
- edit draft
- save local draft

Not allowed:

- claiming an authoritative new revision exists
- incrementing server revision locally
- canonicalizing locally as authority

---

## F8. Retry semantics

Correction POST must not be blindly replayed.

Retry only using the same stable idempotency key.

---

## F9. Revision history UI

Show:

```text
Revision 3 — Edited by you
Revision 2 — Edited by you
Revision 1 — AI parse
```

Optional metadata:

- created date
- correction category
- selected marker

Do not expose raw AI provider details.

---

## F10. Accessibility

Test:

- VoiceOver labels
- semantic headings
- Dynamic Type
- minimum target sizes
- keyboard/focus behavior
- validation announcement
- conflict announcement
- save progress state

---

## F11. iOS Tests

### ViewModel

Test:

- load success
- load failure
- start edit
- cancel edit
- save success
- save validation error
- 409 conflict
- retry with same idempotency key
- offline draft retention
- reload latest while keeping draft

### DTO

Test:

- AI revision decode
- USER revision decode
- history decode
- nullable parent for AI
- required parent for USER
- error contract decoding

### UI

Test happy path and conflict path.

### Package F Exit Gate

iOS unit/UI tests green.

---

# 12. Package G — Privacy, Security, Lifecycle, and Observability

## Package G Goal

Ensure corrections behave as sensitive student data and participate in account lifecycle guarantees.

---

## G1. Privacy classification

User correction content is:

```text
Sensitive student content
```

It must not enter general analytics/logging.

---

## G2. Logging rules

Allowed:

```text
problemSessionId
revision
source
outcome
errorCode
correlationId
```

Avoid:

```text
raw question
expression text
constraint text
assumption text
full normalized JSON
```

If IDs are considered sensitive in existing logging policy, use only trace/correlation identifiers.

---

## G3. Analytics rules

Allowed low-cardinality signals:

```text
problem_type
changed_field_category
correction_reason
success/failure
revision depth bucket
```

Never send correction content.

---

## G4. Training governance

Default:

```text
user correction
→ training ineligible
```

Future model improvement requires separate governance, lineage, minimization, and consent/legal policy.

---

## G5. Account export

Extend lifecycle export with user revision metadata.

Recommended export:

```text
problemParseId
revision
source
parentParseId
supportStatus
reviewRequired
normalizedProblem
correctionReason
correctedFields
createdAt
```

Exclude:

```text
request hash
idempotency key
internal AI raw output
provider secret metadata
```

---

## G6. Account deletion

Verify account/session deletion cascades through:

```text
USER parse revisions
parent links
canonical problems
classifications
```

No orphan self-FK rows.

---

## G7. Rate limiting

Correction does not call AI, but revision spam must be controlled.

Add a bounded mutation limiter.

Example policy label:

```text
problem.parse.correction
```

Do not treat it as an expensive AI quota unless product policy requires it.

---

## G8. Observability metrics

Add:

```text
correction request count
correction success count
correction conflict count
correction invalid count
idempotent replay count
current parse selection changes
```

Also calculate operational correction rate by problem type where appropriate.

---

## G9. No new AI cost

Sprint 4.8 correction creation must add:

```text
0 AI inference calls
0 verifier calls
```

unless a later explicit action is requested.

---

## G10. Package G Tests

Test:

- export includes USER revision metadata
- export excludes internal correction hash/idempotency
- deletion removes USER revisions
- logs do not contain corrected content
- metrics have bounded labels
- rate limit path stable

### Package G Exit Gate

Privacy lifecycle and security tests green.

---

# 13. Package H — Production Closure

## Package H Goal

Prove Sprint 4.8 as production-ready and synchronize all documentation.

---

## H1. Backend regression

Run:

- correction domain tests
- correction integration tests
- canonicalization tests
- classification regression
- BOLA/controller tests
- Flyway tests
- application context tests

---

## H2. iOS regression

Run:

- ProblemReview tests
- networking tests
- auth regression
- capture regression
- commerce regression as required by project check

---

## H3. Contract checks

Run:

```text
make contracts-check
```

---

## H4. Docs checks

Run:

```text
make docs-check
```

---

## H5. Full API suite

Run:

```text
make test-api
```

---

## H6. Full repo quality gate

Run:

```text
make check
```

Sprint must not be marked COMPLETE before this is green.

---

# 14. Detailed Test Matrix

## Database

- V014 clean migration
- backfill
- source-aware constraints
- parent FK
- selected parse FK
- revision uniqueness
- idempotency uniqueness
- cascade delete

## Domain

- validation
- request hash
- diff
- selection policy
- revision allocation

## Application

- review retrieval
- correction success
- immutable old revision
- conflict
- idempotency
- concurrent edits
- BOLA
- history

## Canonical

- selected parse used
- stale canonical historical
- corrected canonical generated

## Classification

- stale classification not authoritative
- new canonical starts unclassified
- classification from corrected canonical

## Controller

- 401
- concealed 404/BOLA
- 200 review
- 200 history
- 201 correction
- 409 stale revision
- 409 idempotency conflict
- 422 semantic invalid

## iOS

- ViewModel states
- DTO decoding
- save/retry
- conflict
- offline draft
- revision history
- accessibility

## Privacy

- export
- delete
- log minimization
- analytics minimization
- training-ineligible default

---

# 15. Documentation Updates

Create:

```text
SPRINT_4.8_IMPLEMENTATION_MAP.md
SPRINT_4.8_EXECUTION_REPORT.md
```

Update:

```text
00_MASTER_INDEX.md
DOCUMENTATION_MANIFEST.md
domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md
backend/20_BACKEND_MODULE_CONTRACTS.md
data/22_POSTGRESQL_DATA_MODEL.md
data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md
ios/15_IOS_ARCHITECTURE_AND_FILE_HIERARCHY.md
ios/17_IOS_NETWORKING_PERSISTENCE_AND_OFFLINE.md
security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md
roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md
quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md
```

Only after all gates pass:

```text
CAP-PROBLEM-004 → COMPLETE
REQ-PROBLEM-002 → SATISFIED
Sprint 4.8 → COMPLETE
```

---

# 16. Package Execution Order

Use this exact sequence:

```text
Package A
Database + persistence
        ↓
Package B
Correction domain engine
        ↓
Package C
Application service
        ↓
Package D
Canonical/classification authority
        ↓
Package E
API/OpenAPI
        ↓
Package F
iOS ProblemReview
        ↓
Package G
Privacy/security/observability
        ↓
Package H
Production closure
```

Do not start iOS implementation before backend semantics are stable enough to define the API contract.

---

# 17. Package Exit Criteria

## Package A

- V014 applies
- schema validates
- USER provenance representable
- AI provenance remains valid
- current_parse FK/backfill works

## Package B

- deterministic correction validation
- deterministic hash
- revision/selection rules tested

## Package C

- correction transaction safe
- idempotency safe
- concurrency safe
- BOLA safe

## Package D

- canonicalization selected-parse based
- classification current-canonical based

## Package E

- public contract stable
- internal fields hidden
- contract check green

## Package F

- learner can review/edit/save
- conflict recovery works
- drafts survive network/offline failure

## Package G

- export/delete complete
- sensitive content protected
- metrics safe

## Package H

- full repo green
- docs synchronized
- completion report written

---

# 18. Final Definition of Done

Sprint 4.8 is COMPLETE only if all conditions below are true.

## Data integrity

- [ ] Existing parse revisions remain immutable.
- [ ] USER correction always creates a new revision.
- [ ] Parent lineage is persisted.
- [ ] Cross-user lineage is impossible.
- [ ] Cross-session lineage is impossible.
- [ ] Revision numbers remain unique per session.

## Provenance

- [ ] AI revision has real AI provenance.
- [ ] USER revision has user provenance.
- [ ] USER revision does not fabricate provider/model/prompt data.

## Selection authority

- [ ] `current_parse_id` is authoritative.
- [ ] Existing sessions are safely backfilled.
- [ ] USER selection cannot be silently replaced by AI retry.
- [ ] Downstream canonicalization reads selected parse.

## Concurrency

- [ ] stale base edit returns 409
- [ ] concurrent correction produces one winner
- [ ] idempotency retry does not create duplicate revision
- [ ] same key + changed payload is rejected

## Validation

- [ ] USER correction runs trusted schema validation
- [ ] USER correction runs trusted semantic validation
- [ ] invalid correction persists nothing
- [ ] client cannot set server-owned semantic state

## Downstream behavior

- [ ] old canonical remains history
- [ ] corrected parse produces new canonical
- [ ] old classification remains history
- [ ] current corrected canonical starts with correct classification state
- [ ] reclassification attaches to new canonical

## API

- [ ] review endpoint implemented
- [ ] history endpoint implemented
- [ ] correction endpoint implemented
- [ ] OpenAPI synchronized
- [ ] stable error contract
- [ ] no internal provenance/cost leakage

## iOS

- [ ] review screen implemented
- [ ] edit supported fields
- [ ] save correction
- [ ] revision history
- [ ] conflict recovery
- [ ] offline draft behavior
- [ ] accessibility coverage

## Privacy and security

- [ ] BOLA tests green
- [ ] raw correction content excluded from generic logs
- [ ] raw correction content excluded from generic analytics
- [ ] correction training-ineligible by default
- [ ] account export includes appropriate user revision data
- [ ] account deletion removes correction history
- [ ] mutation abuse/rate limiting considered and tested

## Quality gates

- [ ] Maven compile green
- [ ] unit tests green
- [ ] Testcontainers integration tests green
- [ ] FlywayMigrationTest green
- [ ] ApplicationContextTest green
- [ ] iOS tests green
- [ ] contracts-check green
- [ ] docs-check green
- [ ] make test-api green
- [ ] make check green
- [ ] git diff --check green
- [ ] execution report updated
- [ ] capability matrix updated
- [ ] traceability matrix updated

---

# 19. Expected Sprint Outcome

Before Sprint 4.8:

```text
Image
 ↓
Recognition
 ↓
AI Parse
 ↓
Canonical
 ↓
Classification
```

After Sprint 4.8:

```text
Image
 ↓
RecognitionEvidence
 ↓
AI Parse R1
 ↓
User Review
 ↓
USER Correction R2
 ↓
Selected Parse Authority
 ↓
Safe CanonicalProblem
 ↓
ProblemClassification
 ↓
Phase 5 Solve-Ready Input
```

This sprint is the bridge between “AI interpreted the question” and “the platform is confident enough to solve the learner-selected semantic interpretation.”

That distinction is essential for a verification-first learning system.

---

# 20. Recommended First Implementation Step

Begin with **Package A only**.

Do not create the Swift screen first.

The first engineering milestone should be:

```text
V014
+
ProblemParseJpaEntity source-aware model
+
current_parse authority
+
Flyway tests
+
ApplicationContextTest
```

Once Package A is green, proceed to Package B.

