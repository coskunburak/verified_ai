package com.verifiedai.verification.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import com.verifiedai.verification.domain.repository.VerificationRunRepository;
import com.verifiedai.verification.infrastructure.privacy.VerificationLifecycleContributor;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.verifiedai.verification.domain.model.MathematicalExecution.VERIFIER_VERSION;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificationLifecycleContributorTest {
    private final VerificationRunRepository repository = mock(VerificationRunRepository.class);
    private final VerificationLifecycleContributor contributor = new VerificationLifecycleContributor(repository, org.mockito.Mockito.mock(com.verifiedai.verification.domain.repository.VerificationDecisionRepository.class));
    // Deliberately has no Java time module, matching the shared account export boundary.
    private final ObjectMapper accountMapper = new ObjectMapper();

    @Test void plainAccountSerializerPreservesAllEvidenceAndProvenanceWithIsoTimestamps() throws Exception {
        UUID user = UUID.randomUUID(), execution = UUID.randomUUID(), plan = UUID.randomUUID(), solution = UUID.randomUUID();
        var source = new PlanningSource.Identity(user, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2, 3);
        var timestamp = Instant.parse("2026-10-29T07:53:49.123456789Z");
        var exact = new ExactValue("1", "3");
        var proven = new Result(VerificationMethod.EQUATION_SUBSTITUTION, "v1", VERIFIER_VERSION,
            Outcome.PROVEN, Reason.VALID_ROOT, List.of("denominator-1"),
            new Proof(Descriptor.ORIGINAL_EQUATION_SUBSTITUTION, exact, exact, null));
        var failed = new Result(VerificationMethod.DOMAIN_CONSTRAINT_CHECK, "v1", VERIFIER_VERSION,
            Outcome.EXECUTION_FAILED, Reason.TIMEOUT, List.of("denominator-1"), null);
        var evidence = List.of(proven, failed).stream().map(result -> new VerificationEvidence(
            VerificationEvidence.identity(execution, result.method()), execution, plan, "verification-policy-v1",
            "canonical-capabilities-v2", source, solution, CanonicalSolution.Version.CURRENT, result.method().purpose(),
            Map.of("x", PlanningSource.Domain.REAL), result, "export-correlation", timestamp)).toList();
        var run = new VerificationRun(execution, UUID.randomUUID(), plan, "verification-policy-v1",
            "canonical-capabilities-v2", source, solution, CanonicalSolution.Version.CURRENT,
            "export-correlation", timestamp, evidence);
        when(repository.export(user)).thenReturn(List.of(run));

        var document = accountMapper.readTree(accountMapper.writeValueAsString(
            Map.of(contributor.category(), contributor.exportUserData(user))));
        var exported = document.path("verificationEvidence").path("runs");
        assertThat(exported.size()).isEqualTo(1);
        var exportedRun = exported.get(0);
        assertThat(exportedRun.size()).isEqualTo(11);
        assertThat(exportedRun.path("id").asText()).isEqualTo(run.id().toString());
        assertThat(exportedRun.path("requestKey").asText()).isEqualTo(run.requestKey().toString());
        assertProvenance(exportedRun, run);
        assertThat(exportedRun.path("evidence").size()).isEqualTo(evidence.size());
        for (int index = 0; index < evidence.size(); index++) {
            var expected = evidence.get(index);
            var actual = exportedRun.path("evidence").get(index);
            assertThat(actual.size()).isEqualTo(13);
            assertThat(actual.path("id").asText()).isEqualTo(expected.id().toString());
            assertThat(actual.path("executionId").asText()).isEqualTo(run.id().toString());
            assertProvenance(actual, run);
            assertThat(actual.path("claim").asText()).isEqualTo(expected.claim().name());
            assertThat(actual.path("domainAssumptions")).isEqualTo(accountMapper.valueToTree(expected.domainAssumptions()));
            assertThat(actual.path("result")).isEqualTo(accountMapper.valueToTree(expected.result()));
        }
        assertThat(exportedRun.path("evidence").get(0).path("result").path("proof").path("leftValue").path("denominator").asText())
            .isEqualTo("3");
        assertThat(exportedRun.path("evidence").get(1).path("result").path("proof").isNull()).isTrue();
        assertThat(run.createdAt()).isEqualTo(timestamp);
        verify(repository).export(user);
        verifyNoMoreInteractions(repository);
    }

    private void assertProvenance(JsonNode actual, VerificationRun expected) {
        assertThat(actual.path("planId").asText()).isEqualTo(expected.planId().toString());
        assertThat(actual.path("policyVersion").asText()).isEqualTo(expected.policyVersion());
        assertThat(actual.path("capabilityVersion").asText()).isEqualTo(expected.capabilityVersion());
        assertThat(actual.path("source")).isEqualTo(accountMapper.valueToTree(expected.source()));
        assertThat(actual.path("solutionId").asText()).isEqualTo(expected.solutionId().toString());
        assertThat(actual.path("solutionVersion")).isEqualTo(accountMapper.valueToTree(expected.solutionVersion()));
        assertThat(actual.path("correlationId").asText()).isEqualTo(expected.correlationId());
        assertThat(actual.path("createdAt").isTextual()).isTrue();
        assertThat(actual.path("createdAt").asText()).isEqualTo(expected.createdAt().toString());
    }

    @Test void ownerWithoutRunsExportsAnEmptyJsonArray() throws Exception {
        UUID user = UUID.randomUUID();
        when(repository.export(user)).thenReturn(List.of());
        var exported = accountMapper.readTree(accountMapper.writeValueAsString(contributor.exportUserData(user)));
        assertThat(exported.path("runs").isArray()).isTrue();
        assertThat(exported.path("runs").isEmpty()).isTrue();
        verify(repository).export(user);
        verifyNoMoreInteractions(repository);
    }
}
