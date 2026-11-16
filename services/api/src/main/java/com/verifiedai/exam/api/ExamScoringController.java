package com.verifiedai.exam.api;

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
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping({"/v1/exams/sessions", "/api/v1/exams/sessions"})
public class ExamScoringController {

    public static final String SCHEMA_VERSION = "2026-04-01";
    private static final CacheControl NO_STORE = CacheControl.noStore().mustRevalidate();

    private final ExamScoringEngineService scoringEngineService;

    public ExamScoringController(ExamScoringEngineService scoringEngineService) {
        this.scoringEngineService = Objects.requireNonNull(
                scoringEngineService,
                "scoringEngineService cannot be null"
        );
    }

    public record ExamTopicScoreBreakdownDto(
            String topicCode,
            String topicTitle,
            int totalQuestions,
            int correctCount,
            int wrongCount,
            int blankCount,
            BigDecimal rawCorrectScore,
            BigDecimal wrongPenaltyDeduction,
            BigDecimal netScore,
            BigDecimal accuracyPercentage,
            int totalTimeSpentSeconds,
            BigDecimal averageSecondsPerItem
    ) {
    }

    public record ExamDifficultyScoreBreakdownDto(
            DifficultyBand difficultyBand,
            int totalQuestions,
            int correctCount,
            int wrongCount,
            int blankCount,
            BigDecimal netScore,
            BigDecimal accuracyPercentage,
            int totalTimeSpentSeconds,
            BigDecimal averageSecondsPerItem
    ) {
    }

    public record ExamSessionItemEvaluationDto(
            UUID evaluationId,
            UUID sessionItemId,
            UUID questionItemId,
            int positionIndex,
            String questionCode,
            String topicCode,
            String topicTitle,
            DifficultyBand difficultyBand,
            CognitiveLevel cognitiveLevel,
            QuestionFormatType formatType,
            ItemEvaluationOutcome evaluationOutcome,
            String selectedOptionKey,
            BigDecimal numericResponseValue,
            String correctOptionKey,
            BigDecimal expectedNumericAnswer,
            BigDecimal numericTolerance,
            BigDecimal rawPointsAwarded,
            BigDecimal penaltyPointsDeducted,
            BigDecimal netPointsContribution,
            boolean flaggedForReview,
            int timeSpentSeconds,
            String selectedOptionDistractorRationale,
            String solutionExplanationMarkdown,
            String evidenceSourceRef,
            F28ScratchpadTraceStatus f28ScratchpadStatus,
            int scratchpadAssetCount,
            List<UUID> scratchpadAssetIds,
            int questionVersion,
            int llmTokensUsed,
            boolean trainingEligible,
            Instant evaluatedAt
    ) {
    }

    public record ExamScoreSummaryResponseDto(
            String schemaVersion,
            UUID scoreId,
            UUID sessionId,
            UUID userId,
            UUID examDefinitionId,
            UUID assembledAssessmentId,
            String examCode,
            ScoringFormulaType scoringFormulaType,
            BigDecimal wrongPenaltyRatio,
            int totalQuestions,
            int correctCount,
            int wrongCount,
            int blankCount,
            BigDecimal rawCorrectScore,
            BigDecimal wrongPenaltyDeduction,
            BigDecimal netScore,
            BigDecimal minPossibleNetScore,
            BigDecimal maxPossibleNetScore,
            BigDecimal normalizedPercentageScore,
            BigDecimal accuracyPercentage,
            int totalTimeSpentSeconds,
            BigDecimal averageSecondsPerItem,
            List<ExamTopicScoreBreakdownDto> topicBreakdowns,
            List<ExamDifficultyScoreBreakdownDto> difficultyBreakdowns,
            List<ExamSessionItemEvaluationDto> itemEvaluations,
            int scoreVersion,
            Instant sessionSubmittedAt,
            Instant scoredAt,
            Instant lastRegradedAt,
            int llmTokensUsed,
            BigDecimal inferenceCostUsd,
            boolean trainingEligible
    ) {
    }

    public record ExamScoreRegradeRequestDto(
            ScoreRegradeReasonCode reasonCode,
            String reasonDetail,
            String triggeredBy,
            Boolean trainingEligible
    ) {
    }

    public record ExamScoreRegradeAuditDto(
            UUID regradeAuditId,
            UUID scoreId,
            UUID sessionId,
            UUID userId,
            int previousScoreVersion,
            int newScoreVersion,
            BigDecimal previousNetScore,
            BigDecimal newNetScore,
            int previousCorrectCount,
            int newCorrectCount,
            int previousWrongCount,
            int newWrongCount,
            int previousBlankCount,
            int newBlankCount,
            ScoreRegradeReasonCode reasonCode,
            String reasonDetail,
            String triggeredBy,
            Map<String, Object> deltaSummary,
            boolean trainingEligible,
            Instant regradedAt
    ) {
    }

    public record ExamScoreRegradeResponseDto(
            String schemaVersion,
            ExamScoreSummaryResponseDto scoreSummary,
            ExamScoreRegradeAuditDto regradeAudit,
            int llmTokensUsed,
            BigDecimal inferenceCostUsd,
            boolean trainingEligible
    ) {
    }

    public record ExamScoreRegradeAuditListResponseDto(
            String schemaVersion,
            UUID sessionId,
            int totalCount,
            List<ExamScoreRegradeAuditDto> audits,
            boolean trainingEligible
    ) {
    }

    @PostMapping("/{sessionId}/score-summary")
    public ResponseEntity<ExamScoreSummaryResponseDto> computeOrGetScoreSummary(
            @PathVariable("sessionId") UUID sessionId,
            Authentication authentication
    ) {
        UUID userId = resolveLearnerId(authentication);
        ScoreSummaryResult result = scoringEngineService.computeOrGetScoreSummary(userId, sessionId);
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(toScoreSummaryResponse(result.snapshot()));
    }

    @GetMapping("/{sessionId}/score-summary")
    public ResponseEntity<ExamScoreSummaryResponseDto> getScoreSummary(
            @PathVariable("sessionId") UUID sessionId,
            Authentication authentication
    ) {
        UUID userId = resolveLearnerId(authentication);
        ExamSessionScoreSnapshot snapshot = scoringEngineService.getScoreSummary(userId, sessionId);
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(toScoreSummaryResponse(snapshot));
    }

    @PostMapping("/{sessionId}/regrade")
    public ResponseEntity<ExamScoreRegradeResponseDto> regradeSession(
            @PathVariable("sessionId") UUID sessionId,
            @RequestBody(required = false) ExamScoreRegradeRequestDto request,
            Authentication authentication
    ) {
        UUID userId = resolveLearnerId(authentication);
        if (request == null) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.EXAM_REGRADE_INVALID_REQUEST,
                    "Regrade request payload is required",
                    true,
                    "PROVIDE_REGRADE_PAYLOAD"
            );
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "trainingEligible must be false per ADR-006",
                    false,
                    "SET_TRAINING_ELIGIBLE_FALSE"
            );
        }

        RegradeResult result = scoringEngineService.regradeSession(
                userId,
                sessionId,
                request.reasonCode(),
                request.reasonDetail(),
                request.triggeredBy()
        );
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(new ExamScoreRegradeResponseDto(
                        SCHEMA_VERSION,
                        toScoreSummaryResponse(result.snapshot()),
                        toRegradeAuditDto(result.audit()),
                        0,
                        BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                        false
                ));
    }

    @GetMapping("/{sessionId}/regrade-audits")
    public ResponseEntity<ExamScoreRegradeAuditListResponseDto> listRegradeAudits(
            @PathVariable("sessionId") UUID sessionId,
            Authentication authentication
    ) {
        UUID userId = resolveLearnerId(authentication);
        List<ExamScoreRegradeAuditDto> audits = scoringEngineService.listRegradeAudits(userId, sessionId).stream()
                .map(ExamScoringController::toRegradeAuditDto)
                .toList();
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(new ExamScoreRegradeAuditListResponseDto(
                        SCHEMA_VERSION,
                        sessionId,
                        audits.size(),
                        audits,
                        false
                ));
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleApiProblem(ApiProblemException ex) {
        Map<String, Object> exDetails = ex.details();
        boolean recoverable = Boolean.TRUE.equals(exDetails.get("recoverable"));
        String userAction = exDetails.getOrDefault("userAction", "REVIEW_REQUEST").toString();
        return ResponseEntity.status(ex.status()).body(buildProblemDetails(
                "https://verifiedai.com/errors/" + ex.code().name().toLowerCase(),
                ex.getMessage(),
                ex.status(),
                ex.code(),
                recoverable,
                userAction
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetailsResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildProblemDetails(
                "https://verifiedai.com/errors/" + ApiErrorCode.EXAM_SESSION_FORBIDDEN.name().toLowerCase(),
                ex.getMessage(),
                HttpStatus.FORBIDDEN,
                ApiErrorCode.EXAM_SESSION_FORBIDDEN,
                false,
                "USE_OWN_LEARNER_ACCOUNT"
        ));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ProblemDetailsResponse> handleValidation(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildProblemDetails(
                "https://verifiedai.com/errors/" + ApiErrorCode.EXAM_REGRADE_INVALID_REQUEST.name().toLowerCase(),
                "Validation failed for exam scoring request",
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.EXAM_REGRADE_INVALID_REQUEST,
                true,
                "FIX_REQUEST_FIELDS"
        ));
    }

    private static ExamScoreSummaryResponseDto toScoreSummaryResponse(ExamSessionScoreSnapshot snapshot) {
        List<ExamTopicScoreBreakdownDto> topicDtos = snapshot.topicBreakdowns().stream()
                .map(tb -> new ExamTopicScoreBreakdownDto(
                        tb.topicCode(),
                        tb.topicTitle(),
                        tb.totalQuestions(),
                        tb.correctCount(),
                        tb.wrongCount(),
                        tb.blankCount(),
                        tb.rawCorrectScore(),
                        tb.wrongPenaltyDeduction(),
                        tb.netScore(),
                        tb.accuracyPercentage(),
                        tb.totalTimeSpentSeconds(),
                        tb.averageSecondsPerItem()
                ))
                .toList();

        List<ExamDifficultyScoreBreakdownDto> difficultyDtos = snapshot.difficultyBreakdowns().stream()
                .map(db -> new ExamDifficultyScoreBreakdownDto(
                        db.difficultyBand(),
                        db.totalQuestions(),
                        db.correctCount(),
                        db.wrongCount(),
                        db.blankCount(),
                        db.netScore(),
                        db.accuracyPercentage(),
                        db.totalTimeSpentSeconds(),
                        db.averageSecondsPerItem()
                ))
                .toList();

        List<ExamSessionItemEvaluationDto> evalDtos = snapshot.itemEvaluations().stream()
                .map(ExamScoringController::toItemEvaluationDto)
                .toList();

        return new ExamScoreSummaryResponseDto(
                SCHEMA_VERSION,
                snapshot.scoreId(),
                snapshot.sessionId(),
                snapshot.userId(),
                snapshot.examDefinitionId(),
                snapshot.assembledAssessmentId(),
                snapshot.examCode(),
                snapshot.scoringFormulaType(),
                snapshot.wrongPenaltyRatio(),
                snapshot.totalQuestions(),
                snapshot.correctCount(),
                snapshot.wrongCount(),
                snapshot.blankCount(),
                snapshot.rawCorrectScore(),
                snapshot.wrongPenaltyDeduction(),
                snapshot.netScore(),
                snapshot.minPossibleNetScore(),
                snapshot.maxPossibleNetScore(),
                snapshot.normalizedPercentageScore(),
                snapshot.accuracyPercentage(),
                snapshot.totalTimeSpentSeconds(),
                snapshot.averageSecondsPerItem(),
                topicDtos,
                difficultyDtos,
                evalDtos,
                snapshot.scoreVersion(),
                snapshot.sessionSubmittedAt(),
                snapshot.scoredAt(),
                snapshot.lastRegradedAt(),
                0,
                snapshot.inferenceCostUsd(),
                false
        );
    }

    private static ExamSessionItemEvaluationDto toItemEvaluationDto(ExamSessionItemEvaluation eval) {
        return new ExamSessionItemEvaluationDto(
                eval.evaluationId(),
                eval.sessionItemId(),
                eval.questionItemId(),
                eval.positionIndex(),
                eval.questionCode(),
                eval.topicCode(),
                eval.topicTitle(),
                eval.difficultyBand(),
                eval.cognitiveLevel(),
                eval.formatType(),
                eval.evaluationOutcome(),
                eval.selectedOptionKey(),
                eval.numericResponseValue(),
                eval.correctOptionKey(),
                eval.expectedNumericAnswer(),
                eval.numericTolerance(),
                eval.rawPointsAwarded(),
                eval.penaltyPointsDeducted(),
                eval.netPointsContribution(),
                eval.flaggedForReview(),
                eval.timeSpentSeconds(),
                eval.selectedOptionDistractorRationale(),
                eval.solutionExplanationMarkdown(),
                eval.evidenceSourceRef(),
                eval.f28ScratchpadStatus(),
                eval.scratchpadAssetCount(),
                eval.scratchpadAssetIds(),
                eval.questionVersion(),
                0,
                false,
                eval.evaluatedAt()
        );
    }

    private static ExamScoreRegradeAuditDto toRegradeAuditDto(ExamScoreRegradeAudit audit) {
        return new ExamScoreRegradeAuditDto(
                audit.regradeAuditId(),
                audit.scoreId(),
                audit.sessionId(),
                audit.userId(),
                audit.previousScoreVersion(),
                audit.newScoreVersion(),
                audit.previousNetScore(),
                audit.newNetScore(),
                audit.previousCorrectCount(),
                audit.newCorrectCount(),
                audit.previousWrongCount(),
                audit.newWrongCount(),
                audit.previousBlankCount(),
                audit.newBlankCount(),
                audit.reasonCode(),
                audit.reasonDetail(),
                audit.triggeredBy(),
                audit.deltaSummary(),
                false,
                audit.regradedAt()
        );
    }

    private static ProblemDetailsResponse buildProblemDetails(
            String type,
            String title,
            HttpStatus status,
            ApiErrorCode code,
            boolean recoverable,
            String userAction
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errorCode", code.name());
        details.put("retryable", recoverable);
        details.put("recoverable", recoverable);
        details.put("userAction", userAction);
        return new ProblemDetailsResponse(
                type,
                title,
                status.value(),
                code,
                CorrelationIds.current(),
                details
        );
    }

    private static UUID resolveLearnerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiProblemException(
                    HttpStatus.UNAUTHORIZED,
                    ApiErrorCode.AUTH_TOKEN_EXPIRED,
                    "Authentication token is missing or invalid",
                    false,
                    "SIGN_IN"
            );
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedLearnerPrincipal learnerPrincipal) {
            return learnerPrincipal.learnerId();
        }
        if (principal instanceof Jwt jwt) {
            return VerifiedJwtPrincipal.from(jwt).learnerId();
        }
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        String name = authentication.getName();
        if (name != null && !name.isBlank()) {
            try {
                return UUID.fromString(name.trim());
            } catch (IllegalArgumentException ignored) {
                throw new AccessDeniedException("Authenticated principal does not carry a valid learner identifier");
            }
        }
        throw new AccessDeniedException("Authenticated principal is not permitted to access exam scoring endpoints");
    }
}
