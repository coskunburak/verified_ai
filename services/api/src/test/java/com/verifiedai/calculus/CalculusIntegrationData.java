package com.verifiedai.calculus;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.problem.application.solving.SolveSource;
import java.nio.file.*;
import java.util.*;

/** Manually specified expected mathematics; never executes a CAS to generate expectations. */
public final class CalculusIntegrationData {
    public static final ObjectMapper JSON=new ObjectMapper();
    public static List<JsonNode> cases() {
        try {
            var root=JSON.readTree(Files.readString(Path.of("../../packages/test-fixtures/verification/calculus-integration-v1/cases.json")));
            var list=new ArrayList<JsonNode>(); root.forEach(list::add); return list;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static ObjectNode candidate(SolveSource source, JsonNode expression) {
        var n=JSON.createObjectNode();
        n.put("schemaVersion","solution-candidate-v2").put("canonicalProblemId",source.canonicalProblemId().toString())
            .put("problemParseId",source.problemParseId().toString()).put("problemParseRevision",source.problemParseRevision())
            .put("canonicalRevision",source.canonicalRevision()).put("candidateVersion",2).put("generationOrigin","LOCAL_FIXTURE")
            .put("problemType",source.problemType()).put("taskType",source.taskType())
            .put("method",com.verifiedai.problem.application.solving.CalculusSourceDocument.method(source.problemType(),source.taskType()));
        var claim=JSON.createObjectNode().put("kind","EXPRESSION"); claim.set("expression",expression);
        n.set("finalAnswer",claim);
        var step=n.putArray("steps").addObject().put("id","step-1").put("order",1).put("category","CONCLUDE")
            .put("explanation","Offline candidate proposal."); step.set("claim",claim); step.putArray("dependsOn");
        n.putArray("sourceAssumptionIds"); n.putArray("derivedRestrictionIds");
        var refs=n.putArray("sourceConstraintIds");
        try { JSON.readTree(source.canonicalJson()).path("calculus").path("restrictions").forEach(r->refs.add(r.path("id").asText())); }
        catch (Exception e) { throw new IllegalArgumentException(e); }
        n.putArray("ambiguities"); n.putArray("unresolvedIssues"); return n;
    }
    public static SolveSource source(JsonNode parse) {
        UUID user=UUID.randomUUID(),session=UUID.randomUUID(),canonical=UUID.randomUUID(),id=UUID.randomUUID();
        var c=JSON.createObjectNode().put("schemaVersion","canonical-problem-v2").put("normalizerVersion","calculus-source-normalizer-v1");
        c.putObject("sourceParse").put("problemParseId",id.toString()).put("problemParseRevision",2)
            .put("problemParseSchemaVersion","problem-parse-v2").put("source","USER");
        c.set("acceptedParse",parse); c.set("calculus",parse.path("calculus"));
        c.set("problemType",parse.path("problemType")); c.set("taskType",parse.path("taskType"));
        var d=c.putObject("display"); d.put("normalizedText","fixture").putNull("displayLatex");
        d.putArray("variables").add("x"); d.put("sourceConstraintCount",parse.path("calculus").path("restrictions").size())
            .put("derivedRestrictionCount",0).put("reviewRequired",false);
        return new SolveSource(canonical,user,session,id,2,1,parse.path("problemType").asText(),parse.path("taskType").asText(),c.toString());
    }
}
