# 76 — Competitive Intelligence, Market Positioning Matrix, and Signature Product Innovations

- **Research Date:** 2026-10-31
- **Status:** CANONICAL PRODUCT STRATEGY & COMPETITIVE INTELLIGENCE (V6)
- **Governing Documents:** `product/01_PRODUCT_VISION_AND_POSITIONING.md`, `product/04_FEATURE_CATALOG_AND_PRODUCT_RULES.md`, `adr/ADR-011_FOCUSED_V1_COMMERCIAL_LAUNCH_GATE_BEFORE_PHASE_08_13.md`

---

## 1. Executive Positioning Thesis (Stratejik Konumlandırma Tezi)

Matematik eğitim teknolojileri pazarı 2026 itibarıyla iki kusurlu uç arasında sıkışmıştır:
1. **Eski Nesil Sembolik/Kural Tabanlı Çözücüler (Photomath, Symbolab, Mathway):** Salt cebirsel ifadeleri hızlı ve doğru çözerler; ancak Türkiye YKS (`TYT`/`AYT`) tarzı uzun bağlamsal hikâye problemlerini (word problems) anlayamazlar ve **öğrencinin kendi el yazısıyla yaptığı çok adımlı çözümdeki ilk hatalı satırı (`First Wrong Step`) teşhis edemezler.**
2. **Yeni Nesil Doğrulanmamış LLM Sarmalayıcıları (Gauth, Solvely, Düz ChatGPT):** Bağlamsal metinleri iyi okurlar; ancak arkalarında deterministik bir sembolik doğrulama motoru (`SymPy` / CAS) bulunmadığı için ara adımlarda işlem hatası yapar, tanım kümesi kısıtlarını (ör. paydayı sıfır yapan yalancı kökleri) gözden kaçırır ve öğrenciye yüksek özgüvenle yanlış matematik öğretirler.

**Verified AI'ın Rekabetçi Kalesi (Defensible Wedge):**
> **"Deterministik Olarak Doğrulanmış (SymPy Kanıtlı) AI Çözümü + Öğrencinin Kendi El Yazısı Çözümünde İlk Hatalı Adım Teşhisi (`Analyze My Work`) + YKS/Calculus Tanım Kümesi Tuzak Radarı."**

---

## 2. Deep Competitive Tear-Down Matrix (13 Rakip Analizi)

| Ürün | Durum / Sahiplik (2026) | Çekirdek Mimari | Fiyatlandırma (TR / Global) | En Güçlü Yönü | Kritik Zayıflığı ve Verified AI'ın Vuracağı Boşluk |
|---|---|---|---|---|---|
| **1. Photomath** | Aktif (Google, 2023 satın alımı) | Özel OCR + Kural Tabanlı Uzman Sistem (Expert System) + İnsan içerik kütüphanesi | Temel ücretsiz; **Photomath Plus** `~$9.99/ay` (`~$59.99/yıl`) | Kusursuz kamera OCR hızı ve standart ders kitabı denklemlerinde sıfır halüsinasyonlu animasyonlu adımlar. | Özgün Türkçe hikâye sorularında (YKS Yeni Nesil) sıkça başarısız olur; öğrencinin kendi karaladığı çözümü okuyup *"3. satırda hata yaptın"* diyemez; kalıcı beceri/hata grafiği yoktur. |
| **2. Symbolab** | Aktif (Course Hero / Learneo) | Tescilli Sembolik CAS + Kural Tabanlı Adım Üretici | **Symbolab Pro** `~$6.99–$9.99/ay` (`~$29.99–$49.99/yıl`) | Türev, integral, limit, seriler ve matrislerde çok güçlü deterministik adım üretimi. | Arayüzü eski ve reklam/paywall dolu; pedagojik Sokratik yönlendirmesi zayıf; öğrencinin el yazısı çözümünü satır satır denetleyemez. |
| **3. Brilliant** | Aktif (Bağımsız) | İnsan tarafından elle tasarlanmış interaktif görsel dersler (Sıfır açık uçlu çözücü) | `~$24.99/ay` (`~$149.99/yıl`) | Dünyanın en iyi aktif öğrenme ("learning by doing") ve görsel sezgi deneyimi. | **Kendi sorunuzu çekip soramazsınız.** Öğrencinin o akşamki YKS denemesindeki veya Calculus ödevindeki sorusuna cevap veremez. |
| **4. Wolfram Alpha** | Aktif (Wolfram Research) | Wolfram Language / Mathematica (Deterministik CAS standardı) + LLM eklentisi | **Pro** `~$7.25–$9.99/ay` | Sembolik/sayısal kesinliğin altın standardı. | Tüketici mobil UX'i zordur; lise öğrencisi için adımları fazla akademik/mekaniktir; Türkçe sınav pedagojisine ve öğrenci hata takibine uzak. |
| **5. Mathway** | Aktif (Chegg) | Kural tabanlı sembolik çözücü | Cevap ücretsiz; adımlar `~$9.99/ay` (`~$39.99/yıl`) | Geniş cebir/kalkülüs kapsamı, hızlı sonuç. | Adımların tamamı sert bir paywall arkasındadır; bağlamsal metin sorularında ve pedagojik açıklamada çok zayıftır. |
| **6. Gauth (Gauthmath)** | Aktif (ByteDance) | VLM + LLM + Canlı insan eğitmen pazar yeri | **Gauth Plus** `~$9.99–$11.99/ay` | Çok hızlı metin/görsel okuma, agresif TikTok pazarlaması. | **Deterministik doğrulama motoru yoktur.** Karmaşık cebir ve kalkülüste LLM halüsinasyonlarını öğrenciye doğruymuş gibi sunar; oyunlaştırması yüzeyseldir. |
| **7. Microsoft Math Solver** | **KAPATILDI (7 Temmuz 2025)** | Bing/Edge OCR + Sembolik CAS | Ücretsizdi (Emekli edildi) | Ücretsiz adım adım çözüm ve grafik sunduğu için milyonlarca öğrencinin favorisiydi. | Microsoft 7 Temmuz 2025'te ürünü resmen kapattı ve genel Copilot'a yönlendirdi. Piyasada "güvenilir, reklamsız, doğrulanmış matematik aracı" arayan devasa bir yetim kullanıcı kitlesi bıraktı. |
| **8. Desmos** | Aktif (Desmos Studio) | Deterministik 60fps 2D/3D grafik ve sayısal hesaplama motoru | **Ücretsiz** (Okul/sınav ortaklıkları) | Eşitsizlik bölgeleri, parametrik eğriler ve kaydırgaçlarda (sliders) rakipsiz görsel hız. | Adım adım cebirsel çözüm veya fotoğraf çekip hata teşhisi yapmaz. Verified AI onunla yarışmamalı, benzer bir interaktif grafik kartını çözüm adımlarına gömmelidir. |
| **9. GeoGebra** | Aktif (Byju's) | Dinamik geometri + CAS + Cebir birleşimi | **Ücretsiz** | Geometri ve cebir arasındaki canlı bağlantı. | Mobil arayüzü karmaşıktır; AI destekli hata teşhisi veya kişisel çalışma planlayıcısı yoktur. |
| **10. ThetaWise** | Aktif (ABD AI-native girişim) | LLM + Sembolik araç çağrısı (Tool-use) + Sesli/yazılı tahta | `~$9.99–$14.99/ay` | Üniversite matematik/kalkülüs öğrencileri arasında doğruluk odaklı Sokratik sohbetiyle hızlı büyüme. | Türkçe müfredat/YKS uyumu yoktur; öğrencinin el yazısı çözümünü satır satır SymPy ile doğrulayan şeffaf bir kanıt kartı (`VerificationEvidence`) sunmaz. |
| **11. Khanmigo** | Aktif (Khan Academy) | GPT-4 tabanlı Sokratik istem (prompt) sarmalayıcısı | `~$4/ay` (`$44/yıl`) | Cevabı doğrudan vermeyip soru sorarak düşündürme disiplini. | Arkasında bağımsız bir CAS/SymPy doğrulayıcı olmadığı için öğrencinin doğru ara adımını bazen yanlış sanıp kafa karıştırır; kamera/el yazısı akışı zayıftır. |
| **12. Solvely / Question.AI** | Aktif | Çoklu ders VLM sarmalayıcıları | `~$9.99/ay` | Hızlı ödev cevabı. | Sıfır doğrulama, sıfır pedagojik derinlik, yüksek abonelik iptal (churn) oranı. |
| **13. Kunduz** | Aktif (Türkiye merkezli) | **İnsan eğitmen ağı** (Soru fotoğrafı -> Eğitmen havuzu -> 15-30 dk içinde fotoğraf yanıtı) | Paket bazlı `~700 TL – 1.300 TL/ay` | Türkiye YKS (`TYT`/`AYT`) "Yeni Nesil" sorularına tam uyum ve insan empatisi. | **Birim ekonomisi ve hız darboğazı:** Cevap 15–30 dakika sürer ve insan maliyeti yüzünden çok pahalıdır. Verified AI, `SymPy` doğrulamalı çözümü **4 saniyede** ve soru başına `~0.20 TL` maliyetle sunarak Kunduz'a **100x hız ve 4x fiyat avantajı** kurar. |

---

## 3. 30 Product Innovation Proposals & Multi-Criteria Scoring Matrix (30 İnovasyon Fikri ve Puanlama)

Puanlama ölçeği: `1` (En düşük) – `5` (En yüksek/en avantajlı). `Maliyet` ve `Solo Efor` sütunlarında `5` = en düşük maliyet / en hızlı solo geliştirme demektir.
Toplam Ağırlıklı Skor = $2\times\text{Öğrenme} + 2\times\text{Farklılaşma} + 1.5\times\text{Tutundurma} + 1.5\times\text{Gelir} + 1\times\text{Fizibilite} + 1\times\text{Maliyet} + 1\times\text{Solo Efor}$ (Maksimum `50.0`).

| Kod | İnovasyon / Özellik Adı | Öğrenme (2x) | Farklılaşma (2x) | Tutundurma (1.5x) | Gelir (1.5x) | Fizibilite (1x) | Maliyet (1x) | Solo Efor (1x) | Skor (/50) | Karar |
|---|---|---|---|---|---|---|---|---|---|---|
| **F01** | **İlk Hatalı Adım Dedektifi (`Analyze My Work` via SymPy Step-Equivalence)** | 5 | 5 | 5 | 5 | 4 | 4 | 4 | **47.0** | **SEÇİLDİ (V1 Çekirdek #1)** |
| **F02** | **Tanım Kümesi ve Tuzak Radarı (`Domain Restriction & Extraneous Root Radar`)** | 5 | 5 | 4 | 5 | 5 | 5 | 5 | **48.5** | **SEÇİLDİ (V1 Çekirdek #2)** |
| **F03** | **Karşı-Örnek Üretici (`Concrete Counterexample Generator`: "Neden Yanlış?")** | 5 | 5 | 4 | 4 | 5 | 5 | 5 | **47.0** | **SEÇİLDİ (V1 Çekirdek #3)** |
| **F04** | **Karşı-Olgu İkiz Soru Üretici (`Counterfactual Twin Problem` — SymPy Doğrulamalı)** | 5 | 5 | 5 | 5 | 4 | 4 | 4 | **47.0** | **SEÇİLDİ (V1 Çekirdek #4)** |
| **F05** | **Kişisel Hata Pasaportu (`Mistake Passport` & İmzalı Hata Parmak İzi)** | 5 | 4 | 5 | 5 | 5 | 5 | 4 | **47.0** | **SEÇİLDİ (V1 Çekirdek #5)** |
| **F08** | **YKS "Yeni Nesil" Hikâye -> Cebir Köprüsü (`Story-to-Equation Visual Mapper`)** | 5 | 5 | 5 | 5 | 4 | 4 | 4 | **47.0** | **SEÇİLDİ (V1 Çekirdek #6)** |
| **F09** | **Kademeli İpucu Kumbarası (`Progressive Socratic Hint Ladder`)** | 5 | 4 | 4 | 4 | 5 | 5 | 5 | **45.0** | **SEÇİLDİ (V1 Çekirdek #7)** |
| **F15** | **Sınav Hızı vs. Garantili Çözüm İkili Görünümü (`Exam Shortcut vs. Formal Proof`)** | 4 | 5 | 5 | 5 | 4 | 4 | 4 | **45.0** | **SEÇİLDİ (V1 Çekirdek #8)** |
| F06 | Parametrik Kaydırgaçlı Canlı Grafik Kartı (`Interactive Parameter Slider`) | 4 | 4 | 4 | 3 | 4 | 5 | 3 | 38.5 | V1.5 |
| F07 | "Bunu Nereden Çıkardık?" Teorem Kökeni (`Prerequisite Micro-Card`) | 4 | 3 | 3 | 3 | 5 | 5 | 5 | 38.0 | V1.5 |
| F10 | Tersine Rol: "AI'ın Çözümündeki Hatayı Bul" (`Spot the Flaw Challenge`) | 5 | 4 | 4 | 3 | 4 | 4 | 4 | 40.5 | V1.5 |
| F11 | Çözüm Yolu Çatallanma Ağacı (`Multi-Method Decision Tree`) | 4 | 4 | 3 | 4 | 4 | 3 | 3 | 36.5 | V1.5 |
| F12 | "Sınavda Kaç Dakika Sürer?" Zaman Bütçesi Tahmincisi | 3 | 4 | 4 | 4 | 4 | 5 | 4 | 39.0 | V1.5 |
| F13 | Cebirsel Dönüşüm Animatörü (`Step-by-Step Term Highlighting Diff`) | 4 | 4 | 4 | 4 | 4 | 5 | 4 | 41.0 | **ADR-010 ile F01'e dahil** |
| F14 | Unutma Eğrisi Alarmı (`Half-Life Regression Decay Alert`) | 5 | 4 | 5 | 4 | 4 | 5 | 4 | 44.5 | V1.5 (Faz 7/8) |
| F16 | Sesli Düşünme Teşhisi (`Think-Aloud Voice Note Diagnosis`) | 4 | 4 | 3 | 3 | 3 | 3 | 3 | 34.0 | V2 |
| F17 | Deneme Sınavı Optik/Sayfa Toplu Hata Tarayıcı (`Batch Mock Exam Triage`) | 4 | 4 | 5 | 5 | 3 | 3 | 2 | 39.0 | V2 |
| F18 | "Sadece İlk Adımı Göster, Gerisini Ben Yapayım" (`First-Step Unblocker`) | 5 | 3 | 4 | 3 | 5 | 5 | 5 | 41.5 | **F09'un 1. kademesi** |
| F19 | Kavramsal Ön-Koşul Röntgeni (`2-Minute Root-Cause Diagnostic Quiz`) | 5 | 4 | 4 | 4 | 4 | 4 | 3 | 41.0 | V1.5 |
| F20 | Formül Kağıdı Oluşturucu (`Personalized Cheat-Sheet Generator`) | 3 | 3 | 4 | 4 | 5 | 4 | 4 | 37.0 | V1.5 |
| F21 | Grafik-Fonksiyon Eşleştirme Mini Oyunu | 4 | 3 | 4 | 2 | 3 | 5 | 2 | 33.0 | Elenen |
| F22 | PDF Ders Notundan Otomatik Çalışma Seti Çıkarıcı | 4 | 3 | 4 | 5 | 3 | 2 | 2 | 34.5 | V2 |
| F23 | Apple Pencil Canlı Satır İçi Doğrulama (`Live Ink Verification`) | 4 | 5 | 4 | 4 | 2 | 4 | 1 | 36.0 | V2 (iPad) |
| F24 | Sınav Günü Stres/Tuzak Simülatörü (`5-Question Daily Trap Sprint`) | 4 | 4 | 5 | 4 | 4 | 4 | 4 | 41.5 | V1.5 |
| F25 | Çözüm Güveni Kalibrasyonu (`Confidence Betting before Reveal`) | 4 | 3 | 3 | 2 | 5 | 5 | 5 | 36.5 | V1.5 |
| F26 | Veli / Koç Haftalık Şeffaf Gelişim Özeti (PDF/Web Link) | 2 | 3 | 4 | 5 | 4 | 5 | 4 | 36.5 | V1.5 |
| F27 | Sembolik "Neden $0/0$ Belirsizdir?" İnteraktif Limit Laboratuvarı | 4 | 4 | 3 | 3 | 4 | 5 | 3 | 37.0 | V2 |
| F28 | Arkadaşına Meydan Oku (`Verified Duel Link`) | 3 | 3 | 4 | 3 | 4 | 5 | 3 | 34.5 | V2 |
| F29 | Üniversite Calculus 101 Vize/Final Kurtarma Paketi | 4 | 4 | 4 | 5 | 4 | 4 | 4 | 41.5 | V1.5 |
| F30 | "Doğrulama Kanıtı" Şeffaflık Kartı (`SymPy Proof Receipt`) | 4 | 5 | 4 | 4 | 5 | 5 | 5 | **45.0** | **Mevcut Sprint 6.2'de var!** |

---

## 4. Detailed Specification of the Top 8 Selected Signature Capabilities (V1 İmza Özellikler)

### 1. `F01` — İlk Hatalı Adım Dedektifi (`Analyze My Work` via SymPy Step-Equivalence)
- **Kullanıcı Deneyimi:** Öğrenci soruyu ve kendi el yazısı çözümünü fotoğraflar. Uygulama öğrencinin satırlarını $L_1, L_2, \dots, L_k$ olarak ayrıştırır.
- **Teknik Mekanizma:** `services/math-verifier` her ardışık $L_i \implies L_{i+1}$ geçişini sembolik eşdeğerlik ve çözüm kümesi korunumu açısından test eder. Eşitliğin bozulduğu **ilk satır** kırmızı vurguyla (`ADR-010`) işaretlenir; önceki doğru adımlar yeşil onay alır.

### 2. `F02` — Tanım Kümesi ve Tuzak Radarı (`Domain Restriction & Extraneous Root Radar`)
- **Kullanıcı Deneyimi:** Rasyonel denklemler, çift dereceli köklü ifadeler ve logaritmalarda ekranın en üstünde sarı/kırmızı bir **"Tuzak Radarı"** rozeti belirir: *"Dikkat: $x = 2$ paydayı sıfır yaptığı için tanım kümesi dışındadır (`Yalancı Kök`)!"*
- **Teknik Mekanizma:** `ADR-009` kapsamında `DomainConstraintGuard` orijinal ifadenin doğal tanım kümesini ($D_{\text{natural}}$) çıkarır ve aday kökleri bu kümeyle kesiştirir.

### 3. `F03` — Somut Karşı-Örnek Üretici (`Concrete Counterexample Generator`: "Neden Yanlış?")
- **Kullanıcı Deneyimi:** Öğrenci $(a+b)^2 = a^2 + b^2$ veya $\sqrt{x^2+y^2} = x+y$ gibi hatalı bir sadeleştirme yaptığında soyut bir kural okumak yerine anında sayısal kanıt görür: *"Senin geçişinde $x=3, y=4$ koyarsak sol taraf $5$, sağ taraf $7$ çıkıyor ($5 \neq 7$)."*
- **Teknik Mekanizma:** `CounterexampleEngine` (`ai/78_SYMBOLIC_VERIFICATION_ENGINE_V2_AND_DOMAIN_TRAP_RADAR.md`) deterministik rasyonel/tam sayı ızgarasında eşitliği bozan en küçük tam sayıyı bulup döndürür.

### 4. `F04` — Karşı-Olgu İkiz Soru Üretici (`Counterfactual Twin Problem`)
- **Kullanıcı Deneyimi:** Öğrenci bir hatayı düzelttikten sonra ekranın altında **"Şimdi Aynı Tuzağı Tek Başına Geç (İkiz Soru)"** butonu çıkar.
- **Teknik Mekanizma:** LLM aynı cebirsel yapıda ve aynı hata tuzağını barındıran ancak katsayıları değiştirilmiş yeni bir soru üretir; `math-verifier` bu yeni sorunun temiz ve tekil/sonlu çözümü olduğunu arka planda doğrulamadan öğrenciye göstermez.

### 5. `F05` — Kişisel Hata Pasaportu (`Mistake Passport`)
- **Kullanıcı Deneyimi:** Öğrencinin profilinde *"Konu Bazlı Başarı"* yanında **"En Sık Düştüğün 3 Cebirsel Tuzak"** listelenir (ör. `INEQ_NEGATIVE_MULT_NO_FLIP`: *"Son 2 haftada 4 kez eşitsizliği negatif sayıyla çarparken yön değiştirmeyi unuttun"*).

### 6. `F08` — YKS "Yeni Nesil" Hikâye -> Cebir Köprüsü (`Story-to-Equation Visual Mapper`)
- **Kullanıcı Deneyimi:** Uzun Türkçe paragraf sorularında metindeki her kritik cümle parçası renkli bir kapsülle vurgulanır ve okla karşılık geldiği cebirsel ifadeye bağlanır (ör. *"Ali'nin yaşının 2 katının 3 eksiği"* $\longrightarrow$ $2x - 3$).

### 7. `F09` — Kademeli Sokratik İpucu Kumbarası (`Progressive Socratic Hint Ladder`)
- **Kullanıcı Deneyimi:** Tam çözümü doğrudan açmak yerine 3 aşamalı ipucu:
  - *İpucu 1 (Strateji / İlk Adım):* "Paydaları eşitlemeden önce $x^2-4$ ifadesini çarpanlarına ayırmayı dene."
  - *İpucu 2 (Ara Denklem):* "Denklem şu hale gelmeli: $\dots$"
  - *Tam Çözüm:* Tüm doğrulanmış adımlar.

### 8. `F15` — Sınav Hızı vs. Garantili Çözüm İkili Görünümü (`Exam Shortcut vs. Formal Proof`)
- **Kullanıcı Deneyimi:** Özellikle TYT/AYT sorularında üstteki sekme değişimiyle:
  - **Sekme 1 (Garantili Adım Adım Çözüm):** Tüm cebirsel ara adımlar.
  - **Sekme 2 (Sınav Hızı / 30 Saniyelik Taktik):** Katsayılar toplamı ($P(1)$), simetri ekseni veya grafik kesişimi ile sınavda zaman kazandıran pratik yol (yine `math-verifier` ile nihai cevabı doğrulanmış olarak).
