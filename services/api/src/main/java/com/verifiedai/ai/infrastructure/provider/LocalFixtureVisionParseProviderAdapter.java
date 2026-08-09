package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiUsage;
import com.verifiedai.ai.application.AiVisionParseRequest;
import com.verifiedai.ai.application.AiVisionParseResult;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
class LocalFixtureVisionParseProviderAdapter implements VisionParseProviderAdapter {
    @Override
    public String providerId() {
        return "LOCAL_FIXTURE";
    }

    @Override
    public AiVisionParseResult execute(AiVisionParseRequest request, AiRoutePlan routePlan) {
        long started = System.nanoTime();
        String text = request.upstreamQualityWarnings().isEmpty()
            ? "x^2 + 3x = 10"
            : "x^2 + 3x = 10";
        String rawJson = """
            {
              "schemaVersion": "%s",
              "blocks": [
                {
                  "id": "block-1",
                  "kind": "MATH",
                  "text": "%s",
                  "boundingBox": {"x": 0.120000, "y": 0.300000, "width": 0.720000, "height": 0.180000},
                  "readingOrder": 0,
                  "confidence": {"raw": 0.980000, "normalized": 0.980000, "scale": "0_TO_1"},
                  "uncertainty": [],
                  "layoutHints": ["INLINE_MATH"]
                }
              ],
              "documentUncertainty": [],
              "reviewRequired": false
            }
            """.formatted(routePlan.schemaVersion(), text.replace("\"", "\\\""));
        long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000);
        return new AiVisionParseResult(
            rawJson,
            new AiProvenance(
                providerId(),
                "local-fixture-vision-v1",
                routePlan.routePolicyVersion(),
                routePlan.promptId(),
                routePlan.promptVersion(),
                routePlan.schemaVersion(),
                null,
                "local-fixture-" + request.inputAssetId().toString().toLowerCase(Locale.ROOT),
                false
            ),
            AiUsage.zeroCost(routePlan.pricingVersion()),
            latencyMs
        );
    }
}
