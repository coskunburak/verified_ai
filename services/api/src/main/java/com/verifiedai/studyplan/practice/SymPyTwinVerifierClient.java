package com.verifiedai.studyplan.practice;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
public class SymPyTwinVerifierClient {

    public record GeneratedTwinPayload(
            String trapFamily,
            String promptLatex,
            String promptInstructionTr,
            String promptInstructionEn,
            String canonicalSolutionExpr,
            String trapDistractorExpr,
            String sympyVerificationHash,
            BigDecimal targetDifficulty
    ) {
    }

    public record EvaluationResult(
            boolean isCorrect,
            boolean trapTriggered,
            String trapDiagnosticKey,
            String trapDiagnosticMessageTr,
            String trapDiagnosticMessageEn
    ) {
    }

    public GeneratedTwinPayload generateTwin(UUID userId, String skillId, String trapFamily, int ordinalIndex) {
        if (trapFamily == null || trapFamily.isBlank()) {
            throw new IllegalArgumentException("trapFamily cannot be null or blank");
        }

        byte[] seed = hashSeed(userId + ":" + skillId + ":" + trapFamily + ":" + ordinalIndex);

        return switch (trapFamily) {
            case "ALG_DOMAIN_EXCLUSION_MISSED" -> generateRationalTwin(seed);
            case "INEQ_NEGATIVE_MULT_NO_FLIP" -> generateInequalityTwin(seed);
            case "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS" -> generateRadicalTwin(seed);
            case "CALC_CHAIN_RULE_OMITTED" -> generateChainRuleTwin(seed);
            default -> throw new IllegalArgumentException("Unsupported trap family: " + trapFamily);
        };
    }

    public EvaluationResult evaluateSubmission(
            String trapFamily,
            String promptLatex,
            String canonicalSolutionExpr,
            String trapDistractorExpr,
            String submittedAnswerLatex
    ) {
        if (submittedAnswerLatex == null || submittedAnswerLatex.isBlank()) {
            return new EvaluationResult(false, false, null, null, null);
        }

        String sub = cleanExpr(submittedAnswerLatex);
        String canon = cleanExpr(canonicalSolutionExpr);
        String trap = trapDistractorExpr != null ? cleanExpr(trapDistractorExpr) : null;

        // Check if correct
        if (matchesAnswer(sub, canon, trapFamily)) {
            return new EvaluationResult(true, false, null, null, null);
        }

        // Check if trap triggered
        if (trap != null && matchesAnswer(sub, trap, trapFamily)) {
            return createTrapDiagnostic(trapFamily);
        }

        return new EvaluationResult(false, false, null, null, null);
    }

    private boolean matchesAnswer(String submitted, String target, String trapFamily) {
        if (submitted.equalsIgnoreCase(target)) {
            return true;
        }

        // Set matching for roots e.g. "{3, 5}" vs "3, 5" or "5, 3"
        Set<String> subTokens = parseSetTokens(submitted);
        Set<String> targetTokens = parseSetTokens(target);
        if (!subTokens.isEmpty() && subTokens.equals(targetTokens)) {
            return true;
        }

        // Inequality matching e.g. "x > -4" vs "x>-4"
        String subNorm = submitted.replace(" ", "");
        String targetNorm = target.replace(" ", "");
        return subNorm.equalsIgnoreCase(targetNorm);
    }

    private Set<String> parseSetTokens(String text) {
        String cleaned = text.replace("{", "").replace("}", "").replace("x=", "").replace("x =", "");
        String[] parts = cleaned.split("[,;]");
        Set<String> set = new HashSet<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    private EvaluationResult createTrapDiagnostic(String trapFamily) {
        return switch (trapFamily) {
            case "ALG_DOMAIN_EXCLUSION_MISSED" -> new EvaluationResult(
                    false,
                    true,
                    "TRAP_DOMAIN_EXCLUSION",
                    "Bulunan köklerden biri paydayı sıfır yaparak ifadeyi tanımsız kılmaktadır. Tanım kümesi kısıtlamalarını kontrol ediniz.",
                    "One of the roots causes division by zero in the original equation. Check domain exclusions."
            );
            case "INEQ_NEGATIVE_MULT_NO_FLIP" -> new EvaluationResult(
                    false,
                    true,
                    "TRAP_INEQUALITY_SIGN_FLIP",
                    "Eşitsizlik negatif bir sayıya bölünürken veya çarpılırken eşitsizlik yönü tersine dönmelidir.",
                    "When multiplying or dividing an inequality by a negative value, the inequality sign must flip."
            );
            case "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS" -> new EvaluationResult(
                    false,
                    true,
                    "TRAP_EXTRANEOUS_ROOT",
                    "Her iki tarafın karesi alındığında yabancı kökler ortaya çıkabilir. Bulunan kökleri orijinal denklemde deneyiniz.",
                    "Squaring both sides can introduce extraneous roots. Verify candidate roots in the original radical equation."
            );
            case "CALC_CHAIN_RULE_OMITTED" -> new EvaluationResult(
                    false,
                    true,
                    "TRAP_CHAIN_RULE_INNER",
                    "Bileşke fonksiyon türevinde iç fonksiyonun türeviyle çarpmayı unutmayınız.",
                    "Remember to multiply by the derivative of the inner function when applying the chain rule."
            );
            default -> new EvaluationResult(false, true, "GENERIC_TRAP", "Odak alanı tespit edildi.", "Reasoning focus area detected.");
        };
    }

    private String cleanExpr(String s) {
        return s.trim()
                .replace("$", "")
                .replace("\\left", "")
                .replace("\\right", "")
                .replace("\\cdot", "*")
                .replace("\\times", "*")
                .trim();
    }

    private GeneratedTwinPayload generateRationalTwin(byte[] seed) {
        int rExclRaw = (seed[0] & 0xFF) % 14 - 7;
        int rExcl = rExclRaw != 0 ? rExclRaw : 3;

        int rValidRaw = (seed[1] & 0xFF) % 18 - 9;
        int rValid = (rValidRaw != 0 && rValidRaw != rExcl) ? rValidRaw : (rExcl + 2);

        int a = ((seed[2] & 0xFF) % 3) + 1;
        int kStep = ((seed[3] & 0xFF) % 7) - 3;
        if (kStep == 0 || (rValid + kStep == rExcl) || (rValid + kStep == 0)) {
            kStep = (rValid + 2 != rExcl) ? 2 : -2;
        }
        int b = -a * kStep;
        int trueRoot = rValid + kStep;

        String exclTerm = rExcl > 0 ? "x - " + rExcl : "x + " + (-rExcl);
        String validTerm = rValid > 0 ? "x - " + rValid : "x + " + (-rValid);
        String axTerm = a > 1 ? a + "x" : "x";
        int rhsNum = a * rExcl;
        String bSign = b > 0 ? "+" : "-";
        int bAbs = Math.abs(b);

        String prompt = String.format("\\frac{%s}{%s} %s \\frac{%d}{%s} = \\frac{%d}{%s}",
                axTerm, exclTerm, bSign, bAbs, validTerm, rhsNum, exclTerm);
        String canon = String.valueOf(trueRoot);
        String trap = String.format("{%d, %d}", Math.min(trueRoot, rExcl), Math.max(trueRoot, rExcl));
        String hash = computeSha256("ALG_DOMAIN_EXCLUSION_MISSED|" + prompt + "|" + canon + "|" + trap);

        return new GeneratedTwinPayload(
                "ALG_DOMAIN_EXCLUSION_MISSED",
                prompt,
                "Denklemi çözünüz. Tanımsızlık oluşturan köklere dikkat ediniz.",
                "Solve the equation. Pay careful attention to roots that make denominators zero.",
                canon,
                trap,
                hash,
                new BigDecimal("0.5000")
        );
    }

    private GeneratedTwinPayload generateInequalityTwin(byte[] seed) {
        int a = ((seed[0] & 0xFF) % 6) + 2;
        int k = ((seed[1] & 0xFF) % 17) - 8;
        int b = ((seed[2] & 0xFF) % 19) - 9;
        int c = b - a * k;

        String bStr = b > 0 ? "+ " + b : (b < 0 ? "- " + (-b) : "");
        String prompt = String.format("-%dx %s < %d", a, bStr, c).replace("  ", " ");
        String canon = "x > " + k;
        String trap = "x < " + k;
        String hash = computeSha256("INEQ_NEGATIVE_MULT_NO_FLIP|" + prompt + "|" + canon + "|" + trap);

        return new GeneratedTwinPayload(
                "INEQ_NEGATIVE_MULT_NO_FLIP",
                prompt,
                "Eşitsizliği çözünüz.",
                "Solve the inequality.",
                canon,
                trap,
                hash,
                new BigDecimal("0.4500")
        );
    }

    private GeneratedTwinPayload generateRadicalTwin(byte[] seed) {
        int u = ((seed[0] & 0xFF) % 4) + 1;
        int vOffset = ((seed[1] & 0xFF) % 4) + 1;
        int v = u + vOffset;
        int c = ((seed[2] & 0xFF) % 9) - 4;
        int a = v - u;
        int b = u * v - c * (v - u);

        int rValid = c + v;
        int rExt = c - u;

        String rhsStr = c > 0 ? "x - " + c : (c < 0 ? "x + " + (-c) : "x");
        String axStr = a > 1 ? a + "x" : "x";
        String bStr = b > 0 ? "+ " + b : (b < 0 ? "- " + (-b) : "");
        String prompt = String.format("\\sqrt{%s %s} = %s", axStr, bStr, rhsStr).replace("  ", " ");

        String canon = String.valueOf(rValid);
        String trap = String.format("{%d, %d}", Math.min(rValid, rExt), Math.max(rValid, rExt));
        String hash = computeSha256("ALG_SQUARE_BOTH_SIDES_EXTRANEOUS|" + prompt + "|" + canon + "|" + trap);

        return new GeneratedTwinPayload(
                "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS",
                prompt,
                "Denklemi çözünüz. Yabancı kökleri kontrol ediniz.",
                "Solve the equation. Check for extraneous roots.",
                canon,
                trap,
                hash,
                new BigDecimal("0.5500")
        );
    }

    private GeneratedTwinPayload generateChainRuleTwin(byte[] seed) {
        int a = ((seed[0] & 0xFF) % 4) + 2;
        int n = ((seed[1] & 0xFF) % 2) + 2;
        int m = ((seed[2] & 0xFF) % 3) + 3;
        int bRaw = ((seed[3] & 0xFF) % 18) - 9;
        int b = bRaw != 0 ? bRaw : 3;

        String bSign = b > 0 ? "+" : "-";
        String prompt = String.format("f(x) = (%dx^{%d} %s %d)^{%d}", a, n, bSign, Math.abs(b), m);
        String canon = String.format("%dx^{%d}(%dx^{%d} %s %d)^{%d}", m * a * n, n - 1, a, n, bSign, Math.abs(b), m - 1);
        String trap = String.format("%d(%dx^{%d} %s %d)^{%d}", m, a, n, bSign, Math.abs(b), m - 1);
        String hash = computeSha256("CALC_CHAIN_RULE_OMITTED|" + prompt + "|" + canon + "|" + trap);

        return new GeneratedTwinPayload(
                "CALC_CHAIN_RULE_OMITTED",
                prompt,
                "Fonksiyonun türevini bulunuz.",
                "Find the derivative of the function.",
                canon,
                trap,
                hash,
                new BigDecimal("0.6000")
        );
    }

    private byte[] hashSeed(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
