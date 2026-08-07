# ADR-006 — Production Student Data Is Not Training Data by Default

- **Status:** Accepted
- **Decision:** User/student content and interactions are not eligible for model training merely because they exist in production. Training eligibility requires explicit governed status, purpose, lineage, and privacy review.

## Rationale

The product handles educational content and potentially minors' data. Data minimization, trust, and reproducibility take priority over opportunistic dataset accumulation.

## Consequences

- Training eligibility is explicit and auditable.
- Retention/deletion continues to apply independently of ML ambitions.
- Golden evaluation data is protected from leakage.
- Model artifacts reference exact dataset versions.
