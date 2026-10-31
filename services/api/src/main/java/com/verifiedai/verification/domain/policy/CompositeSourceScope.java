package com.verifiedai.verification.domain.policy;

import com.verifiedai.solving.domain.model.canonical.MathExpression;
import com.verifiedai.verification.domain.model.PlanningSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;

/** Exact bounded feasibility of constant/affine restrictions, before interpreting contradictions. */
final class CompositeSourceScope {
    enum State { NONEMPTY, EMPTY, UNKNOWN }
    private CompositeSourceScope() { }
    static State inspect(PlanningSource source) {
        if (source.variables().size() > 1 || source.variables().containsValue(PlanningSource.Domain.UNKNOWN)) return State.UNKNOWN;
        Fraction lower = null, upper = null;
        boolean lowerClosed = true, upperClosed = true;
        var excluded = new HashSet<Fraction>();
        int[] budget = {0};
        try {
            for (var restriction : source.restrictions()) {
                var difference = affine(restriction.left(), source, budget).subtract(affine(restriction.right(), source, budget));
                var op = restriction.relation();
                if (difference.a.sign() == 0) {
                    int sign = difference.b.sign();
                    boolean valid = switch (op) {
                        case EQUALS -> sign == 0;
                        case NOT_EQUALS -> sign != 0;
                        case LESS_THAN -> sign < 0;
                        case LESS_THAN_OR_EQUAL -> sign <= 0;
                        case GREATER_THAN -> sign > 0;
                        case GREATER_THAN_OR_EQUAL -> sign >= 0;
                    };
                    if (!valid) return State.EMPTY;
                    continue;
                }
                var root = difference.b.negate().divide(difference.a);
                if (difference.a.sign() < 0) op = switch (op) {
                    case LESS_THAN -> PlanningSource.Relation.GREATER_THAN;
                    case LESS_THAN_OR_EQUAL -> PlanningSource.Relation.GREATER_THAN_OR_EQUAL;
                    case GREATER_THAN -> PlanningSource.Relation.LESS_THAN;
                    case GREATER_THAN_OR_EQUAL -> PlanningSource.Relation.LESS_THAN_OR_EQUAL;
                    default -> op;
                };
                if (op == PlanningSource.Relation.NOT_EQUALS) { excluded.add(root); continue; }
                if (op == PlanningSource.Relation.EQUALS || op == PlanningSource.Relation.GREATER_THAN || op == PlanningSource.Relation.GREATER_THAN_OR_EQUAL) {
                    boolean closed = op != PlanningSource.Relation.GREATER_THAN;
                    if (lower == null || root.compareTo(lower) > 0) { lower = root; lowerClosed = closed; }
                    else if (root.equals(lower)) lowerClosed &= closed;
                }
                if (op == PlanningSource.Relation.EQUALS || op == PlanningSource.Relation.LESS_THAN || op == PlanningSource.Relation.LESS_THAN_OR_EQUAL) {
                    boolean closed = op != PlanningSource.Relation.LESS_THAN;
                    if (upper == null || root.compareTo(upper) < 0) { upper = root; upperClosed = closed; }
                    else if (root.equals(upper)) upperClosed &= closed;
                }
            }
            if (lower != null && upper != null) {
                if (lower.compareTo(upper) > 0 || lower.equals(upper) && (!lowerClosed || !upperClosed || excluded.contains(lower))) return State.EMPTY;
                if (source.variables().containsValue(PlanningSource.Domain.INTEGER)) {
                    var first = lower.ceil(); var last = upper.floor();
                    if (!lowerClosed && lower.equals(new Fraction(first, BigInteger.ONE))) first = first.add(BigInteger.ONE);
                    if (!upperClosed && upper.equals(new Fraction(last, BigInteger.ONE))) last = last.subtract(BigInteger.ONE);
                    if (last.compareTo(first) < 0) return State.EMPTY;
                    // At most 48 excluded points; never enumerate a potentially huge interval.
                    if (last.subtract(first).compareTo(BigInteger.valueOf(excluded.size())) < 0) {
                        boolean present = false;
                        for (var n = first; n.compareTo(last) <= 0; n = n.add(BigInteger.ONE))
                            present |= !excluded.contains(new Fraction(n, BigInteger.ONE));
                        if (!present) return State.EMPTY;
                    }
                }
            }
            return State.NONEMPTY;
        } catch (IllegalArgumentException | ArithmeticException e) { return State.UNKNOWN; }
    }
    private record Affine(Fraction a, Fraction b) {
        Affine subtract(Affine other) { return new Affine(a.subtract(other.a), b.subtract(other.b)); }
        Affine scale(Fraction value) { return new Affine(a.multiply(value), b.multiply(value)); }
    }
    private static Affine affine(MathExpression expression, PlanningSource source, int[] budget) {
        if (++budget[0] > 1200) throw new IllegalArgumentException("Scope budget exceeded");
        if (expression instanceof MathExpression.Number n) {
            var decimal = new BigDecimal(n.value());
            return new Affine(Fraction.ZERO, new Fraction(decimal.unscaledValue(), BigInteger.TEN.pow(decimal.scale())));
        }
        if (expression instanceof MathExpression.Variable v && source.variables().containsKey(v.symbol()))
            return new Affine(Fraction.ONE, Fraction.ZERO);
        if (expression instanceof MathExpression.Unary u) return affine(u.operand(), source, budget).scale(Fraction.ONE.negate());
        if (expression instanceof MathExpression.Binary b) {
            var left = affine(b.left(), source, budget); var right = affine(b.right(), source, budget);
            return switch (b.operator()) {
                case ADD -> left.subtract(right.scale(Fraction.ONE.negate()));
                case SUBTRACT -> left.subtract(right);
                case MULTIPLY -> {
                    if (left.a.sign() == 0) yield right.scale(left.b);
                    if (right.a.sign() == 0) yield left.scale(right.b);
                    throw new IllegalArgumentException("Nonlinear restriction");
                }
                case DIVIDE -> {
                    if (right.a.sign() != 0 || right.b.sign() == 0) throw new IllegalArgumentException("Nonconstant denominator");
                    yield left.scale(Fraction.ONE.divide(right.b));
                }
                case POWER -> {
                    if (!right.a.equals(Fraction.ZERO) || !right.b.equals(Fraction.ONE)) throw new IllegalArgumentException("Nonlinear power");
                    yield left;
                }
            };
        }
        throw new IllegalArgumentException("Unsupported source feasibility");
    }
    private record Fraction(BigInteger numerator, BigInteger denominator) implements Comparable<Fraction> {
        static final Fraction ZERO = new Fraction(BigInteger.ZERO, BigInteger.ONE);
        static final Fraction ONE = new Fraction(BigInteger.ONE, BigInteger.ONE);
        Fraction {
            if (denominator.signum() == 0 || numerator.abs().bitLength() > 1024 || denominator.abs().bitLength() > 1024)
                throw new IllegalArgumentException("Unbounded fraction");
            if (denominator.signum() < 0) { numerator = numerator.negate(); denominator = denominator.negate(); }
            var gcd = numerator.gcd(denominator); numerator = numerator.divide(gcd); denominator = denominator.divide(gcd);
        }
        int sign() { return numerator.signum(); }
        Fraction negate() { return new Fraction(numerator.negate(), denominator); }
        Fraction subtract(Fraction other) { return new Fraction(numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)), denominator.multiply(other.denominator)); }
        Fraction multiply(Fraction other) { return new Fraction(numerator.multiply(other.numerator), denominator.multiply(other.denominator)); }
        Fraction divide(Fraction other) { return new Fraction(numerator.multiply(other.denominator), denominator.multiply(other.numerator)); }
        public int compareTo(Fraction other) { return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator)); }
        BigInteger floor() {
            var qr = numerator.divideAndRemainder(denominator);
            return qr[1].signum() < 0 ? qr[0].subtract(BigInteger.ONE) : qr[0];
        }
        BigInteger ceil() { return negate().floor().negate(); }
    }
}
