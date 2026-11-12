package com.verifiedai.studyplan.practice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.verifiedai.studyplan.practice.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record PracticeSessionItemDto(
        @JsonProperty("id") UUID id,
        @JsonProperty("sessionId") UUID sessionId,
        @JsonProperty("userId") UUID userId,
        @JsonProperty("ordinalIndex") int ordinalIndex,
        @JsonProperty("slotRole") PracticeSlotRole slotRole,
        @JsonProperty("questionSource") PracticeQuestionSource questionSource,
        @JsonProperty("sourceQuestionId") String sourceQuestionId,
        @JsonProperty("skillId") String skillId,
        @JsonProperty("trapFamily") String trapFamily,
        @JsonProperty("targetDifficulty") BigDecimal targetDifficulty,
        @JsonProperty("itemDifficulty") BigDecimal itemDifficulty,
        @JsonProperty("zpdWindow") ZpdWindowDto zpdWindow,
        @JsonProperty("promptLatex") String promptLatex,
        @JsonProperty("promptInstructionTr") String promptInstructionTr,
        @JsonProperty("promptInstructionEn") String promptInstructionEn,
        @JsonProperty("canonicalSolutionExpr") String canonicalSolutionExpr,
        @JsonProperty("trapDistractorExpr") String trapDistractorExpr,
        @JsonProperty("sympyVerificationHash") String sympyVerificationHash,
        @JsonProperty("isTwinVerification") boolean isTwinVerification,
        @JsonProperty("lockState") VerificationLockStateDto lockState,
        @JsonProperty("hintsUnlockedCount") int hintsUnlockedCount,
        @JsonProperty("attemptCount") int attemptCount,
        @JsonProperty("itemStatus") PracticeItemStatus itemStatus,
        @JsonProperty("submittedAnswerLatex") String submittedAnswerLatex,
        @JsonProperty("detectedTrapTriggered") boolean detectedTrapTriggered,
        @JsonProperty("verificationDurationMs") Long verificationDurationMs,
        @JsonProperty("submittedAt") Instant submittedAt,
        @JsonProperty("createdAt") Instant createdAt
) {
    public static PracticeSessionItemDto fromEntity(PracticeSessionItemEntity entity) {
        Instant now = Instant.now();
        int remaining = 0;
        boolean isLocked = false;
        if (entity.isTwinVerification()) {
            if (entity.getUnlockedAt() != null) {
                long elapsedSeconds = Duration.between(entity.getUnlockedAt(), now).getSeconds();
                remaining = (int) Math.max(0, entity.getIndependentLockSeconds() - elapsedSeconds);
                isLocked = (remaining > 0);
            } else {
                remaining = entity.getIndependentLockSeconds();
                isLocked = true;
            }
        }

        VerificationLockStateDto lockState = new VerificationLockStateDto(
                isLocked,
                entity.getIndependentLockSeconds(),
                remaining,
                entity.getUnlockedAt()
        );

        ZpdWindowDto zpd = new ZpdWindowDto(
                entity.getZpdLowerBound(),
                entity.getZpdUpperBound(),
                entity.getTargetDifficulty()
        );

        return new PracticeSessionItemDto(
                entity.getId(),
                entity.getSession() != null ? entity.getSession().getId() : null,
                entity.getUserId(),
                entity.getOrdinalIndex(),
                entity.getSlotRole(),
                entity.getQuestionSource(),
                entity.getSourceQuestionId(),
                entity.getSkillId(),
                entity.getTrapFamily(),
                entity.getTargetDifficulty(),
                entity.getItemDifficulty(),
                zpd,
                entity.getPromptLatex(),
                entity.getPromptInstructionTr(),
                entity.getPromptInstructionEn(),
                entity.getCanonicalSolutionExpr(),
                entity.getTrapDistractorExpr(),
                entity.getSympyVerificationHash(),
                entity.isTwinVerification(),
                lockState,
                entity.getHintsUnlockedCount(),
                entity.getAttemptCount(),
                entity.getItemStatus(),
                entity.getSubmittedAnswerLatex(),
                entity.isDetectedTrapTriggered(),
                entity.getVerificationDurationMs(),
                entity.getSubmittedAt(),
                entity.getCreatedAt()
        );
    }
}
