package com.verifiedai.problem.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class CanonicalMathParser {
    private static final Set<String> FUNCTIONS = Set.of("SQRT", "SIN", "COS", "TAN", "LOG", "EXP");
    private static final NumberNode ZERO = new NumberNode("INTEGER", "0");

    private final Set<String> declaredVariables;
    private final CanonicalMathLimits limits;
    private final List<CanonicalRestriction> derivedRestrictions = new ArrayList<>();
    private List<Token> tokens;
    private int position;
    private int restrictionIndex;

    CanonicalMathParser(Set<String> declaredVariables, CanonicalMathLimits limits) {
        this.declaredVariables = Set.copyOf(declaredVariables);
        this.limits = limits;
    }

    ParsedExpression parse(String expression) {
        if (expression == null || expression.isBlank()) {
            throw failure(CanonicalizationFailure.UNSUPPORTED_EXPRESSION, "Expression is blank");
        }
        if (expression.length() > limits.maxExpressionLength()) {
            throw failure(CanonicalizationFailure.COMPLEXITY_LIMIT, "Expression length exceeds canonical limit");
        }
        tokens = tokenize(expression);
        position = 0;
        derivedRestrictions.clear();
        restrictionIndex = 0;
        CanonicalExpressionNode node = parseExpression();
        expect(TokenType.EOF);
        Complexity complexity = Complexity.measure(node);
        if (complexity.nodeCount() > limits.maxAstNodes() || complexity.depth() > limits.maxAstDepth()) {
            throw failure(CanonicalizationFailure.COMPLEXITY_LIMIT, "AST complexity exceeds canonical limits");
        }
        if (complexity.functionNestingDepth() > limits.maxFunctionNestingDepth()) {
            throw failure(CanonicalizationFailure.COMPLEXITY_LIMIT, "Function nesting exceeds canonical limit");
        }
        return new ParsedExpression(node, List.copyOf(derivedRestrictions), complexity);
    }

    private CanonicalExpressionNode parseExpression() {
        return parseAdditive();
    }

    private CanonicalExpressionNode parseAdditive() {
        CanonicalExpressionNode node = parseMultiplicative();
        while (match(TokenType.PLUS) || match(TokenType.MINUS)) {
            Token operator = previous();
            CanonicalExpressionNode right = parseMultiplicative();
            node = new BinaryNode(operator.type() == TokenType.PLUS ? "ADD" : "SUBTRACT", node, right);
        }
        return node;
    }

    private CanonicalExpressionNode parseMultiplicative() {
        CanonicalExpressionNode node = parsePower();
        while (true) {
            if (match(TokenType.STAR)) {
                node = new BinaryNode("MULTIPLY", node, parsePower());
            } else if (match(TokenType.SLASH)) {
                CanonicalExpressionNode denominator = parsePower();
                addDenominatorRestrictions(denominator);
                node = new BinaryNode("DIVIDE", node, denominator);
            } else if (startsImplicitProduct(peek().type())) {
                node = new BinaryNode("MULTIPLY", node, parsePower());
            } else {
                return node;
            }
        }
    }

    private CanonicalExpressionNode parsePower() {
        CanonicalExpressionNode base = parseUnary();
        if (match(TokenType.CARET)) {
            CanonicalExpressionNode exponent = parsePower();
            validateExponent(exponent);
            return new BinaryNode("POWER", base, exponent);
        }
        return base;
    }

    private CanonicalExpressionNode parseUnary() {
        if (match(TokenType.PLUS)) {
            return parseUnary();
        }
        if (match(TokenType.MINUS)) {
            return new UnaryNode("NEGATE", parseUnary());
        }
        return parsePrimary();
    }

    private CanonicalExpressionNode parsePrimary() {
        if (match(TokenType.NUMBER)) {
            return number(previous().lexeme());
        }
        if (match(TokenType.IDENTIFIER)) {
            String identifier = previous().lexeme();
            if (match(TokenType.LEFT_PAREN)) {
                String function = identifier.toUpperCase(Locale.ROOT);
                if (!FUNCTIONS.contains(function)) {
                    throw failure(CanonicalizationFailure.UNSUPPORTED_EXPRESSION, "Function is not allowlisted");
                }
                CanonicalExpressionNode argument = parseExpression();
                expect(TokenType.RIGHT_PAREN);
                FunctionNode node = new FunctionNode(function, List.of(argument));
                addFunctionDomainRestriction(node, argument);
                return node;
            }
            if (!declaredVariables.contains(identifier)) {
                throw failure(CanonicalizationFailure.UNSAFE_IDENTIFIER, "Identifier is not declared by ProblemParse variables");
            }
            return new VariableNode(identifier);
        }
        if (match(TokenType.LEFT_PAREN)) {
            CanonicalExpressionNode node = parseExpression();
            expect(TokenType.RIGHT_PAREN);
            return node;
        }
        throw failure(CanonicalizationFailure.UNSUPPORTED_EXPRESSION, "Unexpected token in expression");
    }

    private NumberNode number(String value) {
        int digitCount = 0;
        for (int i = 0; i < value.length(); i += 1) {
            if (Character.isDigit(value.charAt(i))) {
                digitCount += 1;
            }
        }
        if (digitCount > limits.maxNumericDigits()) {
            throw failure(CanonicalizationFailure.COMPLEXITY_LIMIT, "Numeric literal exceeds canonical digit limit");
        }
        String numericType = value.contains(".") ? "DECIMAL" : "INTEGER";
        return new NumberNode(numericType, value);
    }

    private void validateExponent(CanonicalExpressionNode exponent) {
        CanonicalExpressionNode normalized = exponent;
        boolean negative = false;
        if (exponent instanceof UnaryNode unary && "NEGATE".equals(unary.operator())) {
            negative = true;
            normalized = unary.operand();
        }
        if (!(normalized instanceof NumberNode number)) {
            throw failure(CanonicalizationFailure.COMPLEXITY_LIMIT, "Symbolic exponents are outside canonical v1");
        }
        BigDecimal exponentValue = new BigDecimal(number.value());
        if (negative) {
            exponentValue = exponentValue.negate();
        }
        if (exponentValue.abs().compareTo(BigDecimal.valueOf(limits.maxExponentMagnitude())) > 0) {
            throw failure(CanonicalizationFailure.COMPLEXITY_LIMIT, "Exponent magnitude exceeds canonical limit");
        }
    }

    private void addDenominatorRestrictions(CanonicalExpressionNode denominator) {
        if (isNonZeroNumeric(denominator)) {
            return;
        }
        if (isZeroNumeric(denominator)) {
            throw failure(CanonicalizationFailure.INVALID_CONSTRAINT, "Static zero denominator is invalid");
        }
        if (denominator instanceof BinaryNode binary && "MULTIPLY".equals(binary.operator())) {
            addDenominatorRestrictions(binary.left());
            addDenominatorRestrictions(binary.right());
            return;
        }
        derivedRestrictions.add(new CanonicalRestriction(
            "derived-denominator-" + (++restrictionIndex),
            "NOT_EQUALS",
            denominator,
            ZERO,
            "DENOMINATOR_NON_ZERO",
            "DERIVED",
            List.of()
        ));
    }

    private void addFunctionDomainRestriction(FunctionNode function, CanonicalExpressionNode argument) {
        String relation = null;
        String reason = null;
        CanonicalExpressionNode left = argument;
        if ("SQRT".equals(function.function())) {
            relation = "GREATER_THAN_OR_EQUAL";
            reason = "SQRT_DOMAIN_NON_NEGATIVE";
        } else if ("LOG".equals(function.function())) {
            relation = "GREATER_THAN";
            reason = "LOG_DOMAIN_POSITIVE";
        } else if ("TAN".equals(function.function())) {
            relation = "NOT_EQUALS";
            reason = "TAN_DOMAIN_COS_NON_ZERO";
            left = new FunctionNode("COS", List.of(argument));
        }
        if (relation == null) {
            return;
        }
        derivedRestrictions.add(new CanonicalRestriction(
            "derived-function-domain-" + (++restrictionIndex),
            relation,
            left,
            ZERO,
            reason,
            "DERIVED",
            List.of()
        ));
    }

    private static boolean isZeroNumeric(CanonicalExpressionNode node) {
        if (!(node instanceof NumberNode number)) {
            return false;
        }
        return new BigDecimal(number.value()).compareTo(BigDecimal.ZERO) == 0;
    }

    private static boolean isNonZeroNumeric(CanonicalExpressionNode node) {
        if (!(node instanceof NumberNode number)) {
            return false;
        }
        return new BigDecimal(number.value()).compareTo(BigDecimal.ZERO) != 0;
    }

    private boolean startsImplicitProduct(TokenType type) {
        return type == TokenType.NUMBER || type == TokenType.IDENTIFIER || type == TokenType.LEFT_PAREN;
    }

    private boolean match(TokenType type) {
        if (peek().type() != type) {
            return false;
        }
        position += 1;
        return true;
    }

    private void expect(TokenType type) {
        if (!match(type)) {
            throw failure(CanonicalizationFailure.UNSUPPORTED_EXPRESSION, "Expression grammar is unsupported");
        }
    }

    private Token peek() {
        return tokens.get(position);
    }

    private Token previous() {
        return tokens.get(position - 1);
    }

    private List<Token> tokenize(String expression) {
        List<Token> result = new ArrayList<>();
        int index = 0;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (Character.isWhitespace(current)) {
                index += 1;
            } else if (current == '+') {
                result.add(new Token(TokenType.PLUS, "+"));
                index += 1;
            } else if (current == '-') {
                result.add(new Token(TokenType.MINUS, "-"));
                index += 1;
            } else if (current == '*') {
                result.add(new Token(TokenType.STAR, "*"));
                index += 1;
            } else if (current == '/') {
                result.add(new Token(TokenType.SLASH, "/"));
                index += 1;
            } else if (current == '^') {
                result.add(new Token(TokenType.CARET, "^"));
                index += 1;
            } else if (current == '(') {
                result.add(new Token(TokenType.LEFT_PAREN, "("));
                index += 1;
            } else if (current == ')') {
                result.add(new Token(TokenType.RIGHT_PAREN, ")"));
                index += 1;
            } else if (Character.isDigit(current) || current == '.') {
                index = tokenizeNumber(expression, index, result);
            } else if (Character.isLetter(current)) {
                index = tokenizeIdentifier(expression, index, result);
            } else {
                throw failure(CanonicalizationFailure.UNSUPPORTED_EXPRESSION, "Expression contains unsupported character");
            }
        }
        result.add(new Token(TokenType.EOF, ""));
        return result;
    }

    private int tokenizeNumber(String expression, int start, List<Token> result) {
        int index = start;
        boolean dotSeen = false;
        boolean digitSeen = false;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (Character.isDigit(current)) {
                digitSeen = true;
                index += 1;
            } else if (current == '.' && !dotSeen) {
                dotSeen = true;
                index += 1;
            } else {
                break;
            }
        }
        String value = expression.substring(start, index);
        if (!digitSeen || ".".equals(value) || value.endsWith(".")) {
            throw failure(CanonicalizationFailure.UNSUPPORTED_EXPRESSION, "Numeric literal is invalid");
        }
        result.add(new Token(TokenType.NUMBER, value));
        return index;
    }

    private int tokenizeIdentifier(String expression, int start, List<Token> result) {
        int index = start;
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (Character.isLetterOrDigit(current) || current == '_') {
                index += 1;
            } else {
                break;
            }
        }
        String identifier = expression.substring(start, index);
        if (!identifier.matches("[A-Za-z][A-Za-z0-9_]{0,15}")) {
            throw failure(CanonicalizationFailure.UNSAFE_IDENTIFIER, "Identifier is outside canonical policy");
        }
        result.add(new Token(TokenType.IDENTIFIER, identifier));
        return index;
    }

    private CanonicalizationException failure(CanonicalizationFailure failure, String message) {
        return new CanonicalizationException(failure, message);
    }

    record ParsedExpression(
        CanonicalExpressionNode node,
        List<CanonicalRestriction> derivedRestrictions,
        Complexity complexity
    ) {
    }

    record Complexity(int nodeCount, int depth, int functionNestingDepth) {
        static Complexity measure(CanonicalExpressionNode node) {
            if (node instanceof NumberNode || node instanceof VariableNode) {
                return new Complexity(1, 1, 0);
            }
            if (node instanceof UnaryNode unary) {
                Complexity operand = measure(unary.operand());
                return new Complexity(1 + operand.nodeCount(), 1 + operand.depth(), operand.functionNestingDepth());
            }
            if (node instanceof BinaryNode binary) {
                Complexity left = measure(binary.left());
                Complexity right = measure(binary.right());
                return new Complexity(
                    1 + left.nodeCount() + right.nodeCount(),
                    1 + Math.max(left.depth(), right.depth()),
                    Math.max(left.functionNestingDepth(), right.functionNestingDepth())
                );
            }
            FunctionNode function = (FunctionNode) node;
            Complexity argument = measure(function.args().getFirst());
            return new Complexity(
                1 + argument.nodeCount(),
                1 + argument.depth(),
                1 + argument.functionNestingDepth()
            );
        }
    }

    private enum TokenType {
        NUMBER,
        IDENTIFIER,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        CARET,
        LEFT_PAREN,
        RIGHT_PAREN,
        EOF
    }

    private record Token(TokenType type, String lexeme) {
    }
}
