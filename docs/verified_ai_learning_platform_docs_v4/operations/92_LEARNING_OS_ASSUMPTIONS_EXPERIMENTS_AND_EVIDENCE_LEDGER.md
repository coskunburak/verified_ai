# 92 — Learning OS Assumptions, Controlled Experiments, and Evidence Classification Ledger

## Document Metadata

| Field | Value |
|---|---|
| **Document ID** | `DOC-92` |
| **Canonical Path** | `operations/92_LEARNING_OS_ASSUMPTIONS_EXPERIMENTS_AND_EVIDENCE_LEDGER.md` |
| **Title** | Learning OS Assumptions, Controlled Experiments, and Evidence Classification Ledger |
| **Status** | `CANONICAL_GOVERNANCE_AND_EVIDENCE_LEDGER` |
| **Owner** | Founder / Principal Architect & Production Engineering Program Manager |
| **Date / Version** | `2026-11-07` / `v1.0.0` |
| **Baseline Commit** | `e39bafe88ea369f98c96a65497a94462dc79ef33` (`main`) |
| **Governing ADRs** | `adr/ADR-004_SEPARATE_PYTHON_MATH_VERIFIER.md`, `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md` |

---

## 1. Purpose

This document serves as the single canonical **Epistemic Hygiene Ledger** and **Controlled Experimentation Register** for the **Verified AI Learning OS**.

It ensures that the founder, engineering agents, and stakeholders never confuse:
- What is **already implemented and verified in source code** (`SOURCE_VERIFIED`)
- What is **backed by peer-reviewed science or official public data** (`EMPIRICAL_RESEARCH` / `VERIFIED_PUBLIC_FACT`)
- What is **claimed by competitors without independent symbolic verification** (`COMPETITOR_MARKETING_CLAIM`)
- What is a **modeled unit-economics or financial projection** (`FINANCIAL_MODEL_ASSUMPTION`)
- What is an **architectural sizing or latency estimate** (`ENGINEERING_ESTIMATE`)
- What is a **product or market hypothesis awaiting real student validation** (`FOUNDER_HYPOTHESIS` / `NOT_VERIFIED`)

---

## 2. Mandatory Evidence Classification Definitions

| Classification Tag | Definition | Allowed Usage in Documentation & Product Decisions |
|---|---|---|
| **`SOURCE_VERIFIED`** | Directly confirmed by inspecting local repository files, Flyway SQL migrations (`V001`–`V036`), Java/Python/Swift source code, or signed owner execution reports at commit `e39bafe88ea369f98c96a65497a94462dc79ef33`. | May be cited as existing architectural truth. Note: `SOURCE_COMPLETE` still requires `OWNER_ACCEPTED` before production release. |
| **`EMPIRICAL_RESEARCH`** | Established by peer-reviewed scientific publications (PNAS, Nature Scientific Reports, PSPI, EEF) or official government/industry statistical datasets (ÖSYM YKS annual reports, RevenueCat benchmark dataset). | Used to ground pedagogical algorithms, anti-crutch guardrails, and baseline funnel ranges. |
| **`COMPETITOR_MARKETING_CLAIM`** | Capabilities or accuracy rates advertised by competitors (Kunduz, Doping Hafıza, Raunt, Photomath, Gauth, etc.) without independent symbolic audit. | Must never be treated as verified technical parity; must be paired with an architectural teardown (`product/82`). |
| **`FOUNDER_HYPOTHESIS`** | Strategic assumption about student behavior, retention lift, feature preference, or willingness to pay that has not yet been proven on live Verified AI cohorts. | Must be linked to a falsifiable experiment ID (`EXP-LOS-*`) and stage gate (`ADR-011`). |
| **`FINANCIAL_MODEL_ASSUMPTION`** | Bottom-up economic input or output (conversion rate, ARPU, churn, cache hit ratio, blended cost per solve, 24-month ARR). | Must always be presented across sensitivity scenarios (`Pessimistic / Base / Optimistic / Exceptional` in `roadmap/88`). |
| **`ENGINEERING_ESTIMATE`** | Projected p95 latency, deterministic template coverage %, sprint duration, or database table sizing for unbuilt/planned modules (`V037+`). | Must be verified by load/integration tests during the target sprint's acceptance run. |
| **`NOT_VERIFIED`** | Any claim lacking code proof, empirical citation, or owner test execution (including live cloud provider routing in production and 50-student TestFlight cohort results). | Cannot be used to authorize Phase 8–13 production rollout until empirically resolved. |

---

## 3. Master Evidence Classification Ledger (Repository-Wide Audit)

| Ledger ID | Claim / Statement | Canonical Source Doc(s) | Epistemic Classification | Verification Evidence or Falsification Condition |
|---|---|---|---|---|
| **`EVD-001`** | Flyway migrations `V001`–`V023` and `V027`–`V036` exist in `services/api/src/main/resources/db/migration/platform/`; current head is `V036__attempt_mistake_classifications.sql`. | `roadmap/87`, `architecture/90` | `SOURCE_VERIFIED` | Direct filesystem inspection at `e39bafe88ea369f98c96a65497a94462dc79ef33`. (`V024`–`V026` were skipped in numbering; `db/planned/V025` is unexecuted). |
| **`EVD-002`** | Sprint 7.3 (`AttemptEvaluation` `V035`, first-error detection) passed full owner acceptance. | `sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.4_EXECUTION_REPORT.md` | `SOURCE_VERIFIED` | Owner acceptance run `20261107-025110-XrMKJK` (`LOCAL_ACCEPTANCE_PASSED`). |
| **`EVD-003`** | Sprint 7.4 (`AttemptMistakeClassification` `V036`, `MistakeTaxonomy` `mistake-category-v1`) is implemented in source code, and B/D defects from run `20261107-114912-CFdjVI` were repaired in commit `e39bafe`, but a fresh `--full` owner acceptance run is still pending. | `sprints/phase07_attempt_mistake_and_mastery_intelligence/SPRINT_7.4_EXECUTION_REPORT.md`, `roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md` | `SOURCE_VERIFIED` (Code) / `NOT_VERIFIED` (Final Owner Sign-Off) | Requires owner execution of `bash scripts/quality/sprint_7_4_owner_acceptance.sh --full`. |
| **`EVD-004`** | `MistakeTaxonomy.java` defines 10 top-level categories, 17 specific mistake codes, and 5 explicit canonical skill mappings (`ALG_SIGN_FLIP_DISTRIBUTE`, `ALG_FRACTION_ADD_DENOM`, `INEQ_NEGATIVE_MULT_NO_FLIP`, `FUNC_COMPOSITION_ORDER`, `CALC_CHAIN_RULE_OMITTED`). | `learning/83`, `architecture/90` | `SOURCE_VERIFIED` | Direct inspection of `services/api/src/main/java/com/verifiedai/mistake/MistakeTaxonomy.java`. |
| **`EVD-005`** | `LearnerMasteryPolicy.java` and `V031` implement `bkt-lite-v1` with `score` $\in [0.05, 0.99]$, `half_life_days` $\in [1, 90]$, `evidence_count`, and `confidence` $\in [0, 1]$. | `learning/86`, `architecture/90` | `SOURCE_VERIFIED` | Direct inspection of `V031` SQL and `com.verifiedai.attempt.LearnerMasteryPolicy`. |
| **`EVD-006`** | `PostgresAiSpendGuard.java` and migrations `V016`/`V017` enforce deterministic daily and monthly AI spend ceilings in PostgreSQL. | `ai/60`, `roadmap/80`, `roadmap/88` | `SOURCE_VERIFIED` | Direct inspection of `com.verifiedai.ai.PostgresAiSpendGuard` and `V016`/`V017`. |
| **`EVD-007`** | Blended variable AI solver cost is `$0.005813 / solve` yielding an `86.0%` gross margin at `150 solves/month` on a `$9.99/mo` plan. | `ai/60`, `roadmap/80`, `roadmap/88` | `FINANCIAL_MODEL_ASSUMPTION` + `ENGINEERING_ESTIMATE` | Arithmetic verified on paper, but **live cloud traffic is `NOT_VERIFIED`** until `Sprint 6.2B` and `Sprint 11.9A` measure real vision/OCR + solver + twin token consumption and cache hit rates. |
| **`EVD-008`** | 50-student TestFlight commercial validation cohort (`Sprint 11.9A`) and `ADR-011` `GO` decision. | `sprints/02`, `adr/ADR-011`, `roadmap/87` | `NOT_VERIFIED` (`0 / 50` students tested at baseline) | Blocks Phase 8–13 production rollout until executed and signed off by owner. |
| **`EVD-009`** | Unguarded generative AI access ("GPT Base") during high-school math practice causes a `-17%` post-test score penalty when removed, whereas scaffolded tutoring ("GPT Tutor") prevents this penalty. | `product/81`, `learning/84`, `learning/86` | `EMPIRICAL_RESEARCH` | Bastani et al. (2025), PNAS `10.1073/pnas.2422633122` ($N \approx 1,000$ high-school students). |
| **`EVD-010`** | Pedagogically structured AI tutoring outperforms in-class active learning in undergraduate STEM while taking less study time. | `product/81`, `learning/85`, `learning/86` | `EMPIRICAL_RESEARCH` | Kestin et al. (2025), Nature Scientific Reports `10.1038/s41598-025-97652-6` (Harvard RCT). |
| **`EVD-011`** | Retrieval practice and distributed (spaced) practice have *High Utility* across learner ages and domains; interleaved practice has *Moderate Utility* for mathematical problem discrimination; passive rereading has *Low Utility*. | `learning/84`, `learning/86` | `EMPIRICAL_RESEARCH` | Dunlosky et al. (2013), *Psychological Science in the Public Interest*. |
| **`EVD-012`** | In 2024-YKS, `3,120,870` candidates applied and `2,819,362` attended TYT (`1,776,496` attended AYT); in 2025-YKS, `2,560,649` applied and `2,351,641` attended TYT (`1,549,940` attended AYT). | `product/82` | `VERIFIED_PUBLIC_FACT` / `EMPIRICAL_RESEARCH` | Official ÖSYM 2024-YKS and 2025-YKS Evaluation Reports. |
| **`EVD-013`** | Education subscription apps see `51%–59%` annual plan share, `~$0.30` median Day-14 RPI, `~2.1%` freemium D35 download-to-paid conversion (`~10.7%` hard paywall), and `~27%–28%` Year-1 retention. | `product/82`, `roadmap/88` | `VERIFIED_PUBLIC_FACT` / `EMPIRICAL_RESEARCH` | RevenueCat *State of Subscription Apps (2025–2026)* report. |
| **`EVD-014`** | Turkish and global competitors (Doping Hafıza, Raunt, Kunduz, Photomath, Gauth) claim "AI personalized study plans" or "instant step solutions." | `product/82` | `COMPETITOR_MARKETING_CLAIM` | Technical teardown (`product/82`) shows competitors either grade binary MCQ test outcomes without step inspection or solve the prompt without diagnosing the student's own multi-step draft. |
| **`EVD-015`** | Tier-1 deterministic parametric templates in `attempt_learning.py` can fulfill $\ge 80\%$ of high-school algebra, inequality, function, and introductory calculus twin requests in `< 150 ms` at `$0.00` LLM token cost. | `learning/84`, `roadmap/88` | `ENGINEERING_ESTIMATE` | Validated on initial error families in `V031`; full 8-family coverage to be verified in `Sprint 8.2` golden tests. |
| **`EVD-016`** | Requiring a 60-second solution lock on Verified Twins and capping `HINT_ASSISTED` mastery at `0.78` increases 7-day delayed unassisted retention ($\text{DTIR}_{7\text{d}}$) to $\ge 65\%$ without causing $> 15\%$ session abandonment. | `learning/84`, `learning/86`, `quality/91` | `FOUNDER_HYPOTHESIS` | To be empirically tested in `EXP-LOS-01` and `EXP-LOS-02` (`Sprint 11.9A` & `Sprint 10.5`). |

---

## 4. Controlled Experiments Register (`EXP-LOS-01` through `EXP-LOS-10`)

Every major product, pedagogical, and monetization assumption in the Learning OS is bound to a structured experiment with pre-registered success and guardrail criteria:

| Exp ID | Target Sprint / Phase | Hypothesis Under Test (`FOUNDER_HYPOTHESIS`) | Experimental Design (Control vs. Treatment) | Primary Success Metric | Guardrail Metric (Must Not Degrade) | Decision Rule (`SHIP / ITERATE / REVERT`) |
|---|---|---|---|---|---|---|
| **`EXP-LOS-01`** | `Sprint 11.9A` (TestFlight $N=50$) | Showing a **Concrete Counterexample Card ($x=c$)** alongside the First-Error Step Diff increases immediate Verified Twin attempt start rate. | Within-subject / split problem families: Step Diff only vs. Step Diff + Counterexample Proof Card (`H-01`). | Twin Start Rate $\ge 35\%$ (`+20%` relative lift over Step Diff only). | Diagnosis screen dwell bounce rate $\le 25\%$. | **SHIP** if Twin Start $\ge 35\%$; **ITERATE** copy if students find counterexample notation dense. |
| **`EXP-LOS-02`** | `Sprint 7.8` / `11.9A` | A **60-second Solution Lock** on the Verified Twin prevents passive answer clicking while maintaining high completion. | Compare `30s` lock vs. `60s` lock vs. `1-Tap First-Step Micro-Choice` unlock (`Sprint 10.5`). | Immediate Unassisted Twin Transfer Rate ($\text{UTTR}_{\text{imm}}$) $\ge 55\%$. | Twin abandonment rate during lock window $\le 30\%$. | If abandonment $> 30\%$, **PIVOT** lock to *"Select the 1st valid operation to unlock step entry"* (`PIVOT_UX` in `roadmap/87`). |
| **`EXP-LOS-03`** | `Sprint 7.7` / `8.4` | Framing recurring errors as an externalized **"Exam Trap Passport (`Sınav Tuzağı Pasaportu`)"** drives higher weekly return visits than a standard "Mistake History" list. | Cohort comparison / A/B copy & layout test (`Exam Trap Radar` vs. `Mistake Log`). | Day-7 Retention (`D7`) $\ge 20\%$ and Passport tab weekly open rate $\ge 45\%$. | Student dispute/hide rate $\le 5\%$. | **SHIP** Exam Trap Passport framing globally if `D7 >= 20%`. |
| **`EXP-LOS-04`** | `Sprint 8.3` / `10.5` | **2-Minute "Spot the Flaw" Reverse Verification Warmup (`H-04`)** increases daily habit streak continuation on busy school days. | 50/50 user split: Standard 3-problem Daily Coach vs. 1 "Spot the Flaw" warmup + 2 Twins. | Day-14 Streak Retention lift $\ge +15\%$. | Total daily verified twin completions must not drop by $> 10\%$. | **SHIP** to Free/Pro daily warmup if D14 streak lifts without cannibalizing twin solving. |
| **`EXP-LOS-05`** | `Sprint 8.5` | Triggering a **90-second Interactive Micro-Lesson** after 2 consecutive twin failures rescues learners who are stuck on a structural misconception. | Compare immediate 3rd Twin retry vs. 90s Micro-Lesson card -> 3rd Twin retry. | Subsequent unassisted Twin pass rate $\ge 60\%$. | Micro-lesson skip/abandon rate $\le 25\%$. | **SHIP** mandatory micro-lesson trigger after 2nd failure if subsequent pass rate $\ge 60\%$. |
| **`EXP-LOS-06`** | `Sprint 8.6` / `10.5` | The **1-Click Weekly Tutor/Parent PDF Share Packet (`H-06`)** acts as a zero-CAC organic acquisition and retention loop. | Track cohort of students who export $\ge 1$ Weekly PDF vs. non-exporters, plus QR/link attribution. | D30 retention of exporters $\ge 1.4\times$ non-exporters; viral K-factor $\ge 0.10$. | Zero privacy complaints or accidental PII exposure (`G-PRIV-03`). | **SHIP** and promote to primary Sunday evening Coach notification if K-factor $\ge 0.10$. |
| **`EXP-LOS-07`** | `Sprint 9.7` | Displaying the **YKS Net-Gain Simulator (`+\Delta Net / hr`, `H-03`)** increases study plan adherence on high-weight AYT topics (Calculus, Functions, Polynomials). | Standard mastery-sorted topic list vs. Expected Net-Gain ($\Delta\text{Net}/\text{hr}$) ranked list. | Completion rate of recommended daily study tasks $\ge 50\%$. | Learner anxiety / overwhelm feedback $< 5\%$. | **SHIP** Net-Gain ranking as default for YKS TYT/AYT tracks. |
| **`EXP-LOS-08`** | `Sprint 10.2` | Triggering the **Contextual Upgrade Paywall immediately after a student unlocks their 1st Independent Mastery Shield** converts better than a hard quota wall alone (`EXP-MON-01`). | Control: Quota-exhaustion paywall only. Treatment: Mastery-Shield celebration upsell + Quota wall. | Trial start rate relative lift $\ge +25\%$. | D1/D7 retention of Free users must not drop by $> 5\%$. | **SHIP** contextual celebration paywall if trial start lifts $\ge +25\%$. |
| **`EXP-LOS-09`** | `Sprint 10.2` | Offering a **6-Month YKS Exam Season Pass (`1,699 TRY`)** alongside Monthly and Annual plans increases net revenue per install in Turkey (`EXP-MON-02`). | StoreKit paywall layout test: 2-plan (`Monthly + Annual`) vs. 3-plan (`Monthly + 6M YKS Pass + Annual`). | Day-30 Net RPI lift $\ge +18\%$. | Annual plan cannibalization must stay net-revenue positive. | **SHIP** 3-plan seasonal paywall between October and May. |
| **`EXP-LOS-10`** | `Sprint 10.5` | **Randomized 14-Day Crossover Learning Outcome Trial** (`learning/86` Section 6.2): Full Learning OS Loop (Step Diagnosis + 60s Locked Twins + Spaced Passport) vs. Solver + Step Diagnosis Only. | 14-day crossover RCT ($N \ge 200$ consenting learners) culminating in an unassisted 10-item timed post-test. | Statistically significant ($p < 0.05$, Cohen's $d \ge 0.25$) unassisted post-test score advantage for Full Learning OS arm. | Blended AI inference cost per learner remains within `$1.30/mo` budget. | Publishes verified empirical efficacy proof for product positioning and B2B2C expansion. |

---

## 5. Governance Rules for Updating This Ledger

1. **No Silent Promotion of Assumptions:** An entry tagged `FOUNDER_HYPOTHESIS`, `FINANCIAL_MODEL_ASSUMPTION`, or `NOT_VERIFIED` can only be promoted to `SOURCE_VERIFIED` or `EMPIRICAL_RESEARCH` after the corresponding owner acceptance script or cohort experiment completes and its run ID / statistical summary is recorded in this ledger.
2. **Sprint Exit Gate Requirement:** Every sprint execution report from `Sprint 7.5` onward must include an **Epistemic Status Update** table referencing the `EVD-*` and `EXP-LOS-*` rows affected by that sprint.

---

## 6. Cross-References

- `00_MASTER_INDEX.md`
- `product/81_LEARNING_OS_PRODUCT_VISION_AND_POSITIONING.md`
- `product/82_LEARNING_OS_MARKET_COMPETITOR_AND_CUSTOMER_RESEARCH.md`
- `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`
- `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`
- `learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md`
- `learning/86_LEARNING_OUTCOMES_RETRIEVAL_PRACTICE_AND_MASTERY_EVALUATION.md`
- `roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md`
- `roadmap/88_LEARNING_OS_MONETIZATION_GROWTH_AND_VALIDATION_PLAN.md`
- `security/89_STUDENT_COACH_PRIVACY_MINOR_SAFETY_AND_TEACHER_ACCESS.md`
- `architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md`
- `quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`
