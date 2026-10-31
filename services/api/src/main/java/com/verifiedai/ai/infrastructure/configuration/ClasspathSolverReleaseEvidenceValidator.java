package com.verifiedai.ai.infrastructure.configuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.ArtifactIntegrity;
import com.verifiedai.ai.application.OutputSchemaRegistryEntry;
import com.verifiedai.ai.application.PromptRegistryEntry;
import com.verifiedai.ai.application.PromptRegistryFailureCode;
import com.verifiedai.ai.application.PromptRegistryValidationException;
import com.verifiedai.ai.application.SolverReleaseEvidenceValidator;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.ResourceLoader;

/**
 * Reads only immutable artifacts packaged with the application, never network URLs or user paths.
 * The Git-reviewed registry digest is the trust anchor. No bundled production approval is supplied.
 * External evidence review remains a human responsibility; this does not generate a quality verdict.
 */
final class ClasspathSolverReleaseEvidenceValidator implements SolverReleaseEvidenceValidator {
    private static final int MAX_BYTES = 512 * 1024;
    private static final String SCOPE = "REAL_PROVIDER_AND_PROTECTED_HOLDOUT";
    private final ResourceLoader resources;
    private final ObjectMapper mapper = evidenceMapper();

    /** Versioned evidence uses exact ISO-8601 Duration text, independent of optional modules. */
    static ObjectMapper evidenceMapper() {
        SimpleModule durations = new SimpleModule("solver-release-duration-v1");
        durations.addSerializer(Duration.class, new JsonSerializer<Duration>() {
            @Override
            public void serialize(Duration value, JsonGenerator output, SerializerProvider provider) throws IOException {
                output.writeString(value.toString());
            }
        });
        return new ObjectMapper().registerModule(durations)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    ClasspathSolverReleaseEvidenceValidator(ResourceLoader resources) {
        this.resources = resources;
    }

    @Override
    public void validate(PromptRegistryEntry prompt, OutputSchemaRegistryEntry schema, AiRoutePlan route) {
        try {
            var approval = prompt.approval();
            require(approval != null && approval.solverAuthorizationReference() != null);
            JsonNode authorization = read(approval.solverAuthorizationReference(), approval.solverAuthorizationSha256());
            requireFields(authorization, Set.of("schemaVersion", "scope", "outcome", "approvalReference",
                "approvedBy", "approvedAt", "report", "baseline"));
            equal(authorization, "schemaVersion", "solver-release-authorization-v1");
            equal(authorization, "scope", SCOPE);
            equal(authorization, "outcome", "PASS");
            equal(authorization, "approvalReference", approval.approvalReference());
            require(text(authorization, "approvalReference").matches("docs/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*\\.md"));
            equal(authorization, "approvedBy", approval.approvedBy());
            equal(authorization, "approvedAt", approval.approvedAt().toString());
            JsonNode report = referenced(authorization.path("report"), approval.evaluationReportReference());
            JsonNode baseline = referenced(authorization.path("baseline"), approval.baselineReference());
            evidence(report);
            evidence(baseline);
            require(report.path("datasetSha256").equals(baseline.path("datasetSha256")));
            require(report.path("caseSchemaVersion").equals(baseline.path("caseSchemaVersion")));
            require(report.path("artifacts").path("schemaSha256").equals(baseline.path("artifacts").path("schemaSha256")));
            require(report.path("pricingVersion").equals(baseline.path("pricingVersion")));
            require(report.path("currency").equals(baseline.path("currency")));
            require(report.path("workloadSha256").equals(baseline.path("workloadSha256")));
            JsonNode artifacts = report.path("artifacts");
            requireFields(artifacts, Set.of("promptSha256", "schemaSha256", "route"));
            equal(artifacts, "promptSha256", prompt.contentSha256());
            equal(artifacts, "schemaSha256", schema.contentSha256());
            // Bind every execution setting and fallback in the record, not only a mutable route alias.
            require(artifacts.path("route").equals(mapper.readTree(mapper.writeValueAsBytes(route))));
            equal(report, "pricingVersion", route.pricingVersion());
            require(route.allTargets().stream().allMatch(target ->
                target.model().matches("[^\\s@]+@sha256:[a-f0-9]{64}")));
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private JsonNode referenced(JsonNode reference, String expectedReference) throws IOException {
        requireFields(reference, Set.of("reference", "sha256"));
        equal(reference, "reference", expectedReference);
        return read(expectedReference, text(reference, "sha256"));
    }

    private void evidence(JsonNode report) {
        requireFields(report, Set.of("schemaVersion", "scope", "outcome", "datasetSha256",
            "workloadSha256", "caseSchemaVersion", "evaluationCodeCommit", "pricingVersion", "currency",
            "artifacts", "evidence", "criticalFailures", "approvalReference", "approvedBy"));
        equal(report, "schemaVersion", "solver-production-evidence-v1");
        equal(report, "scope", SCOPE);
        equal(report, "outcome", "PASS");
        for (String field : List.of("datasetSha256", "workloadSha256")) {
            ArtifactIntegrity.requireDigest(text(report, field));
        }
        require(text(report, "evaluationCodeCommit").matches("(?:[a-f0-9]{40}|[a-f0-9]{64})"));
        require(text(report, "caseSchemaVersion").matches("[a-z][a-z0-9-]{0,95}"));
        require(text(report, "pricingVersion").matches("[a-z][a-z0-9-]{0,95}"));
        require(text(report, "currency").matches("[A-Z]{3}"));
        require(text(report, "approvalReference").matches("docs/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*\\.md"));
        require(!text(report, "approvalReference").contains("//"));
        ArtifactIntegrity.requireIdentifier(text(report, "approvedBy"));
        require(report.path("criticalFailures").isIntegralNumber()
            && report.path("criticalFailures").canConvertToInt() && report.path("criticalFailures").intValue() == 0);
        JsonNode artifacts = report.path("artifacts");
        requireFields(artifacts, Set.of("promptSha256", "schemaSha256", "route"));
        ArtifactIntegrity.requireDigest(text(artifacts, "promptSha256"));
        ArtifactIntegrity.requireDigest(text(artifacts, "schemaSha256"));
        require(artifacts.path("route").isObject());
        Set<String> required = Set.of("independentLabels", "representativeBenchmark", "approvedThresholds",
            "sufficientObservations", "realProvider", "protectedHoldout", "cost", "latency");
        requireFields(report.path("evidence"), required);
        for (String key : required) {
            require(report.path("evidence").path(key).isBoolean() && report.path("evidence").path(key).booleanValue());
        }
    }

    private JsonNode read(String path, String digest) throws IOException {
        require(path != null && path.matches("evaluations/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*\\.json") && !path.contains("//"));
        ArtifactIntegrity.requireDigest(digest);
        try (InputStream input = resources.getResource("classpath:" + path).getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            require(bytes.length > 0 && bytes.length <= MAX_BYTES);
            ArtifactIntegrity.verify(bytes, digest);
            JsonNode value = mapper.readTree(bytes);
            require(value != null && value.isObject());
            return value;
        }
    }

    private static void requireFields(JsonNode object, Set<String> names) {
        require(object.isObject() && object.size() == names.size());
        for (String name : names) require(object.hasNonNull(name));
    }

    private static String text(JsonNode object, String field) {
        require(object.path(field).isTextual());
        return object.path(field).textValue();
    }

    private static void equal(JsonNode object, String field, String value) {
        require(object.path(field).isTextual() && object.path(field).textValue().equals(value));
    }

    private static void require(boolean condition) {
        if (!condition) throw invalid();
    }

    private static PromptRegistryValidationException invalid() {
        return new PromptRegistryValidationException(PromptRegistryFailureCode.AI_SOLVER_RELEASE_EVIDENCE_INVALID,
            "Solver release blocked: missing, incompatible or unapproved immutable evidence");
    }
}
