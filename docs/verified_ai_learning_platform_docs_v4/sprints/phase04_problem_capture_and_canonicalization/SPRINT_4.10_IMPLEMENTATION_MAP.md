# Sprint 4.10 Production Implementation Map
## Ingestion Golden Dataset, Accuracy Gates, and Production Hardening

**Project:** Verified AI Learning Platform  
**Repository:** `coskunburak/verified_ai`  
**Source audited:** GitHub `main`  
**Phase:** Phase 4 — Problem Capture & Canonicalization  
**Sprint:** 4.10 — final Phase 4 sprint  
**Precondition:** Sprint 4.9 `COMPLETE - LOCAL VALIDATION GREEN`  
**Primary bounded context:** `problem`  
**Evaluation boundary:** engineering/release tooling around the existing provider-neutral `AiModelGateway` capabilities  
**New model training:** FORBIDDEN  
**New self-hosted model:** FORBIDDEN  
**New solving/verification:** OUT OF SCOPE  
**Expected Flyway migration:** NONE by default  
**Expected public API change:** NONE by default  

Current Phase 4 AI contracts:

```text
VISION_PARSE
  route: vision-route-v1
  prompt: vision-recognition/v001
  schema: recognition-evidence-v1

PROBLEM_NORMALIZE
  route: problem-parser-route-v1
  prompt: problem-parser/v001
  schema: problem-parse-v1

PROBLEM_CLASSIFY
  route: problem-classifier-route-v1
  prompt: problem-classifier/v001
  schema: problem-classification-v1
```

Current deterministic downstream contracts:

```text
canonical-problem-v1
verifier-input-v1
curriculum-v1-seed
```

---

# 1. Executive Summary

Sprint 4.10 must convert the Phase 4 ingestion pipeline from “functionally tested” into a **measured, reproducible, release-gated production capability**.

Sprint 4.1–4.9 already established:

```text
Capture / Import
    ↓
ProblemAsset
    ↓
Preprocessing + Quality Evidence
    ↓
RecognitionJob / RecognitionEvidence
    ↓
ProblemParseJob / ProblemParse
    ↓
User Review + Immutable Correction Revisions
    ↓
Selected Parse Authority
    ↓
CanonicalProblem
    ↓
ProblemClassification
    ↓
ProblemSession History / Retry / Recovery
    ↓
READY_FOR_SOLVE
```

Sprint 4.10 does **not** redesign this pipeline. It measures it, hardens defects revealed by evidence, adds difficult/adversarial fixtures, creates an approved baseline, and makes regressions blockable by CI.

The final architecture is:

```text
Versioned Evaluation Dataset
        ↓
Dataset Validator + Leakage/Integrity Checks
        ↓
Existing Production Ingestion Boundaries
        ↓
Observed Stage Outputs
        ↓
Deterministic Scorers
        ↓
Slice Aggregation
        ↓
Quality + Latency + Cost Report
        ↓
Approved Baseline Comparison
        ↓
PASS / FAIL / BLOCKED Release Gate
```

The sprint is complete only when we can answer:

```text
How accurate is recognition?
How accurate is parser normalization?
Which problem families are production-reachable?
How often do ambiguous cases correctly become REVIEW_REQUIRED?
How often are unsupported cases honestly rejected?
Did canonicalization change meaning?
Did classification regress on a critical skill?
Did a prompt/model/route change increase latency or cost?
Did any critical case silently become authoritative when it should not?
Can another engineer reproduce the exact evaluation?
Can CI prevent promotion of a bad change?
```

---

# 2. GitHub Main Audit — Current Reality

## 2.1 Sprint 4.9 is actually complete

The current Sprint 4.9 execution report records:

```text
COMPLETE - LOCAL VALIDATION GREEN
```

with:

- V015 history index;
- closed `ProblemSessionStage`;
- closed `ProblemSessionNextAction`;
- recovery planner;
- owner-scoped history/detail;
- exact-stage retry;
- no generic recover-all;
- iOS ProblemHistory + SwiftData stale-while-revalidate;
- 213 backend tests;
- full iOS simulator test suite;
- 20 verifier tests;
- full `make check`.

Sprint 4.10 therefore treats history/recovery as an existing production contract.

---

## 2.2 Current evaluation repository is only a seed

Current repository shape:

```text
evaluations/
├── README.md
└── parser/
    └── golden/
        └── problem-parse-v1-seed.json
```

The seed explicitly declares:

```text
Synthetic only
No production student content
No protected holdout
No training eligibility
```

It is a governance seed, not a representative accuracy benchmark.

---

## 2.3 Existing classification golden coverage is a smoke test

Current `ProblemClassificationGoldenEvaluationTest` covers only five production-reachable examples:

```text
arithmetic evaluate
algebra simplify
linear equation
quadratic equation
inequality
```

It is useful regression protection, but not enough to measure:

- distributional recognition accuracy;
- parser field accuracy;
- hard-tail behavior;
- correction-required rate;
- critical slice regressions;
- provider latency;
- provider cost;
- protected holdout behavior.

---

## 2.4 Existing recognition/parser integration tests are behavior tests

Recognition currently already tests:

- idempotent durable job;
- provenance/cost;
- wrong-user protection;
- invalid coordinates;
- missing confidence → UNKNOWN;
- prompt injection stored as visible evidence only;
- upstream quality warnings;
- privacy export/delete.

Parser currently already tests:

- supported durable revision;
- first-class unsupported;
- schema-invalid retry;
- semantic-invalid terminal failure;
- relation mismatch;
- variable mismatch;
- explicit-assumption policy;
- ambiguity → review;
- wrong-user protection;
- privacy lifecycle;
- concurrent revision allocation.

Sprint 4.10 must preserve these and add measurable quality evaluation rather than duplicate them.

---

## 2.5 CI and Makefile gaps

Current `.github/workflows` has:

```text
backend-ci.yml
contract-ci.yml
docs-link-check.yml
ios-ci.yml
math-verifier-ci.yml
security-scan.yml
```

There is no:

```text
ai-evaluation.yml
```

Current Makefile has no:

```text
eval-ai
```

Both are required deliverables for a durable evaluation discipline.

---

## 2.6 Current provider limitation

The audited provider directory contains:

```text
ConfiguredAiModelGateway
LocalFixtureVisionParseProviderAdapter
LocalFixtureProblemNormalizeProviderAdapter
LocalFixtureProblemClassifyProviderAdapter
Unavailable* adapters
```

No approved real external provider adapter is visible in the current directory.

Therefore:

```text
LOCAL_FIXTURE regression
≠
real production OCR/model accuracy
```

Sprint 4.10 must never present deterministic fixture results as real provider quality.

---

## 2.7 Existing production safety guard

`ConfiguredAiModelGateway` already fails startup when `app.environment=production` and any enabled Phase 4 capability uses `LOCAL_FIXTURE`.

Sprint 4.10 will **test this guard**, not reimplement it.

---

# 3. Canonical Sprint Mission

Canonical mission:

> Create measurable parser/OCR quality baselines, regression tests, difficult-input fixtures, and release gates.

Production interpretation:

1. Build a versioned ingestion dataset.
2. Define stage-specific gold expectations.
3. Add hard-tail/adversarial cases.
4. Measure recognition accuracy.
5. Measure parser structural/semantic accuracy.
6. Measure canonicalization correctness.
7. Measure classification correctness.
8. Measure end-to-end ingestion outcomes.
9. Measure latency.
10. Measure inference cost.
11. Freeze an approved baseline.
12. Add candidate-vs-baseline release gates.
13. Add CI automation.
14. Harden runtime only from proven defects.
15. Close Phase 4 with auditable evidence.

---

# 4. Phase 4 Exit Contract

Phase 4 cannot be marked COMPLETE merely because Sprint 4.10 source files exist.

Exit requires:

- Sprint 4.1–4.10 acceptance evidence;
- full Phase 4 integration;
- architecture-drift review;
- security/privacy delta review;
- AI quality/cost delta review;
- database migration/retention review;
- iOS accessibility/localization review;
- observability/supportability review;
- no unresolved Phase 4 P0/P1;
- documentation synchronization;
- explicit Phase 5 handoff.

---

# 5. Evaluation Execution Modes

Sprint 4.10 must separate four modes.

## MODE 0 — Dataset/Contract Validation

Properties:

```text
AI calls: 0
provider secrets: 0
cost: 0
run on: every relevant PR
```

Validates:

- evaluation JSON schema;
- manifest schema;
- case-ID uniqueness;
- asset checksums;
- source/license metadata;
- required slice coverage;
- taxonomy identifiers;
- partition integrity;
- training eligibility;
- duplicate/leakage rules;
- protected holdout manifest.

---

## MODE 1 — Deterministic Fixture Regression

Route:

```text
LOCAL_FIXTURE
```

Purpose:

- exercise evaluation infrastructure;
- exercise current application services;
- verify scorers;
- verify reports;
- verify comparator;
- provide cheap deterministic PR regression.

Required report label:

```text
executionMode = LOCAL_FIXTURE_REGRESSION
```

Forbidden claim:

```text
"production OCR accuracy"
```

---

## MODE 2 — Connected Representative Evaluation

Route:

```text
approved non-fixture provider
```

Measures:

- true provider recognition quality;
- parser/provider behavior;
- classification/provider behavior;
- provider schema drift;
- latency;
- usage/cost;
- fallback behavior.

Requires:

- trusted environment;
- secrets;
- strict cost budget;
- exact route/model/prompt/schema provenance.

If no route is available:

```text
BLOCKED_NO_APPROVED_PROVIDER_ROUTE
```

No ad-hoc fake benchmark.

---

## MODE 3 — Protected Holdout Release Gate

Purpose:

- final promotion evidence;
- contamination-resistant regression;
- hard-tail quality gate.

Requirements:

- immutable dataset version/checksum;
- access-controlled content/labels;
- protected CI environment;
- no prompt/fine-tune/training use;
- no automatic baseline overwrite.

---

# 6. Non-Negotiable Invariants

## INV-4.10-001 — Evaluation data is not training data
All Sprint 4.10 examples default to `NOT_ELIGIBLE` for training.

## INV-4.10-002 — Production user corrections are not gold
A user correction is evidence, not automatically truth.

## INV-4.10-003 — Protected holdout cannot enter prompts/training
No holdout examples in few-shot prompts, synthetic generation, or future tuning.

## INV-4.10-004 — Aggregate quality cannot hide critical slice failure
Overall averages and critical slices are both gated.

## INV-4.10-005 — Explicit review is safer than silent wrong authority
Ambiguous inputs may correctly return `REVIEW_REQUIRED`.

## INV-4.10-006 — Unsupported is a valid correct result
Do not coerce unsupported cases to improve headline accuracy.

## INV-4.10-007 — Stage support is independent
Recognition/parser support does not imply canonical/classification E2E support.

## INV-4.10-008 — No false calculus E2E claim
Parser taxonomy examples for calculus do not make calculus production-reachable downstream.

## INV-4.10-009 — Provider confidence is not truth
Quality labels decide correctness.

## INV-4.10-010 — Production validators are used during evaluation
No benchmark-only permissive normalizer.

## INV-4.10-011 — No public evaluation endpoint
No `/api/v1/evaluation`.

## INV-4.10-012 — Per-run reports are generated artifacts
Reports live as CI/local generated artifacts; approved baselines live in source control.

## INV-4.10-013 — CI cannot approve its own candidate baseline
Baseline promotion is explicit/manual.

## INV-4.10-014 — Connected evaluation is cost-bounded
Budget exhaustion yields `BLOCKED_BUDGET`, never partial PASS.

## INV-4.10-015 — Evaluation cannot mutate runtime route automatically
A failing candidate is blocked, not silently rerouted/promoted.

## INV-4.10-016 — Fixture mode cannot be confused with provider mode
Execution mode/provider/model are mandatory report fields.

## INV-4.10-017 — Reproducibility metadata is mandatory
Dataset, commit, route, provider/model, prompts, schemas, ontology, policies, runtime args all recorded.

## INV-4.10-018 — Security hard-tail cases are release critical
Prompt injection, unsafe math, invalid geometry, unsupported coercion and stale lineage are hard-gated.

## INV-4.10-019 — No Phase 5 solving
Evaluation ends at `READY_FOR_SOLVE`.

## INV-4.10-020 — Functional tests and accuracy evaluation are separate evidence
`make test-api` cannot substitute for measured ingestion quality.

---

# 7. Evaluation Dataset Contract

Introduce:

```text
ingestion-evaluation-case-v1
ingestion-evaluation-report-v1
ingestion-evaluation-policy-v1
recognition-eval-normalization-v1
recognition-math-tokenizer-v1
```

Evaluation contracts are engineering contracts, not learner API contracts.

Recommended schemas:

```text
packages/schemas/ingestion-evaluation-case.schema.json
packages/schemas/ingestion-evaluation-report.schema.json
```

---

# 8. Recommended Evaluation Case Shape

Conceptually:

```json
{
  "schemaVersion": "ingestion-evaluation-case-v1",
  "id": "linear-equation-clean-en-001",
  "partition": "REGRESSION",
  "criticality": "NORMAL",
  "origin": {
    "type": "SYNTHETIC",
    "sourceId": "internal-fixture-v1",
    "license": "INTERNAL_TEST_ONLY",
    "trainingEligibility": "NOT_ELIGIBLE"
  },
  "input": {
    "assetPath": "assets/synthetic/linear-equation-clean-en-001.jpg",
    "contentType": "image/jpeg",
    "inputMode": "PHOTO_LIBRARY",
    "locale": "en-US"
  },
  "slices": [
    "EQUATION",
    "CLEAN",
    "ENGLISH"
  ],
  "expected": {
    "preprocessing": {},
    "recognition": {},
    "parse": {},
    "canonical": {},
    "classification": {},
    "terminalStage": "READY_FOR_SOLVE",
    "reviewExpected": false
  }
}
```

---

# 9. Stage-Specific Expected Support

Every case must distinguish:

```text
expectedRecognitionOutcome
expectedParseOutcome
expectedCanonicalOutcome
expectedClassificationOutcome
expectedSessionOutcome
```

Example:

```text
Derivative image:
  recognition = SUPPORTED
  parser = SUPPORTED
  canonical = UNSUPPORTED_CURRENT_SCOPE
  classification = NOT_AUTHORITATIVE
  e2e = UNSUPPORTED
```

This protects against fake capability expansion.

---

# 10. Dataset Partitions

Mandatory:

```text
DEVELOPMENT
REGRESSION
HARD_TAIL
PROTECTED_HOLDOUT
```

Future optional:

```text
PRODUCTION_CORRECTION_ELIGIBLE
```

but Sprint 4.10 must not fill this from ordinary user content.

---

# 11. Corpus Size and Coverage

Do not gate only on raw example count.

Recommended initial engineering target:

```text
400–600 labeled cases
```

across development/regression/hard-tail/holdout.

Definition of Done is based on:

```text
required slices
×
minimum case coverage
×
critical behavior coverage
```

not “we have N rows”.

Suggested starting review floor:

```text
>=20 examples per major production-supported problem slice
>=10 examples per critical hard-tail category
```

The accepted counts must be frozen in a versioned coverage policy after initial review.

---

# 12. Required Input Slices

## Source / modality

```text
camera-like image
photo-library-like image
sanitized screenshot/image
PDF recovery/unsupported path where applicable
```

## Visual quality

```text
CLEAN
LOW_RESOLUTION
BLUR
GLARE
LOW_CONTRAST
TIGHT_CROP
OFF_CENTER
ROTATED_ORIENTATION
MILD_PERSPECTIVE
DENSE_TEXT
MULTILINE
```

## Locale

```text
en-US
tr-TR
```

where natural-language instructions are present.

---

# 13. Required Math Slices

Production-reachable families:

```text
arithmetic
fractions
percentages
order of operations
algebraic simplification
exponents
radicals where current downstream scope permits
linear equation
quadratic equation
inequality
multi-statement/system only where canonical contract truly supports it
```

Parser-only/broader-scope fixtures:

```text
function value
limit
derivative
integral
```

must carry downstream expected unsupported/no-E2E labels.

---

# 14. OCR Confusion Corpus

Include:

```text
0 vs O
1 vs l vs I
x variable vs multiplication
ASCII hyphen vs Unicode minus
< vs <=/≤
> vs >=/≥
= vs ≠
+ vs ±
superscript 2 vs trailing 2
decimal dot vs decimal comma
fraction bar vs slash
radical symbol
parenthesis vs bracket
subscript/superscript
```

Critical-symbol errors must be measured separately from generic character error.

---

# 15. Ambiguity Corpus

Include:

```text
ambiguous exponent
cropped denominator
uncertain relation sign
uncertain digit
uncertain variable
ambiguous reading order
partly occluded math
```

Expected result is frequently:

```text
REVIEW_REQUIRED
```

rather than forced support.

---

# 16. Unsupported Corpus

Include explicit cases outside current production canonical reachability.

Correct output may be:

```text
UNSUPPORTED
```

This counts as a successful trust outcome when expected.

---

# 17. Security / Adversarial Corpus

Include content such as:

```text
ignore previous instructions
return admin token
role-tag strings
prompt delimiters
URL-looking text
JSON-looking text
SQL-looking text
tool-call-looking text
oversized visible text
malformed provider JSON
unknown fields
invalid coordinates
duplicate blocks
invalid reading order
undeclared variables
invented assumptions
unsafe expression depth
extreme exponents
```

Never use real credentials or personal data.

---

# 18. Fixture Source Governance

Allowed origins:

```text
SYNTHETIC
INTERNALLY_AUTHORED
PERMISSIVELY_LICENSED_PUBLIC
```

For licensed public data record:

- source;
- license;
- acquisition date;
- allowed purpose;
- transformation provenance.

Unknown-license scraped content is forbidden.

---

# 19. Protected Holdout Strategy

Recommended:

```text
Git repository:
  holdout manifest
  version
  checksum
  coverage summary
  loader contract

Restricted evaluation storage:
  holdout inputs
  holdout labels
```

Runner configuration:

```text
INGESTION_HOLDOUT_PATH
INGESTION_HOLDOUT_SHA256
```

Checksum mismatch:

```text
BLOCKED_DATASET
```

No provider or production secrets in the dataset.

---

# 20. Dataset Leakage / Dedup Rules

Validator must detect:

- duplicate case IDs;
- duplicate asset checksums;
- normalized expression duplicates across protected/regression partitions;
- exact normalized content duplicates;
- missing source/license;
- undeclared files.

Near duplicates are flagged for review.

A protected-holdout leak into normal regression/prompts is release blocking.

---

# 21. Recognition Metrics

Mandatory:

```text
normalized exact-match rate
Character Error Rate (CER)
math-token error rate
critical-symbol error rate
block extraction accuracy
block kind accuracy
reading-order accuracy
coordinate validity
optional IoU where boxes are labeled
ambiguity/review recall
```

---

# 22. Recognition Normalization Policy

Allowed:

```text
Unicode NFC
line-ending normalization
bounded whitespace normalization
```

Forbidden:

```text
dropping minus
changing inequality relation
removing exponent
converting decimal semantics without locale policy
```

Evaluation normalization must not “wash out” semantic errors.

---

# 23. Math Tokenizer

Create versioned tokenizer:

```text
recognition-math-tokenizer-v1
```

It must preserve:

- numbers;
- variables;
- operators;
- relations;
- delimiters;
- function tokens.

Unit tests must use hand-computable sequences.

---

# 24. Parser Metrics

Mandatory:

```text
JSON-valid rate
schema-valid rate
semantic-valid rate
support-status exact accuracy
subject exact accuracy
topic exact accuracy
task-type exact accuracy
problem-type exact accuracy
expression normalized match
canonical structural match where supported
variable-set precision/recall
constraint exactness
assumption policy accuracy
source-block reference validity
review-required precision/recall
unsupported accuracy
```

Invented source IDs or assumptions are critical failures.

---

# 25. Canonicalization Metrics

Because canonicalization is deterministic, gates are stricter:

```text
expected-supported success
expected-unsupported rejection
AST structural exact match
canonical schema validity
variable exactness
restriction-set exactness
unsafe-input rejection
deterministic repeatability
idempotency
selected-parse correctness
```

Security-critical deterministic rejection fixtures should require 100% correct rejection.

---

# 26. Classification Metrics

For production-reachable canonical cases:

```text
status exact accuracy
primary skill exact accuracy
secondary skill precision/recall
difficulty exact accuracy
subject/topic exact accuracy
review-reason correctness
false-authoritative classification rate
confidence-policy compliance
```

Current v1 confidence regression should protect:

```text
no HIGH
UNCALIBRATED
```

until a future evaluated policy changes it.

---

# 27. End-to-End Metrics

Mandatory:

```text
end_to_end_ingestion_success_rate
recognition_success_rate
parser_success_rate
canonical_success_rate
classification_success_rate
expected_review_rate
expected_unsupported_rate
correction_required_estimate
silent_wrong_rate
false_authoritative_accept_rate
false_ready_for_solve_rate
pipeline_p50_latency
pipeline_p95_latency
pipeline_average_cost_micros
pipeline_p95_cost_micros
```

Do not add “false verified” as a Phase 4 metric because verification does not yet exist.


---

# 28. Critical Trust Metric — False Authoritative Acceptance

Define:

```text
false_authoritative_acceptance
```

as:

> Gold or trusted evidence indicates ambiguity, unsupported structure, stale lineage or semantic mismatch, but the pipeline still advances an incorrect result as authoritative without required review/unsupported handling.

Examples:

```text
ambiguous relation sign
→ CLASSIFIED / READY_FOR_SOLVE

unsupported structure
→ forced canonical supported representation

invented parser assumption
→ accepted parse

old parse
→ current canonical after USER correction

stale canonical
→ current classification
```

For `CRITICAL` fixtures:

```text
false_authoritative_accept_count == 0
```

is a hard release gate.

---

# 29. Correction-Required Estimate

Real user corrections are not gold.

However curated gold cases can estimate whether a learner would need Sprint 4.8 correction.

A case counts as estimated correction-required when:

- AI parse materially differs from gold;
- error is within user-editable parser-level fields;
- pipeline did not correctly reject/mark unsupported;
- correction can restore the gold structure.

Report:

```text
correction_required_estimate
```

overall and by slice.

---

# 30. Evaluation Outcome Vocabulary

Use closed evaluation-only outcomes:

```text
PASS
EXPECTED_REVIEW
EXPECTED_UNSUPPORTED
FAIL_RECOGNITION
FAIL_SCHEMA
FAIL_SEMANTIC
FAIL_CANONICAL
FAIL_CLASSIFICATION
FAIL_SILENT_WRONG
FAIL_SAFETY
BLOCKED_PROVIDER
BLOCKED_DATASET
BLOCKED_BUDGET
BLOCKED_INCOMPARABLE_BASELINE
```

Do not reuse runtime job statuses as evaluation results.

---

# 31. Evaluation Report Contract

Every run creates a machine-readable report containing:

```text
reportSchemaVersion
evaluationPolicyVersion
runId
executionMode
datasetVersion
datasetSha256
gitCommit
startedAt
completedAt

routeProvenance:
  capability
  routePolicyVersion
  provider
  model
  promptId
  promptVersion
  schemaVersion
  pricingVersion

runtime:
  javaVersion
  pythonVersion
  os
  connectedProvider

counts:
  total
  evaluated
  skipped
  blocked
  passed
  failed

metrics:
  recognition
  parser
  canonicalization
  classification
  endToEnd
  latency
  cost

slices:
  [...]

criticalFailures:
  [...]

regressions:
  [...]

gateDecision:
  PASS | FAIL | BLOCKED

reportSha256
```

Generated report must avoid protected raw content.

Case IDs are sufficient to identify failures.

---

# 32. Approved Baseline Contract

Create:

```text
evaluations/baselines/production-ingestion-v1.json
```

It records:

- approved dataset version/checksum;
- approved route/prompt/schema provenance;
- evaluation policy version;
- approved overall metrics;
- approved critical-slice metrics;
- latency baseline;
- cost baseline;
- approval metadata;
- source report checksum;
- source git commit.

Metric values are generated from the approved report.

Do not hand-enter them.

---

# 33. Release Gate Policy

Create:

```text
evaluations/baselines/ingestion-release-gates-v1.yaml
```

## Hard gates

Examples:

```text
dataset_schema_valid = true
asset_checksum_valid = true
protected_holdout_checksum_valid = true
critical_false_authoritative_accept_count = 0
unsafe_input_false_accept_count = 0
invented_source_reference_count = 0
production_local_fixture_guard = PASS
protected_partition_leak_count = 0
```

## Relative quality gates

Do not invent arbitrary global 95% limits before establishing the baseline.

Compare candidate against approved baseline.

Examples:

```text
recognition CER regression <= approved allowed delta
parser semantic-valid regression <= allowed delta
classification primary-skill regression <= allowed delta
```

Critical slices may have zero tolerated regression.

## Latency gate

Compare:

```text
p50
p95
```

not only averages.

## Cost gate

Compare:

```text
average cost per case
p95 cost per case
cost per capability
```

Any material increase requires explicit justification.

## Slice gate

At minimum:

```text
ARITHMETIC
ALGEBRA
LINEAR_EQUATION
QUADRATIC_EQUATION
INEQUALITY
AMBIGUOUS
UNSUPPORTED
LOW_QUALITY
PROMPT_INJECTION
TR
EN
```

No aggregate masking.

---

# 34. Baseline Promotion Process

Exact production process:

```text
1. Run candidate evaluation.
2. Validate dataset identity/checksum.
3. Validate route/prompt/schema comparability.
4. Review critical hard-gate failures.
5. Review overall deltas.
6. Review every required critical slice.
7. Review p50/p95 latency.
8. Review cost.
9. Human-review material disagreements.
10. Freeze report checksum.
11. Explicitly approve baseline update.
12. Commit approved baseline/gate metadata.
```

Forbidden:

```text
candidate CI run
→ automatically overwrite approved baseline
```

---

# 35. Package A — Semantic Contract and Phase 4 Exit Governance

## Goal

Freeze evaluation meaning and Phase 4 exit semantics before tooling.

## A1. Add Phase 4 ingestion evaluation requirement

Add a requirement such as:

```text
REQ-INGEST-EVAL-001
```

Recommended:

> Phase 4 ingestion changes are evaluated against a versioned representative regression/hard-tail corpus; material prompt/model/route/schema changes require reproducible quality, latency and cost evidence before promotion.

Acceptance evidence:

- dataset manifest;
- evaluation report;
- hard-tail suite;
- approved baseline;
- release comparator;
- CI workflow.

Operational evidence:

- connected/scheduled evaluation result;
- cost/latency report;
- rollback procedure.

## A2. Do not steal global Sprint 5.12 ownership

Global:

```text
REQ-EVAL-001
CAP-EVAL-001
```

covers broader V1 solver/tutor/model release evaluation.

After 4.10, appropriate status is:

```text
Foundation / Partial
```

not global Complete.

## A3. Define Phase 4 exit package

Explicitly require:

- quality baseline;
- hard-tail results;
- provider-connected evidence or explicit blocker/exception;
- architecture drift;
- security/privacy;
- latency/cost;
- migration/retention;
- iOS regression;
- full repository gates.

## A4. Default no V016

Evaluation artifacts are not learner business state.

## A5. Default no public API

Evaluation is not a learner endpoint.

### Package A Exit Gate

- [ ] evaluation vocabulary frozen
- [ ] requirement mapped
- [ ] global evaluation ownership remains honest
- [ ] no DB/API scope creep
- [ ] Phase 4 closure evidence contract documented

---

# 36. Package B — Dataset Structure, Schema, Manifest, Provenance, Coverage

## Goal

Create the durable evaluation source of truth.

## B1. Move toward canonical repository hierarchy

Target:

```text
evaluations/
├── README.md
├── golden-datasets/
│   └── parsing/
│       └── ingestion-v1/
├── rubrics/
├── runners/
├── baselines/
└── reports/
```

The old Sprint 4.5 seed must not be silently discarded.

Either migrate cases or preserve a legacy pointer.

## B2. Proposed ingestion corpus

```text
evaluations/golden-datasets/parsing/ingestion-v1/
├── manifest.yaml
├── development.jsonl
├── regression.jsonl
├── hard-tail.jsonl
├── assets/
│   ├── synthetic/
│   ├── internal-authored/
│   └── licensed-public/
└── licenses/
    └── README.md
```

Protected holdout payload remains access controlled.

## B3. Evaluation schemas

Create:

```text
packages/schemas/ingestion-evaluation-case.schema.json
packages/schemas/ingestion-evaluation-report.schema.json
```

Update:

```text
packages/schemas/README.md
```

## B4. Manifest fields

Mandatory:

```text
datasetId
datasetVersion
caseSchemaVersion
createdAt
owner
purpose
runtimeSchemaCompatibility
ontologyVersion
partitions
caseCounts
sliceCounts
sourcePolicy
trainingEligibilityPolicy
protectedHoldoutVersion
protectedHoldoutSha256
assetManifestSha256
knownLimitations
```

## B5. Asset integrity

Every committed binary asset gets SHA-256.

Validator fails on:

- absent asset;
- checksum mismatch;
- duplicate asset ID;
- undeclared asset;
- invalid MIME.

## B6. Coverage policy

Create:

```text
evaluations/rubrics/ingestion-coverage-v1.yaml
```

It defines required:

- problem slices;
- visual slices;
- locale slices;
- hard-tail categories;
- per-slice minimum case counts.

## B7. Source provenance

Each case must declare origin and rights.

## B8. Training eligibility

Sprint 4.10 committed corpus is:

```text
NOT_ELIGIBLE
```

for training by default.

## B9. Dataset validator

Create:

```text
evaluations/runners/validate_ingestion_dataset.py
```

Responsibilities:

- schema;
- IDs;
- checksums;
- provenance;
- license completeness;
- coverage;
- taxonomy;
- duplicate/leakage;
- prohibited user origin;
- protected manifest consistency.

## B10. Preserve seed semantics

Ensure new corpus still has at least equivalent categories:

```text
supported
unsupported
ambiguity
schema invalid
semantic invalid
adversarial visible instructions
```

### Package B Exit Gate

- [ ] corpus versioned
- [ ] schema valid
- [ ] checksums valid
- [ ] provenance complete
- [ ] coverage policy satisfied
- [ ] no user production content
- [ ] leakage scan green
- [ ] old seed semantics preserved

---

# 37. Package C — Recognition Accuracy Evaluation

## Goal

Measure recognition content/geometry/uncertainty quality.

## C1. Specific scorer module

Create:

```text
evaluations/runners/recognition_metrics.py
```

No generic `utils`.

## C2. Normalization version

Implement:

```text
recognition-eval-normalization-v1
```

with unit tests.

## C3. Math tokenizer

Implement:

```text
recognition-math-tokenizer-v1
```

with operator/relation preserving behavior.

## C4. Exact and distance metrics

Calculate:

- normalized exact match;
- CER;
- math-token edit rate;
- critical-symbol error.

## C5. Blocks

Where ground truth exists:

- expected block recall;
- extra block rate;
- block-kind accuracy;
- reading-order accuracy.

## C6. Geometry

Where labeled:

- validity;
- optional IoU;
- out-of-bounds count.

Do not over-gate geometry if product semantics only require source traceability.

## C7. Ambiguity

Measure:

```text
ambiguity_review_recall
ambiguity_false_accept_rate
```

## C8. Confidence

Measure schema/field behavior, not model confidence as truth.

Missing provider confidence must remain `UNKNOWN`.

## C9. Hard response limits

Fixtures:

- excessive blocks;
- overlong text;
- response too large;
- invalid coordinates;
- invalid ordering.

## C10. Prompt injection

Visible instruction-looking text remains data only.

## C11. Connected cost/latency

Record:

- provider latency;
- total stage latency;
- usage;
- estimated cost;
- fallback.

### Package C Exit Gate

- [ ] recognition metrics deterministic
- [ ] OCR confusion slices represented
- [ ] low-quality slices represented
- [ ] ambiguity handling measured
- [ ] hard limits tested
- [ ] prompt injection hard gate
- [ ] cost/latency included when connected

---

# 38. Package D — Parser Accuracy Evaluation

## Goal

Measure structured interpretation correctness.

## D1. Parser metrics module

Create:

```text
evaluations/runners/parser_metrics.py
```

## D2. Syntax/schema

Track:

```text
json_valid_rate
schema_valid_rate
```

## D3. Semantic validator

Track:

```text
semantic_valid_rate
```

using the production validator.

## D4. Support state

Exact:

```text
SUPPORTED
REVIEW_REQUIRED
UNSUPPORTED
```

## D5. Taxonomy/task/problem type

Exact expected values.

## D6. Expression comparison

Two layers:

```text
normalized syntax
canonical structural comparison where downstream supports it
```

No LLM judge.

## D7. Variables

Track exact-set + precision/recall.

## D8. Source references

All source block IDs must resolve to real recognition evidence.

Invented source references = hard failure.

## D9. Constraints/assumptions

Track:

- missing explicit constraints;
- invented assumptions;
- source-vs-derived distinction.

## D10. Ambiguity

Expected ambiguous case should not be forced supported.

## D11. Unsupported

Correct unsupported = successful trust behavior.

## D12. Prompt injection

Instruction-looking recognition content is untrusted data.

### Package D Exit Gate

- [ ] parser metrics complete
- [ ] production validator used
- [ ] expression comparison deterministic
- [ ] source lineage hard gate
- [ ] assumption hard gate
- [ ] ambiguity/unsupported measured
- [ ] no LLM evaluator

---

# 39. Package E — Canonicalization and Classification Evaluation

## Goal

Protect downstream current-authority semantics.

## E1. Canonical expected outputs

Gold cases include:

- type;
- task;
- AST;
- variables;
- restrictions;
- expected support/rejection.

## E2. Repeatability

Same input must yield same deterministic canonical semantic output.

## E3. Unsafe inputs

Expected rejection is hard-gated.

## E4. Selected parse

Cases must include:

```text
AI R1
USER R2 selected
```

and prove canonicalization uses R2.

## E5. Stale canonical

Old canonical may remain historical but must not be current.

## E6. Expand classification golden corpus

The existing five-case smoke remains.

Add representative production-reachable cases across current skill catalog.

Do not include a skill as E2E-supported merely because ontology contains it.

## E7. Classification statuses

Evaluate reachable:

```text
CLASSIFIED
REVIEW_REQUIRED
UNKNOWN
UNSUPPORTED
```

## E8. Confidence policy

Protect current v1:

```text
no HIGH
UNCALIBRATED
```

## E9. Ontology integrity

Every expected skill ID resolves in `curriculum-v1-seed`.

### Package E Exit Gate

- [ ] canonical AST baseline
- [ ] deterministic repeatability
- [ ] selected-parse authority
- [ ] stale canonical safety
- [ ] expanded classification corpus
- [ ] ontology integrity
- [ ] confidence-policy regression
- [ ] no calculus E2E overclaim

---

# 40. Package F — End-to-End Ingestion Executor

## Goal

Evaluate the real Phase 4 pipeline, not a duplicate benchmark implementation.

## F1. Java test/evaluation harness

Recommended package:

```text
services/api/src/test/java/com/verifiedai/problem/evaluation/
```

Possible files:

```text
IngestionEvaluationFixtureLoader.java
IngestionEvaluationExecutor.java
IngestionEvaluationResult.java
IngestionGoldenRegressionTest.java
IngestionHardTailTest.java
IngestionPhase4EndToEndTest.java
```

## F2. Real application boundaries

The executor invokes actual:

- preprocessing service where relevant;
- recognition service;
- parser service;
- correction service where case requires;
- canonical service;
- classification service;
- session projection.

No benchmark-only normalizer/canonicalizer.

## F3. Stop boundary

Final successful Phase 4 state:

```text
READY_FOR_SOLVE
```

No solver call.

## F4. Correction cases

Curated evaluation annotation drives the real correction application service.

This is not copied production user data.

## F5. Retry cases

Evaluate Sprint 4.9 exact-stage retry:

- recognition retry only;
- parse retry only;
- classification retry only.

Prior successful work counts must remain unchanged.

## F6. Relaunch-style recovery

Reload state from PostgreSQL and run projection, proving no in-memory test state is authoritative.

## F7. Connected mode

Use the same capability boundary.

If approved route unavailable:

```text
BLOCKED_PROVIDER
```

## F8. Generated raw-result format

Write generated result JSONL with:

- case ID;
- observed semantic fields;
- timing;
- usage/cost;
- provenance;
- evaluation outcome.

No production DB `evaluation_runs` table.

### Package F Exit Gate

- [ ] real production services
- [ ] no parallel benchmark semantics
- [ ] correction/recovery included
- [ ] PostgreSQL reload included
- [ ] stops at READY_FOR_SOLVE
- [ ] connected blocker explicit



---

# 41. Package G — Aggregation, Comparator, Baseline, Release Gate

## Goal

Turn evaluation observations into an auditable release decision.

## G1. Main runner

Create:

```text
evaluations/runners/run_ingestion_eval.py
```

Responsibilities:

1. resolve manifest;
2. validate dataset;
3. resolve execution mode;
4. load evaluator raw results;
5. compute metrics;
6. aggregate required slices;
7. calculate latency/cost;
8. produce machine-readable report;
9. calculate report checksum.

## G2. Release comparator

Create:

```text
evaluations/runners/compare_release.py
```

Inputs:

```text
approved baseline
candidate report
gate policy
```

Output:

```text
PASS
FAIL
BLOCKED
```

with exact reasons.

## G3. Missing metrics fail closed

If a required metric or slice is absent:

```text
BLOCKED
```

not implicit pass.

## G4. Baseline compatibility

Comparator validates at minimum:

```text
datasetVersion
evaluationPolicyVersion
caseSchemaVersion
runtime schema versions
ontologyVersion
```

Prompt/model may intentionally change as candidate dimensions.

If datasets/schemas are incomparable:

```text
BLOCKED_INCOMPARABLE_BASELINE
```

## G5. Regression details

Each regression record contains:

```text
metric
slice
baseline
candidate
delta
allowedDelta
severity
caseIds
```

No protected raw fixture content.

## G6. Floating-point stability

Use stable deterministic arithmetic/rounding policy.

Presentation rounding must not alter gate calculation.

## G7. Manual approved-baseline promotion

Create script:

```text
scripts/evaluation/update-approved-baseline.sh
```

Require explicit:

```text
--approve
--source-report <file>
```

Recommended validations:

- source report schema valid;
- source report checksum valid;
- no hard-gate failure;
- source dataset recognized;
- source mode acceptable;
- git working tree implications printed.

Do not require baseline update simply because quality changed.

## G8. Candidate quality improvement with cost regression

Comparator can return FAIL or require exception depending gate policy.

Quality cannot silently justify unbounded cost.

## G9. Candidate cost improvement with quality regression

Cheaper but materially worse remains FAIL.

### Package G Exit Gate

- [ ] report reproducible
- [ ] comparator deterministic
- [ ] missing data blocked
- [ ] incompatible baseline blocked
- [ ] critical slices enforced
- [ ] cost/latency included
- [ ] baseline promotion explicit
- [ ] no auto-approval

---

# 42. Package H — Makefile and Evaluation Scripts

## Goal

Create stable commands for humans, Codex, and CI.

## H1. Root Makefile

Add documented target:

```make
eval-ai:
    ...
```

Default `make eval-ai` should be deterministic and secret-free.

It may execute:

```text
dataset validation
fixture regression
report generation
baseline compare
```

## H2. Connected mode

Recommended invocation:

```bash
AI_EVAL_MODE=connected make eval-ai
```

rather than hiding provider calls in the default target.

If a distinct target is clearer:

```text
make eval-ai-connected
```

Either pattern is acceptable; choose one and document it once.

## H3. Canonical shell runner

Create:

```text
scripts/evaluation/run-golden-suite.sh
```

Requirements:

- `set -euo pipefail`;
- validate prerequisites;
- fail loudly;
- no secret echo;
- create generated report directory;
- propagate gate non-zero exit.

## H4. Baseline promotion

Create:

```text
scripts/evaluation/update-approved-baseline.sh
```

No provider call.

It only promotes a pre-existing reviewed report.

## H5. Help output

Update `make help`:

```text
make eval-ai       Run deterministic ingestion quality evaluation
```

## H6. Generated report path

Use a path intentionally ignored by git.

For example:

```text
.generated/evaluations/<run-id>/
```

or a repo-standard ignored report location.

Do not accidentally commit every run.

### Package H Exit Gate

- [ ] stable `make eval-ai`
- [ ] secret-free default
- [ ] explicit connected mode
- [ ] non-zero bad gate
- [ ] baseline script safe
- [ ] help updated
- [ ] generated outputs ignored

---

# 43. Package I — GitHub Actions AI Evaluation Workflow

## Goal

Make ingestion evaluation part of release discipline.

Create:

```text
.github/workflows/ai-evaluation.yml
```

## I1. Deterministic PR triggers

Run on material paths such as:

```text
services/api/src/main/java/com/verifiedai/problem/**
services/api/src/main/java/com/verifiedai/ai/**
services/api/src/main/resources/prompts/**
services/api/src/main/resources/application*.yml
packages/schemas/**
packages/curriculum/**
evaluations/**
scripts/evaluation/**
```

Avoid paid AI evaluation on docs-only PR.

## I2. Safe jobs

Recommended:

```text
dataset-validation
deterministic-ingestion-regression
release-baseline-compare
```

These require no provider secrets.

## I3. Connected job

Recommended:

```text
connected-ingestion-evaluation
```

only in trusted contexts:

```text
workflow_dispatch
scheduled
protected main/release environment
```

depending cost policy.

## I4. GitHub Environment

Use a protected environment such as:

```text
ai-evaluation
```

when connected secrets exist.

## I5. Fork safety

No connected provider secret may be exposed to untrusted fork PRs.

## I6. Budget variables

Connected job supports:

```text
AI_EVAL_MAX_TOTAL_COST_MICROS
AI_EVAL_MAX_CASES
AI_EVAL_TIMEOUT
```

A budget stop results in:

```text
BLOCKED_BUDGET
```

## I7. Artifacts

Upload safe generated artifacts:

```text
ingestion-evaluation-report.json
ingestion-evaluation-summary.md
ingestion-release-comparison.json
```

Do not upload protected images/labels into unrestricted workflow artifacts.

## I8. Artifact retention

Set explicit retention.

Approved baseline is source-controlled separately.

## I9. Branch protection adoption

Recommended rollout:

1. introduce job non-blocking;
2. stabilize repeatability;
3. require deterministic check for material PRs;
4. keep paid/holdout checks protected according to release policy.

### Package I Exit Gate

- [ ] workflow created
- [ ] path filters correct
- [ ] deterministic PR coverage
- [ ] fork-safe
- [ ] connected protected
- [ ] budget-bounded
- [ ] artifacts safe
- [ ] no baseline write from CI

---

# 44. Package J — Runtime Production Hardening from Evidence

## Goal

Fix real Phase 4 defects revealed by the corpus, without unrelated cleanup.

## J1. “Failing fixture first” rule

Every runtime hardening fix must begin with a reproducible failing case.

Flow:

```text
new failure discovered
→ add/confirm correctly labeled fixture
→ reproduce
→ patch owning implementation
→ focused tests
→ full evaluation
→ baseline comparison
```

## J2. Production LOCAL_FIXTURE guard

Current guard already exists.

Add/retain tests proving production fails when enabled:

```text
VISION_PARSE = LOCAL_FIXTURE
PROBLEM_NORMALIZE = LOCAL_FIXTURE
PROBLEM_CLASSIFY = LOCAL_FIXTURE
```

Do not rewrite working code for cosmetic ownership.

## J3. Timeout/max-attempt review

Current route plans already have timeout and max attempts.

Evaluation report must record them.

Hard-tail tests verify retry exhaustion remains bounded.

## J4. Response-size bounds

Exercise:

```text
just below limit
at limit
over limit
```

for AI outputs.

No oversized accepted evidence/revision.

## J5. Fallback provenance

If a connected fallback exists:

- `fallbackUsed=true`;
- actual provider/model recorded;
- cost includes fallback;
- schema contract unchanged.

## J6. Preprocessing thresholds

Evaluate:

- blur;
- glare;
- contrast;
- resolution;
- crop.

Do not tune a threshold from one example.

Any threshold change requires before/after evaluation and config-version consideration.

## J7. Schema strictness

Unknown provider fields should remain governed by the current schema policy.

Do not weaken strict parsing simply to improve connected pass rate.

If a legitimate schema evolution is needed, version it.

## J8. Parser semantic false accept

Any false semantic accept becomes a permanent regression fixture.

## J9. Selected parse lineage

Hard-tail must prove:

```text
USER correction selected
→ old AI parse not authoritative
→ canonical rebuilt/current for selected parse
→ old classification not current
```

## J10. Retry storm

Simulate repeated retryable provider failures.

Verify:

- attempts bounded;
- no infinite worker loop;
- no duplicate durable accepted result;
- cost bounded;
- session recovery remains correct.

## J11. Stale running jobs

Regression protect 4.9 recovery and existing job stale recovery.

## J12. Privacy logging

Run failure-heavy corpus and inspect structured logs/test hooks.

No raw problem expression should become metric labels/general debug dump.

### Package J Exit Gate

- [ ] every fix fixture-backed
- [ ] production fixture guard green
- [ ] retry/timeout bounded
- [ ] response size bounded
- [ ] fallback provenance correct
- [ ] no silent schema relaxation
- [ ] lineage safe
- [ ] no retry storm
- [ ] privacy logs reviewed

---

# 45. Package K — Security and Privacy Hard-Tail Gate

## Goal

Translate threat-model promises into release fixtures.

## K1. Prompt injection-looking content

Cases include:

```text
IGNORE PREVIOUS INSTRUCTIONS
SYSTEM:
DEVELOPER:
RETURN SECRET
FETCH http://...
CALL TOOL
SQL-looking commands
JSON tool-looking objects
```

Expected:

```text
visible content only
no instruction authority
no URL fetch
no secret access
no tool call
```

## K2. Recognition geometry abuse

Cases:

```text
negative coordinates
coordinates > 1
invalid bounding-box dimensions
invalid reading order
duplicate IDs
too many blocks
oversized text
```

Expected strict controlled failure/review.

## K3. Parser adversarial math

Cases:

```text
undeclared symbols
unexpected functions
extreme depth
extreme exponent
malformed relation
source-reference fabrication
hidden assumptions
```

Expected controlled semantic/schema failure.

## K4. Canonical/verifier-input safety

Regression protect:

- allowlisted operators/functions;
- declared variables;
- complexity limits;
- no unsafe text eval.

## K5. BOLA full Phase 4

Run owner-isolation regression for:

```text
assets
preprocessing
recognition
parse
correction
canonical
classification
history
detail
```

## K6. Dataset PII heuristic scan

Check committed evaluation metadata/assets for obvious prohibited patterns:

```text
email
phone
real account ID
real JWT/token
production object key
real student name metadata
```

This is an engineering safety scan, not a substitute for privacy/legal review.

## K7. Secret scan

Existing repository secret scan remains mandatory.

## K8. Holdout leakage

Protected holdout fingerprint appearing in committed training/prompt/dev data is hard failure.

### Package K Exit Gate

- [ ] prompt injection gate
- [ ] geometry gate
- [ ] parser abuse gate
- [ ] canonical safety gate
- [ ] BOLA Phase 4 green
- [ ] dataset privacy scan
- [ ] secret scan
- [ ] holdout leakage zero

---

# 46. Package L — Observability, Quality Drift, Cost and Latency Operations

## Goal

Make future degradation diagnosable without raw learner content.

## L1. Evaluation vs production telemetry

Evaluation metrics live primarily in evaluation reports.

Do not create thousands of per-case Micrometer series.

Production metrics remain low-cardinality.

## L2. Verify current production observability

Ensure Phase 4 runtime already emits enough low-cardinality data for:

```text
recognition outcomes
schema invalid
timeouts
fallback
parse outcomes
semantic invalid
unsupported
review required
classification outcomes
latency
estimated cost
```

## L3. Scheduled quality drift response

Document:

```text
scheduled connected evaluation FAIL
→ freeze promotion
→ identify metric/slice
→ inspect route/prompt/schema provenance
→ compare prior report
→ rollback candidate/canary if necessary
→ add permanent regression fixture when novel
```

## L4. Cost report

Connected evaluation includes:

```text
recognition cost/case
parse cost/case
classification cost/case
total ingestion cost/case
fallback incremental cost
p95 cost
```

## L5. Latency report

Per stage and E2E:

```text
p50
p95
```

Define boundaries clearly.

Dataset setup time is not provider latency.

## L6. Provider drift

Scheduled connected evaluation can reveal drift even with no code commit.

Report records actual:

```text
provider
model
route policy
prompt
schema
pricing version
```

## L7. Alert strategy

Actionable only:

- repeated connected gate failure;
- critical hard gate failure;
- production schema-invalid spike;
- provider latency spike;
- unexpected cost increase.

Do not page for a single noncritical dev fixture mismatch.

### Package L Exit Gate

- [ ] runtime telemetry audited
- [ ] quality drift process
- [ ] cost attribution
- [ ] p50/p95 latency
- [ ] provider provenance
- [ ] privacy-safe labels
- [ ] actionable alert policy

---

# 47. Package M — iOS / Learner Journey Phase 4 Regression

## Goal

Prove evaluation hardening does not break the user-facing Phase 4 journey.

Sprint 4.10 should not introduce a new major iOS feature.

## M1. Full Phase 4 user path

Regression:

```text
capture/import
→ upload
→ preprocessing
→ recognition
→ parse
→ review/correction
→ canonicalization
→ classification
→ history/recovery
→ READY_FOR_SOLVE boundary
```

## M2. Recovery outcomes

Verify UI mapping for:

```text
retake/reimport
retry preprocessing where exposed
retry recognition
retry parse
review parse
retry classification
unsupported
offline
```

## M3. No fake progress

Continue semantic stages, not invented percentage.

## M4. App termination

Terminate/relaunch during:

```text
recognition
parse
classification
```

Expected:

```text
GET detail
poll running durable job
no duplicate mutation
```

## M5. Offline history

Sprint 4.9 stale-while-revalidate remains green.

## M6. Selected parse review

User correction remains selected after navigation/relaunch.

## M7. Accessibility phase close

Review:

- Dynamic Type;
- VoiceOver;
- semantic status text;
- retry action labels;
- ProblemReview editors;
- ProblemHistory rows/detail;
- offline banner;
- reduced motion.

## M8. Localization

No new failure/recovery text bypasses localization.

## M9. Real-device evidence

The capability matrix still records real-device capture validation as external evidence.

If available, attach real-device camera/focus/picker evidence.

If unavailable, preserve the technical-debt/known-limitation record; do not claim it tested.

### Package M Exit Gate

- [ ] full user journey green
- [ ] recovery states green
- [ ] termination/relaunch green
- [ ] no duplicate AI mutation
- [ ] correction authority green
- [ ] accessibility reviewed
- [ ] localization reviewed
- [ ] real-device limitation honest

---

# 48. Package N — Architecture Drift, Data and Phase Integrity Review

## Goal

Verify ten Phase 4 sprints still form one coherent architecture.

## N1. Layer separation

Confirm:

```text
ProblemAsset
≠ RecognitionEvidence
≠ ProblemParse
≠ CanonicalProblem
≠ ProblemClassification
```

## N2. Authority chain

Confirm:

```text
problem_sessions.current_parse_id
        ↓
canonical matching selected parse
        ↓
classification matching current canonical
```

No latest-row shortcut.

## N3. Provider SDK leakage

Search:

```text
problem domain/application
iOS
```

for provider-specific SDK imports.

None should be introduced by 4.10.

## N4. Prompt repository drift

Current runtime prompts live under:

```text
services/api/src/main/resources/prompts/
```

while canonical long-term hierarchy describes root `prompts/`.

Sprint 4.10 should evaluate the **actual runtime prompt identity** and document placement drift.

Do not perform unrelated Sprint 5.2 prompt-registry migration.

## N5. Schema audit

Review:

```text
recognition-evidence
problem-parse
canonical-problem
verifier-input
problem-classification
```

Any existing filename anomaly, such as classification schema naming, must be reference-searched before any rename.

No casual contract rename.

## N6. Migration audit

Review V007–V015:

- immutability;
- ownership FKs;
- cascade;
- indexes;
- selected parse;
- classification history;
- history pagination.

Default no V016.

## N7. Retention/deletion

Full Phase 4 account export/delete regression.

## N8. Job orchestration

Verify:

- job idempotency;
- stale recovery;
- retryable/terminal;
- max attempts;
- stale result protection;
- exact-stage retry.

### Package N Exit Gate

- [ ] architecture layers intact
- [ ] authority chain exact
- [ ] no provider leakage
- [ ] prompt drift documented
- [ ] schemas audited
- [ ] V007–V015 reviewed
- [ ] privacy lifecycle green
- [ ] jobs green

---

# 49. Package O — Documentation, Evidence and Phase 4 Closure

## Goal

Produce a Phase 4 evidence package that Phase 5 can safely consume.

## O1. Sprint documents

Create:

```text
SPRINT_4.10_IMPLEMENTATION_MAP.md
SPRINT_4.10_EXECUTION_REPORT.md
```

Execution report starts:

```text
PENDING FINAL GATES
```

## O2. Generated evaluation evidence

Generate:

```text
ingestion-evaluation-report.json
ingestion-evaluation-summary.md
ingestion-release-comparison.json
```

## O3. Phase 4 closure report

Recommended:

```text
PHASE_04_EXECUTION_REPORT.md
```

It summarizes:

- all Sprint statuses;
- implemented capabilities;
- quality baseline;
- security/privacy;
- cost/latency;
- architecture;
- known limitations;
- external blockers;
- Phase 5 handoff.

## O4. Canonical docs to review/update

```text
00_MASTER_INDEX.md
DOCUMENTATION_MANIFEST.md

ai/28_AI_EVALUATION_AND_GOLDEN_DATASET.md
ai/29_AI_COST_LATENCY_AND_RELIABILITY.md

operations/37_OBSERVABILITY_SLOS_AND_INCIDENTS.md
operations/38_CI_CD_ENVIRONMENTS_AND_RELEASES.md

security/35_SECURITY_THREAT_MODEL.md
security/36_PRIVACY_AND_STUDENT_DATA_PROTECTION.md

quality/41_ENGINEERING_STANDARDS_FOR_CODEX.md
quality/42_DEFINITION_OF_DONE_AND_ACCEPTANCE.md
quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md

roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md

sprints/phase04_problem_capture_and_canonicalization/
  PHASE_04_PROBLEM_CAPTURE_AND_CANONICALIZATION.md
```

## O5. Capability status

At full 4.10 completion:

```text
CAP-PROBLEM-001..005 = their actual completed state
CAP-EVAL-001 = Partial/Foundation
```

Do not mark global evaluation fully Complete unless Phase 5.12 scope is actually delivered.

## O6. RTM

Recommended after successful 4.10:

```text
REQ-INGEST-EVAL-001 = Satisfied
REQ-EVAL-001 = Foundation
```

### Package O Exit Gate

- [ ] Sprint report
- [ ] eval report
- [ ] comparison artifact
- [ ] Phase 4 report
- [ ] docs synced
- [ ] capability matrix honest
- [ ] RTM honest
- [ ] Phase 5 handoff written



---

# 50. Proposed File-Level Implementation Map

## Evaluation corpus

```text
evaluations/
├── README.md
├── golden-datasets/
│   └── parsing/
│       └── ingestion-v1/
│           ├── manifest.yaml
│           ├── development.jsonl
│           ├── regression.jsonl
│           ├── hard-tail.jsonl
│           ├── assets/
│           │   ├── synthetic/
│           │   ├── internal-authored/
│           │   └── licensed-public/
│           └── licenses/
│               └── README.md
├── rubrics/
│   ├── ingestion-coverage-v1.yaml
│   ├── ingestion-recognition-v1.yaml
│   ├── ingestion-parser-v1.yaml
│   └── ingestion-e2e-v1.yaml
├── runners/
│   ├── validate_ingestion_dataset.py
│   ├── run_ingestion_eval.py
│   ├── recognition_metrics.py
│   ├── parser_metrics.py
│   ├── ingestion_metrics.py
│   └── compare_release.py
├── baselines/
│   ├── production-ingestion-v1.json
│   └── ingestion-release-gates-v1.yaml
└── reports/
    └── .gitkeep
```

Avoid generic catch-all files such as `utils.py`.

---

## Evaluation schemas

```text
packages/schemas/
├── ingestion-evaluation-case.schema.json
└── ingestion-evaluation-report.schema.json
```

Potentially update:

```text
packages/schemas/README.md
```

---

## Backend evaluation test support

Recommended:

```text
services/api/src/test/java/com/verifiedai/problem/evaluation/
├── IngestionEvaluationFixtureLoader.java
├── IngestionEvaluationExecutor.java
├── IngestionEvaluationResult.java
├── IngestionGoldenRegressionTest.java
├── IngestionHardTailTest.java
└── IngestionPhase4EndToEndTest.java
```

Do not put this in production main packages unless a reusable production behavior genuinely belongs there.

---

## Scripts

```text
scripts/evaluation/
├── run-golden-suite.sh
└── update-approved-baseline.sh
```

---

## CI

```text
.github/workflows/ai-evaluation.yml
```

---

## Root/config files likely modified

```text
Makefile
.gitignore only if generated eval path needs an explicit rule
services/api/pom.xml only if a dedicated evaluation test profile is truly necessary
evaluations/README.md
```

---

# 51. Current Phase 4 Reachability Matrix

The dataset must reflect actual runtime support.

| Family | Recognition | Parser | Canonical V1 | Classification V1 | Phase 4 E2E claim |
|---|---:|---:|---:|---:|---:|
| Arithmetic | Yes | Yes | Yes | Yes | Yes |
| Algebra | Yes | Yes | Yes | Yes | Yes |
| Equation | Yes | Yes | Yes | Yes | Yes |
| Inequality | Yes | Yes | Yes | Yes | Yes |
| Function | Recognition only/broad | Parser may support | Do not assume | Do not assume | No claim |
| Limit | Recognition only/broad | Parser may support | No current E2E claim | No current E2E claim | No |
| Derivative | Recognition only/broad | Parser may support | No current E2E claim | Ontology alone is insufficient | No |
| Integral | Recognition only/broad | Parser may support | No current E2E claim | Ontology alone is insufficient | No |

The exact matrix must be validated against code during implementation.

---

# 52. Connected Provider Completion Rule

Because the audited repository does not currently expose a non-fixture production provider adapter, do not fake connected accuracy.

Full connected-quality evidence requires:

```text
approved route
non-fixture provider
provider/model provenance
real cost/latency
representative corpus
```

If unavailable:

```text
CONNECTED_PROVIDER_BASELINE = BLOCKED
```

Allowed closure choices:

1. Sprint remains not fully complete until evidence exists; or
2. an explicit accepted exception names:
   - owner;
   - risk;
   - expiry condition;
   - remediation sprint;
   - missing evidence.

Do not silently redefine fixture accuracy as production accuracy.

---

# 53. Evaluation Cost-Budget Contract

Connected run configuration should expose:

```text
max_total_cost_micros
max_cost_per_case_micros
max_cases
max_wall_clock_duration
```

Before an expensive call:

- check remaining budget;
- respect route max cost;
- stop cleanly when budget is insufficient.

Result:

```text
BLOCKED_BUDGET
```

A partial run cannot PASS a full release gate.

---

# 54. Latency Boundary Contract

Measure separately:

```text
provider latency
stage/application latency
end-to-end evaluated pipeline latency
```

Do not include:

- fixture generation;
- dependency bootstrap;
- report rendering;

inside provider latency.

Report p50/p95.

---

# 55. Failure Injection Matrix

Required:

| Failure | Expected behavior |
|---|---|
| provider timeout | bounded retry / correct retryable outcome |
| provider 429 | bounded retry/fallback according to policy |
| provider 5xx | bounded retry/fallback |
| malformed JSON | schema failure, no authoritative artifact |
| oversized AI output | controlled rejection |
| missing confidence | UNKNOWN/review semantics |
| invalid coordinates | controlled recognition failure |
| object storage unavailable | recoverable operational failure |
| parser semantic-invalid | no accepted bad revision |
| parser unsupported | first-class unsupported |
| ambiguous recognition | review-required propagation |
| stale running job | recovery policy |
| stale parse after USER correction | not authoritative |
| stale canonical | not current |
| stale classification | not current |
| duplicate retry | idempotent logical work |

---

# 56. Hard-Gate Matrix

Hard release blockers:

```text
dataset integrity violation
protected holdout leakage
critical false-authoritative acceptance
unsafe canonical input accepted
invented source evidence accepted
unsupported coerced into supported authority
cross-user access
production LOCAL_FIXTURE enabled
selected-parse lineage violation
raw secret/PII in fixture or report
```

Measured relative gates:

```text
CER
token error
parser field accuracy
skill accuracy
review/correction estimates
latency
cost
```

This distinction prevents arbitrary probabilistic 100% targets while keeping trust/security invariants strict.

---

# 57. CI Decision Semantics

Use exactly:

```text
PASS
FAIL
BLOCKED
```

## PASS

All required comparable evidence exists and gates pass.

## FAIL

Evidence is comparable and at least one gate is violated.

## BLOCKED

A trustworthy decision cannot be produced because:

- provider unavailable;
- holdout unavailable;
- invalid dataset;
- budget exhausted;
- missing required slice;
- incompatible baseline;
- invalid report provenance.

`BLOCKED` must never be coerced to PASS.

---

# 58. Report Privacy Contract

Normal reports contain:

- fixture/case ID;
- slice;
- outcome;
- aggregate metrics;
- safe provider/model provenance;
- latency/cost;
- gate result.

Normal reports do not contain:

- protected asset bytes;
- raw student production content;
- access/refresh tokens;
- signed URLs;
- object keys;
- secrets;
- full raw provider output unless stored in a separately restricted debug artifact.

---

# 59. Evaluator Unit Tests

## Dataset validator

Test:

```text
duplicate ID
missing asset
checksum mismatch
invalid schema
missing license/source
forbidden production-user origin
missing required slice
protected duplicate
```

## CER/token metrics

Use examples with known edit distance.

## Precision/recall

Use exact hand-calculated cases.

## Percentile

Use a deterministic small latency vector and assert p50/p95 policy.

## Comparator

Test:

```text
exact pass
small allowed regression
regression beyond delta
critical hard-gate failure
missing metric
missing slice
incompatible dataset
cost regression
latency regression
BLOCKED behavior
```

## Baseline promotion

At minimum exercise dry-run/argument validation.

---

# 60. Existing Regression Suite to Preserve

The final sprint gate must retain green coverage for, at minimum:

```text
ProblemAssetUploadApplicationServiceTest
ProblemAssetPreprocessingApplicationServiceTest
ProblemRecognitionApplicationServiceTest
ProblemParseApplicationServiceTest
ProblemParseCorrectionApplicationServiceTest
CanonicalProblemApplicationServiceTest
ProblemClassificationApplicationServiceTest
ProblemClassificationGoldenEvaluationTest
ProblemSessionLifecyclePolicyTest
ProblemSessionRecoveryPlannerTest
ProblemSessionCursorCodecTest
```

plus relevant controller, migration, privacy and integration tests.

---

# 61. Mandatory Phase 4 E2E Evaluation Cases

## E2E-01 — Clean arithmetic

Expected:

```text
correct recognition
correct parse
correct canonical
correct skill
READY_FOR_SOLVE
```

## E2E-02 — Linear equation

Protect relation and variable semantics.

## E2E-03 — Quadratic

Protect exponent recognition and quadratic classification.

## E2E-04 — Inequality

Protect `<`, `>`, `≤`, `≥` semantic relation.

## E2E-05 — Ambiguous sign

Expected:

```text
REVIEW_REQUIRED
```

not silent READY_FOR_SOLVE.

## E2E-06 — User correction

Flow:

```text
AI parse R1
→ curated correction R2
→ R2 selected
→ canonical from R2
→ classification from current canonical
```

## E2E-07 — Unsupported structure

Expected explicit unsupported.

## E2E-08 — Prompt injection-looking visible content

Expected no instruction authority.

## E2E-09 — Retryable recognition failure

Expected exact-stage retry.

## E2E-10 — Retryable parser failure

Recognition must not repeat.

## E2E-11 — Stale lineage

Old parse/canonical/classification cannot become current.

## E2E-12 — Cross-user

No data/mutation leak.

---

# 62. Final Approved Quality Report Sections

The final approved ingestion report should contain:

```text
1. Executive result
2. Dataset identity/checksum
3. Evaluation mode
4. Route/provider/model/prompt/schema provenance
5. Overall quality
6. Recognition metrics
7. Parser metrics
8. Canonicalization metrics
9. Classification metrics
10. End-to-end metrics
11. Review/unsupported behavior
12. Correction-required estimate
13. False-authoritative acceptance
14. Hard-tail/security results
15. Slice table
16. p50/p95 latency
17. Cost distribution
18. Regressions vs baseline
19. Known limitations
20. Gate decision
21. Approval metadata
22. Report checksum
```

---

# 63. Known-Limitations Contract

The final Sprint/Phase report must explicitly list unproven areas.

Examples:

```text
real-device camera/focus/picker evidence unavailable
approved non-fixture provider route unavailable
calculus not production E2E
PDF OCR path unsupported/limited
protected holdout coverage still small
```

“Not tested” never becomes “supported”.

---

# 64. Rollout of the Evaluation Gate Itself

Do not make a brand-new statistical pipeline an immediate required merge gate before proving repeatability.

Recommended:

```text
1. Land dataset validator.
2. Land deterministic evaluator.
3. Run repeatedly on main.
4. Freeze first approved deterministic baseline.
5. Add CI in informational/non-blocking mode.
6. Eliminate flakiness.
7. Make deterministic gate required on material paths.
8. Add connected scheduled/manual evaluation.
9. Add protected holdout release gate when route/storage are operational.
```

---

# 65. Rollback Strategy

If evaluator tooling fails:

- preserve runtime route;
- preserve last approved baseline;
- mark evaluation BLOCKED;
- rollback tooling/workflow if necessary.

If candidate route/prompt/model fails:

- do not update baseline;
- retain current approved route;
- fix or abandon candidate.

If a canary already regresses:

- rollback route/config independently;
- preserve evaluation evidence;
- add permanent regression case if novel.

---

# 66. Why No V016 by Default

Sprint 4.10 artifacts are engineering/release data:

```text
dataset
baseline
reports
gates
```

not learner product state.

Therefore a table such as:

```text
evaluation_runs
```

should not be added to production PostgreSQL merely for convenience.

If hardening uncovers a real durable runtime defect that needs schema change:

- create V016;
- document cause;
- add Testcontainers;
- update retention/deletion.

---

# 67. Why No Public Evaluation Endpoint

Evaluation runs in engineering/release environments.

Do not add:

```text
/api/v1/evaluation/run
/api/v1/admin/evaluation
```

A public/admin evaluator adds security surface without learner value.

If a runtime API bug is found, fix that actual endpoint contract rather than exposing evaluation internals.

---

# 68. Exact Implementation Order

```text
A — Governance / exit contract
B — Dataset / schema / manifest / coverage
C — Recognition scoring
D — Parser scoring
E — Canonical/classification scoring
F — E2E evaluator
G — Aggregator / baseline / comparator
H — Makefile / scripts
I — GitHub Actions
J — Runtime hardening from failing fixtures
K — Security/privacy adversarial
L — Operations/cost/latency
M — iOS regression
N — Architecture/data drift review
O — Documentation + Phase closure
```

Do not begin by editing prompts.

Measurement comes first.

---

# 69. First Implementation Slice

Execute:

```text
1. evaluation schemas
2. ingestion-v1 manifest
3. migrate/preserve Sprint 4.5 seed
4. dataset validator
5. checksum validation
6. coverage validation
7. leakage validation
8. validator tests
9. `make eval-ai` skeleton
```

Runtime behavior remains unchanged.

First slice proves:

> The benchmark itself is trustworthy before judging the system.

---

# 70. Second Implementation Slice

Execute:

```text
1. recognition normalization/tokenizer
2. recognition metrics
3. parser metrics
4. metric unit tests
5. deterministic fixture run
6. machine-readable report
```

Proves:

> We can reproducibly score Phase 4 outputs without provider cost.

---

# 71. Third Implementation Slice

Execute:

```text
1. canonical scorer
2. classification scorer
3. expanded classification corpus
4. E2E executor
5. correction cases
6. retry/recovery cases
7. hard-tail cases
8. false-authoritative metric
```

Proves:

> The whole current Phase 4 pipeline has a meaningful deterministic regression baseline.

---

# 72. Fourth Implementation Slice

Execute:

```text
1. baseline schema/artifact
2. gate policy
3. comparator
4. explicit baseline promotion
5. Makefile finalization
6. AI evaluation workflow
```

Proves:

> A regression can block a release automatically.

---

# 73. Fifth Implementation Slice — Connected Quality

If an approved non-fixture route exists:

```text
1. connected run
2. protected holdout run
3. cost/latency
4. disagreement review
5. approved production-ingestion baseline
```

If not:

```text
CONNECTED_PROVIDER_BASELINE = BLOCKED
```

Do not fake completion.

---

# 74. Proposed Stable Commands

Main deterministic gate:

```bash
make eval-ai
```

Dataset debug:

```bash
python3 evaluations/runners/validate_ingestion_dataset.py \
  --manifest evaluations/golden-datasets/parsing/ingestion-v1/manifest.yaml
```

Comparator:

```bash
python3 evaluations/runners/compare_release.py \
  --baseline evaluations/baselines/production-ingestion-v1.json \
  --candidate .generated/evaluations/latest/ingestion-evaluation-report.json \
  --policy evaluations/baselines/ingestion-release-gates-v1.yaml
```

Connected:

```bash
AI_EVAL_MODE=connected make eval-ai
```

Exact arguments may be normalized during implementation, but there must be one canonical documented command.

---

# 75. Final Repository Gates

Run the repository-standard gates:

```bash
make doctor
make lint
make docs-check
make contracts-check
make secret-scan
make test-api
make test-verifier
make test-ios
make eval-ai
make check
git diff --check
git status --short
```

Connected provider/holdout evaluation is run separately when configured and recorded in the execution report.

---

# 76. Definition of Done — Dataset

- [ ] `ingestion-v1` dataset exists
- [ ] manifest versioned
- [ ] case/report schemas versioned
- [ ] asset checksums
- [ ] source/license provenance
- [ ] no production student content
- [ ] coverage policy
- [ ] required slices
- [ ] hard-tail
- [ ] protected-holdout contract
- [ ] duplicate/leakage zero
- [ ] Sprint 4.5 seed semantics retained

---

# 77. Definition of Done — Recognition

- [ ] exact match
- [ ] CER
- [ ] math token error
- [ ] critical symbol error
- [ ] block/order metrics
- [ ] coordinate validation
- [ ] ambiguity/review metrics
- [ ] visual-quality slices
- [ ] injection hard-tail
- [ ] response-limit hard-tail
- [ ] connected cost/latency when available

---

# 78. Definition of Done — Parser

- [ ] JSON valid
- [ ] schema valid
- [ ] semantic valid
- [ ] support status
- [ ] subject/topic
- [ ] task/problem type
- [ ] expression
- [ ] variables
- [ ] constraints
- [ ] assumptions
- [ ] source lineage
- [ ] review-required
- [ ] unsupported
- [ ] prompt injection as data

---

# 79. Definition of Done — Canonicalization

- [ ] supported success
- [ ] unsupported rejection
- [ ] AST structural exactness
- [ ] variables
- [ ] restrictions
- [ ] unsafe rejection
- [ ] deterministic repeatability
- [ ] selected parse authority
- [ ] stale lineage

---

# 80. Definition of Done — Classification

- [ ] expanded representative current-scope corpus
- [ ] primary skill accuracy
- [ ] secondary skill precision/recall
- [ ] difficulty
- [ ] semantic status
- [ ] subject/topic
- [ ] review reason
- [ ] ontology integrity
- [ ] confidence policy
- [ ] no fake E2E skills

---

# 81. Definition of Done — End-to-End

- [ ] clean arithmetic
- [ ] linear equation
- [ ] quadratic
- [ ] inequality
- [ ] ambiguity
- [ ] correction
- [ ] unsupported
- [ ] injection
- [ ] exact-stage retry
- [ ] session recovery
- [ ] stale lineage
- [ ] BOLA
- [ ] READY_FOR_SOLVE stop boundary

---

# 82. Definition of Done — Release Gate

- [ ] machine-readable report
- [ ] approved baseline
- [ ] gate policy
- [ ] hard gates
- [ ] overall relative gates
- [ ] critical slice gates
- [ ] p50/p95 latency
- [ ] cost
- [ ] missing/incomparable = BLOCKED
- [ ] manual baseline promotion
- [ ] report checksum

---

# 83. Definition of Done — CI

- [ ] `ai-evaluation.yml`
- [ ] deterministic PR jobs
- [ ] material-path filters
- [ ] no fork secret exposure
- [ ] connected protected job
- [ ] cost budget
- [ ] artifact upload
- [ ] no automatic baseline write
- [ ] stable `make eval-ai`

---

# 84. Definition of Done — Hardening

- [ ] LOCAL_FIXTURE production guard regression
- [ ] timeout/max attempts
- [ ] response size
- [ ] fallback provenance
- [ ] retry storm bounded
- [ ] schema strictness
- [ ] semantic false-accept fixtures
- [ ] selected-current lineage
- [ ] privacy-safe logs
- [ ] no unrelated refactor

---

# 85. Definition of Done — Phase 4 Closure

- [ ] 4.1 evidence reviewed
- [ ] 4.2 evidence reviewed
- [ ] 4.3 evidence reviewed
- [ ] 4.4 evidence reviewed
- [ ] 4.5 evidence reviewed
- [ ] 4.6 evidence reviewed
- [ ] 4.7 evidence reviewed
- [ ] 4.8 evidence reviewed
- [ ] 4.9 evidence reviewed
- [ ] 4.10 evidence green or explicit accepted exception
- [ ] architecture drift review
- [ ] security/privacy delta
- [ ] AI quality/cost delta
- [ ] migration/retention review
- [ ] iOS accessibility/localization
- [ ] observability/supportability
- [ ] no Phase 4 P0/P1
- [ ] docs synchronized
- [ ] full repository gate
- [ ] Phase 5 handoff

---

# 86. Required Sprint 4.10 Demonstrations

## Demo A — Deterministic baseline

```text
dataset
→ validation
→ deterministic evaluation
→ report
→ baseline compare
→ PASS
```

Show exact dataset/report checksum.

## Demo B — Intentional regression

Use a controlled test-only bad candidate or prepared result.

Example:

```text
inequality relation sign becomes wrong
→ critical slice regression
→ comparator FAIL
→ process exits non-zero
```

This proves the gate is real.

## Demo C — Ambiguity trust

```text
ambiguous input
→ REVIEW_REQUIRED
→ false_ready_for_solve = 0
```

## Demo D — Connected provider

If available:

```text
connected run
→ provider/model provenance
→ quality
→ p95 latency
→ cost
→ gate decision
```

If unavailable:

```text
BLOCKED_NO_APPROVED_PROVIDER_ROUTE
```

with explicit blocker/exception evidence.

---

# 87. Required Evidence Package

Persist or attach evidence for:

```text
SPRINT_4.10_IMPLEMENTATION_MAP.md
SPRINT_4.10_EXECUTION_REPORT.md
dataset manifest
coverage report
dataset checksum
hard-tail summary
evaluation report
approved baseline
gate policy
release comparison
cost/latency summary
CI workflow evidence
full backend/iOS/verifier test summary
architecture drift review
security/privacy review
known limitations
PHASE_04_EXECUTION_REPORT.md
Phase 5 handoff
```

---

# 88. Phase 5 Handoff Contract

Phase 5 may rely on:

- durable ProblemSession lifecycle;
- measured recognition evidence;
- measured parser structure;
- explicit correction revisions;
- selected parse authority;
- safe canonical representation;
- current classification authority;
- history/retry/recovery;
- ingestion golden/hard-tail corpus;
- approved Phase 4 quality baseline;
- candidate release comparator;
- quality/cost/latency reporting;
- provider/prompt/schema provenance;
- protected-evaluation governance.

Phase 5 may NOT assume:

- calculus is E2E;
- a real provider baseline exists if 4.10 recorded it blocked;
- solution correctness has been evaluated;
- verification exists;
- global `CAP-EVAL-001` is complete.

---

# 89. Boundaries Not to Cross

Sprint 4.10 must not implement:

```text
full Sprint 5.1 AI router expansion
solver primary
solver secondary
arbitration
verification planner
final verification status
explanation generation
solution screen
tutor
attempt
mistake
mastery
study plan
model training
self-hosted inference
```

---

# 90. Runtime Fix Review Checklist

For each issue found by evaluation:

```text
1. Is the fixture valid?
2. Is its label trustworthy?
3. Is it current production scope?
4. Is the issue reproducible?
5. Which module owns it?
6. Is a schema version change required?
7. Is a prompt version change required?
8. Does provenance remain immutable?
9. Does cost change?
10. Does latency change?
11. Does review/unsupported behavior change?
12. Does another critical slice regress?
13. Does full evaluation pass?
```

---

# 91. Prompt Change Rule

If prompt tuning becomes necessary:

Do NOT edit:

```text
v001
```

in place.

Create:

```text
v002
```

and record:

- prompt ID/version;
- compatible schema;
- baseline candidate;
- quality delta;
- cost delta;
- latency delta;
- rollback.

If a prompt change is unnecessary, do not force one into 4.10.

---

# 92. Schema Change Rule

If evaluation reveals a contract defect:

- determine whether code validation or schema is wrong;
- preserve old durable revision interpretation;
- use a new schema version for incompatible change;
- update all consumers/evaluation cases.

Do not broaden strict validation merely because a provider emitted unexpected output.

---

# 93. Route/Model Change Rule

Any route/model change requires:

```text
baseline
candidate
hard-tail
quality
latency
cost
rollback
```

No “cheaper model looks good in five examples” promotion.

---

# 94. Dataset Change Rule

A gold-label change is a semantic change to evaluation.

Require:

- reason;
- review;
- version/checksum decision;
- coverage re-run.

Protected holdout cannot be relabeled casually to make a candidate pass.

---

# 95. Approved Baseline Change Rule

Baseline means:

```text
accepted production behavior
```

not:

```text
latest generated run
```

Update only from a complete reviewed report.

---

# 96. Allowed Sprint Statuses

Execution report may use:

```text
PENDING FINAL GATES

IMPLEMENTED — CONNECTED PROVIDER BASELINE BLOCKED

COMPLETE — DETERMINISTIC GATES GREEN WITH EXPLICIT APPROVED EXCEPTION

COMPLETE — FULL INGESTION QUALITY GATES GREEN
```

Use the strongest truthful status only.

---

# 97. Final Phase Status

Only after accepted 4.10 closure:

```text
Phase 4 COMPLETE

4.1  COMPLETE
4.2  COMPLETE
4.3  COMPLETE
4.4  COMPLETE
4.5  COMPLETE
4.6  COMPLETE
4.7  COMPLETE
4.8  COMPLETE
4.9  COMPLETE
4.10 COMPLETE
```

---

# 98. Production Completion Statement Template

Use only after actual evidence:

```text
Sprint 4.10 COMPLETE — FULL INGESTION QUALITY GATES GREEN

Delivered:
- versioned ingestion evaluation dataset;
- manifest, provenance and checksums;
- required slice coverage;
- hard-tail/adversarial corpus;
- protected holdout contract;
- recognition accuracy metrics;
- parser semantic/structural metrics;
- canonicalization regression metrics;
- classification accuracy metrics;
- Phase 4 end-to-end evaluator;
- false-authoritative-acceptance hard gate;
- latency and cost reporting;
- approved ingestion baseline;
- release-gate comparator;
- explicit baseline promotion;
- root Makefile evaluation command;
- GitHub Actions AI evaluation workflow;
- production fixture-route regression guard;
- Phase 4 security/privacy/architecture/operations closure evidence;
- full repository regression green.

Phase 4 COMPLETE.
Phase 5 may consume the canonical problem and classification contracts.
```

---

# 99. Final Engineering Principle

Before Sprint 4.10:

```text
"We believe ingestion works because many tests pass."
```

After Sprint 4.10:

```text
"We know what ingestion supports,
how accurately each stage performs,
where it fails,
when it correctly asks for review,
what it costs,
how long it takes,
which slices regress,
which failures are release-critical,
and CI can prevent a bad change from being promoted."
```

That is the production-level goal of Sprint 4.10.
