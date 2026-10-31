# Sprint 5.11 — implementation map

## Ownership and baseline

Verification owns deterministic arbitration, immutable decisions and the read projection.
Solving exports bounded secondary corroboration through its application boundary; it never
assigns a verification verdict. Problem owns selected source and owner locking. Existing
Sprint 5.10 local work is preserved. HEAD and local origin/main: 66f00200be48f24f5f690ff32fc0bd376ad952e2.

## Planned slice and acceptance boundary

Pure versioned policy consumes current typed source, canonical solution, authoritative plan,
immutable exact run and validated secondary snapshot. Reuse composite-evidence-v1 for generic
claims and composite-calculus-evidence-v1 for source-scoped calculus claims. No provider
calls, numeric execution or cost reservations. A separate V023 decision table references
immutable runs with owner/source/solution integrity, append-only history and owned cascades.
Short publication transaction rechecks source, plan, evidence and secondary snapshot.
Canonical reads finish before source locking. Public GET by primary solve job is read-only;
POST evaluation accepts only an owned immutable run key and UUID idempotency key.
iOS displays a separate status, checked/unchecked scope, contradictions and recovery advice.
Generation state remains independent. No automatic retry or paid escalation.

## Release and limits

Publication defaults disabled. Promotion requires local/test profile plus explicit owner
acceptance; production remains disabled. Calculus composition is implemented as a separate
versioned adapter; complete local owner acceptance passed in run 20261031-014130-mAGAKm.
Calculus claims retain their open-interval,
antiderivative-representative or punctured-neighborhood scope; explanation steps are always
unchecked. Rollback disables flags and retains rows.
Owner alone executes all A–H gates. New policy, persistence, API, iOS and architecture tests
must be authored; no tests, builds, linters or validators are run by the agent.

## Implemented components and provenance

- VerificationDecision: immutable verification-decision-v1 / verification-policy-v1 with
  source identity, solution representation/normalizer, run/request/plan IDs, capability,
  evidence IDs, sorted secondary references/signals, scope, coverage, uncertainty and recovery.
  Algorithm/method proof versions resolve through the immutable run; no mathematical payload
  is copied. Public projection excludes owner ID, internal request key and secondary payload.
- VerificationPolicy: reuses CompositeEvidencePolicy and its request/result validation.
  Priority: invalid/stale/review/unsupported composition; source feasibility; exact disproof
  or conflict; secondary reconciliation; incomplete coverage/failure; release gate; final
  claim promotion. Stable enum ordering and sorted references make repeated inputs reproducible.
- VerificationRecoveryPolicy: pure advice, maximum three attempts and existing run's 300-second
  window; malformed responses remain terminal, unsupported capability is not an outage.
- VerificationArbitrationService: canonical reads first, owner-authorized preparation,
  evaluation without locks, short publication with exact snapshot recheck. Latest primary
  replacement, latest evidence, source correction, deletion and secondary completion invalidate
  publication/currentness. Same-key retries reauthorize and reuse only matching fresh scopes.
- SecondaryCorroborationQuery: solving application boundary, at most 17 rows read to detect
  overflow, at most 16 reference IDs, strict known agreement/comparison versions and approved
  independence. Overflow or malformed evidence fails closed. Pending independent work blocks
  promotion; the service does not start it.
- Decision repository/V023: bounded JSON, owner/run composite FK, provenance insertion trigger,
  append-only triggers, current lookup index and owned cascade. Attempts query caps at three.
- Public controller: authenticated GET by primary job and POST existing run evaluation. No new
  evidence execution/retry/inference endpoint. Existing generation response is unchanged.
- iOS: GET port and separate display, strict decision vocabulary, exact source matching,
  checked/unchecked methods, contradiction warning, explicit unchecked steps, localized TR/EN
  copy, cancellation/generation guards and no cached verdict on offline/background transitions.

## Security, telemetry, cost and rollback

No additional model/provider call, routing policy, entitlement bypass, usage reservation,
secondary dispatch, prompt or training-eligibility change. Read operations are free of durable
mutation. Evaluation records request/status/reason/coverage/contradiction/uncertainty/recovery
and latency metrics using bounded labels. Idempotency and stale publication rejections have
bounded counters. Actual performance and cost baselines are pending owner evidence; no
latency or currency measurements are invented. Incremental inference invocations: zero by
construction, to be confirmed by architecture/integration tests.

Feature flags: app.verification.arbitration-enabled and arbitration-owner-accepted default
false. Enabling arbitration outside app.environment local/test is rejected even if owner
acceptance is true. Rollback sets arbitration-enabled false, returning null current projections,
and retains V023 and all historical rows for export/deletion. Never rewrite old evidence or
run a destructive down migration. iOS retains its existing generated-unverified fallback.

## Acceptance matrix and limitations

| Gate | Owner execution / authored coverage |
|---|---|
| A | Pure policy matrix: full, partial, no proof, disproof, numerical/secondary non-proof, unknown versions, source review, calculus block |
| B | Stable ordering, bounded recovery, budget/rate classification, idempotent publication, no automatic inference |
| C | PostgreSQL V022→V023, unique publication, ownership, insertion integrity, parallel publication, correction/deletion/replacement races, export/delete/rollback |
| D | Public auth/null/key contracts, strict shared JSON schema fixtures, malformed/unknown/missing fields, privacy allowlist |
| E | iOS contract/model/UI tests: states, conflict, unavailable/offline, late response suppression, recovery and accessibility |
| F | Existing Modulith/provider/privacy suites plus arbitration dependency rules |
| G | Full Maven verify, Linux verifier pytest, simulator XCTest; paid Qwen intentionally SKIPPED_BY_POLICY |
| H | Lint/docs/contracts/secrets, scanner/prompt regressions, released byte immutability, Git inventory |

The owner script runs these suites, preserves reports and fails closed on skipped required
backend suites. The calculus policy suite and named real-worker/persistence/API/race cases
are required before calculus_composite can report PASS. integration_dependency reports PASS
only after all A–H gates pass. The previous unconditional exit 2 is removed.

Calculus source-backed evidence is composed by CalculusCompositePolicy. Its domain,
representative/limit scope, provenance and failure rules are described in the closure
checkpoint below. Complete local owner acceptance passed on 2026-10-31; see the final checkpoint.
Optional numeric diagnostics remain ephemeral: the pure policy handles bound diagnostics,
but durable arbitration accepts exact runs only. No automatic sampling is introduced.
The policy does not verify explanation steps, expand mathematical executors or expose
secondary/private solution content. Mutation publication is primary-only; secondary evidence
is corroboration. Existing canonical worker/evidence execution activation remains independent.

Secondary read validation also rejects missing/null creator fields, boolean coercion, invalid
step counts, and missing completed evidence. Invalid input classification takes precedence
over retry exhaustion. These refinements have authored tests but no agent-run validation.

## Owner acceptance repair — 2026-10-30

Owner Linux verifier: 317 passed, no skips. Backend stopped in testCompile before tests:
two AssertJ assertions around generic TransactionTemplate.execute needed explicit
Optional<VerificationDecision> locals. Test-only repair preserves both historical-value
assertions and all production semantics. Full owner rerun remains required; iOS/quality
have no execution evidence yet. No agent test/build/validator execution.

## Owner acceptance SQL repair — 2026-10-30 12:51 Europe/Istanbul

The second owner run passed Linux (317 tests) and both Java compilation phases, then failed
on decision insertion: V023 mixed JSON extraction and containment without explicit operand
grouping, causing PostgreSQL `boolean -> unknown`. Both evidence-ID containment operands
now parenthesize JSON extraction. Existing valid-publication and forged-lineage integration
cases cover this path; all checks await the owner rerun. Backend reported one error and 578
skips under fail-fast, not a pass. No agent test/build/SQL/validator execution; no released
migration changed. Evidence: `.generated/sprint-5-11-owner/20261030-125041-xThc5z`.

## Owner acceptance UI repair — 2026-10-30 13:21 Europe/Istanbul

Evidence: `.generated/sprint-5-11-owner/20261030-131313-2nTdrU`. Linux passed 317;
backend passed with 654 cases, zero failures/errors and two policy skips. iOS unit tests
passed 110; UI tests had one failure among 11 at the controlled QUEUED-to-RUNNING stimulus.
The existing screenshot/hierarchy remained QUEUED after the toolbar tap; controlled-fixture
ViewModel tests passed. Exact native event-dispatch cause remains unproven. Move only the
DEBUG controlled advance button into scroll content with a minimum 44-point hit area and
explicit displayed-model capture; target it within scroll views in the UI test. Preserve
all transition assertions and timeouts. No agent tests/builds/validators ran; only existing
result attachments were read/exported. E awaits owner rerun, G/H remain NOT_RUN.

## Latest owner acceptance — 2026-10-30 14:13 Europe/Istanbul

OWNER_VALIDATED_IMPLEMENTED_SCOPE: owner A–H, prerequisites, backend and Linux all PASS.
Evidence: `.generated/sprint-5-11-owner/20261030-140545-EIykOa`. Linux 317 passed; backend
654 cases with zero failures/errors and two policy skips; iOS 110 unit and 11 UI passed.
Quality and immutability checks passed. The previous UI failure is resolved in owner evidence.
Earlier pending/failing entries above are historical checkpoints.

Overall BLOCKED / exit 2 is the explicit missing calculus-composite prerequisite, not a test
failure. Full scope remains PARTIAL_BLOCKED_BY_SPRINT_5_10, hosted CI NOT_RUN and rollout
DISABLED. This follow-up only records evidence in documentation/manifest; these metadata
edits have not been revalidated. No agent tests/builds/validators or release actions ran.

## Calculus dependency closure — 2026-10-31

Current state: IMPLEMENTED_PENDING_OWNER_VALIDATION. Historical 2026-10-30 generic owner
passes and BLOCKED summaries above do not validate the new calculus integration.

CompositeEvidencePolicy dispatches calculus to CalculusCompositePolicy, which emits the
separate composite-calculus-evidence-v1 assessment. It validates exact source/candidate/run/
plan lineage and reconstructs the full expected calculus metadata before accepting any
certificate. Domain coverage follows only exact worker certificates after original-domain
checks; numerical diagnostics and all nonproof outcomes cannot promote a decision.

VerificationPolicy handles both evidence variants, preserving exact contradictions,
secondary conflict, bounded recovery and local/test release approval. VERIFIED calculus
has exactly one method and OPEN_INTERVAL, ANTIDERIVATIVE_REPRESENTATIVE or
PUNCTURED_NEIGHBORHOOD scope. Generic FINAL_ANSWER, mixed-method claims, integral-family
completeness and explanation-step verification are excluded. The unreleased public decision
schema, shared fixtures, iOS decoder and localized disclosures enforce the same scope.
No existing released math/canonical schema or migration is changed; no new inference route
or live v2 candidate rendering capability is claimed.

CalculusCompositePolicyTest and new real-worker/persistence/HTTP/privacy/currentness/race
cases in CalculusProductionIntegrationTest are required by the owner script. Python/iOS
contracts and the UI scope test cover the projection. The old unconditional dependency
BLOCKED exit is replaced by evidence-based completion after all A–H gates. Missing/skipped
cases fail. All closure checks are NOT_RUN_BY_AGENT; hosted CI NOT_RUN; rollout DISABLED.

## Owner calculus pass and UI dependency repair — 2026-10-31 01:35

Owner evidence `20261031-012727-vjmltT`: Linux 338 passed; backend 663 cases with zero
failures/errors and two policy skips; calculus_composite PASS; iOS unit 111 passed and
calculus scope UI passed. One of 12 UI tests failed because a fully local verification
fixture received a real offline network update. E failed; G/H/integration_dependency NOT_RUN.

AppDependencies now selects a constant reachable monitor only for the explicitly enabled
DEBUG solve/history fixture pair; RootView injects it into those two flow models. Live
network guards and other features remain unchanged. Offline-host progression and non-fixture
fallback tests are authored, not executed. Full owner rerun remains required; no agent tests,
builds or validators ran. The existing failure screenshot/hierarchy was inspected directly.


## Complete local owner acceptance — 2026-10-31 01:50 Europe/Istanbul

OWNER_ACCEPTANCE_PASSED: `.generated/sprint-5-11-owner/20261031-014130-mAGAKm/summary.json`
records overall PASS / exit 0, all A–H gates PASS, calculus_composite PASS and
integration_dependency PASS. Linux: 338 passed. Backend: 661 passed plus two intentional
paid-model skips. iOS: 113 unit and 12 UI tests passed, including the previous UI failure
and both fixture-network regressions. Quality and released-byte immutability gates passed.
This supersedes earlier pending/blocked/failed checkpoints. Paid models remain
SKIPPED_BY_POLICY, hosted CI NOT_RUN and production rollout DISABLED. The agent only read
owner evidence and refreshed documentation after this run; that documentation refresh has
not been revalidated. No implementation change, agent test, commit, push or deployment.
