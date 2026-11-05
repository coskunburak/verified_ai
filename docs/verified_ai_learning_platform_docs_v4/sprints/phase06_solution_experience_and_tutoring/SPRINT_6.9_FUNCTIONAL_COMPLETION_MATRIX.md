# Sprint 6.9 functional completion matrix

Source implementation audit only. No tests, builds, validators or acceptance were executed by the agent.
COMPLETE below means the bounded implementation exists, not that acceptance passed.

Common contract evidence: packages/schemas/math-visualization-{context,source,request,result,public}-v1.schema.json;
Python Request/Result, VisualizationContract.java, MathVisualization.swift and shared synthetic fixtures.

| ID | State | Production source | Authored evidence | Boundary / remaining work |
|---|---|---|---|---|
| V01 | COMPLETE | app/verifiers/math_visualization.py model/sample; FunctionPlotView | test_math_visualization.py test_semantic_golden; quartic/constant/slope/hole cases | Single explicitly real x; rational numerator/denominator degree <=4; unsupported exact set representations refused |
| V02 | COMPLETE | model relation residual; exact Feature and solutionSet | quadratic-roots, empty-real-roots, repeated-root, equation-cancelled-root | Equality only for residual plot; numeric crossing is not root evidence |
| V03 | COMPLETE | trusted SymPy diff after rational eligibility; derivative series in FunctionPlotView | derivative-hole, constant-zero-derivative, zero-function | Derivative of ambient rational function restricted to original domain; no two-sided endpoint assertion |
| V04 | COMPLETE | bands, DomainConstraintGuard, NumberLineView, exact native set presentation | rational-half-open, intersection, interval-union, finite-set, empty-set, all-reals | Exact endpoint strings remain authoritative; plotted positions approximate; out-of-view intervals remain in text |
| V05 | COMPLETE | exact features ROOT_INTERCEPT/Y_INTERCEPT/SLOPE/QUADRATIC_VERTEX | slope and quadratic semantic cases | Only graph consequences; no generic Euclidean geometry, monotonic-interval claims or invented shapes |
| V06 | COMPLETE | MathVisualizationView/Model bounded zoom, pan, Charts selection, sample buttons, toggles | MathVisualizationTests viewport/offline/toggle/selection; MathVisualizationUITests | Linear interpolation is explicitly illustrative; native gesture/device accessibility still needs owner review |
| V07 | COMPLETE | finite Concept enum, localized explanation catalog, exact/approximate labels | contract/localization owner checker and golden preconditions | No automatic narrative generation or verification promotion |
| V08 | COMPLETE | PrimarySolveView, SourceMethodComparisonView, RootView dependency injection; dedicated visualization context | VisualizationIntegrationTest read-only replay; UI navigation test | Current source method-comparison entry supported; historical learner comparison does not reuse an old source |
| V09 | PARTIAL | STORY_BRIDGE -> PROVENANCE_UNAVAILABLE; localized recovery text | provenance-unavailable golden case | No trustworthy reviewed phrase-to-variable/relation span contract. Existing sourceBlockIds and token offsets are not that proof. No phrase highlighting delivered |
| V10 | COMPLETE | service reauthorization, strict contracts, worker isolation, native failure states | Python worker failures/admission/auth; Java controller/BOLA/rate/deletion/concurrency; Swift cancellation/account/retry | All runtime evidence pending; explicit rate-limit/malformed recovery states carry no graph |

## Evidence paths

- Python: services/math-verifier/tests/test_math_visualization.py (mathematical, transport, process and route tests).
- Java: services/api/src/test/java/com/verifiedai/visualization/VisualizationContractTest.java,
  VisualizationControllerTest.java, VisualizationVerifierTest.java and VisualizationIntegrationTest.java (disposable PostgreSQL).
- iOS: apps/ios/VerifiedAITests/ProblemSolving/MathVisualizationTests.swift and
  apps/ios/VerifiedAIUITests/MathVisualizationUITests.swift.
- Golden: evaluations/golden-datasets/visualizations/bounded-real-v1.json, 26 synthetic cases.
- Owner checks: scripts/quality/check_math_visualization_contracts.py and evaluate_math_visualization.py.
- Regressions: full Python suite, selected existing backend auth/privacy/Modulith/solve/attempt suites,
  all configured iOS unit/UI tests and Sprint 6.7/6.8 contract/golden checks.

F08 is an explicit functional limitation, not a completed phrase bridge. Broader UI device/locale matrices,
production staging, hosted CI and Linux isolation evidence cannot be inferred from authored tests.
