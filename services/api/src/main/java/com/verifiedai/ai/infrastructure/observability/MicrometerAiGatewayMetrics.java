package com.verifiedai.ai.infrastructure.observability;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiExecutionResult;
import com.verifiedai.ai.application.AiExecutionStatus;
import com.verifiedai.ai.application.AiGatewayMetrics;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiRouteTarget;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class MicrometerAiGatewayMetrics
    implements AiGatewayMetrics {

    private final MeterRegistry registry;
    private final String environment;

    MicrometerAiGatewayMetrics(
        MeterRegistry registry,
        @Value("${app.environment:local}")
        String environment
    ) {
        this.registry = registry;
        this.environment = environment;
    }

    @Override
    public void request(
        AiRoutePlan routePlan
    ) {
        Counter.builder(
                "ai.gateway.request.total"
            )
            .tag(
                "capability",
                routePlan.capability().name()
            )
            .tag(
                "route_policy_version",
                routePlan.routePolicyVersion()
            )
            .tag(
                "provider",
                routePlan.primaryProvider()
            )
            .tag(
                "environment",
                environment
            )
            .register(registry)
            .increment();
    }

    @Override
    public void result(
        AiRoutePlan routePlan,
        AiRouteTarget target,
        AiExecutionResult result
    ) {
        String failure =
            result.failureClass() == null
                ? "NONE"
                : result.failureClass().name();

        Counter.builder(
                "ai.gateway.result.total"
            )
            .tag(
                "capability",
                routePlan.capability().name()
            )
            .tag(
                "route_policy_version",
                routePlan.routePolicyVersion()
            )
            .tag(
                "provider",
                target.provider()
            )
            .tag(
                "status",
                result.status().name()
            )
            .tag(
                "failure_class",
                failure
            )
            .tag(
                "fallback_used",
                Boolean.toString(
                    result.fallbackUsed()
                )
            )
            .tag(
                "environment",
                environment
            )
            .register(registry)
            .increment();

        DistributionSummary.builder(
                "ai.gateway.latency.ms"
            )
            .baseUnit("milliseconds")
            .tag(
                "capability",
                routePlan.capability().name()
            )
            .tag(
                "provider",
                target.provider()
            )
            .tag(
                "environment",
                environment
            )
            .register(registry)
            .record(
                result.gatewayLatencyMs()
            );

        DistributionSummary.builder(
                "ai.gateway.provider.latency.ms"
            )
            .baseUnit("milliseconds")
            .tag(
                "capability",
                routePlan.capability().name()
            )
            .tag(
                "provider",
                target.provider()
            )
            .tag(
                "environment",
                environment
            )
            .register(registry)
            .record(
                result.providerLatencyMs()
            );

        long cost =
            result.usage() == null
                ? 0
                : result
                .usage()
                .estimatedCostMicros();

        DistributionSummary.builder(
                "ai.gateway.estimated_cost_micros"
            )
            .tag(
                "capability",
                routePlan.capability().name()
            )
            .tag(
                "provider",
                target.provider()
            )
            .tag(
                "environment",
                environment
            )
            .register(registry)
            .record(cost);

        if (
            result.failureClass()
                == com.verifiedai.ai.application
                .AiProviderFailureClass.SCHEMA_INVALID
        ) {
            increment(
                "ai.gateway.schema_invalid.total",
                routePlan.capability(),
                target.provider()
            );
        }

        if (
            result.status()
                == AiExecutionStatus.BLOCKED_BUDGET
        ) {
            increment(
                "ai.gateway.budget_exceeded.total",
                routePlan.capability(),
                target.provider()
            );
        }
    }

    @Override
    public void retry(
        AiRoutePlan routePlan,
        AiRouteTarget failedTarget
    ) {
        increment(
            "ai.gateway.retry.total",
            routePlan.capability(),
            failedTarget.provider()
        );
    }

    @Override
    public void fallback(
        AiRoutePlan routePlan,
        AiRouteTarget target
    ) {
        increment(
            "ai.gateway.fallback.total",
            routePlan.capability(),
            target.provider()
        );
    }

    @Override
    public void blocked(
        AiCapability capability,
        AiExecutionStatus status
    ) {
        Counter.builder(
                "ai.gateway.blocked.total"
            )
            .tag(
                "capability",
                capability.name()
            )
            .tag(
                "status",
                status.name()
            )
            .tag(
                "environment",
                environment
            )
            .register(registry)
            .increment();
    }

    @Override
    public void ledgerWrite(
        AiCapability capability,
        String outcome
    ) {
        Counter.builder(
                "ai.gateway.ledger.write.total"
            )
            .tag(
                "capability",
                capability.name()
            )
            .tag(
                "outcome",
                outcome
            )
            .tag(
                "environment",
                environment
            )
            .register(registry)
            .increment();
    }

    private void increment(
        String metric,
        AiCapability capability,
        String provider
    ) {
        Counter.builder(metric)
            .tag(
                "capability",
                capability.name()
            )
            .tag(
                "provider",
                provider
            )
            .tag(
                "environment",
                environment
            )
            .register(registry)
            .increment();
    }
}
