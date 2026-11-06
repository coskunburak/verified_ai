# Sprint 6.10 implementation map

Baseline: `main`, `8591761b0019396cde94a5cba5beb18bcd639eb7`. `0349160` and `8591761` are local ancestors; reference `53017b1` is absent. The dirty working tree already contains Phase 7.2 implementation and repairs. Preserve it, including structured-work bindings, acknowledgements, source revisions, backend module boundaries and V034.

| Workstream | Initial classification | Ownership and change |
|---|---|---|
| A Dynamic Type | NEEDS_HARDENING | TutorView: scrollable composer at accessibility sizes; PrimarySolveView and MathVisualizationView: adaptive controls. Existing ScaledMetric and horizontal math scrolling reused. |
| B VoiceOver | NEEDS_HARDENING | AttemptMathPresentation: bounded, grouped speech and safe unsupported fallback; solution focus; native chart sample labels; tutor speaker/history semantics retained. |
| C EN/TR | NEEDS_HARDENING | Localizable.xcstrings: additive fallback/graph/recovery copy; owner checker inventories critical prefixes and source references, checks placeholders. Canonical math untouched. |
| D Motion/contrast | NEEDS_HARDENING | Tutor motion suppression; ColorTokens adaptive light/dark/increased contrast; remove RootView forced dark. Existing textual verification states and chart dash patterns reused. |
| E Recovery | NEEDS_HARDENING | Existing SolveRecoveryStore/TutorPendingStore/account and cancellation fences reused; restore pending tutor content on relaunch, cancel obsolete page loading. No automatic submission. |
| F Performance | NEEDS_HARDENING | Avoid unchanged native math reparsing, lazy tutor rows, bounded page loading, allocation-free nearest graph sample. No shared cache of learner content. |
| G UX | NEEDS_HARDENING | Preserve identifiers, explicit uncertainty, saved/unassessed distinction, hint/reveal confirmation and progressive explanation disclosure. |

No API, schema, persistence format, migration, provider, dependency or feature-gate changes. Inference-cost delta: zero. No training eligibility change, raw-content logging, or production activation. Existing protected pending store remains the only durable tutor recovery artifact.

Tests: extend registered XCTest/XCUITest files with synthetic math, accessibility, locale and recovery scenarios. Owner acceptance runs full iOS targets and relevant Phase 6/7 backend, authorization/privacy, contracts and docs. Evidence must be fresh and source-stable; manual physical-device VoiceOver, contrast and performance remain external gates. No test/build execution by agent.

Rollback: selectively undo Sprint 6.10 presentation and runner changes after review, preserving all pre-existing Phase 7.2 changes. No data rollback or flag activation required.
