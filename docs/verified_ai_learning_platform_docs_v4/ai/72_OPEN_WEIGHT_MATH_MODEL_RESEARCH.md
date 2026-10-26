# Open-weight mathematics model research

Research date: 2026-10-26. Decision: PROVISIONAL. Read-only official model cards,
licenses and provider listings; no inference, weight download, GPU allocation or
project benchmark occurred. These are research candidates, not approved routes.
The shortlist is bounded, not a claim to rank every current model.

## Candidate evidence

| Exact candidate | Evidence and license | Project use and uncertainty |
| --- | --- | --- |
| Qwen3.5-35B-A3B | [Official card](https://huggingface.co/Qwen/Qwen3.5-35B-A3B): multimodal MoE, Apache-2.0 | Primary-solver research candidate. Active parameter count does not describe total weight memory. Turkish task quality, structured-output adherence and serving latency unmeasured. |
| Qwen3.5-9B | [Official card](https://huggingface.co/Qwen/Qwen3.5-9B): image/text, Apache-2.0 | Compact baseline and future fine-tuning candidate; evaluate the exact quantization separately. No production quality approval. |
| Qwen3-VL-8B-Instruct | [Official card](https://huggingface.co/Qwen/Qwen3-VL-8B-Instruct): multimodal, Apache-2.0 | Image/notation comparison candidate; transcription accuracy and faithful interpretation must be scored separately from solving. |
| DeepSeek-R1-0528-Qwen3-8B | [Official card](https://huggingface.co/deepseek-ai/DeepSeek-R1-0528-Qwen3-8B): text reasoning, MIT metadata | Compact text reasoning comparator; preserve upstream Qwen notices as well. Reasoning-token volume and Turkish fidelity require project evidence. |
| DeepSeek-R1-0528 | [Official card](https://huggingface.co/deepseek-ai/DeepSeek-R1-0528): MIT, commercial use and distillation stated | Conditional stronger-model research comparator via hosted inference; very large total weights make solo-operated dedicated deployment unattractive without measured utilization. |
| DeepSeek-Math-V2 | [Official card](https://huggingface.co/deepseek-ai/DeepSeek-Math-V2): specialized proof-oriented mathematics; current card states Apache-2.0 | Future advanced mathematics comparator. An [older LICENSE revision](https://huggingface.co/deepseek-ai/DeepSeek-Math-V2/blob/a016c5149eb8756f46ae2184cabcb92ee052be1f/LICENSE) contains MIT text. Pin the exact weights revision and applicable license before procurement; no license inference across revisions. |
| Phi-4-reasoning-plus | [Official card](https://huggingface.co/microsoft/Phi-4-reasoning-plus), [license](https://huggingface.co/microsoft/Phi-4-reasoning-plus/blob/main/LICENSE): 14B dense, MIT | Compact reasoning comparator; card emphasizes English and longer outputs. Turkish and cost efficiency remain uncertain. |
| Gemma-3-27b-it | [Official card](https://huggingface.co/google/gemma-3-27b-it): image/text, Gemma terms | Comparison candidate with custom use terms, not interchangeable with Apache/MIT. Exact license obligations must be reviewed before deployment or derived-model distribution. |
| Ministral-3-8B-Instruct-2512 | [Official card](https://huggingface.co/mistralai/Ministral-3-8B-Instruct-2512): vision, FP8, Apache-2.0 | Compact multimodal alternative; do not transfer instruct scores to a reasoning variant. |
| PaddleOCR-VL | [Official card](https://huggingface.co/PaddlePaddle/PaddleOCR-VL): document/formula OCR, Apache-2.0 | Extraction specialist comparison, not a solver or verifier. Handwriting and Turkish symbols need separate held-out evaluation. |

Published scores use different prompts, sampling, token budgets, tool access, judge
models and dataset splits. They are not directly ranked here. Proof benchmark success
is not evidence of TYT/AYT visual parsing, answer normalization or student-facing safety.
Official cards support candidate discovery; they do not supply project acceptance.
Newer provider-listing names are not automatically included without model-card/license review.

## Provisional recommendations

- Primary solver research: Qwen3.5-35B-A3B, compared against a managed API control.
- Compact cost candidate: Qwen3.5-9B; DeepSeek-R1-0528-Qwen3-8B as text-only comparator.
- Vision: Qwen3-VL-8B-Instruct, with PaddleOCR-VL as an extraction-only specialist.
- Conditional stronger model: hosted DeepSeek-R1-0528, subject to exact deployment,
  retention, region and maximum-billable-output approval.
- Future fine-tuning: Qwen3.5-9B for a bounded task, only after dataset eligibility,
  license, quality and TCO gates. No production student data is eligible by default.

These recommendations are engineering hypotheses, not measured winners.

## Serving and memory planning

Weight-only theoretical storage for dense parameters is approximately P×2 bytes at
BF16, P bytes at INT8, P/2 at INT4: 8B ≈16/8/4 GB; 9B ≈18/9/4.5 GB; 14B ≈28/14/7 GB;
27B ≈54/27/13.5 GB; total 35B ≈70/35/17.5 GB. These arithmetic lower bounds are NOT
GPU requirements or measured deployment configurations. Add KV cache, vision encoder,
activations, quantization metadata, runtime workspace, batch and context headroom.
MoE active parameters reduce compute per token, not storage for all experts.

Cards describe Transformers/vLLM/SGLang or model-specific serving paths. Pin runtime,
weights revision, tokenizer, quantization and CUDA stack for an owner benchmark;
never execute model-card installation snippets automatically. Long reasoning outputs
can erase token-price advantages. Small-model latency and Turkish accuracy are UNKNOWN.
Hosted inference can reduce operational burden, but availability does not establish
privacy compliance or exact-model revision guarantees. Self-hosting remains conditional.
