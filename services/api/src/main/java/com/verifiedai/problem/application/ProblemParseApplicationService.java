package com.verifiedai.problem.application;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiModelGateway;
import com.verifiedai.ai.application.AiProblemNormalizeRequest;
import com.verifiedai.ai.application.AiProblemNormalizeResult;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.problem.domain.model.ProblemParseJobStatus;
import com.verifiedai.problem.domain.model.ProblemParseSource;
import com.verifiedai.problem.domain.model.ProblemParseSupportStatus;
import com.verifiedai.problem.domain.model.ProblemSessionStatus;
import com.verifiedai.problem.infrastructure.parser.ProblemParserProperties;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJobJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemParseJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemSessionJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemSessionJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.RecognitionEvidenceJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.RecognitionEvidenceJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProblemParseApplicationService {
    private final ProblemSessionJpaRepository sessionRepository;
    private final RecognitionEvidenceJpaRepository evidenceRepository;
    private final ProblemParseJobJpaRepository parseJobRepository;
    private final ProblemParseJpaRepository parseRepository;
    private final AiModelGateway aiModelGateway;
    private final CapabilityAccessPolicy capabilityAccessPolicy;
    private final ProblemParseNormalizer parseNormalizer;
    private final ProblemParserProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final ProblemParseMetrics metrics;
    private final ProblemParseSelectionPolicy selectionPolicy;
    private final ProblemParseCorrectionMetrics correctionMetrics;
    private final ProblemSessionLifecyclePolicy lifecyclePolicy;
    private final ProblemSessionMetrics sessionMetrics;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("ParameterNumber")
    ProblemParseApplicationService(
        ProblemSessionJpaRepository sessionRepository,
        RecognitionEvidenceJpaRepository evidenceRepository,
        ProblemParseJobJpaRepository parseJobRepository,
        ProblemParseJpaRepository parseRepository,
        AiModelGateway aiModelGateway,
        CapabilityAccessPolicy capabilityAccessPolicy,
        ProblemParseNormalizer parseNormalizer,
        ProblemParserProperties properties,
        JdbcTemplate jdbcTemplate,
        Clock clock,
        ProblemParseMetrics metrics,
        ProblemParseSelectionPolicy selectionPolicy,
        ProblemParseCorrectionMetrics correctionMetrics,
        ProblemSessionLifecyclePolicy lifecyclePolicy,
        ProblemSessionMetrics sessionMetrics,
        TransactionTemplate transactionTemplate
    ) {
        this.sessionRepository = sessionRepository;
        this.evidenceRepository = evidenceRepository;
        this.parseJobRepository = parseJobRepository;
        this.parseRepository = parseRepository;
        this.aiModelGateway = aiModelGateway;
        this.capabilityAccessPolicy = capabilityAccessPolicy;
        this.parseNormalizer = parseNormalizer;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.metrics = metrics;
        this.selectionPolicy = selectionPolicy;
        this.correctionMetrics = correctionMetrics;
        this.lifecyclePolicy = lifecyclePolicy;
        this.sessionMetrics = sessionMetrics;
        this.transactionTemplate = transactionTemplate;
    }

    public ProblemParseStatusResult requestParse(UUID userId, UUID problemSessionId) {
        requireActiveAccount(userId);
        capabilityAccessPolicy.requireBasicSolve(userId);
        AiRoutePlan routePlan = aiModelGateway.routePlan(AiCapability.PROBLEM_NORMALIZE);
        ProblemParseJobJpaEntity job = transactionTemplate.execute(status -> {
            ProblemSessionJpaEntity session = sessionRepository.findByIdAndUserIdForUpdate(problemSessionId, userId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_FORBIDDEN, "Problem session was not found", false, "RETRY"));
            transitionToParsingIfNeeded(session, clock.instant());
            RecognitionEvidenceJpaEntity evidence = currentRecognitionEvidence(userId, problemSessionId);
            Optional<ProblemParseJobJpaEntity> existing = parseJobRepository
                .findByUserIdAndProblemSessionIdAndRecognitionEvidenceIdAndRecognitionEvidenceRevisionAndCapabilityAndPromptIdAndPromptVersionAndSchemaVersionAndRoutePolicyVersion(
                    userId,
                    problemSessionId,
                    evidence.id(),
                    evidence.revision(),
                    AiCapability.PROBLEM_NORMALIZE.name(),
                    routePlan.promptId(),
                    routePlan.promptVersion(),
                    routePlan.schemaVersion(),
                    routePlan.routePolicyVersion()
                );
            if (existing.isPresent()) {
                return existing.get();
            }
            ProblemParseJobJpaEntity created = ProblemParseJobJpaEntity.queued(
                UUID.randomUUID(),
                userId,
                problemSessionId,
                evidence.id(),
                evidence.revision(),
                routePlan.routePolicyVersion(),
                routePlan.promptId(),
                routePlan.promptVersion(),
                routePlan.schemaVersion(),
                routePlan.maxAttempts(),
                clock.instant()
            );
            metrics.started();
            return parseJobRepository.saveAndFlush(created);
        });
        return statusFor(job);
    }

    public ProblemParseStatusResult getParse(UUID userId, UUID problemSessionId) {
        requireActiveAccount(userId);
        sessionRepository.findByIdAndUserId(problemSessionId, userId)
            .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_FORBIDDEN, "Problem session was not found", false, "RETRY"));
        return parseJobRepository.findFirstByProblemSessionIdAndUserIdOrderByCreatedAtDesc(problemSessionId, userId)
            .map(this::statusFor)
            .orElseGet(() -> ProblemParseStatusResult.notStarted(problemSessionId));
    }

    public int runDueParseJobs(int limit) {
        recoverStaleRunningJobs(limit);
        List<UUID> jobIds = parseJobRepository.findDueJobIds(clock.instant(), PageRequest.of(0, limit));
        int completed = 0;
        for (UUID jobId : jobIds) {
            if (runParseJob(jobId)) {
                completed += 1;
            }
        }
        return completed;
    }

    public boolean runParseJob(UUID jobId) {
        long totalStarted = System.nanoTime();
        ParseExecutionContext context = startExecution(jobId);
        if (context == null) {
            return false;
        }
        try {
            AiProblemNormalizeResult aiResult = executeProvider(context);
            NormalizedProblemParse normalized = parseNormalizer.normalize(
                aiResult.rawOutputJson(),
                context.job().schemaVersion(),
                context.recognitionEvidence()
            );
            completeAccepted(context, aiResult, normalized, System.nanoTime() - totalStarted);
            return true;
        } catch (ProblemParseValidationException exception) {
            if (exception.failure() == ProblemParseValidationFailure.SCHEMA) {
                metrics.schemaInvalid();
                completeFailure(context, ApiErrorCode.PROBLEM_PARSE_FAILED, "SCHEMA_INVALID", true);
            } else {
                metrics.semanticInvalid();
                completeFailure(context, ApiErrorCode.PROBLEM_PARSE_FAILED, "SEMANTIC_INVALID", false);
            }
            return true;
        } catch (AiProviderException exception) {
            completeFailure(context, ApiErrorCode.PROBLEM_PARSE_FAILED, exception.failureClass().name(), exception.retryable());
            return true;
        }
    }

    private ParseExecutionContext startExecution(UUID jobId) {
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            ProblemParseJobJpaEntity job = parseJobRepository.findByIdForUpdate(jobId).orElse(null);
            if (job == null || !job.dueAt(now)) {
                return null;
            }
            int attemptNumber = job.markRunning(now);
            RecognitionEvidenceJpaEntity evidence = evidenceRepository.findByIdAndUserId(job.recognitionEvidenceId(), job.userId())
                .orElseThrow(() -> problem(HttpStatus.CONFLICT, ApiErrorCode.PROBLEM_PARSE_FAILED, "Recognition evidence is unavailable", true, "RECOGNIZE"));
            if (!evidence.problemSessionId().equals(job.problemSessionId()) || evidence.revision() != job.recognitionEvidenceRevision()) {
                throw problem(HttpStatus.CONFLICT, ApiErrorCode.PROBLEM_PARSE_FAILED, "Recognition evidence is not eligible for parsing", true, "RECOGNIZE");
            }
            return new ParseExecutionContext(job, evidence, attemptNumber);
        });
    }

    private AiProblemNormalizeResult executeProvider(ParseExecutionContext context) {
        AiRoutePlan routePlan = aiModelGateway.routePlan(AiCapability.PROBLEM_NORMALIZE);
        AiProblemNormalizeResult result = aiModelGateway.executeProblemNormalize(new AiProblemNormalizeRequest(
            context.job().problemSessionId(),
            context.recognitionEvidence().id(),
            context.recognitionEvidence().revision(),
            context.recognitionEvidence().normalizedEvidenceJson(),
            context.recognitionEvidence().upstreamQualityEvidenceJson(),
            context.job().promptId(),
            context.job().promptVersion(),
            context.job().schemaVersion(),
            routePlan.timeout()
        ));
        if (result.rawOutputJson().getBytes(StandardCharsets.UTF_8).length > routePlan.maxResponseBytes()) {
            throw new AiProviderException(AiProviderFailureClass.OUTPUT_TOO_LARGE, false, "Problem parser output exceeded response limit");
        }
        return result;
    }

    private void completeAccepted(
        ParseExecutionContext context,
        AiProblemNormalizeResult aiResult,
        NormalizedProblemParse normalized,
        long totalNanos
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            ProblemParseJobJpaEntity job = parseJobRepository.findByIdForUpdate(context.job().id())
                .orElseThrow();
            if (!ProblemParseJobStatus.RUNNING.name().equals(job.status()) || job.attemptCount() != context.attemptNumber()) {
                return;
            }
            if (parseRepository.findByParseJobId(job.id()).isEmpty()) {
                ProblemSessionJpaEntity session = sessionRepository.findByIdAndUserIdForUpdate(job.problemSessionId(), job.userId())
                    .orElseThrow();
                int revision = parseRepository.maxRevision(job.problemSessionId()) + 1;
                Instant now = clock.instant();
                ProblemParseJpaEntity saved = parseRepository.save(ProblemParseJpaEntity.fromAi(
                    UUID.randomUUID(),
                    job.id(),
                    job.userId(),
                    job.problemSessionId(),
                    job.recognitionEvidenceId(),
                    job.recognitionEvidenceRevision(),
                    revision,
                    normalized.supportStatus(),
                    normalized.unsupportedReason(),
                    normalized.reviewRequired(),
                    job.schemaVersion(),
                    normalized.rawOutputJson(),
                    normalized.normalizedProblemJson(),
                    aiResult.provenance(),
                    aiResult.usage(),
                    aiResult.providerLatencyMs(),
                    Math.max(0, totalNanos / 1_000_000),
                    null,
                    now
                ));
                boolean selected = !ProblemParseSupportStatus.UNSUPPORTED.name().equals(normalized.supportStatus())
                    && selectionPolicy.shouldSelectAiParse(session.currentParseId());
                if (selected) {
                    session.selectParse(saved.id(), now);
                    correctionMetrics.selectionChanged(ProblemParseSource.AI.name());
                    transition(
                        session,
                        normalized.reviewRequired() ? ProblemSessionStatus.REVIEW_REQUIRED : ProblemSessionStatus.PARSED,
                        now
                    );
                }
            }
            if (ProblemParseSupportStatus.UNSUPPORTED.name().equals(normalized.supportStatus())) {
                job.markUnsupported(clock.instant());
            } else {
                job.markSucceeded(normalized.reviewRequired(), clock.instant());
            }
        });
        if (aiResult.provenance().fallbackUsed()) {
            metrics.fallback();
        }
        metrics.providerLatency(aiResult.providerLatencyMs());
        metrics.totalLatency(totalNanos);
        metrics.estimatedCost(aiResult.usage().estimatedCostMicros());
        if (ProblemParseSupportStatus.UNSUPPORTED.name().equals(normalized.supportStatus())) {
            metrics.unsupported(normalized.unsupportedReason());
        }
        metrics.success(aiResult.provenance().provider(), normalized.supportStatus(), normalized.reviewRequired());
    }

    private void completeFailure(
        ParseExecutionContext context,
        ApiErrorCode errorCode,
        String failureClass,
        boolean retryable
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            ProblemParseJobJpaEntity job = parseJobRepository.findByIdForUpdate(context.job().id())
                .orElseThrow();
            if (!ProblemParseJobStatus.RUNNING.name().equals(job.status()) || job.attemptCount() != context.attemptNumber()) {
                return;
            }
            Instant now = clock.instant();
            job.markFailure(errorCode.name(), failureClass, retryable, nextAttemptAt(job.id(), job.attemptCount(), now), now);
        });
        metrics.failure(failureClass);
    }

    private void recoverStaleRunningJobs(int limit) {
        Instant staleBefore = clock.instant().minus(properties.stuckRunningTimeout());
        for (UUID jobId : parseJobRepository.findStaleRunningJobIds(staleBefore, PageRequest.of(0, limit))) {
            transactionTemplate.executeWithoutResult(status -> parseJobRepository.findByIdForUpdate(jobId)
                .ifPresent(job -> job.recoverStuckRunning(
                    ApiErrorCode.PROBLEM_PARSE_FAILED.name(),
                    nextAttemptAt(job.id(), job.attemptCount(), clock.instant()),
                    clock.instant()
                )));
        }
    }

    private ProblemParseStatusResult statusFor(ProblemParseJobJpaEntity job) {
        Optional<ProblemParseJpaEntity> parse = parseRepository.findByParseJobId(job.id());
        return new ProblemParseStatusResult(
            job.id(),
            job.problemSessionId(),
            job.recognitionEvidenceId(),
            job.recognitionEvidenceRevision(),
            job.status(),
            job.capability(),
            job.attemptCount(),
            job.maxAttempts(),
            job.lastErrorCode(),
            job.lastFailureClass(),
            parse.map(ProblemParseJpaEntity::id).orElse(null),
            parse.map(ProblemParseJpaEntity::revision).orElse(null),
            parse.map(ProblemParseJpaEntity::supportStatus).orElse(null),
            parse.map(ProblemParseJpaEntity::unsupportedReason).orElse(null),
            parse.map(ProblemParseJpaEntity::reviewRequired).orElse(job.reviewRequired()),
            job.schemaVersion(),
            job.promptId(),
            job.promptVersion(),
            job.routePolicyVersion(),
            parse.map(ProblemParseJpaEntity::provider).orElse(null),
            parse.map(ProblemParseJpaEntity::model).orElse(null),
            parse.map(ProblemParseJpaEntity::normalizedProblemJson).orElse(null),
            job.createdAt(),
            job.updatedAt(),
            job.completedAt()
        );
    }

    private RecognitionEvidenceJpaEntity currentRecognitionEvidence(UUID userId, UUID problemSessionId) {
        return evidenceRepository.findFirstByProblemSessionIdAndUserIdOrderByCreatedAtDesc(problemSessionId, userId)
            .orElseThrow(() -> problem(
                HttpStatus.CONFLICT,
                ApiErrorCode.PROBLEM_PARSE_FAILED,
                "No recognition evidence is available for parsing",
                true,
                "RECOGNIZE"
            ));
    }

    private void requireActiveAccount(UUID userId) {
        String userStatus = jdbcTemplate.query(
            "select status from users where id = ?",
            preparedStatement -> preparedStatement.setObject(1, userId),
            resultSet -> resultSet.next() ? resultSet.getString("status") : null
        );
        if (!"ACTIVE".equals(userStatus)) {
            throw problem(HttpStatus.FORBIDDEN, ApiErrorCode.ACCOUNT_NOT_ACTIVE, "Account is not active", false, "SIGN_IN");
        }
    }

    private void transitionToParsingIfNeeded(ProblemSessionJpaEntity session, Instant now) {
        ProblemSessionStatus current = ProblemSessionStatus.valueOf(session.status());
        if (current == ProblemSessionStatus.PARSING || current == ProblemSessionStatus.PARSED) {
            return;
        }
        transition(session, ProblemSessionStatus.PARSING, now);
    }

    private void transition(ProblemSessionJpaEntity session, ProblemSessionStatus target, Instant now) {
        ProblemSessionStatus current = ProblemSessionStatus.valueOf(session.status());
        if (current == target) {
            return;
        }
        if (current == ProblemSessionStatus.ASSET_UPLOADED
            && (target == ProblemSessionStatus.PARSED || target == ProblemSessionStatus.REVIEW_REQUIRED)) {
            transition(session, ProblemSessionStatus.PARSING, now);
            current = ProblemSessionStatus.valueOf(session.status());
        }
        lifecyclePolicy.requireTransition(current, target);
        if (target == ProblemSessionStatus.PARSING) {
            session.markParsing(now);
        } else if (target == ProblemSessionStatus.PARSED) {
            session.markParsed(now);
        } else if (target == ProblemSessionStatus.REVIEW_REQUIRED) {
            session.markReviewRequired(now);
        }
        sessionMetrics.lifecycleTransition(current.name(), target.name());
    }

    private Instant nextAttemptAt(UUID jobId, int attemptCount, Instant now) {
        long jitterSeconds = Math.floorMod(jobId.getLeastSignificantBits(), 3);
        long backoffSeconds = Math.min(60, (long) Math.pow(2, Math.max(1, attemptCount)));
        return now.plus(Duration.ofSeconds(backoffSeconds + jitterSeconds));
    }

    private static ApiProblemException problem(
        HttpStatus status,
        ApiErrorCode code,
        String title,
        boolean recoverable,
        String userAction
    ) {
        return new ApiProblemException(status, code, title, recoverable, userAction);
    }

    private record ParseExecutionContext(
        ProblemParseJobJpaEntity job,
        RecognitionEvidenceJpaEntity recognitionEvidence,
        int attemptNumber
    ) {
    }
}
