package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiCapabilityResult;
import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemNormalizeRequest;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiVisionParseRequest;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
final class GenericLocalFixtureProviderAdapter
    implements AiProviderAdapter {

    private final LocalFixtureVisionParseProviderAdapter
        visionAdapter;

    private final LocalFixtureProblemNormalizeProviderAdapter
        normalizeAdapter;

    private final LocalFixtureProblemClassifyProviderAdapter
        classifyAdapter;

    GenericLocalFixtureProviderAdapter(
        LocalFixtureVisionParseProviderAdapter visionAdapter,
        LocalFixtureProblemNormalizeProviderAdapter normalizeAdapter,
        LocalFixtureProblemClassifyProviderAdapter classifyAdapter
    ) {
        this.visionAdapter = visionAdapter;
        this.normalizeAdapter = normalizeAdapter;
        this.classifyAdapter = classifyAdapter;
    }

    @Override
    public String providerId() {
        return "LOCAL_FIXTURE";
    }

    @Override
    public Set<AiCapability> supportedCapabilities() {
        return Set.of(
            AiCapability.VISION_PARSE,
            AiCapability.PROBLEM_NORMALIZE,
            AiCapability.PROBLEM_CLASSIFY
        );
    }

    @Override
    public AiCapabilityResult execute(
        AiProviderRequest request
    ) {
        AiCapabilityResult result =
            switch (
                request.command().capability()
                ) {
                case VISION_PARSE ->
                    visionAdapter.execute(
                        requireType(
                            request.command().request(),
                            AiVisionParseRequest.class
                        ),
                        request.routePlan()
                    );

                case PROBLEM_NORMALIZE ->
                    normalizeAdapter.execute(
                        requireType(
                            request.command().request(),
                            AiProblemNormalizeRequest.class
                        ),
                        request.routePlan()
                    );

                case PROBLEM_CLASSIFY ->
                    classifyAdapter.execute(
                        requireType(
                            request.command().request(),
                            AiProblemClassifyRequest.class
                        ),
                        request.routePlan()
                    );

                default ->
                    throw new AiProviderException(
                        AiProviderFailureClass
                            .UNSUPPORTED_PAYLOAD,
                        false,
                        "LOCAL_FIXTURE does not support capability"
                    );
            };

        if (
            result == null
                || result.provenance() == null
        ) {
            return result;
        }

        AiProvenance current =
            result.provenance();

        AiProvenance normalized =
            new AiProvenance(
                request.target().provider(),
                request.target().model(),
                request.routePlan()
                    .routePolicyVersion(),
                request.routePlan().promptId(),
                request.routePlan().promptVersion(),
                request.routePlan().schemaVersion(),
                current.providerRequestId(),
                current.providerResponseId(),
                false
            );

        return result.withExecutionMetadata(
            normalized,
            result.providerLatencyMs()
        );
    }

    private static <T> T requireType(
        Object value,
        Class<T> expectedType
    ) {
        if (!expectedType.isInstance(value)) {
            throw new AiProviderException(
                AiProviderFailureClass
                    .UNSUPPORTED_PAYLOAD,
                false,
                "AI capability request type mismatch"
            );
        }

        return expectedType.cast(value);
    }
}
