package com.verifiedai.mistake.passport.service;

import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.billing.application.LearningOsCapability;
import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto;
import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto.*;
import com.verifiedai.mistake.passport.repository.MistakePassportProjectionRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MistakePassportReadModelService {
    private CapabilityAccessPolicy access;
    @org.springframework.beans.factory.annotation.Value("${verifiedai.billing.learning-os-tiers-enabled:false}")
    private String learningOsFlag;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAccess(CapabilityAccessPolicy access) {
        this.access = access;
    }

    private boolean tierGuardsEnabled() {
        return "true".equalsIgnoreCase(learningOsFlag);
    }

    private boolean hasFullPassportAccess(UUID learnerId) {
        if (access == null) {
            return false;
        }
        try {
            return access.check(learnerId, LearningOsCapability.MISTAKE_PASSPORT_FULL);
        } catch (ApiProblemException ex) {
            if (ex.code() == ApiErrorCode.ENTITLEMENT_SYNC_FAILED) {
                return false;
            }
            throw ex;
        }
    }


    private static final String ARITHMETIC_SLIP_CODE = "ARITH_BASIC_COMPUTATION";

    private final MistakePassportProjectionRepository projectionRepository;

    public MistakePassportReadModelService(MistakePassportProjectionRepository projectionRepository) {
        this.projectionRepository = projectionRepository;
    }

    @Transactional(readOnly = true, noRollbackFor = ApiProblemException.class)
    public MistakePassportResponseDto getLearnerMistakePassport(
            UUID authenticatedLearnerId,
            TrapLifecycleStatus statusFilter
    ) {
        List<ExamTrapItemDto> allItems = projectionRepository.fetchPassportItemsByLearnerId(authenticatedLearnerId);

        List<ExamTrapItemDto> allConceptual = allItems.stream()
            .filter(item -> !ARITHMETIC_SLIP_CODE.equals(item.mistakeCode()))
            .toList();

        int conqueredCount = (int) allConceptual.stream()
            .filter(i -> i.lifecycleStatus() == TrapLifecycleStatus.STABLE_RESOLVED)
            .count();

        int activeCount = (int) allConceptual.stream()
            .filter(i -> i.lifecycleStatus() == TrapLifecycleStatus.RECURRING_PATTERN
                      || i.lifecycleStatus() == TrapLifecycleStatus.IN_REMEDIATION)
            .count();

        int pendingRetentionCount = (int) allConceptual.stream()
            .filter(i -> i.lifecycleStatus() == TrapLifecycleStatus.RESOLVED_PENDING_RETENTION)
            .count();

        BigDecimal independentTransferRate = computeAverageMetric(
            allConceptual, i -> i.skillMetrics().independentCompetenceScore()
        );
        BigDecimal meanMastery = computeAverageMetric(
            allConceptual, i -> i.skillMetrics().posteriorMasteryProbability()
        );
        BigDecimal meanRetention = computeAverageMetric(
            allConceptual, i -> i.skillMetrics().retentionProbability()
        );

        PassportSummaryMetrics summary = new PassportSummaryMetrics(
            conqueredCount,
            activeCount,
            pendingRetentionCount,
            independentTransferRate,
            meanMastery,
            meanRetention
        );

        List<ExamTrapItemDto> filteredItems = (statusFilter == null)
            ? allItems
            : allItems.stream().filter(i -> i.lifecycleStatus() == statusFilter).toList();

        if (tierGuardsEnabled() && !hasFullPassportAccess(authenticatedLearnerId)) {
            var titles = allConceptual.stream().filter(i -> i.lifecycleStatus() == TrapLifecycleStatus.RECURRING_PATTERN
                || i.lifecycleStatus() == TrapLifecycleStatus.IN_REMEDIATION)
                .sorted(java.util.Comparator.comparingInt(ExamTrapItemDto::occurrenceCount).reversed())
                .limit(2).map(ExamTrapItemDto::localizedTrapTitleKey).toList();
            var proofs = allItems.stream().map(ExamTrapItemDto::latestCounterexample).filter(Objects::nonNull).distinct().toList();
            return new MistakePassportResponseDto("mistake-passport.v1", authenticatedLearnerId, Instant.now(), summary,
                List.of(), List.of(), titles, proofs);
        }
        Map<Boolean, List<ExamTrapItemDto>> partitioned = filteredItems.stream()
            .collect(Collectors.partitioningBy(i -> ARITHMETIC_SLIP_CODE.equals(i.mistakeCode())));

        return new MistakePassportResponseDto(
            "mistake-passport.v1",
            authenticatedLearnerId,
            Instant.now(),
            summary,
            partitioned.getOrDefault(false, List.of()), // Conceptual & Structural Traps (Primary)
            partitioned.getOrDefault(true, List.of())   // ARITH_BASIC_COMPUTATION (Secondary Tab)
        );
    }

    private BigDecimal computeAverageMetric(
            List<ExamTrapItemDto> items,
            Function<ExamTrapItemDto, BigDecimal> extractor
    ) {
        if (items.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal sum = items.stream()
            .map(extractor)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(items.size()), 4, RoundingMode.HALF_UP);
    }
}
