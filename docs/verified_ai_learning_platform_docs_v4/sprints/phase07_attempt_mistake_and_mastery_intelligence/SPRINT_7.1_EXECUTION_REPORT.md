# Sprint 7.1 execution report

Status: LOCAL_ACCEPTANCE_PASSED. Source: SOURCE_COMPLETE.
Owner execution: FULL RUN PASS (Run: 20261105-151510-r9cSGm, Gates A–G PASS, source unchanged). Agent execution: NOT_EXECUTED.
Date: 2026-11-05, Europe/Istanbul. Starting branch: main. Starting SHA:
0349160164a45c6956dcb656a72ca7a34aa391e2. Initial working tree: clean.
Ending branch/SHA remain unchanged; source edits are uncommitted and unstaged. The sandbox denied the requested
new codex/sprint-7-1-attempt-submission branch lock because .git is read-only. No escalation, protected-ref update,
commit, push, reset, clean, merge, rebase or deployment was performed. There was no unrelated owner work to overwrite.

## Reused functionality and scope reconciliation

Reused StudentAttempt, existing attempt controllers/repository/orchestrator, owner/source transaction locking,
manual normalization and verifier boundary, reviewed handwriting provenance, immutable learning artifacts, bounded
history, capacity, idempotency, privacy contribution and iOS APIClient/pending architecture. Commercial-wedge
Sprint 7.0A and the existing 6.7–6.8 delivery are prior implementation, not competing Phase 7 aggregates.
ADR-011 and the Phase 7 production gate remain external release constraints, not permission for activation.

## Delivered changes

- Explicit finalAnswer plus optional intermediate reasoning, using submission-v3 and student-attempt-v2 on existing APIs.
- Capture-only durable acknowledgement without normalization, inference, verification, mistake events or mastery updates.
- Typed MVC request, strict bounded pre-binding contract validation, full UUID validation, duplicate/unknown-field rejection,
  explicit malformed UTF-8 rejection and consistent UTF-16/UTF-8 limits. Both Jackson-2 contract and Jackson-3 HTTP boundaries are covered.
- Same-key replay after timeout/source change/feature disable, payload conflicts and bounded concurrent creation through existing locks.
- Unexpected verifier exceptions preserve reauthorized saved evidence; source changes still prohibit assessment publication.
- V033 final_answer constraints, immutable child membership, deferred contiguous shape and Unicode constraints without historical rewrites.
- Default v1 GET compatibility projection and native representationVersion=2 history; original v1/v2 schema bytes and hashes preserved.
- iOS answer entry/review, accurate Saved / Not assessed copy, explicit older-source history, source-bound draft review,
  stable-key retry, pending cleanup, account/generation fences, protected storage and EN/TR accessibility identifiers.
- Privacy-safe accepted-after-commit, rejection, replay, source-conflict, recovery, persistence-failure and duration metrics.
- Owner-only A–G acceptance, isolated PostgreSQL/simulator execution, current-source fingerprints, per-gate JSON/logs,
  summary.json, summary.md, failure-inventory.json and execution metadata. Targeted --gate runs never claim full acceptance.

## Test sources authored or extended; none executed by the agent

- Backend domain/request: final-answer intent, exact text, zero/eleven/twelve-line boundaries, Unicode/NUL/surrogates,
  illegal states, uncertainty, training exclusion, malformed/coerced/nested authority fields and Jackson-3 DTO binding.
- HTTP/PostgreSQL: insert/read/history, compatibility projection, auth/BOLA, stale context, duplicate fields, byte limits,
  same/conflicting keys, concurrent replay, immutable parent/steps, capacity/limiter telemetry, source/account deletion races,
  no mastery mutation, export/deletion and synthetic V032 → V033 upgrade preserving the historical row JSON.
- Contract checker: positive/adversarial shared fixtures, frozen v1/v2 contracts, OpenAPI validation, schema validation,
  legacy display compatibility, migration byte preservation and EN/TR/source registration.
- iOS unit/UI: exact saved answer, interrupted/lost acknowledgement, same-key recovery, recoverable 503, definitive 409
  retaining a source-bound draft, changed-source review, owner isolation, no false verification, shared response fixture,
  historical display and English/Turkish controls. Existing manual/photo/method/solution regressions remain in the runner.
- Evidence writer: full versus targeted acceptance, changed-source invalidation, missing/corrupt/skipped evidence,
  simulator unavailability, required upgrade-case presence, source/secret path exclusions and privacy prerequisite failures.

No Maven, pytest, Swift/Xcode, application build, contract validator, golden evaluator, acceptance script, migration,
Docker integration, model inference or deployment command was executed by the agent. Textual source/diff inspection and
`git diff --check` are the only verification performed; no whitespace errors were reported. This is not compilation,
test, CI, runtime, performance or acceptance evidence. Historical 6.7–6.9 PASS results were not imported.

## Contract, migration and privacy compatibility

The existing /api/v1 paths remain. Legacy POST payloads keep their exact hash semantics. Updated clients explicitly
request representationVersion=2; default GET safely displays new answers as unassessed final text lines. Only v3
requests create native v2 records. No historical attempt is reclassified. Source ownership is server-authoritative.
Final answers follow existing DERIVED_LEARNING retention/export/deletion and training_eligible=false; no child table
or data export destination was added. Metrics contain bounded outcomes only, never learner content or account labels.
All test input is synthetic. No credentials, environment files, process environment dumps, Keychain, SSH or cloud stores
were read. No code/data was sent to an external service. New basic submission paid inference cost: zero by construction.

V033 must precede the new binary. It does not rewrite V030–V032 or historical row contents. The additional step text check
is NOT VALID for historical records and enforced for new writes. The immutable membership trigger checks the parent's
original transaction identity; owner PostgreSQL execution must validate that behavior. Rollback disables new submissions
while retaining the upgraded reader/schema and owned history. Do not roll back to a binary that cannot read v2 rows;
do not drop the final_answer column or use Flyway repair. Shared/production migration execution is not authorized here.

## Owner acceptance tooling and development dependencies

Entry point: bash scripts/quality/sprint_7_1_owner_acceptance.sh --full.
Output: .generated/sprint-7-1-owner/<run-id>/; latest points to that fresh run. Existing .gitignore excludes .generated/.
The runner preserves every gate, produces explicit missing/failure/skip inventories, detects source mutation during the
run and never reuses historical passes. --gate B/C/D/F/G provides focused current-source repair evidence only.
Gate A inventories sources/migrations/fixtures; B covers Java/PostgreSQL and dependent backend regressions; C covers
API/schema contracts; D covers iOS unit/UI; E consumes fresh privacy/auth/training/abuse tests plus a source-only secret
scan; F covers documentation; G covers existing deterministic attempt/method verifier tests and evidence-writer tests.
Missing simulator/tool/dependency evidence is BLOCKED. A failed gate or changed source is FAIL. No test skip is concealed.

PyYAML and openapi-spec-validator were added only to the verifier's development dependency group for Gate C; no product
runtime dependency was added. Quality tooling owns them. Existing string checks cannot validate full OpenAPI structure;
this is the reason for admitting these bounded dependencies. No package install or vulnerability/license audit was run;
owner dependency review remains part of acceptance. Removal requires replacing the OpenAPI check with equivalent local
validation. No package/provider/network operation occurs in the evidence writer itself.

## Remaining external conditions

- Fresh full owner acceptance, dependency availability/review, Java 21, PostgreSQL/Testcontainers and Xcode simulator execution.
- Physical-device VoiceOver review, hosted CI and operational latency evidence; no measurement is claimed here.
- ADR-011/50-student TestFlight outcomes and explicit production authorization remain BLOCKED / NOT_VERIFIED.
- Final-answer mathematical evaluation and expanded extraction remain intentionally outside Sprint 7.1, not unfinished capture work.

SOURCE_COMPLETE is source delivery only. LOCAL_ACCEPTANCE_PASSED is the current handoff status.
PARTIAL would mean unfinished in-scope source; no such item is intentionally deferred. BLOCKED applies to external
conditions above. VERIFIED_BY_OWNER local acceptance has been established for source fingerprint
e5469065bd883a096404dd20b8879bfe1d6909360eee784e9e4154a72ae531de. No Phase 7.2 work or broad production rollout was started.

The implementation map contains the complete changed/new file inventory; the functional matrix maps each requirement
to its owning source, authored tests and remaining execution/release limitation.

## First owner run and source repairs — 2026-11-05

Owner evidence: .generated/sprint-7-1-owner/20261105-141319-LWwTI8/summary.json.
Source fingerprint: 2ff4473de882dc37e955ad0d7b8daaf36ea01fb6771d07cf2a4dc02819cb6cb9.
The full run preserved source integrity and completed cleanup. Overall FAIL:
A PASS, B FAIL, C BLOCKED, D PASS, E BLOCKED, F FAIL, G PASS.
Backend executed 131 tests with two failing cases, no missing/skipped cases. iOS passed with no missing/skipped
cases. Verifier/dependent regressions executed 240 tests without failures, missing cases or skips.
These results belong only to that recorded fingerprint; they do not verify the subsequent repairs.

- Gate B: finalAnswerConcurrentReplayStaleAndDisabledHistory issued a raw DELETE against users, violating the
  retained problem_sessions_user_id_fkey. deletedAccountCannotBeRecreatedByLateAssessment used the same invalid
  deletion inside its verifier callback; the recovery boundary retained the submission, so the expected rejection
  never occurred. Both tests now invoke the real AccountPrivacyApplicationService request/confirmation flow,
  assert the DELETED tombstone and ACCOUNT_NOT_ACTIVE rejection, and check evidence removal. The late-assessment
  test also checks removal of child steps. Production constraints were not relaxed to accommodate test setup.
- Gate C: the owner's selected verifier virtual environment lacks openapi_spec_validator. The dependency is already
  declared in the development extra of services/math-verifier/pyproject.toml. The owner must install that extra
  into services/math-verifier/.venv before rerunning; no dependency installation was performed by the agent.
- Gate E: blocked by the failed backend prerequisite; the source scan reported no findings. It requires fresh
  successful backend evidence and must not be promoted from the source scan alone.
- Gate F: the Sprint 7.1 trace table repeated REQ-ATTEMPT-001 and REQ-DATA-001 as new definitions. Its requirement
  cells now explicitly reference the existing IDs. Canonical requirement definitions and uniqueness checks remain.
- The owner runner now prints its generated summary and blocked-gate prerequisite messages before the evidence
  path so a completed invocation cannot be mistaken for successful acceptance.

The agent inspected the owner's existing logs and authored these repairs without running tests, builds, contract
validators, acceptance tooling or migrations.

## Full owner rerun and verified local acceptance — 2026-11-05

Owner evidence: .generated/sprint-7-1-owner/20261105-151510-r9cSGm/summary.json.
Source fingerprint: e5469065bd883a096404dd20b8879bfe1d6909360eee784e9e4154a72ae531de.
The full rerun verified complete source integrity (sourceUnchanged: true) and completed cleanup.
Overall outcome: PASS (LOCAL_ACCEPTANCE_PASSED). Mode: FULL.
Every gate executed and passed:
- Gate A (Preflight source/migration inventory): PASS.
- Gate B (Backend Maven suites & PostgreSQL Testcontainers): PASS (131 executed, 0 failed, 0 missing, 0 skipped).
- Gate C (Versioned contract & OpenAPI 3.1 validation): PASS (PyYAML & openapi-spec-validator executed cleanly).
- Gate D (iOS XCTest unit & UI suites): PASS (Xcode simulator executed, all attempt submission & recovery suites passed).
- Gate E (Privacy & Security hardening): PASS (backend prerequisites satisfied, zero secrets found in source scan).
- Gate F (Documentation governance): PASS (docs_check passed, canonical requirement links verified).
- Gate G (Deterministic verifier regressions): PASS (240 executed, 0 failed, 0 missing, 0 skipped).
- Cleanup: PASS.

Local acceptance is complete and verified on the current source fingerprint.
Remaining release gates (ADR-011 50-student TestFlight gate, hosted CI, physical-device VoiceOver) remain external.
