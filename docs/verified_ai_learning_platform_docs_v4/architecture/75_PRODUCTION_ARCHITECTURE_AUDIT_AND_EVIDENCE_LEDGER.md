# 75 — Production Architecture Audit and Repository Evidence Ledger

- **Audit Date:** 2026-10-31
- **Inspected Branch:** `codex/sprint-5-12-evaluation-release-gate`
- **Inspected HEAD Commit SHA:** `e6a928db2d70efb0b9f2556e478a243205c54955` (`feat(sprint-6.2): complete verification transparency, evidence ux and local acceptance`)
- **Upstream Default Branch (`origin/main`) SHA:** `dd3d4829da650dcd9bedec4f834226a5b102ab79`
- **Status:** CANONICAL ARCHITECTURE AUDIT (V6)

---

## 1. Purpose and Scope (Amaç ve Kapsam)

Bu doküman, **Verified AI** monoreposunun (`services/api`, `services/math-verifier`, `apps/ios`, `contracts`, `evaluations`, `infra`, `docs`) gerçek kaynak kodları, veritabanı şemaları, test raporları ve mimari karar kayıtları (`ADR-001`..`ADR-011`) üzerinden yapılan derin teknik denetimin resmi kanıt defteridir (Evidence Ledger).

Temel ilkeler:
1. Yerel birim/entegrasyon testlerinin (`LOCAL_A_H_OWNER_PASS`) geçmiş olması, bir alt sistemin canlı üretimde (`PRODUCTION_APPROVED`) doğrulandığı anlamına gelmez.
2. Dokümantasyon ile gerçek kaynak kodu çeliştiğinde, çalışma zamanı davranışı için kaynak kodu (`Java` / `Python` / `Swift`) gerçek durumu, `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` ve kabul edilmiş ADR'ler ise hedef sözleşmeyi temsil eder.

---

## 2. Subsystem Classification Matrix (Alt Sistem Durum Sınıflandırması)

Sınıflandırma seviyeleri:
`IMPLEMENTED` | `LOCALLY_TESTED` | `EXTERNALLY_VALIDATED` | `PRODUCTION_APPROVED` | `PLANNED` | `BLOCKED`

| # | Alt Sistem (Subsystem) | Mevcut Durum | Somut Kod ve Şema Kanıtları | Kritik Bulgular ve Üretim Engelleri |
|---|---|---|---|---|
| 1 | **Identity, Apple Auth & JWT Session** | `IMPLEMENTED` / `LOCALLY_TESTED` | `V001`, `V002` Flyway şemaları; Apple JWS doğrulama, refresh token rotasyonu, oturum iptali (`services/api/src/main/java/com/verifiedai/identity/`) | Yerel entegrasyon testleri tam; gerçek Apple Production Sandbox E2E kabul testi henüz koşulmadı (`TD-APPLE-001`). |
| 2 | **StoreKit 2 & App Store Server Notifications V2** | `IMPLEMENTED` / `LOCALLY_TESTED` | `V004` (`commerce_subscription_records`, `commerce_apple_notification_events`); iOS StoreKit 2 + sunucu yetkili JWS mutabakatı (`services/api/src/main/java/com/verifiedai/billing/`) | Mimari olarak tam üretim kalitesinde; canlı App Store Connect webhook ve gerçek ödeme mutabakatı dış doğrulama bekliyor (`TD-BILL-001`). |
| 3 | **Problem Capture, S3 Upload & Image Preprocessing** | `IMPLEMENTED` / `LOCALLY_TESTED` | `V007`, `V008`; iOS kamera/galeri akışı, S3 presigned upload, Java2D gri-tonlama/kontrast/boyutlandırma (`services/api/src/main/java/com/verifiedai/problem/`) | Yerel MinIO/S3 ile doğrulandı; gerçek öğrenci el yazısı ve düşük ışıklı sınav kitapçığı fotoğrafları üzerinde saha verisi yok. |
| 4 | **Vision OCR, Normalization & Parse Review** | `IMPLEMENTED` / `LOCALLY_TESTED` (`BLOCKED` canlı modelde) | `V009`..`V013`; `CanonicalMathDocument`, kullanıcı düzeltmeli `ProblemParseRevision` geçmişi | Şema, versiyonlama ve iOS inceleme ekranı hazır; ancak canlı bir multimodal bulut VLM adaptörü (`Gemini 2.5 Flash` / `OpenAI Vision`) henüz bağlanmadı. |
| 5 | **AI Gateway, Spend Guard & Cost Ledger** | `IMPLEMENTED` / `LOCALLY_TESTED` | `V014`, `V015`; `services/api/src/main/java/com/verifiedai/ai/infrastructure/budget/PostgresAiSpendGuard.java` | Veritabanı seviyesinde atomik bütçe rezervasyonu (`COMMITTED_USAGE` / `RESERVATION_CEILING`) ve maliyet defteri üretim kalitesinde. |
| 6 | **Primary & Secondary AI Solver Adapters** | `LOCALLY_TESTED` (`BLOCKED` üretim modeli için) | `services/api/src/main/java/com/verifiedai/ai/infrastructure/provider/VllmSolveProviderAdapter.java`; `infra/local/qwen-model-release.json` | Kod tabanında yalnızca `Fixture` ve `VllmSolveProviderAdapter` (`Qwen/Qwen3.5-4B`) mevcut. Canlı bulut API adaptörleri (`OpenAI`, `Gemini`) henüz yazılmadı (`adr/ADR-008_MANAGED_CLOUD_API_ROUTING_AND_QWEN_4B_DE_PROMOTION.md`). |
| 7 | **Python/SymPy Deterministic Math Verifier** | `IMPLEMENTED` / `LOCALLY_TESTED` | `services/math-verifier/app/verifiers/canonical_execution.py`, `services/math-verifier/app/verifiers/calculus.py` (338 Python testi PASS) | Sıfır `eval`, deterministik SymPy dönüşümü, aritmetik, cebir, doğrusal denklem/eşitsizlik, limit, türev ve temel belirli/belirsiz integral doğrulaması çalışıyor. Ancak çoklu kök (`FiniteSet`) ve tanım kümesi (`DomainConstraintGuard`) V2 genişletmesi gerekiyor (`adr/ADR-009_CANONICAL_SOLUTION_SET_ALGEBRA_AND_DOMAIN_GUARD.md`). |
| 8 | **Composite Verification & Arbitration Policy** | `IMPLEMENTED` / `LOCALLY_TESTED` | `V017`..`V022`; `services/api/src/main/java/com/verifiedai/verification/domain/policy/VerificationPolicy.java` | `VERIFIED` etiketi yalnızca deterministik kanıtla veriliyor; iki LLM'in anlaşması (`BLIND_CONSENSUS`) asla `VERIFIED` sayılmıyor (`UNVERIFIED`). |
| 9 | **Evaluation Harness & Release Governance** | `IMPLEMENTED` / `LOCALLY_TESTED` | `V023`; `evaluations/baselines/solver-release-policy-v1.json`; `scripts/evaluation/run-golden-suite.sh` | Yönetişim motoru çalışıyor ancak veri seti yalnızca 18 sentetik vakadan (`11` pozitif, `7` red) oluşuyor. 500 vakalık gerçek `verified-ai-eval-v2` veri seti henüz koşulmadı. |
| 10 | **iOS Solution Results & Verification Evidence UX** | `IMPLEMENTED` / `LOCALLY_TESTED` | `apps/ios/VerifiedAI/Features/ProblemSolving/Domain/Model/SolutionCandidate.swift`, `MathematicalExpressionView.swift` (134 iOS testi PASS) | Doğrulama rozetleri, belirsizlik açıklamaları ve TR/EN yerelleştirme hazır; ancak `MathematicalExpressionView.swift` henüz düz metin (`Text` monospaced) basıyor, 2D LaTeX dizgisi (`SwiftMath`) gerekiyor (`adr/ADR-010_NATIVE_2D_MATH_RENDERING_AND_STEP_DIFF_HIGHLIGHTING.md`). |
| 11 | **Socratic Tutoring & Analyze My Work (Phase 6.3–6.10)** | `PLANNED` | `sprints/phase06_solution_experience_and_tutoring/PHASE_06_SOLUTION_EXPERIENCE_AND_TUTORING.md` | Henüz kodlanmadı; en yüksek rekabetçi farklılaşma potansiyeline sahip katman (`learning/79_PEDAGOGICAL_DIAGNOSIS_HANDWRITING_UX_AND_RETENTION_ENGINE.md`). |
| 12 | **Mistake Intelligence, Mastery & Adaptive Engine (Phase 7–8)** | `PLANNED` | `learning/30_MISTAKE_INTELLIGENCE.md`, `learning/31_MASTERY_AND_KNOWLEDGE_GRAPH.md`, `learning/32_ADAPTIVE_LEARNING_AND_STUDY_PLANNER.md` | Tasarım dokümanları mevcut; veritabanı tabloları ve servis modülleri henüz yazılmadı. |
| 13 | **Exam Mode & Proprietary ML (Phase 9–13)** | `PLANNED` | `learning/33_EXAM_MODE_AND_ASSESSMENT_ENGINE.md`, `ai/59_SMALL_MODEL_FINE_TUNING_AND_SELF_HOSTING_READINESS.md` | `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md` uyarınca ilk gerçek öğrenci beta doğrulaması sonrasına donduruldu. |

---

## 3. Compact Repository Evidence Ledger (Kanıt Defteri)

| İddia / Mimari Özellik | Dosya Yolu | Commit SHA | Sembol / Bölüm | Doğrulama Durumu | Güven | Açık Soru / Aksiyon |
|---|---|---|---|---|---|---|
| `VERIFIED` statüsü yalnızca deterministik doğrulayıcı geçtiğinde verilir; iki modelin uzlaşması `UNVERIFIED` döner | `services/api/src/main/java/com/verifiedai/verification/domain/policy/VerificationPolicy.java` | `e6a928db` | `evaluate(...)`, `UNVERIFIED_BLIND_CONSENSUS_ONLY` | Doğrulandı (Kod + Test) | %100 | Yok — bu kural korunacak. |
| `Qwen/Qwen3.5-4B` vLLM adaptörü varsayılan olarak kapalıdır ve yalnızca sabit kapasite tahsisi kabul eder | `services/api/src/main/resources/application.yml` ve `VllmSolveProviderAdapter.java` | `e6a928db` | `vllm.enabled: false`, `validateSupportedPricingModel` | Doğrulandı (Kod) | %100 | `ADR-008` ile V1'de bulut API adaptörlerine (`OpenAI`/`Gemini`) geçilecek. |
| Kanonik çözüm modeli (`canonical_v1`) denklem çözümlerinde yalnızca tekil `Equality` (`x = c`) kabul eder | `services/api/src/main/java/com/verifiedai/solving/domain/model/canonical/CanonicalSolution.java` | `e6a928db` | `validateOutcomeAlignment` (`SOLVE_FINAL_EQUATION_NOT_ISOLATED`) | Doğrulandı (Kod) | %100 | `ADR-009` ile `FiniteSet`, `Interval` ve `DomainConstraintGuard` eklenecek. |
| Python matematik doğrulayıcısı `eval` kullanmadan deterministik SymPy AST dönüşümü yapar | `services/math-verifier/app/verifiers/canonical_execution.py` ve `calculus.py` | `e6a928db` | `verify_canonical_request`, `_to_sympy`, `verify_calculus` | Doğrulandı (Kod + 338 Test) | %100 | İkinci dereceden çoklu kökler ve tanım kümesi tuzak kontrolü (`extraneous_roots`) eklenecek. |
| iOS matematiksel ifade bileşeni şu an düz `Text` (`.monospaced`) kullanmaktadır | `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Components/MathematicalExpressionView.swift` | `e6a928db` | `body` (`Text(trimmedExpression)`) | Doğrulandı (Kod) | %100 | `ADR-010` ile yerel 2D matematiksel dizgi (`SwiftMath`) entegre edilecek. |
| AI bütçe koruyucusu PostgreSQL üzerinde atomik rezervasyon ve harcama kaydı tutar | `services/api/src/main/java/com/verifiedai/ai/infrastructure/budget/PostgresAiSpendGuard.java` | `e6a928db` | `reserveBudget`, `recordSettlement` | Doğrulandı (Kod) | %100 | Canlı API adaptörlerinin gerçek token/maliyet yanıtları bu sınıfa bağlanacak. |

---

## 4. Document-to-Code Discrepancies and Strategic Corrections (Mimari Çelişkiler ve Düzeltmeler)

1. **Çelişki 1 — API-First Stratejisi (`ADR-005`/`ADR-007`) vs. Erken Self-Hosted `Qwen3.5-4B` (`vLLM`) Sapması:**
   - **Tespit:** `ai/57_API_FIRST_HYBRID_AI_STRATEGY_AND_MODEL_EVOLUTION.md` ve `adr/ADR-005_API_FIRST_PROGRESSIVELY_PROPRIETARY_AI_STRATEGY.md` V1'de harici yönetilen bulut API'lerini zorunlu kılarken, Sprint 5.4-RM'de hiçbir bulut API adaptörü yazılmadan doğrudan `VllmSolveProviderAdapter` (`Qwen/Qwen3.5-4B`) kodlanmıştır.
   - **Çözüm (`ADR-008`):** `VllmSolveProviderAdapter` yalnızca çevrimdışı (offline) regresyon ve Faz 13 deneyleri için korunacak; V1 üretim hattı `Gemini 2.5 Flash` + `GPT-4.1-mini` + `o4-mini` bulut adaptörleri üzerinden çalışacaktır.
2. **Çelişki 2 — Ürün Kapsamı (Polinom/İkinci Dereceden Denklemler) vs. Tekil Eşitlik (`Equality`) Kısıtı:**
   - **Tespit:** Ürün vizyonu lise/YKS cebirini kapsarken `CanonicalSolution.java` yalnızca tek kök (`x = 4`) doğrulamasına izin vermektedir.
   - **Çözüm (`ADR-009`):** `CanonicalMathDocument v2` ile sonlu kümeler (`FiniteSet`), aralıklar (`Interval`) ve tanım kümesi denetimi (`DomainConstraintGuard`) eklenmektedir.
3. **Çelişki 3 — 13 Fazlık Monolitik Takvim vs. Solo Kurucu Pazara Çıkış Hızı:**
   - **Tespit:** 120+ sprintlik planın tamamını canlıya çıkmadan bitirmeye çalışmak, gerçek öğrenci geri bildirimini aylarca geciktirmektedir.
   - **Çözüm (`ADR-011`):** Faz 8–13 dondurulmuş; 30 günlük odaklanmış ticari çekirdek (`Canlı Bulut Çözümü` + `Çözümümü Kontrol Et / İlk Hatalı Adım` + `Tanım Kümesi Tuzak Radarı`) sonrası 50 gerçek öğrencili TestFlight Beta kapısı zorunlu kılınmıştır.
