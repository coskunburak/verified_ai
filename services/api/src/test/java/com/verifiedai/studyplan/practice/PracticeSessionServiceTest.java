package com.verifiedai.studyplan.practice;

import com.verifiedai.studyplan.practice.dto.PracticeItemSubmissionResponse;
import com.verifiedai.studyplan.practice.dto.PracticeSessionAbandonRequest;
import com.verifiedai.studyplan.practice.dto.StartPracticeSessionRequest;
import com.verifiedai.studyplan.practice.dto.SubmitPracticeItemRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PracticeSessionServiceTest {

    private PracticeSessionRepository sessionRepository;
    private PracticeSessionItemRepository itemRepository;
    private AdaptivePracticeQuestionSelector selector;
    private SymPyTwinVerifierClient twinVerifierClient;
    private PracticeSessionService service;
    private PracticeSessionPrivacyPurgeContributor purgeContributor;

    private final UUID userId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final UUID otherUserId = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @BeforeEach
    void setUp() {
        sessionRepository = mock(PracticeSessionRepository.class);
        itemRepository = mock(PracticeSessionItemRepository.class);
        selector = mock(AdaptivePracticeQuestionSelector.class);
        twinVerifierClient = mock(SymPyTwinVerifierClient.class);

        service = new PracticeSessionService(sessionRepository, itemRepository, selector, twinVerifierClient);
        purgeContributor = new PracticeSessionPrivacyPurgeContributor(sessionRepository);
    }

    @Test
    @DisplayName("INV-PRAC-004: Hint request within 60s verification lock is rejected and hints remain 0")
    void testHintLockRejectionDuringIndependentVerification() {
        UUID sessionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        PracticeSessionEntity session = new PracticeSessionEntity(
                sessionId, userId, null, PracticeSessionMode.DAILY_1_2_1,
                "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5500"), 4, Instant.now()
        );

        PracticeSessionItemEntity item = new PracticeSessionItemEntity(
                itemId, session, userId, 0, PracticeSlotRole.TRAP_CLOSURE,
                PracticeQuestionSource.DETERMINISTIC_TWIN_TIER1,
                "TWIN-01", "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5000"), new BigDecimal("0.5000"),
                new BigDecimal("0.4000"), new BigDecimal("0.6000"),
                "\\frac{x}{x-2} = 3", "Çöz", "Solve", "3", "{2, 3}",
                "hash", true, 60, Instant.now(), Instant.now()
        );
        session.addItem(item);

        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));

        SubmitPracticeItemRequest req = new SubmitPracticeItemRequest(null, 15, true);
        PracticeItemSubmissionResponse response = service.submitPracticeItemAnswer(sessionId, itemId, userId, req);

        assertTrue(response.verificationLockActive());
        assertTrue(response.remainingLockSeconds() > 0);
        assertEquals(0, item.getHintsUnlockedCount());
    }

    @Test
    @DisplayName("INV-PRAC-004: Rapid guess guard throws 422 without consuming attempt when t < 8s and answer is incorrect")
    void testRapidGuessGuardTriggered() {
        UUID sessionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        PracticeSessionEntity session = new PracticeSessionEntity(
                sessionId, userId, null, PracticeSessionMode.DAILY_1_2_1,
                "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5500"), 4, Instant.now()
        );

        PracticeSessionItemEntity item = new PracticeSessionItemEntity(
                itemId, session, userId, 0, PracticeSlotRole.TRAP_CLOSURE,
                PracticeQuestionSource.DETERMINISTIC_TWIN_TIER1,
                "TWIN-01", "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5000"), new BigDecimal("0.5000"),
                new BigDecimal("0.4000"), new BigDecimal("0.6000"),
                "\\frac{x}{x-2} = 3", "Çöz", "Solve", "3", "{2, 3}",
                "hash", true, 60, Instant.now(), Instant.now()
        );
        session.addItem(item);

        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(twinVerifierClient.evaluateSubmission(any(), any(), any(), any(), eq("wrong")))
                .thenReturn(new SymPyTwinVerifierClient.EvaluationResult(false, false, null, null, null));

        SubmitPracticeItemRequest req = new SubmitPracticeItemRequest("wrong", 4, false);

        assertThrows(RapidGuessGuardException.class, () ->
                service.submitPracticeItemAnswer(sessionId, itemId, userId, req)
        );

        // Assert attempt count was not consumed and status is still PENDING
        assertEquals(0, item.getAttemptCount());
        assertEquals(PracticeItemStatus.PENDING, item.getItemStatus());
    }

    @Test
    @DisplayName("INV-PRAC-004: Trap closure achieved when verified correct with zero hints unlocked")
    void testTrapClosureAchieved() {
        UUID sessionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        PracticeSessionEntity session = new PracticeSessionEntity(
                sessionId, userId, null, PracticeSessionMode.DAILY_1_2_1,
                "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5500"), 4, Instant.now()
        );

        PracticeSessionItemEntity item = new PracticeSessionItemEntity(
                itemId, session, userId, 0, PracticeSlotRole.TRAP_CLOSURE,
                PracticeQuestionSource.DETERMINISTIC_TWIN_TIER1,
                "TWIN-01", "MATH.ALG.RATIONAL_EQ", "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5000"), new BigDecimal("0.5000"),
                new BigDecimal("0.4000"), new BigDecimal("0.6000"),
                "\\frac{x}{x-2} = 3", "Çöz", "Solve", "3", "{2, 3}",
                "hash", true, 60, Instant.now(), Instant.now()
        );
        session.addItem(item);

        when(sessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.of(session));
        when(twinVerifierClient.evaluateSubmission(any(), any(), any(), any(), eq("3")))
                .thenReturn(new SymPyTwinVerifierClient.EvaluationResult(true, false, null, null, null));

        SubmitPracticeItemRequest req = new SubmitPracticeItemRequest("3", 20, false);
        PracticeItemSubmissionResponse res = service.submitPracticeItemAnswer(sessionId, itemId, userId, req);

        assertTrue(res.isCorrect());
        assertTrue(res.trapClosureAchieved());
        assertTrue(session.isTrapClosureAchieved());
        assertEquals(PracticeItemStatus.SUBMITTED_CORRECT, item.getItemStatus());
    }

    @Test
    @DisplayName("INV-PRAC-005: Broken Object Level Authorization (BOLA) returns 404 for cross-tenant access")
    void testBolaCrossTenantAccessReturns404() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findByIdAndUserId(sessionId, otherUserId)).thenReturn(Optional.empty());

        assertThrows(PracticeSessionNotFoundException.class, () ->
                service.getPracticeSession(sessionId, otherUserId)
        );
    }

    @Test
    @DisplayName("INV-PRAC-005: Privacy purge contributor executes cascading delete within SLA")
    void testPrivacyPurgeExecution() {
        long start = System.currentTimeMillis();
        purgeContributor.deleteUserData(userId, Instant.now());
        long duration = System.currentTimeMillis() - start;

        verify(sessionRepository, times(1)).deleteAllByUserId(userId);
        assertTrue(duration < 500, "Purge duration exceeded 500ms SLA: " + duration + "ms");
    }
}
