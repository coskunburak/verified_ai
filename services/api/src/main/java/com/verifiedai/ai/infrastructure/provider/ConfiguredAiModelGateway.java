package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiCapabilityDescriptor;
import com.verifiedai.ai.application.AiCapabilityRegistry;
import com.verifiedai.ai.application.AiCapabilityResult;
import com.verifiedai.ai.application.AiExecutionCommand;
import com.verifiedai.ai.application.AiExecutionContext;
import com.verifiedai.ai.application.AiExecutionResult;
import com.verifiedai.ai.application.AiExecutionStatus;
import com.verifiedai.ai.application.AiGatewayMetrics;
import com.verifiedai.ai.application.AiModelGateway;
import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemClassifyResult;
import com.verifiedai.ai.application.AiProblemNormalizeRequest;
import com.verifiedai.ai.application.AiProblemNormalizeResult;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiRouteContext;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiRoutePlanner;
import com.verifiedai.ai.application.AiRoutePolicy;
import com.verifiedai.ai.application.AiRouteTarget;
import com.verifiedai.ai.application.AiUsageRecord;
import com.verifiedai.ai.application.AiUsageRecorder;
import com.verifiedai.ai.application.AiVisionParseRequest;
import com.verifiedai.ai.application.AiVisionParseResult;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class ConfiguredAiModelGateway
    implements AiModelGateway {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(
            ConfiguredAiModelGateway.class
        );

    private final AiRoutePlanner routePlanner;
    private final AiCapabilityRegistry capabilityRegistry;
    private final Map<String, AiProviderAdapter> providers;
    private final AiUsageRecorder usageRecorder;
    private final AiGatewayMetrics metrics;
    private final Clock clock;

    ConfiguredAiModelGateway(
        AiRoutePlanner routePlanner,
        AiCapabilityRegistry capabilityRegistry,
        List<AiProviderAdapter> providerAdapters,
        AiUsageRecorder usageRecorder,
        AiGatewayMetrics metrics,
        Clock clock
    ) {
        this.routePlanner = routePlanner;
        this.capabilityRegistry =
            capabilityRegistry;
        this.usageRecorder =
            usageRecorder;
        this.metrics = metrics;
        this.clock = clock;

        Map<String, AiProviderAdapter> indexed =
            new HashMap<>();

        for (
            AiProviderAdapter adapter :
            providerAdapters
        ) {
            String id =
                normalizeProvider(
                    adapter.providerId()
                );

            AiProviderAdapter existing =
                indexed.putIfAbsent(
                    id,
                    adapter
                );

            if (existing != null) {
                throw new IllegalStateException(
                    "Duplicate AI provider id: "
                        + id
                );
            }
        }

        this.providers = Map.copyOf(indexed);
    }

    @PostConstruct
    void validateProviderConfiguration() {
        for (
            AiRoutePolicy policy :
            routePlanner
                .policies()
                .values()
        ) {
            if (!policy.enabled()) {
                continue;
            }

            for (
                AiRouteTarget target :
                policy
                    .routePlan()
                    .allTargets()
            ) {
                AiProviderAdapter adapter =
                    providers.get(
                        normalizeProvider(
                            target.provider()
                        )
                    );

                if (adapter == null) {
                    throw new IllegalStateException(
                        "AI provider is not registered: "
                            + target.provider()
                            + " for "
                            + policy.capability()
                    );
                }

                if (
                    !adapter.supports(
                        policy.capability()
                    )
                ) {
                    throw new IllegalStateException(
                        "AI provider "
                            + target.provider()
                            + " does not support "
                            + policy.capability()
                    );
                }
            }
        }
    }

    @Override
    public AiRoutePlan routePlan(
        AiRouteContext context
    ) {
        return routePlanner.routePlan(
            context
        );
    }

    @Override
    public AiExecutionResult execute(
        AiExecutionCommand command
    ) {
        long gatewayStarted =
            System.nanoTime();

        AiCapabilityDescriptor descriptor =
            capabilityRegistry.require(
                command.capability()
            );

        if (
            !routePlanner.enabled(
                command.capability()
            )
        ) {
            metrics.blocked(
                command.capability(),
                AiExecutionStatus.DISABLED
            );

            return failure(
                command,
                null,
                AiExecutionStatus.DISABLED,
                AiProviderFailureClass
                    .CONFIGURATION_DISABLED,
                false,
                0,
                0,
                gatewayStarted,
                null
            );
        }

        AiRoutePlan routePlan =
            routePlanner.routePlan(
                command.routeContext()
            );

        metrics.request(routePlan);

        AiExecutionResult policyFailure =
            validateCommandContract(
                command,
                routePlan,
                gatewayStarted
            );

        if (policyFailure != null) {
            metrics.blocked(
                command.capability(),
                policyFailure.status()
            );

            return policyFailure;
        }

        UUID ledgerRecordId =
            UUID.randomUUID();

        AiUsageRecord ledger =
            AiUsageRecord.started(
                ledgerRecordId,
                command,
                routePlan,
                clock.instant()
            );

        if (
            descriptor.requiresUsageLedger()
        ) {
            try {
                usageRecorder.reserve(
                    ledger
                );

                metrics.ledgerWrite(
                    command.capability(),
                    "RESERVED"
                );
            } catch (RuntimeException exception) {
                metrics.ledgerWrite(
                    command.capability(),
                    "RESERVE_FAILED"
                );

                AiExecutionResult result =
                    failure(
                        command,
                        routePlan,
                        AiExecutionStatus
                            .BLOCKED_POLICY,
                        AiProviderFailureClass
                            .LEDGER_UNAVAILABLE,
                        true,
                        0,
                        0,
                        gatewayStarted,
                        null
                    );

                logResult(
                    command,
                    result,
                    routePlan.primary()
                );

                return result;
            }
        }

        if (
            command.maxCostMicros() != null
                && routePlan.maxCostMicros()
                > command.maxCostMicros()
        ) {
            AiExecutionResult blocked =
                failure(
                    command,
                    routePlan,
                    AiExecutionStatus
                        .BLOCKED_BUDGET,
                    AiProviderFailureClass
                        .BUDGET_EXCEEDED,
                    false,
                    0,
                    0,
                    gatewayStarted,
                    ledgerRecordId
                );

            return finish(
                descriptor,
                command,
                routePlan,
                ledger,
                blocked,
                routePlan.primary()
            );
        }

        List<AiRouteTarget> targets =
            routePlan.allTargets();

        int maximumProviderAttempts =
            Math.min(
                routePlan.maxAttempts(),
                targets.size()
            );

        long providerNanos = 0;
        int attempts = 0;

        AiRouteTarget lastTarget =
            routePlan.primary();

        for (
            int index = 0;
            index < maximumProviderAttempts;
            index++
        ) {
            AiRouteTarget target =
                targets.get(index);

            lastTarget = target;
            attempts++;

            if (index > 0) {
                metrics.fallback(
                    routePlan,
                    target
                );
            }

            long providerStarted =
                System.nanoTime();

            try {
                AiProviderAdapter adapter =
                    requireProvider(
                        command.capability(),
                        target
                    );

                AiCapabilityResult output =
                    adapter.execute(
                        new AiProviderRequest(
                            command,
                            routePlan,
                            target
                        )
                    );

                long callNanos =
                    elapsedNanos(
                        providerStarted
                    );

                providerNanos +=
                    callNanos;

                validateProviderOutput(
                    output,
                    routePlan,
                    target
                );

                if (
                    output
                        .usage()
                        .estimatedCostMicros()
                        > effectiveBudget(
                        command,
                        routePlan
                    )
                ) {
                    AiExecutionResult blocked =
                        failure(
                            command,
                            routePlan,
                            AiExecutionStatus
                                .BLOCKED_BUDGET,
                            AiProviderFailureClass
                                .BUDGET_EXCEEDED,
                            false,
                            attempts,
                            providerNanos,
                            gatewayStarted,
                            ledgerRecordId
                        );

                    return finish(
                        descriptor,
                        command,
                        routePlan,
                        ledger,
                        blocked,
                        target
                    );
                }

                boolean fallbackUsed =
                    index > 0;

                AiProvenance current =
                    output.provenance();

                AiProvenance provenance =
                    new AiProvenance(
                        current.provider(),
                        current.model(),
                        routePlan
                            .routePolicyVersion(),
                        routePlan.promptId(),
                        routePlan.promptVersion(),
                        routePlan.schemaVersion(),
                        current.providerRequestId(),
                        current.providerResponseId(),
                        fallbackUsed
                    );

                AiCapabilityResult normalizedOutput =
                    output.withExecutionMetadata(
                        provenance,
                        millis(callNanos)
                    );

                AiExecutionResult success =
                    new AiExecutionResult(
                        command
                            .executionContext()
                            .operationId(),
                        command.capability(),
                        AiExecutionStatus.SUCCEEDED,
                        normalizedOutput,
                        routePlan,
                        provenance,
                        normalizedOutput.usage(),
                        millis(providerNanos),
                        millis(
                            elapsedNanos(
                                gatewayStarted
                            )
                        ),
                        attempts,
                        fallbackUsed,
                        null,
                        false,
                        ledgerRecordId
                    );

                return finish(
                    descriptor,
                    command,
                    routePlan,
                    ledger,
                    success,
                    target
                );

            } catch (
                AiProviderException exception
            ) {
                providerNanos +=
                    elapsedNanos(
                        providerStarted
                    );

                boolean hasFallback =
                    exception.retryable()
                        && index + 1
                        < maximumProviderAttempts;

                if (hasFallback) {
                    metrics.retry(
                        routePlan,
                        target
                    );
                    continue;
                }

                AiExecutionStatus status =
                    failureStatus(
                        exception,
                        target
                    );

                AiExecutionResult failed =
                    failure(
                        command,
                        routePlan,
                        status,
                        exception.failureClass(),
                        exception.retryable(),
                        attempts,
                        providerNanos,
                        gatewayStarted,
                        ledgerRecordId
                    );

                return finish(
                    descriptor,
                    command,
                    routePlan,
                    ledger,
                    failed,
                    lastTarget
                );

            } catch (
                RuntimeException exception
            ) {
                providerNanos +=
                    elapsedNanos(
                        providerStarted
                    );

                AiExecutionResult failed =
                    failure(
                        command,
                        routePlan,
                        AiExecutionStatus
                            .FAILED_TERMINAL,
                        AiProviderFailureClass.UNKNOWN,
                        false,
                        attempts,
                        providerNanos,
                        gatewayStarted,
                        ledgerRecordId
                    );

                return finish(
                    descriptor,
                    command,
                    routePlan,
                    ledger,
                    failed,
                    lastTarget
                );
            }
        }

        AiExecutionResult failed =
            failure(
                command,
                routePlan,
                AiExecutionStatus.FAILED_TERMINAL,
                AiProviderFailureClass.UNKNOWN,
                false,
                attempts,
                providerNanos,
                gatewayStarted,
                ledgerRecordId
            );

        return finish(
            descriptor,
            command,
            routePlan,
            ledger,
            failed,
            lastTarget
        );
    }

    @Override
    public AiVisionParseResult executeVisionParse(
        AiVisionParseRequest request,
        AiExecutionContext executionContext
    ) {
        AiExecutionResult result =
            execute(
                new AiExecutionCommand(
                    AiCapability.VISION_PARSE,
                    request,
                    executionContext,
                    AiRouteContext.basic(
                        AiCapability.VISION_PARSE,
                        request.timeout()
                    ),
                    request.contentType(),
                    null,
                    request.promptId(),
                    request.promptVersion(),
                    request.schemaVersion(),
                    null
                )
            );

        return requireSuccess(
            result,
            AiVisionParseResult.class
        );
    }

    @Override
    public AiProblemNormalizeResult executeProblemNormalize(
        AiProblemNormalizeRequest request,
        AiExecutionContext executionContext
    ) {
        AiExecutionResult result =
            execute(
                new AiExecutionCommand(
                    AiCapability.PROBLEM_NORMALIZE,
                    request,
                    executionContext,
                    AiRouteContext.basic(
                        AiCapability.PROBLEM_NORMALIZE,
                        request.timeout()
                    ),
                    "application/json",
                    null,
                    request.promptId(),
                    request.promptVersion(),
                    request.schemaVersion(),
                    null
                )
            );

        return requireSuccess(
            result,
            AiProblemNormalizeResult.class
        );
    }

    @Override
    public AiProblemClassifyResult executeProblemClassify(
        AiProblemClassifyRequest request,
        AiExecutionContext executionContext
    ) {
        AiExecutionResult result =
            execute(
                new AiExecutionCommand(
                    AiCapability.PROBLEM_CLASSIFY,
                    request,
                    executionContext,
                    AiRouteContext.basic(
                        AiCapability.PROBLEM_CLASSIFY,
                        request.timeout()
                    ),
                    "application/json",
                    null,
                    request.promptId(),
                    request.promptVersion(),
                    request.schemaVersion(),
                    null
                )
            );

        return requireSuccess(
            result,
            AiProblemClassifyResult.class
        );
    }

    private AiExecutionResult validateCommandContract(
        AiExecutionCommand command,
        AiRoutePlan routePlan,
        long gatewayStarted
    ) {
        if (
            !matches(
                command.expectedPromptId(),
                routePlan.promptId()
            )
                || !matches(
                command.expectedPromptVersion(),
                routePlan.promptVersion()
            )
                || !matches(
                command.expectedOutputSchemaVersion(),
                routePlan.schemaVersion()
            )
        ) {
            return failure(
                command,
                routePlan,
                AiExecutionStatus.BLOCKED_POLICY,
                AiProviderFailureClass.POLICY_BLOCKED,
                false,
                0,
                0,
                gatewayStarted,
                null
            );
        }

        if (
            command.executionContext().deadline()
                != null
                && !clock.instant().isBefore(
                command
                    .executionContext()
                    .deadline()
            )
        ) {
            return failure(
                command,
                routePlan,
                AiExecutionStatus.BLOCKED_POLICY,
                AiProviderFailureClass.POLICY_BLOCKED,
                false,
                0,
                0,
                gatewayStarted,
                null
            );
        }

        return null;
    }

    private void validateProviderOutput(
        AiCapabilityResult output,
        AiRoutePlan routePlan,
        AiRouteTarget target
    ) {
        if (
            output == null
                || output.rawOutputJson() == null
                || output.provenance() == null
                || output.usage() == null
        ) {
            throw new AiProviderException(
                AiProviderFailureClass.SCHEMA_INVALID,
                true,
                "AI provider returned incomplete output"
            );
        }

        int responseBytes =
            output.rawOutputJson()
                .getBytes(
                    StandardCharsets.UTF_8
                )
                .length;

        if (
            responseBytes
                > routePlan.maxResponseBytes()
        ) {
            throw new AiProviderException(
                AiProviderFailureClass
                    .OUTPUT_TOO_LARGE,
                false,
                "AI provider response exceeded configured size limit"
            );
        }

        AiProvenance provenance =
            output.provenance();

        if (
            !normalizeProvider(
                provenance.provider()
            ).equals(
                target.provider()
            )
                || !provenance
                .model()
                .equals(
                    target.model()
                )
                || !routePlan
                .routePolicyVersion()
                .equals(
                    provenance
                        .routePolicyVersion()
                )
                || !routePlan
                .promptId()
                .equals(
                    provenance.promptId()
                )
                || !routePlan
                .promptVersion()
                .equals(
                    provenance.promptVersion()
                )
                || !routePlan
                .schemaVersion()
                .equals(
                    provenance.schemaVersion()
                )
        ) {
            throw new AiProviderException(
                AiProviderFailureClass.POLICY_BLOCKED,
                false,
                "AI provider provenance did not match selected route"
            );
        }
    }

    private AiProviderAdapter requireProvider(
        AiCapability capability,
        AiRouteTarget target
    ) {
        AiProviderAdapter adapter =
            providers.get(
                normalizeProvider(
                    target.provider()
                )
            );

        if (adapter == null) {
            throw new AiProviderException(
                AiProviderFailureClass
                    .PROVIDER_NOT_REGISTERED,
                false,
                "Configured AI provider is not registered"
            );
        }

        if (!adapter.supports(capability)) {
            throw new AiProviderException(
                AiProviderFailureClass
                    .UNSUPPORTED_PAYLOAD,
                false,
                "Configured AI provider does not support capability"
            );
        }

        return adapter;
    }

    private AiExecutionResult finish(
        AiCapabilityDescriptor descriptor,
        AiExecutionCommand command,
        AiRoutePlan routePlan,
        AiUsageRecord ledger,
        AiExecutionResult result,
        AiRouteTarget target
    ) {
        if (
            descriptor.requiresUsageLedger()
        ) {
            try {
                AiUsageRecord completed =
                    ledger.completed(
                        result,
                        target,
                        clock.instant()
                    );

                usageRecorder.complete(
                    completed
                );

                metrics.ledgerWrite(
                    command.capability(),
                    "COMPLETED"
                );

            } catch (
                RuntimeException exception
            ) {
                metrics.ledgerWrite(
                    command.capability(),
                    "COMPLETE_FAILED"
                );

                AiExecutionResult ledgerFailure =
                    new AiExecutionResult(
                        command
                            .executionContext()
                            .operationId(),
                        command.capability(),
                        AiExecutionStatus
                            .FAILED_RETRYABLE,
                        null,
                        routePlan,
                        null,
                        null,
                        result.providerLatencyMs(),
                        result.gatewayLatencyMs(),
                        result.attemptCount(),
                        result.fallbackUsed(),
                        AiProviderFailureClass
                            .LEDGER_UNAVAILABLE,
                        true,
                        result.ledgerRecordId()
                    );

                metrics.result(
                    routePlan,
                    target,
                    ledgerFailure
                );

                logResult(
                    command,
                    ledgerFailure,
                    target
                );

                return ledgerFailure;
            }
        }

        metrics.result(
            routePlan,
            target,
            result
        );

        logResult(
            command,
            result,
            target
        );

        return result;
    }

    private void logResult(
        AiExecutionCommand command,
        AiExecutionResult result,
        AiRouteTarget target
    ) {
        /*
         * Never add raw request/output, user id, problem text,
         * object key, signed URL, prompt body or credentials here.
         */
        LOGGER.info(
            "ai_gateway_result "
                + "operationId={} "
                + "capability={} "
                + "routePolicyVersion={} "
                + "provider={} "
                + "status={} "
                + "failureClass={} "
                + "retryable={} "
                + "fallbackUsed={} "
                + "estimatedCostMicros={} "
                + "gatewayLatencyMs={} "
                + "correlationId={} "
                + "traceId={}",
            command
                .executionContext()
                .operationId(),
            command.capability(),
            result.routePlan() == null
                ? "NONE"
                : result
                .routePlan()
                .routePolicyVersion(),
            target == null
                ? "NONE"
                : target.provider(),
            result.status(),
            result.failureClass(),
            result.retryable(),
            result.fallbackUsed(),
            result.usage() == null
                ? 0
                : result
                .usage()
                .estimatedCostMicros(),
            result.gatewayLatencyMs(),
            command
                .executionContext()
                .correlationId(),
            command
                .executionContext()
                .traceId()
        );
    }

    private AiExecutionResult failure(
        AiExecutionCommand command,
        AiRoutePlan routePlan,
        AiExecutionStatus status,
        AiProviderFailureClass failureClass,
        boolean retryable,
        int attempts,
        long providerNanos,
        long gatewayStarted,
        UUID ledgerRecordId
    ) {
        return new AiExecutionResult(
            command
                .executionContext()
                .operationId(),
            command.capability(),
            status,
            null,
            routePlan,
            null,
            null,
            millis(providerNanos),
            millis(
                elapsedNanos(
                    gatewayStarted
                )
            ),
            attempts,
            attempts > 1,
            failureClass,
            retryable,
            ledgerRecordId
        );
    }

    private AiExecutionStatus failureStatus(
        AiProviderException exception,
        AiRouteTarget target
    ) {
        if (
            exception.failureClass()
                == AiProviderFailureClass
                .PROVIDER_NOT_REGISTERED
        ) {
            return AiExecutionStatus
                .BLOCKED_PROVIDER_UNAVAILABLE;
        }

        if (
            exception.failureClass()
                == AiProviderFailureClass
                .PROVIDER_UNAVAILABLE
                && "UNAVAILABLE".equals(
                target.provider()
            )
        ) {
            return AiExecutionStatus
                .BLOCKED_PROVIDER_UNAVAILABLE;
        }

        if (
            exception.failureClass()
                == AiProviderFailureClass
                .POLICY_BLOCKED
        ) {
            return AiExecutionStatus.BLOCKED_POLICY;
        }

        return exception.retryable()
            ? AiExecutionStatus.FAILED_RETRYABLE
            : AiExecutionStatus.FAILED_TERMINAL;
    }

    private long effectiveBudget(
        AiExecutionCommand command,
        AiRoutePlan routePlan
    ) {
        if (command.maxCostMicros() == null) {
            return routePlan.maxCostMicros();
        }

        return Math.min(
            routePlan.maxCostMicros(),
            command.maxCostMicros()
        );
    }

    private <T extends AiCapabilityResult>
    T requireSuccess(
        AiExecutionResult result,
        Class<T> expectedType
    ) {

        if (
            !result.succeeded()
                || !expectedType.isInstance(
                result.output()
            )
        ) {
            throw new AiProviderException(
                result.failureClass() == null
                    ? AiProviderFailureClass.UNKNOWN
                    : result.failureClass(),
                result.retryable(),
                "AI gateway execution failed with status "
                    + result.status()
            );
        }

        return expectedType.cast(
            result.output()
        );
    }

    private static boolean matches(
        String expected,
        String actual
    ) {
        return expected == null
            || expected.equals(actual);
    }

    private static String normalizeProvider(
        String provider
    ) {
        if (
            provider == null
                || provider.isBlank()
        ) {
            throw new IllegalArgumentException(
                "provider is required"
            );
        }

        return provider
            .trim()
            .toUpperCase(Locale.ROOT);
    }

    private static long elapsedNanos(
        long started
    ) {
        return Math.max(
            0,
            System.nanoTime() - started
        );
    }

    private static long millis(
        long nanos
    ) {
        return Math.max(
            0,
            nanos / 1_000_000
        );
    }
}
