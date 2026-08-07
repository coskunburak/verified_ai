# Privacy and Student Data Protection

## Principles

- collect minimum necessary data,
- state purpose clearly,
- limit retention,
- separate analytics from content,
- support deletion/export roadmap,
- avoid unnecessary sensitive inference.

## Phase 1 Data Classification Baseline

| Class | Examples | Default handling | Training eligibility |
|---|---|---|---|
| Public | Marketing copy, public documentation, licensed public math examples | May be public if license allows | Only if license/purpose permits. |
| Internal | architecture docs, non-secret runbooks, non-sensitive operational metadata | Internal repository/process access | Not training data by default. |
| Sensitive | raw problem images, typed questions, tutor transcripts, attempts, mistake evidence, mastery history | Minimize collection, restrict access, redact from general logs/analytics, include in deletion/retention design | Not eligible by default. |
| Student Personal Data | account-linked learning history, profile, problem history, attempts, study plans, exam goals | Backend-authoritative, access-controlled, export/deletion roadmap | Not eligible by default. |
| Authentication Secret | access tokens, refresh tokens, identity tokens, token hashes | Secret-safe storage, never logged, revoke/delete according to auth lifecycle | Never training eligible. |
| Payment / Entitlement Metadata | transaction identifiers, entitlement status, billing event hashes | Server-authoritative, minimized, audited, retained per legal/security need | Never raw training data. |
| AI Operational Metadata | provider/model, prompt/schema version, tokens/units, latency, cost, route policy, trace ID | Persist for audit/economics without raw content where possible | Not training data by default. |
| Training Ineligible Data | default state for production student content and protected holdouts | Excluded from training/export pipelines | No. |
| Training Eligible Data | explicitly governed public/licensed/synthetic or consent/policy-approved data | Requires lineage, purpose, minimization, retention, revocation, and dataset versioning | Only for approved purpose. |

## Privacy Baseline Rules

- Raw student content must not enter generic analytics by default.
- Logs must prefer stable IDs, skill codes, statuses, and trace IDs over raw problem text or images.
- Account deletion must reach identity, object assets, problem history, attempts, mastery, study plans, billing records, and AI operational metadata according to their retention class.
- Provider contracts/settings must be checked for retention and training use before external AI processing is enabled.
- User corrections may improve product quality but are not ground truth or training eligible without validation and eligibility governance.

## Raw student content

Images can contain handwriting, names, school identifiers and unrelated background. Crop/preprocess to minimize irrelevant capture.

## External AI processing

Provider settings/contracts must be reviewed for retention, training use and regional processing. Never promise a privacy property that is not technically/contractually enforced.

## Analytics minimization

Good:
- topic=CALCULUS,
- verification=VERIFIED,
- solve_status=SUCCESS.

Avoid raw question text, images and unrestricted tutor transcripts in third-party analytics.

## User rights

Provide account deletion and appropriate history deletion. Data export can be phased but should be designed into ownership model.

## Minors

If product targets children, jurisdiction-specific requirements and age-gating/parent features require legal review before launch.

## Model improvement

Private user content is not automatically reused for training/evaluation. Any future use requires explicit policy, minimization and appropriate consent/legal basis.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Training-data privacy rule

Production student content is not training data by default. Any future training use requires an explicit eligibility/purpose process, minimization, lineage, and revocation handling. Product retention/deletion promises remain authoritative.
<!-- HYBRID_AI_STRATEGY_V3:END -->
