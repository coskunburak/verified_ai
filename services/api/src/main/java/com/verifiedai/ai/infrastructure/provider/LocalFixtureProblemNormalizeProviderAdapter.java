package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiProblemNormalizeRequest;
import com.verifiedai.ai.application.AiProblemNormalizeResult;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiUsage;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
class LocalFixtureProblemNormalizeProviderAdapter implements ProblemNormalizeProviderAdapter {
    @Override
    public String providerId() {
        return "LOCAL_FIXTURE";
    }

    @Override
    public AiProblemNormalizeResult execute(AiProblemNormalizeRequest request, AiRoutePlan routePlan) {
        long started = System.nanoTime();
        String evidence = request.normalizedRecognitionEvidenceJson().toLowerCase(Locale.ROOT);
        String rawJson;
        if (evidence.contains("matrix") || evidence.contains("eigen") || evidence.contains("diagram")) {
            rawJson = unsupported(routePlan.schemaVersion());
        } else if (evidence.contains("x^2?") || evidence.contains("x^7?") || evidence.contains("ambiguous")) {
            rawJson = reviewRequired(routePlan.schemaVersion());
        } else {
            rawJson = supportedEquation(routePlan.schemaVersion());
        }
        long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        return new AiProblemNormalizeResult(
            rawJson,
            new AiProvenance(
                providerId(),
                "local-fixture-problem-parser-v1",
                routePlan.routePolicyVersion(),
                routePlan.promptId(),
                routePlan.promptVersion(),
                routePlan.schemaVersion(),
                null,
                "local-fixture-" + request.recognitionEvidenceId().toString().toLowerCase(Locale.ROOT),
                false
            ),
            AiUsage.zeroCost(routePlan.pricingVersion()),
            latencyMs
        );
    }

    private static String supportedEquation(String schemaVersion) {
        return """
            {
              "schemaVersion": "%s",
              "supportStatus": "SUPPORTED",
              "unsupportedReason": null,
              "subjectId": "MATH",
              "topicId": "MATH.EQUATIONS",
              "taskType": "SOLVE_EQUATION",
              "problemType": "EQUATION",
              "expressions": [
                {
                  "id": "expr-1",
                  "role": "PRIMARY",
                  "sourceText": "x^2 + 3x = 10",
                  "normalizedText": "x^2 + 3x = 10",
                  "displayLatex": "x^2 + 3x = 10",
                  "relation": "EQUALS",
                  "sourceBlockIds": ["block-1"]
                }
              ],
              "variables": [
                {"symbol": "x", "role": "VARIABLE", "sourceBlockIds": ["block-1"]}
              ],
              "constraints": [],
              "assumptions": [],
              "uncertainty": {"recognition": [], "parse": [], "reviewRequired": false},
              "sourceEvidenceRefs": [{"blockId": "block-1", "fieldPath": "expressions[0]"}],
              "visualQualityRisks": [],
              "reviewRequired": false
            }
            """.formatted(schemaVersion);
    }

    private static String reviewRequired(String schemaVersion) {
        return """
            {
              "schemaVersion": "%s",
              "supportStatus": "REVIEW_REQUIRED",
              "unsupportedReason": null,
              "subjectId": "MATH",
              "topicId": "MATH.EQUATIONS",
              "taskType": "SOLVE_EQUATION",
              "problemType": "EQUATION",
              "expressions": [
                {
                  "id": "expr-1",
                  "role": "PRIMARY",
                  "sourceText": "x^2? + 3x = 10",
                  "normalizedText": "x^2? + 3x = 10",
                  "displayLatex": null,
                  "relation": "EQUALS",
                  "sourceBlockIds": ["block-1"]
                }
              ],
              "variables": [
                {"symbol": "x", "role": "VARIABLE", "sourceBlockIds": ["block-1"]}
              ],
              "constraints": [],
              "assumptions": [],
              "uncertainty": {"recognition": ["ambiguous exponent"], "parse": ["exponent is unclear"], "reviewRequired": true},
              "sourceEvidenceRefs": [{"blockId": "block-1", "fieldPath": "expressions[0]"}],
              "visualQualityRisks": [],
              "reviewRequired": true
            }
            """.formatted(schemaVersion);
    }

    private static String unsupported(String schemaVersion) {
        return """
            {
              "schemaVersion": "%s",
              "supportStatus": "UNSUPPORTED",
              "unsupportedReason": "UNSUPPORTED_STRUCTURE",
              "subjectId": "MATH",
              "topicId": null,
              "taskType": null,
              "problemType": null,
              "expressions": [],
              "variables": [],
              "constraints": [],
              "assumptions": [],
              "uncertainty": {"recognition": [], "parse": ["current schema cannot represent this structure"], "reviewRequired": false},
              "sourceEvidenceRefs": [{"blockId": "block-1", "fieldPath": "supportStatus"}],
              "visualQualityRisks": [],
              "reviewRequired": false
            }
            """.formatted(schemaVersion);
    }
}
