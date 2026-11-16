package com.verifiedai.exam.application;

import com.verifiedai.exam.domain.DeterministicExamScoringPolicy;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamScoreRegradeAudit;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSessionItem;
import com.verifiedai.exam.domain.ExamSessionItemEvaluation;
import com.verifiedai.exam.domain.ExamSessionScoreSnapshot;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.exam.domain.QuestionBankItem;
import com.verifiedai.exam.domain.QuestionBankRepository;
import com.verifiedai.exam.domain.ScoreRegradeReasonCode;
import com.verifiedai.exam.domain.TopicScoreBreakdown;
import com.verifiedai.exam.events.ExamScoreComputedEvent;
import com.verifiedai.exam.events.ExamScoreComputedEvent.TopicScoreSummary;
import com.verifiedai.exam.infrastructure.persistence.ExamScoringRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamScoringEngineService {

    private static final Logger LOG = LoggerFactory.getLogger(ExamScoringEngineService.class);

    public record ScoreSummaryResult(
            boolean newlyComputed,
            ExamSessionScoreSnapshot snapshot
    ) {
    }

    public record RegradeResult(
            ExamSessionScoreSnapshot snapshot,
            ExamScoreRegradeAudit audit
    ) {
    }

    private final ExamScoringRepository scoringRepository;
    private final ExamSessionRepository sessionRepository;
    private final ExamRepository examRepository;
    private final QuestionBankRepository questionBankRepository;
    private final DeterministicExamScoringPolicy scoringPolicy;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private volatile boolean scoringEngineEnabled;

    private final Counter computedCounter;
    private final Counter regradeCounter;
    private final Counter bolaDeniedCounter;
    private final Counter activeSessionRejectedCounter;
    private final Timer scoringLatencyTimer;

    @Autowired
    public ExamScoringEngineService(
            ExamScoringRepository scoringRepository,
            ExamSessionRepository sessionRepository,
            ExamRepository examRepository,
            QuestionBankRepository questionBankRepository,
            DeterministicExamScoringPolicy scoringPolicy,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<Clock> clockProvider,
            @Value("${verifiedai.exam.scoring-engine-enabled:false}") boolean scoringEngineEnabled
    ) {
        this.scoringRepository = Objects.requireNonNull(scoringRepository, "scoringRepository cannot be null");
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository cannot be null");
        this.examRepository = Objects.requireNonNull(examRepository, "examRepository cannot be null");
        this.questionBankRepository = Objects.requireNonNull(questionBankRepository, "questionBankRepository cannot be null");
        this.scoringPolicy = Objects.requireNonNull(scoringPolicy, "scoringPolicy cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.meterRegistry = meterRegistryProvider != null
                ? meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new)
                : new SimpleMeterRegistry();
        this.clock = clockProvider != null
                ? clockProvider.getIfAvailable(Clock::systemUTC)
                : Clock.systemUTC();
        this.scoringEngineEnabled = scoringEngineEnabled;

        this.computedCounter = Counter.builder("verifiedai.exam.scoring.computed.total")
                .description("Total deterministic exam score snapshots computed")
                .register(this.meterRegistry);
        this.regradeCounter = Counter.builder("verifiedai.exam.scoring.regrade.total")
                .description("Total audited exam session regrades executed")
                .register(this.meterRegistry);
        this.bolaDeniedCounter = Counter.builder("verifiedai.exam.scoring.bola.denied.total")
                .description("Total cross-learner BOLA attempts rejected on exam scoring endpoints")
                .register(this.meterRegistry);
        this.activeSessionRejectedCounter = Counter.builder("verifiedai.exam.scoring.active_session.rejected.total")
                .description("Total scoring requests rejected because session is not in scorable terminal state")
                .register(this.meterRegistry);
        this.scoringLatencyTimer = Timer.builder("verifiedai.exam.scoring.latency")
                .description("Latency of deterministic exam session scoring")
                .register(this.meterRegistry);
    }

    public boolean isScoringEngineEnabled() {
        return scoringEngineEnabled;
    }

    public void setScoringEngineEnabled(boolean scoringEngineEnabled) {
        this.scoringEngineEnabled = scoringEngineEnabled;
    }

    @Transactional
    public ScoreSummaryResult computeOrGetScoreSummary(UUID authenticatedUserId, UUID sessionId) {
        requireFeatureEnabled();
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");

        ExamSession session = loadOwnedScorableSession(authenticatedUserId, sessionId);

        Optional<ExamSessionScoreSnapshot> existing = scoringRepository.findScoreSnapshotBySessionId(sessionId);
        if (existing.isPresent()) {
            return new ScoreSummaryResult(false, existing.get());
        }

        Timer.Sample sample = Timer.start(meterRegistry);
        ExamDefinition definition = resolveExamDefinition(session);
        Map<UUID, QuestionBankItem> questionsById = loadQuestionBankItems(session);

        Instant now = clock.instant();
        Instant submittedAt = session.submittedAt() != null ? session.submittedAt() : now;

        ExamSessionScoreSnapshot computed = scoringPolicy.scoreSession(
                UUID.randomUUID(),
                session,
                definition,
                questionsById,
                1,
                submittedAt,
                now,
                null
        );

        ExamSessionScoreSnapshot saved = scoringRepository.saveInitialScoreSnapshot(computed);
        sample.stop(scoringLatencyTimer);
        computedCounter.increment();

        publishScoreComputedEvent(saved, false, now);
        LOG.info(
                "exam.score.computed sessionId={} examCode={} netScore={} correct={} wrong={} blank={} tokens=0 costUsd=0.00",
                saved.sessionId(),
                saved.examCode(),
                saved.netScore(),
                saved.correctCount(),
                saved.wrongCount(),
                saved.blankCount()
        );
        return new ScoreSummaryResult(true, saved);
    }

    @Transactional(readOnly = true)
    public ExamSessionScoreSnapshot getScoreSummary(UUID authenticatedUserId, UUID sessionId) {
        requireFeatureEnabled();
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");

        loadOwnedScorableSession(authenticatedUserId, sessionId);
        return scoringRepository.findScoreSnapshotBySessionId(sessionId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_SCORE_NOT_FOUND,
                        "Score summary not found for exam session: " + sessionId,
                        true,
                        "TRIGGER_POST_SCORE_SUMMARY"
                ));
    }

    @Transactional
    public RegradeResult regradeSession(
            UUID authenticatedUserId,
            UUID sessionId,
            ScoreRegradeReasonCode reasonCode,
            String reasonDetail,
            String triggeredBy
    ) {
        requireFeatureEnabled();
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");

        validateRegradeParameters(reasonCode, reasonDetail, triggeredBy);

        ExamSession session = loadOwnedScorableSession(authenticatedUserId, sessionId);
        ExamSessionScoreSnapshot previous = scoringRepository.findScoreSnapshotBySessionId(sessionId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_SCORE_NOT_FOUND,
                        "Cannot regrade session without an initial score snapshot: " + sessionId,
                        true,
                        "COMPUTE_INITIAL_SCORE_FIRST"
                ));

        Timer.Sample sample = Timer.start(meterRegistry);
        ExamDefinition definition = resolveExamDefinition(session);
        Map<UUID, QuestionBankItem> questionsById = loadQuestionBankItems(session);
        Instant now = clock.instant();

        int newVersion = previous.scoreVersion() + 1;
        ExamSessionScoreSnapshot recomputed = scoringPolicy.scoreSession(
                previous.scoreId(),
                session,
                definition,
                questionsById,
                newVersion,
                previous.sessionSubmittedAt(),
                now,
                now
        ).forRegrade(newVersion, previous.scoredAt(), now);

        Map<String, Object> deltaSummary = buildDeltaSummary(previous, recomputed);

        ExamScoreRegradeAudit audit = new ExamScoreRegradeAudit(
                UUID.randomUUID(),
                previous.scoreId(),
                session.id(),
                session.userId(),
                previous.scoreVersion(),
                newVersion,
                previous.netScore(),
                recomputed.netScore(),
                previous.correctCount(),
                recomputed.correctCount(),
                previous.wrongCount(),
                recomputed.wrongCount(),
                previous.blankCount(),
                recomputed.blankCount(),
                reasonCode,
                reasonDetail.trim(),
                triggeredBy.trim(),
                deltaSummary,
                false,
                now
        );

        ExamSessionScoreSnapshot saved = scoringRepository.updateRegradedScoreSnapshot(recomputed, audit);
        sample.stop(scoringLatencyTimer);
        regradeCounter.increment();

        publishScoreComputedEvent(saved, true, now);
        LOG.info(
                "exam.score.regraded sessionId={} oldVersion={} newVersion={} previousNet={} newNet={} reasonCode={}",
                saved.sessionId(),
                previous.scoreVersion(),
                newVersion,
                previous.netScore(),
                saved.netScore(),
                reasonCode
        );
        return new RegradeResult(saved, audit);
    }

    @Transactional(readOnly = true)
    public List<ExamScoreRegradeAudit> listRegradeAudits(UUID authenticatedUserId, UUID sessionId) {
        requireFeatureEnabled();
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");

        loadOwnedScorableSession(authenticatedUserId, sessionId);
        return scoringRepository.findRegradeAuditsBySessionId(sessionId);
    }

    private ExamSession loadOwnedScorableSession(UUID authenticatedUserId, UUID sessionId) {
        ExamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_SESSION_NOT_FOUND,
                        "Exam session not found: " + sessionId,
                        false,
                        "VERIFY_SESSION_ID"
                ));

        if (!session.userId().equals(authenticatedUserId)) {
            bolaDeniedCounter.increment();
            LOG.warn("BOLA blocked on scoring endpoint: requester={} owner={} sessionId={}",
                    authenticatedUserId, session.userId(), sessionId);
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_SESSION_FORBIDDEN,
                    "Cross-learner access to exam session score is forbidden",
                    false,
                    "USE_OWN_LEARNER_ACCOUNT"
            );
        }

        if (session.status() != ExamSessionStatus.SUBMITTED
                && session.status() != ExamSessionStatus.EXPIRED_AUTO_SUBMITTED) {
            activeSessionRejectedCounter.increment();
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_SESSION_NOT_SCORABLE,
                    "Exam session in status " + session.status()
                            + " cannot be scored or reveal answer keys (INV-EXAM-003)",
                    false,
                    "SUBMIT_EXAM_SESSION_FIRST"
            );
        }

        return session;
    }

    private ExamDefinition resolveExamDefinition(ExamSession session) {
        return examRepository.findById(session.blueprintId())
                .or(() -> examRepository.findLatestByCode(session.examCode(), true))
                .orElseThrow(() -> new ApiProblemException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.EXAM_DEFINITION_NOT_FOUND,
                        "Exam definition not found for session blueprintId=" + session.blueprintId()
                                + " examCode=" + session.examCode(),
                        false,
                        "VERIFY_EXAM_DEFINITION"
                ));
    }

    private Map<UUID, QuestionBankItem> loadQuestionBankItems(ExamSession session) {
        Map<UUID, QuestionBankItem> itemsById = new LinkedHashMap<>();
        for (ExamSessionItem sessionItem : session.items()) {
            UUID qId = sessionItem.questionItemId();
            if (itemsById.containsKey(qId)) {
                continue;
            }
            QuestionBankItem question = questionBankRepository.findById(qId)
                    .orElseThrow(() -> new ApiProblemException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            ApiErrorCode.EXAM_QUESTION_ITEM_NOT_FOUND,
                            "Referenced question bank item missing: " + qId,
                            false,
                            "RESTORE_QUESTION_BANK_ITEM"
                    ));
            itemsById.put(qId, question);
        }
        return itemsById;
    }

    private static Map<String, Object> buildDeltaSummary(
            ExamSessionScoreSnapshot previous,
            ExamSessionScoreSnapshot updated
    ) {
        Map<Integer, ExamSessionItemEvaluation> prevByPos = previous.itemEvaluations().stream()
                .collect(Collectors.toMap(ExamSessionItemEvaluation::positionIndex, Function.identity(), (a, b) -> a));

        List<Integer> changedPositions = new ArrayList<>();
        for (ExamSessionItemEvaluation curr : updated.itemEvaluations()) {
            ExamSessionItemEvaluation prev = prevByPos.get(curr.positionIndex());
            if (prev == null
                    || prev.evaluationOutcome() != curr.evaluationOutcome()
                    || prev.netPointsContribution().compareTo(curr.netPointsContribution()) != 0
                    || !Objects.equals(prev.correctOptionKey(), curr.correctOptionKey())
                    || !Objects.equals(prev.expectedNumericAnswer(), curr.expectedNumericAnswer())) {
                changedPositions.add(curr.positionIndex());
            }
        }

        BigDecimal netDelta = updated.netScore().subtract(previous.netScore()).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("netScoreDelta", netDelta);
        summary.put("correctCountDelta", updated.correctCount() - previous.correctCount());
        summary.put("wrongCountDelta", updated.wrongCount() - previous.wrongCount());
        summary.put("blankCountDelta", updated.blankCount() - previous.blankCount());
        summary.put("changedPositionIndices", changedPositions);
        return summary;
    }

    private void publishScoreComputedEvent(ExamSessionScoreSnapshot snapshot, boolean isRegrade, Instant occurredAt) {
        List<TopicScoreSummary> topicSummaries = snapshot.topicBreakdowns().stream()
                .map(tb -> new TopicScoreSummary(
                        tb.topicCode(),
                        tb.topicTitle(),
                        tb.totalQuestions(),
                        tb.correctCount(),
                        tb.wrongCount(),
                        tb.blankCount(),
                        tb.netScore(),
                        tb.accuracyPercentage(),
                        tb.totalTimeSpentSeconds()
                ))
                .toList();

        eventPublisher.publishEvent(new ExamScoreComputedEvent(
                UUID.randomUUID(),
                snapshot.scoreId(),
                snapshot.sessionId(),
                snapshot.userId(),
                snapshot.examDefinitionId(),
                snapshot.assembledAssessmentId(),
                snapshot.examCode(),
                snapshot.scoreVersion(),
                isRegrade,
                snapshot.scoringFormulaType().name(),
                snapshot.totalQuestions(),
                snapshot.correctCount(),
                snapshot.wrongCount(),
                snapshot.blankCount(),
                snapshot.netScore(),
                snapshot.normalizedPercentageScore(),
                snapshot.accuracyPercentage(),
                topicSummaries,
                0,
                false,
                occurredAt
        ));
    }

    private void requireFeatureEnabled() {
        if (!scoringEngineEnabled) {
            throw new ApiProblemException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.EXAM_SCORING_FEATURE_DISABLED,
                    "Exam scoring engine feature is currently disabled",
                    true,
                    "TRY_AGAIN_LATER"
            );
        }
    }

    private static void validateRegradeParameters(
            ScoreRegradeReasonCode reasonCode,
            String reasonDetail,
            String triggeredBy
    ) {
        if (reasonCode == null) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.EXAM_REGRADE_INVALID_REQUEST,
                    "reasonCode is required for exam session regrade",
                    true,
                    "PROVIDE_REGRADE_REASON_CODE"
            );
        }
        if (reasonDetail == null || reasonDetail.isBlank() || reasonDetail.trim().length() > 500) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.EXAM_REGRADE_INVALID_REQUEST,
                    "reasonDetail must be non-blank and at most 500 characters",
                    true,
                    "PROVIDE_REGRADE_REASON_DETAIL"
            );
        }
        if (triggeredBy == null || triggeredBy.isBlank() || triggeredBy.trim().length() > 120) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.EXAM_REGRADE_INVALID_REQUEST,
                    "triggeredBy must be non-blank and at most 120 characters",
                    true,
                    "PROVIDE_REGRADE_ACTOR"
            );
        }
    }
}
