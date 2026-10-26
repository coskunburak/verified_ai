# Sprint 5.4-RM — Gerçek model entegrasyon haritası

Durum: **BACKEND_REGRESSION_PASS — REAL_MODEL_ACCEPTANCE_BLOCKED**.
2026-10-27 owner tam backend regresyonu geçti: 389 PASS / 2 gerçek-model gated SKIP.
Adapter/eligibility/artifact/pipeline sınıflarında 20 sentetik test geçti; inference/kalite kanıtı yoktur.
Ayrı Python/evaluation araçları için yeni owner çıktısı beklenir. Önceki A–E tarihsel kanıtı ayrı kalır.
[Model kararı](SPRINT_5.4_QWEN_MODEL_DECISION.md), [sahibi komutları](SPRINT_5.4_REAL_MODEL_OWNER_RUNBOOK.md),
[kabul kaydı](SPRINT_5.4_REAL_MODEL_ACCEPTANCE_REPORT.md).

## Sınırlar ve sahiplik

`PrimarySolveService → AiModelGateway → ConfiguredAiRoutePlanner → VllmSolveProviderAdapter → kontrollü vLLM`
ve dönüşte `AiSolveResult → mevcut SolutionCandidateValidator → GENERATED_UNVERIFIED / REVIEW_REQUIRED`.
V017/V018, public API, iOS ve solution-candidate-v1 değişmedi. Sadece VerificationPolicy VERIFIED
verebilir; bu sprint bağımsız doğrulama planner/arbitration uygulamaz. Fixture gerçek zekâ kanıtı değildir.
AI transport/config AI infrastructure'a aittir; solving katmanı vLLM türlerine bağımlı değildir.
Yeni SDK/dependency yoktur; JDK 21 HttpClient ve mevcut Jackson kullanılır.

## Uygunluk, prompt ve dil

`SolverModelEligibility`: exact provider/model, yerel/test ortamı, durable ledger, tek attempt,
fallback yok, route/prompt/schema/pricing/gate uyumu, pozitif allocation ve explicit approved eligibility.
`VllmSolverProperties`: owner evaluation reference, exact model/tokenizer/processor/runtime,
image digest ve deployment evidence digest zorunlu. Bunlar yönetici beyanıdır; belge varlığı veya
GPU üzerinde yüklenmiş ağırlıklar otomatik doğrulanmış sayılmaz. Üretim/staging engeli bağımsız kalır.

`solver/v001` byte'ları ve digest'i korunur. `solver/v002`, yeni `OFFLINE_EVALUATION` prompt yaşam
döngüsüyle sadece local/test ortamına açılır. Bu, kalite kabulü değildir; staged/production promotion
mevcut digest/evaluation/approval kurallarını yine gerektirir. Yeni durum fixture-only LOCAL_ONLY
kuralını değiştirmez. `v002` registry SHA-256 ile bağlıdır; geri dönüş üretimde SOLVE kapatılmasıdır.

Servis dili trusted `app.solving.language=tr|en` değeridir; varsayılan `tr`. Request metni locale,
provider, endpoint, bütçe veya route yetkisi belirleyemez. Kullanıcı başına dil seçimi bu API'ye eklenmedi.
Canonical problem/parse/revision/assumption/restriction bilgileri gönderilir; görüntü gönderilmez.

## Transport ve hata sınıfları

- Tam origin allowlist; `/v1` endpoint; userinfo/query/fragment yasak. HTTP yalnız literal loopback;
  diğer erişim HTTPS. Redirect ve istemci kaynaklı URL yoktur. Service credential loglanmaz.
- `/v1/models` kimlik/erişilebilirlik gözlemi 1 s ile sınırlıdır, 5 s cache kullanır; route seçimi
  taze health gerektirir. Bu probe GPU generation değildir ve kalite onayı vermez.
- `/tokenize` exact chat template ile input sayar; 6144 input + 2048 output ≤8192 context.
  Model sunucusunun `max_model_len` değeri de kontrol edilir. Oversize input generation öncesi durur.
- JSON request ≤65536 B; wire response ≤131072 B; candidate ≤65536 B. Sınırlı subscriber,
  duplicate-key/trailing-token/depth denetimi; bir assistant completion ve `finish_reason=stop` gerekir.
- Bağlantı ≤2 s; tokenize+completion+body+mapping toplamı attempt/command deadline ile ≤30 s.
  Concurrency ≤2; stream/tool call yoktur. Süre dolunca future iptal edilir ve bağlantı kesilmeye çalışılır.
- 401/403→INVALID_AUTH; 429→RATE_LIMITED; 5xx→PROVIDER_UNAVAILABLE; malformed/missing usage→SCHEMA_INVALID;
  oversized→OUTPUT_TOO_LARGE; yanlış kimlik→POLICY_BLOCKED; timeout→TIMEOUT; I/O/interruption→UNKNOWN.
  Bu adaptör tüm hatalarda automatic replay'i kapatır. İstemci disconnect'i GPU işinin durduğunu veya
  ücretin iade edildiğini kanıtlamaz. Mevcut cancel/source-change fencing geç sonucu engeller;
  kullanıcı cancel'i aktif HTTP çağrısını anında sonlandırmaz, deadline üst sınırı korunur.

## Muhasebe

Fixture bütçesi açıkça sıfır. Gerçek iş ceiling'i `min(app.solving.max-cost-micros, route.maxCostMicros)`;
başlangıç varsayılanı sıfır. Mevcut PostgresAiSpendGuard operation/session/user/day/month/provider/global
ve tier hesaplarını transaction içinde rezerve eder. Yeni ledger yoktur.
Desteklenen tek ekonomi `PREPAID_CAPACITY_FIXED_ALLOCATION`: önceden ayrılmış kapasite için sahibi onaylı
sabit request allocation. Input/output allocation pozitif, image=0; fiyat sürümü exact. Adapter'ın
cost-ceiling beyanı bu iç tahsis miktarı içindir, GPU sağlayıcısının faturası veya token tarifesi için değildir.
Hata/eksik usage'da commitment tutulur; reported cost/token NULL ve UNKNOWN olabilir, confirmed zero olmaz.
Başarıdaki gerçek token sayıları mevcut `ai_spend_attempts` tablosuna yazılır; üst usage toplamı
commitment üst sınırıdır. Retry/fallback bu rota için kapalı; gelecekte mevcut ortak ceiling kullanılır.
İç allocation, marjinal enerji/işletim gideri ve tam GPU TCO raporda ayrı kalır.

## Güvenlik ve yaşam döngüsü

Riskler: prompt injection, model JSON saldırısı, SSRF/redirect, yanlış model alias'ı, payload/compute DoS,
duplicate charge, credential/log sızıntısı, başarısız işin tekrarı ve model self-verification iddiası.
Karşılıkları kod sınırları, owner-attested deployment, özel ağ/loopback, mevcut ledger/idempotency ve
mevcut semantic validator'dır. Serving recipe request loglarını ve prefix cache'i kapatır; runtime
cache sadece model/derleme cache'i içindir. Prompt/response debug logging açılmaz. Canlı student-data
ortamı bu tarif için onaylı değildir. Secret yönetimi operatör sınırıdır; kayıtlı config/evidence'da secret yoktur.

Accepted candidate mevcut privacy lifecycle/export/deletion davranışını izler. Rejected raw output,
reasoning_content veya private chain-of-thought tutulmaz. Benchmark raporları yalnız sentetik normalize
candidate ve operasyon metadata'sı içerir; `.generated/qwen-evaluation` altında kalır. Bunlar eğitim verisi değildir.
Üretim içeriği yanlışlıkla test ortamına taşınmamalıdır; test corpus yolu sabit ve synthetic origin kontrollüdür.

## Ölçüm ve gelecekteki işler

Durable smoke mevcut kaynak canonicalization/job/idempotency/ledger hattını test eder. Canonical benchmark
ise izole corpus snapshot'larını aynı gateway/ledger üzerinden çağırır; ingestion/OCR performansı ölçmez.
18 sentetik vaka vardır: 11 çözülebilir aday, 1 belirsiz kaynak reddi, 6 gelecek kapsam reddi.
Gold değerler proje tarafından yazıldı; bağımsız referans incelemesi beklenir. Release corpus/holdout değildir.

Offline raporlayıcı bağımsız JSON Schema kontrolü ve sınırlı exact rational grading yapar; eval/sympify yoktur.
Symbolic eşdeğerlik belirsizse manual review gerekir. Rejected ham payload tutulmadığından bazı schema
sonuçları UNAVAILABLE'dır; yalnız schema acceptance lower bound raporlanır. Bunu ≥99% release kanıtı saymayın.
Step validity, Türkçe kalite ve invented assumptions bağımsız reviewer rubric'inden gelir; eksik veri boş kalır.
Üretim yanlış-VERIFIED oranı, Q4 etkisi, OCR, yük altı kapasite ve gerçek fatura maliyeti ölçülmedi.

QLoRA kararı: başlamayın. Önce base benchmark ve hata analizi; rights/lineage onaylı veri,
train/dev/test family split, korumalı holdout, baseline accuracy, training bütçesi, adapter versioning,
before/after kalite gerekir. Base model yeterliyse tuning gerekmez. Escalation seam mevcut RouteRole,
failureClass ve authoritative canonical source'tur; self-confidence esaslı yönlendirme eklenmez.
