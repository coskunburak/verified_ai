package com.verifiedai.growth.application;

import com.verifiedai.featureflags.application.FeatureFlagEvaluationPort;
import com.verifiedai.growth.domain.GrowthContentSanitizer;
import com.verifiedai.growth.domain.ReferralCodeGenerator;
import com.verifiedai.growth.domain.ShareableArtifactStatus;
import com.verifiedai.growth.domain.ShareableArtifactType;
import com.verifiedai.growth.infrastructure.GrowthProperties;
import com.verifiedai.growth.infrastructure.GrowthReadJdbcRepository;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository.StoredShareableArtifact;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns creation, owner-scoped listing, BOLA-protected revocation, and PII-free public
 * deep-link resolution for shareable learning artifacts (INV-GRW-001, INV-GRW-003, INV-GRW-005).
 */
@Service
public class ShareableArtifactService {

    private static final String SHARING_KILL_SWITCH_KEY = "growth_sharing_enabled";

    private final GrowthWriteJdbcRepository writeRepository;
    private final GrowthReadJdbcRepository readRepository;
    private final GrowthProperties properties;
    private final FeatureFlagEvaluationPort featureFlagEvaluationPort;
    private final GrowthMetrics metrics;
    private final Clock clock;

    public ShareableArtifactService(
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
    public ShareableArtifactView createShare(
        UUID userId,
        String artifactTypeRaw,
        String localeRaw,
        String displayAliasRaw,
        String referralCodeRaw,
        Map<String, Object> summaryPayloadRaw,
        Integer ttlDays,
        String idempotencyKeyRaw
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        ensureSharingEnabled(userId);
        requireActiveAccount(userId);

        String idempotencyKey = GrowthContentSanitizer.requireIdempotencyKey(idempotencyKeyRaw);
        ShareableArtifactType artifactType = ShareableArtifactType.parse(artifactTypeRaw);
        String locale = GrowthContentSanitizer.normalizeLocale(localeRaw);
        String displayAlias = GrowthContentSanitizer.sanitizeDisplayAlias(displayAliasRaw);
        String referralCode = (referralCodeRaw != null && !referralCodeRaw.isBlank())
            ? ReferralCodeGenerator.normalizeAndValidateCode(referralCodeRaw)
            : null;
        Map<String, Object> sanitizedSummary = GrowthContentSanitizer.sanitizeSummaryPayload(
            artifactType,
            summaryPayloadRaw
        );
        int effectiveTtlDays = ttlDays != null ? ttlDays : properties.shareDefaultTtlDays();
        if (effectiveTtlDays < 1 || effectiveTtlDays > properties.shareMaxTtlDays()) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "ttlDays must be between 1 and " + properties.shareMaxTtlDays(),
                false,
                "FIX_GROWTH_REQUEST"
            );
        }

        Map<String, Object> canonicalPayload = new LinkedHashMap<>();
        canonicalPayload.put("artifactType", artifactType.name());
        canonicalPayload.put("locale", locale);
        canonicalPayload.put("displayAlias", displayAlias == null ? "" : displayAlias);
        canonicalPayload.put("referralCode", referralCode == null ? "" : referralCode);
        canonicalPayload.put("summaryPayload", sanitizedSummary);
        canonicalPayload.put("ttlDays", effectiveTtlDays);
        String payloadHash = GrowthContentSanitizer.sha256Hex(canonicalPayload);

        writeRepository.acquireAdvisoryLock("share:" + userId);

        Optional<StoredShareableArtifact> existingOpt = writeRepository.findShareByUserAndIdempotencyKey(
            userId,
            idempotencyKey
        );
        if (existingOpt.isPresent()) {
            StoredShareableArtifact existing = existingOpt.get();
            if (!existing.payloadHash().equals(payloadHash)) {
                throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.GROWTH_IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key already used with a different share payload",
                    false,
                    "USE_NEW_IDEMPOTENCY_KEY"
                );
            }
            metrics.recordShare(existing.artifactType().name(), "REPLAYED");
            return ShareableArtifactView.from(existing, true);
        }

        Instant now = clock.instant();
        if (writeRepository.countActiveSharesForUser(userId, now) >= properties.maxActiveSharesPerUser()) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_SHARE_LIMIT_EXCEEDED,
                "Maximum active share artifacts reached for account",
                true,
                "REVOKE_OLD_SHARES"
            );
        }

        String shareToken = generateUniqueShareToken();
        String deepLinkUrl = "verified-ai://share/" + shareToken;
        Instant expiresAt = now.plus(Duration.ofDays(effectiveTtlDays));
        StoredShareableArtifact row = new StoredShareableArtifact(
            UUID.randomUUID(),
            userId,
            shareToken,
            idempotencyKey,
            payloadHash,
            artifactType,
            locale,
            displayAlias,
            referralCode,
            GrowthContentSanitizer.toCanonicalJson(sanitizedSummary),
            deepLinkUrl,
            ShareableArtifactStatus.ACTIVE,
            true,
            0L,
            CorrelationIds.current(),
            0L,
            false,
            expiresAt,
            null,
            now,
            now
        );
        writeRepository.insertShare(row);
        metrics.recordShare(artifactType.name(), "CREATED");
        return ShareableArtifactView.from(row, false);
    }

    @Transactional(readOnly = true)
    public List<ShareableArtifactView> listShares(UUID userId, int limit) {
        Objects.requireNonNull(userId, "userId must not be null");
        ensureSharingEnabled(userId);
        requireActiveAccount(userId);
        if (limit < 1 || limit > 50) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.GROWTH_VALIDATION_FAILED,
                "limit must be between 1 and 50",
                false,
                "FIX_QUERY_LIMIT"
            );
        }
        return writeRepository.listSharesForUser(userId, limit).stream()
            .map(row -> ShareableArtifactView.from(row, false))
            .toList();
    }

    @Transactional
    public ShareableArtifactView revokeShare(UUID userId, UUID shareId, String idempotencyKeyRaw) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(shareId, "shareId must not be null");
        ensureSharingEnabled(userId);
        requireActiveAccount(userId);
        GrowthContentSanitizer.requireIdempotencyKey(idempotencyKeyRaw);

        writeRepository.acquireAdvisoryLock("share:" + userId);

        StoredShareableArtifact existing = writeRepository.findShareByIdAndUserId(shareId, userId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.GROWTH_SHARE_NOT_FOUND,
                "Shareable artifact not found",
                false,
                "CHECK_SHARE_ID"
            ));

        if (existing.status() == ShareableArtifactStatus.REVOKED) {
            metrics.recordShare(existing.artifactType().name(), "REVOKE_REPLAYED");
            return ShareableArtifactView.from(existing, true);
        }
        if (existing.status() == ShareableArtifactStatus.EXPIRED) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_INVALID_TRANSITION,
                "Expired shareable artifact cannot be revoked",
                false,
                "CHECK_SHARE_STATUS"
            );
        }

        Instant now = clock.instant();
        int updated = writeRepository.revokeShare(shareId, userId, existing.version(), now);
        if (updated == 0) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_INVALID_TRANSITION,
                "Concurrent modification while revoking shareable artifact",
                true,
                "RETRY"
            );
        }
        StoredShareableArtifact reloaded = writeRepository.findShareByIdAndUserId(shareId, userId)
            .orElseThrow();
        metrics.recordShare(reloaded.artifactType().name(), "REVOKED");
        return ShareableArtifactView.from(reloaded, false);
    }

    @Transactional
    public PublicShareArtifactView resolvePublicShare(String shareTokenRaw) {
        String shareToken = GrowthContentSanitizer.validateShareToken(shareTokenRaw);
        UUID syntheticContextId = UUID.nameUUIDFromBytes(shareToken.getBytes(StandardCharsets.UTF_8));
        ensureSharingEnabled(syntheticContextId);

        writeRepository.acquireAdvisoryLock("share-public:" + shareToken);

        StoredShareableArtifact row = writeRepository.findShareByToken(shareToken)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.GROWTH_SHARE_NOT_FOUND,
                "Shareable artifact not found",
                false,
                "CHECK_SHARE_LINK"
            ));

        Instant now = clock.instant();
        if (row.status() == ShareableArtifactStatus.REVOKED) {
            throw new ApiProblemException(
                HttpStatus.GONE,
                ApiErrorCode.GROWTH_SHARE_UNAVAILABLE,
                "This shared card is no longer available",
                false,
                "REQUEST_NEW_SHARE"
            );
        }
        if (row.status() == ShareableArtifactStatus.EXPIRED || !row.expiresAt().isAfter(now)) {
            if (row.status() == ShareableArtifactStatus.ACTIVE) {
                writeRepository.markShareExpired(row.id(), now);
            }
            throw new ApiProblemException(
                HttpStatus.GONE,
                ApiErrorCode.GROWTH_SHARE_UNAVAILABLE,
                "This shared card has expired",
                false,
                "REQUEST_NEW_SHARE"
            );
        }

        writeRepository.incrementShareOpenCount(row.id(), now);
        metrics.recordShare(row.artifactType().name(), "PUBLIC_OPENED");
        return PublicShareArtifactView.from(row);
    }

    private String generateUniqueShareToken() {
        for (int i = 0; i < 5; i++) {
            String candidate = ReferralCodeGenerator.generateShareToken();
            if (writeRepository.findShareByToken(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to allocate unique share token");
    }

    private void ensureSharingEnabled(UUID userContextId) {
        boolean killSwitchAllowed = featureFlagEvaluationPort == null
            || featureFlagEvaluationPort.evaluateBoolean(SHARING_KILL_SWITCH_KEY, userContextId, true);
        if (!properties.sharingEnabled() || !killSwitchAllowed) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.GROWTH_FEATURE_DISABLED,
                "Growth sharing is currently disabled",
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
