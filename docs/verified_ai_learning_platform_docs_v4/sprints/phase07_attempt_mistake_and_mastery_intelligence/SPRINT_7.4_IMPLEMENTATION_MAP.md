# Sprint 7.4 implementation map

1. Baseline main / 5e248fe1c62140809ea974b81eaba0913a6a18eb; preserve all pre-existing 7.3 changes.
2. 7.3 accepted evaluation boundary exists locally; owner run 20261107-025110-XrMKJK is prior-source evidence only.
3. mistake module owns public pure taxonomy/category/strength/review semantics; no dependency on attempt internals.
4. attempt owns accepted-evaluation adapter, authenticated orchestration, artifact and persistence lifecycle.
5. Keep all 17 specific v2 codes and both generic/legacy codes; add a separate versioned category mapping.
6. Classify only the sequence's first disproof after exact proof of all earlier steps; never use final-only failure as causal proof.
7. Separate signature-supported, generic-invalid, uncertain, unsupported, source-review, incomplete and no-error states.
8. Evidence bands replace fabricated probabilities; MODEL_SUGGESTION/USER_CORRECTION/HUMAN_REVIEW cannot enter deterministic publication.
9. Skill remains null unless an explicit canonical ontology mapping is active; legacy attempt-skill strings are not canonical.
10. Add MistakeClassificationDecision/MistakeTaxonomy and AttemptMistakeClassifier, AttemptMistakeClassification and strict request/value/view contracts.
11. Add AttemptMistakeService, AttemptMistakeRepository and thin AttemptMistakeController on /attempts/{id}/classification.
12. POST uses owned accepted evaluation ID/source/work revision and idempotency key; GET never computes.
13. V036 adds append-only owner/evaluation-linked classification, strict lineage trigger and unique policy publication.
14. Source/account locks serialize publication and privacy; accepted evaluations and mastery remain untouched.
15. Export via existing attempt privacy contributor; cascade deletion follows the evaluation parent.
16. Add default-off classification flag; historical GET and exact replay survive disablement.
17. Python reuses bounded predicates; additive structural policy provenance makes unversioned historical signatures review-only.
18. Add shared mapping, strict JSON Schema/OpenAPI, synthetic fixture and cross-language contract checks.
19. Extend existing Swift models/API/view model/results/capture with explicit classify action and recovery GET.
20. Preserve generation/account/source fences; typed states, EN/TR, Dynamic Type and VoiceOver labels.
21. Author Java policy/API/PostgreSQL/concurrency/privacy/security and Swift decoding/state/UI tests.
22. Golden cases cover 17 signature families, identity/near-match guards and unresolved scopes without providers.
23. Add independent 7.4 owner runner/evidence writer with fresh A–G evidence, fingerprints and skip rejection.
24. Reuse current-source 7.1–7.3 regressions; never import prior results as a 7.4 pass.
25. Metrics contain bounded outcomes, authority and policy only; new paid inference cost delta is zero.
26. Rollback disables creation and retains V036/readers; no migration execution or deployment by agent.
27. Update canonical mistake/ontology/domain/API/data/privacy/verification, manifest and traceability documents.
28. All new tests NOT_EXECUTED / PENDING_OWNER_TEST; external CI/ADR-011/production gates remain separate.
