package com.verifiedai.problem.application.classification;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiModelGateway;
import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemClassifyResult;
import com.verifiedai.ai.application.AiProblemNormalizeRequest;
import com.verifiedai.ai.application.AiProblemNormalizeResult;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiUsage;
import com.verifiedai.ai.application.AiVisionParseRequest;
import com.verifiedai.ai.application.AiVisionParseResult;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.asset.ProblemAssetLifecycleContributor;
import com.verifiedai.problem.domain.port.PresignedProblemAssetUpload;
import com.verifiedai.problem.domain.port.ProblemAssetObjectMetadata;
import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.problem.support.ProblemClassificationIntegrationFixture;
import com.verifiedai.problem.support.ProblemClassificationIntegrationFixture.CanonicalFixture;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(
    ProblemClassificationApplicationServiceTest
        .ClassificationTestConfiguration.class
)
@TestPropertySource(
    properties =
        "app.problem-classifier.worker-interval=PT1H"
)
final class ProblemClassificationApplicationServiceTest
    extends PostgresIntegrationTestSupport {

    @Autowired
    ProblemClassificationApplicationService service;

    @Autowired
    ClassificationFakeAiModelGateway aiGateway;

    @Autowired
    ProblemAssetLifecycleContributor lifecycleContributor;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private ProblemClassificationIntegrationFixture fixture;

    @BeforeEach
    void setUp() {
        fixture =
            new ProblemClassificationIntegrationFixture(
                jdbcTemplate
            );

        fixture.clean();
        aiGateway.reset();
    }

    @Test
    void requestIsDurableIdempotentAndWorkerPersistsClassification() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "2x + 3 = 9",
                false
            );

        aiGateway.enqueue(
            classified(
                "MATH.EQUATIONS.LINEAR_ONE_VARIABLE",
                List.of(),
                "EASY"
            )
        );

        ProblemClassificationStatusResult first =
            service.requestClassification(
                userId,
                canonical.sessionId()
            );

        ProblemClassificationStatusResult second =
            service.requestClassification(
                userId,
                canonical.sessionId()
            );

        assertThat(
            first.jobStatus()
        ).isEqualTo("QUEUED");

        assertThat(
            second.classificationJobId()
        ).isEqualTo(
            first.classificationJobId()
        );

        assertThat(
            fixture.count(
                "problem_classification_jobs"
            )
        ).isEqualTo(1);

        assertThat(
            service.runDueClassificationJobs(10)
        ).isEqualTo(1);

        ProblemClassificationStatusResult result =
            service.getClassification(
                userId,
                canonical.sessionId()
            );

        assertThat(result.jobStatus())
            .isEqualTo("SUCCEEDED");

        assertThat(result.classificationSource())
            .isEqualTo("AI");

        assertThat(result.classificationStatus())
            .isEqualTo("CLASSIFIED");

        assertThat(result.primarySkillId())
            .isEqualTo(
                "MATH.EQUATIONS.LINEAR_ONE_VARIABLE"
            );

        assertThat(result.topicId())
            .isEqualTo("MATH.EQUATIONS");

        assertThat(result.subjectId())
            .isEqualTo("MATH");

        assertThat(result.confidenceBand())
            .isEqualTo("MEDIUM");

        assertThat(result.confidenceCalibration())
            .isEqualTo("UNCALIBRATED");

        assertThat(result.classificationRevision())
            .isEqualTo(1);

        assertThat(
            fixture.count(
                "problem_classifications"
            )
        ).isEqualTo(1);
    }

    @Test
    void providerExecutionOccursOutsideDatabaseTransaction() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        aiGateway.enqueue(
            classified(
                "MATH.EQUATIONS.LINEAR_ONE_VARIABLE",
                List.of(
                    "MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS"
                ),
                "MEDIUM"
            )
        );

        service.requestClassification(
            userId,
            canonical.sessionId()
        );

        service.runDueClassificationJobs(
            10
        );

        assertThat(
            aiGateway.transactionStates()
        ).containsExactly(false);

        assertThat(
            jdbcTemplate.queryForObject(
                """
                select ordinal
                from problem_classification_secondary_skills
                """,
                Integer.class
            )
        ).isEqualTo(0);

        assertThat(
            jdbcTemplate.queryForObject(
                """
                select skill_id
                from problem_classification_secondary_skills
                """,
                String.class
            )
        ).isEqualTo(
            "MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS"
        );
    }

    @Test
    void upstreamRiskProducesSystemReviewRequiredWithoutAiCall() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x? + 1 = 2",
                true
            );

        service.requestClassification(
            userId,
            canonical.sessionId()
        );

        service.runDueClassificationJobs(
            10
        );

        ProblemClassificationStatusResult result =
            service.getClassification(
                userId,
                canonical.sessionId()
            );

        assertThat(result.jobStatus())
            .isEqualTo("SUCCEEDED");

        assertThat(result.classificationSource())
            .isEqualTo("SYSTEM");

        assertThat(result.classificationStatus())
            .isEqualTo("REVIEW_REQUIRED");

        assertThat(result.reviewReason())
            .isEqualTo("UPSTREAM_RISK");

        assertThat(result.confidenceBand())
            .isEqualTo("LOW");

        assertThat(aiGateway.classificationCalls())
            .isZero();
    }

    @Test
    void unsupportedCanonicalPairProducesSystemUnsupportedWithoutAiCall() {
        UUID userId =
            fixture.insertUser();

        /*
         * V012 accepts both enum values independently.
         * Classification policy must still reject the
         * invalid pair EQUATION:EVALUATE.
         */
        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "EVALUATE",
                "x + 1",
                false
            );

        service.requestClassification(
            userId,
            canonical.sessionId()
        );

        service.runDueClassificationJobs(
            10
        );

        ProblemClassificationStatusResult result =
            service.getClassification(
                userId,
                canonical.sessionId()
            );

        assertThat(result.jobStatus())
            .isEqualTo("SUCCEEDED");

        assertThat(result.classificationSource())
            .isEqualTo("SYSTEM");

        assertThat(result.classificationStatus())
            .isEqualTo("UNSUPPORTED");

        assertThat(result.primarySkillId())
            .isNull();

        assertThat(result.confidenceBand())
            .isEqualTo("UNKNOWN");

        assertThat(aiGateway.classificationCalls())
            .isZero();
    }

    @Test
    void malformedProviderOutputBecomesRetryableFailureWithoutRevision() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        aiGateway.enqueue(
            """
            {
              "unexpected": true
            }
            """
        );

        service.requestClassification(
            userId,
            canonical.sessionId()
        );

        service.runDueClassificationJobs(
            10
        );

        ProblemClassificationStatusResult result =
            service.getClassification(
                userId,
                canonical.sessionId()
            );

        assertThat(result.jobStatus())
            .isEqualTo(
                "FAILED_RETRYABLE"
            );

        assertThat(result.lastErrorCode())
            .isEqualTo(
                "CLASSIFICATION_SCHEMA_INVALID"
            );

        assertThat(result.lastFailureClass())
            .isEqualTo(
                "SCHEMA_INVALID"
            );

        assertThat(
            fixture.count(
                "problem_classifications"
            )
        ).isZero();
    }

    @Test
    void unknownTaxonomyIdIsTerminalOntologyFailure() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "unknown-skill-test",
                false
            );

        aiGateway.enqueue(
            classified(
                "FAKE.NONEXISTENT.SKILL",
                List.of(),
                "MEDIUM"
            )
        );

        service.requestClassification(
            userId,
            canonical.sessionId()
        );

        service.runDueClassificationJobs(
            10
        );

        ProblemClassificationStatusResult result =
            service.getClassification(
                userId,
                canonical.sessionId()
            );

        assertThat(result.jobStatus())
            .isEqualTo(
                "FAILED_TERMINAL"
            );

        assertThat(result.lastErrorCode())
            .isEqualTo(
                "CLASSIFICATION_ONTOLOGY_INVALID"
            );

        assertThat(result.lastFailureClass())
            .isEqualTo(
                "PRIMARY_SKILL_UNKNOWN"
            );

        assertThat(
            fixture.count(
                "problem_classifications"
            )
        ).isZero();
    }

    @Test
    void validTaxonomySkillOutsideCandidateSetIsRejected() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        aiGateway.enqueue(
            classified(
                "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                List.of(),
                "EASY"
            )
        );

        service.requestClassification(
            userId,
            canonical.sessionId()
        );

        service.runDueClassificationJobs(
            10
        );

        ProblemClassificationStatusResult result =
            service.getClassification(
                userId,
                canonical.sessionId()
            );

        assertThat(result.jobStatus())
            .isEqualTo(
                "FAILED_TERMINAL"
            );

        assertThat(result.lastFailureClass())
            .isEqualTo(
                "CANDIDATE_INVALID"
            );

        assertThat(
            fixture.count(
                "problem_classifications"
            )
        ).isZero();
    }

    @Test
    void retryableProviderFailureRemainsRetryable() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        aiGateway.enqueueFailure(
            new AiProviderException(
                AiProviderFailureClass.TIMEOUT,
                true,
                "timeout"
            )
        );

        service.requestClassification(
            userId,
            canonical.sessionId()
        );

        service.runDueClassificationJobs(
            10
        );

        ProblemClassificationStatusResult result =
            service.getClassification(
                userId,
                canonical.sessionId()
            );

        assertThat(result.jobStatus())
            .isEqualTo(
                "FAILED_RETRYABLE"
            );

        assertThat(result.lastFailureClass())
            .isEqualTo("TIMEOUT");
    }

    @Test
    void terminalProviderFailureBecomesTerminal() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        aiGateway.enqueueFailure(
            new AiProviderException(
                AiProviderFailureClass.INVALID_AUTH,
                false,
                "auth failure"
            )
        );

        service.requestClassification(
            userId,
            canonical.sessionId()
        );

        service.runDueClassificationJobs(
            10
        );

        ProblemClassificationStatusResult result =
            service.getClassification(
                userId,
                canonical.sessionId()
            );

        assertThat(result.jobStatus())
            .isEqualTo(
                "FAILED_TERMINAL"
            );

        assertThat(result.lastFailureClass())
            .isEqualTo(
                "INVALID_AUTH"
            );
    }

    @Test
    void staleCanonicalCannotBecomeAuthoritative() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture first =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        ProblemClassificationStatusResult request =
            service.requestClassification(
                userId,
                first.sessionId()
            );

        fixture.addCanonicalRevision(
            userId,
            first.sessionId(),
            2,
            "EQUATION",
            "SOLVE_EQUATION",
            "x + 2 = 5",
            false
        );

        assertThat(
            service.runClassificationJob(
                request.classificationJobId()
            )
        ).isFalse();

        ProblemClassificationStatusResult current =
            service.getClassification(
                userId,
                first.sessionId()
            );

        /*
         * Current canonical revision has no classification.
         * Old job must not become authoritative.
         */
        assertThat(current.jobStatus())
            .isEqualTo("NOT_STARTED");

        assertThat(
            jdbcTemplate.queryForObject(
                """
                select status
                from problem_classification_jobs
                where id = ?
                """,
                String.class,
                request.classificationJobId()
            )
        ).isEqualTo(
            "FAILED_TERMINAL"
        );

        assertThat(
            jdbcTemplate.queryForObject(
                """
                select last_failure_class
                from problem_classification_jobs
                where id = ?
                """,
                String.class,
                request.classificationJobId()
            )
        ).isEqualTo(
            "STALE_CANONICAL"
        );

        assertThat(
            fixture.count(
                "problem_classifications"
            )
        ).isZero();
    }

    @Test
    void concurrentSameFingerprintRequestsCreateOneLogicalJob()
        throws Exception {

        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        var executor =
            Executors.newFixedThreadPool(2);

        try {
            var first =
                executor.submit(
                    () ->
                        service.requestClassification(
                            userId,
                            canonical.sessionId()
                        )
                );

            var second =
                executor.submit(
                    () ->
                        service.requestClassification(
                            userId,
                            canonical.sessionId()
                        )
                );

            UUID firstId =
                first.get()
                    .classificationJobId();

            UUID secondId =
                second.get()
                    .classificationJobId();

            assertThat(firstId)
                .isEqualTo(secondId);

            assertThat(
                fixture.count(
                    "problem_classification_jobs"
                )
            ).isEqualTo(1);

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void changedPromptCreatesNewImmutableClassificationRevision() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        aiGateway.enqueue(
            classified(
                "MATH.EQUATIONS.LINEAR_ONE_VARIABLE",
                List.of(),
                "EASY"
            )
        );

        service.requestClassification(
            userId,
            canonical.sessionId()
        );

        service.runDueClassificationJobs(
            10
        );

        aiGateway.setPromptVersion(
            "v002"
        );

        aiGateway.enqueue(
            classified(
                "MATH.EQUATIONS.LINEAR_ONE_VARIABLE",
                List.of(),
                "MEDIUM"
            )
        );

        service.requestClassification(
            userId,
            canonical.sessionId()
        );

        service.runDueClassificationJobs(
            10
        );

        List<Integer> revisions =
            jdbcTemplate.queryForList(
                """
                select revision
                from problem_classifications
                where canonical_problem_id = ?
                order by revision
                """,
                Integer.class,
                canonical.canonicalProblemId()
            );

        assertThat(revisions)
            .containsExactly(
                1,
                2
            );
    }

    @Test
    void staleRunningJobIsRecovered() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        ProblemClassificationStatusResult request =
            service.requestClassification(
                userId,
                canonical.sessionId()
            );

        Instant stale =
            Instant.now()
                .minus(
                    Duration.ofHours(1)
                );

        jdbcTemplate.update(
            """
            update problem_classification_jobs
            set
                status = 'RUNNING',
                attempt_count = 1,
                started_at = ?,
                updated_at = ?,
                next_attempt_at = ?
            where id = ?
            """,
            java.sql.Timestamp.from(stale),
            java.sql.Timestamp.from(stale),
            java.sql.Timestamp.from(stale),
            request.classificationJobId()
        );

        service.runDueClassificationJobs(
            10
        );

        String status =
            jdbcTemplate.queryForObject(
                """
                select status
                from problem_classification_jobs
                where id = ?
                """,
                String.class,
                request.classificationJobId()
            );

        String failureClass =
            jdbcTemplate.queryForObject(
                """
                select last_failure_class
                from problem_classification_jobs
                where id = ?
                """,
                String.class,
                request.classificationJobId()
            );

        assertThat(status)
            .isIn(
                "FAILED_RETRYABLE",
                "RUNNING",
                "SUCCEEDED"
            );

        /*
         * If the recovered job's backoff expires inside
         * this test invocation it may immediately resume.
         * What must never happen is loss of the durable job.
         */
        assertThat(
            fixture.count(
                "problem_classification_jobs"
            )
        ).isEqualTo(1);

        if (
            "FAILED_RETRYABLE"
                .equals(status)
        ) {
            assertThat(
                failureClass
            ).isEqualTo("TIMEOUT");
        }
    }

    @Test
    void wrongUserIsBolaProtectedAndCreatesNoJob() {
        UUID ownerId =
            fixture.insertUser();

        UUID attackerId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                ownerId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        assertThatThrownBy(() ->
            service.requestClassification(
                attackerId,
                canonical.sessionId()
            )
        )
            .isInstanceOfSatisfying(
                ApiProblemException.class,
                exception ->
                    assertThat(
                        exception.code()
                    ).isEqualTo(
                        ApiErrorCode
                            .RESOURCE_FORBIDDEN
                    )
            );

        assertThat(
            fixture.count(
                "problem_classification_jobs"
            )
        ).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void privacyExportIncludesClassificationWithoutRawProviderOutputAndDeletionCascades() {
        UUID userId =
            fixture.insertUser();

        CanonicalFixture canonical =
            fixture.insertCanonical(
                userId,
                "EQUATION",
                "SOLVE_EQUATION",
                "x + 1 = 2",
                false
            );

        aiGateway.enqueue(
            classified(
                "MATH.EQUATIONS.LINEAR_ONE_VARIABLE",
                List.of(
                    "MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS"
                ),
                "EASY"
            )
        );

        service.requestClassification(
            userId,
            canonical.sessionId()
        );

        service.runDueClassificationJobs(
            10
        );

        Map<String, Object> export =
            lifecycleContributor
                .exportUserData(
                    userId
                );

        assertThat(
            (List<Map<String, Object>>)
                export.get(
                    "problemClassificationJobs"
                )
        ).hasSize(1);

        assertThat(
            (List<Map<String, Object>>)
                export.get(
                    "problemClassifications"
                )
        ).hasSize(1);

        assertThat(
            (List<Map<String, Object>>)
                export.get(
                    "problemClassificationSecondarySkills"
                )
        ).hasSize(1);

        assertThat(
            export.get(
                "rawProblemClassifierOutputIncluded"
            )
        ).isEqualTo(false);

        lifecycleContributor
            .deleteUserData(
                userId,
                Instant.now()
            );

        assertThat(
            fixture.count(
                "problem_classification_secondary_skills"
            )
        ).isZero();

        assertThat(
            fixture.count(
                "problem_classifications"
            )
        ).isZero();

        assertThat(
            fixture.count(
                "problem_classification_jobs"
            )
        ).isZero();
    }

    private static String classified(
        String primarySkillId,
        List<String> secondarySkillIds,
        String difficulty
    ) {
        String secondaries =
            secondarySkillIds
                .stream()
                .map(value ->
                    "\"" + value + "\""
                )
                .reduce(
                    (left, right) ->
                        left + "," + right
                )
                .orElse("");

        return """
            {
              "schemaVersion": "problem-classification-v1",
              "ontologyVersion": "curriculum-v1-seed",
              "status": "CLASSIFIED",
              "primarySkillId": "%s",
              "secondarySkillIds": [%s],
              "difficulty": "%s",
              "reviewReason": null
            }
            """.formatted(
            primarySkillId,
            secondaries,
            difficulty
        );
    }

    @TestConfiguration
    static class ClassificationTestConfiguration {

        @Bean
        @Primary
        ClassificationFakeAiModelGateway
        classificationFakeAiModelGateway() {

            return new ClassificationFakeAiModelGateway();
        }

        @Bean
        @Primary
        ProblemAssetStorage
        classificationTestProblemAssetStorage() {

            return new ProblemAssetStorage() {

                @Override
                public PresignedProblemAssetUpload presignPut(
                    String objectKey,
                    String contentType,
                    long sizeBytes,
                    Duration ttl
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ProblemAssetObjectMetadata head(
                    String objectKey
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public byte[] readBytes(
                    String objectKey,
                    long maxSizeBytes
                ) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void putObject(
                    String objectKey,
                    String contentType,
                    byte[] bytes
                ) {
                    // Not needed by this fixture.
                }

                @Override
                public String sha256Hex(
                    String objectKey
                ) {
                    return "0".repeat(64);
                }

                @Override
                public void deleteIfExists(
                    String objectKey
                ) {
                    // Intentional no-op for lifecycle tests.
                }
            };
        }
    }

    static final class ClassificationFakeAiModelGateway
        implements AiModelGateway {

        private final Queue<Object> outcomes =
            new ConcurrentLinkedQueue<>();

        private final AtomicInteger calls =
            new AtomicInteger();

        private final List<Boolean>
            transactionStates =
            new CopyOnWriteArrayList<>();

        private volatile String promptVersion =
            "v001";

        @Override
        public AiRoutePlan routePlan(
            AiCapability capability
        ) {
            if (
                capability
                    != AiCapability.PROBLEM_CLASSIFY
            ) {
                throw new UnsupportedOperationException();
            }

            return new AiRoutePlan(
                AiCapability.PROBLEM_CLASSIFY,
                "problem-classifier-route-v1",
                "TEST_PROVIDER",
                null,
                "problem-classifier",
                promptVersion,
                "problem-classification-v1",
                Duration.ofSeconds(5),
                2,
                16_384,
                10_000,
                "test-pricing-v1"
            );
        }

        @Override
        public AiVisionParseResult executeVisionParse(
            AiVisionParseRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiProblemNormalizeResult executeProblemNormalize(
            AiProblemNormalizeRequest request
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AiProblemClassifyResult executeProblemClassify(
            AiProblemClassifyRequest request
        ) {
            calls.incrementAndGet();

            transactionStates.add(
                TransactionSynchronizationManager
                    .isActualTransactionActive()
            );

            Object outcome =
                outcomes.poll();

            if (outcome == null) {
                throw new IllegalStateException(
                    "No classification fixture outcome queued"
                );
            }

            if (
                outcome
                    instanceof RuntimeException exception
            ) {
                throw exception;
            }

            String rawJson =
                (String) outcome;

            AiRoutePlan route =
                routePlan(
                    AiCapability.PROBLEM_CLASSIFY
                );

            return new AiProblemClassifyResult(
                rawJson,
                new AiProvenance(
                    "TEST_PROVIDER",
                    "test-classifier-v1",
                    route.routePolicyVersion(),
                    route.promptId(),
                    route.promptVersion(),
                    route.schemaVersion(),
                    "request-id",
                    "response-id",
                    false
                ),
                new AiUsage(
                    10,
                    5,
                    null,
                    1,
                    100,
                    "USD",
                    route.pricingVersion()
                ),
                7
            );
        }

        void enqueue(
            String rawOutput
        ) {
            outcomes.add(
                rawOutput
            );
        }

        void enqueueFailure(
            RuntimeException exception
        ) {
            outcomes.add(
                exception
            );
        }

        void setPromptVersion(
            String promptVersion
        ) {
            this.promptVersion =
                promptVersion;
        }

        int classificationCalls() {
            return calls.get();
        }

        List<Boolean> transactionStates() {
            return List.copyOf(
                transactionStates
            );
        }

        void reset() {
            outcomes.clear();
            calls.set(0);
            transactionStates.clear();
            promptVersion = "v001";
        }
    }
}
