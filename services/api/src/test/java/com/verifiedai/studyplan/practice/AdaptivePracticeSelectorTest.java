package com.verifiedai.studyplan.practice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdaptivePracticeSelectorTest {

    private CuratedPracticeQuestionBank questionBank;
    private SymPyTwinVerifierClient twinVerifierClient;
    private PracticeSessionItemRepository itemRepository;
    private AdaptivePracticeQuestionSelector selector;

    private final UUID userId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @BeforeEach
    void setUp() {
        questionBank = new CuratedPracticeQuestionBank();
        twinVerifierClient = mock(SymPyTwinVerifierClient.class);
        itemRepository = mock(PracticeSessionItemRepository.class);
        when(itemRepository.findAll()).thenReturn(List.of());

        selector = new AdaptivePracticeQuestionSelector(questionBank, twinVerifierClient, itemRepository);
    }

    @Test
    @DisplayName("INV-PRAC-001: 1-2-1 session allocates Trap Closure -> Spaced Drill -> Spaced Drill -> Exam Synthesis")
    void testStandard121SessionAllocation() {
        when(twinVerifierClient.generateTwin(any(), any(), any(), anyInt()))
                .thenReturn(new SymPyTwinVerifierClient.GeneratedTwinPayload(
                        "ALG_DOMAIN_EXCLUSION_MISSED",
                        "\\frac{x}{x-3} = 2",
                        "Denklemi çözünüz.",
                        "Solve the equation.",
                        "6",
                        "{3, 6}",
                        "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0",
                        new BigDecimal("0.5000")
                ));

        PracticeSessionEntity session = new PracticeSessionEntity(
                UUID.randomUUID(),
                userId,
                null,
                PracticeSessionMode.DAILY_1_2_1,
                "MATH.ALG.RATIONAL_EQ",
                "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5500"),
                4,
                Instant.now()
        );

        List<PracticeSessionItemEntity> items = selector.allocateSessionItems(
                session,
                userId,
                "MATH.ALG.RATIONAL_EQ",
                "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5500"),
                0,
                new BigDecimal("0.6000")
        );

        assertEquals(4, items.size());
        assertEquals(PracticeSlotRole.TRAP_CLOSURE, items.get(0).getSlotRole());
        assertEquals(PracticeQuestionSource.DETERMINISTIC_TWIN_TIER1, items.get(0).getQuestionSource());
        assertTrue(items.get(0).isTwinVerification());
        assertEquals(60, items.get(0).getIndependentLockSeconds());

        assertEquals(PracticeSlotRole.SPACED_DRILL, items.get(1).getSlotRole());
        assertEquals(PracticeQuestionSource.CURATED_BANK, items.get(1).getQuestionSource());
        assertFalse(items.get(1).isTwinVerification());

        assertEquals(PracticeSlotRole.SPACED_DRILL, items.get(2).getSlotRole());
        assertEquals(PracticeQuestionSource.CURATED_BANK, items.get(2).getQuestionSource());

        assertEquals(PracticeSlotRole.EXAM_SYNTHESIS, items.get(3).getSlotRole());
        assertEquals(PracticeQuestionSource.CURATED_BANK, items.get(3).getQuestionSource());
    }

    @Test
    @DisplayName("INV-PRAC-001: Depleted active trap debts fall back deterministically to EXAM_SYNTHESIS for Slot 1")
    void testTrapDebtDepletedFallbackToExamSynthesis() {
        PracticeSessionEntity session = new PracticeSessionEntity(
                UUID.randomUUID(),
                userId,
                null,
                PracticeSessionMode.DAILY_1_2_1,
                "MATH.ALG.RATIONAL_EQ",
                null, // 0 active trap debts
                new BigDecimal("0.6500"),
                4,
                Instant.now()
        );

        List<PracticeSessionItemEntity> items = selector.allocateSessionItems(
                session,
                userId,
                "MATH.ALG.RATIONAL_EQ",
                null,
                new BigDecimal("0.6500"),
                0,
                new BigDecimal("0.7000")
        );

        assertEquals(4, items.size());
        assertEquals(PracticeSlotRole.EXAM_SYNTHESIS, items.get(0).getSlotRole());
        assertEquals(PracticeSlotRole.SPACED_DRILL, items.get(1).getSlotRole());
        assertEquals(PracticeSlotRole.SPACED_DRILL, items.get(2).getSlotRole());
        assertEquals(PracticeSlotRole.EXAM_SYNTHESIS, items.get(3).getSlotRole());
    }

    @Test
    @DisplayName("INV-PRAC-002: ZPD difficulty curve clamps to [0.0500, 0.9500] using BigDecimal scale 4")
    void testZpdDifficultyClampingAndPrecision() {
        // Cold-start floor clamping: mastery 0.0000, trap closure (-0.0500), streak -3 (-0.0450)
        // base = -0.0950 -> clamped to 0.0500
        var lowZpd = selector.calculateZpd(new BigDecimal("0.0000"), PracticeSlotRole.TRAP_CLOSURE, -3);
        assertEquals(new BigDecimal("0.0500"), lowZpd.targetDifficulty());
        assertEquals(new BigDecimal("0.0000"), lowZpd.lowerBound());
        assertEquals(new BigDecimal("0.1500"), lowZpd.upperBound());
        assertEquals(4, lowZpd.targetDifficulty().scale());

        // Ceiling clamping: mastery 1.0000, exam synthesis (+0.0800), streak +3 (+0.0450)
        // base = 1.1250 -> clamped to 0.9500
        var highZpd = selector.calculateZpd(new BigDecimal("1.0000"), PracticeSlotRole.EXAM_SYNTHESIS, 3);
        assertEquals(new BigDecimal("0.9500"), highZpd.targetDifficulty());
        assertEquals(new BigDecimal("0.8500"), highZpd.lowerBound());
        assertEquals(new BigDecimal("1.0000"), highZpd.upperBound());

        // Mid-range: mastery 0.5000, spaced drill (+0.0200), streak 0
        // base = 0.5200
        var midZpd = selector.calculateZpd(new BigDecimal("0.5000"), PracticeSlotRole.SPACED_DRILL, 0);
        assertEquals(new BigDecimal("0.5200"), midZpd.targetDifficulty());
        assertEquals(new BigDecimal("0.4200"), midZpd.lowerBound());
        assertEquals(new BigDecimal("0.6200"), midZpd.upperBound());
    }

    @Test
    @DisplayName("INV-PRAC-003: Fail-closed fallback to curated bank when Tier-1 twin generation fails")
    void testFailClosedFallbackToCuratedBank() {
        when(twinVerifierClient.generateTwin(any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("Simulated verifier timeout or invariant violation"));

        PracticeSessionEntity session = new PracticeSessionEntity(
                UUID.randomUUID(),
                userId,
                null,
                PracticeSessionMode.DAILY_1_2_1,
                "MATH.ALG.RATIONAL_EQ",
                "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5000"),
                4,
                Instant.now()
        );

        List<PracticeSessionItemEntity> items = selector.allocateSessionItems(
                session,
                userId,
                "MATH.ALG.RATIONAL_EQ",
                "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5000"),
                0,
                new BigDecimal("0.5000")
        );

        assertEquals(4, items.size());
        PracticeSessionItemEntity item0 = items.get(0);
        assertEquals(PracticeSlotRole.TRAP_CLOSURE, item0.getSlotRole());
        // Fails closed to CURATED_BANK
        assertEquals(PracticeQuestionSource.CURATED_BANK, item0.getQuestionSource());
        assertTrue(item0.isTwinVerification());
        assertNotNull(item0.getSympyVerificationHash());
    }

    @Test
    @DisplayName("Candidate utility scoring correctly balances difficulty, trap targeting, and exam ROI")
    void testCandidateScoringFormula() {
        CuratedPracticeQuestion q = new CuratedPracticeQuestion(
                "TEST-01",
                "MATH.ALG.RATIONAL_EQ",
                "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5000"),
                "\\frac{x}{x-2} = 1",
                "Çözünüz",
                "Solve",
                "4",
                "{2, 4}",
                "hash"
        );

        // Target difficulty 0.5000, trap matches (1.0), exam ROI 0.5000
        // U = 0.5000 * (1 - 0) + 0.3000 * 1.0 + 0.2000 * 0.5000 = 0.5000 + 0.3000 + 0.1000 = 0.9000
        BigDecimal score = selector.scoreCandidate(
                q,
                "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5000"),
                new BigDecimal("0.5000")
        );

        assertEquals(new BigDecimal("0.9000"), score.setScale(4, RoundingMode.HALF_EVEN));
    }
}
