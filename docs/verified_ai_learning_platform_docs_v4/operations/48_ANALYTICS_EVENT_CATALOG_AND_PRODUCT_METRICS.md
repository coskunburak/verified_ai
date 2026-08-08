# Analytics Event Catalog and Product Metrics

## Principles

Analytics event names describe business meaning, not UI implementation. Avoid generic events such as `button_clicked`.

Never send raw problem text/image to general analytics.

## Acquisition/activation events

- onboarding_started
- onboarding_completed
- sign_in_completed
- problem_capture_started
- problem_capture_source_selected
- problem_capture_quality_warning
- problem_capture_accepted
- problem_asset_uploaded
- problem_parse_completed
- first_solution_completed
- first_verified_solution_viewed

Sprint 4.1 local capture instrumentation uses privacy-safe client log names for opened/source-selected/camera-ready/capture/import/quality-warning/accepted/failure states. These records must not include raw problem images, OCR text, PDF bytes, filenames, local paths, or user-authored content. Product analytics promotion remains a later analytics-client task; the same minimization rule applies when events are promoted.

## Solving events

- solve_requested
- solve_completed
- solve_failed
- parse_corrected
- verification_details_viewed
- solution_alternative_viewed

Properties:
- problem_type
- primary_skill_code
- difficulty_band
- verification_status
- latency_bucket
- entitlement_tier

## Tutor events

- tutor_session_started
- tutor_turn_submitted
- hint_requested
- answer_revealed
- tutor_session_completed

## Learning events

- attempt_submitted
- mistake_detected
- mistake_reviewed
- practice_session_started
- practice_session_completed
- mastery_band_changed
- study_plan_item_completed

Do not send exact mastery float if a band is adequate for third-party analytics.

## Billing events

- paywall_viewed
- purchase_started
- purchase_completed
- purchase_failed
- restore_completed
- entitlement_changed

## North-star candidates

### Weekly Verified Learning Sessions
A session containing meaningful attempt/practice and verified/reference learning evidence.

### Skills Improved per Active Learner
Count of skills with statistically/algorithmically meaningful positive mastery transition.

## Funnel

Install → onboarding → first problem → first completed solution → verification detail/tutor → second session → subscription.

## Retention

D1/D7/D30 are necessary but not sufficient. Also measure:
- weekly study sessions,
- repeat mistake review,
- study-plan completion,
- returning to previously weak skill.

## Quality metrics

- wrong answer report rate,
- parse correction rate,
- false verification incidents,
- user dispute rate on mistake diagnosis.

## Cost/product economics

- AI cost per completed solve,
- AI cost per paying active learner,
- AI cost per verified learning session,
- gross margin by entitlement tier.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## Analytics vs training separation

Product analytics is not a training dataset. Events should contain minimal semantic metadata and avoid raw problem/attempt content. Any future use of analytics-derived data for ML follows explicit eligibility and dataset-lineage governance.
<!-- HYBRID_AI_STRATEGY_V3:END -->
