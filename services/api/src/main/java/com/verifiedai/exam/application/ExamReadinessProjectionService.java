package com.verifiedai.exam.application;

import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileRecord;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetRecord;
import com.verifiedai.exam.domain.ExamModels.TargetStatus;
import com.verifiedai.exam.domain.ExamReadinessCalculator;
import com.verifiedai.exam.domain.ExamReadinessCalculator.ActiveTrapSignal;
import com.verifiedai.exam.domain.ExamReadinessCalculator.CalculationInput;
import com.verifiedai.exam.domain.ExamReadinessCalculator.MockScoreSignal;
import com.verifiedai.exam.domain.ExamReadinessCalculator.SkillMasterySignal;
import com.verifiedai.exam.domain.ExamReadinessProjection;
import com.verifiedai.exam.domain.ExamReadinessRepository;
import com.verifiedai.exam.domain.ExamSessionScoreSnapshot;
import com.verifiedai.exam.events.ExamReadinessComputedEvent;
import com.verifiedai.exam.infrastructure.persistence.ExamScoringRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service computing and persisting deterministic Exam Readiness Projections
 * with 90% credible intervals, active-trap penalty attribution, and sparse-evidence
 * PRELIMINARY guard (INV-EXAM-001, INV-EXAM-004, ADR-006, ADR-011).
 */
@Service
public class ExamReadinessProjectionService {

    private static final Logger LOG = LoggerFactory.getLogger(ExamReadinessProjectionService.class);
    public static String canonicalExamCode(String code) {
        if (code == null) return null;
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return EXAM_CODE_ALIASES.getOrDefault(normalized, normalized);
    }

    private static final Map<String, String> EXAM_CODE_ALIASES = Map.of(
            "YKS_TYT", "TR_YKS_TYT_MATH",
            "YKS_TYT_MATH", "TR_YKS_TYT_MATH",
            "YKS_AYT_SAY", "TR_YKS_AYT_MATH",
            "YKS_AYT", "TR_YKS_AYT_MATH",
            "YKS_AYT_MATH", "TR_YKS_AYT_MATH",
            "SAT_MATH", "INTL_SAT_MATH",
            "AP_CALC_BC", "INTL_AP_CALC_BC",
            "IB_MATH_AA_HL", "INTL_IB_MATH_AA_HL"
    );

    private final ExamReadinessRepository readinessRepository;
    private final ExamReadinessSignalReader signalReader;
    private final ExamRepository examRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final ExamScoringRepository examScoringRepository;
    private final ExamReadinessCalculator calculator;
    private final ApplicationEventPublisher eventPublisher;
    private final ExamProperties examProperties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    private final Counter computedCounter;
    private final Counter cacheHitCounter;
    private final Counter bolaDeniedCounter;
    private final Timer computationLatencyTimer;

    @Autowired
    public ExamReadinessProjectionService(
            ExamReadinessRepository readinessRepository,
            ExamReadinessSignalReader signalReader,
            ExamRepository examRepository,
            ObjectProvider<CandidateProfileRepository> candidateProfileRepositoryProvider,
            ObjectProvider<ExamScoringRepository> examScoringRepositoryProvider,
            ExamReadinessCalculator calculator,
            ApplicationEventPublisher eventPublisher,
            ExamProperties examProperties,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<Clock> clockProvider
    ) {
        this(
                readinessRepository,
                signalReader,
                examRepository,
                candidateProfileRepositoryProvider != null ? candidateProfileRepositoryProvider.getIfAvailable() : null,
                examScoringRepositoryProvider != null ? examScoringRepositoryProvider.getIfAvailable() : null,
                calculator,
                eventPublisher,
                examProperties,
                meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new) : new SimpleMeterRegistry(),
                clockProvider != null ? clockProvider.getIfAvailable(Clock::systemUTC) : Clock.systemUTC()
        );
    }

    public ExamReadinessProjectionService(
            ExamReadinessRepository readinessRepository,
            ExamReadinessSignalReader signalReader,
            ExamRepository examRepository,
            CandidateProfileRepository candidateProfileRepository,
            ExamScoringRepository examScoringRepository,
            ExamReadinessCalculator calculator,
            ApplicationEventPublisher eventPublisher,
            ExamProperties examProperties,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.readinessRepository = Objects.requireNonNull(readinessRepository, "readinessRepository cannot be null");
        this.signalReader = Objects.requireNonNull(signalReader, "signalReader cannot be null");
        this.examRepository = Objects.requireNonNull(examRepository, "examRepository cannot be null");
        this.candidateProfileRepository = candidateProfileRepository;
        this.examScoringRepository = examScoringRepository;
        this.calculator = Objects.requireNonNull(calculator, "calculator cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.examProperties = Objects.requireNonNull(examProperties, "examProperties cannot be null");
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.clock = clock != null ? clock : Clock.systemUTC();

        this.computedCounter = Counter.builder("verifiedai.exam.readiness.computed.total")
                .description("Total deterministic exam readiness projections computed")
                .register(this.meterRegistry);
        this.cacheHitCounter = Counter.builder("verifiedai.exam.readiness.cache_hit.total")
                .description("Total persisted exam readiness projections served without recompute")
                .register(this.meterRegistry);
        this.bolaDeniedCounter = Counter.builder("verifiedai.exam.readiness.bola.denied.total")
                .description("Total cross-learner BOLA attempts rejected on exam readiness endpoints")
                .register(this.meterRegistry);
        this.computationLatencyTimer = Timer.builder("verifiedai.exam.readiness.latency")
                .description("Latency of deterministic exam readiness calculation")
                .register(this.meterRegistry);
    }

    @Transactional
    public ExamReadinessProjection getOrComputeReadiness(
            UUID authenticatedUserId,
            UUID requestedUserId,
            String examCode,
            boolean forceRecompute
    ) {
        requireFeatureEnabled();
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId is required");

        if (requestedUserId != null && !requestedUserId.equals(authenticatedUserId)) {
            bolaDeniedCounter.increment();
            LOG.warn("BOLA blocked on exam readiness endpoint: requester={} requestedUserId={}",
                    authenticatedUserId, requestedUserId);
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_ACCESS_DENIED,
                    "Cross-learner access to exam readiness projection is forbidden",
                    false,
                    "USE_OWN_LEARNER_ACCOUNT"
            );
        }

        if (examCode == null || examCode.isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "examCode query or request parameter is required",
                    true,
                    "PROVIDE_EXAM_CODE"
            );
        }

        String normalizedCode = examCode.trim().toUpperCase(Locale.ROOT);
        ExamDefinition definition = resolveExamDefinition(normalizedCode);

        if (!forceRecompute) {
            Optional<ExamReadinessProjection> existing = readinessRepository
                    .findByUserIdAndExamCode(authenticatedUserId, definition.examCode());
            if (existing.isPresent()) {
                cacheHitCounter.increment();
                return existing.get();
            }
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        TargetSelection targetSelection = resolveTargetSelection(authenticatedUserId, definition.examCode());
        List<MockScoreSignal> recentMockScores = loadRecentMockScores(authenticatedUserId, definition);
        Map<String, SkillMasterySignal> masteryBySkill = signalReader.loadSkillMasterySignals(authenticatedUserId);
        List<ActiveTrapSignal> activeTraps = signalReader.loadActiveTrapSignals(authenticatedUserId);

        Instant now = clock.instant();
        ExamReadinessProjection computed = calculator.calculate(new CalculationInput(
                UUID.randomUUID(),
                authenticatedUserId,
                definition,
                targetSelection.targetId(),
                targetSelection.targetNetScore(),
                masteryBySkill,
                activeTraps,
                recentMockScores,
                now
        ));

        ExamReadinessProjection saved = readinessRepository.saveUpsert(computed);
        sample.stop(computationLatencyTimer);
        computedCounter.increment();

        eventPublisher.publishEvent(new ExamReadinessComputedEvent(
                saved.projectionId(),
                saved.userId(),
                saved.examCode(),
                saved.estimatedNetLow(),
                saved.estimatedNetMid(),
                saved.estimatedNetHigh(),
                saved.netLostToTraps(),
                saved.epistemicConfidence(),
                saved.confidenceTier(),
                saved.calibrationRequired(),
                false,
                saved.computedAt()
        ));

        LOG.info(
                "exam.readiness.computed examCode={} confidenceTier={} interval=[{}, {}, {}] netLostToTraps={} tokens=0 costUsd=0.00",
                saved.examCode(),
                saved.confidenceTier(),
                saved.estimatedNetLow(),
                saved.estimatedNetMid(),
                saved.estimatedNetHigh(),
                saved.netLostToTraps()
        );
        return saved;
    }

    private ExamDefinition resolveExamDefinition(String normalizedCode) {
        Optional<ExamDefinition> direct = examRepository.findLatestByCode(normalizedCode, false);
        if (direct.isPresent()) {
            return direct.get();
        }
        String alias = EXAM_CODE_ALIASES.get(normalizedCode);
        if (alias != null) {
            Optional<ExamDefinition> aliased = examRepository.findLatestByCode(alias, false);
            if (aliased.isPresent()) {
                return aliased.get();
            }
        }
        throw new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.EXAM_DEFINITION_NOT_FOUND,
                "Active exam definition not found for examCode=" + normalizedCode,
                false,
                "SELECT_SUPPORTED_EXAM"
        );
    }

    private record TargetSelection(UUID targetId, BigDecimal targetNetScore) {
    }

    private TargetSelection resolveTargetSelection(UUID userId, String canonicalExamCode) {
        List<StudentExamTargetRecord> targets = examRepository.findTargetsByUserId(userId);
        Optional<StudentExamTargetRecord> activeMatch = targets.stream()
                .filter(t -> t.targetStatus() == TargetStatus.ACTIVE && matchesExamCode(t.examCode(), canonicalExamCode))
                .findFirst();
        if (activeMatch.isPresent()) {
            StudentExamTargetRecord target = activeMatch.get();
            return new TargetSelection(target.targetId(), target.targetScore());
        }
        if (candidateProfileRepository != null) {
            Optional<CandidateProfileRecord> profileOpt = candidateProfileRepository.findProfileByUserId(userId);
            if (profileOpt.isPresent() && matchesExamCode(profileOpt.get().activeExamCode(), canonicalExamCode)) {
                return new TargetSelection(profileOpt.get().activeTargetId(), profileOpt.get().targetScore());
            }
        }
        return new TargetSelection(null, null);
    }

    private List<MockScoreSignal> loadRecentMockScores(UUID userId, ExamDefinition definition) {
        if (examScoringRepository == null) {
            return List.of();
        }
        int expectedDurationSeconds = Math.max(60, definition.durationMinutes() * 60);
        List<ExamSessionScoreSnapshot> snapshots = examScoringRepository.findLatestScoresByUserId(userId, 20);
        return snapshots.stream()
                .filter(s -> matchesExamCode(s.examCode(), definition.examCode()))
                .map(s -> new MockScoreSignal(
                        s.sessionId(),
                        s.netScore(),
                        s.totalQuestions(),
                        s.correctCount(),
                        s.wrongCount(),
                        s.blankCount(),
                        s.totalTimeSpentSeconds(),
                        expectedDurationSeconds,
                        s.scoredAt()
                ))
                .toList();
    }

    private static boolean matchesExamCode(String candidateCode, String canonicalExamCode) {
        if (candidateCode == null || canonicalExamCode == null) {
            return false;
        }
        String normCandidate = candidateCode.trim().toUpperCase(Locale.ROOT);
        String normCanonical = canonicalExamCode.trim().toUpperCase(Locale.ROOT);
        if (normCandidate.equals(normCanonical)) {
            return true;
        }
        String resolvedCandidate = EXAM_CODE_ALIASES.getOrDefault(normCandidate, normCandidate);
        String resolvedCanonical = EXAM_CODE_ALIASES.getOrDefault(normCanonical, normCanonical);
        return resolvedCandidate.equals(resolvedCanonical);
    }

    private void requireFeatureEnabled() {
        if (!examProperties.isReadinessEnabled()) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_READINESS_DISABLED,
                    "Exam readiness projection feature is currently disabled",
                    false,
                    "ENABLE_EXAM_READINESS"
            );
        }
    }
}
