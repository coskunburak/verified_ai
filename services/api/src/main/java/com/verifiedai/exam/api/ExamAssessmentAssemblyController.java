package com.verifiedai.exam.api;

import com.verifiedai.exam.application.ExamAssessmentAssemblyService;
import com.verifiedai.exam.application.ExamAssessmentAssemblyService.AssembleCommand;
import com.verifiedai.exam.application.ExamAssessmentAssemblyService.AssembleResult;
import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentItem;
import com.verifiedai.exam.domain.AssembledAssessmentStatus;
import com.verifiedai.exam.domain.AssemblyDistributionSummary;
import com.verifiedai.exam.domain.AssessmentBlueprint;
import com.verifiedai.exam.domain.AssessmentBlueprintMode;
import com.verifiedai.exam.domain.AssessmentBlueprintStatus;
import com.verifiedai.exam.domain.BlueprintTopicRule;
import com.verifiedai.exam.domain.CognitiveTaxonomyLevel;
import com.verifiedai.exam.domain.ProvenanceVerificationStatus;
import com.verifiedai.exam.domain.QuestionBankItem;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.domain.QuestionItemStatus;
import com.verifiedai.exam.domain.QuestionLicenseClassification;
import com.verifiedai.exam.domain.QuestionOption;
import com.verifiedai.exam.domain.QuestionProvenanceRecord;
import com.verifiedai.exam.domain.QuestionProvenanceSourceType;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/v1/exams", "/api/v1/exams"})
public class ExamAssessmentAssemblyController {

    private final ExamAssessmentAssemblyService assemblyService;

    public ExamAssessmentAssemblyController(ExamAssessmentAssemblyService assemblyService) {
        this.assemblyService = Objects.requireNonNull(assemblyService, "assemblyService cannot be null");
    }

    // --- DTO Contracts ---

    public record QuestionOptionRequestDto(
            String optionKey,
            int displayOrder,
            String contentMarkdown,
            String contentLatex,
            boolean isCorrect,
            String distractorRationale,
            Boolean trainingEligible
    ) {
    }

    public record QuestionProvenanceRequestDto(
            QuestionProvenanceSourceType sourceType,
            QuestionLicenseClassification licenseClassification,
            String copyrightHolder,
            String sourceReference,
            String authorId,
            String reviewerId,
            ProvenanceVerificationStatus verificationStatus,
            String provenanceHash,
            Instant verifiedAt,
            Boolean trainingEligible
    ) {
    }

    public record QuestionBankItemCreateRequestDto(
            int examVersionNumber,
            UUID examSectionId,
            String questionCode,
            String topicCode,
            String subtopicCode,
            QuestionItemFormat itemFormat,
            QuestionDifficultyBand difficultyBand,
            BigDecimal difficultyBValue,
            BigDecimal discriminationAValue,
            BigDecimal guessingCValue,
            CognitiveTaxonomyLevel cognitiveLevel,
            String stemMarkdown,
            String stemLatex,
            String altTextDescription,
            boolean hasVisualDiagram,
            BigDecimal numericCorrectAnswer,
            BigDecimal numericTolerance,
            Map<String, Object> structuredRubric,
            String solutionExplanationMarkdown,
            int estimatedTimeSeconds,
            BigDecimal maxPoints,
            BigDecimal negativeMarkingWeight,
            List<QuestionOptionRequestDto> options,
            QuestionProvenanceRequestDto provenance,
            Boolean trainingEligible
    ) {
    }

    public record QuestionOptionResponseDto(
            UUID id,
            String optionKey,
            int displayOrder,
            String contentMarkdown,
            String contentLatex,
            boolean isCorrect,
            String distractorRationale,
            boolean trainingEligible
    ) {
    }

    public record QuestionProvenanceResponseDto(
            UUID id,
            QuestionProvenanceSourceType sourceType,
            QuestionLicenseClassification licenseClassification,
            String copyrightHolder,
            String sourceReference,
            String authorId,
            String reviewerId,
            ProvenanceVerificationStatus verificationStatus,
            String provenanceHash,
            Instant verifiedAt,
            boolean trainingEligible,
            Instant createdAt
    ) {
    }

    public record QuestionBankItemResponseDto(
            UUID id,
            UUID examDefinitionId,
            int examVersionNumber,
            UUID examSectionId,
            String questionCode,
            String topicCode,
            String subtopicCode,
            QuestionItemFormat itemFormat,
            QuestionDifficultyBand difficultyBand,
            BigDecimal difficultyBValue,
            BigDecimal discriminationAValue,
            BigDecimal guessingCValue,
            CognitiveTaxonomyLevel cognitiveLevel,
            String stemMarkdown,
            String stemLatex,
            String altTextDescription,
            boolean hasVisualDiagram,
            BigDecimal numericCorrectAnswer,
            BigDecimal numericTolerance,
            Map<String, Object> structuredRubric,
            String solutionExplanationMarkdown,
            int estimatedTimeSeconds,
            BigDecimal maxPoints,
            BigDecimal negativeMarkingWeight,
            QuestionItemStatus status,
            String contentSha256,
            long exposureCount,
            List<QuestionOptionResponseDto> options,
            QuestionProvenanceResponseDto provenance,
            boolean trainingEligible,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record BlueprintTopicRuleRequestDto(
            String topicCode,
            int targetWeightBps,
            int minQuestionCount,
            int maxQuestionCount,
            List<QuestionItemFormat> requiredItemFormats,
            int ruleOrder,
            Boolean trainingEligible
    ) {
    }

    public record AssessmentBlueprintCreateRequestDto(
            int examVersionNumber,
            UUID examSectionId,
            String blueprintCode,
            String title,
            String description,
            AssessmentBlueprintMode blueprintMode,
            int totalQuestionCount,
            int baseDurationSeconds,
            int easyTargetBps,
            int mediumTargetBps,
            int hardTargetBps,
            int expertTargetBps,
            Integer difficultyToleranceBps,
            int recallTargetBps,
            int comprehensionTargetBps,
            int applicationTargetBps,
            int analysisTargetBps,
            int evaluationTargetBps,
            Integer allowRecentlySeenCooldownDays,
            Boolean shuffleQuestions,
            Boolean shuffleOptions,
            List<BlueprintTopicRuleRequestDto> topicRules,
            Boolean trainingEligible
    ) {
    }

    public record BlueprintTopicRuleResponseDto(
            UUID id,
            String topicCode,
            int targetWeightBps,
            int minQuestionCount,
            int maxQuestionCount,
            List<QuestionItemFormat> requiredItemFormats,
            int ruleOrder,
            boolean trainingEligible
    ) {
    }

    public record AssessmentBlueprintResponseDto(
            UUID id,
            UUID examDefinitionId,
            int examVersionNumber,
            UUID examSectionId,
            String blueprintCode,
            String title,
            String description,
            AssessmentBlueprintMode blueprintMode,
            int totalQuestionCount,
            int baseDurationSeconds,
            int easyTargetBps,
            int mediumTargetBps,
            int hardTargetBps,
            int expertTargetBps,
            int difficultyToleranceBps,
            int recallTargetBps,
            int comprehensionTargetBps,
            int applicationTargetBps,
            int analysisTargetBps,
            int evaluationTargetBps,
            int allowRecentlySeenCooldownDays,
            boolean shuffleQuestions,
            boolean shuffleOptions,
            AssessmentBlueprintStatus status,
            String blueprintHash,
            List<BlueprintTopicRuleResponseDto> topicRules,
            boolean trainingEligible,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AssessmentAssemblyRequestDto(
            UUID userId,
            UUID candidateProfileId,
            UUID blueprintId,
            String assemblySeed,
            BigDecimal overrideTimeMultiplier,
            Boolean highContrastMode,
            Boolean screenReaderOptimized,
            Boolean extendedBreakPrompts,
            Boolean trainingEligible
    ) {
    }

    /**
     * Candidate-safe option representation: strictly omits isCorrect and distractorRationale.
     */
    public record CandidateQuestionOptionResponseDto(
            String optionKey,
            int displayOrder,
            String contentMarkdown,
            String contentLatex,
            boolean trainingEligible
    ) {
    }

    /**
     * Candidate-safe assembled item: strictly omits numericCorrectAnswer and solutionExplanationMarkdown.
     */
    public record AssembledAssessmentItemResponseDto(
            UUID id,
            UUID questionItemId,
            String questionCode,
            int sequenceNumber,
            String topicCode,
            String subtopicCode,
            QuestionItemFormat itemFormat,
            QuestionDifficultyBand difficultyBand,
            CognitiveTaxonomyLevel cognitiveLevel,
            String stemMarkdown,
            String stemLatex,
            String altTextDescription,
            boolean hasVisualDiagram,
            List<String> optionOrder,
            List<CandidateQuestionOptionResponseDto> options,
            BigDecimal allocatedPoints,
            int estimatedTimeSeconds,
            boolean trainingEligible
    ) {
    }

    public record AssembledAssessmentResponseDto(
            UUID id,
            UUID userId,
            UUID candidateProfileId,
            UUID blueprintId,
            String blueprintCode,
            String blueprintTitle,
            AssessmentBlueprintMode blueprintMode,
            UUID examDefinitionId,
            int examVersionNumber,
            String assemblySeed,
            String reproducibilityHash,
            int totalQuestionCount,
            int baseDurationSeconds,
            BigDecimal timeMultiplier,
            int effectiveDurationSeconds,
            AppliedAccommodationSnapshot accommodationsSnapshot,
            AssemblyDistributionSummary distributionSummary,
            AssembledAssessmentStatus status,
            Instant assembledAt,
            Instant expiresAt,
            List<AssembledAssessmentItemResponseDto> items,
            boolean trainingEligible
    ) {
    }

    // --- Endpoints ---

    @PostMapping("/{examDefinitionId}/question-bank/items")
    public ResponseEntity<QuestionBankItemResponseDto> createQuestionBankItem(
            @PathVariable("examDefinitionId") UUID examDefinitionId,
            @Valid @RequestBody QuestionBankItemCreateRequestDto request
    ) {
        UUID itemId = UUID.randomUUID();
        Instant now = Instant.now();

        List<QuestionOption> options = request.options() == null
                ? List.of()
                : request.options().stream()
                        .map(o -> new QuestionOption(
                                UUID.randomUUID(),
                                itemId,
                                o.optionKey(),
                                o.displayOrder(),
                                o.contentMarkdown(),
                                o.contentLatex(),
                                o.isCorrect(),
                                o.distractorRationale(),
                                Boolean.TRUE.equals(o.trainingEligible())
                        ))
                        .toList();

        QuestionProvenanceRecord provenance = null;
        if (request.provenance() != null) {
            QuestionProvenanceRequestDto p = request.provenance();
            provenance = new QuestionProvenanceRecord(
                    UUID.randomUUID(),
                    itemId,
                    p.sourceType(),
                    p.licenseClassification(),
                    p.copyrightHolder(),
                    p.sourceReference(),
                    p.authorId(),
                    p.reviewerId(),
                    p.verificationStatus() == null ? ProvenanceVerificationStatus.VERIFIED : p.verificationStatus(),
                    p.provenanceHash(),
                    p.verifiedAt() == null ? now : p.verifiedAt(),
                    Boolean.TRUE.equals(p.trainingEligible()),
                    now
            );
        }

        QuestionBankItem draft = new QuestionBankItem(
                itemId,
                examDefinitionId,
                request.examVersionNumber(),
                request.examSectionId(),
                request.questionCode(),
                request.topicCode(),
                request.subtopicCode(),
                request.itemFormat(),
                request.difficultyBand(),
                request.difficultyBValue(),
                request.discriminationAValue(),
                request.guessingCValue(),
                request.cognitiveLevel(),
                request.stemMarkdown(),
                request.stemLatex(),
                request.altTextDescription(),
                request.hasVisualDiagram(),
                request.numericCorrectAnswer(),
                request.numericTolerance(),
                request.structuredRubric(),
                request.solutionExplanationMarkdown(),
                request.estimatedTimeSeconds(),
                request.maxPoints(),
                request.negativeMarkingWeight(),
                QuestionItemStatus.DRAFT,
                null,
                0L,
                Boolean.TRUE.equals(request.trainingEligible()),
                options,
                provenance,
                now,
                now
        );

        QuestionBankItem created = assemblyService.createQuestionBankItem(examDefinitionId, draft);
        return ResponseEntity.status(HttpStatus.CREATED).body(toQuestionItemResponse(created));
    }

    @PostMapping("/{examDefinitionId}/question-bank/items/{itemId}/publish")
    public ResponseEntity<QuestionBankItemResponseDto> publishQuestionBankItem(
            @PathVariable("examDefinitionId") UUID examDefinitionId,
            @PathVariable("itemId") UUID itemId
    ) {
        QuestionBankItem published = assemblyService.publishQuestionBankItem(examDefinitionId, itemId);
        return ResponseEntity.ok(toQuestionItemResponse(published));
    }

    @GetMapping("/{examDefinitionId}/question-bank/items")
    public ResponseEntity<List<QuestionBankItemResponseDto>> listQuestionBankItems(
            @PathVariable("examDefinitionId") UUID examDefinitionId,
            @RequestParam(value = "examVersionNumber", required = false) Integer examVersionNumber,
            @RequestParam(value = "status", required = false) QuestionItemStatus status,
            @RequestParam(value = "topicCode", required = false) String topicCode,
            @RequestParam(value = "difficultyBand", required = false) QuestionDifficultyBand difficultyBand
    ) {
        List<QuestionBankItemResponseDto> items = assemblyService
                .listQuestionBankItems(examDefinitionId, examVersionNumber, status, topicCode, difficultyBand)
                .stream()
                .map(ExamAssessmentAssemblyController::toQuestionItemResponse)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping("/{examDefinitionId}/blueprints")
    public ResponseEntity<AssessmentBlueprintResponseDto> createBlueprint(
            @PathVariable("examDefinitionId") UUID examDefinitionId,
            @Valid @RequestBody AssessmentBlueprintCreateRequestDto request
    ) {
        UUID blueprintId = UUID.randomUUID();
        Instant now = Instant.now();

        List<BlueprintTopicRule> rules = request.topicRules() == null
                ? List.of()
                : request.topicRules().stream()
                        .map(r -> new BlueprintTopicRule(
                                UUID.randomUUID(),
                                blueprintId,
                                r.topicCode(),
                                r.targetWeightBps(),
                                r.minQuestionCount(),
                                r.maxQuestionCount(),
                                r.requiredItemFormats(),
                                r.ruleOrder(),
                                Boolean.TRUE.equals(r.trainingEligible())
                        ))
                        .toList();

        AssessmentBlueprint draft = new AssessmentBlueprint(
                blueprintId,
                examDefinitionId,
                request.examVersionNumber(),
                request.examSectionId(),
                request.blueprintCode(),
                request.title(),
                request.description(),
                request.blueprintMode(),
                request.totalQuestionCount(),
                request.baseDurationSeconds(),
                request.easyTargetBps(),
                request.mediumTargetBps(),
                request.hardTargetBps(),
                request.expertTargetBps(),
                request.difficultyToleranceBps() == null ? 500 : request.difficultyToleranceBps(),
                request.recallTargetBps(),
                request.comprehensionTargetBps(),
                request.applicationTargetBps(),
                request.analysisTargetBps(),
                request.evaluationTargetBps(),
                request.allowRecentlySeenCooldownDays() == null ? 14 : request.allowRecentlySeenCooldownDays(),
                request.shuffleQuestions() == null || request.shuffleQuestions(),
                request.shuffleOptions() == null || request.shuffleOptions(),
                AssessmentBlueprintStatus.DRAFT,
                null,
                Boolean.TRUE.equals(request.trainingEligible()),
                rules,
                now,
                now
        );

        AssessmentBlueprint created = assemblyService.createBlueprint(examDefinitionId, draft);
        return ResponseEntity.status(HttpStatus.CREATED).body(toBlueprintResponse(created));
    }

    @PostMapping("/{examDefinitionId}/blueprints/{blueprintId}/activate")
    public ResponseEntity<AssessmentBlueprintResponseDto> activateBlueprint(
            @PathVariable("examDefinitionId") UUID examDefinitionId,
            @PathVariable("blueprintId") UUID blueprintId
    ) {
        AssessmentBlueprint activated = assemblyService.activateBlueprint(examDefinitionId, blueprintId);
        return ResponseEntity.ok(toBlueprintResponse(activated));
    }

    @GetMapping("/{examDefinitionId}/blueprints")
    public ResponseEntity<List<AssessmentBlueprintResponseDto>> listBlueprints(
            @PathVariable("examDefinitionId") UUID examDefinitionId,
            @RequestParam(value = "examVersionNumber", required = false) Integer examVersionNumber,
            @RequestParam(value = "status", required = false) AssessmentBlueprintStatus status,
            @RequestParam(value = "blueprintMode", required = false) AssessmentBlueprintMode blueprintMode
    ) {
        List<AssessmentBlueprintResponseDto> blueprints = assemblyService
                .listBlueprints(examDefinitionId, examVersionNumber, status, blueprintMode)
                .stream()
                .map(ExamAssessmentAssemblyController::toBlueprintResponse)
                .toList();
        return ResponseEntity.ok(blueprints);
    }

    @PostMapping("/{examDefinitionId}/assessments/assemble")
    public ResponseEntity<AssembledAssessmentResponseDto> assembleAssessment(
            @PathVariable("examDefinitionId") UUID examDefinitionId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader,
            @Valid @RequestBody AssessmentAssemblyRequestDto request
    ) {
        UUID authenticatedUserId = resolveAuthenticatedUserId(jwt, authentication, requestedUserIdHeader);
        AssembleResult result = assemblyService.assembleAssessment(new AssembleCommand(
                examDefinitionId,
                authenticatedUserId,
                request.userId(),
                request.candidateProfileId(),
                request.blueprintId(),
                request.assemblySeed(),
                request.overrideTimeMultiplier(),
                request.highContrastMode(),
                request.screenReaderOptimized(),
                request.extendedBreakPrompts()
        ));

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(toAssembledAssessmentResponse(result.assessment()));
    }

    @PostMapping("/candidates/{userId}/assessments/assemble")
    public ResponseEntity<AssembledAssessmentResponseDto> assembleCandidateAssessment(
            @PathVariable("userId") UUID userId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader,
            @Valid @RequestBody AssessmentAssemblyRequestDto request
    ) {
        UUID authenticatedUserId = resolveAuthenticatedUserId(jwt, authentication, requestedUserIdHeader);
        if (request.userId() != null && !request.userId().equals(userId)) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "Path userId does not match request payload userId",
                    false,
                    "Ensure path userId and payload userId match the authenticated session."
            );
        }
        AssembleResult result = assemblyService.assembleAssessment(new AssembleCommand(
                null,
                authenticatedUserId,
                userId,
                request.candidateProfileId(),
                request.blueprintId(),
                request.assemblySeed(),
                request.overrideTimeMultiplier(),
                request.highContrastMode(),
                request.screenReaderOptimized(),
                request.extendedBreakPrompts()
        ));

        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(toAssembledAssessmentResponse(result.assessment()));
    }

    @GetMapping("/candidates/{userId}/assessments")
    public ResponseEntity<List<AssembledAssessmentResponseDto>> listCandidateAssembledAssessments(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "examDefinitionId", required = false) UUID examDefinitionId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader
    ) {
        UUID authenticatedUserId = resolveAuthenticatedUserId(jwt, authentication, requestedUserIdHeader);
        List<AssembledAssessmentResponseDto> assessments = assemblyService
                .listAssembledAssessments(authenticatedUserId, userId, examDefinitionId)
                .stream()
                .map(ExamAssessmentAssemblyController::toAssembledAssessmentResponse)
                .toList();
        return ResponseEntity.ok(assessments);
    }

    @GetMapping("/candidates/{userId}/assessments/{assessmentId}")
    public ResponseEntity<AssembledAssessmentResponseDto> getAssembledAssessment(
            @PathVariable("userId") UUID userId,
            @PathVariable("assessmentId") UUID assessmentId,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader
    ) {
        UUID authenticatedUserId = resolveAuthenticatedUserId(jwt, authentication, requestedUserIdHeader);
        AssembledAssessment assessment = assemblyService.getAssembledAssessment(
                authenticatedUserId,
                userId,
                assessmentId
        );
        return ResponseEntity.ok(toAssembledAssessmentResponse(assessment));
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleProblem(ApiProblemException ex) {
        ProblemDetailsResponse body = new ProblemDetailsResponse(
                "https://api.verifiedai.app/problems/" + ex.code().name().toLowerCase().replace('_', '-'),
                ex.getMessage(),
                ex.status().value(),
                ex.code(),
                CorrelationIds.current(),
                ex.details()
        );
        return ResponseEntity.status(ex.status()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetailsResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetailsResponse body = new ProblemDetailsResponse(
                "https://api.verifiedai.app/problems/exam-blueprint-distribution-invalid",
                ex.getMessage(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                CorrelationIds.current(),
                Map.of("recoverable", true, "userAction", "Check request parameters and retry.")
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    // --- Mappers & Helpers ---

    private static UUID resolveAuthenticatedUserId(
            Jwt jwt,
            Authentication authentication,
            String requestedUserIdHeader
    ) {
        UUID principalId = null;
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            principalId = parseUuidOrForbidden(jwt.getSubject());
        } else if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof AuthenticatedLearnerPrincipal alp) {
                principalId = alp.learnerId();
            } else if (principal instanceof VerifiedJwtPrincipal vjp) {
                principalId = vjp.userId();
            } else if (principal instanceof UUID uuid) {
                principalId = uuid;
            } else if (authentication.getName() != null && !authentication.getName().isBlank()) {
                principalId = parseUuidOrForbidden(authentication.getName());
            }
        }

        UUID headerId = null;
        if (requestedUserIdHeader != null && !requestedUserIdHeader.isBlank()) {
            headerId = parseUuidOrForbidden(requestedUserIdHeader.trim());
        }

        if (principalId != null && headerId != null && !principalId.equals(headerId)) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "X-User-Id header does not match authenticated JWT subject",
                    false,
                    "Ensure X-User-Id matches the authenticated user session."
            );
        }

        return principalId != null ? principalId : headerId;
    }

    private static UUID parseUuidOrForbidden(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "Invalid learner UUID identifier",
                    false,
                    "Authenticate with a valid learner UUID."
            );
        }
    }

    private static QuestionBankItemResponseDto toQuestionItemResponse(QuestionBankItem item) {
        List<QuestionOptionResponseDto> options = item.options().stream()
                .map(o -> new QuestionOptionResponseDto(
                        o.id(),
                        o.optionKey(),
                        o.displayOrder(),
                        o.contentMarkdown(),
                        o.contentLatex(),
                        o.isCorrect(),
                        o.distractorRationale(),
                        false
                ))
                .toList();

        QuestionProvenanceResponseDto provenance = null;
        if (item.provenance() != null) {
            QuestionProvenanceRecord p = item.provenance();
            provenance = new QuestionProvenanceResponseDto(
                    p.id(),
                    p.sourceType(),
                    p.licenseClassification(),
                    p.copyrightHolder(),
                    p.sourceReference(),
                    p.authorId(),
                    p.reviewerId(),
                    p.verificationStatus(),
                    p.provenanceHash(),
                    p.verifiedAt(),
                    false,
                    p.createdAt()
            );
        }

        return new QuestionBankItemResponseDto(
                item.id(),
                item.examDefinitionId(),
                item.examVersionNumber(),
                item.examSectionId(),
                item.questionCode(),
                item.topicCode(),
                item.subtopicCode(),
                item.itemFormat(),
                item.difficultyBand(),
                item.difficultyBValue(),
                item.discriminationAValue(),
                item.guessingCValue(),
                item.cognitiveLevel(),
                item.stemMarkdown(),
                item.stemLatex(),
                item.altTextDescription(),
                item.hasVisualDiagram(),
                item.numericCorrectAnswer(),
                item.numericTolerance(),
                item.structuredRubric(),
                item.solutionExplanationMarkdown(),
                item.estimatedTimeSeconds(),
                item.maxPoints(),
                item.negativeMarkingWeight(),
                item.status(),
                item.contentSha256(),
                item.exposureCount(),
                options,
                provenance,
                false,
                item.createdAt(),
                item.updatedAt()
        );
    }

    private static AssessmentBlueprintResponseDto toBlueprintResponse(AssessmentBlueprint bp) {
        List<BlueprintTopicRuleResponseDto> rules = bp.topicRules().stream()
                .map(r -> new BlueprintTopicRuleResponseDto(
                        r.id(),
                        r.topicCode(),
                        r.targetWeightBps(),
                        r.minQuestionCount(),
                        r.maxQuestionCount(),
                        r.requiredItemFormats(),
                        r.ruleOrder(),
                        false
                ))
                .toList();

        return new AssessmentBlueprintResponseDto(
                bp.id(),
                bp.examDefinitionId(),
                bp.examVersionNumber(),
                bp.examSectionId(),
                bp.blueprintCode(),
                bp.title(),
                bp.description(),
                bp.blueprintMode(),
                bp.totalQuestionCount(),
                bp.baseDurationSeconds(),
                bp.easyTargetBps(),
                bp.mediumTargetBps(),
                bp.hardTargetBps(),
                bp.expertTargetBps(),
                bp.difficultyToleranceBps(),
                bp.recallTargetBps(),
                bp.comprehensionTargetBps(),
                bp.applicationTargetBps(),
                bp.analysisTargetBps(),
                bp.evaluationTargetBps(),
                bp.allowRecentlySeenCooldownDays(),
                bp.shuffleQuestions(),
                bp.shuffleOptions(),
                bp.status(),
                bp.blueprintHash(),
                rules,
                false,
                bp.createdAt(),
                bp.updatedAt()
        );
    }

    private static AssembledAssessmentResponseDto toAssembledAssessmentResponse(AssembledAssessment a) {
        List<AssembledAssessmentItemResponseDto> items = a.items().stream()
                .map(ExamAssessmentAssemblyController::toAssembledItemResponse)
                .toList();

        return new AssembledAssessmentResponseDto(
                a.id(),
                a.userId(),
                a.candidateProfileId(),
                a.blueprintId(),
                a.blueprintCode(),
                a.blueprintTitle(),
                a.blueprintMode(),
                a.examDefinitionId(),
                a.examVersionNumber(),
                a.assemblySeed(),
                a.reproducibilityHash(),
                a.totalQuestionCount(),
                a.baseDurationSeconds(),
                a.timeMultiplier(),
                a.effectiveDurationSeconds(),
                a.accommodationsSnapshot(),
                a.distributionSummary(),
                a.status(),
                a.assembledAt(),
                a.expiresAt(),
                items,
                false
        );
    }

    private static AssembledAssessmentItemResponseDto toAssembledItemResponse(AssembledAssessmentItem item) {
        List<CandidateQuestionOptionResponseDto> safeOptions = item.orderedOptions().stream()
                .map(opt -> new CandidateQuestionOptionResponseDto(
                        opt.optionKey(),
                        opt.displayOrder(),
                        opt.contentMarkdown(),
                        opt.contentLatex(),
                        false
                ))
                .toList();

        return new AssembledAssessmentItemResponseDto(
                item.id(),
                item.questionItemId(),
                item.questionCode(),
                item.sequenceNumber(),
                item.topicCode(),
                item.subtopicCode(),
                item.itemFormat(),
                item.difficultyBand(),
                item.cognitiveLevel(),
                item.stemMarkdown(),
                item.stemLatex(),
                item.altTextDescription(),
                item.hasVisualDiagram(),
                item.optionOrder(),
                safeOptions,
                item.allocatedPoints(),
                item.estimatedTimeSeconds(),
                false
        );
    }
}
