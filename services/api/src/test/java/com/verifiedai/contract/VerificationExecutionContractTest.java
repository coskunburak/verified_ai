package com.verifiedai.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import com.verifiedai.verification.infrastructure.http.ExecutionWireCodec;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Owner gate D and CI explicitly provision Python; default backend-only runs report this skip. */
@EnabledIfEnvironmentVariable(named = "VERIFIEDAI_MATH_WIRE", matches = "true")
class VerificationExecutionContractTest {
    @Test void actualPythonProcessConsumesJavaWireRequestAndProducesValidatedEvidence() throws Exception {
        var mapper = new ObjectMapper(); var canonical = new CanonicalMathCodec();
        var cases = mapper.readTree(Files.readString(Path.of("../../packages/test-fixtures/verification/execution-v1/cases.json")));
        var wire = new ExecutionWireCodec();
        for (var fixture : cases) {
            var input = fixture.path("request");
            Map<String, PlanningSource.Domain> variables = new HashMap<>();
            input.path("variables").forEach(v -> variables.put(v.path("symbol").textValue(), PlanningSource.Domain.valueOf(v.path("domain").textValue())));
            var restrictions = new ArrayList<PlanningSource.Restriction>();
            for (var r : input.path("restrictions")) {
                var left = canonical.object().put("kind", "EXPRESSION"); left.set("expression", r.path("left"));
                var right = canonical.object().put("kind", "EXPRESSION"); right.set("expression", r.path("right"));
                restrictions.add(new PlanningSource.Restriction(r.path("id").textValue(), PlanningSource.Relation.valueOf(r.path("relation").textValue()),
                    ((com.verifiedai.solving.domain.model.canonical.MathStatement.Expression) canonical.statement(left)).expression(),
                    ((com.verifiedai.solving.domain.model.canonical.MathStatement.Expression) canonical.statement(right)).expression()));
            }
            var request = new Request(UUID.fromString(input.path("executionId").textValue()), input.path("correlationId").textValue(),
                VerificationMethod.valueOf(input.path("method").textValue()), canonical.statement(input.path("original")),
                canonical.statement(input.path("candidate")), variables, restrictions);
            String python = Objects.requireNonNull(System.getenv("VERIFIEDAI_VERIFIER_PYTHON"), "Set the owner Python environment");
            var process = new ProcessBuilder(python, "-c", "import sys; from app.api.schemas.execution import ExecutionRequest; "
                + "from app.policies.process_executor import ProcessExecutor; "
                + "print(ProcessExecutor().execute(ExecutionRequest.model_validate_json(sys.stdin.read())).model_dump_json())")
                .directory(Path.of("../math-verifier").toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            try {
                try (var stdin = process.getOutputStream()) { stdin.write(wire.request(request)); }
                assertThat(process.waitFor(10, TimeUnit.SECONDS)).as(fixture.path("id").textValue()).isTrue();
                assertThat(process.exitValue()).isZero();
                var result = wire.result(process.getInputStream().readNBytes(16385), request);
                assertThat(result.outcome().name()).as(fixture.path("id").textValue()).isEqualTo(fixture.path("expectedOutcome").textValue());
                assertThat(result.reasonCode().name()).isEqualTo(fixture.path("expectedReason").textValue());
            } finally { process.destroyForcibly(); }
        }
    }
}
