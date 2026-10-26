# Mathematics model evaluation plan

Status: PREPARED — PENDING_OWNER_TEST / BLOCKED_EXTERNAL for connected inference.
No model benchmark has been run. Sprint 4.10 local 18-case ingestion results are fixture
regressions, not math-model accuracy or cost measurements.

## Versioned corpus design

Corpus `math-routing-research-v001` is a proposed evaluation-only corpus. Minimum
240 independently authored/licensed cases; 20 per row below, with balanced Turkish
and English where applicable. Images must be original consenting adult-created
fixtures or explicitly licensed assets. No scraped examination books/student uploads.

| Slice | Expected contract behavior |
| --- | --- |
| Elementary arithmetic | Existing expression ingestion; future solving benchmark |
| Algebraic expressions | Existing bounded expression ingestion |
| Equations | Only existing canonical subset; future solve benchmark |
| Inequalities | Only existing canonical subset; unsupported forms remain rejected |
| Functions/calculus | Future-scope unless exact current canonical contract accepts input; no general calculus support claim |
| Turkish word problems | Parsing/faithfulness evaluation; unsupported translations remain review-required |
| TYT-style | Original style-inspired items, never a copyright claim over exam questions |
| AYT-style | Separate supported canonical subset from advanced/future cases |
| Handwriting | Extraction and ambiguity preservation; no guessed missing symbols |
| Printed questions | Extraction fidelity and notation alignment |
| Incomplete OCR/ambiguous inputs | Review/refusal; false authoritative acceptance is a hard failure |
| Graphs/diagrams/geometry | Future-scope or refusal/review benchmark; never production-supported by this sprint |

Manifest fields: caseId, revision, sha256, origin, rightsHolder/license, consent scope,
locale, modality, topic, canonicalSupportStatus, expectedDisposition, referenceAnswer,
referenceSteps, referenceReviewerIds, independentReviewDate, split, trainingEligibility.
Use 60% development / 20% release / 20% protected holdout, stratified by slice; near-duplicate
families must share a split. Protected payloads remain outside the repository, checksum
referenced. Holdout results never become tuning examples. Missing rights or reference
review blocks inclusion. Corpus construction and rights review remain owner work;
this document is a design, not a claim that 240 validated cases already exist.

## Paired benchmark protocol

Freeze corpus manifest, exact weight/provider deployment, runtime/quantization,
prompt/schema digest, context/token/image ceilings, sampling settings, seed (when
supported), tools, timeout and pricing version. Feed identical accepted inputs to
all candidates; keep provider retries distinct from independent solver comparison.
Separate extraction, normalized-text solving and end-to-end paths to avoid attributing
OCR errors to reasoning. Use at least three repetitions for stochastic routes; report
all runs, not best-of-k. Human graders are blind to model identity and resolve disagreements.

Metrics: final-answer and normalization accuracy; independently graded step correctness;
unsupported handling; schema validity; verifier compatibility (not verifier truth);
hallucinated assumptions; false authoritative answers; Turkish fidelity; OCR exact/math-symbol
accuracy; p50/p95 latency; input/output/reasoning token and image usage; timeout/fallback
rate; estimated USD; cost per correct answer. Report denominators, excluded/failed cases,
confidence intervals, and per-slice results. Failures remain in denominators.
Cost per VERIFIED solution is UNAVAILABLE until an independent verifier produces evidence;
any forecast must be labelled PROJECTED. Model agreement is not independent verification.

## Proposed release thresholds, pending approval

Zero privacy violations, fabricated VERIFIED labels, unsupported coercions and critical
false authoritative acceptance. Zero silent malformed-output acceptance. Proposed
schema validity ≥99%; answer accuracy non-inferior to approved baseline within a
predeclared 2 percentage-point margin, with confidence intervals. No critical slice
regression; missing statistical power means INSUFFICIENT_EVIDENCE. Latency and monthly
cost ceilings need approved business values; no fabricated SLO. Quantized variants
repeat the same gates. A model cannot trade correctness for a cheaper token price.

## Owner execution design

First run deterministic router/gateway/budget tests from Sprint 5.3 report. Existing
`AI_EVAL_MODE=local make eval-ai` only checks ingestion compatibility. Do NOT use it as
a solver benchmark. Connected benchmark runner/adapter is deliberately not activated:
it requires owner-approved provider, exact deployment/pricing/license, privacy region,
protected data access, max cases, max attempts, total reservation and token ceilings.
Produce JSONL per-attempt evidence and aggregate JSON/Markdown with immutable input
manifest hashes. Export no raw student content to metrics/logs. Compare on the same
manifest; never mix current and historical pricing in an observed-cost column.

## Sprint 5.4-RM prepared implementation

The earlier runner blocker is now partially addressed: an owner-opt-in durable real-model smoke test,
18-case synthetic canonical benchmark, bounded adapter, independent numeric/schema report tool and
manual quality/capacity templates are authored. None has been executed. The synthetic corpus has
11 candidate cases and 7 source/future-scope rejection cases; it is not the 240-case release corpus.
Independent gold review, protected holdout, OCR evaluation, statistical power and production verdict
metrics remain unavailable. Rejected raw payloads are not retained, so exact schema acceptance may
be unavailable; report a lower bound rather than inventing results.
[Protocol and commands](../sprints/phase05_ai_solving_and_verification_core/SPRINT_5.4_REAL_MODEL_OWNER_RUNBOOK.md).
The proposed quality thresholds require one explicit owner-approved version before promotion.
