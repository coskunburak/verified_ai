# Sprint 5.4-RM — Sahibi çalıştırma ve kabul kapıları

**Asistan test/build/validator/inference çalıştırmadı.** Sırayla ilerleyin; ilk hatada durup komutu, özet sonucu ve
ilk anlamlı stack trace'i paylaşın. Credential/env dosyasını, tam config çıktısını veya öğrenci içeriğini paylaşmayın.
Önce A–C; D–F yalnız exact model/runtime, donanım ve kapasite bütçesi sahibi tarafından onaylandığında.
Test başarılı olsa bile dağıtım kararı otomatik verilmez.

2026-10-27 kanıt güncellemesi: owner tam Maven regresyonu 389 PASS / 2 gerçek-model gated SKIP,
iOS 111 test başarılı, verifier 20 PASS; depo kalite kontrolleri tamamlandı. Aynı testleri tekrar etmeyin.
A/C'deki ayrı Python/evaluation adımları ve D–F canlı model kapıları tamamlanmış sayılmaz.
[Güncel kabul raporu](SPRINT_5.4_REAL_MODEL_ACCEPTANCE_REPORT.md) kapsamı ve açık engelleri kaydeder.

## Gate A — Statik, kontrat ve negatif testler

Dizin `/Users/burakcoskun/Desktop/ai_verification_tutor/services/api`.
Java 21, Maven ve Python 3 gerekir. Mock HTTP testleri loopback açar; GPU/Docker/paid API gerekmez.
Maven dependency cache yoksa indirme ağı gerekir. Python schema testleri `jsonschema` 4.x gerektirir;
eksik bağımlılığı raporlayın, otomatik kurulum yoktur.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

```sh
unset QWEN_OWNER_RUN
mvn -Dtest=VllmSolveProviderAdapterTest,SolverModelEligibilityTest,QwenEvaluationArtifactsTest,SolverRouteConfigurationTest,SolverFixtureAdapterTest,ConfiguredAiRoutePlannerTest,AiRouteSelectorTest,PromptRegistryValidatorTest,PromptReleasePolicyTest,ClasspathArtifactResolverTest,ArtifactIntegrityTest,SolutionCandidateValidatorTest,SolutionCandidateContractTest,AiProviderBoundaryTest,ModularityTest test
cd /Users/burakcoskun/Desktop/ai_verification_tutor
python3 evaluations/runners/validate_solver_fixtures.py
python3 -m unittest discover -s evaluations/runners -p test_qwen_report.py
python3 scripts/quality/check_prompt_immutability.py --base-ref f3cec8ab05bf182137fcfd3f80c41f56b2a5bddd
```

Ölçer: exact model/prompt uygunluğu, production/staging engeli, endpoint/JSON/usage/deadline sınırları,
zero-budget/unknown-spend davranışı, fixture regresyonu, trust sınırı ve sayısal grader.
Paylaşın: Maven summary, başarısız test adı, Python sonuçları. CODE/CONTRACT/DEPENDENCY hatalarını
ayırın; bu kapı model kalitesini veya GPU çalışmasını kanıtlamaz.

## Gate B — Backend ve PostgreSQL

Docker Desktop, PostgreSQL Testcontainers image erişimi ve aynı Java/Maven ortamı gerekir.
Gerçek model, GPU, credential veya ücretli sağlayıcı gerekmez.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
unset QWEN_OWNER_RUN
mvn -Dtest=VllmSolvePipelineIntegrationTest,PrimarySolveIntegrationTest,PrimarySolveControllerTest,AiSpendGuardIntegrationTest,AiUsageLedgerIntegrationTest,FlywayMigrationTest,AccountPrivacyApplicationServiceTest test
```

Ölçer: V017/V018, canonical source, idempotency, retry/cancel/recovery, aggregate budget, usage,
account lifecycle ve authenticated API. Paylaşın: test summary ve ilk hata. Docker/database bootstrap
hatası ENVIRONMENT; migration/transaction/ledger hatası CODE veya CONTRACT olarak incelenir.

## Gate C — Tam regresyon

Docker, Java/Maven, verifier Python ortamı, Xcode/iPhone 16 Pro simulator gerekir. İlk API image
build'i ve cache eksikleri ağ gerektirebilir; gerçek inference veya GPU yoktur.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
unset QWEN_OWNER_RUN
make check
AI_EVAL_MODE=local make eval-ai
```

Paylaşın: bileşen özetleri, son make sonucu ve varsa ilk hata. `make eval-ai` ingestion fixture
regresyonudur; Qwen doğruluğu değildir. Yeni farklar için mevcut eski A–E logları kullanılmaz.

## Gate D — İzole serving, ayrı adımlar

Mac üzerinde bu vLLM GPU tarifini başlatmayın. Önceden sahibi tarafından sağlanmış Linux/NVIDIA GPU,
Docker GPU runtime, yeterli RAM/disk ve yaklaşık 24 GB VRAM sınıfı planlanır; fit kanıtı henüz yoktur.
Hiçbir komut instance/subscription oluşturmaz. Kullanılan makine zaten faturalıysa zaman maliyeti sürer;
bu maliyet için ayrıca sahibi onayı ve kapatma planı gerekir. İlk tercih önceden ayrılmış kapasitedir.

Önce Mac tarafında secret içermeyen örneği yerel ignored dosyaya kopyalayın ve düzenleyin:

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
umask 077
mkdir -p .generated/qwen-evaluation
cp -n infra/local/qwen-owner.env.example .generated/qwen-evaluation/owner.env
chmod 600 .generated/qwen-evaluation/owner.env
${EDITOR:-vi} .generated/qwen-evaluation/owner.env
```

`change_me` değerlerini gerçek onay/evidence ile doldurun. Harcama tutarları USD micro-unit'tir;
input/output pozitif ve toplamları allocation'ı aşmamalı; image=0. Total, run için onaylı üst sınırdır.
Bu değerler tahminî aylık GPU fiyatlarından otomatik türetilmez. `QWEN_OWNER_RUN` ancak onaydan sonra
`APPROVED_SYNTHETIC_EVALUATION` olmalıdır. Credential 24–256 güvenli karakter olmalıdır.
`QWEN_REPORT_DIR` her çalıştırmada yeni, mutlak ve `.generated/qwen-evaluation/` altında olmalıdır.

GPU host üzerinde repository'nin aynı değişiklikleri içeren checkout'u ve gerekli path/env değerleri
hazır olmalı. Secret aktarımını özel kanal üzerinden yapın. Prerequisite incelemesi ayrı:

```sh
cd "$QWEN_GPU_REPO"
nvidia-smi
docker version
docker compose version
hf --version
docker buildx imagetools inspect vllm/vllm-openai:v0.17.1
```

Son komut yalnız registry metadata okur; gerçek image digest'ini ve platformu inceleyip
`QWEN_RUNTIME_DIGEST=sha256:...` olarak owner env'e kaydedin. Hash uydurmayın. Image/kernel/CUDA
ve lisans kontrolü başarısızsa **BLOCKED_RUNTIME_APPROVAL**. Donanım çıktısı ve redakte edilmiş digest
paylaşılabilir. `docker compose config` secret genişletebileceği için çıktısını paylaşmayın.

Onaydan sonra image indirme, ağırlık indirmeden ayrıdır:

```sh
docker pull "vllm/vllm-openai:v0.17.1@$QWEN_RUNTIME_DIGEST"
```

Ayrı bir adımda yalnız sabit model snapshot'ını indirin; ağ ve çok GB disk gerekir:

```sh
hf download Qwen/Qwen3.5-4B --revision 851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a --local-dir "$QWEN_WEIGHTS_DIR"
```

Başlatmadan önce LICENSE/config/tokenizer/chat template ve tüm ağırlık dosyalarının SHA-256 listesini,
resmî revision metadata'sıyla karşılaştırın; image digest, CLI, GPU/driver ve izin verilen corpus'u
`deployment-evidence.json` dosyasında bağlayın. Dosya üretimi bir onay kararıdır; model alias'ı yeterli değildir.
Sahibi hazırladığı bu dosyanın digest'ini alır:

```sh
sha256sum "$QWEN_WEIGHTS_DIR"/*.safetensors "$QWEN_WEIGHTS_DIR"/config.json "$QWEN_WEIGHTS_DIR"/tokenizer.json "$QWEN_WEIGHTS_DIR"/chat_template.jinja "$QWEN_WEIGHTS_DIR"/LICENSE
sha256sum deployment-evidence.json
```

`QWEN_DEPLOYMENT_SHA256` ikinci digest'tir; `null` manifest alanlarını onaylı evidence kopyasında doldurun.
Mevcut manifestte snapshot/file checksum değerleri bilerek eksiktir. Sonraki bağımsız başlatma:

```sh
cd "$QWEN_GPU_REPO"
mkdir -p "$QWEN_RUNTIME_CACHE_DIR"
docker compose -f infra/local/docker-compose.qwen-evaluation.yml up -d --no-build --pull never
docker compose -f infra/local/docker-compose.qwen-evaluation.yml logs --tail=100 qwen-evaluation
```

Beklenen: pinned BF16 model, 8192 context, max 2 sequence; image/video limit=0, request logging kapalı,
loopback port ve internal network. OOM **CAPACITY**, unsupported architecture/kernel **RUNTIME_COMPATIBILITY**,
model dosyası/hash hatası **ARTIFACT_IDENTITY**. Ayarları rastgele büyütmeyin. Recipe çalıştırılmadı;
ilk loglar runtime flag/driver uyumluluğunu da doğrulayacaktır.

Sabitlenen vLLM sürümünde request/output logging `--no-enable-log-requests` ve
`--no-enable-log-outputs` ile kapatılır. Yerleşik bearer kontrolü yalnız `/v1` yollarını korur;
`/tokenize` bu kontrolün dışında kalır. Bu yüzden loopback/private network ve SSH tüneli zorunludur.
HTTPS endpoint kullanılacaksa reverse proxy `/tokenize` dahil bütün izinli yolları ayrıca doğrulamalı,
diğer yolları reddetmelidir. Portu public açmak bu tarifin kapsamında değildir.
Kaynaklar: [v0.17.1 CLI](https://github.com/vllm-project/vllm/blob/v0.17.1/vllm/entrypoints/openai/cli_args.py),
[authentication middleware](https://github.com/vllm-project/vllm/blob/v0.17.1/vllm/entrypoints/openai/server_utils.py).

Gerekirse Mac'ten sahibi onaylı GPU host'a ayrı SSH tüneli açın (`QWEN_GPU_SSH_TARGET` sahibi seçer):

```sh
ssh -N -L 127.0.0.1:8000:127.0.0.1:8000 "$QWEN_GPU_SSH_TARGET"
```

Farklı terminalde kimlik/erişim kontrolü; yalnız metadata, generation yapmaz. Credential shell
argümanına yazılmaz; Python environment'tan okur. Adapter'ın HTTPS/allowlist politikası dışına çıkmayın.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
. .generated/qwen-evaluation/owner.env
python3 - <<'PY'
import json, os, urllib.request
endpoint = os.environ['QWEN_ENDPOINT']
if endpoint != 'http://127.0.0.1:8000/v1':
    raise SystemExit('Use the reviewed loopback SSH tunnel for this connectivity command')
request = urllib.request.Request(endpoint + '/models', headers={'Authorization': 'Bearer ' + os.environ['QWEN_SERVICE_CREDENTIAL']})
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None
opener = urllib.request.build_opener(NoRedirect)
with opener.open(request, timeout=2) as response:
    data = response.read(16385)
if len(data) > 16384:
    raise SystemExit('Oversized metadata')
ids = [x['id'] for x in json.loads(data)['data']]
expected = 'qwen3.5-4b-851bf6e806efd8d0a36b00ddf55e13ccb7b8cd0a-bf16-vllm0171'
if ids != [expected]:
    raise SystemExit('MODEL_IDENTITY_MISMATCH')
print('Exact served model identity matched; weight attestation still requires owner evidence.')
PY
```

## Gate E — Gerçek primary SOLVE smoke

Gate D, Java/Maven, Docker/PostgreSQL Testcontainers ve private endpoint gerekir. Bu komut **gerçek
inference** yapar; yalnız sahibi çalıştırır. Test source synthetic, production öğrencisi yoktur.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
. .generated/qwen-evaluation/owner.env
cd services/api
mvn '-Dtest=QwenRealModelEvaluationTest#durableSmoke' test
```

Ölçer: mevcut canonicalization → durable job → router → gerçek adapter → candidate validator →
GENERATED_UNVERIFIED; duplicate run'un tek attempt olması, exact model ve nonzero ledger.
Paylaşın: Maven summary ve `QWEN_REPORT_DIR/qwen-durable-smoke.json` (sentetik public candidate,
redakte edilmiş deployment metadata ve token/allocation). Tek doğru yanıt kalite kabulü değildir.
Unknown usage/timeout varsa replay yapmayın; retained commitment ve job state'i inceleyin.

## Gate F — Matematik, schema, latency ve maliyet

Gate E sonrası owner env'de yeni report directory seçin. Aynı approved budget; 18-case üst sınırı.
Her yeni test süreci izole DB başlattığından run'lar arası toplam GPU bütçesini operatör ayrıca izler.
Fixture corpus bağımsız incelemesi tamamlanmadan release-quality sonucu çıkarılamaz.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
. .generated/qwen-evaluation/owner.env
cd services/api
mvn '-Dtest=QwenRealModelEvaluationTest#canonicalBenchmark' test
cd /Users/burakcoskun/Desktop/ai_verification_tutor
python3 evaluations/runners/summarize_qwen_evaluation.py "$QWEN_REPORT_DIR/qwen-attempts.jsonl" --output "$QWEN_REPORT_DIR/summary.json"
```

Maven benchmark'in tamamlanması doğruluk kapısının geçilmesi değildir. Operasyonel hatada corpus loop
durur; partial coverage raporlanır. Kabul edilen candidate bağımsız jsonschema ve numeric grader'dan
geçer; unresolved symbolic cases, adım doğruluğu, Türkçe ve invented assumptions reviewer gerektirir.
İnceleme şablonu `evaluations/fixtures/solver/qwen-review-template.json`; fresh output ile tekrar:

```sh
python3 evaluations/runners/summarize_qwen_evaluation.py "$QWEN_REPORT_DIR/qwen-attempts.jsonl" --reviews "$QWEN_REPORT_DIR/reviews.json" --output "$QWEN_REPORT_DIR/reviewed-summary.json"
```

GPU host üzerinde ayrı terminalde ölçüm başlatın; benchmark bittiğinde Ctrl-C ile sonlandırın:

```sh
nvidia-smi --query-gpu=timestamp,name,memory.used,utilization.gpu,power.draw --format=csv -l 1
```

Çıktıyı owner evidence klasörüne kaydedin. `qwen-capacity-template.json` kopyasına warm/cold ayrımı,
load time, peak memory, wall-time, batch/concurrency, fatura ve operator/storage/network giderlerini yazın.
Mevcut runner ardışık kalite/latency ölçer; concurrency throughput kapasite kabulü değildir.
Structured-output karşılaştırması için yeni run directory ve `QWEN_STRUCTURED_OUTPUTS=true` ile aynı
corpus/ayarları kullanın. Q4 karşılaştırması **henüz çalıştırılamaz**: exact quant artifact/runtime/kernel
onayından sonra manifest/eligibility için ayrı gözden geçirilmiş değişiklik gerekir. Kör `--quantization` eklemeyin.

Paylaşın: attempts JSONL, summary, reviewer sonuçları, memory/time CSV, capacity template, exact image/model
evidence ve run ayarları. MALFORMED/SCHEMA, SOURCE/SEMANTIC, MATH_ACCURACY, LANGUAGE, TIMEOUT,
UNKNOWN_USAGE, BUDGET ve CAPACITY hatalarını ayrı değerlendirin. Gerçek fatura yoksa measured cost boş kalır.

## Gate G — Karar ve kapatma

Karar seçenekleri: ACCEPTED_FOR_OFFLINE_EVALUATION, ACCEPTED_FOR_STAGING, REQUIRES_MORE_EVIDENCE,
REJECTED, PRODUCTION_APPROVED. Şu an REQUIRES_MORE_EVIDENCE. Staging/production seçeneği kodda
henüz açılmaz; ilgili prompt/schema/model approval, kalite, privacy, TCO, operasyon ve ADR kapıları gerekir.

Rollback: uygulamada `app.solving.enabled=false`, `worker-enabled=false`, SOLVE route disabled,
`app.ai.vllm-solver.enabled=false`; restart. Outstanding RUNNING işleri mevcut recovery/OUTCOME_UNKNOWN
prosedürüyle inceleyin; kör replay/refund yoktur. Fixture sadece test profilinde geri alınabilir,
production fallback olarak kullanılamaz. GPU host'ta ayrı kapatma:

```sh
cd "$QWEN_GPU_REPO"
docker compose -f infra/local/docker-compose.qwen-evaluation.yml down
```

Bu komut GPU cloud kiralamasını sonlandırmaz. Önceden kiralanmış makinenin faturalandırmasını kapatma
işlemi sahibinin ayrı sorumluluğudur; bu çalışma provisioning/termination komutu içermez.
