# Sprint 5.10 — implementation map

## Verified baseline and authority

2026-10-30: repository coskunburak/verified_ai, root
`/Users/burakcoskun/Desktop/ai_verification_tutor`. Original clean branch
`codex/sprint-5-9-production-integration`, HEAD
`94a29a6d1fd44e628759d3d7c737d002b286e626`. Remote main fetched and verified at
`66f00200be48f24f5f690ff32fc0bd376ad952e2` (PR #35); its tree equals original HEAD.
Implementation branch: `codex/sprint-5-10-composite-verification`.
PR #34 and #35 are ancestors. No applicable AGENTS.md found. All existing ignored
owner files (.env, environments, generated logs, IDE and build artifacts) preserved.

The supplied predecessor description is historical: PR #35 adds source-backed calculus
integration. Its closure report records owner local acceptance, but production rollout
remains disabled. This sprint does not reinterpret that as permission to enable calculus.

## Design and ownership

Verification owns a pure, ephemeral `composite-evidence-v1` assessment. Its policy
revalidates the authoritative planner against the current typed source and immutable
candidate, then binds every method result to one plan/run/source/candidate version.
Original domain restrictions survive cancellation. Domain checks and equation completeness
are separate required obligations. Unknown domain or contradictory constraints cannot
produce complete support. Exact contradiction is retained ahead of numerical agreement.
No product VERIFIED state, arbitration, retry policy, UI, prompt, or provider operation.

The additive `numeric-diagnostic-v1` / `numeric-diagnostic-result-v1` contracts reuse
canonical typed AST grammar. `equivalence-binary64-v1` attempts at most 16 deterministic
points on a 1/16 grid in [-8,8], requires four stable admissible evaluations, uses absolute/relative tolerances
1e-10, and reports only bounded counts and enum reasons. No proof field exists. Original
expressions are evaluated without cancellation; exact rational restrictions filter points.
INTEGER/UNKNOWN, unsupported restriction functions, and relations are conservative non-proof
outcomes. SHA-256 over versioned mathematical input supplies reproducibility, excluding
execution/correlation/scope IDs. Raw points and input digests are not returned or logged.

## Integration and persistence

Python uses the existing shared spawn supervisor, authentication, preparse request bound
and finite telemetry. Java uses the existing bounded HTTP/1.1 transport and a separate
numeric application port. Numeric activation requires explicit local/test flags and Java
owner acceptance. No numeric planner approval or learner-path invocation is introduced.
The pure composite may be used independently of rollout; callers must obtain current
owned snapshots through existing application authorization before invoking it.

No durable state, migrations, public schemas, exports, deletion contracts or historical
five-method aggregate changes. Composite output is a recomputable internal projection;
numeric diagnostics are ephemeral. Released exact/calculus v1 schemas stay byte-identical.
No calculus composite adapter: its scoped evidence is deliberately rejected by this v1
policy, preserving the independently versioned calculus boundary.

## Budgets, cost and rollback

Unchanged: 3 s worker elapsed, 2 s CPU, 512 MiB Linux address space, two shared worker
slots without queue, 64 KiB request, 16 KiB response, depth 32, 120 nodes/expression,
1200 total nodes, function depth eight, integer powers <=12 and 1024-bit fractions.
Numerical evaluation additionally bounds magnitudes, cancellation/error estimates,
near-zero denominators and function arguments. Composite is bounded to five exact methods
and one optional diagnostic; it performs no network work. Automatic cumulative learner
execution remains unchanged (zero new calls); future orchestration must budget optional
sampling and skip it when unnecessary. Incremental provider inference cost is zero.
Rollback: leave numeric flags false and stop invoking the ephemeral policy; retain historical
records. Calculus flags and production guards remain unchanged.

## Owner acceptance matrix

A/B/D/G: full Linux verifier suite (mathematics, shared schemas, authenticated routes,
spawn deadlines, admission and resource isolation). C/D/E/F/G: full backend suite with
VERIFIEDAI_MATH_WIRE=true and a provisioned Python interpreter, covering composite domain,
numeric HTTP, existing PostgreSQL authorization/concurrency/immutability/lifecycle and
architecture regressions. H: lint, docs, contracts, secrets and released-version governance.
No duplicate full suites; no paid inference. Tests authored but NOT_RUN_BY_AGENT.
Acceptance requires all owner gates and applicable hosted CI; production activation is a
separate decision. Initial status OWNER_VALIDATION_PENDING.

## Implemented boundary refinement

`CompositeVerification.current` is the authorized ephemeral application read adapter. It
reuses the existing planning service for parent eligibility, then rechecks the fresh plan
under the source-owner transaction. It accepts an optional typed diagnostic already bound
to that run; it never executes diagnostics or changes historical state. Finite assessment,
coverage, numerical-state/conflict, unsupported/failure-count and latency metrics are emitted.

`CompositeSourceScope` independently checks constant/affine real/integer restriction
feasibility using bounded exact rationals, before interpreting candidate domain failure.
Nonlinear/unknown feasibility is unresolved. This is an additional composition obligation;
method proofs are retained unchanged. Exact domain-proof/domain-disproof conflicts are
explicitly distinct from ordinary root-soundness versus incomplete-root-set evidence.

Minimum controlled acceptance activation: local/test Java numeric-enabled and
numeric-owner-accepted plus Python MATH_VERIFIER_NUMERIC_ENABLED. Numeric planner approval
is deliberately absent. Production requires a separately reviewed release change; neither
these flags nor owner acceptance bypass the production guard. Calculus flags remain false.

## Owner prerequisite repair — 2026-10-30

First owner execution stopped at prerequisites with exit 1 and no test output. The
handoff now includes the existing bundled rg directory, explicit prerequisite diagnostics,
ERR line/stage reporting and priority for the existing Python 3.11 interpreter when
provisioning the isolated owner environment. Runtime source is unchanged; all owner gates
remain pending. See the execution report for the retained failure log and complete block.

## Owner backend assertion repair — 2026-10-30

Owner Linux suite: 283 passed. Maven compiled then failed the new composite integration
assertion because its source domain is UNKNOWN; policy correctly returns INCOMPLETE_EVIDENCE.
Correct the test's assumed category and assert unresolved domain, retained timeout failures
and no exact certainty; add the matching pure policy regression. Production semantics are
unchanged. Retain Linux evidence and rerun full backend plus Gate H. Maven's fail-fast
skips are not passing backend evidence. Owner validation remains partial/pending.

## Owner backend acceptance and lint repair — 2026-10-30

Owner Maven verify: BUILD SUCCESS, 616 passing tests and two intentional paid-model
skips (618 reported), zero failures/errors. Required-suite validation passed. Retain
Linux 283 passing tests. Gate H stopped on one import-layout finding in numeric_sampling;
manually wrap two long imports without semantic changes. Only Gate H remains for local
acceptance; no agent validation. Hosted CI and production rollout remain separate/pending.

## Owner local acceptance — 2026-10-30 01:17:36 Europe/Istanbul

Owner Gate H exited 0 in `20261030-011736-XMCp8w`: lint, docs (300 Markdown files),
contracts, secrets, five immutable-prompt units, seven scanner units and unchanged
released contracts/migrations/fixtures passed. Retain Linux 283 passed and backend
616 passed/two intentional paid-model skips. All local A–H gates have owner PASS across
these checkpoints; no agent validation. This evidence-only documentation refresh awaits
a final docs-check, which may close from its owner log without another document edit.
Hosted CI remains NOT VERIFIED; production rollout stays DISABLED.
