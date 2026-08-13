package com.verifiedai.problem.application.canonicalization;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class CanonicalMathParserTest {

    @Test
    void parsesPrecedenceImplicitMultiplicationAndExactNumbers() {
        CanonicalMathParser parser = new CanonicalMathParser(Set.of("x"), CanonicalMathLimits.V1);

        CanonicalMathParser.ParsedExpression result = parser.parse("2x + 3.5*(x - 1)^2");

        assertThat(result.node()).isInstanceOf(BinaryNode.class);
        BinaryNode root = (BinaryNode) result.node();
        assertThat(root.operator()).isEqualTo("ADD");
        assertThat(root.left()).isInstanceOf(BinaryNode.class);
        assertThat(((BinaryNode) root.left()).operator()).isEqualTo("MULTIPLY");
        assertThat(result.complexity().nodeCount()).isGreaterThan(1);
        assertThat(result.derivedRestrictions()).isEmpty();
    }

    @Test
    void derivesDenominatorRestrictionWithoutCancellingExpression() {
        CanonicalMathParser parser = new CanonicalMathParser(Set.of("x"), CanonicalMathLimits.V1);

        CanonicalMathParser.ParsedExpression result = parser.parse("(x^2 - 1)/(x - 1)");

        assertThat(result.derivedRestrictions()).hasSize(1);
        CanonicalRestriction restriction = result.derivedRestrictions().getFirst();
        assertThat(restriction.reason()).isEqualTo("DENOMINATOR_NON_ZERO");
        assertThat(restriction.relation()).isEqualTo("NOT_EQUALS");
        assertThat(restriction.left()).isInstanceOf(BinaryNode.class);
        assertThat(((BinaryNode) restriction.left()).operator()).isEqualTo("SUBTRACT");
    }

    @Test
    void derivesFunctionDomainRestrictions() {
        CanonicalMathParser parser = new CanonicalMathParser(Set.of("x"), CanonicalMathLimits.V1);

        CanonicalMathParser.ParsedExpression result = parser.parse("sqrt(x) + log(x)");

        assertThat(result.derivedRestrictions())
            .extracting(CanonicalRestriction::reason)
            .containsExactly("SQRT_DOMAIN_NON_NEGATIVE", "LOG_DOMAIN_POSITIVE");
    }

    @Test
    void rejectsUndeclaredVariablesUnsafeTokensAndLargeExponents() {
        CanonicalMathParser parser = new CanonicalMathParser(Set.of("x"), CanonicalMathLimits.V1);

        assertThatThrownBy(() -> parser.parse("y + 1"))
            .isInstanceOf(CanonicalizationException.class)
            .extracting(exception -> ((CanonicalizationException) exception).failure())
            .isEqualTo(CanonicalizationFailure.UNSAFE_IDENTIFIER);
        assertThatThrownBy(() -> parser.parse("__import__(x)"))
            .isInstanceOf(CanonicalizationException.class)
            .extracting(exception -> ((CanonicalizationException) exception).failure())
            .isEqualTo(CanonicalizationFailure.UNSUPPORTED_EXPRESSION);
        assertThatThrownBy(() -> parser.parse("x^99"))
            .isInstanceOf(CanonicalizationException.class)
            .extracting(exception -> ((CanonicalizationException) exception).failure())
            .isEqualTo(CanonicalizationFailure.COMPLEXITY_LIMIT);
    }
}
