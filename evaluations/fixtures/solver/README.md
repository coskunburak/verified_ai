# Primary solver contract fixtures

These project-authored synthetic cases contain no student data, holdout material or model calls.
They are NOT_ELIGIBLE for training. `cases.json` covers arithmetic, algebra, equations,
inequalities, fractional answers, source assumptions/excluded values, ambiguous source data,
unsupported geometry/calculus, stale identity, malformed outputs and false verification claims.

`schemaValid` labels structural JSON Schema expectations. `semanticAccepted` labels the
source-relative Java safety contract. `independentAnswerLabel` is a synthetic author label,
not an external verifier verdict. Wrong but well-formed answers and contradictory steps are
intentionally accepted as unverified: this guards against conflating shape/safety with truth.
These cases are public regression fixtures; they cannot establish real model accuracy.

Owner-only execution:

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
python3 evaluations/runners/validate_solver_fixtures.py
cd services/api
mvn -Dtest=SolutionCandidateContractTest,SolverFixtureAdapterTest test
```

The Python checker requires `jsonschema` 4.x (`Draft202012Validator`) in the owner's Python
interpreter; no package is installed automatically. It writes `.generated/evaluations/solver/schema-report.json`.
The Java test evaluates semantic expectations and produces Surefire reports. They are separate
gates. Model accuracy, step correctness, p50/p95 inference latency, tokens, cost per accepted
candidate and hallucinated-premise frequency require an approved real model, independent labels,
and measured runs; the schema report leaves these measurements null. Candidate acceptance is
never independent verification. A future model evaluation must retain rejected-call usage as well
as accepted-call usage, report unsupported handling and false verification claims separately,
and bind exact model, prompt/schema digests, route, dataset revision and pricing to its report.
No automatic inference runner is enabled while BLOCKED_MODEL_DECISION applies.
