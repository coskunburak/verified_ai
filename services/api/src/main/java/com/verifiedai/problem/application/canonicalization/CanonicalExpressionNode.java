package com.verifiedai.problem.application.canonicalization;

sealed interface CanonicalExpressionNode
    permits NumberNode, VariableNode, UnaryNode, BinaryNode, FunctionNode {
    String kind();
}

record NumberNode(String kind, String numericType, String value) implements CanonicalExpressionNode {
    NumberNode(String numericType, String value) {
        this("NUMBER", numericType, value);
    }
}

record VariableNode(String kind, String symbol) implements CanonicalExpressionNode {
    VariableNode(String symbol) {
        this("VARIABLE", symbol);
    }
}

record UnaryNode(String kind, String operator, CanonicalExpressionNode operand) implements CanonicalExpressionNode {
    UnaryNode(String operator, CanonicalExpressionNode operand) {
        this("UNARY", operator, operand);
    }
}

record BinaryNode(
    String kind,
    String operator,
    CanonicalExpressionNode left,
    CanonicalExpressionNode right
) implements CanonicalExpressionNode {
    BinaryNode(String operator, CanonicalExpressionNode left, CanonicalExpressionNode right) {
        this("BINARY", operator, left, right);
    }
}

record FunctionNode(
    String kind,
    String function,
    java.util.List<CanonicalExpressionNode> args
) implements CanonicalExpressionNode {
    FunctionNode(String function, java.util.List<CanonicalExpressionNode> args) {
        this("FUNCTION", function, java.util.List.copyOf(args));
    }
}
