package com.verifiedai.solving.application;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.domain.model.SolutionCandidate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Strict v1 structural contract, then source-relative semantics. Never verifies mathematics. */
@Component
public final class SolutionCandidateValidator {
    private final ObjectMapper mapper = new ObjectMapper(JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(80).maxStringLength(65536).build()).build())
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> TYPES = Set.of("ARITHMETIC_EXPRESSION", "ALGEBRAIC_EXPRESSION", "EQUATION", "INEQUALITY");
    private static final Set<String> RELATIONS = Set.of("EQUALS", "LESS_THAN", "LESS_THAN_OR_EQUAL", "GREATER_THAN", "GREATER_THAN_OR_EQUAL");

    public SolutionCandidate validate(String output, SolveSource source, String provider) {
        JsonNode root = parse(output);
        fields(root, "schemaVersion", "canonicalProblemId", "problemParseId", "problemParseRevision", "candidateVersion",
            "generationOrigin", "problemType", "taskType", "method", "steps", "finalAnswer", "sourceAssumptionIds",
            "sourceConstraintIds", "derivedRestrictionIds", "ambiguities", "unresolvedIssues");
        require("solution-candidate-v1".equals(text(root, "schemaVersion", 64)), "SCHEMA_INVALID");
        require(root.path("candidateVersion").isIntegralNumber() && root.path("candidateVersion").canConvertToInt() && root.path("candidateVersion").intValue()==1, "SCHEMA_INVALID");
        require(root.path("problemParseRevision").isIntegralNumber()
            && root.path("problemParseRevision").canConvertToInt()
            && root.path("problemParseRevision").intValue()==source.problemParseRevision()
            && source.canonicalProblemId().toString().equals(text(root,"canonicalProblemId",36))
            && source.problemParseId().toString().equals(text(root,"problemParseId",36)), "SOURCE_MISMATCH");
        String expectedOrigin = "LOCAL_FIXTURE".equals(provider) ? "LOCAL_FIXTURE" : "AI_MODEL";
        require(expectedOrigin.equals(text(root,"generationOrigin",32)), "ORIGIN_MISMATCH");
        require(source.problemType().equals(text(root,"problemType",64))
            && source.taskType().equals(text(root,"taskType",64)), "TASK_MISMATCH");
        require(Set.of("ARITHMETIC", "ALGEBRAIC_TRANSFORMATION", "ISOLATE_VARIABLE", "INEQUALITY_TRANSFORMATION")
            .contains(text(root,"method",64)), "SCHEMA_INVALID");
        JsonNode canonical = sourceDocument(source);
        Set<String> variables = new HashSet<>();
        canonical.path("variables").forEach(v -> variables.add(v.path("symbol").asText()));
        exactRefs(root, "sourceAssumptionIds", canonical, "sourceAssumptions", 16);
        exactRefs(root, "sourceConstraintIds", canonical, "sourceConstraints", 16);
        exactRefs(root, "derivedRestrictionIds", canonical, "derivedRestrictions", 32);
        strings(root.path("ambiguities"), 8, 500, false);
        strings(root.path("unresolvedIssues"), 8, 500, false);
        JsonNode steps = root.path("steps");
        array(steps, 1, 24);
        Set<String> seen = new HashSet<>();
        int order=1;
        for (JsonNode step : steps) {
            fields(step,"id","order","category","claim","explanation","dependsOn");
            String id=text(step,"id",32);
            require(!seen.contains(id), "STEP_ORDER_INVALID");
            require(step.path("order").isIntegralNumber() && step.path("order").canConvertToInt() && step.path("order").intValue()==order++, "STEP_ORDER_INVALID");
            require(Set.of("RESTATE","TRANSFORM","CONCLUDE").contains(text(step,"category",32)), "SCHEMA_INVALID");
            text(step,"explanation",1000);
            strings(step.path("dependsOn"),24,32,true);
            for (JsonNode ref:step.path("dependsOn")) require(seen.contains(ref.asText()), "STEP_REFERENCE_INVALID");
            statement(step.path("claim"),variables,canonical);
            seen.add(id);
        }
        JsonNode answer=root.path("finalAnswer");
        statement(answer,variables,canonical);
        boolean relation = Set.of("EQUATION","INEQUALITY").contains(source.problemType());
        require((relation ? "RELATION" : "EXPRESSION").equals(answer.path("kind").asText()), "ANSWER_REPRESENTATION_INVALID");
        if (relation) {
            require("VARIABLE".equals(answer.path("left").path("kind").asText()), "ANSWER_REPRESENTATION_INVALID");
            require("EQUATION".equals(source.problemType()) == "EQUALS".equals(answer.path("relation").asText()), "ANSWER_REPRESENTATION_INVALID");
        }
        try { return mapper.treeToValue(root, SolutionCandidate.class); }
        catch (Exception e) { throw new CandidateRejected("SCHEMA_INVALID"); }
    }

    public JsonNode sourceDocument(SolveSource source) {
        JsonNode c=parse(source.canonicalJson());
        require(TYPES.contains(source.problemType()) && "canonical-problem-v1".equals(c.path("schemaVersion").asText())
            && source.problemType().equals(c.path("problemType").asText())
            && source.taskType().equals(c.path("taskType").asText())
            && source.problemParseId().toString().equals(c.path("sourceParse").path("problemParseId").asText())
            && source.problemParseRevision()==c.path("sourceParse").path("problemParseRevision").asInt()
            && c.path("display").path("reviewRequired").isBoolean()
            && !c.path("display").path("reviewRequired").asBoolean(), "SOURCE_UNSUPPORTED");
        array(c.path("statements"),1,1);
        String expectedTask=switch(source.problemType()) {
            case "ARITHMETIC_EXPRESSION" -> "EVALUATE";
            case "ALGEBRAIC_EXPRESSION" -> "SIMPLIFY";
            case "EQUATION" -> "SOLVE_EQUATION";
            default -> "SOLVE_INEQUALITY";
        };
        require(expectedTask.equals(source.taskType()),"SOURCE_UNSUPPORTED");
        return c;
    }

    public String serialize(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot serialize solve artifact"); }
    }

    public JsonNode parse(String raw) {
        if (raw==null || raw.getBytes(StandardCharsets.UTF_8).length>65536) throw new CandidateRejected("OUTPUT_TOO_LARGE");
        try { JsonNode n=mapper.readTree(raw); require(n!=null,"SCHEMA_INVALID"); return n; }
        catch (CandidateRejected e) { throw e; }
        catch (Exception e) { throw new CandidateRejected("SCHEMA_INVALID"); }
    }

    private void statement(JsonNode s, Set<String> variables, JsonNode source) {
        if ("EXPRESSION".equals(s.path("kind").asText())) {
            fields(s,"kind","expression"); node(s.path("expression"),variables,source,1,0,new int[]{0});
        } else {
            fields(s,"kind","relation","left","right");
            require("RELATION".equals(text(s,"kind",32)) && RELATIONS.contains(text(s,"relation",32)),"SCHEMA_INVALID");
            int[] count={0}; node(s.path("left"),variables,source,1,0,count); node(s.path("right"),variables,source,1,0,count);
        }
    }

    private void node(JsonNode n, Set<String> vars, JsonNode source, int depth, int functions, int[] count) {
        require(depth<=32 && ++count[0]<=120 && functions<=8,"COMPLEXITY_LIMIT");
        switch(text(n,"kind",16)) {
            case "NUMBER" -> {
                fields(n,"kind","numericType","value");
                String value=text(n,"value",80), type=text(n,"numericType",16);
                require(Set.of("INTEGER","DECIMAL","RATIONAL").contains(type)
                    && value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")
                    && value.replaceAll("[^0-9]", "").length()<=64
                    && (!"INTEGER".equals(type) || !value.contains(".")),"UNSAFE_MATH");
            }
            case "VARIABLE" -> { fields(n,"kind","symbol"); require(vars.contains(text(n,"symbol",16)),"UNKNOWN_VARIABLE"); }
            case "UNARY" -> {
                fields(n,"kind","operator","operand"); require("NEGATE".equals(text(n,"operator",16)),"UNSAFE_MATH");
                node(n.path("operand"),vars,source,depth+1,functions,count);
            }
            case "BINARY" -> {
                fields(n,"kind","operator","left","right");
                String op=text(n,"operator",16);
                require(Set.of("ADD","SUBTRACT","MULTIPLY","DIVIDE","POWER").contains(op),"UNSAFE_MATH");
                node(n.path("left"),vars,source,depth+1,functions,count);
                node(n.path("right"),vars,source,depth+1,functions,count);
                if ("POWER".equals(op)) {
                    JsonNode exp=n.path("right");
                    require("NUMBER".equals(exp.path("kind").asText()),"UNSAFE_MATH");
                    BigDecimal value=new BigDecimal(exp.path("value").asText());
                    require(value.stripTrailingZeros().scale()<=0 && value.abs().compareTo(BigDecimal.valueOf(12))<=0,"UNSAFE_MATH");
                    if(value.signum()<0) require(contains(source,n),"NEW_DOMAIN_RESTRICTION");
                }
                if ("DIVIDE".equals(op)) {
                    JsonNode denominator=n.path("right");
                    boolean nonzeroNumber="NUMBER".equals(denominator.path("kind").asText())
                        && new BigDecimal(denominator.path("value").asText()).signum()!=0;
                    require(nonzeroNumber || contains(source,n),"NEW_DOMAIN_RESTRICTION");
                }
            }
            case "FUNCTION" -> {
                fields(n,"kind","function","args"); String function=text(n,"function",16);
                require(Set.of("SQRT","SIN","COS","TAN","LOG","EXP").contains(function),"UNSAFE_MATH");
                array(n.path("args"),1,1);
                node(n.path("args").get(0),vars,source,depth+1,functions+1,count);
                if (Set.of("SQRT","TAN","LOG").contains(function)) require(contains(source,n),"NEW_DOMAIN_RESTRICTION");
            }
            default -> throw new CandidateRejected("UNSAFE_MATH");
        }
    }

    private boolean contains(JsonNode haystack, JsonNode needle) {
        if (haystack.equals(needle)) return true;
        for (JsonNode child:haystack) if(contains(child,needle)) return true;
        return false;
    }
    private void exactRefs(JsonNode root,String field,JsonNode source,String sourceField,int max) {
        JsonNode refs=root.path(field); strings(refs,max,96,true);
        var expected=mapper.createArrayNode(); source.path(sourceField).forEach(n->expected.add(n.path("id").asText()));
        require(refs.equals(expected),"SOURCE_RESTRICTIONS_CHANGED");
    }
    private static void fields(JsonNode n,String... keys) {
        Set<String> actual=new HashSet<>(); n.fieldNames().forEachRemaining(actual::add);
        require(n.isObject() && actual.equals(Set.of(keys)),"SCHEMA_INVALID");
    }
    private static void array(JsonNode n,int min,int max) { require(n.isArray() && n.size()>=min && n.size()<=max,"SCHEMA_INVALID"); }
    private static void strings(JsonNode n,int max,int len,boolean unique) {
        array(n,0,max); Set<String> seen=new HashSet<>();
        for(JsonNode s:n) { checkText(s,len); require(!unique || seen.add(s.asText()),"SCHEMA_INVALID"); }
    }
    private static String text(JsonNode n,String field,int max) { JsonNode v=n.path(field); checkText(v,max); return v.asText(); }
    private static void checkText(JsonNode n,int max) {
        require(n.isTextual() && !n.asText().isBlank() && n.asText().length()<=max,"SCHEMA_INVALID");
        require(!n.asText().matches("(?is).*\\b(verified|confidence|chain.of.thought)\\b.*"),"FALSE_VERIFICATION_CLAIM");
    }
    private static void require(boolean ok,String code) { if(!ok) throw new CandidateRejected(code); }
}
