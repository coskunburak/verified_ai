package com.verifiedai.curriculum.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class StaticCurriculumTaxonomyCatalogTest {

    private final StaticCurriculumTaxonomyCatalog catalog =
        new StaticCurriculumTaxonomyCatalog();

    @Test
    void curriculumV1SeedHasExactlyTwentySevenActiveSkills() {
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
        ).hasSize(7);

        assertThat(
            snapshot.activeSkillIds()
        ).hasSize(27);

        assertThat(
            snapshot.skillToTopicMap()
        ).hasSize(27);
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
