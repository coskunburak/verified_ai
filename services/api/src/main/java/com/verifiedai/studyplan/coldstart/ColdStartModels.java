package com.verifiedai.studyplan.coldstart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Schema-aligned domain records and HTTP DTOs for Sprint 8.10 cold-start calibration
 * ({@code cold-start-v1}). Zero Jackson 2 imports, zero PII, trainingEligible always false.
 */
public final class ColdStartModels {

    public static final String SCHEMA_VERSION = "cold-start-v1";

    private ColdStartModels() {
    }

    public enum DiagnosticStatus {
        COMPLETED,
        REVALIDATION_REQUIRED
    }

    public enum BaselineTier {
        FOUNDATION_SOLID,
        CALIBRATED_MIXED,
        FOUNDATION_REPAIR
    }

    public enum CalibrationMode {
        EXPLORATION,
        ACTIVE_CALIBRATED,
        REVALIDATION_REQUIRED
    }

    public enum ConfidenceTier {
        PRELIMINARY,
        CALIBRATED
    }

    public enum ItemVerdict {
        CORRECT_FAST,
        CORRECT_SLOW,
        TRAP_DISTRACTOR,
        INCORRECT_OTHER,
        TIMED_OUT,
        SKIPPED
    }

    public enum RationaleCode {
        PRELIMINARY_SPARSE_EVIDENCE,
        GOAL_CAPACITY_CONFLICT_RESOLVED,
        INACTIVE_DECAY_APPLIED,
        EXPLICIT_PREFERENCE_OVERRIDE,
        TRAP_SEEDED_WARMUP,
        BASELINE_SOLID_ACCELERATED,
        BASELINE_REPAIR_FOUNDATION
    }

    public record DiagnosticItemSubmissionRequest(
            @NotBlank @Size(min = 3, max = 64) String itemId,
            @Size(max = 32) String selectedOptionId,
            @Size(max = 128) String answer,
            @Min(0) @Max(1800) int elapsedSeconds,
            boolean timedOut,
            boolean skipped
    ) {
    }

    public record ColdStartDiagnosticRequest(
            @Size(min = 3, max = 32) String schemaVersion,
            @NotBlank @Size(min = 8, max = 128) String idempotencyKey,
            @NotBlank @Size(min = 2, max = 16) String locale,
            @Size(max = 32) String declaredExamTarget,
            @Min(5) @Max(240) Integer declaredDailyBudgetMinutes,
            @Size(max = 32) String preferredModality,
            Boolean explicitPreferenceOverride,
            @Min(0) @Max(3650) Integer daysSinceLastVerifiedAttempt,
            @NotNull @Size(min = 3, max = 3) List<@Valid DiagnosticItemSubmissionRequest> responses
    ) {
    }

    public record ColdStartItemOutcome(
            String itemId,
            String skillCode,
            boolean correct,
            ItemVerdict verdict,
            String detectedTrapArchetype,
            double assignedPrior,
            int elapsedSeconds
    ) {
    }

    public record ColdStartSkillPrior(
            String skillCode,
            double priorProbability,
            int evidenceCount,
            ConfidenceTier confidenceTier,
            boolean decayApplied
    ) {
    }

    public record ColdStartRationale(
            RationaleCode code,
            String localizedMessage
    ) {
    }

    public record ColdStartDiagnosticResponse(
            String schemaVersion,
            UUID diagnosticId,
            UUID learnerId,
            String locale,
            DiagnosticStatus status,
            BaselineTier baselineTier,
            int correctCount,
            int totalItems,
            CalibrationMode calibrationMode,
            ConfidenceTier confidenceTier,
            boolean goalConflictResolved,
            int recommendedDailyBudgetMinutes,
            boolean inactiveDecayApplied,
            boolean explicitPreferenceOverride,
            String declaredExamTarget,
            Integer declaredDailyBudgetMinutes,
            String preferredModality,
            String revalidationPromptKey,
            List<String> detectedTrapArchetypes,
            List<ColdStartItemOutcome> itemOutcomes,
            List<ColdStartSkillPrior> initializedPriors,
            List<ColdStartRationale> rationales,
            int attemptDurationSeconds,
            int llmTokensUsed,
            boolean trainingEligible,
            boolean idempotentReplay,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record StoredColdStartRecord(
            UUID diagnosticId,
            UUID learnerId,
            String schemaVersion,
            String idempotencyKey,
            String locale,
            DiagnosticStatus status,
            BaselineTier baselineTier,
            int correctCount,
            int totalItems,
            CalibrationMode calibrationMode,
            ConfidenceTier confidenceTier,
            boolean goalConflictResolved,
            int recommendedDailyBudgetMinutes,
            boolean inactiveDecayApplied,
            boolean explicitPreferenceOverride,
            String declaredExamTarget,
            Integer declaredDailyBudgetMinutes,
            String preferredModality,
            String revalidationPromptKey,
            List<String> detectedTrapArchetypes,
            List<ColdStartItemOutcome> itemOutcomes,
            List<ColdStartSkillPrior> initializedPriors,
            List<ColdStartRationale> rationales,
            int attemptDurationSeconds,
            int llmTokensUsed,
            boolean trainingEligible,
            Instant createdAt,
            Instant updatedAt
    ) {
        public ColdStartDiagnosticResponse toResponse(boolean idempotentReplay) {
            return new ColdStartDiagnosticResponse(
                    schemaVersion,
                    diagnosticId,
                    learnerId,
                    locale,
                    status,
                    baselineTier,
                    correctCount,
                    totalItems,
                    calibrationMode,
                    confidenceTier,
                    goalConflictResolved,
                    recommendedDailyBudgetMinutes,
                    inactiveDecayApplied,
                    explicitPreferenceOverride,
                    declaredExamTarget,
                    declaredDailyBudgetMinutes,
                    preferredModality,
                    revalidationPromptKey,
                    detectedTrapArchetypes,
                    itemOutcomes,
                    initializedPriors,
                    rationales,
                    attemptDurationSeconds,
                    llmTokensUsed,
                    trainingEligible,
                    idempotentReplay,
                    createdAt,
                    updatedAt
            );
        }
    }
}
