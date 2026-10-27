package com.verifiedai.solving.domain.model.canonical;

import java.util.List;
import java.util.Objects;

/** Exact v1 syntax. Construction establishes structure, never mathematical truth. */
public sealed interface MathExpression {
    enum NumericType { INTEGER, DECIMAL, RATIONAL }
    enum UnaryOperator { NEGATE }
    enum BinaryOperator { ADD, SUBTRACT, MULTIPLY, DIVIDE, POWER }
    enum FunctionName {
        SQRT, SIN, COS, TAN, LOG, EXP;
        public int arity() { return 1; }
        public boolean requiresSourceDomainRestriction() { return this==SQRT || this==TAN || this==LOG; }
    }
    record Number(NumericType numericType, String value) implements MathExpression {
        public Number {
            Objects.requireNonNull(numericType);
            if(value==null || value.length()>80 || !value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")
                || value.replaceAll("[^0-9]", "").length()>64
                || numericType==NumericType.INTEGER && value.contains(".")) throw new IllegalArgumentException("Invalid exact number");
        }
    }
    record Variable(String symbol) implements MathExpression {
        public Variable {
            if(symbol==null || !symbol.matches("[A-Za-z][A-Za-z0-9_]{0,15}")) throw new IllegalArgumentException("Invalid symbol");
        }
    }
    record Unary(UnaryOperator operator, MathExpression operand) implements MathExpression {
        public Unary { Objects.requireNonNull(operator); Objects.requireNonNull(operand); bounded(operand,2,0,new int[]{1}); }
    }
    record Binary(BinaryOperator operator, MathExpression left, MathExpression right) implements MathExpression {
        public Binary {
            Objects.requireNonNull(operator); Objects.requireNonNull(left); Objects.requireNonNull(right);
            int[] count={1}; bounded(left,2,0,count); bounded(right,2,0,count);
        }
    }
    record Function(FunctionName function, List<MathExpression> args) implements MathExpression {
        public Function {
            Objects.requireNonNull(function); args=List.copyOf(args);
            if(args.size()!=function.arity()) throw new IllegalArgumentException("Invalid function arity");
            bounded(args.getFirst(),2,1,new int[]{1});
        }
    }
    static void bounded(MathExpression value,int depth,int functions,int[] count) {
        Objects.requireNonNull(value);
        if(depth>32 || ++count[0]>120 || functions>8) throw new IllegalArgumentException("Expression complexity exceeded");
        switch(value) {
            case Unary u -> bounded(u.operand(),depth+1,functions,count);
            case Binary b -> { bounded(b.left(),depth+1,functions,count); bounded(b.right(),depth+1,functions,count); }
            case Function f -> bounded(f.args().getFirst(),depth+1,functions+1,count);
            default -> { }
        }
    }
}
