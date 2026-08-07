# Training Data Lineage, Consent, and Dataset Pipeline

## 1. Purpose

This is the data-engineering counterpart to the AI training governance policy. It defines persistent records, transformations, access boundaries, and deletion/revocation propagation for future proprietary model datasets.

## 2. Recommended relational concepts

Potential tables/entities (introduced only when Phase 13 begins):

- `training_data_sources`
- `training_eligibility_decisions`
- `dataset_definitions`
- `dataset_versions`
- `dataset_items`
- `dataset_split_assignments`
- `label_provenance`
- `dataset_quality_reports`
- `model_experiments`
- `model_artifacts`
- `model_evaluation_runs`

Do not add these tables prematurely if no training program exists; the schema section is a forward contract.

## 3. Source reference vs copied content

Prefer immutable references plus reproducible materialization where practical. If content must be copied into a dataset store, record source lineage and transformation history.

## 4. Eligibility decision record

Fields should include:

- source type/id,
- eligibility state,
- allowed purposes,
- policy version,
- decision timestamp,
- reason,
- actor/process,
- revocation status.

## 5. Transformations

Every transformation stage is versioned:

- de-identification,
- normalization,
- canonical math conversion,
- labeling,
- deduplication,
- quality filtering,
- split assignment.

## 6. Dataset manifests

An immutable manifest includes counts and checksums sufficient to reproduce the training/evaluation materialization.

## 7. Split leakage controls

Use stable content fingerprints and semantic-near-duplicate detection to prevent the same or trivially transformed problem from crossing protected splits.

## 8. Access model

Production API workloads should not have arbitrary access to training stores. Training/evaluation jobs use scoped credentials in separated environments.

## 9. Deletion/revocation propagation

A lineage lookup must identify which datasets/artifacts include a revoked source. The remediation policy is documented per legal/product requirement.

## 10. Analytics separation

Product analytics events are not a model-training lake. Movement from analytics to training requires an explicit governed pipeline and purpose review.

## 11. Storage security

Encrypt datasets, restrict access, log exports, and avoid embedding secrets/PII in artifact names or manifests.
