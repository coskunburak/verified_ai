package com.verifiedai.problem.application.classification;

import com.verifiedai.problem.domain.model.classification.ClassificationDifficulty;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ProblemClassificationOutputNormalizerTest {

    private ProblemClassificationOutputNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer =
            new ProblemClassificationOutputNormalizer();
    }

    @Test
    void validMinimalClassifiedOutputIsNormalized() {
        ProblemClassificationProposal result =
            normalizer.normalize(
                classifiedOutput(),
                ProblemClassificationContract
                    .SCHEMA_VERSION,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION
            );

        assertThat(
            result.status()
        ).isEqualTo(
            ProblemClassificationStatus.CLASSIFIED
        );

        assertThat(
            result.primarySkillId()
        ).isEqualTo(
            ProblemClassificationTestCatalog
                .LINEAR_ONE_VARIABLE
        );

        assertThat(
            result.secondarySkillIds()
        ).isEmpty();

        assertThat(
            result.difficulty()
        ).isEqualTo(
            ClassificationDifficulty.MEDIUM
        );

        assertThat(
            result.reviewReason()
        ).isNull();
    }

    @Test
    void providerConfidenceFieldIsRejected() {
        String output =
            classifiedOutput()
                .replace(
                    "\"reviewReason\": null",
                    """
                    "reviewReason": null,
                    "confidence": 0.98
                    """
                );

        assertThatThrownBy(() ->
            normalizer.normalize(
                output,
                ProblemClassificationContract
                    .SCHEMA_VERSION,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION
            )
        )
            .isInstanceOf(
                ProblemClassificationOutputException.class
            )
            .hasMessageContaining(
                "Unknown classification output field: confidence"
            );
    }

    @Test
    void providerReasoningFieldIsRejected() {
        String output =
            classifiedOutput()
                .replace(
                    "\"reviewReason\": null",
                    """
                    "reviewReason": null,
                    "reasoning": "selected by the model"
                    """
                );

        assertThatThrownBy(() ->
            normalizer.normalize(
                output,
                ProblemClassificationContract
                    .SCHEMA_VERSION,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION
            )
        )
            .isInstanceOf(
                ProblemClassificationOutputException.class
            )
            .hasMessageContaining(
                "Unknown classification output field: reasoning"
            );
    }

    @Test
    void missingRequiredFieldIsRejected() {
        String output =
            classifiedOutput()
                .replace(
                    """
                    "reviewReason": null
                    """,
                    ""
                )
                .replace(
                    ",\n}",
                    "\n}"
                );

        assertThatThrownBy(() ->
            normalizer.normalize(
                output,
                ProblemClassificationContract
                    .SCHEMA_VERSION,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION
            )
        )
            .isInstanceOf(
                ProblemClassificationOutputException.class
            );
    }

    @Test
    void wrongSchemaVersionIsRejected() {
        String output =
            classifiedOutput()
                .replace(
                    ProblemClassificationContract
                        .SCHEMA_VERSION,
                    "problem-classification-v999"
                );

        assertThatThrownBy(() ->
            normalizer.normalize(
                output,
                ProblemClassificationContract
                    .SCHEMA_VERSION,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION
            )
        )
            .isInstanceOf(
                ProblemClassificationOutputException.class
            )
            .hasMessageContaining(
                "schema version mismatch"
            );
    }

    @Test
    void wrongOntologyVersionIsRejected() {
        String output =
            classifiedOutput()
                .replace(
                    ProblemClassificationTestCatalog
                        .ONTOLOGY_VERSION,
                    "curriculum-v999"
                );

        assertThatThrownBy(() ->
            normalizer.normalize(
                output,
                ProblemClassificationContract
                    .SCHEMA_VERSION,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION
            )
        )
            .isInstanceOf(
                ProblemClassificationOutputException.class
            )
            .hasMessageContaining(
                "ontology version mismatch"
            );
    }

    @Test
    void duplicateSecondarySkillsAreRejected() {
        String output =
            classifiedOutput()
                .replace(
                    "\"secondarySkillIds\": []",
                    """
                    "secondarySkillIds": [
                      "MATH.ALGEBRA.EXPONENT_RULES",
                      "MATH.ALGEBRA.EXPONENT_RULES"
                    ]
                    """
                );

        assertThatThrownBy(() ->
            normalizer.normalize(
                output,
                ProblemClassificationContract
                    .SCHEMA_VERSION,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION
            )
        )
            .isInstanceOf(
                ProblemClassificationOutputException.class
            )
            .hasMessageContaining(
                "duplicate skill ID"
            );
    }

    @Test
    void moreThanFiveSecondarySkillsAreRejected() {
        String output =
            classifiedOutput()
                .replace(
                    "\"secondarySkillIds\": []",
                    """
                    "secondarySkillIds": [
                      "S1",
                      "S2",
                      "S3",
                      "S4",
                      "S5",
                      "S6"
                    ]
                    """
                );

        assertThatThrownBy(() ->
            normalizer.normalize(
                output,
                ProblemClassificationContract
                    .SCHEMA_VERSION,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION
            )
        )
            .isInstanceOf(
                ProblemClassificationOutputException.class
            )
            .hasMessageContaining(
                "exceeds maximum size"
            );
    }

    @Test
    void unknownStatusEnumIsRejected() {
        String output =
            classifiedOutput()
                .replace(
                    "\"status\": \"CLASSIFIED\"",
                    "\"status\": \"AMBIGUOUS\""
                );

        assertThatThrownBy(() ->
            normalizer.normalize(
                output,
                ProblemClassificationContract
                    .SCHEMA_VERSION,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION
            )
        )
            .isInstanceOf(
                ProblemClassificationOutputException.class
            )
            .hasMessageContaining(
                "unknown enum value"
            );
    }

    @Test
    void nonObjectRootIsRejected() {
        assertThatThrownBy(() ->
            normalizer.normalize(
                "[]",
                ProblemClassificationContract
                    .SCHEMA_VERSION,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION
            )
        )
            .isInstanceOf(
                ProblemClassificationOutputException.class
            )
            .hasMessageContaining(
                "root must be an object"
            );
    }

    private static String classifiedOutput() {
        return """
            {
              "schemaVersion": "problem-classification-v1",
              "ontologyVersion": "curriculum-v1-seed",
              "status": "CLASSIFIED",
              "primarySkillId": "MATH.EQUATIONS.LINEAR_ONE_VARIABLE",
              "secondarySkillIds": [],
              "difficulty": "MEDIUM",
              "reviewReason": null
            }
            """;
    }
}
