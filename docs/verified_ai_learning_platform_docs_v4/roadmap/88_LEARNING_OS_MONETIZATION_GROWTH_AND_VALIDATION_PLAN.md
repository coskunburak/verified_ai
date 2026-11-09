# 88 — Learning OS Monetization, Growth, and Commercial Validation Plan

## Document Metadata

| Field | Value |
|---|---|
| **Document ID** | `DOC-88` |
| **Canonical Path** | `roadmap/88_LEARNING_OS_MONETIZATION_GROWTH_AND_VALIDATION_PLAN.md` |
| **Title** | Learning OS Monetization, Growth, and Commercial Validation Plan |
| **Status** | `CANONICAL_COMMERCIAL_AND_FINANCIAL_SPECIFICATION` |
| **Owner** | EdTech Business & Monetization Strategist / Founder |
| **Date / Version** | `2026-11-07` / `v1.0.0` |
| **Baseline Commit** | `e39bafe88ea369f98c96a65497a94462dc79ef33` (`main`) |
| **Governing ADRs** | `adr/ADR-003_PROVIDER_NEUTRAL_AI.md`, `adr/ADR-006_PRODUCTION_STUDENT_DATA_NOT_TRAINING_DATA_BY_DEFAULT.md`, `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md` |

---

## 1. Purpose

This document defines the monetization architecture, subscription packaging, unit-economics audit, 24-month four-scenario financial model (`Pessimistic`, `Base`, `Optimistic`, `Exceptional`), ethical monetization guardrails, and commercial validation experiments for the **Verified AI Learning OS**.

It extends `product/05_MONETIZATION_AND_ENTITLEMENTS.md`, `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`, and `roadmap/80_UNIT_ECONOMICS_30_DAY_LAUNCH_CONTRACT_AND_EXECUTION_ROADMAP.md` from a single-tier solver subscription into a multi-tier **Learning OS monetization engine**.

---

## 2. Sources and Evidence Classification

- `SOURCE_VERIFIED`: Verified against `services/api/src/main/java/com/verifiedai/billing/` (`EntitlementApplicationService`, `CapabilityAccessPolicy`, `AppleBillingApplicationService`), `services/api/src/main/java/com/verifiedai/ai/PostgresAiSpendGuard.java` (`V016`, `V017`), `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`, and `roadmap/80_UNIT_ECONOMICS_30_DAY_LAUNCH_CONTRACT_AND_EXECUTION_ROADMAP.md`.
- `EMPIRICAL_RESEARCH` / `VERIFIED_PUBLIC_FACT`: RevenueCat *State of Subscription Apps (2025–2026)* Education category benchmarks (`product/82_LEARNING_OS_MARKET_COMPETITOR_AND_CUSTOMER_RESEARCH.md`) and Apple App Store Small Business Program (`15%` commission under `$1M` annual proceeds).
- `FINANCIAL_MODEL_ASSUMPTION`: Projected user acquisition, trial-to-paid conversion, retention, solve volume per active user, and cloud hosting tiers.
- `FOUNDER_HYPOTHESIS`: Upgrade lift driven by Mistake Passport, Verified Twin streaks, and YKS Net-Gain Simulator.

---

## 3. Critical Audit of Existing Unit Economics Claims (`roadmap/80` & `ai/60`)

To maintain strict epistemic hygiene, every number in `roadmap/80_UNIT_ECONOMICS_30_DAY_LAUNCH_CONTRACT_AND_EXECUTION_ROADMAP.md` and `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md` is audited below:

| Claim in Existing Docs (`roadmap/80`, `ai/60`) | Audited Classification | Technical & Financial Reality Check |
|---|---|---|
| **Daily/Monthly AI Spend Guard (`PostgresAiSpendGuard`, `V016`, `V017`)** | `SOURCE_VERIFIED` | Implemented in Java and PostgreSQL; deterministically blocks paid inference when budget cap is reached. |
| **Blended Solver Inference Cost `$0.005813 / solve`** | `FINANCIAL_MODEL_ASSUMPTION` + `ENGINEERING_ESTIMATE` | Arithmetic is internally consistent *assuming* an `85% / 12% / 3%` tier route split and `25%` cache hit ratio. **However, zero live cloud traffic has been measured yet (`Sprint 6.2B` pending).** Under Learning OS usage (Vision OCR step scan + First-Error Diagnosis + Socratic Hint + Hybrid Twin fallback), uncached multimodal sessions can cost `$0.0085–$0.0140` unless Tier-1 deterministic templates (`$0.00` AI cost, `learning/84`) handle $\ge 80\%$ of twins. |
| **`86.0%` Gross Margin on `$9.99/mo` Pro Subscription** | `FINANCIAL_MODEL_ASSUMPTION` | Assumes `15%` Apple fee, `150 solves/month`, and zero refund/uncollected tax drag. In Turkey, VAT (`KDV 20%`) and localized PPP pricing (`199–399 TRY/mo`) reduce net USD proceeds per domestic subscriber unless balanced by `Coach Pro`, `YKS Season Pass`, and international `$14.99–$19.99/mo` tiers. |
| **Month-12 Base `365` Paid Subscribers / `$41,610` ARR in `roadmap/80`** | `FINANCIAL_MODEL_ASSUMPTION` | Plausible solo-founder projection, NOT yet empirically validated (`0/50` TestFlight users tested). |

---

## 4. Learning OS Packaging, Tiers, and Entitlement Matrix

### 4.1 Consumer & B2B2C Tier Catalog

| Tier / SKU Code | Target Persona | Turkey PPP Price (`TRY` incl. VAT) | Global Tier-1 Price (`USD`) | Core Entitlements & Daily/Monthly Quotas (`com.verifiedai.billing`) |
|---|---|---|---|---|
| **1. `FREE` (Discovery & Habit Hook)** | Casual High School & YKS Explorers | `0 TRY` | `$0.00` | • **3 Verified Problem/Attempt Checks per day** (deterministic + standard AI)<br>• **First-Error Step Diagnosis + Counterexample Card** on every free attempt<br>• **1 Verified Twin ("Prove It Solo") per day**<br>• **Top 2 Active Exam Traps** visible in Mistake Passport<br>• **1 Onboarding 10-Question Diagnostic** |
| **2. `PRO` (`pro_monthly` / `pro_annual`)** | Regular High School (Grades 9–11) & Self-Study Learners | `199 TRY / mo`<br>`1,499 TRY / yr` (`~125 TRY/mo`) | `$9.99 / mo`<br>`$69.99 / yr` | • **150 Verified Attempt/Problem Checks per month** (soft burst up to 15/day)<br>• **Unlimited Deterministic Verified Twins** + 30 Hybrid AI Twins/mo<br>• **Full Mistake Passport & Mistake DNA History** + OCR Dispute Rollback<br>• **3-Rung Socratic Hint Ladder**<br>• **Skill Mastery & Retention Half-Life Map** |
| **3. `COACH_PRO` (`coach_pro_monthly` / `coach_pro_annual`)** | High-Intent YKS (TYT/AYT), IB, AP & SAT Candidates | `349 TRY / mo`<br>`2,499 TRY / yr` (`~208 TRY/mo`) | `$16.99 / mo`<br>`$119.99 / yr` | • **Everything in `PRO`**, plus:<br>• **400 Verified Attempt/Problem Checks per month**<br>• **Daily 15-Minute Personal AI Math Coach** + Spaced Review Queue<br>• **90-Second Interactive Micro-Lessons** for recurring traps<br>• **YKS / Exam Readiness Credible-Interval Gauge & Net-Gain Simulator**<br>• **4 Timed Mini-Mock Assessments per month** with trap attribution<br>• **1-Click Weekly Tutor/Parent PDF Share Packet** |
| **4. `YKS_SEASON_PASS` (`coach_pro_season_6m`)** | Grade 12 & Mezun YKS Sprint Cohort (Nov–June) | `1,699 TRY / 6 months` (One-time non-renewing or seasonal) | `$79.99 / 6 months` | • Full `COACH_PRO` entitlements through exam day + **Pre-Exam 48-Hour Trap Immunity Cram Packs (`H-05`)**. Aligns with Turkish families' preference to buy a single exam-countdown package without recurring monthly friction. |
| **5. `TUTOR_COPILOT_PACK` *(Phase 12.7 B2B2C)*** | Private Math Tutors & Boutique Exam Coaches | `999 TRY / mo` (Includes tutor seat + 5 student links; `+149 TRY` per extra student) | `$39.99 / mo` | • Consent-gated view of linked students' **Mistake Passports** & **Weekly Readiness Summaries** prior to tutoring lessons<br>• 1-click assignment of **Verified Twin Homework Packs**. |
| **6. `INSTITUTION_PILOT` *(Conditional Phase 14)*** | Boutique Dershanes & Private High Schools (25–100 seats) | Custom annual invoice (`~1,200 TRY / student / yr`) | Custom (`$45 / student / yr`) | • Classroom-level anonymized mistake heatmaps + bulk diagnostic administration. |

### 4.2 Variable Cost Per Active User by Tier (`ENGINEERING_ESTIMATE` + `FINANCIAL_MODEL_ASSUMPTION`)

Because **Tier-1 Deterministic Twins (`attempt_learning.py`)**, **Symbolic Step Diffs (`attempt_verification_v2.py`)**, **Mistake DNA Beta-Binomial math**, and **BKT-lite mastery updates** run on CPU in Java/Python at **`$0.00` external LLM token cost**, the Learning OS dramatically increases perceived coaching value while adding minimal marginal AI spend:

| Cost Component | `FREE` Active User / Mo | `PRO` Subscriber / Mo | `COACH_PRO` Subscriber / Mo | Cost Control Mechanism (`SOURCE_VERIFIED`) |
|---|---|---|---|---|
| **Solver & Step OCR/Diagnosis Calls** | 12 attempts (`~$0.069`) | 90 attempts (`~$0.523`) | 180 attempts (`~$1.046`) | `PostgresAiSpendGuard` + SHA-256 canonical cache (`25–35%` hit rate) |
| **Deterministic Twin Generation (~80% share)** | 8 twins (`$0.000`) | 60 twins (`$0.000`) | 120 twins (`$0.000`) | SymPy parametric templates in `services/math-verifier/` |
| **Hybrid LLM Twin & Socratic/Micro-Lesson Calls** | 2 turns (`~$0.008`) | 25 turns (`~$0.105`) | 60 turns (`~$0.252`) | Schema-constrained Tier-1 mini models (`gpt-4o-mini` / `claude-3-5-haiku` / `gemini-2.5-flash`) |
| **Total Monthly Variable AI Inference Cost** | **`~$0.077 / mo`** | **`~$0.628 / mo`** | **`~$1.298 / mo`** | Hard per-user monthly circuit breaker at `$2.50` (`PRO`) and `$4.20` (`COACH_PRO`) |

---

## 5. 24-Month Four-Scenario Financial Model (`FINANCIAL_MODEL_ASSUMPTION`)

All four scenarios model a solo-founder operation starting commercial billing after the `ADR-011` TestFlight gate, blending Turkish PPP subscriptions (`PRO` ~$5.00 net of VAT, `COACH_PRO` ~$8.75 net of VAT, `YKS_SEASON_PASS`) and rising international USD subscriptions (`$9.99–$16.99/mo`) from Month 9 onward. Store commission is modeled at **15%** (Apple Small Business Program / Google Play first `$1M` tier).

### 5.1 Scenario Assumptions Summary

| Parameter | 1. Pessimistic (`Slow Organic / High Friction`) | 2. Base (`Recommended Learning OS V1 Plan`) | 3. Optimistic (`Strong YKS Creator & Referral Loop`) | 4. Exceptional (`Breakout YKS + Android + EN Tier-1`) |
|---|---|---|---|---|
| **Download-to-Trial / Free Activation** | `18%` | `28%` | `35%` | `42%` |
| **Free-to-Paid Conversion (D35)** | `1.2%` | `2.4%` *(RevenueCat Freemium median ~2.1%)* | `3.8%` | `5.2%` |
| **Plan Mix (`Coach Pro + Season` vs `Pro`)** | `30%` Coach / `70%` Pro | `50%` Coach / `50%` Pro | `65%` Coach / `35%` Pro | `70%` Coach+Tutor / `30%` Pro |
| **Blended Gross Monthly ARPU (USD eq.)** | `$6.40` | `$8.20` | `$9.60` | `$11.20` |
| **Blended Monthly Paid Churn (Effective)** | `9.5%` | `6.5%` | `4.8%` | `3.8%` |
| **Refund Rate** | `3.5%` | `2.0%` | `1.5%` | `1.2%` |

---

### 5.2 Scenario 1: `PESSIMISTIC` (Slow Adoption / Conservative Conversion)

| Metric | Month 1 | Month 3 | Month 6 | Month 12 | Month 24 |
|---|---|---|---|---|---|
| **Active Free Users (MAU)** | 250 | 600 | 1,200 | 2,400 | 4,500 |
| **Monthly Trial Starts** | 20 | 45 | 85 | 160 | 290 |
| **New Paid Conversions** | 3 | 8 | 15 | 29 | 54 |
| **Active Paid Subscribers (EOP)** | **3** | **16** | **48** | **135** | **310** |
| **Gross Subscription Revenue (`USD`)** | `$19` | `$102` | `$307` | `$864` | `$1,984` |
| **Store Commission (15%) & Refunds (3.5%)** | `-$4` | `-$19` | `-$57` | `-$160` | `-$367` |
| **Variable AI Cost (Free + Paid)** | `-$22` | `-$58` | `-$128` | `-$285` | `-$580` |
| **Infra & Tooling Fixed/Var Cost** | `-$65` | `-$65` | `-$85` | `-$110` | `-$160` |
| **Net Contribution / Operating Cashflow** | **`-$72`** | **`-$40`** | **`+$37`** | **`+$309`** | **`+$877`** |

*Takeaway:* Even in the Pessimistic case, strict deterministic-first architecture keeps monthly cash burn under `$75/mo` in Months 1–3 and reaches infrastructure self-sustainability by Month 6.

---

### 5.3 Scenario 2: `BASE` (Target Solo-Founder Learning OS V1 Trajectory)

| Metric | Month 1 | Month 3 | Month 6 | Month 12 | Month 24 |
|---|---|---|---|---|---|
| **Active Free Users (MAU)** | 450 | 1,400 | 3,500 | 8,500 | 24,000 |
| **Monthly Trial Starts** | 45 | 140 | 340 | 820 | 2,300 |
| **New Paid Conversions** | 11 | 34 | 84 | 204 | 576 |
| **Active Paid Subscribers (EOP)** | **11** | **68** | **245** | **740** | **2,650** |
| **Gross Subscription Revenue (`USD`)** | `$90` | `$558` | `$2,009` | `$6,068` | `$21,730` |
| **Store Commission (15%) & Refunds (2.0%)** | `-$15` | `-$95` | `-$342` | `-$1,032` | `-$3,694` |
| **Variable AI Cost (Free + Paid)** | `-$45` | `-$172` | `-$495` | `-$1,360` | `-$4,380` |
| **Infra & Growth/ASO Budget** | `-$75` | `-$110` | `-$220` | `-$450` | `-$1,200` |
| **Net Contribution / Operating Cashflow** | **`-$45`** | **`+$181`** | **`+$952`** | **`+$3,226`** | **`+$12,456`** |
| **Annualized Run-Rate Revenue (ARR)** | `$1,080` | `$6,696` | `$24,108` | **`$72,816`** | **`$260,760`** |

---

### 5.4 Scenario 3: `OPTIMISTIC` (Strong YKS Seasonality + Tutor PDF Viral Loop)

| Metric | Month 1 | Month 3 | Month 6 | Month 12 | Month 24 |
|---|---|---|---|---|---|
| **Active Free Users (MAU)** | 800 | 3,000 | 8,000 | 22,000 | 65,000 |
| **Monthly Trial Starts** | 95 | 360 | 950 | 2,600 | 7,500 |
| **New Paid Conversions** | 30 | 114 | 304 | 836 | 2,470 |
| **Active Paid Subscribers (EOP)** | **30** | **210** | **820** | **2,850** | **9,800** |
| **Gross Subscription Revenue (`USD`)** | `$288` | `$2,016` | `$7,872` | `$27,360` | `$94,080` |
| **Store Commission (15%) & Refunds (1.5%)** | `-$48` | `-$333` | `-$1,299` | `-$4,514` | `-$15,523` |
| **Variable AI Cost (Free + Paid)** | `-$92` | `-$445` | `-$1,480` | `-$4,720` | `-$15,100` |
| **Infra, Creator Affiliates & Growth Spend** | `-$100` | `-$300` | `-$950` | `-$3,200` | `-$11,500` |
| **Net Contribution / Operating Cashflow** | **`+$48`** | **`+$938`** | **`+$4,143`** | **`+$14,926`** | **`+$51,957`** |
| **Annualized Run-Rate Revenue (ARR)** | `$3,456` | `$24,192` | `$94,464` | **`$328,320`** | **`$1,128,960`** |

---

### 5.5 Scenario 4: `EXCEPTIONAL` (Breakout YKS + Android Launch M10 + International IB/AP/SAT)

| Metric | Month 1 | Month 3 | Month 6 | Month 12 | Month 24 |
|---|---|---|---|---|---|
| **Active Free Users (MAU)** | 1,500 | 6,500 | 18,000 | 55,000 | 160,000 |
| **Monthly Trial Starts** | 210 | 910 | 2,500 | 7,700 | 22,000 |
| **New Paid Conversions** | 78 | 338 | 936 | 2,860 | 8,320 |
| **Active Paid Subscribers (EOP)** | **78** | **590** | **2,450** | **8,900** | **31,500** |
| **Gross Subscription Revenue (`USD`)** | `$874` | `$6,608` | `$27,440` | `$99,680` | `$352,800` |
| **Store Commission & Refunds** | `-$142` | `-$1,070` | `-$4,445` | `-$16,148` | `-$57,154` |
| **Variable AI Cost (Free + Paid)** | `-$195` | `-$1,120` | `-$4,250` | `-$14,400` | `-$48,200` |
| **Infra, Paid UA & Contractor Support** | `-$200` | `-$900` | `-$4,000` | `-$18,000` | `-$65,000` |
| **Net Contribution / Operating Cashflow** | **`+$337`** | **`+$3,518`** | **`+$14,745`** | **`+$51,132`** | **`+$182,446`** |

---

## 6. Ethical Monetization Guardrails (`INV-MON-001`)

1. **Never Hold Diagnosis Hostage After Capture:** If the system accepts a student's free-quota attempt, it **MUST** display the First Meaningful Error Diagnosis and Counterexample without blurring the error location behind a surprise paywall. Paywalls gate *volume* (additional daily checks), *longitudinal depth* (unlimited Mistake Passport history, Daily 15-Min Coach queue), and *simulation tools* (YKS Net Simulator, Mock Packs).
2. **Zero Mid-Exam Interruption:** Never trigger a paywall modal during an active timed diagnostic or twin countdown.
3. **Proactive Trial-End Reminder:** Offer a 1-tap opt-in notification 48 hours before any 7-day trial converts (`Apple Guideline 3.1.2` best practice), maximizing parent/student trust and keeping refund rates `< 2.0%`.
4. **Strict Zero-Data-Sale & Zero-Ad Guarantee (`ADR-006`):** No third-party behavioral ad SDKs are ever embedded in the client; student problem data is never sold or used to train third-party foundation models.

---

## 7. Commercial Validation Experiments (`Sprint 10.2` & `Sprint 10.5`)

| Experiment ID | Hypothesis (`FOUNDER_HYPOTHESIS`) | Variants | Primary Metric & Success Gate |
|---|---|---|---|
| **`EXP-MON-01` (Contextual Trigger Timing)** | Triggering the Coach Pro upgrade sheet immediately after a student conquers their 1st Verified Twin converts better than triggering on raw solve-quota exhaustion. | **Control:** Paywall on 4th daily solve.<br>**Treatment:** Paywall after 1st "Mastery Shield Unlocked" Twin celebration + 4th solve. | Trial Start Rate $\ge +25\%$ relative lift without D7 retention degradation. |
| **`EXP-MON-02` (YKS 6-Month Season Pass vs Annual Only)** | Offering a non-recurring or seasonal 6-Month YKS Pass alongside Monthly/Annual increases total checkout completion in Turkey between November and March. | **Control:** Monthly + Annual only.<br>**Treatment:** Monthly + 6-Month YKS Pass + Annual. | Day-30 Net Revenue Per Install (RPI) $\ge +18\%$. |
| **`EXP-MON-03` (1-Click Tutor PDF Referral Loop)** | Including a clean *"Verified by Verified AI — Mistake Passport"* footer with a QR link on the exported 1-page Weekly Tutor Packet drives organic student & tutor acquisition. | Tracked via privacy-safe referral campaign code on `/v1/reports/weekly` share sheet. | Viral K-factor contribution $\ge 0.12$ new installs per active weekly exporter. |

---

## 8. Requirements, Assumptions, Non-Goals, and Acceptance Criteria

### 8.1 Requirements
- `MON-REQ-001`: `CapabilityAccessPolicy` (`com.verifiedai.billing`) must enforce distinct quota counters for `ATTEMPT_VERIFY`, `HYBRID_TWIN_GENERATE`, `COACH_DAILY_MISSION`, and `EXAM_SIMULATION`.
- `MON-REQ-002`: Every paid AI call must pass through `PostgresAiSpendGuard` (`V016`/`V017`) with per-tier and global daily spend ceilings.
- `MON-REQ-003`: StoreKit 2 signed transaction verification (`AppleBillingApplicationService`) must handle upgrades, downgrades, grace periods, and refunds idempotently.

### 8.2 Acceptance Criteria
1. Automated billing integration tests verify entitlement transitions across `FREE`, `PRO`, `COACH_PRO`, and `YKS_SEASON_PASS`.
2. Unit-economics telemetry dashboard (`Sprint 10.3`) reports real-time AI cost per active subscriber vs net revenue per subscriber.

---

## 9. Risks and Open Decisions

- **Risk `R-MON-01` (Heavy-User Abuse on "Unlimited" Claims):** Marketing "Unlimited AI" invites account sharing and runaway vision OCR token costs.
  - *Mitigation:* Never market raw "Unlimited LLM Calls." Market **Unlimited Deterministic Twin Drills** (`$0.00` AI cost) and explicit fair-use monthly caps (`150` checks on `PRO`, `400` checks on `COACH_PRO`) with automatic fallback to deterministic symbolic verification when AI quota is exhausted.
- **Open Decision `OD-MON-01`:** Whether to offer a 7-day free trial on the 6-Month YKS Season Pass or reserve the 7-day trial strictly for the Annual plan (recommended: 7-day trial on Annual plan; instant 3-day starter preview or direct purchase on Season Pass).

---

## 10. Cross-References

- `product/05_MONETIZATION_AND_ENTITLEMENTS.md`
- `product/82_LEARNING_OS_MARKET_COMPETITOR_AND_CUSTOMER_RESEARCH.md`
- `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`
- `roadmap/80_UNIT_ECONOMICS_30_DAY_LAUNCH_CONTRACT_AND_EXECUTION_ROADMAP.md`
- `roadmap/87_LEARNING_OS_STRATEGIC_PHASE_AND_SPRINT_EXPANSION_PROGRAM.md`
- `operations/92_LEARNING_OS_ASSUMPTIONS_EXPERIMENTS_AND_EVIDENCE_LEDGER.md`
