package com.verifiedai.mistake.passport.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MistakePassportProjectionRepository {

    private static final String PASSPORT_PROJECTION_SQL = """
        SELECT
            mdp.id AS mistake_dna_id,
            mdp.mistake_code,
            mdp.mistake_family,
            mdp.skill_node_id,
            mdp.lifecycle_status,
            mdp.occurrence_count,
            mdp.consecutive_solo_passes,
            mdp.last_detected_at,
            mrt.retention_unlock_eligible_at,
            mrt.conquered_at,
            COALESCE(lsms.observed_outcome_score, 0.0000) AS observed_outcome_score,
            COALESCE(lsms.independent_competence_score, 0.0000) AS independent_competence_score,
            COALESCE(lsms.posterior_mastery_probability, 0.0000) AS posterior_mastery_probability,
            COALESCE(lsms.retention_probability, 0.0000) AS retention_probability,
            COALESCE(lsms.scaffold_dependency_index, 0.0000) AS scaffold_dependency_index,
            ctd.id AS active_twin_drill_id,
            ctd.drill_status AS active_twin_status,
            ctd.latest_counterexample_payload
        FROM mistake_dna_profiles mdp
        LEFT JOIN learner_skill_mastery_states lsms
            ON lsms.learner_id = mdp.learner_id
           AND lsms.skill_node_id = mdp.skill_node_id
        LEFT JOIN LATERAL (
            SELECT
                t.retention_unlock_eligible_at,
                t.conquered_at
            FROM mistake_retention_transitions t
            WHERE t.mistake_dna_id = mdp.id
            ORDER BY t.transitioned_at DESC
            LIMIT 1
        ) mrt ON TRUE
        LEFT JOIN LATERAL (
            SELECT
                d.id,
                d.drill_status,
                d.latest_counterexample_payload
            FROM counterfactual_twin_drills d
            WHERE d.learner_id = mdp.learner_id
              AND d.mistake_dna_id = mdp.id
            ORDER BY d.created_at DESC
            LIMIT 1
        ) ctd ON TRUE
        WHERE mdp.learner_id = :learnerId
        ORDER BY
            CASE mdp.lifecycle_status
                WHEN 'RECURRING_PATTERN' THEN 1
                WHEN 'IN_REMEDIATION' THEN 2
                WHEN 'RESOLVED_PENDING_RETENTION' THEN 3
                WHEN 'STABLE_RESOLVED' THEN 4
                ELSE 5
            END,
            mdp.last_detected_at DESC
        """;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public MistakePassportProjectionRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public List<ExamTrapItemDto> fetchPassportItemsByLearnerId(UUID learnerId) {
        return jdbcClient.sql(PASSPORT_PROJECTION_SQL)
            .param("learnerId", learnerId)
            .query((rs, rowNum) -> {
                UUID mistakeDnaId = rs.getObject("mistake_dna_id", UUID.class);
                String mistakeCode = rs.getString("mistake_code");
                String normalizedKey = mistakeCode.toLowerCase();

                SkillFiveMetricsSnapshot metrics = new SkillFiveMetricsSnapshot(
                    rs.getBigDecimal("observed_outcome_score"),
                    rs.getBigDecimal("independent_competence_score"),
                    rs.getBigDecimal("posterior_mastery_probability"),
                    rs.getBigDecimal("retention_probability"),
                    rs.getBigDecimal("scaffold_dependency_index")
                );

                UUID twinId = rs.getObject("active_twin_drill_id", UUID.class);
                ActiveTwinRemediationLink twinLink = (twinId != null)
                    ? new ActiveTwinRemediationLink(
                        twinId,
                        rs.getString("active_twin_status"),
                        "verifiedai://twin-challenge/" + twinId
                    )
                    : null;

                CounterexampleProofSnapshot counterexample = parseCounterexample(
                    rs.getString("latest_counterexample_payload")
                );

                return new ExamTrapItemDto(
                    mistakeDnaId,
                    mistakeCode,
                    rs.getString("mistake_family"),
                    rs.getString("skill_node_id"),
                    "trap." + normalizedKey + ".title",
                    "trap." + normalizedKey + ".coaching_framing",
                    TrapLifecycleStatus.valueOf(rs.getString("lifecycle_status")),
                    rs.getInt("occurrence_count"),
                    rs.getInt("consecutive_solo_passes"),
                    toInstant(rs.getTimestamp("last_detected_at")),
                    toInstant(rs.getTimestamp("retention_unlock_eligible_at")),
                    toInstant(rs.getTimestamp("conquered_at")),
                    metrics,
                    twinLink,
                    counterexample
                );
            })
            .list();
    }

    private Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private CounterexampleProofSnapshot parseCounterexample(String jsonPayload) {
        if (jsonPayload == null || jsonPayload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonPayload, CounterexampleProofSnapshot.class);
        } catch (Exception e) {
            return null;
        }
    }
}
