package com.verifiedai.curriculum.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class StaticCurriculumTaxonomyCatalogTest {

    private final StaticCurriculumTaxonomyCatalog catalog =
        new StaticCurriculumTaxonomyCatalog();

    @Test
    void curriculumSeedHasFortySevenActiveSkillsAcrossTwelveTopics() {
        CurriculumTaxonomySnapshot snapshot =
            catalog.snapshot();

        assertThat(
            snapshot.ontologyVersion()
        ).isEqualTo(
            "curriculum-v1-seed"
        );

        assertThat(
            snapshot.activeSubjectIds()
        ).containsExactly(
            "MATH"
        );

        assertThat(
            snapshot.activeTopicIds()
        ).hasSize(12);

        assertThat(
            snapshot.activeSkillIds()
        ).hasSize(47);

        assertThat(
            snapshot.skillToTopicMap()
        ).hasSize(47);
    }

    @Test
    void yks2026_2ExpansionTopicsAndSkillsAreActiveAndMappedToMath() {
        assertThat(catalog.isActiveTopic("MATH.WORD_PROBLEMS")).isTrue();
        assertThat(catalog.isActiveTopic("MATH.SEQUENCES")).isTrue();
        assertThat(catalog.isActiveTopic("MATH.TRIGONOMETRY")).isTrue();
        assertThat(catalog.isActiveTopic("MATH.PROBABILITY")).isTrue();
        assertThat(catalog.isActiveTopic("MATH.GEOMETRY")).isTrue();

        assertThat(catalog.activeSkillIdsForTopic("MATH.WORD_PROBLEMS"))
            .containsExactlyInAnyOrder(
                "MATH.WORD_PROBLEMS.NUMBER_FRACTION",
                "MATH.WORD_PROBLEMS.RATIO_PROPORTION",
                "MATH.WORD_PROBLEMS.PERCENTAGE_PROFIT_LOSS",
                "MATH.WORD_PROBLEMS.MOTION_WORK_AGE",
                "MATH.WORD_PROBLEMS.DATA_GRAPH_INTERPRETATION",
                "MATH.WORD_PROBLEMS.ROUTINE_NONROUTINE_LOGIC"
            );

        assertThat(catalog.activeSkillIdsForTopic("MATH.ALGEBRA"))
            .contains(
                "MATH.ALGEBRA.POLYNOMIALS",
                "MATH.ALGEBRA.COMPLEX_NUMBERS",
                "MATH.ALGEBRA.LOGARITHMS_EXPONENTIAL",
                "MATH.ALGEBRA.ABSOLUTE_VALUE"
            );

        assertThat(catalog.activeSkillIdsForTopic("MATH.SEQUENCES"))
            .containsExactlyInAnyOrder(
                "MATH.SEQUENCES.ARITHMETIC_SEQUENCES",
                "MATH.SEQUENCES.GEOMETRIC_SEQUENCES"
            );

        assertThat(catalog.activeSkillIdsForTopic("MATH.TRIGONOMETRY"))
            .containsExactlyInAnyOrder(
                "MATH.TRIGONOMETRY.IDENTITIES_AND_REDUCTION",
                "MATH.TRIGONOMETRY.SUM_DIFFERENCE_DOUBLE_ANGLE",
                "MATH.TRIGONOMETRY.TRIG_EQUATIONS"
            );

        assertThat(catalog.activeSkillIdsForTopic("MATH.PROBABILITY"))
            .containsExactlyInAnyOrder(
                "MATH.PROBABILITY.PERMUTATION_COMBINATION_BINOMIAL",
                "MATH.PROBABILITY.CONDITIONAL_PROBABILITY"
            );

        assertThat(catalog.activeSkillIdsForTopic("MATH.GEOMETRY"))
            .containsExactlyInAnyOrder(
                "MATH.GEOMETRY.TRIANGLES_SIMILARITY",
                "MATH.GEOMETRY.CIRCLES_AND_POLYGONS",
                "MATH.GEOMETRY.ANALYTIC_LINES_CIRCLES"
            );
    }

    @Test
    void everyActiveSkillResolvesToActiveTopicAndSubject() {
        CurriculumTaxonomySnapshot snapshot =
            catalog.snapshot();

        assertThat(
            snapshot.activeSkillIds()
        ).allSatisfy(skillId -> {
            String topicId =
                catalog.topicForSkill(
                    skillId
                );

            assertThat(topicId)
                .isNotBlank();

            assertThat(
                catalog.isActiveTopic(
                    topicId
                )
            ).isTrue();

            String subjectId =
                catalog.subjectForTopic(
                    topicId
                );

            assertThat(subjectId)
                .isEqualTo("MATH");

            assertThat(
                catalog.isActiveSubject(
                    subjectId
                )
            ).isTrue();
        });
    }

    @Test
    void calculusExistsInOntologyButDoesNotChangeSprint47CanonicalCoverage() {
        CurriculumTaxonomySnapshot snapshot =
            catalog.snapshot();

        assertThat(
            snapshot.activeSkillIds()
        )
            .contains(
                "MATH.CALCULUS.LIMITS.DIRECT_SUBSTITUTION",
                "MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE",
                "MATH.CALCULUS.INTEGRATION.POWER_RULE"
            );
    }
}
