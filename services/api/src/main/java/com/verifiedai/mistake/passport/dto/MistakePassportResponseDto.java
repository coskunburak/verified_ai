package com.verifiedai.mistake.passport.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MistakePassportResponseDto(
    String schemaVersion, // "mistake-passport.v1"
    UUID learnerId,
    Instant generatedAt,
    PassportSummaryMetrics summaryMetrics,
    List<ExamTrapItemDto> conceptualTraps,      // Primary tab: Conceptual & Structural Traps
    List<ExamTrapItemDto> arithmeticSlips,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> summaryTrapTitleKeys,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<CounterexampleProofSnapshot> earnedProofs
) {
    public MistakePassportResponseDto(String schemaVersion, UUID learnerId, Instant generatedAt,
        PassportSummaryMetrics metrics, List<ExamTrapItemDto> traps, List<ExamTrapItemDto> slips) {
        this(schemaVersion, learnerId, generatedAt, metrics, traps, slips, List.of(), List.of());
    }

    public record PassportSummaryMetrics(
        int conqueredExamTrapsCount,            // K: STABLE_RESOLVED count
        int activeExamTrapsCount,               // RECURRING_PATTERN + IN_REMEDIATION
        int pendingRetentionVerificationCount,  // RESOLVED_PENDING_RETENTION (7-day lock)
        BigDecimal independentTransferRate,     // X%: I_{u,s} across resolved/active twin drills [0.0000 - 1.0000]
        BigDecimal meanPosteriorMastery,        // M_{u,s} [0.0000 - 1.0000]
        BigDecimal meanRetentionProbability     // R_{u,s}(t) [0.0000 - 1.0000]
    ) {}

    public record ExamTrapItemDto(
        UUID mistakeDnaId,
        String mistakeCode,                     // e.g., "ALG_SIGN_DISTRIBUTION", "ARITH_BASIC_COMPUTATION"
        String mistakeFamily,                   // "CONCEPTUAL_STRUCTURAL" | "ARITHMETIC_SLIP"
        String skillNodeId,                     // Curriculum skill code
        String localizedTrapTitleKey,           // e.g., "trap.alg_sign_distribution.title"
        String localizedCoachingFramingKey,     // Non-stigmatizing externalized description key
        TrapLifecycleStatus lifecycleStatus,    // RECURRING_PATTERN, IN_REMEDIATION, RESOLVED_PENDING_RETENTION, STABLE_RESOLVED
        int occurrenceCount,
        int consecutiveSoloTwinPasses,
        Instant lastDetectedAt,
        Instant retentionUnlockEligibleAt,      // Populated when RESOLVED_PENDING_RETENTION (+7 days)
        Instant conqueredAt,                    // Populated when STABLE_RESOLVED
        SkillFiveMetricsSnapshot skillMetrics,  // Strict DOC-86 5-metric separation
        ActiveTwinRemediationLink activeTwinLink,
        CounterexampleProofSnapshot latestCounterexample // H-01 card payload
    ) {}

    public enum TrapLifecycleStatus {
        RECURRING_PATTERN,
        IN_REMEDIATION,
        RESOLVED_PENDING_RETENTION,
        STABLE_RESOLVED
    }

    public record SkillFiveMetricsSnapshot(
        BigDecimal observedOutcomeScore,        // O_{u,a}
        BigDecimal independentCompetenceScore,  // I_{u,s}
        BigDecimal posteriorMasteryProbability, // M_{u,s} (Inner Ring)
        BigDecimal retentionProbability,        // R_{u,s}(t) (Outer Ring)
        BigDecimal scaffoldDependencyIndex      // S_{u,s}
    ) {}

    public record ActiveTwinRemediationLink(
        UUID twinDrillId,
        String twinChallengeStatus,             // "READY_FOR_SOLO_PROVE_IT", "IN_PROGRESS", "AWAITING_7_DAY_RETENTION"
        String deepLinkUri                      // "verifiedai://twin-challenge/{twinDrillId}"
    ) {}

    public record CounterexampleProofSnapshot(
        String cardId,                          // "H-01"
        String faultyStepLatex,                 // e.g., "(a+b)^2 = a^2 + b^2"
        String validStepLatex,                  // e.g., "(a+b)^2 = a^2 + 2ab + b^2"
        String witnessSubstitutionLatex,        // e.g., "a = 2, b = 3"
        String lhsEvaluatedLatex,               // e.g., "(2+3)^2 = 25"
        String rhsFaultyEvaluatedLatex,         // e.g., "2^2 + 3^2 = 13"
        String contradictionStatementLatex,     // e.g., "25 \\neq 13"
        String accessibilitySpeechTr,           // Turkish VoiceOver full mathematical speech
        String accessibilitySpeechEn            // English VoiceOver full mathematical speech
    ) {}
}
