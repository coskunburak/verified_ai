package com.verifiedai.studyplan.practice;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Component
public class CuratedPracticeQuestionBank {

    private final List<CuratedPracticeQuestion> questions = new ArrayList<>();

    public CuratedPracticeQuestionBank() {
        initBank();
    }

    private void initBank() {
        // 1. Rational Equation
        registerQuestion(
                "BANK-ALG-RATIONAL-001",
                "MATH.ALG.RATIONAL_EQ",
                "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.5000"),
                "\\frac{2x}{x-3} + \\frac{4}{x-5} = \\frac{6}{x-3}",
                "Denklemi çözünüz. Tanımsızlık oluşturan köklere dikkat ediniz.",
                "Solve the equation. Pay careful attention to roots that make denominators zero.",
                "3",
                "{3, 5}"
        );
        registerQuestion(
                "BANK-ALG-RATIONAL-002",
                "MATH.ALG.RATIONAL_EQ",
                "ALG_DOMAIN_EXCLUSION_MISSED",
                new BigDecimal("0.6500"),
                "\\frac{3x}{x-4} - \\frac{6}{x-2} = \\frac{12}{x-4}",
                "Denklemi çözünüz. Tanımsızlık oluşturan köklere dikkat ediniz.",
                "Solve the equation. Pay careful attention to roots that make denominators zero.",
                "4",
                "{2, 4}"
        );

        // 2. Linear Inequality
        registerQuestion(
                "BANK-ALG-INEQ-001",
                "MATH.ALG.LINEAR_INEQ",
                "INEQ_NEGATIVE_MULT_NO_FLIP",
                new BigDecimal("0.4500"),
                "-3x + 7 < 19",
                "Eşitsizliği çözünüz.",
                "Solve the inequality.",
                "x > -4",
                "x < -4"
        );
        registerQuestion(
                "BANK-ALG-INEQ-002",
                "MATH.ALG.LINEAR_INEQ",
                "INEQ_NEGATIVE_MULT_NO_FLIP",
                new BigDecimal("0.5500"),
                "-5x - 4 \\ge 21",
                "Eşitsizliği çözünüz.",
                "Solve the inequality.",
                "x <= -5",
                "x >= -5"
        );

        // 3. Radical Equation
        registerQuestion(
                "BANK-ALG-RADICAL-001",
                "MATH.ALG.RADICAL_EQ",
                "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS",
                new BigDecimal("0.5500"),
                "\\sqrt{2x + 15} = x",
                "Denklemi çözünüz. Yabancı kökleri kontrol ediniz.",
                "Solve the equation. Check for extraneous roots.",
                "5",
                "{-3, 5}"
        );
        registerQuestion(
                "BANK-ALG-RADICAL-002",
                "MATH.ALG.RADICAL_EQ",
                "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS",
                new BigDecimal("0.7000"),
                "\\sqrt{3x + 10} = x - 2",
                "Denklemi çözünüz. Yabancı kökleri kontrol ediniz.",
                "Solve the equation. Check for extraneous roots.",
                "6",
                "{-1, 6}"
        );

        // 4. Calculus Chain Rule
        registerQuestion(
                "BANK-CALC-CHAIN-001",
                "MATH.CALC.CHAIN_RULE",
                "CALC_CHAIN_RULE_OMITTED",
                new BigDecimal("0.6000"),
                "f(x) = (3x^2 + 2)^4",
                "Fonksiyonun türevini bulunuz.",
                "Find the derivative of the function.",
                "24x(3x^2 + 2)^3",
                "4(3x^2 + 2)^3"
        );
        registerQuestion(
                "BANK-CALC-CHAIN-002",
                "MATH.CALC.CHAIN_RULE",
                "CALC_CHAIN_RULE_OMITTED",
                new BigDecimal("0.7500"),
                "f(x) = (2x^3 - 5)^5",
                "Fonksiyonun türevini bulunuz.",
                "Find the derivative of the function.",
                "30x^2(2x^3 - 5)^4",
                "5(2x^3 - 5)^4"
        );

        // 5. Synthesis / Multi-Topic Fallbacks
        registerQuestion(
                "BANK-SYNTH-001",
                "MATH.ALG.SYNTHESIS",
                null,
                new BigDecimal("0.6500"),
                "x^2 - 6x + 9 = 0",
                "Denklemin köklerini bulunuz.",
                "Find the roots of the equation.",
                "3",
                null
        );
        registerQuestion(
                "BANK-SYNTH-002",
                "MATH.CALC.SYNTHESIS",
                null,
                new BigDecimal("0.8000"),
                "\\lim_{x \\to 0} \\frac{\\sin(5x)}{x}",
                "Limit değerini hesaplayınız.",
                "Evaluate the limit.",
                "5",
                null
        );
    }

    private void registerQuestion(
            String id,
            String skillId,
            String trapFamily,
            BigDecimal difficulty,
            String promptLatex,
            String instructionTr,
            String instructionEn,
            String canonicalSolutionExpr,
            String trapDistractorExpr
    ) {
        String raw = (trapFamily != null ? trapFamily : "NONE") + "|" + promptLatex + "|" + canonicalSolutionExpr + "|" + (trapDistractorExpr != null ? trapDistractorExpr : "NONE");
        String hash = computeSha256(raw);
        questions.add(new CuratedPracticeQuestion(
                id,
                skillId,
                trapFamily,
                difficulty,
                promptLatex,
                instructionTr,
                instructionEn,
                canonicalSolutionExpr,
                trapDistractorExpr,
                hash
        ));
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

    public List<CuratedPracticeQuestion> getCandidatesForSkill(String skillId) {
        List<CuratedPracticeQuestion> list = questions.stream()
                .filter(q -> q.skillId().equalsIgnoreCase(skillId) || skillId.startsWith(q.skillId()) || q.skillId().startsWith(skillId))
                .toList();
        if (list.isEmpty()) {
            return questions; // fallback to general pool if skill candidate is empty
        }
        return list;
    }

    public CuratedPracticeQuestion findBestMatching(
            String skillId,
            BigDecimal targetDifficulty,
            String trapFamily,
            Set<String> excludedQuestionIds
    ) {
        List<CuratedPracticeQuestion> candidates = getCandidatesForSkill(skillId);
        return candidates.stream()
                .filter(q -> excludedQuestionIds == null || !excludedQuestionIds.contains(q.questionId()))
                .min(Comparator
                        .comparing((CuratedPracticeQuestion q) -> {
                            boolean matchesTrap = trapFamily != null && trapFamily.equalsIgnoreCase(q.trapFamily());
                            return matchesTrap ? 0 : 1;
                        })
                        .thenComparing(q -> q.difficulty().subtract(targetDifficulty).abs())
                        .thenComparing(CuratedPracticeQuestion::questionId)
                )
                .orElseGet(() -> candidates.get(0));
    }

    public List<CuratedPracticeQuestion> getAll() {
        return Collections.unmodifiableList(questions);
    }
}
