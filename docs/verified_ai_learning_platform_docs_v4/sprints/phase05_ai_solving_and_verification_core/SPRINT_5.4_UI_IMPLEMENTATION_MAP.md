# Sprint 5.4-UI — Uygulama haritası

Durum: **SPRINT_5.4_UI_IMPLEMENTED — AUTOMATED_REGRESSION_PASS — MANUAL_ACCEPTANCE_PARTIAL**.
Owner'ın 2026-10-27 Gate A çalıştırmasında Swift derlemesi ve 26 unit/contract testi geçti.
Gate B'nin ilk 6 UI testi ve history satırını tamamen dokunulabilir yapan düzeltme sonrası
odaklı relaunch testi owner tarafından geçti (`gate-b-relaunch-02.xcresult`: TEST SUCCEEDED).
Gate C backend testleri owner tarafından 22/22 geçti (BUILD SUCCESS, 2026-10-27 02:10:27 +03:00);
ayrı `make contracts-check` sonucu da `contract check passed`. Gate C tamamlandı.
Gate D: owner'ın Türkçe sonuç ekranı görsellerinde cevap, iki adım, fixture/unverified uyarıları
ve alt kontroller okunur. Owner oturuma dönüş, EN/en büyük yazı ve VoiceOver/Reduce Motion
kontrollerinin her birini geçti olarak bildirdi. Diğer senaryolar/offline/küçük ekran/hesap temizliği
matrisi açık tutulur. Gate E `make test-ios`: owner PASS, 111 test (2026-10-27 02:21:12 +03:00).
Gate E kalan depo kontrolleri de owner tarafından tamamlandı: API 389 PASS / 2 gated SKIP,
verifier 20 PASS. İki skip gerçek Qwen smoke/benchmark testidir (`QWEN_OWNER_RUN` yok).
Otomatik kontroller tekrarlanmaz; Gate D genişletilmiş manuel matris ve gerçek model engeli açık tutulur.
Asistan derleme, test, sözleşme kontrolü, ekran görüntüsü veya UI otomasyonu çalıştırmadı.

## Baseline ve korunan çalışma

Branch `feature/sprint-5-2-prompt-registry-governance`, HEAD
`f3cec8ab05bf182137fcfd3f80c41f56b2a5bddd`. UI başlangıcında RM'nin 42 dosyası dirty idi.
Remote referans `403e0ba3ce2361dce5bbedecd0ccb2fac3cc508b` yerel HEAD değildir; fetch/branch değişimi yok.
RM'nin adapter/router/service/prompt/budget dosyaları bu UI paketinde değiştirilmedi.
Ortak master index, manifest, canonical tree ve coverage belgesine yalnız UI kayıtları eklendi.
Önceki A–E kanıtları yeni RM/UI değişikliklerinin testi sayılmaz.

## Sahiplik ve ürün yolculuğu

`apps/ios/VerifiedAI/Features/ProblemSolving` tipli candidate sunumu ve çözüm yaşam döngüsünü sahiplenir.
`APIClient`, mevcut auth interceptor, `AppDependencies`, Observation/MainActor ve tasarım tokenları kullanılır.
Yeni bağımlılık, ikinci solver, istemci verifier, ödeme sağlayıcısı veya doğrudan vLLM çağrısı yoktur.

Mevcut giriş → Home/ProblemHistory → sahip olunan session detail → `READY_FOR_SOLVE` → Solve Problem
→ mevcut işi salt okunur kurtarma → iş yoksa açık Start → QUEUED/RUNNING → unverified candidate.
Çekim/classification tamamlanınca aynı session kimliğiyle geçmiş detayına dönülür; server detail uygunluk
kararını verir. Ayrı capture akışı veya yerel canonical problem oluşturulmaz.
History `readyForSolve` artık navigation intent üretir. Result ekranı aynı NavigationStack'tedir.
Kapatma yalnız yerel takibi durdurur. Review/stale yönlendirmesi mevcut ProblemReviewView'u kullanır;
fixture modunda gerçek parse servisine gidilmez, session detayına dönülür.

## API ve tek backend uzantısı

| İşlem | Sözleşme | İstemci davranışı |
| --- | --- | --- |
| Başlat | POST `/api/v1/problem-sessions/{sessionId}/solves` | Önce kalıcı anahtar, sonra explicit POST |
| Oku | GET `/api/v1/problem-sessions/{sessionId}/solves/{jobId}` | Sadece mevcut iş, eşleşen jobId kontrolü |
| Yeniden dene | POST `/api/v1/problem-sessions/{sessionId}/solves/{jobId}/retry` | RETRYABLE_FAILURE ve attempt < 3; yeni anahtar; sunucu son otorite |
| İptal | POST `/api/v1/problem-sessions/{sessionId}/solves/{jobId}/cancel` | Yalnız açık kullanıcı aksiyonu; dış ücret iptali garantisi yok |
| Kurtar | GET `/api/v1/problem-sessions/{sessionId}/solves/current` | Yeni salt okunur endpoint; `{job:null}` veya snapshot |

Consumer gap: eski API yalnız bilinen jobId'yi okuyordu. Yeniden kurulum/diğer cihazdan session'a dönüş
mevcut işi keşfedemiyor, yeniden Start ise SOLVE_STATE_CONFLICT oluyordu.
`CurrentSolveQuery` önce `SolveSourceAccess.lockOwner`, sonra user+session filtreli son işi bulur.
Mevcut `PrimarySolveService.get` yeniden sahiplik ve seçili kaynak kontrolü yapar; tarihsel işi STALE
olarak projekte eder. Query çalışma başlatmaz, aday değiştirmez, usage/entitlement debit yapmaz.
PrimarySolveService veya RM dosyası değiştirilmedi; schema/migration yok. Null alanı açıkça serialize edilir.
OpenAPI ve mevcut authenticated controller integration testine owner/foreign/anonymous/no-job/stale kapsamı eklendi.
Lookup bir anlık snapshot'tır; eşzamanlı değişiklik bir sonraki GET'te uzlaştırılır.

## Yaşam döngüsü ve hata politikası

| Sunucu durumu | Ekran |
| --- | --- |
| QUEUED / RUNNING | Gerçek durum metni, yüzde veya hayali doğrulama aşaması olmadan progress |
| GENERATED_UNVERIFIED | Önerilen cevap, yöntem, adımlar ve zorunlu bağımsız doğrulama yapılmadı uyarısı |
| REVIEW_REQUIRED | Ayrı durum, görünür ambiguity/unresolved issues ve problem review yolu |
| RETRYABLE_FAILURE | attempt < 3 ise kullanıcı kontrollü retry; backend yeniden onaylar |
| TERMINAL_FAILURE / POLICY_BLOCKED / UNSUPPORTED | Açıklama, spinner yok, uygun dönüş yolu |
| STALE | Önceki candidate saklansa bile cevap gizlenir; source review |
| CANCELLED | Açık iptal durumu; provider billing durdu iddiası yok |
| OUTCOME_UNKNOWN | Salt okunur refresh; otomatik yeni inference veya retry yok |

SOLVE_DISABLED, SOLVE_SOURCE_NOT_READY, SOLVE_STATE_CONFLICT, SOLVE_RETRY_LIMIT, PROBLEM_UNSUPPORTED,
IDEMPOTENCY_KEY_REUSED, auth, entitlement, decoding ve transport için yerelleştirilmiş sınıflar vardır.
Sunucunun ham title/trace/endpoint bilgisi ekrana taşınmaz. Paylaşılan NetworkError transport türlerini
tek sınıfa indirdiği için timeout ile diğer transport kesintileri ortak belirsiz-sonuç mesajı kullanır.
Retryability yalnız HTTP durumundan çıkarılmaz. GET mevcut auth-refresh politikasını kullanır;
POST otomatik auth replay yapmaz.

## Polling, idempotency ve güvenlik

Tek sahipli cancellable Task: request ve GET polling örtüşmez. GET aralıkları 2, 3, 4, sonra 5 saniyedir;
bir foreground/explicit refresh döngüsü en çok 15 GET yapar. Sonrasında manuel refresh istenir.
Yalnız QUEUED/RUNNING takip edilir; terminalde durur. Ekran kapanması/background task'ı iptal eder,
önbellekteki candidate görünümünü temizler. Foreground önce server current GET yapar.
NetworkMonitor offline ise yeni POST yoktur; polling durur. Offline düzelince foreground veya Refresh gerekir.
Polling sırasında uyku aralarında Cancel/Refresh mümkündür; yeni aksiyon önce eski task'ı iptal eder.
Generation fence geç gelen yanıtların görünümü değiştirmesini engeller.

Start/retry anahtarı POST'tan önce atomik, file-protected ve backup dışı Application Support kaydına yazılır.
Yalnız user/session namespace ve operation/job UUID'leri tutulur; soru, image veya candidate tutulmaz.
128 unresolved marker sınırı aşılırsa kayıt silip yeni işlem yapmak yerine işlem durur.
Belirsiz POST için yeni key üretilmez; kullanıcı aynı isteği kurtarabilir. Yeni explicit retry ayrı key alır.
Server current sonucu mevcut işi doğrular; local marker sahiplik/entitlement otoritesi değildir.
Fixture/live kayıtları ayrıdır. Sign-out, account deletion/expiry/account replacement kayıtları temizler;
accountGeneration fence geçmişte devam eden eski hesap yanıtlarının yeni UI/cache'e yazılmasını engeller.
Local dosya temizliği başarısız olsa da namespace başka hesaba okunmaz; yalnız payload'sız hata olayı loglanır.

## Matematik, belirsizlik ve erişilebilirlik

İzinli number/variable/negation/binary/function AST; exact numeric string, equality/inequality.
Decode derinliği sınırlı; statement başına 120 düğüm, 32 depth; 24 sıralı step ve önceki-step dependency kontrolü.
Bilinmeyen durum, eksik alan, bilinmeyen AST, yanlış lineage veya bozuk sıra safe error'dur.
Ayrı verifier yoktur; bu kontroller matematiksel doğruluk kanıtı değildir.

Plain SwiftUI Text kullanılır: Markdown/link/HTML/WebKit/script çalıştırılmaz. Taban ve üs açıkça
parantezlenir; `(-x)^2` ile `-(x^2)` ayrılır. Fraction iki boyutlu dizilmek yerine tam parantezli `/` ile,
power `^` ile sunulur. Bu bilinçli v1 sınırıdır; yeni bir render bağımlılığı yoktur.
Source assumption/restriction yalnız ID olarak geliyorsa metin uydurulmaz; açıklayıcı sınırlılık mesajı gösterilir.
Provider/model/prompt metadata isteğe bağlı detayda ve doğruluk kanıtı olmadığı notuyla sunulur.

Türkçe/İngilizce string catalog kayıtları, sembollerin konuşma karşılıkları, doğal step sırası,
Dynamic Type, dikey genişleyen metinler, 44pt kontroller, durum/hata VoiceOver duyuruları vardır.
Özel animasyon kullanılmaz; Reduce Motion için yeni hareket yoktur. Görsel/VoiceOver QA henüz yapılmadı.

## Gate ve fixture politikası

DEBUG + development + `--enable-primary-solve`: gerçek authenticated backend client; yalnız UI görünürlük gate'i.
Release/staging UI gate kapalıdır. Sunucu capability/model/entitlement otoritesi değişmez.
DEBUG + development + `--ui-testing-authenticated --solve-fixture`: açık etiketli sentetik history ve solve double.
Normal backend hatasında fixture'a düşülmez. Fixture gerçek Qwen veya server pipeline kalitesinin kanıtı değildir.
Fixture örnekleri queued/running/generated/review/retry/policy/stale/cancelled/unknown/malformed durumlarını destekler.
Senaryo `--solve-scenario=...`, yalnız sentetik test işini temizlemek için `--solve-reset` kullanılır.

## Test haritası ve Phase 6.1 sınırı

- PrimarySolveContractTests: DTO/lifecycle/path/method/key, missing/unknown/trust/lineage/order/error mapping.
- PrimarySolveViewModelTests: start/poll/terminal/bound/offline/dismiss/resume/retry/cancel/unknown/ambiguous/storage.
- SolveRecoveryStoreTests: disk üzerinden relaunch, hesap ve fixture namespace ayrımı, temizleme.
- MathPresentationTests: precedence, negative bases/exponents, exact decimal, fractions/functions/relations, unsafe/deep/wide AST.
- ProblemHistoryViewModelTests: readyForSolve navigation intent ve mevcut workflow regresyonu.
- PrimarySolveUITests: history entry, queued/running, candidate/reopen/relaunch, retry/review/stale/unknown/malformed, TR büyük yazı.
- PrimarySolveControllerTest: gerçek HTTP/auth/PostgreSQL lookup kontratı; GPU yok.

Sınırlılıklar: full verified UX, verification evidence, tutoring, solution sets, diagrams, calculus ve Qwen
kalite kabulü tamamlanmış sayılmaz. STALE ekranda otomatik yeni-source solve açılmaz; kullanıcı source review'a döner.
Yeni canonical için ayrı açık çözüm başlatma UX'i Phase 6.1'e devredilir. API eski işin yerine sessizce yeni iş üretmez.
Gate A owner kanıtı [execution report](SPRINT_5.4_UI_EXECUTION_REPORT.md) içinde kayıtlıdır.
Gate B ve Gate C owner kanıtları da execution report içinde kayıtlıdır.
Gate E otomatik regresyonu geçti; Gate D genişletilmiş manuel matris ve gerçek model Gate F engeli sürer.
