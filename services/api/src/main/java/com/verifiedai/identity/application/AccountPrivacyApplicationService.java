package com.verifiedai.identity.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.identity.infrastructure.persistence.RefreshTokenJpaRepository;
import com.verifiedai.identity.infrastructure.persistence.SessionJpaRepository;
import com.verifiedai.identity.infrastructure.persistence.UserJpaEntity;
import com.verifiedai.identity.infrastructure.persistence.UserJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountPrivacyApplicationService {
    private static final String EXPORT_SCHEMA_VERSION = "phase3-account-v1";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final UserJpaRepository userRepository;
    private final SessionJpaRepository sessionRepository;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final List<AccountDataLifecycleContributor> lifecycleContributors;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final Clock clock;
    private final AccountPrivacyMetrics metrics;

    AccountPrivacyApplicationService(
        UserJpaRepository userRepository,
        SessionJpaRepository sessionRepository,
        RefreshTokenJpaRepository refreshTokenRepository,
        List<AccountDataLifecycleContributor> lifecycleContributors,
        JdbcTemplate jdbcTemplate,
        Clock clock,
        AccountPrivacyMetrics metrics
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.lifecycleContributors = lifecycleContributors;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public AccountStateResult currentAccount(UUID userId) {
        return toAccountState(requireUser(userId));
    }

    @Transactional
    public DataExportResult requestExport(UUID userId) {
        Instant now = clock.instant();
        UserJpaEntity user = requireActiveUserForUpdate(userId);
        UUID exportId = UUID.randomUUID();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("schemaVersion", EXPORT_SCHEMA_VERSION);
        content.put("generatedAt", now.toString());
        content.put("account", Map.of(
            "userId", user.id().toString(),
            "status", user.status(),
            "createdAt", user.createdAt().toString()
        ));
        content.put("sessions", sessionSummary(userId));
        for (AccountDataLifecycleContributor contributor : lifecycleContributors) {
            content.put(contributor.category(), contributor.exportUserData(userId));
        }

        jdbcTemplate.update(
            """
            insert into data_exports (
                id, user_id, status, schema_version, content_json, requested_at, completed_at, expires_at
            ) values (?, ?, 'READY', ?, ?::jsonb, ?, ?, ?)
            """,
            exportId,
            userId,
            EXPORT_SCHEMA_VERSION,
            writeJson(content),
            timestamp(now),
            timestamp(now),
            timestamp(now.plus(7, ChronoUnit.DAYS))
        );
        recordPrivacyEvent(userId, "DATA_EXPORT_REQUESTED", "USER_REQUEST");
        metrics.exportRequested();
        return requireExport(userId, exportId, now);
    }

    @Transactional(readOnly = true)
    public DataExportResult exportStatus(UUID userId, UUID exportId) {
        return requireExport(userId, exportId, clock.instant());
    }

    @Transactional
    public Map<String, Object> downloadExport(UUID userId, UUID exportId) {
        Instant now = clock.instant();
        ExportRecord export = exportRecord(userId, exportId)
            .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, ApiErrorCode.DATA_EXPORT_NOT_FOUND, "Data export was not found", "RETRY"));
        if (export.expiresAt().isBefore(now)) {
            markExportExpired(exportId);
            throw problem(HttpStatus.GONE, ApiErrorCode.DATA_EXPORT_EXPIRED, "Data export has expired", "RETRY");
        }
        jdbcTemplate.update(
            "update data_exports set downloaded_at = ? where id = ? and user_id = ?",
            timestamp(now),
            exportId,
            userId
        );
        recordPrivacyEvent(userId, "DATA_EXPORT_DOWNLOADED", "USER_REQUEST");
        metrics.exportDownloaded();
        try {
            return objectMapper.readValue(export.contentJson(), MAP_TYPE);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored data export content is invalid", exception);
        }
    }

    @Transactional
    public DeletionRequestResult requestDeletion(UUID userId) {
        Instant now = clock.instant();
        UserJpaEntity user = requireUserForUpdate(userId);
        user.requestDeletion(now);
        recordPrivacyEvent(userId, "ACCOUNT_DELETION_REQUESTED", "USER_REQUEST");
        metrics.deletionRequested();
        return new DeletionRequestResult(user.id(), user.status(), user.deletionRequestedAt(), user.deletedAt());
    }

    @Transactional(readOnly = true)
    public DeletionRequestResult deletionRequest(UUID userId) {
        UserJpaEntity user = requireUser(userId);
        return new DeletionRequestResult(user.id(), user.status(), user.deletionRequestedAt(), user.deletedAt());
    }

    @Transactional
    public DeletionRequestResult confirmDeletion(UUID userId, String confirmationText) {
        if (!"DELETE".equals(confirmationText)) {
            throw problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.DELETION_CONFIRMATION_INVALID,
                "Deletion confirmation must be DELETE",
                "RETRY"
            );
        }
        Instant now = clock.instant();
        UserJpaEntity user = requireUserForUpdate(userId);
        if (user.deleted()) {
            return new DeletionRequestResult(user.id(), user.status(), user.deletionRequestedAt(), user.deletedAt());
        }
        user.markDeletionInProgress(now);
        refreshTokenRepository.revokeActiveByUserId(userId, now);
        sessionRepository.revokeActiveByUserId(userId, now, "ACCOUNT_DELETED");
        for (AccountDataLifecycleContributor contributor : lifecycleContributors) {
            contributor.deleteUserData(userId, now);
        }
        user.markDeleted(now);
        recordPrivacyEvent(userId, "ACCOUNT_DELETED", "USER_CONFIRMED");
        metrics.deletionCompleted();
        return new DeletionRequestResult(user.id(), user.status(), user.deletionRequestedAt(), user.deletedAt());
    }

    private List<Map<String, Object>> sessionSummary(UUID userId) {
        return jdbcTemplate.query(
            """
            select id, status, created_at, last_seen_at, expires_at, revoked_at, revocation_reason
            from sessions
            where user_id = ?
            order by created_at desc
            limit 20
            """,
            (resultSet, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sessionId", resultSet.getObject("id", UUID.class).toString());
                row.put("status", resultSet.getString("status"));
                row.put("createdAt", resultSet.getTimestamp("created_at").toInstant().toString());
                row.put("lastSeenAt", resultSet.getTimestamp("last_seen_at").toInstant().toString());
                row.put("expiresAt", resultSet.getTimestamp("expires_at").toInstant().toString());
                Timestamp revokedAt = resultSet.getTimestamp("revoked_at");
                row.put("revokedAt", revokedAt == null ? null : revokedAt.toInstant().toString());
                row.put("revocationReason", resultSet.getString("revocation_reason"));
                return row;
            },
            userId
        );
    }

    private UserJpaEntity requireActiveUserForUpdate(UUID userId) {
        UserJpaEntity user = requireUserForUpdate(userId);
        if (!user.active()) {
            throw inactiveAccountProblem(user.status());
        }
        return user;
    }

    private UserJpaEntity requireUserForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> inactiveAccountProblem("MISSING"));
    }

    private UserJpaEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> inactiveAccountProblem("MISSING"));
    }

    private AccountStateResult toAccountState(UserJpaEntity user) {
        return new AccountStateResult(
            user.id(),
            user.status(),
            user.createdAt(),
            user.deletionRequestedAt(),
            user.deletedAt()
        );
    }

    private DataExportResult requireExport(UUID userId, UUID exportId, Instant now) {
        ExportRecord record = exportRecord(userId, exportId)
            .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, ApiErrorCode.DATA_EXPORT_NOT_FOUND, "Data export was not found", "RETRY"));
        if (record.expiresAt().isBefore(now)) {
            markExportExpired(exportId);
            return new DataExportResult(
                record.exportId(),
                "EXPIRED",
                record.schemaVersion(),
                record.requestedAt(),
                record.completedAt(),
                record.downloadedAt(),
                record.expiresAt()
            );
        }
        return new DataExportResult(
            record.exportId(),
            record.status(),
            record.schemaVersion(),
            record.requestedAt(),
            record.completedAt(),
            record.downloadedAt(),
            record.expiresAt()
        );
    }

    private java.util.Optional<ExportRecord> exportRecord(UUID userId, UUID exportId) {
        return jdbcTemplate.query(
            """
            select id, status, schema_version, content_json::text, requested_at, completed_at, downloaded_at, expires_at
            from data_exports
            where id = ? and user_id = ?
            """,
            preparedStatement -> {
                preparedStatement.setObject(1, exportId);
                preparedStatement.setObject(2, userId);
            },
            resultSet -> resultSet.next()
                ? java.util.Optional.of(new ExportRecord(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("status"),
                    resultSet.getString("schema_version"),
                    resultSet.getString("content_json"),
                    resultSet.getTimestamp("requested_at").toInstant(),
                    nullableInstant(resultSet.getTimestamp("completed_at")),
                    nullableInstant(resultSet.getTimestamp("downloaded_at")),
                    resultSet.getTimestamp("expires_at").toInstant()
                ))
                : java.util.Optional.empty()
        );
    }

    private void markExportExpired(UUID exportId) {
        jdbcTemplate.update("update data_exports set status = 'EXPIRED' where id = ? and status = 'READY'", exportId);
    }

    private void recordPrivacyEvent(UUID userId, String eventType, String reason) {
        jdbcTemplate.update(
            "insert into privacy_events (id, user_id, event_type, reason, created_at) values (?, ?, ?, ?, ?)",
            UUID.randomUUID(),
            userId,
            eventType,
            reason,
            timestamp(clock.instant())
        );
    }

    private String writeJson(Map<String, Object> content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception exception) {
            throw new IllegalStateException("Data export content could not be serialized", exception);
        }
    }

    private static ApiProblemException inactiveAccountProblem(String status) {
        ApiErrorCode code = "DELETED".equals(status) ? ApiErrorCode.ACCOUNT_DELETED : ApiErrorCode.ACCOUNT_NOT_ACTIVE;
        return problem(HttpStatus.FORBIDDEN, code, "Account is not active", "SIGN_IN");
    }

    private static ApiProblemException problem(HttpStatus status, ApiErrorCode code, String title, String userAction) {
        return new ApiProblemException(status, code, title, true, userAction);
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record ExportRecord(
        UUID exportId,
        String status,
        String schemaVersion,
        String contentJson,
        Instant requestedAt,
        Instant completedAt,
        Instant downloadedAt,
        Instant expiresAt
    ) {}
}
