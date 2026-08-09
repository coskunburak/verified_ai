# PostgreSQL Data Model

## Why PostgreSQL

The product is naturally relational and transactional: User → Attempt → Problem → Skill → Mistake → Mastery → StudyPlan → Exam. We require referential integrity, joins, aggregate reporting, transactional updates, stable migrations and auditability.

## Conventions

- consistent UUID/ULID strategy,
- UTC timestamps,
- snake_case,
- explicit foreign keys,
- version/optimistic locking where needed,
- JSONB only for flexible artifacts, not core relational facts.

## Identity

### users
- id PK
- status
- created_at
- deletion_requested_at
- deleted_at

### user_identities
- id
- user_id FK
- provider
- provider_subject
- created_at
Unique(provider, provider_subject)

### refresh_tokens
- id
- user_id
- session_id
- token_hash
- family_id
- created_at
- expires_at
- used_at
- revoked_at
- replaced_by_id

### sessions
- id
- user_id
- status
- created_at
- last_seen_at
- expires_at
- revoked_at
- revocation_reason

### auth_security_events
- id
- event_type
- user_id nullable
- session_id nullable
- reason
- created_at

Refresh tokens store only a hash of the opaque high-entropy token. A presented used token revokes the current session and active refresh-token family members as possible theft evidence.

Sprint 3.7 extends `users.status` with `DELETION_REQUESTED`, `DELETION_IN_PROGRESS`, and `DELETED`. Runtime authorization permits a deletion-requested account to complete only account/deletion/logout flows; learning, profile, and billing actions require `ACTIVE`.

### data_exports
- id PK
- user_id FK nullable on account tombstone cleanup
- status `READY` / `EXPIRED` / `FAILED`
- schema_version
- content_json JSONB
- requested_at
- completed_at
- downloaded_at nullable
- expires_at

`data_exports` contains the generated user export document for a short download window. It excludes raw auth tokens, token hashes, internal fraud signals, and raw payment credentials/JWS payloads.

### privacy_events
- id PK
- user_id FK nullable on account tombstone cleanup
- event_type
- reason
- created_at

`privacy_events` records export requested/downloaded and account deletion lifecycle events. It is audit evidence, not a debug log.

## Profile

### learning_profiles
- id PK
- user_id FK, unique
- education_level
- preferred_language
- explanation_depth
- daily_study_minutes
- timezone
- goal_context
- onboarding_status
- created_at
- updated_at
- version

`learning_profiles` is one row per authenticated `users` row. `NOT_STARTED` is represented by absence of a row; persisted rows are `IN_PROGRESS` or `COMPLETED`.

Account deletion deletes the profile row. Export includes profile fields before deletion through the account lifecycle contributor.

## Curriculum

### subjects
### topics
### skills
### skill_prerequisites
### curricula
### curriculum_skills

`skills.code` is stable/unique.

## Problem lifecycle

### problem_sessions
- id
- user_id
- status
- input_mode
- current_parse_id nullable
- problem_id nullable
- solve_job_id nullable
- created_at
- updated_at
- completed_at
- version
Index(user_id, created_at desc)

Sprint 4.2 creates the minimal durable session foundation. Allowed initial upload statuses are `CREATED` and `ASSET_UPLOADED`; later Phase 4 sprints may advance through `PARSING`, `PARSED`, `SOLVING`, `VERIFYING`, `COMPLETED`, `REVIEW_REQUIRED`, `FAILED`, or `CANCELLED`. `input_mode` is one of `CAMERA`, `PHOTO_LIBRARY`, `FILE`, or `PDF`. The table includes a composite `(id, user_id)` uniqueness constraint so child assets can enforce same-owner relationships.

### problem_assets
- id
- problem_session_id
- user_id
- source_type
- asset_kind
- status
- object_key
- content_type
- size_bytes
- checksum_sha256
- crop_x/crop_y/crop_width/crop_height
- image_width/image_height nullable
- page_count nullable
- retention_class
- upload_expires_at
- uploaded_at nullable
- available_at nullable
- reservation_idempotency_key
- reservation_request_hash
- created_at
- updated_at
- version

Sprint 4.2 persists raw asset metadata only. `ProblemAsset` is not canonical `Problem`, OCR evidence, parser output, solution, verification, or mastery evidence. Constraints enforce supported kinds/content types, 20 MB maximum size, lower-case SHA-256 format, normalized crop bounds, image/PDF metadata bounds, unique object keys, and per-user reservation idempotency keys. A composite FK `(problem_session_id, user_id)` to `problem_sessions(id, user_id)` prevents cross-owner asset/session attachment. Indexes cover session lookup, owner history, status, and pending upload expiry.

### problem_asset_derivatives
- id
- source_asset_id
- problem_session_id
- user_id
- derivative_kind
- status
- selected_for_recognition
- object_key nullable
- content_type nullable
- size_bytes nullable
- checksum_algorithm/checksum_value nullable
- width/height nullable
- source_width/source_height
- crop_x/crop_y/crop_width/crop_height
- processor_name/processor_version/configuration_version
- orientation_normalized
- perspective_applied
- contrast_normalized
- resized
- quality_outcome
- failure_code nullable
- created_at
- updated_at
- completed_at
- version

Sprint 4.3 stores backend-generated preprocessing derivatives separately from immutable source `problem_assets`. `OCR_OPTIMIZED` and `THUMBNAIL` are the only Sprint 4.3 derivative kinds. `selected_for_recognition=true` is unique per source asset and identifies the preferred Sprint 4.4 recognition input. READY derivatives require private JPEG object metadata and SHA-256 checksums; FAILED derivatives store a failure code and no object key. Composite FKs enforce same-owner source/session relationships.

### problem_asset_quality_evidence
- id
- derivative_id
- source_asset_id
- user_id
- signal_type
- severity
- score
- threshold
- policy_version
- message_code
- created_at

Sprint 4.3 stores deterministic capture quality evidence for `BLUR`, `GLARE`, `CROP_FRAMING`, `CONTRAST_READABILITY`, and `RESOLUTION`. Evidence is linked to the selected derivative/source asset and is metadata only; it does not contain OCR text, recognized math, solver output, or raw image bytes.

### recognition_jobs
- id
- problem_session_id
- source_asset_id
- input_derivative_id
- user_id
- capability
- status
- attempt_count/max_attempts
- prompt_id/prompt_version/schema_version
- route_policy_version
- next_attempt_at
- locked_until
- last_error_code nullable
- last_error_message nullable
- created_at
- started_at nullable
- completed_at nullable
- updated_at
- version

Sprint 4.4 jobs are durable async work records for provider-neutral `VISION_PARSE`. Uniqueness by user/session/input derivative/capability/prompt/schema prevents unlimited duplicate jobs for mobile retries. Composite owner FKs ensure the selected derivative, source asset, and session all belong to the same user.

### recognition_evidence
- id
- recognition_job_id
- problem_session_id
- source_asset_id
- input_derivative_id
- user_id
- revision
- schema_version
- raw_provider_output JSONB
- normalized_evidence JSONB
- provider/model
- provider_request_id nullable
- provider_response_id nullable
- prompt_id/prompt_version
- route_policy_version
- input_tokens/output_tokens/image_units/request_units
- provider_latency_ms/total_latency_ms
- estimated_cost_micros/currency/pricing_version
- created_at

Sprint 4.4 evidence is raw recognition evidence only. Raw provider output is stored separately from normalized recognition blocks. Normalized coordinates are relative to the exact `input_derivative_id` and are not canonical problem geometry over the original upload unless later transform lineage maps them.

### problem_parses
- id
- problem_session_id
- revision
- parser_version
- prompt_version
- raw_output_jsonb
- normalized_problem_jsonb
- parse_confidence
- selected
- created_at

### problems
- id
- subject_id
- topic_id
- primary_skill_id
- problem_type
- normalized_representation_jsonb
- difficulty
- created_at

### problem_secondary_skills
problem_id + skill_id junction.

## Solving

### solutions
- id
- problem_id
- method_code
- final_answer_jsonb
- created_at

### solution_steps
- id
- solution_id
- step_index
- expression
- explanation
- concept_code
Unique(solution_id, step_index)

### solver_runs
- id
- problem_id
- solution_id nullable
- role PRIMARY/SECONDARY/ARBITER
- provider
- model
- prompt_version
- schema_version
- normalized_output_jsonb
- latency_ms
- input_tokens
- output_tokens
- estimated_cost
- status
- created_at

## Verification

### verification_runs
- id
- solution_id
- policy_version
- overall_status
- started_at
- completed_at

### verification_signals
- id
- verification_run_id
- method
- status
- verifier_version
- details_jsonb
- created_at

## Attempts/Mistakes

### attempts
- id
- user_id
- problem_id
- source
- final_answer_jsonb
- submitted_at
- evaluation_status
Index(user_id, submitted_at desc)

### attempt_steps
- id
- attempt_id
- step_index
- expression
- raw_text
Unique(attempt_id, step_index)

### mistakes
- id
- user_id
- attempt_id
- attempt_step_id nullable
- skill_id
- category
- confidence
- evidence_jsonb
- status
- created_at
Index(user_id, skill_id, created_at desc)

## Mastery

### skill_mastery
- user_id
- skill_id
- mastery_score
- mastery_confidence
- evidence_count
- algorithm_version
- last_practiced_at
- updated_at
PK(user_id, skill_id)

### mastery_history
- id
- user_id
- skill_id
- previous_score
- new_score
- previous_confidence
- new_confidence
- cause_type
- cause_id
- algorithm_version
- created_at

## Study planning

### study_plans
- id
- user_id
- starts_on
- ends_on
- version
- status
- generated_at

### study_plan_items
- id
- study_plan_id
- scheduled_date
- skill_id
- activity_type
- target_count
- target_minutes
- priority
- reason_code
- completed_at

### study_sessions
- id
- user_id
- plan_item_id nullable
- started_at
- completed_at
- duration_seconds
- summary_jsonb

## Exams

### exam_definitions
### exam_skill_weights
### user_exams
### readiness_history

## Billing

### entitlements
- id
- user_id FK, unique
- tier
- status
- source
- effective_at
- expires_at
- original_transaction_id
- environment
- last_verified_at
- created_at
- updated_at
- version

Default users receive `FREE` / `DEFAULT_FREE` / `ACTIVE`. Sprint 3.5/3.6 App Store-sourced entitlements carry the Apple original transaction ID, App Store environment and last backend verification timestamp. Entitlement transitions remain server-authoritative.

### commerce_account_tokens
- id
- user_id FK, unique
- app_account_token unique
- created_at

Stable per-user UUID tokens are supplied to StoreKit as `appAccountToken` and later used to bind Apple commercial evidence back to the owning account.

### app_store_transactions
- id
- user_id FK
- transaction_id unique
- original_transaction_id
- web_order_line_item_id nullable
- product_id
- app_account_token
- environment
- transaction_jws_sha256
- purchase_date
- expires_date nullable
- revocation_date nullable
- ownership_type nullable
- signed_date nullable
- status
- created_at
- updated_at

The table stores decoded Apple transaction evidence and a payload digest, not raw signed transaction JWS.

### app_store_subscriptions
- id
- user_id FK
- original_transaction_id unique
- product_id
- environment
- status
- app_store_status nullable
- auto_renew_product_id nullable
- current_transaction_id nullable
- expires_date nullable
- grace_period_expires_date nullable
- revocation_date nullable
- renewal_info_jws_sha256 nullable
- last_notification_id nullable
- last_verified_at
- created_at
- updated_at

This table is the billing lifecycle projection consumed by entitlement recalculation.

### app_store_notifications
- id
- notification_uuid unique
- notification_type
- subtype nullable
- environment
- original_transaction_id nullable
- signed_payload_sha256
- processing_status
- received_at
- processed_at nullable
- failure_reason nullable

Notifications are an inbox for App Store Server Notifications V2 delivery, dedupe and terminal processing state.

### billing_events
- id
- external_event_id unique
- event_type
- payload_hash
- processed_at
- result

## AI usage

### ai_usage
- id
- trace_id
- user_id nullable
- operation
- capability
- route_policy_version
- provider
- model
- prompt_version
- schema_version
- input_tokens
- output_tokens
- image_units nullable
- latency_ms
- retry_count
- fallback_count
- escalation_reason nullable
- estimated_cost
- outcome
- verification_outcome nullable
- entitlement_tier nullable
- created_at

Indexes on time, user+time, operation+time.

## Audit

### audit_log
- id
- actor_type
- actor_id
- action
- target_type
- target_id
- metadata_jsonb
- created_at

## Migration order

Current implemented platform migrations:

- V001 platform foundation marker
- V002 identity/auth/session tables
- V003 learning profiles
- V004 entitlements
- V005 App Store billing tables and entitlement App Store fields
- V006 account privacy lifecycle tables
- V007 problem asset upload lifecycle
- V008 problem asset preprocessing lifecycle
- V009 problem recognition lifecycle

Planned later-domain migrations continue with curriculum, problem, solving, verification, attempts/mistakes, mastery, study plan, exam, billing event ingestion, and AI usage/audit tables in their owning sprint order.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI economics and future ML-governance records

Production V1 must persist sufficient `ai_usage`/route provenance to calculate cost and reconstruct outputs. Recommended dimensions include capability, route-policy version, provider/model, prompt/schema, usage units, estimated cost, latency, escalation reason and trace ID.

Do **not** add training-dataset/model-registry tables until proprietary model work begins. When Phase 13 starts, use the forward data contract in `data/62_TRAINING_DATA_LINEAGE_CONSENT_AND_DATASET_PIPELINE.md`.
<!-- HYBRID_AI_STRATEGY_V3:END -->
