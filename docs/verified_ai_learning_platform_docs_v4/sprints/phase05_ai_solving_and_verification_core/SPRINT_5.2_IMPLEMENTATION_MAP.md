# Sprint 5.2 Implementation Map

Durum: `PARTIAL`; Grup A–C `VERIFIED_PASS` (2026-10-26), Grup D yerel kabulü `VERIFIED_PASS`; dış yayın kapıları açık.
Documentation mode: LOCAL_CANONICAL_DOCS.

## A. Başlangıç ve kapsam

2026-10-25 başlangıcı: `feature/sprint-5-2-prompt-registry-governance`,
HEAD `65fcbea601b8587df5093cafeb464decea99267b`, temiz çalışma ağacı.
Önceki Sprint 5.2 registry, classifier prompt, şema paketleme ve testleri zaten mevcuttu.
Bunlar yeniden oluşturulmadı. Önceki raporun başlangıç commit'i
`8247c97cebc4c61b4019dfef25042687c9717cfc` ve PASS beyanları tarihsel kayıttır;
bu revizyona ait kullanıcı test kanıtı değildir.

Sprint 5.1: gateway, capability descriptors, route metadata, provider startup validation,
usage ledger ve fallback/provenance uygulaması kaynakta mevcut. Ayrı 5.1 execution report
ve bu oturumda sunulmuş test logları yok. `CAP-AI-001` Partial olarak korundu.
Kaynak incelemesinde 5.2'yi engelleyen bir mimari uyuşmazlık görülmedi.

Amaç: mevcut registry'nin bütünlük, onay, şema sahipliği ve ortam sınırlarını tamamlamak.
Model seçimi, SOLVE aktivasyonu, eğitim, GPU, yeni provider, iOS ve DB migration kapsam dışı.
Assistant hiçbir test, derleme, lint, formatter, doğrulama, evaluation veya CI çalıştırmadı.
Commit/push yapılmadı; bağımlılık kurulmadı.

## B. Başvurulan kaynaklar

- Sprint 5.2 canonical specification: çalışma kapsamı, yayın ve kabul kapıları.
- Sprint 5.1 implementation map, 5.2 handoff bölümü: mevcut capability/route sözleşmeleri.
- `ai/26_PROMPT_SCHEMA_AND_VERSIONING.md`: registry ve sürüm sahipliği.
- `ai/25_AI_ORCHESTRATION_AND_MODEL_ROUTING.md`: fallback, provenance, validator sınırı.
- `domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md`: AI otoritesi ve doğrulama ayrımı.
- `quality/41_ENGINEERING_STANDARDS_FOR_CODEX.md`, `quality/42_DEFINITION_OF_DONE_AND_ACCEPTANCE.md`: değişiklik ve kanıt kuralları.
- `ai/57_API_FIRST_HYBRID_AI_STRATEGY_AND_MODEL_EVOLUTION.md`, `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`: provider bağımsızlığı ve sıfır ek çağrı.
- ADR-003/005/006/007: modül sınırı, model stratejisi ve training eligibility.
- `00_MASTER_INDEX.md`, `quality/56_COMPLETE_PRODUCTION_REPOSITORY_FILE_HIERARCHY.md`: öncelik ve ai modül yerleşimi.
- `ai/28_AI_EVALUATION_AND_GOLDEN_DATASET.md`, `ai/61_MODEL_REGISTRY_RELEASE_AND_ROLLBACK_GOVERNANCE.md`: kanıt, onay ve rollback.
- CAP-AI-001/002, REQ-AI-003 ve ilgili teknik borç kayıtları: dürüst kabul durumu.

## C. Mimari karar ve sözleşmeler

Owning bounded context/module = ai. Application katmanı immutable metadata/policy,
infrastructure katmanı classpath byte okuma ve JSON artifact kimliği kontrolünü sahiplenir.
Mevcut classpath prompt kaynağı korunur; kökteki `prompts/` ikinci bir kopya değildir.
Git + deployment metadata yeterlidir; CMS, yeni registry deposu veya DB tablosu eklenmedi.

| Sözleşme | Sorumluluk |
| --- | --- |
| `PromptRegistryEntry` | ID, vNNN, capability, ai module, owner, output schema, path, SHA-256, release/evaluation/safety/policy metadata |
| `OutputSchemaRegistryEntry` | Logical version, JSON `$id`, path, SHA-256, mevcut semantic validator sahipliği |
| `PromptReleaseApproval` | İki artifact hash'i, baseline/report/approval referansları, reviewer ve timestamp |
| `OutputSemanticContract` | Mevcut üç V1 validator'ın capability ve schema-version desteği; domain validator kopyası değildir |
| `ArtifactIntegrity` | SHA-256 biçimi ve gerçek byte bütünlüğü |
| `PromptRegistryValidator` | Exact route binding, capability/schema/policy uyumu, ortam ve yayın uygunluğu |

Öncelik: capability descriptor varsayılanları → açık route seçimi → exact registry kaydı →
schema/semantic-owner uyumu → artifact hash kontrolü → mevcut provider startup guard →
mevcut response validation/provenance. Daha yeni sürüm otomatik seçilmez.
`AiRoutePlan`, `AiProvenance` ve usage ledger sözleşmeleri değişmedi.

`DRAFT`, `VALIDATED`, `APPROVED`, `DEPRECATED`, `ROLLED_BACK` çalıştırılamaz.
`LOCAL_ONLY` yalnız local/test fixture primary ve fallback hedeflerine açıktır.
`STAGING` ve `PRODUCTION` hash'lere bağlı onay/evaluation referansları gerektirir.
Staging route aşaması STAGING/PRODUCTION, production route aşaması PRODUCTION olmalıdır.
Yanlış/boş ortam fail-closed; `prod` ve `production` aynı production guard'larını kullanır.
Mevcut üç artifact LOCAL_ONLY; hiçbir üretim onayı uydurulmadı.

Approval bir Git-review attestation'dır. Runtime rapor metriklerini yeniden hesaplamaz veya
uzak kaynağa gitmez. Reviewer, referansların aynı deployment commit'inde varlığını, gerçek
kalite/maliyet/latency/regression sonucunu ve staging kanıtını onaylamalıdır. Salt flag değişimi
onay üretmez. Hosted branch protection henüz dış kanıt beklemektedir.

## D. Yeni/değişen dosyalar

Aşağıdaki Java yolları `services/api/src/` altındadır.

| Dosya/grup | Değişiklik |
| --- | --- |
| `main/java/com/verifiedai/ai/application/ArtifactIntegrity.java` | Yeni byte hash politikası |
| `main/java/com/verifiedai/ai/application/PromptReleaseApproval.java` | Yeni onay sözleşmesi |
| `main/java/com/verifiedai/ai/application/OutputSemanticContract.java` | Yeni validator sahiplik tanımı |
| `main/java/com/verifiedai/ai/application/PromptRegistry.java`, `PromptRegistryEntry.java` | Pinned prompt hash'leri, metadata ve privacy-safe missing-entry hatası |
| `main/java/com/verifiedai/ai/application/OutputSchemaRegistry.java`, `OutputSchemaRegistryEntry.java` | Şema hash/ID/semantic sahipliği |
| `main/java/com/verifiedai/ai/application/PromptRegistryValidator.java`, `PromptReleaseStage.java`, `PromptRegistryFailureCode.java` | Eligibility ve typed failure politikası |
| `main/java/com/verifiedai/ai/infrastructure/configuration/ClasspathPromptArtifactResolver.java` | Canonical identity path + digest kontrolü |
| `main/java/com/verifiedai/ai/infrastructure/configuration/ClasspathOutputSchemaArtifactResolver.java` | Digest + strict JSON + schema identity kontrolü |
| `main/java/com/verifiedai/ai/infrastructure/configuration/ConfiguredAiRoutePlanner.java` | prod alias guard düzeltmesi |
| `main/resources/application-staging.yml` | Onaysız mevcut rotaları kapalı başlatma |
| `test/java/com/verifiedai/ai/application/PromptReleasePolicyTest.java` | Yeni release/rollback/privacy testleri |
| `test/java/com/verifiedai/ai/infrastructure/configuration/ArtifactIntegrityTest.java` | Yeni byte bütünlüğü/JSON/path testleri |
| `PromptRegistryValidatorTest`, `ClasspathArtifactResolverTest`, `ConfiguredAiRoutePlannerTest` | Mevcut testlerin genişletilmesi |
| `scripts/quality/check_prompt_immutability.py`, `test_prompt_immutability.py` | Trusted base ile sürüm karşılaştırması ve testleri |
| `scripts/security/secret_scan.sh`, `test_secret_scan.py`, `.github/workflows/security-scan.yml` | Owner logunda bulunan false-success hatası için fail-closed exit handling ve regression tests |
| `.github/workflows/backend-ci.yml` | Şema path trigger'ı, immutable version gate ve Python unit test adımları |
| `ai/26`, CAP-AI-002, REQ-AI-003, technical-debt register, artifact README'leri, manifest | Güncel sözleşme ve pending evidence |

## E. Korunan invariantlar ve hata davranışı

V001 promptları ve üç şema byte olarak değiştirilmedi. Classifier schema dosyasının
tek uzantılı adı bu oturumdan önce mevcuttu; yeni rename yapılmadı.
Local fixture adapter'lar prompt gövdelerini inference için kullanmaz; classifier prompt
production kalitesi iddiası değildir. Tüm gelecek capability'ler kapalıdır.

Unknown identity/version, eksik artifact, digest mismatch, traversal/URL/alias,
uyumsuz schema/capability/policy, onaysız release ve yanlış ortam startup'ta durur.
Deterministik configuration hatası paid retry/fallback veya başarılı öğrenci yanıtına dönüşmez.
Unsupported provider mevcut gateway startup guard'ında durur; disabled capability mevcut
request-time typed failure sözleşmesini korur. Registry öğrenci içeriği almaz veya loglamaz.

JSON artifact kontrolü, öğrenci yanıtının syntax/structure/semantic kontrolünün yerine geçmez.
`problem` modülündeki normalizer/validator'lar değişmedi. Hiçbiri matematiksel VERIFIED üretmez.
Historical prompt/schema identity immutable registry digest'ine bağlanır; current default ile
üzerine yazılmaz. Eski kayıtlar ve deployed Git revision/config release geçmişinde tutulmalıdır.

## F. Test matrisi ve mevcut kanıt

| Grup | Kapsam | Durum |
| --- | --- | --- |
| A | Registry, approval, hash, schema identity, path, prod/staging, gerçek classpath planner | VERIFIED_PASS — owner log 2026-10-26, 57 test, 0 failure/error/skipped |
| B | Immutability gate unit tests + base comparison, docs/contracts/secrets | VERIFIED_PASS — owner logs: immutability 5/5, base comparison, docs (273 files), contracts; fixed scanner 7/7 (4.099s) and actual secret scan passed |
| C | Gateway/fallback/provenance, Modulith, recognition/parser compatibility, local evaluation | VERIFIED_PASS — 34 Java tests plus successful owner rerun of 23 integration tests (00:18:15+03:00, zero failures/errors/skips); local evaluation 18/18, 13 critical, zero regressions |
| D | A–C sonrası repository full quality gate | VERIFIED_PASS (local, combined owner runs) — initial 284 API passes plus S3 rerun 1/1; Make image/docs/contracts/secrets passed; verifier 20/20; iOS TEST SUCCEEDED |

Kullanıcının 2026-10-26 Grup A logu compile/test başarısını gösteriyor: BUILD SUCCESS, 57/57.
Kaynak düzeltmesi gerekmedi; Grup A tekrarı istenmiyor. Grup B Python testleri (5/5) ve
base comparison kullanıcı loguyla kabul edildi. Docs gate metadata hatası için bildirilen
16 .DS_Store dosyası silinmeden repo dışına taşındı; kural değiştirilmedi. Kullanıcı
docs (273 dosya) ve contract başarısını doğruladı. Secret scan eksik rg nedeniyle
gerçekte çalışmadığından yanlış başarı mesajı reddedildi; exit-code handling düzeltildi,
yedi negatif/pozitif regression testi ve security CI adımı eklendi. Kullanıcı yeni
7/7 test başarısını (4.099s) ve hatasız gerçek secret scan sonucunu sundu; Grup B
kabul edildi. C logunda 34 Java testi ve 18/18 local evaluation başarılı; Docker socket
bağlantıları reddedildiği için 23 integration testi başlayamadı. Üç integration sınıfının sonraki
owner-run tekrarı Docker erişimi sağlanınca 23/23 BUILD SUCCESS verdi; Grup C kabul edildi.
Grup D owner logunda MinIO pull 404 nedeniyle durdu. Test image override ve pinned-source
Dockerfile owner build ve hedefli S3 testiyle doğrulandı (1/1, 00:28:44+03:00).
Make image hedefi ve docs/contracts/secrets owner loguyla geçti; verifier 20/20, iOS
TEST SUCCEEDED (00:31:48+03:00). Hosted CI/branch protection ve Compose recovery açık.
Komutlar ve log teslim şartları [execution report](SPRINT_5.2_EXECUTION_REPORT.md) içindedir.

## G. Yayın, rollback ve sınırlamalar

Yeni sürümü yeni dosyada oluştur; immutable metadata kaydını ekle; owner test/evaluation
kanıtlarını review et; hash-bound approval kaydet; staging route'u açıkça pinle ve doğrula;
production promotion için review edilmiş staging kanıtını release kaydına ekle; production
artifact/route aşamalarını açıkça seç. Baseline karşılaştırması eski sürümü düzenlemeyi reddeder.

Rollback: önceki hâlâ PRODUCTION-approved artifact/schema/route-policy üçlüsüne pinle.
Yeni sürümün başarısızlığını release kaydına yaz; aktif pin kaldırıldıktan sonra ROLLED_BACK yap.
Uygun eski sürüm yoksa capability'yi kapat. Eski gövdeleri/digest'leri değiştirme ve validator'ı kapatma.

CODEOWNERS mevcut; required Backend CI + review branch protection ayarları owner tarafından
kanıtlanmalıdır. CI/branch yönetimini aşabilen admin yetkisi runtime hash ile engellenemez.
Yeni schema sürümleri semantic compatibility ve immutable gate kapsamına açık ekleme gerektirir.
Schema ve prompt metadata constructor'ları internal olarak genişledi; public API/iOS değişmedi.

Yeni AI çağrısı: 0. Yeni GPU maliyeti: 0. Yeni ücretli API maliyeti: 0.
Startup CPU/memory: mevcut boyut sınırları içinde hash ve üç schema JSON parse işlemi;
ölçüm yapılmadı. Request başına yeni inference veya metadata I/O yok.
Operasyonel ek yük: release review, saklanan evidence ve CI; parasal ölçüm uydurulmadı.

## H. Sprint 5.3 devri

Exact prompt/schema identity, pinned hashes, explicit route contracts, controlled release,
rollback ve mevcut usage/provenance temeli yerel owner kanıtıyla doğrulandı; dış yayın kapıları açık.
5.3 için onaylı non-fixture route, provider credentials/privacy-region-retention review,
Türkçe matematik/recognition kapsamlı representative dataset, protected holdout ve gerçek
quality/cost/latency raporları eksik dış girdilerdir. Local fixture başarısı bunların yerine geçmez.
Bu oturum model seçmez, benchmark çalıştırmaz veya SOLVE etkinleştirmez.
