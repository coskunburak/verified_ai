# Sprint 7.2 execution report

Started: 2026-11-05, Europe/Istanbul.
Source status: SOURCE_COMPLETE; failure repairs pending owner rerun. Owner acceptance: FAIL (prior source). Agent test/build execution: NOT_EXECUTED.
External production/TestFlight gate: BLOCKED_BY_EXTERNAL_GATE / NOT_VERIFIED.

Baseline and current Git branch: main. Baseline SHA: 8591761b0019396cde94a5cba5beb18bcd639eb7.
The initial tree contained owner changes in security scanning, AttemptMathParserV2, tutoring application/domain/store/tests,
and an untracked backend security acceptance runner. Those files were inspected only when directly relevant and not edited
by this sprint. No commit, push, reset, clean, rebase, merge, branch mutation, deployment or migration execution was performed.

## Delivered source

- Existing StudentAttempt aggregate/repository/source lock and ManualStepNormalizer are reused. New capture-only structured
  route stores accepted parent evidence and a versioned child artifact atomically, without verifier or learning publication.
- Strict typed request and response preserve original observations, corrected text, IDs, order, segmentation links, explicit
  final intent, review state, normalization state, engine, trust provenance and immutable source/revision lineage.
- Server-recorded gateway raw text/confidence/box/job must match existing owned evidence. Native metadata remains explicitly
  client-attested. No provider authority is accepted from clients; no additional inference route or dependency was introduced.
- V034 adds an immutable bounded JSONB artifact with owner/source/parent text checks, original-transaction membership,
  unique predecessor, revision guards, owner index and privacy cascades. V030–V033 and prior wire/hash meanings are unchanged.
- Additive context capability defaults off. Same-key replay and historical reads survive disable; source changes reject new
  publication. Privacy export includes structuredWork and account/source deletion reaches the complete artifact/revision chain.
- iOS integrates explicit structured entry, raw/reviewed text separation, source crop review, unconfirmed/unsupported states,
  step movement, learner-directed split/merge, final role, normalized native preview and revision creation. Protected pending
  requests preserve exact retry keys; drafts remain source-bound and late/account-switched responses cannot publish.
- Native crop previews are private, backup-excluded, keyed by owner/problem/capture, immutable locally and bounded to 2 MB × 30.
  Canceled review removes its unused preview; existing account purge clears them. Gateway unknown coordinate origin is exposed
  rather than guessed. EN/TR copy and accessible controls distinguish saved/structured from mathematical verification.
- Bounded metrics cover extraction, syntax outcomes, rejection, retry, conflict, persistence failure and duration without content.

## Test sources authored, not executed

Backend unit: AttemptWorkNormalizerTest (shared synthetic goldens, IDs/order, final intent, Unicode/limits, raw correction),
AttemptWorkProvenanceTest (gateway tampering), AttemptWorkRequestTest (strict schema/authority/coercion/duplicate boundaries).
Backend HTTP: AttemptWorkControllerTest (versioned output, legacy projection, auth/BOLA, stale source, invalid and oversized input).
PostgreSQL: AttemptWorkIntegrationTest (atomic capture, immutability, export/deletion, concurrent replay, revision conflict,
rollback/history, stale/deleted owner, native corrections, foreign gateway reference, V033 upgrade without historical backfill).
Existing Sprint 7.1 domain/HTTP/PostgreSQL/verifier and related solve/tutor/module regressions remain in acceptance.

Contracts: check_structured_work_contracts.py plus shared request/response/golden fixtures; strict positive/negative schema
mutations, frozen legacy schemas/migrations, OpenAPI responses, EN/TR and iOS fixture registration. Mathematical goldens
include implicit/Unicode unsupported notation, rational domain holes, potential lost roots, inequality direction, abs,
sqrt/log arguments, multiline fractions, parallel solutions, missing symbols/confusables, calculus and prompt-like text.
The assertions concern faithful evidence representation, not mathematical correctness.

iOS: AttemptStructuredWorkTests cover multi-step/final intent, raw OCR correction, ordering/split/merge, interrupted requests,
source/account isolation, byte/line bounds, shared decoding, unconfirmed multiline review transitions, localized accessible states, protected preview purge and canceled
responses. Two additional UI cases cover EN/TR structured save/unassessed state at accessibility content size.
Python: test_structured_work_golden.py and test_sprint_7_2_evidence.py, including missing/corrupt/skipped evidence, source change,
partial-run masquerading as full acceptance, old-suite-only results, simulator failure and security prerequisites.

No Maven, pytest, XCTest/XCUITest, Xcode/build, contract checker, acceptance runner, migration, Docker operation, model call or
package installation was executed by the agent. Targeted source/diff inspection, git diff --check and read-only inspection/export
of the owner's existing xcresult diagnostics were used. This is
not test, compilation, performance, runtime, CI or acceptance evidence. Prior Sprint 7.1 PASS is not Sprint 7.2 evidence.

## Owner acceptance

Entry: bash scripts/quality/sprint_7_2_owner_acceptance.sh --full.
Fresh output: .generated/sprint-7-2-owner/<run-id>/ with summary.json, summary.md, failure-inventory.json,
execution-metadata.json, per-gate logs/JSON, source-before/after fingerprints and migration inventory.
A source/migration inventory; B Java/PostgreSQL; C schema/OpenAPI/golden contracts; D full iOS unit/UI;
E fresh privacy/auth/abuse backend prerequisites plus allowlisted secret scan; F docs/manifest/traceability/diff;
G verifier regressions, structured goldens and evidence-writer tests. Missing tools/dependencies/simulator or unexpected
skips cannot become PASS. Targeted --gate runs do not constitute full acceptance. Isolated simulator resources are cleaned.
No prior run output is imported. No production configuration or secret store is read by source inventory.

## Owner failure diagnosis and repair — 2026-11-06

Owner run: .generated/sprint-7-2-owner/20261106-002946-Fm9j55.
Recorded source fingerprint: 521c5be27593fb715d8ce0a27580cd52a3bf0382633d7009361b31be50bbc1aa.
Source unchanged during that run: true. A/C/F/G and cleanup passed; B/D failed; E was blocked.
These historical passes are not acceptance evidence for the repaired source.

- B failed compilation before any backend case ran: SolveSource exposes canonicalProblemId(), not canonicalId().
  Corrected both AttemptWorkService and the same incorrect accessor in AttemptWorkIntegrationTest setup.
- D recorded 245 passed cases and two failed EN/TR structured-save UI cases. Both app crash reports show
  Array._checkSubscript → Binding subscript → ToggleState after acknowledgement clears the draft.
  Replaced indexed collection bindings with identity-resolved bindings. Retired controls read a safe snapshot;
  writes require an active, unlocked draft with the same source/predecessor and an existing step ID.
  Text/role edits invalidate confirmation synchronously; observation links remain draft-owned.
- Added two synthetic XCTest regressions for acknowledgement, delayed reads/writes, successor revisions, logout,
  reordering and removal. These new regressions are authored but not executed and are part of a future full D run.
- Added --failed-from to the owner runner for failed XCTest selectors and a backend suite that never executed.
  It selects A/B/D/E for this run: A is source inventory, B reruns the unexecuted backend suite, D runs only the
  two failed methods, E reevaluates its dependency using fresh B evidence. C/F/G and passed iOS cases do not run.
  Missing/invalid selectors fail closed. Selection-writer
  regressions are authored but not executed; a future full G run includes them.
- Fresh output includes repair-selection.json and ios-selected-tests.txt. All PASS evidence must come from the
  new run; historical output supplies selectors only. Success is TARGETED_REPAIR_PASSED, never full acceptance.

Owner repair command (repository root):

```bash
bash scripts/quality/sprint_7_2_owner_acceptance.sh \
  --failed-from .generated/sprint-7-2-owner/20261106-002946-Fm9j55
```

Repair runtime status: PENDING_OWNER_TEST. No test or build was executed by the agent after these changes.

Owner rerun 20261106-010418-ojHCIJ passed both selected iOS cases. Backend executed 148 cases with one failure,
ModularityTest.verifiesSpringModulithBoundaries. Gate E lacked complete fresh backend evidence.
AttemptExtractionReader now maps the internal recognition bounding box to a public extraction record in the existing
named interface. The wire fields and decimal values are preserved. The existing gateway provenance test checks the
serialized box shape and round trip.

The updated --failed-from runner selects only the failed backend method from this rerun. It records the limited scope
and requires matching fresh JUnit evidence. Gate E requires a fresh complete backend run.

```bash
bash scripts/quality/sprint_7_2_owner_acceptance.sh --failed-from .generated/sprint-7-2-owner/20261106-010418-ojHCIJ
```

The selector was checked against both owner reports. The module boundary repair awaits owner execution.

## Rollback and remaining verification

Keep V034 and the compatible reader. Disable ATTEMPT_STRUCTURED_WORK_ENABLED; do not erase artifacts or repair migration
checksums. Existing attempt production authorization and vision gates remain independently required. Historical read,
export, deletion and matching-key replay remain available while creation is off. Do not roll back to a reader that cannot
interpret current accepted evidence. Production deployment, TestFlight and paid inference are not authorized by this work.

Owner must execute the complete runner with Java 21, Docker/PostgreSQL Testcontainers, verifier development dependencies and
Xcode/iOS simulator available, review failures against the exact fingerprint, then supply current results. Device VoiceOver,
operational latency, hosted CI and ADR-011 ≥50-student/30-day TestFlight evidence remain external. No known source gap is
intentionally concealed behind the flag; SOURCE_COMPLETE does not certify runtime behavior or grant production approval.

The implementation map includes the complete sprint file inventory; the functional matrix separates implementation,
owner verification and external release conditions. Canonical domain/API/data/iOS/learning/quality documents and the
Documentation Manifest are updated only where this representation changes their semantics.
