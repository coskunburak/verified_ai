# Sprint 6.9 implementation map

Status: PARTIAL (core source implemented, F08 provenance highlighting unsupported); baseline main 59987b21650a598e39d666610064d0d726f38b0f, clean tree.
Owner exclusively executes tests, builds, validators and acceptance.

- Ownership: dedicated visualization backend module; problem supplies current source, verification supplies canonical reader. Python owns deterministic math; iOS owns presentation only.
- Ephemeral source projection, no StudentAttempt, persistence, debit, solve or verification mutation. No migration/deletion contributor because nothing durable is added.
- Contracts: separate request/result/source/public v1 with policy v1; exact sets/features separated from approximate samples. No verdict field.
- Scope: one explicitly real x; bounded rational polynomial expressions, residual equations, polynomial/rational derivatives, exact real sets/inequalities. Unknown scope and non-rational/transcendental syntax refused.
- Domain guard examines syntax before cancellation. Segment samples only within proven continuous domain components; finite viewport, bounded points, no cross-hole joins.
- Authorization: JWT and current owner/source before and after worker, short transactions, account deletion fence, source revisions, default-off independent flag.
- Resource: existing two-slot worker, 3 seconds elapsed, 2 seconds CPU, 512 MiB Linux memory, 16 KiB response. Zero paid inference, no training eligibility.
- iOS: native Charts, number-line endpoint semantics, semantic summaries, bounded viewport, cancellation/account fence, explicit retry, EN/TR.
- F08: reviewed phrase-to-relation provenance is absent from canonical-problem-v3. No inferred spans; explicitly unsupported pending a reviewed provenance contract.
- Acceptance: new independent full owner entry point, regression suites plus synthetic mathematical assertions, source fingerprints, skipped/missing inventories; hosted CI and production authorization remain separate.
- Rollback: disable visualization flags on API/verifier. Historical contracts and migrations remain unchanged.

## Delivered capability policy

| Family | Exact semantics | Display / refusal |
|---|---|---|
| Linear/constant/polynomial | Rational coefficients; numerator/denominator degree <=4 | Finite samples; exact finite roots where representable; affine slope and quadratic vertex |
| Rational | Syntax-derived denominator exclusions before cancellation | Separate series on each original continuous domain component, including removable holes |
| Equation | Residual left-right, complete canonical real solution set | Exact roots separate from samples; identity and empty real sets supported |
| Derivative | Symbolic derivative of eligible ambient rational function | Function/derivative toggle; original domain retained, no finite differences |
| Inequality | Canonical exact real relation set intersected with restrictions | Number line only, correct open/closed endpoints |
| Sets | Concrete finite sets, intervals, unions/intersections, empty/reals | Exact strings and canonical nodes; finite approximate positions and outward rays |
| Story bridge | No reviewed phrase/relation evidence in current canonical contract | PROVENANCE_UNAVAILABLE; V09 PARTIAL |

Natural domain, explicit source domain and solution set are separate fields. Original source AST remains internal.
Exact features are mathematical facts; plotted polylines and numeric positions are approximate display data.
No VERIFIED field exists. Unknown domains never become real by sampling. Unsupported intervals/roots refuse safely.

## API and authorization lifecycle

1. GET /api/v1/problem-sessions/{problemId}/visualizations/context returns owned current identity and availability.
2. POST same base path requires strict math-visualization-source-v1: source identity, type, viewport, resolution.
3. Short transaction locks active owner/session and fetches current canonical source; exact revisions must match.
4. Deterministic UUID includes owner/problem/source/policy/type/viewport/resolution. No replay persistence exists.
5. Authenticated internal POST /internal/v1/verify/visualization runs outside the transaction.
6. Strict result validation binds every identity/request field and sample envelope. A short second transaction
   rechecks active ownership/current source before CURRENT_AT_PUBLICATION. Responses use Cache-Control: no-store.
7. iOS fences both account session and request generation around awaits; disappearance/background discards graph;
   retry obtains current context rather than silently rebinding an old response.

## Resource, privacy and operational controls

- Public request bytes <=16384; internal <=65536; worker and HTTP response <=16384.
- Resolution 16..64 per curve, <=128 total; app uses 48. At most 24 number-line bands, 12 exact features.
- Viewport coordinates [-1000,1000], spans [0.01,2000]; finite JSON only, no arbitrary expression input.
- Natural-domain membership and whole adjacent intervals must be proven before joining samples. Steep jumps
  >one quarter of viewport height split. No interpolation accuracy or pixel-level completeness claim.
- Worker budgets unchanged: two concurrent slots, 3s elapsed, 2s CPU, 512 MiB Linux RLIMIT_AS; Java 4s HTTP/4.5s wait.
- API rate policy 20/minute, closed on rate backend failure. No database locks across worker HTTP.
- Metrics: visualization.requests outcome, visualization.reason reason, visualization.worker.failure reason,
  visualization.duration histogram; Python VISUALIZATION outcome/reason counters and elapsed totals. Labels finite.
- Logs contain correlation ID/outcome/duration only. Zero paid AI requests; no source content logged or exported to training.
- Latency buckets 250ms/1s/5s are operational observation targets, not measured service guarantees.
- Rollback on invalid-response/timeouts or source-binding failures: disable API/verifier visualization flags;
  inspect bounded metrics and correlation without raw math. Existing solve/attempt routes remain available.

## Test and acceptance strategy

Owner-only full entry point runs documentation/schema/OpenAPI/regression checks, Python suite and synthetic
26-case golden evaluation, disposable Testcontainers backend suites, all configured Swift unit/UI suites,
localization/registration/security static checks and source fingerprint reconciliation. No owner DB migrations,
production infrastructure, provider calls, Git edits or prior PASS reuse. Missing/skipped required evidence is
BLOCKED or FAIL, never invisible PASS. Generated artifacts are isolated under .generated/sprint-6-9-owner.
Linux isolation evidence, hosted CI, manual device/accessibility review and release authorization remain separate.

## Known limitations and follow-up dependency

- F08 phrase highlighting remains PARTIAL pending a reviewed phrase-to-variable/relation provenance contract,
  parser production path, versioned consumer validation and synthetic Turkish fixtures. Block IDs or mathematical
  token offsets alone do not meet that semantic requirement.
- No generic Euclidean geometry, absolute/transcendental/multivariate plotting, unsupported implicit sets or
  invented monotonic intervals. Unsupported result is visible and carries no graph.
- Device screenshots, broad Turkish UI flows, full Dynamic Type/VoiceOver execution and performance measurements
  require owner execution. The authored tests do not establish that those checks passed.

## Exact changed/created file manifest

62 source/document/fixture files; paths are repository-relative. Existing migrations unchanged.

- `.github/workflows/math-verifier-ci.yml`
- `apps/ios/VerifiedAI.xcodeproj/project.pbxproj`
- `apps/ios/VerifiedAI/App/AppDependencies.swift`
- `apps/ios/VerifiedAI/App/RootView.swift`
- `apps/ios/VerifiedAI/Features/AttemptAnalysis/Presentation/AlternativeMethodComparisonView.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveView.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveViewModel.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Visualization/MathVisualization.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Visualization/MathVisualizationView.swift`
- `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Visualization/MathVisualizationViewModel.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings`
- `apps/ios/VerifiedAITests/ProblemSolving/MathVisualizationTests.swift`
- `apps/ios/VerifiedAIUITests/MathVisualizationUITests.swift`
- `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md`
- `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md`
- `docs/verified_ai_learning_platform_docs_v4/architecture/14_API_DESIGN_AND_CONTRACTS.md`
- `docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `docs/verified_ai_learning_platform_docs_v4/ios/16_IOS_DESIGN_SYSTEM_AND_UX_ENGINEERING.md`
- `docs/verified_ai_learning_platform_docs_v4/quality/42_DEFINITION_OF_DONE_AND_ACCEPTANCE.md`
- `docs/verified_ai_learning_platform_docs_v4/quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/PHASE_06_SOLUTION_EXPERIENCE_AND_TUTORING.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.9_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.9_FUNCTIONAL_COMPLETION_MATRIX.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase06_solution_experience_and_tutoring/SPRINT_6.9_IMPLEMENTATION_MAP.md`
- `evaluations/golden-datasets/visualizations/bounded-real-v1.json`
- `packages/contracts/openapi/internal-math-verifier.yaml`
- `packages/contracts/openapi/public-api.yaml`
- `packages/schemas/fixtures/math-visualization-context-v1.json`
- `packages/schemas/fixtures/math-visualization-public-v1.json`
- `packages/schemas/fixtures/math-visualization-request-v1.json`
- `packages/schemas/fixtures/math-visualization-result-v1.json`
- `packages/schemas/fixtures/math-visualization-source-v1.json`
- `packages/schemas/math-visualization-context-v1.schema.json`
- `packages/schemas/math-visualization-public-v1.schema.json`
- `packages/schemas/math-visualization-request-v1.schema.json`
- `packages/schemas/math-visualization-result-v1.schema.json`
- `packages/schemas/math-visualization-source-v1.schema.json`
- `scripts/quality/check_math_visualization_contracts.py`
- `scripts/quality/evaluate_math_visualization.py`
- `scripts/quality/sprint_6_9_evidence.py`
- `scripts/quality/sprint_6_9_owner_acceptance.sh`
- `services/api/src/main/java/com/verifiedai/sharedkernel/contracts/VersionedJsonContract.java`
- `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java`
- `services/api/src/main/java/com/verifiedai/visualization/api/VisualizationController.java`
- `services/api/src/main/java/com/verifiedai/visualization/api/VisualizationRequestLimitFilter.java`
- `services/api/src/main/java/com/verifiedai/visualization/application/VisualizationService.java`
- `services/api/src/main/java/com/verifiedai/visualization/application/VisualizationVerifier.java`
- `services/api/src/main/java/com/verifiedai/visualization/domain/VisualizationContract.java`
- `services/api/src/main/java/com/verifiedai/visualization/infrastructure/HttpVisualizationVerifier.java`
- `services/api/src/main/java/com/verifiedai/visualization/package-info.java`
- `services/api/src/test/java/com/verifiedai/visualization/VisualizationContractTest.java`
- `services/api/src/test/java/com/verifiedai/visualization/VisualizationControllerTest.java`
- `services/api/src/test/java/com/verifiedai/visualization/VisualizationIntegrationTest.java`
- `services/api/src/test/java/com/verifiedai/visualization/VisualizationVerifierTest.java`
- `services/math-verifier/app/api/routes/verify.py`
- `services/math-verifier/app/api/schemas/math_visualization.py`
- `services/math-verifier/app/config.py`
- `services/math-verifier/app/observability/metrics.py`
- `services/math-verifier/app/policies/process_executor.py`
- `services/math-verifier/app/verifiers/math_visualization.py`
- `services/math-verifier/tests/test_math_visualization.py`
- `services/math-verifier/tests/test_sprint_6_9_evidence.py`
