package com.verifiedai.attempt.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.problem.application.attempt.AttemptSourceDocument;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.verification.application.VerificationSourceReader;
import java.util.*;
import org.springframework.stereotype.Component;

/** Adapter to the existing v2 engine; never reparses or repairs captured syntax. */
@Component
public class AttemptEvaluationPreparer {
    private final CanonicalMathCodec codec;
    private final VerificationSourceReader reader;
    public AttemptEvaluationPreparer(CanonicalMathCodec codec, VerificationSourceReader reader) { this.codec=codec; this.reader=reader; }
    public record Prepared(String sequence, String finalAnswer, boolean completeForm, UUID finalStepId, boolean reviewRequired) {}
    public Prepared prepare(SolveSource source, AttemptStructuredWork work, UUID execution) { return prepare(source,work,execution,false); }
    public Prepared prepare(SolveSource source, AttemptStructuredWork work, UUID execution, boolean confirmed) {
        var answer=work.steps().stream().filter(s->s.role()==AttemptWorkInput.Role.FINAL_ANSWER).findFirst().orElse(null);
        UUID finalId=answer==null?null:answer.id();
        boolean review=answer!=null && answer.reviewStatus()!=AttemptStructuredWork.ReviewStatus.CONFIRMED;
        try {
            ObjectNode root=base(source,execution,confirmed);
            var steps=root.putArray("steps");
            for(var step:work.steps()) steps.addObject().set("statement",statement(step));
            String sequence=bounded(root);
            String finalRequest=null; boolean complete=false;
            if(answer!=null && !review && answer.normalizedStatement()!=null) {
                var ast=statement(answer);
                root.putArray("steps").addObject().set("statement",ast);
                finalRequest=bounded(root);
                complete=completeForm(source.taskType(),root,ast);
            }
            return new Prepared(sequence,finalRequest,complete,finalId,review);
        } catch(IllegalArgumentException | VerificationSourceReader.SourceRejected ex) {
            return new Prepared(null,null,false,finalId,review);
        }
    }
    private JsonNode statement(AttemptStructuredWork.Step step) {
        return step.reviewStatus()==AttemptStructuredWork.ReviewStatus.CONFIRMED && step.normalizedStatement()!=null
            ?codec.parse(step.normalizedStatement()):com.fasterxml.jackson.databind.node.NullNode.instance;
    }
    private ObjectNode base(SolveSource source,UUID execution,boolean confirmed) {
        var root=codec.object().put("schemaVersion","attempt-verification-v2").put("executionId",execution.toString())
            .put("correlationId",execution.toString());
        var vars=root.putArray("variables"); var restrictions=root.putArray("restrictions");
        boolean explicit=true;
        if("canonical-problem-v3".equals(codec.parse(source.canonicalJson()).path("schemaVersion").asText())) {
            var doc=AttemptSourceDocument.read(source); AttemptSourceDocument.variables(doc);
            root.set("original",doc.path("original")); root.set("restrictions",doc.path("restrictions"));
            for(var v:doc.path("variables")) {
                String domain=v.path("domain").asText(); explicit &= "REAL".equals(domain);
                vars.addObject().put("symbol",v.path("symbol").asText()).put("domain",explicit || confirmed?"REAL":domain);
            }
        } else {
            var parsed=reader.readForExecution(source);
            if(parsed.reviewRequired() || parsed.statements().size()!=1 || parsed.variables().size()>1
                || !parsed.assumptionIds().isEmpty() || parsed.calculus()!=null) throw new IllegalArgumentException("Unsupported source");
            root.set("original",codec.json(parsed.statements().getFirst()));
            for(var v:parsed.variables().entrySet()) {
                if(!Set.of("REAL","UNKNOWN").contains(v.getValue().name())) throw new IllegalArgumentException("Unsupported source domain");
                explicit &= v.getValue().name().equals("REAL");
                vars.addObject().put("symbol",v.getKey()).put("domain",confirmed?"REAL":v.getValue().name());
            }
            for(var r:parsed.restrictions()) {
                // Natural exclusions must be recomputed on BOTH syntax trees, not imposed on the answer.
                if(parsed.derivedRestrictionIds().contains(r.id())) continue;
                var item=restrictions.addObject().put("id",r.id()).put("relation",r.relation().name());
                item.set("left",codec.json(r.left())); item.set("right",codec.json(r.right()));
            }
        }
        return root.put("domainBasis",explicit?"SOURCE_EXPLICIT":confirmed?"REVIEWED_REAL_TASK":"UNRESOLVED");
    }
    private static String bounded(ObjectNode value) {
        String text=value.toString();
        if(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32768) throw new IllegalArgumentException("Bound exceeded");
        return text;
    }
    /** Syntactic completion gate, NOT a proof. The independent deterministic result is also required. */
    static boolean completeForm(String task,JsonNode request,JsonNode answer) {
        // v2 proves equivalence WITHIN explicit restrictions, not that a submitted set excludes
        // all out-of-scope values. Until that independent containment proof exists, do not
        // promote scoped equivalence to complete final-answer correctness.
        if(!Set.of("SOURCE_EXPLICIT","REVIEWED_REAL_TASK").contains(request.path("domainBasis").asText()) || !request.path("restrictions").isEmpty()) return false;
        if("EVALUATE".equals(task)) return "EXPRESSION".equals(answer.path("kind").asText()) && scalar(answer.path("expression"));
        if(!Set.of("SOLVE_EQUATION","SOLVE_INEQUALITY").contains(task) || request.path("variables").size()!=1
            || !"RELATION".equals(answer.path("kind").asText()) || !"RELATION".equals(request.path("original").path("kind").asText())) return false;
        var left=answer.path("left"); var right=answer.path("right");
        if(!"VARIABLE".equals(left.path("kind").asText())
            || !left.path("symbol").asText().equals(request.path("variables").get(0).path("symbol").asText())) return false;
        String relation=answer.path("relation").asText();
        if("MEMBER_OF".equals(relation)) return set(right);
        return ("EQUALS".equals(relation) || "SOLVE_INEQUALITY".equals(task) && Set.of("LESS_THAN","LESS_THAN_OR_EQUAL","GREATER_THAN","GREATER_THAN_OR_EQUAL").contains(relation)) && scalar(right);
    }
    private static boolean scalar(JsonNode node) {
        String kind=node.path("kind").asText();
        return "NUMBER".equals(kind) || "UNARY".equals(kind) && "NEGATE".equals(node.path("operator").asText()) && scalar(node.path("operand"))
            || "BINARY".equals(kind) && "DIVIDE".equals(node.path("operator").asText()) && scalar(node.path("left")) && scalar(node.path("right"));
    }
    private static boolean endpoint(JsonNode node) { return scalar(node) || "CONSTANT".equals(node.path("kind").asText()) && Set.of("POSITIVE_INFINITY","NEGATIVE_INFINITY").contains(node.path("value").asText()); }
    private static boolean set(JsonNode node) {
        return switch(node.path("kind").asText()) {
            case "EMPTY_SET","REALS" -> true;
            case "FINITE_SET" -> node.path("args").isArray() && !node.path("args").isEmpty() && all(node.path("args"),false);
            case "INTERVAL" -> endpoint(node.path("left")) && endpoint(node.path("right"));
            case "UNION","INTERSECTION" -> node.path("args").isArray() && node.path("args").size()>=2 && all(node.path("args"),true);
            default -> false;
        };
    }
    private static boolean all(JsonNode nodes,boolean sets) { for(var n:nodes) if(!(sets?set(n):scalar(n))) return false; return true; }
}
