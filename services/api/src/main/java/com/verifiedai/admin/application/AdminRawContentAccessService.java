package com.verifiedai.admin.application;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.admin.domain.AdminAuditAction;
import com.verifiedai.admin.domain.AdminAuditTargetType;
import com.verifiedai.admin.domain.AdminRedactionPolicy;
import com.verifiedai.admin.infrastructure.AdminSupportProperties;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository.ElevatedRawArtifacts;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository.ProblemSessionRow;
import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository;
import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository.StoredAuditLog;
import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository.StoredRawContentGrant;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Elevated, time-bounded, audited raw student content access service (INV-ADM-001 & INV-ADM-002).
 */
@Service
public class AdminRawContentAccessService {

    public static final String GRANT_SCHEMA_VERSION = "admin-raw-content-grant-v1";
    public static final String INSPECTION_SCHEMA_VERSION = "admin-raw-content-inspection-v1";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final AdminSupportReadJdbcRepository readRepository;
    private final AdminSupportWriteJdbcRepository writeRepository;
    private final AdminSupportProperties properties;
    private final AdminSupportMetrics metrics;
    private final Clock clock;

    public AdminRawContentAccessService(
        AdminSupportReadJdbcRepository readRepository,
        AdminSupportWriteJdbcRepository writeRepository,
        AdminSupportProperties properties,
        AdminSupportMetrics metrics,
        Clock clock
    ) {
        this.readRepository = Objects.requireNonNull(readRepository, "readRepository must not be null");
        this.writeRepository = Objects.requireNonNull(writeRepository, "writeRepository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public AdminRawContentInspectionView.RawContentGrantView createGrant(
        UUID problemSessionId,
        String rawTicketReference,
        String rawReason,
        Integer requestedTtlMinutes,
        String rawIdempotencyKey,
        UUID actorUserId,
        Collection<? extends GrantedAuthority> authorities
    ) {
        requireRawContentAccessEnabled();
        requireElevatedAuthority(authorities);
        Objects.requireNonNull(problemSessionId, "problemSessionId must not be null");
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");

        String idempotencyKey = AdminRedactionPolicy.validateIdempotencyKey(rawIdempotencyKey);
        String ticketReference = AdminRedactionPolicy.validateTicketReference(rawTicketReference);
        String reason = AdminRedactionPolicy.validateElevatedGrantReason(rawReason);
        int ttlMinutes = AdminRedactionPolicy.validateGrantTtlMinutes(
            requestedTtlMinutes,
            properties.rawContentGrantTtlMinutes()
        );
        String actorRole = AdminRedactionPolicy.extractActorRole(authorities);

        String payloadHash = AdminRedactionPolicy.sha256Hex(
            "RAW_GRANT|" + actorUserId + "|" + problemSessionId + "|" + ticketReference + "|" + reason + "|" + ttlMinutes
        );

        writeRepository.acquireAdvisoryLock("adm-raw-grant:" + idempotencyKey);
        Optional<StoredRawContentGrant> existingGrant = writeRepository.findRawContentGrantByIdempotencyKey(idempotencyKey);
        if (existingGrant.isPresent()) {
            StoredRawContentGrant grant = existingGrant.get();
            if (!grant.actorUserId().equals(actorUserId)
                || !grant.problemSessionId().equals(problemSessionId)
                || !payloadHash.equals(grant.payloadHash())) {
                metrics.recordRawContent("grant_conflict");
                throw idempotencyConflict();
            }
            metrics.recordRawContent("grant_replayed");
            return toGrantView(grant, true);
        }

        ProblemSessionRow session = readRepository.findProblemSession(problemSessionId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.ADMIN_TARGET_NOT_FOUND,
                "Problem session not found: " + problemSessionId,
                false,
                "CHECK_PROBLEM_SESSION_ID"
            ));

        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttlMinutes, ChronoUnit.MINUTES);
        UUID auditId = UUID.randomUUID();
        UUID grantId = UUID.randomUUID();
        String correlationId = normalizeCorrelationId();

        writeRepository.insertAuditLog(new StoredAuditLog(
            auditId,
            AdminAuditAction.RAW_CONTENT_GRANT_CREATED,
            AdminAuditTargetType.PROBLEM_SESSION,
            problemSessionId.toString(),
            actorUserId,
            actorRole,
            idempotencyKey,
            payloadHash,
            reason,
            "{}",
            toJsonObject(Map.of(
                "grantId", grantId.toString(),
                "ticketReference", ticketReference,
                "ttlMinutes", ttlMinutes,
                "expiresAt", expiresAt.toString()
            )),
            correlationId,
            false,
            now
        ));

        StoredRawContentGrant grant = new StoredRawContentGrant(
            grantId,
            actorUserId,
            actorRole,
            session.userId(),
            problemSessionId,
            ticketReference,
            reason,
            idempotencyKey,
            payloadHash,
            auditId,
            now,
            expiresAt,
            null,
            correlationId,
            false
        );
        writeRepository.insertRawContentGrant(grant);
        metrics.recordRawContent("grant_created");
        return toGrantView(grant, false);
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public AdminRawContentInspectionView inspectRawContent(
        UUID problemSessionId,
        UUID grantId,
        UUID actorUserId,
        Collection<? extends GrantedAuthority> authorities
    ) {
        requireRawContentAccessEnabled();
        requireElevatedAuthority(authorities);
        Objects.requireNonNull(problemSessionId, "problemSessionId must not be null");
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        if (grantId == null) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.ADMIN_VALIDATION_FAILED,
                "grantId query parameter is required",
                false,
                "PROVIDE_GRANT_ID"
            );
        }

        StoredRawContentGrant grant = writeRepository.findRawContentGrantById(grantId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.ADMIN_TARGET_NOT_FOUND,
                "Raw content access grant not found: " + grantId,
                false,
                "CREATE_RAW_CONTENT_GRANT"
            ));

        if (!grant.actorUserId().equals(actorUserId) || !grant.problemSessionId().equals(problemSessionId)) {
            metrics.recordRawContent("inspect_forbidden");
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ADMIN_RAW_CONTENT_FORBIDDEN,
                "Raw content grant does not belong to calling actor and problem session",
                false,
                "USE_OWN_GRANT"
            );
        }

        Instant now = clock.instant();
        if (grant.revokedAt() != null || !grant.expiresAt().isAfter(now)) {
            metrics.recordRawContent("inspect_expired");
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.ADMIN_RAW_CONTENT_GRANT_EXPIRED,
                "Raw content access grant has expired or been revoked",
                true,
                "CREATE_NEW_GRANT"
            );
        }

        ElevatedRawArtifacts artifacts = readRepository.findElevatedRawArtifacts(
            problemSessionId,
            properties.maxTraceRecords()
        );
        List<AdminRawContentInspectionView.RawAssetArtifactView> assets = artifacts.assets().stream()
            .map(a -> new AdminRawContentInspectionView.RawAssetArtifactView(
                a.assetId(),
                a.assetKind(),
                a.status(),
                a.objectKey(),
                a.contentType(),
                a.createdAt()
            ))
            .toList();

        List<AdminRawContentInspectionView.RawParseArtifactView> parses = artifacts.parses().stream()
            .map(p -> new AdminRawContentInspectionView.RawParseArtifactView(
                p.parseId(),
                p.revision(),
                p.source(),
                p.supportStatus(),
                p.rawOutputJson(),
                p.normalizedProblemJson(),
                p.createdAt()
            ))
            .toList();

        UUID auditId = UUID.randomUUID();
        writeRepository.insertAuditLog(new StoredAuditLog(
            auditId,
            AdminAuditAction.RAW_CONTENT_INSPECTED,
            AdminAuditTargetType.PROBLEM_SESSION,
            problemSessionId.toString(),
            actorUserId,
            AdminRedactionPolicy.extractActorRole(authorities),
            null,
            null,
            "Audited elevated raw content inspection under ticket " + grant.ticketReference(),
            "{}",
            toJsonObject(Map.of(
                "grantId", grant.id().toString(),
                "ticketReference", grant.ticketReference(),
                "assetCount", assets.size(),
                "parseCount", parses.size()
            )),
            normalizeCorrelationId(),
            false,
            now
        ));
        metrics.recordRawContent("inspected");

        return new AdminRawContentInspectionView(
            INSPECTION_SCHEMA_VERSION,
            grant.id(),
            grant.problemSessionId(),
            grant.targetUserId(),
            grant.ticketReference(),
            grant.expiresAt(),
            auditId,
            assets,
            parses,
            now,
            0,
            false
        );
    }

    private void requireRawContentAccessEnabled() {
        if (!properties.rawContentAccessEnabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.ADMIN_RAW_CONTENT_ACCESS_DISABLED,
                "Elevated raw student content access is disabled",
                true,
                "RETRY_LATER"
            );
        }
        if (!properties.consoleEnabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.ADMIN_CONSOLE_DISABLED,
                "Admin support console is disabled",
                true,
                "RETRY_LATER"
            );
        }
    }

    private static void requireElevatedAuthority(Collection<? extends GrantedAuthority> authorities) {
        if (!AdminRedactionPolicy.hasRawContentAccess(authorities)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ADMIN_RAW_CONTENT_FORBIDDEN,
                "ADMIN_RAW_CONTENT_READ or ROLE_ADMIN_ENGINEERING authority is required for raw content access",
                false,
                "REQUEST_ELEVATED_ROLE"
            );
        }
    }

    private static AdminRawContentInspectionView.RawContentGrantView toGrantView(
        StoredRawContentGrant grant,
        boolean replayed
    ) {
        return new AdminRawContentInspectionView.RawContentGrantView(
            GRANT_SCHEMA_VERSION,
            grant.id(),
            grant.actorUserId(),
            grant.targetUserId(),
            grant.problemSessionId(),
            grant.ticketReference(),
            grant.reason(),
            grant.auditLogId(),
            grant.grantedAt(),
            grant.expiresAt(),
            replayed,
            0,
            false
        );
    }

    private static String normalizeCorrelationId() {
        String current = CorrelationIds.current();
        if (current == null || current.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return current.length() > 64 ? current.substring(0, 64) : current;
    }

    private static String toJsonObject(Map<String, Object> values) {
        try {
            return JSON_MAPPER.writeValueAsString(new LinkedHashMap<>(values));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize raw grant audit JSON", exception);
        }
    }

    private static ApiProblemException idempotencyConflict() {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            ApiErrorCode.ADMIN_IDEMPOTENCY_CONFLICT,
            "Idempotency-Key already used with a different raw-content grant request",
            false,
            "USE_NEW_IDEMPOTENCY_KEY"
        );
    }
}
