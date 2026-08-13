package com.verifiedai.ai.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiCapabilityRegistry;
import com.verifiedai.ai.application.AiCapabilityRequest;
import com.verifiedai.ai.application.AiCapabilityResult;
import com.verifiedai.ai.application.AiExecutionCommand;
import com.verifiedai.ai.application.AiExecutionContext;
import com.verifiedai.ai.application.AiExecutionResult;
import com.verifiedai.ai.application.AiExecutionStatus;
import com.verifiedai.ai.application.AiGatewayMetrics;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiRouteContext;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiRoutePlanner;
import com.verifiedai.ai.application.AiRoutePolicy;
import com.verifiedai.ai.application.AiRouteTarget;
import com.verifiedai.ai.application.AiUsage;
import com.verifiedai.ai.application.AiUsageRecord;
import com.verifiedai.ai.application.AiUsageRecorder;
import com.verifiedai.ai.application.AiVisionParseResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ConfiguredAiModelGatewayTest {

    private static final Clock CLOCK =
        Clock.fixed(
            Instant.parse(
                "2026-01-01T00:00:00Z"
            ),
            ZoneOffset.UTC
        );

    @Test
    void retryablePrimaryFailureUsesFallback() {
        CountingProvider primary =
            failingProvider(
                "PRIMARY",
                AiProviderFailureClass.TIMEOUT,
                true
            );

        CountingProvider fallback =
            successfulProvider("FALLBACK");

        AiExecutionResult result =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(
                            new AiRouteTarget(
                                "FALLBACK",
                                "fallback-model"
                            )
                        ),
                        65_536,
                        20_000
                    )
                ),
                primary,
                fallback
            ).execute(
                command(
                    AiCapability.VISION_PARSE,
                    null
                )
            );

        assertThat(result.status())
            .isEqualTo(AiExecutionStatus.SUCCEEDED);
        assertThat(primary.callCount()).isEqualTo(1);
        assertThat(fallback.callCount()).isEqualTo(1);
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.provenance().provider())
            .isEqualTo("FALLBACK");
    }

    @Test
    void terminalPrimaryFailureDoesNotUseFallback() {
        CountingProvider primary =
            failingProvider(
                "PRIMARY",
                AiProviderFailureClass.INVALID_AUTH,
                false
            );

        CountingProvider fallback =
            successfulProvider("FALLBACK");

        AiExecutionResult result =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(
                            new AiRouteTarget(
                                "FALLBACK",
                                "fallback-model"
                            )
                        ),
                        65_536,
                        20_000
                    )
                ),
                primary,
                fallback
            ).execute(
                command(
                    AiCapability.VISION_PARSE,
                    null
                )
            );

        assertThat(result.status())
            .isEqualTo(
                AiExecutionStatus.FAILED_TERMINAL
            );
        assertThat(result.failureClass())
            .isEqualTo(
                AiProviderFailureClass.INVALID_AUTH
            );
        assertThat(primary.callCount()).isEqualTo(1);
        assertThat(fallback.callCount()).isZero();
    }

    @Test
    void fallbackMarksProvenanceExactlyOnce() {
        CountingProvider primary =
            failingProvider(
                "PRIMARY",
                AiProviderFailureClass.TIMEOUT,
                true
            );

        CountingProvider fallback =
            successfulProvider("FALLBACK");

        RecordingMetrics metrics =
            new RecordingMetrics();

        AiExecutionResult result =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(
                            new AiRouteTarget(
                                "FALLBACK",
                                "fallback-model"
                            )
                        ),
                        65_536,
                        20_000
                    )
                ),
                metrics,
                RecordingUsageRecorder.success(),
                primary,
                fallback
            ).execute(
                command(
                    AiCapability.VISION_PARSE,
                    null
                )
            );

        assertThat(result.status())
            .isEqualTo(AiExecutionStatus.SUCCEEDED);
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.provenance().fallbackUsed())
            .isTrue();
        assertThat(result.output().provenance().fallbackUsed())
            .isTrue();
        assertThat(metrics.fallbackCount)
            .isEqualTo(1);
    }

    @Test
    void disabledCapabilityDoesNotCallProvider() {
        CountingProvider provider =
            successfulProvider("PRIMARY");

        FixedRoutePlanner planner =
            FixedRoutePlanner.disabled(
                AiCapability.VISION_PARSE
            );

        AiExecutionResult result =
            gateway(
                planner,
                provider
            ).execute(
                command(
                    AiCapability.VISION_PARSE,
                    null
                )
            );

        assertThat(result.status())
            .isEqualTo(AiExecutionStatus.DISABLED);
        assertThat(provider.callCount()).isZero();
        assertThat(planner.routePlanCalls).isZero();
    }

    @Test
    void budgetBlockedBeforeProviderCall() {
        CountingProvider provider =
            successfulProvider("PRIMARY");

        AiExecutionResult result =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(),
                        65_536,
                        10_000
                    )
                ),
                provider
            ).execute(
                command(
                    AiCapability.VISION_PARSE,
                    1L
                )
            );

        assertThat(result.status())
            .isEqualTo(
                AiExecutionStatus.BLOCKED_BUDGET
            );
        assertThat(provider.callCount()).isZero();
        assertThat(result.attemptCount()).isZero();
    }

    @Test
    void providerOutputTooLargeIsTerminal() {
        CountingProvider provider =
            successfulProvider(
                "PRIMARY",
                Set.of(AiCapability.VISION_PARSE),
                "too-large-output",
                null,
                10,
                0
            );

        AiExecutionResult result =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(),
                        4,
                        20_000
                    )
                ),
                provider
            ).execute(
                command(
                    AiCapability.VISION_PARSE,
                    null
                )
            );

        assertThat(result.status())
            .isEqualTo(
                AiExecutionStatus.FAILED_TERMINAL
            );
        assertThat(result.failureClass())
            .isEqualTo(
                AiProviderFailureClass.OUTPUT_TOO_LARGE
            );
        assertThat(provider.callCount()).isEqualTo(1);
    }

    @Test
    void providerWithWrongPromptProvenanceIsBlocked() {
        CountingProvider provider =
            successfulProvider(
                "PRIMARY",
                Set.of(AiCapability.VISION_PARSE),
                "{\"schemaVersion\":\"recognition-evidence-v1\"}",
                "wrong-prompt",
                10,
                0
            );

        AiExecutionResult result =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(),
                        65_536,
                        20_000
                    )
                ),
                provider
            ).execute(
                command(
                    AiCapability.VISION_PARSE,
                    null
                )
            );

        assertThat(result.status())
            .isEqualTo(
                AiExecutionStatus.BLOCKED_POLICY
            );
        assertThat(result.failureClass())
            .isEqualTo(
                AiProviderFailureClass.POLICY_BLOCKED
            );
    }

    @Test
    void unregisteredProviderFailsStartup() {
        ConfiguredAiModelGateway gateway =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "MISSING",
                        "missing-model",
                        List.of(),
                        65_536,
                        20_000
                    )
                )
            );

        assertThatThrownBy(
            gateway::validateProviderConfiguration
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessageContaining(
                "not registered"
            )
            .hasMessageContaining(
                "VISION_PARSE"
            );
    }

    @Test
    void duplicateProviderIdFailsConstruction() {
        assertThatThrownBy(() ->
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(),
                        65_536,
                        20_000
                    )
                ),
                successfulProvider("PRIMARY"),
                successfulProvider("primary")
            )
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessageContaining(
                "Duplicate AI provider id"
            );
    }

    @Test
    void unsupportedCapabilityProviderPairFailsStartup() {
        ConfiguredAiModelGateway gateway =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(),
                        65_536,
                        20_000
                    )
                ),
                successfulProvider(
                    "PRIMARY",
                    Set.of(
                        AiCapability.PROBLEM_CLASSIFY
                    ),
                    "{\"schemaVersion\":\"recognition-evidence-v1\"}",
                    null,
                    10,
                    0
                )
            );

        assertThatThrownBy(
            gateway::validateProviderConfiguration
        )
            .isInstanceOf(
                IllegalStateException.class
            )
            .hasMessageContaining(
                "does not support"
            )
            .hasMessageContaining(
                "VISION_PARSE"
            );
    }

    @Test
    void ledgerReservationFailurePreventsProviderCall() {
        CountingProvider provider =
            successfulProvider("PRIMARY");

        RecordingUsageRecorder recorder =
            RecordingUsageRecorder.failingReserve();

        AiExecutionResult result =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(),
                        65_536,
                        20_000
                    )
                ),
                new RecordingMetrics(),
                recorder,
                provider
            ).execute(
                command(
                    AiCapability.VISION_PARSE,
                    null
                )
            );

        assertThat(result.status())
            .isEqualTo(
                AiExecutionStatus.BLOCKED_POLICY
            );
        assertThat(result.failureClass())
            .isEqualTo(
                AiProviderFailureClass.LEDGER_UNAVAILABLE
            );
        assertThat(result.retryable()).isTrue();
        assertThat(provider.callCount()).isZero();
        assertThat(recorder.reservations).isEmpty();
    }

    @Test
    void ledgerCompletionFailureDoesNotReturnFalseSuccess() {
        CountingProvider provider =
            successfulProvider("PRIMARY");

        AiExecutionResult result =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(),
                        65_536,
                        20_000
                    )
                ),
                new RecordingMetrics(),
                RecordingUsageRecorder.failingComplete(),
                provider
            ).execute(
                command(
                    AiCapability.VISION_PARSE,
                    null
                )
            );

        assertThat(provider.callCount()).isEqualTo(1);
        assertThat(result.status())
            .isEqualTo(
                AiExecutionStatus.FAILED_RETRYABLE
            );
        assertThat(result.failureClass())
            .isEqualTo(
                AiProviderFailureClass.LEDGER_UNAVAILABLE
            );
        assertThat(result.output()).isNull();
    }

    @Test
    void gatewayRecordsLatencyOnSuccess() {
        CountingProvider provider =
            successfulProvider(
                "PRIMARY",
                Set.of(AiCapability.VISION_PARSE),
                "{\"schemaVersion\":\"recognition-evidence-v1\"}",
                null,
                10,
                5
            );

        RecordingMetrics metrics =
            new RecordingMetrics();

        AiExecutionResult result =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(),
                        65_536,
                        20_000
                    )
                ),
                metrics,
                RecordingUsageRecorder.success(),
                provider
            ).execute(
                command(
                    AiCapability.VISION_PARSE,
                    null
                )
            );

        assertThat(result.status())
            .isEqualTo(AiExecutionStatus.SUCCEEDED);
        assertThat(metrics.results).hasSize(1);
        assertThat(
            metrics.results.getFirst()
                .providerLatencyMs()
        ).isGreaterThanOrEqualTo(1);
        assertThat(
            metrics.results.getFirst()
                .gatewayLatencyMs()
        ).isGreaterThanOrEqualTo(1);
    }

    @Test
    void gatewayRecordsLatencyOnFailure() {
        CountingProvider provider =
            failingProvider(
                "PRIMARY",
                AiProviderFailureClass.TIMEOUT,
                false,
                5
            );

        RecordingMetrics metrics =
            new RecordingMetrics();

        AiExecutionResult result =
            gateway(
                FixedRoutePlanner.enabled(
                    visionPlan(
                        "PRIMARY",
                        "primary-model",
                        List.of(),
                        65_536,
                        20_000
                    )
                ),
                metrics,
                RecordingUsageRecorder.success(),
                provider
            ).execute(
                command(
                    AiCapability.VISION_PARSE,
                    null
                )
            );

        assertThat(result.status())
            .isEqualTo(
                AiExecutionStatus.FAILED_TERMINAL
            );
        assertThat(metrics.results).hasSize(1);
        assertThat(
            metrics.results.getFirst()
                .providerLatencyMs()
        ).isGreaterThanOrEqualTo(1);
        assertThat(
            metrics.results.getFirst()
                .gatewayLatencyMs()
        ).isGreaterThanOrEqualTo(1);
    }

    @Test
    void futureSolveCapabilityIsDisabled() {
        CountingProvider provider =
            successfulProvider(
                "PRIMARY",
                Set.of(AiCapability.SOLVE),
                "{\"schemaVersion\":\"solve-v1\"}",
                null,
                10,
                0
            );

        AiExecutionResult result =
            gateway(
                FixedRoutePlanner.disabled(
                    AiCapability.SOLVE
                ),
                provider
            ).execute(
                command(
                    AiCapability.SOLVE,
                    null
                )
            );

        assertThat(result.status())
            .isEqualTo(AiExecutionStatus.DISABLED);
        assertThat(provider.callCount()).isZero();
    }

    private static ConfiguredAiModelGateway gateway(
        FixedRoutePlanner planner,
        AiProviderAdapter... providers
    ) {
        return gateway(
            planner,
            new RecordingMetrics(),
            RecordingUsageRecorder.success(),
            providers
        );
    }

    private static ConfiguredAiModelGateway gateway(
        FixedRoutePlanner planner,
        RecordingMetrics metrics,
        RecordingUsageRecorder usageRecorder,
        AiProviderAdapter... providers
    ) {
        return new ConfiguredAiModelGateway(
            planner,
            AiCapabilityRegistry.defaults(),
            List.of(providers),
            usageRecorder,
            metrics,
            CLOCK
        );
    }

    private static AiRoutePlan visionPlan(
        String primaryProvider,
        String primaryModel,
        List<AiRouteTarget> fallbacks,
        int maxResponseBytes,
        long maxCostMicros
    ) {
        return routePlan(
            AiCapability.VISION_PARSE,
            primaryProvider,
            primaryModel,
            fallbacks,
            maxResponseBytes,
            maxCostMicros
        );
    }

    private static AiRoutePlan routePlan(
        AiCapability capability,
        String primaryProvider,
        String primaryModel,
        List<AiRouteTarget> fallbacks,
        int maxResponseBytes,
        long maxCostMicros
    ) {
        return new AiRoutePlan(
            capability,
            routePolicyVersion(capability),
            capability.name()
                .toLowerCase(Locale.ROOT)
                + "-default-v1",
            new AiRouteTarget(
                primaryProvider,
                primaryModel
            ),
            fallbacks,
            promptId(capability),
            promptVersion(capability),
            schemaVersion(capability),
            Duration.ofSeconds(10),
            Math.max(
                1,
                1 + fallbacks.size()
            ),
            maxResponseBytes,
            maxCostMicros,
            "test-pricing-v1",
            AiRoutePlan.CachePolicy.DISABLED,
            AiRoutePlan.StreamingPolicy.DISABLED,
            "NO_TRAINING_BY_DEFAULT",
            "UNSPECIFIED",
            capability.name() + ":PRIMARY",
            null,
            AiRoutePlan.ReleaseStage.LOCAL_ONLY
        );
    }

    private static AiExecutionCommand command(
        AiCapability capability,
        Long maxCostMicros
    ) {
        return new AiExecutionCommand(
            capability,
            new TestCapabilityRequest(),
            new AiExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "correlation-id",
                "trace-id",
                null,
                null,
                Map.of()
            ),
            AiRouteContext.basic(
                capability,
                Duration.ofSeconds(10)
            ),
            "application/json",
            null,
            executable(capability)
                ? promptId(capability)
                : null,
            executable(capability)
                ? promptVersion(capability)
                : null,
            executable(capability)
                ? schemaVersion(capability)
                : null,
            maxCostMicros
        );
    }

    private static CountingProvider successfulProvider(
        String providerId
    ) {
        return successfulProvider(
            providerId,
            Set.of(AiCapability.VISION_PARSE),
            "{\"schemaVersion\":\"recognition-evidence-v1\"}",
            null,
            10,
            0
        );
    }

    private static CountingProvider successfulProvider(
        String providerId,
        Set<AiCapability> supportedCapabilities,
        String rawOutputJson,
        String promptIdOverride,
        long estimatedCostMicros,
        long delayMillis
    ) {
        return new CountingProvider(
            providerId,
            supportedCapabilities,
            null,
            false,
            rawOutputJson,
            promptIdOverride,
            estimatedCostMicros,
            delayMillis
        );
    }

    private static CountingProvider failingProvider(
        String providerId,
        AiProviderFailureClass failureClass,
        boolean retryable
    ) {
        return failingProvider(
            providerId,
            failureClass,
            retryable,
            0
        );
    }

    private static CountingProvider failingProvider(
        String providerId,
        AiProviderFailureClass failureClass,
        boolean retryable,
        long delayMillis
    ) {
        return new CountingProvider(
            providerId,
            Set.of(AiCapability.VISION_PARSE),
            failureClass,
            retryable,
            null,
            null,
            0,
            delayMillis
        );
    }

    private static boolean executable(
        AiCapability capability
    ) {
        return capability == AiCapability.VISION_PARSE
            || capability == AiCapability.PROBLEM_NORMALIZE
            || capability == AiCapability.PROBLEM_CLASSIFY;
    }

    private static String routePolicyVersion(
        AiCapability capability
    ) {
        return switch (capability) {
            case VISION_PARSE -> "vision-route-v1";
            case PROBLEM_NORMALIZE -> "problem-parser-route-v1";
            case PROBLEM_CLASSIFY -> "problem-classifier-route-v1";
            default -> capability.name()
                .toLowerCase(Locale.ROOT)
                + "-route-v1";
        };
    }

    private static String promptId(
        AiCapability capability
    ) {
        return switch (capability) {
            case VISION_PARSE -> "vision-recognition";
            case PROBLEM_NORMALIZE -> "problem-parser";
            case PROBLEM_CLASSIFY -> "problem-classifier";
            default -> capability.name()
                .toLowerCase(Locale.ROOT);
        };
    }

    private static String promptVersion(
        AiCapability capability
    ) {
        return executable(capability)
            ? "v001"
            : "future";
    }

    private static String schemaVersion(
        AiCapability capability
    ) {
        return switch (capability) {
            case VISION_PARSE -> "recognition-evidence-v1";
            case PROBLEM_NORMALIZE -> "problem-parse-v1";
            case PROBLEM_CLASSIFY -> "problem-classification-v1";
            default -> capability.name()
                .toLowerCase(Locale.ROOT)
                + "-v1";
        };
    }

    private record TestCapabilityRequest()
        implements AiCapabilityRequest {
    }

    private static final class FixedRoutePlanner
        implements AiRoutePlanner {

        private final Map<AiCapability, AiRoutePolicy>
            policies;

        private int routePlanCalls;

        private FixedRoutePlanner(
            Map<AiCapability, AiRoutePolicy> policies
        ) {
            this.policies = Map.copyOf(policies);
        }

        static FixedRoutePlanner enabled(
            AiRoutePlan routePlan
        ) {
            EnumMap<AiCapability, AiRoutePolicy> policies =
                disabledPolicies();

            policies.put(
                routePlan.capability(),
                AiRoutePolicy.enabled(routePlan)
            );

            return new FixedRoutePlanner(policies);
        }

        static FixedRoutePlanner disabled(
            AiCapability capability
        ) {
            EnumMap<AiCapability, AiRoutePolicy> policies =
                disabledPolicies();

            policies.put(
                capability,
                AiRoutePolicy.disabled(capability)
            );

            return new FixedRoutePlanner(policies);
        }

        @Override
        public boolean enabled(
            AiCapability capability
        ) {
            AiRoutePolicy policy =
                policies.get(capability);

            return policy != null
                && policy.enabled();
        }

        @Override
        public AiRoutePlan routePlan(
            AiRouteContext context
        ) {
            routePlanCalls++;

            AiRoutePolicy policy =
                policies.get(
                    context.capability()
                );

            if (
                policy == null
                    || !policy.enabled()
            ) {
                throw new AiProviderException(
                    AiProviderFailureClass
                        .CONFIGURATION_DISABLED,
                    false,
                    "AI capability is disabled"
                );
            }

            return policy.routePlan();
        }

        @Override
        public Map<AiCapability, AiRoutePolicy>
        policies() {
            return policies;
        }

        private static EnumMap<
            AiCapability,
            AiRoutePolicy
            > disabledPolicies() {

            EnumMap<AiCapability, AiRoutePolicy> policies =
                new EnumMap<>(
                    AiCapability.class
                );

            for (
                AiCapability capability :
                AiCapability.values()
            ) {
                policies.put(
                    capability,
                    AiRoutePolicy.disabled(
                        capability
                    )
                );
            }

            return policies;
        }
    }

    private static final class CountingProvider
        implements AiProviderAdapter {

        private final String providerId;
        private final Set<AiCapability> supportedCapabilities;
        private final AiProviderFailureClass failureClass;
        private final boolean retryable;
        private final String rawOutputJson;
        private final String promptIdOverride;
        private final long estimatedCostMicros;
        private final long delayMillis;
        private int callCount;

        private CountingProvider(
            String providerId,
            Set<AiCapability> supportedCapabilities,
            AiProviderFailureClass failureClass,
            boolean retryable,
            String rawOutputJson,
            String promptIdOverride,
            long estimatedCostMicros,
            long delayMillis
        ) {
            this.providerId = providerId;
            this.supportedCapabilities =
                Set.copyOf(supportedCapabilities);
            this.failureClass = failureClass;
            this.retryable = retryable;
            this.rawOutputJson = rawOutputJson;
            this.promptIdOverride =
                promptIdOverride;
            this.estimatedCostMicros =
                estimatedCostMicros;
            this.delayMillis = delayMillis;
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public Set<AiCapability>
        supportedCapabilities() {
            return supportedCapabilities;
        }

        @Override
        public AiCapabilityResult execute(
            AiProviderRequest request
        ) {
            callCount++;
            sleep();

            if (failureClass != null) {
                throw new AiProviderException(
                    failureClass,
                    retryable,
                    "provider failed"
                );
            }

            AiRoutePlan routePlan =
                request.routePlan();

            AiRouteTarget target =
                request.target();

            AiProvenance provenance =
                new AiProvenance(
                    target.provider(),
                    target.model(),
                    routePlan.routePolicyVersion(),
                    promptIdOverride == null
                        ? routePlan.promptId()
                        : promptIdOverride,
                    routePlan.promptVersion(),
                    routePlan.schemaVersion(),
                    "request-" + callCount,
                    "response-" + callCount,
                    false
                );

            return new AiVisionParseResult(
                rawOutputJson,
                provenance,
                new AiUsage(
                    1,
                    2,
                    null,
                    1,
                    estimatedCostMicros,
                    "USD",
                    routePlan.pricingVersion()
                ),
                0
            );
        }

        int callCount() {
            return callCount;
        }

        private void sleep() {
            if (delayMillis <= 0) {
                return;
            }

            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                    "test provider interrupted",
                    exception
                );
            }
        }
    }

    private static final class RecordingUsageRecorder
        implements AiUsageRecorder {

        private final boolean failReserve;
        private final boolean failComplete;
        private final List<AiUsageRecord> reservations =
            new ArrayList<>();
        private final List<AiUsageRecord> completions =
            new ArrayList<>();

        private RecordingUsageRecorder(
            boolean failReserve,
            boolean failComplete
        ) {
            this.failReserve = failReserve;
            this.failComplete = failComplete;
        }

        static RecordingUsageRecorder success() {
            return new RecordingUsageRecorder(
                false,
                false
            );
        }

        static RecordingUsageRecorder failingReserve() {
            return new RecordingUsageRecorder(
                true,
                false
            );
        }

        static RecordingUsageRecorder failingComplete() {
            return new RecordingUsageRecorder(
                false,
                true
            );
        }

        @Override
        public void reserve(
            AiUsageRecord record
        ) {
            if (failReserve) {
                throw new IllegalStateException(
                    "ledger unavailable"
                );
            }

            reservations.add(record);
        }

        @Override
        public void complete(
            AiUsageRecord record
        ) {
            if (failComplete) {
                throw new IllegalStateException(
                    "ledger unavailable"
                );
            }

            completions.add(record);
        }
    }

    private static final class RecordingMetrics
        implements AiGatewayMetrics {

        private final List<AiExecutionResult> results =
            new ArrayList<>();

        private int requestCount;
        private int retryCount;
        private int fallbackCount;
        private int blockedCount;
        private int ledgerWriteCount;

        @Override
        public void request(
            AiRoutePlan routePlan
        ) {
            requestCount++;
        }

        @Override
        public void result(
            AiRoutePlan routePlan,
            AiRouteTarget target,
            AiExecutionResult result
        ) {
            results.add(result);
        }

        @Override
        public void retry(
            AiRoutePlan routePlan,
            AiRouteTarget failedTarget
        ) {
            retryCount++;
        }

        @Override
        public void fallback(
            AiRoutePlan routePlan,
            AiRouteTarget target
        ) {
            fallbackCount++;
        }

        @Override
        public void blocked(
            AiCapability capability,
            AiExecutionStatus status
        ) {
            blockedCount++;
        }

        @Override
        public void ledgerWrite(
            AiCapability capability,
            String outcome
        ) {
            ledgerWriteCount++;
        }
    }
}
