package com.verifiedai.problem.application.attempt;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import java.util.*;

/** Additive real-set canonical source. Does not reinterpret historical canonical v1/v2 runs. */
public final class AttemptSourceDocument {
    private AttemptSourceDocument() { }
    public static JsonNode read(SolveSource source) {
        var root=VersionedJsonContract.validate("canonical-problem-v3",source.canonicalJson());
        var lineage=root.path("sourceParse");
        if(!source.problemParseId().toString().equals(lineage.path("problemParseId").asText())
            || source.problemParseRevision()!=lineage.path("problemParseRevision").asInt()
            || !source.problemType().equals(root.path("problemType").asText())
            || !source.taskType().equals(root.path("taskType").asText())
            || root.path("display").path("reviewRequired").asBoolean(true)) throw new IllegalArgumentException("Unreviewed canonical lineage");
        return root;
    }
    public static Set<String> variables(JsonNode root) {
        var symbols=new HashSet<String>();
        for(var v:root.path("variables")) {
            if(!v.path("role").asText().equals("VARIABLE") || !Set.of("UNKNOWN","REAL").contains(v.path("domain").asText())) throw new IllegalArgumentException("Unsupported variable scope");
            symbols.add(v.path("symbol").asText());
        }
        if(symbols.size()>1) throw new IllegalArgumentException("Single variable only");
        return Set.copyOf(symbols);
    }
}
