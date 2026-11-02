package com.verifiedai.problem.application.tutoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;

/** Structural applicability only, not a verifier or a solution. Never publishes source text. */
public final class TutorHintSource {
    public enum Family { TWO_STEP_LINEAR, UNSUPPORTED }
    private TutorHintSource() {}
    public static Family family(SolveSource source) {
        try {
            var root = VersionedJsonContract.parse(source.canonicalJson());
            if (!"canonical-problem-v1".equals(root.path("schemaVersion").asText())
                || !"EQUATION".equals(source.problemType()) || !"SOLVE_EQUATION".equals(source.taskType())
                || !source.problemType().equals(root.path("problemType").asText()) || !source.taskType().equals(root.path("taskType").asText())
                || !"problem-parse-v1".equals(root.path("sourceParse").path("problemParseSchemaVersion").asText())
                || !source.problemParseId().toString().equals(root.path("sourceParse").path("problemParseId").asText())
                || !root.path("sourceParse").path("problemParseRevision").isIntegralNumber()
                || !root.path("sourceParse").path("problemParseRevision").canConvertToInt()
                || source.problemParseRevision() != root.path("sourceParse").path("problemParseRevision").intValue()
                || !root.path("display").path("reviewRequired").isBoolean()
                || root.path("display").path("reviewRequired").booleanValue()
                || !empty(root,"sourceConstraints") || !empty(root,"sourceAssumptions") || !empty(root,"derivedRestrictions")
                || !root.path("variables").isArray() || root.path("variables").size()!=1
                || !root.path("statements").isArray() || root.path("statements").size()!=1) return Family.UNSUPPORTED;
            var variable=root.path("variables").get(0);
            if (!"VARIABLE".equals(variable.path("role").asText())
                || !java.util.Set.of("UNKNOWN","REAL").contains(variable.path("domain").asText())) return Family.UNSUPPORTED;
            var statement=root.path("statements").get(0);
            var sum=statement.path("left"); var product=sum.path("left"); var symbol=product.path("right");
            if (!"RELATION".equals(statement.path("kind").asText()) || !"EQUALS".equals(statement.path("relation").asText())
                || !binary(sum,"ADD") || !binary(product,"MULTIPLY")
                || !integer(product.path("left"),2,99) || !integer(sum.path("right"),1,999)
                || !integer(statement.path("right"),1,999)
                || !"VARIABLE".equals(symbol.path("kind").asText())
                || !variable.path("symbol").asText().equals(symbol.path("symbol").asText())) return Family.UNSUPPORTED;
            return Family.TWO_STEP_LINEAR;
        } catch (IllegalArgumentException ex) { return Family.UNSUPPORTED; }
    }
    private static boolean empty(JsonNode root,String name) { return root.path(name).isArray() && root.path(name).isEmpty(); }
    private static boolean binary(JsonNode n,String op) { return "BINARY".equals(n.path("kind").asText()) && op.equals(n.path("operator").asText()); }
    private static boolean integer(JsonNode n,int min,int max) {
        if (!"NUMBER".equals(n.path("kind").asText()) || !"INTEGER".equals(n.path("numericType").asText())
            || !n.path("value").isTextual() || !n.path("value").asText().matches("[1-9][0-9]{0,2}")) return false;
        int value=Integer.parseInt(n.path("value").asText()); return value>=min && value<=max;
    }
}
