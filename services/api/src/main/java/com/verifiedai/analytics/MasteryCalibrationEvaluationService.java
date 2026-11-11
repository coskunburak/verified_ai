package com.verifiedai.analytics;

import com.verifiedai.analytics.MasteryCalibrationSummaryDto.ReliabilityBinDto;
import com.verifiedai.analytics.MasteryCalibrationSummaryDto.SkillCalibrationSliceDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Transactional
public class MasteryCalibrationEvaluationService {

    private final LearnerMasteryCalibrationRepository repository;

    public MasteryCalibrationEvaluationService(LearnerMasteryCalibrationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
    }

    /**
     * Ingests a new unassisted calibration observation idempotently.
     */
    public LearnerMasteryCalibrationRecordEntity recordCalibrationObservation(
        UUID userId,
        UUID attemptId,
        String canonicalSkillCode,
        BigDecimal predictedRetentionProb,
        int observedIndependentOutcome
    ) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(attemptId, "attemptId cannot be null");
        Objects.requireNonNull(canonicalSkillCode, "canonicalSkillCode cannot be null");

        Optional<LearnerMasteryCalibrationRecordEntity> existing =
            repository.findByAttemptIdAndCanonicalSkillCode(attemptId, canonicalSkillCode);
        if (existing.isPresent()) {
            return existing.get();
        }

        BigDecimal brierResidual = BrierAndEceCalculator.computeBrierResidual(
            predictedRetentionProb,
            observedIndependentOutcome
        );

        LearnerMasteryCalibrationRecordEntity entity = new LearnerMasteryCalibrationRecordEntity(
            userId,
            attemptId,
            canonicalSkillCode,
            predictedRetentionProb,
            (short) observedIndependentOutcome,
            brierResidual,
            Instant.now()
        );

        return repository.save(entity);
    }

    /**
     * Computes the full cohort psychometric calibration summary across an evaluation window.
     */
    @Transactional(readOnly = true)
    public MasteryCalibrationSummaryDto getCalibrationSummary(String canonicalSkillCode, int windowDays) {
        int boundedWindowDays = Math.max(1, Math.min(windowDays, 365));
        Instant to = Instant.now();
        Instant from = to.minus(boundedWindowDays, ChronoUnit.DAYS);

        String skillFilter = (canonicalSkillCode != null && !canonicalSkillCode.isBlank())
            ? canonicalSkillCode.trim()
            : null;

        List<LearnerMasteryCalibrationRecordEntity> records = (skillFilter != null)
            ? repository.findByCanonicalSkillCodeAndCreatedAtBetweenOrderByCreatedAtDesc(skillFilter, from, to)
            : repository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);

        List<BrierAndEceCalculator.CalibrationSample> cohortSamples = records.stream()
            .map(r -> new BrierAndEceCalculator.CalibrationSample(
                r.getPredictedRetentionProb(),
                r.getObservedIndependentOutcome()
            ))
            .toList();

        BrierAndEceCalculator.CalibrationEvaluationResult cohortEval =
            BrierAndEceCalculator.evaluate(cohortSamples);

        List<ReliabilityBinDto> reliabilityBins = cohortEval.reliabilityBins().stream()
            .map(b -> new ReliabilityBinDto(
                b.binIndex(),
                b.lowerBound(),
                b.upperBound(),
                b.sampleCount(),
                b.meanPredictedConfidence(),
                b.observedIndependentAccuracy(),
                b.absoluteCalibrationGap()
            ))
            .toList();

        Map<String, List<LearnerMasteryCalibrationRecordEntity>> groupedBySkill = new TreeMap<>();
        for (LearnerMasteryCalibrationRecordEntity rec : records) {
            groupedBySkill.computeIfAbsent(rec.getCanonicalSkillCode(), k -> new ArrayList<>()).add(rec);
        }

        List<SkillCalibrationSliceDto> skillSlices = new ArrayList<>(groupedBySkill.size());
        for (Map.Entry<String, List<LearnerMasteryCalibrationRecordEntity>> entry : groupedBySkill.entrySet()) {
            List<BrierAndEceCalculator.CalibrationSample> skillSamples = entry.getValue().stream()
                .map(r -> new BrierAndEceCalculator.CalibrationSample(
                    r.getPredictedRetentionProb(),
                    r.getObservedIndependentOutcome()
                ))
                .toList();
            BrierAndEceCalculator.CalibrationEvaluationResult skillEval =
                BrierAndEceCalculator.evaluate(skillSamples);

            skillSlices.add(new SkillCalibrationSliceDto(
                entry.getKey(),
                skillEval.sampleSize(),
                skillEval.brierScore(),
                skillEval.expectedCalibrationError(),
                skillEval.calibrationStatus().name(),
                skillEval.gatePassed()
            ));
        }

        return new MasteryCalibrationSummaryDto(
            MasteryCalibrationSummaryDto.CURRENT_SCHEMA_VERSION,
            to,
            skillFilter,
            boundedWindowDays,
            cohortEval.sampleSize(),
            cohortEval.brierScore(),
            MasteryCalibrationSummaryDto.DEFAULT_BRIER_THRESHOLD,
            cohortEval.expectedCalibrationError(),
            MasteryCalibrationSummaryDto.DEFAULT_ECE_THRESHOLD,
            cohortEval.signedCalibrationBias(),
            cohortEval.calibrationStatus().name(),
            cohortEval.gatePassed(),
            reliabilityBins,
            skillSlices
        );
    }
}
