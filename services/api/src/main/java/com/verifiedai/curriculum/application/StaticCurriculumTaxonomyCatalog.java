package com.verifiedai.curriculum.application;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class StaticCurriculumTaxonomyCatalog implements CurriculumTaxonomyCatalog {
    private static final CurriculumTaxonomySnapshot SNAPSHOT = new CurriculumTaxonomySnapshot(
        "curriculum-v1-seed",
        Set.of("MATH"),
        Set.of(
            "MATH.ARITHMETIC",
            "MATH.ALGEBRA",
            "MATH.EQUATIONS",
            "MATH.FUNCTIONS",
            "MATH.CALCULUS.LIMITS",
            "MATH.CALCULUS.DIFFERENTIATION",
            "MATH.CALCULUS.INTEGRATION"
        )
    );

    @Override
    public boolean isActiveSubject(String subjectId) {
        return SNAPSHOT.activeSubjectIds().contains(subjectId);
    }

    @Override
    public boolean isActiveTopic(String topicId) {
        return SNAPSHOT.activeTopicIds().contains(topicId);
    }

    @Override
    public CurriculumTaxonomySnapshot snapshot() {
        return SNAPSHOT;
    }
}
