package com.verifiedai.solving.application.canonical;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.solving.domain.model.canonical.MathExpression;
import com.verifiedai.solving.domain.model.canonical.MathStatement;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Explicit Jackson 2 persistence boundary. Jackson 3 HTTP configuration never handles these trees. */
@Component
public final class CanonicalMathCodec {
    private final ObjectMapper mapper=new ObjectMapper(JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(80).maxStringLength(65536).build()).build())
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public JsonNode parse(String json) {
        if(json==null || json.getBytes(StandardCharsets.UTF_8).length>131072) throw new IllegalArgumentException("Stored artifact too large");
        try { var result=mapper.readTree(json); if(result==null) throw new IllegalArgumentException("Empty artifact"); return result; }
        catch(java.io.IOException e) { throw new IllegalArgumentException("Malformed stored artifact"); }
    }
    public ObjectNode object() { return mapper.createObjectNode(); }
    public String encode(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(java.io.IOException e) { throw new IllegalArgumentException("Cannot encode canonical artifact"); }
    }
    public List<String> strings(JsonNode node) {
        if(!node.isArray()) throw new IllegalArgumentException("Expected array");
        List<String> result=new ArrayList<>();
        for(var value:node) { if(!value.isTextual()) throw new IllegalArgumentException("Expected text"); result.add(value.textValue()); }
        return List.copyOf(result);
    }
    public MathStatement statement(JsonNode node) {
        return switch(node.path("kind").asText()) {
            case "EXPRESSION" -> new MathStatement.Expression(expression(node.path("expression"),1,new int[]{0}));
            case "RELATION" -> {
                int[] count={0};
                yield new MathStatement.Comparison(MathStatement.Relation.valueOf(node.path("relation").asText()),
                    expression(node.path("left"),1,count),expression(node.path("right"),1,count));
            }
            default -> throw new IllegalArgumentException("Unsupported statement");
        };
    }
    private MathExpression expression(JsonNode n,int depth,int[] count) {
        if(depth>32 || ++count[0]>120) throw new IllegalArgumentException("Expression complexity exceeded");
        return switch(n.path("kind").asText()) {
            case "NUMBER" -> new MathExpression.Number(MathExpression.NumericType.valueOf(n.path("numericType").asText()),n.path("value").asText());
            case "VARIABLE" -> new MathExpression.Variable(n.path("symbol").asText());
            case "UNARY" -> new MathExpression.Unary(MathExpression.UnaryOperator.valueOf(n.path("operator").asText()),expression(n.path("operand"),depth+1,count));
            case "BINARY" -> new MathExpression.Binary(MathExpression.BinaryOperator.valueOf(n.path("operator").asText()),
                expression(n.path("left"),depth+1,count),expression(n.path("right"),depth+1,count));
            case "FUNCTION" -> {
                if(!n.path("args").isArray() || n.path("args").size()!=1) throw new IllegalArgumentException("Unsupported function arity");
                yield new MathExpression.Function(MathExpression.FunctionName.valueOf(n.path("function").asText()),List.of(expression(n.path("args").get(0),depth+1,count)));
            }
            default -> throw new IllegalArgumentException("Unsupported expression");
        };
    }
    public ObjectNode json(MathStatement statement) {
        var n=object();
        switch(statement) {
            case MathStatement.Expression e -> { n.put("kind","EXPRESSION"); n.set("expression",json(e.expression())); }
            case MathStatement.Comparison r -> {
                n.put("kind","RELATION").put("relation",r.relation().name()); n.set("left",json(r.left())); n.set("right",json(r.right()));
            }
        }
        return n;
    }
    public ObjectNode json(MathExpression expression) {
        var n=object();
        switch(expression) {
            case MathExpression.Number number -> n.put("kind","NUMBER").put("numericType",number.numericType().name()).put("value",number.value());
            case MathExpression.Variable v -> n.put("kind","VARIABLE").put("symbol",v.symbol());
            case MathExpression.Unary u -> { n.put("kind","UNARY").put("operator",u.operator().name()); n.set("operand",json(u.operand())); }
            case MathExpression.Binary b -> {
                n.put("kind","BINARY").put("operator",b.operator().name()); n.set("left",json(b.left())); n.set("right",json(b.right()));
            }
            case MathExpression.Function f -> { n.put("kind","FUNCTION").put("function",f.function().name()); n.putArray("args").add(json(f.args().getFirst())); }
        }
        return n;
    }
}
