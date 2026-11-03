package com.verifiedai.problem.application.canonicalization;

import com.verifiedai.problem.application.attempt.ManualStepNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public final class CanonicalManualStepNormalizer implements ManualStepNormalizer {
    private final ObjectMapper mapper = new ObjectMapper();
    public String normalizeV2(String text, Set<String> variables) {
        try { return new AttemptMathParserV2(variables).parse(text); } catch(IllegalArgumentException ex) { return null; }
    }
    public String normalize(String text, Set<String> variables) {
        if (text == null || text.isBlank() || text.length() > 256 || variables.size() > 1) return null;
        // The legacy parser binds unary minus before power. Never reinterpret ambiguous manual syntax.
        // Explicit multiplication is also required: x(y) is a function in the legacy grammar.
        if (text.contains("^") && text.contains("-")) return null;
        int depth=0, tokens=0;
        for(char c:text.toCharArray()) {
            if(c=='(' && ++depth>16 || c==')' && --depth<0 || !Character.isWhitespace(c) && ++tokens>200) return null;
        }
        if(depth!=0 || !text.matches("[A-Za-z0-9_+*/^(). =-]+")) return null;
        try {
            var parts=text.split("=",-1);
            if(parts.length>2) return null;
            var parser=new CanonicalMathParser(variables,CanonicalMathLimits.V1);
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("kind",parts.length==1 ? "EXPRESSION" : "RELATION");
            result.put("relation",parts.length==1 ? null : "EQUALS");
            result.put("expression",parts.length==1 ? node(parser.parse(parts[0]).node()) : null);
            result.put("left",parts.length==2 ? node(parser.parse(parts[0]).node()) : null);
            result.put("right",parts.length==2 ? node(parser.parse(parts[1]).node()) : null);
            return mapper.writeValueAsString(result);
        } catch(CanonicalizationException | IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            return null;
        }
    }
    private Map<String,Object> node(CanonicalExpressionNode n) {
        if(n instanceof NumberNode x) {
            if(!x.value().matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) throw new IllegalArgumentException("Unsupported numeric notation");
            return Map.of("kind","NUMBER","numericType",x.numericType(),"value",x.value());
        }
        if(n instanceof VariableNode x) return Map.of("kind","VARIABLE","symbol",x.symbol());
        if(n instanceof UnaryNode x) return Map.of("kind","UNARY","operator",x.operator(),"operand",node(x.operand()));
        if(n instanceof BinaryNode x) return Map.of("kind","BINARY","operator",x.operator(),"left",node(x.left()),"right",node(x.right()));
        var x=(FunctionNode)n;
        return Map.of("kind","FUNCTION","function",x.function(),"args",x.args().stream().map(this::node).toList());
    }
}
