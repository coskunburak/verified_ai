package com.verifiedai.exam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.verifiedai.exam.application.ExamReadinessProjectionService;
import com.verifiedai.exam.domain.ActiveTrapImpactItem;
import com.verifiedai.exam.domain.ExamReadinessConfidenceTier;
import com.verifiedai.exam.domain.ExamReadinessProjection;
import com.verifiedai.exam.domain.ExamReadinessRationaleCode;
import com.verifiedai.exam.domain.ExamReadinessRepository;
import com.verifiedai.exam.domain.ExamReadinessTopicAllocation;
import com.verifiedai.exam.domain.SectionReadinessBreakdown;
import com.verifiedai.exam.infrastructure.privacy.ExamPrivacyLifecycleContributor;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Category B Testcontainers PostgreSQL 16 + Flyway V059 integration tests for
 * {@link ExamReadinessRepository}, CHECK constraint enforcement, and GDPR Article 17 purge.
 */
class ExamReadinessMigrationAndRepositoryTest extends PostgresIntegrationTestSupport {

    private static final UUID USER_A = UUID.fromString("96000000-0000-4000-8000-000000000a01");
    private static final UUID USER_B = UUID.fromString("96000000-0000-4000-8000-000000000b02");
    private static final Instant FIXED_NOW = Instant.parse("2026-11-16T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExamReadinessRepository readinessRepository;

    @Autowired
    private ExamReadinessProjectionService readinessService;

    @Autowired
    private ExamPrivacyLifecycleContributor privacyContributor;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM exam_readiness_topic_allocations WHERE user_id IN (?, ?)", USER_A, USER_B);
        jdbcTemplate.update("DELETE FROM exam_readiness_projections WHERE user_id IN (?, ?)", USER_A, USER_B);
        ensureUserExists(USER_A);
        ensureUserExists(USER_B);
    }

    @Test
    void upsertRoundTripReplacesAllocationsAndEnforcesV059CheckConstraints() {
        UUID proj1Id = UUID.fromString("96000000-0000-4000-8000-000000001001");
        ExamReadinessProjection first = sampleProjection(
                proj1Id,
                USER_A,
                "TR_YKS_TYT_MATH",
                new BigDecimal("18.50"),
                new BigDecimal("24.00"),
                new BigDecimal("29.50"),
                new BigDecimal("0.6200"),
                ExamReadinessConfidenceTier.ESTABLISHED,
                false,
                0
        );
        ExamReadinessProjection savedFirst = readinessRepository.saveUpsert(first);
        assertEquals(proj1Id, savedFirst.projectionId());
        assertEquals(new BigDecimal("24.00"), savedFirst.estimatedNetMid());
        assertEquals(1, readinessRepository.findTopicAllocationsByUserId(USER_A).size());

        // Second upsert for the same (USER_A, TR_YKS_TYT_MATH) replaces the row & allocations cleanly
        UUID proj2Id = UUID.fromString("96000000-0000-4000-8000-000000001002");
        ExamReadinessProjection updated = sampleProjection(
                proj2Id,
                USER_A,
                "TR_YKS_TYT_MATH",
                new BigDecimal("20.00"),
                new BigDecimal("25.50"),
                new BigDecimal("30.00"),
                new BigDecimal("0.6800"),
                ExamReadinessConfidenceTier.ESTABLISHED,
                false,
                0
        );
        ExamReadinessProjection savedSecond = readinessRepository.saveUpsert(updated);
        assertEquals(proj2Id, savedSecond.projectionId());
        assertEquals(new BigDecimal("25.50"), savedSecond.estimatedNetMid());
        assertEquals(1, readinessRepository.findAllByUserId(USER_A).size());
        assertEquals(1, readinessRepository.findTopicAllocationsByUserId(USER_A).size());

        // Verify PostgreSQL CHECK constraints reject forbidden states
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("UPDATE exam_readiness_projections SET training_eligible = TRUE WHERE projection_id = ?", proj2Id));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("UPDATE exam_readiness_projections SET llm_tokens_used = 5 WHERE projection_id = ?", proj2Id));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("UPDATE exam_readiness_projections SET inference_cost_usd = 0.010000 WHERE projection_id = ?", proj2Id));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("UPDATE exam_readiness_projections SET estimated_net_low = 29.00, estimated_net_mid = 25.00 WHERE projection_id = ?", proj2Id));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update("UPDATE exam_readiness_projections SET net_lost_to_traps = -1.00 WHERE projection_id = ?", proj2Id));
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update(
                        "UPDATE exam_readiness_projections SET epistemic_confidence = 0.1200, confidence_tier = 'ESTABLISHED' WHERE projection_id = ?",
                        proj2Id));
    }

    @Test
    void serviceComputesAgainstV054CatalogAndPrivacyPurgeDeletesOnlyTargetUser() {
        ExamReadinessProjection computedA = readinessService.getOrComputeReadiness(USER_A, null, "YKS_TYT", true);
        assertEquals("TR_YKS_TYT_MATH", computedA.examCode());
        assertEquals(ExamReadinessConfidenceTier.PRELIMINARY, computedA.confidenceTier());
        assertTrue(computedA.calibrationRequired());
        assertFalse(computedA.trainingEligible());
        assertEquals(0, computedA.llmTokensUsed());

        ExamReadinessProjection cachedA = readinessService.getOrComputeReadiness(USER_A, null, "YKS_TYT", false);
        assertEquals(computedA.projectionId(), cachedA.projectionId());

        ExamReadinessProjection computedB = readinessService.getOrComputeReadiness(USER_B, null, "YKS_TYT", true);
        assertEquals(USER_B, computedB.userId());

        Map<String, Object> exportA = privacyContributor.exportUserData(USER_A);
        assertFalse(((List<?>) exportA.get("examReadinessProjections")).isEmpty());
        assertFalse(((List<?>) exportA.get("examReadinessTopicAllocations")).isEmpty());
        assertEquals(false, exportA.get("trainingEligible"));
        assertEquals(0, exportA.get("llmTokensUsed"));

        privacyContributor.deleteUserData(USER_A, Instant.now());
        assertTrue(readinessRepository.findAllByUserId(USER_A).isEmpty());
        assertTrue(readinessRepository.findTopicAllocationsByUserId(USER_A).isEmpty());
        assertEquals(1, readinessRepository.findAllByUserId(USER_B).size());
    }

    private void ensureUserExists(UUID userId) {
        jdbcTemplate.update(
                """
                INSERT INTO users (id, status, created_at, updated_at)
                VALUES (?, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """,
                userId
        );
    }

    private static ExamReadinessProjection sampleProjection(
            UUID projectionId,
            UUID userId,
            String examCode,
            BigDecimal low,
            BigDecimal mid,
            BigDecimal high,
            BigDecimal epistemicConfidence,
            ExamReadinessConfidenceTier tier,
            boolean calibrationRequired,
            int diagCount
    ) {
        UUID allocId = UUID.nameUUIDFromBytes((projectionId + ":1").getBytes());
        return new ExamReadinessProjection(
                projectionId,
                userId,
                examCode,
                null,
                null,
                40,
                new BigDecimal("0.2500"),
                low,
                mid,
                high,
                new BigDecimal("0.90"),
                new BigDecimal("32.00"),
                new BigDecimal("32.00").subtract(mid),
                new BigDecimal("1.75"),
                new BigDecimal("0.9200"),
                new BigDecimal("0.8400"),
                epistemicConfidence,
                tier,
                calibrationRequired,
                diagCount,
                4,
                25,
                1,
                List.of(new ActiveTrapImpactItem("ARITHMETIC_SLIP", "exam.readiness.trap.arithmeticSlip",
                        new BigDecimal("0.3200"), new BigDecimal("1.75"), 2)),
                List.of(new SectionReadinessBreakdown("TYT_MATH", "TYT Mathematics", 40,
                        low, mid, high, new BigDecimal("0.6800"), new BigDecimal("0.8400"), epistemicConfidence)),
                List.of(new ExamReadinessTopicAllocation(allocId, "TYT_MATH", "MATH.ALGEBRA.FACTORING",
                        "topic.math.algebra.factoring", new BigDecimal("10.00"), new BigDecimal("0.5200"),
                        new BigDecimal("0.7800"), new BigDecimal("1.450"), 45, 1,
                        ExamReadinessRationaleCode.ACTIVE_TRAP_MITIGATION, false)),
                "EXAM_READINESS_V1",
                false,
                0,
                BigDecimal.ZERO,
                FIXED_NOW
        );
    }
}
