package com.verifiedai.solving;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.problem.application.solving.SolveSource;
import java.util.UUID;

public final class SolverTestData {
    private static final ObjectMapper M=new ObjectMapper();
    private SolverTestData() { }
    public static SolveSource source() {
        UUID parse=UUID.randomUUID();
        ObjectNode c=M.createObjectNode(); c.put("schemaVersion","canonical-problem-v1");
        c.put("problemType","EQUATION"); c.put("taskType","SOLVE_EQUATION");
        c.putObject("sourceParse").put("problemParseId",parse.toString()).put("problemParseRevision",1);
        c.putObject("display").put("reviewRequired",false);
        c.putArray("variables").addObject().put("symbol","x");
        c.putArray("statements").add(relation("1"));
        c.putArray("sourceAssumptions"); c.putArray("sourceConstraints"); c.putArray("derivedRestrictions");
        return new SolveSource(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),parse,1,1,"EQUATION","SOLVE_EQUATION",c.toString());
    }
    public static ObjectNode relation(String value) {
        var n=M.createObjectNode();n.put("kind","RELATION");n.put("relation","EQUALS");
        n.putObject("left").put("kind","VARIABLE").put("symbol","x");
        n.putObject("right").put("kind","NUMBER").put("numericType","INTEGER").put("value",value);return n;
    }
    public static ObjectNode candidate(SolveSource source) {
        try {
            var c=M.readTree(source.canonicalJson());
            var n=M.createObjectNode(); n.put("schemaVersion","solution-candidate-v1");
            n.put("canonicalProblemId",source.canonicalProblemId().toString());n.put("problemParseId",source.problemParseId().toString());
            n.put("problemParseRevision",source.problemParseRevision());n.put("candidateVersion",1);n.put("generationOrigin","LOCAL_FIXTURE");
            n.put("problemType",source.problemType());n.put("taskType",source.taskType());n.put("method","ISOLATE_VARIABLE");
            var answer=relation("1");n.set("finalAnswer",answer);
            var step=n.putArray("steps").addObject();step.put("id","step-1").put("order",1).put("category","CONCLUDE")
                .put("explanation","Synthetic fixture claim; not model inference.");step.set("claim",answer.deepCopy());step.putArray("dependsOn");
            for(var pair:new String[][]{{"sourceAssumptionIds","sourceAssumptions"},{"sourceConstraintIds","sourceConstraints"},{"derivedRestrictionIds","derivedRestrictions"}}) {
                var refs=n.putArray(pair[0]); c.path(pair[1]).forEach(v->refs.add(v.path("id").asText()));
            }
            n.putArray("ambiguities");n.putArray("unresolvedIssues");return n;
        } catch(Exception e) {throw new IllegalStateException(e);}
    }
}
