package com.verifiedai.solving.domain.model.canonical;

import java.util.Objects;

public sealed interface MathStatement {
    enum Relation { EQUALS, LESS_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN, GREATER_THAN_OR_EQUAL }
    record Expression(MathExpression expression) implements MathStatement {
        public Expression { MathExpression.bounded(expression,1,0,new int[]{0}); }
    }
    record Comparison(Relation relation,MathExpression left,MathExpression right) implements MathStatement {
        public Comparison {
            Objects.requireNonNull(relation);
            int[] count={0}; MathExpression.bounded(left,1,0,count); MathExpression.bounded(right,1,0,count);
        }
    }
}
