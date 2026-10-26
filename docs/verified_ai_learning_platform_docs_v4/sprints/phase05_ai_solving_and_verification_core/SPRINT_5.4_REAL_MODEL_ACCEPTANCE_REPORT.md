# Sprint 5.4-RM — Gerçek model kabul raporu

## 1. BASELINE

2026-10-26; branch `feature/sprint-5-2-prompt-registry-governance`;
HEAD `f3cec8ab05bf182137fcfd3f80c41f56b2a5bddd`. Başlangıç çalışma ağacı temizdi.
İstekteki `main@403e0ba3ce2361dce5bbedecd0ccb2fac3cc508b` ile aynı değildi; yerel ağaç esas alındı.
Remote HEAD varsayılmadı/fetch edilmedi. Önceki raporda sahibi tarafından kabul edilen A–E kanıtı var;
o kanıt bu yeni değişikliklerin test sonucu değildir. Git commit/push/reset/stash/rebase/merge yapılmadı.

## 2. MODEL DECISION

Qwen3.5-4B, sahibinin seçtiği uygulama ve araştırma adayıdır. Exact model/tokenizer/processor revision
`851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a`. Resmî metadata/config/lisans ve versioned vLLM kaynakları
okundu; ağırlık indirilmedi. [Karar kaydı](SPRINT_5.4_QWEN_MODEL_DECISION.md) ADR-003/005/006/007'yi korur.
Üretim onayı yoktur; Q4 artifact, gerçek kalite, kapasite, privacy ve TCO kanıtı eksiktir.

## 3. ARCHITECTURE

Mevcut canonical source → durable SOLVE → gateway/route/spend ledger → controlled vLLM adapter →
solution-candidate-v1 validator. Yalnız mevcut VerificationPolicy VERIFIED verebilir. Provider ve
runtime detayları AI infrastructure içinde kalır. Public API/iOS/V017/V018/schema değişmedi.

## 4. IMPLEMENTATION

Exact offline eligibility, default-disabled typed configuration, bounded JDK HTTP transport,
authenticated model health, tokenization/output/usage mapping, immutable solver/v002 ve server-trusted
Türkçe/İngilizce dili yazıldı. Gerçek route pozitif approved allocation gerektirir; fixture sıfırdır.
Aggregate bütçeler mevcut ledger'dadır; uncertain outcome automatic replay edilmez.
Unit/negative/integration testleri, owner-only gerçek smoke/benchmark, 18 sentetik vaka,
offline numerical/schema raporlayıcı, review/capacity şablonları ve izole serving tarifi hazırlandı.
Durum: **BACKEND_REGRESSION_PASS — REAL_MODEL_ACCEPTANCE_BLOCKED** (owner kanıtı: 2026-10-27).
Derleme ve sentetik backend regresyonu geçti; gerçek model kalite/kapasite/kabulü tamamlanmadı.

## 5. FILE INVENTORY

Aşağıdaki envanter repository-relative gerçek dosya yollarıdır. Yeni/modified dosyaların tamamı
bu checkpoint'e aittir; generated model çıktısı veya ağırlık yoktur.

| Dosya | Açıklama |
| --- | --- |
| `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md` | Yeni Sprint 5.4-RM dokümanlarının keşif bağlantıları. |
| `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md` | Değişen/yeni dokümanların satır ve byte metadata envanteri. |
| `docs/verified_ai_learning_platform_docs_v4/ai/26_PROMPT_SCHEMA_AND_VERSIONING.md` | Offline evaluation aşamasının yönetişim kaydı. |
| `docs/verified_ai_learning_platform_docs_v4/ai/72_OPEN_WEIGHT_MATH_MODEL_RESEARCH.md` | Sahibin Qwen3.5-4B tercihi; tarihsel adaylar korunur. |
| `docs/verified_ai_learning_platform_docs_v4/ai/73_MATH_MODEL_EVALUATION_PLAN.md` | Yeni tooling, smoke/release corpus ve evidence ayrımı. |
| `docs/verified_ai_learning_platform_docs_v4/ai/74_MODEL_ROUTING_COST_AND_TCO_DECISION.md` | Fixed allocation ile gerçek GPU TCO ayrımı. |
| `docs/verified_ai_learning_platform_docs_v4/operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md` | Gerçek kalite, Q4, schema ve operasyon release blocker kayıtları. |
| `docs/verified_ai_learning_platform_docs_v4/quality/65_CANONICAL_DOCUMENTATION_TREE_AND_SOURCE_OF_TRUTH_MAP.md` | Yeni sprint dokümanlarının canonical tree kaydı. |
| `docs/verified_ai_learning_platform_docs_v4/roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md` | Production coverage artışı iddia etmeyen güncel evidence durumu. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.4_EXECUTION_REPORT.md` | Önceki A–E ile yeni test edilmemiş Stage F ayrımı. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.4_IMPLEMENTATION_MAP.md` | Tarihsel fixture kapsamından yeni integration map bağlantısı. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.4_QWEN_MODEL_DECISION.md` | Exact model, lisans, quantization ve ADR uzlaştırması. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.4_REAL_MODEL_ACCEPTANCE_REPORT.md` | Bu checkpoint, tüm dosyalar ve eksik kabul kanıtları. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.4_REAL_MODEL_INTEGRATION_MAP.md` | Mimari, trust, privacy, cost ve gelecek iş sınırları. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.4_REAL_MODEL_OWNER_RUNBOOK.md` | A–G sahibi komutları, prerequisites ve rollback. |
| `evaluations/fixtures/solver/qwen-capacity-template.json` | GPU kapasite, marjinal maliyet ve tam TCO evidence şablonu. |
| `evaluations/fixtures/solver/qwen-review-template.json` | Bağımsız adım/Türkçe/assumption inceleme şablonu. |
| `evaluations/fixtures/solver/qwen-smoke-v1.json` | 18 sentetik matematik/scope vakası; bağımsız gold review bekler. |
| `evaluations/runners/summarize_qwen_evaluation.py` | Owner-run bağımsız schema/numeric grading ve sınırlılık raporu. |
| `evaluations/runners/test_qwen_report.py` | Grader doğruluk/karmaşıklık ve canonical source schema testleri. |
| `infra/local/docker-compose.qwen-evaluation.yml` | Opt-in pinned image, loopback/private network GPU serving tarifi. |
| `infra/local/qwen-model-release.json` | Doğrulanan metadata ve açıkça eksik artifact/quality alanları. |
| `infra/local/qwen-owner.env.example` | Secret içermeyen, fail-closed owner konfigürasyon örneği. |
| `services/api/src/main/java/com/verifiedai/ai/application/PromptRegistry.java` | solver/v002 immutable registry ve digest kaydı. |
| `services/api/src/main/java/com/verifiedai/ai/application/PromptRegistryValidator.java` | Offline evaluation prompt aşaması; staging/production sınırları. |
| `services/api/src/main/java/com/verifiedai/ai/application/PromptReleaseStage.java` | OFFLINE_EVALUATION enum değeri. |
| `services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration/AiInfrastructureConfiguration.java` | Typed solver config ve kontrollü provider health bağlantısı. |
| `services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration/ConfiguredAiRoutePlanner.java` | Exact model uygunluğu ve taze health seçimi. |
| `services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration/SolverModelEligibility.java` | Yerel/test real-model aktivasyonunun fail-closed kontrolü. |
| `services/api/src/main/java/com/verifiedai/ai/infrastructure/configuration/VllmSolverProperties.java` | Model/runtime/endpoint/token/maliyet konfigürasyon sözleşmesi. |
| `services/api/src/main/java/com/verifiedai/ai/infrastructure/provider/BoundedInferenceHttp.java` | Sınırlı async HTTP, deadline, disconnect ve güvenli hata sınıflama. |
| `services/api/src/main/java/com/verifiedai/ai/infrastructure/provider/VllmSolveProviderAdapter.java` | vLLM tokenize/completion → AiSolveResult/provenance/usage. |
| `services/api/src/main/java/com/verifiedai/solving/application/PrimarySolveService.java` | Server-trusted dil, configured bütçe ve route-bound prompt. |
| `services/api/src/main/resources/application.yml` | Disabled varsayılanlar ve sıfır gerçek inference bütçesi. |
| `services/api/src/main/resources/prompts/solver/v002.md` | Yeni immutable canonical mathematics prompt; v001 korunur. |
| `services/api/src/test/java/com/verifiedai/ai/VllmTestPolicy.java` | Yalnız sentetik unit-test release/bütçe fixture. |
| `services/api/src/test/java/com/verifiedai/ai/infrastructure/configuration/QwenEvaluationArtifactsTest.java` | Manifest/prompt eşleşmesi ve corpus kapsam testleri. |
| `services/api/src/test/java/com/verifiedai/ai/infrastructure/configuration/SolverModelEligibilityTest.java` | Model, revision, quantization, ortam ve endpoint negatif testleri. |
| `services/api/src/test/java/com/verifiedai/ai/infrastructure/provider/VllmSolveProviderAdapterTest.java` | HTTP mapping/timeout/usage/identity/ledger/trust negatif testleri. |
| `services/api/src/test/java/com/verifiedai/integration/solving/QwenRealModelEvaluationTest.java` | Owner opt-in gerçek durable smoke ve canonical benchmark. |
| `services/api/src/test/java/com/verifiedai/integration/solving/VllmSolvePipelineIntegrationTest.java` | Sahte HTTP ile gerçek adapter/job/PostgreSQL maliyet entegrasyonu. |
| `services/api/src/test/resources/application-qwen-evaluation.yml` | Sadece owner-run test profili; zorunlu onay/bütçe/secret alanları. |

## 6. DEPLOYMENT READINESS

Versioned vLLM 0.17.1 API/mimari belgeleri esas alındı. Runtime image digest ve local weight checksum'ı
sahibi tarafından doğrulanmalıdır. Linux/NVIDIA ve 24 GB VRAM sınıfı yalnız planlama hedefidir.
Başlangıç BF16 reference; 8192 context/6144 input/2048 output, concurrency≤2. Q4 halen blocked.
Loopback/private network, service credential ve SSH/TLS gerekir. Docker/inference başlatılmadı.
vLLM bearer kontrolü `/v1` ile sınırlıdır; `/tokenize` için loopback/SSH izolasyonu zorunludur;
HTTPS alternatifi tüm izinli yolları doğrulayan ayrı reverse proxy gerektirir.

## 7. MATHEMATICAL QUALITY

Ölçülen model skoru **yok**. Grader exact AST ve sınırlı rational eşdeğerlik ölçer; adımlar/Türkçe/
assumptions bağımsız review ister. Corpus reference review ve protected holdout eksiktir.
Schema acceptance bazı rejected output'lar için lower bound'dur; ≥99% release sonucu değildir.
Geometry/diagram/graph/calculus/word/multipart kapsamı güvenli red olarak ayrılır. OCR ve model
kapasitesi aynı benchmark sonucu gibi sunulmaz. Hiçbir offline skor kullanıcıya VERIFIED vermez.

## 8. COST

Gerçek ölçülen maliyet **yok**. Kod PREPAID_CAPACITY_FIXED_ALLOCATION destekler; GPU faturası veya
external token-billed API üst sınırı sağlamaz. Gerçek input/output tokenları kaydedilir; başarısız
ve usage-unknown işlemlerin commitment'ı korunur. Marjinal maliyet, capacity allocation ve tam TCO
ayrı raporlanır. Hiçbir fiyat/bütçe önerisi otomatik runtime değeri yapılmadı.

## 9. TEST STATUS

Owner'ın 2026-10-27 02:26:21 +03:00 tam API çalıştırması: **391 tests, 0 failures, 0 errors,
2 skipped — BUILD SUCCESS**; 389 PASS. Mevcut Surefire XML kanıtında RM sınıfları:

| Test sınıfı | PASS | SKIP |
| --- | ---: | ---: |
| QwenEvaluationArtifactsTest | 2 | 0 |
| SolverModelEligibilityTest | 6 | 0 |
| VllmSolveProviderAdapterTest | 10 | 0 |
| VllmSolvePipelineIntegrationTest | 2 | 0 |
| QwenRealModelEvaluationTest | 0 | 2 |

Son iki skip `durableSmoke` / `canonicalBenchmark`: `QWEN_OWNER_RUN` yok; canlı inference yapılmadı.
iOS tam regresyonu 111 test başarılı; math-verifier 20 PASS. Doctor/lint/docs/contracts owner PASS;
secret scan devamı sıralı make çalıştırmasının API/verifier'a ulaşıp başarılı dönmesinden çıkarıldı.
Detaylı terminal/paket kanıtları [UI execution report](SPRINT_5.4_UI_EXECUTION_REPORT.md) içinde kayıtlıdır.
Bu sonuçlar adapter'ın sentetik/HTTP/PostgreSQL regresyonunu kapsar; model kalitesi anlamına gelmez.
`test_qwen_report.py`, ayrı fixture validator/prompt immutability komutları ve `make eval-ai` için
bu yeni checkpoint'te ayrıca owner çıktısı verilmedi; çalıştırılmış sayılmazlar.
Asistan test/build/validator/inference çalıştırmadı; mevcut owner raporlarını okudu.

## 10. OWNER TEST COMMANDS

Tam cwd, prerequisites, Docker/GPU/ağ/maliyet gereksinimleri, kopyalanabilir komutlar, beklenen
kanıtlar ve hata sınıfları [Owner Runbook](SPRINT_5.4_REAL_MODEL_OWNER_RUNBOOK.md) içinde A–G olarak verildi.
Tam Maven regresyonunda kapsanan testleri yalnız yeni hata/değişiklik varsa tekrarlayın.
A/C'deki ayrı Python/evaluation komutlarının kanıtı hâlâ eksiktir. D–F yalnız sahibi açıkça onayladıktan sonra.

## 11. BLOCKERS

- Java adapter/eligibility/pipeline derleme ve sentetik regresyonu geçti; ayrı Python/evaluation araçlarının
  yeni checkpoint kanıtı eksik, gerçek inference kabulü engelli.
- Exact runtime image digest, weight/license checksum ve deployment evidence onaylanmadı.
- Q4 yöntemi/artifact/kernel uyumluluğu ve paired accuracy etkisi bilinmiyor.
- GPU fit, gecikme, concurrency throughput ve gerçek toplam maliyet ölçülmedi.
- Bağımsız gold review, yeterli corpus/holdout ve tek versioned quality gate sahibi onayı eksik.
- Production privacy/security/operations/shadow/canary/TCO/ADR kapıları açık; staging/production kodda kapalı.
- Cancellation remote work/charge iptal garantisi değildir; dış token-billed fallback uygulanmadı.

## 12. NEXT ACTION

Geçen Maven regresyonunu tekrar etmeyin. Ayrı Python/evaluation kanıtları ve model/runtime/kapasite
onayı gelmeden gerçek-model D/E/F kapılarına ilerlenmez. Kullanıcı bu aşamada raporların tamamlanması
ve mevcut RM/UI çalışmalarının GitHub'a commit/push edilmesini istedi; açık engeller bu raporda devredildi.
Mevcut release kararı **REQUIRES_MORE_EVIDENCE**. PRODUCTION_APPROVED değildir.
