# Sprint 5.12 — execution report

Status: OWNER_ACCEPTANCE_PASSED for local implementation scope. Production promotion remains BLOCKED.

## A. Baseline

Initial checkout was clean on codex/sprint-5-11-verification-arbitration, HEAD
0b0b21adaefacd222da7c98dbf3cbc8ad3229ccb. Initially cached origin/main was
66f00200be48f24f5f690ff32fc0bd376ad952e2. Read-only remote lookup verified main at
dd3d4829da650dcd9bedec4f834226a5b102ab79 (PR #36). After fetching that ref, source
diff showed the same tracked tree; implementation branch was safely created at it:
codex/sprint-5-12-evaluation-release-gate. No owner changes were overwritten.
No applicable AGENTS.md was found. No commit, push, merge, reset, rebase, stash or PR.
Sprint 5.11's recorded owner acceptance is inherited historical evidence only.

## B. Implemented deliverables

- 28 synthetic governed cases, checksummed manifest, bounded JSON loader, strict case
  schema, partition/rights/training guards and mandatory critical-slice coverage.
- Deterministic local observation scoring and scoped reports; separate dataset-validation,
  local, connected and holdout modes. External modes remain BLOCKED without calls.
- Separate answer/schema/disposition/verification/scope/uncertainty/source/contradiction
  metrics with denominators, missing observations and per-slice breakdowns. Independent
  step/method labels are unavailable. Numeric samples and agreement never create proof.
- Pure bounded economics reducer includes unsuccessful attempts, retries/fallbacks,
  stage/cache attribution, usage, reservations/commitments/estimates/invoices/forecasts,
  compatible currency/pricing/workload, nearest-rank attempt latency and honest missing
  unit costs. It is not connected to a provider ledger; local economics is unavailable.
- Fail-closed comparator, critical-failure precedence, report integrity and compatibility,
  reviewed create-only local baseline promotion with exact artifact binding. No approved
  baseline or production threshold is fabricated. Proposed values remain proposals.
- Existing Java prompt registry strengthened with default-deny SHA-pinned authorization,
  report and baseline content checks, complete route/prompt/schema/model binding and
  mandatory external evidence attestations. Existing solver production prohibition stays.
- Separate deterministic solver CI job; existing ingestion path preserved. Owner A–J
  acceptance reuses the complete Sprint 5.11 platform run and audits fresh test/report
  evidence, skip policy, stage exits and canonical report hashes.

## C. Changed file inventory

See the complete ownership inventory below. No files were removed. Existing approved
baselines, released prompts/schemas, mathematical policies, migrations, and iOS sources
were not edited. New evaluation contracts are additive.

## D. Security and architectural review

Source review only: provider isolation, no-training default, student-content exclusion
and sole verification-policy authority remain intact. New durable artifacts are offline
synthetic files, not learner database state. No database migration or public API change.
Reports contain IDs, controlled outcomes, aggregate metrics and hashes, not raw inputs.
Input files/cases/attempts/runtime are bounded; traversal, symlink references, duplicate
JSON keys and checksum substitution fail closed. Classpath release artifacts are bounded
and require a human-reviewed registry hash. Hashes ensure integrity, not authenticity of
self-asserted reviews: protected branches and independent evidence review remain external.

## E. Test coverage authored, not executed

- DatasetTests: malformed/versioned manifests and schemas, duplicate/order/checksum,
  missing labels, rights/training provenance, cross-partition leakage, missing coverage,
  restricted partitions, oversized inputs, privacy patterns, unsafe paths and symlinks.
- RunnerTests: reproducibility, bounded config/runtime, missing/error/timeout outputs,
  report safety, unavailable denominators and explicitly blocked external modes.
- HonestyTests: wrong schema-valid answers, false VERIFIED, partial/numeric/secondary
  nonproof, contradiction, stale lineage, domains, calculus scope, uncertainty and security.
- EconomicsTests: zero/missing denominators, missing usage, failed/retry/fallback costs,
  currency/pricing/workload incompatibility, latency tails, ledger bounds and proposals.
- ReleaseTests: deterministic comparison, quality/safety regression, missing baselines,
  artifact/report tampering, prompt substitution, immutable reviewed creation, rollback,
  unsafe references, unapproved thresholds and fixture-to-production isolation.
- SolverReleaseRegistryTest and SolverReleaseEvidenceTest: default denial, mandatory
  release checks, content substitution, exact identities, incompatible baselines,
  missing/coerced attestations, malformed/oversized artifacts and prior-config rollback.

No unit/integration tests, Maven compile/test/verify, Xcode builds/tests, Docker checks,
AI evaluation runners, lint, format/contract/documentation validators, CI simulations or
paid inference were executed by the agent. Artifact-authoring scripts only wrote source,
fixtures, schemas and documentation metadata. No PASS or test count is claimed.

## F. Release state and limitations

Deterministic fixture evaluation capability is authored, awaiting owner execution. It
scores authored synthetic observations, not a live backend or provider response. Existing
backend and verifier suites supply separate implementation regression evidence. The
corpus is not the proposed independently reviewed 240-case production benchmark.

Connected evaluation: BLOCKED_NO_APPROVED_PROVIDER_ADAPTER. Protected holdout:
BLOCKED_PROTECTED_HOLDOUT_UNAVAILABLE. Production promotion: BLOCKED. Independently
reviewed labels, representative data/rights, protected storage, exact provider deployment,
observed cost/latency, approved statistical thresholds, sufficient observations,
comparable approved baseline and stage-specific approval remain unavailable. Production
routes, secondary rollout and wider verification flags remain disabled as before.

The pure economics comparison kernel accepts pre-reviewed threshold inputs for unit
comparison only; no production approval entry point trusts an arbitrary threshold JSON.
Java production evidence is a separately reviewed attestation envelope, not a provider
runner or automated verification of human review. No production bundle is supplied.

Owner must configure branch protection to require deterministic solver/ingestion plus
backend/verifier checks; require reviews for policy/registry/evidence changes; protect
ai-evaluation environment with trusted-main and reviewer rules; establish restricted
holdout access and externally reviewed stage evidence. Hosted settings are not applied.

## G. Owner manual acceptance

Single supported command from repository root:

`bash scripts/quality/sprint_5_12_owner_acceptance.sh`

The script writes a unique .generated/sprint-5-12-owner directory, prints its path and
preserves summary.json, stage exits/logs, Python test counts and safe evaluation reports.
It invokes Sprint 5.11 acceptance once and links its freshly created evidence directory,
including backend/Linux/iOS results. It rejects absent suites and unexpected skips.
The paid-model skip is explicit and does not become production approval. It never calls
a provider, promotes a baseline, deploys, commits or pushes. Missing tools require owner
provisioning; they are not silently skipped. Full local success remains separate from
production readiness and hosted CI.

## H. Next step

Return the generated summary.json and failing stage logs. Follow-up repair will inspect
only returned evidence and directly implicated code; all reruns remain owner-only.

## Complete file inventory by ownership

### AI evaluation

- `evaluations/README.md`
- `evaluations/baselines/solver-release-policy-v1.json`
- `evaluations/fixtures/solver/evaluation-config-v1.json`
- `evaluations/fixtures/solver/evaluation-observations-v1.json`
- `evaluations/golden-datasets/solving/solver-v1/cases.json`
- `evaluations/golden-datasets/solving/solver-v1/manifest.json`
- `evaluations/runners/solver_evaluation.py`
- `evaluations/runners/solver_release.py`
- `evaluations/runners/test_solver_evaluation.py`
- `evaluations/runners/test_solver_release.py`

### Shared schemas

- `packages/schemas/solver-evaluation-case.schema.json`
- `packages/schemas/solver-evaluation-report.schema.json`
- `packages/schemas/solver-production-evidence.schema.json`
- `packages/schemas/solver-release-authorization.schema.json`

### Backend AI governance

- `services/api/src/main/java/com/verifiedai/ai/application/PromptRegistryFailureCode.java`
- `services/api/src/main/java/com/verifiedai/ai/application/PromptRegistryValidator.java`
- `services/api/src/main/java/com/verifiedai/ai/application/PromptReleaseApproval.java`
- `services/api/src/main/java/com/verifiedai/ai/application/SolverReleaseEvidenceValidator.java`
- `services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration/AiInfrastructureConfiguration.java`
- `services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration/ClasspathSolverReleaseEvidenceValidator.java`
- `services/api/src/test/java/com/verifiedai/ai/application/SolverReleaseRegistryTest.java`
- `services/api/src/test/java/com/verifiedai/ai/infrastructure/configuration/SolverReleaseEvidenceTest.java`

### CI and owner tooling

- `.github/workflows/ai-evaluation.yml`
- `Makefile`
- `scripts/quality/check_contracts.py`
- `scripts/quality/sprint_5_12_owner_acceptance.sh`

### Canonical documentation

- `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/ai/28_AI_EVALUATION_AND_GOLDEN_DATASET.md`
- `docs/verified_ai_learning_platform_docs_v4/ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`
- `docs/verified_ai_learning_platform_docs_v4/ai/61_MODEL_REGISTRY_RELEASE_AND_ROLLBACK_GOVERNANCE.md`
- `docs/verified_ai_learning_platform_docs_v4/quality/64_AI_MODEL_REPLACEMENT_DECISION_GATES.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.12_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.12_IMPLEMENTATION_MAP.md`

## Owner acceptance repair — 2026-10-31

Owner evidence: .generated/sprint-5-12-owner/20261031-120947-i2Hxbh/summary.json
and the supplied terminal log. A/B/C/D/G passed in that run; E/F/H/I/J failed.
E exposed an incorrect test expectation: a rehashed substituted prompt is rejected
by the strict report schema as REPORT_SCHEMA_INVALID before the later semantic guard.
The test now asserts that actual boundary, BLOCKED and no production authorization
for every provenance field. Production validation was not weakened.

F exposed unsupported Jackson Duration serialization in both test fixture construction
and the production evidence adapter. Optional module auto-discovery was replaced with
an explicit Duration serializer using exact ISO-8601 text (including nanoseconds),
without adding a dependency. Tests cover PT1S, nanosecond preservation, timeout changes
and numeric substitution. H/I lacked completed platform evidence because Maven stopped
on the backend error; J correctly rejected the incomplete run. No independent iOS or
quality acceptance is claimed from that run.

These repairs and tests are authored only. No test, build, evaluator or validator was
executed by the agent. State remains IMPLEMENTED_PENDING_OWNER_VALIDATION. Rerun the
same single owner acceptance entry point and return its new summary and failing logs.
Production promotion remains BLOCKED; no commit or push was performed.

## Complete local owner acceptance — 2026-10-31

Current state: OWNER_ACCEPTANCE_PASSED. Owner-run evidence:
.generated/sprint-5-12-owner/20261031-121345-aTjFqW/summary.json.
All A–J stages PASS; exitCode 0. The agent read the existing summary, suite records and
owner-supplied log, and did not run tests, builds, evaluators or validators.

Recorded suite evidence: DatasetTests 19, RunnerTests 12, HonestyTests 13,
EconomicsTests 14 and ReleaseTests 18 executed successfully, with no skips or failures
(76 evaluation tests total). The platform evidence directory is
.generated/sprint-5-11-owner/20261031-121348-R2rJT3/. Its required backend, Linux,
iOS and quality gates passed. The log records Linux 338 passed; the new Java suites
SolverReleaseRegistryTest 4 and SolverReleaseEvidenceTest 10 passed without skips;
iOS 113 unit and 12 UI tests passed. Phase 4 ingestion evaluation, baseline comparison
and its 12 runner tests passed. Final J reconciled the required evidence successfully.

This run supersedes the earlier pending and failed local-acceptance checkpoints above.
The earlier descriptions of tests as authored/unexecuted describe agent activity and
pre-acceptance history, not the current owner evidence. Paid models remain
SKIPPED_BY_POLICY; hosted CI NOT_RUN; real-model evaluation, protected holdout and
production promotion BLOCKED; production rollout DISABLED.

After reading the evidence, only acceptance documentation and its manifest were updated.
Those post-run metadata edits are not covered by this recorded run and were not validated
by the agent. No implementation or acceptance-script change was needed. No commit, push,
PR or deployment was performed. External release evidence and approvals remain required.
