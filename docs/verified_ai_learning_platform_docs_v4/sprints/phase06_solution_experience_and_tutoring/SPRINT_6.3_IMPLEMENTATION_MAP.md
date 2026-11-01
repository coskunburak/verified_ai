# Sprint 6.3 — Implementation map

Status: `LOCAL_ACCEPTANCE_PASSED` in owner run `20261101-172742-BCuatU`:
154 iOS tests and 85 backend tests passed, zero failures/skips; Gates A–D passed.
Hosted CI `NOT_RUN`; production release `BLOCKED`. The agent did not execute tests.

## Baseline and scheduling exception

- Workspace started clean on `main`, HEAD / locally available `origin/main`:
  `6017ec45e0cffeb90d8b5bdbd9395bb3d47e91f8`. No remote fetch was performed.
- This legitimately follows the prompt reference `a1df27382167bea012792ff1a2ebf8e6198b1c29`.
- Sprint 6.2 recorded local acceptance: `20261031-184154-aBdvps`, 134 iOS tests,
  9 backend/evaluation suites. Hosted CI unconfirmed; production blocked.
- Owner directs local Sprint 6.3 development ahead of the V6 Track B schedule.
  ADR-011 and 6.2B/C/D commercial-wedge gates remain in force. This exception
  expires at local implementation handoff and grants no release authorization.
- Creating `codex/sprint-6-3-explanation-depth` was denied by the sandbox's
  read-only Git metadata boundary. Work remains uncommitted on `main`.

## Implementation boundary and owners

1. **ProblemSolving**: pure `ExplanationPresentation`, using validated candidate v1
   and the existing `ExplanationDepth`; no math evaluation or verdict generation.
2. **Onboarding/profile**: existing `OnboardingViewModel` owns accepted profile state;
   add temporary selection/save state and a depth-only request through existing
   `LearningProfileAPI`. Serialize/coalesce intentional changes with `expectedVersion`.
3. **RootView**: inject the shared profile owner into solution models; bind bootstrap
   to authenticated account, invalidate all asynchronous state on reset.
4. **PrimarySolveView**: four accessible localized choices, complete-step recovery,
   beginner disclosure and deep dependency inspection. Retain evidence hierarchy.
5. **Backend**: existing partial PATCH, ownership, active-account check, version lock,
   validation and deletion contributor are sufficient. Add regression tests only.

## Semantics, invariants and limitations

- QUICK: last original step and its explanation; explicit condensation notice and
  complete ordered sequence available on demand. One-step candidates stay honest.
- STANDARD: complete original claims, explanations, categories and dependencies.
- DEEP: complete sequence plus directly referenced original dependency claims;
  explicitly no extra conceptual derivation in candidate v1.
- BEGINNER: all claims remain visible; explanations expand individually. Bounded
  terminology notes for arithmetic expressions and equations only, never proof.
- Unsupported supplemental context falls back explicitly to original content.
- Final answer, all source/parse lineage, step IDs/order/claims/dependencies,
  restrictions, ambiguities and unresolved issues remain unchanged. Verification
  policy, proof scope, `EXPLANATION_STEPS_UNCHECKED` and recovery remain server-owned.
- Restriction references lack resolved prose in v1: retain the existing explicit
  unavailable-references warning in every mode. Do not manufacture restrictions.

## Profile lifecycle and recovery

Accepted `LearningProfile` is the only persisted authority. No UserDefaults cache.
STANDARD is the explicit fallback without a known preference. Temporary selections
are distinguished from saving/saved/unsaved. Conflict or uncertain failure requires
read-only profile reload before another deliberate save. Authentication/authorization
errors cannot silently retry. Closing/backgrounding cancels presentation work;
generation/account guards reject delayed responses. Reopening reloads when needed.
Already displayed valid content can change modes offline; existing solution freshness
rules still prevent offline reopening from reviving stale authoritative evidence.

## Contracts, security and cost

No backend production edits, endpoint, schema, migration, prompt, routing or verifier
changes. No credentials or real learner data. Logs contain fixed event categories only.
Zero AI, solver, secondary-solver or verifier calls per switch. Profile reads/writes
alone may occur; rapid selections coalesce behind one in-flight PATCH. No telemetry
contains account IDs, profile payloads, expressions or candidate content.
Model evaluation is not triggered by this presentation-only change. Future EXPLAIN
artifacts require separate versioned/schema-validated, on-demand budget approval.

## Localization, accessibility and rollout

Reuse String Catalog EN/TR labels, design tokens and mathematical rendering. Vertical
controls wrap at accessibility sizes; active choice has selected trait/value. Disclosure
has stable identifiers and no animation dependency. VoiceOver device review remains
an owner gate; translations/terminology require owner pedagogical review before release.
Existing solve feature gates and production restrictions remain unchanged. Rollback
removes presentation wiring without data migration; existing profile enum values remain
compatible. STANDARD remains the safe fallback.

## Tests and evidence

Author pure projection, profile serialization/concurrency/failure, no-solve-side-effect,
synthetic UI and backend partial-PATCH tests. Extend the existing Xcode targets explicitly.
Owner runner: `scripts/quality/sprint_6_3_owner_acceptance.sh`; evidence under
`.generated/sprint-6-3-owner/latest/summary.json`. No agent-run build, lint, tests,
validation, acceptance or paid evaluation. Final file inventory and gate status are
recorded in `SPRINT_6.3_EXECUTION_REPORT.md`.
