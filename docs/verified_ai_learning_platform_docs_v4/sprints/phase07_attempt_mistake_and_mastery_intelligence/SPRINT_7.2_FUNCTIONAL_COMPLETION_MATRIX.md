# Sprint 7.2 functional completion matrix

Source: SOURCE_COMPLETE with failure repairs. Execution: PENDING_OWNER_TEST for repaired source.
Prior owner run 20261106-002946-Fm9j55: A/C/F/G/cleanup PASS, B/D FAIL, E BLOCKED.
B executed zero cases because compilation failed; D passed 245 cases and failed two structured-save UI cases.
The accessor and stale SwiftUI binding repairs have not been rerun. No row is VERIFIED_BY_OWNER for the current source.
ALREADY_IMPLEMENTED means reused existing behavior, not a new assertion that its regression tests ran.

| ID | Function / source owner | Implementation | Required current evidence | Execution |
|---|---|---|---|---|
| A | Reuse map, sole StudentAttempt aggregate, existing owner/source locks and final-answer-only history | ALREADY_IMPLEMENTED + SOURCE_COMPLETE additive map | Gate A inventory; legacy request/attempt regressions in B/C | PENDING_OWNER_TEST |
| B | AttemptWorkInput / AttemptStructuredWork: raw, reviewed, syntax, IDs/order, roles, uncertainty and provenance | SOURCE_COMPLETE | AttemptWorkNormalizerTest, AttemptWorkRequestTest; strict schemas and fixtures | PENDING_OWNER_TEST |
| C | AttemptWorkNormalizer → existing ManualStepNormalizer; original denominator/root/log/sign syntax retained; unsupported text preserved | SOURCE_COMPLETE | syntheticMathGoldensPreserveSyntaxWithoutProvingClaims; golden inventory and verifier regressions | PENDING_OWNER_TEST |
| D | Native/existing gateway OCR, original observations, exact gateway metadata checks, source image association | ALREADY_IMPLEMENTED extraction + SOURCE_COMPLETE provenance/review | AttemptWorkProvenanceTest; foreignGatewayAssetCannotBeUsedAndLeavesNoPartialAttempt; native correction XCTest | PENDING_OWNER_TEST |
| E | Typed POST/GET, strict UTF-8/duplicate/unknown input filter, ownership, stale source, bounded rates/capacity and same-key recovery | SOURCE_COMPLETE | AttemptWorkControllerTest; concurrentReplayRevisionConflictAndRollbackRecovery; staleSourceBolaAndDeletedAccountCannotPublish | PENDING_OWNER_TEST |
| F | V034 immutable derived artifact, atomic parent insertion, exact text equality, revision chain, export/deletion and V033 upgrade | SOURCE_COMPLETE | AttemptWorkIntegrationTest including upgradeFromV033PreservesHistoricalEvidenceWithoutBackfill; legacy V032 upgrade | PENDING_OWNER_TEST |
| G | iOS entry, source review, movement, manual split/merge, explicit final answer, server preview, unsupported/unconfirmed states | SOURCE_COMPLETE | AttemptStructuredWorkTests; EN/TR UI cases with accessibility content size | PENDING_OWNER_TEST |
| H | Protected pending/draft/native preview, owner/source/generation fences, canceled responses, retry, legacy decoding | ALREADY_IMPLEMENTED foundations + SOURCE_COMPLETE structured extension | Interrupted submission, source-bound draft, preview purge and canceled response XCTests | PENDING_OWNER_TEST |
| I | Privacy-safe metrics, training exclusion, no model/verifier calls, zero new inference spend | SOURCE_COMPLETE | Native/gateway tests; export/deletion and metric assertions; fresh Gate E backend prerequisite | PENDING_OWNER_TEST |
| J | Default-off flag, historical read during disable, source-aware owner runner, canonical docs and manifest | SOURCE_COMPLETE | Full current-source A–G run; fingerprints; evidence-writer adversarial regressions | PENDING_OWNER_TEST |

## External release conditions

| Condition | Status | Exit evidence |
|---|---|---|
| Java 21/PostgreSQL/Testcontainers, contract dependencies, Xcode/simulator and verifier suites | PENDING_OWNER_TEST | Fresh full Sprint 7.2 runner output with unchanged source |
| Hosted CI and physical-device VoiceOver | BLOCKED_BY_EXTERNAL_GATE | Owner-provided current-source CI/device evidence |
| ADR-011 and Phase 7 ≥50 real students / 30-day TestFlight Go/No-Go | BLOCKED_BY_EXTERNAL_GATE | Actual cohort/retention/false-verification evaluation and owner authorization |
| Production migration, rollout and paid model activation | BLOCKED_BY_EXTERNAL_GATE | Separate explicit production authorization; not enabled by source completion |

The owner-requested --failed-from repair run executes the unexecuted backend suite, only the two failed iOS methods,
source inventory and the blocked security dependency. Passing it does not promote historical passing gates or certify
the newly authored regression cases; those remain pending a future full run.

## Supported limits, not hidden implementation gaps

- Existing deterministic parser syntax is reused: explicit ASCII multiplication/functions/powers. Unicode superscripts,
  bars, implicit multiplication and unsupported calculus remain exact text with UNSUPPORTED, rather than guessed ASTs.
- Unconfirmed/ambiguous/multiline work can be durably saved as REVIEW_REQUIRED; it cannot become verified/mastery evidence.
- Native observations/coordinates are client-attested; capture UUID binds a protected local crop preview, not a server upload.
  Preview is bounded/local and can disappear on logout/reinstallation. Gateway bytes retain the existing asset lifecycle.
- Existing gateway coordinates have no declared origin; full-image review replaces an unjustified region overlay.
- Per-step AST bound is 1800 characters to retain a safe artifact envelope; unsupported complexity preserves original text.
- A new review revision cannot change original observations, engine, source or job/capture identity. A new source needs a
  new attempt with no predecessor. No historical v1/v2 data is reinterpreted or backfilled.

No in-scope source work is intentionally deferred as PARTIAL. Compilation, runtime correctness, acceptance and release
readiness remain unverified until the owner executes the authored suites and supplies evidence.
