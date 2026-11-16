package com.verifiedai.exam.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.verifiedai.exam.application.ExamScoringEngineService.RegradeResult;
import com.verifiedai.exam.application.ExamScoringEngineService.ScoreSummaryResult;
import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.CognitiveTaxonomyLevel;
import com.verifiedai.exam.domain.DeterministicExamScoringPolicy;
import com.verifiedai.exam.domain.ExamBlueprintHashCalculator;
import com.verifiedai.exam.domain.ExamItemResponseState;
import com.verifiedai.exam.domain.ExamModels.CognitiveLevelMix;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamFamily;
import com.verifiedai.exam.domain.ExamModels.ExamSectionSpec;
import com.verifiedai.exam.domain.ExamModels.ExamStatus;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.exam.domain.ExamModels.NavigationPolicy;
import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import com.verifiedai.exam.domain.ExamModels.ScoringParameters;
import com.verifiedai.exam.domain.ExamScoreRegradeAudit;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSessionItem;
import com.verifiedai.exam.domain.ExamSessionScoreSnapshot;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.exam.domain.ProvenanceVerificationStatus;
import com.verifiedai.exam.domain.QuestionBankItem;
import com.verifiedai.exam.domain.QuestionBankRepository;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.domain.QuestionItemStatus;
import com.verifiedai.exam.domain.QuestionLicenseClassification;
import com.verifiedai.exam.domain.QuestionOption;
import com.verifiedai.exam.domain.QuestionProvenanceRecord;
import com.verifiedai.exam.domain.QuestionProvenanceSourceType;
import com.verifiedai.exam.domain.ScoreRegradeReasonCode;
import com.verifiedai.exam.domain.ExamSessionItem.SessionItemOptionSnapshot;
import com.verifiedai.exam.events.ExamScoreComputedEvent;
import com.verifiedai.exam.infrastructure.persistence.ExamScoringRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

class ExamScoringEngineServiceTest {

    private static final UUID USER_ID = UUID.fromString("95000000-0000-4000-8000-000000000101");
    private static final UUID OTHER_USER_ID = UUID.fromString("95000000-0000-4000-8000-000000000202");
    private static final UUID DEF_ID = UUID.fromString("91000000-0000-4000-8000-000000000101");
    private static final UUID BP_ID = UUID.fromString("94000000-0000-4000-8000-000000000101");
    private static final UUID SESSION_ID = UUID.fromString("95000000-0000-4000-8000-000000000303");
    private static final UUID Q1_ID = UUID.fromString("93000000-0000-4000-8000-000000000101");
    private static final Instant T0 = Instant.parse("2026-11-16T01:00:00Z");

    private ExamScoringRepository scoringRepository;
    private ExamSessionRepository sessionRepository;
    private ExamRepository examRepository;
    private QuestionBankRepository questionBankRepository;
    private ApplicationEventPublisher eventPublisher;
    private ExamScoringEngineService service;

    @BeforeEach
    void setUp() {
        scoringRepository = mock(ExamScoringRepository.class);
        sessionRepository = mock(ExamSessionRepository.class);
        examRepository = mock(ExamRepository.class);
        questionBankRepository = mock(QuestionBankRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        @SuppressWarnings("unchecked")
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterProvider = mock(ObjectProvider.class);
        when(meterProvider.getIfAvailable(any())).thenReturn(meterRegistry);

        @SuppressWarnings("unchecked")
        ObjectProvider<Clock> clockProvider = mock(ObjectProvider.class);
        when(clockProvider.getIfAvailable(any())).thenReturn(Clock.fixed(T0, ZoneOffset.UTC));

        service = new ExamScoringEngineService(
                scoringRepository,
                sessionRepository,
                examRepository,
                questionBankRepository,
                new DeterministicExamScoringPolicy(),
                eventPublisher,
                meterProvider,
                clockProvider,
                true
        );
    }

    @Test
    void rejectsWhenFeatureFlagDisabledBolaCrossTenantOrNonScorableStatus() {
        service.setScoringEngineEnabled(false);
        ApiProblemException disabled = assertThrows(ApiProblemException.class,
                () -> service.computeOrGetScoreSummary(USER_ID, SESSION_ID));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, disabled.status());
        assertEquals(ApiErrorCode.EXAM_SCORING_FEATURE_DISABLED, disabled.code());

        service.setScoringEngineEnabled(true);
        ExamSession ownedSubmitted = sampleSession(ExamSessionStatus.SUBMITTED, "B");
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(ownedSubmitted));

        ApiProblemException bola = assertThrows(ApiProblemException.class,
                () -> service.computeOrGetScoreSummary(OTHER_USER_ID, SESSION_ID));
        assertEquals(HttpStatus.FORBIDDEN, bola.status());
        assertEquals(ApiErrorCode.EXAM_SESSION_FORBIDDEN, bola.code());

        for (ExamSessionStatus blockedStatus : List.of(
                ExamSessionStatus.IN_PROGRESS,
                ExamSessionStatus.OFFLINE_GRACE_BUFFER,
                ExamSessionStatus.ABANDONED
        )) {
            when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(sampleSession(blockedStatus, "B")));
            ApiProblemException nonScorable = assertThrows(ApiProblemException.class,
                    () -> service.computeOrGetScoreSummary(USER_ID, SESSION_ID));
            assertEquals(HttpStatus.CONFLICT, nonScorable.status());
            assertEquals(ApiErrorCode.EXAM_SESSION_NOT_SCORABLE, nonScorable.code());
        }
    }

    @Test
    void computesIdempotentlyAndRegradesWithVersionBumpAndAuditRecord() {
        ExamSession submitted = sampleSession(ExamSessionStatus.SUBMITTED, "A");
        QuestionBankItem wrongThenRightKey = questionItem(Q1_ID, "B");

        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(submitted));
        when(examRepository.findById(BP_ID)).thenReturn(Optional.empty());
        when(examRepository.findLatestByCode("TR_YKS_TYT_MATH", true)).thenReturn(Optional.of(tytDefinition()));
        when(questionBankRepository.findById(Q1_ID)).thenReturn(Optional.of(wrongThenRightKey));
        when(scoringRepository.findScoreSnapshotBySessionId(SESSION_ID)).thenReturn(Optional.empty());
        when(scoringRepository.saveInitialScoreSnapshot(any())).thenAnswer(inv -> inv.getArgument(0));

        // 404 before initial score exists on GET
        ApiProblemException missingScore = assertThrows(ApiProblemException.class,
                () -> service.getScoreSummary(USER_ID, SESSION_ID));
        assertEquals(HttpStatus.NOT_FOUND, missingScore.status());
        assertEquals(ApiErrorCode.EXAM_SCORE_NOT_FOUND, missingScore.code());

        // First compute -> version 1, -0.25 net (learner selected A, key was B), event published
        ScoreSummaryResult first = service.computeOrGetScoreSummary(USER_ID, SESSION_ID);
        assertTrue(first.newlyComputed());
        assertEquals(1, first.snapshot().scoreVersion());
        assertEquals(new BigDecimal("-0.25"), first.snapshot().netScore());
        assertEquals(0, first.snapshot().llmTokensUsed());
        assertFalse(first.snapshot().trainingEligible());
        verify(eventPublisher, times(1)).publishEvent(any(ExamScoreComputedEvent.class));

        // Idempotent replay -> returns existing snapshot without recomputing or saving again
        when(scoringRepository.findScoreSnapshotBySessionId(SESSION_ID)).thenReturn(Optional.of(first.snapshot()));
        ScoreSummaryResult replay = service.computeOrGetScoreSummary(USER_ID, SESSION_ID);
        assertFalse(replay.newlyComputed());
        assertEquals(first.snapshot().scoreId(), replay.snapshot().scoreId());
        verify(scoringRepository, times(1)).saveInitialScoreSnapshot(any());
        verify(eventPublisher, times(1)).publishEvent(any(ExamScoreComputedEvent.class));

        // Invalid regrade request -> 400 EXAM_REGRADE_INVALID_REQUEST
        ApiProblemException invalidRegrade = assertThrows(ApiProblemException.class,
                () -> service.regradeSession(USER_ID, SESSION_ID, ScoreRegradeReasonCode.ANSWER_KEY_CORRECTION, "   ", "sme"));
        assertEquals(HttpStatus.BAD_REQUEST, invalidRegrade.status());
        assertEquals(ApiErrorCode.EXAM_REGRADE_INVALID_REQUEST, invalidRegrade.code());

        // Correct answer key updated to A, trigger regrade -> version 2, +1.00 net, +1.25 delta
        when(questionBankRepository.findById(Q1_ID)).thenReturn(Optional.of(questionItem(Q1_ID, "A")));
        when(scoringRepository.updateRegradedScoreSnapshot(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        RegradeResult regradeResult = service.regradeSession(
                USER_ID,
                SESSION_ID,
                ScoreRegradeReasonCode.ANSWER_KEY_CORRECTION,
                "Option A confirmed by SME panel",
                "sme-committee"
        );
        assertEquals(2, regradeResult.snapshot().scoreVersion());
        assertEquals(new BigDecimal("1.00"), regradeResult.snapshot().netScore());
        assertEquals(1, regradeResult.snapshot().correctCount());
        assertEquals(0, regradeResult.snapshot().wrongCount());
        assertNotNull(regradeResult.snapshot().lastRegradedAt());
        ExamScoreRegradeAudit audit = regradeResult.audit();
        assertEquals(1, audit.previousScoreVersion());
        assertEquals(2, audit.newScoreVersion());
        assertEquals(new BigDecimal("-0.25"), audit.previousNetScore());
        assertEquals(new BigDecimal("1.00"), audit.newNetScore());
        assertFalse(audit.trainingEligible());
        verify(scoringRepository, never()).deleteByUserId(any());
        verify(eventPublisher, times(2)).publishEvent(any(ExamScoreComputedEvent.class));
    }

    private static ExamSession sampleSession(ExamSessionStatus status, String selectedKey) {
        ExamSessionItem item = new ExamSessionItem(
                UUID.randomUUID(), SESSION_ID, 1, Q1_ID, "TYT-Q1", "MAIN", 1,
                "MATH.ARITHMETIC.FRACTIONS", "SINGLE_CHOICE_MCQ", QuestionDifficultyBand.EASY,
                "Stem", List.of(
                        new SessionItemOptionSnapshot("A", 1, "Opt A", null, false),
                        new SessionItemOptionSnapshot("B", 2, "Opt B", null, false),
                        new SessionItemOptionSnapshot("C", 3, "Opt C", null, false),
                        new SessionItemOptionSnapshot("D", 4, "Opt D", null, false)
                ),
                null, BigDecimal.ONE, new BigDecimal("0.2500"),
                ExamItemResponseState.ANSWERED, selectedKey, null, false, List.of(),
                35, 1, 1L, T0, true, false, T0, T0, List.of()
        );
        return new ExamSession(
                SESSION_ID, USER_ID, UUID.randomUUID(), BP_ID, "TR_YKS_TYT_MATH", 1,
                status, "FREE_NAVIGATION", true,
                new AppliedAccommodationSnapshot(new BigDecimal("1.00"), false, false, false),
                900, new BigDecimal("1.00"), 900, 120, T0, T0.plusSeconds(900),
                T0.plusSeconds(1020), T0, null,
                status == ExamSessionStatus.SUBMITTED ? T0.plusSeconds(200) : null,
                status == ExamSessionStatus.SUBMITTED ? "USER_INITIATED" : null,
                status == ExamSessionStatus.SUBMITTED ? "sub-1" : null,
                1, 1, 35, 1, 0, 0L, null, false, T0, T0, List.of(item)
        );
    }

    private static ExamDefinition tytDefinition() {
        ExamDefinition draft = new ExamDefinition(
                DEF_ID, "TR_YKS_TYT_MATH", "2026.1", Jurisdiction.TR, ExamFamily.YKS,
                "YKS TYT Math", "YKS TYT Mat", ExamStatus.ACTIVE, 40, 65,
                ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG, new BigDecimal("0.2500"),
                new BigDecimal("-10.00"), new BigDecimal("40.00"), NavigationPolicy.FREE_JUMP,
                "0".repeat(64), T0, null, null,
                List.of(new ExamSectionSpec("MAIN", "Main", "Ana", 40, 65, 1)),
                new ScoringParameters(null, null, 1, null, null, List.of()),
                List.of(new ExamTopicWeight(UUID.randomUUID(), DEF_ID, "MAIN",
                        "MATH.ARITHMETIC.FRACTIONS", "MATH.ARITHMETIC",
                        new BigDecimal("40.0000"), 1, 40, new CognitiveLevelMix(0.3, 0.5, 0.2), 1)),
                false, T0, T0
        );
        return new ExamDefinition(
                draft.id(), draft.examCode(), draft.examVersion(), draft.jurisdiction(), draft.examFamily(),
                draft.titleEn(), draft.titleTr(), draft.status(), draft.totalQuestions(), draft.durationMinutes(),
                draft.scoringFormulaType(), draft.wrongPenaltyFactor(), draft.minScore(), draft.maxScore(),
                draft.navigationPolicy(), ExamBlueprintHashCalculator.computeHash(draft),
                draft.effectiveFrom(), draft.deprecatedAt(), draft.supersededByVersion(),
                draft.sections(), draft.scoringParameters(), draft.topicWeights(), false, T0, T0
        );
    }

    private static QuestionBankItem questionItem(UUID id, String correctKey) {
        return new QuestionBankItem(
                id, DEF_ID, 1, null, "TYT-Q1", "MATH.ARITHMETIC.FRACTIONS", null,
                QuestionItemFormat.SINGLE_CHOICE_MCQ, QuestionDifficultyBand.EASY,
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, CognitiveTaxonomyLevel.RECALL,
                "Evaluate the deterministic fraction arithmetic problem stem.", null, null, false, null, null, Map.of(),
                "Deterministic arithmetic explanation.", 45, BigDecimal.ONE, new BigDecimal("0.2500"),
                QuestionItemStatus.PUBLISHED, null, 1L, false,
                List.of(
                        new QuestionOption(UUID.randomUUID(), id, "A", 1, "Opt A", null, "A".equals(correctKey), "A distraction", false),
                        new QuestionOption(UUID.randomUUID(), id, "B", 2, "Opt B", null, "B".equals(correctKey), "B distraction", false),
                        new QuestionOption(UUID.randomUUID(), id, "C", 3, "Opt C", null, "C".equals(correctKey), "C distraction", false),
                        new QuestionOption(UUID.randomUUID(), id, "D", 4, "Opt D", null, "D".equals(correctKey), "D distraction", false)
                ),
                new QuestionProvenanceRecord(UUID.randomUUID(), id, QuestionProvenanceSourceType.ORIGINAL_AUTHORED,
                        QuestionLicenseClassification.PROPRIETARY_INTERNAL, "VerifiedAI", "REF",
                        "author", "reviewer", ProvenanceVerificationStatus.VERIFIED, null, T0, false, T0),
                T0, T0
        );
    }
}
