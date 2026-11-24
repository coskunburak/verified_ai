package com.verifiedai.growth.application;

import com.verifiedai.featureflags.application.FeatureFlagEvaluationPort;
import com.verifiedai.growth.domain.GrowthContentSanitizer;
import com.verifiedai.growth.domain.ReferralAttributionSource;
import com.verifiedai.growth.domain.ReferralAttributionStateMachine;
import com.verifiedai.growth.domain.ReferralAttributionStatus;
import com.verifiedai.growth.domain.ReferralCodeGenerator;
import com.verifiedai.growth.infrastructure.GrowthProperties;
import com.verifiedai.growth.infrastructure.GrowthReadJdbcRepository;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.ReferrerAggregateStats;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.StoredReferralAttribution;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.StoredReferralCode;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns Crockford-safe referral code minting, deadlock-free anti-abuse attribution,
 * and server-authoritative milestone qualification (INV-GRW-002, INV-GRW-003).
 * Never mutates billing entitlements tables directly.
 */
@Service
public class ReferralAttributionService {

    private static final String REFERRAL_KILL_SWITCH_KEY = "growth_referral_enabled";
    private static final String REWARD_TYPE_BONUS_SOLVES = "BONUS_VERIFIED_SOLVES_CREDIT";

    private final GrowthWriteJdbcRepository writeRepository;
    private final GrowthReadJdbcRepository readRepository;
    private final GrowthProperties properties;
    private final FeatureFlagEvaluationPort featureFlagEvaluationPort;
    private final GrowthMetrics metrics;
    private final Clock clock;

    public ReferralAttributionService(
        GrowthWriteJdbcRepository writeRepository,
        GrowthReadJdbcRepository readRepository,
        GrowthProperties properties,
        FeatureFlagEvaluationPort featureFlagEvaluationPort,
        GrowthMetrics metrics,
        Clock clock
    ) {
        this.writeRepository = Objects.requireNonNull(writeRepository, "writeRepository must not be null");
        this.readRepository = Objects.requireNonNull(readRepository, "readRepository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.featureFlagEvaluationPort = featureFlagEvaluationPort;
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public ReferralSummaryView getOrCreateSummary(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        ensureReferralEnabled(userId);
        requireActiveAccount(userId);

        writeRepository.acquireAdvisoryLock("referral-code:" + userId);
        StoredReferralCode codeRow = writeRepository.findReferralCodeByUserId(userId)
            .orElseGet(() -> mintReferralCode(userId));

        Instant now = clock.instant();
        ReferrerAggregateStats stats = writeRepository.summarizeReferrerStats(userId);
        long monthCount = writeRepository.countReferrerAttributionsSince(userId, now.minus(Duration.ofDays(30)));
        long yearRewardCount = writeRepository.countReferrerRewardsGrantedSince(userId, now.minus(Duration.ofDays(365)));

        int monthlyRemaining = Math.max(0, properties.maxReferralsPerMonth() - (int) monthCount);
        int annualRemaining = Math.max(0, properties.maxRewardsPerYear() - (int) yearRewardCount);

        return new ReferralSummaryView(
            codeRow.referralCode(),
            "verified-ai://referral/" + codeRow.referralCode(),
            codeRow.active(),
            stats.totalAttributions(),
            stats.qualifiedCount(),
            stats.rewardedCount(),
            stats.totalRewardCredits(),
            monthlyRemaining,
            annualRemaining,
            false,
            0,
            codeRow.createdAt()
        );
    }

    @Transactional
    public ReferralAttributionResultView attributeReferral(
        UUID referredUserId,
        String referralCodeRaw,
        String sourceRaw,
        String deviceFingerprintHashRaw,
        String idempotencyKeyRaw
    ) {
        Objects.requireNonNull(referredUserId, "referredUserId must not be null");
        ensureReferralEnabled(referredUserId);
        requireActiveAccount(referredUserId);

        String idempotencyKey = GrowthContentSanitizer.requireIdempotencyKey(idempotencyKeyRaw);
        String referralCode = ReferralCodeGenerator.normalizeAndValidateCode(referralCodeRaw);
        ReferralAttributionSource source = ReferralAttributionSource.parse(sourceRaw);
        String deviceHash = GrowthContentSanitizer.normalizeDeviceFingerprintHash(deviceFingerprintHashRaw);

        Map<String, Object> canonicalPayload = new LinkedHashMap<>();
        canonicalPayload.put("referralCode", referralCode);
        canonicalPayload.put("attributionSource", source.name());
        canonicalPayload.put("deviceFingerprintHash", deviceHash == null ? "" : deviceHash);
        String payloadHash = GrowthContentSanitizer.sha256Hex(canonicalPayload);

        StoredReferralCode codeRow = writeRepository.findReferralCodeByCode(referralCode)
            .filter(StoredReferralCode::active)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.GROWTH_REFERRAL_CODE_NOT_FOUND,
                "Referral code not found or inactive",
                false,
                "CHECK_REFERRAL_CODE"
            ));

        UUID referrerUserId = codeRow.userId();
        if (referrerUserId.equals(referredUserId)) {
            metrics.recordReferral("REJECTED_SELF");
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_SELF_REFERRAL_FORBIDDEN,
                "Self-referral is not permitted",
                false,
                "USE_FRIEND_CODE"
            );
        }

        acquireOrderedLocks(referrerUserId, referredUserId, deviceHash);

        Optional<StoredReferralAttribution> idemExistingOpt =
            writeRepository.findAttributionByReferredUserAndIdempotencyKey(referredUserId, idempotencyKey);
        if (idemExistingOpt.isPresent()) {
            StoredReferralAttribution existing = idemExistingOpt.get();
            if (!existing.payloadHash().equals(payloadHash)) {
                throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.GROWTH_IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key already used with a different referral attribution payload",
                    false,
                    "USE_NEW_IDEMPOTENCY_KEY"
                );
            }
            metrics.recordReferral("REPLAYED");
            return ReferralAttributionResultView.from(existing, true);
        }

        if (writeRepository.findAttributionByReferredUserId(referredUserId).isPresent()) {
            metrics.recordReferral("REJECTED_DUPLICATE");
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_REFERRAL_ALREADY_ATTRIBUTED,
                "Account already has an attributed referral",
                false,
                "CONTINUE_LEARNING"
            );
        }

        if (writeRepository.existsAttributionBetween(referredUserId, referrerUserId)) {
            metrics.recordReferral("REJECTED_CIRCULAR");
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_CIRCULAR_REFERRAL_FORBIDDEN,
                "Circular referral between these accounts is not permitted",
                false,
                "USE_DIFFERENT_INVITE"
            );
        }

        if (deviceHash != null) {
            if (writeRepository.existsAttributionForReferrerAndDevice(referrerUserId, deviceHash)
                || writeRepository.countAttributionsByDeviceHash(deviceHash) >= properties.maxAttributionsPerDevice()) {
                metrics.recordReferral("REJECTED_DEVICE_ABUSE");
                throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.GROWTH_ABUSE_LIMIT_EXCEEDED,
                    "Device referral attribution limit reached",
                    false,
                    "CONTACT_SUPPORT"
                );
            }
        }

        Instant now = clock.instant();
        if (writeRepository.countReferrerAttributionsSince(referrerUserId, now.minus(Duration.ofDays(30)))
            >= properties.maxReferralsPerMonth()) {
            metrics.recordReferral("REJECTED_MONTHLY_CAP");
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_ABUSE_LIMIT_EXCEEDED,
                "Referrer monthly attribution cap reached",
                true,
                "TRY_AGAIN_NEXT_MONTH"
            );
        }

        StoredReferralAttribution row = new StoredReferralAttribution(
            UUID.randomUUID(),
            codeRow.id(),
            referrerUserId,
            referredUserId,
            codeRow.referralCode(),
            idempotencyKey,
            payloadHash,
            deviceHash,
            source,
            ReferralAttributionStatus.ATTRIBUTED,
            null,
            null,
            0,
            CorrelationIds.current(),
            0L,
            false,
            now,
            null,
            null,
            now
        );
        writeRepository.insertAttribution(row);
        metrics.recordReferral("ATTRIBUTED");
        return ReferralAttributionResultView.from(row, false);
    }

    @Transactional
    public ReferralAttributionResultView qualifyAndGrantReward(UUID referredUserId, String idempotencyKeyRaw) {
        Objects.requireNonNull(referredUserId, "referredUserId must not be null");
        ensureReferralEnabled(referredUserId);
        requireActiveAccount(referredUserId);
        GrowthContentSanitizer.requireIdempotencyKey(idempotencyKeyRaw);

        writeRepository.acquireAdvisoryLock("referral-qualify:" + referredUserId);

        StoredReferralAttribution existing = writeRepository.findAttributionByReferredUserId(referredUserId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.GROWTH_REFERRAL_CODE_NOT_FOUND,
                "No referral attribution found for account",
                false,
                "ATTRIBUTE_REFERRAL_FIRST"
            ));

        if (existing.status() == ReferralAttributionStatus.REWARD_GRANTED) {
            metrics.recordReferral("QUALIFY_REPLAYED");
            return ReferralAttributionResultView.from(existing, true);
        }

        ReferralAttributionStateMachine.assertTransitionAllowed(
            existing.status(),
            ReferralAttributionStatus.QUALIFIED
        );

        if (!readRepository.hasCompletedLearningMilestone(referredUserId)) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_INVALID_TRANSITION,
                "Referred learner has not completed a verified learning milestone",
                true,
                "COMPLETE_LEARNING_MILESTONE"
            );
        }

        Instant now = clock.instant();
        if (writeRepository.countReferrerRewardsGrantedSince(existing.referrerUserId(), now.minus(Duration.ofDays(365)))
            >= properties.maxRewardsPerYear()) {
            metrics.recordReferral("REJECTED_ANNUAL_REWARD_CAP");
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_ABUSE_LIMIT_EXCEEDED,
                "Referrer annual reward cap reached",
                false,
                "CHECK_REFERRAL_CAPS"
            );
        }

        ReferralAttributionStateMachine.assertTransitionAllowed(
            ReferralAttributionStatus.QUALIFIED,
            ReferralAttributionStatus.REWARD_GRANTED
        );

        Instant qualifiedAt = existing.qualifiedAt() != null ? existing.qualifiedAt() : now;
        int updated = writeRepository.updateAttributionStatusAndReward(
            existing.id(),
            existing.version(),
            ReferralAttributionStatus.REWARD_GRANTED,
            null,
            REWARD_TYPE_BONUS_SOLVES,
            properties.referralRewardSolveCredits(),
            qualifiedAt,
            now,
            now
        );
        if (updated == 0) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_INVALID_TRANSITION,
                "Concurrent modification while qualifying referral",
                true,
                "RETRY"
            );
        }

        StoredReferralAttribution reloaded = writeRepository.findAttributionByReferredUserId(referredUserId)
            .orElseThrow();
        metrics.recordReferral("REWARD_GRANTED");
        return ReferralAttributionResultView.from(reloaded, false);
    }

    private StoredReferralCode mintReferralCode(UUID userId) {
        Instant now = clock.instant();
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = ReferralCodeGenerator.generateReferralCode();
            if (writeRepository.findReferralCodeByCode(candidate).isEmpty()) {
                StoredReferralCode row = new StoredReferralCode(
                    UUID.randomUUID(),
                    userId,
                    candidate,
                    true,
                    0L,
                    false,
                    now,
                    now
                );
                writeRepository.insertReferralCode(row);
                metrics.recordReferral("CODE_MINTED");
                return row;
            }
        }
        throw new IllegalStateException("Unable to mint unique referral code");
    }

    private void acquireOrderedLocks(UUID a, UUID b, String deviceHash) {
        String first = a.toString().compareTo(b.toString()) <= 0 ? a.toString() : b.toString();
        String second = a.toString().compareTo(b.toString()) <= 0 ? b.toString() : a.toString();
        writeRepository.acquireAdvisoryLock("referral-user:" + first);
        if (!first.equals(second)) {
            writeRepository.acquireAdvisoryLock("referral-user:" + second);
        }
        if (deviceHash != null) {
            writeRepository.acquireAdvisoryLock("referral-device:" + deviceHash);
        }
    }

    private void ensureReferralEnabled(UUID userContextId) {
        boolean killSwitchAllowed = featureFlagEvaluationPort == null
            || featureFlagEvaluationPort.evaluateBoolean(REFERRAL_KILL_SWITCH_KEY, userContextId, true);
        if (!properties.referralEnabled() || !killSwitchAllowed) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.GROWTH_FEATURE_DISABLED,
                "Growth referrals are currently disabled",
                true,
                "TRY_AGAIN_LATER"
            );
        }
    }

    private void requireActiveAccount(UUID userId) {
        if (!readRepository.isAccountActive(userId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Active account required",
                false,
                "SIGN_IN"
            );
        }
    }
}
