package com.verifiedai.problem.application.parse;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiModelGateway;
import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemClassifyResult;
import com.verifiedai.ai.application.AiProblemNormalizeRequest;
import com.verifiedai.ai.application.AiProblemNormalizeResult;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiUsage;
import com.verifiedai.ai.application.AiVisionParseRequest;
import com.verifiedai.ai.application.AiVisionParseResult;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.asset.ProblemAssetLifecycleContributor;
import com.verifiedai.problem.domain.port.PresignedProblemAssetUpload;
import com.verifiedai.problem.domain.port.ProblemAssetObjectMetadata;
import com.verifiedai.problem.domain.port.ProblemAssetObjectNotFoundException;
import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.net.URI;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(
    ProblemParseApplicationServiceTest.ParseTestConfiguration.class
)
@TestPropertySource(
    properties = "app.problem-parser.worker-interval=PT1H"
)
final class ProblemParseApplicationServiceTest
    extends PostgresIntegrationTestSupport {

    private static final Instant NOW =
        Instant.parse("2026-08-09T00:00:00Z");

    @Autowired
    ProblemParseApplicationService parseApplicationService;

    @Autowired
    ParseFakeAiModelGateway aiGateway;

    @Autowired
    ProblemAssetLifecycleContributor lifecycleContributor;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute(
            """
            truncate table
                problem_parses,
                problem_parse_jobs,
                recognition_evidence,
                recognition_jobs,
                problem_asset_quality_evidence,
                problem_asset_derivatives,
                problem_assets,
                problem_sessions,
                entitlements,
                learning_profiles,
                auth_security_events,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """
        );

        aiGateway.reset();
    }

    @Test
    void requestParseCreatesIdempotentJobAndWorkerPersistsSupportedRevision() {
        UUID userId = insertUser();

        UUID sessionId =
            insertRecognizedProblem(
                userId,
                "x^2 + 3x = 10",
                false,
                "PASS"
            );

        aiGateway.enqueue(
            supportedEquationOutput()
        );

        ProblemParseStatusResult first =
            parseApplicationService.requestParse(
                userId,
                sessionId
            );

        ProblemParseStatusResult second =
            parseApplicationService.requestParse(
                userId,
                sessionId
            );

        assertThat(
            first.jobStatus()
        ).isEqualTo("QUEUED");

        assertThat(
            second.parseJobId()
        ).isEqualTo(
            first.parseJobId()
        );

        assertThat(
            count("problem_parse_jobs")
        ).isEqualTo(1);

        int completed =
            parseApplicationService
                .runDueParseJobs(10);

        assertThat(
            completed
        ).isEqualTo(1);

        ProblemParseStatusResult result =
            parseApplicationService.getParse(
                userId,
                sessionId
            );

        assertThat(
            result.jobStatus()
        ).isEqualTo("SUCCEEDED");

        assertThat(
            result.supportStatus()
        ).isEqualTo("SUPPORTED");

        assertThat(
            result.parseRevision()
        ).isEqualTo(1);

        assertThat(
            result.recognitionEvidenceId()
        ).isNotNull();

        assertThat(
            result.recognitionEvidenceRevision()
        ).isEqualTo(1);

        assertThat(
            result.provider()
        ).isEqualTo("TEST_PROVIDER");

        assertThat(
            result.model()
        ).isEqualTo("test-parser-v1");

        assertThat(
            result.promptId()
        ).isEqualTo("problem-parser");

        assertThat(
            result.schemaVersion()
        ).isEqualTo("problem-parse-v1");

        assertThat(
            result.normalizedProblemJson()
        )
            .contains(
                "\"taskType\": \"SOLVE_EQUATION\""
            )
            .doesNotContain(
                "safeVerifierAst"
            )
            .doesNotContain(
                "primarySkill"
            );

        assertThat(
            jsonText(
                "problem_parses",
                "raw_output_jsonb",
                "1 = 1"
            )
        ).isNotEqualTo(
            jsonText(
                "problem_parses",
                "normalized_problem_jsonb",
                "1 = 1"
            )
        );
    }

    @Test
    void unsupportedProblemPersistsFirstClassUnsupportedOutcomeWithoutRetry() {
        UUID userId = insertUser();

        UUID sessionId =
            insertRecognizedProblem(
                userId,
                "Find eigenvalues of matrix A",
                false,
                "PASS"
            );

        aiGateway.enqueue(
            unsupportedOutput(
                "UNSUPPORTED_STRUCTURE"
            )
        );

        parseApplicationService.requestParse(
            userId,
            sessionId
        );

        parseApplicationService
            .runDueParseJobs(10);

        ProblemParseStatusResult result =
            parseApplicationService.getParse(
                userId,
                sessionId
            );

        assertThat(
            result.jobStatus()
        ).isEqualTo("UNSUPPORTED");

        assertThat(
            result.supportStatus()
        ).isEqualTo("UNSUPPORTED");

        assertThat(
            result.unsupportedReason()
        ).isEqualTo(
            "UNSUPPORTED_STRUCTURE"
        );

        assertThat(
            result.attemptCount()
        ).isEqualTo(1);

        assertThat(
            count("problem_parses")
        ).isEqualTo(1);
    }

    @Test
    void schemaInvalidOutputRetriesWithoutDurableParseRevision() {
        UUID userId = insertUser();

        UUID sessionId =
            insertRecognizedProblem(
                userId,
                "x + 1 = 2",
                false,
                "PASS"
            );

        aiGateway.enqueue(
            schemaInvalidOutput()
        );

        parseApplicationService.requestParse(
            userId,
            sessionId
        );

        parseApplicationService
            .runDueParseJobs(10);

        ProblemParseStatusResult result =
            parseApplicationService.getParse(
                userId,
                sessionId
            );

        assertThat(
            result.jobStatus()
        ).isEqualTo(
            "FAILED_RETRYABLE"
        );

        assertThat(
            result.lastErrorCode()
        ).isEqualTo(
            "PROBLEM_PARSE_FAILED"
        );

        assertThat(
            result.lastFailureClass()
        ).isEqualTo(
            "SCHEMA_INVALID"
        );

        assertThat(
            count("problem_parses")
        ).isEqualTo(0);
    }

    @Test
    void semanticInvalidVariableMismatchFailsWithoutDurableParseRevision() {
        UUID userId = insertUser();

        UUID sessionId =
            insertRecognizedProblem(
                userId,
                "x + 2 = 5",
                false,
                "PASS"
            );

        aiGateway.enqueue(
            semanticInvalidVariableMismatchOutput()
        );

        parseApplicationService.requestParse(
            userId,
            sessionId
        );

        parseApplicationService
            .runDueParseJobs(10);

        ProblemParseStatusResult result =
            parseApplicationService.getParse(
                userId,
                sessionId
            );

        assertThat(
            result.jobStatus()
        ).isEqualTo(
            "FAILED_TERMINAL"
        );

        assertThat(
            result.lastErrorCode()
        ).isEqualTo(
            "PROBLEM_PARSE_FAILED"
        );

        assertThat(
            result.lastFailureClass()
        ).isEqualTo(
            "SEMANTIC_INVALID"
        );

        assertThat(
            count("problem_parses")
        ).isEqualTo(0);
    }

    @Test
    void semanticInvalidEquationTaskWithoutEquationRelationFailsWithoutDurableParseRevision() {
        UUID userId = insertUser();

        UUID sessionId =
            insertRecognizedProblem(
                userId,
                "x + 2",
                false,
                "PASS"
            );

        aiGateway.enqueue(
            semanticInvalidEquationMissingRelationOutput()
        );

        parseApplicationService.requestParse(
            userId,
            sessionId
        );

        parseApplicationService
            .runDueParseJobs(10);

        ProblemParseStatusResult result =
            parseApplicationService.getParse(
                userId,
                sessionId
            );

        assertThat(
            result.jobStatus()
        ).isEqualTo(
            "FAILED_TERMINAL"
        );

        assertThat(
            result.lastErrorCode()
        ).isEqualTo(
            "PROBLEM_PARSE_FAILED"
        );

        assertThat(
            result.lastFailureClass()
        ).isEqualTo(
            "SEMANTIC_INVALID"
        );

        assertThat(
            count("problem_parses")
        ).isEqualTo(0);
    }

    @Test
    void semanticInvalidInequalityTaskWithoutInequalityRelationFailsWithoutDurableParseRevision() {
        UUID userId = insertUser();

        UUID sessionId =
            insertRecognizedProblem(
                userId,
                "x + 2 = 5",
                false,
                "PASS"
            );

        aiGateway.enqueue(
            semanticInvalidInequalityMissingRelationOutput()
        );

        parseApplicationService.requestParse(
            userId,
            sessionId
        );

        parseApplicationService
            .runDueParseJobs(10);

        ProblemParseStatusResult result =
            parseApplicationService.getParse(
                userId,
                sessionId
            );

        assertThat(
            result.jobStatus()
        ).isEqualTo(
            "FAILED_TERMINAL"
        );

        assertThat(
            result.lastErrorCode()
        ).isEqualTo(
            "PROBLEM_PARSE_FAILED"
        );

        assertThat(
            result.lastFailureClass()
        ).isEqualTo(
            "SEMANTIC_INVALID"
        );

        assertThat(
            count("problem_parses")
        ).isEqualTo(0);
    }

    @Test
    void nonExplicitAssumptionFailsWithoutDurableParseRevision() {
        UUID userId = insertUser();

        UUID sessionId =
            insertRecognizedProblem(
                userId,
                "x + 2 = 5",
                false,
                "PASS"
            );

        aiGateway.enqueue(
            nonExplicitAssumptionOutput()
        );

        parseApplicationService.requestParse(
            userId,
            sessionId
        );

        parseApplicationService
            .runDueParseJobs(10);

        ProblemParseStatusResult result =
            parseApplicationService.getParse(
                userId,
                sessionId
            );

        assertThat(
            result.jobStatus()
        ).isEqualTo(
            "FAILED_RETRYABLE"
        );

        assertThat(
            result.lastErrorCode()
        ).isEqualTo(
            "PROBLEM_PARSE_FAILED"
        );

        assertThat(
            result.lastFailureClass()
        ).isEqualTo(
            "SCHEMA_INVALID"
        );

        assertThat(
            count("problem_parses")
        ).isEqualTo(0);
    }

    @Test
    void ambiguousRecognitionEvidenceForcesReviewRequiredParseState() {
        UUID userId = insertUser();

        UUID sessionId =
            insertRecognizedProblem(
                userId,
                "x^2? + 3x = 10",
                true,
                "PASS"
            );

        aiGateway.enqueue(
            supportedEquationOutput()
        );

        parseApplicationService.requestParse(
            userId,
            sessionId
        );

        parseApplicationService
            .runDueParseJobs(10);

        ProblemParseStatusResult result =
            parseApplicationService.getParse(
                userId,
                sessionId
            );

        assertThat(
            result.jobStatus()
        ).isEqualTo("SUCCEEDED");

        assertThat(
            result.supportStatus()
        ).isEqualTo(
            "REVIEW_REQUIRED"
        );

        assertThat(
            result.reviewRequired()
        ).isTrue();

        assertThat(
            result.normalizedProblemJson()
        ).contains(
            "ambiguous exponent"
        );
    }

    @Test
    void parseRejectsWrongUserWithoutCreatingJob() {
        UUID ownerId = insertUser();
        UUID attackerId = insertUser();

        UUID sessionId =
            insertRecognizedProblem(
                ownerId,
                "x + 1 = 2",
                false,
                "PASS"
            );

        assertThatThrownBy(
            () ->
                parseApplicationService
                    .requestParse(
                        attackerId,
                        sessionId
                    )
        )
            .isInstanceOf(
                ApiProblemException.class
            )
            .extracting(
                exception ->
                    ((ApiProblemException) exception)
                        .code()
            )
            .isEqualTo(
                ApiErrorCode.RESOURCE_FORBIDDEN
            );

        assertThat(
            count("problem_parse_jobs")
        ).isEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void accountExportIncludesNormalizedParseAndDeletionCascadesParserRows() {
        UUID userId = insertUser();

        UUID sessionId =
            insertRecognizedProblem(
                userId,
                "x + 1 = 2",
                false,
                "PASS"
            );

        aiGateway.enqueue(
            supportedEquationOutput()
        );

        parseApplicationService.requestParse(
            userId,
            sessionId
        );

        parseApplicationService
            .runDueParseJobs(10);

        Map<String, Object> export =
            lifecycleContributor
                .exportUserData(userId);

        assertThat(
            (List<Map<String, Object>>)
                export.get("problemParseJobs")
        ).hasSize(1);

        assertThat(
            (List<Map<String, Object>>)
                export.get("problemParses")
        ).hasSize(1);

        assertThat(
            export.get(
                "rawProblemParserOutputIncluded"
            )
        ).isEqualTo(false);

        lifecycleContributor
            .deleteUserData(
                userId,
                NOW
            );

        assertThat(
            count("problem_parse_jobs")
        ).isEqualTo(0);

        assertThat(
            count("problem_parses")
        ).isEqualTo(0);
    }

    @Test
    void concurrentSuccessfulParseWritesReceiveDistinctRevisionNumbers()
        throws Exception {

        UUID userId = insertUser();

        UUID sessionId =
            insertRecognizedProblem(
                userId,
                "x + 1 = 2",
                false,
                "PASS"
            );

        UUID evidenceId =
            jdbcTemplate.queryForObject(
                "select id from recognition_evidence",
                UUID.class
            );

        UUID jobOne =
            insertQueuedParseJob(
                userId,
                sessionId,
                evidenceId,
                "v001"
            );

        UUID jobTwo =
            insertQueuedParseJob(
                userId,
                sessionId,
                evidenceId,
                "v002"
            );

        aiGateway.enqueue(
            supportedEquationOutput()
        );

        aiGateway.enqueue(
            supportedEquationOutput()
        );

        var executor =
            Executors.newFixedThreadPool(2);

        try {
            List<Callable<Boolean>> tasks =
                List.of(
                    () ->
                        parseApplicationService
                            .runParseJob(jobOne),
                    () ->
                        parseApplicationService
                            .runParseJob(jobTwo)
                );

            for (
                var future :
                executor.invokeAll(tasks)
            ) {
                assertThat(
                    future.get()
                ).isTrue();
            }
        } finally {
            executor.shutdownNow();
        }

        List<Integer> revisions =
            jdbcTemplate.queryForList(
                """
                select revision
                from problem_parses
                order by revision
                """,
                Integer.class
            );

        assertThat(
            revisions
        ).containsExactly(
            1,
            2
        );
    }

    private UUID insertUser() {
        UUID userId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            insert into users (
                id,
                status,
                created_at,
                updated_at
            )
            values (?, 'ACTIVE', ?, ?)
            """,
            userId,
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );

        return userId;
    }

    private UUID insertRecognizedProblem(
        UUID userId,
        String text,
        boolean ambiguous,
        String qualityOutcome
    ) {
        UUID sessionId =
            UUID.randomUUID();

        UUID assetId =
            UUID.randomUUID();

        UUID derivativeId =
            UUID.randomUUID();

        UUID recognitionJobId =
            UUID.randomUUID();

        UUID evidenceId =
            UUID.randomUUID();

        byte[] sourceBytes =
            "source-image".getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            );

        byte[] derivativeBytes =
            "ocr-optimized-image".getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            );

        String sourceKey =
            "problem-assets/"
                + sessionId
                + "/"
                + assetId
                + "/original";

        String derivativeKey =
            "problem-assets/"
                + sessionId
                + "/"
                + assetId
                + "/derivatives/"
                + derivativeId
                + "/ocr-optimized.jpg";

        jdbcTemplate.update(
            """
            insert into problem_sessions (
                id,
                user_id,
                status,
                input_mode,
                created_at,
                updated_at
            )
            values (
                ?,
                ?,
                'ASSET_UPLOADED',
                'CAMERA',
                ?,
                ?
            )
            """,
            sessionId,
            userId,
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );

        jdbcTemplate.update(
            """
            insert into problem_assets (
                id,
                problem_session_id,
                user_id,
                source_type,
                asset_kind,
                status,
                object_key,
                content_type,
                size_bytes,
                checksum_algorithm,
                checksum_value,
                crop_x,
                crop_y,
                crop_width,
                crop_height,
                image_width,
                image_height,
                page_count,
                retention_class,
                upload_expires_at,
                available_at,
                created_at,
                updated_at,
                reservation_idempotency_key,
                reservation_request_hash
            )
            values (
                ?,
                ?,
                ?,
                'CAMERA',
                'IMAGE',
                'AVAILABLE',
                ?,
                'image/jpeg',
                ?,
                'SHA-256',
                ?,
                0,
                0,
                1,
                1,
                1200,
                900,
                null,
                'TEMPORARY_RAW',
                ?,
                ?,
                ?,
                ?,
                ?,
                ?
            )
            """,
            assetId,
            sessionId,
            userId,
            sourceKey,
            (long) sourceBytes.length,
            sha256Hex(sourceBytes),
            Timestamp.from(
                NOW.plus(
                    Duration.ofMinutes(15)
                )
            ),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            "reserve-" + assetId,
            "0".repeat(64)
        );

        jdbcTemplate.update(
            """
            insert into problem_asset_derivatives (
                id,
                source_asset_id,
                problem_session_id,
                user_id,
                derivative_kind,
                status,
                selected_for_recognition,
                object_key,
                content_type,
                size_bytes,
                checksum_algorithm,
                checksum_value,
                width,
                height,
                source_width,
                source_height,
                crop_x,
                crop_y,
                crop_width,
                crop_height,
                processor_name,
                processor_version,
                configuration_version,
                orientation_normalized,
                perspective_applied,
                contrast_normalized,
                resized,
                quality_outcome,
                created_at,
                updated_at,
                completed_at
            )
            values (
                ?,
                ?,
                ?,
                ?,
                'OCR_OPTIMIZED',
                'READY',
                true,
                ?,
                'image/jpeg',
                ?,
                'SHA-256',
                ?,
                1200,
                900,
                1200,
                900,
                0,
                0,
                1,
                1,
                'DOCUMENT_PREPROCESSOR',
                '1.0',
                'capture-quality-v1',
                false,
                false,
                false,
                false,
                ?,
                ?,
                ?,
                ?
            )
            """,
            derivativeId,
            assetId,
            sessionId,
            userId,
            derivativeKey,
            (long) derivativeBytes.length,
            sha256Hex(derivativeBytes),
            qualityOutcome,
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );

        jdbcTemplate.update(
            """
            insert into recognition_jobs (
                id,
                user_id,
                problem_session_id,
                source_asset_id,
                input_derivative_id,
                status,
                capability,
                prompt_id,
                prompt_version,
                schema_version,
                route_policy_version,
                attempt_count,
                max_attempts,
                next_attempt_at,
                review_required,
                created_at,
                updated_at,
                started_at,
                completed_at
            )
            values (
                ?,
                ?,
                ?,
                ?,
                ?,
                'SUCCEEDED',
                'VISION_PARSE',
                'vision-recognition',
                'v001',
                'recognition-evidence-v1',
                'vision-route-v1',
                1,
                2,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?
            )
            """,
            recognitionJobId,
            userId,
            sessionId,
            assetId,
            derivativeId,
            Timestamp.from(NOW),
            ambiguous,
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );

        jdbcTemplate.update(
            """
            insert into recognition_evidence (
                id,
                recognition_job_id,
                user_id,
                problem_session_id,
                source_asset_id,
                input_derivative_id,
                revision,
                capability,
                schema_version,
                raw_output_jsonb,
                normalized_evidence_jsonb,
                upstream_quality_evidence_jsonb,
                provider,
                model,
                route_policy_version,
                prompt_id,
                prompt_version,
                input_tokens,
                output_tokens,
                image_units,
                request_units,
                provider_latency_ms,
                total_latency_ms,
                estimated_cost_micros,
                currency,
                pricing_version,
                created_at
            )
            values (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                1,
                'VISION_PARSE',
                'recognition-evidence-v1',
                ?::jsonb,
                ?::jsonb,
                ?::jsonb,
                'TEST_PROVIDER',
                'test-vision-v1',
                'vision-route-v1',
                'vision-recognition',
                'v001',
                12,
                24,
                1,
                1,
                7,
                9,
                42,
                'USD',
                'test-pricing-v1',
                ?
            )
            """,
            evidenceId,
            recognitionJobId,
            userId,
            sessionId,
            assetId,
            derivativeId,
            recognitionJson(
                text,
                ambiguous
            ),
            normalizedRecognitionJson(
                text,
                ambiguous
            ),
            upstreamQualityJson(
                qualityOutcome
            ),
            Timestamp.from(NOW)
        );

        return sessionId;
    }

    private UUID insertQueuedParseJob(
        UUID userId,
        UUID sessionId,
        UUID evidenceId,
        String promptVersion
    ) {
        UUID jobId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            insert into problem_parse_jobs (
                id,
                user_id,
                problem_session_id,
                recognition_evidence_id,
                recognition_evidence_revision,
                status,
                capability,
                prompt_id,
                prompt_version,
                schema_version,
                route_policy_version,
                attempt_count,
                max_attempts,
                next_attempt_at,
                review_required,
                created_at,
                updated_at
            )
            values (
                ?,
                ?,
                ?,
                ?,
                1,
                'QUEUED',
                'PROBLEM_NORMALIZE',
                'problem-parser',
                ?,
                'problem-parse-v1',
                'problem-parser-route-v1',
                0,
                2,
                ?,
                false,
                ?,
                ?
            )
            """,
            jobId,
            userId,
            sessionId,
            evidenceId,
            promptVersion,
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );

        return jobId;
    }

    private static String recognitionJson(
        String text,
        boolean ambiguous
    ) {
        return """
            {
              "schemaVersion":"recognition-evidence-v1",
              "blocks":[
                {
                  "id":"block-1",
                  "kind":"MATH",
                  "text":"%s",
                  "boundingBox":{
                    "x":0.1,
                    "y":0.2,
                    "width":0.7,
                    "height":0.2
                  },
                  "readingOrder":0,
                  "confidence":{
                    "raw":0.98,
                    "normalized":0.98,
                    "scale":"0_TO_1"
                  },
                  "uncertainty":%s,
                  "layoutHints":[
                    "INLINE_MATH"
                  ]
                }
              ],
              "documentUncertainty":[],
              "reviewRequired":%s
            }
            """.formatted(
            text.replace(
                "\"",
                "\\\""
            ),
            ambiguous
                ? "[\"ambiguous exponent\"]"
                : "[]",
            ambiguous
        );
    }

    private static String normalizedRecognitionJson(
        String text,
        boolean ambiguous
    ) {
        return """
            {
              "schemaVersion":"recognition-evidence-v1",
              "coordinateSpace":{
                "space":"INPUT_ASSET_NORMALIZED",
                "version":"input-asset-normalized-v1",
                "inputAssetId":"fixture",
                "width":1200,
                "height":900
              },
              "blocks":[
                {
                  "id":"block-1",
                  "kind":"MATH",
                  "text":"%s",
                  "boundingBox":{
                    "x":0.100000,
                    "y":0.200000,
                    "width":0.700000,
                    "height":0.200000
                  },
                  "readingOrder":0,
                  "confidence":{
                    "status":"KNOWN",
                    "normalized":0.980000,
                    "rawProviderConfidence":0.98
                  },
                  "uncertainty":%s,
                  "layoutHints":[
                    "INLINE_MATH"
                  ]
                }
              ],
              "documentUncertainty":[],
              "upstreamQualityEvidence":[],
              "reviewRequired":%s,
              "canonicalProblemCreated":false
            }
            """.formatted(
            text.replace(
                "\"",
                "\\\""
            ),
            ambiguous
                ? "[\"ambiguous exponent\"]"
                : "[]",
            ambiguous
        );
    }

    private static String upstreamQualityJson(
        String qualityOutcome
    ) {
        return """
            {
              "qualitySignals":[
                {
                  "signalType":"RESOLUTION",
                  "severity":"%s",
                  "score":1200,
                  "threshold":900,
                  "policyVersion":"capture-quality-v1",
                  "messageCode":"CAPTURE_RESOLUTION_%s"
                }
              ]
            }
            """.formatted(
            qualityOutcome,
            qualityOutcome
        );
    }

    private static String supportedEquationOutput() {
        return """
            {
              "schemaVersion":"problem-parse-v1",
              "supportStatus":"SUPPORTED",
              "unsupportedReason":null,
              "subjectId":"MATH",
              "topicId":"MATH.EQUATIONS",
              "taskType":"SOLVE_EQUATION",
              "problemType":"EQUATION",
              "expressions":[
                {
                  "id":"expr-1",
                  "role":"PRIMARY",
                  "sourceText":"x + 2 = 5",
                  "normalizedText":"x + 2 = 5",
                  "displayLatex":"x + 2 = 5",
                  "relation":"EQUALS",
                  "sourceBlockIds":[
                    "block-1"
                  ]
                }
              ],
              "variables":[
                {
                  "symbol":"x",
                  "role":"VARIABLE",
                  "sourceBlockIds":[
                    "block-1"
                  ]
                }
              ],
              "constraints":[],
              "assumptions":[],
              "uncertainty":{
                "recognition":[],
                "parse":[],
                "reviewRequired":false
              },
              "sourceEvidenceRefs":[
                {
                  "blockId":"block-1",
                  "fieldPath":"expressions[0]"
                }
              ],
              "visualQualityRisks":[],
              "reviewRequired":false
            }
            """;
    }

    private static String unsupportedOutput(
        String reason
    ) {
        return """
            {
              "schemaVersion":"problem-parse-v1",
              "supportStatus":"UNSUPPORTED",
              "unsupportedReason":"%s",
              "subjectId":"MATH",
              "topicId":null,
              "taskType":null,
              "problemType":null,
              "expressions":[],
              "variables":[],
              "constraints":[],
              "assumptions":[],
              "uncertainty":{
                "recognition":[],
                "parse":[
                  "current schema cannot represent this structure"
                ],
                "reviewRequired":false
              },
              "sourceEvidenceRefs":[
                {
                  "blockId":"block-1",
                  "fieldPath":"supportStatus"
                }
              ],
              "visualQualityRisks":[],
              "reviewRequired":false
            }
            """.formatted(reason);
    }

    private static String schemaInvalidOutput() {
        return """
            {
              "schemaVersion":"problem-parse-v1",
              "supportStatus":"SUPPORTED",
              "unsupportedReason":null,
              "subjectId":"MATH",
              "topicId":"MATH.EQUATIONS",
              "problemType":"EQUATION",
              "expressions":[],
              "variables":[],
              "constraints":[],
              "assumptions":[],
              "uncertainty":{
                "recognition":[],
                "parse":[],
                "reviewRequired":false
              },
              "sourceEvidenceRefs":[],
              "visualQualityRisks":[],
              "reviewRequired":false
            }
            """;
    }

    private static String semanticInvalidVariableMismatchOutput() {
        return """
            {
              "schemaVersion":"problem-parse-v1",
              "supportStatus":"SUPPORTED",
              "unsupportedReason":null,
              "subjectId":"MATH",
              "topicId":"MATH.EQUATIONS",
              "taskType":"SOLVE_EQUATION",
              "problemType":"EQUATION",
              "expressions":[
                {
                  "id":"expr-1",
                  "role":"PRIMARY",
                  "sourceText":"x + 2 = 5",
                  "normalizedText":"x + 2 = 5",
                  "displayLatex":"x + 2 = 5",
                  "relation":"EQUALS",
                  "sourceBlockIds":[
                    "block-1"
                  ]
                }
              ],
              "variables":[
                {
                  "symbol":"y",
                  "role":"VARIABLE",
                  "sourceBlockIds":[
                    "block-1"
                  ]
                }
              ],
              "constraints":[],
              "assumptions":[],
              "uncertainty":{
                "recognition":[],
                "parse":[],
                "reviewRequired":false
              },
              "sourceEvidenceRefs":[
                {
                  "blockId":"block-1",
                  "fieldPath":"expressions[0]"
                }
              ],
              "visualQualityRisks":[],
              "reviewRequired":false
            }
            """;
    }

    private static String semanticInvalidEquationMissingRelationOutput() {
        return """
            {
              "schemaVersion":"problem-parse-v1",
              "supportStatus":"SUPPORTED",
              "unsupportedReason":null,
              "subjectId":"MATH",
              "topicId":"MATH.EQUATIONS",
              "taskType":"SOLVE_EQUATION",
              "problemType":"EQUATION",
              "expressions":[
                {
                  "id":"expr-1",
                  "role":"PRIMARY",
                  "sourceText":"x + 2",
                  "normalizedText":"x + 2",
                  "displayLatex":"x + 2",
                  "relation":null,
                  "sourceBlockIds":[
                    "block-1"
                  ]
                }
              ],
              "variables":[
                {
                  "symbol":"x",
                  "role":"VARIABLE",
                  "sourceBlockIds":[
                    "block-1"
                  ]
                }
              ],
              "constraints":[],
              "assumptions":[],
              "uncertainty":{
                "recognition":[],
                "parse":[],
                "reviewRequired":false
              },
              "sourceEvidenceRefs":[
                {
                  "blockId":"block-1",
                  "fieldPath":"expressions[0]"
                }
              ],
              "visualQualityRisks":[],
              "reviewRequired":false
            }
            """;
    }

    private static String semanticInvalidInequalityMissingRelationOutput() {
        return """
            {
              "schemaVersion":"problem-parse-v1",
              "supportStatus":"SUPPORTED",
              "unsupportedReason":null,
              "subjectId":"MATH",
              "topicId":"MATH.EQUATIONS",
              "taskType":"SOLVE_INEQUALITY",
              "problemType":"INEQUALITY",
              "expressions":[
                {
                  "id":"expr-1",
                  "role":"PRIMARY",
                  "sourceText":"x + 2 = 5",
                  "normalizedText":"x + 2 = 5",
                  "displayLatex":"x + 2 = 5",
                  "relation":"EQUALS",
                  "sourceBlockIds":[
                    "block-1"
                  ]
                }
              ],
              "variables":[
                {
                  "symbol":"x",
                  "role":"VARIABLE",
                  "sourceBlockIds":[
                    "block-1"
                  ]
                }
              ],
              "constraints":[],
              "assumptions":[],
              "uncertainty":{
                "recognition":[],
                "parse":[],
                "reviewRequired":false
              },
              "sourceEvidenceRefs":[
                {
                  "blockId":"block-1",
                  "fieldPath":"expressions[0]"
                }
              ],
              "visualQualityRisks":[],
              "reviewRequired":false
            }
            """;
    }

    private static String nonExplicitAssumptionOutput() {
        return """
            {
              "schemaVersion":"problem-parse-v1",
              "supportStatus":"SUPPORTED",
              "unsupportedReason":null,
              "subjectId":"MATH",
              "topicId":"MATH.EQUATIONS",
              "taskType":"SOLVE_EQUATION",
              "problemType":"EQUATION",
              "expressions":[
                {
                  "id":"expr-1",
                  "role":"PRIMARY",
                  "sourceText":"x + 2 = 5",
                  "normalizedText":"x + 2 = 5",
                  "displayLatex":"x + 2 = 5",
                  "relation":"EQUALS",
                  "sourceBlockIds":[
                    "block-1"
                  ]
                }
              ],
              "variables":[
                {
                  "symbol":"x",
                  "role":"VARIABLE",
                  "sourceBlockIds":[
                    "block-1"
                  ]
                }
              ],
              "constraints":[],
              "assumptions":[
                {
                  "id":"assumption-1",
                  "text":"x is real",
                  "explicit":false,
                  "sourceBlockIds":[
                    "block-1"
                  ]
                }
              ],
              "uncertainty":{
                "recognition":[],
                "parse":[],
                "reviewRequired":false
              },
              "sourceEvidenceRefs":[
                {
                  "blockId":"block-1",
                  "fieldPath":"expressions[0]"
                }
              ],
              "visualQualityRisks":[],
              "reviewRequired":false
            }
            """;
    }

    private Integer count(
        String table
    ) {
        return jdbcTemplate.queryForObject(
            "select count(*) from "
                + table,
            Integer.class
        );
    }

    private String jsonText(
        String table,
        String expression,
        String predicate
    ) {
        return jdbcTemplate.queryForObject(
            "select "
                + expression
                + "::text from "
                + table
                + " where "
                + predicate,
            String.class
        );
    }

    private static String sha256Hex(
        byte[] bytes
    ) {
        try {
            return HexFormat.of()
                .formatHex(
                    MessageDigest
                        .getInstance("SHA-256")
                        .digest(bytes)
                );
        } catch (Exception exception) {
            throw new IllegalStateException(
                exception
            );
        }
    }

    @TestConfiguration
    static class ParseTestConfiguration {

        @Bean
        @Primary
        ParseFakeAiModelGateway aiModelGateway() {
            return new ParseFakeAiModelGateway();
        }

        @Bean
        @Primary
        ParseTestStorage problemAssetStorage() {
            return new ParseTestStorage();
        }
    }

    static final class ParseFakeAiModelGateway
        implements AiModelGateway {

        private final ArrayDeque<String> outputs =
            new ArrayDeque<>();

        @Override
        public synchronized AiRoutePlan routePlan(
            AiCapability capability
        ) {
            return switch (capability) {
                case VISION_PARSE ->
                    new AiRoutePlan(
                        capability,
                        "vision-route-v1",
                        "TEST_PROVIDER",
                        "",
                        "vision-recognition",
                        "v001",
                        "recognition-evidence-v1",
                        Duration.ofSeconds(20),
                        2,
                        65_536,
                        20_000,
                        "test-pricing-v1"
                    );

                case PROBLEM_NORMALIZE ->
                    new AiRoutePlan(
                        capability,
                        "problem-parser-route-v1",
                        "TEST_PROVIDER",
                        "",
                        "problem-parser",
                        "v001",
                        "problem-parse-v1",
                        Duration.ofSeconds(20),
                        2,
                        65_536,
                        20_000,
                        "test-pricing-v1"
                    );

                case PROBLEM_CLASSIFY ->
                    throw new UnsupportedOperationException(
                        "Problem classification is outside parser tests"
                    );
            };
        }

        @Override
        public AiVisionParseResult executeVisionParse(
            AiVisionParseRequest request
        ) {
            throw new UnsupportedOperationException(
                "Vision parsing is outside parser tests"
            );
        }

        @Override
        public synchronized AiProblemNormalizeResult
        executeProblemNormalize(
            AiProblemNormalizeRequest request
        ) {

            String rawOutput =
                outputs.isEmpty()
                    ? supportedEquationOutput()
                    : outputs.removeFirst();

            return new AiProblemNormalizeResult(
                rawOutput,
                new AiProvenance(
                    "TEST_PROVIDER",
                    "test-parser-v1",
                    "problem-parser-route-v1",
                    request.promptId(),
                    request.promptVersion(),
                    request.schemaVersion(),
                    "test-request",
                    "test-response",
                    false
                ),
                new AiUsage(
                    16,
                    32,
                    null,
                    1,
                    64,
                    "USD",
                    "test-pricing-v1"
                ),
                11
            );
        }

        @Override
        public AiProblemClassifyResult executeProblemClassify(
            AiProblemClassifyRequest request
        ) {
            throw new UnsupportedOperationException(
                "Problem classification is outside parser tests"
            );
        }

        synchronized void enqueue(
            String rawOutputJson
        ) {
            outputs.add(
                rawOutputJson
            );
        }

        synchronized void reset() {
            outputs.clear();
        }
    }

    static final class ParseTestStorage
        implements ProblemAssetStorage {

        private final Map<String, StoredObject> objects =
            new ConcurrentHashMap<>();

        @Override
        public PresignedProblemAssetUpload presignPut(
            String objectKey,
            String contentType,
            long sizeBytes,
            Duration ttl
        ) {
            return new PresignedProblemAssetUpload(
                URI.create(
                    "http://127.0.0.1:9000/verified-ai-problem-assets-local/"
                        + objectKey
                ),
                Instant.now().plus(ttl),
                Map.of(
                    "Content-Type",
                    contentType
                )
            );
        }

        @Override
        public ProblemAssetObjectMetadata head(
            String objectKey
        ) {
            StoredObject object =
                objects.get(objectKey);

            if (object == null) {
                throw new ProblemAssetObjectNotFoundException(
                    "missing"
                );
            }

            return new ProblemAssetObjectMetadata(
                object.sizeBytes(),
                object.contentType()
            );
        }

        @Override
        public byte[] readBytes(
            String objectKey,
            long maxSizeBytes
        ) {
            StoredObject object =
                objects.get(objectKey);

            if (object == null) {
                throw new ProblemAssetObjectNotFoundException(
                    "missing"
                );
            }

            return object.bytes().clone();
        }

        @Override
        public void putObject(
            String objectKey,
            String contentType,
            byte[] bytes
        ) {
            objects.put(
                objectKey,
                new StoredObject(
                    contentType,
                    bytes.clone(),
                    bytes.length,
                    ProblemParseApplicationServiceTest
                        .sha256Hex(bytes)
                )
            );
        }

        @Override
        public String sha256Hex(
            String objectKey
        ) {
            StoredObject object =
                objects.get(objectKey);

            if (object == null) {
                throw new ProblemAssetObjectNotFoundException(
                    "missing"
                );
            }

            return object.checksumSha256();
        }

        @Override
        public void deleteIfExists(
            String objectKey
        ) {
            objects.remove(objectKey);
        }

        private record StoredObject(
            String contentType,
            byte[] bytes,
            long sizeBytes,
            String checksumSha256
        ) {
        }
    }
}
