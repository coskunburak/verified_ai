package com.verifiedai.admin.application;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.admin.domain.AdminAuditAction;
import com.verifiedai.admin.domain.AdminAuditTargetType;
import com.verifiedai.admin.domain.AdminRedactionPolicy;
import com.verifiedai.admin.domain.WrongAnswerReportReason;
import com.verifiedai.admin.domain.WrongAnswerReportStateMachine;
import com.verifiedai.admin.domain.WrongAnswerReportStatus;
import com.verifiedai.admin.domain.WrongAnswerResolutionCode;
import com.verifiedai.admin.infrastructure.AdminSupportProperties;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository.ProblemReportSnapshot;
import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository;
import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository.StoredAuditLog;
import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository.StoredWrongAnswerReport;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service managing BOLA-checked learner wrong-answer report submission
 * and optimistic-locked, audited admin queue review/resolution.
 */
@Service
public class WrongAnswerReportService {

    public static final String QUEUE_SCHEMA_VERSION = "wrong-answer-report-queue-v1";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final AdminSupportReadJdbcRepository readRepository;
    private final AdminSupportWriteJdbcRepository writeRepository;
    private final AdminSupportProperties properties;
    private final AdminSupportMetrics metrics;
    private final Clock clock;

    public WrongAnswerReportService(
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
    public WrongAnswerReportView submitLearnerReport(
        UUID userId,
        UUID problemSessionId,
        String rawReportReason,
        String rawUserNote,
        String rawIdempotencyKey
    ) {
        requireConsoleEnabled();
        Objects.requireNonNull(userId, "userId must not be null");
        if (!readRepository.isAccountActive(userId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Active account required to submit wrong-answer reports",
                false,
                "SIGN_IN"
            );
        }
        if (problemSessionId == null) {
            throw validationFailed("problemSessionId is required");
        }
        String idempotencyKey = AdminRedactionPolicy.validateIdempotencyKey(rawIdempotencyKey);
        WrongAnswerReportReason reason = parseReportReason(rawReportReason);
        String userNote = AdminRedactionPolicy.validateOptionalUserNote(rawUserNote);

        String payloadHash = AdminRedactionPolicy.sha256Hex(
            "WAR_CREATE|" + userId + "|" + problemSessionId + "|" + reason.name() + "|"
                + (userNote == null ? "" : userNote)
        );

        writeRepository.acquireAdvisoryLock("war-create:" + userId + ":" + idempotencyKey);
        Optional<StoredWrongAnswerReport> existing = writeRepository.findWrongAnswerReportByUserAndIdempotencyKey(
            userId,
            idempotencyKey
        );
        if (existing.isPresent()) {
            StoredWrongAnswerReport report = existing.get();
            if (!report.problemSessionId().equals(problemSessionId) || !payloadHash.equals(report.payloadHash())) {
                metrics.recordWrongAnswer("idempotency_conflict");
                throw idempotencyConflict();
            }
            metrics.recordWrongAnswer("replayed");
            return toView(report, true);
        }

        // INV-ADM-005: BOLA protection — foreign or missing problem session returns indistinguishable 404
        readRepository.findOwnedProblemSession(problemSessionId, userId)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.PROBLEM_SESSION_NOT_FOUND,
                "Problem session not found: " + problemSessionId,
                false,
                "CHECK_PROBLEM_SESSION_ID"
            ));

        ProblemReportSnapshot snapshot = readRepository.findProblemSnapshotsForReport(problemSessionId);
        Instant now = clock.instant();
        StoredWrongAnswerReport created = new StoredWrongAnswerReport(
            UUID.randomUUID(),
            userId,
            problemSessionId,
            idempotencyKey,
            payloadHash,
            reason,
            userNote,
            snapshot.verificationStatus(),
            snapshot.policyVersion(),
            snapshot.modelVersion(),
            snapshot.problemType(),
            WrongAnswerReportStatus.OPEN,
            null,
            null,
            null,
            null,
            normalizeCorrelationId(),
            0L,
            false,
            now,
            now,
            null
        );
        writeRepository.insertWrongAnswerReport(created);
        metrics.recordWrongAnswer("OPEN");
        return toView(created, false);
    }

    @Transactional(readOnly = true)
    public WrongAnswerReportView.WrongAnswerReportQueueView listReports(String rawStatus, int requestedLimit) {
        requireConsoleEnabled();
        if (requestedLimit < 1 || requestedLimit > 200) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "Queue limit must be between 1 and 200",
                false,
                "FIX_LIMIT_PARAM"
            );
        }
        WrongAnswerReportStatus statusFilter = null;
        if (rawStatus != null && !rawStatus.isBlank() && !"ALL".equalsIgnoreCase(rawStatus.trim())) {
            statusFilter = parseReportStatus(rawStatus);
        }
        int effectiveLimit = Math.min(requestedLimit, properties.maxLookupLimit());
        List<WrongAnswerReportView> reports = writeRepository.listWrongAnswerReports(statusFilter, effectiveLimit)
            .stream()
            .map(r -> toView(r, false))
            .toList();
        return new WrongAnswerReportView.WrongAnswerReportQueueView(
            QUEUE_SCHEMA_VERSION,
            reports.size(),
            reports,
            clock.instant(),
            0,
            false
        );
    }

    @Transactional
    public WrongAnswerReportView reviewReport(
        UUID reportId,
        String rawTargetStatus,
        String rawResolutionCode,
        String rawResolutionNote,
        String rawAuditReason,
        UUID linkedRemediationAuditId,
        Long expectedVersion,
        String rawIdempotencyKey,
        UUID actorUserId,
        String actorRole
    ) {
        requireConsoleEnabled();
        Objects.requireNonNull(reportId, "reportId must not be null");
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");

        String idempotencyKey = AdminRedactionPolicy.validateIdempotencyKey(rawIdempotencyKey);
        if (expectedVersion == null || expectedVersion < 0) {
            throw validationFailed("expectedVersion must be non-negative");
        }
        WrongAnswerReportStatus targetStatus = parseReportStatus(rawTargetStatus);
        WrongAnswerResolutionCode resolutionCode = parseResolutionCode(rawResolutionCode);
        String auditReason = AdminRedactionPolicy.validateStandardReason(rawAuditReason);
        String trimmedNote = rawResolutionNote == null || rawResolutionNote.isBlank() ? null : rawResolutionNote.trim();

        String payloadHash = AdminRedactionPolicy.sha256Hex(
            "WAR_REVIEW|" + reportId + "|" + targetStatus.name() + "|"
                + (resolutionCode != null ? resolutionCode.name() : "null") + "|"
                + (trimmedNote == null ? "" : trimmedNote) + "|" + auditReason + "|" + expectedVersion
        );

        writeRepository.acquireAdvisoryLock("war-review-idem:" + idempotencyKey);
        Optional<StoredAuditLog> existingAudit = writeRepository.findAuditByIdempotencyKey(idempotencyKey);
        if (existingAudit.isPresent()) {
            StoredAuditLog audit = existingAudit.get();
            if (audit.action() != AdminAuditAction.WRONG_ANSWER_REPORT_REVIEWED
                || !reportId.toString().equals(audit.targetId())
                || !payloadHash.equals(audit.payloadHash())) {
                metrics.recordWrongAnswer("review_idempotency_conflict");
                throw idempotencyConflict();
            }
            StoredWrongAnswerReport existingReport = writeRepository.findWrongAnswerReportById(reportId)
                .orElseThrow(() -> reportNotFound(reportId));
            metrics.recordWrongAnswer("review_replayed");
            return toView(existingReport, true);
        }

        writeRepository.acquireAdvisoryLock("war-report:" + reportId);
        StoredWrongAnswerReport existing = writeRepository.findWrongAnswerReportById(reportId)
            .orElseThrow(() -> reportNotFound(reportId));

        WrongAnswerReportStateMachine.validateTransition(
            existing.status(),
            targetStatus,
            resolutionCode,
            trimmedNote
        );

        if (existing.version() != expectedVersion) {
            metrics.recordWrongAnswer("optimistic_conflict");
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.OPTIMISTIC_CONFLICT,
                "Wrong-answer report version mismatch for " + reportId,
                true,
                "REFRESH_AND_RETRY"
            );
        }

        if (linkedRemediationAuditId != null && writeRepository.findAuditById(linkedRemediationAuditId).isEmpty()) {
            throw validationFailed("linkedRemediationAuditId does not reference an existing audit log");
        }

        Instant now = clock.instant();
        Instant resolvedAt = targetStatus.isTerminal() ? now : null;
        int updated = writeRepository.updateWrongAnswerReportOptimistic(
            reportId,
            targetStatus,
            actorUserId,
            resolutionCode,
            trimmedNote,
            linkedRemediationAuditId,
            expectedVersion,
            now,
            resolvedAt
        );
        if (updated != 1) {
            metrics.recordWrongAnswer("optimistic_conflict");
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.OPTIMISTIC_CONFLICT,
                "Wrong-answer report concurrently modified for " + reportId,
                true,
                "REFRESH_AND_RETRY"
            );
        }

        StoredWrongAnswerReport after = writeRepository.findWrongAnswerReportById(reportId).orElseThrow();
        writeRepository.insertAuditLog(new StoredAuditLog(
            UUID.randomUUID(),
            AdminAuditAction.WRONG_ANSWER_REPORT_REVIEWED,
            AdminAuditTargetType.WRONG_ANSWER_REPORT,
            reportId.toString(),
            actorUserId,
            normalizeRole(actorRole),
            idempotencyKey,
            payloadHash,
            auditReason,
            reportStateJson(existing),
            reportStateJson(after),
            normalizeCorrelationId(),
            false,
            now
        ));
        metrics.recordWrongAnswer(targetStatus.name());
        return toView(after, false);
    }

    private void requireConsoleEnabled() {
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

    private static WrongAnswerReportReason parseReportReason(String raw) {
        if (raw == null || raw.isBlank()) {
            throw validationFailed("reportReason is required");
        }
        try {
            return WrongAnswerReportReason.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validationFailed("Unsupported reportReason: " + raw);
        }
    }

    private static WrongAnswerReportStatus parseReportStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw validationFailed("status is required");
        }
        try {
            return WrongAnswerReportStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validationFailed("Unsupported wrong-answer report status: " + raw);
        }
    }

    private static WrongAnswerResolutionCode parseResolutionCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return WrongAnswerResolutionCode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validationFailed("Unsupported resolutionCode: " + raw);
        }
    }

    private static WrongAnswerReportView toView(StoredWrongAnswerReport r, boolean replayed) {
        return new WrongAnswerReportView(
            r.id(),
            r.userId(),
            r.problemSessionId(),
            r.reportReason().name(),
            r.userNote(),
            r.verificationStatusSnapshot(),
            r.policyVersionSnapshot(),
            r.modelVersionSnapshot(),
            r.problemTypeSnapshot(),
            r.status().name(),
            r.reviewerUserId(),
            r.resolutionCode() == null ? null : r.resolutionCode().name(),
            r.resolutionNote(),
            r.linkedRemediationAuditId(),
            r.correlationId(),
            r.version(),
            r.trainingEligible(),
            r.createdAt(),
            r.updatedAt(),
            r.resolvedAt(),
            replayed
        );
    }

    private static String reportStateJson(StoredWrongAnswerReport r) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", r.status().name());
        state.put("resolutionCode", r.resolutionCode() != null ? r.resolutionCode().name() : null);
        state.put("version", r.version());
        try {
            return JSON_MAPPER.writeValueAsString(state);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize wrong-answer report state", exception);
        }
    }

    private static String normalizeRole(String actorRole) {
        if (actorRole == null || actorRole.isBlank()) {
            return "ROLE_ADMIN";
        }
        String trimmed = actorRole.trim();
        return trimmed.length() > 48 ? trimmed.substring(0, 48) : trimmed;
    }

    private static String normalizeCorrelationId() {
        String current = CorrelationIds.current();
        if (current == null || current.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return current.length() > 64 ? current.substring(0, 64) : current;
    }

    private static ApiProblemException validationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.ADMIN_VALIDATION_FAILED,
            message,
            false,
            "FIX_ADMIN_REQUEST"
        );
    }

    private static ApiProblemException reportNotFound(UUID reportId) {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.WRONG_ANSWER_REPORT_NOT_FOUND,
            "Wrong-answer report not found: " + reportId,
            false,
            "CHECK_REPORT_ID"
        );
    }

    private static ApiProblemException idempotencyConflict() {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            ApiErrorCode.ADMIN_IDEMPOTENCY_CONFLICT,
            "Idempotency-Key already used with a different wrong-answer report payload",
            false,
            "USE_NEW_IDEMPOTENCY_KEY"
        );
    }
}
