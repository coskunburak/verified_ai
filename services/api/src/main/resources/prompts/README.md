# Prompt Artifacts

This directory is the canonical executable prompt repository for the Spring API service.

Root `prompts/README.md` points here and must not be used to maintain a second prompt copy.

## Structure

```text
prompts/
├── README.md
├── vision-recognition/
│   └── v001.md
├── problem-parser/
│   └── v001.md
└── problem-classifier/
    └── v001.md
```

Each prompt family directory name is the `promptId`. Each Markdown file name is the immutable `promptVersion`.

## Version Naming

Prompt versions use `vNNN`, for example `v001` and `v002`.

Do not use `latest`, `current`, `prod`, `new`, or `final`.

## Immutability

Released prompt versions are immutable behavior artifacts. Do not edit a released `v001` body to change model behavior. Create `v002`, register it, run required tests/evaluation, and explicitly update route configuration to the new version.

## Registry Linkage

Every executable prompt must have a `PromptRegistryEntry` with:

- `promptId`
- `promptVersion`
- capability
- owner
- logical output schema version
- artifact path and pinned SHA-256
- release stage
- evaluation reference and digest-bound release approval
- compatible route-policy version
- safety notes
- training eligibility

## Schema Linkage

Prompt registry entries reference logical schema versions such as `problem-classification-v1`. `OutputSchemaRegistry` maps logical schema versions to packaged artifacts sourced from `packages/schemas`.

Schema-valid output is still untrusted until backend semantic validation accepts it.

## Release Stages

- `LOCAL_ONLY`: local/test use only.
- `STAGING`: staging release candidate.
- `PRODUCTION`: production route eligible after review and evidence.

Production enabled routes fail closed unless the registry entry is `PRODUCTION` with matching approval/evaluation evidence. `DRAFT`, `VALIDATED`, `APPROVED`, `DEPRECATED` and `ROLLED_BACK` are not executable. `LOCAL_ONLY` requires local/test fixture-only targets. Staging is disabled by default until an approved candidate is configured.

## Promotion Workflow

1. Create a new immutable prompt version.
2. Register metadata.
3. Bind a logical schema version.
4. Run deterministic tests.
5. Run applicable evaluation.
6. Promote release stage explicitly.
7. Pin route configuration to the new version.
8. Deploy and monitor.

## Rollback Workflow

Rollback production by returning route configuration to a previous registered `promptVersion` and `schemaVersion`. Do not edit old prompt bodies, delete registry history, or disable validation.

## Security And Privacy Rules

Prompt artifacts must not contain secrets, provider credentials, signed URLs, JWTs, learner identifiers, or raw private student content.

Prompt text must not be the sole enforcement point for taxonomy integrity, schema validity, selected-parse authority, privacy, provider security, or unsupported behavior.

## Training Eligibility

Prompt-governed production student workflows default to `NOT_ELIGIBLE` for training. Training eligibility requires separate governed lineage and privacy review.

## Immutable release gate

The metadata manifest is `PromptRegistry.defaults()`; schema hashes and semantic owners are
in `OutputSchemaRegistry.defaults()`. Startup verifies exact bytes. Backend CI compares
existing versions against the trusted PR base, even if a change also updates registered hashes.
Required CODEOWNERS review and branch protection complement this check; hosting settings
still require owner verification. See canonical `ai/26_PROMPT_SCHEMA_AND_VERSIONING.md`
for approval evidence, schema ownership and rollback policy. All present artifacts remain
local fixtures; none is approved for real-provider production inference.
