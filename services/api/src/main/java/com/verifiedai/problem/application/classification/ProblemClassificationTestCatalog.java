package com.verifiedai.problem.application.classification;

import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.curriculum.application.CurriculumTaxonomySnapshot;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class ProblemClassificationTestCatalog
    implements CurriculumTaxonomyCatalog {

    static final String ONTOLOGY_VERSION =
        "curriculum-v1-seed";

    static final String MATH =
        "MATH";

    static final String SCIENCE =
        "SCIENCE";

    static final String ARITHMETIC =
        "MATH.ARITHMETIC";

    static final String ALGEBRA =
        "MATH.ALGEBRA";

    static final String EQUATIONS =
        "MATH.EQUATIONS";

    static final String PHYSICS =
        "SCIENCE.PHYSICS";

    static final String INTEGER_OPERATIONS =
        "MATH.ARITHMETIC.INTEGER_OPERATIONS";

    static final String FRACTIONS =
        "MATH.ARITHMETIC.FRACTIONS";

    static final String PERCENTAGES =
        "MATH.ARITHMETIC.PERCENTAGES";

    static final String ORDER_OF_OPERATIONS =
        "MATH.ARITHMETIC.ORDER_OF_OPERATIONS";

    static final String SIMPLIFY_EXPRESSIONS =
        "MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS";

    static final String EXPONENT_RULES =
        "MATH.ALGEBRA.EXPONENT_RULES";

    static final String RADICALS =
        "MATH.ALGEBRA.RADICALS";

    static final String LINEAR_ONE_VARIABLE =
        "MATH.EQUATIONS.LINEAR_ONE_VARIABLE";

    static final String LINEAR_SYSTEMS =
        "MATH.EQUATIONS.LINEAR_SYSTEMS";

    static final String QUADRATIC_SOLVING =
        "MATH.EQUATIONS.QUADRATIC_SOLVING";

    static final String INEQUALITIES_BASIC =
        "MATH.EQUATIONS.INEQUALITIES_BASIC";

    static final String PHYSICS_MOTION =
        "SCIENCE.PHYSICS.MOTION";

    private final Set<String> subjects =
        Set.of(
            MATH,
            SCIENCE
        );

    private final Set<String> topics =
        Set.of(
            ARITHMETIC,
            ALGEBRA,
            EQUATIONS,
            PHYSICS
        );

    private final Map<String, String> skillToTopic =
        new LinkedHashMap<>();

    private final Map<String, String> topicToSubject =
        Map.of(
            ARITHMETIC,
            MATH,

            ALGEBRA,
            MATH,

            EQUATIONS,
            MATH,

            PHYSICS,
            SCIENCE
        );

    ProblemClassificationTestCatalog() {
        add(
            INTEGER_OPERATIONS,
            ARITHMETIC
        );
        add(
            FRACTIONS,
            ARITHMETIC
        );
        add(
            PERCENTAGES,
            ARITHMETIC
        );
        add(
            ORDER_OF_OPERATIONS,
            ARITHMETIC
        );

        add(
            SIMPLIFY_EXPRESSIONS,
            ALGEBRA
        );
        add(
            EXPONENT_RULES,
            ALGEBRA
        );
        add(
            RADICALS,
            ALGEBRA
        );

        add(
            LINEAR_ONE_VARIABLE,
            EQUATIONS
        );
        add(
            LINEAR_SYSTEMS,
            EQUATIONS
        );
        add(
            QUADRATIC_SOLVING,
            EQUATIONS
        );
        add(
            INEQUALITIES_BASIC,
            EQUATIONS
        );

        add(
            PHYSICS_MOTION,
            PHYSICS
        );
    }

    @Override
    public boolean isActiveSubject(
        String subjectId
    ) {
        return subjects.contains(
            subjectId
        );
    }

    @Override
    public boolean isActiveTopic(
        String topicId
    ) {
        return topics.contains(
            topicId
        );
    }

    @Override
    public boolean isActiveSkill(
        String skillId
    ) {
        return skillToTopic.containsKey(
            skillId
        );
    }

    @Override
    public String topicForSkill(
        String skillId
    ) {
        return skillToTopic.get(
            skillId
        );
    }

    @Override
    public String subjectForTopic(
        String topicId
    ) {
        return topicToSubject.get(
            topicId
        );
    }

    @Override
    public Set<String> activeSkillIdsForTopic(
        String topicId
    ) {
        Set<String> result =
            new LinkedHashSet<>();

        skillToTopic.forEach(
            (skillId, mappedTopic) -> {
                if (
                    mappedTopic.equals(
                        topicId
                    )
                ) {
                    result.add(
                        skillId
                    );
                }
            }
        );

        return Set.copyOf(
            result
        );
    }

    @Override
    public CurriculumTaxonomySnapshot snapshot() {
        return new CurriculumTaxonomySnapshot(
            ONTOLOGY_VERSION,
            subjects,
            topics,
            Set.copyOf(
                skillToTopic.keySet()
            ),
            Map.copyOf(
                skillToTopic
            ),
            topicToSubject
        );
    }

    private void add(
        String skillId,
        String topicId
    ) {
        skillToTopic.put(
            skillId,
            topicId
        );
    }
}
