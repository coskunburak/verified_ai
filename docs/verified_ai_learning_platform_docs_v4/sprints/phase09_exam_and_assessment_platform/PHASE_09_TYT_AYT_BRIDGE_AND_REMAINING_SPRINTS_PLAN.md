# Phase 9 — TYT/AYT Yeni Nesil Ara Paketler ve Sprint 9.7–9.10 Production Uygulama Planı

- **Durum:** `APPROVED_OPTION_A_5_WAVES` (Sprint `9.1`–`9.6` `IMPLEMENTED & VERIFIED`; Onaylanan Uygulama Yapısı — **Seçenek A (5 Dalga):** `Dalga 1: Sprint 9.6B [Bridge B1+B2+B3, V060]` -> `Dalga 2: Sprint 9.7 + Bridge B4 [V061]` -> `Dalga 3: Sprint 9.8 [V062]` -> `Dalga 4: Sprint 9.9 [V063]` -> `Dalga 5: Sprint 9.10 [V064]`)
- **Tarih:** `2026-11-16`
- **Kanonik Referanslar:**
  - `sprints/phase09_exam_and_assessment_platform/PHASE_09_EXAM_AND_ASSESSMENT_PLATFORM.md`
  - `sprints/phase09_exam_and_assessment_platform/SPRINT_9.6B_TYT_AYT_CURRICULUM_EXPANSION_QUESTION_BANK_AND_SYMPY_BENCHMARKS.md`
  - `sprints/phase09_exam_and_assessment_platform/SPRINT_9.7_WEAK_AREA_PRIORITIZATION_AFTER_ASSESSMENTS.md`
  - `sprints/phase09_exam_and_assessment_platform/SPRINT_9.8_FINAL_REVIEW_AND_PRE_EXAM_STUDY_MODE.md`
  - `sprints/phase09_exam_and_assessment_platform/SPRINT_9.9_EXAM_ANALYTICS_REPORTS_AND_PROGRESS_COMPARISON.md`
  - `sprints/phase09_exam_and_assessment_platform/SPRINT_9.10_ASSESSMENT_INTEGRITY_ACCESSIBILITY_FAILURE_RECOVERY_AND_RELIABILITY.md`
  - `adr/ADR-005_API_FIRST_PROGRESSIVELY_PROPRIETARY_AI_STRATEGY.md`
  - `adr/ADR-006_PRODUCTION_STUDENT_DATA_NOT_TRAINING_DATA_BY_DEFAULT.md`
  - `adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md`
  - `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md`

---

## 1. Mevcut Durum Analizi (Baseline Audit — `Sprint 9.6` Sonrası)

| Katman | Mevcut Durum (`V054`–`V059`) | Tespit Edilen Boşluk (TYT/AYT Hedefi) | Seçenek A Dalgası |
|---|---|---|---|
| **Müfredat Ontolojisi** | `StaticCurriculumTaxonomyCatalog` toplam **27 skill / 7 topic** içeriyor (`ARITHMETIC`, `ALGEBRA`, `EQUATIONS`, `FUNCTIONS`, `LIMITS`, `DIFFERENTIATION`, `INTEGRATION`). | **Oran-Orantı, Hareket/Yaş/İşçi/Karışım Yeni Nesil Problemleri, Polinomlar, Logaritma, İleri Trigonometri, Diziler, Permütasyon-Kombinasyon-Olasılık, Geometri** yok. | **Dalga 1 (`Sprint 9.6B` / `B1`)** |
| **Soru Bankası (`V056`)** | Yalnızca **16 adet** İngilizce `TYT-MATH-2026-*` tohum sorusu (4 alt konu, `has_visual_diagram = FALSE`); **0 adet `AYT-MATH`** ve **0 adet uzun Türkçe problem** var. | Blueprint montajı gerçek sınavda yetersiz kalıyor (*"Bu plan için ek doğrulanmış sorular hazırlanıyor"* uyarısı). | **Dalga 1 (`Sprint 9.6B` / `B2`)** |
| **Golden Benchmark (`evaluations/`)** | `ingestion-v1` (18 vaka), `solver-v1` (28 vaka, EN), `attempts`, `methods`, `tutoring`, `visualizations` mevcut. | **Türkçe Yeni Nesil TYT Paragraf Problem** ve **Zor AYT Matematik** parse/solve golden veri seti henüz yok. | **Dalga 1 (`Sprint 9.6B` / `B3`)** |
| **AI Eğitim & Kalibrasyon** | `ADR-006` gereği tüm üretim öğrenci verisi `training_eligible = FALSE`; `V1` API-first + `SymPy` verifier ile çalışıyor. | `Phase 13` açık ağırlıklı matematik modeline (`tr-TR` TYT/AYT) zemin hazırlayacak temiz, `SymPy`-doğrulanmış SME veri boru hattı kurulmamış. | **Dalga 2 (`Sprint 9.7` + `B4`)** |
| **Phase 9 Kalan Sprintler** | `9.1`–`9.6` tamamlandı ve doğrulandı (`LOCAL_ACCEPTANCE_PASSED`). | `9.7` (Hata Karnesi + `B4`), `9.8` (48h Özet PDF), `9.9` (Hız/Korelasyon Analitiği), `9.10` (Tamper + `tr-TR` VoiceOver) bekliyor. | **Dalga 2–5 (`Sprint 9.7`–`9.10`)** |

---

## 2. Seçenek A (5 Dalga) Uçtan Uca Uygulama Sırası ve Migrasyon Haritası

| Dalga | Kapsam | Flyway / Paket | Çıkış Kabul Kapısı |
|---|---|---|---|
| **Dalga 1** | `SPRINT_9.6B` (`Bridge B1 + B2 + B3`: `2026.2` Müfredat + `>= 120` Türkçe TYT/AYT Soru Bankası + `yks-tr-*` Golden Benchmarks & `SymPy` Kapısı) | `V060` | `bash scripts/quality/sprint_9_6b_owner_acceptance.sh --VerifyHost` |
| **Dalga 2** | `SPRINT_9.7` (`H-03` Deneme Hata Karnesi + StudyPlan ROI Sync) + `Bridge B4` (`Phase 13` Temiz Seed Export & Qwen Baseline) | `V061` | `bash scripts/quality/sprint_9_7_owner_acceptance.sh --VerifyHost` |
| **Dalga 3** | `SPRINT_9.8` (`H-15` 48 Saat Panic Inhibition + On-Device `PDFKit` Kişisel Tuzak/Formül Özeti) | `V062` | `bash scripts/quality/sprint_9_8_owner_acceptance.sh --VerifyHost` |
| **Dalga 4** | `SPRINT_9.9` (`INV-EXAM-005` Uzunlamasına Analitik, Konu Hız-Zaman & Tuzak Azalması Korelasyonu) | `V063` | `bash scripts/quality/sprint_9_9_owner_acceptance.sh --VerifyHost` |
| **Dalga 5** | `SPRINT_9.10` (`INV-EXAM-006` `HMAC-SHA256` Bütünlük + `tr-TR` VoiceOver Matematik + **Phase 9 Exit Gate**) | `V064` | `bash scripts/quality/sprint_9_10_owner_acceptance.sh --VerifyHost` |

```
[Dalga 1 — Sprint 9.6B (Bridge B1+B2+B3): V060 + Türkçe TYT/AYT + SymPy Evals]
          |
          v
[Dalga 2 — Sprint 9.7 + Bridge B4: V061 (Deneme Hata Karnesi + StudyPlan ROI + Phase 13 Seed Export)]
          |
          v
[Dalga 3 — Sprint 9.8: V062 (48 Saat Pre-Exam Modu + On-Device PDF Cheat Sheet)]
          |
          v
[Dalga 4 — Sprint 9.9: V063 (Uzunlamasına Analitik, Hız-Baskısı & Tuzak Korelasyonu)]
          |
          v
[Dalga 5 — Sprint 9.10: V064 (HMAC Bütünlük, tr-TR VoiceOver Matematik & Phase 9 Exit Gate)]
```

---

## 3. Ara Paketler (`Bridge B1–B4`): TYT Yeni Nesil & Zor AYT Hazırlığı

### 3.1 Paket `B1` — ÖSYM TYT/AYT Müfredat Ontolojisi & Versiyon `2026.2` (`V060` Bölüm 1)
- **Hedef:** Yeni nesil TYT problemleri ve AYT ileri matematik alt konularının `ExamBlueprintValidator`, `ExamReadinessCalculator` ve `MistakeTaxonomy` ile tam uyumlu hale getirilmesi.
- **Kapsam & Değişiklikler:**
  1. `services/api/src/main/java/com/verifiedai/curriculum/application/StaticCurriculumTaxonomyCatalog.java` içine aşağıdaki topic ve skill'ler eklenir:
     - `MATH.WORD_PROBLEMS` -> `NUMBER_FRACTION`, `RATIO_PROPORTION`, `PERCENTAGE_PROFIT_LOSS`, `MOTION_WORK_AGE`, `DATA_GRAPH_INTERPRETATION`, `ROUTINE_NONROUTINE_LOGIC`
     - `MATH.ALGEBRA` -> `POLYNOMIALS`, `COMPLEX_NUMBERS`, `LOGARITHMS_EXPONENTIAL`, `ABSOLUTE_VALUE`
     - `MATH.SEQUENCES` -> `ARITHMETIC_SEQUENCES`, `GEOMETRIC_SEQUENCES`
     - `MATH.TRIGONOMETRY` -> `IDENTITIES_AND_REDUCTION`, `SUM_DIFFERENCE_DOUBLE_ANGLE`, `TRIG_EQUATIONS`
     - `MATH.PROBABILITY` -> `PERMUTATION_COMBINATION_BINOMIAL`, `CONDITIONAL_PROBABILITY`
     - `MATH.GEOMETRY` -> `TRIANGLES_SIMILARITY`, `CIRCLES_AND_POLYGONS`, `ANALYTIC_LINES_CIRCLES`
  2. `V060__yks_tyt_ayt_curriculum_expansion_and_question_bank.sql`:
     - `TR_YKS_TYT_MATH` ve `TR_YKS_AYT_MATH` için `2026.2` (`status = 'ACTIVE'`) sınav tanımları eklenir; önceki `2026.1` kayıtları `DEPRECATED` (`superseded_at` / `superseded_by_version = '2026.2'`) olurken mevcut `student_exam_targets` snapshot'ları (`V054`) değişmeden korunur.
     - `exam_topic_weights` toplamı tam `40.0000` olacak şekilde korunur (`ExamBlueprintValidator` ağırlık korunumu invariantı).
  3. `MistakeTaxonomy.java`: Yeni nesil problem ve AYT tuzakları için canonical mapping korunur/zenginleştirilir (`WORD_VARIABLE_MISASSIGNMENT`, `DOM_LOG_NON_POSITIVE`, `DOM_EXTRANEOUS_ROOT_DENOM`, `TRIG_PERIOD_OMITTED`, `ABS_SINGLE_BRANCH_ONLY`, `CALC_LIMIT_INDETERMINATE_SUB`, `CALC_CHAIN_RULE_OMITTED`, `CALC_INTEGRAL_BOUNDS_SWAPPED`).

### 3.2 Paket `B2` — Türkçe Yeni Nesil TYT & Zor AYT Soru Bankası + Çeldirici Tuzak Eşlemesi (`V060` Bölüm 2)
- **Hedef:** Hem TYT hem AYT blueprint'lerinin hiç "eksik soru" uyarısı vermeden (`assessment_assembly`) doldurulabilmesi; her sorunun gerçek ÖSYM paragraf/zorluk düzeyinde ve 5 şıklı (`A–E`) tuzak rasyonelli olması.
- **Üretim Kuralları (`V056` Şema İnvaryantları):**
  - Her soru için `exam_question_provenance_records` kaydı zorunludur (`author_id <> reviewer_id`, `source_type IN ('ORIGINAL_AUTHORED', 'SME_CURATED_SYNTHETIC')`, `license_classification = 'PROPRIETARY_INTERNAL'`, `verification_status = 'VERIFIED'`, `training_eligible = FALSE`).
  - Tüm sorular **Türkçe (`stem_markdown`)** yazılır; çoktan seçmeli **5 şık (`A`..`E`)** `exam_question_options` tablosuna işlenir ve yanlış şıkların `distractor_rationale` alanı öğrencinin yapacağı olası `mistake_code` ile eşlenir (Sprint 9.7 hata karnesi ile doğrudan konuşur).
  - Görsel/grafik/tablo içeren yeni nesil sorularda `has_visual_diagram = TRUE` ve erişilebilirlik için en az 10 karakterlik Türkçe `alt_text_description` zorunludur.
  - Tüm `content_sha256` ve `provenance_hash` alanları deterministik SHA-256 doğrulama testiyle korunur.
- **Hedef Soru Hacmi & Blueprint Seti:**

| Sınav | Blueprint Kodu | Soru Sayısı | Zorluk Dağılımı (`easy/med/hard/expert` bps) | Havuz Hedefi |
|---|---|---|---|---|
| `TR_YKS_TYT_MATH` | `BP-TYT-MATH-DIAG-2026-V1` | `8` (15 dk) | `2500 / 5000 / 2500 / 0` | Güncellenmiş 4+4 Türkçe havuz |
| `TR_YKS_TYT_MATH` | `BP-TYT-MATH-DRILL-2026-V1` | `10` (20 dk) | `2000 / 4000 / 3000 / 1000` | Yeni nesil problem ağırlıklı |
| `TR_YKS_TYT_MATH` | `BP-TYT-MATH-WORD-PROBLEMS-V2` | `12` (22 dk) | `1000 / 4000 / 4000 / 1000` | **En az 36 Türkçe Yeni Nesil Problem** |
| `TR_YKS_TYT_MATH` | `BP-TYT-MATH-FULL-SIM-2026-V2` | `40` (65 dk) | `2000 / 4500 / 2500 / 1000` | **Toplam >= 60 Türkçe TYT Sorusu** |
| `TR_YKS_AYT_MATH` | `BP-AYT-MATH-DIAG-2026-V2` | `10` (25 dk) | `1000 / 4000 / 4000 / 1000` | Polinom, Log, Trig, Limit, Türev, İntegral |
| `TR_YKS_AYT_MATH` | `BP-AYT-MATH-LTI-DRILL-2026-V2` | `12` (30 dk) | `0 / 2500 / 5000 / 2500` | Sadece Limit-Türev-İntegral Zor Set |
| `TR_YKS_AYT_MATH` | `BP-AYT-MATH-FULL-SIM-2026-V2` | `40` (90 dk) | `1000 / 3500 / 4000 / 1500` | **Toplam >= 60 Zor Türkçe AYT Sorusu** |

### 3.3 Paket `B3` — Türkçe TYT/AYT Golden Datasets & SymPy Doğrulama Kapısı (`evaluations/`)
- **Hedef:** Yapay zekânın Türkçe uzun paragraf problemlerini matematiksel modele dönüştürme (`parsing`) ve zor AYT matematik sorularını doğru adım/kısıtlarla çözme (`solving`) başarısını ölçülebilir ve regresyon geçirmez hale getirmek.
- **Konumlar ve Yapı:**
  1. `evaluations/golden-datasets/parsing/yks-tr-ingestion-v1/` (`manifest.json` / `regression.jsonl` / `hard-tail.jsonl`):
     - Uzun Türkçe yeni nesil problem metinleri, birim/kısıt tuzakları (`x > 0`, tam sayı şartı, yüzde artış-azalış zinciri), sentetik el yazısı deneme kağıdı vakaları (`trainingEligibility: NOT_ELIGIBLE`).
  2. `evaluations/golden-datasets/solving/yks-tr-hard-v1/`:
     - En az **60 doğrulanmış vaka** (`30` TYT yeni nesil problem denklem kurma + `30` zor AYT polinom/logaritma/trigonometri/limit/türev/integral vakası).
     - Her vaka `solver-evaluation-case-v1` şemasına uyar, `locale: "tr-TR"`, `slices` içinde `YKS_TYT_PROBLEM`, `YKS_AYT_CALCULUS`, `YKS_AYT_TRIG`, `YKS_AYT_LOG`, `DOMAIN_TRAP`, `HARD_TAIL` etiketlerini taşır.
  3. **Otomatik Kalite Kapısı (`scripts/quality/validate_yks_tr_benchmarks.py`):**
     - `V060` içindeki her sorunun `numeric_correct_answer` / `stem_latex` ve doğru şık değerini `services/math-verifier` (`SymPy`) ile karşılaştırır.
     - Yanlış şık rasyonellerinin geçerli `MistakeTaxonomy` kodları içerdiğini ve SHA-256 manifest bütünlüğünü doğrular.

### 3.4 Dalga 1 Çıkış Kapısı & `Paket B4` Bağlantısı (`Sprint 9.6B` -> `Dalga 2 / Sprint 9.7`)
- **Dalga 1 (`SPRINT_9.6B` = `B1 + B2 + B3`, `V060`):** Yukarıdaki `3.1`–`3.3` adımlarının tamamı `bash scripts/quality/sprint_9_6b_owner_acceptance.sh --VerifyHost` kapısıyla birlikte tek paket halinde teslim edilir (`SPRINT_9.6B_TYT_AYT_CURRICULUM_EXPANSION_QUESTION_BANK_AND_SYMPY_BENCHMARKS.md`).
- **Paket `B4` (`Dalga 2 / Sprint 9.7` İçinde) — `Phase 13` Model İyileştirme Köprüsü (`ADR-006` Uyumlu, Sıfır Öğrenci Verisi):**
  - **Hedef:** `V1`'de API-first + SymPy yaklaşımını bozmadan, ileride kendi matematik modelimizi (`Phase 13`) Türkçe TYT/AYT üzerine eğitebilmek için **temiz, lisanslı, halüsinasyonsuz** veri sözleşmesini bugünden hazırlamak.
  - **Üretim öğrenci verisi asla kullanılmaz (`ADR-006`):** Yalnızca `B2` ve `B3` kapsamında SME / sentetik üretilmiş ve `SymPy` tarafından her ara adımı doğrulanmış (`STEP_VERIFIED`) kayıtlar `evaluations/exports/yks-tr-phase13-seed/` formatına (JSONL: `problem_tr`, `canonical_equations`, `verified_steps`, `trap_distractors`, `license`, `sympy_digest`) dönüştürülür.
  - `evaluations/runners/summarize_qwen_evaluation.py` üzerinden Türkçe TYT/AYT slayslarında baz model (`API baseline` vs açık ağırlıklı adaylar) skorlanıp karar kaydı (`PHASE_13` giriş kapısı) oluşturulur.

---

## 4. Seçenek A: Dalga 2–5 (`Sprint 9.7–9.10`) Production Uygulama Planı

### 4.1 Dalga 2 — Sprint 9.7 + `Bridge B4` — Deneme Sonrası Hata Karnesi, Zayıf Alan Önceliklendirme (`H-03`) & `Phase 13` Seed İhracı
- **Durum (2026-11-17): COMPLETED — OWNER HOST ACCEPTANCE PASSED.** V061 kodu, 120 kayıtlık temiz ihracat ve native telafi navigasyonu hazır; kullanıcı host kabulünü ve 430 dosyalık doküman kontrolünü başarıyla tamamladı. Qwen baseline: `REQUIRES_MORE_EVIDENCE`. Ayrıntılar: [Sprint 9.7 execution report](SPRINT_9.7_EXECUTION_REPORT.md).
- **Önkoşul:** `Dalga 1 (Sprint 9.6B / V060)` + `Sprint 9.6 (V059)` kabul edilmiş olmalı.
- **Mimari & Ownership (`com.verifiedai.exam` + `com.verifiedai.studyplan` + `evaluations/exports/`):**
  - `ExamSession` + `ExamSessionScoreSnapshot` (`V058`) + `ExamScratchpadAsset` (`F28`) + `Mistake DNA` (`V037`) verilerini tüketerek her yanlış ve boş soru için 4 deterministik sınıflandırma üretir:
    1. `CONCEPTUAL_GAP` ($M_{u,s} < 0.45$)
    2. `RECURRING_MISTAKE_TRAP` (aktif `mistake_code` eşleşmesi)
    3. `TIME_PRESSURE_SLIP` (soru başı süre > beklenen sürenin `%135`'i ve aritmetik/işlem hatası)
    4. `STRATEGIC_OMISSION` (bilinçli/zaman kaynaklı boş bırakma)
  - Net kazanım/saat formülü (`H-03`) ve 7 günlük `DailyCoachSession` görevlerinin yeniden sıralanması (`ExamAttributionGeneratedEvent` -> `studyplan` dinleyicisi).
  - **Bridge `B4` Teslimatı:** `evaluations/exports/yks-tr-phase13-seed/` JSONL şema ihracı (yalnızca `V060`/`yks-tr-hard-v1` `SymPy`-onaylı SME kayıtları, sıfır öğrenci verisi) + `summarize_qwen_evaluation.py` Türkçe baseline raporu.
- **Veritabanı (`V061__sprint_9_7_post_assessment_attribution.sql`):**
  - `exam_session_item_attributions` (`attribution_id`, `session_id`, `item_evaluation_id`, `user_id`, `skill_id`, `attribution_category`, `matched_mistake_code`, `time_spent_seconds`, `estimated_net_impact`, `recommended_action_type`, `training_eligible = FALSE`, `llm_tokens_used = 0`).
  - `exam_study_reallocation_batches` (`batch_id`, `user_id`, `source_session_id`, `top_roi_plan_json`, `applied_to_study_plan`, `created_at`, `training_eligible = FALSE`).
- **API & Kontratlar (`CNT-LOS-10` & `CNT-LOS-11`):**
  - `GET /v1/exams/sessions/{sessionId}/attribution-report` (& `/api/v1/...`)
  - `POST /v1/exams/sessions/{sessionId}/apply-study-rebalance` (seçilen yüksek ROI konularını 7 günlük plana yazar)
  - Feature Flag: `verifiedai.exam.attribution-enabled: ${VERIFIEDAI_EXAM_ATTRIBUTION_ENABLED:false}`
- **iOS (`Concept B`):**
  - `PostExamAttributionView.swift` & `PostExamAttributionViewModel.swift`: 4 kategorili net kaybı özeti, deneme sorusundan tek dokunuşla `Twin` veya `Micro-Lesson` telafisine geçiş, offline SQLite önbelleği (`cached_exam_attributions`).
- **Kabul Kriterleri (`sprint_9_7_owner_acceptance.sh --VerifyHost`):**
  - Kategori A–E testleri, `%100` deterministik 4-kategori sınıflandırma ve `$0.00` LLM maliyeti, `ExamPrivacyLifecycleContributor` child-first purge + `B4` clean-seed doğrulaması.

---

### 4.2 Dalga 3 — Sprint 9.8 — Sınav Öncesi Final Tekrar & 48 Saat Kişisel Tuzak/Formül Özeti (`H-15`)
- **Önkoşul:** `Sprint 9.7` kabul edilmiş olmalı.
- **Domain İnvaryantları:**
  - **Panic Inhibition Rule:** Adayın `CandidateProfile` hedef tarihine $\le 48$ saat kaldığında yeni/öğrenilmemiş ($M_{u,s} < 0.50$) ağır konular kuyruktan tamamen çıkarılır; yalnızca sınav ağırlığı yüksek bilindik konulardaki ($M_{u,s} \ge 0.50$) aktif tuzak onarımı sunulur.
  - **On-Device PDF (`$0.00` Bulut Depolama Maliyeti):** Öğrencinin en sık düştüğü **ilk 10 hata tuzağı**, karşı-örnek kanıtı ve ilgili doğrulanmış formül özeti **yalnızca iOS cihazında (`PDFKit` / `ImageRenderer`)** 1 sayfalık PDF'e dönüştürülür; buluta özel not yüklenmez.
- **Veritabanı (`V062__sprint_9_8_pre_exam_review_sessions.sql`):**
  - `pre_exam_study_sessions` (`session_id`, `user_id`, `exam_code`, `hours_remaining`, `panic_inhibition_active`, `selected_skills_json`, `top_traps_json`, `cheat_sheet_generated`, `training_eligible = FALSE`, `llm_tokens_used = 0`, `created_at`).
- **API & Kontratlar:**
  - `GET /v1/exams/pre-exam-review/{examCode}`
  - `GET /v1/exams/cheat-sheet/{examCode}` (`exam-cheat-sheet-v1` Draft 2020-12 şema, `$0.00` LLM token)
  - Feature Flag: `verifiedai.exam.pre-exam-mode-enabled: ${VERIFIEDAI_EXAM_PRE_EXAM_MODE_ENABLED:false}`
- **Python Verifier Etkisi:**
  - Cheat sheet'te gösterilecek kanonik formül çiftlerinin `SymPy` AST eşdeğerlik doğrulamasından geçmiş statik şablon paketi.
- **iOS (`Concept B`):**
  - `PreExamCramView.swift`, `PreExamCramViewModel.swift`, `PersonalCheatSheetPDFRenderer.swift` (Share Sheet + Offline PDF görüntüleme, zero-gradient/shadow Concept B sayfa düzeni).
- **Kabul Kriterleri (`sprint_9_8_owner_acceptance.sh`):**
  - 48h modunda $M < 0.50$ yabancı konu sıfır çıkmalı; PDF renderer snapshot/boyut/offline testleri geçmeli.

---

### 4.3 Dalga 4 — Sprint 9.9 — Uzunlamasına Sınav Analitiği, Hız-Zaman & Tuzak Korelasyonu (`INV-EXAM-005`)
- **Önkoşul:** `Sprint 9.8` kabul edilmiş olmalı.
- **Domain & İstatistik Kuralları (`$0.00` LLM Maliyeti):**
  - **Score Trajectory (`INV-EXAM-005`):** Son $N \le 20$ deneme oturumu üzerinden net skoru ve hareketli ortalamayı hesaplar.
  - **Speed-Pressure Pacing:** Alt konu bazında soru başı ortalama süreyi (`mean_seconds_per_question`) beklenen süreyle (`estimated_time_seconds` `V056`/`V060`) kıyaslayarak darboğazları işaretler (ör. TYT Problemlerde `135s` vs hedef `90s`, AYT İntegralde `210s` vs `150s`).
  - **Trap Remediation Correlation:** Oturum bazlı `RECURRING_MISTAKE_TRAP` olay insidansı (`SESSION_MATCHED_TRAP_INCIDENCE`) ile net skor arasındaki gözlemsel Pearson ilişkisini (`associationIsCausal: false`) hesaplar; güncel aktif tuzak sayısını (`activeTrapCountNow`) anlık bilgi olarak iliştirir ve tarihsel geçiş kanıtı olmadan `conqueredTrapsEvidenceStatus = UNAVAILABLE` (`conqueredTrapsCount = 0`) döner.
- **Veritabanı (`V063__sprint_9_9_exam_analytics_snapshots.sql`):**
  - `exam_analytics_snapshots` (`snapshot_id`, `user_id`, `exam_code`, `mock_count`, `net_trend_json`, `pacing_stats_json`, `trap_correlation_json`, `conquered_traps_count`, `as_of_date`, `source_fingerprint`, `algorithm_version`, `source_last_score_id`, `schema_version`, `training_eligible = FALSE`, `llm_tokens_used = 0`, `inference_cost_usd = 0`).
- **API & Kontratlar:**
  - `GET /v1/analytics/exams/progress?examCode={code}` (& `/api/v1/...`, `exam-analytics-progress-v1` şema, `Cache-Control: no-store`, BOLA `403`).
  - Feature Flag: `verifiedai.exam.analytics-enabled: ${VERIFIEDAI_EXAM_ANALYTICS_ENABLED:false}`
- **iOS (`Concept B`):**
  - `ExamProgressHistoryView.swift` & `ExamProgressHistoryViewModel.swift`: *Swift Charts* `LineMark`/`PointMark` net + 3'lü hareketli ortalama grafiği, `BarMark` konu hız karşılaştırması, gözlemsel tuzak ilişkisi özeti, Concept B düz yüzey ve owner/exam izolasyonlu SQLite önbellek.
- **Kabul Kriterleri (`sprint_9_9_owner_acceptance.sh --VerifyHost`):**
  - 1..50 simüle deneme verisi üzerinde regresyon/korelasyon doğrulaması + BOLA + offline isolation.

---

### 4.4 Dalga 5 — Sprint 9.10 — Sınav Bütünlüğü, Tamper Koruması, `tr-TR` VoiceOver Matematik & Phase 9 Exit Gate (`INV-EXAM-006`)
- **Önkoşul:** `Sprint 9.9` kabul edilmiş olmalı.
- **Domain & Güvenlik İnvaryantları (`INV-EXAM-006` + `ADR-010`):**
  - **Cryptographic Submission Integrity (`INV-EXAM-006`):** Deneme gönderim paketleri (`submit`) sunucu zaman damgası, `sessionId` ve kullanıcı oturum anahtarından türetilen **`HMAC-SHA256`** imzası ile doğrulanır; saat geri alma, replay veya ara paket değiştirme girişimleri reddedilir (`409` / `403`, audit log).
  - **Universal `tr-TR` Spoken Math (`ADR-010`):** `V060` içindeki zor AYT ve TYT yeni nesil matematik ifadelerinin tamamı Dynamic Type (`xSmall`–`AX3`) ile ölçeklenir ve semantik `tr-TR` VoiceOver erişilebilirlik metni üretir (ör. $\int_0^1 x^2\,dx$ -> *"sıfırdan bire x kare d x integrali"*).
  - **Phase 9 Çıkış Kapısı Sertifikasyonu:** `CNT-LOS-10` ve `CNT-LOS-11` uç noktalarının $< 350\text{ms}$ P95 SLA, sıfır P0/P1 hata, tam `V054`–`V064` Flyway zinciri ve tam `ExamPrivacyLifecycleContributor` silme sıralaması doğrulaması.
- **Veritabanı (`V064__sprint_9_10_exam_integrity_audit_logs.sql`):**
  - `exam_integrity_audit_logs` (`id`, `session_id`, `user_id`, `event_type`, `signature_verified`, `client_clock_skew_ms`, `payload_sha256`, `training_eligible = FALSE`, `created_at`).
- **API & Kontratlar:**
  - `ExamSessionRuntimeController` gönderim imza doğrulama başlığı/alanı (`submissionSignature`, geriye uyumlu feature-flag kontrollü).
  - `GET /v1/admin/exams/quality-gate` (`ROLE_ADMIN`, `exam-quality-gate-v1`).
  - Feature Flag: `verifiedai.exam.integrity-guard-enabled: ${VERIFIEDAI_EXAM_INTEGRITY_GUARD_ENABLED:false}`
- **iOS (`Concept B`):**
  - `SpokenMathTurkishAccessibilityFormatter.swift` (deterministik LaTeX -> `tr-TR` VoiceOver metin dönüştürücü + 50 karmaşık formül snapshot/birim testi).
  - Ağ kesintisi / timeout hata enjeksiyonlu `ExamSessionRuntime` kurtarma doğrulaması.
- **Çıkış Kapısı:** `bash scripts/quality/sprint_9_10_owner_acceptance.sh --VerifyHost` -> **Phase 9 Exit Gate PASSED** -> **Phase 10 (`Sprint 10.1` Paywall & Entitlement)** açılır.

---

## 5. Riskler ve Azaltma Planı (Production Safeguards)

| Risk | Etki | Önlem / Azaltma |
|---|---|---|
| **Hatalı cevaplı veya muğlak Türkçe soru** | Yanlış net projeksiyonu ve öğrenci güven kaybı | `Bridge B3`'teki `SymPy` doğrulama kapısı + `author_id <> reviewer_id` çift imza kısıtı olmadan `PUBLISHED` statüsü verilmez |
| **Uzun Türkçe yeni nesil metinlerin iOS'ta taşması** | Okunabilirlik ve `Dynamic Type AX3` bozulması | `B2` sonrasında UI snapshot + `AX3` layout testleri `Sprint 9.7` öncesi koşulur |
| **Mevcut `2026.1` hedeflerin veya `V056` testlerinin kırılması** | Eski entegrasyon testlerinde regresyon | `2026.1` blueprint ID'leri (`94000000-...101/102`) korunur; yeni müfredat `2026.2` ve yeni blueprint ID'leri ile eklenir |
| **Öğrenci verisinin eğitim setine sızması** | `ADR-006` / KVKK ihlali | Tüm yeni tablolarda `CHECK (training_eligible = FALSE)`; `B4` yalnızca SME/sentetik `NOT_ELIGIBLE` -> kürasyonlu offline havuz kuralını işletir |
| **Erişilebilirlik kör noktası** | Görme engelli öğrencide AYT formül okunamaması | `V060` görsellerinde `chk_exam_qbank_visual_alt_text` + `Sprint 9.10` 50 formüllü `tr-TR` VoiceOver test seti |

---

## 6. Faz Sonrası Hedef (`Phase 13` Bağlantısı)

`Bridge B1–B4` ile başlayan ve `Sprint 9.7–9.10` boyunca doğrulanan tüm Türkçe yeni nesil TYT & zor AYT vakaları:
1. `V1` süresince **prompt/parser/SymPy** doğruluğunu haftalık `evaluations/` koşularıyla ölçer,
2. `Phase 9–12` tamamlandığında **`Phase 13`** altında `Qwen2.5-Math` / açık ağırlıklı modellerin `tr-TR` ÖSYM ince ayar (fine-tuning) kıyaslaması için hazır, temiz ve lisanslı benchmark omurgası sağlar.
