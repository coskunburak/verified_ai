package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemClassifyResult;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiUsage;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
class LocalFixtureProblemClassifyProviderAdapter implements ProblemClassifyProviderAdapter {
    @Override
    public String providerId() {
        return "LOCAL_FIXTURE";
    }

    @Override
    public AiProblemClassifyResult execute(AiProblemClassifyRequest request, AiRoutePlan routePlan) {
        long started = System.nanoTime();
        String projection = request.classificationProjectionJson() != null
            ? request.classificationProjectionJson().toLowerCase(Locale.ROOT)
            : "";
        String rawJson = classify(request, projection, routePlan.schemaVersion());
        long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        return new AiProblemClassifyResult(
            rawJson,
            new AiProvenance(
                providerId(),
                "local-fixture-problem-classifier-v1",
                routePlan.routePolicyVersion(),
                routePlan.promptId(),
                routePlan.promptVersion(),
                routePlan.schemaVersion(),
                null,
                "local-fixture-" + request.canonicalProblemId().toString().toLowerCase(Locale.ROOT),
                false
            ),
            AiUsage.zeroCost(routePlan.pricingVersion()),
            latencyMs
        );
    }

    private static String classify(AiProblemClassifyRequest request, String projection, String schemaVersion) {
        if (projection.contains("unknown-skill-test")) {
            return classifiedResponse(schemaVersion, "FAKE.NONEXISTENT.SKILL", "MEDIUM", "HIGH");
        }
        if (projection.contains("ambiguous")) {
            return reviewRequiredResponse(schemaVersion);
        }
        if (projection.contains("differentiat") || projection.contains("derivative")) {
            return classifiedResponse(schemaVersion, "MATH.CALCULUS.DIFFERENTIATION.POWER_RULE", "MEDIUM", "HIGH");
        }
        if (projection.contains("limit")) {
            return classifiedResponse(schemaVersion, "MATH.CALCULUS.LIMITS.DIRECT_SUBSTITUTION", "MEDIUM", "HIGH");
        }
        if (projection.contains("integrat") || projection.contains("integral")) {
            return classifiedResponse(schemaVersion, "MATH.CALCULUS.INTEGRATION.POWER_RULE", "MEDIUM", "HIGH");
        }
        if (projection.contains("quadratic") || (projection.contains("x^2") && "SOLVE_EQUATION".equals(request.taskType()))) {
            return classifiedResponse(schemaVersion, "MATH.EQUATIONS.QUADRATIC_SOLVING", "MEDIUM", "HIGH");
        }
        return switch (request.problemType() + ":" + request.taskType()) {
            case "EQUATION:SOLVE_EQUATION" ->
                classifiedResponse(schemaVersion, "MATH.EQUATIONS.LINEAR_ONE_VARIABLE", "MEDIUM", "HIGH");
            case "INEQUALITY:SOLVE_INEQUALITY" ->
                classifiedResponse(schemaVersion, "MATH.EQUATIONS.INEQUALITIES_BASIC", "MEDIUM", "HIGH");
            case "ARITHMETIC_EXPRESSION:EVALUATE" ->
                classifiedResponse(schemaVersion, "MATH.ARITHMETIC.INTEGER_OPERATIONS", "EASY", "HIGH");
            case "ALGEBRAIC_EXPRESSION:SIMPLIFY" ->
                classifiedResponse(schemaVersion, "MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS", "MEDIUM", "HIGH");
            default ->
                classifiedResponse(schemaVersion, "MATH.ARITHMETIC.INTEGER_OPERATIONS", "EASY", "HIGH");
        };
    }

    private static String classifiedResponse(String schemaVersion, String primarySkillId, String difficulty, String confidence) {
        return """
            {
              "schemaVersion": "%s",
              "status": "CLASSIFIED",
              "primarySkillId": "%s",
              "secondarySkillIds": [],
              "difficulty": "%s",
              "confidence": "%s",
              "reasoning": "Fixture classification based on problem type and task type"
            }
            """.formatted(schemaVersion, primarySkillId, difficulty, confidence);
    }

    private static String reviewRequiredResponse(String schemaVersion) {
        return """
            {
              "schemaVersion": "%s",
              "status": "REVIEW_REQUIRED",
              "primarySkillId": null,
              "secondarySkillIds": [],
              "difficulty": null,
              "confidence": "LOW",
              "reasoning": "Ambiguous classification - multiple plausible skills"
            }
            """.formatted(schemaVersion);
    }
}
