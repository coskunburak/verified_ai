package com.verifiedai.exam.domain;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain value objects, enums, and REST DTO contracts for Phase 9 / Sprint 9.1
 * Exam Definitions, Curriculum Mapping, and Versioning.
 */
public final class ExamModels {

    private ExamModels() {
    }

    public enum Jurisdiction {
        TR,
        INTL
    }

    public enum ExamFamily {
        YKS,
        LGS,
        SAT,
        AP,
        IB
    }

    public enum ExamStatus {
        DRAFT,
        ACTIVE,
        DEPRECATED,
        ARCHIVED
    }

    public enum ScoringFormulaType {
        CORRECT_MINUS_QUARTER_WRONG,
        RAW_CORRECT_ONLY,
        WEIGHTED_SECTION_RUBRIC
    }

    public enum NavigationPolicy {
        FREE_JUMP,
        SECTION_LOCKED,
        LINEAR_ONLY
    }

    public enum TargetStatus {
        ACTIVE,
        SUPERSEDED,
        ACHIEVED,
        ARCHIVED
    }

    public record ExamSectionSpec(
            String sectionCode,
            String titleEn,
            String titleTr,
            int questionCount,
            int durationMinutes,
            int order
    ) {
    }

    public record CognitiveLevelMix(
            double recall,
            double application,
            double analysis
    ) {
    }

    public record SectionWeightEntry(
            String sectionCode,
            double weight
    ) {
    }

    public record ScoringParameters(
            Double scaleMin,
            Double scaleMax,
            Integer moduleCount,
            Integer rubricScaleMin,
            Integer rubricScaleMax,
            List<SectionWeightEntry> sectionWeights
    ) {
        public ScoringParameters {
            sectionWeights = sectionWeights == null ? List.of() : List.copyOf(sectionWeights);
        }
    }

    public record ExamTopicWeight(
            UUID id,
            UUID examDefinitionId,
            String sectionCode,
            String skillId,
            String domainCode,
            BigDecimal questionCountWeight,
            int minQuestions,
            int maxQuestions,
            CognitiveLevelMix cognitiveLevelMix,
            int sortOrder
    ) {
    }

    public record ExamDefinition(
            UUID id,
            String examCode,
            String examVersion,
            Jurisdiction jurisdiction,
            ExamFamily examFamily,
            String titleEn,
            String titleTr,
            ExamStatus status,
            int totalQuestions,
            int durationMinutes,
            ScoringFormulaType scoringFormulaType,
            BigDecimal wrongPenaltyFactor,
            BigDecimal minScore,
            BigDecimal maxScore,
            NavigationPolicy navigationPolicy,
            String blueprintHash,
            Instant effectiveFrom,
            Instant deprecatedAt,
            String supersededByVersion,
            List<ExamSectionSpec> sections,
            ScoringParameters scoringParameters,
            List<ExamTopicWeight> topicWeights,
            boolean trainingEligible,
            Instant createdAt,
            Instant updatedAt
    ) {
        public ExamDefinition {
            Objects.requireNonNull(examCode, "examCode cannot be null");
            Objects.requireNonNull(examVersion, "examVersion cannot be null");
            sections = sections == null ? List.of() : List.copyOf(sections);
            topicWeights = topicWeights == null ? List.of() : List.copyOf(topicWeights);
        }
    }

    public record ExamDefinitionSummaryDto(
            UUID examDefinitionId,
            String examCode,
            String examVersion,
            Jurisdiction jurisdiction,
            ExamFamily examFamily,
            String titleEn,
            String titleTr,
            ExamStatus status,
            int totalQuestions,
            int durationMinutes,
            ScoringFormulaType scoringFormulaType,
            BigDecimal wrongPenaltyFactor,
            BigDecimal minScore,
            BigDecimal maxScore,
            NavigationPolicy navigationPolicy,
            String blueprintHash,
            Instant effectiveFrom,
            Instant deprecatedAt,
            String supersededByVersion,
            int sectionCount,
            int topicWeightCount,
            boolean trainingEligible
    ) {
    }

    public record ExamDefinitionCatalogResponse(
            String schemaVersion,
            List<ExamDefinitionSummaryDto> definitions,
            int totalCount,
            boolean includeDeprecated,
            boolean trainingEligible,
            int llmTokensUsed
    ) {
    }

    public record ExamTopicWeightDto(
            String sectionCode,
            String skillId,
            String domainCode,
            BigDecimal questionCountWeight,
            int minQuestions,
            int maxQuestions,
            CognitiveLevelMix cognitiveLevelMix,
            int sortOrder
    ) {
    }

    public record ExamDefinitionDetailResponse(
            String schemaVersion,
            UUID examDefinitionId,
            String examCode,
            String examVersion,
            Jurisdiction jurisdiction,
            ExamFamily examFamily,
            String titleEn,
            String titleTr,
            ExamStatus status,
            int totalQuestions,
            int durationMinutes,
            ScoringFormulaType scoringFormulaType,
            BigDecimal wrongPenaltyFactor,
            BigDecimal minScore,
            BigDecimal maxScore,
            NavigationPolicy navigationPolicy,
            String blueprintHash,
            Instant effectiveFrom,
            Instant deprecatedAt,
            String supersededByVersion,
            List<ExamSectionSpec> sections,
            ScoringParameters scoringParameters,
            List<ExamTopicWeightDto> topicWeights,
            boolean trainingEligible,
            int llmTokensUsed
    ) {
    }

    public record PinnedBlueprintSnapshot(
            UUID examDefinitionId,
            String examCode,
            String examVersion,
            Jurisdiction jurisdiction,
            ExamFamily examFamily,
            String titleEn,
            String titleTr,
            int totalQuestions,
            int durationMinutes,
            ScoringFormulaType scoringFormulaType,
            BigDecimal wrongPenaltyFactor,
            BigDecimal minScore,
            BigDecimal maxScore,
            NavigationPolicy navigationPolicy,
            String blueprintHash,
            Instant effectiveFrom,
            List<ExamSectionSpec> sections,
            ScoringParameters scoringParameters,
            List<ExamTopicWeightDto> topicWeights,
            boolean trainingEligible
    ) {
    }

    public record ReadinessGapPreview(
            String schemaVersion,
            BigDecimal currentEstimatedScore,
            BigDecimal targetScore,
            BigDecimal gapPoints,
            String estimationBasis,
            int llmTokensUsed,
            boolean trainingEligible
    ) {
    }

    public record UpsertStudentExamTargetRequest(
            @NotBlank
            @Pattern(regexp = "^student-exam-target-request-v1$")
            String schemaVersion,

            @NotBlank
            @Pattern(regexp = "^[A-Z0-9_]{3,64}$")
            String examCode,

            @Size(min = 1, max = 32)
            String examVersion,

            @Pattern(regexp = "^[a-f0-9]{64}$")
            String expectedBlueprintHash,

            @NotNull
            @DecimalMin("-100.00")
            @DecimalMax("2000.00")
            BigDecimal targetScore,

            @NotNull
            LocalDate targetDate,

            @Min(1)
            @Max(5)
            Integer priorityRank,

            Boolean isPrimary,

            @NotBlank
            @Size(min = 4, max = 128)
            String idempotencyKey
    ) {
    }

    public record StudentExamTargetRecord(
            UUID targetId,
            UUID userId,
            UUID examDefinitionId,
            String examCode,
            String examVersion,
            String blueprintHash,
            BigDecimal targetScore,
            LocalDate targetDate,
            int priorityRank,
            TargetStatus targetStatus,
            boolean isPrimary,
            String idempotencyKey,
            String snapshotPayloadJson,
            Instant supersededAt,
            int llmTokensUsed,
            boolean trainingEligible,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record StudentExamTargetResponse(
            String schemaVersion,
            UUID targetId,
            UUID userId,
            UUID examDefinitionId,
            String examCode,
            String examVersion,
            String blueprintHash,
            BigDecimal targetScore,
            LocalDate targetDate,
            int priorityRank,
            TargetStatus targetStatus,
            boolean isPrimary,
            String idempotencyKey,
            ReadinessGapPreview readinessGapPreview,
            PinnedBlueprintSnapshot pinnedBlueprint,
            Instant supersededAt,
            boolean idempotentReplay,
            int llmTokensUsed,
            boolean trainingEligible,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record StudentExamTargetListResponse(
            String schemaVersion,
            UUID userId,
            List<StudentExamTargetResponse> targets,
            int totalCount,
            int llmTokensUsed,
            boolean trainingEligible
    ) {
    }

    public record TargetBindingOutcome(
            StudentExamTargetResponse response,
            boolean created
    ) {
    }

    public record SectionResponseCount(
            String sectionCode,
            int correctCount,
            int wrongCount,
            int blankCount
    ) {
    }

    public record ScoringEvaluationResult(
            BigDecimal rawNetScore,
            BigDecimal scaledScore,
            BigDecimal minScore,
            BigDecimal maxScore,
            int correctCount,
            int wrongCount,
            int blankCount,
            ScoringFormulaType formulaType,
            int llmTokensUsed
    ) {
    }
}
