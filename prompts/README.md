# Prompts

Executable production prompt artifacts live with the Spring API service that executes them under `services/api/src/main/resources/prompts`.

This root directory is a pointer only. Do not maintain prompt body copies here.

Current executable prompt families:

- `vision-recognition/v001.md`
- `problem-parser/v001.md`
- `problem-classifier/v001.md`

Prompt metadata is governed by the API `PromptRegistry`. Logical output schema versions map to canonical schema files in `packages/schemas`.
