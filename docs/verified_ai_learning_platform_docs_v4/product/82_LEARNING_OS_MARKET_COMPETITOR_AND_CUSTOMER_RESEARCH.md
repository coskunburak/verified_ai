# 82 — Learning OS Market, Competitor, and Customer Research

## Document Metadata

| Field | Value |
|---|---|
| **Document ID** | `DOC-82` |
| **Canonical Path** | `product/82_LEARNING_OS_MARKET_COMPETITOR_AND_CUSTOMER_RESEARCH.md` |
| **Title** | Learning OS Market, Competitor, and Customer Research |
| **Status** | `CANONICAL_MARKET_RESEARCH_SPECIFICATION` |
| **Owner** | Founder / Product Strategy & Market Intelligence Lead |
| **Date / Version** | `2026-11-07` / `v1.0.0` |
| **Baseline Commit** | `e39bafe88ea369f98c96a65497a94462dc79ef33` (`main`) |
| **Governing ADRs** | `adr/ADR-004_SEPARATE_PYTHON_MATH_VERIFIER.md`, `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md` |

---

## 1. Purpose

This document provides a rigorous, source-classified market and competitive intelligence teardown for the **Verified AI Learning OS** across two primary arenas:
1. **Turkish High School (Grades 9–12) & YKS (TYT / AYT) Exam Preparation Ecosystem** (primary commercial wedge).
2. **Global Mathematics Solvers, Symbolic Calculators, and AI Tutoring Platforms** (international expansion arena).

Every claim in this document is explicitly tagged to prevent the conflation of official demographic statistics, competitor marketing slogans, public app-store signals, and bottom-up financial projections.

---

## 2. Evidence Classification Taxonomy

All statements in this document use both the repository-wide epistemic tags (`SOURCE_VERIFIED`, `EMPIRICAL_RESEARCH`, `COMPETITOR_MARKETING_CLAIM`, `FOUNDER_HYPOTHESIS`, `FINANCIAL_MODEL_ASSUMPTION`, `ENGINEERING_ESTIMATE`, `NOT_VERIFIED`) and the specific competitive intelligence classification tags:

- `VERIFIED_PUBLIC_FACT`: Official government/institutional statistics (e.g., ÖSYM YKS yearly evaluation reports, RevenueCat published industry datasets, official App Store pricing tiers).
- `COMPETITOR_CLAIM`: Capabilities advertised by a competitor on its website or store listing that are not independently audited for symbolic mathematical accuracy.
- `USER_REVIEW_SIGNAL`: Recurring patterns reported by students and parents in App Store, Google Play, Ekşi Sözlük, Reddit, and forum reviews.
- `INFERENCE`: Architectural or business-model deduction derived from public product behavior, pricing structures, and technical latency/output characteristics.
- `UNKNOWN` / `NOT_AVAILABLE`: Private financial, retention, or proprietary model metrics not publicly disclosed.

---

## 3. Existing Architecture Relationship

This specification extends `product/76_COMPETITIVE_INTELLIGENCE_AND_MARKET_POSITIONING_MATRIX.md` and `roadmap/80_UNIT_ECONOMICS_30_DAY_LAUNCH_CONTRACT_AND_EXECUTION_ROADMAP.md`. Where `product/76` focused primarily on solver-level parity (OCR + step explanation), `product/82` evaluates competitors across the full **Learning OS value chain**:
- First meaningful mistake localization (`V035` `AttemptEvaluation`)
- Longitudinal misconception tracking (`V036` `AttemptMistakeClassification` + Sprint 7.5 Mistake Ledger)
- Verified Counterfactual Twin drills (`V031` `counterfactual_twin_drills`)
- Evidence-separated mastery (`bkt-lite-v1`)
- High-stakes exam readiness and net-gain attribution (`Phase 9`)

---

## 4. Turkish Exam Market Sizing (ÖSYM YKS Population vs. Bottom-Up TAM / SAM / SOM)

### 4.1 Official ÖSYM YKS Candidate Population (`VERIFIED_PUBLIC_FACT` / `EMPIRICAL_RESEARCH`)

According to official ÖSYM (Ölçme, Seçme ve Yerleştirme Merkezi) YKS evaluation statistics:

| Exam Year & Session | Applied Candidates (`Başvuran Aday`) | Attending Candidates (`Sınava Giren Aday`) | Valid Exam Score Pool (`Sınavı Geçerli Olan`) | Source Classification |
|---|---|---|---|---|
| **2024-YKS TYT** (Basic Proficiency — 40 Math Questions) | `3,120,870` | `2,819,362` | `2,819,075` | `VERIFIED_PUBLIC_FACT` (ÖSYM 2024 YKS Report) |
| **2024-YKS AYT** (Field Proficiency — 40 Advanced Math Questions) | `2,019,699` | `1,776,496` | `1,776,449` | `VERIFIED_PUBLIC_FACT` (ÖSYM 2024 YKS Report) |
| **2024-YKS YDT** (Foreign Language) | `233,384` | `171,090` | `171,084` | `VERIFIED_PUBLIC_FACT` (ÖSYM 2024 YKS Report) |
| **2025-YKS TYT** (Basic Proficiency — 40 Math Questions) | `2,560,649` | `2,351,641` | `2,351,397` | `VERIFIED_PUBLIC_FACT` (ÖSYM 2025 YKS Report) |
| **2025-YKS AYT** (Field Proficiency — 40 Advanced Math Questions) | `1,721,057` | `1,549,940` | `1,549,883` | `VERIFIED_PUBLIC_FACT` (ÖSYM 2025 YKS Report) |
| **2025-YKS YDT** (Foreign Language) | `215,451` | `156,040` | `156,030` | `VERIFIED_PUBLIC_FACT` (ÖSYM 2025 YKS Report) |

**Critical Strategic Insight (`INFERENCE`):**
1. **Do Not Equate Total TYT Applicants with Paid App Buyers:** Roughly 35–40% of TYT applicants are casual test-takers, vocational track students not targeting quantitative university degrees, or repeat registrants with minimal study spend.
2. **The True High-Intent Core is the AYT Sayısal & Eşit Ağırlık Cohort plus Grade 11 Pre-YKS Students:** In 2025-YKS, `1,549,940` candidates actively sat for AYT, where AYT Mathematics (40 questions covering polynomials, functions, logarithms, sequences, trigonometry, limits, derivatives, and integrals) is the single highest-variance determinant of university placement rank.

### 4.2 Bottom-Up Turkish & International Market Segmentation (`FINANCIAL_MODEL_ASSUMPTION`)

| Market Layer | Population Definition | Estimated Learner Count | Willingness-to-Pay (WTP) Profile | Evidence Tag |
|---|---|---|---|---|
| **Raw Exam & High-School Demographic Pool (Turkey)** | ~2.35M–2.82M active YKS TYT test-takers + ~4.8M high school students (Grades 9–12 across Fen, Anadolu, Özel, and Meslek Liseleri). | `~6.0M` total students (overlapping Grade 12) | Highly skewed; top 25% of households drive >75% of private tutoring & digital prep spend. | `VERIFIED_PUBLIC_FACT` + `INFERENCE` |
| **Turkish TAM (Total Addressable Market — Paid Digital Math Prep)** | AYT Sayısal/EA candidates + serious TYT-only candidates + Grades 9–11 college-prep students with smartphone/tablet and digital payment access. | `~1,400,000` learners / year | `$45–$120 / year` (`~1,500–4,200 TRY / year`) for digital math/solver/coaching subscriptions (`~$84M–$110M/yr` annual domestic digital pool). | `FINANCIAL_MODEL_ASSUMPTION` |
| **Turkish SAM (Serviceable Addressable Market — iOS First, then Android)** | High-intent YKS & Grade 10–12 learners actively using mobile question-solving or coaching apps (iOS share in Turkey upper-income student segment ~32–38%; expands to 100% upon Phase 12.10 Android launch). | `~450,000` (iOS-only V1) -> `~1,200,000` (iOS + Android V2) | `$59–$119 / year` equivalent across Pro, Coach Pro, and 6-month YKS Season Passes. | `FINANCIAL_MODEL_ASSUMPTION` |
| **Turkish SOM (Serviceable Obtainable Market — Months 1–24 Solo Founder)** | Realistic organic + founder-led social/creator + referral capture without VC-scale paid CAC burn. | **M12 Base:** `420–650` paid active subs; **M24 Base:** `2,200–4,500` paid active subs (`0.1%–0.4%` of SAM). | Blended gross ARPU `$7.50–$11.50 / month` equivalent (`roadmap/88_LEARNING_OS_MONETIZATION_GROWTH_AND_VALIDATION_PLAN.md`). | `FINANCIAL_MODEL_ASSUMPTION` |
| **International Expansion SAM (Phase 12+ EN Tier-1)** | English-speaking IB Math AA/AI, AP Calculus AB/BC, SAT Math, and A-Level Mathematics self-study mobile learners. | `~3,500,000+` annual exam-track learners | `$9.99–$19.99 / month` or `$69.99–$119.99 / year`. | `FINANCIAL_MODEL_ASSUMPTION` |

---

## 5. Deep Competitor Teardown: Turkish YKS & High School Ecosystem

### 5.1 Turkish Competitor Matrix

| Competitor | Core Product Model (`VERIFIED_PUBLIC_FACT`) | Advertised Positioning (`COMPETITOR_CLAIM`) | Actual Math Verification & Mistake Engine (`INFERENCE`) | Pricing & Packaging Signal (`VERIFIED_PUBLIC_FACT` / `USER_REVIEW_SIGNAL`) | Vulnerability Exploited by Verified AI Learning OS |
|---|---|---|---|---|---|
| **Kunduz** | Photo Q&A routed to human tutor network + video courses + 1-on-1 human PDR coaching packages. | *"7/24 soru çözüm, 15 dakikada eğitmenden yanıt, birebir online koçluk."* | **Human-dependent & non-symbolic:** Tutors send a photo of a handwritten solution to the prompt. Tutors rarely diagnose the student's own multi-step draft; wait time is 5–30 minutes; quality varies by freelance tutor. | Tiered seasonal/monthly packages (Soru Çözüm vs. Full Koçluk); high human COGS prevents low-cost instant step verification. | **Instant (<3s) symbolic step-diff verification** on the student's own work + automated Verified Twin retry without waiting 15 minutes for a human tutor. |
| **Doping Hafıza** | Pre-recorded animated/video courses, memory techniques ("Zihin Haritaları"), digital question banks, AI study schedule ("Yapay Zeka Akıllı Çalışma Planı"). | *"Yapay zeka destekli kişiselleştirilmiş çalışma programı ve sıfırdan zirveye konu anlatımı."* | **Item-level binary grading only:** "AI" adjusts video/test schedules based on multiple-choice test scores (correct/wrong), NOT symbolic step-by-step inspection of student algebra or calculus work. | High-ticket annual curriculum bundles (sold via web/call-center installment plans, often 15,000–35,000+ TRY/year). | Verified AI is an **agile, low-friction daily mistake-repair and step-verification coach** that complements any video/book source at ~1/10th the annual cost. |
| **Raunt (SEBİT / Türk Telekom)** | Hybrid printed book sets (Palme, Limit, Raunt) + digital platform with topic videos and adaptive test routing. | *"Noktasal eksikleri belirleyen akıllı yönlendirme sistemi."* | **Classic IRT / Prerequisite Tree on Multiple-Choice Tests:** Tracks which sub-topic test the student failed, but cannot read a student's scratchpad or explain why their sign flipped on line 3. | Annual hybrid book + software bundle (high upfront commitment). | Raunt requires buying an entire closed ecosystem of printed books; Verified AI works on **any question the student is currently attempting** and diagnoses step-level misconceptions. |
| **Talebee / Koç'a Sor** | Human mentor/peer coaching marketplaces and Q&A scheduling apps for YKS students. | *"Derece öğrencisi koçlarla haftalık planlama ve motivasyon takibi."* | **Zero automated math verification:** University-student mentors manually review weekly net counts ("TYT Matematik 22 doğru 6 yanlış") without inspecting the mathematical structure of those 6 errors. | Monthly human coaching subscription (`~1,500–4,000 TRY/month`). | Human peer coaches guess why a student's net plateaued; Verified AI provides **mathematical Mistake DNA evidence** (and can even export a 1-page report to the student's human coach). |
| **YKS Pilot / AI Sınav / Pusula YKS** | Emerging mobile YKS study trackers, net calculators, Pomodoro timers, and LLM wrapper chatbots. | *"Yapay zeka YKS koçun, deneme analizi ve soru çözümü."* | **Thin LLM API wrappers (`INFERENCE`):** Rely on direct OpenAI/Gemini multimodal prompts without a symbolic CAS/SymPy verification firewall. Prone to hallucinating geometry/calculus steps and domain rules. | Freemium mobile subscriptions (`~100–350 TRY/month`). | Direct LLM wrappers violate mathematical soundness on YKS AYT edge cases (piecewise continuity, absolute value roots, parameter intervals). Verified AI enforces **fail-closed symbolic verification (`ADR-001`, `ADR-009`)**. |
| **Solvi (Clarif Co / Solvely)** | AI Homework Scanner & Solver + "AI Video Çözüm" (synthetic animated video lessons with voiceover) + Chat Tutor. | *"Sadece cevabı değil, videolu ve sesli anlatımla adım adım özel öğretmen gibi öğretir."* | **Pure Generative Multimodal LLM (Zero CAS):** Converts OCR text to LLM script, then synthesizes a pre-rendered animated video. Completely unverified; hallucinates on Turkish YKS word problems and edge cases with synthetic confidence. | Aggressive auto-renew subscriptions (3-day free trial -> ~600–1,500 TRY/yr); widespread user complaints regarding unexpected auto-charges. | **Passive Video Illusion:** Students passively watch animations without active cognitive retention. Verified AI replaces static `.mp4` with **Active 4-Tier Socratic Scaffolding + Interactive Apple Pencil Canvas + SymPy 0% Hallucination Proof (`DOC-93`)**. |

### 5.2 Key Voice-of-Customer Signals in Turkey (`USER_REVIEW_SIGNAL`)
1. **"Video çözümü izliyorum, anladım sanıyorum ama denemede yine aynı tarz soruyu kaçırıyorum"** *(The Illusion of Competence)*: Students watch thousands of QR-code publisher video solutions passively without performing retrieval practice on an isomorphic twin question.
2. **"Kunduz'da eğitmen kendi bildiği yoldan çözüp atmış, benim işlemimde nerede hata yaptığımı söylememiş"**: Students want validation of *their own method* (`V031` `attempt_verified_methods` + `V035` `AttemptEvaluation`), not a disconnected rigid solution.
3. **"Deneme analizi yaparken sadece 'Fonksiyonlar 2 yanlış' yazıyorum ama fonksiyonun neresinde hata yaptığımı 2 hafta sonra unutuyorum"**: Directly validates the need for the **Mistake DNA / Mistake Passport (`learning/83`)** with specific misconception codes (`FUNC_COMPOSITION_ORDER`, `ALG_DOMAIN_EXCLUSION_MISSED`).

---

## 6. Deep Competitor Teardown: International Math Solvers & AI Tutors

### 6.1 Global Competitor Matrix

| Competitor | Category & Owner (`VERIFIED_PUBLIC_FACT`) | Pricing Structure (`VERIFIED_PUBLIC_FACT`) | Symbolic Engine vs. LLM (`INFERENCE`) | Step-by-Step Error Detection on Student Work | Twin Practice & Longitudinal Mastery | Strategic Gap vs. Verified AI Learning OS |
|---|---|---|---|---|---|---|
| **Photomath** (Google) | Camera Math Solver | Free basic steps; Photomath Plus `~$9.99/mo` or `~$69.99/yr` | Proprietary deterministic rule-tree + OCR | **No:** Scans the *problem prompt* and outputs its own animated textbook steps. Does not grade a student's multi-step attempt. | **No:** Static solver history; no spaced repetition or counterfactual twin drills. | Photomath is an **answer lookup utility**; Verified AI is a **diagnostic coach** that audits the student's steps and verifies independent transfer. |
| **Symbolab** (Course Hero / Learneo) | Symbolic Calculator & Practice | Free answers; Pro `~$9.99/mo` or `~$29.99–$69.99/yr` | Deterministic CAS engine + step rules | **Partial (Syntax/Web Practice only):** Strong CAS solver, but mobile UX is calculator-centric and does not diagnose multi-step handwritten/typed student derivations with causal misconception attribution. | **Basic:** Web practice quizzes exist, but are disconnected from causal step-error diagnosis on user homework. | Symbolab feels like an engineering calculator; Verified AI delivers a **mobile-native Personal Math Coach** with causal Mistake DNA. |
| **Mathway** (Chegg) | Multi-Domain Math Calculator | Free final answer; Premium `~$9.99/mo` or `~$39.99–$79.99/yr` | Rule-based symbolic solver | **No:** Paywalls step-by-step solution to the prompt; zero inspection of student intermediate steps. | **No:** Pure transactional solver. | Vulnerable to churn once homework ends; lacks longitudinal learning state. |
| **Wolfram\|Alpha** | Computational Knowledge Engine | Free output; Pro `~$5.00–$9.99/mo` (`~$60/yr`) | Mathematica symbolic kernel (Gold-standard CAS) | **Step-by-step verification exists for select desktop inputs**, but mobile UX is complex, English-centric, and not designed as a K-12/exam pedagogical coach. | **No:** Problem generator is stateless and not tied to a learner mistake ledger or exam readiness model. | Wolfram is a professional computation engine; Verified AI combines **symbolic rigor with high-school/exam pedagogy, Mistake DNA, and daily spaced coaching**. |
| **Gauth (Gauthmath)** (ByteDance) | AI + Human Homework Helper | Free limited; Gauth Plus `~$11.99/mo` | Multimodal LLM + question bank matching + human fallback | **Low reliability (`USER_REVIEW_SIGNAL`):** Optimized to deliver fast homework answers across all subjects; prone to LLM step hallucinations when database match fails. | **No:** Answer-delivery workflow Encourages copying (Bastani et al. crutch effect). | Gauth optimizes for *cheating/completion*; Verified AI optimizes for **exam survival where the phone is taken away**. |
| **Khanmigo** (Khan Academy) | Conversational Socratic AI Tutor | Free for teachers; `~$4/mo` (`$44/yr`) for learners/parents | LLM prompt-scaffolded tutor (GPT-4 class) | **Conversational only:** Refuses to give direct answers and asks Socratic questions, but lacks a deterministic symbolic step-diff engine (`ADR-010`) and counterexample generator (`counterexample_engine.py`). | Tied to Khan Academy course videos/exercises rather than arbitrary student exam problems. | Khanmigo can still be confused by subtle algebraic equivalences; Verified AI anchors every Socratic hint in **deterministic SymPy verification**. |
| **ThetaWise** | Multimodal AI Math Tutor | Limited free; Pro `~$19.99/mo` (`~$119.99/yr`) | LLM + symbolic tool-use hybrid, whiteboards, AI video notes | **Interactive chat steps**, but focused on broad US college STEM PDFs/lectures rather than deterministic step-diff mistake ledgers and localized exam coaching. | Custom quiz generation, no formal BKT-lite independent vs assisted evidence separation. | Validates willingness to pay `$19.99/mo` for AI math tutoring while leaving open the **deterministic Mistake Passport + Exam Coach** wedge. |
| **Brilliant.org** | Interactive STEM Courses | Free intro; Premium `~$13.99–$24.99/mo` (`~$149/yr`) | Curated interactive puzzles | **Course-bound:** Only checks pre-authored multiple-choice or single-box puzzle inputs inside Brilliant's own courses. | Course progression only; cannot inspect a student's external YKS/AP/IB practice problem. | Brilliant is "Duolingo for STEM concepts"; Verified AI is **"Your personal exam-prep math coach that works on your real practice problems."** |

---

## 7. Subscription & Funnel Benchmarks (`VERIFIED_PUBLIC_FACT` / `EMPIRICAL_RESEARCH`)

Grounded in the **RevenueCat State of Subscription Apps (2025–2026)** dataset across Education and AI applications:

1. **Plan Duration Preference:** In the Education category, **annual plans represent 51%–59% of subscriptions**, while monthly plans represent ~30% (`VERIFIED_PUBLIC_FACT`). However, in Turkey's YKS market, exam periodicity creates a strong seasonal demand curve peaking between October and May (`INFERENCE`), making a **6-Month YKS Exam Season Pass** and **Monthly Coach Pro** critical alongside the Annual plan.
2. **Trial Length vs. Conversion:** Standard 5-to-9 day trials (7-day baseline) are the norm in Education, while Day-0 cancellation is high if the user does not experience an immediate "aha" moment in Session 1 (`VERIFIED_PUBLIC_FACT`).
3. **Download-to-Paid Conversion:** Median Education Day-14 Revenue Per Install (RPI) is `~$0.30`; freemium download-to-paid conversion averages `~2.1%` by Day 35 versus `~10.7%` for hard paywalls (`VERIFIED_PUBLIC_FACT`). Because Verified AI relies on organic habit loops and student word-of-mouth, our model uses a **Value-First Freemium + Contextual Paywall** architecture (`roadmap/88_LEARNING_OS_MONETIZATION_GROWTH_AND_VALIDATION_PLAN.md`) and models conservative `1.5%–3.5%` free-to-paid conversion in base scenarios rather than assuming top-decile hard-paywall conversion.

---

## 8. Customer Research Validation Protocol & Requirements

### 8.1 Requirements Derived from Market Research

| Requirement ID | Requirement Statement | Target Sprint |
|---|---|---|
| `MKT-RES-001` | Every problem verification result must support alternative valid student solution methods (`V031` `attempt_verified_methods`) so students are never penalized for solving via a valid path different from a textbook answer key. | `Sprint 7.3` (Implemented) / `Sprint 7.5` |
| `MKT-RES-002` | Pricing and packaging must support Turkish Purchasing Power Parity (PPP) and seasonal YKS timelines (Monthly, Annual, and Exam Season Pass) via StoreKit 2 (`com.verifiedai.billing`). | `Sprint 10.1` / `Sprint 10.2` |
| `MKT-RES-003` | Product onboarding must deliver a complete **Diagnosis -> Counterexample -> Verified Twin** "aha" loop within the first 3 minutes of Session 1 before presenting a trial paywall. | `Sprint 8.3` / `Sprint 10.2` |

### 8.2 Explicit Assumptions & Non-Goals
- **Assumption `A-MKT-01` (`FOUNDER_HYPOTHESIS`):** YKS Sayısal/EA students who already pay for question banks or YouTube-based study routines will pay `199–349 TRY/month` (`~$5.80–$10.00/mo`) for an always-on Personal AI Math Coach that diagnoses their step mistakes and tracks their Mistake DNA.
- **Non-Goal:** Competing on human tutor SLA response times (we do not staff human Q&A queues).

### 8.3 Acceptance Criteria
1. Market sizing in all canonical documents separates raw ÖSYM exam headcount (`2.35M–2.82M` TYT; `1.55M–1.78M` AYT) from addressable digital buyers (`TAM/SAM/SOM`).
2. Competitor claims are never cited as verified capabilities without an explicit `COMPETITOR_CLAIM` or `INFERENCE` tag.
3. The `Sprint 11.9A` 50-student TestFlight cohort collects structured willingness-to-pay (Van Westendorp / price-sensitivity) and competitor-switching survey data.

---

## 9. Risks and Open Decisions

- **Risk `R-MKT-01` (Macroeconomic Purchasing Power in Turkey):** High domestic inflation can compress USD-equivalent ARPU in Turkey.
  - *Mitigation:* Strict deterministic-first cost engineering (`ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`) keeping per-solve variable AI cost near `$0.0058`, plus Phase 12.6 international English tier-1 expansion (`IB/AP/SAT`) at `$14.99–$19.99/mo`.
- **Open Decision `OD-MKT-01`:** Exact TRY price points for the 6-Month YKS Season Pass vs. Annual Plan during the `Sprint 11.9A` / `Sprint 10.2` pricing test.

---

## 10. Cross-References

- `product/01_PRODUCT_VISION_AND_POSITIONING.md`
- `product/05_MONETIZATION_AND_ENTITLEMENTS.md`
- `product/76_COMPETITIVE_INTELLIGENCE_AND_MARKET_POSITIONING_MATRIX.md`
- `product/81_LEARNING_OS_PRODUCT_VISION_AND_POSITIONING.md`
- `roadmap/80_UNIT_ECONOMICS_30_DAY_LAUNCH_CONTRACT_AND_EXECUTION_ROADMAP.md`
- `roadmap/88_LEARNING_OS_MONETIZATION_GROWTH_AND_VALIDATION_PLAN.md`
- `operations/92_LEARNING_OS_ASSUMPTIONS_EXPERIMENTS_AND_EVIDENCE_LEDGER.md`
