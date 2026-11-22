package com.verifiedai.billing.application;

import com.verifiedai.billing.domain.model.AppStoreEnvironment;
import com.verifiedai.billing.domain.model.EntitlementSource;
import com.verifiedai.billing.domain.model.EntitlementStatus;
import com.verifiedai.billing.domain.model.EntitlementTier;
import com.verifiedai.billing.domain.model.PremiumCapability;
import com.verifiedai.billing.infrastructure.persistence.EntitlementJpaEntity;
import com.verifiedai.billing.infrastructure.persistence.EntitlementJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntitlementApplicationService implements CapabilityAccessPolicy {
    private final EntitlementJpaRepository entitlementRepository;
    private final Clock clock;
    private final JdbcTemplate jdbcTemplate;
    private final EntitlementMetrics metrics;
    private final AppleProductCatalog catalog;
    private final com.verifiedai.billing.infrastructure.persistence.AppStoreBillingRepository store;
    private final boolean tiersEnabled;
    private QuotaLedgerService quotaLedgerService;

    EntitlementApplicationService(
        EntitlementJpaRepository entitlementRepository,
        Clock clock,
        JdbcTemplate jdbcTemplate,
        EntitlementMetrics metrics, AppleProductCatalog catalog,
        com.verifiedai.billing.infrastructure.persistence.AppStoreBillingRepository store,
        @org.springframework.beans.factory.annotation.Value("${verifiedai.billing.learning-os-tiers-enabled:false}") String flag
    ) {
        this.entitlementRepository = entitlementRepository;
        this.clock = clock;
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
        this.catalog = catalog;
        this.store = store;
        this.tiersEnabled = "true".equalsIgnoreCase(flag);
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setQuotaLedgerService(QuotaLedgerService quotaLedgerService) {
        this.quotaLedgerService = quotaLedgerService;
    }

    @Transactional
    public String currentAiTier(UUID userId) {
        var legacy = getCurrent(userId);
        if (!legacy.status().grantsAccess() || (legacy.expiresAt() != null && !legacy.expiresAt().isAfter(clock.instant()))) return "FREE";
        var current = TierResolutionPolicy.storageTier(computeAccess(userId).effectiveTier());
        return current.includes(legacy.tier()) ? legacy.tier().name() : current.name();
    }

    record Selection(com.verifiedai.billing.infrastructure.persistence.AppStoreBillingRepository.SubscriptionProjection subscription,
                     com.verifiedai.billing.domain.model.AppStoreProductMapping product, EffectiveEntitlementAccess access) {}

    java.util.Optional<Selection> bestSubscription(UUID userId, Instant now) {
        var byTier = java.util.Comparator.comparingInt((Selection s) ->
            TierResolutionPolicy.storageTier(s.access().effectiveTier()).ordinal()).reversed();
        var byExpires = java.util.Comparator.comparing((Selection s) -> s.subscription().expiresAt(),
            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()));
        var byGrace = java.util.Comparator.comparing((Selection s) -> s.subscription().graceUntil(),
            java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()));
        return store.subscriptions(userId).stream()
            .filter(s -> s.environment() == catalog.environment() && s.purchasedAt() != null && !s.purchasedAt().isAfter(now))
            .flatMap(s -> catalog.findByAppStoreProductId(s.productId()).stream().map(p -> new Selection(s, p,
                LearningOsTierCapabilityPolicy.project(p.entitlementTier(), EntitlementStatus.valueOf(s.status().name()),
                    EntitlementSource.APP_STORE_SUBSCRIPTION, s.expiresAt(), s.graceUntil(), s.season(), tiersEnabled, now))))
            .sorted(byTier.thenComparing(byExpires).thenComparing(byGrace))
            .findFirst();
    }

    @Transactional(readOnly = true, noRollbackFor = ApiProblemException.class)
    public EffectiveEntitlementAccess resolveAccess(UUID userId) {
        return computeAccess(userId);
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public EffectiveEntitlementAccess access(UUID userId) {
        EffectiveEntitlementAccess base = computeAccess(userId);
        if (quotaLedgerService == null) {
            return base;
        }
        return base.withQuotaSummary(quotaLedgerService.getOrInitializeSummary(userId, base));
    }

    private EffectiveEntitlementAccess computeAccess(UUID userId) {
        try {
            if (!store.userActive(userId)) throw new ApiProblemException(HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE, "Active account required", false, "SIGN_IN");
            Instant now = clock.instant();
            var apple = bestSubscription(userId, now);
            var entity = entitlementRepository.findByUserId(userId);
            if (entity.isPresent() && !entity.get().effectiveAt().isAfter(now)
                && ("PROMOTIONAL".equals(entity.get().source()) || "ADMIN_SUPPORT".equals(entity.get().source()))) {
                var row = entity.get();
                var grant = LearningOsTierCapabilityPolicy.project(EntitlementTier.valueOf(row.tier()),
                    EntitlementStatus.valueOf(row.status()), EntitlementSource.valueOf(row.source()), row.expiresAt(),
                    null, false, tiersEnabled, now);
                if (apple.isEmpty() || TierResolutionPolicy.storageTier(grant.effectiveTier()).includes(
                    TierResolutionPolicy.storageTier(apple.get().access().effectiveTier()))) return grant;
            }
            return apple.map(Selection::access).orElseGet(() -> LearningOsTierCapabilityPolicy.project(
                EntitlementTier.FREE, EntitlementStatus.ACTIVE, EntitlementSource.DEFAULT_FREE, null, null, false, tiersEnabled, now));
        } catch (org.springframework.dao.DataAccessException ex) {
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.ENTITLEMENT_SYNC_FAILED,
                "Entitlement verification unavailable", true, "RETRY");
        }
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = ApiProblemException.class)
    public CapabilityDecision evaluate(UUID userId, LearningOsCapability capability) {
        java.util.Objects.requireNonNull(capability);
        if (!tiersEnabled) {
            return new CapabilityDecision(true, capability, "FREE", "FREE", "FEATURE_DISABLED", false);
        }
        EffectiveEntitlementAccess result;
        try {
            result = computeAccess(userId);
        } catch (ApiProblemException ex) {
            if (ex.code() == ApiErrorCode.ENTITLEMENT_SYNC_FAILED) {
                metrics.checked(capability, "UNKNOWN", false, "VERIFICATION_UNAVAILABLE");
            }
            throw ex;
        }
        boolean allowed = result.learningOsCapabilities().contains(capability);
        String denyReason;
        if (allowed) {
            denyReason = "NONE";
        } else if (result.status() == EntitlementStatus.REVOKED) {
            denyReason = "REVOKED";
        } else if (result.status() == EntitlementStatus.EXPIRED
            || (!result.paidAccessActive() && result.legacyTier() != EntitlementTier.FREE)) {
            denyReason = "EXPIRED";
        } else {
            denyReason = "TIER_REQUIRED";
        }
        metrics.checked(capability, result.effectiveTier(), allowed, denyReason);
        return new CapabilityDecision(allowed, capability, result.effectiveTier(), result.packageCode(), denyReason, true);
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = ApiProblemException.class)
    public boolean check(UUID userId, LearningOsCapability capability) {
        return evaluate(userId, capability).allowed();
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = ApiProblemException.class)
    public void require(UUID userId, LearningOsCapability capability) {
        CapabilityAccessPolicy.super.require(userId, capability);
    }

    @Transactional
    public EntitlementResult getCurrent(UUID userId) {
        lockUserEntitlement(userId);
        EntitlementJpaEntity entitlement = entitlementRepository.findByUserId(userId)
            .orElseGet(() -> entitlementRepository.saveAndFlush(EntitlementJpaEntity.defaultFree(userId, clock.instant())));
        EntitlementResult result = toResult(entitlement);
        metrics.resolution(result.tier(), result.status());
        return result;
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public EntitlementResult requireCapability(UUID userId, PremiumCapability capability) {
        EntitlementResult entitlement = getCurrent(userId);
        boolean allowed = tiersEnabled ? computeAccess(userId).capabilities().contains(capability) : entitlement.allows(capability);
        if (allowed) {
            metrics.accessAllowed(capability, entitlement.tier());
            return entitlement;
        }

        metrics.accessDenied(capability, entitlement.tier());
        throw new ApiProblemException(
            HttpStatus.FORBIDDEN,
            ApiErrorCode.ENTITLEMENT_REQUIRED,
            "Entitlement required for " + capability.name(),
            true,
            "UPGRADE"
        );
    }

    @Override
    @Transactional(noRollbackFor = ApiProblemException.class)
    public void requireBasicSolve(UUID userId) {
        requireCapability(
            userId,
            PremiumCapability.BASIC_SOLVE
        );
    }

    @Transactional
    public EntitlementResult applyAppStoreSubscription(
        UUID userId,
        EntitlementTier tier,
        EntitlementStatus status,
        Instant effectiveAt,
        Instant expiresAt,
        String originalTransactionId,
        AppStoreEnvironment environment,
        Instant verifiedAt, String packageCode, Instant graceUntil
    ) {
        lockUserEntitlement(userId);
        EntitlementJpaEntity entitlement = entitlementRepository.findByUserId(userId)
            .orElseGet(() -> EntitlementJpaEntity.defaultFree(userId, verifiedAt));
        entitlement.applyAppStoreSubscription(
            tier,
            status,
            effectiveAt,
            expiresAt,
            originalTransactionId,
            environment,
            verifiedAt
        );
        entitlement.setPackageProjection(packageCode, graceUntil);
        EntitlementResult result = toResult(entitlementRepository.saveAndFlush(entitlement));
        metrics.resolution(result.tier(), result.status());
        return result;
    }

    @Transactional
    public EntitlementResult applyDefaultFree(UUID userId, Instant now) {
        lockUserEntitlement(userId);
        EntitlementJpaEntity entitlement = entitlementRepository.findByUserId(userId)
            .orElseGet(() -> EntitlementJpaEntity.defaultFree(userId, now));
        entitlement.applyDefaultFree(now);
        EntitlementResult result = toResult(entitlementRepository.saveAndFlush(entitlement));
        metrics.resolution(result.tier(), result.status());
        return result;
    }

    @Transactional
    public EntitlementResult applyAccountDeleted(UUID userId, Instant now) {
        lockUserEntitlement(userId);
        EntitlementJpaEntity entitlement = entitlementRepository.findByUserId(userId)
            .orElseGet(() -> EntitlementJpaEntity.defaultFree(userId, now));
        if (entitlement.tier().equals(EntitlementTier.FREE.name())
            && entitlement.status().equals(EntitlementStatus.REVOKED.name())
            && entitlement.source().equals(EntitlementSource.DEFAULT_FREE.name())) {
            return toResult(entitlement);
        }
        entitlement.applyAccountDeleted(now);
        EntitlementResult result = toResult(entitlementRepository.saveAndFlush(entitlement));
        metrics.resolution(result.tier(), result.status());
        return result;
    }

    private static final java.util.Set<String> ACTIVE_TRANSITION_CAUSES =
        java.util.Set.of("PURCHASE", "RENEWAL", "ADMIN", "RECONCILE");

    void recordTransition(UUID userId, EntitlementResult previous, EffectiveEntitlementAccess next,
                          String event, String cause, Instant now) {
        var rows = jdbcTemplate.queryForList("""
            select to_package, to_status from billing_entitlement_transition_audit
            where user_id=? order by recorded_at desc, id desc limit 1
            """, userId);
        String before = rows.isEmpty() ? TierResolutionPolicy.presentation(previous.tier())
            : (String) rows.get(0).get("to_package");
        String status = rows.isEmpty() ? previous.status().name() : (String) rows.get(0).get("to_status");
        String after = next.packageCode();
        if (before.equals(after) && status.equals(next.status().name())) return;
        String reason = switch (next.status()) {
            case EXPIRED -> "EXPIRY";
            case REVOKED -> "REFUND".equals(cause) ? "REFUND" : "REVOKE";
            case GRACE_PERIOD -> next.paidAccessActive() ? "GRACE" : "EXPIRY";
            case BILLING_RETRY -> next.paidAccessActive() ? "RETRY" : "EXPIRY";
            case ACTIVE -> !next.paidAccessActive() ? "EXPIRY"
                : (ACTIVE_TRANSITION_CAUSES.contains(cause) ? cause : "RECONCILE");
        };
        int inserted = jdbcTemplate.update("""
            insert into billing_entitlement_transition_audit
                (user_id,event_key_hash,from_package,to_package,from_status,to_status,reason,occurred_at)
            values (?,encode(sha256(convert_to(?, 'UTF8')), 'hex'),?,?,?,?,?,?) on conflict(event_key_hash) do nothing
            """, userId, userId + "/" + event + "/" + after + "/" + next.status(), before, after, status,
            next.status().name(), reason, java.sql.Timestamp.from(now));
        if (inserted == 1) metrics.transition(before, after, reason);
    }

    void lockUserEntitlement(UUID userId) {
        jdbcTemplate.query(
            "select pg_advisory_xact_lock(hashtextextended(?, 3404))",
            preparedStatement -> preparedStatement.setString(1, userId.toString()),
            resultSet -> {
            }
        );
    }

    private EntitlementResult toResult(EntitlementJpaEntity entity) {
        EntitlementTier tier = EntitlementTier.valueOf(entity.tier());
        EntitlementStatus status = EntitlementStatus.valueOf(entity.status());
        List<PremiumCapability> capabilities = accessibleCapabilities(tier, status);
        return new EntitlementResult(
            entity.id(),
            entity.userId(),
            tier,
            EntitlementSource.valueOf(entity.source()),
            status,
            entity.effectiveAt(),
            entity.expiresAt(),
            capabilities,
            entity.version()
        );
    }

    private List<PremiumCapability> accessibleCapabilities(EntitlementTier tier, EntitlementStatus status) {
        if (!status.grantsAccess()) {
            return List.of();
        }
        return Arrays.stream(PremiumCapability.values())
            .filter(capability -> tier.includes(capability.minimumTier()))
            .toList();
    }
}
