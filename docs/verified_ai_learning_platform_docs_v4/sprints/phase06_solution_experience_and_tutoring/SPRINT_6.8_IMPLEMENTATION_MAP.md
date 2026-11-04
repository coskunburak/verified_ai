# Sprint 6.8 implementation map

Status: LOCAL_ACCEPTANCE_PASSED. Owner acceptance run `20261104-152542-vwl6JJ` (exit code 0; all gates PASS). Baseline inspected: main at
fd7e0c3aae1a4b8154e71d628bd1a9bd47688fc9, clean workspace. No agent-run tests/builds.

## Design and ownership

- Extend the attempt learning worker, controller/service and iOS feature. Preserve v1 contracts and immutable V031 evidence.
- Add method-comparison-v2 request/result contracts, explicit exact proof scope and deterministic comparison policy. No inference calls.
- Attempt route negotiates v2 through a versioned body; empty body retains v1. Source route accepts only exact canonical/parse identity.
- v2 is an ephemeral source-authorized comparison, never a learner verification or canonical-solver verdict. No new table/migration; no artificial attempts.
- Authorize owner/current source in short transactions before and after isolated bounded execution. Deterministic source/version execution identity; retries recompute, no durable cache or duplicate artifacts. Rate limit and verifier admission bounds remain active.
- Python owns mathematics, Java owns source/access and publication, Swift owns localized presentation. Native math renderer reused.
- Full/compact presentation shares outcome, proof and domain conditions. Step counts describe displayed algebra, never measured speed or learning outcomes.
- Rollback: default-off attempt flag; old empty-body v1 requests and stored v1 artifacts stay readable. No production activation authorized.
- Acceptance: owner-only runner covers schemas/docs, verifier/property/golden, Java/Testcontainers privacy and lifecycle, iOS unit/UI plus regression. Missing prerequisites and skipped tests block acceptance.

Detailed source manifest, mathematical boundaries and evidence matrix are appended with implementation.

## Implemented mathematical boundary

| Family | Conditions and proof | Refusal / presentation |
|---|---|---|
| Factoring | Rational quadratic, nonzero leading coefficient, one or two exact real roots; factor equation and complete root set independently compared on source scope | No card for negative discriminant; repeated-product and square derivations deduplicate |
| Completing square | Exact normalized square identity, complete real outcome, including repeated root and empty real set | A duplicate factoring derivation is suppressed; no imaginary radical is displayed |
| Quadratic formula | Exact rational coefficients, nonnegative discriminant, both signs, filtered complete set | Negative discriminant uses square explanation only; no fabricated second card |
| Symbolic simplification | Rational polynomial expressions of degree at most four; exact irreducible factoring identity and identical natural domain | Already factored presentation yields NO_ALTERNATIVE |
| Polynomial expansion | Rational polynomial products of degree at most four; canonical expanded form collecting like terms | Already expanded presentation yields NO_ALTERNATIVE |
| Direct algebra | Linear equations ($ax+b=0$) and constant equations/identities ($0=0$, $c=0$); exact linear isolation and complete solution set ($\{r\}$, $\mathbb{R}$, $\emptyset$) | Higher-degree nonlinear equations use specific family solvers; degenerate quadratics normalize to linear |
| Rational clearing | Rational equations with polynomial numerator and denominator (degree $\le 4$); clears denominators while preserving natural domain exclusions | Extraneous roots falling on domain holes are eliminated; empty real set verified when all candidate roots are excluded |
| Absolute cases | Affine single-variable absolute value equations ($|ax+b|=c$); splits into piecewise sign branches with complete outcome | Non-constant targets, non-affine arguments and multivariate absolute values are refused |

The bounded variable vocabulary is one real variable named `x`; other symbol names are explicitly unsupported.
All source syntax is inspected before cancellation. Variable denominators are verified with explicit natural domain exclusions.
Single-variable $x$ real AST (`Source.variables: max_length=1`) contract boundary prevents representing auxiliary
multivariable systems (such as Vieta's system $r_1+r_2=-b/a, r_1 r_2=c/a$ or general substitution) faithfully without
schema violation; presenting Vieta without auxiliary variables would duplicate the factored form, so it is refused.
Transcendental functions, unsupported radical equations (e.g. $\sqrt{x} = -2$), infeasible restrictions, and
non-real/unresolved variable domains are explicitly and honestly returned as UNSUPPORTED. Source restrictions and
natural domain exclusions remain visible in both full and compact presentations and are applied to complete final root sets.
Radical roots are exact typed SQRT nodes.

Each candidate transition and source-to-final relation uses DomainConstraintGuard/compare under exact
source restrictions. Numeric probes cannot establish a method. Formula substitution AST remains distinct;
commutative/repeated factor structure is normalized without expanding every method into the same polynomial.
Full steps contain justified transformations; compact selects essential transformations and outcome,
leaving the original statement, restrictions and natural domain exclusions visible. No arbitrary zero-expression
multiplication/division or squaring is generated.

## Contracts, source and lifecycle

- Internal request/result: method-comparison-v2 / method-comparison-result-v2, at the existing attempt-learning endpoint.
- Evidence: verified-method-v2; comparison policy: method-comparison-policy-v2; presentation: method-presentation-v2.
- Public envelope: method-comparison-public-v2. Source-only request: method-comparison-source-v2 JSON schema (request version remains method-comparison-v2).
- The attempt route accepts only the exact version selector or the historical empty body. New source route accepts only version and four source identity fields. No client owner, proof, status or learner-verification input is admitted.
- Source-only uses canonical v3 AttemptSourceDocument or existing VerificationSourceReader for supported older canonical representations. UNKNOWN is not converted to REAL. Attempt-based scope may retain the separately recorded learner REAL confirmation.
- A source/version/owner-bound deterministic execution ID supports replay after a lost response. Different idempotency keys do not create new artifacts. This is read/generation idempotence, not a new mutation ledger. There is intentionally no shared or durable cache.
- Ownership/account/current source are checked before and after the worker; no transaction spans HTTP execution. Concurrent calls may recompute but cannot create conflicting stored records. Admission remains two HTTP calls per API instance plus isolated process bounds.
- Existing 16 KiB worker response limit is retained. Optional null AST properties are omitted on v2 wire serialization; Java compares source trees ignoring only absent/null optional properties. Unencodable or oversized evidence is unavailable, never a partial proof.
- Unsupported/no-distinct responses have zero methods. Worker failure, timeout, corrupt response and disabled generation map to safe 503; stale source to 409. Public freshness is CURRENT_AT_PUBLICATION, never a claim that later source edits cannot occur.
- v1 stored methods are untouched and remain readable/exportable/deletable through V031 privacy paths. v2 produces no durable learner artifact. trainingEligible=false is explicit on the public envelope.

## iOS flow and observability

PrimarySolveView opens SourceMethodComparisonView without entering learner steps. The existing learning
view model fetches fresh context, then requests exactly that revision. AttemptLearningPanel requests v2
against an existing attempt. Method picker, full/compact toggle, native math, per-step justifications,
prerequisites, advantages, limits and domain conditions are localized in Turkish and English. Submitted
learner text is verbatim and its line count is explicitly not equated to algebraic transformation count.
No intended learner technique is inferred. Busy gates prevent repeated taps; generation/account fences
reject late responses. Disappearance/background clears ephemeral cards; resumption fetches current scope.
No custom animation or WebView is introduced; Dynamic Type uses existing math/text layout and VoiceOver
uses semantic labels plus non-color-only evidence/status icons.

Finite outcome metric `attempt.methods.outcome` and duration timer `attempt.methods.duration` carry no
source content. Worker learning metrics admit the explicit NO_ALTERNATIVE outcome. Expected paid/provider
inference calls per action: zero. One bounded comparison request plus source context retrieval on the main
flow. No latency, learning gain or human completion-time benchmark has been measured.

## Authored test matrix (not execution evidence)

| Boundary | Owner-executed sources |
|---|---|
| Mathematics, domains, exact roots, ordering, compact, duplicates, malformed authority, worker failures | `services/math-verifier/tests/test_method_comparison_v2.py` plus all existing verifier suites |
| Deterministic golden | `evaluations/golden-datasets/methods/rational-method-comparison-v2.json` (18 synthetic cases across 8 method families), `scripts/quality/evaluate_method_comparison.py` |
| Strict contracts, legacy history, localization | `scripts/quality/check_method_comparison_contracts.py`, MethodComparisonEvidenceTest |
| BOLA/auth, default-off, source change, account removal, unlocked HTTP, concurrent replay, privacy | StudentAttemptControllerTest, StudentAttemptIntegrationTest, AttemptVerifierClientTest |
| Native render/legacy decode, scope, empty/single, retry, duplicate tap, stale/account cancellation, EN/TR | MethodComparisonTests inside AttemptAnalysisTests.swift; three new AttemptAnalysisUITests flows |
| Regression | Sprint 6.7 golden and verifier suites, selected verification/canonical/tutoring/privacy/Modulith backend suites, full iOS test run |

No migration was created and V030–V032 are unchanged. Testcontainers installs the existing chain only in
its disposable PostgreSQL container. The runner does not invoke Flyway against any owner database.

## Acceptance, rollout and remaining blockers

Owner entry point: `scripts/quality/sprint_6_8_owner_acceptance.sh --full` via bash. Each invocation creates
an independent evidence directory under `.generated/sprint-6-8-owner/`. JSON and Markdown summaries record
HEAD, before/after fingerprints, failed/skipped/missing tests, gate statuses and release separation.
Explicit --failed-only runs recorded failures and never-executed gate recovery even after source repair,
while recording fullCurrentSourceAcceptance=false. Without an explicit mode, only recorded failing cases are selected where safe; changed production/contracts/runner/
golden inputs force fresh full acceptance. Targeted repairs never claim full current-source coverage.
Known Linux-only resource-limit skips remain BLOCKED on macOS; Linux execution evidence is an external
release prerequisite, not fabricated by the macOS runner. Missing Docker/Java/Xcode/Python prerequisites
are blocked separately. Evidence directories are preserved.

Default-off attempt flags and existing production-authorization checks remain unchanged. Rollback disables
attempt access and/or restores previous source; no downgrade migration or data rewrite is needed. Owner
acceptance, hosted CI, Linux resource-limit execution, device accessibility/Dynamic Type and pedagogical
review are pending. Production activation and deployment require separate owner authorization.

## Exact source manifest

Paths are repository-relative and name every changed/created delivery file. No migration or historical dataset is changed.

| Path | Change | Ownership / purpose |
|---|---|---|
| `apps/ios/VerifiedAI.xcodeproj/project.pbxproj` | Modified | iOS build registration: comparison view and synthetic fixture |
| `apps/ios/VerifiedAI/App/AppDependencies.swift` | Modified | iOS: API, account-fenced state, native comparison and navigation |
| `apps/ios/VerifiedAI/App/RootView.swift` | Modified | iOS: API, account-fenced state, native comparison and navigation |
| `apps/ios/VerifiedAI/Features/AttemptAnalysis/Data/AttemptLearningAPI.swift` | Modified | iOS: API, account-fenced state, native comparison and navigation |
| `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AlternativeMethodComparisonView.swift` | Created | iOS: API, account-fenced state, native comparison and navigation |
| `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AnalyzeMyWorkViewModel.swift` | Modified | iOS: API, account-fenced state, native comparison and navigation |
| `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AttemptLearningViewModel.swift` | Modified | iOS: API, account-fenced state, native comparison and navigation |
| `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/CounterfactualTwinSheetView.swift` | Modified | iOS: API, account-fenced state, native comparison and navigation |
| `apps/ios/VerifiedAI/Features/ProblemHistory/Presentation/History/ProblemHistoryView.swift` | Modified | Direct native ready-for-solve navigation and explicit accessible interaction area |
| `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveView.swift` | Modified | iOS: API, account-fenced state, native comparison and navigation |
| `apps/ios/VerifiedAI/Resources/Localizable.xcstrings` | Modified | iOS localized English/Turkish comparison copy |
| `apps/ios/VerifiedAITests/AttemptAnalysis/AttemptAnalysisTests.swift` | Modified | iOS unit/contract tests |
| `apps/ios/VerifiedAIUITests/AttemptAnalysisUITests.swift` | Modified | iOS localized UI/retry/method selection tests |
| `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md` | Modified | Documentation inventory and exact affected line/byte counts |
| `docs/verified_ai_learning_platform_docs_v4/architecture/14_API_DESIGN_AND_CONTRACTS.md` | Modified | Canonical documentation, traceability and implementation evidence |
| `docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` | Modified | Canonical documentation, traceability and implementation evidence |
| `docs/verified_ai_learning_platform_docs_v4/quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md` | Modified | Canonical documentation, traceability and implementation evidence |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/PHASE_06_SOLUTION_EXPERIENCE_AND_TUTORING.md` | Modified | Canonical documentation, traceability and implementation evidence |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.7_IMPLEMENTATION_MAP.md` | Modified | Canonical documentation, traceability and implementation evidence |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.8_EXECUTION_REPORT.md` | Created | Canonical documentation, traceability and implementation evidence |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.8_IMPLEMENTATION_MAP.md` | Created | Canonical documentation, traceability and implementation evidence |
| `evaluations/golden-datasets/methods/rational-method-comparison-v2.json` | Created | Versioned synthetic method golden dataset |
| `packages/contracts/openapi/internal-math-verifier.yaml` | Modified | Strict versioned schemas and OpenAPI contracts |
| `packages/contracts/openapi/public-api.yaml` | Modified | Strict versioned schemas and OpenAPI contracts |
| `packages/schemas/fixtures/method-comparison-public-v2.json` | Created | Shared synthetic public fixture consumed by three runtimes |
| `packages/schemas/method-comparison-public-v2.schema.json` | Created | Strict versioned schemas and OpenAPI contracts |
| `packages/schemas/method-comparison-result-v2.schema.json` | Created | Strict versioned schemas and OpenAPI contracts |
| `packages/schemas/method-comparison-source-v2.schema.json` | Created | Strict versioned schemas and OpenAPI contracts |
| `packages/schemas/method-comparison-v2.schema.json` | Created | Strict versioned schemas and OpenAPI contracts |
| `scripts/quality/check_method_comparison_contracts.py` | Created | Owner-only acceptance, contracts, evaluation and evidence collection |
| `scripts/quality/evaluate_method_comparison.py` | Created | Owner-only acceptance, contracts, evaluation and evidence collection |
| `scripts/quality/sprint_6_8_evidence.py` | Created | Owner-only acceptance, contracts, evaluation and evidence collection |
| `scripts/quality/sprint_6_8_owner_acceptance.sh` | Created | Owner-only acceptance, contracts, evaluation and evidence collection |
| `services/api/src/main/java/com/verifiedai/attempt/api/AttemptLearningController.java` | Modified | Backend: source ownership, versioned transport and evidence validation |
| `services/api/src/main/java/com/verifiedai/attempt/api/AttemptRequestLimitFilter.java` | Modified | Backend: source ownership, versioned transport and evidence validation |
| `services/api/src/main/java/com/verifiedai/attempt/application/AttemptLearningService.java` | Modified | Backend: source ownership, versioned transport and evidence validation |
| `services/api/src/main/java/com/verifiedai/attempt/application/AttemptVerifierClient.java` | Modified | Backend: source ownership, versioned transport and evidence validation |
| `services/api/src/main/java/com/verifiedai/attempt/domain/MethodComparisonEvidence.java` | Created | Backend: source ownership, versioned transport and evidence validation |
| `services/api/src/main/java/com/verifiedai/attempt/infrastructure/http/HttpAttemptVerifierClient.java` | Modified | Backend: source ownership, versioned transport and evidence validation |
| `services/api/src/main/java/com/verifiedai/sharedkernel/contracts/VersionedJsonContract.java` | Modified | Backend: source ownership, versioned transport and evidence validation |
| `services/api/src/test/java/com/verifiedai/attempt/api/StudentAttemptControllerTest.java` | Modified | Backend tests: source/auth/privacy/replay and strict contracts |
| `services/api/src/test/java/com/verifiedai/attempt/domain/MethodComparisonEvidenceTest.java` | Created | Backend tests: source/auth/privacy/replay and strict contracts |
| `services/api/src/test/java/com/verifiedai/attempt/infrastructure/AttemptVerifierClientTest.java` | Modified | Backend tests: source/auth/privacy/replay and strict contracts |
| `services/api/src/test/java/com/verifiedai/attempt/integration/StudentAttemptIntegrationTest.java` | Modified | Backend tests: source/auth/privacy/replay and strict contracts |
| `services/math-verifier/app/api/routes/verify.py` | Modified | Verifier: versioned bounded comparison and dispatch |
| `services/math-verifier/app/api/schemas/method_comparison.py` | Created | Verifier: versioned bounded comparison and dispatch |
| `services/math-verifier/app/observability/metrics.py` | Modified | Verifier: versioned bounded comparison and dispatch |
| `services/math-verifier/app/policies/process_executor.py` | Modified | Verifier: versioned bounded comparison and dispatch |
| `services/math-verifier/app/verifiers/attempt_learning.py` | Modified | Verifier: versioned bounded comparison and dispatch |
| `services/math-verifier/tests/test_method_comparison_v2.py` | Created | Verifier tests: exact math, failure and contract regressions |
