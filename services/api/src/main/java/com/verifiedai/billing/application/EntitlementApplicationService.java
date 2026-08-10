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

    EntitlementApplicationService(
        EntitlementJpaRepository entitlementRepository,
        Clock clock,
        JdbcTemplate jdbcTemplate,
        EntitlementMetrics metrics
    ) {
        this.entitlementRepository = entitlementRepository;
        this.clock = clock;
        this.jdbcTemplate = jdbcTemplate;
        this.metrics = metrics;
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
        if (entitlement.allows(capability)) {
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
        Instant verifiedAt
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
        entitlement.applyAccountDeleted(now);
        EntitlementResult result = toResult(entitlementRepository.saveAndFlush(entitlement));
        metrics.resolution(result.tier(), result.status());
        return result;
    }

    private void lockUserEntitlement(UUID userId) {
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
