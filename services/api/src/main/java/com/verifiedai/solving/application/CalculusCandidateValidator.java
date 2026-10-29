package com.verifiedai.solving.application;

import com.fasterxml.jackson.databind.*;
import com.verifiedai.problem.application.solving.*;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import com.verifiedai.solving.domain.model.SolutionCandidate;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import java.util.*;

/** The candidate proposes only expressions; every calculus obligation belongs to the source. */
final class CalculusCandidateValidator {
    SolutionCandidate validate(String json, SolveSource source, String provider) {
        try {
            var c = VersionedJsonContract.validate("solution-candidate-v2", json);
            var original = CalculusSourceDocument.canonical(source);
            if (!source.canonicalProblemId().toString().equals(c.path("canonicalProblemId").asText())
                || !source.problemParseId().toString().equals(c.path("problemParseId").asText())
                || source.problemParseRevision() != c.path("problemParseRevision").asInt()
                || source.canonicalRevision() != c.path("canonicalRevision").asInt()
                || !source.problemType().equals(c.path("problemType").asText())
                || !source.taskType().equals(c.path("taskType").asText())
                || !CalculusSourceDocument.method(source.problemType(), source.taskType()).equals(c.path("method").asText())) invalid();
            if (!("LOCAL_FIXTURE".equals(provider) ? "LOCAL_FIXTURE" : "AI_MODEL").equals(c.path("generationOrigin").asText())) invalid();
            if (!c.path("sourceAssumptionIds").isEmpty() || !c.path("derivedRestrictionIds").isEmpty()) invalid();
            var expected = new ObjectMapper().createArrayNode();
            original.path("calculus").path("restrictions").forEach(r -> expected.add(r.path("id").asText()));
            if (!expected.equals(c.path("sourceConstraintIds"))) invalid();
            String variable = original.path("calculus").path("variable").path("symbol").asText();
            var seen = new HashSet<String>(); int order = 1;
            for (var step : c.path("steps")) {
                String id = step.path("id").asText();
                if (seen.contains(id) || step.path("order").asInt() != order++) invalid();
                for (var dependency : step.path("dependsOn")) if (!seen.contains(dependency.asText())) invalid();
                statement(step.path("claim"), variable); seen.add(id);
            }
            statement(c.path("finalAnswer"), variable);
            return new ObjectMapper().treeToValue(c, SolutionCandidate.class);
        } catch (Exception e) { throw new CandidateRejected("CALCULUS_CANDIDATE_INVALID"); }
    }
    private void statement(JsonNode claim, String variable) {
        if (!"EXPRESSION".equals(claim.path("kind").asText())) invalid();
        CalculusSourceDocument.expression(claim.path("expression"), variable, 1, 0, new int[]{0});
        new CanonicalMathCodec().statement(claim);
    }
    private static void invalid() { throw new IllegalArgumentException("Candidate/source mismatch"); }
}
