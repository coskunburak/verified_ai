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
- token_hash
- family_id
- expires_at
- revoked_at
- replaced_by
- created_at

## Profile

### learning_profiles
- user_id PK/FK
- education_level
- preferred_language
- explanation_depth
- daily_study_minutes
- timezone
- created_at
- updated_at

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
- current_parse_id nullable
- problem_id nullable
- solve_job_id nullable
- created_at
- completed_at
Index(user_id, created_at desc)

### problem_assets
- id
- problem_session_id
- object_key
- asset_type
- mime_type
- size_bytes
- checksum
- retention_class
- created_at

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
- user_id
- tier
- status
- source
- effective_at
- expires_at
- original_transaction_id
- last_verified_at

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

V001 identity
V002 profile
V003 curriculum
V004 problem
V005 solving
V006 verification
V007 attempts_mistakes
V008 mastery
V009 studyplan
V010 exam
V011 billing
V012 ai_usage_audit

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI economics and future ML-governance records

Production V1 must persist sufficient `ai_usage`/route provenance to calculate cost and reconstruct outputs. Recommended dimensions include capability, route-policy version, provider/model, prompt/schema, usage units, estimated cost, latency, escalation reason and trace ID.

Do **not** add training-dataset/model-registry tables until proprietary model work begins. When Phase 13 starts, use the forward data contract in `data/62_TRAINING_DATA_LINEAGE_CONSENT_AND_DATASET_PIPELINE.md`.
<!-- HYBRID_AI_STRATEGY_V3:END -->
