# Sprint 5.4-UI — Uygulama ve owner kabul raporu

**SPRINT_5.4_UI_IMPLEMENTED — AUTOMATED_REGRESSION_PASS — MANUAL_ACCEPTANCE_PARTIAL**

Checkpoint: 2026-10-27 (Europe/Istanbul).

Gate A: **PASS_OWNER_EXECUTED — 26/26 tests**. Gate B: **PASS_OWNER_EXECUTED — 6 initial PASS + relaunch focused PASS**.
Gate C: **PASS_OWNER_EXECUTED — 22/22 backend tests + contract check passed**.
Gate D: **PARTIAL_OWNER_ACCEPTANCE — screenshots + return/EN/large-text/VoiceOver/Reduce-Motion PASS**.
Gate E iOS: **PASS_OWNER_EXECUTED — 111 tests; TEST SUCCEEDED**.
Gate E: **PASS_OWNER_EXECUTED — API 389 PASS / 2 gated SKIP; verifier 20 PASS; iOS 111 tests successful**.
Gate D genişletilmiş manuel matrisi bekliyor; Gate F blocked.

## A. BASELINE

Branch `feature/sprint-5-2-prompt-registry-governance`; HEAD
`f3cec8ab05bf182137fcfd3f80c41f56b2a5bddd`. Başlangıçtaki 42 RM dosyası korundu.
UI paketi ayrı kabul edilir. RM backend regresyonu geçti; REAL_MODEL_ACCEPTANCE_BLOCKED sürüyor.
Önceki A–E owner kanıtı yalnız eski baseline içindir; bu rapordaki yeni kanıt ayrıca kayıtlıdır.
İlk checkpoint'te commit/push yapılmadı. Kullanıcı 2026-10-27'de raporların tamamlanmasını ve
mevcut RM/UI çalışmasının GitHub'a commit/push edilmesini açıkça istedi; yayın kapsamı aşağıdadır.

## B. PRODUCT JOURNEY

Mevcut giriş → History → ready session → Solve Problem → mevcut işi keşfet → explicit Start
→ QUEUED/RUNNING → önerilen cevap/adımlar → zorunlu unverified disclosure → çık/yeniden aç/server recovery.
Capture/classification sonu aynı session detayına döner. Review ve stale ekranı kaynak incelemesine yönlendirir.

## C. IOS IMPLEMENTATION

ProblemSolving domain/API/recovery/ViewModel/SwiftUI feature'ı, güvenli AST presenter ve DEBUG fixture yazıldı.
AppDependencies ve mevcut History NavigationStack kullanılır. Tasarım tokenları ve TR/EN catalog genişletildi.
Release/staging görünürlük kapalı; development açık gate ister. Yeni paket/servis kurulmadı.

## D. SOLVE CONTRACT

Mevcut start/read/retry/cancel aynen tüketilir. Yalnız `GET /solves/current` owner-scoped read-only lookup eklendi.
Tüm 11 lifecycle durumu tipli; doğrulama notice'ı authoritative contract sabitiyle eşleştirilir.
Backend service/RM model dosyaları değiştirilmedi. Ayrıntılar [uygulama haritasında](SPRINT_5.4_UI_IMPLEMENTATION_MAP.md).

## E. MATHEMATICAL PRESENTATION

Exact string sayılar; izinli AST; parantezli işlem/fraction/power/functions/relations. Safe plain Text,
VoiceOver sözlü semboller; HTML/LaTeX/code evaluation yok. Source restrictions metni olmayan ID'lerden
kural uydurulmaz. Bu bir candidate ekranıdır; bağımsız matematiksel doğrulama yapılmaz.

## F. RECOVERY AND SECURITY

15 GET ile sınırlı tek task, cancellation/generation fence, foreground reconciliation. Açık retry dışında
inference tekrarı yok. Belirsiz POST key'i atomik user-scoped marker'da korunur. Server lookup yeniden
kurulum ve cihazlar arası keşfi sağlar. Sign-out/delete temizler; stale/error cevapları gizlenir.

## G. FILE INVENTORY

Aşağıdaki tablo yalnız UI paketinin dosyalarını listeler. Ortak dokümanlarda önceki RM kayıtları korunmuştur.

| Dosya | Değişiklik |
| --- | --- |
| `apps/ios/VerifiedAI.xcodeproj/project.pbxproj` | Yeni Swift ve XCTest dosyalarının mevcut hedef kayıtları. |
| `apps/ios/VerifiedAI/App/AppDependencies.swift` | Paylaşılan API/recovery injection; explicit DEBUG fixture ve görünürlük gate. |
| `apps/ios/VerifiedAI/App/RootView.swift` | Hesap kapsamlı çözüm factory, capture handoff ve çıkış/silme temizliği. |
| `apps/ios/VerifiedAI/Features/Home/Presentation/HomePlaceholderView.swift` | History çözüm bağımlılığı ve sentetik fixture etiketi. |
| `apps/ios/VerifiedAI/Features/ProblemCapture/Presentation/Capture/ProblemCaptureView.swift` | Classified problem için aynı session detayına devam yolu. |
| `apps/ios/VerifiedAI/Features/ProblemHistory/Presentation/History/ProblemHistoryView.swift` | Solve/review navigation, ready action, kaydırılabilir session detail. |
| `apps/ios/VerifiedAI/Features/ProblemHistory/Presentation/History/ProblemHistoryViewModel.swift` | READY_FOR_SOLVE navigation intent ve hesap değişiminde geç yanıt engeli. |
| `apps/ios/VerifiedAI/Resources/Localizable.xcstrings` | Çözüm durumları, hatalar, uyarılar ve matematik için TR/EN metinler. |
| `apps/ios/VerifiedAI/Features/ProblemSolving/Domain/Model/SolutionCandidate.swift` | Tipli snapshot/candidate/AST, bounded decoding ve trust/lineage kontrolleri. |
| `apps/ios/VerifiedAI/Features/ProblemSolving/Domain/Port/PrimarySolveServicing.swift` | Current/start/read/retry/cancel servis sınırı. |
| `apps/ios/VerifiedAI/Features/ProblemSolving/Data/Remote/PrimarySolveAPI.swift` | Mevcut APIClient üzerinden endpoint ve idempotency mapping. |
| `apps/ios/VerifiedAI/Features/ProblemSolving/Data/Local/SolveRecoveryStore.swift` | Yalnız kimlikleri tutan atomik, korumalı, kullanıcı kapsamlı marker. |
| `apps/ios/VerifiedAI/Features/ProblemSolving/Data/Debug/FixturePrimarySolveAPI.swift` | Yalnız DEBUG sentetik solve/history/cache; canlı hata fallback değildir. |
| `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveViewModel.swift` | Tek-task lifecycle, polling, retry, cancellation ve uncertain recovery. |
| `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Solution/PrimarySolveView.swift` | Cevap/adımlar/uncertainty/unverified disclosure ve erişilebilir kontroller. |
| `apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Components/MathematicalExpressionView.swift` | AST grouping ve konuşma anlamını koruyan plain Text sunumu. |
| `apps/ios/VerifiedAITests/ProblemSolving/PrimarySolveContractTests.swift` | Wire/path/key/error/trust/lineage testleri. |
| `apps/ios/VerifiedAITests/ProblemSolving/PrimarySolveViewModelTests.swift` | Async lifecycle, offline, bound, duplicate, unknown ve recovery testleri. |
| `apps/ios/VerifiedAITests/ProblemSolving/MathPresentationTests.swift` | Üs/eksi/parantez/fraction/long text ve unsafe AST testleri. |
| `apps/ios/VerifiedAITests/ProblemSolving/SolveRecoveryStoreTests.swift` | Disk relaunch, hesap/fixture scope ve temizleme testleri. |
| `apps/ios/VerifiedAITests/ProblemHistory/Presentation/ProblemHistoryViewModelTests.swift` | Ready action için navigation intent testi. |
| `apps/ios/VerifiedAIUITests/PrimarySolveUITests.swift` | Fixture tabanlı navigasyon, sonuç, retry/review/stale/relaunch ve TR büyük yazı UI testleri. |
| `packages/contracts/openapi/public-api.yaml` | Salt okunur current solve lookup ve null/owner/error sözleşmesi. |
| `services/api/src/main/java/com/verifiedai/solving/application/CurrentSolveQuery.java` | Sahiplik kontrollü son iş keşfi; mevcut get ile STALE projeksiyonu. |
| `services/api/src/main/java/com/verifiedai/solving/api/PrimarySolveController.java` | Authenticated GET /solves/current bağlantısı. |
| `services/api/src/main/java/com/verifiedai/solving/infrastructure/persistence/SolveStore.java` | User+session filtreli deterministik son iş sorgusu. |
| `services/api/src/test/java/com/verifiedai/solving/api/PrimarySolveControllerTest.java` | Lookup auth/owner/no-job/stale/iş üretmeme testleri. |
| `docs/verified_ai_learning_platform_docs_v4/00_MASTER_INDEX.md` | Yeni UI sprint kayıtlarının keşif bağlantıları; RM korunur. |
| `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md` | Yeni/değişen doküman byte/satır envanteri. |
| `docs/verified_ai_learning_platform_docs_v4/quality/65_CANONICAL_DOCUMENTATION_TREE_AND_SOURCE_OF_TRUTH_MAP.md` | İki UI dokümanının canonical tree kaydı. |
| `docs/verified_ai_learning_platform_docs_v4/quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md` | Unverified iOS journey için owner evidence bekleyen izlenebilirlik kaydı. |
| `docs/verified_ai_learning_platform_docs_v4/roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md` | UI implementation ile üretim/gerçek-model kabulünün ayrımı. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.4_UI_IMPLEMENTATION_MAP.md` | Ürün yolu, sahiplik, API/state/recovery/trust/erişilebilirlik haritası. |
| `docs/verified_ai_learning_platform_docs_v4/sprints/phase05_ai_solving_and_verification_core/SPRINT_5.4_UI_EXECUTION_REPORT.md` | Baseline, dosya envanteri, A–F owner komutları ve kabul sınırları. |

## H. TEST CODE / EVIDENCE

Swift domain/API/lifecycle/recovery/math testleri, mevcut history testine navigation testi,
PrimarySolveUITests ve backend controller lookup testleri yazıldı.
Derleme, XCTest, Maven, validator, lint, docs-check, UI automation/screenshot, backend/Docker veya
inference asistan tarafından çalıştırılmadı. Kaynak/owner kanıtı okuma ve dosya/metadata yazımı yapıldı.

Owner'ın 2026-10-27 01:58:09'da tamamlanan Gate A çıktısı: **TEST SUCCEEDED**.
iPhone 16 Pro / iOS 18.4 simulator üzerinde Swift uygulama, unit ve UI test target'ları derlendi;
yalnız seçili unit/contract testleri yürütüldü. Sonuçlar:

| Test sınıfı | Geçen test |
| --- | ---: |
| PrimarySolveContractTests | 6 |
| PrimarySolveViewModelTests | 8 |
| SolveRecoveryStoreTests | 1 |
| MathPresentationTests | 5 |
| ProblemHistoryViewModelTests | 6 |
| Toplam | 26 |

Kanıt: owner'ın paylaştığı `e38ac9ff-50b2-4872-a251-54ccd3919699/Yapıştırılan metin.txt` terminal çıktısı;
sonuç paketi `.generated/sprint54-ui/gate-a-01.xcresult` (paket asistan tarafından açılmadı).
AppIntents metadata extraction uyarıları başarılı derlemeyi engellemedi. Kod düzeltmesi gerekmedi.
Gate D görsel/erişilebilirlik kabulü kısmi; Gate E iOS regresyonu geçti, kalan depo kontrolleri bekliyor.
Gate A başarısı tam sprint kabulü, gerçek model kabulü veya matematiksel doğrulama değildir.

Owner'ın 2026-10-27 02:04:17'de tamamlanan Gate B çıktısı: **TEST FAILED**, 7 testten 6 PASS.
Tek başarısız test: `PrimarySolveUITests.testRelaunchRecoversJobWithoutNewStart()`.
Kanıt: `7ecc0720-e717-4f62-a633-f9d83dc9b092/Yapıştırılan metin.txt` ve
`.generated/sprint54-ui/gate-b-01.xcresult`. Mevcut paketin failure/activity kayıtları ve önceden
kaydedilmiş metin UI ağacı/tap verisi `xcresulttool get/export object --legacy` ile okundu;
yeni test, UI otomasyonu veya screenshot üretilmedi.

İlk launch ve çözüm üretimi geçti. İkinci launch sonrası history satırına tap atıldı, ancak
`history.solve` bulunamadı (testin o çalıştırmadaki satır 16'sı); satır 24'te iki ikincil hata oluştu.
Recovery sonucu kontrolüne henüz ulaşılmadığından bu çıktı veri kaybı kanıtı değildir.
Kayıtlı tap `(201, 756.83)`, history satırının `(40, 722, 322, 69.7)` çerçevesindeki metinler arası
boş merkeze düşüyor; ekranda reconnect hata/başarı mesajı yok. Bu bulgulara yönelik düzeltme:
`ProblemHistoryRow` plain button label'ına `.contentShape(Rectangle())` eklenerek tüm satır
dokunma alanına dahil edildi. UI testlerine `continueAfterFailure = false` eklenerek ilk hatadan
sonra eksik kontrole swipe/tap yapılması önlendi. Timeout veya kabul assertion'ları gevşetilmedi.
Owner, odaklı tekrar için `.generated/sprint54-ui/gate-b-relaunch-02.xcresult` yolunu ve
**TEST SUCCEEDED** sonucunu paylaştı. Relaunch navigasyon düzeltmesi owner çalıştırmasıyla doğrulandı.
Gate B kabulü ilk çalıştırmadaki 6 PASS ve düzeltme sonrası tek relaunch testi PASS kanıtlarının
birleşimine dayanıyordu. Sonraki Gate E tam iOS çalıştırması da PASS verdi; böylece UI suite dahil
tam iOS regresyonu için ayrıca düzeltme sonrası owner kanıtı oluştu.
Yeni sonuç paketi asistan tarafından açılmadı; paylaşılan terminal özeti kanıt olarak kaydedildi.

Owner'ın 2026-10-27 02:10:27 +03:00'da tamamlanan Gate C Maven çıktısı: **BUILD SUCCESS**.
`PrimarySolveControllerTest,PrimarySolveIntegrationTest,ModularityTest` odaklı komutu için paylaşılan
toplam: **Tests run: 22, Failures: 0, Errors: 0, Skipped: 0**, süre 15.513 s.
Paylaşılan kesitte `PrimarySolveControllerTest`: 3 test, 0 failure/error/skipped.
Kanıt, bu sohbette owner'ın paylaştığı terminal özetidir; Maven asistan tarafından çalıştırılmadı.
Gate C backend derleme/test alt adımı geçti. Owner, repo kökünde çalıştırdığı `make contracts-check`
komutunun `contract check passed` çıktısını da bu sohbette paylaştı. Gate C tamamlandı;
Maven veya contracts-check için şu aşamada tekrar gerekmez.

Gate D owner kanıtı: simulator bootstatus `Finished`, install sonrası başarılı launch
`com.verifiedai.learning: 36664`. Owner'ın paylaştığı `Ekran Resmi 2026-10-27 02.15.20.png`
ve `Ekran Resmi 2026-10-27 02.15.25.png` doğrudan incelendi; asistan yeni screenshot almadı.
İlk görsel iPhone 16 Pro / iOS 18.4 simulator'ında Türkçe sonuç ekranını, ikincisi kaydırılmış
çözüm adımlarını ve alt kontrolleri gösteriyor.

| Kontrol | Owner görsel kanıtı |
| --- | --- |
| Fixture disclosure | Görünür: geliştirme örneği, gerçek model çıkarımı olmadığı açık. |
| Unverified disclosure | Görünür: bağımsız matematiksel doğrulama yapılmadığı yazıyor. |
| Önerilen cevap ve adımlar | `x = 1`; Adım 1 `x = (2 − 1)`, Adım 2 `x = 1`; sıralı ve okunur. |
| Alt kontroller | Üretim bilgileri kapalı bölümü ve Durumu yenile düğmesi görünür. |
| Görünen alanların yerleşimi | Bu boyutta belirgin yatay taşma veya metin kesilmesi görülmedi. |
| Oturuma dönüş | Owner: geçti; aynı çözüm yeniden açılıyor. |
| İngilizce ve en büyük yazı | Owner: geçti; metin ve düğmeler okunuyor. |
| VoiceOver ve Reduce Motion | Owner: geçti; okuma sırası ve gezinmede sorun yok. |
| Kalan manuel kabul | Diğer senaryolar, offline, küçük ekran ve hesap temizliği henüz ayrı owner sonucu olarak raporlanmadı. |

Owner son üç maddelik manuel kontrol listesine “her biri geçti problem yok” yanıtını verdi.
Dil/yazı ve erişilebilirlik kabulü bu beyana dayanır; ekranlardan çıkarılmadı. Gate D'nin genişletilmiş
matrisi tam PASS olarak işaretlenmedi. Ek kod düzeltmesi gerekmedi; odaklı kapılar ve bildirilen
manuel kontroller geçtiği için Gate E iOS regresyon alt adımına geçilir. Kalan manuel kanıtlar
tam sprint kabulünden önce tamamlanmalıdır.

Gate E iOS owner kanıtı: `make test-ios` çıktısı **TEST SUCCEEDED**, tamamlanma
2026-10-27 02:21:12 +03:00; test aşaması 167.331 s.
Sonuç paketi `.generated/DerivedData/Logs/Test/Test-VerifiedAI-2026.10.27_02-18-19-+0300.xcresult`.
Asistan mevcut paketin yalnız kök özetini `xcresulttool get object --legacy` ile okudu:
`testsCount = 111`, issue özeti boş. Test/derleme yeniden çalıştırılmadı.
Bu kanıt full iOS suite içindir; API/verifier tam regresyon kanıtı sonraki owner çalıştırmasında alındı.

Owner'ın `make -o test-ios -o contracts-check check` denemesi `doctor` aşamasında durdu:
toolchain/Java 21/Compose kontrolleri geçti; repo hygiene dört `.DS_Store` nedeniyle başarısız oldu.
Sonraki lint/docs/secrets/API/verifier hedefleri bu denemede çalışmış sayılmaz.
`git ls-files --` ile dört yolun da untracked olduğu ve `.gitignore` içinde `.DS_Store` bulunduğu
görüldü. Asistan yalnız bildirilen dört regular Finder metadata dosyasını kaldırdı:
`.DS_Store`, `docs/.DS_Store`, `docs/verified_ai_learning_platform_docs_v4/.DS_Store`,
`docs/verified_ai_learning_platform_docs_v4/sprints/.DS_Store`. Ürün kodu, doctor ve ignore kuralı
değiştirilmedi. Temizlik sonrası doctor veya başka kontrol asistan tarafından çalıştırılmadı;
sonraki owner denemesi aşağıda kayıtlıdır.

Owner'ın sonraki `make -o test-ios -o contracts-check check` çıktısında doctor başarılı,
lint `All checks passed!` / `lint checks completed`, docs `documentation check passed: 286 Markdown files`.
Secret scan tarama başlamadan `ripgrep (rg) is required on PATH` nedeniyle exit 2 ile durdu;
bu çıktı secret bulunduğu anlamına gelmez. API/verifier bu denemede henüz çalışmadı.
Mevcut executable `/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path/rg`
yerel ortamda bulundu. Yeni kurulum veya secret-scan değişikliği yapılmadı. Owner terminali için
bu dizini PATH'e ekleyen devam komutu hazırlandı. Doctor/lint/iOS/contracts tekrar atlanır;
bu kanıt kaydıyla dokümanlar değiştiği için docs-check tekrar açık tutulur.

Owner, PATH düzeltmesi sonrası devam komutunun sonunu paylaştı:
API **Tests run: 391, Failures: 0, Errors: 0, Skipped: 2 — BUILD SUCCESS**;
süre 36.702 s, tamamlanma 2026-10-27 02:26:21 +03:00. Böylece 389 test geçti, 2 test gated skip.
Mevcut Surefire XML kayıtları asistan tarafından yalnız okundu; iki skip:
`QwenRealModelEvaluationTest.durableSmoke` ve `.canonicalBenchmark`.
İkisinin de nedeni `Environment variable [QWEN_OWNER_RUN] does not exist`.
Bu testler gerçek model kapısına aittir; fixture/regresyon başarısı bu testleri geçti saymaz.

Verifier: **20 passed, 1 warning in 0.53s**. Uyarı FastAPI/Starlette TestClient'ın httpx kullanımına
ilişkin deprecation bildirimi; test başarısızlığı değil. Bu sonuç için bağımlılık değişikliği yapılmadı.
Son kesitte docs/secret-scan satırları yeniden paylaşılmadı; bilinen sıralı Makefile komutunun API/verifier
aşamasına ulaşıp hata vermeden dönmesinden önceki docs/secret-scan hedeflerinin başarıyla tamamlandığı
çıkarılır. Doctor/lint ve contracts için önceki owner PASS kanıtları kullanıldı. Gate E bileşik owner
kanıtıyla tamamlandı; asistan test, build, validator veya secret scan çalıştırmadı.

## I. OWNER COMMANDS

Her kapıyı sırayla çalıştırın; ilk anlamlı hatada durup komut, özet ve ilk error/stack trace'i paylaşın.
Credential, öğrenci verisi veya tam env/config çıktısı paylaşmayın. Test komutları bu çalışma ağacını derler;
RM'nin bekleyen backend dosyaları compile/dependency hatası oluşturursa ayrı RM bağımlılığı olarak kaydedilir.

### Gate A — Odaklı Swift unit/contract testleri

**PASS_OWNER_EXECUTED (2026-10-27): 26/26.** Aşağıdaki komut kanıt kaydıdır; şu aşamada tekrar gerekmez.

Önkoşul: Xcode, iOS Simulator iPhone 16 Pro runtime. GPU/backend/Docker gerekmez.
Proje ve scheme mevcut Makefile ile aynıdır. Sonuç yolu her yeni denemede farklı olmalıdır.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
mkdir -p .generated/sprint54-ui
xcodebuild test \
  -project apps/ios/VerifiedAI.xcodeproj \
  -scheme VerifiedAI \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -derivedDataPath .generated/DerivedData \
  -resultBundlePath .generated/sprint54-ui/gate-a-01.xcresult \
  -only-testing:VerifiedAITests/PrimarySolveContractTests \
  -only-testing:VerifiedAITests/PrimarySolveViewModelTests \
  -only-testing:VerifiedAITests/SolveRecoveryStoreTests \
  -only-testing:VerifiedAITests/MathPresentationTests \
  -only-testing:VerifiedAITests/ProblemHistoryViewModelTests
```

Paylaşın: build/test summary ve ilk compiler/test hatası. Bu kapı cihaz görsel kalitesini kanıtlamaz.

### Gate B — Deterministic iOS UI

**PASS_OWNER_EXECUTED:** ilk çalıştırma 6 PASS / 1 FAIL, odaklı düzeltme tekrarı PASS.
Aşağıdaki suite komutu tarihsel kayıttır ve `gate-b-01` tekrar kullanılmaz.
A geçtikten sonra; test launch args açık sentetik auth/history/solve kullanır. Gerçek model çağrılmaz.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
xcodebuild test \
  -project apps/ios/VerifiedAI.xcodeproj \
  -scheme VerifiedAI \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -derivedDataPath .generated/DerivedData \
  -resultBundlePath .generated/sprint54-ui/gate-b-01.xcresult \
  -only-testing:VerifiedAIUITests/PrimarySolveUITests
```

Tamamlanan odaklı tekrar: yalnız relaunch testi, **TEST SUCCEEDED**. Aşağıdaki komut tarihsel kayıttır;
`gate-b-relaunch-02` tekrar kullanılmaz ve şu aşamada test tekrarı gerekmez.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
xcodebuild test \
  -project apps/ios/VerifiedAI.xcodeproj \
  -scheme VerifiedAI \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -derivedDataPath .generated/DerivedData \
  -resultBundlePath .generated/sprint54-ui/gate-b-relaunch-02.xcresult \
  -only-testing:VerifiedAIUITests/PrimarySolveUITests/testRelaunchRecoversJobWithoutNewStart
```

Paylaşın: UI test özeti, ilk başarısız adım; gerekirse sahibi ekran görüntüsü. VoiceOver sırası ve Reduce Motion
manuel D kapısında değerlendirilir. Fixture kabulü gerçek backend/inference E2E kanıtı değildir.

### Gate C — Backend HTTP/auth/contract

**Backend PASS_OWNER_EXECUTED:** 22/22 test, 0 failure/error/skipped; BUILD SUCCESS (2026-10-27 02:10:27 +03:00).
Owner'ın ayrı `make contracts-check` çıktısı da **contract check passed**. Gate C tamamlandı;
aşağıdaki komutlar kanıt kaydıdır ve şu aşamada tekrar gerekmez.

Önkoşul: Java 21/Maven, sahibi tarafından açık Docker Desktop ve Testcontainers PostgreSQL erişimi.
Aşağıdaki testler backend'i test süreci içinde açar; GPU veya gerçek Qwen yoktur. Asistan Docker başlatmaz.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
unset QWEN_OWNER_RUN
mvn -Dtest=PrimarySolveControllerTest,PrimarySolveIntegrationTest,ModularityTest test
```

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
make contracts-check
```

Beklenen: anonymous/foreign owner reddi, job:null, mevcut job discovery, STALE projection ve
lookup'ın iş/usage üretmemesi. Test önkoşul hatasını ürün hatasından ayrı bildirin.

### Gate D — Simulator ürün demosu ve erişilebilirlik

Owner'ın ilk demo denemesinde install/terminate/launch komutları `No devices are booted` döndürdü.
Uygulama açılmadığından bu deneme ürün/görsel kabul kanıtı değildir; Gate D bekliyor.
Hiç simulator açık değilse, önce Gate A/B owner loglarındaki iPhone 16 Pro'yu başlatın:

```sh
xcrun simctl boot 56E87D46-F64B-4AC7-AB87-1D94F5C9F3D0
open -a Simulator
xcrun simctl bootstatus 56E87D46-F64B-4AC7-AB87-1D94F5C9F3D0 -b
```

Bu UDID önceki owner çıktısındaki iPhone 16 Pro / iOS 18.4 hedefidir. Hazır olduktan sonra
aşağıdaki install/launch adımlarına geçin. Asistan simulator veya test çalıştırmadı.

Simulator uygulamasında hedef iPhone 16 Pro açık/booted olmalıdır. UI testleri ayrı clone üzerinde
çalışabileceği için mevcut build ürününü demo simulator'ına yükleyin; yeniden build gerekmez:

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
xcrun simctl install booted .generated/DerivedData/Build/Products/Debug-iphonesimulator/VerifiedAI.app
```

Birden fazla simulator açıksa yalnız demo hedefini açık bırakın veya `booted` yerine onun UDID'sini kullanın.
App testten açık kaldıysa önce ayrı komutla kapatın:

```sh
xcrun simctl terminate booted com.verifiedai.learning
```

App zaten kapalıysa terminate hatası bir ürün/test hatası değildir. Sonra:

```sh
xcrun simctl launch booted com.verifiedai.learning \
  --ui-testing --ui-testing-authenticated --solve-fixture --solve-reset \
  --solve-scenario=GENERATED_UNVERIFIED
```

1. Mevcut development auth seam ile Home açılır; sentetik fixture etiketi görülür.
2. Problem History'deki örnek session'ı açın; Solve Problem'a basın.
3. Mevcut iş yoksa Start, sonra gerçek QUEUED/RUNNING metinlerini izleyin; yüzde gösterilmez.
4. Önerilen x = 1, iki adım, unverified uyarısı ve isteğe bağlı metadata'yı inceleyin.
5. Back to Session → tekrar Solve Problem: aynı iş, yeni Start yok.
6. Uygulamayı terminate edin; aşağıdaki reset'siz komutla açın ve aynı session'a dönün:

```sh
xcrun simctl launch booted com.verifiedai.learning \
  --ui-testing --ui-testing-authenticated --solve-fixture \
  --solve-scenario=GENERATED_UNVERIFIED
```

7. Ayrı temiz senaryo çalıştırmaları için terminate sonrası ilk launch komutunda state'i
`REVIEW_REQUIRED`, `RETRYABLE_FAILURE`, `POLICY_BLOCKED`, `STALE`, `CANCELLED`, `OUTCOME_UNKNOWN`,
`UNSUPPORTED`, `TERMINAL_FAILURE` veya `MALFORMED` yapın. Unknown'da retry olmamalı; stale/error'da cevap görünmemeli.
8. Simulator ağını sahibi kontrolünde kesip Refresh/Start davranışını inceleyin; ağ düzelince Refresh yapın.
9. İngilizce/Türkçe, en büyük erişilebilir metin, VoiceOver step sırası ve spoken power/fraction, Reduce Motion
ve küçük ekranı kontrol edin. Long prose/expressions dikey genişlemeli; kontrol metinleri kesilmemeli.
10. Sign-out → yeniden fixture giriş: local pending marker temizliği, başka gerçek hesaba fixture veri taşınmaması.

Gerçek capture/import devam yolu bu fixture solve demosunda taklit edilmez. Mevcut capture regresyon testleri
E kapısındadır; gerçek dev server ile capture → classification → aynı session detail yolu ayrıca owner demo ister.
Development backend UI için Xcode Run arguments yalnız `--enable-primary-solve`; fixture/auth bypass argümanlarını
kaldırın ve mevcut gerçek dev kimliğinizle giriş yapın. Backend disabled ise anlamlı unavailable ekranı beklenir.
Backend'te fixture solver yalnız mevcut test configuration'dadır; normal backend'de sahte provider otomatik açılmaz.

### Gate E — Tam regresyon

**PASS_OWNER_EXECUTED.** Aşağıdaki devam komutu tamamlandı; yeni hata/değişiklik olmadan tekrar gerekmez.

Odaklı kapılar ve owner'ın bildirdiği temel manuel kontroller geçti; `make test-ios` de **PASS** (111 test).
Gate D'nin kalan genişletilmiş manuel matrisi ayrı olarak açık tutulur; regresyon bu kanıtın yerine geçmez.

A–D kanıtları incelendikten sonra. Docker, Java, verifier ortamı ve simulator önkoşulları gerekir.
Kalan `make check` hedefleri mevcut MinIO test image build'i dahil kontrolleri çalıştırabilir; yalnız sahibi yürütür.
Kanıtı alınmış doctor/lint/iOS/contracts hedefleri `-o` ile bu çalıştırmada atlanır.
Güncellenmiş belgeler için docs-check, ardından secret scan ve API/verifier testleri çalışır.
Makefile değiştirilmez; PATH eklemesi yalnız mevcut owner shell oturumu içindir.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
unset QWEN_OWNER_RUN
export PATH="/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path:$PATH"
make -o doctor -o lint -o test-ios -o contracts-check check
```

Tek tek özetleri paylaşın. Aynı tam suite'i değişiklik/hata gerektirmedikçe tekrar çalıştırmayın.

### Gate F — Opsiyonel gerçek Qwen E2E

**BLOCKED_REAL_MODEL_E2E**. RM adapter testleri, exact endpoint/runtime, donanım, privacy ve harcama onayı
ile sahibin açık inference izni olmadan bu kapı çalıştırılmaz. Bu rapor canlı çağrı komutu vermez.
RM owner runbook sonuçları kabul edilince gerçek backend UI gate'iyle ayrı E2E kanıtı toplanır.
Fixture tabanlı UI kabulü bu kapının açık olmasına bağlı değildir.

## J. BLOCKERS / SINIRLAR

- Swift derlemesi, Gate A unit/contract testleri ve Gate B'nin ilk 6 testi + odaklı relaunch tekrarı
  owner tarafından geçti. Gate C backend derleme/test de 22/22 ve contracts-check geçti;
  Gate D Türkçe sonuç ekranı görsel kanıtı ve oturuma dönüş/EN/en büyük yazı/VoiceOver/Reduce Motion
  owner PASS beyanı alındı. Gate E tamamlandı: iOS 111 test başarılı, API 389 PASS / 2 gated SKIP,
  verifier 20 PASS. Gate D genişletilmiş manuel matris bekleniyor.
- RM'nin mevcut model kalite/quantization/kapasite/TCO engelleri aynen geçerli.
- Source restriction metinleri response'ta yok; ID'ler öğrenciye açıklama gibi gösterilmiyor.
- Render v1 plain text, explicit parentheses; tipografik stacked fractions ve gelişmiş notation sonraki iş.
- STALE ekranından otomatik yeni canonical solve yok; kaynak review'a yönlendirme var.
- Fixture auth geliştime seam'idir; üretim authentication/entitlement kabulü sayılmaz.
- İstemci görünürlük gate'i üretim capability veya bağımsız doğrulama onayı değildir.

## K. NEXT ACTION

Geçmiş otomatik test ve kontrolleri tekrarlamayın. Gate D'nin kalan diğer durum/hata senaryoları,
offline, küçük ekran ve hesap temizliği kontrolleri için owner PASS/FAIL/NOT_CHECKED sonucu beklenir.
Gerçek backend capture → classification → aynı session detail demosu da fixture kanıtından ayrı tutulur.
Gate D'nin tamamı için owner kanıtı gelmeden tam UI VERIFIED_PASS verilmez.
UI acceptance = AUTOMATED_REGRESSION_PASS / MANUAL_ACCEPTANCE_PARTIAL;
RM acceptance = REAL_MODEL_ACCEPTANCE_BLOCKED; mathematical verification = NOT_PERFORMED.

## L. FINAL REPORT / GIT HANDOFF — 2026-10-27

Rapor mevcut owner kanıtlarıyla tamamlandı; kalan maddeler başarılı kabul edilmeden açık kayıt olarak devredildi.
Otomatik sonuçlar: iOS 111 test başarılı; API 389 PASS / 2 gerçek-model gated SKIP;
verifier 20 PASS. Doctor/lint/docs/contracts geçti; secret-scan geçişi sıralı make devamından çıkarıldı.
Görsel TR sonuç ekranı ve owner'ın oturuma dönüş/EN/en büyük yazı/VoiceOver/Reduce Motion beyanı kayıtlıdır.
Kalan manuel matris ve gerçek backend capture demosu için bu sohbette tam owner kabul kanıtı yoktur.
Gerçek Qwen kalite/kapasite/TCO ve üretim onayı verilmedi.

Kullanıcı isteğiyle mevcut `feature/sprint-5-2-prompt-registry-governance` dalındaki Sprint 5.4-RM ve
5.4-UI kaynak, fixture, test ve doküman değişiklikleri birlikte commit/push kapsamına alındı.
Hedef mevcut `origin` deposu `https://github.com/coskunburak/verified_ai.git` ve aynı uzak daldır.
Generated test paketleri, yerel env/credential dosyaları ve model ağırlıkları kapsam dışıdır.
Commit kimliği Git geçmişinde tutulur; rapora kendi commit hash'i yazılmaz.
Son rapor düzenlemeleri yalnız kanıt/durum/manifest metadatasıdır; asistan yeni test/validator çalıştırmadı.
