package com.verifiedai.ai;

import com.verifiedai.ai.application.*;
import com.verifiedai.ai.infrastructure.configuration.AiRouteRegistryProperties;
import com.verifiedai.ai.infrastructure.configuration.AiRoutingProperties;
import com.verifiedai.ai.infrastructure.configuration.GeminiProviderProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Synthetic, test-only fixtures and policies for Gemini provider verification.
 * Uses synthetic credentials only; never accesses real API keys or external services.
 */
public final class GeminiTestPolicy {
    private GeminiTestPolicy() {}

    public static final String CREDENTIAL = "not_a_secret_synthetic_gemini_test_key";
    public static final String MODEL = GeminiProviderProperties.DEFAULT_MODEL;
    public static final String ROUTE = "solve-gemini-eval-v1";
    public static final String GATE = "gemini-offline-evaluation-v1";

    public static GeminiProviderProperties config(URI endpoint) {
        return new GeminiProviderProperties(
            true,
            endpoint,
            MODEL,
            CREDENTIAL,
            "owner-test-approval",
            new GeminiProviderProperties.Limits(
                1048576,
                32768,
                8192,
                65536,
                131072,
                Duration.ofSeconds(1),
                Duration.ofSeconds(30),
                2
            ),
            new GeminiProviderProperties.Accounting(
                "gemini-2.5-flash-test-v1",
                "PREPAID_CAPACITY_FIXED_ALLOCATION",
                100L
            )
        );
    }

    public static AiRouteRegistryProperties.CapabilityRoute route(String provider, String model, long budget) {
        return new AiRouteRegistryProperties.CapabilityRoute(
            true,
            ROUTE,
            "gemini-evaluation-v1",
            new AiRouteRegistryProperties.RouteTarget(provider, model),
            List.of(),
            "solver",
            "v002",
            "solution-candidate-v1",
            Duration.ofSeconds(30),
            1,
            65536,
            budget,
            "gemini-2.5-flash-test-v1",
            AiRoutePlan.CachePolicy.DISABLED,
            AiRoutePlan.StreamingPolicy.DISABLED,
            "ZERO_RETENTION",
            "LOCAL_TEST",
            "gemini-evaluation",
            GATE,
            AiRoutePlan.ReleaseStage.LOCAL_ONLY
        );
    }

    public static AiRoutePlan plan() {
        var c = route(GeminiProviderProperties.PROVIDER, MODEL, 100);
        return new AiRoutePlan(
            AiCapability.SOLVE,
            c.routePolicyVersion(),
            c.routeId(),
            new AiRouteTarget(c.primary().provider(), c.primary().model()),
            List.of(),
            c.promptId(),
            c.promptVersion(),
            c.schemaVersion(),
            c.timeout(),
            c.maxAttempts(),
            c.maxResponseBytes(),
            c.maxCostMicros(),
            c.pricingVersion(),
            c.cachePolicy(),
            c.streamingPolicy(),
            c.providerRetentionPolicy(),
            c.privacyRegion(),
            c.circuitBreakerKey(),
            c.qualityGateVersion(),
            c.releaseStage()
        );
    }

    public static AiRoutingProperties routing() {
        return new AiRoutingProperties(
            Duration.ofSeconds(30),
            List.of(new AiRouteEligibility(
                AiCapability.SOLVE,
                plan().primary(),
                GATE,
                100L,
                Duration.ofSeconds(30),
                Set.of("EVALUATE", "SIMPLIFY", "SOLVE_EQUATION", "SOLVE_INEQUALITY"),
                Set.of("UNKNOWN", "EASY"),
                Set.of("FREE"),
                "LOCAL_TEST",
                "ZERO_RETENTION",
                true
            ))
        );
    }

    public static AiExecutionCommand command(com.verifiedai.problem.application.solving.SolveSource s, long budget, Instant deadline) {
        var c = new AiRouteContext(
            AiCapability.SOLVE,
            s.problemType(),
            s.taskType(),
            null,
            null,
            false,
            "LOW",
            false,
            null,
            Locale.forLanguageTag("tr"),
            Duration.ofSeconds(30),
            budget,
            null,
            Map.of(),
            null,
            "LOCAL_TEST",
            "ZERO_RETENTION",
            AiRouteContext.RouteRole.PRIMARY_SOLVER
        );
        return new AiExecutionCommand(
            AiCapability.SOLVE,
            new AiSolveRequest(
                s.canonicalProblemId(),
                s.problemParseId(),
                s.problemParseRevision(),
                s.canonicalJson()
            ),
            new AiExecutionContext(
                UUID.randomUUID(),
                s.userId(),
                s.problemSessionId(),
                "gemini-test-correlation",
                null,
                "gemini-test-idempotency",
                deadline,
                Map.of()
            ),
            c,
            "application/json",
            "canonical-problem-v1",
            "solver",
            "v002",
            "solution-candidate-v1",
            budget
        );
    }
}
