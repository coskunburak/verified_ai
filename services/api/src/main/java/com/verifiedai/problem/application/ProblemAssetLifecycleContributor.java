package com.verifiedai.problem.application;

import com.verifiedai.problem.domain.port.ProblemAssetStorage;
import com.verifiedai.problem.infrastructure.persistence.ProblemAssetJpaRepository;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ProblemAssetLifecycleContributor
    implements AccountDataLifecycleContributor {

    private final ProblemAssetJpaRepository assetRepository;
    private final ProblemAssetStorage storage;
    private final JdbcTemplate jdbcTemplate;

    ProblemAssetLifecycleContributor(
        ProblemAssetJpaRepository assetRepository,
        ProblemAssetStorage storage,
        JdbcTemplate jdbcTemplate
    ) {
        this.assetRepository = assetRepository;
        this.storage = storage;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String category() {
        return "problemAssets";
    }

    @Override
    public Map<String, Object> exportUserData(
        UUID userId
    ) {
        List<Map<String, Object>> assets =
            assetRepository
                .findByUserIdOrderByCreatedAtDesc(
                    userId
                )
                .stream()
                .map(asset -> {
                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    row.put(
                        "problemSessionId",
                        asset.problemSessionId()
                            .toString()
                    );
                    row.put(
                        "problemAssetId",
                        asset.id().toString()
                    );
                    row.put(
                        "sourceType",
                        asset.sourceType()
                    );
                    row.put(
                        "assetKind",
                        asset.assetKind()
                    );
                    row.put(
                        "status",
                        asset.status()
                    );
                    row.put(
                        "contentType",
                        asset.contentType()
                    );
                    row.put(
                        "sizeBytes",
                        asset.sizeBytes()
                    );
                    row.put(
                        "checksumAlgorithm",
                        asset.checksumAlgorithm()
                    );
                    row.put(
                        "checksumSha256",
                        asset.checksumValue()
                    );
                    row.put(
                        "retentionClass",
                        asset.retentionClass()
                    );
                    row.put(
                        "createdAt",
                        asset.createdAt()
                            .toString()
                    );
                    row.put(
                        "availableAt",
                        asset.availableAt() == null
                            ? null
                            : asset.availableAt()
                            .toString()
                    );

                    return row;
                })
                .toList();

        List<Map<String, Object>> derivatives =
            jdbcTemplate.query(
                """
                select
                    source_asset_id,
                    id,
                    derivative_kind,
                    status,
                    selected_for_recognition,
                    content_type,
                    size_bytes,
                    checksum_algorithm,
                    checksum_value,
                    quality_outcome,
                    failure_code,
                    processor_name,
                    processor_version,
                    configuration_version,
                    created_at,
                    completed_at
                from problem_asset_derivatives
                where user_id = ?
                order by created_at desc
                """,
                (resultSet, rowNum) -> {
                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    row.put(
                        "sourceAssetId",
                        resultSet
                            .getObject(
                                "source_asset_id"
                            )
                            .toString()
                    );
                    row.put(
                        "derivativeId",
                        resultSet
                            .getObject("id")
                            .toString()
                    );
                    row.put(
                        "derivativeKind",
                        resultSet.getString(
                            "derivative_kind"
                        )
                    );
                    row.put(
                        "status",
                        resultSet.getString(
                            "status"
                        )
                    );
                    row.put(
                        "selectedForRecognition",
                        resultSet.getBoolean(
                            "selected_for_recognition"
                        )
                    );
                    row.put(
                        "contentType",
                        resultSet.getString(
                            "content_type"
                        )
                    );
                    row.put(
                        "sizeBytes",
                        resultSet.getObject(
                            "size_bytes"
                        )
                    );
                    row.put(
                        "checksumAlgorithm",
                        resultSet.getString(
                            "checksum_algorithm"
                        )
                    );
                    row.put(
                        "checksumSha256",
                        resultSet.getString(
                            "checksum_value"
                        )
                    );
                    row.put(
                        "qualityOutcome",
                        resultSet.getString(
                            "quality_outcome"
                        )
                    );
                    row.put(
                        "failureCode",
                        resultSet.getString(
                            "failure_code"
                        )
                    );
                    row.put(
                        "processorName",
                        resultSet.getString(
                            "processor_name"
                        )
                    );
                    row.put(
                        "processorVersion",
                        resultSet.getString(
                            "processor_version"
                        )
                    );
                    row.put(
                        "configurationVersion",
                        resultSet.getString(
                            "configuration_version"
                        )
                    );
                    row.put(
                        "createdAt",
                        resultSet
                            .getTimestamp(
                                "created_at"
                            )
                            .toInstant()
                            .toString()
                    );
                    row.put(
                        "completedAt",
                        instantOrNull(
                            resultSet.getTimestamp(
                                "completed_at"
                            )
                        )
                    );

                    return row;
                },
                userId
            );

        List<Map<String, Object>> qualityEvidence =
            jdbcTemplate.query(
                """
                select
                    source_asset_id,
                    derivative_id,
                    signal_type,
                    severity,
                    score,
                    threshold,
                    policy_version,
                    message_code,
                    created_at
                from problem_asset_quality_evidence
                where user_id = ?
                order by created_at desc
                """,
                (resultSet, rowNum) -> {
                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    row.put(
                        "sourceAssetId",
                        resultSet
                            .getObject(
                                "source_asset_id"
                            )
                            .toString()
                    );
                    row.put(
                        "derivativeId",
                        resultSet
                            .getObject(
                                "derivative_id"
                            )
                            .toString()
                    );
                    row.put(
                        "signalType",
                        resultSet.getString(
                            "signal_type"
                        )
                    );
                    row.put(
                        "severity",
                        resultSet.getString(
                            "severity"
                        )
                    );
                    row.put(
                        "score",
                        resultSet.getBigDecimal(
                            "score"
                        )
                    );
                    row.put(
                        "threshold",
                        resultSet.getBigDecimal(
                            "threshold"
                        )
                    );
                    row.put(
                        "policyVersion",
                        resultSet.getString(
                            "policy_version"
                        )
                    );
                    row.put(
                        "messageCode",
                        resultSet.getString(
                            "message_code"
                        )
                    );
                    row.put(
                        "createdAt",
                        resultSet
                            .getTimestamp(
                                "created_at"
                            )
                            .toInstant()
                            .toString()
                    );

                    return row;
                },
                userId
            );

        List<Map<String, Object>> recognitionJobs =
            jdbcTemplate.query(
                """
                select
                    id,
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
                    last_error_code,
                    last_failure_class,
                    review_required,
                    created_at,
                    completed_at
                from recognition_jobs
                where user_id = ?
                order by created_at desc
                """,
                (resultSet, rowNum) -> {
                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    row.put(
                        "recognitionJobId",
                        resultSet
                            .getObject("id")
                            .toString()
                    );
                    row.put(
                        "problemSessionId",
                        resultSet
                            .getObject(
                                "problem_session_id"
                            )
                            .toString()
                    );
                    row.put(
                        "sourceAssetId",
                        resultSet
                            .getObject(
                                "source_asset_id"
                            )
                            .toString()
                    );
                    row.put(
                        "inputDerivativeId",
                        resultSet
                            .getObject(
                                "input_derivative_id"
                            )
                            .toString()
                    );
                    row.put(
                        "status",
                        resultSet.getString(
                            "status"
                        )
                    );
                    row.put(
                        "capability",
                        resultSet.getString(
                            "capability"
                        )
                    );
                    row.put(
                        "promptId",
                        resultSet.getString(
                            "prompt_id"
                        )
                    );
                    row.put(
                        "promptVersion",
                        resultSet.getString(
                            "prompt_version"
                        )
                    );
                    row.put(
                        "schemaVersion",
                        resultSet.getString(
                            "schema_version"
                        )
                    );
                    row.put(
                        "routePolicyVersion",
                        resultSet.getString(
                            "route_policy_version"
                        )
                    );
                    row.put(
                        "attemptCount",
                        resultSet.getInt(
                            "attempt_count"
                        )
                    );
                    row.put(
                        "maxAttempts",
                        resultSet.getInt(
                            "max_attempts"
                        )
                    );
                    row.put(
                        "lastErrorCode",
                        resultSet.getString(
                            "last_error_code"
                        )
                    );
                    row.put(
                        "lastFailureClass",
                        resultSet.getString(
                            "last_failure_class"
                        )
                    );
                    row.put(
                        "reviewRequired",
                        resultSet.getBoolean(
                            "review_required"
                        )
                    );
                    row.put(
                        "createdAt",
                        resultSet
                            .getTimestamp(
                                "created_at"
                            )
                            .toInstant()
                            .toString()
                    );
                    row.put(
                        "completedAt",
                        instantOrNull(
                            resultSet.getTimestamp(
                                "completed_at"
                            )
                        )
                    );

                    return row;
                },
                userId
            );

        List<Map<String, Object>> recognitionEvidence =
            jdbcTemplate.query(
                """
                select
                    id,
                    recognition_job_id,
                    problem_session_id,
                    source_asset_id,
                    input_derivative_id,
                    revision,
                    schema_version,
                    normalized_evidence_jsonb::text
                        as normalized_evidence_json,
                    upstream_quality_evidence_jsonb::text
                        as upstream_quality_evidence_json,
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
                from recognition_evidence
                where user_id = ?
                order by created_at desc
                """,
                (resultSet, rowNum) -> {
                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    row.put(
                        "recognitionEvidenceId",
                        resultSet
                            .getObject("id")
                            .toString()
                    );
                    row.put(
                        "recognitionJobId",
                        resultSet
                            .getObject(
                                "recognition_job_id"
                            )
                            .toString()
                    );
                    row.put(
                        "problemSessionId",
                        resultSet
                            .getObject(
                                "problem_session_id"
                            )
                            .toString()
                    );
                    row.put(
                        "sourceAssetId",
                        resultSet
                            .getObject(
                                "source_asset_id"
                            )
                            .toString()
                    );
                    row.put(
                        "inputDerivativeId",
                        resultSet
                            .getObject(
                                "input_derivative_id"
                            )
                            .toString()
                    );
                    row.put(
                        "revision",
                        resultSet.getInt(
                            "revision"
                        )
                    );
                    row.put(
                        "schemaVersion",
                        resultSet.getString(
                            "schema_version"
                        )
                    );
                    row.put(
                        "normalizedEvidenceJson",
                        resultSet.getString(
                            "normalized_evidence_json"
                        )
                    );
                    row.put(
                        "upstreamQualityEvidenceJson",
                        resultSet.getString(
                            "upstream_quality_evidence_json"
                        )
                    );
                    row.put(
                        "provider",
                        resultSet.getString(
                            "provider"
                        )
                    );
                    row.put(
                        "model",
                        resultSet.getString(
                            "model"
                        )
                    );
                    row.put(
                        "routePolicyVersion",
                        resultSet.getString(
                            "route_policy_version"
                        )
                    );
                    row.put(
                        "promptId",
                        resultSet.getString(
                            "prompt_id"
                        )
                    );
                    row.put(
                        "promptVersion",
                        resultSet.getString(
                            "prompt_version"
                        )
                    );
                    row.put(
                        "inputTokens",
                        resultSet.getObject(
                            "input_tokens"
                        )
                    );
                    row.put(
                        "outputTokens",
                        resultSet.getObject(
                            "output_tokens"
                        )
                    );
                    row.put(
                        "imageUnits",
                        resultSet.getObject(
                            "image_units"
                        )
                    );
                    row.put(
                        "requestUnits",
                        resultSet.getInt(
                            "request_units"
                        )
                    );
                    row.put(
                        "providerLatencyMs",
                        resultSet.getObject(
                            "provider_latency_ms"
                        )
                    );
                    row.put(
                        "totalLatencyMs",
                        resultSet.getObject(
                            "total_latency_ms"
                        )
                    );
                    row.put(
                        "estimatedCostMicros",
                        resultSet.getObject(
                            "estimated_cost_micros"
                        )
                    );
                    row.put(
                        "currency",
                        resultSet.getString(
                            "currency"
                        )
                    );
                    row.put(
                        "pricingVersion",
                        resultSet.getString(
                            "pricing_version"
                        )
                    );
                    row.put(
                        "createdAt",
                        resultSet
                            .getTimestamp(
                                "created_at"
                            )
                            .toInstant()
                            .toString()
                    );

                    row.put(
                        "rawProviderOutputIncluded",
                        false
                    );

                    return row;
                },
                userId
            );

        List<Map<String, Object>> problemParseJobs =
            jdbcTemplate.query(
                """
                select
                    id,
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
                    last_error_code,
                    last_failure_class,
                    review_required,
                    created_at,
                    completed_at
                from problem_parse_jobs
                where user_id = ?
                order by created_at desc
                """,
                (resultSet, rowNum) -> {
                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    row.put(
                        "parseJobId",
                        resultSet
                            .getObject("id")
                            .toString()
                    );
                    row.put(
                        "problemSessionId",
                        resultSet
                            .getObject(
                                "problem_session_id"
                            )
                            .toString()
                    );
                    row.put(
                        "recognitionEvidenceId",
                        resultSet
                            .getObject(
                                "recognition_evidence_id"
                            )
                            .toString()
                    );
                    row.put(
                        "recognitionEvidenceRevision",
                        resultSet.getInt(
                            "recognition_evidence_revision"
                        )
                    );
                    row.put(
                        "status",
                        resultSet.getString(
                            "status"
                        )
                    );
                    row.put(
                        "capability",
                        resultSet.getString(
                            "capability"
                        )
                    );
                    row.put(
                        "promptId",
                        resultSet.getString(
                            "prompt_id"
                        )
                    );
                    row.put(
                        "promptVersion",
                        resultSet.getString(
                            "prompt_version"
                        )
                    );
                    row.put(
                        "schemaVersion",
                        resultSet.getString(
                            "schema_version"
                        )
                    );
                    row.put(
                        "routePolicyVersion",
                        resultSet.getString(
                            "route_policy_version"
                        )
                    );
                    row.put(
                        "attemptCount",
                        resultSet.getInt(
                            "attempt_count"
                        )
                    );
                    row.put(
                        "maxAttempts",
                        resultSet.getInt(
                            "max_attempts"
                        )
                    );
                    row.put(
                        "lastErrorCode",
                        resultSet.getString(
                            "last_error_code"
                        )
                    );
                    row.put(
                        "lastFailureClass",
                        resultSet.getString(
                            "last_failure_class"
                        )
                    );
                    row.put(
                        "reviewRequired",
                        resultSet.getBoolean(
                            "review_required"
                        )
                    );
                    row.put(
                        "createdAt",
                        resultSet
                            .getTimestamp(
                                "created_at"
                            )
                            .toInstant()
                            .toString()
                    );
                    row.put(
                        "completedAt",
                        instantOrNull(
                            resultSet.getTimestamp(
                                "completed_at"
                            )
                        )
                    );

                    return row;
                },
                userId
            );

        List<Map<String, Object>> problemParses =
            jdbcTemplate.query(
                """
                select
                    id,
                    parse_job_id,
                    problem_session_id,
                    recognition_evidence_id,
                    recognition_evidence_revision,
                    revision,
                    source,
                    support_status,
                    unsupported_reason,
                    review_required,
                    schema_version,
                    normalized_problem_jsonb::text
                        as normalized_problem_json,
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
                    parent_parse_id,
                    correction_reason,
                    corrected_fields_jsonb::text
                        as corrected_fields_json,
                    correction_schema_version,
                    created_at
                from problem_parses
                where user_id = ?
                order by created_at desc
                """,
                (resultSet, rowNum) -> {
                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    row.put(
                        "problemParseId",
                        resultSet
                            .getObject("id")
                            .toString()
                    );
                    row.put(
                        "parseJobId",
                        objectStringOrNull(
                            resultSet.getObject(
                                "parse_job_id"
                            )
                        )
                    );
                    row.put(
                        "problemSessionId",
                        resultSet
                            .getObject(
                                "problem_session_id"
                            )
                            .toString()
                    );
                    row.put(
                        "recognitionEvidenceId",
                        resultSet
                            .getObject(
                                "recognition_evidence_id"
                            )
                            .toString()
                    );
                    row.put(
                        "recognitionEvidenceRevision",
                        resultSet.getInt(
                            "recognition_evidence_revision"
                        )
                    );
                    row.put(
                        "revision",
                        resultSet.getInt(
                            "revision"
                        )
                    );
                    row.put(
                        "source",
                        resultSet.getString(
                            "source"
                        )
                    );
                    row.put(
                        "supportStatus",
                        resultSet.getString(
                            "support_status"
                        )
                    );
                    row.put(
                        "unsupportedReason",
                        resultSet.getString(
                            "unsupported_reason"
                        )
                    );
                    row.put(
                        "reviewRequired",
                        resultSet.getBoolean(
                            "review_required"
                        )
                    );
                    row.put(
                        "schemaVersion",
                        resultSet.getString(
                            "schema_version"
                        )
                    );
                    row.put(
                        "normalizedProblemJson",
                        resultSet.getString(
                            "normalized_problem_json"
                        )
                    );
                    row.put(
                        "provider",
                        resultSet.getString(
                            "provider"
                        )
                    );
                    row.put(
                        "model",
                        resultSet.getString(
                            "model"
                        )
                    );
                    row.put(
                        "routePolicyVersion",
                        resultSet.getString(
                            "route_policy_version"
                        )
                    );
                    row.put(
                        "promptId",
                        resultSet.getString(
                            "prompt_id"
                        )
                    );
                    row.put(
                        "promptVersion",
                        resultSet.getString(
                            "prompt_version"
                        )
                    );
                    row.put(
                        "fallbackUsed",
                        booleanOrNull(
                            resultSet,
                            "fallback_used"
                        )
                    );
                    row.put(
                        "inputTokens",
                        resultSet.getObject(
                            "input_tokens"
                        )
                    );
                    row.put(
                        "outputTokens",
                        resultSet.getObject(
                            "output_tokens"
                        )
                    );
                    row.put(
                        "imageUnits",
                        resultSet.getObject(
                            "image_units"
                        )
                    );
                    row.put(
                        "requestUnits",
                        resultSet.getObject(
                            "request_units"
                        )
                    );
                    row.put(
                        "providerLatencyMs",
                        resultSet.getObject(
                            "provider_latency_ms"
                        )
                    );
                    row.put(
                        "totalLatencyMs",
                        resultSet.getObject(
                            "total_latency_ms"
                        )
                    );
                    row.put(
                        "estimatedCostMicros",
                        resultSet.getObject(
                            "estimated_cost_micros"
                        )
                    );
                    row.put(
                        "currency",
                        resultSet.getString(
                            "currency"
                        )
                    );
                    row.put(
                        "pricingVersion",
                        resultSet.getString(
                            "pricing_version"
                        )
                    );
                    row.put(
                        "parentParseId",
                        objectStringOrNull(
                            resultSet.getObject(
                                "parent_parse_id"
                            )
                        )
                    );
                    row.put(
                        "correctionReason",
                        resultSet.getString(
                            "correction_reason"
                        )
                    );
                    row.put(
                        "correctedFields",
                        resultSet.getString(
                            "corrected_fields_json"
                        )
                    );
                    row.put(
                        "correctionSchemaVersion",
                        resultSet.getString(
                            "correction_schema_version"
                        )
                    );
                    row.put(
                        "createdAt",
                        resultSet
                            .getTimestamp(
                                "created_at"
                            )
                            .toInstant()
                            .toString()
                    );

                    row.put(
                        "rawParserOutputIncluded",
                        false
                    );

                    return row;
                },
                userId
            );

        List<Map<String, Object>> canonicalProblems =
            jdbcTemplate.query(
                """
                select
                    id,
                    problem_session_id,
                    problem_parse_id,
                    problem_parse_revision,
                    canonical_revision,
                    schema_version,
                    verifier_schema_version,
                    problem_type,
                    task_type,
                    canonical_problem_jsonb::text
                        as canonical_problem_json,
                    verifier_input_jsonb::text
                        as verifier_input_json,
                    display_jsonb::text
                        as display_json,
                    created_at
                from canonical_problems
                where user_id = ?
                order by created_at desc
                """,
                (resultSet, rowNum) -> {
                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    row.put(
                        "canonicalProblemId",
                        resultSet
                            .getObject("id")
                            .toString()
                    );
                    row.put(
                        "problemSessionId",
                        resultSet
                            .getObject(
                                "problem_session_id"
                            )
                            .toString()
                    );
                    row.put(
                        "problemParseId",
                        resultSet
                            .getObject(
                                "problem_parse_id"
                            )
                            .toString()
                    );
                    row.put(
                        "problemParseRevision",
                        resultSet.getInt(
                            "problem_parse_revision"
                        )
                    );
                    row.put(
                        "canonicalRevision",
                        resultSet.getInt(
                            "canonical_revision"
                        )
                    );
                    row.put(
                        "schemaVersion",
                        resultSet.getString(
                            "schema_version"
                        )
                    );
                    row.put(
                        "verifierSchemaVersion",
                        resultSet.getString(
                            "verifier_schema_version"
                        )
                    );
                    row.put(
                        "problemType",
                        resultSet.getString(
                            "problem_type"
                        )
                    );
                    row.put(
                        "taskType",
                        resultSet.getString(
                            "task_type"
                        )
                    );
                    row.put(
                        "canonicalProblemJson",
                        resultSet.getString(
                            "canonical_problem_json"
                        )
                    );
                    row.put(
                        "verifierInputJson",
                        resultSet.getString(
                            "verifier_input_json"
                        )
                    );
                    row.put(
                        "displayJson",
                        resultSet.getString(
                            "display_json"
                        )
                    );
                    row.put(
                        "createdAt",
                        resultSet
                            .getTimestamp(
                                "created_at"
                            )
                            .toInstant()
                            .toString()
                    );

                    return row;
                },
                userId
            );

        List<Map<String, Object>>
            problemClassificationJobs =
            jdbcTemplate.query(
                """
                select
                    id,
                    problem_session_id,
                    canonical_problem_id,
                    canonical_problem_revision,
                    ontology_version,
                    projection_version,
                    status,
                    capability,
                    prompt_id,
                    prompt_version,
                    schema_version,
                    route_policy_version,
                    attempt_count,
                    max_attempts,
                    last_error_code,
                    last_failure_class,
                    created_at,
                    updated_at,
                    started_at,
                    completed_at
                from problem_classification_jobs
                where user_id = ?
                order by created_at desc
                """,
                (resultSet, rowNum) -> {
                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    row.put(
                        "classificationJobId",
                        resultSet
                            .getObject("id")
                            .toString()
                    );
                    row.put(
                        "problemSessionId",
                        resultSet
                            .getObject(
                                "problem_session_id"
                            )
                            .toString()
                    );
                    row.put(
                        "canonicalProblemId",
                        resultSet
                            .getObject(
                                "canonical_problem_id"
                            )
                            .toString()
                    );
                    row.put(
                        "canonicalProblemRevision",
                        resultSet.getInt(
                            "canonical_problem_revision"
                        )
                    );
                    row.put(
                        "ontologyVersion",
                        resultSet.getString(
                            "ontology_version"
                        )
                    );
                    row.put(
                        "projectionVersion",
                        resultSet.getString(
                            "projection_version"
                        )
                    );
                    row.put(
                        "status",
                        resultSet.getString(
                            "status"
                        )
                    );
                    row.put(
                        "capability",
                        resultSet.getString(
                            "capability"
                        )
                    );
                    row.put(
                        "promptId",
                        resultSet.getString(
                            "prompt_id"
                        )
                    );
                    row.put(
                        "promptVersion",
                        resultSet.getString(
                            "prompt_version"
                        )
                    );
                    row.put(
                        "schemaVersion",
                        resultSet.getString(
                            "schema_version"
                        )
                    );
                    row.put(
                        "routePolicyVersion",
                        resultSet.getString(
                            "route_policy_version"
                        )
                    );
                    row.put(
                        "attemptCount",
                        resultSet.getInt(
                            "attempt_count"
                        )
                    );
                    row.put(
                        "maxAttempts",
                        resultSet.getInt(
                            "max_attempts"
                        )
                    );
                    row.put(
                        "lastErrorCode",
                        resultSet.getString(
                            "last_error_code"
                        )
                    );
                    row.put(
                        "lastFailureClass",
                        resultSet.getString(
                            "last_failure_class"
                        )
                    );
                    row.put(
                        "createdAt",
                        resultSet
                            .getTimestamp(
                                "created_at"
                            )
                            .toInstant()
                            .toString()
                    );
                    row.put(
                        "updatedAt",
                        resultSet
                            .getTimestamp(
                                "updated_at"
                            )
                            .toInstant()
                            .toString()
                    );
                    row.put(
                        "startedAt",
                        instantOrNull(
                            resultSet.getTimestamp(
                                "started_at"
                            )
                        )
                    );
                    row.put(
                        "completedAt",
                        instantOrNull(
                            resultSet.getTimestamp(
                                "completed_at"
                            )
                        )
                    );

                    return row;
                },
                userId
            );

        List<Map<String, Object>>
            problemClassifications =
            jdbcTemplate.query(
                """
                select
                    id,
                    classification_job_id,
                    problem_session_id,
                    canonical_problem_id,
                    revision,
                    source,
                    status,
                    review_reason,
                    ontology_version,
                    classification_schema_version,
                    projection_version,
                    subject_id,
                    topic_id,
                    primary_skill_id,
                    difficulty,
                    difficulty_policy_version,
                    confidence_band,
                    confidence_policy_version,
                    confidence_calibration,
                    capability,
                    provider,
                    model,
                    prompt_id,
                    prompt_version,
                    route_policy_version,
                    fallback_used,
                    provider_latency_ms,
                    estimated_cost_micros,
                    created_at
                from problem_classifications
                where user_id = ?
                order by created_at desc
                """,
                (resultSet, rowNum) -> {
                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    row.put(
                        "classificationId",
                        resultSet
                            .getObject("id")
                            .toString()
                    );
                    row.put(
                        "classificationJobId",
                        resultSet
                            .getObject(
                                "classification_job_id"
                            )
                            .toString()
                    );
                    row.put(
                        "problemSessionId",
                        resultSet
                            .getObject(
                                "problem_session_id"
                            )
                            .toString()
                    );
                    row.put(
                        "canonicalProblemId",
                        resultSet
                            .getObject(
                                "canonical_problem_id"
                            )
                            .toString()
                    );
                    row.put(
                        "revision",
                        resultSet.getInt(
                            "revision"
                        )
                    );
                    row.put(
                        "source",
                        resultSet.getString(
                            "source"
                        )
                    );
                    row.put(
                        "status",
                        resultSet.getString(
                            "status"
                        )
                    );
                    row.put(
                        "reviewReason",
                        resultSet.getString(
                            "review_reason"
                        )
                    );
                    row.put(
                        "ontologyVersion",
                        resultSet.getString(
                            "ontology_version"
                        )
                    );
                    row.put(
                        "schemaVersion",
                        resultSet.getString(
                            "classification_schema_version"
                        )
                    );
                    row.put(
                        "projectionVersion",
                        resultSet.getString(
                            "projection_version"
                        )
                    );
                    row.put(
                        "subjectId",
                        resultSet.getString(
                            "subject_id"
                        )
                    );
                    row.put(
                        "topicId",
                        resultSet.getString(
                            "topic_id"
                        )
                    );
                    row.put(
                        "primarySkillId",
                        resultSet.getString(
                            "primary_skill_id"
                        )
                    );
                    row.put(
                        "difficulty",
                        resultSet.getString(
                            "difficulty"
                        )
                    );
                    row.put(
                        "difficultyPolicyVersion",
                        resultSet.getString(
                            "difficulty_policy_version"
                        )
                    );
                    row.put(
                        "confidenceBand",
                        resultSet.getString(
                            "confidence_band"
                        )
                    );
                    row.put(
                        "confidencePolicyVersion",
                        resultSet.getString(
                            "confidence_policy_version"
                        )
                    );
                    row.put(
                        "confidenceCalibration",
                        resultSet.getString(
                            "confidence_calibration"
                        )
                    );
                    row.put(
                        "capability",
                        resultSet.getString(
                            "capability"
                        )
                    );
                    row.put(
                        "provider",
                        resultSet.getString(
                            "provider"
                        )
                    );
                    row.put(
                        "model",
                        resultSet.getString(
                            "model"
                        )
                    );
                    row.put(
                        "promptId",
                        resultSet.getString(
                            "prompt_id"
                        )
                    );
                    row.put(
                        "promptVersion",
                        resultSet.getString(
                            "prompt_version"
                        )
                    );
                    row.put(
                        "routePolicyVersion",
                        resultSet.getString(
                            "route_policy_version"
                        )
                    );
                    row.put(
                        "fallbackUsed",
                        resultSet.getBoolean(
                            "fallback_used"
                        )
                    );
                    row.put(
                        "providerLatencyMs",
                        resultSet.getObject(
                            "provider_latency_ms"
                        )
                    );
                    row.put(
                        "estimatedCostMicros",
                        resultSet.getObject(
                            "estimated_cost_micros"
                        )
                    );
                    row.put(
                        "createdAt",
                        resultSet
                            .getTimestamp(
                                "created_at"
                            )
                            .toInstant()
                            .toString()
                    );

                    row.put(
                        "rawProviderOutputIncluded",
                        false
                    );

                    return row;
                },
                userId
            );

        List<Map<String, Object>>
            problemClassificationSecondarySkills =
            jdbcTemplate.query(
                """
                select
                    pcs.classification_id,
                    pcs.ordinal,
                    pcs.skill_id
                from problem_classification_secondary_skills pcs
                join problem_classifications pc
                  on pc.id = pcs.classification_id
                where pc.user_id = ?
                order by
                    pc.created_at desc,
                    pcs.ordinal asc
                """,
                (resultSet, rowNum) -> {
                    Map<String, Object> row =
                        new LinkedHashMap<>();

                    row.put(
                        "classificationId",
                        resultSet
                            .getObject(
                                "classification_id"
                            )
                            .toString()
                    );
                    row.put(
                        "ordinal",
                        resultSet.getInt(
                            "ordinal"
                        )
                    );
                    row.put(
                        "skillId",
                        resultSet.getString(
                            "skill_id"
                        )
                    );

                    return row;
                },
                userId
            );

        Map<String, Object> export =
            new LinkedHashMap<>();

        export.put(
            "assets",
            assets
        );
        export.put(
            "derivatives",
            derivatives
        );
        export.put(
            "qualityEvidence",
            qualityEvidence
        );
        export.put(
            "recognitionJobs",
            recognitionJobs
        );
        export.put(
            "recognitionEvidence",
            recognitionEvidence
        );
        export.put(
            "problemParseJobs",
            problemParseJobs
        );
        export.put(
            "problemParses",
            problemParses
        );
        export.put(
            "canonicalProblems",
            canonicalProblems
        );
        export.put(
            "problemClassificationJobs",
            problemClassificationJobs
        );
        export.put(
            "problemClassifications",
            problemClassifications
        );
        export.put(
            "problemClassificationSecondarySkills",
            problemClassificationSecondarySkills
        );

        export.put(
            "rawBinaryIncluded",
            false
        );
        export.put(
            "derivedBinaryIncluded",
            false
        );
        export.put(
            "rawRecognitionProviderOutputIncluded",
            false
        );
        export.put(
            "rawProblemParserOutputIncluded",
            false
        );
        export.put(
            "rawProblemClassifierOutputIncluded",
            false
        );

        export.put(
            "retentionNote",
            "Raw source image/PDF objects and derived preprocessing objects "
                + "are private object-storage assets and are removed during "
                + "account deletion. Recognition, parser, user correction, "
                + "canonical-problem, and classification lifecycle rows cascade "
                + "with the owning problem session. Raw AI provider outputs, "
                + "correction request hashes, and idempotency keys are excluded "
                + "from account export payloads."
        );

        return export;
    }

    @Override
    public void deleteUserData(
        UUID userId,
        Instant now
    ) {
        List<String> objectKeys =
            jdbcTemplate.query(
                """
                select object_key
                from problem_assets
                where user_id = ?
                  and status <> 'DELETED'

                union all

                select object_key
                from problem_asset_derivatives
                where user_id = ?
                  and object_key is not null
                """,
                (resultSet, rowNum) ->
                    resultSet.getString(
                        "object_key"
                    ),
                userId,
                userId
            );

        for (String objectKey : objectKeys) {
            storage.deleteIfExists(
                objectKey
            );
        }

        jdbcTemplate.update(
            """
            delete from problem_sessions
            where user_id = ?
            """,
            userId
        );
    }

    private static String instantOrNull(
        Timestamp timestamp
    ) {
        return timestamp == null
            ? null
            : timestamp
            .toInstant()
            .toString();
    }

    private static String objectStringOrNull(
        Object value
    ) {
        return value == null
            ? null
            : value.toString();
    }

    private static Boolean booleanOrNull(
        java.sql.ResultSet resultSet,
        String column
    ) throws java.sql.SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull()
            ? null
            : value;
    }
}
