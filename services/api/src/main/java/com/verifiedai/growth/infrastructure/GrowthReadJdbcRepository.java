package com.verifiedai.growth.infrastructure;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Strictly read-only JDBC projection repository for Sprint 10.9 (INV-GRW-002, INV-GRW-004, INV-GRW-007).
 * Executes ONLY SELECT queries across existing platform tables and never mutates foreign-owned state.
 */
@Repository
public class GrowthReadJdbcRepository {

    private final JdbcClient jdbcClient;

    public GrowthReadJdbcRepository(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
    }

    public boolean isAccountActive(UUID userId) {
        Optional<String> status = jdbcClient.sql("SELECT status FROM users WHERE id = :userId")
            .param("userId", userId, Types.OTHER)
            .query(String.class)
            .optional();
        return status.map("ACTIVE"::equals).orElse(false);
    }

    public Optional<Instant> findUserCreatedAt(UUID userId) {
        return jdbcClient.sql("SELECT created_at FROM users WHERE id = :userId")
            .param("userId", userId, Types.OTHER)
            .query((rs, rowNum) -> {
                Timestamp ts = rs.getTimestamp("created_at");
                return ts != null ? ts.toInstant() : null;
            })
            .optional()
            .filter(Objects::nonNull);
    }

    public long countVerifiedLearningActions(UUID userId) {
        Long problemSessionsCount = jdbcClient.sql(
                "SELECT COUNT(*) FROM problem_sessions WHERE user_id = :userId"
            )
            .param("userId", userId, Types.OTHER)
            .query(Long.class)
            .single();
        Long analyticsCount = jdbcClient.sql(
                "SELECT COUNT(*) FROM analytics_events WHERE user_id = :userId"
            )
            .param("userId", userId, Types.OTHER)
            .query(Long.class)
            .single();
        return (problemSessionsCount != null ? problemSessionsCount : 0L)
            + (analyticsCount != null ? analyticsCount : 0L);
    }

    public boolean hasCompletedLearningMilestone(UUID userId) {
        if (countVerifiedLearningActions(userId) >= 1L) {
            return true;
        }
        Boolean hasProfile = jdbcClient.sql(
                "SELECT EXISTS (SELECT 1 FROM learning_profiles WHERE user_id = :userId)"
            )
            .param("userId", userId, Types.OTHER)
            .query(Boolean.class)
            .single();
        return Boolean.TRUE.equals(hasProfile);
    }

    public boolean hasRecentFriction(UUID userId, Instant since) {
        Boolean exists = jdbcClient.sql(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM wrong_answer_reports
                    WHERE user_id = :userId
                      AND status IN ('OPEN', 'IN_REVIEW', 'RESOLVED_CONFIRMED_DEFECT')
                      AND created_at >= :since
                )
                """
            )
            .param("userId", userId, Types.OTHER)
            .param("since", Timestamp.from(since), Types.TIMESTAMP)
            .query(Boolean.class)
            .single();
        return Boolean.TRUE.equals(exists);
    }

    public boolean hasActiveExamSession(UUID userId) {
        Boolean exists = jdbcClient.sql(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM exam_sessions
                    WHERE user_id = :userId
                      AND status IN ('IN_PROGRESS', 'OFFLINE_GRACE_BUFFER')
                )
                """
            )
            .param("userId", userId, Types.OTHER)
            .query(Boolean.class)
            .single();
        return Boolean.TRUE.equals(exists);
    }
}
