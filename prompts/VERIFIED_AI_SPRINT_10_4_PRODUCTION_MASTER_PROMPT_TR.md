# VERIFIED AI — SPRINT 10.4 PRODUCTION-LEVEL MASTER PROMPT (TR)
**Faz:** Phase 10 — Monetization, Growth, and Product Operations  
**Sprint:** Sprint 10.4 — Notification Infrastructure and Preference Center  
**Baz Hat:** Sprint 10.1 (`V065`) + Sprint 10.2 (`V066`) + Sprint 10.3 (`V067`) Tamamlanmış ve Owner Yerel Kabul Kapılarından Geçmiş Durumda  
**Hedef Migration:** `V068__sprint_10_4_notification_infrastructure_and_preferences.sql`  
**Çalışma Modu:** `OWNER_RUNS_ALL_TESTS | NO_COMMIT | NO_PUSH | NO_DEPLOY | ZERO_SECRETS`

---

## 0. ROL, YÜRÜTME KURALLARI VE GÜVENLİK SINIRLARI

Sen **Principal Distributed Systems Architect**, **Staff Spring Boot 4 / Spring Modulith & PostgreSQL Engineer**, **Staff iOS (Swift 6 Strict Concurrency / SwiftUI / UserNotifications & APNs) Architect** ve **Privacy/Compliance (GDPR/KVKK)** uzmanısın.

### Mutlak Yürütme Kuralları
1. **OWNER_RUNS_ALL_TESTS:** Maven/Gradle testlerini, Docker/Testcontainers kabul betiklerini veya `xcodebuild` komutlarını sen çalıştırıp uzun loglar üretme (yalnızca statik/sentaks kontrolü: `py_compile`, `bash -n`, `plutil -lint`, `docs_check.py` gibi hafif doğrulamalar yap); tüm testleri ve kabul betiklerini repo sahibinin (`owner`) tek komutla çalıştırabileceği şekilde hazırla.
2. **NO_COMMIT | NO_PUSH | NO_DEPLOY:** `git commit`, `git push`, tag oluşturma, GitHub Actions tetikleme veya deploy işlemi **KESİNLİKLE** yapma. Mevcut çalışma ağacındaki değişiklikleri koru.
3. **ZERO_SECRETS:** `.env`, `.env.*`, `application-local.yml`, `.p8`/`.p12` APNs sertifikaları, keychain veya canlı API anahtarı içeren hiçbir dosyayı okuma, loglama veya koda gömme. Tüm örneklerde ve testlerde yalnızca açıkça sentetik placeholder (`local_dev_apns_token_placeholder`, `synthetic-device-token`) kullan.
4. **NO_REGRESSION (Sprint 10.1 / 10.2 / 10.3 Koruması):**
   - `V065`, `V066` ve `V067` migration dosyaları **ASLA** değiştirilmeyecektir (Flyway checksum değişmezliği).
   - `Sprint103QuotaMigrationTest.java` içindeki `V066 -> V067` yükseltme adımında `.target("67")` yoksa (`.load().migrate()` doğrudan en son sürüme gidiyorsa) `V068` eklendiğinde kırılmaması için yalnızca `.target("67")` sabitlemesini ekle; başka Sprint 10.3 davranışını değiştirme.
   - Spring Modulith **`NO_FIELD_INJECTION`** kuralına kesinlikle uy: tüm yeni sınıflarda constructor injection kullan; mevcut servislere bağımlılık eklenecekse `@Autowired(required = false)` setter enjeksiyonu kullan.
   - `VerifiedAiApplication.java` üzerine global `@EnableScheduling` ekleme; zamanlanmış bildirim dağıtım worker'ı yalnızca `verifiedai.notification.dispatch-worker-enabled=true` iken şartlı (`@ConditionalOnProperty`) aktif olmalıdır.
   - **$0.00 LLM Maliyeti:** Sprint 10.4 kapsamında **hiçbir** AI/LLM sağlayıcı çağrısı eklenmez; tüm kararlar deterministik sunucu politikasıyla verilir.
5. **Kapsam Sınırı:** Sprint 10.5 (Lifecycle kampanya tetikleyicileri / çalışma planı ve deneme kampanyası içerik kuralları) ve Sprint 10.9 (viral paylaşım / referral döngüleri) kapsamını Sprint 10.4 içine taşıma. Sprint 10.4 yalnızca **tipli bildirim niyetleri, tercih merkezi, sessiz saatler, cihaz token yaşam döngüsü, idempotent zamanlama/dağıtım altyapısı ve GDPR/telemetri** omurgasını kurar; Sprint 10.5'in tüketeceği temiz `@NamedInterface("application")` SPI portunu yayınlar.

---

## 1. SEÇİCİ CONTEXT (ALLOWLIST) — TOKEN BÜTÇESİ KURALI

Tüm repoyu tarama (`find`, geniş `grep`) veya Phase 1–9 içindeki ilgisiz modülleri (`problem`, `solving`, `verification`, `attempt`, `mastery`, `mistake`, `exam`, `tutoring`, `services/math-verifier`, `evaluations`) context'e alma. Yalnızca aşağıdaki dosyalara odaklan:

### A. Kanonik Dokümanlar (Salt-Okunur)
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.4_NOTIFICATION_INFRASTRUCTURE_AND_PREFERENCE_CENTER.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.5_LIFECYCLE_MESSAGING_AND_LEARNING_RELEVANT_RE_ENGAGEMENT.md` *(yalnızca Sprint 10.4'ten beklenen SPI sınırı için)*
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/PHASE_10_MONETIZATION_GROWTH_AND_PRODUCT_OPERATIONS.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.3_IMPLEMENTATION_MAP.md`
- `docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.3_EXECUTION_REPORT.md`
- `docs/verified_ai_learning_platform_docs_v4/data/23_DATA_LIFECYCLE_RETENTION_AND_AUDIT.md` *(silme, retention, PII ve export sınırları)*
- `docs/verified_ai_learning_platform_docs_v4/architecture/14_API_DESIGN_AND_CONTRACTS.md` *(`/api/v1`, `Idempotency-Key`, `ProblemDetails`)*
- `docs/verified_ai_learning_platform_docs_v4/roadmap/88_LEARNING_OS_MONETIZATION_GROWTH_AND_VALIDATION_PLAN.md` *(Section 6 — 48 saatlik trial-end hatırlatıcısı)*

### B. Backend Dayanak Noktaları
- `services/api/pom.xml`
- `services/api/src/main/java/com/verifiedai/VerifiedAiApplication.java`
- `services/api/src/main/java/com/verifiedai/notification/package-info.java` *(mevcut boş Spring Modulith modülü)*
- `services/api/src/main/java/com/verifiedai/configuration/SecurityConfiguration.java`
- `services/api/src/main/java/com/verifiedai/configuration/PlatformConfiguration.java`
- `services/api/src/main/java/com/verifiedai/sharedkernel/privacy/AccountDataLifecycleContributor.java`
- `services/api/src/main/java/com/verifiedai/sharedkernel/security/AuthenticatedUser.java`
- `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiErrorCode.java`
- `services/api/src/main/java/com/verifiedai/sharedkernel/error/ApiProblemException.java`
- `services/api/src/main/java/com/verifiedai/billing/application/BillingLifecycleContributor.java` *(GDPR export/delete deseni)*
- `services/api/src/main/java/com/verifiedai/profile/application/LearningProfileApplicationService.java` *(yalnızca `validateTimezone` / `ZoneId` doğrulama deseni)*
- `services/api/src/main/resources/application.yml` *(yalnızca `verifiedai:` bloğu)*
- `services/api/src/test/java/com/verifiedai/architecture/ModulithArchitectureTest.java`
- `services/api/src/test/java/com/verifiedai/billing/application/Sprint103QuotaMigrationTest.java` *(Flyway `.target("67")` kontrolü)*

### C. Sözleşmeler, iOS & Kalite Betikleri
- `packages/contracts/openapi/public-api.yaml` ve `packages/openapi/verified-ai-core-api.yaml` *(çift ayna konvansiyonu)*
- `apps/ios/VerifiedAI/App/AppDependencies.swift`
- `apps/ios/VerifiedAI/App/RootView.swift`
- `apps/ios/VerifiedAI/App/DeepLinkRouter.swift`
- `apps/ios/VerifiedAI/Features/Account/Presentation/AccountSettingsView.swift`
- `apps/ios/VerifiedAI/Features/Account/Presentation/AccountSettingsViewModel.swift`
- `apps/ios/VerifiedAI/Core/DesignSystem/Components/DesignSystemComponents.swift`
- `apps/ios/VerifiedAI/Core/Networking/Endpoint.swift` ve `HTTPRequest.swift`
- `apps/ios/VerifiedAI/Resources/Localizable.xcstrings` *(örnek `billing.*` / `account.*` yapısı)*
- `apps/ios/VerifiedAI.xcodeproj/project.pbxproj` *(yalnızca `Subscription` / `Account` referans satırları)*
- `scripts/quality/validate_sprint_10_3_contracts.py`, `scripts/quality/validate_json_schemas.py`, `scripts/quality/sprint_10_3_owner_acceptance.sh`, `scripts/quality/sprint_10_3_swift_check.sh`, `scripts/quality/docs_check.py`

---

## 2. KANONİK DOMAIN MODELİ, DURUM MAKİNESİ VE DEĞİŞMEZLER

### 2.1 Tipli Kategoriler ve Bildirim Niyetleri
- **`NotificationCategory`:**
  - `STUDY_PLAN`: Günlük çalışma planı hatırlatıcıları (`defaultOptIn = false`, kullanıcı opt-in gerektirir).
  - `SPACED_REVIEW`: Aralıklı tekrar kuyruğu hatırlatıcıları (`defaultOptIn = false`).
  - `EXAM_DEADLINE`: Sınav geri sayım kilometre taşları (`defaultOptIn = false`).
  - `BILLING_TRIAL_REMINDER`: 7 günlük deneme bitiminden 48 saat önce bilgilendirme (`roadmap/88` Section 6, `defaultOptIn = false`, tek tıkla opt-in).
  - `ACCOUNT_SECURITY`: Hesap güvenliği / silme / kritik hesap durumu (`optOutAllowed = false`, sistem kritik bildirimi).
- **`NotificationIntentType` (her biri sabit bir `NotificationCategory` ile eşlenir):**
  - `STUDY_PLAN_DAILY_REMINDER` -> `STUDY_PLAN`
  - `SPACED_REVIEW_DUE` -> `SPACED_REVIEW`
  - `EXAM_COUNTDOWN_MILESTONE` -> `EXAM_DEADLINE`
  - `TRIAL_CONVERSION_REMINDER` -> `BILLING_TRIAL_REMINDER`
  - `ACCOUNT_SECURITY_NOTICE` -> `ACCOUNT_SECURITY`

### 2.2 Yasal Teslimat Durum Makinesi (`NotificationIntentStatus`)
- Durumlar: `SCHEDULED`, `DISPATCHING`, `DELIVERED`, `SUPPRESSED_OPT_OUT`, `SUPPRESSED_QUIET_HOURS`, `SUPPRESSED_RATE_CAP`, `FAILED_RETRYABLE`, `FAILED_TERMINAL`, `CANCELLED`.
- Yasal geçişler:
  - İlk oluşturma: `SCHEDULED` veya politika gereği doğrudan `SUPPRESSED_OPT_OUT` / `SUPPRESSED_RATE_CAP`.
  - `SCHEDULED` -> `DISPATCHING` | `SUPPRESSED_OPT_OUT` | `SUPPRESSED_QUIET_HOURS` | `SUPPRESSED_RATE_CAP` | `CANCELLED`
  - `DISPATCHING` -> `DELIVERED` | `FAILED_RETRYABLE` | `FAILED_TERMINAL` | `SUPPRESSED_OPT_OUT` | `SUPPRESSED_QUIET_HOURS` | `SUPPRESSED_RATE_CAP`
  - `FAILED_RETRYABLE` -> `DISPATCHING` | `FAILED_TERMINAL` | `CANCELLED`
  - Terminal durumlar (`DELIVERED`, `SUPPRESSED_OPT_OUT`, `SUPPRESSED_QUIET_HOURS`, `SUPPRESSED_RATE_CAP`, `FAILED_TERMINAL`, `CANCELLED`): geri dönüşsüz (hiçbir geçişe izin verilmez).
- Yasa dışı her durum geçişi explicit domain hatasıyla (`ApiErrorCode.NOTIFICATION_ILLEGAL_TRANSITION`, HTTP `409 Conflict`) reddedilir.

### 2.3 Kırılmaz Domain Değişmezleri (Invariants)
1. **`INV-NOTIF-001` (Açık Opt-In & Anti-Spam):** Varsayılan tercihlerde tüm öğrenme ve abonelik-hatırlatma kategorileri (`STUDY_PLAN`, `SPACED_REVIEW`, `EXAM_DEADLINE`, `BILLING_TRIAL_REMINDER`) **kapalıdır (`false`)**. Tercih kaydı henüz yoksa `NotificationPreferencePolicy` öğrenme bildirimlerini bastırır (`SUPPRESSED_OPT_OUT`); kullanıcı açıkça açmadan asla gönderim yapılmaz.
2. **`INV-NOTIF-002` (Saat Dilimi Duyarlı Quiet Hours & Erteleme):** Kullanıcı yerel zaman dilimi (`ZoneId`, varsayılan `Europe/Istanbul`) ve sessiz saat aralığı (`quiet_hours_start_local`..`quiet_hours_end_local`, varsayılan `22:00`–`08:00`) ile değerlendirilir. Gece yarısını aşan aralıklar (`22:00` → `08:00`) ve DST geçişleri deterministik `QuietHoursPolicy` ile hesaplanır:
   - Quiet hours içindeki öğrenme niyetleri ya kullanıcı politikasına göre sessiz saat sonuna ertelenir (`deliver_after = nextQuietWindowEndUtc`) ya da hemen gönderim zorunluysa (`allowDefer = false`) `SUPPRESSED_QUIET_HOURS` olarak kapanır.
   - `ACCOUNT_SECURITY` kritik bildirimleri quiet hours'tan muaftır (yalnızca güvenlik olaylarında).
3. **`INV-NOTIF-003` (Idempotent Zamanlama & Tekil Dağıtım):**
   - Her zamanlama isteği `(user_id, idempotency_key)` + `payload_hash` (SHA-256) ile tekilleştirilir. Aynı anahtar ve aynı payload geldiğinde mevcut intent idempotent olarak döner; aynı anahtar farklı payload ile gelirse `409 Conflict` (`ApiErrorCode.IDEMPOTENCY_KEY_REUSED`) döner.
   - Dağıtım işçisi `SELECT ... FOR UPDATE SKIP LOCKED` ile satırı atomik olarak `DISPATCHING` durumuna alır; paralel worker örnekleri aynı intent'i iki kez gönderemez.
4. **`INV-NOTIF-004` (Günlük Frekans Tavanı):** Kullanıcının yerel takvim gününde teslim edilen/dağıtılan kritik-olmayan bildirim sayısı `daily_cap` (varsayılan `2`, sunucu üst sınırı `5`) değerini aşarsa sonraki intent'ler `SUPPRESSED_RATE_CAP` olarak işaretlenir.
5. **`INV-NOTIF-005` (PII Minimizasyonu & `training_eligible = FALSE`):**
   - Tüm Sprint 10.4 tablolarında `training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT training_eligible)` kısıtı bulunur.
   - `payload_jsonb` içinde yalnızca tipli şablon parametreleri (örn. `{"deepLinkRoute":"spaced-review","dueCount":4}`) tutulur; ham soru metni, görsel URL'i, e-posta veya kişisel not asla saklanmaz.
   - Ham APNs device token'ı `notification_device_tokens` tablosunda `token_sha256` (64-hex tekil anahtar) ve sunucu dağıtım adaptörü için izole `device_token` olarak saklanır; **loglara, hata mesajlarına, metriklere ve GDPR `exportUserData` çıktısına ham `device_token` ASLA yazılmaz.**
6. **`INV-NOTIF-006` (GDPR Yaşam Döngüsü & Cihaz İptali):** `NotificationLifecycleContributor implements AccountDataLifecycleContributor` kullanıcı tercihlerini, cihaz metadata'sını (`token_sha256` ve ham `device_token` hariç), bildirim niyetlerini ve teslimat denemelerini dışa aktarır; `deleteUserData` çağrıldığında tüm bildirim kayıtlarını child-first sırayla siler (`ON DELETE CASCADE` ile çift güvence).

---

## 3. VERİTABANI MİMARİSİ: `V068__sprint_10_4_notification_infrastructure_and_preferences.sql`

> **Önemli:** `V001`–`V067` aralığı doludur. Migration dosya adı kesinlikle **`services/api/src/main/resources/db/migration/platform/V068__sprint_10_4_notification_infrastructure_and_preferences.sql`** olmalıdır.

```sql
-- V068__sprint_10_4_notification_infrastructure_and_preferences.sql
-- Sprint 10.4: Notification Infrastructure and Preference Center

CREATE TABLE IF NOT EXISTS notification_preferences (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    study_plan_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    spaced_review_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    exam_deadline_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
    trial_reminder_enabled  BOOLEAN NOT NULL DEFAULT FALSE,
    timezone                VARCHAR(64) NOT NULL DEFAULT 'Europe/Istanbul',
    quiet_hours_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_hours_start_local TIME NOT NULL DEFAULT TIME '22:00:00',
    quiet_hours_end_local   TIME NOT NULL DEFAULT TIME '08:00:00',
    daily_cap               INTEGER NOT NULL DEFAULT 2 CHECK (daily_cap BETWEEN 0 AND 5),
    training_eligible       BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT training_eligible),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_notification_preferences_user UNIQUE (user_id),
    CONSTRAINT ck_notification_quiet_hours_distinct CHECK (quiet_hours_start_local <> quiet_hours_end_local)
);

CREATE TABLE IF NOT EXISTS notification_device_tokens (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform          VARCHAR(16) NOT NULL CHECK (platform IN ('IOS')),
    environment       VARCHAR(16) NOT NULL CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    token_sha256      CHAR(64) NOT NULL CHECK (token_sha256 ~ '^[0-9a-f]{64}$'),
    device_token      VARCHAR(512) NOT NULL,
    app_version       VARCHAR(32) NULL,
    locale            VARCHAR(16) NOT NULL DEFAULT 'tr-TR' CHECK (locale IN ('tr-TR', 'en-US')),
    status            VARCHAR(24) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED', 'INVALID_TOKEN')),
    last_seen_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at        TIMESTAMPTZ NULL,
    training_eligible BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT training_eligible),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_notification_device_user_hash UNIQUE (user_id, token_sha256)
);

CREATE INDEX IF NOT EXISTS idx_notification_device_tokens_active
    ON notification_device_tokens (user_id, status, updated_at DESC)
    WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS notification_intents (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    idempotency_key    VARCHAR(128) NOT NULL,
    payload_hash       CHAR(64) NOT NULL CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    intent_type        VARCHAR(48) NOT NULL CHECK (intent_type IN (
                           'STUDY_PLAN_DAILY_REMINDER',
                           'SPACED_REVIEW_DUE',
                           'EXAM_COUNTDOWN_MILESTONE',
                           'TRIAL_CONVERSION_REMINDER',
                           'ACCOUNT_SECURITY_NOTICE'
                       )),
    category           VARCHAR(32) NOT NULL CHECK (category IN (
                           'STUDY_PLAN',
                           'SPACED_REVIEW',
                           'EXAM_DEADLINE',
                           'BILLING_TRIAL_REMINDER',
                           'ACCOUNT_SECURITY'
                       )),
    locale             VARCHAR(16) NOT NULL DEFAULT 'tr-TR' CHECK (locale IN ('tr-TR', 'en-US')),
    payload_jsonb      JSONB NOT NULL DEFAULT '{}'::jsonb,
    deep_link_route    VARCHAR(128) NOT NULL DEFAULT 'home',
    scheduled_for      TIMESTAMPTZ NOT NULL,
    deliver_after      TIMESTAMPTZ NOT NULL,
    status             VARCHAR(32) NOT NULL CHECK (status IN (
                           'SCHEDULED',
                           'DISPATCHING',
                           'DELIVERED',
                           'SUPPRESSED_OPT_OUT',
                           'SUPPRESSED_QUIET_HOURS',
                           'SUPPRESSED_RATE_CAP',
                           'FAILED_RETRYABLE',
                           'FAILED_TERMINAL',
                           'CANCELLED'
                       )),
    suppression_reason VARCHAR(64) NULL,
    attempt_count      INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_retry_at      TIMESTAMPTZ NULL,
    delivered_at       TIMESTAMPTZ NULL,
    correlation_id     VARCHAR(128) NULL,
    training_eligible  BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT training_eligible),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_notification_intents_user_idem UNIQUE (user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_notification_intents_dispatch_due
    ON notification_intents (deliver_after ASC, created_at ASC)
    WHERE status IN ('SCHEDULED', 'FAILED_RETRYABLE');

CREATE INDEX IF NOT EXISTS idx_notification_intents_user_daily
    ON notification_intents (user_id, delivered_at DESC)
    WHERE status = 'DELIVERED';

CREATE TABLE IF NOT EXISTS notification_delivery_attempts (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_id            UUID NOT NULL REFERENCES notification_intents(id) ON DELETE CASCADE,
    user_id              UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_token_id      UUID NULL REFERENCES notification_device_tokens(id) ON DELETE SET NULL,
    attempt_number       INTEGER NOT NULL CHECK (attempt_number > 0),
    provider             VARCHAR(32) NOT NULL CHECK (provider IN ('NOOP', 'APNS')),
    outcome              VARCHAR(32) NOT NULL CHECK (outcome IN (
                             'DELIVERED',
                             'RETRYABLE_ERROR',
                             'TERMINAL_ERROR',
                             'INVALID_DEVICE_TOKEN',
                             'SUPPRESSED'
                         )),
    provider_error_code  VARCHAR(64) NULL,
    latency_ms           BIGINT NOT NULL DEFAULT 0 CHECK (latency_ms >= 0),
    training_eligible    BOOLEAN NOT NULL DEFAULT FALSE CHECK (NOT training_eligible),
    attempted_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_notification_attempt_num UNIQUE (intent_id, attempt_number)
);

CREATE INDEX IF NOT EXISTS idx_notification_attempts_user
    ON notification_delivery_attempts (user_id, attempted_at DESC);
```

---

## 4. BACKEND MİMARİSİ (`com.verifiedai.notification` — JAVA 21 / SPRING BOOT 4 / SPRING MODULITH)

### 4.1 Feature Flags & Konfigürasyon (`application.yml` & `NotificationProperties`)
`verifiedai:` bloğu altına güvenli kapalı varsayılanlarla ekle:
```yaml
verifiedai:
  notification:
    enabled: ${VERIFIEDAI_NOTIFICATION_ENABLED:false}
    dispatch-worker-enabled: ${VERIFIEDAI_NOTIFICATION_DISPATCH_WORKER_ENABLED:false}
    default-timezone: ${VERIFIEDAI_NOTIFICATION_DEFAULT_TIMEZONE:Europe/Istanbul}
    default-daily-cap: 2
    max-daily-cap: 5
    batch-size: 50
    max-attempts: 3
    retry-base-delay: PT2M
    dispatch-interval: PT30S
    apns:
      enabled: ${VERIFIEDAI_NOTIFICATION_APNS_ENABLED:false}
      environment: ${VERIFIEDAI_NOTIFICATION_APNS_ENVIRONMENT:SANDBOX}
      bundle-id: ${VERIFIEDAI_NOTIFICATION_APNS_BUNDLE_ID:com.verifiedai.learning}
      key-id: ${APNS_KEY_ID:}
      team-id: ${APNS_TEAM_ID:}
```
- `NotificationProperties` (`@ConfigurationProperties(prefix = "verifiedai.notification")`) record'u ile negatif/geçersiz limitleri ve `defaultTimezone` `ZoneId` geçerliliğini başlatmada doğrula.
- `apns.enabled=true` iken zorunlu kimlik bilgileri eksikse (`prod`/`staging`) uygulama fail-closed başlatılmalı veya `ApnsPushDeliveryAdapter` aktif edilmemelidir (test/local ortamda `NoOpPushDeliveryGateway` çalışır).

### 4.2 Modül Sınırları, Sınıflar ve İmzalar
Tüm yeni sınıflar mevcut `com.verifiedai.notification` Spring Modulith modülü altında yer alır:
- **`com.verifiedai.notification.application`** (`@NamedInterface("application")` ile `package-info.java` ekle; Sprint 10.5 bu arayüzü tüketebilir):
  1. `NotificationCategory` *(enum)*, `NotificationIntentType` *(enum)*, `NotificationIntentStatus` *(enum)*, `DevicePlatform` *(enum)*, `ApnsEnvironment` *(enum)*.
  2. `NotificationSchedulingPort` *(Public SPI interface — Sprint 10.5 tüketimi için)*:
     - `ScheduleNotificationResult schedule(ScheduleNotificationCommand command)`
     - `boolean cancel(UUID userId, UUID intentId)`
  3. `ScheduleNotificationCommand` *(record)* & `ScheduleNotificationResult` *(record)*.
  4. `NotificationPreferenceView` *(record)*, `UpdateNotificationPreferencesCommand` *(record)*.
  5. `DeviceTokenRegistrationCommand` *(record)*, `DeviceTokenView` *(record — asla ham `device_token` dönmez, yalnızca `id`, `platform`, `environment`, `locale`, `status`, `lastSeenAt`)*.
  6. `NotificationPreferenceService` *(`@Service`, constructor injection)*:
     - `getPreferences(UUID userId)`: tercih yoksa veritabanına yazmadan veya kontrollü ilk erişimde güvenli opt-out varsayılanını (`studyPlanEnabled=false`, `spacedReviewEnabled=false`, `examDeadlineEnabled=false`, `trialReminderEnabled=false`, `quietHoursEnabled=true`, `22:00`–`08:00`, `dailyCap=2`) döner.
     - `updatePreferences(UUID userId, UpdateNotificationPreferencesCommand command)`: `ZoneId` doğrulaması (`400 NOTIFICATION_INVALID_TIMEZONE`), `quietHoursStart != quietHoursEnd` (`400 NOTIFICATION_INVALID_QUIET_HOURS`), `dailyCap` `[0, maxDailyCap]` kontrolü, optimistic locking (`expectedVersion` uyuşmazlığında `409 OPTIMISTIC_CONFLICT` / `NOTIFICATION_PREFERENCE_CONFLICT`).
  7. `DeviceTokenService` *(`@Service`, constructor injection)*:
     - `registerToken(UUID userId, DeviceTokenRegistrationCommand command)`: hex/base64 APNs token format + uzunluk (`32..512`) doğrulaması (`400 NOTIFICATION_DEVICE_TOKEN_INVALID`), SHA-256 hash üretimi, `(user_id, token_sha256)` idempotent upsert.
     - `listDevices(UUID userId)` & `revokeToken(UUID userId, UUID deviceTokenId)`: sahiplik doğrulaması (BOLA koruması — başka kullanıcının device ID'si için `404 RESOURCE_NOT_FOUND` / `403`).
  8. `NotificationSchedulingService implements NotificationSchedulingPort` *(`@Service`, constructor injection)*:
     - `enabled=false` iken veritabanına yazmadan veya policy-disabled sonucuyla güvenli davranır (endpoint katmanında `503`/`409` veya açık `FEATURE_DISABLED` durumu sözleşmeyle sabitlenir).
     - Idempotency (`(user_id, idempotency_key)` + `payload_hash`), `NotificationPreferencePolicy` + `QuietHoursPolicy` + `daily_cap` değerlendirmesi, PII içermeyen `payload_jsonb` doğrulaması.
  9. `NotificationDispatchService` *(`@Service`, constructor injection)*:
     - `dispatchDueBatch(int limit)`: transaction içinde `FOR UPDATE SKIP LOCKED` ile `deliver_after <= now` ve `status IN ('SCHEDULED', 'FAILED_RETRYABLE')` satırlarını `DISPATCHING` olarak sahiplenir; dış gateway çağrısını transaction dışında yürütür; sonucu yeni kısa transaction içinde (`DELIVERED`, `FAILED_RETRYABLE` exponential backoff, `FAILED_TERMINAL`, veya invalid token bildirildiyse ilgili `notification_device_tokens.status = 'INVALID_TOKEN'`) kaydeder ve `notification_delivery_attempts` satırını yazar.
  10. `NotificationLifecycleContributor implements AccountDataLifecycleContributor`:
      - `category()` -> `"notification"`.
      - `exportUserData(UUID userId)`: tercihleri, cihaz metadata'sını (**ham `device_token` ve `token_sha256` hariç**), niyet özetlerini (**ham `payload_jsonb` sadece güvenli alanlar**), teslimat deneme sayılarını/durumlarını döner.
      - `deleteUserData(UUID userId, Instant now)`: `notification_delivery_attempts`, `notification_intents`, `notification_device_tokens`, `notification_preferences` tablolarını child-first sırayla siler.
- **`com.verifiedai.notification.domain`**:
  - `QuietHoursPolicy`: `isWithinQuietHours(Instant utcInstant, ZoneId zone, LocalTime start, LocalTime end)` ve `nextAllowedDispatchAt(Instant utcInstant, ZoneId zone, LocalTime start, LocalTime end)` (gece yarısını aşan ve DST atlama/tekrar durumlarında deterministik hesaplama).
  - `NotificationPreferencePolicy`: kategori bazlı opt-in ve `ACCOUNT_SECURITY` muafiyet kuralları.
  - `NotificationDeliveryStateMachine`: yasal geçiş tablosu ve `requireTransition(from, to)`.
- **`com.verifiedai.notification.infrastructure`**:
  - `PushDeliveryGateway` *(port interface)*, `PushDeliveryRequest`, `PushDeliveryResult`.
  - `NoOpPushDeliveryGateway` *(`@ConditionalOnProperty(name = "verifiedai.notification.apns.enabled", havingValue = "false", matchIfMissing = true)`)*.
  - `NotificationSchedulingConfiguration` *(`@Configuration`, `@EnableScheduling`, `@ConditionalOnProperty(name = "verifiedai.notification.dispatch-worker-enabled", havingValue = "true")`)*.
- **`com.verifiedai.notification.api`**:
  - `NotificationPreferenceController`:
    - `GET /api/v1/notifications/preferences` (+ `/v1/notifications/preferences`)
    - `PUT /api/v1/notifications/preferences` (+ `/v1/notifications/preferences`)
  - `NotificationDeviceTokenController`:
    - `GET /api/v1/notifications/devices` (+ `/v1/notifications/devices`)
    - `POST /api/v1/notifications/devices` (+ `/v1/notifications/devices`, `Idempotency-Key` opsiyonel/destekli)
    - `DELETE /api/v1/notifications/devices/{deviceId}` (+ `/v1/notifications/devices/{deviceId}`)
  - `NotificationIntentController`:
    - `GET /api/v1/notifications/intents` (+ `/v1/notifications/intents`, yalnızca mevcut kullanıcının sayfalanmış bildirim geçmişi)
  - Tüm controller'lar `AuthenticatedUser.from(jwt).userId()` kullanır (client'tan `userId` almaz), `CacheControl.noStore().cachePrivate()` döner.

### 4.3 `ApiErrorCode` & Micrometer Telemetrisi
- `ApiErrorCode.java` eklemeleri:
  - `NOTIFICATION_FEATURE_DISABLED` (503 / 409)
  - `NOTIFICATION_INVALID_TIMEZONE` (400)
  - `NOTIFICATION_INVALID_QUIET_HOURS` (400)
  - `NOTIFICATION_DEVICE_TOKEN_INVALID` (400)
  - `NOTIFICATION_PREFERENCE_CONFLICT` (409)
  - `NOTIFICATION_INTENT_NOT_FOUND` (404)
  - `NOTIFICATION_ILLEGAL_TRANSITION` (409)
- Micrometer metrikleri (düşük kardinaliteli tag'ler — kullanıcı ID veya token tag'i yasak):
  - `notification_scheduled_total` (`category`, `intent_type`, `outcome`)
  - `notification_suppressed_total` (`category`, `reason`)
  - `notification_delivery_attempt_total` (`provider`, `outcome`)
  - `notification_delivery_latency` (`provider`, `outcome`)
  - `notification_preferences_updated_total` (`quiet_hours_enabled`)

---

## 5. REST API, JSON SCHEMA VE ÇİFT OPENAPI AYNA SÖZLEŞMESİ

1. Yeni JSON Schema dosyaları:
   - `packages/schemas/notification-preferences.schema.json`
   - `packages/schemas/notification-device-token.schema.json`
   - `packages/schemas/notification-intent-summary.schema.json`
   (Tüm şemalarda `"additionalProperties": false`, kesin enum'lar, IANA timezone pattern/uzunluk ve `HH:mm` yerel saat formatı.)
2. Pozitif ve negatif fixture dosyaları:
   - `packages/schemas/fixtures/notification-preferences.cases.json` (varsayılan opt-out, tam opt-in + özel saat dilimi, quiet hours kapalı vb.)
   - `packages/schemas/fixtures/notification-device-token.cases.json`
3. **Her iki OpenAPI aynası** (`packages/contracts/openapi/public-api.yaml` ve `packages/openapi/verified-ai-core-api.yaml`) birebir senkronize edilir; tüm yeni endpoint'ler (`GET/PUT /api/v1/notifications/preferences`, `GET/POST /api/v1/notifications/devices`, `DELETE /api/v1/notifications/devices/{deviceId}`, `GET /api/v1/notifications/intents` ve `/v1/notifications/...` alias'ları) + `ProblemDetails` 400/401/403/404/409/503 yanıtları tanımlanır.

---

## 6. iOS İSTEMCİSİ (`apps/ios/VerifiedAI/Features/Notifications/` — SWIFT 6 STRICT CONCURRENCY)

1. **`Domain/NotificationModels.swift`:**
   - `NotificationPreferences: Codable, Sendable, Equatable` (`studyPlanEnabled`, `spacedReviewEnabled`, `examDeadlineEnabled`, `trialReminderEnabled`, `timezone`, `quietHoursEnabled`, `quietHoursStartLocal`, `quietHoursEndLocal`, `dailyCap`, `version`, `updatedAt`).
   - `NotificationDeviceRegistration: Codable, Sendable, Equatable` ve `PushAuthorizationState: String, Sendable, Equatable` (`.notDetermined`, `.authorized`, `.denied`, `.provisional`).
2. **`Data/NotificationAPI.swift` & `PushPermissionManager.swift`:**
   - `protocol NotificationServicing: Sendable` (`fetchPreferences`, `updatePreferences`, `registerDeviceToken`, `revokeDeviceToken`).
   - `protocol PushAuthorizationProviding: Sendable` (`UNUserNotificationCenter` üzerinde canlı adaptör + XCTest'te sistem izin diyalogu açmayan sahte stub).
   - Kullanıcının iOS push izni yoksa cihaz ayarı reddedilmişse Ayarlar'a (`UIApplication.openSettingsURLString`) yönlendiren sakin kurtarma banner'ı.
   - APNs token yalnızca `Auth` oturumu aktifken ve kullanıcı en az bir bildirim kategorisini açtığında (veya açık izin verdiğinde) backend'e kaydedilir; oturum kapandığında cihaz token iptal/reset akışı güvenli çalışır.
3. **`Presentation/NotificationPreferencesViewModel.swift` (`@MainActor @Observable`):**
   - `state`: `.idle`, `.loading`, `.ready`, `.saving`, `.offline`, `.failed`.
   - Optimistic `version` takibi ve `409` çakışmasında sunucudan yeniden yükleme kurtarma akışı.
   - `320pt` ve `xSmall`–`AX3` Dynamic Type altında taşma olmayan `NotificationPreferencesView.swift` (Concept B düz `AppSurfaceCard`, `AppNoticeBanner`, `ViewThatFits`, sakin/baskısız kopya — sahte aciliyet/kırmızı alarm yok, VoiceOver etiketleri ve hint'leri tam).
4. **Bağlantılar & Kayıtlar:**
   - `AccountSettingsView.swift` içine Notification Preference Center `NavigationLink` kartı ekle (`AppDependencies.swift` ve `RootView.swift` üzerinden bağla).
   - `DeepLinkRouter.swift`: `verified-ai://notification-preferences`, `verified-ai://study-plan`, `verified-ai://spaced-review` rotalarını güvenli doğrulamayla ekle.
   - `Localizable.xcstrings`: tüm `notification.*` anahtarları için eksiksiz `en` ve `tr` çevirileri.
   - `project.pbxproj`: tüm yeni `.swift` ve test dosyalarını eksiksiz kaydet ve `plutil -lint` ile doğrula.
   - `SWIFT_STRICT_CONCURRENCY=complete` altında sıfır uyarı/hata.

---

## 7. A–F TEST MATRİSİ VE KALİTE / KABUL BETİKLERİ

- **A. Birim Testleri:**
  - `QuietHoursPolicyTest`: gece yarısını aşan `22:00`–`08:00`, gündüz içi aralıklar, `Europe/Istanbul` vs `America/New_York`, DST ileri/geri geçiş günü, `nextAllowedDispatchAt` doğruluğu.
  - `NotificationPreferencePolicyTest`: varsayılan opt-out (`INV-NOTIF-001`), `ACCOUNT_SECURITY` muafiyeti, `daily_cap` sınırları.
  - `NotificationDeliveryStateMachineTest`: tüm yasal ve yasa dışı durum geçişleri.
- **B. Veritabanı & Flyway Migration Testi (`Sprint104NotificationMigrationTest`):**
  - Testcontainers PostgreSQL 16 üzerinde `V067 -> V068` yükseltmesi ve idempotency (`migrationsExecuted == 1`, ikinci çalıştırmada `0`).
  - `CHECK` kısıtları (geçersiz `daily_cap`, `start == end` quiet hours, geçersiz `token_sha256`, `training_eligible = true` reddi), `UNIQUE` kısıtları ve `ON DELETE CASCADE`.
- **C. Eşzamanlılık & Dispatch Testi (`NotificationSchedulingConcurrencyIT`):**
  - **20 paralel thread** (`ExecutorService` + `CountDownLatch`) ile aynı `(userId, idempotencyKey)` isteği atıldığında veritabanında tam **1** intent satırı oluştuğunu, aynı key farklı payload ile geldiğinde `409` döndüğünü doğrula.
  - `NotificationDispatchService` ile paralel/tekrarlı `dispatchDueBatch` çağrılarının `FOR UPDATE SKIP LOCKED` sayesinde çift teslimat üretmediğini, `RETRYABLE_ERROR` backoff ve `INVALID_DEVICE_TOKEN` durumlarında cihaz token'ının `INVALID_TOKEN` işaretlendiğini kanıtla.
- **D. API, AuthZ & GDPR Testi (`NotificationControllerIT`):**
  - `401` (anonim / süresi dolmuş JWT), `403` (silinmiş/pasif hesap), BOLA (başka kullanıcının `deviceId` kaydını silememe), `PUT /preferences` optimistic version conflict (`409`), flag kapalı davranışı, `NotificationLifecycleContributor` GDPR export (ham `device_token` yok!) & delete kullanıcı izolasyonu.
- **E. Sözleşme Doğrulama Betiği (`scripts/quality/validate_sprint_10_4_contracts.py`):**
  - JSON Schema Draft 2020-12 + tüm pozitif/negatif fixture'lar, çift OpenAPI ayna eşitliği, `V068` SQL kısıtları, `application.yml` `verifiedai.notification.*` varsayılan `false` değerleri, `Localizable.xcstrings` `en`/`tr` paritesi, `project.pbxproj` kaydı; `validate_json_schemas.py` içine bağlanır.
- **F. Owner Kabul & Swift Kontrol Betikleri:**
  - **`scripts/quality/sprint_10_4_owner_acceptance.sh`:** `validate_sprint_10_4_contracts.py` + `validate_json_schemas.py` + `docs_check.py` + Maven/Testcontainers A–D ve Sprint 10.1–10.3 + `ModulithArchitectureTest` regresyon paketlerini çalıştırır; **macOS Docker Desktop `Ryuk` geçici port gecikmesine karşı otomatik retry (max 3 deneme, 5sn bekleme)** ve Surefire XML doğrulaması içerir.
  - **`scripts/quality/sprint_10_4_swift_check.sh`:** `plutil -lint`, `validate_sprint_10_4_contracts.py` ve `xcodebuild test` (`SWIFT_STRICT_CONCURRENCY=complete`) ile yeni `Notification*Tests` + mevcut `AccountSettingsViewModelTests` ve `Subscription` regresyon testlerini doğrular.

---

## 8. DOKÜMANTASYON VE TESLİMAT FORMATI

Uygulamayı bitirdiğinde şu 3 dokümantasyon adımını da tamamla:
1. `docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.4_IMPLEMENTATION_MAP.md` *(Yeni)*
2. `docs/verified_ai_learning_platform_docs_v4/sprints/phase10_monetization_growth_and_product_operations/SPRINT_10.4_EXECUTION_REPORT.md` *(Yeni — `IMPLEMENTED_NOT_RUN` durumu ve owner komutlarıyla)*
3. `docs/verified_ai_learning_platform_docs_v4/DOCUMENTATION_MANIFEST.md` *(güncel satır/byte sayılarıyla — `python3 scripts/quality/docs_check.py` hatasız geçmeli)*

Son yanıtında yalnızca:
- Değiştirilen / eklenen dosyaların tam listesini,
- Owner'ın çalıştıracağı tek satırlık kabul komutlarını (`bash scripts/quality/sprint_10_4_owner_acceptance.sh` ve `bash scripts/quality/sprint_10_4_swift_check.sh`),
- Herhangi bir açık risk veya varsayım varsa madde madde belirt.
