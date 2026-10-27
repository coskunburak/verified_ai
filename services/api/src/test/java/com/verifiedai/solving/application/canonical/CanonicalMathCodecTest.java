package com.verifiedai.solving.application.canonical;

import com.verifiedai.solving.domain.model.canonical.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CanonicalMathCodecTest {
    CanonicalMathCodec codec=new CanonicalMathCodec();
    @Test void allV1OperatorsFunctionsAndRelationsRoundTripWithoutNormalization() {
        var x=new MathExpression.Variable("x");var number=new MathExpression.Number(MathExpression.NumericType.DECIMAL,"-0.0100");
        for(var op:MathExpression.BinaryOperator.values()) {
            var s=new MathStatement.Expression(new MathExpression.Binary(op,x,number));
            assertThat(codec.statement(codec.parse(codec.json(s).toString()))).isEqualTo(s);
        }
        for(var f:MathExpression.FunctionName.values()) {
            var s=new MathStatement.Expression(new MathExpression.Function(f,List.of(x)));
            assertThat(codec.statement(codec.json(s))).isEqualTo(s);
        }
        for(var r:MathStatement.Relation.values()) {
            var s=new MathStatement.Comparison(r,new MathExpression.Unary(MathExpression.UnaryOperator.NEGATE,x),number);
            assertThat(codec.statement(codec.json(s))).isEqualTo(s);
        }
    }
    @Test void duplicateKeysTrailingTokensAndOversizedPayloadsFailClosed() {
        for(String json:List.of("{\"a\":1,\"a\":2}","{} {}","x".repeat(131073)))
            assertThatThrownBy(()->codec.parse(json)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->codec.statement(codec.parse("{\"kind\":\"EXECUTE\"}"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void explicitJackson2BoundaryDoesNotDependOnJackson3GlobalMapper() throws Exception {
        var statement=new MathStatement.Expression(new MathExpression.Number(MathExpression.NumericType.DECIMAL,"1.00"));
        var transport=tools.jackson.databind.json.JsonMapper.builder().build();
        String bridged=transport.readTree(codec.json(statement).toString()).toString();
        assertThat(codec.statement(codec.parse(bridged))).isEqualTo(statement);
        assertThat(codec.json(statement)).isInstanceOf(com.fasterxml.jackson.databind.JsonNode.class);
    }
}
