package com.verifiedai.problem.application.solving;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import java.math.*;
import java.util.*;

/** Versioned source syntax. No verifier types or mathematical correctness decision. */
public final class CalculusSourceDocument {
    private CalculusSourceDocument() { }
    public static String method(String type, String task) {
        return switch (type + "/" + task) {
            case "DERIVATIVE/DIFFERENTIATE" -> "DERIVATIVE_BACKCHECK";
            case "INTEGRAL/INTEGRATE" -> "INTEGRAL_DIFFERENTIATION_BACKCHECK";
            case "LIMIT/FIND_LIMIT" -> "FINITE_LIMIT";
            default -> throw new IllegalArgumentException("Unsupported calculus task");
        };
    }
    public static JsonNode parse(String raw) {
        var root = VersionedJsonContract.validate("problem-parse-v2", raw);
        metadata(root);
        if (root.path("expressions").size() != 1 || root.path("variables").size() != 1
            || !root.path("variables").get(0).path("symbol").equals(root.path("calculus").path("variable").path("symbol"))
            || !"VARIABLE".equals(root.path("variables").get(0).path("role").asText())
            || !"PRIMARY".equals(root.path("expressions").get(0).path("role").asText()))
            fail();
        // Free-text assumptions/constraints cannot add executable obligations. Typed restrictions carry them.
        if (!root.path("constraints").isEmpty() || !root.path("assumptions").isEmpty()) fail();
        var refs = root.path("sourceEvidenceRefs");
        for (String field : List.of("original", "variable", "interval", "restrictions")) {
            boolean found = false;
            for (var ref : refs) found |= ("calculus." + field).equals(ref.path("fieldPath").asText());
            if (!found) fail();
        }
        if (root.path("problemType").asText().equals("LIMIT")) {
            boolean found = false;
            for (var ref : refs) found |= "calculus.approach".equals(ref.path("fieldPath").asText());
            if (!found) fail();
        }
        if (root.path("problemType").asText().equals("INTEGRAL")) {
            boolean found=false;
            for (var ref : refs) found |= "calculus.integralScope".equals(ref.path("fieldPath").asText());
            if (!found) fail();
        }
        return root;
    }
    public static JsonNode canonical(SolveSource source) {
        var root = VersionedJsonContract.validate("canonical-problem-v2", source.canonicalJson());
        var p = root.path("sourceParse");
        if (!source.problemParseId().toString().equals(p.path("problemParseId").asText())
            || source.problemParseRevision() != p.path("problemParseRevision").asInt()
            || !source.problemType().equals(root.path("problemType").asText())
            || !source.taskType().equals(root.path("taskType").asText())) fail();
        var accepted = parse(root.path("acceptedParse").toString());
        if (!accepted.path("calculus").equals(root.path("calculus"))
            || !accepted.path("problemType").equals(root.path("problemType"))
            || !accepted.path("taskType").equals(root.path("taskType"))
            || accepted.path("reviewRequired").asBoolean(true)
            || !"SUPPORTED".equals(accepted.path("supportStatus").asText())
            || accepted.path("uncertainty").path("reviewRequired").asBoolean(true)
            || !accepted.path("uncertainty").path("recognition").isEmpty()
            || !accepted.path("uncertainty").path("parse").isEmpty()) fail();
        metadata(root);
        return root;
    }
    public static void metadata(JsonNode root) {
        String selected = method(root.path("problemType").asText(), root.path("taskType").asText());
        var c = root.path("calculus");
        String symbol = c.path("variable").path("symbol").asText();
        if (!symbol.matches("[A-Za-z][A-Za-z0-9_]{0,15}") || !"REAL".equals(c.path("variable").path("domain").asText())) fail();
        if ((selected.equals("FINITE_LIMIT")) != !c.path("approach").isNull()
            || (selected.equals("INTEGRAL_DIFFERENTIATION_BACKCHECK")) != !c.path("integralScope").isNull()) fail();
        var lower = exact(c.path("interval").path("lower")); var upper = exact(c.path("interval").path("upper"));
        if (lower != null && upper != null && compare(lower, upper) >= 0) fail();
        if (!c.path("approach").isNull()) {
            var target = exact(c.path("approach").path("target"));
            if (target == null) fail();
            String d = c.path("approach").path("direction").asText();
            if (lower != null && (compare(target, lower) < 0 || compare(target, lower) == 0 && !d.equals("RIGHT"))) fail();
            if (upper != null && (compare(target, upper) > 0 || compare(target, upper) == 0 && !d.equals("LEFT"))) fail();
        }
        int[] total = {0}; expression(c.path("original"), symbol, 1, 0, total);
        var ids = new HashSet<String>();
        var comparisons = new HashMap<List<JsonNode>,Integer>();
        for (var restriction : c.path("restrictions")) {
            if (!ids.add(restriction.path("id").asText())) fail();
            expression(restriction.path("left"), symbol, 1, 0, total);
            expression(restriction.path("right"), symbol, 1, 0, total);
            int allowed=switch(restriction.path("relation").asText()) {
                case "LESS_THAN" -> 1; case "LESS_THAN_OR_EQUAL" -> 3; case "EQUALS" -> 2;
                case "NOT_EQUALS" -> 5; case "GREATER_THAN_OR_EQUAL" -> 6; case "GREATER_THAN" -> 4;
                default -> 0;
            };
            var key=List.of(restriction.path("left"),restriction.path("right"));
            int compatible=comparisons.getOrDefault(key,7)&allowed;
            if (compatible==0 || restriction.path("left").equals(restriction.path("right")) && (allowed&2)==0) fail();
            comparisons.put(key,compatible);
        }
        if (total[0] > 1200) fail();
    }
    private static BigInteger[] exact(JsonNode n) {
        if (n.isNull()) return null;
        String a = n.path("numerator").asText(), b = n.path("denominator").asText();
        if (!a.matches("-?(0|[1-9][0-9]{0,308})") || !b.matches("[1-9][0-9]{0,308}")) fail();
        var numerator = new BigInteger(a); var denominator = new BigInteger(b);
        if (numerator.abs().bitLength() > 1024 || denominator.bitLength() > 1024
            || !numerator.gcd(denominator).equals(BigInteger.ONE)
            || !numerator.toString().equals(a)) fail();
        return new BigInteger[]{numerator, denominator};
    }
    private static int compare(BigInteger[] a, BigInteger[] b) { return a[0].multiply(b[1]).compareTo(b[0].multiply(a[1])); }
    public static void expression(JsonNode n, String symbol, int depth, int functions, int[] total) {
        int before = total[0]; node(n, symbol, depth, functions, total);
        if (total[0] - before > 120) fail();
    }
    private static void node(JsonNode n, String symbol, int depth, int functions, int[] total) {
        if (depth > 32 || functions > 8 || ++total[0] > 1200) fail();
        switch (n.path("kind").asText()) {
            case "NUMBER" -> {
                String value = n.path("value").asText();
                if (!value.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?") || value.replaceAll("[^0-9]", "").length() > 64) fail();
                if ("INTEGER".equals(n.path("numericType").asText()) && value.contains(".")) fail();
            }
            case "VARIABLE" -> { if (!symbol.equals(n.path("symbol").asText())) fail(); }
            case "UNARY" -> node(n.path("operand"), symbol, depth + 1, functions, total);
            case "BINARY" -> {
                node(n.path("left"), symbol, depth + 1, functions, total); node(n.path("right"), symbol, depth + 1, functions, total);
                if ("POWER".equals(n.path("operator").asText())) {
                    var exponent = n.path("right");
                    if (!"NUMBER".equals(exponent.path("kind").asText())) fail();
                    var value = new BigDecimal(exponent.path("value").asText());
                    if (value.stripTrailingZeros().scale() > 0 || value.abs().compareTo(BigDecimal.valueOf(12)) > 0) fail();
                }
            }
            case "FUNCTION" -> node(n.path("args").get(0), symbol, depth + 1, functions + 1, total);
            default -> fail();
        }
    }
    private static void fail() { throw new IllegalArgumentException("Calculus source semantics rejected"); }
}
