package com.verifiedai.problem.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class ProblemClassificationIntegrationFixture {

    private static final Instant NOW =
        Instant.parse(
            "2026-08-10T00:00:00Z"
        );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper =
        new ObjectMapper();

    public ProblemClassificationIntegrationFixture(
        JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clean() {
        jdbcTemplate.execute(
            """
            truncate table
                problem_classification_secondary_skills,
                problem_classifications,
                problem_classification_jobs,
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
            """
        );
    }

    public UUID insertUser() {
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

    public CanonicalFixture insertCanonical(
        UUID userId,
        String problemType,
        String taskType,
        String normalizedText,
        boolean reviewRequired
    ) {
        UUID sessionId =
            insertUpstreamSession(userId);

        return addCanonicalRevision(
            userId,
            sessionId,
            1,
            problemType,
            taskType,
            normalizedText,
            reviewRequired,
            1
        );
    }

    public CanonicalFixture addCanonicalRevision(
        UUID userId,
        UUID sessionId,
        int canonicalRevision,
        String problemType,
        String taskType,
        String normalizedText,
        boolean reviewRequired
    ) {
        return addCanonicalRevision(
            userId,
            sessionId,
            canonicalRevision,
            problemType,
            taskType,
            normalizedText,
            reviewRequired,
            1
        );
    }

    public CanonicalFixture addCanonicalRevision(
        UUID userId,
        UUID sessionId,
        int canonicalRevision,
        String problemType,
        String taskType,
        String normalizedText,
        boolean reviewRequired,
        int statementCount
    ) {
        UUID evidenceId =
            jdbcTemplate.queryForObject(
                """
                select id
                from recognition_evidence
                where problem_session_id = ?
                order by revision desc
                limit 1
                """,
                UUID.class,
                sessionId
            );

        UUID parseJobId =
            UUID.randomUUID();

        UUID parseId =
            UUID.randomUUID();

        String promptVersion =
            "v%03d".formatted(
                canonicalRevision
            );

        Instant createdAt =
            NOW.plusSeconds(
                canonicalRevision
            );

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
                updated_at,
                started_at,
                completed_at
            )
            values (
                ?, ?, ?, ?, 1,
                'SUCCEEDED',
                'PROBLEM_NORMALIZE',
                'problem-parser',
                ?,
                'problem-parse-v1',
                'problem-parser-route-v1',
                1,
                2,
                ?,
                false,
                ?, ?, ?, ?
            )
            """,
            parseJobId,
            userId,
            sessionId,
            evidenceId,
            promptVersion,
            Timestamp.from(createdAt),
            Timestamp.from(createdAt),
            Timestamp.from(createdAt),
            Timestamp.from(createdAt),
            Timestamp.from(createdAt)
        );

        jdbcTemplate.update(
            """
            insert into problem_parses (
                id,
                parse_job_id,
                user_id,
                problem_session_id,
                recognition_evidence_id,
                recognition_evidence_revision,
                revision,
                source,
                support_status,
                unsupported_reason,
                review_required,
                schema_version,
                raw_output_jsonb,
                normalized_problem_jsonb,
                provider,
                model,
                route_policy_version,
                prompt_id,
                prompt_version,
                fallback_used,
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
                ?, ?, ?, ?, ?, 1, ?,
                'AI',
                'SUPPORTED',
                null,
                false,
                'problem-parse-v1',
                '{}'::jsonb,
                '{}'::jsonb,
                'TEST_PROVIDER',
                'test-parser-v1',
                'problem-parser-route-v1',
                'problem-parser',
                ?,
                false,
                1,
                1,
                null,
                1,
                1,
                1,
                0,
                'USD',
                'test-pricing-v1',
                ?
            )
            """,
            parseId,
            parseJobId,
            userId,
            sessionId,
            evidenceId,
            canonicalRevision,
            promptVersion,
            Timestamp.from(createdAt)
        );

        UUID canonicalId =
            UUID.randomUUID();

        jdbcTemplate.update(
            """
            insert into canonical_problems (
                id,
                user_id,
                problem_session_id,
                problem_parse_id,
                problem_parse_revision,
                canonical_revision,
                schema_version,
                verifier_schema_version,
                problem_type,
                task_type,
                canonical_problem_jsonb,
                verifier_input_jsonb,
                display_jsonb,
                created_at
            )
            values (
                ?, ?, ?, ?, ?, ?,
                'canonical-problem-v1',
                'verifier-input-v1',
                ?,
                ?,
                ?::jsonb,
                '{}'::jsonb,
                ?::jsonb,
                ?
            )
            """,
            canonicalId,
            userId,
            sessionId,
            parseId,
            canonicalRevision,
            canonicalRevision,
            problemType,
            taskType,
            canonicalJson(
                statementCount
            ),
            displayJson(
                normalizedText,
                reviewRequired
            ),
            Timestamp.from(createdAt)
        );

        return new CanonicalFixture(
            sessionId,
            canonicalId,
            canonicalRevision
        );
    }

    public int count(
        String table
    ) {
        Integer result =
            jdbcTemplate.queryForObject(
                "select count(*) from " + table,
                Integer.class
            );

        return result == null
            ? 0
            : result;
    }

    private UUID insertUpstreamSession(
        UUID userId
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
            "classification-fixture-source"
                .getBytes(
                    StandardCharsets.UTF_8
                );

        byte[] derivativeBytes =
            "classification-fixture-derivative"
                .getBytes(
                    StandardCharsets.UTF_8
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
                + "/ocr.jpg";

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
                ?, ?,
                'ASSET_UPLOADED',
                'CAMERA',
                ?, ?
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
                ?, ?, ?,
                'CAMERA',
                'IMAGE',
                'AVAILABLE',
                ?,
                'image/jpeg',
                ?,
                'SHA-256',
                ?,
                0, 0, 1, 1,
                1200,
                900,
                null,
                'TEMPORARY_RAW',
                ?, ?, ?, ?,
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
                ?, ?, ?, ?,
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
                0, 0, 1, 1,
                'DOCUMENT_PREPROCESSOR',
                '1.0',
                'capture-quality-v1',
                false,
                false,
                false,
                false,
                'PASS',
                ?, ?, ?
            )
            """,
            derivativeId,
            assetId,
            sessionId,
            userId,
            derivativeKey,
            (long) derivativeBytes.length,
            sha256Hex(
                derivativeBytes
            ),
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
                ?, ?, ?, ?, ?,
                'SUCCEEDED',
                'VISION_PARSE',
                'vision-recognition',
                'v001',
                'recognition-evidence-v1',
                'vision-route-v1',
                1,
                2,
                ?,
                false,
                ?, ?, ?, ?
            )
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

        /*
         * Recognition/parser payload contents are deliberately
         * irrelevant to this test fixture.
         *
         * Sprint 4.7 must classify CanonicalProblem only.
         */
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
                ?, ?, ?, ?, ?, ?,
                1,
                'VISION_PARSE',
                'recognition-evidence-v1',
                '{}'::jsonb,
                '{}'::jsonb,
                '{}'::jsonb,
                'TEST_PROVIDER',
                'test-vision-v1',
                'vision-route-v1',
                'vision-recognition',
                'v001',
                1,
                1,
                1,
                1,
                1,
                1,
                0,
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
            Timestamp.from(NOW)
        );

        return sessionId;
    }

    private String canonicalJson(
        int statementCount
    ) {
        try {
            ObjectNode root =
                objectMapper
                    .createObjectNode();

            root.put(
                "schemaVersion",
                "canonical-problem-v1"
            );

            ArrayNode statements =
                root.putArray(
                    "statements"
                );

            for (
                int index = 0;
                index < statementCount;
                index += 1
            ) {
                statements
                    .addObject()
                    .put(
                        "id",
                        "statement-" + index
                    );
            }

            return objectMapper
                .writeValueAsString(root);

        } catch (Exception exception) {
            throw new IllegalStateException(
                exception
            );
        }
    }

    private String displayJson(
        String normalizedText,
        boolean reviewRequired
    ) {
        try {
            ObjectNode root =
                objectMapper
                    .createObjectNode();

            root.put(
                "normalizedText",
                normalizedText
            );

            root.put(
                "displayLatex",
                normalizedText
            );

            root.putArray(
                "variables"
            ).add("x");

            root.put(
                "sourceConstraintCount",
                0
            );

            root.put(
                "derivedRestrictionCount",
                0
            );

            root.put(
                "reviewRequired",
                reviewRequired
            );

            return objectMapper
                .writeValueAsString(root);

        } catch (Exception exception) {
            throw new IllegalStateException(
                exception
            );
        }
    }

    private static String sha256Hex(
        byte[] bytes
    ) {
        try {
            return HexFormat.of()
                .formatHex(
                    MessageDigest
                        .getInstance(
                            "SHA-256"
                        )
                        .digest(bytes)
                );

        } catch (Exception exception) {
            throw new IllegalStateException(
                exception
            );
        }
    }

    public record CanonicalFixture(
        UUID sessionId,
        UUID canonicalProblemId,
        int canonicalRevision
    ) {
    }
}
