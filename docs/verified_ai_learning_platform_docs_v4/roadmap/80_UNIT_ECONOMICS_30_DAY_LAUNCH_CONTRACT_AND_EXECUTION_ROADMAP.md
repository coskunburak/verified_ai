# 80 — Unit Economics, Commercial Pricing, 30-Day Founder Go/No-Go Contract, and Prioritized Execution Roadmap

- **Strategy & Financial Baseline Date:** 2026-10-31
- **Status:** CANONICAL BUSINESS, UNIT ECONOMICS & EXECUTION ROADMAP (V6)
- **Governing Documents:** `product/05_MONETIZATION_AND_ENTITLEMENTS.md`, `ai/60_AI_UNIT_ECONOMICS_AND_INFERENCE_COST_ENGINEERING.md`, `roadmap/43_PRODUCT_AND_TECHNICAL_ROADMAP.md`, `adr/ADR-008_MANAGED_CLOUD_API_ROUTING_AND_QWEN_4B_DE_PROMOTION.md`, `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md`

---

## 1. Per-Solve Unit Economics Breakdown (Soru Başına Birim Maliyet Analizi)

`ADR-008` ile belirlenen yönetilen bulut API yönlendirme mimarisi (`Gemini 2.5 Flash` + `GPT-4.1-mini` + koşullu `%15` `o4-mini` + `SymPy`) üzerinden **1 tam uçtan uca doğrulanmış soru çözümünün** gerçekçi ağırlıklı ortalama maliyeti (`%15` kanonik önbellek isabeti ve `%5` yeniden deneme payı dahil):

| Aşama (Pipeline Stage) | Model / Motor | Ort. Giriş Token / Görsel | Ort. Çıkış Token (Düşünme Dahil) | Tetiklenme Oranı | Çağrı Başına Maliyet (USD) | Soru Başına Ağırlıklı Maliyet (USD) |
|---|---|---|---|---|---|---|
| **1. Görsel OCR & Normalizasyon (`R1`)** | `Gemini 2.5 Flash` | `1,100` (Görsel + İstem) | `250` | `%85` (`%15` Cache Hit) | `\$0.000955` | `\$0.000812` |
| **2. Birincil Yapısal Çözücü (`R2`)** | `GPT-4.1-mini` | `1,800` | `1,050` | `%85` | `\$0.002400` | `\$0.002040` |
| **3. Deterministik Doğrulayıcı** | `Python / SymPy` (`math-verifier`) | CPU (`~85 ms`) | — | `%100` | `\$0.000010` | `\$0.000010` |
| **4. Koşullu Üst Çözücü (`R3` Escalation)** | `OpenAI o4-mini` | `2,200` | `1,800` | `%15` (Zor soru / Doğrulama reddi) | `\$0.010340` | `\$0.001551` |
| **5. Sokratik İpucu / Hata Teşhisi (`R4`)** | `GPT-4.1-mini` | `1,500` | `500` | `%70` (Etkileşim oranı) | `\$0.001400` | `\$0.000980` |
| **6. Yeniden Deneme / Şema Onarımı** | `GPT-4.1-mini` | — | — | `%5` ek yük | — | `\$0.000270` |
| **7. S3 Görsel Saklama + Veritabanı** | AWS S3 / Cloudflare R2 + Postgres | `220 KB` JPEG | — | `%100` | `\$0.000150` | `\$0.000150` |
| **TOPLAM (1 Doğrulanmış Çözüm + Etkileşim)** | **Hibrit Bulut + SymPy** | — | — | — | — | **`\$0.005813` (~`0.20 TL`)** |

> **Stratejik Kıyaslama:** Türkiye'deki insan eğitmenli rakiplerde (ör. Kunduz) soru başına eğitmen maliyeti ve 15–30 dakikalık bekleme süresi nedeniyle aylık paketler `700–1,300 TL` bandındadır. Verified AI, deterministik SymPy kanıtlı çözümü **4 saniyede** ve soru başına **`~0.20 TL` (`\$0.0058`)** marjinal maliyetle üretir.

---

## 2. Subscription Tiers, Quotas, and Gross Margin Model (Paketler ve Brüt Marj)

Apple App Store **Small Business Program** (`\$1M/yıl` altı gelirde **`%15` komisyon**) baz alınmıştır:

| Paket (Tier) | Türkiye Fiyatı (KDV Dahil) | Küresel (ABD/AB) Fiyatı | Aylık Kotalar (Server-Authoritative) | Ort. Kullanıcı Tüketimi (50 / 130 Çözüm) | Aylık AI + Bulut COGS (USD) | Apple %15 Sonrası Net Gelir (TR / Global) | Brüt Kar Marjı (TR / Global) |
|---|---|---|---|---|---|---|---|
| **FREE (Keşif)** | `0 TL` | `\$0.00` | Günde `3` doğrulanmış çözüm + `1` `Analyze My Work` (El yazısı hata teşhisi) | Ayda ~12 çözüm | `\$0.07 / ay` | `\$0.00` | Pazarlama / Edinme maliyeti (`CAC`) |
| **PRO (YKS / Lise Standart)** | **`199.99 TL / ay`** (`1,499 TL / yıl`) | **`\$9.99 / ay`** (`\$59.99 / yıl`) | Ayda `150` doğrulanmış çözüm + `60` `Analyze My Work` + Hata Pasaportu + İkiz Sorular | Ort. `55` çözüm + `20` hata analizi / ay | **`\$0.46 / ay`** | `~\$4.15` (TR) / `\$8.49` (Global) | **`%88.9` (TR) / `%94.6` (Global)** |
| **PRO+ (Yoğun Sınav & Calculus)** | **`349.99 TL / ay`** (`2,499 TL / yıl`) | **`\$19.99 / ay`** (`\$119.99 / yıl`) | Ayda `400` doğrulanmış çözüm + `150` `Analyze My Work` + Öncelikli `o4-mini` Kalkülüs/AYT rotası | Ort. `140` çözüm + `50` hata analizi / ay | **`\$1.28 / ay`** | `~\$7.25` (TR) / `\$16.99` (Global) | **`%82.3` (TR) / `%92.5` (Global)** |

*Not: Türkiye'deki bir `PRO` abonesi aylık `150` soruluk kotasının tamamını sonuna kadar kullansa bile (`150 × \$0.00581 = \$0.87`), `~\$4.15` net App Store gelirine karşı **`%79` brüt kar marjı** bırakır; zarar etme riski `PostgresAiSpendGuard` ile matematiksel olarak sıfırlanmıştır.*

---

## 3. Scaling Financial Projections (100'den 100,000 Ücretli Aboneye Projeksiyon)

Karma gelir varsayımı: `%70` Türkiye + `%30` Küresel abone karması, ağırlıklı ortalama App Store/KDV sonrası net gelir **`\$6.40 / ücretli abone / ay`**, ücretsiz kullanıcıların ücretliye oranı `8:1`:

| Ücretli Abone Sayısı | Aktif Ücretsiz Kullanıcı | Aylık Toplam Çözüm | Aylık Net Gelir (USD) | Ücretli AI COGS (`\$0.65`/abone) | Ücretsiz AI COGS (`\$0.05`/ücretsiz) | Sabit Altyapı (DB, Redis, S3, Monitoring) | Toplam Aylık Maliyet (USD) | Aylık Net Katkı Karı (USD) | Net Marj (%) |
|---|---|---|---|---|---|---|---|---|---|
| **100** | 800 | `16,000` | `\$640` | `\$65` | `\$40` | `\$65` (Render/Fly/Supabase Pro) | `\$170` | **`+\$470`** | **`%73.4`** |
| **1,000** | 8,000 | `160,000` | `\$6,400` | `\$650` | `\$400` | `\$180` | `\$1,230` | **`+\$5,170`** | **`%80.8`** |
| **10,000** | 80,000 | `1,600,000` | `\$64,000` | `\$6,500` | `\$4,000` | `\$850` | `\$11,350` | **`+\$52,650`** | **`%82.3`** |
| **100,000** | 800,000 | `16,000,000` | `\$640,000` | `\$45,500` *(Faz 13 Hibrit %30 indirim)* | `\$28,000` | `\$6,500` | `\$80,000` | **`+\$560,000`** | **`%87.5`** |

> **Başabaş Noktası (Break-Even Point):** Sabit sunucu ve Apple Developer (`\$99/yıl`) giderlerini karşılamak için yalnızca **18 adet ücretli abone** yeterlidir!

---

## 4. Candid Founder Strategy Audit & The 30-Day Go/No-Go Contract (Kurucu Gerçeklik Sözleşmesi)

### 4.1 Neden Şimdi Bırakmak Stratejik Bir Hatadır?
Kod tabanında 23 Flyway veritabanı şeması (`V001..V023`), Apple Sign-In, StoreKit 2 + Server Notifications V2, S3 görüntü işleme, atomik AI bütçe koruyucusu (`PostgresAiSpendGuard.java`), sıfır `eval` kullanan deterministik SymPy doğrulama servisi (`math-verifier`) ve iOS doğrulama kanıt ekranları (`SolutionCandidate.swift`) **zaten inşa edilmiş ve yerel testlerden geçmiştir**. İşin en yorucu `%70`'lik altyapısı bitmişken, henüz canlı bir bulut AI modelini bağlayıp tek bir gerçek öğrenciye göstermeden bırakmak rasyonel değildir.

### 4.2 Asıl Tehdit Rakipler Değil, "Mimari Erteleme Sendromu"dur
Projenin önündeki en büyük tehlike Google (Photomath) veya OpenAI değil; `sprints/00_SPRINT_MASTER_PLAN.md` içindeki 13 fazı (120+ sprint) tek başına canlıya çıkmadan bitirmeye çalışmaktır. Bu nedenle `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md` kararıyla **Faz 8–13 dondurulmuş** ve aşağıdaki **30 Günlük Odaklanmış Canlıya Çıkış Sözleşmesi** yürürlüğe alınmıştır:

### 4.3 30 Günlük Odaklanmış MVP Uygulama Planı (4 Haftalık Sprint)

| Hafta | Hedef | Somut Mühendislik Teslimatları | Çıkış Kriteri |
|---|---|---|---|
| **1. Hafta (Gün 1–7)** | **Kontağı Çevir (Canlı Bulut AI Entegrasyonu)** | 1. `Qwen/vLLM` işini dondur (`ADR-008`).<br>2. `GeminiVisionProviderAdapter` (`Gemini 2.5 Flash`) ve `OpenAiSolveProviderAdapter` (`GPT-4.1-mini` + `o4-mini`) sınıflarını yaz.<br>3. `PostgresAiSpendGuard` ve `VerificationPolicy` üzerinden ilk gerçek fotoğraf -> canlı AI -> `SymPy` doğrulamalı çözümü uçtan uca geçir. | iPhone'dan çekilen gerçek bir denklem fotoğrafının 4 saniye içinde `VERIFIED` rozetiyle çözülmesi. |
| **2. Hafta (Gün 8–14)** | **Cebirsel Tamlık & 2D Matematiksel Görünüm** | 1. `CanonicalSolution.java` ve `canonical_execution.py` içine `FiniteSet` (çoklu kök), `Interval` ve `DomainConstraintGuard` (`extraneous_roots`) ekle (`ADR-009`).<br>2. iOS `MathematicalExpressionView.swift` içine `SwiftMath` 2D LaTeX dizgisini entegre et (`ADR-010`). | 2. dereceden denklemlerin, rasyonel yalancı kök tuzaklarının ve kesirli/köklü ifadelerin iOS'ta ders kitabı kalitesinde 2D görünmesi. |
| **3. Hafta (Gün 15–21)** | **Katil Özellik: "Çözümümü Kontrol Et" (`Analyze My Work`)** | 1. `services/math-verifier` içinde satır satır öğrenci çözümü doğrulama (`verify_student_attempt`) ve `CounterexampleEngine` (`F01`, `F02`, `F03`) uç noktasını aç.<br>2. iOS'ta öğrencinin kendi defter çözümünü çekip **ilk hatalı satırını kırmızı vurguyla + sayısal karşı-örnekle** gördüğü ekranı ve **İkiz Soru (`F04`)** butonunu tamamla. | Öğrencinin bilerek hata yaptığı 20 el yazısı çözümde ilk hatalı satırın `>= %85` başarıyla yakalanması. |
| **4. Hafta (Gün 22–30)** | **50 Gerçek Öğrenciyle TestFlight Saha Testi** | 1. `verified-ai-eval-v2` (ilk 150 çekirdek vaka) koşusunu tamamla (`0` yanlış `VERIFIED`).<br>2. Uygulamayı TestFlight üzerinden Türkiye'de YKS'ye (`TYT`/`AYT`) hazırlanan ve üniversite Calculus 101 alan **50 gerçek öğrenciye** dağıt. | 50 gerçek öğrencinin uygulamayla en az 500 gerçek soru/el yazısı çözümü denemesi. |

### 4.4 30. Gün Karar Kapısı (Objective Go / No-Go Criteria)
- **DEVAM ET VE ÖLÇEKLE (GO):** 50 beta öğrencisinin en az `%30`'u (15+ öğrenci) 2. haftada uygulamayı aktif kullanmaya devam ediyorsa, `VERIFIED` çözüm oranı desteklenen konularda `>= %85` ise ve öğrenciler *"ChatGPT ve Photomath'in bulamadığı işlem hatamı ve tanım kümesi tuzaklarını buluyor"* diyorsa -> Doğrudan App Store ticari lansmanına (`PRO` / `PRO+`) geçilir.
- **DUR VEYA PİVOT ET (NO-GO):** Canlı bulut modelleri, 2D LaTeX ve `Analyze My Work` gerçek öğrencilerin eline verildiği halde 2. hafta tutundurma `< %10` kalıyorsa -> Faz 8–13 için tek satır bile ek kod yazılmaz; kullanıcı mülakatlarına göre dar dikey (ör. sadece YKS Deneme Hata Karnesi) pivotu yapılır.

---

## 5. 6-Month and 12-Month Post-Beta Roadmap (Orta ve Uzun Vadeli Yol Haritası)

### 2.–6. Ay: Ticari V1 Büyümesi (Yalnızca 30 Günlük Beta Kapısı Geçildikten Sonra)
- **Ay 2:** App Store Türkiye ve Küresel Lansman (`StoreKit 2` canlı ödeme aktivasyonu, `PRO` / `PRO+` paywall A/B testleri).
- **Ay 3:** **Kişisel Hata Pasaportu (`F05`)** ve deterministik **BKT-Lite + Unutma Eğrisi Motoru** (`learning/79_PEDAGOGICAL_DIAGNOSIS_HANDWRITING_UX_AND_RETENTION_ENGINE.md`).
- **Ay 4:** **YKS "Yeni Nesil" Hikâye -> Cebir Görsel Köprüsü (`F08`)** ve **Sınav Hızı vs. Garantili Çözüm İkili Sekmesi (`F15`)**.
- **Ay 5:** AYT Limit, Türev, İntegral kapsamının genişletilmesi ve interaktif fonksiyon/türev grafik kartları.
- **Ay 6:** YKS / Calculus Sınav Öncesi "Tuzak Tarama Kampı" modülü ve organik paylaşım döngüleri (Arkadaşına İkiz Soru Gönder).

### 6.–12. Ay: Ölçeklenme ve Faz 13 Hibrit Model Bağımsızlığı
- **Ay 7–9:** 500 vakalık `verified-ai-eval-v2` tam otomasyonu, `R0` semantik/kanonik önbellek optimizasyonu ile birim maliyetin `\$0.0058`'den `\$0.0038` seviyesine çekilmesi.
- **Ay 10–12 (Yalnızca `>25,000` Ücretli Abone Kapısı Geçilirse):** Rıza alınmış ve `SymPy` ile doğrulanmış üretim verisi üzerinde `Qwen3-8B` / `Qwen2.5-VL-7B` modellerinin LoRA ile ince ayarı (`Phase 13`) ve yüksek hacimli alt görevlerde hibrit yönlendirme.

---

## 6. Top 10 Strategic & Technical Risks and Mitigations (En Kritik 10 Risk)

| # | Risk | Şiddet | Somut Önlem (Mitigation) |
|---|---|---|---|
| 1 | **Solo kurucu tükenmişliği (13 fazı canlıya çıkmadan bitirmeye çalışmak)** | Kritik | `ADR-011` ile Faz 8–13 donduruldu; 30 günlük TestFlight hedefi tek odak yapıldı. |
| 2 | **LLM'in Türkçe hikâye sorusunu yanlış denkleme dökmesi** | Yüksek | Metin->denklem adımı asla tek başına `VERIFIED` sayılmaz; `F08` Görsel Köprü ile öğrencinin kurulan denklemi onaylaması sağlanır. |
| 3 | **Tanım kümesi dışı yalancı kökün (`extraneous root`) doğru sanılması** | Kritik | `ADR-009` `DomainConstraintGuard` her aday kökü orijinal payda, çift kök ve logaritma kısıtlarına karşı deterministik test eder. |
| 4 | **`o4-mini` düşünme tokenlarının (reasoning tokens) faturayı şişirmesi** | Yüksek | `R3` yalnızca `%15` oranında doğrulayıcı reddiyle tetiklenir ve `max_completion_tokens <= 2048` + `PostgresAiSpendGuard` ile sınırlandırılır. |
| 5 | **Kötü el yazısı fotoğraflarında OCR hatası** | Yüksek | `Gemini 2.5 Flash` düşük güven (`<0.85`) döndürdüğünde çözüm üretilmez; mevcut Sprint 4.8 kullanıcı düzeltme ekranına yönlendirilir. |
| 6 | **Türkiye App Store satın alma gücü düşüklüğü** | Orta | Soru başına maliyet `~0.20 TL` olduğu için `199.99 TL/ay` fiyatla bile `%88` brüt marj korunur; Kunduz'un `1/5` fiyatına üstün hız sunulur. |
| 7 | **SymPy'nin ağır sembolik ifadelerde CPU'yu kilitlemesi (ReDoS/CAS hang)** | Yüksek | `services/math-verifier` içinde mevcut `1,500 ms` süreç zaman aşımı ve düğüm sayısı sınırı (`MAX_AST_NODES`) sıkıca korunur. |
| 8 | **Ücretsiz kullanıcıların kota suistimali (Çoklu hesap)** | Orta | Sign in with Apple zorunluluğu + cihaz/IP hız sınırları (`Sprint 3.8` mevcut) + ücretsiz pakette `o4-mini` (`R3`) rotasının kapalı olması. |
| 9 | **Üçüncü parti bulut API kesintisi (OpenAI veya Google çökmesi)** | Orta | Sağlayıcıdan bağımsız port mimarisi (`ADR-003`, `ADR-008`) sayesinde `GPT-4.1-mini` kesildiğinde 1 saniyede `Gemini 2.5 Flash` çözücüye geçilir. |
| 10 | **Öğrencilerin uygulamayı sadece ödev kopyalamak için kullanıp sınavda gelişmemesi** | Orta | Doğrudan tam çözüme bakmak hakimiyet puanını artırmaz; puan ve seri (streak) kazanmak için `Analyze My Work` veya `İkiz Soru (F04)` çözümü zorunludur. |
