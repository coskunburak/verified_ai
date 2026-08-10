package com.verifiedai.ai.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemClassifyResult;
import com.verifiedai.ai.application.AiProblemNormalizeRequest;
import com.verifiedai.ai.application.AiProblemNormalizeResult;
import com.verifiedai.ai.application.AiProviderException;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiUsage;
import com.verifiedai.ai.application.AiVisionParseRequest;
import com.verifiedai.ai.application.AiVisionParseResult;
import com.verifiedai.ai.infrastructure.configuration.AiProblemClassifierProperties;
import com.verifiedai.ai.infrastructure.configuration.AiProblemParserProperties;
import com.verifiedai.ai.infrastructure.configuration.AiVisionRecognitionProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class ConfiguredAiModelGatewayTest {

    @Test
    void retryablePrimaryFailureUsesFallbackAndMarksProvenance() {
        ConfiguredAiModelGateway gateway =
            new ConfiguredAiModelGateway(
                properties("PRIMARY", "FALLBACK"),
                parserProperties("UNAVAILABLE", ""),
                classifierProperties("UNAVAILABLE", ""),
                List.of(
                    failingProvider(
                        "PRIMARY",
                        AiProviderFailureClass.TIMEOUT,
                        true
                    ),
                    successfulProvider(
                        "FALLBACK",
                        "fallback-model"
                    )
                ),
                List.of(
                    unavailableProblemProvider()
                ),
                List.of(
                    unavailableClassificationProvider()
                ),
                "local"
            );

        AiVisionParseResult result =
            gateway.executeVisionParse(request());

        assertThat(
            result.provenance().provider()
        ).isEqualTo("FALLBACK");

        assertThat(
            result.provenance().model()
        ).isEqualTo("fallback-model");

        assertThat(
            result.provenance().fallbackUsed()
        ).isTrue();

        assertThat(
            result.rawOutputJson()
        ).contains("recognition-evidence-v1");
    }

    @Test
    void terminalPrimaryFailureDoesNotUseFallback() {
        ConfiguredAiModelGateway gateway =
            new ConfiguredAiModelGateway(
                properties("PRIMARY", "FALLBACK"),
                parserProperties("UNAVAILABLE", ""),
                classifierProperties("UNAVAILABLE", ""),
                List.of(
                    failingProvider(
                        "PRIMARY",
                        AiProviderFailureClass.INVALID_AUTH,
                        false
                    ),
                    successfulProvider(
                        "FALLBACK",
                        "fallback-model"
                    )
                ),
                List.of(
                    unavailableProblemProvider()
                ),
                List.of(
                    unavailableClassificationProvider()
                ),
                "local"
            );

        assertThatThrownBy(
            () -> gateway.executeVisionParse(request())
        )
            .isInstanceOf(AiProviderException.class)
            .satisfies(exception -> {
                AiProviderException providerException =
                    (AiProviderException) exception;

                assertThat(
                    providerException.failureClass()
                ).isEqualTo(
                    AiProviderFailureClass.INVALID_AUTH
                );

                assertThat(
                    providerException.retryable()
                ).isFalse();
            });
    }

    @Test
    void productionConfigurationRejectsLocalFixtureProviderWhenEnabled() {
        ConfiguredAiModelGateway gateway =
            new ConfiguredAiModelGateway(
                properties("LOCAL_FIXTURE", ""),
                parserProperties("UNAVAILABLE", ""),
                classifierProperties("UNAVAILABLE", ""),
                List.of(
                    successfulProvider(
                        "LOCAL_FIXTURE",
                        "local-fixture-vision-v1"
                    )
                ),
                List.of(
                    unavailableProblemProvider()
                ),
                List.of(
                    unavailableClassificationProvider()
                ),
                "production"
            );

        assertThatThrownBy(
            gateway::validateProductionConfiguration
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LOCAL_FIXTURE");
    }

    @Test
    void retryableProblemParserFailureUsesFallbackAndMarksProvenance() {
        ConfiguredAiModelGateway gateway =
            new ConfiguredAiModelGateway(
                properties("UNAVAILABLE", ""),
                parserProperties(
                    "PRIMARY",
                    "FALLBACK"
                ),
                classifierProperties("UNAVAILABLE", ""),
                List.of(
                    successfulProvider(
                        "UNAVAILABLE",
                        "unused"
                    )
                ),
                List.of(
                    failingProblemProvider(
                        "PRIMARY",
                        AiProviderFailureClass.TIMEOUT,
                        true
                    ),
                    successfulProblemProvider(
                        "FALLBACK",
                        "fallback-parser-model"
                    )
                ),
                List.of(
                    unavailableClassificationProvider()
                ),
                "local"
            );

        AiProblemNormalizeResult result =
            gateway.executeProblemNormalize(
                problemRequest()
            );

        assertThat(
            result.provenance().provider()
        ).isEqualTo("FALLBACK");

        assertThat(
            result.provenance().model()
        ).isEqualTo(
            "fallback-parser-model"
        );

        assertThat(
            result.provenance().fallbackUsed()
        ).isTrue();

        assertThat(
            result.rawOutputJson()
        ).contains("problem-parse-v1");
    }

    @Test
    void productionConfigurationRejectsLocalFixtureProblemParserWhenEnabled() {
        ConfiguredAiModelGateway gateway =
            new ConfiguredAiModelGateway(
                properties("UNAVAILABLE", ""),
                parserProperties(
                    "LOCAL_FIXTURE",
                    ""
                ),
                classifierProperties("UNAVAILABLE", ""),
                List.of(
                    successfulProvider(
                        "UNAVAILABLE",
                        "unused"
                    )
                ),
                List.of(
                    successfulProblemProvider(
                        "LOCAL_FIXTURE",
                        "local-fixture-problem-parser-v1"
                    )
                ),
                List.of(
                    unavailableClassificationProvider()
                ),
                "production"
            );

        assertThatThrownBy(
            gateway::validateProductionConfiguration
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LOCAL_FIXTURE");
    }

    @Test
    void retryableProblemClassifierFailureUsesFallbackAndMarksProvenance() {
        ConfiguredAiModelGateway gateway =
            new ConfiguredAiModelGateway(
                properties("UNAVAILABLE", ""),
                parserProperties("UNAVAILABLE", ""),
                classifierProperties(
                    "PRIMARY",
                    "FALLBACK"
                ),
                List.of(
                    successfulProvider(
                        "UNAVAILABLE",
                        "unused"
                    )
                ),
                List.of(
                    unavailableProblemProvider()
                ),
                List.of(
                    failingClassificationProvider(
                        "PRIMARY",
                        AiProviderFailureClass.TIMEOUT,
                        true
                    ),
                    successfulClassificationProvider(
                        "FALLBACK",
                        "fallback-classifier-model"
                    )
                ),
                "local"
            );

        AiProblemClassifyResult result =
            gateway.executeProblemClassify(
                classificationRequest()
            );

        assertThat(
            result.provenance().provider()
        ).isEqualTo("FALLBACK");

        assertThat(
            result.provenance().model()
        ).isEqualTo(
            "fallback-classifier-model"
        );

        assertThat(
            result.provenance().fallbackUsed()
        ).isTrue();

        assertThat(
            result.rawOutputJson()
        ).contains(
            "problem-classification-v1"
        );
    }

    @Test
    void productionConfigurationRejectsLocalFixtureProblemClassifierWhenEnabled() {
        ConfiguredAiModelGateway gateway =
            new ConfiguredAiModelGateway(
                properties("UNAVAILABLE", ""),
                parserProperties("UNAVAILABLE", ""),
                classifierProperties(
                    "LOCAL_FIXTURE",
                    ""
                ),
                List.of(
                    successfulProvider(
                        "UNAVAILABLE",
                        "unused"
                    )
                ),
                List.of(
                    unavailableProblemProvider()
                ),
                List.of(
                    successfulClassificationProvider(
                        "LOCAL_FIXTURE",
                        "local-fixture-problem-classifier-v2"
                    )
                ),
                "production"
            );

        assertThatThrownBy(
            gateway::validateProductionConfiguration
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("LOCAL_FIXTURE")
            .hasMessageContaining("classifier");
    }

    private static AiVisionRecognitionProperties properties(
        String primaryProvider,
        String fallbackProvider
    ) {
        return new AiVisionRecognitionProperties(
            true,
            primaryProvider,
            fallbackProvider,
            "vision-route-v1",
            "vision-recognition",
            "v001",
            "recognition-evidence-v1",
            Duration.ofSeconds(20),
            2,
            65_536,
            20_000,
            "test-pricing-v1"
        );
    }

    private static AiProblemParserProperties parserProperties(
        String primaryProvider,
        String fallbackProvider
    ) {
        return new AiProblemParserProperties(
            true,
            primaryProvider,
            fallbackProvider,
            "problem-parser-route-v1",
            "problem-parser",
            "v001",
            "problem-parse-v1",
            Duration.ofSeconds(20),
            2,
            65_536,
            20_000,
            "test-pricing-v1"
        );
    }

    private static AiProblemClassifierProperties classifierProperties(
        String primaryProvider,
        String fallbackProvider
    ) {
        return new AiProblemClassifierProperties(
            true,
            primaryProvider,
            fallbackProvider,
            "problem-classifier-route-v1",
            "problem-classifier",
            "v001",
            "problem-classification-v1",
            Duration.ofSeconds(15),
            2,
            16_384,
            10_000,
            "test-classifier-pricing-v1"
        );
    }

    private static AiVisionParseRequest request() {
        return new AiVisionParseRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "image/jpeg",
            "image".getBytes(
                StandardCharsets.UTF_8
            ),
            1200,
            900,
            "vision-recognition",
            "v001",
            "recognition-evidence-v1",
            Duration.ofSeconds(20),
            List.of(
                "RESOLUTION:WARNING"
            )
        );
    }

    private static AiProblemNormalizeRequest problemRequest() {
        return new AiProblemNormalizeRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            """
            {
              "schemaVersion":"recognition-evidence-v1",
              "blocks":[
                {
                  "id":"block-1",
                  "text":"x + 1 = 2"
                }
              ],
              "documentUncertainty":[],
              "reviewRequired":false
            }
            """,
            """
            {
              "qualitySignals":[]
            }
            """,
            "problem-parser",
            "v001",
            "problem-parse-v1",
            Duration.ofSeconds(20)
        );
    }

    private static AiProblemClassifyRequest classificationRequest() {
        return new AiProblemClassifyRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "EQUATION",
            "SOLVE_EQUATION",
            """
            {
              "projectionVersion":"problem-classification-projection-v1",
              "canonicalSchemaVersion":"canonical-problem-v1",
              "problemType":"EQUATION",
              "taskType":"SOLVE_EQUATION",
              "normalizedText":"x + 1 = 2",
              "displayLatex":"x + 1 = 2",
              "variables":["x"],
              "statementCount":1,
              "sourceConstraintCount":0,
              "derivedRestrictionCount":0,
              "upstreamReviewRequired":false
            }
            """,
            "curriculum-v1-seed",
            """
            {
              "ontologyVersion":"curriculum-v1-seed",
              "primarySkillIds":[
                "MATH.EQUATIONS.LINEAR_ONE_VARIABLE"
              ],
              "secondarySkillIds":[
                "MATH.EQUATIONS.LINEAR_ONE_VARIABLE"
              ]
            }
            """,
            "problem-classifier",
            "v001",
            "problem-classification-v1",
            Duration.ofSeconds(15)
        );
    }

    private static VisionParseProviderAdapter successfulProvider(
        String providerId,
        String model
    ) {
        return new VisionParseProviderAdapter() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public AiVisionParseResult execute(
                AiVisionParseRequest request,
                AiRoutePlan routePlan
            ) {
                return new AiVisionParseResult(
                    """
                    {
                      "schemaVersion":"recognition-evidence-v1",
                      "blocks":[],
                      "documentUncertainty":[],
                      "reviewRequired":false
                    }
                    """,
                    new AiProvenance(
                        providerId,
                        model,
                        routePlan.routePolicyVersion(),
                        request.promptId(),
                        request.promptVersion(),
                        request.schemaVersion(),
                        "request-id",
                        "response-id",
                        false
                    ),
                    new AiUsage(
                        1,
                        2,
                        1,
                        1,
                        3,
                        "USD",
                        "test-pricing-v1"
                    ),
                    5
                );
            }
        };
    }

    private static VisionParseProviderAdapter failingProvider(
        String providerId,
        AiProviderFailureClass failureClass,
        boolean retryable
    ) {
        return new VisionParseProviderAdapter() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public AiVisionParseResult execute(
                AiVisionParseRequest request,
                AiRoutePlan routePlan
            ) {
                throw new AiProviderException(
                    failureClass,
                    retryable,
                    "provider failed"
                );
            }
        };
    }

    private static ProblemNormalizeProviderAdapter successfulProblemProvider(
        String providerId,
        String model
    ) {
        return new ProblemNormalizeProviderAdapter() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public AiProblemNormalizeResult execute(
                AiProblemNormalizeRequest request,
                AiRoutePlan routePlan
            ) {
                return new AiProblemNormalizeResult(
                    """
                    {
                      "schemaVersion":"problem-parse-v1",
                      "supportStatus":"UNSUPPORTED",
                      "unsupportedReason":"UNSUPPORTED_STRUCTURE",
                      "subjectId":"MATH",
                      "topicId":null,
                      "taskType":null,
                      "problemType":null,
                      "expressions":[],
                      "variables":[],
                      "constraints":[],
                      "assumptions":[],
                      "uncertainty":{
                        "recognition":[],
                        "parse":[],
                        "reviewRequired":false
                      },
                      "sourceEvidenceRefs":[],
                      "visualQualityRisks":[],
                      "reviewRequired":false
                    }
                    """,
                    new AiProvenance(
                        providerId,
                        model,
                        routePlan.routePolicyVersion(),
                        request.promptId(),
                        request.promptVersion(),
                        request.schemaVersion(),
                        "request-id",
                        "response-id",
                        false
                    ),
                    new AiUsage(
                        1,
                        2,
                        null,
                        1,
                        3,
                        "USD",
                        "test-pricing-v1"
                    ),
                    5
                );
            }
        };
    }

    private static ProblemNormalizeProviderAdapter failingProblemProvider(
        String providerId,
        AiProviderFailureClass failureClass,
        boolean retryable
    ) {
        return new ProblemNormalizeProviderAdapter() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public AiProblemNormalizeResult execute(
                AiProblemNormalizeRequest request,
                AiRoutePlan routePlan
            ) {
                throw new AiProviderException(
                    failureClass,
                    retryable,
                    "provider failed"
                );
            }
        };
    }

    private static ProblemNormalizeProviderAdapter unavailableProblemProvider() {
        return failingProblemProvider(
            "UNAVAILABLE",
            AiProviderFailureClass.CONFIGURATION_DISABLED,
            false
        );
    }

    private static ProblemClassifyProviderAdapter successfulClassificationProvider(
        String providerId,
        String model
    ) {
        return new ProblemClassifyProviderAdapter() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public AiProblemClassifyResult execute(
                AiProblemClassifyRequest request,
                AiRoutePlan routePlan
            ) {
                return new AiProblemClassifyResult(
                    """
                    {
                      "schemaVersion":"problem-classification-v1",
                      "ontologyVersion":"curriculum-v1-seed",
                      "status":"CLASSIFIED",
                      "primarySkillId":"MATH.EQUATIONS.LINEAR_ONE_VARIABLE",
                      "secondarySkillIds":[],
                      "difficulty":"EASY",
                      "reviewReason":null
                    }
                    """,
                    new AiProvenance(
                        providerId,
                        model,
                        routePlan.routePolicyVersion(),
                        request.promptId(),
                        request.promptVersion(),
                        request.schemaVersion(),
                        "classification-request-id",
                        "classification-response-id",
                        false
                    ),
                    new AiUsage(
                        10,
                        5,
                        null,
                        1,
                        20,
                        "USD",
                        "test-classifier-pricing-v1"
                    ),
                    4
                );
            }
        };
    }

    private static ProblemClassifyProviderAdapter failingClassificationProvider(
        String providerId,
        AiProviderFailureClass failureClass,
        boolean retryable
    ) {
        return new ProblemClassifyProviderAdapter() {
            @Override
            public String providerId() {
                return providerId;
            }

            @Override
            public AiProblemClassifyResult execute(
                AiProblemClassifyRequest request,
                AiRoutePlan routePlan
            ) {
                throw new AiProviderException(
                    failureClass,
                    retryable,
                    "classification provider failed"
                );
            }
        };
    }

    private static ProblemClassifyProviderAdapter unavailableClassificationProvider() {
        return failingClassificationProvider(
            "UNAVAILABLE",
            AiProviderFailureClass.CONFIGURATION_DISABLED,
            false
        );
    }
}
