package com.verifiedai.exam.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.verifiedai.exam.application.ExamScoringEngineService;
import com.verifiedai.exam.application.ExamScoringEngineService.RegradeResult;
import com.verifiedai.exam.application.ExamScoringEngineService.ScoreSummaryResult;
import com.verifiedai.exam.domain.CognitiveLevel;
import com.verifiedai.exam.domain.DifficultyBand;
import com.verifiedai.exam.domain.DifficultyScoreBreakdown;
import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import com.verifiedai.exam.domain.ExamScoreRegradeAudit;
import com.verifiedai.exam.domain.ExamSessionItemEvaluation;
import com.verifiedai.exam.domain.ExamSessionScoreSnapshot;
import com.verifiedai.exam.domain.F28ScratchpadTraceStatus;
import com.verifiedai.exam.domain.ItemEvaluationOutcome;
import com.verifiedai.exam.domain.QuestionFormatType;
import com.verifiedai.exam.domain.ScoreRegradeReasonCode;
import com.verifiedai.exam.domain.TopicScoreBreakdown;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ExamScoringControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("95000000-0000-4000-8000-000000000901");
    private static final UUID SESSION_ID = UUID.fromString("95000000-0000-4000-8000-000000000902");
    private static final UUID SCORE_ID = UUID.fromString("95000000-0000-4000-8000-000000000903");
    private static final Instant T0 = Instant.parse("2026-11-16T02:00:00Z");

    private ExamScoringEngineService scoringService;
    private MockMvc mockMvc;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        scoringService = mock(ExamScoringEngineService.class);
        ExamScoringController controller = new ExamScoringController(scoringService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        auth = new UsernamePasswordAuthenticationToken(
                (AuthenticatedLearnerPrincipal) () -> USER_ID,
                null,
                List.of()
        );
    }

    @Test
    void computesGetsRegradesAndListsAuditsAcrossBothRoutePrefixes() throws Exception {
        ExamSessionScoreSnapshot v1 = sampleSnapshot(1, new BigDecimal("0.75"), 1, 1, 0, null);
        when(scoringService.computeOrGetScoreSummary(USER_ID, SESSION_ID))
                .thenReturn(new ScoreSummaryResult(true, v1));
        when(scoringService.getScoreSummary(USER_ID, SESSION_ID)).thenReturn(v1);

        mockMvc.perform(post("/v1/exams/sessions/" + SESSION_ID + "/score-summary").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("2026-04-01"))
                .andExpect(jsonPath("$.netScore").value(0.75))
                .andExpect(jsonPath("$.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.inferenceCostUsd").value(0.0))
                .andExpect(jsonPath("$.trainingEligible").value(false));

        mockMvc.perform(get("/api/v1/exams/sessions/" + SESSION_ID + "/score-summary").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreId").value(SCORE_ID.toString()))
                .andExpect(jsonPath("$.scoreVersion").value(1));

        ExamSessionScoreSnapshot v2 = sampleSnapshot(2, new BigDecimal("2.00"), 2, 0, 0, T0.plusSeconds(30));
        ExamScoreRegradeAudit audit = new ExamScoreRegradeAudit(
                UUID.randomUUID(), SCORE_ID, SESSION_ID, USER_ID, 1, 2,
                new BigDecimal("0.75"), new BigDecimal("2.00"), 1, 2, 1, 0, 0, 0,
                ScoreRegradeReasonCode.ANSWER_KEY_CORRECTION,
                "SME panel corrected item 2",
                "admin-qa",
                Map.of(
                        "netScoreDelta", new BigDecimal("1.25"),
                        "correctCountDelta", 1,
                        "wrongCountDelta", -1,
                        "blankCountDelta", 0,
                        "changedPositionIndices", List.of(2)
                ),
                false,
                T0.plusSeconds(30)
        );
        when(scoringService.regradeSession(
                eq(USER_ID), eq(SESSION_ID), eq(ScoreRegradeReasonCode.ANSWER_KEY_CORRECTION),
                eq("SME panel corrected item 2"), eq("admin-qa")))
                .thenReturn(new RegradeResult(v2, audit));
        when(scoringService.listRegradeAudits(USER_ID, SESSION_ID)).thenReturn(List.of(audit));

        mockMvc.perform(post("/v1/exams/sessions/" + SESSION_ID + "/regrade")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reasonCode": "ANSWER_KEY_CORRECTION",
                                  "reasonDetail": "SME panel corrected item 2",
                                  "triggeredBy": "admin-qa",
                                  "trainingEligible": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("2026-04-01"))
                .andExpect(jsonPath("$.scoreSummary.scoreVersion").value(2))
                .andExpect(jsonPath("$.regradeAudit.newScoreVersion").value(2))
                .andExpect(jsonPath("$.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.inferenceCostUsd").value(0.0))
                .andExpect(jsonPath("$.trainingEligible").value(false));

        mockMvc.perform(get("/api/v1/exams/sessions/" + SESSION_ID + "/regrade-audits").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("2026-04-01"))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.trainingEligible").value(false));
    }

    @Test
    void enforcesAuthAndMapsDomainErrors() throws Exception {
        // Missing authentication -> 401 AUTH_TOKEN_EXPIRED
        mockMvc.perform(get("/v1/exams/sessions/" + SESSION_ID + "/score-summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_EXPIRED"));

        // 403 BOLA forbidden
        doThrow(new ApiProblemException(
                HttpStatus.FORBIDDEN, ApiErrorCode.EXAM_SESSION_FORBIDDEN, "Forbidden", false, "USE_OWN_ACCOUNT"))
                .when(scoringService).getScoreSummary(USER_ID, SESSION_ID);
        mockMvc.perform(get("/v1/exams/sessions/" + SESSION_ID + "/score-summary").principal(auth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EXAM_SESSION_FORBIDDEN"));

        // 404 Score not found
        doThrow(new ApiProblemException(
                HttpStatus.NOT_FOUND, ApiErrorCode.EXAM_SCORE_NOT_FOUND, "Missing", true, "COMPUTE_FIRST"))
                .when(scoringService).getScoreSummary(USER_ID, SESSION_ID);
        mockMvc.perform(get("/v1/exams/sessions/" + SESSION_ID + "/score-summary").principal(auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXAM_SCORE_NOT_FOUND"));

        // 409 Active session not scorable
        doThrow(new ApiProblemException(
                HttpStatus.CONFLICT, ApiErrorCode.EXAM_SESSION_NOT_SCORABLE, "Active session", false, "SUBMIT_FIRST"))
                .when(scoringService).computeOrGetScoreSummary(USER_ID, SESSION_ID);
        mockMvc.perform(post("/v1/exams/sessions/" + SESSION_ID + "/score-summary").principal(auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_SESSION_NOT_SCORABLE"));

        // 400 Invalid regrade payload (null body)
        mockMvc.perform(post("/v1/exams/sessions/" + SESSION_ID + "/regrade").principal(auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXAM_REGRADE_INVALID_REQUEST"));

        // 503 Feature disabled
        doThrow(new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.EXAM_SCORING_FEATURE_DISABLED, "Disabled", true, "RETRY_LATER"))
                .when(scoringService).computeOrGetScoreSummary(USER_ID, SESSION_ID);
        mockMvc.perform(post("/v1/exams/sessions/" + SESSION_ID + "/score-summary").principal(auth))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EXAM_SCORING_FEATURE_DISABLED"));
    }

    private static ExamSessionScoreSnapshot sampleSnapshot(
            int version,
            BigDecimal netScore,
            int correct,
            int wrong,
            int blank,
            Instant regradedAt
    ) {
        UUID itemId1 = UUID.randomUUID();
        UUID q1 = UUID.randomUUID();
        ExamSessionItemEvaluation eval = new ExamSessionItemEvaluation(
                UUID.randomUUID(), SCORE_ID, SESSION_ID, USER_ID, itemId1, q1, 1,
                "TYT-Q1", "MATH.ARITHMETIC.FRACTIONS", "Fractions",
                DifficultyBand.EASY, CognitiveLevel.RECALL, QuestionFormatType.SINGLE_CHOICE_MCQ,
                ItemEvaluationOutcome.CORRECT, "A", null, "A", null, null,
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, false, 45, null,
                "Exact arithmetic simplification.", "question_bank:TYT-Q1:v1",
                F28ScratchpadTraceStatus.NONE, 0, List.of(), 1, 0, false, T0
        );
        return new ExamSessionScoreSnapshot(
                SCORE_ID, SESSION_ID, USER_ID, UUID.randomUUID(), UUID.randomUUID(),
                "TR_YKS_TYT_MATH", ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG,
                new BigDecimal("0.2500"), correct + wrong + blank, correct, wrong, blank,
                BigDecimal.valueOf(correct), new BigDecimal("0.25").multiply(BigDecimal.valueOf(wrong)),
                netScore, new BigDecimal("-0.50"), new BigDecimal("2.00"),
                new BigDecimal("37.50"), new BigDecimal("50.00"), 90, new BigDecimal("45.00"),
                List.of(new TopicScoreBreakdown(
                        "MATH.ARITHMETIC.FRACTIONS", "Fractions", correct + wrong + blank, correct, wrong, blank,
                        BigDecimal.valueOf(correct), new BigDecimal("0.25").multiply(BigDecimal.valueOf(wrong)),
                        netScore, new BigDecimal("50.00"), 90, new BigDecimal("45.00")
                )),
                List.of(new DifficultyScoreBreakdown(
                        DifficultyBand.EASY, correct + wrong + blank, correct, wrong, blank,
                        netScore, new BigDecimal("50.00"), 90, new BigDecimal("45.00")
                )),
                List.of(eval), version, T0, T0, regradedAt, 0, BigDecimal.ZERO, false
        );
    }
}
