package com.verifiedai.studyplan.spaced;

import com.verifiedai.studyplan.spaced.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpacedReviewServiceTest {

    private SpacedReviewQueueRepository repository;
    private SpacedRepetitionScheduler scheduler;
    private JdbcClient jdbcClient;
    private SpacedReviewService service;
    private SpacedReviewPrivacyPurgeContributor purgeContributor;

    private final UUID userA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID userB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        repository = mock(SpacedReviewQueueRepository.class);
        scheduler = new SpacedRepetitionScheduler();
        jdbcClient = mock(JdbcClient.class, RETURNS_DEEP_STUBS);

        service = new SpacedReviewService(repository, scheduler, jdbcClient);
        purgeContributor = new SpacedReviewPrivacyPurgeContributor(repository);
    }

    @Test
    @DisplayName("INV-SR-004: 47-item backlog capped to 3 items with 7D immunity prioritized and interleaved")
    void testBacklogCapAndInterleaving() {
        Instant now = Instant.parse("2026-11-13T12:00:00Z");
        List<SpacedReviewQueueEntity> entities = new ArrayList<>();

        // Add 2 TRAP_IMMUNITY_7D items for Skill A
        for (int i = 0; i < 2; i++) {
            entities.add(new SpacedReviewQueueEntity(
                    UUID.randomUUID(), userA, "MATH.ALG.QUADRATIC_DISCRIMINANT", "TRAP_SIGN_DISTRIB_NEG",
                    SpacedTriggerType.TRAP_IMMUNITY_7D, new BigDecimal("0.7100"), new BigDecimal("7.00"),
                    now.minus(Duration.ofHours(2)), UUID.randomUUID(), "prompt_immunity_" + i,
                    SpacedQueueStatus.PENDING
            ));
        }

        // Add 45 RETENTION_DECAY items across Skill B and Skill C
        for (int i = 0; i < 25; i++) {
            entities.add(new SpacedReviewQueueEntity(
                    UUID.randomUUID(), userA, "MATH.CALC.CHAIN_RULE_COMPOSITE", null,
                    SpacedTriggerType.RETENTION_DECAY, new BigDecimal("0.6500"), new BigDecimal("4.00"),
                    now.minus(Duration.ofDays(1)), null, "prompt_b_" + i,
                    SpacedQueueStatus.PENDING
            ));
        }
        for (int i = 0; i < 20; i++) {
            entities.add(new SpacedReviewQueueEntity(
                    UUID.randomUUID(), userA, "MATH.TRIG.DOUBLE_ANGLE_IDENTITY", null,
                    SpacedTriggerType.RETENTION_DECAY, new BigDecimal("0.6800"), new BigDecimal("5.00"),
                    now.minus(Duration.ofDays(2)), null, "prompt_c_" + i,
                    SpacedQueueStatus.PENDING
            ));
        }

        when(repository.findByUserIdAndStatusIn(eq(userA), any())).thenReturn(entities);
        when(repository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return entities.stream().filter(e -> e.getId().equals(id)).findFirst();
        });

        SpacedQueueResponse response = service.getDailySpacedQueue(userA, now);

        assertEquals(3, response.surfacedCount(), "Daily queue must be capped at 3 items");
        assertEquals(3, response.items().size());
        assertTrue(response.interleavingSatisfied(), "Interleaving invariant must be satisfied");

        // First item should be TRAP_IMMUNITY_7D (prioritized)
        assertEquals(SpacedTriggerType.TRAP_IMMUNITY_7D, response.items().get(0).triggerType());

        // At least 2 distinct skills represented
        assertTrue(response.interleavedSkillCount() >= 2);

        // Adjacent items must not share the same skill code
        assertNotEquals(response.items().get(0).canonicalSkillCode(), response.items().get(1).canonicalSkillCode());
    }

    @Test
    @DisplayName("INV-SR-002: Transition to STABLE_RESOLVED on independent pass vs RELAPSED on trap error")
    void testImmunityResolutionTransitions() {
        Instant now = Instant.parse("2026-11-13T12:00:00Z");
        UUID itemId = UUID.randomUUID();

        SpacedReviewQueueEntity entity = new SpacedReviewQueueEntity(
                itemId, userA, "MATH.ALG.QUADRATIC_DISCRIMINANT", "TRAP_SIGN_DISTRIB_NEG",
                SpacedTriggerType.TRAP_IMMUNITY_7D, new BigDecimal("0.7100"), new BigDecimal("7.00"),
                now.minus(Duration.ofHours(1)), UUID.randomUUID(), "prompt", SpacedQueueStatus.SERVED
        );

        when(repository.findByIdAndUserId(itemId, userA)).thenReturn(Optional.of(entity));

        // Case 1: Independent verification passed without triggering trap -> STABLE_RESOLVED
        SpacedItemCompleteRequest passRequest = new SpacedItemCompleteRequest(
                true, true, 9500L, null
        );
        SpacedItemCompletionResponse passResponse = service.completeQueueItem(userA, itemId, passRequest, now);

        assertEquals(MistakeResolutionStatus.STABLE_RESOLVED, passResponse.mistakeResolutionStatus());
        assertTrue(passResponse.updatedHalfLifeDays().compareTo(new BigDecimal("7.00")) > 0);

        // Reset entity to SERVED for Case 2
        entity.setStatus(SpacedQueueStatus.SERVED);
        entity.setHalfLifeDays(new BigDecimal("7.00"));

        // Case 2: Trap triggered -> RELAPSED
        SpacedItemCompleteRequest trapRequest = new SpacedItemCompleteRequest(
                false, false, 8500L, "TRAP_SIGN_DISTRIB_NEG"
        );
        SpacedItemCompletionResponse relapseResponse = service.completeQueueItem(userA, itemId, trapRequest, now);

        assertEquals(MistakeResolutionStatus.RELAPSED, relapseResponse.mistakeResolutionStatus());
        // Half-life decayed to max(0.50, 7.00 * 0.55 = 3.85)
        assertEquals(new BigDecimal("3.85"), relapseResponse.updatedHalfLifeDays());
    }

    @Test
    @DisplayName("INV-SR-006: BOLA protection returns 404 when User B accesses User A's item")
    void testBOLAProtectionTenantMismatch() {
        UUID itemId = UUID.randomUUID();
        when(repository.findByIdAndUserId(itemId, userB)).thenReturn(Optional.empty());

        SpacedItemCompleteRequest request = new SpacedItemCompleteRequest(true, true, 8500L, null);

        assertThrows(SpacedQueueItemNotFoundException.class, () ->
                service.completeQueueItem(userB, itemId, request, Instant.now())
        );

        assertThrows(SpacedQueueItemNotFoundException.class, () ->
                service.dismissQueueItem(userB, itemId, new SpacedItemDismissRequest("SCHEDULE_LATER"), Instant.now())
        );
    }

    @Test
    @DisplayName("INV-SR-006: Single-query privacy purge executes within 500ms SLA")
    void testSingleQueryPrivacyPurge() {
        when(repository.purgeAllByUserId(userA)).thenReturn(5);

        long start = System.currentTimeMillis();
        purgeContributor.deleteUserData(userA, Instant.now());
        long elapsed = System.currentTimeMillis() - start;

        verify(repository, times(1)).purgeAllByUserId(userA);
        assertTrue(elapsed < 500, "Purge must complete in under 500 ms SLA");
    }
}
