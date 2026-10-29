package com.verifiedai.verification.infrastructure.persistence;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.solving.domain.model.canonical.*;
import java.io.IOException;

/** Explicit bounded AST representation for stored calculus restriction evidence and privacy export. */
public final class EvidenceMathJson {
    private EvidenceMathJson() { }
    private static void strict(JsonNode n,int depth,int[] count) throws IOException {
        if (depth>32 || ++count[0]>120 || !n.isObject()) throw new IOException("Stored AST limit");
        var fields=new java.util.HashSet<String>(); n.fieldNames().forEachRemaining(fields::add);
        var expected=switch(n.path("kind").asText()) {
            case "NUMBER" -> java.util.Set.of("kind","numericType","value");
            case "VARIABLE" -> java.util.Set.of("kind","symbol");
            case "UNARY" -> java.util.Set.of("kind","operator","operand");
            case "BINARY" -> java.util.Set.of("kind","operator","left","right");
            case "FUNCTION" -> java.util.Set.of("kind","function","args");
            default -> throw new IOException("Stored AST kind");
        };
        if (!fields.equals(expected)) throw new IOException("Stored AST fields");
        for (String field : expected) if (!java.util.Set.of("left","right","operand","args").contains(field)
            && !n.path(field).isTextual()) throw new IOException("Stored AST scalar type");
        switch(n.path("kind").asText()) {
            case "UNARY" -> strict(n.path("operand"),depth+1,count);
            case "BINARY" -> { strict(n.path("left"),depth+1,count); strict(n.path("right"),depth+1,count); }
            case "FUNCTION" -> {
                if (!n.path("args").isArray() || n.path("args").size()!=1) throw new IOException("Stored function arity");
                strict(n.path("args").get(0),depth+1,count);
            }
            default -> { }
        }
    }
    public abstract static class EvidenceShape {
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        public abstract com.verifiedai.verification.domain.model.CalculusEvidence calculus();
    }
    public static SimpleModule module() {
        var codec=new CanonicalMathCodec();
        var module=new SimpleModule();
        module.setMixInAnnotation(com.verifiedai.verification.domain.model.VerificationEvidence.class,EvidenceShape.class);
        module.addSerializer(MathExpression.class,new JsonSerializer<MathExpression>() {
            public void serialize(MathExpression value,JsonGenerator g,SerializerProvider provider) throws IOException {
                g.writeTree(codec.json(value));
            }
        });
        module.addDeserializer(MathExpression.class,new JsonDeserializer<MathExpression>() {
            public MathExpression deserialize(JsonParser p,DeserializationContext context) throws IOException {
                // Read only this embedded AST; the enclosing mapper checks document trailing tokens.
                JsonNode tree=context.readTree(p);
                strict(tree,1,new int[]{0});
                var wrapper=codec.object().put("kind","EXPRESSION"); wrapper.set("expression",tree);
                return ((MathStatement.Expression)codec.statement(wrapper)).expression();
            }
        });
        return module;
    }
}
