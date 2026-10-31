package com.verifiedai.ai.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.ai.application.*;
import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

final class SolverReleaseEvidenceTest {
    private static final ObjectMapper JSON = ClasspathSolverReleaseEvidenceValidator.evidenceMapper();
    private static final String SCOPE = "REAL_PROVIDER_AND_PROTECTED_HOLDOUT";
    private static final String AUTH = "evaluations/releases/solver-v1.json";
    private static final String REPORT = "evaluations/reports/solver-v1.json";
    private static final String BASELINE = "evaluations/baselines/solver-v1.json";
    private static final Instant TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final PromptRegistryEntry ORIGINAL = PromptRegistry.defaults().require("solver", "v001");
    private static final OutputSchemaRegistryEntry SCHEMA = OutputSchemaRegistry.defaults().require("solution-candidate-v1");

    @Test
    void reviewedExactEvidenceAndPreviouslyApprovedRollbackAreAcceptedByEvidenceBoundary() throws Exception {
        Fixture fixture = new Fixture();
        assertThatCode(() -> fixture.validate()).doesNotThrowAnyException();
        AiRoutePlan previous = fixture.route;
        fixture.route = route("new-model@sha256:" + "b".repeat(64));
        rejects(fixture);
        // Restoring the exact older approved configuration reuses its reviewed evidence.
        fixture.route = previous;
        assertThatCode(() -> fixture.validate()).doesNotThrowAnyException();
    }

    @Test
    void durationEvidenceIsExactIsoTextWithoutOptionalTimeModules() throws Exception {
        assertThat(JSON.readTree(JSON.writeValueAsBytes(Duration.ofSeconds(1))).textValue()).isEqualTo("PT1S");
        Duration precise = Duration.ofSeconds(1, 1);
        assertThat(JSON.readTree(JSON.writeValueAsBytes(precise)).textValue()).isEqualTo("PT1.000000001S");
        Fixture fixture = new Fixture();
        assertThat(fixture.report.path("artifacts").path("route").path("timeout").textValue()).isEqualTo("PT1S");
        fixture.route = route(fixture.route.primaryModel(), precise);
        rejects(fixture); // A one-nanosecond configuration change invalidates the old approval.
    }

    @Test
    void numericOrDifferentTimeoutCannotSubstituteForApprovedDuration() throws Exception {
        Fixture numeric = new Fixture();
        ((ObjectNode) numeric.report.path("artifacts").path("route")).put("timeout", 1);
        numeric.reseal();
        rejects(numeric);
        Fixture changed = new Fixture();
        ((ObjectNode) changed.report.path("artifacts").path("route")).put("timeout", "PT2S");
        changed.reseal();
        rejects(changed);
    }

    @Test
    void reportBaselineAndAuthorizationSubstitutionFailClosed() throws Exception {
        for (String path : List.of(AUTH, REPORT, BASELINE)) {
            Fixture fixture = new Fixture();
            fixture.bytes.put(path, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            rejects(fixture);
        }
    }

    @Test
    void fixturePassBlockedOrFailedScopeNeverBecomesProductionEvidence() throws Exception {
        for (String scope : List.of("LOCAL_FIXTURE", "DETERMINISTIC_REGRESSION", "PROTECTED_HOLDOUT")) {
            Fixture fixture = new Fixture();
            fixture.report.put("scope", scope);
            fixture.reseal();
            rejects(fixture);
        }
        for (String outcome : List.of("FAIL", "BLOCKED")) {
            Fixture fixture = new Fixture();
            fixture.report.put("outcome", outcome);
            fixture.reseal();
            rejects(fixture);
        }
    }

    @Test
    void criticalFailuresAndEveryMissingExternalRequirementBlockRelease() throws Exception {
        Fixture critical = new Fixture();
        critical.report.put("criticalFailures", 1);
        critical.reseal();
        rejects(critical);
        for (String field : List.of("independentLabels", "representativeBenchmark", "approvedThresholds",
            "sufficientObservations", "realProvider", "protectedHoldout", "cost", "latency")) {
            Fixture fixture = new Fixture();
            ((ObjectNode) fixture.report.path("evidence")).put(field, false);
            fixture.reseal();
            rejects(fixture);
        }
    }

    @Test
    void criticalAttestationsCannotBeCoercedFromStringsOrOmitted() throws Exception {
        Fixture textual = new Fixture();
        ((ObjectNode) textual.report.path("evidence")).put("realProvider", "true");
        textual.reseal();
        rejects(textual);
        Fixture missing = new Fixture();
        ((ObjectNode) missing.report.path("evidence")).remove("protectedHoldout");
        missing.reseal();
        rejects(missing);
        Fixture extra = new Fixture();
        ((ObjectNode) extra.report.path("evidence")).put("allowFixtureApproval", true);
        extra.reseal();
        rejects(extra);
        Fixture overflow = new Fixture();
        overflow.report.put("criticalFailures", new java.math.BigInteger("18446744073709551616"));
        overflow.reseal();
        rejects(overflow);
    }

    @Test
    void promptSchemaRouteModelAndPricingSubstitutionAreRejected() throws Exception {
        for (String key : List.of("promptSha256", "schemaSha256")) {
            Fixture fixture = new Fixture();
            ((ObjectNode) fixture.report.path("artifacts")).put(key, "b".repeat(64));
            fixture.reseal();
            rejects(fixture);
        }
        Fixture route = new Fixture();
        ((ObjectNode) route.report.path("artifacts").path("route")).put("maxAttempts", 2);
        route.reseal();
        rejects(route);
        for (String key : List.of("datasetSha256", "workloadSha256", "caseSchemaVersion", "pricingVersion", "currency")) {
            Fixture fixture = new Fixture();
            fixture.baseline.put(key, key.endsWith("Sha256") ? "b".repeat(64) : key.equals("currency") ? "EUR" : "other-v1");
            fixture.reseal();
            rejects(fixture);
        }
        Fixture alias = new Fixture();
        alias.route = route("mutable-model-alias");
        ((ObjectNode) alias.report.path("artifacts")).set("route", JSON.valueToTree(alias.route));
        alias.reseal();
        rejects(alias);
    }

    @Test
    void missingOversizedMalformedDuplicateAndUnsafeArtifactsAreRejected() throws Exception {
        Fixture missing = new Fixture();
        missing.bytes.remove(REPORT);
        rejects(missing);
        for (byte[] payload : List.of(new byte[524289], "{broken".getBytes(),
            "{\"schemaVersion\":1,\"schemaVersion\":2}".getBytes(), "{} {}".getBytes())) {
            Fixture fixture = new Fixture();
            fixture.bytes.put(REPORT, payload);
            fixture.sealAuthorization();
            rejects(fixture);
        }
        Fixture unsafe = new Fixture();
        ((ObjectNode) unsafe.authorization.path("report")).put("reference", "evaluations/../secret.json");
        unsafe.sealApproval();
        rejects(unsafe);
    }

    @Test
    void authorizationMustMatchHumanApprovalAndCannotOmitOrInventFields() throws Exception {
        for (String key : List.of("approvedBy", "approvedAt", "approvalReference", "schemaVersion")) {
            Fixture fixture = new Fixture();
            fixture.authorization.put(key, "substitution");
            fixture.sealApproval();
            rejects(fixture);
        }
        Fixture extra = new Fixture();
        extra.authorization.put("allowMissingHoldout", true);
        extra.sealApproval();
        rejects(extra);
    }

    private static void rejects(Fixture fixture) {
        assertThatThrownBy(fixture::validate).isInstanceOf(PromptRegistryValidationException.class)
            .hasMessageNotContaining("{broken").hasMessageNotContaining("secret.json");
    }

    private static AiRoutePlan route(String model) {
        return route(model, Duration.ofSeconds(1));
    }

    private static AiRoutePlan route(String model, Duration timeout) {
        return new AiRoutePlan(AiCapability.SOLVE, "solve-route-v1", "approved-solver-v1",
            new AiRouteTarget("APPROVED_PROVIDER", model), List.of(), "solver", "v001", "solution-candidate-v1",
            timeout, 1, 4096, 100, "approved-pricing-v1", AiRoutePlan.CachePolicy.DISABLED,
            AiRoutePlan.StreamingPolicy.DISABLED, "ZERO_RETENTION", "EU", "SOLVE:APPROVED_PROVIDER",
            "approved-gate-v1", AiRoutePlan.ReleaseStage.PRODUCTION);
    }

    private static String hash(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static final class Fixture {
        private final Map<String, byte[]> bytes = new HashMap<>();
        private AiRoutePlan route = route("reviewed-model@sha256:" + "a".repeat(64));
        private final ObjectNode report = evidence();
        private final ObjectNode baseline = report.deepCopy();
        private ObjectNode authorization;
        private PromptRegistryEntry prompt;

        private Fixture() throws Exception { reseal(); }

        private ObjectNode evidence() {
            ObjectNode value = JSON.createObjectNode();
            value.put("schemaVersion", "solver-production-evidence-v1");
            value.put("scope", SCOPE).put("outcome", "PASS").put("datasetSha256", "a".repeat(64))
                .put("workloadSha256", "a".repeat(64)).put("caseSchemaVersion", "solver-evaluation-case-v1")
                .put("evaluationCodeCommit", "a".repeat(40)).put("pricingVersion", "approved-pricing-v1")
                .put("currency", "USD").put("criticalFailures", 0)
                .put("approvalReference", "docs/approvals/solver.md").put("approvedBy", "owner-reviewer");
            ObjectNode artifacts = value.putObject("artifacts");
            artifacts.put("promptSha256", ORIGINAL.contentSha256()).put("schemaSha256", SCHEMA.contentSha256());
            artifacts.set("route", JSON.valueToTree(route));
            ObjectNode evidence = value.putObject("evidence");
            for (String key : List.of("independentLabels", "representativeBenchmark", "approvedThresholds",
                "sufficientObservations", "realProvider", "protectedHoldout", "cost", "latency")) evidence.put(key, true);
            return value;
        }

        private void reseal() throws Exception {
            bytes.put(REPORT, JSON.writeValueAsBytes(report));
            bytes.put(BASELINE, JSON.writeValueAsBytes(baseline));
            sealAuthorization();
        }

        private void sealAuthorization() throws Exception {
            authorization = JSON.createObjectNode();
            authorization.put("schemaVersion", "solver-release-authorization-v1").put("scope", SCOPE)
                .put("outcome", "PASS").put("approvalReference", "docs/approvals/solver.md")
                .put("approvedBy", "owner-reviewer").put("approvedAt", TIME.toString());
            authorization.putObject("report").put("reference", REPORT).put("sha256", hash(bytes.get(REPORT)));
            authorization.putObject("baseline").put("reference", BASELINE).put("sha256", hash(bytes.get(BASELINE)));
            sealApproval();
        }

        private void sealApproval() throws Exception {
            bytes.put(AUTH, JSON.writeValueAsBytes(authorization));
            PromptReleaseApproval approval = new PromptReleaseApproval(ORIGINAL.contentSha256(), SCHEMA.contentSha256(),
                BASELINE, REPORT, "docs/approvals/solver.md", "owner-reviewer", TIME, AUTH, hash(bytes.get(AUTH)));
            prompt = new PromptRegistryEntry(ORIGINAL.promptId(), ORIGINAL.promptVersion(), ORIGINAL.capability(),
                ORIGINAL.owner(), ORIGINAL.outputSchemaVersion(), ORIGINAL.promptArtifactPath(), PromptReleaseStage.PRODUCTION,
                BASELINE, REPORT, ORIGINAL.safetyNotes(), ORIGINAL.compatibleRoutePolicyVersion(), ORIGINAL.trainingEligibility(),
                ORIGINAL.contentSha256(), approval);
        }

        private void validate() {
            var resources = new DefaultResourceLoader() {
                @Override public Resource getResource(String location) {
                    byte[] value = bytes.get(location.substring("classpath:".length()));
                    return new ByteArrayResource(value == null ? new byte[0] : value);
                }
            };
            new ClasspathSolverReleaseEvidenceValidator(resources).validate(prompt, SCHEMA, route);
        }
    }
}
