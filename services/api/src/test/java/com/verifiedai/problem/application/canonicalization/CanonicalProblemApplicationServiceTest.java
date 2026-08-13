package com.verifiedai.problem.application.canonicalization;

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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(CanonicalProblemApplicationServiceTest.CanonicalTestConfiguration.class)
final class CanonicalProblemApplicationServiceTest extends PostgresIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @Autowired
    CanonicalProblemApplicationService canonicalProblemApplicationService;

    @Autowired
    ProblemAssetLifecycleContributor lifecycleContributor;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
            truncate table
                canonical_problems,
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
            """);
    }

    @Test
    void canonicalizeCreatesTypedVerifierInputAndDerivedDenominatorRestrictionWithoutSolving() {
        UUID userId = insertUser();
        UUID sessionId = insertSupportedParse(userId, equationParseJson("1/(x - 2) = 3"), 1);

        CanonicalProblemResult result = canonicalProblemApplicationService.canonicalize(userId, sessionId);

        assertThat(result.schemaVersion()).isEqualTo("canonical-problem-v1");
        assertThat(result.verifierSchemaVersion()).isEqualTo("verifier-input-v1");
        assertThat(result.problemType()).isEqualTo("EQUATION");
        assertThat(result.derivedRestrictionCount()).isEqualTo(1);
        String canonicalJson = jsonText("canonical_problems", "canonical_problem_jsonb", "1 = 1");
        String verifierJson = jsonText("canonical_problems", "verifier_input_jsonb", "1 = 1");
        assertThat(canonicalJson)
            .contains("DENOMINATOR_NON_ZERO")
            .contains("canonical-problem-v1")
            .doesNotContain("VERIFIED")
            .doesNotContain("solution");
        assertThat(verifierJson)
            .contains("verifier-input-v1")
            .contains("\"kind\": \"BINARY\"");
    }

    @Test
    void canonicalizeRetainsCancelledDenominatorRestriction() {
        UUID userId = insertUser();
        UUID sessionId = insertSupportedParse(userId, equationParseJson("(x^2 - 1)/(x - 1) = x + 1"), 1);

        canonicalProblemApplicationService.canonicalize(userId, sessionId);

        String canonicalJson = jsonText("canonical_problems", "canonical_problem_jsonb", "1 = 1");
        assertThat(canonicalJson)
            .contains("DENOMINATOR_NON_ZERO")
            .contains("SUBTRACT")
            .contains("\"value\": \"1\"");
    }

    @Test
    void canonicalizeIsIdempotentForSameParseRevisionAndCreatesNewCanonicalRevisionForNewParse() {
        UUID userId = insertUser();
        UUID sessionId = insertSupportedParse(userId, equationParseJson("x + 1 = 2"), 1);

        CanonicalProblemResult first = canonicalProblemApplicationService.canonicalize(userId, sessionId);
        CanonicalProblemResult second = canonicalProblemApplicationService.canonicalize(userId, sessionId);
        insertSupportedParse(userId, sessionId, equationParseJson("x + 2 = 5"), 2);
        CanonicalProblemResult third = canonicalProblemApplicationService.canonicalize(userId, sessionId);

        assertThat(second.canonicalProblemId()).isEqualTo(first.canonicalProblemId());
        assertThat(third.canonicalRevision()).isEqualTo(2);
        assertThat(third.problemParseRevision()).isEqualTo(2);
        assertThat(count("canonical_problems")).isEqualTo(2);
    }

    @Test
    void unsafeParserTextFailsWithoutPersistingCanonicalProblem() {
        UUID userId = insertUser();
        UUID sessionId = insertSupportedParse(userId, equationParseJson("__import__(x) = 1"), 1);

        assertThatThrownBy(() -> canonicalProblemApplicationService.canonicalize(userId, sessionId))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.PROBLEM_PARSE_FAILED);
        assertThat(count("canonical_problems")).isEqualTo(0);
    }

    @Test
    void unsupportedParseFailsAsUnsupportedInput() {
        UUID userId = insertUser();
        UUID sessionId = insertUnsupportedParse(userId);

        assertThatThrownBy(() -> canonicalProblemApplicationService.canonicalize(userId, sessionId))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.PROBLEM_UNSUPPORTED);
        assertThat(count("canonical_problems")).isEqualTo(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void accountExportIncludesCanonicalProblemAndDeletionCascadesRows() {
        UUID userId = insertUser();
        UUID sessionId = insertSupportedParse(userId, equationParseJson("x + 1 = 2"), 1);
        canonicalProblemApplicationService.canonicalize(userId, sessionId);

        Map<String, Object> export = lifecycleContributor.exportUserData(userId);

        assertThat((List<Map<String, Object>>) export.get("canonicalProblems")).hasSize(1);
        lifecycleContributor.deleteUserData(userId, NOW);
        assertThat(count("canonical_problems")).isEqualTo(0);
        assertThat(count("problem_sessions")).isEqualTo(0);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into users (id, status, created_at, updated_at) values (?, 'ACTIVE', ?, ?)",
            userId,
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        return userId;
    }

    private UUID insertSupportedParse(UUID userId, String normalizedProblemJson, int revision) {
        UUID sessionId = insertRecognizedProblem(userId);
        insertSupportedParse(userId, sessionId, normalizedProblemJson, revision);
        return sessionId;
    }

    private void insertSupportedParse(UUID userId, UUID sessionId, String normalizedProblemJson, int revision) {
        UUID evidenceId = jdbcTemplate.queryForObject(
            "select id from recognition_evidence where problem_session_id = ?",
            UUID.class,
            sessionId
        );
        insertParse(userId, sessionId, evidenceId, normalizedProblemJson, "SUPPORTED", null, revision);
    }

    private UUID insertUnsupportedParse(UUID userId) {
        UUID sessionId = insertRecognizedProblem(userId);
        UUID evidenceId = jdbcTemplate.queryForObject("select id from recognition_evidence", UUID.class);
        insertParse(userId, sessionId, evidenceId, unsupportedParseJson(), "UNSUPPORTED", "UNSUPPORTED_STRUCTURE", 1);
        return sessionId;
    }

    private UUID insertRecognizedProblem(UUID userId) {
        UUID sessionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID derivativeId = UUID.randomUUID();
        UUID recognitionJobId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        byte[] sourceBytes = "source-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] derivativeBytes = "ocr-optimized-image".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbcTemplate.update(
            "insert into problem_sessions (id, user_id, status, input_mode, created_at, updated_at) values (?, ?, 'ASSET_UPLOADED', 'CAMERA', ?, ?)",
            sessionId,
            userId,
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        jdbcTemplate.update(
            """
            insert into problem_assets (
                id, problem_session_id, user_id, source_type, asset_kind, status, object_key, content_type,
                size_bytes, checksum_algorithm, checksum_value, crop_x, crop_y, crop_width, crop_height,
                image_width, image_height, page_count, retention_class, upload_expires_at, available_at,
                created_at, updated_at, reservation_idempotency_key, reservation_request_hash
            )
            values (?, ?, ?, 'CAMERA', 'IMAGE', 'AVAILABLE', ?, 'image/jpeg', ?, 'SHA-256', ?, 0, 0, 1, 1,
                    1200, 900, null, 'TEMPORARY_RAW', ?, ?, ?, ?, ?, ?)
            """,
            assetId,
            sessionId,
            userId,
            "problem-assets/" + sessionId + "/" + assetId + "/original",
            (long) sourceBytes.length,
            sha256Hex(sourceBytes),
            Timestamp.from(NOW.plus(Duration.ofMinutes(15))),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            "reserve-" + assetId,
            "0".repeat(64)
        );
        jdbcTemplate.update(
            """
            insert into problem_asset_derivatives (
                id, source_asset_id, problem_session_id, user_id, derivative_kind, status, selected_for_recognition,
                object_key, content_type, size_bytes, checksum_algorithm, checksum_value, width, height,
                source_width, source_height, crop_x, crop_y, crop_width, crop_height, processor_name,
                processor_version, configuration_version, orientation_normalized, perspective_applied,
                contrast_normalized, resized, quality_outcome, created_at, updated_at, completed_at
            )
            values (?, ?, ?, ?, 'OCR_OPTIMIZED', 'READY', true, ?, 'image/jpeg', ?, 'SHA-256', ?, 1200, 900,
                    1200, 900, 0, 0, 1, 1, 'DOCUMENT_PREPROCESSOR', '1.0', 'capture-quality-v1',
                    false, false, false, false, 'PASS', ?, ?, ?)
            """,
            derivativeId,
            assetId,
            sessionId,
            userId,
            "problem-assets/" + sessionId + "/" + assetId + "/derivatives/" + derivativeId + "/ocr-optimized.jpg",
            (long) derivativeBytes.length,
            sha256Hex(derivativeBytes),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        jdbcTemplate.update(
            """
            insert into recognition_jobs (
                id, user_id, problem_session_id, source_asset_id, input_derivative_id, status, capability,
                prompt_id, prompt_version, schema_version, route_policy_version, attempt_count, max_attempts,
                next_attempt_at, review_required, created_at, updated_at, started_at, completed_at
            )
            values (?, ?, ?, ?, ?, 'SUCCEEDED', 'VISION_PARSE', 'vision-recognition', 'v001',
                    'recognition-evidence-v1', 'vision-route-v1', 1, 2, ?, false, ?, ?, ?, ?)
            """,
            recognitionJobId,
            userId,
            sessionId,
            assetId,
            derivativeId,
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        jdbcTemplate.update(
            """
            insert into recognition_evidence (
                id, recognition_job_id, user_id, problem_session_id, source_asset_id, input_derivative_id,
                revision, capability, schema_version, raw_output_jsonb, normalized_evidence_jsonb,
                upstream_quality_evidence_jsonb, provider, model, route_policy_version, prompt_id,
                prompt_version, input_tokens, output_tokens, image_units, request_units, provider_latency_ms,
                total_latency_ms, estimated_cost_micros, currency, pricing_version, created_at
            )
            values (?, ?, ?, ?, ?, ?, 1, 'VISION_PARSE', 'recognition-evidence-v1',
                    ?::jsonb, ?::jsonb, ?::jsonb, 'TEST_PROVIDER', 'test-vision-v1', 'vision-route-v1',
                    'vision-recognition', 'v001', 12, 24, 1, 1, 7, 9, 42, 'USD', 'test-pricing-v1', ?)
            """,
            evidenceId,
            recognitionJobId,
            userId,
            sessionId,
            assetId,
            derivativeId,
            recognitionJson(),
            normalizedRecognitionJson(),
            upstreamQualityJson(),
            Timestamp.from(NOW)
        );
        return sessionId;
    }

    private void insertParse(
        UUID userId,
        UUID sessionId,
        UUID evidenceId,
        String normalizedProblemJson,
        String supportStatus,
        String unsupportedReason,
        int revision
    ) {
        UUID parseJobId = UUID.randomUUID();
        UUID parseId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            insert into problem_parse_jobs (
                id, user_id, problem_session_id, recognition_evidence_id, recognition_evidence_revision,
                status, capability, prompt_id, prompt_version, schema_version, route_policy_version,
                attempt_count, max_attempts, next_attempt_at, review_required, created_at, updated_at,
                started_at, completed_at
            )
            values (?, ?, ?, ?, 1, 'SUCCEEDED', 'PROBLEM_NORMALIZE', 'problem-parser', ?,
                    'problem-parse-v1', 'problem-parser-route-v1', 1, 2, ?, false, ?, ?, ?, ?)
            """,
            parseJobId,
            userId,
            sessionId,
            evidenceId,
            "v00" + revision,
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        jdbcTemplate.update(
            """
            insert into problem_parses (
                id, parse_job_id, user_id, problem_session_id, recognition_evidence_id,
                recognition_evidence_revision, revision, source, support_status, unsupported_reason,
                review_required, schema_version, raw_output_jsonb, normalized_problem_jsonb, provider,
                model, route_policy_version, prompt_id, prompt_version, fallback_used, input_tokens,
                output_tokens, image_units, request_units, provider_latency_ms, total_latency_ms,
                estimated_cost_micros, currency, pricing_version, created_at
            )
            values (?, ?, ?, ?, ?, 1, ?, 'AI', ?, ?, false, 'problem-parse-v1',
                    ?::jsonb, ?::jsonb, 'TEST_PROVIDER', 'test-parser-v1',
                    'problem-parser-route-v1', 'problem-parser', ?, false, 16, 32, null, 1,
                    11, 13, 64, 'USD', 'test-pricing-v1', ?)
            """,
            parseId,
            parseJobId,
            userId,
            sessionId,
            evidenceId,
            revision,
            supportStatus,
            unsupportedReason,
            normalizedProblemJson,
            normalizedProblemJson,
            "v00" + revision,
            Timestamp.from(NOW.plusSeconds(revision))
        );
        jdbcTemplate.update(
            "update problem_sessions set current_parse_id = ?, updated_at = ? where id = ?",
            parseId,
            Timestamp.from(NOW.plusSeconds(revision)),
            sessionId
        );
    }

    private static String equationParseJson(String normalizedText) {
        return """
            {"schemaVersion":"problem-parse-v1","supportStatus":"SUPPORTED","unsupportedReason":null,"subjectId":"MATH","topicId":"MATH.EQUATIONS","taskType":"SOLVE_EQUATION","problemType":"EQUATION","expressions":[{"id":"expr-1","role":"PRIMARY","sourceText":"%s","normalizedText":"%s","displayLatex":"%s","relation":"EQUALS","sourceBlockIds":["block-1"]}],"variables":[{"symbol":"x","role":"VARIABLE","sourceBlockIds":["block-1"]}],"constraints":[],"assumptions":[],"uncertainty":{"recognition":[],"parse":[],"reviewRequired":false},"sourceEvidenceRefs":[{"blockId":"block-1","fieldPath":"expressions[0]"}],"visualQualityRisks":[],"reviewRequired":false}
            """.formatted(json(normalizedText), json(normalizedText), json(normalizedText));
    }

    private static String unsupportedParseJson() {
        return """
            {"schemaVersion":"problem-parse-v1","supportStatus":"UNSUPPORTED","unsupportedReason":"UNSUPPORTED_STRUCTURE","subjectId":"MATH","topicId":null,"taskType":null,"problemType":null,"expressions":[],"variables":[],"constraints":[],"assumptions":[],"uncertainty":{"recognition":[],"parse":["unsupported"],"reviewRequired":false},"sourceEvidenceRefs":[{"blockId":"block-1","fieldPath":"supportStatus"}],"visualQualityRisks":[],"reviewRequired":false}
            """;
    }

    private static String recognitionJson() {
        return """
            {"schemaVersion":"recognition-evidence-v1","blocks":[{"id":"block-1","kind":"MATH","text":"fixture","boundingBox":{"x":0.1,"y":0.2,"width":0.7,"height":0.2},"readingOrder":0,"confidence":{"raw":0.98,"normalized":0.98,"scale":"0_TO_1"},"uncertainty":[],"layoutHints":["INLINE_MATH"]}],"documentUncertainty":[],"reviewRequired":false}
            """;
    }

    private static String normalizedRecognitionJson() {
        return """
            {"schemaVersion":"recognition-evidence-v1","coordinateSpace":{"space":"INPUT_ASSET_NORMALIZED","version":"input-asset-normalized-v1","inputAssetId":"fixture","width":1200,"height":900},"blocks":[{"id":"block-1","kind":"MATH","text":"fixture","boundingBox":{"x":0.100000,"y":0.200000,"width":0.700000,"height":0.200000},"readingOrder":0,"confidence":{"status":"KNOWN","normalized":0.980000,"rawProviderConfidence":0.98},"uncertainty":[],"layoutHints":["INLINE_MATH"]}],"documentUncertainty":[],"upstreamQualityEvidence":[],"reviewRequired":false,"canonicalProblemCreated":false}
            """;
    }

    private static String upstreamQualityJson() {
        return """
            {"qualitySignals":[{"signalType":"RESOLUTION","severity":"PASS","score":1200,"threshold":900,"policyVersion":"capture-quality-v1","messageCode":"CAPTURE_RESOLUTION_PASS"}]}
            """;
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private String jsonText(String table, String expression, String predicate) {
        return jdbcTemplate.queryForObject("select " + expression + "::text from " + table + " where " + predicate, String.class);
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @TestConfiguration
    static class CanonicalTestConfiguration {
        @Bean
        @Primary
        CanonicalTestStorage problemAssetStorage() {
            return new CanonicalTestStorage();
        }
    }

    static final class CanonicalTestStorage implements ProblemAssetStorage {
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

        @Override
        public PresignedProblemAssetUpload presignPut(String objectKey, String contentType, long sizeBytes, Duration ttl) {
            return new PresignedProblemAssetUpload(
                URI.create("http://127.0.0.1:9000/verified-ai-problem-assets-local/" + objectKey),
                Instant.now().plus(ttl),
                Map.of("Content-Type", contentType)
            );
        }

        @Override
        public ProblemAssetObjectMetadata head(String objectKey) {
            StoredObject object = objects.get(objectKey);
            if (object == null) {
                throw new ProblemAssetObjectNotFoundException("missing");
            }
            return new ProblemAssetObjectMetadata(object.sizeBytes(), object.contentType());
        }

        @Override
        public byte[] readBytes(String objectKey, long maxSizeBytes) {
            StoredObject object = objects.get(objectKey);
            if (object == null) {
                throw new ProblemAssetObjectNotFoundException("missing");
            }
            return object.bytes().clone();
        }

        @Override
        public void putObject(String objectKey, String contentType, byte[] bytes) {
            objects.put(
                objectKey,
                new StoredObject(
                    contentType,
                    bytes.clone(),
                    bytes.length,
                    CanonicalProblemApplicationServiceTest.sha256Hex(bytes)
                )
            );
        }

        @Override
        public String sha256Hex(String objectKey) {
            StoredObject object = objects.get(objectKey);
            if (object == null) {
                throw new ProblemAssetObjectNotFoundException("missing");
            }
            return object.checksumSha256();
        }

        @Override
        public void deleteIfExists(String objectKey) {
            objects.remove(objectKey);
        }

        private record StoredObject(String contentType, byte[] bytes, long sizeBytes, String checksumSha256) {
        }
    }
}
