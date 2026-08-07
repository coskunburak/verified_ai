# Product Vision and Positioning

## 1. Product thesis

Generic AI assistants are already strong at reading questions, producing answers, explaining concepts and tutoring conversationally. The product therefore cannot win by being another "take a photo and get an answer" application.

The recurring user problem is larger:

> I do not know which parts of mathematics I actually understand, why I keep making the same mistakes, whether an AI-generated answer can be trusted, or what I should study next to improve before an exam.

The product combines five layers that are usually fragmented across homework solvers, chat assistants, tutoring platforms and exam applications:

1. Problem understanding.
2. Solution generation.
3. Verification and uncertainty.
4. Learning diagnosis.
5. Adaptive next action.

---

## 2. Product promise

### Primary promise
> Scan or enter a math problem, receive a clear solution, see whether it could be independently verified, understand your mistake, and automatically improve your learning plan.

### Long-term promise
> The longer the student uses the product, the more accurately the system understands the student's mathematical strengths, weaknesses, recurring mistakes, exam risk and optimal next practice.

## 2A. Phase 1 Product Charter Baseline

This charter is the canonical Phase 1 baseline for scope and success. It prevents roadmap ideas from silently becoming Production V1 obligations.

### Initial target learner

Production V1 primarily serves an independent secondary-school or early university learner studying mathematics, with an exam or course outcome and a need for trustworthy step-by-step help. The product may support adjacent learners, but V1 product, UX, data, and verification choices optimize for this initial learner rather than every student, teacher, parent, or institution.

### Product promise for V1

The V1 promise is: math problem assistance that exposes verification evidence and uncertainty, helps the learner understand the solution and their mistakes, and preserves structured learning state for future practice.

### Scope taxonomy

| Scope label | Meaning | Examples |
|---|---|---|
| MVP | Earliest useful slice proving capture or entry -> parse -> solve -> verification status -> explanation with safe failure states. | Typed/image math input, parse review, async solve, visible verification state, basic history. |
| Production V1 | Commercially credible iOS-first mathematics learning product with trustworthy solving, mistake intelligence, server-authoritative state, subscriptions, privacy, telemetry, and quality gates. | Verified solve, tutor, mistake book, mastery v1, StoreKit-backed entitlements, rate limits, admin traces, golden evaluation gates. |
| V1.5 | Learning-depth expansion after V1 quality and economics are validated. | Adaptive daily plan, spaced repetition, weekly reports, broader verification coverage. |
| V2 | Exam/course platform expansion after core learner value is proven. | Exam definitions, mock exams, readiness, PDF/course import, richer assessment workflows. |
| Future | Optional expansion only after evidence supports it. | Android/web, physics, teacher/classroom mode, parent summaries, proprietary ML Phase 13. |

### V1 non-goals

- Generic chat assistant.
- Cheating-first answer dump.
- All academic subjects at launch.
- All mathematics domains at launch.
- Teacher marketplace or human tutor marketplace.
- Android-first or web-first client.
- Microservice architecture.
- Firestore as canonical product storage.
- Client-authoritative entitlement, mastery, verification, billing, or exam scoring.
- Foundation-model training, self-hosted production inference, or proprietary solver training.
- Unverified certainty or LLM self-confidence presented as product truth.

### Success metrics

Phase 1 establishes these metrics as product/engineering targets for later instrumentation. Exact numeric thresholds are set only after baseline measurement.

| Metric | Category | Why it matters |
|---|---|---|
| Weekly Verified Learning Sessions | North-star/product quality | Measures sessions that combine useful learning activity with verified/reference evidence. |
| Mastered Skills per Active Learner | Learning outcome | Measures durable improvement rather than answer consumption. |
| Verification Success Rate | Trust quality | Measures where the product can keep its verification promise. |
| Cost per Verified Solution | Unit economics | Connects AI spend to successful trusted outcomes. |
| First Solution Completion Rate | Activation | Measures whether new learners reach product value. |
| Parse Correction Rate | Product/input quality | Shows OCR/parser friction and taxonomy accuracy. |
| Mistake Diagnosis Dispute Rate | Learning quality | Measures trust in mistake intelligence. |
| D7/D30 Returning Learning Sessions | Retention | Measures repeated learning use, not only app opens. |
| Paid Conversion After Verified Value | Commercial | Measures monetization after value demonstration. |
| p95 Time to Result by Problem Class | Reliability/UX | Measures latency for the end-to-end solve experience. |

---

## 3. What the product is

The product is simultaneously:

- an AI-assisted mathematics solver,
- a deterministic verification platform,
- a Socratic tutor,
- an automatic mistake notebook,
- a skill mastery graph,
- an adaptive practice engine,
- an exam-preparation system,
- a persistent learning profile.

The value comes from the system, not from any single model call.

---

## 4. What the product is not

It is not:

- a generic chat interface,
- a thin wrapper around one model provider,
- a cheating-first answer generator,
- a system that claims certainty without verification,
- a product that tries to support every academic subject in V1,
- a paywall-first app that hides value before purchase,
- a platform that lets UI state become authoritative business state.

---

## 5. Initial subject and scope

### Subject
Mathematics.

### Initial skill families
- arithmetic,
- algebra,
- equations,
- functions,
- limits,
- derivatives,
- basic integrals.

Why these first:
- frequent student demand,
- structured parsing is practical,
- deterministic or semi-deterministic verification is often possible,
- stable skill taxonomy is feasible,
- narrow enough for one high-quality product team/developer.

### Future expansion
- trigonometry,
- linear algebra,
- probability,
- statistics,
- advanced calculus,
- physics,
- exam-specific curricula.

---

## 6. Competitive positioning

### Against ChatGPT/Gemini
They are excellent at:
- reading questions,
- explaining,
- generating examples,
- tutoring conversationally.

We differentiate through:
- persistent structured problem history,
- verification evidence,
- attempt-level mistake diagnosis,
- mastery by canonical skill,
- automatic next-best-practice decision,
- exam/curriculum awareness,
- product workflow requiring fewer prompts.

### Against Gauth-like homework solvers
They are strong at:
- camera capture,
- broad subject coverage,
- fast solution generation,
- step-by-step help.

We differentiate through:
- narrower initial scope with deeper trust,
- explicit uncertainty,
- first-divergence mistake intelligence,
- long-term knowledge graph,
- adaptive study planning,
- deterministic math verification where feasible.

---

## 7. Product principles

### P1 — Trust must be earned
Never label an answer verified because a model says it is correct.

### P2 — Learning data is more valuable than answer history
Attempts, hints, mistakes, time, difficulty and skill context are first-class domain data.

### P3 — Reduce prompting
The product should decide what the learner should do next without requiring prompt engineering.

### P4 — Uncertainty is a feature
"Unable to verify" is superior to false confidence.

### P5 — Depth before breadth
A small number of math domains done exceptionally well is better than weak support for every subject.

### P6 — AI is replaceable infrastructure
The domain must survive model/provider changes.

### P7 — Value increases over time
History, mastery and personalization should create switching cost and increasing utility.

---

## 8. North-star lifecycle

1. User joins and identifies level/goal.
2. User scans a real problem.
3. System parses and normalizes it.
4. System solves it.
5. Verification pipeline evaluates evidence.
6. User attempts related work.
7. System identifies a recurring weakness.
8. Mastery graph updates.
9. Daily plan adapts.
10. Targeted practice reduces the error pattern.
11. Exam readiness rises.
12. User trusts the product because uncertainty and evidence are visible.

---

## 9. Product success statement

The intended user reaction is:

> I came for answers, but I stay because it understands what I keep getting wrong and tells me what to practice.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## 2026-08 AI strategy clarification

The product is not differentiated by owning a foundation model. Production V1 intentionally uses provider-neutral external models while the platform owns verification, learning state, mistake intelligence, adaptive decisions, evaluation, and unit-economics controls.

The long-term product moat is expected to accumulate in verified mathematical evidence, structured learner-state models, governed datasets, and task-specific learning intelligence. Proprietary models are introduced only where they improve measurable learner value or sustainable economics.

A marketing claim such as “our own AI model” is not a roadmap objective. Trustworthy learning outcomes are.
<!-- HYBRID_AI_STRATEGY_V3:END -->
