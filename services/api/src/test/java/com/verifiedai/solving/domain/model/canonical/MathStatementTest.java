package com.verifiedai.solving.domain.model.canonical;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MathStatementTest {
    MathExpression one=new MathExpression.Number(MathExpression.NumericType.INTEGER,"1");
    @Test void numbersRetainExactLexemesAndNumericTypes() {
        var a=new MathExpression.Number(MathExpression.NumericType.DECIMAL,"-0.0100");
        assertThat(a.value()).isEqualTo("-0.0100");
        assertThat(new MathExpression.Number(MathExpression.NumericType.RATIONAL,"1.00").value()).isEqualTo("1.00");
        for(String value:List.of("NaN","1e3","01","1/2","1".repeat(65)))
            assertThatThrownBy(()->new MathExpression.Number(MathExpression.NumericType.DECIMAL,value)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MathExpression.Number(MathExpression.NumericType.INTEGER,"1.0")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void aritySymbolsAndExpressionBoundsAreEnforced() {
        assertThatThrownBy(()->new MathExpression.Function(MathExpression.FunctionName.LOG,List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new MathExpression.Variable("x;eval()")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->{ MathExpression n=one;for(int i=0;i<33;i++) n=new MathExpression.Unary(MathExpression.UnaryOperator.NEGATE,n); }).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->{ MathExpression n=one;for(int i=0;i<9;i++) n=new MathExpression.Function(MathExpression.FunctionName.SIN,List.of(n)); }).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->{ MathExpression n=one;for(int i=0;i<7;i++) n=new MathExpression.Binary(MathExpression.BinaryOperator.ADD,n,n); }).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void operandOrderAndUnreducedSyntaxRemainDifferent() {
        var x=new MathExpression.Variable("x");
        var sum=new MathExpression.Binary(MathExpression.BinaryOperator.ADD,x,x);
        var product=new MathExpression.Binary(MathExpression.BinaryOperator.MULTIPLY,new MathExpression.Number(MathExpression.NumericType.INTEGER,"2"),x);
        assertThat(sum).isNotEqualTo(product);
        var a=new MathStatement.Comparison(MathStatement.Relation.EQUALS,x,one);
        var b=new MathStatement.Comparison(MathStatement.Relation.EQUALS,one,x);
        assertThat(a).isNotEqualTo(b);
        assertThat(MathExpression.FunctionName.LOG.requiresSourceDomainRestriction()).isTrue();
    }
}
