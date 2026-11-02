# Socratic policy v1 acceptance rubric

Dataset: evaluations/golden-datasets/tutoring/socratic-policy-v1.json. All content is synthetic,
trainingEligible=false. No expert labels or production learner content. Owner execution only.

| Measure | Local evidence and threshold | Scope/limitation |
|---|---|---|
| Legal transition rate | All dataset transitions accepted by domain legality checks; expected phases/actions match | Structural, not mathematical progression |
| Answer leakage | Zero occurrence of fixture forbidden answers in replies | Finite fixtures, not general model safety |
| False correctness | Every reply UNKNOWN and uncertain=true; no ADVANCE with UNKNOWN | No step verifier available |
| Unverified certainty | Every reply referenceStatus=UNAVAILABLE | No reference reader; no claims about verification |
| Schema validity | All outputs satisfy tutor-turn-v1; authority/malformed fixtures rejected | API schema, not a model schema |
| Source/ownership | Zero foreign or stale new writes in HTTP/PostgreSQL assertions | Required integration cases reconciled |
| Retry duplication | Exactly one reply and one learner parent across concurrent/restarted requests | Local atomic commit only |
| Response latency | Measured policy nanoseconds per synthetic case; new SQL transaction bounded at 5 seconds | Network p50/p95 and production SLO NOT_EVALUATED |
| Inference cost | Zero calls, context bytes, retries and cost per turn/session | No inference route introduced |
| Pedagogical progression quality | Expert labels, independent learner outcome comparison required | NOT_EVALUATED; blocks production pedagogy claim |

Unverified/unsupported math, EN/TR, conceptual progression, confusion, repeated reveal requests,
policy/role/chain-of-thought/authority/exfiltration injection and reflection are in the dataset.
Staleness, retry, close-during-processing and partial failure use real synthetic PostgreSQL tests.
Provider unavailable is exercised as a disabled deterministic capability with no mutation.
Provider timeout and model-output schema invalidity are NOT_APPLICABLE to no-dispatch implementation;
existing AI gateway/route/registry regressions still run. Live AI remains NOT_EVALUATED.

A missing dataset case, integration case, suite, fresh result bundle/report, a failure or a skip prevents
local acceptance. Expert review cannot be replaced by a synthetic structural success percentage.
