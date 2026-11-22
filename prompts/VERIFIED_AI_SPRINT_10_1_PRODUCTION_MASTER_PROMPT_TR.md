# VERIFIED AI — SPRINT 10.1 PRODUCTION MASTER IMPLEMENTATION PROMPT (TR)

**Sprint:** 10.1 — Paywall Architecture, Offer Presentation, and Pricing Configuration  
**Domain:** `INV-MON-001` / `CNT-LOS-12` / `Screen 12`  
**Hedef dal:** `main` — başlangıç referansı `36566f9f2f2709c63a7900681cd851314b3c1a27` (19.11.2026)  
**Ajan modu:** `IMPLEMENT_WITHOUT_COMMIT`, `NO_PUSH`, `NO_DEPLOY`, `OWNER_RUNS_TESTS`  
**Çıktı durumu:** Bu belge bir *uygulama talimatıdır*; tamamlanmış implementasyon veya koşulmuş test kanıtı değildir.

> **Kanıt dürüstlüğü notu.** 20.11.2026 tarihinde GitHub commit sayfasında 36566f9 görülebildi; ham GitHub kaynaklarının tamamı bu hazırlık oturumunda güvenilir biçimde erişilebilir değildi. Bu sebeple aşağıdaki mevcut-sistem adları ve Sprint 9.10 kabul durumları, talepte sağlanan somut repo referanslarıyla birlikte **yerel checkout üzerinden kodlama ajanının doğrulayacağı giriş koşullarıdır**. Kontrol edilmemiş bir constructor, sınıf adı, migration içeriği, şema alanı veya PASS sonucu doğrulanmış gibi kabul edilmez. Ajan ilgili dosya gerçeklerini ilk aşamada doğrulamalı; uyuşmazlıkları raporlamalı ve güvenli biçimde çözmelidir. Başarısız/eksik kanıtı saklamak yasaktır.

---

## 0. Çalışma şekli ve kesin görev tanımı

Sen Principal/Staff seviyesinde Java/Spring Modulith, Flyway/PostgreSQL, Swift 6/SwiftUI/StoreKit 2, EdTech ve KVKK güvenliği uzmanı uygulama ajanısın. Mevcut Verified AI mimarisine **uyumlu, test edilebilir, backward-compatible, güvenli şekilde kapalı** Sprint 10.1 kodunu teslim et. Ek soru sorma; eksik detaylarda kod gerçeklerine uyan en dar, geriye uyumlu çözümü seç ve karar günlüğüne kaydet. Bu sprintin amacı **teklif kataloglama, gösterim, restore ve satın alma arayüzü, ticari sözleşmeler ve güvenli tetikleme**dir. Sert sunucu yetki denetimleri Sprint 10.2'ye; yeni harcama/kredi/kota ledger ve fiyat bazlı tüketim Sprint 10.3'e aittir.

### Mutlak güvenlik ve yetki sınırları

1. Sadece mevcut checkout içinde çalış. `.env*`, credential, token, sertifika, API anahtarı, provisioning profile, keychain, özel servis hesapları, `DerivedData/`, `target/`, `build/` dosyalarını **açma, tarama, yazdırma veya rapora kopyalama**. Hiçbir secret'ı loglama. `git status --short` gibi metadata komutları uygundur; gizli dosya içerikleri değildir.
2. **NO_COMMIT / NO_PUSH / NO_DEPLOY.** GitHub Actions tetikleme, PR açma, branch değiştirme, canlı App Store işlemi, gerçek Apple satın alma, fiyat değişikliği, production DB migration çalıştırma yok. Var olan kullanıcı değişikliklerini koru; `reset --hard`, `clean -fd`, force overwrite yok.
3. **Testleri sen çalıştırma.** Owner tarafından yerel çalıştırılacak **tek kopyalanabilir, yorum satırsız shell komut bloğu** üret. Scriptleri yazabilirsin, ama hiçbir test/build/lint/validator/migration scripti çalıştırma. Yalnızca kod ve doküman değişiklikleri yap, statik inceleme ile olası hataları azalt. Raporlarda `NOT_RUN_OWNER_REQUIRED` kullan.
4. Sprint 9.10 raporlarında kanıt yaratma/yeniden yazma yok. Özellikle yerel kabul kimliği `s910-20261119T111201Z` için `PASS` belirtilmiş olsa bile uzak staging P95 ve fiziksel cihaz VoiceOver işlerini **PENDING** olarak koru; kanıtları sahte biçimde yeşilleme.
5. Kapsam dışı tarama yok: `services/math-verifier/`, `evaluations/`, `problem/`, `solving/`, `attempt/`, `mastery/`, `studyplan/`, `tutoring/`, Phase 01–08 ve eski Phase 09 sprint içeriklerine girme. Arayüz uyumsuzluğunu görmek için yalnızca gerekli çağrı satırlarını hedefli aramayla incele. Tam `tree`, global `find`, tüm repo `grep -R`, geniş stdout dökümü kullanma.
6. Büyük dosyalar için `rg -n` + küçük `sed -n start,endp` aralıkları kullan: `public-api.yaml`, `verified-ai-core-api.yaml`, `validate_json_schemas.py`, `Localizable.xcstrings`, `project.pbxproj`. Dosyanın tamamını stdout/context'e basma. Kaynak taramasında sert limit uygula.
7. Kullanıcıların eğitim çıktıları üzerinden ticari baskı kurulamaz. **`INV-MON-001` sıfır istisna**: ücretsiz kotadan **kabul edilmiş** çözümün First-Error Diagnosis ve Counterexample sonucu asla bulanıklaştırılamaz, kesilemez, geri alınamaz, kısmen saklanamaz veya satın almaya bağlanamaz. Bu çıktılara ait erişim önceki kabul kararıyla korunur.
8. **Aktif geri sayım sırasında paywall yok:** exam session, cold-start diagnostic ve prove-it solo twin akışlarının aktif timer state'leri UI, event ve navigation seviyesinde paywall tetikleyemez. Timer durumu bilinmiyorsa gösterimi engelle (fail-closed).
9. Çocuklara/öğrencilere yönelik etik UX: yapay aciliyet, sahte sayaç, gizli varsayılan seçim, yanıltıcı ücretsiz deneme, sosyal baskı, agresif tekrar gösterme veya çıkış engelleme yok. Screen 12 kapanabilir olmalı; ücretsiz devam yolu korunmalı.
10. **$0.00 ek LLM maliyeti:** paywall/teklif/fiyatlandırma kodu LLM çağırmaz, prompt üretmez, üçüncü taraf üretken AI API'sına gitmez. Yalnızca deterministic backend + StoreKit 2 + var olan entitlement altyapısı.

---

## 1. Kanıt odaklı, az tokenlı giriş denetimi

**İzin verilen inceleme yüzeyleri ve maksimum kapsam** (sadece bu dört adım; geniş repo taraması yasak):

### A — Sprint 9.10 exit / DB (en fazla dört kısa okuma)

- `git log -n 3 --oneline`, `git rev-parse HEAD`, `git status --short` ile checkout ve owner değişikliklerini kaydet; `36566f9` atası mı, yeni commit var mı doğrula. HEAD farklıysa kod gerçeklerini HEAD'de baz al; kesin SHA'yı yaz.
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase09_exam_and_assessment_platform/SPRINT_9.10_EXECUTION_REPORT.md` — yalnız çıkış kanıtı, tarih, run ID ve açık kapılar.
- `.../SPRINT_9.10_ACCEPTANCE_MATRIX.md` — yalnız yerel PASS ile staging P95/cihaz VoiceOver PENDING ayrımı.
- `services/api/src/main/resources/db/migration/platform/` sadece basename listesi; son versiyon `V064__sprint_9_10_exam_integrity_audit_logs.sql` var mı doğrula. **Yalnız** `V004__create_entitlements.sql` ve `V005__create_app_store_billing.sql` içeriğini hedefli oku. Varsayılan yeni migration V065'tir; daha ileri migration varsa çakışmayı saklama, buna göre numara belirleme kararını owner'a blocker olarak kaydet — var olan migration'ı yeniden numaralandırma.

### B — Kanonik tasarım (belirtilen bölümler)

- `docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/PHASE_10_MONETIZATION_GROWTH_AND_PRODUCT_OPERATIONS.md`
- Aynı dizinde `SPRINT_10.1_PAYWALL_ARCHITECTURE_OFFER_PRESENTATION_AND_PRICING_CONFIGURATION.md`
- `docs/verified_ai_learning_platform_docs_v4/product/05_MONETIZATION_AND_ENTITLEMENTS.md`
- `docs/verified_ai_learning_platform_docs_v4/roadmap/88_LEARNING_OS_MONETIZATION_GROWTH_AND_VALIDATION_PLAN.md`
- `docs/verified_ai_learning_platform_docs_v4/architecture/90_LEARNING_OS_DOMAIN_CONTRACTS_AND_PLATFORM_INTEGRATION.md` — yalnız `CNT-LOS-12` / billing.
- `docs/verified_ai_learning_platform_docs_v4/ios/18_IOS_AUTH_STOREKIT_AND_DEVICE_SECURITY.md` — yalnız StoreKit 2 / cihaz güvenliği.
- `docs/verified_ai_learning_platform_docs_v4/domain/10_DOMAIN_INVARIANTS_AND_BUSINESS_RULES.md` — yalnız entitlement, monetization, privacy.
- Gerekirse `docs/verified_ai_learning_platform_docs_v4/learning/85_PERSONAL_AI_MATHEMATICS_COACH_AND_DAILY_LEARNING_EXPERIENCE.md` — yalnız Screen 12.

### C — Sadece kod yüzeyleri

- `services/api/src/main/java/com/verifiedai/billing/`: mevcut sınıf dosya adları ve seçilmiş içerikleri; özellikle `EntitlementTier`, `EntitlementStatus`, `PremiumCapability`, `AppleProductCatalog`, `AppleBillingProperties`, `AppleBillingController`, `EntitlementController`, `EntitlementResponse`, `EntitlementApplicationService`, `AppleBillingApplicationService`, `CapabilityAccessPolicy`, `BillingLifecycleContributor`, `AppStoreBillingRepository`, `EntitlementJpaEntity`.
- `ApiErrorCode.java` — billing kod satırları; `services/api/src/main/resources/application.yml` — yalnız billing/apple config satırları; security config — yalnız ilgili route authorization eşlemesi.
- `apps/ios/VerifiedAI/Features/Subscription/` ve `apps/ios/VerifiedAITests/Subscription/` — mevcut subscription dosyaları ve testleri. Özellikle `EntitlementModels.swift`, `SubscriptionCommerceModels.swift`, `EntitlementAPI.swift`, `AppleBillingAPI.swift`, `StoreKitProductRepository.swift`, `StoreKitTransactionObserver.swift`, `CapabilityGate.swift`, `EntitlementViewModel.swift`, `PaywallViewModel.swift`, `PaywallView.swift`, `EntitlementDisplayCache.swift`.
- **Tek hedefli arama** ile `ExamSessionView`, `ColdStartDiagnosticFlowView`, `ProveItSoloTwinView` içinde mevcut timer/flow state'lerini bul; timer gerçek state adlarını uydurma, koddan türet.
- OpenAPI ikizleri: `packages/contracts/openapi/public-api.yaml`, `packages/openapi/verified-ai-core-api.yaml` — yalnız billing/entitlement bölümleri. `packages/schemas/` — varsa billing/entitlement dosyaları. Kabul kalıbı: `scripts/quality/sprint_9_10_owner_acceptance.sh`, `scripts/quality/sprint_9_10_swift_check.sh` — yalnız yapı.

### D — Kısa uyuşmazlık kayıt defteri

Değişiklikten önce `SPRINT_10.1_IMPLEMENTATION_MAP.md` içine **kanıt matrisi** yaz: dosya/satır aralığı, bulunan imza veya mevcut sözleşme, karar, değişecek dosya. Emin olunmayan noktayı `UNVERIFIED` yap; asla sahte keşif kaydı yazma. A, B, C dışında dolanma yok; bağlantı ihtiyacı varsa yalnız minimum sembol düzeyinde hedefli inceleme yap.

---

## 2. Kilitli kanonik mimari kararlar

| ID | Bağlayıcı karar | Uyumluluk/kabul beklentisi |
|---|---|---|
| ADR-101-01 | Yeni DB migration **V065**; mevcut `V004`/`V005` geçmişi immutable | Var olan CHECK ve index korunur; yeni kolon/tablolar additive; rollback için elle, veri kaybı olmadan plan |
| ADR-101-02 | `verifiedai.billing.contextual-paywall-enabled: false` | Config yok/bozuk/false ise display ve impression yazımı kapalı; billing/restore güvenlik akışları bozulmaz |
| ADR-101-03 | Storage compatibility için eski `FREE/PRO/PRO_PLUS` semantiği yaşar; public merchandising `FREE/PRO/COACH_PRO/YKS_SEASON_PASS` | `PRO_PLUS` → `COACH_PRO` **yalnız sunum/compatibility alias**; mevcut receipt/webhook DB kayıtları geriye dönük okunur |
| ADR-101-04 | **Season Pass, entitlement tier değil süreli offer/product**: `YKS_SEASON_PASS` public offer family, entitlement projection `COACH_PRO` capabilities with immutable bounded validity | 6 aylık satın alma süresi receipt/transaction ile doğrulanır; `YKS_SEASON_PASS` kimliği offer ve source metadata'da korunur; yeni legacy enum yazma zorunluluğu yok |
| ADR-101-05 | Existing `/api/v1/me/entitlements` ve `/api/v1/billing/apple/**` davranışları korunur. Yeni `/v1/billing/offers`, `/v1/billing/entitlements` ile `/api/v1/billing/offers`, `/api/v1/billing/entitlements` alias'ları tek servis/sözleşmeden beslenir | Alias'ta response, error, auth, ETag/cache, locale ve status eşdeğerliği; legacy endpoint bozulmaz |
| ADR-101-06 | `entitlement-v2` **additive DTO/schema**; legacy response için destructive alan adı değişikliği yok | V2 `effectiveTier`, `capabilities`, `source`, `expiresAt`, entitlement state ve gerekliyse `legacyTier` gibi açık köprü alanları; gerçek repo tiplerine uyarlanır |
| ADR-101-07 | `INV-MON-001`: kabul edilmiş ücretsiz eğitim çıktısı satın almaya bağlanmaz; timer sırasında paywall yok | Hem servis/UI coordinator hem navigation katmanında guard; edge-case testleri zorunlu |
| ADR-101-08 | Apple App Store Guideline 3.1.2: gerçek StoreKit fiyatları, açık dönem/trial ve restore, Terms/Privacy, yükümlülük metinleri | Sunucu varsayılan fiyatı hukuki gerçek gibi sunamaz; çevrimdışı durumda sahte fiyat gösterilmez |
| ADR-101-09 | Sprint 9.10 staging P95 ve cihaz VoiceOver PENDING | Sprint 10.1 kodu teslim edilebilir ancak rollout flag kapalı kalır; release GO değildir |
| ADR-101-10 | Sprint 10.1 quota/spend enforcement oluşturmaz | `PostgresAiSpendGuard` ve kota/kredi ledger'ları dokunulmaz; hard enforcement Sprint 10.2, quotas Sprint 10.3 |

**Tier/offer kesin semantik:** `PRO_PLUS` mevcut persisted değerini veya Apple receipt productId'sini **topluca rewrite etme**. Tek bir `TierResolutionPolicy` (adı mevcut mimariye uyarlanabilir) ile `PRO_PLUS` ve yeni `COACH_PRO` isteğini **eşdeğer coaching capability setine** projekte et; hiçbir downgrade/kayıp yaratma. `YKS_SEASON_PASS` ayrı *purchase offer* olarak temsil edilsin, subscription/entitlement `status` enum'una dönüştürülmesin. Apple ürün kimlikleri **yalnız mevcut katalog/config veya App Store Connect'te doğrulanmış değerlerden** gelsin; `com.*` SKU uydurma. Planlanan monthly/annual/6-month sku referansları yoksa disabled katalog girdisi + açık blocker üret, sahte satın alınabilir ürün gösterme. Bir legacy aboneliğin otomatik olarak yeni SKU'ya taşındığını iddia etme.

**Kararların önceliği:** Güvenlik/Apple kuralları/`INV-MON-001` > mevcut persisted verinin korunması > kodlanan gerçek API sözleşmesi > Phase 10 hedef belgesi > bu promptun örnek isimleri. Belgeler çelişirse bu ADR'leri uygula ve farkı raporla; sözleşme değiştirmeden önce regression fixture ekle.

---

## 3. Tam kapsam ve sprint sınırı

**Kapsam içi:** sunucu tarafı locale-aware offer katalog ve fiyat sunum konfigürasyonu; güvenli enable/disable; sale eligibility'nin deterministic gösterim düzeyi; entitlement-v2 okuma API'si; eski yolları bozmayan twin route; StoreKit 2 gerçek ürünlerle paywall; purchase + restore'un mevcut verified backend işlem hattına bağlanması; türetilmiş anonim event metrics; privacy lifecycle; kontratlar, migration, EN/TR localization, erişilebilirlik, test kaynakları, sprint kanıt belgeleri ve owner scriptleri.

**Kapsam dışı:** yeni AI tüketim kota/credit ledger, rate limiter, pricing-by-token, PostgresAiSpendGuard refactor, global feature gate enforcement, bütün ders/çözüm ekranlarını premium kilitleme, promotional trial başlatmak için server-side sahte sinyal, server transaction verification/webhook zincirini yeniden tasarlama, yeni ödeme sağlayıcısı, pricing A/B engine, push kampanyası, prod feature activation, remote staging/gerçek cihaz testini tamamlandı sayma.

**Uygulanacak rollout durumları:** `DISABLED` (default: hiçbir contextual entry gösterilmez), `PREVIEW_IN_TEST` (test config/mocklar; end-user rollout yok), `ENABLED_BY_OWNER` (sonraki sürüm/owner gate ile ve yalnız kanıt sonrası). Testlerde controlled flag override; production `application.yml` default false.

---

## 4. Katman bazlı uygulama spesifikasyonu

### 4A. DB — `V065` (Flyway/PostgreSQL)

1. V004/V005 gerçek tablolarını ve constraint isimlerini oku; **if-not-needed skip** yaklaşımı: sadece ihtiyaç olan additive nesneleri oluştur. Yeni migration `V065__sprint_10_1_paywall_offer_catalog.sql` (sabit sürüm; mevcut kayıt varsa çakışmayı explicit blocker yap). V004/V005'i değiştirme.
2. Önerilen yapı (mevcut tablolarla isim çatışmasını önleyerek): `billing_offer_catalog` (stable offer code, `product_id`, store, billing period enum/string, offer family, `presentation_tier`, locale eligibility/marketing copy ID, enabled flag, starts/ends, immutable identity, created/updated; **fiyatı hardcode etme**) ve `billing_paywall_events` (random event ID, privacy-safe event kind, coarse context enum, non-PII screen key, timestamp, version, **`training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK(training_eligible = FALSE)`**, retention expiration). Gerekliyse per-locale mapping ayrı tablo. Kontratlara göre gereksiz tablo ekleme.
3. `UNIQUE(store, product_id, offer_family)` gibi gerçek mevcut çoklulukla çelişmeyen unique constraints; stable offer identifier uniqueness; check için `locale IN ('tr-TR','en-US')` uygulama katmanında veya normalize locale table; bound timestamps için `expires_at > starts_at`; productId format için injection-safe uzunluk; active interval ve lookups için hedefli indeksler. SQL check ve migration Testcontainers testleri.
4. `trial` süresini veya parasal tutarı veritabanında gerçek Apple fiyatı gibi göstermeye çalışma. Apple `Product` + subscription offer metadata **display authority**; backend yalnız whitelist/eligibility ve product kimliği/period/family/marketing asset mapping'i döndürür. Locale varyantında belirsizlik varsa `offers=[]` ve degrade reason, asla örnek fiyat.
5. `billing_paywall_events` yalnız gerçekten gerekiyorsa: kullanılmayan telemetry kayıtlarını sırf veri toplamak için üretme. Metrics için Micrometer varsa gereksiz event tablosu kurma; retention gerekçesi gerektiren her persisted event için yaşam döngüsü zorunlu.
6. `BillingLifecycleContributor`: KVKK export: özneyle ilişkili entitlement/billing kayıtlarının var olan export formatında PII minimizasyonlu kapsamı; child-first delete: foreign key sırasına uygun, idempotent ve transaction-safe; retention: minimum yasal/muhasebe zorunluluğu ile silinmesi gereken telemetry ayrımı; signed transaction/finansal kayıtların hukuki saklama gerekçesini koddan silerek yok etme. Test: iki kullanıcı, ilişkili kayıtlar, export, subject deletion, retention boundary, referential integrity, no cross-account leak. `training_eligible = FALSE` sütunu ve export güvenliği sabit.
7. Prod veri `UPDATE`/backfill zorunluysa idempotent, bounded ve immutable history ilkesi; eski `PRO_PLUS` değerini `COACH_PRO`'ya mass rewrite **yapma**.

### 4B. Backend — Domain, config, API, observability

**Mevcut Java/Spring stiline uyarak `com.verifiedai.billing` içinde** kodla; mevcut servis/controller adlarıyla çakışan paralel façade yaratma. Önce mevcut `AppleProductCatalog`, `AppleBillingProperties`, `EntitlementApplicationService`, `AppleBillingApplicationService`, `CapabilityAccessPolicy`, `EntitlementResponse`, `EntitlementController`, `AppleBillingController`, `BillingLifecycleContributor`, repo/JPA mapping'ini karşılaştır.

- `BillingOffer`, `OfferFamily`, `OfferBillingPeriod`, `OfferPresentation`, `OfferEligibility`, `BillingOfferCatalogService`, `TierResolutionPolicy`, `EntitlementV2Response` gibi **işlevsel olarak gerekli** tipler; isimleri mevcut architecture conventions'a göre seç. DTO immutable/validation aware, model ve API ayrı; mapping tek yerde.
- `verifiedai.billing.contextual-paywall-enabled` config property **false default**. `@ConfigurationProperties` ile eşle, uygulama config importu doğru; flag absent/null/malformed -> fail closed, startup/health logging PII'siz. **Flag kapalı olması legacy entitlement/restore endpointini kapatmamalı.** Sadece newly exposed offer surface ve contextual paywall invitation/analytics gating etkilenmeli. Endpoint ihtiyacı için disabled = empty, predictable response veya bilinçli 404/feature-disabled; tek seçim ve schema fixture ile sabitle. Önerilen: 200 `offers: []`, `enabled: false`, `reason: FEATURE_DISABLED`; müşteri açısından fiyat yanılsaması yaratmaz.
- Katalog doğrulaması: sıfır/tekrarlı/çakışan SKU, tanımsız tier/family, locale eksikliği, başlangıç/son tarih tutarsızlığı, satılmayan ürünü aktif gösterme, trial olmayan üründe trial marketing gibi hatalar **fail-closed**; log yalnız configuration key + reason, secret yok. Eksik EN veya TR, yanlış locale veya invalid App Store product config -> ilgili offer gizlenir; read API 5xx yerine güvenli degrade; yanlış locale -> net validation 4xx veya documented fallback (`tr-TR`/`en-US` dışına allowlist uygulayarak), tek karar. Tavsiye: unsupported locale 400 validation code; `Accept-Language` ikincil olabilir, explicit param öncelikli.
- **API:** `GET /v1/billing/offers?locale=tr-TR|en-US`; `GET /api/v1/billing/offers?...` tek handler/service. `GET /v1/billing/entitlements` ve `GET /api/v1/billing/entitlements` tek handler; mevcut `/api/v1/me/entitlements` legacy response'u aynı tut. `/api/v1/billing/apple/**` purchase/restore/webhook route ve imza/authorization korunur. Alias route'a security bypass yok; offers public ise sadece public-safe catalog fields, entitlement authenticated user-scoped. `me` ID client'ından alma; security principal. JSON 2xx + expected error envelope, 401/403, cache semantics, no-store on personalized entitlement, JSON locale negotiation açık olsun.
- **Price authority:** server tarafı para birimi ve localized Apple price string üretme; `Product.displayPrice`, `Product.subscription.subscriptionPeriod`, `Product.subscription.introductoryOffer` ile gerçek cihaz lokalizasyonuna göre legal display. Backend katalog `productId`, family, schedule, benefits copy ids gibi price-independent bilgiler döndürür. Catalog cached olabilir ama entitlement ve personalized eligibility response `Cache-Control: private, no-store`.
- **Subscription truth source:** transaction doğrulanmadan premium açma; mevcut server verify/webhook idempotency ve revocation/refund/cancellation/expiration zincirini koru. Client StoreKit verified transaction → mevcut güvenli server sync/validation → refresh entitlement; local transaction yalnız doğrulama-sürerken `pending`, guaranteed unlocked değil. Cancel = dönem sonu durumu, ani entitlement kaybı değil; refund/revoke için existing logic. Season Pass 6 ay gerçekten App Store product metadata'sına göre doğrulanmalı; kendi tarih hesabınla receipt'i geçersiz kılamazsın.
- **Jackson 3:** projede kullanılan `tools.jackson.*` importlarını ve module setup'ını koru, eski `com.fasterxml.jackson.*` örneklerini körlemesine ekleme. Kullandığın serializer/test builder'ları dependency gerçekten var mı doğrula. Java validation packages mevcut seviyeye göre.
- **Security:** auth/CORS/CSRF ve Apple webhook signed validation mevcut politika ile aynı; public offers endpointten plan dışında entitlements sızmaz. Owner başka hesabın entitlement'ını parametreyle göremez. Rate-limit abuse korumasını bozma, yeni global enforcement icat etme.
- **Micrometer metrics:** `paywall_viewed`, `trial_started`, `subscription_purchased`, `restore_purchases_tapped` event'leri **low-cardinality** enum etiketleriyle (surface enum, locale enum, offer family enum, result enum) kaydedilsin; `userId`, email, transactionId, SKU serbest metin, problem içeriği, IP, tam cihaz ID, raw receipt **tag olamaz**. Etkinlikler bir kez ve doğru olay anında: `viewed` görünürlük doğrulandığında, `trial_started` Apple verified transaction/signed trial confirmation sonrası, `subscription_purchased` verified confirmed sync sonrası (idempotent), `restore_purchases_tapped` gerçek kullanıcının tıklaması üzerine. Tüketici ölçüm adları mevcut metrics naming convention'a uysun; aynı event çift sayılmasın. Flag disabled ise paywall view metriği oluşmaz.
- **Modulith boundary:** Billing başka modülün repository/entity'sini doğrudan import etmesin; mevcut published interface/event contract ile bağla. `ModulithArchitectureTest` kısıtlarını koru. Yeni outbound LLM dependency yok.

### 4C. İkiz OpenAPI / JSON Schema / contract fixtures

1. `packages/contracts/openapi/public-api.yaml` ve `packages/openapi/verified-ai-core-api.yaml` dosyalarındaki sadece billing/entitlement paths, operations, schemas ve error refs'leri güncelle; endpoint eşleşmeleri ve response required/nullable/enum kuralları bitwise-semantic eşdeğer olsun. Existing operationIds ve legacy schema bağlantılarını kırma. 10.1 dokümanında `CNT-LOS-12` tablosuna açık karşılık koy.
2. `packages/schemas/` içinde mevcut naming'i izleyen **Draft 2020-12** `billing-offers-v1.schema.json` / `entitlement-v2.schema.json` (veya mevcut konvansiyonun en yakın isimleri) oluştur. Her şema `$schema`, `$id`, `additionalProperties` stratejisi, required, documented enum, date-time, locale pattern, amount-free offer UI metadata, disabled/degrade semantics içerir. Optional alanlar yanlış `null` üretmez; `legacyTier` mapping belirtilir.
3. Pozitif fixture: tr-TR monthly, en-US annual, 6-month season pass, disabled empty offers, old `PRO_PLUS` entitlement → effective COACH_PRO, old FREE, canceled-but-active, expired/revoked, restore. Negatif mutation: unsupported locale, duplicate SKU, unknown tier, invalid status/date, leaked PII, invalid required field, price spoofing/client entitlement, `training_eligible: true` if event payload in scope. Fixtures docs ile tutarlı.
4. `scripts/quality/validate_json_schemas.py` içindeki **yalnız** kayıt/dispatch bölümü ve gerekiyorsa `check_contracts.py` ilgili matcher'ı ekle; global unrelated fixtures değiştirme. Schema validators'ı çalıştırma; owner scriptine ekle.
5. Backward-contract snapshot testi: `/api/v1/me/entitlements` mevcut alan adları ve legacy tier değerleri bozulmadı; twin routes aynı semantic JSON (request route differences hariç), status/error/authorization aynı.

### 4D. iOS — Swift 6 / SwiftUI / StoreKit 2 / Concept B

**Hedef:** `apps/ios/VerifiedAI/Features/Subscription/` mevcut altyapısını genişlet; alternatif purchase engine veya çift observer başlatma. Mimaride mevcut test seams, DI ve `@MainActor` kullanımını koru.

- **Gerekli kod:** `ContextualPaywallSheetView.swift`, `PaywallTriggerCoordinator.swift` (name collision yoksa) ve mevcut `PaywallViewModel.swift`, `PaywallView.swift`, `StoreKitProductRepository.swift`, `StoreKitTransactionObserver.swift`, `EntitlementViewModel.swift`, `EntitlementAPI.swift`, `AppleBillingAPI.swift`, `CapabilityGate.swift` uyarlamaları. `PaywallTriggerCoordinator` bağımsız test edilebilir deterministic state machine olsun: `eligible, timerActive, timerUnknown, previouslyAcceptedFreeSolution, flagEnabled, explicitUserInitiated, source, cooldown` girdileri; `suppress(reason)` veya `present(offerContext)` çıktısı. Timer sırasında explicit tap bile paywall **açmaz**; güvenli ücretsiz akış korunur.
- **Active-timer bridge:** `ExamSessionView`, `ColdStartDiagnosticFlowView`, `ProveItSoloTwinView` içinde bulduğun gerçek `timer state`/countdown alanlarıyla tek source of truth üret. Yeni genel app-wide state framework kurma. `timerActive || timerUnknown` varsa coordinator presentation yok; navigation/sheet'ı async state değişse bile `dismiss` veya `reject` ile güvenli yönet. Sınavı duraklatmak/saatini sıfırlamak/paywall'a geçmek yasak. Existing accepted solution diagnosis/counterexample gizlenmez.
- **Feature flag:** backend config disabled sonucu client paywall trigger kapalı; network timeout/offline/parse error = disabled. Flag doğrulanmadan cached `enabled=true` ile paywall gösterme; display cache entitlement projection olabilir ancak yeni offer eligibility yetkisi değildir. UI'da offline `Restore Purchases` ve existing access mümkünse koru; sahte plan/fiyat oluşturma.
- **Product loading:** Backend offer whitelist'teki productId listesini `Product.products(for:)` ile yükle; `Product` metadata'dan **gerçek yerel para birimli fiyat**, month/year/6-month billing interval, trial duration, billed-after-trial terms, renewal disclosure türet. Unmatched/incomplete/multiple SKUs => disable affected CTA. Price unavailable -> '-' veya uydurma TRY fiyatı değil, açık 'Fiyat bilgisi yüklenemedi' ve satın almanın disabled durumu.
- **Purchase:** user initiated, explicit CTA, transaction update, verification, server sync, entitlement refresh, pending/cancel/declined/revoked/offline edge states ayrı. Unverified StoreKit transaction premium kazandırmaz. İşlem idempotent; duplicate taps disable; cancellation hata olarak korkutucu sunulmaz; restore button `AppStore.sync()` / mevcut repository restore işlevini kullanır, sonra server refresh; restore no purchase case anlaşılır. Cancellation management link via Apple-native subscription management only if existing flows support. StoreKit test config product IDs katalogla senkron.
- **Lifecycle:** `@MainActor` UI/state mutation, `Sendable` boundaries, `Task` cancellation, listener registration exactly once, onDisappear cleanup, transaction confirmation only verified. Main thread network blocking yok; `async` closures retaining `self` leak'e yol açmaz; Swift 6 strict concurrency warnings sıfır hedefi.
- **Legal/Apple 3.1.2:** plan adı, tam fiyat, para birimi, gerçek tahsilat dönemi, auto-renewal, toplam fiyatın nasıl doğduğu, intro/trial şartları ve **varsa** trial bitişi/sonraki tahsilat bilgisi; `Restore Purchases` her zaman keşfedilebilir; `Terms of Use` ve `Privacy Policy` geçerli mevcut URL'lere gider. Trial hatırlatıcısı yalnız bildirim izni/gerçek mekanizma varsa pazarlanır; sunulmayan notification promised gibi yazılmaz. `YKS Season Pass` otomatik yenileniyorsa öyle, non-renewing ise öyle anlat; ürün tipi App Store config gerçeklerinden türetilir. Hiçbir trial varsayılmaz.
- **Concept B UI:** tek tip güçlü tipografi, flat neutral surfaces, yüksek kontrast, düzenli spacing, minimal border. **Gradient, shadow, `glassEffect`, glassmorphism, sahte glass blur, shimmering CTA yok.** 320pt viewport'ta yatay taşma yok; scrollable content, sticky CTA yalnız VO/AX3 engellemiyorsa. `xSmall`–`AX3`, Bold Text, Reduce Motion, VoiceOver rotor/focus order, full price/period/trial content tek okunabilir erişilebilir label, button hint ve state announcements, minimum 44pt tap targets, semantics, light/dark mod testleri.
- **Localization:** `Localizable.xcstrings` tam EN/TR; price hardcoded string değil `Product.displayPrice`; pluralization/period localization gerçek API'ye göre; copy IDs monotonic; yasal metinler otomatik kırpılmaz; `project.pbxproj` kaynak üyeliği repo'nun Xcode build sistemiyle uyumlu kaydedilir (sadece ilgili sections). İngilizce placeholder'ın Türkçe ekranda kaçması kabul edilmez.
- **Re-entry / cooldown:** genuine intent olan kullanıcı menüden abonelik ekranına ulaşabilsin, ancak contextual upsell kullanıcıyı çözümler arasında sıkıştırmasın; çoklu tekrar/trial baiting yok. Token/kredi bakiyesini satın alma yapmadan güncelleme.

### 4E. Dokümantasyon ve manifest

Yalnız ilgili hedefleri güncelle: Sprint 10.1 kanonik tasarım belgesi, Phase 10 geçiş/kapılar, product `05_MONETIZATION_AND_ENTITLEMENTS.md`, roadmap `88...`, architecture `90...` yalnız `CNT-LOS-12`, iOS security doc yalnız StoreKit/Screen 12, domain invariants `INV-MON-001`. İlgili kanonik katalog dosyası gerçekten mevcutsa `DOCUMENTATION_MANIFEST.md` kaydını yeni dokümanlarla ve değişiklik checksum/relative path conventions ile senkronize et; manifest formatını tahmin etme.

**Yeni üç dosya, aynı Sprint 10.1 dizininde:**

1. `SPRINT_10.1_IMPLEMENTATION_MAP.md` — verified base HEAD, entry evidence, ADR kararları, dosya/dependency haritası, route/schema matrisi, feature flag, migration ve rollback planı, Scope In/Out.
2. `SPRINT_10.1_EXECUTION_REPORT.md` — gerçekten değişen dosyalar, owner test komutları, testlerin **NOT_RUN_OWNER_REQUIRED**, bilinen risks/gates, QA owner action list. Hiçbir otomatik PASS iddiası yok.
3. `SPRINT_10.1_ACCEPTANCE_MATRIX.md` — kategori bazlı A–F test vaka sayıları, komut, kanıt ref'i, expected result, `NOT_RUN_OWNER_REQUIRED`, blockers ve physical device/staging pending. **Sadece owner kanıtı geldiğinde** bir hücre PASS olabilir.

Docs sprint geçişini “implementation prepared, owner evidence pending” olarak yaz; 9.10 exit report'unu yeniden yorumlayıp remote staging kanıtını üretme.

---

## 5. Kategorik test ve kabul matrisi — A–F

Aşağıdaki test kaynaklarını **oluştur/güncelle**, **çalıştırma**. Gerçek repo klasör/test naming ve dependency sürümlerine uyarla; varsayımsal Maven/Xcode target isimleri ile komut verme.

| Grup | Örnekler ve zorunlu assertions | Kabul sınırı |
|---|---|---|
| **A — Domain / Backend / Modulith** | Tier projection `FREE/PRO/PRO_PLUS→COACH_PRO`, separate YKS offer family, revoked/expired/canceled, fail-closed offers, locale allowlist, duplicate catalog SKU, business invariants, no LLM, module dependency boundaries, telemetry cardinality | Eski entitlement değişmez; `ModulithArchitectureTest` green owner verified |
| **B — PostgreSQL / Testcontainers / KVKK** | V004/V005 → V065 clean migration; existing records survive; repeat migrate no duplicate; product/check/unique constraints; `training_eligible=false`; 2-user export isolation, child-first delete, retention cutoff, yasal saklama ayrımı | No data loss, no cross-user export, no orphan rows |
| **C — MockMvc / API / RBAC** | Legacy endpoint snapshot; `/v1` and `/api/v1` twin equivalence; offers disabled 200 empty; authorized entitlement, no-token 401, foreign-user blocked; locale invalid; no-store, error contract, verified restore/purchase backward compatibility | No auth bypass, no breaking API, no forged price |
| **D — iOS / StoreKitTest / A11y** | Monthly, annual, 6-month season product; successful purchase; restore; cancel; pending; refund/revoke; offline product load; unverified transaction; duplicate tap; server failure; mid-exam/mid-diagnostic/mid-twin suppression; 320pt, Dynamic Type xSmall..AX3, VoiceOver, EN/TR, legal links, flat Concept B | No paywall during live timer, no free diagnosis hostage, production default disabled |
| **E — Contracts / Validators** | Draft 2020-12 schema valid+invalid fixtures; twin OpenAPI drift checks; legacy `/me` contract; `CNT-LOS-12`; EN/TR required key parity; correct project membership | Both specs semantic parity; invalid fixtures fail validation |
| **F — Release / Reporting / Regression** | Acceptance scripts exit nonzero on missing prereqs, report truthful status, no code/test runs by agent, feature flag disabled config, privacy metrics no PII, zero LLM billing changes, no commit/push/deploy; original 9.10 PENDING preserved | No rollout without staging P95 + physical VO owner proof |

**Test implementation notes:** Testcontainers requires owner Docker; do not fall back to silently skipped DB integration test and claim PASS. StoreKitTest only simulator/owner Xcode support; physical VoiceOver acceptance cannot be inferred from simulator success. No data backfill on prod in tests. Use deterministic fake clock/store/signed transaction fixture (no real Apple credentials). Add explicit tests where malformed offer feeds fail closed and matching localized `Product` data displays actual price.

### Owner script 1 — `scripts/quality/sprint_10_1_owner_acceptance.sh`

- `#!/usr/bin/env bash`, `set -euo pipefail`; run from repo root reliably. Preserve existing Sprint 9.10 acceptance script conventions and standard environment setup.
- Preflight: required Java/Maven wrapper, Python validator, Docker availability for Testcontainers; missing dependencies fail with actionable stderr; no hidden skip-to-green.
- Owner-chosen command sequence: scoped API unit/Modulith/MockMvc integration tests; Testcontainers privacy and migration; JSON Schema/OpenAPI twins; docs validation; optionally call sprint 10.1 Swift check separately. Respect existing test naming and Maven selectors; no `-DskipTests`, no blanket ignore fail.
- Print per-gate `PASS/FAIL/NOT_RUN` only from actual process exit codes; summary cannot show PASS if any required gate wasn't executed. Explicitly name manual staging P95 and physical VoiceOver `PENDING_MANUAL` regardless of local status; exit nonzero for required local gates failure, but manual gates remain report status pending.
- No `git commit`, push, deploy, migration against live database, Apple network purchases, or secret inspection.

### Owner script 2 — `scripts/quality/sprint_10_1_swift_check.sh`

- Reuse Sprint 9.10 Swift script's tool discovery, scheme, device/simulator destination pattern and output style. Simulator build + selected Subscription Swift 6 tests/StoreKitTest and localization/asset registration checks; no physical device evidence claims.
- `xcodebuild` commands assembled from **actual** project/workspace/scheme from existing 9.10 script; flags for strict concurrency mirror current project. If Xcode/StoreKit testing unsupported on owner machine, explicit `BLOCKED_ENVIRONMENT`, not fake pass.
- UI snapshot/A11y automation may be supplemented by manual owner matrix for 320pt, AX3, EN/TR, VoiceOver; never mark manual test pass just because test sources compiled.

### Final delivery: owner copy-paste commands

In your final answer give **ONE** fenced `bash` block without comments, made of exact owner commands appropriate to repo. Preferred structure after inspecting the 9.10 scripts:

```bash
bash scripts/quality/sprint_10_1_owner_acceptance.sh
bash scripts/quality/sprint_10_1_swift_check.sh
```

Do not claim this example is verified until actual scripts exist; replace with exact executable paths. Do not execute it yourself.

---

## 6. Uçtan uca senaryolar — acceptance Gherkin

1. **Given** accepted FREE question, **when** diagnosis/counterexample becomes available, **then** it remains fully readable independent of paywall state or later entitlement changes.
2. **Given** timed exam/diagnostic/solo twin is actively counting down, **when** eligibility or purchase CTA triggers, **then** no paywall appears; countdown remains unaffected. Unknown timer state also denies contextual display.
3. **Given** default `contextual-paywall-enabled=false`, **when** any contextual trigger fires, **then** no sheet is presented and no `paywall_viewed` event is recorded; legacy restore endpoint still works.
4. **Given** valid offer list, **when** StoreKit returns actual localized product, **then** price/period/trial/legal content is complete and matches StoreKit; no hardcoded backend price string.
5. **Given** absent or conflicting StoreKit SKU, **when** the paywall loads, **then** that offer purchase button is disabled with accessible graceful explanation, not a fake offer.
6. **Given** old `PRO_PLUS` entitlement, **when** V2 endpoint is read, **then** effective coaching capabilities survive and legacy endpoint still says `PRO_PLUS` as before.
7. **Given** 6-month season offer, **when** an Apple-verified transaction sync succeeds, **then** entitlement is derived from actual transaction validity, offering coaching capabilities without indefinite permission.
8. **Given** purchase is pending/offline/unverified, **when** UI refreshes, **then** it never grants premium solely on a client claim.
9. **Given** restore with old purchase, **when** sync confirms a valid entitlement, **then** state refreshes idempotently with no duplicate event or new charge.
10. **Given** invalid locale, duplicate SKU, mismatched family or privacy-unsafe metric tag, **when** input is processed, **then** safely rejected/filtered with clear testable error or omission, without PII.
11. **Given** 320pt and AX3 on EN/TR, **when** Screen 12 renders, **then** all legal/price details and Close/Restore controls remain reachable and readable in ordered VoiceOver focus.
12. **Given** Sprint 9.10 staging P95 and physical VO not owner verified, **when** documents/flags are prepared, **then** gate state stays `PENDING` and release flag remains false.

---

## 7. Implementation order and quality stop conditions

**Sequence (no tests executed):**

1. Record HEAD and scoped evidence (A–D); identify actual files, types, routes and product IDs. Produce `IMPLEMENTATION_MAP` draft including ADR verification notes.
2. Add **contract snapshots and backward-compatibility tests first** as code, then DTO/schema/twin routes. Do not run tests.
3. Implement V065 only if needed; audit V004/V005 checks and privacy lifecycle; add DB/Testcontainers tests.
4. Implement fail-closed config, tier projection, catalog and APIs; bind metrics and secure principal; add unit/MockMvc/Modulith tests.
5. Implement iOS StoreKit catalog view-model/coordinator and actual timer state integration; add StoreKitTest and UI/accessibility tests; update localization and project membership.
6. Update docs, manifest, script templates, owner acceptance matrix. Static review source diffs for compiled signature consistency, JSON structure, SQL syntax and import issues **without running any check/tool build**.
7. Report actual files/risks/blockers. Stop without commit/push/deploy.

**Hard stop / P0 blocker:** cannot identify canonical existing Apple purchase verification contract; unresolvable V065 collision; legacy entitlement read semantics would break; Screen 12 would display during timer; a paid offer could show fictional Apple price; free accepted diagnosis would be withheld; privacy export/delete cannot meet legal retention. In P0 case, finish safe partial code if possible, preserve disabled flag, flag incomplete work precisely, do not fake completion.

**Compatibility principles:** no destructive schema migration, no breaking `PRO_PLUS` enum persistence, no unauthorized entitlement, no client-supplied unverified tier, no PII metrics, no manual gate PASS inflation, no drift between `/v1` and `/api/v1` aliases, no Scope creep into Sprint 10.2/10.3, no global unrelated formatting.

---

## 8. Final report contract (agent reply)

Return in Turkish, clear and verifiable:

1. **Baseline:** HEAD full SHA, dirty/clean initial working tree, observed Sprint 9.10 local acceptance ID and **staging P95 / physical VoiceOver PENDING**.
2. **Architecture:** ADR-101-01..10 decisions applied, actual tier mapping and exact real product IDs **only if observed** (never invented), selected offer-disabled API behavior, exact endpoint authorization.
3. **Changed files:** full relative path list grouped DB, Java, OpenAPI/schemas, iOS, scripts, docs. Do not omit added files.
4. **Manual test commands:** a single shell code block, comment-free, owner runnable. No claims of test execution.
5. **Acceptance matrix summary:** A–F all `NOT_RUN_OWNER_REQUIRED` by default; unexecuted gates not PASS. Report manual device/staging gate `PENDING_MANUAL` separately.
6. **Known risks:** unverified Apple Connect offer IDs, store metadata, hardware/CI, migrations, privacy retention uncertainties, unresolved legacy semantics. Each risk has impact, mitigation and owner acceptance required.
7. **Change control:** explicit `NO_COMMIT`, `NO_PUSH`, `NO_DEPLOY`; confirmation that `verifiedai.billing.contextual-paywall-enabled=false` remains; $0.00 LLM incremental spend; no alteration of Sprint 9.10 proofs.

**Definition of Done (implementation-level):** Source code, tests, contracts, documentation and acceptance scripts written consistently, safe default false, no knowingly breaking legacy data. **Production release readiness is NOT achieved until owner executes tests, supplies staging P95 and physical VoiceOver evidence, validates App Store commercial metadata and deliberately authorizes rollout.**

---

## 9. Kaynak yerleri / denetlenebilir kaynak izi

- GitHub main commit listesi: https://github.com/coskunburak/verified_ai/commits/main/
- Başlangıç Sprint 9.10 SHA: https://github.com/coskunburak/verified_ai/commit/36566f9f2f2709c63a7900681cd851314b3c1a27
- Kanonik dosya yolları Bölüm 1'de tek tek verilmiştir. Kodlama ajanı girişte **repo checkout'tan** içerik ve gerçek satır/sınıf adlarını doğrulayacak; ham dosyaları bu promptun hazırlanma oturumunda bütünüyle doğrulanmış varsaymayacaktır.

**Son talimat:** Kapsamı aşmadan kodu yaz. Test/build/validator çalıştırma. Commit/push/deploy yapma. Owner için komutları ve dürüst kabul matrisi kanıtlarını teslim et. `INV-MON-001`, feature flag fail-closed, Apple verified pricing ve timer suppression bu sprintin vazgeçilmezleri.
