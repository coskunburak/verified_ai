package com.verifiedai.problem.application.classification;

import com.verifiedai.problem.domain.model.classification.ClassificationDifficulty;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ProblemClassificationCandidatePolicyTest {

    private ProblemClassificationTestCatalog catalog;
    private ProblemClassificationCandidatePolicy policy;

    @BeforeEach
    void setUp() {
        catalog =
            new ProblemClassificationTestCatalog();

        policy =
            new ProblemClassificationCandidatePolicy(
                catalog
            );
    }

    @Test
    void arithmeticCandidateSetIsBounded() {
        ProblemClassificationCandidates candidates =
            policy.candidates(
                catalog.snapshot(),
                "ARITHMETIC_EXPRESSION",
                "EVALUATE"
            );

        assertThat(
            candidates.primarySkillIds()
        ).containsExactlyInAnyOrder(
            ProblemClassificationTestCatalog
                .INTEGER_OPERATIONS,
            ProblemClassificationTestCatalog
                .FRACTIONS,
            ProblemClassificationTestCatalog
                .PERCENTAGES,
            ProblemClassificationTestCatalog
                .ORDER_OF_OPERATIONS
        );
    }

    @Test
    void algebraCandidateSetIsBounded() {
        ProblemClassificationCandidates candidates =
            policy.candidates(
                catalog.snapshot(),
                "ALGEBRAIC_EXPRESSION",
                "SIMPLIFY"
            );

        assertThat(
            candidates.primarySkillIds()
        ).containsExactlyInAnyOrder(
            ProblemClassificationTestCatalog
                .SIMPLIFY_EXPRESSIONS,
            ProblemClassificationTestCatalog
                .EXPONENT_RULES,
            ProblemClassificationTestCatalog
                .RADICALS
        );
    }

    @Test
    void equationCandidateSetIsBounded() {
        ProblemClassificationCandidates candidates =
            policy.candidates(
                catalog.snapshot(),
                "EQUATION",
                "SOLVE_EQUATION"
            );

        assertThat(
            candidates.primarySkillIds()
        ).containsExactlyInAnyOrder(
            ProblemClassificationTestCatalog
                .LINEAR_ONE_VARIABLE,
            ProblemClassificationTestCatalog
                .LINEAR_SYSTEMS,
            ProblemClassificationTestCatalog
                .QUADRATIC_SOLVING
        );
    }

    @Test
    void inequalityHasSinglePrimaryCandidate() {
        ProblemClassificationCandidates candidates =
            policy.candidates(
                catalog.snapshot(),
                "INEQUALITY",
                "SOLVE_INEQUALITY"
            );

        assertThat(
            candidates.primarySkillIds()
        ).containsExactly(
            ProblemClassificationTestCatalog
                .INEQUALITIES_BASIC
        );
    }

    @Test
    void secondaryWhitelistIsLimitedToSupportedMathTopics() {
        ProblemClassificationCandidates candidates =
            policy.candidates(
                catalog.snapshot(),
                "EQUATION",
                "SOLVE_EQUATION"
            );

        assertThat(
            candidates.secondarySkillIds()
        )
            .contains(
                ProblemClassificationTestCatalog
                    .INTEGER_OPERATIONS,
                ProblemClassificationTestCatalog
                    .SIMPLIFY_EXPRESSIONS,
                ProblemClassificationTestCatalog
                    .LINEAR_ONE_VARIABLE
            )
            .doesNotContain(
                ProblemClassificationTestCatalog
                    .PHYSICS_MOTION
            );
    }

    @Test
    void selectionOutsidePrimaryCandidateSetIsRejected() {
        ProblemClassificationCandidates candidates =
            policy.candidates(
                catalog.snapshot(),
                "EQUATION",
                "SOLVE_EQUATION"
            );

        ValidatedProblemClassification classification =
            new ValidatedProblemClassification(
                ProblemClassificationStatus.CLASSIFIED,
                null,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION,
                ProblemClassificationTestCatalog.MATH,
                ProblemClassificationTestCatalog.ARITHMETIC,
                ProblemClassificationTestCatalog
                    .INTEGER_OPERATIONS,
                List.of(),
                ClassificationDifficulty.EASY
            );

        assertThatThrownBy(() ->
            policy.validateSelection(
                classification,
                candidates
            )
        )
            .isInstanceOf(
                ProblemClassificationCandidateException.class
            )
            .hasMessageContaining(
                "outside the request candidate set"
            );
    }

    @Test
    void secondarySelectionOutsideWhitelistIsRejected() {
        ProblemClassificationCandidates candidates =
            policy.candidates(
                catalog.snapshot(),
                "EQUATION",
                "SOLVE_EQUATION"
            );

        ValidatedProblemClassification classification =
            new ValidatedProblemClassification(
                ProblemClassificationStatus.CLASSIFIED,
                null,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION,
                ProblemClassificationTestCatalog.MATH,
                ProblemClassificationTestCatalog.EQUATIONS,
                ProblemClassificationTestCatalog
                    .LINEAR_ONE_VARIABLE,
                List.of(
                    ProblemClassificationTestCatalog
                        .PHYSICS_MOTION
                ),
                ClassificationDifficulty.MEDIUM
            );

        assertThatThrownBy(() ->
            policy.validateSelection(
                classification,
                candidates
            )
        )
            .isInstanceOf(
                ProblemClassificationCandidateException.class
            );
    }

    @Test
    void unsupportedCombinationCannotCreateCandidates() {
        assertThatThrownBy(() ->
            policy.candidates(
                catalog.snapshot(),
                "EQUATION",
                "EVALUATE"
            )
        )
            .isInstanceOf(
                ProblemClassificationCandidateException.class
            )
            .hasMessageContaining(
                "No active primary classification candidates"
            );
    }

    @Test
    void nonClassifiedOutcomeDoesNotRequireCandidateAuthority() {
        ProblemClassificationCandidates candidates =
            policy.candidates(
                catalog.snapshot(),
                "EQUATION",
                "SOLVE_EQUATION"
            );

        ValidatedProblemClassification classification =
            new ValidatedProblemClassification(
                ProblemClassificationStatus.UNKNOWN,
                null,
                ProblemClassificationTestCatalog
                    .ONTOLOGY_VERSION,
                null,
                null,
                null,
                List.of(),
                null
            );

        policy.validateSelection(
            classification,
            candidates
        );
    }
}
