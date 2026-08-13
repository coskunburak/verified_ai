package com.verifiedai.problem.application.classification;

import com.verifiedai.problem.domain.model.classification.ClassificationDifficulty;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationReviewReason;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ProblemClassificationValidatorTest {

    private ProblemClassificationValidator validator;

    @BeforeEach
    void setUp() {
        validator =
            new ProblemClassificationValidator(
                new ProblemClassificationTestCatalog()
            );
    }

    @Test
    void classifiedResultDerivesCanonicalTopicAndSubject() {
        ValidatedProblemClassification result =
            validator.validate(
                proposal(
                    ProblemClassificationStatus.CLASSIFIED,
                    ProblemClassificationTestCatalog
                        .QUADRATIC_SOLVING,
                    List.of(
                        ProblemClassificationTestCatalog
                            .SIMPLIFY_EXPRESSIONS
                    ),
                    ClassificationDifficulty.HARD,
                    null
                )
            );

        assertThat(
            result.subjectId()
        ).isEqualTo(
            ProblemClassificationTestCatalog.MATH
        );

        assertThat(
            result.topicId()
        ).isEqualTo(
            ProblemClassificationTestCatalog.EQUATIONS
        );

        assertThat(
            result.primarySkillId()
        ).isEqualTo(
            ProblemClassificationTestCatalog
                .QUADRATIC_SOLVING
        );

        assertThat(
            result.secondarySkillIds()
        ).containsExactly(
            ProblemClassificationTestCatalog
                .SIMPLIFY_EXPRESSIONS
        );
    }

    @Test
    void unknownPrimarySkillIsRejected() {
        assertFailure(
            proposal(
                ProblemClassificationStatus.CLASSIFIED,
                "MATH.UNKNOWN.SKILL",
                List.of(),
                ClassificationDifficulty.MEDIUM,
                null
            ),
            ProblemClassificationValidationFailure
                .PRIMARY_SKILL_UNKNOWN
        );
    }

    @Test
    void classifiedResultRequiresDifficulty() {
        assertFailure(
            proposal(
                ProblemClassificationStatus.CLASSIFIED,
                ProblemClassificationTestCatalog
                    .LINEAR_ONE_VARIABLE,
                List.of(),
                null,
                null
            ),
            ProblemClassificationValidationFailure
                .DIFFICULTY_REQUIRED
        );
    }

    @Test
    void classifiedResultCannotCarryReviewReason() {
        assertFailure(
            proposal(
                ProblemClassificationStatus.CLASSIFIED,
                ProblemClassificationTestCatalog
                    .LINEAR_ONE_VARIABLE,
                List.of(),
                ClassificationDifficulty.MEDIUM,
                ProblemClassificationReviewReason
                    .AMBIGUOUS_PRIMARY_SKILL
            ),
            ProblemClassificationValidationFailure
                .REVIEW_REASON_FORBIDDEN
        );
    }

    @Test
    void duplicateSecondarySkillsAreRejected() {
        assertFailure(
            proposal(
                ProblemClassificationStatus.CLASSIFIED,
                ProblemClassificationTestCatalog
                    .LINEAR_ONE_VARIABLE,
                List.of(
                    ProblemClassificationTestCatalog
                        .EXPONENT_RULES,
                    ProblemClassificationTestCatalog
                        .EXPONENT_RULES
                ),
                ClassificationDifficulty.MEDIUM,
                null
            ),
            ProblemClassificationValidationFailure
                .SECONDARY_SKILL_DUPLICATED
        );
    }

    @Test
    void primarySkillCannotAlsoBeSecondary() {
        assertFailure(
            proposal(
                ProblemClassificationStatus.CLASSIFIED,
                ProblemClassificationTestCatalog
                    .LINEAR_ONE_VARIABLE,
                List.of(
                    ProblemClassificationTestCatalog
                        .LINEAR_ONE_VARIABLE
                ),
                ClassificationDifficulty.MEDIUM,
                null
            ),
            ProblemClassificationValidationFailure
                .PRIMARY_SKILL_DUPLICATED_AS_SECONDARY
        );
    }

    @Test
    void secondarySkillLimitIsEnforced() {
        assertFailure(
            proposal(
                ProblemClassificationStatus.CLASSIFIED,
                ProblemClassificationTestCatalog
                    .LINEAR_ONE_VARIABLE,
                List.of(
                    ProblemClassificationTestCatalog
                        .INTEGER_OPERATIONS,
                    ProblemClassificationTestCatalog
                        .FRACTIONS,
                    ProblemClassificationTestCatalog
                        .PERCENTAGES,
                    ProblemClassificationTestCatalog
                        .ORDER_OF_OPERATIONS,
                    ProblemClassificationTestCatalog
                        .SIMPLIFY_EXPRESSIONS,
                    ProblemClassificationTestCatalog
                        .EXPONENT_RULES
                ),
                ClassificationDifficulty.MEDIUM,
                null
            ),
            ProblemClassificationValidationFailure
                .SECONDARY_SKILL_LIMIT_EXCEEDED
        );
    }

    @Test
    void crossTopicSecondaryWithinSameSubjectIsAllowed() {
        ValidatedProblemClassification result =
            validator.validate(
                proposal(
                    ProblemClassificationStatus.CLASSIFIED,
                    ProblemClassificationTestCatalog
                        .QUADRATIC_SOLVING,
                    List.of(
                        ProblemClassificationTestCatalog
                            .SIMPLIFY_EXPRESSIONS
                    ),
                    ClassificationDifficulty.HARD,
                    null
                )
            );

        assertThat(
            result.secondarySkillIds()
        ).containsExactly(
            ProblemClassificationTestCatalog
                .SIMPLIFY_EXPRESSIONS
        );
    }

    @Test
    void crossSubjectSecondarySkillIsRejected() {
        assertFailure(
            proposal(
                ProblemClassificationStatus.CLASSIFIED,
                ProblemClassificationTestCatalog
                    .LINEAR_ONE_VARIABLE,
                List.of(
                    ProblemClassificationTestCatalog
                        .PHYSICS_MOTION
                ),
                ClassificationDifficulty.MEDIUM,
                null
            ),
            ProblemClassificationValidationFailure
                .SECONDARY_SKILL_INCOMPATIBLE
        );
    }

    @Test
    void reviewRequiredRequiresReason() {
        assertFailure(
            proposal(
                ProblemClassificationStatus.REVIEW_REQUIRED,
                null,
                List.of(),
                null,
                null
            ),
            ProblemClassificationValidationFailure
                .REVIEW_REASON_REQUIRED
        );
    }

    @Test
    void reviewRequiredCannotExposeAuthority() {
        assertFailure(
            proposal(
                ProblemClassificationStatus.REVIEW_REQUIRED,
                ProblemClassificationTestCatalog
                    .LINEAR_ONE_VARIABLE,
                List.of(),
                null,
                ProblemClassificationReviewReason
                    .AMBIGUOUS_PRIMARY_SKILL
            ),
            ProblemClassificationValidationFailure
                .STATUS_SEMANTICS_INVALID
        );
    }

    @Test
    void unknownAllowsOntologyCoverageGap() {
        ValidatedProblemClassification result =
            validator.validate(
                proposal(
                    ProblemClassificationStatus.UNKNOWN,
                    null,
                    List.of(),
                    null,
                    ProblemClassificationReviewReason
                        .ONTOLOGY_COVERAGE_GAP
                )
            );

        assertThat(
            result.status()
        ).isEqualTo(
            ProblemClassificationStatus.UNKNOWN
        );

        assertThat(
            result.primarySkillId()
        ).isNull();
    }

    @Test
    void unknownRejectsAmbiguousPrimarySkillReason() {
        assertFailure(
            proposal(
                ProblemClassificationStatus.UNKNOWN,
                null,
                List.of(),
                null,
                ProblemClassificationReviewReason
                    .AMBIGUOUS_PRIMARY_SKILL
            ),
            ProblemClassificationValidationFailure
                .STATUS_SEMANTICS_INVALID
        );
    }

    @Test
    void unsupportedCannotCarryAuthorityOrReason() {
        assertFailure(
            proposal(
                ProblemClassificationStatus.UNSUPPORTED,
                null,
                List.of(),
                null,
                ProblemClassificationReviewReason
                    .ONTOLOGY_COVERAGE_GAP
            ),
            ProblemClassificationValidationFailure
                .REVIEW_REASON_FORBIDDEN
        );
    }

    @Test
    void ontologyVersionMismatchIsRejected() {
        ProblemClassificationProposal invalid =
            new ProblemClassificationProposal(
                ProblemClassificationContract
                    .SCHEMA_VERSION,
                "curriculum-wrong",
                ProblemClassificationStatus.CLASSIFIED,
                ProblemClassificationTestCatalog
                    .LINEAR_ONE_VARIABLE,
                List.of(),
                ClassificationDifficulty.MEDIUM,
                null
            );

        assertFailure(
            invalid,
            ProblemClassificationValidationFailure
                .ONTOLOGY_VERSION_MISMATCH
        );
    }

    private static ProblemClassificationProposal proposal(
        ProblemClassificationStatus status,
        String primarySkillId,
        List<String> secondarySkillIds,
        ClassificationDifficulty difficulty,
        ProblemClassificationReviewReason reviewReason
    ) {
        return new ProblemClassificationProposal(
            ProblemClassificationContract
                .SCHEMA_VERSION,
            ProblemClassificationTestCatalog
                .ONTOLOGY_VERSION,
            status,
            primarySkillId,
            secondarySkillIds,
            difficulty,
            reviewReason
        );
    }

    private void assertFailure(
        ProblemClassificationProposal proposal,
        ProblemClassificationValidationFailure
            expectedFailure
    ) {
        assertThatThrownBy(() ->
            validator.validate(
                proposal
            )
        )
            .isInstanceOfSatisfying(
                ProblemClassificationValidationException.class,
                exception ->
                    assertThat(
                        exception.failure()
                    ).isEqualTo(
                        expectedFailure
                    )
            );
    }
}
