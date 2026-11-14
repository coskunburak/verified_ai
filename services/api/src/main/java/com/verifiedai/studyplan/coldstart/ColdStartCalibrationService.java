package com.verifiedai.studyplan.coldstart;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.studyplan.coldstart.ColdStartModels.BaselineTier;
import com.verifiedai.studyplan.coldstart.ColdStartModels.CalibrationMode;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartDiagnosticRequest;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartDiagnosticResponse;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartItemOutcome;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartRationale;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartSkillPrior;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ConfidenceTier;
import com.verifiedai.studyplan.coldstart.ColdStartModels.DiagnosticItemSubmissionRequest;
import com.verifiedai.studyplan.coldstart.ColdStartModels.DiagnosticStatus;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ItemVerdict;
import com.verifiedai.studyplan.coldstart.ColdStartModels.RationaleCode;
import com.verifiedai.studyplan.coldstart.ColdStartModels.StoredColdStartRecord;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic $0.00 LLM-spend cold-start diagnostic calibration service (Sprint 8.10).
 * Implements item scoring, BKT-lite priors in [0.15, 0.65], sparse-evidence PRELIMINARY tagging,
 * goal-capacity conflict resolution, >=14d exponential decay / >=21d revalidation flagging,
 * and explicit learner preference overrides.
 */
@Service
public class ColdStartCalibrationService {

    private static final Logger LOG = LoggerFactory.getLogger(ColdStartCalibrationService.class);

    public static final String ITEM_NUMBER_SENSE = "DIAG_ITEM_1_NUMBER_SENSE";
    public static final String ITEM_QUADRATIC_INEQUALITY = "DIAG_ITEM_2_QUADRATIC_INEQUALITY";
    public static final String ITEM_FUNCTION_COMPOSITION = "DIAG_ITEM_3_FUNCTION_COMPOSITION";

    public static final String SKILL_NUMBER_SENSE = "MATH.ARITHMETIC.ORDER_OF_OPERATIONS";
    public static final String SKILL_QUADRATIC_INEQUALITY = "MATH.EQUATIONS.INEQUALITIES_BASIC";
    public static final String SKILL_FUNCTION_COMPOSITION = "MATH.FUNCTIONS.COMPOSITION";

    public static final List<String> CANONICAL_ITEM_IDS = List.of(
            ITEM_NUMBER_SENSE,
            ITEM_QUADRATIC_INEQUALITY,
            ITEM_FUNCTION_COMPOSITION
    );

    public static final double BASE_PRIOR = 0.25;
    public static final double FAST_CORRECT_PRIOR = 0.65;
    public static final double SLOW_CORRECT_PRIOR = 0.50;
    public static final double TRAP_DISTRACTOR_PRIOR = 0.15;
    public static final double OTHER_INCORRECT_PRIOR = 0.20;
    public static final int FAST_THRESHOLD_SECONDS = 60;
    public static final int SLOW_THRESHOLD_SECONDS = 180;
    public static final int SPARSE_EVIDENCE_MIN_ATTEMPTS = 3;
    public static final int INACTIVE_DECAY_MIN_DAYS = 14;
    public static final int REVALIDATION_MIN_DAYS = 21;
    public static final double INACTIVE_DECAY_HALF_LIFE_DAYS = 14.0;
    public static final int DEFAULT_DAILY_BUDGET_MINUTES = 15;
    public static final int AYT_CONFLICT_BUDGET_THRESHOLD = 20;

    private static final Set<String> ALLOWED_LOCALES = Set.of("en-US", "tr-TR");
    private static final Set<String> ALLOWED_EXAM_TARGETS = Set.of("TYT", "AYT", "LGS", "SAT", "GENERAL");
    private static final Set<String> ALLOWED_MODALITIES = Set.of("PRACTICE_FIRST", "SOCRATIC_COACH", "MIXED", "VIDEO_HINTS_OFF");
    private static final String REVALIDATION_PROMPT_KEY = "cold_start.revalidation.banner";

    private final ColdStartDiagnosticRepository repository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final boolean coldStartEnabled;

    @Autowired
    public ColdStartCalibrationService(
            ColdStartDiagnosticRepository repository,
            MeterRegistry meterRegistry,
            Clock clock,
            @Value("${verifiedai.studyplan.cold-start-enabled:false}") boolean coldStartEnabled
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.coldStartEnabled = coldStartEnabled;
    }

    @Transactional(readOnly = true)
    public ColdStartDiagnosticResponse getLatestCalibration(UUID learnerId, Integer daysInactiveOverride) {
        requireFeatureEnabled();
        Objects.requireNonNull(learnerId, "learnerId cannot be null");

        StoredColdStartRecord record = repository.findLatestByLearnerId(learnerId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.COLD_START_DIAGNOSTIC_NOT_FOUND,
                        "No cold-start calibration baseline recorded for learner.",
                        false,
                        "RUN_COLD_START_DIAGNOSTIC"
                ));

        int effectiveInactiveDays = resolveEffectiveInactiveDays(learnerId, daysInactiveOverride, record.createdAt());
        if (effectiveInactiveDays >= INACTIVE_DECAY_MIN_DAYS && !record.inactiveDecayApplied()) {
            return applyInactivityProjection(record, effectiveInactiveDays);
        }
        return record.toResponse(false);
    }

    @Transactional
    public ColdStartDiagnosticResponse submitDiagnostic(UUID learnerId, ColdStartDiagnosticRequest request) {
        requireFeatureEnabled();
        Objects.requireNonNull(learnerId, "learnerId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        String schemaVersion = request.schemaVersion() == null || request.schemaVersion().isBlank()
                ? ColdStartModels.SCHEMA_VERSION
                : request.schemaVersion().trim();
        if (!ColdStartModels.SCHEMA_VERSION.equals(schemaVersion)) {
            throw invalidRequest("schemaVersion must equal 'cold-start-v1'");
        }

        String idempotencyKey = request.idempotencyKey() != null ? request.idempotencyKey().trim() : "";
        if (idempotencyKey.length() < 8 || idempotencyKey.length() > 128) {
            throw invalidRequest("idempotencyKey must be between 8 and 128 characters");
        }

        Optional<StoredColdStartRecord> existing = repository.findByLearnerIdAndIdempotencyKey(learnerId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get().toResponse(true);
        }

        String locale = normalizeLocale(request.locale());
        String examTarget = normalizeOptionalEnum(request.declaredExamTarget(), ALLOWED_EXAM_TARGETS, "declaredExamTarget");
        String modality = normalizeOptionalEnum(request.preferredModality(), ALLOWED_MODALITIES, "preferredModality");
        Integer declaredBudget = request.declaredDailyBudgetMinutes();
        boolean explicitOverride = Boolean.TRUE.equals(request.explicitPreferenceOverride());

        Map<String, DiagnosticItemSubmissionRequest> submissionByItem = validateAndIndexSubmissions(request.responses());
        Map<String, Integer> historicalEvidenceCounts = repository.findSkillEvidenceCounts(
                learnerId,
                List.of(SKILL_NUMBER_SENSE, SKILL_QUADRATIC_INEQUALITY, SKILL_FUNCTION_COMPOSITION)
        );
        int effectiveInactiveDays = resolveEffectiveInactiveDays(learnerId, request.daysSinceLastVerifiedAttempt(), null);

        incrementCounter("studyplan_cold_start_diagnostic_started_total", "locale", locale);
        LOG.info("event=cold_start_diagnostic_started learner_id={} locale={} llm_tokens_used=0 training_eligible=false",
                learnerId, locale);

        List<ColdStartItemOutcome> outcomes = new ArrayList<>(3);
        List<ColdStartSkillPrior> priors = new ArrayList<>(3);
        Set<String> trapsSet = new LinkedHashSet<>();
        int correctCount = 0;
        int totalElapsedSeconds = 0;
        boolean anyPreliminary = false;
        boolean decayApplied = effectiveInactiveDays >= INACTIVE_DECAY_MIN_DAYS;

        for (String itemId : CANONICAL_ITEM_IDS) {
            DiagnosticItemSubmissionRequest sub = submissionByItem.get(itemId);
            String skillCode = skillForItem(itemId);
            EvaluationResult eval = evaluateItem(itemId, sub);
            outcomes.add(new ColdStartItemOutcome(
                    itemId,
                    skillCode,
                    eval.correct(),
                    eval.verdict(),
                    eval.trapArchetype(),
                    eval.prior(),
                    sub.elapsedSeconds()
            ));
            totalElapsedSeconds += sub.elapsedSeconds();
            if (eval.correct()) {
                correctCount++;
            }
            if (eval.trapArchetype() != null) {
                trapsSet.add(eval.trapArchetype());
            }

            int priorEvidence = historicalEvidenceCounts.getOrDefault(skillCode, 0);
            int totalEvidenceAfterDiagnostic = Math.max(1, priorEvidence + 1);
            ConfidenceTier itemConfidence = totalEvidenceAfterDiagnostic < SPARSE_EVIDENCE_MIN_ATTEMPTS
                    ? ConfidenceTier.PRELIMINARY
                    : ConfidenceTier.CALIBRATED;
            if (itemConfidence == ConfidenceTier.PRELIMINARY) {
                anyPreliminary = true;
            }

            double finalPrior = decayApplied ? applyExponentialDecayFloor(eval.prior(), effectiveInactiveDays) : eval.prior();
            priors.add(new ColdStartSkillPrior(
                    skillCode,
                    finalPrior,
                    totalEvidenceAfterDiagnostic,
                    itemConfidence,
                    decayApplied
            ));
        }

        BaselineTier baselineTier = resolveBaselineTier(correctCount);
        ConfidenceTier overallConfidence = anyPreliminary ? ConfidenceTier.PRELIMINARY : ConfidenceTier.CALIBRATED;

        boolean revalidationRequired = effectiveInactiveDays >= REVALIDATION_MIN_DAYS;
        DiagnosticStatus status = revalidationRequired
                ? DiagnosticStatus.REVALIDATION_REQUIRED
                : DiagnosticStatus.COMPLETED;
        CalibrationMode calibrationMode = revalidationRequired
                ? CalibrationMode.REVALIDATION_REQUIRED
                : (overallConfidence == ConfidenceTier.PRELIMINARY
                        ? CalibrationMode.EXPLORATION
                        : CalibrationMode.ACTIVE_CALIBRATED);

        boolean goalConflictResolved = "AYT".equals(examTarget)
                && declaredBudget != null
                && declaredBudget < AYT_CONFLICT_BUDGET_THRESHOLD;
        int recommendedDailyBudgetMinutes = declaredBudget != null ? declaredBudget : DEFAULT_DAILY_BUDGET_MINUTES;

        List<ColdStartRationale> rationales = buildRationales(
                locale,
                baselineTier,
                overallConfidence,
                goalConflictResolved,
                recommendedDailyBudgetMinutes,
                decayApplied,
                effectiveInactiveDays,
                explicitOverride,
                !trapsSet.isEmpty()
        );

        Instant now = clock.instant();
        StoredColdStartRecord record = new StoredColdStartRecord(
                UUID.randomUUID(),
                learnerId,
                ColdStartModels.SCHEMA_VERSION,
                idempotencyKey,
                locale,
                status,
                baselineTier,
                correctCount,
                CANONICAL_ITEM_IDS.size(),
                calibrationMode,
                overallConfidence,
                goalConflictResolved,
                recommendedDailyBudgetMinutes,
                decayApplied,
                explicitOverride,
                examTarget,
                declaredBudget,
                modality,
                revalidationRequired ? REVALIDATION_PROMPT_KEY : null,
                List.copyOf(trapsSet),
                List.copyOf(outcomes),
                List.copyOf(priors),
                List.copyOf(rationales),
                Math.min(totalElapsedSeconds, 1800),
                0,
                false,
                now,
                now
        );

        try {
            repository.insertDiagnostic(record);
        } catch (DataIntegrityViolationException conflict) {
            Optional<StoredColdStartRecord> concurrent = repository.findByLearnerIdAndIdempotencyKey(learnerId, idempotencyKey);
            if (concurrent.isPresent()) {
                return concurrent.get().toResponse(true);
            }
            throw conflict;
        }

        incrementCounter(
                "studyplan_cold_start_diagnostic_completed_total",
                "baseline_tier", baselineTier.name(),
                "confidence_tier", overallConfidence.name(),
                "calibration_mode", calibrationMode.name()
        );
        if (goalConflictResolved) {
            incrementCounter("studyplan_cold_start_goal_conflict_resolved_total", "exam_target", examTarget);
        }
        if (decayApplied) {
            incrementCounter("studyplan_cold_start_inactive_decay_applied_total", "revalidation", Boolean.toString(revalidationRequired));
        }

        LOG.info(
                "event=cold_start_diagnostic_completed diagnostic_id={} learner_id={} baseline_tier={} confidence_tier={} calibration_mode={} goal_conflict_resolved={} inactive_decay_applied={} explicit_override={} llm_tokens_used=0 training_eligible=false",
                record.diagnosticId(),
                learnerId,
                baselineTier,
                overallConfidence,
                calibrationMode,
                goalConflictResolved,
                decayApplied,
                explicitOverride
        );

        return record.toResponse(false);
    }

    private void requireFeatureEnabled() {
        if (!coldStartEnabled) {
            throw new ApiProblemException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.COLD_START_FEATURE_DISABLED,
                    "Cold-start baseline calibration feature is currently disabled.",
                    true,
                    "ENABLE_COLD_START_CALIBRATION"
            );
        }
    }

    private ApiProblemException invalidRequest(String message) {
        return new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.COLD_START_DIAGNOSTIC_INVALID,
                message,
                false,
                "FIX_COLD_START_REQUEST"
        );
    }

    private String normalizeLocale(String raw) {
        String value = raw == null ? "" : raw.trim();
        if ("tr".equalsIgnoreCase(value) || "tr-TR".equalsIgnoreCase(value)) {
            return "tr-TR";
        }
        if ("en".equalsIgnoreCase(value) || "en-US".equalsIgnoreCase(value)) {
            return "en-US";
        }
        if (!ALLOWED_LOCALES.contains(value)) {
            throw invalidRequest("locale must be 'en-US' or 'tr-TR'");
        }
        return value;
    }

    private String normalizeOptionalEnum(String raw, Set<String> allowed, String fieldName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw invalidRequest("Invalid value for " + fieldName + ": " + raw);
        }
        return normalized;
    }

    private Map<String, DiagnosticItemSubmissionRequest> validateAndIndexSubmissions(
            List<DiagnosticItemSubmissionRequest> responses
    ) {
        if (responses == null || responses.size() != CANONICAL_ITEM_IDS.size()) {
            throw invalidRequest("Exactly 3 canonical diagnostic responses are required.");
        }
        Map<String, DiagnosticItemSubmissionRequest> indexed = new LinkedHashMap<>();
        for (DiagnosticItemSubmissionRequest r : responses) {
            String itemId = r.itemId() != null ? r.itemId().trim() : "";
            if (!CANONICAL_ITEM_IDS.contains(itemId)) {
                throw invalidRequest("Unsupported diagnostic itemId: " + itemId);
            }
            if (indexed.containsKey(itemId)) {
                throw invalidRequest("Duplicate diagnostic itemId: " + itemId);
            }
            indexed.put(itemId, r);
        }
        for (String required : CANONICAL_ITEM_IDS) {
            if (!indexed.containsKey(required)) {
                throw invalidRequest("Missing canonical diagnostic itemId: " + required);
            }
        }
        return indexed;
    }

    private int resolveEffectiveInactiveDays(UUID learnerId, Integer overrideDays, Instant referenceCreatedAt) {
        if (overrideDays != null && overrideDays >= 0) {
            return overrideDays;
        }
        Optional<Instant> lastAt = repository.findLastVerifiedActivityAt(learnerId);
        Instant baselineAt = lastAt.orElse(referenceCreatedAt);
        if (baselineAt == null) {
            return 0;
        }
        long days = Duration.between(baselineAt, clock.instant()).toDays();
        return (int) Math.max(0L, days);
    }

    private ColdStartDiagnosticResponse applyInactivityProjection(StoredColdStartRecord record, int inactiveDays) {
        boolean revalidation = inactiveDays >= REVALIDATION_MIN_DAYS;
        List<ColdStartSkillPrior> decayedPriors = record.initializedPriors().stream()
                .map(prior -> new ColdStartSkillPrior(
                        prior.skillCode(),
                        applyExponentialDecayFloor(prior.priorProbability(), inactiveDays),
                        prior.evidenceCount(),
                        prior.confidenceTier(),
                        true
                ))
                .toList();
        List<ColdStartRationale> updatedRationales = new ArrayList<>(record.rationales());
        if (updatedRationales.stream().noneMatch(r -> r.code() == RationaleCode.INACTIVE_DECAY_APPLIED)) {
            updatedRationales.add(new ColdStartRationale(
                    RationaleCode.INACTIVE_DECAY_APPLIED,
                    localizedInactiveDecayMessage(record.locale(), inactiveDays)
            ));
        }
        return new ColdStartDiagnosticResponse(
                record.schemaVersion(),
                record.diagnosticId(),
                record.learnerId(),
                record.locale(),
                revalidation ? DiagnosticStatus.REVALIDATION_REQUIRED : record.status(),
                record.baselineTier(),
                record.correctCount(),
                record.totalItems(),
                revalidation ? CalibrationMode.REVALIDATION_REQUIRED : record.calibrationMode(),
                record.confidenceTier(),
                record.goalConflictResolved(),
                record.recommendedDailyBudgetMinutes(),
                true,
                record.explicitPreferenceOverride(),
                record.declaredExamTarget(),
                record.declaredDailyBudgetMinutes(),
                record.preferredModality(),
                revalidation ? REVALIDATION_PROMPT_KEY : record.revalidationPromptKey(),
                record.detectedTrapArchetypes(),
                record.itemOutcomes(),
                decayedPriors,
                List.copyOf(updatedRationales),
                record.attemptDurationSeconds(),
                0,
                false,
                false,
                record.createdAt(),
                record.updatedAt()
        );
    }

    static double applyExponentialDecayFloor(double currentPrior, int inactiveDays) {
        if (inactiveDays < INACTIVE_DECAY_MIN_DAYS) {
            return round4(clampPrior(currentPrior));
        }
        double decayed = BASE_PRIOR + (currentPrior - BASE_PRIOR) * Math.pow(2.0, -(inactiveDays / INACTIVE_DECAY_HALF_LIFE_DAYS));
        double floored = Math.max(BASE_PRIOR, decayed);
        return round4(Math.min(FAST_CORRECT_PRIOR, floored));
    }

    private static double clampPrior(double value) {
        return Math.max(TRAP_DISTRACTOR_PRIOR, Math.min(FAST_CORRECT_PRIOR, value));
    }

    private static double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private static String skillForItem(String itemId) {
        return switch (itemId) {
            case ITEM_NUMBER_SENSE -> SKILL_NUMBER_SENSE;
            case ITEM_QUADRATIC_INEQUALITY -> SKILL_QUADRATIC_INEQUALITY;
            case ITEM_FUNCTION_COMPOSITION -> SKILL_FUNCTION_COMPOSITION;
            default -> throw new IllegalArgumentException("Unknown itemId: " + itemId);
        };
    }

    private record EvaluationResult(boolean correct, ItemVerdict verdict, String trapArchetype, double prior) {
    }

    private EvaluationResult evaluateItem(String itemId, DiagnosticItemSubmissionRequest sub) {
        String candidateRaw = (sub.answer() != null && !sub.answer().isBlank())
                ? sub.answer()
                : sub.selectedOptionId();
        String cleaned = cleanMathToken(candidateRaw);

        if (sub.timedOut() || sub.elapsedSeconds() > SLOW_THRESHOLD_SECONDS) {
            return new EvaluationResult(false, ItemVerdict.TIMED_OUT, null, OTHER_INCORRECT_PRIOR);
        }
        if (sub.skipped() || cleaned.isEmpty()) {
            return new EvaluationResult(false, ItemVerdict.SKIPPED, null, OTHER_INCORRECT_PRIOR);
        }

        double correctPrior = sub.elapsedSeconds() <= FAST_THRESHOLD_SECONDS
                ? FAST_CORRECT_PRIOR
                : SLOW_CORRECT_PRIOR;
        ItemVerdict correctVerdict = sub.elapsedSeconds() <= FAST_THRESHOLD_SECONDS
                ? ItemVerdict.CORRECT_FAST
                : ItemVerdict.CORRECT_SLOW;

        return switch (itemId) {
            case ITEM_NUMBER_SENSE -> evaluateNumberSense(cleaned, correctVerdict, correctPrior);
            case ITEM_QUADRATIC_INEQUALITY -> evaluateQuadraticInequality(cleaned, correctVerdict, correctPrior);
            case ITEM_FUNCTION_COMPOSITION -> evaluateFunctionComposition(cleaned, correctVerdict, correctPrior);
            default -> throw invalidRequest("Unsupported diagnostic itemId: " + itemId);
        };
    }

    private EvaluationResult evaluateNumberSense(String cleaned, ItemVerdict correctVerdict, double correctPrior) {
        if ("A".equals(cleaned) || isRationalEquivalent(cleaned, 23, 20)) {
            return new EvaluationResult(true, correctVerdict, null, correctPrior);
        }
        if ("B".equals(cleaned) || isRationalEquivalent(cleaned, 7, 20)) {
            return new EvaluationResult(false, ItemVerdict.TRAP_DISTRACTOR, "SIGN_INVERSION_TRAP", TRAP_DISTRACTOR_PRIOR);
        }
        if ("C".equals(cleaned)
                || isRationalEquivalent(cleaned, 5, 9)
                || isRationalEquivalent(cleaned, 1, 9)
                || isRationalEquivalent(cleaned, 1, 1)) {
            return new EvaluationResult(false, ItemVerdict.TRAP_DISTRACTOR, "FRACTION_DENOMINATOR_TRAP", TRAP_DISTRACTOR_PRIOR);
        }
        return new EvaluationResult(false, ItemVerdict.INCORRECT_OTHER, null, OTHER_INCORRECT_PRIOR);
    }

    private EvaluationResult evaluateQuadraticInequality(String cleaned, ItemVerdict correctVerdict, double correctPrior) {
        if ("A".equals(cleaned) || "[2,3]".equals(cleaned) || "2<=X<=3".equals(cleaned) || "XIN[2,3]".equals(cleaned)) {
            return new EvaluationResult(true, correctVerdict, null, correctPrior);
        }
        if ("B".equals(cleaned)
                || "(-INF,2]U[3,INF)".equals(cleaned)
                || "(-INFINITY,2]U[3,INFINITY)".equals(cleaned)
                || "X<=2ORX>=3".equals(cleaned)) {
            return new EvaluationResult(false, ItemVerdict.TRAP_DISTRACTOR, "INEQUALITY_POLARITY_TRAP", TRAP_DISTRACTOR_PRIOR);
        }
        if ("C".equals(cleaned) || "(2,3)".equals(cleaned) || "2<X<3".equals(cleaned) || "{2,3}".equals(cleaned)) {
            return new EvaluationResult(false, ItemVerdict.TRAP_DISTRACTOR, "ROOT_BOUNDARY_TRAP", TRAP_DISTRACTOR_PRIOR);
        }
        return new EvaluationResult(false, ItemVerdict.INCORRECT_OTHER, null, OTHER_INCORRECT_PRIOR);
    }

    private EvaluationResult evaluateFunctionComposition(String cleaned, ItemVerdict correctVerdict, double correctPrior) {
        if ("A".equals(cleaned) || isRationalEquivalent(cleaned, 21, 1)) {
            return new EvaluationResult(true, correctVerdict, null, correctPrior);
        }
        if ("B".equals(cleaned) || isRationalEquivalent(cleaned, 81, 1)) {
            return new EvaluationResult(false, ItemVerdict.TRAP_DISTRACTOR, "COMPOSITION_ORDER_TRAP", TRAP_DISTRACTOR_PRIOR);
        }
        if ("C".equals(cleaned) || isRationalEquivalent(cleaned, 24, 1) || isRationalEquivalent(cleaned, 18, 1)) {
            return new EvaluationResult(false, ItemVerdict.TRAP_DISTRACTOR, "COMPOSITION_LINEARITY_TRAP", TRAP_DISTRACTOR_PRIOR);
        }
        return new EvaluationResult(false, ItemVerdict.INCORRECT_OTHER, null, OTHER_INCORRECT_PRIOR);
    }

    private static String cleanMathToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.trim()
                .replaceAll("\\\\(?:d|t)?frac\\{([^{}]+)\\}\\{([^{}]+)\\}", "$1/$2")
                .replace("\\left", "")
                .replace("\\right", "")
                .replace("\\cup", "U")
                .replace("\\infty", "INF")
                .replace("\\infinity", "INF")
                .replace("∞", "INF")
                .replace("\\leq", "<=")
                .replace("\\le", "<=")
                .replace("≤", "<=")
                .replace("\\geq", ">=")
                .replace("\\ge", ">=")
                .replace("≥", ">=")
                .replace("\\in", "IN")
                .replace("$", "")
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
        if (text.startsWith("XIN") && text.length() > 3 && (text.charAt(3) == '[' || text.charAt(3) == '(')) {
            text = text.substring(3);
        }
        return text;
    }

    private static boolean isRationalEquivalent(String cleaned, long targetNum, long targetDen) {
        String unwrapped = cleaned.replace("(", "").replace(")", "");
        try {
            if (unwrapped.matches("[+-]?\\d+/\\d+")) {
                String[] parts = unwrapped.split("/", 2);
                long num = Long.parseLong(parts[0]);
                long den = Long.parseLong(parts[1]);
                if (den == 0) {
                    return false;
                }
                return num * targetDen == targetNum * den;
            }
            if (unwrapped.matches("[+-]?\\d+(?:\\.\\d+)?")) {
                BigDecimal candidate = new BigDecimal(unwrapped);
                BigDecimal target = BigDecimal.valueOf(targetNum).divide(BigDecimal.valueOf(targetDen), 6, RoundingMode.HALF_UP);
                return candidate.subtract(target).abs().compareTo(new BigDecimal("0.0001")) <= 0;
            }
        } catch (NumberFormatException ignored) {
            return false;
        }
        return false;
    }

    private static BaselineTier resolveBaselineTier(int correctCount) {
        if (correctCount == 3) {
            return BaselineTier.FOUNDATION_SOLID;
        }
        if (correctCount >= 1) {
            return BaselineTier.CALIBRATED_MIXED;
        }
        return BaselineTier.FOUNDATION_REPAIR;
    }

    private List<ColdStartRationale> buildRationales(
            String locale,
            BaselineTier baselineTier,
            ConfidenceTier confidenceTier,
            boolean goalConflictResolved,
            int budgetMinutes,
            boolean decayApplied,
            int inactiveDays,
            boolean explicitOverride,
            boolean hasTraps
    ) {
        boolean tr = "tr-TR".equals(locale);
        List<ColdStartRationale> rationales = new ArrayList<>();
        if (confidenceTier == ConfidenceTier.PRELIMINARY) {
            rationales.add(new ColdStartRationale(
                    RationaleCode.PRELIMINARY_SPARSE_EVIDENCE,
                    tr
                            ? "İlk üç doğrulanmış çözüm tamamlanana kadar başlangıç kalibrasyonu keşif modunda tutulur."
                            : "Preliminary calibration is active while verified evidence builds across your first three attempts per skill."
            ));
        }
        if (goalConflictResolved) {
            rationales.add(new ColdStartRationale(
                    RationaleCode.GOAL_CAPACITY_CONFLICT_RESOLVED,
                    tr
                            ? "AYT hedefin günlük " + budgetMinutes + " dakikalık tek odaklı çalışma bloklarına dengelendi."
                            : "Your AYT goal has been paced into focused single-skill " + budgetMinutes + "-minute daily blocks so momentum stays sustainable."
            ));
        }
        if (decayApplied) {
            rationales.add(new ColdStartRationale(
                    RationaleCode.INACTIVE_DECAY_APPLIED,
                    localizedInactiveDecayMessage(locale, inactiveDays)
            ));
        }
        if (explicitOverride) {
            rationales.add(new ColdStartRationale(
                    RationaleCode.EXPLICIT_PREFERENCE_OVERRIDE,
                    tr
                            ? "Seçtiğin çalışma biçimi ve günlük süre tercihin planına öncelikli olarak uygulandı."
                            : "Applied your selected study modality and daily time budget preference."
            ));
        }
        if (hasTraps) {
            rationales.add(new ColdStartRationale(
                    RationaleCode.TRAP_SEEDED_WARMUP,
                    tr
                            ? "İşaret ve adım sınırlarını korumak için kısa bir ısınma kontrolü planına eklendi."
                            : "Added a focused sign-check and boundary warmup to strengthen step accuracy."
            ));
        }
        if (rationales.isEmpty()) {
            if (baselineTier == BaselineTier.FOUNDATION_SOLID) {
                rationales.add(new ColdStartRationale(
                        RationaleCode.BASELINE_SOLID_ACCELERATED,
                        tr
                                ? "Güçlü temel saptandı; planın doğrudan orta-ileri becerilere yönlendirildi."
                                : "Strong foundation observed; your plan opens directly with progressive skill synthesis."
                ));
            } else {
                rationales.add(new ColdStartRationale(
                        RationaleCode.BASELINE_REPAIR_FOUNDATION,
                        tr
                                ? "Adım adım güven oluşturan temel pekiştirme akışıyla başlıyoruz."
                                : "Starting with a guided foundational flow to build steady step confidence."
                ));
            }
        }
        return rationales;
    }

    private static String localizedInactiveDecayMessage(String locale, int inactiveDays) {
        if ("tr-TR".equals(locale)) {
            return inactiveDays + " günlük aranın ardından kalıcılık tahmini taban düzeye göre güncellendi; kısa bir tazeleme kontrolü önerilir.";
        }
        return "Retention estimates adjusted toward baseline after " + inactiveDays + " days away; a quick warmup revalidation is ready.";
    }

    private void incrementCounter(String name, String... tags) {
        if (meterRegistry != null) {
            Counter.builder(name).tags(tags).register(meterRegistry).increment();
        }
    }
}
