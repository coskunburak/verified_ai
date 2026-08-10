package com.verifiedai.ai.infrastructure.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.ai.application.AiProblemClassifyRequest;
import com.verifiedai.ai.application.AiProblemClassifyResult;
import com.verifiedai.ai.application.AiProvenance;
import com.verifiedai.ai.application.AiRoutePlan;
import com.verifiedai.ai.application.AiUsage;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
class LocalFixtureProblemClassifyProviderAdapter
    implements ProblemClassifyProviderAdapter {

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    @Override
    public String providerId() {
        return "LOCAL_FIXTURE";
    }

    @Override
    public AiProblemClassifyResult execute(
        AiProblemClassifyRequest request,
        AiRoutePlan routePlan
    ) {
        long started = System.nanoTime();

        String rawJson =
            classify(
                request,
                routePlan.schemaVersion()
            );

        long latencyMs =
            Math.max(
                0,
                (System.nanoTime() - started)
                    / 1_000_000
            );

        return new AiProblemClassifyResult(
            rawJson,
            new AiProvenance(
                providerId(),
                "local-fixture-problem-classifier-v2",
                routePlan.routePolicyVersion(),
                routePlan.promptId(),
                routePlan.promptVersion(),
                routePlan.schemaVersion(),
                null,
                "local-fixture-"
                    + request
                    .canonicalProblemId()
                    .toString()
                    .toLowerCase(
                        Locale.ROOT
                    ),
                false
            ),
            AiUsage.zeroCost(
                routePlan.pricingVersion()
            ),
            latencyMs
        );
    }

    private String classify(
        AiProblemClassifyRequest request,
        String schemaVersion
    ) {
        JsonNode projection =
            readProjection(
                request.classificationProjectionJson()
            );

        String normalizedText =
            projection
                .path("normalizedText")
                .asText("")
                .toLowerCase(Locale.ROOT);

        int statementCount =
            projection
                .path("statementCount")
                .asInt(1);

        if (
            normalizedText.contains(
                "unknown-skill-test"
            )
        ) {
            return classified(
                schemaVersion,
                request.ontologyVersion(),
                "FAKE.NONEXISTENT.SKILL",
                "MEDIUM"
            );
        }

        if (
            normalizedText.contains(
                "ambiguous"
            )
        ) {
            return reviewRequired(
                schemaVersion,
                request.ontologyVersion()
            );
        }

        String key =
            request.problemType()
                + ":"
                + request.taskType();

        return switch (key) {
            case "EQUATION:SOLVE_EQUATION" -> {
                if (statementCount > 1) {
                    yield classified(
                        schemaVersion,
                        request.ontologyVersion(),
                        "MATH.EQUATIONS.LINEAR_SYSTEMS",
                        "MEDIUM"
                    );
                }

                if (
                    normalizedText.contains("^2")
                        || normalizedText.contains(
                        "**2"
                    )
                ) {
                    yield classified(
                        schemaVersion,
                        request.ontologyVersion(),
                        "MATH.EQUATIONS.QUADRATIC_SOLVING",
                        "MEDIUM"
                    );
                }

                yield classified(
                    schemaVersion,
                    request.ontologyVersion(),
                    "MATH.EQUATIONS.LINEAR_ONE_VARIABLE",
                    "EASY"
                );
            }

            case "INEQUALITY:SOLVE_INEQUALITY" ->
                classified(
                    schemaVersion,
                    request.ontologyVersion(),
                    "MATH.EQUATIONS.INEQUALITIES_BASIC",
                    "EASY"
                );

            case "ARITHMETIC_EXPRESSION:EVALUATE",
                 "ARITHMETIC_EXPRESSION:SIMPLIFY" -> {

                if (normalizedText.contains("%")) {
                    yield classified(
                        schemaVersion,
                        request.ontologyVersion(),
                        "MATH.ARITHMETIC.PERCENTAGES",
                        "EASY"
                    );
                }

                if (normalizedText.contains("/")) {
                    yield classified(
                        schemaVersion,
                        request.ontologyVersion(),
                        "MATH.ARITHMETIC.FRACTIONS",
                        "EASY"
                    );
                }

                if (
                    normalizedText.contains("(")
                        || normalizedText.contains(
                        ")"
                    )
                ) {
                    yield classified(
                        schemaVersion,
                        request.ontologyVersion(),
                        "MATH.ARITHMETIC.ORDER_OF_OPERATIONS",
                        "MEDIUM"
                    );
                }

                yield classified(
                    schemaVersion,
                    request.ontologyVersion(),
                    "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                    "EASY"
                );
            }

            case "ALGEBRAIC_EXPRESSION:EVALUATE",
                 "ALGEBRAIC_EXPRESSION:SIMPLIFY" -> {

                if (
                    normalizedText.contains("sqrt")
                ) {
                    yield classified(
                        schemaVersion,
                        request.ontologyVersion(),
                        "MATH.ALGEBRA.RADICALS",
                        "MEDIUM"
                    );
                }

                if (
                    normalizedText.contains("^")
                        || normalizedText.contains("**")
                ) {
                    yield classified(
                        schemaVersion,
                        request.ontologyVersion(),
                        "MATH.ALGEBRA.EXPONENT_RULES",
                        "MEDIUM"
                    );
                }

                yield classified(
                    schemaVersion,
                    request.ontologyVersion(),
                    "MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS",
                    "EASY"
                );
            }

            default ->
                unknown(
                    schemaVersion,
                    request.ontologyVersion()
                );
        };
    }

    private String classified(
        String schemaVersion,
        String ontologyVersion,
        String primarySkillId,
        String difficulty
    ) {
        ObjectNode root =
            base(
                schemaVersion,
                ontologyVersion,
                "CLASSIFIED"
            );

        root.put(
            "primarySkillId",
            primarySkillId
        );

        root.set(
            "secondarySkillIds",
            objectMapper.createArrayNode()
        );

        root.put(
            "difficulty",
            difficulty
        );

        root.putNull("reviewReason");

        return write(root);
    }

    private String reviewRequired(
        String schemaVersion,
        String ontologyVersion
    ) {
        ObjectNode root =
            base(
                schemaVersion,
                ontologyVersion,
                "REVIEW_REQUIRED"
            );

        root.putNull("primarySkillId");

        root.set(
            "secondarySkillIds",
            objectMapper.createArrayNode()
        );

        root.putNull("difficulty");

        root.put(
            "reviewReason",
            "AMBIGUOUS_PRIMARY_SKILL"
        );

        return write(root);
    }

    private String unknown(
        String schemaVersion,
        String ontologyVersion
    ) {
        ObjectNode root =
            base(
                schemaVersion,
                ontologyVersion,
                "UNKNOWN"
            );

        root.putNull("primarySkillId");

        ArrayNode secondary =
            objectMapper.createArrayNode();

        root.set(
            "secondarySkillIds",
            secondary
        );

        root.putNull("difficulty");

        root.put(
            "reviewReason",
            "INSUFFICIENT_SEMANTIC_EVIDENCE"
        );

        return write(root);
    }

    private ObjectNode base(
        String schemaVersion,
        String ontologyVersion,
        String status
    ) {
        ObjectNode root =
            objectMapper.createObjectNode();

        root.put(
            "schemaVersion",
            schemaVersion
        );

        root.put(
            "ontologyVersion",
            ontologyVersion
        );

        root.put(
            "status",
            status
        );

        return root;
    }

    private JsonNode readProjection(
        String json
    ) {
        try {
            return objectMapper.readTree(json);

        } catch (Exception exception) {
            throw new IllegalStateException(
                "Fixture classification projection is invalid",
                exception
            );
        }
    }

    private String write(
        ObjectNode root
    ) {
        try {
            return objectMapper
                .writeValueAsString(root);

        } catch (Exception exception) {
            throw new IllegalStateException(
                "Fixture classification output could not be serialized",
                exception
            );
        }
    }
}
