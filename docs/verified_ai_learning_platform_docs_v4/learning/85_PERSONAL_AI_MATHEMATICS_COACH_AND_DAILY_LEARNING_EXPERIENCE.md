# 85 — Personal AI Mathematics Coach and Daily Learning Experience

## Document Metadata

| Field | Value |
|---|---|
| **Document ID** | `DOC-85` |
| **Canonical Path** | `learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md` |
| **Title** | Personal AI Mathematics Coach and Daily Learning Experience |
| **Status** | `CANONICAL_PRODUCT_AND_UX_SPECIFICATION` |
| **Owner** | Head of Product Strategy, Adaptive Learning Designer & Senior iOS/Android Architect |
| **Date / Version** | `2026-11-07` / `v1.0.0` |
| **Baseline Commit** | `e39bafe88ea369f98c96a65497a94462dc79ef33` (`main`) |
| **Governing ADRs** | `adr/ADR-004_SEPARATE_PYTHON_MATH_VERIFIER.md`, `adr/ADR-003_PROVIDER_NEUTRAL_AI.md`, `adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md`, `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md` |

---

## 1. Purpose

This document specifies the pedagogical behavior, deterministic orchestration rules, and complete screen-by-screen user experience of the **Personal AI Mathematics Coach** across the **Verified AI Learning OS**.

It establishes how Verified AI transforms `apps/ios/VerifiedAI/Features/Home/HomePlaceholderView.swift` and the existing `AttemptAnalysis`, `ProblemCapture`, `ProblemSolving`, `Tutor`, and `Subscription` modules into a cohesive daily learning habit loop without degenerating into an unverified conversational chatbot.

---

## 2. Sources and Evidence Classification

- `SOURCE_VERIFIED`: Grounded in `apps/ios/VerifiedAI/Features/`, `services/api/src/main/java/com/verifiedai/tutoring/`, `services/api/src/main/java/com/verifiedai/attempt/`, `learning/32_ADAPTIVE_LEARNING_AND_STUDY_PLANNER.md`, `learning/34_TUTORING_AND_PEDAGOGICAL_BEHAVIOR.md`, and `learning/79_PEDAGOGICAL_DIAGNOSIS_HANDWRITING_UX_AND_RETENTION_ENGINE.md`.
- `EMPIRICAL_RESEARCH`: Grounded in Kestin et al. (2025, Nature Scientific Reports `10.1038/s41598-025-97652-6`), Bastani et al. (2025, PNAS `10.1073/pnas.2422633122`), and EEF Metacognition & Self-Regulated Learning guidance.
- `ENGINEERING_ESTIMATE`: Client rendering budgets (`ADR-010`), API p95 latency targets, and screen state machine specifications.

---

## 3. Architectural Boundary: Deterministic Coach State vs. Constrained LLM Explanation

### 3.1 The Golden Rule of the Personal AI Math Coach (`INV-COACH-001`)

> **The LLM never decides mathematical truth, never invents learner history, and never fabricates study priorities.**
> Deterministic backend services (`com.verifiedai.attempt`, `com.verifiedai.mistake`, `com.verifiedai.mastery`, `com.verifiedai.studyplan`, `com.verifiedai.exam`) compute the exact mathematical state; the LLM (`AiCapability.EXPLAIN`, `AiCapability.TUTOR`) translates that structured state into concise, empathetic, curriculum-aligned natural language in Turkish (`tr-TR`) or English (`en-US`).

| Coaching Function | Deterministic Authority (100% Rule/CAS/DB Driven) | Allowed Constrained LLM Role | Forbidden LLM Behavior |
|---|---|---|---|
| **Step Error Diagnosis** | `attempt_verification_v2.py` + `AttemptEvaluation` (`V035`) + `counterexample_engine.py` | Phrasing the 2-sentence conceptual explanation around the verified step diff and counterexample $x=c$. | Overriding a SymPy verdict or inventing a counterexample not verified by `counterexample_engine.py`. |
| **Mistake DNA Prioritization** | `MistakeTaxonomy` (`V036`) + Empirical Bayes propensity $\theta_{u,m}$ (`learning/83`) | Summarizing why a recurring trap matters on TYT/AYT or IB/AP exams. | Hallucinating past mistakes not present in `learner_mistake_dna_profiles`. |
| **Socratic Hint Ladder** | `SocraticPolicy` + `HintPolicy` (`com.verifiedai.tutoring`) anchored to next canonical step | Generating the 1-to-3 sentence Socratic question for the current hint rung. | Giving away the final answer on Hint 1 or 2 (`INV-TUT-001`). |
| **Daily 15-Minute Plan** | `StudyPlanEngine` (`com.verifiedai.studyplan`) ranking by $P_{u,m}$, half-life decay $R_{u,s}(t)$, and exam ROI | Generating a 1-sentence daily mission briefing (*"Bugün 12 dakikada Eşitsizlik Yön Tuzağını kapatıyoruz"*). | Creating arbitrary study tasks disconnected from skill/mistake records. |

---

## 4. The Daily 15-Minute Coaching Loop

To fit a high-school student's real evening routine alongside school homework and question banks, the Daily Coach Session is structured around three high-yield blocks totaling **10 to 15 minutes**:

1. **Block 1 — Active Trap Repair (4–5 min):** Review the student's #1 unresolved `RECURRING_PATTERN` or yesterday's failed attempt -> complete a 60-second Micro-Lesson or Socratic step repair -> pass **1 Independent Verified Twin**.
2. **Block 2 — Spaced Retention Check (4–5 min):** Solve **2 Spaced Verification Twins** for skills whose memory half-life $R_{u,s}(t)$ has decayed near the $0.72$ retention threshold (`RESOLVED_PENDING_RETENTION` -> `STABLE_RESOLVED`).
3. **Block 3 — Exam ROI Stretch Item (3–5 min):** Solve **1 Exam-Style Synthesis Problem** on a high-weight target exam skill (e.g., AYT Derivative Chain Rule + Tangent Line) under timed pacing.

---

## 5. Complete 12-Screen & State Specification (`iOS SwiftUI` + Future `Android Compose`)

All 12 core screens obey universal non-functional contracts:
- **Accessibility:** Full VoiceOver mathematical speech synthesis (e.g., reading $\frac{x-2}{x+3} \le 0$ as *"x eksi 2 bölü x artı 3 küçük eşittir sıfır"* in `tr-TR`), Dynamic Type scaling (`xSmall` to `AX3`), minimum `44x44 pt` tap targets, and shape + icon redundancy alongside color for step diffs (`ADR-010`).
- **Localization:** First-class `tr-TR` and `en-US` string catalogs plus localized curriculum terminology (`TYT/AYT MEB Maarif Modeli` vs `IB/AP/SAT`).
- **Offline & Resilience:** Local SQLite/SwiftData cache for the last 20 attempts, Mistake Passport snapshot, and Today's queued Twin pack (`ISSUED` state), with optimistic offline draft saving and idempotent sync upon reconnection.

---

### Screen 1: Learning OS Home / Daily Coach Hub
*(Replaces `apps/ios/VerifiedAI/Features/Home/HomePlaceholderView.swift` in Sprint 7.7 / 8.4)*

- **Entry Points:** App launch default tab (`Tab 1: Coach Hub`), push notification deep link (`verifiedai://coach/today`).
- **Data Requirements:** `GET /v1/coach/today-summary` returning `DailyCoachBriefingDto` (active streak, `conqueredTrapsCount`, top 1 active `MistakeDnaCard`, 3-item `DailyMissionQueue`, and `ExamReadinessSummary`).
- **Information Hierarchy:**
  1. Top Status Bar: Exam Target Pill (`YKS AYT Sayısal • Sınava 218 Gün`) + Independent Verification Streak (`🔥 5 Gün Bağımsız Seri`).
  2. Hero Card: **Today's 12-Minute Coach Mission** (`1 Aktif Tuzak Onarımı • 2 Aralıklı Tekrar İkizi`).
  3. Quick Capture Bar: Prominent **"Scan or Enter Your Problem (`Sorunu Tara veya Adımını Kontrol Et`)"** CTA.
  4. Active Exam Trap Radar Preview (top 2 `RECURRING_PATTERN` badges).
- **Primary & Secondary Actions:** Primary: `Start 12-Min Coach Session` OR `Scan Problem & My Steps`; Secondary: `View Mistake Passport`, `Switch Exam Target`.
- **States:**
  - *Cold Start (0 attempts):* Shows a 3-question "3-Minute Trap & Readiness Diagnostic" card + sample interactive step-diff demo.
  - *Loading:* Skeleton shimmer (`< 180 ms` cached snapshot shown immediately, background refresh).
  - *Offline:* Displays cached daily twins and offline problem draft queue banner.
  - *Error/Retry:* Non-blocking banner with `Tekrar Dene` while keeping Problem Capture active.
- **Feature Flag:** `verifiedai.coach.home-hub-enabled` (fallback: solver home).
- **ASCII Wireframe:**
```text
+--------------------------------------------------+
| [Hedef: AYT Sayısal]          [🔥 5 Gün Bağımsız] |
|--------------------------------------------------|
| 🎯 BUGÜNÜN KOÇ GÖREVİ (12 Dk • +%1.4 Net Etkisi) |
| [!] Tuzak Onarımı: Eşitsizlik Yön Değiştirme     |
| [↻] Aralıklı Tekrar: 2 İkiz Soru Hazır           |
| [  ▶ 12 Dakikalık Görevi Başlat  ]               |
|--------------------------------------------------|
| 📸 KENDİ SORUNU VEYA ÇÖZÜMÜNÜ KONTROL ET         |
| [ 📷 Fotoğraf Çek ]      [ ∑ Matematik Klavyesi ]|
|--------------------------------------------------|
| 🛡️ AKTİF SINAV TUZAKLARIN (Mistake Passport)     |
| • Payda Kökü Unutma (x≠2)     [Onar -> 1 İkiz]   |
| • Zincir Kuralı İç Türev      [Kapatıldı ✓]      |
+--------------------------------------------------+
```

---

### Screen 2: Problem Capture & Structured Step-Entry Workspace
*(Extends `apps/ios/VerifiedAI/Features/ProblemCapture/` & `V034` `AttemptStructuredWork`)*

- **Entry Points:** Home Quick Capture CTA, Tabbar Center Action, or Twin Retry workspace.
- **Data Requirements:** `POST /v1/attempts` with image upload / OCR preview + `AttemptStructuredWork` (`V034` ordered steps `S_1..S_k` and `finalAnswerLatex`).
- **Information Hierarchy:**
  1. Problem Prompt Region (cropped image + editable 2D rendered math preview via `ADR-010`).
  2. Student Steps Stack (`Adım 1`, `Adım 2`, `Adım 3`... with native math keyboard and OCR line splitting).
  3. Mode Selector: **"Check My Steps (`Adımlarımı Kontrol Et` — Recommended)"** vs **"I'm Stuck at Step $k$ (`Adımda Takıldım — İpucu Ver`)"**.
- **States:**
  - *Low OCR Confidence (`< 0.75`):* Highlights uncertain tokens in amber before submission so the student confirms `-` vs `+` or exponent `2` before verification runs.
  - *Unsupported Input (e.g., pure 3D geometry figure without algebraic relations):* Immediate friendly notice before wasting quota.

---

### Screen 3: Attempt Diagnosis & First-Error Step-Diff View
*(Extends `apps/ios/VerifiedAI/Features/AttemptAnalysis/` with `V035` + `V036` + `ADR-010`)*

- **Entry Points:** Immediately after submitting an attempt in Screen 2.
- **Data Requirements:** `GET /v1/attempts/{attemptId}/diagnosis` returning `AttemptEvaluation` (`V035`), `AttemptMistakeClassification` (`V036`), `CounterexamplePayload`, and pre-generated `CounterfactualTwinSummary` (`V031`).
- **Information Hierarchy:**
  1. Overall Verdict Banner: `Adım 1 Doğru ✓` -> `Adım 2 İlk Anlamlı Hata ⚠️` -> `Adım 3–4 Zincirleme Etki (Puan Kırılmadı)`.
  2. **2D Step-Diff Token Highlight (`ADR-010`):** Exact sub-expression token that mutated illegally highlighted with warning badge.
  3. **Concrete Counterexample Proof Card (`H-01`):** e.g., *"Deneme Kanıtı: $x = -2$ değerini 1. adımda ve 2. adımda yerine koyduğumuzda eşitlik bozuluyor: $4 \neq -4$."*
  4. **Exam Trap Classification Pill:** e.g., `INEQ_NEGATIVE_MULT_NO_FLIP` (`Negatif Sayıyla Çarpımda Yön Değiştirme Tuzağı`).
  5. Bottom Sticky CTA: **"Prove It Solo: Solve 1 Verified Twin (`İkiz Soruda Tek Başına Kanıtla — 60sn`)"**.
- **Secondary Actions:** `Adımı Düzenle (OCR Hatası)` (triggers self-service rollback per `learning/83`), `Sokratik İpucu İste`.
- **ASCII Wireframe:**
```text
+--------------------------------------------------+
| ← Çözüm Analizi                  [Adımı Düzenle] |
|--------------------------------------------------|
| ✅ Adım 1:  -2(x - 3) > 8                        |
| ⚠️ Adım 2:  x - 3 > -4     [İLK HATA BURADA]     |
|    └── Fark: '>' işareti '<' olmalıydı           |
| ℹ️ Adım 3:  x > -1  (Zincirleme etki - Ceza yok) |
|--------------------------------------------------|
| 🧪 SOMUT KARŞI ÖRNEK KANITI (x = 0 dene):        |
| Adım 1: -2(0 - 3) = 6 > 8 (Yanlış)               |
| Adım 2: 0 - 3 = -3 > -4   (Doğru görünüyor! ⚡)   |
|--------------------------------------------------|
| 🏷️ SINAV TUZAĞI: Negatif Bölen Yön Tuzağı        |
| [ 🛡️ İkiz Soruda Kanıtla (60sn Kilitli Çözüm) ]  |
+--------------------------------------------------+
```

---

### Screen 4: Guided Socratic Repair & Micro-Lesson Drawer
*(Extends `apps/ios/VerifiedAI/Features/Tutor/` & `Sprint 8.5`)*

- **Entry Points:** Tapping `Sokratik İpucu İste` on Screen 2/3, or automatically after failing a Verified Twin twice.
- **Data Requirements:** `POST /v1/tutor/sessions/{id}/turns` (`com.verifiedai.tutoring`) + `GET /v1/micro-lessons/{mistakeCode}`.
- **Information Hierarchy:**
  1. Pinned Student Broken Step (`Adım 2`) at top of sheet.
  2. **3-Rung Socratic Hint Ladder:**
     - *Rung 1 (Conceptual Nudge):* "Eşitsizliğin her iki tarafını $-2$'ye böldüğünde eşitsizlik yönüne ne olur?"
     - *Rung 2 (Targeted Rule Card):* 60-second visual rule card showing $a < b \implies ca > cb$ for $c < 0$.
     - *Rung 3 (Guided Step Completion):* Interactive fill-in-the-blank for the corrected step $x - 3 < -4$.
  3. Direct Transition CTA: **"Harika! Şimdi İkiz Soruda Bağımsız Kanıtla"**.

---

### Screen 5: Verified Twin "Prove It Solo" Challenge Screen
*(Operationalizes `V031` `counterfactual_twin_drills` & `learning/84`)*

- **Entry Points:** Primary CTA from Screen 3 or Screen 4, or Daily Coach Queue.
- **Data Requirements:** `GET /v1/twins/{twinId}` + `POST /v1/twins/{twinId}/submit`.
- **Information Hierarchy:**
  1. Header Badge: **🛡️ Bağımsız Ustalık Kanıtı (`INDEPENDENT` Hedefi)**.
  2. **60-Second Solution Lock Timer:** Shows *"Tam Çözüm Kilidi: 00:48 — Önce kendi adımlarınla dene!"*
  3. Verified Twin Problem Prompt (2D rendered LaTeX).
  4. Step/Answer Input Canvas.
- **States:**
  - *Passed Independently (0 hints):* Celebratory **Mastery Shield Unlocked** animation; shows Mistake DNA state advancing to `RESOLVED_PENDING_RETENTION` and skill mastery delta (`+0.09`).
  - *Passed with Hint (`HINT_ASSISTED`):* Shows partial progress (`Guided Pass — 0.78 Tavanı`) and offers 1 more clean twin for full independent credit.
  - *Repeated Same Trap:* Opens 90-second Micro-Lesson drawer and issues Variant A2.

---

### Screen 6: Mistake Passport & Exam Trap Radar Screen
*(Operationalizes `learning/83` & `Sprint 7.7`)*

- **Entry Points:** Tab 2 (`Mistake Passport`) or Home Radar card.
- **Data Requirements:** `GET /v1/mistake-passport` returning summary counters (`conqueredTrapsCount`, `activeTrapsCount`, `independentTransferRate`), segmented list of `MistakeDnaCardDto` items (`RECURRING_PATTERN`, `IN_REMEDIATION`, `RESOLVED_PENDING_RETENTION`, `STABLE_RESOLVED`).
- **Information Hierarchy:**
  1. Top Passport Summary Card: **Kapatılan Sınav Tuzakları ($K$)** | **Bağımsız Kanıt Oranı ($X\%$)**.
  2. Filter Tabs: `Aktif Tuzaklar (Onarılacak)` | `Koruma Bekleyenler (7 Gün Tekrarı)` | `Kapatılan Tuzaklar ✓` | `İşlem Dikkatsizlikleri`.
  3. Each Trap Card shows: Friendly Trap Title, Category Badge, Exposure-Adjusted Risk Bar, Last Counterexample Preview, and `1 İkiz Soru Çöz` button.

---

### Screen 7: Skill Mastery & Prerequisite Knowledge Graph Explorer
*(Operationalizes `learning/31`, `learning/86`, and `Sprint 7.6` / `7.9`)*

- **Entry Points:** Tab 3 (`Mastery & Map`) or tapping a skill badge on any screen.
- **Data Requirements:** `GET /v1/mastery/graph?curriculum=TR_YKS_AYT` returning skill nodes with `masteryScore` ($M_{u,s}$), `retentionScore` ($R_{u,s}(t)$), `independentCompetence` ($I_{u,s}$), `evidenceCount`, and prerequisite edges (`domain/46`).
- **Information Hierarchy:**
  1. Curriculum Track Switcher (`TYT Matematik`, `AYT Matematik`, `Grade 9–12`, `IB/AP`).
  2. Visual Topic Map with Dual-Ring Nodes:
     - **Inner Solid Core:** Verified Independent Mastery ($M_{u,s}$).
     - **Outer Decay Ring:** Current Half-Life Retention ($R_{u,s}(t)$) — turns amber when spaced review is due.
  3. Prerequisite Bottleneck Alert: If a student struggles in `MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE` because `MATH.FUNCTIONS.COMPOSITION` mastery is $< 0.45$, highlights the root prerequisite with a 1-tap repair drill.

---

### Screen 8: Daily 15-Minute Study Plan & Spaced Review Queue
*(Operationalizes `learning/32` & `Sprint 8.3` / `8.4`)*

- **Entry Points:** Home Coach Mission CTA or Tab 1 Schedule view.
- **Data Requirements:** `GET /v1/study-plan/today` and `PATCH /v1/study-plan/preferences` (daily budget `10 / 15 / 25 min`, target exam date).
- **Information Hierarchy:**
  1. Ordered 3-step checklist (`1. Tuzak Onarımı`, `2. Hafıza Yarı-Ömür Tekrarı`, `3. Yüksek Net Getirili Soru`).
  2. Transparent "Why This Task?" badge on every card: *"Seçilme Nedeni: Eşitsizlik yön hatası son 14 günde 2 kez tekrarlandı + AYT'de 1.5 soru ağırlığı var."*
  3. One-tap **"Sınav Haftası / Yoğun Gün Modu (5 Dakikalık Mini Seri)"** downshift button so busy students keep their habit alive without guilt.

---

### Screen 9: YKS / Exam Readiness & Net-Gain Simulator Hub
*(Operationalizes `Phase 9` & Innovation `H-03`)*

- **Entry Points:** Tab 4 (`Exam Readiness`) or Home Exam Target Pill.
- **Data Requirements:** `GET /v1/exams/readiness?examCode=TR_YKS_AYT_MATH` returning `ExamReadinessProjectionDto` (`estimatedNetLow`, `estimatedNetMid`, `estimatedNetHigh`, `confidenceTier`, `topRoiTopics`).
- **Information Hierarchy:**
  1. **Credible Interval Net Gauge:** Displays **"Tahmini AYT Matematik Net Aralığı: $24.5 - 28.0\text{ Net}$ (Güven: Orta — 42 Doğrulanmış Kanıt)"** — never a fake single decimal precision number when data is sparse!
  2. **Net Lost to Recurring Traps Counter:** *"Aktif 3 işlem/tanım kümesi tuzağını kapatmak tahmini $+2.8\text{ Net}$ kazandırır."*
  3. **High-ROI Topic Allocator Table:** Ranks topics by $\Delta\text{Net} / \text{Study Hour}$.

---

### Screen 10: Timed Diagnostic & Micro-Mock Assessment Screen
*(Operationalizes `Phase 9` `Sprint 9.1` / `9.2` / `9.6`)*

- **Entry Points:** Exam Readiness Hub (`15 Dakikalık Mini Deneme Başlat`) or Cold-Start Onboarding Diagnostic.
- **Data Requirements:** `POST /v1/exams/sessions` + `POST /v1/exams/sessions/{id}/submit`.
- **Information Hierarchy:**
  1. Distraction-free exam bar with non-anxious pacing timer and question navigator (`Soru 1..10`).
  2. Supports both **Final Option Selection + Scratchpad Step Upload** so post-exam grading attributes every wrong answer to either:
     - `CONCEPTUAL_GAP`
     - `RECURRING_MISTAKE_TRAP` (linked to Mistake DNA)
     - `TIME_PRESSURE_SLIP`
     - `STRATEGIC_OMISSION` (blank)

---

### Screen 11: Weekly Progress Report & Tutor/Parent Share Packet Screen
*(Operationalizes `Sprint 8.6` & Innovation `H-06`)*

- **Entry Points:** Sunday evening push notification or Profile/Passport `Haftalık Rapor Paylaş`.
- **Data Requirements:** `GET /v1/reports/weekly/{weekIso}` + native PDF/image share sheet generator.
- **Information Hierarchy:**
  1. Weekly Verified Summary: Problems Verified, Independent Twins Conquered, Active Traps Remaining, Net Readiness Delta.
  2. **1-Click "Share with My Tutor / Parent (`Özel Ders Hocamla / Ailemle Paylaş`)"** generating a clean 1-page PDF summary containing **only** aggregate skill mastery, conquered traps, and recommended next 3 topics (strictly excluding private chat messages or raw personal identifiers unless opted in per `security/89`).

---

### Screen 12: Contextual Paywall & Entitlement Upgrade Sheet
*(Extends `apps/ios/VerifiedAI/Features/Subscription/` & `com.verifiedai.billing`)*

- **Entry Points:** Triggered contextually *after* the student receives value (e.g., after viewing their First-Error Diagnosis when tapping to unlock unlimited Daily Coach Twins, full Mistake Passport history, or YKS Net Simulator).
- **Data Requirements:** `GET /v1/billing/entitlements` + StoreKit 2 product catalog (`pro_monthly`, `pro_annual`, `coach_pro_yks_season`).
- **Information Hierarchy:**
  1. Dynamic Personalized Value Header: *"Bu hafta 3 sınav tuzağını tespit ettik. Hepsini İkiz Sorularla kapatmak için Coach Pro'ya geç."*
  2. Transparent Tier Comparison (`Free` vs `Pro` vs `Coach Pro / YKS Sezon Paketi`).
  3. Apple Guideline `3.1.2` Mandatory Disclosures: Exact price, billing period, trial cancellation timeline reminder toggle (*"Deneme bitmeden 2 gün önce bana bildirim gönder"*), `Satın Alımları Geri Yükle`, and Terms/Privacy links.

---

## 6. Analytics Events and Performance Budgets

### 6.1 Canonical Analytics Events (Extending `operations/48_ANALYTICS_EVENT_CATALOG_AND_PRODUCT_METRICS.md`)
- `coach_daily_mission_started`, `coach_daily_mission_completed`
- `attempt_first_error_viewed`, `attempt_counterexample_expanded`, `attempt_step_ocr_corrected`, `attempt_diagnosis_disputed`
- `twin_challenge_issued`, `twin_challenge_submitted` (with property `evidence_type`: `INDEPENDENT | HINT_ASSISTED | SOLUTION_REVEALED_ASSISTED | UNSUPPORTED`)
- `mistake_dna_state_transitioned` (`from_state`, `to_state`, `mistake_code`)
- `weekly_tutor_packet_exported`

### 6.2 Strict Latency & Performance Budgets (`ENGINEERING_ESTIMATE`)
- **Cached Screen Render (Screens 1, 6, 7, 8, 9):** `< 200 ms` p95 from local SwiftData/SQLite snapshot.
- **Deterministic Step Evaluation + Mistake Classification (Screen 3):** `< 1,800 ms` p95 (`services/api` + `services/math-verifier`).
- **Deterministic Twin Issuance (Screen 5):** `< 400 ms` p95 (pre-computed asynchronously during Screen 3 diagnosis view).
- **2D Math Layout (`ADR-010`):** `60 fps` scrolling with zero WebView instantiation overhead.

---

## 7. Requirements, Assumptions, Non-Goals, and Acceptance Criteria

### 7.1 Requirements
- `COACH-REQ-001`: The Coach must never present an LLM-generated mathematical claim that has not been validated by `services/math-verifier/` or deterministic backend state.
- `COACH-REQ-002`: Every screen must implement explicit Empty, Loading, Offline, Unsupported/Refusal, and Retry states.
- `COACH-REQ-003`: Paywall sheets must never block a student in the middle of an active timed diagnostic or hide the initial diagnosis of a free-quota attempt that was already accepted for processing.

### 7.2 Acceptance Criteria
1. All 12 screen state machines are covered by SwiftUI view-model unit tests and snapshot/UI tests in the owner acceptance suite for Sprints `7.7`–`10.2`.
2. VoiceOver accessibility labels and `tr-TR` / `en-US` localization keys achieve 100% coverage on all new views.

---

## 8. Risks and Open Decisions

- **Risk `R-COACH-01` (Information Density on Mobile Screens):** Showing step diffs, counterexamples, mistake codes, and twin CTAs simultaneously on a 6.1-inch screen can overwhelm users.
  - *Mitigation:* Progressive disclosure on Screen 3: show the **First-Error Diff + 1-line Counterexample + Primary Twin CTA** by default; keep full step-by-step details and Socratic drawer expandable on tap.
- **Open Decision `OD-COACH-01`:** Default daily notification reminder time for YKS learners (`19:30` local time vs learner-selected study slot during onboarding).

---

## 9. Cross-References

- `learning/32_ADAPTIVE_LEARNING_AND_STUDY_PLANNER.md`
- `learning/34_TUTORING_AND_PEDAGOGICAL_BEHAVIOR.md`
- `learning/79_PEDAGOGICAL_DIAGNOSIS_HANDWRITING_UX_AND_RETENTION_ENGINE.md`
- `learning/83_VERIFIED_MISTAKE_DNA_AND_MISTAKE_PASSPORT_SPECIFICATION.md`
- `learning/84_VERIFIED_TWIN_PRACTICE_AND_INDEPENDENT_LEARNING_ENGINE.md`
- `learning/86_LEARNING_OUTCOMES_RETRIEVAL_PRACTICE_AND_MASTERY_EVALUATION.md`
- `architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md`
