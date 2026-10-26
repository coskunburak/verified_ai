# Sprint 5.2 — Güncel yürütme ve owner test teslimi

**LOCAL VALIDATION VERIFIED — EXTERNAL RELEASE GATES OPEN**

Durum: `PARTIAL`; Grup A–D yerel kabulü `VERIFIED_PASS`; source-build recovery `VERIFIED_PASS`.
Kanıt güncellemesi: 2026-10-26. Uygulama başlangıcı: 2026-10-25. Dal: `feature/sprint-5-2-prompt-registry-governance`.
Başlangıç HEAD: `65fcbea601b8587df5093cafeb464decea99267b`; başlangıç ağacı temiz.

## A–E. Uygulama özeti

Mevcut registry üzerine prompt/schema SHA-256, schema ID ve semantic-owner bağı,
hash-bound approval, açık release/environment kuralları, staging disabled defaults,
prod alias guard ve Git base-revision immutability CI eklendi. Negatif testler yazıldı.
V001 ve schema içerikleri, gateway provenance/usage ledger ve disabled future capabilities korundu.
Uygulama dependency, DB migration, iOS değişikliği veya ücretli çağrı yok. MinIO test recovery için
Go 1.22.5 container build tarifi eklendi; owner build ve hedefli S3 testini doğruladı.
Dosya sahipliği ve kararlar [implementation map](SPRINT_5.2_IMPLEMENTATION_MAP.md) içindedir.

## F–G. Kanıt ve gerçek engeller

Assistant yalnız kaynak/diff ve kullanıcı logu incelemesi yaptı; test, compile/build,
lint/format, validation, evaluation veya CI çalıştırmadı. Commit/push yapılmadı.

2026-10-26 tarihinde kullanıcı tarafından sunulan Grup A Maven logu incelendi:
`BUILD SUCCESS`, 57 test, 0 failure, 0 error, 0 skipped; bitiş
`2026-10-26T00:04:22+03:00`, süre 4.827 saniye. Java 21 kuralı, main/test compilation
ve aşağıdaki yedi sınıfın çalışması logda mevcut. Grup A: `VERIFIED_PASS`.

| Test sınıfı | Test sayısı |
| --- | --- |
| `PromptVersionTest` | 6 |
| `PromptRegistryValidatorTest` | 16 |
| `AiCapabilityRegistryTest` | 2 |
| `PromptReleasePolicyTest` | 12 |
| `ClasspathArtifactResolverTest` | 7 |
| `ArtifactIntegrityTest` | 6 |
| `ConfiguredAiRoutePlannerTest` | 8 |

Annotation-processing ve deprecated API mesajları başarısızlık değildir; log bu
mesajlar için kaynak değişikliği gerektirmiyor. Üretim/test kodu değiştirilmedi.
Grup A tekrarı gerekmiyor. Grup B–C owner loglarıyla doğrulandı; aşağıda ilk başarısız deneme ve başarılı tekrar ayrı kaydedildi. Grup D yerel kanıtı da tamamlandı; dış yayın kapıları açık.

2026-10-26 Grup B kullanıcı logu: immutability Python unit testleri 5/5 `VERIFIED_PASS`
(0.010 saniye); trusted-base artifact karşılaştırması `VERIFIED_PASS` (existing versions unchanged).
`docs-check`: `TEST_FAILED`, yalnız 16 adet `.DS_Store` metadata dosyası bildirildi.
Make bu noktada durdu; contracts-check ve secret-scan çalışmadı.
Dosyalar Git tarafından izlenmiyor ve `.gitignore` içinde zaten dışlanıyor. Docs kuralı
ignore listesine rağmen tüm çalışma ağacını taradığından hata oluştu; kural değiştirilmedi.
Bildirilen 16 dosya içerikleri silinmeden `/private/tmp/verified-ai-macos-metadata-qwxglf7t`
altına aynı göreli yollarla taşındı; `relocation.json` geri taşıma için kaynak kökü ve listeyi tutar.
Metadata taşıma sonrasında sunulan kullanıcı logu: `documentation check passed: 273 Markdown files`
ve `contract check passed`. Bu çalıştırmalar `VERIFIED_PASS`; önceki docs hatası giderildi.
Secret scan ise `rg: command not found` hatasından sonra yanlış biçimde `secret scan passed`
yazdı; bu sonuç kabul edilmedi (`TEST_FAILED`, tarama kanıtı geçersiz).
Kök neden: iki `|| true` ifadesi ripgrep hata kodlarını da başarılı/no-match sayıyordu.

`scripts/security/secret_scan.sh` artık PATH üzerinde rg yoksa 2 ile durur; scan/filter
adımlarında yalnız 0 (eşleşme) ve 1 (eşleşme yok) normal sonuç kabul edilir. Diğer exit
kodları korunur ve success mesajı yazılmaz. Yedi Python regression testi eklendi;
Security Scan workflow bu testleri gerçek taramadan önce çalıştıracak şekilde güncellendi.
2026-10-26 kullanıcı logu bu yedi testi doğruladı: `Ran 7 tests in 4.099s`, `OK`;
düzeltilmiş gerçek tarama `secret scan passed` ile tamamlandı ve araç hatası yok.
Secret-scanner regression ve gerçek tarama `VERIFIED_PASS`; Grup B kabul edildi.
Assistant test/tarama çalıştırmadı. Önceki yanlış başarı mesajı kanıt olarak kullanılmadı.
Makinede mevcut rg yolu `/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path/rg`;
yeni dependency kurulmadı. Bu yola yalnız owner-run komutunun PATH'inde yer verilir,
repo scripti belirli bir uygulama kurulumuna bağlanmaz.
Başarılı Grup A, immutability testleri ve contract kontrolünün tekrarı gerekmiyor.
Aşağıdaki tarihsel rapor korunmuştur; oradaki PASS beyanları kullanıcı tarafından bu oturumda
sunulmuş log değildir ve yeni değişikliklerin kabulü için kullanılamaz.

Sprint 5.1 implementation/handoff mevcut; ayrı execution report/test log eksik.
CAP-AI-001 Partial kaldı. Mevcut üretim dışı classifier artifact korunmuştur.
Connected-provider/holdout değerlendirmesi ve hosted branch-protection kanıtı
`BLOCKED_EXTERNAL`; local fixture testleri bunların yerine geçmez.

## H. Owner komutları

Grup A–D yerel kabul kanıtı tamamlandı. Aşağıdaki komutlar çalışma kaydıdır; yeniden çalıştırılması istenmiyor.
D grubunu yalnız A–C sonuçları değerlendirildikten sonra çalıştırın.
Komutların hiçbiri assistant tarafından çalıştırılmamıştır.

### Grup A — Odaklı Java testleri

`VERIFIED_PASS` — 2026-10-26 kullanıcı logu, 57/57. Aşağıdaki komut kayıt amaçlıdır; tekrar gerekmiyor.

Dizin: `/Users/burakcoskun/Desktop/ai_verification_tutor/services/api`.
Gereksinimler: Java 21, Maven. Docker, Xcode, provider credentials gerekmez.
Maven cache eksikse dependency indirmek için ağ gerekir. JAVA_HOME yolu Makefile'daki
yerel varsayılandır; Java 21 farklı yerdeyse yalnız bu yolu uyarlayın.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -Dtest=AiCapabilityRegistryTest,PromptVersionTest,PromptRegistryValidatorTest,PromptReleasePolicyTest,ClasspathArtifactResolverTest,ArtifactIntegrityTest,ConfiguredAiRoutePlannerTest test
```

Registry binding, eski hash'ler, schema identity, release approval, explicit rollback,
staging/prod, disabled capability ve güvenli classpath resolution kontrol edilir.

### Grup B — Contract/governance

Immutability testleri, base comparison, docs ve contracts kullanıcı loguyla kabul edildi.
Secret scan'in eski başarı mesajı geçersizdi; aşağıdaki düzeltme komutları yeni kullanıcı
loguyla VERIFIED_PASS olarak kabul edildi (7/7 test ve gerçek tarama). Tekrar gerekmiyor:

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
python3 -m unittest discover -s scripts/security -p test_secret_scan.py
PATH="/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path:$PATH" make secret-scan
```

Testler Bash/Python/tee kullanır ve fake rg ile hata yollarını sınar; gerçek scan ise
mevcut bundled rg kullanır. Docker, provider credentials, ağ veya kurulum gerekmez.
Bu düzeltmenin ardından güncellenen dokümantasyonun sonraki kontrolü full gate içindedir.
Aşağıdaki komutlar ilk Grup B çalışma kaydı olarak korunmuştur.

Dizin: repository kökü. Gereksinimler: Python 3, Git, Bash ve ripgrep (`rg`).
Docker/Java/Xcode/provider credentials gerekmez. Paket kurulumu otomatik yapılmaz.
Immutability komutu mevcut değişiklikleri başlangıç commit'iyle karşılaştırır.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
python3 -m unittest discover -s scripts/quality -p test_prompt_immutability.py
python3 scripts/quality/check_prompt_immutability.py --base-ref 65fcbea601b8587df5093cafeb464decea99267b
make docs-check contracts-check secret-scan
```

Bu grup sürüm dosyalarını birlikte prompt+hash değiştirerek aşmayı, manifest tutarlılığını,
contract'ları ve secret leakage riskini kontrol eder. CI workflow düzenlendi fakat tetiklenmedi.

### Grup C — Mevcut AI regresyonu

Güncel sonuç: `VERIFIED_PASS`. Son kullanıcı dosyası önceki hata loguyla birlikte
başarılı tekrar logunu da içeriyor. Docker bağlantısı kurulmuş; üç integration sınıfı
2026-10-26T00:18:15+03:00 tarihinde `BUILD SUCCESS` ile tamamlanmış:
classification golden 5, parser 11, recognition 7; toplam 23 test, 0 failure/error/skipped.
Önceki başarılı 34 Java testiyle birlikte Grup C'nin 57 testinin kanıtı tamamlandı.
Local evaluation 18/18 PASS kaydı geçerlidir. Kaynak düzeltmesi yapılmadı; Docker engeli
çözüldü. Logdaki Netty DNS fallback ve dynamic Java-agent uyarıları test hatası değil.
Aşağıdaki Docker hata/tekrar yönergeleri tarihsel kayıttır; yeniden çalıştırma istenmiyor.

2026-10-26 kullanıcı Maven logu (`00:14:56+03:00`): toplam 57 test, 0 assertion failure,
23 error, 0 skipped, `BUILD FAILURE`. Docker gerektirmeyen dört sınıf `VERIFIED_PASS`:
`ProblemClassificationValidatorTest` 15, `ConfiguredAiModelGatewayTest` 15,
`AiProviderBoundaryTest` 3, `ModularityTest` 1; toplam 34 başarılı test.

İlk anlamlı hata: iki Docker socket'i de connection refused verdi:
`unix:///var/run/docker.sock` ve `unix:///Users/burakcoskun/.docker/run/docker.sock`.
`PostgresIntegrationTestSupport` static initializer içindeki `POSTGRES.start()` başarısız;
sonraki `NoClassDefFoundError` mesajları bunun sonucudur. Kaynak kod değişikliği yapılmadı.
İlk denemede integration kanıtı `BLOCKED_EXTERNAL` idi: `ProblemClassificationGoldenEvaluationTest` 5,
`ProblemParseApplicationServiceTest` 11, `ProblemRecognitionApplicationServiceTest` 7 error.
Bu üç sınıfın sonraki owner-run tekrarı yukarıda kayıtlı şekilde başarıyla tamamlandı.

Aynı kullanıcı logundaki local evaluation `VERIFIED_PASS`: 18/18 vaka, 13 kritik vaka,
0 failed/blocked/skipped; comparator PASS, reasons/regressions boş.
Üretilmiş raporların ilgili alanları da okundu (runner tekrar çalıştırılmadı).
Report SHA-256: `5f12abd1d6771e354fc462e30cf895af96cfdfaeb592dc6fad9c00d30648af18`;
runId: `ingestion-e6641fed-5791-47e3-bdb6-f901b12e052f`.
Fixture near-duplicate uyarıları bu çalışmayı başarısız yapmadı. Bu yalnız
`LOCAL_FIXTURE_REGRESSION` kanıtıdır; connected provider NOT_REQUESTED,
protected holdout BLOCKED_PROTECTED_HOLDOUT durumları değişmedi.

Owner: Docker Desktop'ı açın, engine hazır olduktan sonra `docker info` ile Server
bölümünün hatasız geldiğini kontrol edin. Bu başarısızsa Maven'i tekrar başlatmadan çıktıyı paylaşın.

```sh
open -a Docker
```

Engine hazır olduktan sonra:

```sh
docker info
```

Ardından yalnız eksik integration kanıtı:

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -Dtest=ProblemRecognitionApplicationServiceTest,ProblemParseApplicationServiceTest,ProblemClassificationGoldenEvaluationTest test
```

Başarılı 34 test ve local evaluation tekrarı gerekmiyor. Aşağıdaki eski geniş komut
ilk çalışma kaydıdır; mevcut tekrar kapsamı yukarıdaki üç sınıftır.

Java testleri için Java 21/Maven ve çalışan Docker/Testcontainers gerekir;
PostgreSQL test container image'ı cache'de yoksa ağ gerekir. Provider credentials gerekmez.
Evaluation Python ortamında repo'nun mevcut evaluation bağımlılıkları kurulu olmalıdır.
`AI_EVAL_MODE=local` fixture modunu açıkça seçer; paid provider çağrısı yapmaz.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -Dtest=ConfiguredAiModelGatewayTest,AiProviderBoundaryTest,ModularityTest,ProblemRecognitionApplicationServiceTest,ProblemParseApplicationServiceTest,ProblemClassificationValidatorTest,ProblemClassificationGoldenEvaluationTest test
cd /Users/burakcoskun/Desktop/ai_verification_tutor
AI_EVAL_MODE=local make eval-ai
```

Gateway fallback/provenance/usage sınırları, module boundaries, recognition/parser
uyumluluğu ve deterministik ingestion regression kontrol edilir.
Docker yoksa ilk üç saf Java testi ayrı çalıştırılabilir; integration kanıtı beklemede kalır:

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
mvn -Dtest=ConfiguredAiModelGatewayTest,AiProviderBoundaryTest,ModularityTest test
```

### Grup D — A–C sonrası tam regresyon

2026-10-26 owner `make check` logu: doctor, lint, docs (273 Markdown), contracts ve
secret scan başarılı. API: 285 test, 0 failure, 1 error, 0 skipped; `BUILD FAILURE`.
Tek hata `S3CompatibleProblemAssetStorageTest`: mevcut MinIO image pull işlemi HTTP 404
(`pull access denied`) verdi. Test gövdesi çalışmadı. Make `test-api` hedefinde durdu;
verifier ve iOS bu çalıştırmada çalışmadı. İlk sonuç `BLOCKED_IMAGE_UNAVAILABLE` idi.

Owner güvenilir yerel image/onaylı mirror bulunmadığını bildirdi. Teste explicit
`verifiedai.test.minio-image` property eklendi; varsayılan sürüm ve gerçek S3 assertions
korundu. `infra/local/minio-test/Dockerfile` resmî release tag'ini ve commit'ini sabitleyerek
aynı kaynak sürümünden yerel test image'ı üretir. Owner build (96.5s) ve S3 testini
doğruladı: 1 test, 0 failure/error/skipped; BUILD SUCCESS, 2026-10-26T00:28:44+03:00.
MinIO recovery `VERIFIED_PASS`; ilk API koşusu tek başına tam PASS sayılmadı.
Go 1.22.5 yalnız builder container içinde kullanılır; host kurulumu gerekmez.
Bu image upstream release binary'siyle byte-identical değildir. `make test-api` artık
`test-minio-image` hedefine bağlıdır ve explicit Maven image property kullanır. Backend CI
aynı build hedefini ve property'yi kullanır; image dosyaları/Makefile path trigger'ları eklendi.
Owner Make image hedefini başarıyla çalıştırdı (0.7s, cache). Hosted CI ve bütünleşik
`make test-api` wrapper çalıştırma kanıtı yok; aynı build ve Maven property ayrı owner koşularında doğrulandı. Compose image sorunu ayrı local-stack borcudur.

Aşağıdaki build ve hedefli test başarılı owner çalışmasının kaydıdır; tekrarı gerekmiyor:

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
docker build -t verified-ai/minio-test:2024-07-16-3535197 infra/local/minio-test
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
cd services/api
mvn -Dverifiedai.test.minio-image=verified-ai/minio-test:2024-07-16-3535197 -Dtest=S3CompatibleProblemAssetStorageTest test
```

## I. Gönderilecek kanıt ve sonraki adım

2026-10-26 son owner logu (`7368a785-efa1-4a8b-8505-e9514fb3c910` attachment):

- `make test-minio-image`: başarılı, 0.7s, Docker cache kullanıldı.
- Documentation: 273 Markdown dosyası başarılı; contracts ve secret scan başarılı.
- Verifier: 20 passed, 1 warning, 0.57s. Mevcut Starlette/httpx deprecation uyarısı hata değil.
- iOS: iPhone 16 Pro simulator, `TEST SUCCEEDED`; bitiş 00:31:48+03:00.
  xcresult: `.generated/DerivedData/Logs/Test/Test-VerifiedAI-2026.10.26_00-30-54-+0300.xcresult`.

Grup D yerel kabulü `VERIFIED_PASS`: ilk full API koşusundaki 284 başarılı test,
MinIO düzeltmesinden sonraki 1/1 S3 tekrarı ve son kalan-kapılar koşusu birlikte
kabul kanıtını oluşturur. Tek bir kesintisiz `make check` PASS koşusu iddia edilmiyor.
A–D için yeni test tekrarı gerekmiyor. Bu son değişiklik yalnız kanıt dokümantasyonu ve
manifest güncellemesidir; son belge revizyonu ayrıca docs-check ile çalıştırılmadı.

Hosted CI ve branch-protection/CODEOWNERS kanıtı `BLOCKED_EXTERNAL` olarak açık kalır;
CAP-AI-002 / REQ-AI-003 bu dış yayın koşulları nedeniyle Partial kalır. Connected-provider,
protected holdout ve Compose local-stack recovery kendi kayıtlarında açık tutulur.
Yerel kabul üretim yayını veya bu dış kapılar için PASS anlamına gelmez.
Assistant test/build/validation çalıştırmadı; commit/push yapılmadı.

## Önceki rapor — tarihsel kayıt, bu revizyon için doğrulanmadı

# Sprint 5.2 Execution Report
## Prompt Registry, Schema Governance, and Prompt Release Workflow

Historical recorded status: COMPLETE — not current acceptance

Branch: `feature/sprint-5-2-prompt-registry-governance`

Starting HEAD: `8247c97cebc4c61b4019dfef25042687c9717cfc`

Implementation map: `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.2_IMPLEMENTATION_MAP.md`

## Scope Delivered

- Added source-controlled prompt registry metadata for enabled executable capabilities.
- Added source-controlled output schema registry metadata.
- Added strict immutable prompt version validation for `vNNN` identifiers.
- Added bounded classpath prompt and schema artifact resolvers with safe path checks.
- Integrated prompt/schema validation into AI route-planner startup for enabled routes.
- Added production fail-closed release-stage policy for enabled AI routes.
- Added `problem-classifier/v001` prompt artifact for `PROBLEM_CLASSIFY`.
- Renamed the classifier schema artifact to canonical `packages/schemas/problem-classification.schema.json`.
- Packaged `packages/schemas` into the API service classpath for schema resolution.
- Updated Docker build context so API image builds with schema resources available.
- Updated canonical prompt/schema documentation and repository placement guidance.

## Scope Excluded

- No Sprint 5.3 model-router budget/fallback policy work.
- No solver, secondary solver, arbitration, explanation, tutor, mistake-classifier, or practice-generation prompt activation.
- No new provider calls.
- No database migration.
- No public API or iOS DTO change.

## Safety And Privacy

- Prompt registry failures expose safe identifiers only.
- Prompt bodies, raw student content, provider payloads, object keys, signed URLs, and secrets are not logged by registry validation.
- Default executable prompt workflows remain `NOT_ELIGIBLE` for training.
- Schema-valid AI output remains untrusted until downstream semantic/domain validation accepts it.

## Validation Evidence

Repository and static gates:

```text
make doctor
git diff --check
docker compose config --quiet
docker compose build api
make docs-check
make contracts-check
make secret-scan
make lint
```

Historical claim (not revalidated in this session): PASS. The first API Docker image build exposed a final-stage copy path still pointing at `/workspace/target`; the Dockerfile now copies from `/workspace/services/api/target`, and the rerun built `verified-ai-local-api` successfully.

Deterministic AI evaluation:

```text
make eval-ai
```

Historical claim (not revalidated in this session): PASS. Dataset validation passed with 18 cases and 13 critical cases. Baseline decision was `PASS` with no regressions. The runner emitted existing near-duplicate fixture warnings only and wrote generated artifacts under `.generated/evaluations/latest`.

Focused API regression:

```text
mvn -Dtest=AiCapabilityRegistryTest,PromptVersionTest,PromptRegistryValidatorTest,ConfiguredAiRoutePlannerTest,ClasspathArtifactResolverTest,ConfiguredAiModelGatewayTest,AiProviderBoundaryTest,ModularityTest test
```

Historical claim (not revalidated in this session): PASS, 55 tests run, 0 failures, 0 errors, 0 skipped.

Full API regression:

```text
make test-api
```

Historical claim (not revalidated in this session): PASS, 264 tests run, 0 failures, 0 errors, 0 skipped. The first sandboxed attempt could not access Docker/Testcontainers; the approved escalated rerun completed successfully.

Math verifier regression:

```text
make test-verifier
```

Historical claim (not revalidated in this session): PASS, 20 tests passed. The earlier parallel run timed out one SymPy equivalence test while the full API suite was competing for CPU; the isolated rerun passed with only the existing Starlette/httpx deprecation warning.

iOS no-impact confirmation:

```text
make test-ios
```

Historical claim (not revalidated in this session): PASS, `TEST SUCCEEDED` on iPhone 16 Pro simulator. The first sandboxed attempt could not access CoreSimulator; the approved escalated rerun completed successfully.

## Rollout And Rollback

Rollout requires explicit registry promotion and route pinning to immutable `promptVersion` and `schemaVersion` values. Production enabled routes reject `LOCAL_ONLY` and `STAGING` prompt entries.

Rollback is route re-pinning to the previous registered prompt/schema version. Old prompt bodies and old schema meanings must not be edited.

## Exit Assessment

Historical exit assertion (not a current acceptance decision):

- Enabled local/test routes validate against registered prompt/schema metadata.
- Classifier prompt artifact exists and resolves from the API classpath.
- Classification schema filename drift is resolved.
- Production enabled routes fail closed unless prompt registry entries are `PRODUCTION`.
- Local/test fixture routes remain usable.
- Future capabilities remain disabled.
- Prompt/schema governance adds zero provider calls.
- Documentation, manifest, coverage matrix, and traceability matrix are updated.
