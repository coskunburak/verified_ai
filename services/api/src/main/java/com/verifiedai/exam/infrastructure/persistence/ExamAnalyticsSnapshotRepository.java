package com.verifiedai.exam.infrastructure.persistence;

import com.verifiedai.exam.domain.ExamAnalyticsPolicy.ItemTimingObservation;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy.ScoredSessionInput;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy.TrapIncidenceObservation;
import com.verifiedai.exam.domain.ExamProgressReport;
import com.verifiedai.exam.domain.ExamProgressReport.ScoreTrajectoryPointDto;
import com.verifiedai.exam.domain.ExamProgressReport.TrapRemediationDto;
import com.verifiedai.exam.domain.PacingAnalysisDto;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Owner-scoped persistence and upstream evidence reader for V063 {@code exam_analytics_snapshots}.
 * Never updates V058 score or raw evidence tables.
 */
@Repository
public class ExamAnalyticsSnapshotRepository {

    public record SnapshotRow(
            UUID snapshotId,
            UUID userId,
            String examCode,
            int mockCount,
            List<ScoreTrajectoryPointDto> netTrend,
            List<PacingAnalysisDto> pacingStats,
            TrapRemediationDto trapCorrelation,
            int conqueredTrapsCount,
            LocalDate asOfDate,
            String sourceFingerprint,
            String algorithmVersion,
            UUID sourceLastScoreId,
            String schemaVersion,
            Instant generatedAt,
            Instant updatedAt,
            Instant expiresAt
    ) {
        public ExamProgressReport toReport() {
            ExamProgressReport.EvidenceStatus evidenceStatus;
            BigDecimal deltaNet = null;
            if (mockCount == 0) {
                evidenceStatus = ExamProgressReport.EvidenceStatus.EMPTY;
            } else if (mockCount == 1) {
                evidenceStatus = ExamProgressReport.EvidenceStatus.INSUFFICIENT_FOR_TREND;
            } else {
                evidenceStatus = mockCount == 2
                        ? ExamProgressReport.EvidenceStatus.PARTIAL_TREND
                        : ExamProgressReport.EvidenceStatus.SUFFICIENT_EVIDENCE;
                deltaNet = netTrend.get(mockCount - 1).netScore()
                        .subtract(netTrend.get(0).netScore())
                        .setScale(2, java.math.RoundingMode.HALF_UP);
            }
            return new ExamProgressReport(
                    schemaVersion,
                    snapshotId,
                    userId,
                    examCode,
                    mockCount,
                    evidenceStatus,
                    deltaNet,
                    netTrend,
                    pacingStats,
                    trapCorrelation,
                    conqueredTrapsCount,
                    asOfDate,
                    algorithmVersion,
                    false,
                    0,
                    BigDecimal.ZERO,
                    generatedAt,
                    expiresAt
            );
        }
    }

    private static final Map<String, Set<String>> EXAM_CODE_COMPATIBLE_ALIASES = Map.of(
            "TR_YKS_TYT_MATH", Set.of("TR_YKS_TYT_MATH", "YKS_TYT", "YKS_TYT_MATH"),
            "TR_YKS_AYT_MATH", Set.of("TR_YKS_AYT_MATH", "YKS_AYT_SAY", "YKS_AYT", "YKS_AYT_MATH")
    );

    private static final TypeReference<List<ScoreTrajectoryPointDto>> TRAJECTORY_LIST_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<List<PacingAnalysisDto>> PACING_LIST_TYPE =
            new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper mapper;

    public ExamAnalyticsSnapshotRepository(JdbcClient jdbc, JsonMapper mapper) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc cannot be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
    }

    /**
     * Serializes concurrent first-hit snapshot generation for the same (owner, examCode).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockOwnerExam(UUID userId, String canonicalExamCode) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", "exam_analytics_v063:" + userId + ":" + canonicalExamCode)
                .query((rs, rowNum) -> Boolean.TRUE)
                .single();
    }

    /**
     * Selects the latest 20 scored sessions for (userId, canonicalExamCode) BEFORE applying LIMIT 20.
     */
    public List<ScoredSessionInput> findLatestScoredSessionsByUserAndExam(
            UUID userId,
            String canonicalExamCode,
            int limit
    ) {
        Set<String> allowedCodes = EXAM_CODE_COMPATIBLE_ALIASES.getOrDefault(
                canonicalExamCode,
                Set.of(canonicalExamCode)
        );
        int boundedLimit = Math.max(1, Math.min(limit, 20));
        return jdbc.sql("""
                SELECT score_id, session_id, user_id, net_score, score_version, session_submitted_at, updated_at
                FROM exam_session_scores
                WHERE user_id = :userId
                  AND exam_code IN (:examCodes)
                ORDER BY session_submitted_at DESC, session_id DESC
                LIMIT :limit
                """)
                .param("userId", userId)
                .param("examCodes", allowedCodes)
                .param("limit", boundedLimit)
                .query((rs, rowNum) -> new ScoredSessionInput(
                        rs.getObject("score_id", UUID.class),
                        rs.getObject("session_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        canonicalExamCode,
                        rs.getBigDecimal("net_score"),
                        rs.getInt("score_version"),
                        rs.getTimestamp("session_submitted_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ))
                .list();
    }

    /**
     * Loads per-question timing observations joined with V056 question bank estimated_time_seconds,
     * strictly filtered by owner and the selected session window.
     */
    public List<ItemTimingObservation> findItemTimingsForSessions(UUID userId, List<UUID> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT e.session_id,
                       e.evaluation_id,
                       COALESCE(b.topic_code, e.topic_code) AS topic_code,
                       COALESCE(b.subtopic_code, a.subtopic_id) AS subtopic_code,
                       a.skill_id,
                       e.time_spent_seconds,
                       b.estimated_time_seconds
                FROM exam_session_item_evaluations e
                JOIN exam_question_bank_items b
                  ON b.id = e.question_item_id
                LEFT JOIN exam_session_item_attributions a
                  ON a.item_evaluation_id = e.evaluation_id
                 AND a.user_id = :userId
                WHERE e.user_id = :userId
                  AND e.session_id IN (:sessionIds)
                ORDER BY e.session_id, e.position_index ASC, e.evaluation_id ASC
                """)
                .param("userId", userId)
                .param("sessionIds", sessionIds)
                .query((rs, rowNum) -> new ItemTimingObservation(
                        rs.getObject("session_id", UUID.class),
                        rs.getObject("evaluation_id", UUID.class),
                        rs.getString("topic_code"),
                        rs.getString("subtopic_code"),
                        rs.getString("skill_id"),
                        rs.getInt("time_spent_seconds"),
                        (Integer) rs.getObject("estimated_time_seconds")
                ))
                .list();
    }

    /**
     * Loads V061 post-assessment attribution rows for the owner's selected sessions.
     */
    public List<TrapIncidenceObservation> findTrapIncidencesForSessions(UUID userId, List<UUID> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT session_id,
                       item_evaluation_id,
                       attribution_category,
                       matched_mistake_code
                FROM exam_session_item_attributions
                WHERE user_id = :userId
                  AND session_id IN (:sessionIds)
                ORDER BY session_id, item_evaluation_id
                """)
                .param("userId", userId)
                .param("sessionIds", sessionIds)
                .query((rs, rowNum) -> new TrapIncidenceObservation(
                        rs.getObject("session_id", UUID.class),
                        rs.getObject("item_evaluation_id", UUID.class),
                        rs.getString("attribution_category"),
                        rs.getString("matched_mistake_code")
                ))
                .list();
    }

    public Optional<SnapshotRow> findFreshSnapshot(
            UUID userId,
            String canonicalExamCode,
            String sourceFingerprint,
            String algorithmVersion,
            LocalDate asOfDate,
            Instant now
    ) {
        return jdbc.sql("""
                SELECT snapshot_id, user_id, exam_code, mock_count, net_trend_json, pacing_stats_json,
                       trap_correlation_json, conquered_traps_count, as_of_date, source_fingerprint,
                       algorithm_version, source_last_score_id, schema_version, generated_at, updated_at, expires_at
                FROM exam_analytics_snapshots
                WHERE user_id = :userId
                  AND exam_code = :examCode
                  AND source_fingerprint = :sourceFingerprint
                  AND algorithm_version = :algorithmVersion
                  AND as_of_date = :asOfDate
                  AND expires_at > :now
                ORDER BY generated_at DESC, snapshot_id DESC
                LIMIT 1
                """)
                .param("userId", userId)
                .param("examCode", canonicalExamCode)
                .param("sourceFingerprint", sourceFingerprint)
                .param("algorithmVersion", algorithmVersion)
                .param("asOfDate", Date.valueOf(asOfDate))
                .param("now", Timestamp.from(now))
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SnapshotRow upsertSnapshot(
            ExamProgressReport report,
            String sourceFingerprint,
            UUID sourceLastScoreId
    ) {
        validateReportInvariants(report, sourceFingerprint, sourceLastScoreId);
        if (sourceLastScoreId != null) {
            Set<String> allowedCodes = EXAM_CODE_COMPATIBLE_ALIASES.getOrDefault(
                    report.examCode(),
                    Set.of(report.examCode())
            );
            Integer ownedScoreCount = jdbc.sql("""
                    SELECT COUNT(*) FROM exam_session_scores
                    WHERE score_id = :scoreId
                      AND user_id = :userId
                      AND exam_code IN (:examCodes)
                    """)
                    .param("scoreId", sourceLastScoreId)
                    .param("userId", report.userId())
                    .param("examCodes", allowedCodes)
                    .query(Integer.class)
                    .single();
            if (ownedScoreCount == null || ownedScoreCount != 1) {
                throw new IllegalArgumentException("source_last_score_id does not belong to user and canonical exam");
            }
        }

        String netTrendJson = mapper.writeValueAsString(report.scoreTrajectory());
        String pacingStatsJson = mapper.writeValueAsString(report.pacingByTopic());
        String trapCorrelationJson = mapper.writeValueAsString(report.trapRemediation());

        return jdbc.sql("""
                INSERT INTO exam_analytics_snapshots (
                    snapshot_id, user_id, exam_code, mock_count, net_trend_json, pacing_stats_json,
                    trap_correlation_json, conquered_traps_count, as_of_date, source_fingerprint,
                    algorithm_version, source_last_score_id, schema_version, generated_at, updated_at,
                    expires_at, training_eligible, llm_tokens_used, inference_cost_usd
                ) VALUES (
                    :snapshotId, :userId, :examCode, :mockCount, CAST(:netTrendJson AS jsonb),
                    CAST(:pacingStatsJson AS jsonb), CAST(:trapCorrelationJson AS jsonb),
                    :conqueredTrapsCount, :asOfDate, :sourceFingerprint, :algorithmVersion,
                    :sourceLastScoreId, :schemaVersion, :generatedAt, :updatedAt, :expiresAt,
                    FALSE, 0, 0.000000
                )
                ON CONFLICT (user_id, exam_code, source_fingerprint, algorithm_version)
                DO UPDATE SET
                    mock_count = EXCLUDED.mock_count,
                    net_trend_json = EXCLUDED.net_trend_json,
                    pacing_stats_json = EXCLUDED.pacing_stats_json,
                    trap_correlation_json = EXCLUDED.trap_correlation_json,
                    conquered_traps_count = EXCLUDED.conquered_traps_count,
                    as_of_date = EXCLUDED.as_of_date,
                    source_last_score_id = EXCLUDED.source_last_score_id,
                    schema_version = EXCLUDED.schema_version,
                    generated_at = EXCLUDED.generated_at,
                    updated_at = EXCLUDED.updated_at,
                    expires_at = EXCLUDED.expires_at
                WHERE exam_analytics_snapshots.user_id = EXCLUDED.user_id
                  AND exam_analytics_snapshots.exam_code = EXCLUDED.exam_code
                RETURNING snapshot_id, user_id, exam_code, mock_count, net_trend_json, pacing_stats_json,
                          trap_correlation_json, conquered_traps_count, as_of_date, source_fingerprint,
                          algorithm_version, source_last_score_id, schema_version, generated_at, updated_at, expires_at
                """)
                .param("snapshotId", report.snapshotId())
                .param("userId", report.userId())
                .param("examCode", report.examCode())
                .param("mockCount", report.mockCount())
                .param("netTrendJson", netTrendJson)
                .param("pacingStatsJson", pacingStatsJson)
                .param("trapCorrelationJson", trapCorrelationJson)
                .param("conqueredTrapsCount", report.conqueredTrapsCount())
                .param("asOfDate", Date.valueOf(report.asOfDate()))
                .param("sourceFingerprint", sourceFingerprint)
                .param("algorithmVersion", report.algorithmVersion())
                .param("sourceLastScoreId", sourceLastScoreId)
                .param("schemaVersion", report.schemaVersion())
                .param("generatedAt", Timestamp.from(report.generatedAt()))
                .param("updatedAt", Timestamp.from(report.generatedAt()))
                .param("expiresAt", Timestamp.from(report.expiresAt()))
                .query((rs, rowNum) -> mapRow(rs))
                .single();
    }

    public List<Map<String, Object>> exportByUserId(UUID userId) {
        return jdbc.sql("""
                SELECT snapshot_id, exam_code, mock_count, conquered_traps_count, as_of_date,
                       source_fingerprint, algorithm_version, source_last_score_id, schema_version,
                       generated_at, updated_at, expires_at, training_eligible, llm_tokens_used, inference_cost_usd
                FROM exam_analytics_snapshots
                WHERE user_id = :userId
                ORDER BY generated_at DESC, snapshot_id DESC
                """)
                .param("userId", userId)
                .query()
                .listOfRows();
    }

    public int deleteByUserId(UUID userId) {
        return jdbc.sql("DELETE FROM exam_analytics_snapshots WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }

    public int purgeExpired(Instant now) {
        return jdbc.sql("DELETE FROM exam_analytics_snapshots WHERE expires_at <= :now")
                .param("now", Timestamp.from(now))
                .update();
    }

    private SnapshotRow mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<ScoreTrajectoryPointDto> netTrend = mapper.readValue(
                rs.getString("net_trend_json"),
                TRAJECTORY_LIST_TYPE
        );
        List<PacingAnalysisDto> pacingStats = mapper.readValue(
                rs.getString("pacing_stats_json"),
                PACING_LIST_TYPE
        );
        TrapRemediationDto trapCorrelation = mapper.readValue(
                rs.getString("trap_correlation_json"),
                TrapRemediationDto.class
        );
        return new SnapshotRow(
                rs.getObject("snapshot_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("exam_code"),
                rs.getInt("mock_count"),
                netTrend,
                pacingStats,
                trapCorrelation,
                rs.getInt("conquered_traps_count"),
                rs.getDate("as_of_date").toLocalDate(),
                rs.getString("source_fingerprint"),
                rs.getString("algorithm_version"),
                rs.getObject("source_last_score_id", UUID.class),
                rs.getString("schema_version"),
                rs.getTimestamp("generated_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant()
        );
    }

    private static void validateReportInvariants(
            ExamProgressReport report,
            String sourceFingerprint,
            UUID sourceLastScoreId
    ) {
        Objects.requireNonNull(report, "report cannot be null");
        if (sourceFingerprint == null || !sourceFingerprint.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException("sourceFingerprint must be a 64-char lowercase hex SHA-256");
        }
        if (report.mockCount() == 0 && sourceLastScoreId != null) {
            throw new IllegalArgumentException("sourceLastScoreId must be null when mockCount == 0");
        }
        if (report.mockCount() > 0 && sourceLastScoreId == null) {
            throw new IllegalArgumentException("sourceLastScoreId is required when mockCount > 0");
        }
        if (report.trainingEligible() || report.llmTokensUsed() != 0 || report.inferenceCostUsd().signum() != 0) {
            throw new IllegalArgumentException("Privacy or cost invariant violated");
        }
    }
}
