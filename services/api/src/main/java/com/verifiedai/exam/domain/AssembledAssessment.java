package com.verifiedai.exam.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AssembledAssessment(
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
        boolean trainingEligible,
        List<AssembledAssessmentItem> items
) {
    public AssembledAssessment {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(blueprintId, "blueprintId is required");
        Objects.requireNonNull(examDefinitionId, "examDefinitionId is required");
        Objects.requireNonNull(status, "status is required");
        if (examVersionNumber < 1) {
            throw new IllegalArgumentException("examVersionNumber must be >= 1");
        }
        if (assemblySeed == null || assemblySeed.trim().length() < 4) {
            throw new IllegalArgumentException("assemblySeed must be at least 4 characters");
        }
        assemblySeed = assemblySeed.trim();
        if (totalQuestionCount < 5 || totalQuestionCount > 200) {
            throw new IllegalArgumentException("totalQuestionCount must be between 5 and 200");
        }
        if (baseDurationSeconds < 300 || baseDurationSeconds > 21600) {
            throw new IllegalArgumentException("baseDurationSeconds must be between 300 and 21600");
        }
        timeMultiplier = timeMultiplier == null ? new BigDecimal("1.00") : timeMultiplier;
        if (effectiveDurationSeconds < baseDurationSeconds) {
            throw new IllegalArgumentException("effectiveDurationSeconds must be >= baseDurationSeconds");
        }
        accommodationsSnapshot = accommodationsSnapshot == null
                ? AppliedAccommodationSnapshot.standardDefaults()
                : accommodationsSnapshot;
        items = items == null
                ? List.of()
                : items.stream()
                        .sorted(Comparator.comparingInt(AssembledAssessmentItem::sequenceNumber))
                        .toList();
        Set<UUID> seenItems = new HashSet<>();
        Set<Integer> seenSeqs = new HashSet<>();
        for (AssembledAssessmentItem item : items) {
            if (!seenItems.add(item.questionItemId())) {
                throw new IllegalArgumentException("Duplicate questionItemId in assembled assessment: " + item.questionItemId());
            }
            if (!seenSeqs.add(item.sequenceNumber())) {
                throw new IllegalArgumentException("Duplicate sequenceNumber in assembled assessment: " + item.sequenceNumber());
            }
        }
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
        if (assembledAt == null) {
            assembledAt = Instant.now();
        }
        if (expiresAt == null || !expiresAt.isAfter(assembledAt)) {
            expiresAt = assembledAt.plusSeconds(7L * 24 * 3600);
        }
    }
}
