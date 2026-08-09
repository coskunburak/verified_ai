package com.verifiedai.problem.application;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiModelGateway;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiVisionParseRequest;
import com.verifiedai.ai.application.AiVisionParseResult;
import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.problem.domain.model.RecognitionJobStatus;
import com.verifiedai.problem.domain.port.ProblemAssetObjectNotFoundException;
import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.problem.domain.port.ProblemAssetStorageUnavailableException;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetDerivativeJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetDerivativeJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetQualityEvidenceJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.ProblemSessionJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.RecognitionEvidenceJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.RecognitionEvidenceJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.RecognitionJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.RecognitionJobJpaRepository;
import com.verifiedai.problem.infrastructure.recognition.ProblemRecognitionProperties;
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
public class ProblemRecognitionApplicationService {
    private final ProblemSessionJpaRepository sessionRepository;
    private final ProblemAssetJpaRepository assetRepository;
    private final ProblemAssetDerivativeJpaRepository derivativeRepository;
    private final ProblemAssetQualityEvidenceJpaRepository qualityEvidenceRepository;
    private final RecognitionJobJpaRepository jobRepository;
    private final RecognitionEvidenceJpaRepository evidenceRepository;
    private final ProblemAssetStorage storage;
    private final AiModelGateway aiModelGateway;
    private final CapabilityAccessPolicy capabilityAccessPolicy;
    private final RecognitionEvidenceNormalizer evidenceNormalizer;
    private final ProblemRecognitionProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final ProblemRecognitionMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("ParameterNumber")
    ProblemRecognitionApplicationService(
        ProblemSessionJpaRepository sessionRepository,
        ProblemAssetJpaRepository assetRepository,
        ProblemAssetDerivativeJpaRepository derivativeRepository,
        ProblemAssetQualityEvidenceJpaRepository qualityEvidenceRepository,
        RecognitionJobJpaRepository jobRepository,
        RecognitionEvidenceJpaRepository evidenceRepository,
        ProblemAssetStorage storage,
        AiModelGateway aiModelGateway,
        CapabilityAccessPolicy capabilityAccessPolicy,
        RecognitionEvidenceNormalizer evidenceNormalizer,
        ProblemRecognitionProperties properties,
        JdbcTemplate jdbcTemplate,
        Clock clock,
        ProblemRecognitionMetrics metrics,
        TransactionTemplate transactionTemplate
    ) {
        this.sessionRepository = sessionRepository;
        this.assetRepository = assetRepository;
        this.derivativeRepository = derivativeRepository;
        this.qualityEvidenceRepository = qualityEvidenceRepository;
        this.jobRepository = jobRepository;
        this.evidenceRepository = evidenceRepository;
        this.storage = storage;
        this.aiModelGateway = aiModelGateway;
        this.capabilityAccessPolicy = capabilityAccessPolicy;
        this.evidenceNormalizer = evidenceNormalizer;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
    }

    public RecognitionStatusResult requestRecognition(UUID userId, UUID problemSessionId) {
        requireActiveAccount(userId);
        capabilityAccessPolicy.requireBasicSolve(userId);
        AiRoutePlan routePlan = aiModelGateway.routePlan(AiCapability.VISION_PARSE);
        RecognitionJobJpaEntity job = transactionTemplate.execute(status -> {
            sessionRepository.findByIdAndUserIdForUpdate(problemSessionId, userId)
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_FORBIDDEN, "Problem session was not found", false, "RETRY"));
            ProblemAssetDerivativeJpaEntity input = selectedRecognitionInput(userId, problemSessionId);
            ProblemAssetJpaEntity source = assetRepository.findByIdAndUserId(input.sourceAssetId(), userId)
                .orElseThrow(() -> problem(HttpStatus.CONFLICT, ApiErrorCode.RECOGNITION_INPUT_UNAVAILABLE, "Recognition input source asset is unavailable", true, "RETAKE"));
            if (!source.available()) {
                throw problem(HttpStatus.CONFLICT, ApiErrorCode.RECOGNITION_INPUT_UNAVAILABLE, "Recognition input source asset is not available", true, "RETRY");
            }
            Optional<RecognitionJobJpaEntity> existing = jobRepository
                .findByUserIdAndProblemSessionIdAndInputDerivativeIdAndCapabilityAndPromptIdAndPromptVersionAndSchemaVersion(
                    userId,
                    problemSessionId,
                    input.id(),
                    AiCapability.VISION_PARSE.name(),
                    routePlan.promptId(),
                    routePlan.promptVersion(),
                    routePlan.schemaVersion()
                );
            if (existing.isPresent()) {
                return existing.get();
            }
            RecognitionJobJpaEntity created = RecognitionJobJpaEntity.queued(
                UUID.randomUUID(),
                userId,
                problemSessionId,
                source.id(),
                input.id(),
                routePlan.routePolicyVersion(),
                routePlan.promptId(),
                routePlan.promptVersion(),
                routePlan.schemaVersion(),
                routePlan.maxAttempts(),
                clock.instant()
            );
            metrics.started();
            return jobRepository.saveAndFlush(created);
        });
        return statusFor(job);
    }

    public RecognitionStatusResult getRecognition(UUID userId, UUID problemSessionId) {
        requireActiveAccount(userId);
        sessionRepository.findByIdAndUserId(problemSessionId, userId)
            .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, ApiErrorCode.RESOURCE_FORBIDDEN, "Problem session was not found", false, "RETRY"));
        return jobRepository.findFirstByProblemSessionIdAndUserIdOrderByCreatedAtDesc(problemSessionId, userId)
            .map(this::statusFor)
            .orElseGet(() -> RecognitionStatusResult.notStarted(problemSessionId));
    }

    public int runDueRecognitionJobs(int limit) {
        recoverStaleRunningJobs(limit);
        List<UUID> jobIds = jobRepository.findDueJobIds(clock.instant(), PageRequest.of(0, limit));
        int completed = 0;
        for (UUID jobId : jobIds) {
            if (runRecognitionJob(jobId)) {
                completed += 1;
            }
        }
        return completed;
    }

    public boolean runRecognitionJob(UUID jobId) {
        long totalStarted = System.nanoTime();
        RecognitionExecutionContext context = startExecution(jobId);
        if (context == null) {
            return false;
        }
        try {
            AiVisionParseResult aiResult = executeProvider(context);
            NormalizedRecognitionEvidence normalized = evidenceNormalizer.normalize(
                aiResult.rawOutputJson(),
                context.job().schemaVersion(),
                context.inputDerivative(),
                context.qualitySignals()
            );
            completeSuccess(context, aiResult, normalized, System.nanoTime() - totalStarted);
            return true;
        } catch (RecognitionValidationException exception) {
            metrics.schemaInvalid();
            completeFailure(context, ApiErrorCode.RECOGNITION_SCHEMA_INVALID, AiProviderFailureClass.SCHEMA_INVALID.name(), true);
            return true;
        } catch (AiProviderException exception) {
            if (exception.failureClass() == AiProviderFailureClass.TIMEOUT) {
                metrics.timeout();
            }
            completeFailure(context, errorCodeFor(exception.failureClass()), exception.failureClass().name(), exception.retryable());
            return true;
        } catch (ProblemAssetObjectNotFoundException exception) {
            completeFailure(context, ApiErrorCode.RECOGNITION_INPUT_UNAVAILABLE, "INPUT_UNAVAILABLE", false);
            return true;
        } catch (ProblemAssetStorageUnavailableException exception) {
            completeFailure(context, ApiErrorCode.RECOGNITION_PROVIDER_UNAVAILABLE, "STORAGE_UNAVAILABLE", true);
            return true;
        }
    }

    private RecognitionExecutionContext startExecution(UUID jobId) {
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            RecognitionJobJpaEntity job = jobRepository.findByIdForUpdate(jobId).orElse(null);
            if (job == null || !job.dueAt(now)) {
                return null;
            }
            int attemptNumber = job.markRunning(now);
            ProblemAssetDerivativeJpaEntity input = derivativeRepository.findById(job.inputDerivativeId())
                .orElseThrow(() -> problem(HttpStatus.CONFLICT, ApiErrorCode.RECOGNITION_INPUT_UNAVAILABLE, "Recognition derivative is unavailable", true, "RETAKE"));
            ProblemAssetJpaEntity source = assetRepository.findByIdAndUserId(job.sourceAssetId(), job.userId())
                .orElseThrow(() -> problem(HttpStatus.CONFLICT, ApiErrorCode.RECOGNITION_INPUT_UNAVAILABLE, "Recognition source asset is unavailable", true, "RETAKE"));
            List<ProblemAssetQualitySignalResult> qualitySignals = qualityEvidenceRepository
                .findBySourceAssetIdAndUserIdOrderByCreatedAtAsc(job.sourceAssetId(), job.userId())
                .stream()
                .map(ProblemAssetQualitySignalResult::from)
                .toList();
            return new RecognitionExecutionContext(job, source, input, qualitySignals, attemptNumber);
        });
    }

    private AiVisionParseResult executeProvider(RecognitionExecutionContext context) {
        byte[] imageBytes = storage.readBytes(context.inputDerivative().objectKey(), properties.maxInputBytes());
        AiRoutePlan routePlan = aiModelGateway.routePlan(AiCapability.VISION_PARSE);
        AiVisionParseResult result = aiModelGateway.executeVisionParse(new AiVisionParseRequest(
            context.job().problemSessionId(),
            context.sourceAsset().id(),
            context.inputDerivative().id(),
            context.inputDerivative().contentType(),
            imageBytes,
            context.inputDerivative().width(),
            context.inputDerivative().height(),
            context.job().promptId(),
            context.job().promptVersion(),
            context.job().schemaVersion(),
            routePlan.timeout(),
            context.qualitySignals()
                .stream()
                .filter(signal -> !"PASS".equals(signal.severity()))
                .map(ProblemAssetQualitySignalResult::signalType)
                .toList()
        ));
        if (result.rawOutputJson().getBytes(StandardCharsets.UTF_8).length > routePlan.maxResponseBytes()) {
            throw new AiProviderException(AiProviderFailureClass.OUTPUT_TOO_LARGE, false, "Recognition provider output exceeded response limit");
        }
        return result;
    }

    private void completeSuccess(
        RecognitionExecutionContext context,
        AiVisionParseResult aiResult,
        NormalizedRecognitionEvidence normalized,
        long totalNanos
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            RecognitionJobJpaEntity job = jobRepository.findByIdForUpdate(context.job().id())
                .orElseThrow();
            if (!RecognitionJobStatus.RUNNING.name().equals(job.status()) || job.attemptCount() != context.attemptNumber()) {
                return;
            }
            if (evidenceRepository.findByRecognitionJobId(job.id()).isEmpty()) {
                int revision = evidenceRepository.maxRevision(
                    job.problemSessionId(),
                    job.inputDerivativeId(),
                    job.schemaVersion(),
                    job.promptVersion()
                ) + 1;
                evidenceRepository.save(new RecognitionEvidenceJpaEntity(
                    UUID.randomUUID(),
                    job.id(),
                    job.userId(),
                    job.problemSessionId(),
                    job.sourceAssetId(),
                    job.inputDerivativeId(),
                    revision,
                    job.schemaVersion(),
                    normalized.rawOutputJson(),
                    normalized.normalizedEvidenceJson(),
                    normalized.upstreamQualityEvidenceJson(),
                    aiResult.provenance(),
                    aiResult.usage(),
                    aiResult.providerLatencyMs(),
                    Math.max(0, totalNanos / 1_000_000),
                    null,
                    clock.instant()
                ));
            }
            job.markSucceeded(normalized.reviewRequired(), clock.instant());
        });
        if (aiResult.provenance().fallbackUsed()) {
            metrics.fallback();
        }
        metrics.providerLatency(aiResult.providerLatencyMs());
        metrics.totalLatency(totalNanos);
        metrics.estimatedCost(aiResult.usage().estimatedCostMicros());
        metrics.success(aiResult.provenance().provider(), normalized.reviewRequired());
    }

    private void completeFailure(
        RecognitionExecutionContext context,
        ApiErrorCode errorCode,
        String failureClass,
        boolean retryable
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            RecognitionJobJpaEntity job = jobRepository.findByIdForUpdate(context.job().id())
                .orElseThrow();
            if (!RecognitionJobStatus.RUNNING.name().equals(job.status()) || job.attemptCount() != context.attemptNumber()) {
                return;
            }
            Instant now = clock.instant();
            job.markFailure(errorCode.name(), failureClass, retryable, nextAttemptAt(job.id(), job.attemptCount(), now), now);
        });
        metrics.failure(failureClass);
    }

    private void recoverStaleRunningJobs(int limit) {
        Instant staleBefore = clock.instant().minus(properties.stuckRunningTimeout());
        for (UUID jobId : jobRepository.findStaleRunningJobIds(staleBefore, PageRequest.of(0, limit))) {
            transactionTemplate.executeWithoutResult(status -> jobRepository.findByIdForUpdate(jobId)
                .ifPresent(job -> job.recoverStuckRunning(
                    ApiErrorCode.RECOGNITION_TIMEOUT.name(),
                    nextAttemptAt(job.id(), job.attemptCount(), clock.instant()),
                    clock.instant()
                )));
        }
    }

    private RecognitionStatusResult statusFor(RecognitionJobJpaEntity job) {
        Optional<RecognitionEvidenceJpaEntity> evidence = evidenceRepository.findByRecognitionJobId(job.id());
        List<RecognitionBlockResult> blocks = evidence
            .map(value -> evidenceNormalizer.blocksFromNormalized(value.normalizedEvidenceJson()))
            .orElseGet(List::of);
        return new RecognitionStatusResult(
            job.id(),
            job.problemSessionId(),
            job.sourceAssetId(),
            job.inputDerivativeId(),
            job.status(),
            job.capability(),
            job.attemptCount(),
            job.maxAttempts(),
            job.lastErrorCode(),
            job.lastFailureClass(),
            job.reviewRequired(),
            job.schemaVersion(),
            job.promptId(),
            job.promptVersion(),
            job.routePolicyVersion(),
            evidence.map(RecognitionEvidenceJpaEntity::provider).orElse(null),
            evidence.map(RecognitionEvidenceJpaEntity::model).orElse(null),
            blocks.size(),
            blocks,
            job.createdAt(),
            job.updatedAt(),
            job.completedAt()
        );
    }

    private ProblemAssetDerivativeJpaEntity selectedRecognitionInput(UUID userId, UUID problemSessionId) {
        return derivativeRepository
            .findFirstByProblemSessionIdAndUserIdAndSelectedForRecognitionTrueAndStatusOrderByCreatedAtDesc(
                problemSessionId,
                userId,
                "READY"
            )
            .orElseThrow(() -> problem(
                HttpStatus.CONFLICT,
                ApiErrorCode.RECOGNITION_INPUT_UNAVAILABLE,
                "No selected recognition input is available",
                true,
                "PREPROCESS"
            ));
    }

    private void requireActiveAccount(UUID userId) {
        String status = jdbcTemplate.query(
            "select status from users where id = ?",
            preparedStatement -> preparedStatement.setObject(1, userId),
            resultSet -> resultSet.next() ? resultSet.getString("status") : null
        );
        if (!"ACTIVE".equals(status)) {
            throw problem(HttpStatus.FORBIDDEN, ApiErrorCode.ACCOUNT_NOT_ACTIVE, "Account is not active", false, "SIGN_IN");
        }
    }

    private Instant nextAttemptAt(UUID jobId, int attemptCount, Instant now) {
        long jitterSeconds = Math.floorMod(jobId.getLeastSignificantBits(), 3);
        long backoffSeconds = Math.min(60, (long) Math.pow(2, Math.max(1, attemptCount)));
        return now.plus(Duration.ofSeconds(backoffSeconds + jitterSeconds));
    }

    private static ApiErrorCode errorCodeFor(AiProviderFailureClass failureClass) {
        return switch (failureClass) {
            case TIMEOUT -> ApiErrorCode.RECOGNITION_TIMEOUT;
            case RATE_LIMITED -> ApiErrorCode.RECOGNITION_RATE_LIMITED;
            case OUTPUT_TOO_LARGE -> ApiErrorCode.RECOGNITION_OUTPUT_TOO_LARGE;
            case UNSUPPORTED_PAYLOAD -> ApiErrorCode.RECOGNITION_UNSUPPORTED;
            case SCHEMA_INVALID -> ApiErrorCode.RECOGNITION_SCHEMA_INVALID;
            case PROVIDER_UNAVAILABLE, CONFIGURATION_DISABLED, INVALID_AUTH -> ApiErrorCode.RECOGNITION_PROVIDER_UNAVAILABLE;
            case UNKNOWN -> ApiErrorCode.RECOGNITION_FAILED;
        };
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

    private record RecognitionExecutionContext(
        RecognitionJobJpaEntity job,
        ProblemAssetJpaEntity sourceAsset,
        ProblemAssetDerivativeJpaEntity inputDerivative,
        List<ProblemAssetQualitySignalResult> qualitySignals,
        int attemptNumber
    ) {
    }
}
