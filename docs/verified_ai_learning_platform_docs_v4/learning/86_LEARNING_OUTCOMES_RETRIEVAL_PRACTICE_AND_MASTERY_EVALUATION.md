# 86 — Learning Outcomes, Retrieval Practice, and Mastery Evaluation

## Document Metadata

| Field | Value |
|---|---|
| **Document ID** | `DOC-86` |
| **Canonical Path** | `learning/86_LEARNING_OUTCOMES_RETRIEVAL_PRACTICE_AND_MASTERY_EVALUATION.md` |
| **Title** | Learning Outcomes, Retrieval Practice, and Mastery Evaluation |
| **Status** | `CANONICAL_LEARNING_SCIENCE_AND_PSYCHOMETRICS_SPECIFICATION` |
| **Owner** | Educational Learning Science Researcher & Adaptive Learning Systems Designer |
| **Date / Version** | `2026-11-07` / `v1.0.0` |
| **Baseline Commit** | `e39bafe88ea369f98c96a65497a94462dc79ef33` (`main`) |
| **Governing ADRs** | `adr/ADR-004_SEPARATE_PYTHON_MATH_VERIFIER.md`, `adr/ADR-006_PRODUCTION_STUDENT_DATA_NOT_TRAINING_DATA_BY_DEFAULT.md`, `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md` |

---

## 1. Purpose

This document establishes the learning-science foundation, formal psychometric equations, and empirical evaluation methodology for the **Verified AI Learning OS**.

Specifically, it enforces a strict mathematical and product separation across **five distinct learner state quantities** that EdTech products routinely conflate:
1. **Observed Attempt Correctness ($O_{u,a}$)**
2. **Demonstrated Independent Competence ($I_{u,s}$)**
3. **Estimated Skill Mastery ($M_{u,s}$)**
4. **Time-Sensitive Retention Estimate ($R_{u,s}(t)$)**
5. **Exam Readiness & Expected Net Score ($\widehat{\text{Net}}_{u,E}$)**

---

## 2. Sources and Evidence Classification

- `SOURCE_VERIFIED`: Directly verified against `services/api/src/main/java/com/verifiedai/attempt/LearnerMasteryPolicy.java` (`bkt-lite-v1`), `V031__add_pedagogical_diagnosis_twin_drills_and_mastery.sql` (`learner_skill_mastery`), `learning/31_MASTERY_AND_KNOWLEDGE_GRAPH.md`, and `domain/46_CURRICULUM_SKILL_ONTOLOGY_AND_TAXONOMY.md`.
- `EMPIRICAL_RESEARCH`:
  - **Bastani et al. (2025), PNAS (`10.1073/pnas.2422633122`)**: Field RCT with ~1,000 high-school math students showing that unguarded LLM access ("GPT Base") boosted practice performance by +48% during practice but caused a **-17% drop in subsequent unassisted exam performance** relative to control due to cognitive offloading, whereas scaffolded tutoring ("GPT Tutor", +127% practice lift) mitigated the post-test penalty.
  - **Kestin et al. (2025), Nature Scientific Reports (`10.1038/s41598-025-97652-6`)**: Harvard RCT proving that an AI tutor engineered with explicit pedagogical scaffolding (active cognitive engagement, step-level feedback, anti-spoiler guardrails) produced **more than double the learning gain in less time** compared to active-learning classroom instruction.
  - **Dunlosky et al. (2013), Psychological Science in the Public Interest**: Meta-review rating **Retrieval Practice (Practice Testing)** and **Distributed Practice (Spaced Repetition)** as *High Utility*, **Interleaved Practice** as *Moderate Utility* (especially effective for mathematical problem-type discrimination), and passive rereading/highlighting as *Low Utility*.
  - **Education Endowment Foundation (EEF)**: *Teacher Feedback to Improve Pupil Learning* and *Metacognition and Self-Regulated Learning* guidance reports establishing that feedback only improves learning when learners actively process and apply it on a follow-up task, and that explicit Plan–Monitor–Evaluate cycles drive +7 months of equivalent academic progress.

---

## 3. Existing Architecture Relationship (`SOURCE_VERIFIED`)

In `V031` and `services/api/src/main/java/com/verifiedai/attempt/LearnerMasteryPolicy.java`, the repository already implements `bkt-lite-v1` with persisted columns on `learner_skill_mastery`:
- `mastery_version = 'bkt-lite-v1'`
- `score` $\in [0.0500, 0.9900]$
- `half_life_days` $\in [1.00, 90.00]$
- `evidence_count` $\ge 0$
- `confidence` $\in [0.0000, 1.0000]$
- `last_practiced_at`

This specification formalizes how `bkt-lite-v1` is extended in Phase 7 (`Sprints 7.6–7.10`), Phase 8 (`Sprints 8.1–8.4`), and Phase 9 (`Sprints 9.6–9.9`) so that assisted completions, spaced forgetting, prerequisite propagation, and exam readiness projections operate under rigorous, auditable equations.

---

## 4. Formal Mathematical Specification of the 5 Learner State Quantities

### 4.1 Quantity 1: Observed Attempt Correctness ($O_{u,a}$)
- **Definition:** The immediate symbolic evaluation outcome of learner $u$ on attempt $a$ (`V035` `AttemptEvaluation.overallOutcome`), accompanied by its assistance context `LearningEvidenceType` $e_a \in \{\text{INDEPENDENT}, \text{HINT\_ASSISTED}, \text{SOLUTION\_REVEALED\_ASSISTED}, \text{UNSUPPORTED}\}$.
- **Rule:** $O_{u,a} \in \{0, 1\}$ (or `NULL` when $e_a = \text{UNSUPPORTED}$). Observed correctness on a single assisted attempt is an *activity event*, **never** a measure of durable competence.

---

### 4.2 Quantity 2: Demonstrated Independent Competence ($I_{u,s} \in [0, 1]$)
- **Definition:** The empirical, lower-bound Wilson-smoothed proportion of **unassisted (`INDEPENDENT`) verified successes** achieved by learner $u$ on canonical skill $s$ over exponentially time-weighted attempts.
- **Formula:** Let $n_{u,s}^{\text{ind}+}$ be the exponentially decayed count of `INDEPENDENT` correct attempts on skill $s$ ($h = 28\text{d}$), and $n_{u,s}^{\text{total}}$ be the decayed count of all valid attempts on skill $s$ (excluding `UNSUPPORTED` and OCR-disputed attempts):
  $$I_{u,s} = \frac{n_{u,s}^{\text{ind}+} + \alpha_{\text{ind}} p_0}{n_{u,s}^{\text{total}} + \alpha_{\text{ind}}}, \qquad \alpha_{\text{ind}} = 3.0,\; p_0 = 0.25$$
- **Product Invariant (`INV-LRN-COMP-001`):** A learner who solves 20 problems on skill $s$ by clicking "Show Hint" or "Reveal Solution" every time has $n_{u,s}^{\text{ind}+} = 0$, so their Demonstrated Independent Competence $I_{u,s}$ decreases toward $0$, directly exposing the "crutch effect" and prompting a Verified Twin challenge.

---

### 4.3 Quantity 3: Estimated Skill Mastery ($M_{u,s}(t_k) \in [0.05, 0.99]$)
- **Definition:** The latent posterior probability that learner $u$ has mastered canonical skill $s$ immediately after attempt $k$ at timestamp $t_k$, computed via Evidence-Stratified Bayesian Knowledge Tracing (`bkt-lite-v1` / `bkt-lite-v2`).
- **Parameters per Skill Tier (`Standard` / `Advanced`):**
  - Prior $P(L_0) = 0.25$ (or prerequisite-informed prior $P_{\text{prereq}}(L_0)$).
  - Slip probability $p_S = 0.10$; Guess probability $p_G = 0.15$ (for open-ended symbolic step verification, guessing is lower than 5-choice MCQ).
  - Learning transition rate $p_T$:
    - `INDEPENDENT` pass: $p_T^{\text{ind}} = 0.18$ (or $0.24$ on a Verified Twin pass).
    - `HINT_ASSISTED` pass: $p_T^{\text{hint}} = 0.07$, with posterior hard-capped at $\min(M_{u,s}, 0.78)$.
    - `SOLUTION_REVEALED_ASSISTED`: $p_T^{\text{reveal}} = 0.00$; treated as an incorrect unassisted retrieval prior to reveal.
    - `UNSUPPORTED`: Identity update ($M_{u,s}(t_k) = M_{u,s}(t_{k-1})$).
- **Bayesian Update Equations:**
  Given prior mastery $m^- = R_{u,s}(t_k)$ (decayed to current attempt time $t_k$):
  - If `INDEPENDENT` Correct:
    $$m_{\text{post}} = \frac{m^-(1 - p_S)}{m^-(1 - p_S) + (1 - m^-)p_G}, \qquad M_{u,s}(t_k) = \text{clip}_{[0.05, 0.99]}\!\left(m_{\text{post}} + (1 - m_{\text{post}})p_T^{\text{ind}}\right)$$
  - If `HINT_ASSISTED` Correct:
    $$m_{\text{post}} = \frac{m^-(1 - 1.5 p_S)}{m^-(1 - 1.5 p_S) + (1 - m^-)(2 p_G)}, \qquad M_{u,s}(t_k) = \min\!\left(0.78,\; m_{\text{post}} + (1 - m_{\text{post}})p_T^{\text{hint}}\right)$$
  - If Incorrect (with Tier-1 causal mistake on skill $s$):
    $$m_{\text{post}} = \frac{m^- p_S}{m^- p_S + (1 - m^-)(1 - p_G)}, \qquad M_{u,s}(t_k) = \text{clip}_{[0.05, 0.99]}\!\left(m_{\text{post}} + (1 - m_{\text{post}})(0.03)\right)$$
- **Epistemic Confidence Score $C_{u,s} \in [0, 1]$ (`V031` `confidence`):**
  $$C_{u,s}(t) = \left(1 - e^{-N_{u,s}^{\text{eff}} / 5.0}\right) \cdot 2^{-\Delta t / (2 H_{u,s})}$$
  where $N_{u,s}^{\text{eff}} = n_{u,s}^{\text{ind}} + 0.4 n_{u,s}^{\text{hint}}$ is the effective independent-weighted evidence count.

---

### 4.4 Quantity 4: Time-Sensitive Retention Estimate ($R_{u,s}(t) \in [0.05, 0.99]$)
- **Definition:** The projected recall and execution probability of skill $s$ at any future or current time $t > t_{\text{last}}$, governed by Half-Life Regression (`V031` `half_life_days` $H_{u,s} \in [1.0, 90.0]$).
- **Decay Formula:**
  $$R_{u,s}(t) = m_{\text{floor}} + \left(M_{u,s}(t_{\text{last}}) - m_{\text{floor}}\right) \cdot 2^{-(t - t_{\text{last}}) / H_{u,s}}, \qquad m_{\text{floor}} = 0.05$$
- **Half-Life Update Rule (`Sprint 7.6` / `Sprint 8.3`):**
  - Initial half-life on first skill encounter: $H_0 = 3.0\text{ days}$.
  - On `INDEPENDENT` success when spacing interval $\Delta t \ge 0.5 H_{u,s}$ (true spaced retrieval):
    $$H_{u,s}^{\text{new}} = \min\!\left(90.0,\; H_{u,s} \cdot \left(1.85 + 0.35 \cdot \min\!\left(1.0, \frac{\Delta t}{H_{u,s}}\right)\right)\right)$$
  - On `INDEPENDENT` success during massed practice ($\Delta t < 0.5 H_{u,s}$, e.g., same-day drill):
    $$H_{u,s}^{\text{new}} = \min\!\left(90.0,\; H_{u,s} \cdot 1.20\right)$$
    *(Directly encodes Dunlosky et al. distributed practice law: spaced retrieval expands memory half-life dramatically more than same-hour cramming!)*
  - On causal mistake event:
    $$H_{u,s}^{\text{new}} = \max\!\left(1.0,\; 0.55 \cdot H_{u,s}\right)$$

---

### 4.5 Quantity 5: Exam Readiness & Expected Net Score ($\widehat{\text{Net}}_{u,E}$)
- **Definition:** The learner's projected score distribution on a target high-stakes exam $E$ (e.g., `TR_YKS_TYT_MATH` 40 items or `TR_YKS_AYT_MATH` 40 items, where $\text{Net} = \text{Correct} - 0.25 \cdot \text{Wrong}$), incorporating retention $R_{u,s}(t)$, active recurring traps $\theta_{u,m}$, and timed speed factor $\tau_{u,E}$.
- **Expected Item Success Probability $p_{u,s}^{\text{exam}}(t)$ on Skill $s$:**
  $$p_{u,s}^{\text{exam}}(t) = R_{u,s}(t) \cdot \left(1 - \sum_{m \in \text{Traps}(s)} \beta_m \theta_{u,m}\right) \cdot \tau_{u,E}$$
  where $\sum_{m} \beta_m \theta_{u,m} \in [0, 0.35]$ is the penalty from unresolved recurring exam traps on skill $s$, and $\tau_{u,E} \in [0.80, 1.00]$ is the learner's timed pacing completion factor.
- **Expected Net Score & Uncertainty Interval (`INV-EXAM-001`):**
  For an exam $E$ with $Q_E = \sum_{s \in E} w_{E,s} = 40$ questions and strategic omission threshold $p_{\text{omit}} = 0.30$:
  $$\widehat{\text{Net}}_{u,E}^{\text{mid}}(t) = \sum_{s \in E : p_{u,s}^{\text{exam}} \ge p_{\text{omit}}} w_{E,s} \cdot \left(p_{u,s}^{\text{exam}}(t) - 0.25 \cdot \left(1 - p_{u,s}^{\text{exam}}(t)\right)\right)$$
  Because early learners have low epistemic confidence $C_{u,s}$ on unseen topics, the UI **MUST** display the $90\%$ credible interval:
  $$\left[\widehat{\text{Net}}_{u,E}^{\text{low}},\; \widehat{\text{Net}}_{u,E}^{\text{high}}\right] = \widehat{\text{Net}}_{u,E}^{\text{mid}} \pm 1.645 \sqrt{\sum_{s \in E} w_{E,s}^2 \cdot \sigma_{u,s}^2(C_{u,s})}$$
  If aggregate curriculum coverage confidence $\bar{C}_{u,E} < 0.25$, the readiness gauge shows `"Ön Tahmin (Daha Fazla Kanıt Gerekli)"` and invites the learner to take a 10-question adaptive diagnostic (`Sprint 9.2`).

---

## 5. Prerequisite Knowledge Graph Propagation & Cold-Start Calibration

### 5.1 Conservative Prerequisite Propagation (`Sprint 7.6`)
Given directed prerequisite edges $s_{\text{pre}} \to s_{\text{post}}$ in `domain/46_CURRICULUM_SKILL_ONTOLOGY_AND_TAXONOMY.md`:
1. **Upward Initialization Only (Cold Start):** If $N_{u, s_{\text{post}}}^{\text{eff}} = 0$, the prior $P(L_0)$ for $s_{\text{post}}$ is bounded by $\min_{s_{\text{pre}} \to s_{\text{post}}} M_{u, s_{\text{pre}}}$.
2. **Downward Diagnostic Root-Cause Trigger:** If a learner fails $s_{\text{post}}$ twice and $M_{u, s_{\text{pre}}} < 0.50$, the Study Planner (`Sprint 8.1`) inserts a 1-question prerequisite check on $s_{\text{pre}}$ before continuing $s_{\text{post}}$.
3. **No Phantom Mastery Inflation:** Passing an advanced skill $s_{\text{post}}$ gives at most a soft floor $\max(M_{u, s_{\text{pre}}}, 0.65)$ to unpracticed direct prerequisites, never `MASTERED` ($\ge 0.85$) without direct evidence.

### 5.2 Psychometric Calibration Audit (`Sprint 7.9` & `Sprint 9.8`)
- Every release train evaluates **Brier Score** ($\text{BS} = \frac{1}{N}\sum (p_{u,s} - y_{u,s})^2 \le 0.20$) and **Expected Calibration Error ($\text{ECE} \le 0.08$)** predicting whether a learner will pass the *next unassisted twin or exam item* on skill $s$.

---

## 6. Ethical Pilot Study & Learning Outcome Evaluation Design

To prove that Verified AI Learning OS improves real student learning without inducing the AI crutch effect, we specify a two-stage empirical validation protocol:

### 6.1 Stage 1: Within-Subject Transfer Evaluation (`Sprint 11.9A` 50-Student TestFlight Cohort)
- **Primary Outcome Metric 1 — Immediate Unassisted Twin Transfer Rate ($\text{UTTR}_{\text{imm}}$):**
  $$\text{UTTR}_{\text{imm}} = \frac{\text{Count of First-Attempt INDEPENDENT Twin Passes after Error Diagnosis}}{\text{Count of Twins Attempted after Error Diagnosis}}$$
  *Target Gate:* $\text{UTTR}_{\text{imm}} \ge 55\%$.
- **Primary Outcome Metric 2 — Delayed 7-Day Trap Immunity Rate ($\text{DTIR}_{7\text{d}}$):**
  Proportion of learners who reached `RESOLVED_PENDING_RETENTION` on mistake code $m$ and successfully solve a spaced retrieval check $\ge 7\text{ days}$ later without repeating mistake $m$.
  *Target Gate:* $\text{DTIR}_{7\text{d}} \ge 65\%$.

### 6.2 Stage 2: Randomized Crossover / Delayed-Feature Trial (`Sprint 10.5` Post-Launch Cohort, $N \ge 200$)
- **Ethical Guardrail:** Never withhold mathematical verification or intentionally show bad explanations to a control group of exam students.
- **Crossover Design:**
  - *Arm A (Standard Verified Solver + Step Diagnosis):* Receives verified step-by-step error diagnosis and explanations.
  - *Arm B (Full Learning OS Loop):* Receives verified step-by-step error diagnosis **plus** the mandatory 60-second Verified Twin challenge and spaced Mistake Passport queue.
  - After 14 days, both arms take an identical, unassisted 10-item timed assessment on their top 3 diagnosed skill areas, and Arm A is upgraded to the full Learning OS experience.

---

## 7. Requirements, Assumptions, Non-Goals, and Acceptance Criteria

### 7.1 Requirements
- `OUT-REQ-001`: All API responses and UI surfaces must keep $O_{u,a}$, $I_{u,s}$, $M_{u,s}$, $R_{u,s}(t)$, and $\widehat{\text{Net}}_{u,E}$ distinct and never label raw assisted completion % as "Mastery" or "Exam Readiness."
- `OUT-REQ-002`: Exam net projections must always include `[estimatedNetLow, estimatedNetHigh]` and epistemic confidence tier.
- `OUT-REQ-003`: Spaced retrieval ($\Delta t \ge 0.5 H_{u,s}$) must expand `half_life_days` strictly faster than same-session massed repetition.

### 7.2 Acceptance Criteria
1. Property-based tests in `LearnerMasteryPolicyTest` verify monotonicity, bounds $[0.05, 0.99]$, `HINT_ASSISTED` ceiling $0.78$, `SOLUTION_REVEALED_ASSISTED` non-increase, and `UNSUPPORTED` invariance.
2. Calibration job in `com.verifiedai.analytics` computes Brier Score and ECE on historical attempt sequences.

---

## 8. Risks and Open Decisions

- **Risk `R-OUT-01` (Over-Penalizing Early Slip Errors):** A high-mastery student ($M_{u,s} = 0.92$) who makes a single arithmetic slip (`ARITH_BASIC_COMPUTATION`) should not drop to $0.45$ mastery.
  - *Mitigation:* When `primary_category == 'ARITHMETIC_SLIP'`, increase effective slip parameter $p_S$ from $0.10$ to $0.35$ during the BKT update so structural skill mastery is cushioned while logging the speed/carelessness slip separately.
- **Open Decision `OD-OUT-01`:** Exact threshold of `evidence_count` required before unlocking the narrow ($\pm 1.5\text{ Net}$) high-confidence YKS readiness badge (currently set to $\ge 40$ verified attempts across $\ge 12$ canonical skills).

---

## 9. Cross-References

- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`
- `domain/46_CURRICULUM_SKILL_ONTOLOGY_AND_TAXONOMY.md`
- `learning/31_MASTERY_AND_KNOWLEDGE_GRAPH.md`
- `learning/32_ADAPTIVE_LEARNING_AND_STUDY_PLANNER.md`
- `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`
- `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`
- `learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md`
- `quality/91_LEARNING_OS_PRODUCT_LEARNING_AND_RELEASE_ACCEPTANCE_GATES.md`
