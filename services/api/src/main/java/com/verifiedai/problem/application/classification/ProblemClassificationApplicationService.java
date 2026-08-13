package com.verifiedai.problem.application.classification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiExecutionContext;
import com.verifiedai.ai.application.AiModelGateway;
import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemClassifyResult;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.curriculum.application.CurriculumTaxonomySnapshot;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationJobStatus;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationReviewReason;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationSource;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationStatus;
import com.verifiedai.problem.infrastructure.classification.ProblemClassifierProperties;
import com.verifiedai.problem.infrastructure.persistence.entity.CanonicalProblemJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemClassificationJobJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemClassificationJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemClassificationSecondarySkillJpaEntity;
import com.verifiedai.problem.infrastructure.persistence.repository.CanonicalProblemJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.repository.ProblemClassificationJobJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.repository.ProblemClassificationJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.repository.ProblemClassificationSecondarySkillJpaRepository;
import com.verifiedai.problem.infrastructure.persistence.repository.ProblemSessionJpaRepository;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProblemClassificationApplicationService {

    private static final int MAX_WORKER_BATCH_SIZE = 100;

    private final ProblemSessionJpaRepository sessionRepository;
    private final CanonicalProblemJpaRepository canonicalRepository;

    private final ProblemClassificationJobJpaRepository jobRepository;
    private final ProblemClassificationJpaRepository classificationRepository;
    private final ProblemClassificationSecondarySkillJpaRepository secondarySkillRepository;

    private final CurriculumTaxonomyCatalog taxonomyCatalog;

    private final AiModelGateway aiModelGateway;

    private final ProblemClassificationOutputNormalizer outputNormalizer;
    private final ProblemClassificationValidator validator;
    private final ProblemClassificationEligibilityPolicy eligibilityPolicy;
    private final ProblemClassificationCandidatePolicy candidatePolicy;
    private final ClassificationConfidencePolicy confidencePolicy;

    private final CapabilityAccessPolicy capabilityAccessPolicy;

    private final ProblemClassifierProperties properties;

    private final ObjectMapper objectMapper =
        new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;

    private final Clock clock;

    private final ProblemClassificationMetrics metrics;
    private final TransactionTemplate transactionTemplate;

    @SuppressWarnings("ParameterNumber")
    ProblemClassificationApplicationService(
        ProblemSessionJpaRepository sessionRepository,
        CanonicalProblemJpaRepository canonicalRepository,
        ProblemClassificationJobJpaRepository jobRepository,
        ProblemClassificationJpaRepository classificationRepository,
        ProblemClassificationSecondarySkillJpaRepository secondarySkillRepository,
        CurriculumTaxonomyCatalog taxonomyCatalog,
        AiModelGateway aiModelGateway,
        ProblemClassificationOutputNormalizer outputNormalizer,
        ProblemClassificationValidator validator,
        ProblemClassificationEligibilityPolicy eligibilityPolicy,
        ProblemClassificationCandidatePolicy candidatePolicy,
        ClassificationConfidencePolicy confidencePolicy,
        CapabilityAccessPolicy capabilityAccessPolicy,
        ProblemClassifierProperties properties,
        JdbcTemplate jdbcTemplate,
        Clock clock,
        ProblemClassificationMetrics metrics,
        TransactionTemplate transactionTemplate
    ) {
        this.sessionRepository = sessionRepository;
        this.canonicalRepository = canonicalRepository;
        this.jobRepository = jobRepository;
        this.classificationRepository = classificationRepository;
        this.secondarySkillRepository = secondarySkillRepository;
        this.taxonomyCatalog = taxonomyCatalog;
        this.aiModelGateway = aiModelGateway;
        this.outputNormalizer = outputNormalizer;
        this.validator = validator;
        this.eligibilityPolicy = eligibilityPolicy;
        this.candidatePolicy = candidatePolicy;
        this.confidencePolicy = confidencePolicy;
        this.capabilityAccessPolicy = capabilityAccessPolicy;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.metrics = metrics;
        this.transactionTemplate = transactionTemplate;
    }

    public ProblemClassificationStatusResult requestClassification(
        UUID userId,
        UUID problemSessionId
    ) {
        requireActiveAccount(userId);
        capabilityAccessPolicy.requireBasicSolve(userId);

        AiRoutePlan routePlan =
            requireRouteContract(
                aiModelGateway.routePlan(
                    AiCapability.PROBLEM_CLASSIFY
                )
            );

        CurriculumTaxonomySnapshot ontology =
            taxonomyCatalog.snapshot();

        ProblemClassificationJobJpaEntity job =
            transactionTemplate.execute(status -> {
                sessionRepository
                    .findByIdAndUserIdForUpdate(
                        problemSessionId,
                        userId
                    )
                    .orElseThrow(() ->
                        problem(
                            HttpStatus.NOT_FOUND,
                            ApiErrorCode.RESOURCE_FORBIDDEN,
                            "Problem session was not found",
                            false,
                            "RETRY"
                        )
                    );

                CanonicalProblemJpaEntity canonical =
                    currentCanonical(
                        userId,
                        problemSessionId
                    );

                String fingerprint =
                    ProblemClassificationRequestFingerprint
                        .create(
                            canonical.id(),
                            canonical.canonicalRevision(),
                            ontology.ontologyVersion(),
                            ProblemClassificationContract
                                .PROJECTION_VERSION,
                            routePlan.schemaVersion(),
                            routePlan.promptId(),
                            routePlan.promptVersion(),
                            routePlan.routePolicyVersion()
                        );

                Optional<ProblemClassificationJobJpaEntity>
                    existing =
                    jobRepository
                        .findByRequestFingerprint(
                            fingerprint
                        );

                if (existing.isPresent()) {
                    return existing.get();
                }

                ProblemClassificationJobJpaEntity created =
                    ProblemClassificationJobJpaEntity
                        .queued(
                            UUID.randomUUID(),
                            userId,
                            problemSessionId,
                            canonical.id(),
                            canonical.canonicalRevision(),
                            ontology.ontologyVersion(),
                            ProblemClassificationContract
                                .PROJECTION_VERSION,
                            routePlan.routePolicyVersion(),
                            routePlan.promptId(),
                            routePlan.promptVersion(),
                            routePlan.schemaVersion(),
                            fingerprint,
                            routePlan.maxAttempts(),
                            clock.instant()
                        );

                metrics.started();

                return jobRepository.saveAndFlush(
                    created
                );
            });

        if (job == null) {
            throw new IllegalStateException(
                "Classification transaction returned no job"
            );
        }

        return statusFor(job);
    }

    /*
     * Compatibility alias while the API/controller layer is
     * migrated during the remainder of Sprint 4.7.
     */
    public ProblemClassificationStatusResult classify(
        UUID userId,
        UUID problemSessionId
    ) {
        return requestClassification(
            userId,
            problemSessionId
        );
    }

    public ProblemClassificationStatusResult getClassification(
        UUID userId,
        UUID problemSessionId
    ) {
        requireActiveAccount(userId);

        sessionRepository
            .findByIdAndUserId(
                problemSessionId,
                userId
            )
            .orElseThrow(() ->
                problem(
                    HttpStatus.NOT_FOUND,
                    ApiErrorCode.RESOURCE_FORBIDDEN,
                    "Problem session was not found",
                    false,
                    "RETRY"
                )
            );

        CanonicalProblemJpaEntity canonical =
            currentCanonical(
                userId,
                problemSessionId
            );

        return jobRepository
            .findFirstByCanonicalProblemIdAndUserIdOrderByCreatedAtDesc(
                canonical.id(),
                userId
            )
            .map(this::statusFor)
            .orElseGet(() ->
                ProblemClassificationStatusResult
                    .notStarted(
                        problemSessionId,
                        canonical.id(),
                        canonical.canonicalRevision(),
                        taxonomyCatalog
                            .snapshot()
                            .ontologyVersion()
                    )
            );
    }

    public int runDueClassificationJobs(
        int requestedLimit
    ) {
        int limit =
            Math.max(
                1,
                Math.min(
                    requestedLimit,
                    MAX_WORKER_BATCH_SIZE
                )
            );

        recoverStaleRunningJobs(limit);

        List<UUID> jobIds =
            jobRepository.findDueJobIds(
                clock.instant(),
                PageRequest.of(0, limit)
            );

        int handled = 0;

        for (UUID jobId : jobIds) {
            if (runClassificationJob(jobId)) {
                handled += 1;
            }
        }

        return handled;
    }

    public boolean runClassificationJob(
        UUID jobId
    ) {
        long started = System.nanoTime();

        ClassificationExecutionContext context =
            startExecution(jobId);

        if (context == null) {
            return false;
        }

        try {
            CurriculumTaxonomySnapshot ontology =
                taxonomyCatalog.snapshot();

            if (
                !context.job()
                    .ontologyVersion()
                    .equals(
                        ontology.ontologyVersion()
                    )
            ) {
                completeFailure(
                    context,
                    ApiErrorCode.CLASSIFICATION_ONTOLOGY_INVALID,
                    "ONTOLOGY_VERSION_STALE",
                    false
                );

                return true;
            }

            ClassificationInputProjection projection =
                buildProjection(
                    context.canonicalProblem()
                );

            ProblemClassificationEligibility eligibility =
                eligibilityPolicy.evaluate(
                    context.canonicalProblem()
                        .problemType(),
                    context.canonicalProblem()
                        .taskType(),
                    projection.upstreamReviewRequired()
                );

            if (!eligibility.eligible()) {
                completeSystemOutcome(
                    context,
                    eligibility,
                    projection.upstreamReviewRequired()
                );

                return true;
            }

            ProblemClassificationCandidates candidates =
                candidatePolicy.candidates(
                    ontology,
                    context.canonicalProblem()
                        .problemType(),
                    context.canonicalProblem()
                        .taskType()
                );

            AiProblemClassifyResult aiResult =
                executeProvider(
                    context,
                    projection,
                    candidates
                );

            /*
             * Classification semantic truth remains in the
             * problem module.
             *
             * The generic AI gateway must not own any of these
             * normalization/validation/policy decisions.
             */
            ProblemClassificationProposal proposal =
                outputNormalizer.normalize(
                    aiResult.rawOutputJson(),
                    context.job().schemaVersion(),
                    context.job().ontologyVersion()
                );

            ValidatedProblemClassification validated =
                validator.validate(proposal);

            candidatePolicy.validateSelection(
                validated,
                candidates
            );

            ClassificationConfidenceDecision confidence =
                confidencePolicy.evaluate(
                    validated,
                    projection.upstreamReviewRequired()
                );

            boolean persisted =
                persistOutcome(
                    context,
                    validated,
                    confidence,
                    ProblemClassificationSource.AI,
                    aiResult
                );

            if (persisted) {
                recordAiSuccess(
                    aiResult,
                    validated
                );
            } else {
                metrics.failure(
                    "STALE_CANONICAL"
                );
            }

            return true;

        } catch (
            ProblemClassificationOutputException exception
        ) {
            metrics.schemaInvalid();

            completeFailure(
                context,
                ApiErrorCode.CLASSIFICATION_SCHEMA_INVALID,
                "SCHEMA_INVALID",
                true
            );

            return true;

        } catch (
            ProblemClassificationCandidateException exception
        ) {
            metrics.candidateInvalid();

            completeFailure(
                context,
                ApiErrorCode.CLASSIFICATION_ONTOLOGY_INVALID,
                "CANDIDATE_INVALID",
                false
            );

            return true;

        } catch (
            ProblemClassificationValidationException exception
        ) {
            if (isOntologyFailure(exception)) {
                metrics.ontologyInvalid();

                completeFailure(
                    context,
                    ApiErrorCode.CLASSIFICATION_ONTOLOGY_INVALID,
                    exception.failure().name(),
                    false
                );
            } else {
                metrics.semanticInvalid();

                completeFailure(
                    context,
                    ApiErrorCode.CLASSIFICATION_SCHEMA_INVALID,
                    exception.failure().name(),
                    false
                );
            }

            return true;

        } catch (AiProviderException exception) {
            completeFailure(
                context,
                ApiErrorCode.CLASSIFICATION_PROVIDER_UNAVAILABLE,
                exception.failureClass().name(),
                exception.retryable()
            );

            return true;

        } catch (RuntimeException exception) {
            completeFailure(
                context,
                ApiErrorCode.CLASSIFICATION_FAILED,
                "INTERNAL",
                false
            );

            throw exception;

        } finally {
            metrics.totalLatency(
                System.nanoTime() - started
            );
        }
    }

    private ClassificationExecutionContext startExecution(
        UUID jobId
    ) {
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();

            ProblemClassificationJobJpaEntity job =
                jobRepository
                    .findByIdForUpdate(jobId)
                    .orElse(null);

            if (
                job == null
                    || !job.dueAt(now)
            ) {
                return null;
            }

            CanonicalProblemJpaEntity canonical =
                canonicalRepository
                    .findById(
                        job.canonicalProblemId()
                    )
                    .orElse(null);

            if (
                canonical == null
                    || !canonical.userId()
                    .equals(job.userId())
                    || !canonical.problemSessionId()
                    .equals(
                        job.problemSessionId()
                    )
                    || canonical.canonicalRevision()
                    != job.canonicalProblemRevision()
            ) {
                job.markFailure(
                    ApiErrorCode.CLASSIFICATION_FAILED
                        .name(),
                    "CANONICAL_BINDING_INVALID",
                    false,
                    now,
                    now
                );

                return null;
            }

            CanonicalProblemJpaEntity latest =
                canonicalRepository
                    .findFirstByProblemSessionIdAndUserIdOrderByCanonicalRevisionDesc(
                        job.problemSessionId(),
                        job.userId()
                    )
                    .orElse(null);

            if (
                latest == null
                    || !latest.id()
                    .equals(canonical.id())
            ) {
                job.markFailure(
                    ApiErrorCode.CLASSIFICATION_FAILED
                        .name(),
                    "STALE_CANONICAL",
                    false,
                    now,
                    now
                );

                return null;
            }

            int attemptNumber =
                job.markRunning(now);

            return new ClassificationExecutionContext(
                job,
                canonical,
                attemptNumber
            );
        });
    }

    private AiProblemClassifyResult executeProvider(
        ClassificationExecutionContext context,
        ClassificationInputProjection projection,
        ProblemClassificationCandidates candidates
    ) {
        AiRoutePlan routePlan =
            requireRouteContract(
                aiModelGateway.routePlan(
                    AiCapability.PROBLEM_CLASSIFY
                )
            );

        requireRouteMatchesJob(
            routePlan,
            context.job()
        );

        /*
         * Capability-specific request construction remains here.
         * No concrete provider/model identity leaks into the
         * problem module.
         */
        AiProblemClassifyRequest request =
            new AiProblemClassifyRequest(
                context.canonicalProblem().id(),
                context.job().problemSessionId(),
                context.canonicalProblem()
                    .problemType(),
                context.canonicalProblem()
                    .taskType(),
                toJson(projection),
                context.job().ontologyVersion(),
                toJson(candidates),
                context.job().promptId(),
                context.job().promptVersion(),
                context.job().schemaVersion(),
                routePlan.timeout()
            );

        /*
         * Every durable classification job attempt receives a
         * deterministic AI operation identity.
         *
         * idempotencyKey = jobId
         * operationId    = deterministic(jobId + attemptNumber)
         */
        AiExecutionContext aiExecutionContext =
            AiExecutionContext.forJobAttempt(
                context.job().id(),
                context.attemptNumber(),
                context.job().userId(),
                context.job().problemSessionId(),
                CorrelationIds.current()
            );

        /*
         * Provider routing, approved fallback, usage accounting,
         * technical provenance and gateway execution policy are
         * owned by AiModelGateway.
         */
        AiProblemClassifyResult result =
            aiModelGateway.executeProblemClassify(
                request,
                aiExecutionContext
            );

        /*
         * These technical checks existed before Sprint 5.1.
         *
         * The gateway now enforces the corresponding execution
         * contracts centrally as well, but the caller-side guards
         * remain temporarily as defense-in-depth during migration.
         */
        if (
            result == null
                || result.rawOutputJson() == null
        ) {
            throw new AiProviderException(
                AiProviderFailureClass.SCHEMA_INVALID,
                true,
                "Classification provider returned no output"
            );
        }

        int outputBytes =
            result.rawOutputJson()
                .getBytes(StandardCharsets.UTF_8)
                .length;

        if (
            outputBytes
                > routePlan.maxResponseBytes()
        ) {
            throw new AiProviderException(
                AiProviderFailureClass.OUTPUT_TOO_LARGE,
                false,
                "Classification provider output exceeded response limit"
            );
        }

        if (
            result.provenance() == null
                || result.usage() == null
        ) {
            throw new AiProviderException(
                AiProviderFailureClass.SCHEMA_INVALID,
                false,
                "Classification provider omitted provenance or usage"
            );
        }

        requireProvenanceMatchesJob(
            result,
            context.job()
        );

        if (
            result.usage().estimatedCostMicros()
                > routePlan.maxCostMicros()
        ) {
            throw new AiProviderException(
                AiProviderFailureClass.UNKNOWN,
                false,
                "Classification provider exceeded configured cost budget"
            );
        }

        return result;
    }

    private void completeSystemOutcome(
        ClassificationExecutionContext context,
        ProblemClassificationEligibility eligibility,
        boolean upstreamReviewRequired
    ) {
        ProblemClassificationProposal proposal;

        if (eligibility.upstreamReviewRequired()) {
            proposal =
                new ProblemClassificationProposal(
                    context.job().schemaVersion(),
                    context.job().ontologyVersion(),
                    ProblemClassificationStatus
                        .REVIEW_REQUIRED,
                    null,
                    List.of(),
                    null,
                    ProblemClassificationReviewReason
                        .UPSTREAM_RISK
                );
        } else {
            proposal =
                new ProblemClassificationProposal(
                    context.job().schemaVersion(),
                    context.job().ontologyVersion(),
                    ProblemClassificationStatus
                        .UNSUPPORTED,
                    null,
                    List.of(),
                    null,
                    null
                );
        }

        ValidatedProblemClassification validated =
            validator.validate(proposal);

        ClassificationConfidenceDecision confidence =
            confidencePolicy.evaluate(
                validated,
                upstreamReviewRequired
            );

        boolean persisted =
            persistOutcome(
                context,
                validated,
                confidence,
                ProblemClassificationSource.SYSTEM,
                null
            );

        if (persisted) {
            metrics.success(
                "SYSTEM",
                validated.status().name(),
                ProblemClassificationSource.SYSTEM.name()
            );
        }
    }

    private boolean persistOutcome(
        ClassificationExecutionContext context,
        ValidatedProblemClassification validated,
        ClassificationConfidenceDecision confidence,
        ProblemClassificationSource source,
        AiProblemClassifyResult aiResult
    ) {
        Boolean result =
            transactionTemplate.execute(status -> {
                ProblemClassificationJobJpaEntity job =
                    jobRepository
                        .findByIdForUpdate(
                            context.job().id()
                        )
                        .orElseThrow();

                if (
                    !ProblemClassificationJobStatus
                        .RUNNING
                        .name()
                        .equals(job.status())
                        || job.attemptCount()
                        != context.attemptNumber()
                ) {
                    return false;
                }

                sessionRepository
                    .findByIdAndUserIdForUpdate(
                        job.problemSessionId(),
                        job.userId()
                    )
                    .orElseThrow();

                CanonicalProblemJpaEntity latest =
                    canonicalRepository
                        .findFirstByProblemSessionIdAndUserIdOrderByCanonicalRevisionDesc(
                            job.problemSessionId(),
                            job.userId()
                        )
                        .orElse(null);

                Instant now = clock.instant();

                if (
                    latest == null
                        || !latest.id()
                        .equals(
                            job.canonicalProblemId()
                        )
                        || latest.canonicalRevision()
                        != job.canonicalProblemRevision()
                ) {
                    job.markFailure(
                        ApiErrorCode.CLASSIFICATION_FAILED
                            .name(),
                        "STALE_CANONICAL",
                        false,
                        now,
                        now
                    );

                    return false;
                }

                Optional<ProblemClassificationJpaEntity>
                    existing =
                    classificationRepository
                        .findByClassificationJobId(
                            job.id()
                        );

                if (existing.isPresent()) {
                    job.markSucceeded(now);
                    return true;
                }

                if (
                    !job.ontologyVersion()
                        .equals(
                            validated.ontologyVersion()
                        )
                ) {
                    throw new IllegalStateException(
                        "Validated classification ontology drifted from job ontology"
                    );
                }

                int revision =
                    classificationRepository
                        .maxRevision(
                            job.canonicalProblemId()
                        )
                        + 1;

                UUID classificationId =
                    UUID.randomUUID();

                String provider = null;
                String model = null;
                boolean fallbackUsed = false;
                Long providerLatencyMs = null;
                Long estimatedCostMicros = null;

                if (aiResult != null) {
                    provider =
                        aiResult.provenance()
                            .provider();

                    model =
                        aiResult.provenance()
                            .model();

                    fallbackUsed =
                        aiResult.provenance()
                            .fallbackUsed();

                    providerLatencyMs =
                        aiResult.providerLatencyMs();

                    estimatedCostMicros =
                        aiResult.usage()
                            .estimatedCostMicros();
                }

                ProblemClassificationJpaEntity entity =
                    new ProblemClassificationJpaEntity(
                        classificationId,
                        job.id(),
                        job.userId(),
                        job.problemSessionId(),
                        job.canonicalProblemId(),
                        revision,
                        source.name(),
                        validated.status().name(),
                        validated.reviewReason() == null
                            ? null
                            : validated.reviewReason().name(),
                        validated.ontologyVersion(),
                        job.schemaVersion(),
                        job.projectionVersion(),
                        validated.subjectId(),
                        validated.topicId(),
                        validated.primarySkillId(),
                        validated.difficulty() == null
                            ? null
                            : validated.difficulty().name(),
                        ProblemClassificationContract
                            .DIFFICULTY_POLICY_VERSION,
                        confidence.band().name(),
                        confidence.policyVersion(),
                        confidence.calibration().name(),
                        job.capability(),
                        provider,
                        model,
                        job.promptId(),
                        job.promptVersion(),
                        job.routePolicyVersion(),
                        fallbackUsed,
                        providerLatencyMs,
                        estimatedCostMicros,
                        job.requestFingerprint(),
                        now
                    );

                classificationRepository
                    .saveAndFlush(entity);

                List<
                    ProblemClassificationSecondarySkillJpaEntity
                    > secondaryEntities =
                    new ArrayList<>();

                for (
                    int index = 0;
                    index
                        < validated
                        .secondarySkillIds()
                        .size();
                    index += 1
                ) {
                    secondaryEntities.add(
                        new ProblemClassificationSecondarySkillJpaEntity(
                            classificationId,
                            validated
                                .secondarySkillIds()
                                .get(index),
                            index
                        )
                    );
                }

                if (!secondaryEntities.isEmpty()) {
                    secondarySkillRepository
                        .saveAll(
                            secondaryEntities
                        );
                }

                job.markSucceeded(now);

                return true;
            });

        return Boolean.TRUE.equals(result);
    }

    private void completeFailure(
        ClassificationExecutionContext context,
        ApiErrorCode errorCode,
        String failureClass,
        boolean retryable
    ) {
        transactionTemplate
            .executeWithoutResult(status -> {
                ProblemClassificationJobJpaEntity job =
                    jobRepository
                        .findByIdForUpdate(
                            context.job().id()
                        )
                        .orElseThrow();

                if (
                    !ProblemClassificationJobStatus
                        .RUNNING
                        .name()
                        .equals(job.status())
                        || job.attemptCount()
                        != context.attemptNumber()
                ) {
                    return;
                }

                Instant now = clock.instant();

                job.markFailure(
                    errorCode.name(),
                    failureClass,
                    retryable,
                    nextAttemptAt(
                        job.id(),
                        job.attemptCount(),
                        now
                    ),
                    now
                );
            });

        metrics.failure(failureClass);
    }

    private void recoverStaleRunningJobs(
        int limit
    ) {
        Instant staleBefore =
            clock.instant()
                .minus(
                    properties
                        .stuckRunningTimeout()
                );

        List<UUID> staleJobIds =
            jobRepository
                .findStaleRunningJobIds(
                    staleBefore,
                    PageRequest.of(0, limit)
                );

        for (UUID jobId : staleJobIds) {
            transactionTemplate
                .executeWithoutResult(status ->
                    jobRepository
                        .findByIdForUpdate(jobId)
                        .ifPresent(job -> {
                            Instant now =
                                clock.instant();

                            job.recoverStuckRunning(
                                ApiErrorCode
                                    .CLASSIFICATION_FAILED
                                    .name(),
                                nextAttemptAt(
                                    job.id(),
                                    job.attemptCount(),
                                    now
                                ),
                                now
                            );
                        })
                );
        }
    }

    private ClassificationInputProjection buildProjection(
        CanonicalProblemJpaEntity canonical
    ) {
        JsonNode display =
            readStoredObject(
                canonical.displayJson(),
                "canonical display"
            );

        JsonNode canonicalRoot =
            readStoredObject(
                canonical.canonicalProblemJson(),
                "canonical problem"
            );

        String storedSchemaVersion =
            requiredStoredText(
                canonicalRoot,
                "schemaVersion",
                64
            );

        if (
            !canonical.schemaVersion()
                .equals(storedSchemaVersion)
        ) {
            throw new IllegalStateException(
                "Canonical problem schema metadata does not match stored document"
            );
        }

        JsonNode statements =
            canonicalRoot.get("statements");

        if (
            statements == null
                || !statements.isArray()
                || statements.isEmpty()
        ) {
            throw new IllegalStateException(
                "Canonical problem has no valid statement array"
            );
        }

        return new ClassificationInputProjection(
            ProblemClassificationContract
                .PROJECTION_VERSION,
            canonical.schemaVersion(),
            canonical.problemType(),
            canonical.taskType(),
            requiredStoredText(
                display,
                "normalizedText",
                2000
            ),
            nullableStoredText(
                display,
                "displayLatex",
                2000
            ),
            storedStringArray(
                display,
                "variables",
                16,
                16
            ),
            statements.size(),
            requiredStoredNonNegativeInt(
                display,
                "sourceConstraintCount"
            ),
            requiredStoredNonNegativeInt(
                display,
                "derivedRestrictionCount"
            ),
            requiredStoredBoolean(
                display,
                "reviewRequired"
            )
        );
    }

    private ProblemClassificationStatusResult statusFor(
        ProblemClassificationJobJpaEntity job
    ) {
        Optional<ProblemClassificationJpaEntity>
            classification =
            classificationRepository
                .findByClassificationJobId(
                    job.id()
                );

        List<String> secondarySkillIds =
            classification
                .map(entity ->
                    secondarySkillRepository
                        .findByIdClassificationIdOrderByOrdinalAsc(
                            entity.id()
                        )
                        .stream()
                        .map(
                            ProblemClassificationSecondarySkillJpaEntity::skillId
                        )
                        .toList()
                )
                .orElse(List.of());

        return new ProblemClassificationStatusResult(
            job.id(),
            job.problemSessionId(),
            job.canonicalProblemId(),
            job.canonicalProblemRevision(),

            job.status(),
            job.capability(),
            job.attemptCount(),
            job.maxAttempts(),
            job.lastErrorCode(),
            job.lastFailureClass(),

            classification
                .map(
                    ProblemClassificationJpaEntity::id
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::revision
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::source
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::status
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::reviewReason
                )
                .orElse(null),

            job.ontologyVersion(),
            job.projectionVersion(),
            job.schemaVersion(),

            classification
                .map(
                    ProblemClassificationJpaEntity::subjectId
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::topicId
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::primarySkillId
                )
                .orElse(null),

            secondarySkillIds,

            classification
                .map(
                    ProblemClassificationJpaEntity::difficulty
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::difficultyPolicyVersion
                )
                .orElse(
                    ProblemClassificationContract
                        .DIFFICULTY_POLICY_VERSION
                ),

            classification
                .map(
                    ProblemClassificationJpaEntity::confidenceBand
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::confidencePolicyVersion
                )
                .orElse(
                    ProblemClassificationContract
                        .CONFIDENCE_POLICY_VERSION
                ),

            classification
                .map(
                    ProblemClassificationJpaEntity::confidenceCalibration
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::provider
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::model
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::fallbackUsed
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::providerLatencyMs
                )
                .orElse(null),

            classification
                .map(
                    ProblemClassificationJpaEntity::estimatedCostMicros
                )
                .orElse(null),

            job.createdAt(),
            job.updatedAt(),
            job.completedAt(),

            classification
                .map(
                    ProblemClassificationJpaEntity::createdAt
                )
                .orElse(null)
        );
    }

    private void recordAiSuccess(
        AiProblemClassifyResult aiResult,
        ValidatedProblemClassification validated
    ) {
        if (
            aiResult.provenance()
                .fallbackUsed()
        ) {
            metrics.fallback();
        }

        metrics.providerLatency(
            aiResult.providerLatencyMs()
        );

        metrics.estimatedCost(
            aiResult.usage()
                .estimatedCostMicros()
        );

        metrics.success(
            aiResult.provenance()
                .provider(),
            validated.status().name(),
            ProblemClassificationSource
                .AI
                .name()
        );
    }

    private CanonicalProblemJpaEntity currentCanonical(
        UUID userId,
        UUID problemSessionId
    ) {
        return canonicalRepository
            .findFirstByProblemSessionIdAndUserIdOrderByCanonicalRevisionDesc(
                problemSessionId,
                userId
            )
            .orElseThrow(() ->
                problem(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.CLASSIFICATION_FAILED,
                    "Canonical problem is required before classification",
                    true,
                    "CANONICALIZE"
                )
            );
    }

    private AiRoutePlan requireRouteContract(
        AiRoutePlan routePlan
    ) {
        if (
            routePlan == null
                || routePlan.capability()
                != AiCapability.PROBLEM_CLASSIFY
                || !ProblemClassificationContract
                .SCHEMA_VERSION
                .equals(
                    routePlan.schemaVersion()
                )
                || routePlan.maxAttempts() < 1
                || routePlan.maxResponseBytes() < 1
        ) {
            throw problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.CLASSIFICATION_FAILED,
                "Classification route configuration is invalid",
                true,
                "RETRY"
            );
        }

        return routePlan;
    }

    private void requireRouteMatchesJob(
        AiRoutePlan routePlan,
        ProblemClassificationJobJpaEntity job
    ) {
        if (
            !Objects.equals(
                routePlan.routePolicyVersion(),
                job.routePolicyVersion()
            )
                || !Objects.equals(
                routePlan.promptId(),
                job.promptId()
            )
                || !Objects.equals(
                routePlan.promptVersion(),
                job.promptVersion()
            )
                || !Objects.equals(
                routePlan.schemaVersion(),
                job.schemaVersion()
            )
        ) {
            throw new AiProviderException(
                AiProviderFailureClass.CONFIGURATION_DISABLED,
                false,
                "Classification route changed after job creation"
            );
        }
    }

    private void requireProvenanceMatchesJob(
        AiProblemClassifyResult result,
        ProblemClassificationJobJpaEntity job
    ) {
        if (
            !Objects.equals(
                result.provenance()
                    .routePolicyVersion(),
                job.routePolicyVersion()
            )
                || !Objects.equals(
                result.provenance()
                    .promptId(),
                job.promptId()
            )
                || !Objects.equals(
                result.provenance()
                    .promptVersion(),
                job.promptVersion()
            )
                || !Objects.equals(
                result.provenance()
                    .schemaVersion(),
                job.schemaVersion()
            )
        ) {
            throw new AiProviderException(
                AiProviderFailureClass.SCHEMA_INVALID,
                false,
                "Classification provenance does not match the queued job"
            );
        }
    }

    private boolean isOntologyFailure(
        ProblemClassificationValidationException exception
    ) {
        return switch (exception.failure()) {
            case ONTOLOGY_VERSION_MISMATCH,
                 PRIMARY_SKILL_UNKNOWN,
                 PRIMARY_SKILL_PARENT_INVALID,
                 SECONDARY_SKILL_UNKNOWN,
                 SECONDARY_SKILL_INCOMPATIBLE ->
                true;

            default -> false;
        };
    }

    private JsonNode readStoredObject(
        String json,
        String label
    ) {
        try {
            JsonNode node =
                objectMapper.readTree(json);

            if (
                node == null
                    || !node.isObject()
            ) {
                throw new IllegalStateException(
                    label + " must be a JSON object"
                );
            }

            return node;

        } catch (IllegalStateException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new IllegalStateException(
                "Stored " + label + " JSON is invalid",
                exception
            );
        }
    }

    private String requiredStoredText(
        JsonNode root,
        String field,
        int maxLength
    ) {
        JsonNode value = root.get(field);

        if (
            value == null
                || !value.isTextual()
        ) {
            throw new IllegalStateException(
                "Stored field " + field
                    + " must be textual"
            );
        }

        String text = value.textValue();

        if (
            text == null
                || text.isBlank()
                || text.length() > maxLength
        ) {
            throw new IllegalStateException(
                "Stored field " + field
                    + " is invalid"
            );
        }

        return text;
    }

    private String nullableStoredText(
        JsonNode root,
        String field,
        int maxLength
    ) {
        JsonNode value = root.get(field);

        if (
            value == null
                || value.isNull()
        ) {
            return null;
        }

        if (!value.isTextual()) {
            throw new IllegalStateException(
                "Stored field " + field
                    + " must be textual or null"
            );
        }

        String text = value.textValue();

        if (
            text.length() > maxLength
        ) {
            throw new IllegalStateException(
                "Stored field " + field
                    + " exceeds maximum length"
            );
        }

        return text;
    }

    private List<String> storedStringArray(
        JsonNode root,
        String field,
        int maxItems,
        int maxLength
    ) {
        JsonNode node = root.get(field);

        if (
            node == null
                || !node.isArray()
                || node.size() > maxItems
        ) {
            throw new IllegalStateException(
                "Stored field " + field
                    + " is not a valid bounded array"
            );
        }

        List<String> result =
            new ArrayList<>();

        for (JsonNode value : node) {
            if (!value.isTextual()) {
                throw new IllegalStateException(
                    "Stored field " + field
                        + " contains a non-string value"
                );
            }

            String text = value.textValue();

            if (
                text == null
                    || text.isBlank()
                    || text.length() > maxLength
            ) {
                throw new IllegalStateException(
                    "Stored field " + field
                        + " contains an invalid value"
                );
            }

            result.add(text);
        }

        return List.copyOf(result);
    }

    private int requiredStoredNonNegativeInt(
        JsonNode root,
        String field
    ) {
        JsonNode value = root.get(field);

        if (
            value == null
                || !value.isIntegralNumber()
        ) {
            throw new IllegalStateException(
                "Stored field " + field
                    + " must be an integer"
            );
        }

        int result = value.intValue();

        if (result < 0) {
            throw new IllegalStateException(
                "Stored field " + field
                    + " cannot be negative"
            );
        }

        return result;
    }

    private boolean requiredStoredBoolean(
        JsonNode root,
        String field
    ) {
        JsonNode value = root.get(field);

        if (
            value == null
                || !value.isBoolean()
        ) {
            throw new IllegalStateException(
                "Stored field " + field
                    + " must be boolean"
            );
        }

        return value.booleanValue();
    }

    private String toJson(Object value) {
        try {
            return objectMapper
                .writeValueAsString(value);

        } catch (Exception exception) {
            throw new IllegalStateException(
                "Classification request serialization failed",
                exception
            );
        }
    }

    private Instant nextAttemptAt(
        UUID jobId,
        int attemptCount,
        Instant now
    ) {
        long jitterSeconds =
            Math.floorMod(
                jobId.getLeastSignificantBits(),
                3
            );

        long backoffSeconds =
            Math.min(
                60,
                (long) Math.pow(
                    2,
                    Math.max(1, attemptCount)
                )
            );

        return now.plus(
            Duration.ofSeconds(
                backoffSeconds
                    + jitterSeconds
            )
        );
    }

    private void requireActiveAccount(
        UUID userId
    ) {
        String userStatus =
            jdbcTemplate.query(
                "select status from users where id = ?",
                statement ->
                    statement.setObject(
                        1,
                        userId
                    ),
                resultSet ->
                    resultSet.next()
                        ? resultSet.getString(
                        "status"
                    )
                        : null
            );

        if (!"ACTIVE".equals(userStatus)) {
            throw problem(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account is not active",
                false,
                "SIGN_IN"
            );
        }
    }

    private static ApiProblemException problem(
        HttpStatus status,
        ApiErrorCode code,
        String title,
        boolean recoverable,
        String userAction
    ) {
        return new ApiProblemException(
            status,
            code,
            title,
            recoverable,
            userAction
        );
    }

    private record ClassificationExecutionContext(
        ProblemClassificationJobJpaEntity job,
        CanonicalProblemJpaEntity canonicalProblem,
        int attemptNumber
    ) {
    }
}
