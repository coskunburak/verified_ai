package com.verifiedai.verification.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.verification.domain.model.VerificationPlan.Reason;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static com.verifiedai.verification.PlanningTestData.*;
import static org.assertj.core.api.Assertions.*;

class VerificationSourceReaderTest {
    final VerificationSourceReader reader = new VerificationSourceReader(CODEC);
    void rejects(Reason reason, Consumer<ObjectNode> mutate) {
        var source = equation(); var root = (ObjectNode) CODEC.parse(source.canonicalJson()); mutate.accept(root);
        assertThatThrownBy(() -> reader.read(json(source, root))).isInstanceOfSatisfying(
            VerificationSourceReader.SourceRejected.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }
    @Test void matchesCanonicalLineageBeforeDecodingMath() {
        rejects(Reason.LINEAGE_MISMATCH, n -> ((ObjectNode)n.path("sourceParse")).put("problemParseRevision", 2));
        rejects(Reason.UNSUPPORTED_REPRESENTATION, n -> n.put("schemaVersion", "canonical-problem-v99"));
        rejects(Reason.UNSUPPORTED_REPRESENTATION, n -> ((ObjectNode)n.path("complexityPolicy")).put("maxAstDepth", 999));
    }
    @Test void unknownFunctionsAndNodeKindsNeverBecomeGenericExpressions() {
        rejects(Reason.UNKNOWN_FUNCTION, n -> {
            var left = (ObjectNode)n.path("statements").get(0).path("left"); left.removeAll();
            left.put("kind", "FUNCTION").put("function", "RUN_CODE"); left.putArray("args").add(CODEC.json(x()));
        });
        rejects(Reason.INVALID_CANONICAL_STRUCTURE, n -> ((ObjectNode)n.path("statements").get(0).path("right")).put("kind", "PYTHON"));
    }
    @Test void malformedNumericAndVariableSyntaxIsRejected() {
        rejects(Reason.INVALID_CANONICAL_STRUCTURE, n -> ((ObjectNode)n.path("statements").get(0).path("right")).put("value", "__import__('os')"));
        rejects(Reason.INVALID_CANONICAL_STRUCTURE, n -> ((ObjectNode)n.path("statements").get(0).path("right")).put("value", 1));
        rejects(Reason.INVALID_CANONICAL_STRUCTURE, n -> n.withArray("variables").add(n.path("variables").get(0).deepCopy()));
    }
    @Test void missingCollectionsWrongShapesAndExcessiveCountsFailClosed() {
        rejects(Reason.INVALID_CANONICAL_STRUCTURE, n -> n.remove("sourceConstraints"));
        rejects(Reason.INVALID_CANONICAL_STRUCTURE, n -> ((ObjectNode)n.path("statements").get(0)).set("expression", CODEC.json(x())));
        rejects(Reason.INVALID_CANONICAL_STRUCTURE, n -> { var s = n.path("statements").get(0).deepCopy(); for (int i=0;i<8;i++) n.withArray("statements").add(s); });
    }
    @Test void unknownTaskHasExplicitOutcome() {
        for (String task : new String[]{"DIFFERENTIATE", "INTEGRATE", "FIND_LIMIT", "UNKNOWN_TASK"}) {
            var source = equation(); var root = (ObjectNode)CODEC.parse(source.canonicalJson()); root.put("taskType", task);
            var changed = new SolveSource(source.canonicalProblemId(), source.userId(), source.problemSessionId(), source.problemParseId(),
                1, 1, source.problemType(), task, root.toString());
            assertThatThrownBy(() -> reader.read(changed)).isInstanceOfSatisfying(VerificationSourceReader.SourceRejected.class,
                e -> assertThat(e.reason()).isEqualTo(Reason.UNSUPPORTED_TASK_TYPE));
            assertThatThrownBy(() -> reader.readForExecution(changed)).isInstanceOfSatisfying(VerificationSourceReader.SourceRejected.class,
                e -> assertThat(e.reason()).isEqualTo(Reason.UNSUPPORTED_TASK_TYPE));
        }
    }
    @Test void calculusAndUnknownProblemTypesCannotBeInterpretedAsV1Sources() {
        for (String type : new String[]{"DERIVATIVE", "INTEGRAL", "LIMIT", "UNKNOWN_PROBLEM"}) {
            var source = equation(); var root = (ObjectNode)CODEC.parse(source.canonicalJson()); root.put("problemType", type);
            var changed = new SolveSource(source.canonicalProblemId(), source.userId(), source.problemSessionId(), source.problemParseId(),
                1, 1, type, source.taskType(), root.toString());
            assertThatThrownBy(() -> reader.read(changed)).isInstanceOfSatisfying(VerificationSourceReader.SourceRejected.class,
                e -> assertThat(e.reason()).isEqualTo(Reason.UNSUPPORTED_PROBLEM_TYPE));
            assertThatThrownBy(() -> reader.readForExecution(changed)).isInstanceOfSatisfying(VerificationSourceReader.SourceRejected.class,
                e -> assertThat(e.reason()).isEqualTo(Reason.UNSUPPORTED_PROBLEM_TYPE));
        }
    }
    @Test void jsonSizeDuplicateKeysAndExcessiveAstDepthAreBounded() {
        var source = equation();
        for (String raw : new String[]{"{\"schemaVersion\":\"x\",\"schemaVersion\":\"y\"}", " ".repeat(131073)}) {
            var bad = new SolveSource(source.canonicalProblemId(), source.userId(), source.problemSessionId(), source.problemParseId(), 1, 1,
                source.problemType(), source.taskType(), raw);
            assertThatThrownBy(() -> reader.read(bad)).isInstanceOf(VerificationSourceReader.SourceRejected.class);
        }
        rejects(Reason.INVALID_CANONICAL_STRUCTURE, n -> {
            ObjectNode expression = CODEC.json(x());
            for (int i=0;i<33;i++) { var unary=CODEC.object().put("kind","UNARY").put("operator","NEGATE"); unary.set("operand",expression); expression=unary; }
            ((ObjectNode)n.path("statements").get(0)).set("left",expression);
        });
    }
    @org.junit.jupiter.api.Test void executionDoesNotInferVariableRolesFromParameters() {
        var source = com.verifiedai.verification.PlanningTestData.equation();
        var codec = com.verifiedai.verification.PlanningTestData.CODEC;
        var json = (com.fasterxml.jackson.databind.node.ObjectNode) codec.parse(source.canonicalJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) json.path("variables").get(0)).put("role", "PARAMETER");
        var changed = com.verifiedai.verification.PlanningTestData.json(source, json);
        var decoder = new VerificationSourceReader(codec);
        org.assertj.core.api.Assertions.assertThat(decoder.read(changed)).isNotNull();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> decoder.readForExecution(changed))
            .isInstanceOf(VerificationSourceReader.SourceRejected.class);
    }
}
