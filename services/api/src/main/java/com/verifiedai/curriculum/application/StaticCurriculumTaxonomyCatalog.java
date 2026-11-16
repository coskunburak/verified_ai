package com.verifiedai.curriculum.application;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class StaticCurriculumTaxonomyCatalog implements CurriculumTaxonomyCatalog {
    private static final CurriculumTaxonomySnapshot SNAPSHOT = new CurriculumTaxonomySnapshot(
        "curriculum-v1-seed",
        Set.of("MATH"),
        Set.of(
            "MATH.ARITHMETIC",
            "MATH.ALGEBRA",
            "MATH.WORD_PROBLEMS",
            "MATH.EQUATIONS",
            "MATH.FUNCTIONS",
            "MATH.SEQUENCES",
            "MATH.TRIGONOMETRY",
            "MATH.PROBABILITY",
            "MATH.GEOMETRY",
            "MATH.CALCULUS.LIMITS",
            "MATH.CALCULUS.DIFFERENTIATION",
            "MATH.CALCULUS.INTEGRATION"
        ),
        Set.of(
            "MATH.ARITHMETIC.INTEGER_OPERATIONS",
            "MATH.ARITHMETIC.FRACTIONS",
            "MATH.ARITHMETIC.PERCENTAGES",
            "MATH.ARITHMETIC.ORDER_OF_OPERATIONS",
            "MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS",
            "MATH.ALGEBRA.FACTORING",
            "MATH.ALGEBRA.EXPONENT_RULES",
            "MATH.ALGEBRA.RADICALS",
            "MATH.ALGEBRA.POLYNOMIALS",
            "MATH.ALGEBRA.COMPLEX_NUMBERS",
            "MATH.ALGEBRA.LOGARITHMS_EXPONENTIAL",
            "MATH.ALGEBRA.ABSOLUTE_VALUE",
            "MATH.WORD_PROBLEMS.NUMBER_FRACTION",
            "MATH.WORD_PROBLEMS.RATIO_PROPORTION",
            "MATH.WORD_PROBLEMS.PERCENTAGE_PROFIT_LOSS",
            "MATH.WORD_PROBLEMS.MOTION_WORK_AGE",
            "MATH.WORD_PROBLEMS.DATA_GRAPH_INTERPRETATION",
            "MATH.WORD_PROBLEMS.ROUTINE_NONROUTINE_LOGIC",
            "MATH.EQUATIONS.LINEAR_ONE_VARIABLE",
            "MATH.EQUATIONS.LINEAR_SYSTEMS",
            "MATH.EQUATIONS.QUADRATIC_SOLVING",
            "MATH.EQUATIONS.INEQUALITIES_BASIC",
            "MATH.FUNCTIONS.NOTATION",
            "MATH.FUNCTIONS.COMPOSITION",
            "MATH.FUNCTIONS.GRAPH_FEATURES",
            "MATH.FUNCTIONS.INVERSE_BASIC",
            "MATH.SEQUENCES.ARITHMETIC_SEQUENCES",
            "MATH.SEQUENCES.GEOMETRIC_SEQUENCES",
            "MATH.TRIGONOMETRY.IDENTITIES_AND_REDUCTION",
            "MATH.TRIGONOMETRY.SUM_DIFFERENCE_DOUBLE_ANGLE",
            "MATH.TRIGONOMETRY.TRIG_EQUATIONS",
            "MATH.PROBABILITY.PERMUTATION_COMBINATION_BINOMIAL",
            "MATH.PROBABILITY.CONDITIONAL_PROBABILITY",
            "MATH.GEOMETRY.TRIANGLES_SIMILARITY",
            "MATH.GEOMETRY.CIRCLES_AND_POLYGONS",
            "MATH.GEOMETRY.ANALYTIC_LINES_CIRCLES",
            "MATH.CALCULUS.LIMITS.DIRECT_SUBSTITUTION",
            "MATH.CALCULUS.LIMITS.FACTOR_AND_CANCEL",
            "MATH.CALCULUS.LIMITS.ONE_SIDED_BASIC",
            "MATH.CALCULUS.DIFFERENTIATION.POWER_RULE",
            "MATH.CALCULUS.DIFFERENTIATION.PRODUCT_RULE",
            "MATH.CALCULUS.DIFFERENTIATION.QUOTIENT_RULE",
            "MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE",
            "MATH.CALCULUS.DIFFERENTIATION.TRIG_BASIC",
            "MATH.CALCULUS.INTEGRATION.POWER_RULE",
            "MATH.CALCULUS.INTEGRATION.BASIC_SUBSTITUTION",
            "MATH.CALCULUS.INTEGRATION.CONSTANT_OF_INTEGRATION"
        ),
        Map.ofEntries(
            Map.entry("MATH.ARITHMETIC.INTEGER_OPERATIONS", "MATH.ARITHMETIC"),
            Map.entry("MATH.ARITHMETIC.FRACTIONS", "MATH.ARITHMETIC"),
            Map.entry("MATH.ARITHMETIC.PERCENTAGES", "MATH.ARITHMETIC"),
            Map.entry("MATH.ARITHMETIC.ORDER_OF_OPERATIONS", "MATH.ARITHMETIC"),
            Map.entry("MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS", "MATH.ALGEBRA"),
            Map.entry("MATH.ALGEBRA.FACTORING", "MATH.ALGEBRA"),
            Map.entry("MATH.ALGEBRA.EXPONENT_RULES", "MATH.ALGEBRA"),
            Map.entry("MATH.ALGEBRA.RADICALS", "MATH.ALGEBRA"),
            Map.entry("MATH.ALGEBRA.POLYNOMIALS", "MATH.ALGEBRA"),
            Map.entry("MATH.ALGEBRA.COMPLEX_NUMBERS", "MATH.ALGEBRA"),
            Map.entry("MATH.ALGEBRA.LOGARITHMS_EXPONENTIAL", "MATH.ALGEBRA"),
            Map.entry("MATH.ALGEBRA.ABSOLUTE_VALUE", "MATH.ALGEBRA"),
            Map.entry("MATH.WORD_PROBLEMS.NUMBER_FRACTION", "MATH.WORD_PROBLEMS"),
            Map.entry("MATH.WORD_PROBLEMS.RATIO_PROPORTION", "MATH.WORD_PROBLEMS"),
            Map.entry("MATH.WORD_PROBLEMS.PERCENTAGE_PROFIT_LOSS", "MATH.WORD_PROBLEMS"),
            Map.entry("MATH.WORD_PROBLEMS.MOTION_WORK_AGE", "MATH.WORD_PROBLEMS"),
            Map.entry("MATH.WORD_PROBLEMS.DATA_GRAPH_INTERPRETATION", "MATH.WORD_PROBLEMS"),
            Map.entry("MATH.WORD_PROBLEMS.ROUTINE_NONROUTINE_LOGIC", "MATH.WORD_PROBLEMS"),
            Map.entry("MATH.EQUATIONS.LINEAR_ONE_VARIABLE", "MATH.EQUATIONS"),
            Map.entry("MATH.EQUATIONS.LINEAR_SYSTEMS", "MATH.EQUATIONS"),
            Map.entry("MATH.EQUATIONS.QUADRATIC_SOLVING", "MATH.EQUATIONS"),
            Map.entry("MATH.EQUATIONS.INEQUALITIES_BASIC", "MATH.EQUATIONS"),
            Map.entry("MATH.FUNCTIONS.NOTATION", "MATH.FUNCTIONS"),
            Map.entry("MATH.FUNCTIONS.COMPOSITION", "MATH.FUNCTIONS"),
            Map.entry("MATH.FUNCTIONS.GRAPH_FEATURES", "MATH.FUNCTIONS"),
            Map.entry("MATH.FUNCTIONS.INVERSE_BASIC", "MATH.FUNCTIONS"),
            Map.entry("MATH.SEQUENCES.ARITHMETIC_SEQUENCES", "MATH.SEQUENCES"),
            Map.entry("MATH.SEQUENCES.GEOMETRIC_SEQUENCES", "MATH.SEQUENCES"),
            Map.entry("MATH.TRIGONOMETRY.IDENTITIES_AND_REDUCTION", "MATH.TRIGONOMETRY"),
            Map.entry("MATH.TRIGONOMETRY.SUM_DIFFERENCE_DOUBLE_ANGLE", "MATH.TRIGONOMETRY"),
            Map.entry("MATH.TRIGONOMETRY.TRIG_EQUATIONS", "MATH.TRIGONOMETRY"),
            Map.entry("MATH.PROBABILITY.PERMUTATION_COMBINATION_BINOMIAL", "MATH.PROBABILITY"),
            Map.entry("MATH.PROBABILITY.CONDITIONAL_PROBABILITY", "MATH.PROBABILITY"),
            Map.entry("MATH.GEOMETRY.TRIANGLES_SIMILARITY", "MATH.GEOMETRY"),
            Map.entry("MATH.GEOMETRY.CIRCLES_AND_POLYGONS", "MATH.GEOMETRY"),
            Map.entry("MATH.GEOMETRY.ANALYTIC_LINES_CIRCLES", "MATH.GEOMETRY"),
            Map.entry("MATH.CALCULUS.LIMITS.DIRECT_SUBSTITUTION", "MATH.CALCULUS.LIMITS"),
            Map.entry("MATH.CALCULUS.LIMITS.FACTOR_AND_CANCEL", "MATH.CALCULUS.LIMITS"),
            Map.entry("MATH.CALCULUS.LIMITS.ONE_SIDED_BASIC", "MATH.CALCULUS.LIMITS"),
            Map.entry("MATH.CALCULUS.DIFFERENTIATION.POWER_RULE", "MATH.CALCULUS.DIFFERENTIATION"),
            Map.entry("MATH.CALCULUS.DIFFERENTIATION.PRODUCT_RULE", "MATH.CALCULUS.DIFFERENTIATION"),
            Map.entry("MATH.CALCULUS.DIFFERENTIATION.QUOTIENT_RULE", "MATH.CALCULUS.DIFFERENTIATION"),
            Map.entry("MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE", "MATH.CALCULUS.DIFFERENTIATION"),
            Map.entry("MATH.CALCULUS.DIFFERENTIATION.TRIG_BASIC", "MATH.CALCULUS.DIFFERENTIATION"),
            Map.entry("MATH.CALCULUS.INTEGRATION.POWER_RULE", "MATH.CALCULUS.INTEGRATION"),
            Map.entry("MATH.CALCULUS.INTEGRATION.BASIC_SUBSTITUTION", "MATH.CALCULUS.INTEGRATION"),
            Map.entry("MATH.CALCULUS.INTEGRATION.CONSTANT_OF_INTEGRATION", "MATH.CALCULUS.INTEGRATION")
        ),
        Map.ofEntries(
            Map.entry("MATH.ARITHMETIC", "MATH"),
            Map.entry("MATH.ALGEBRA", "MATH"),
            Map.entry("MATH.WORD_PROBLEMS", "MATH"),
            Map.entry("MATH.EQUATIONS", "MATH"),
            Map.entry("MATH.FUNCTIONS", "MATH"),
            Map.entry("MATH.SEQUENCES", "MATH"),
            Map.entry("MATH.TRIGONOMETRY", "MATH"),
            Map.entry("MATH.PROBABILITY", "MATH"),
            Map.entry("MATH.GEOMETRY", "MATH"),
            Map.entry("MATH.CALCULUS.LIMITS", "MATH"),
            Map.entry("MATH.CALCULUS.DIFFERENTIATION", "MATH"),
            Map.entry("MATH.CALCULUS.INTEGRATION", "MATH")
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
    public boolean isActiveSkill(String skillId) {
        return SNAPSHOT.activeSkillIds().contains(skillId);
    }

    @Override
    public String topicForSkill(String skillId) {
        return SNAPSHOT.skillToTopicMap().get(skillId);
    }

    @Override
    public String subjectForTopic(String topicId) {
        return SNAPSHOT.topicToSubjectMap().get(topicId);
    }

    @Override
    public Set<String> activeSkillIdsForTopic(String topicId) {
        return SNAPSHOT.skillToTopicMap().entrySet().stream()
            .filter(entry -> entry.getValue().equals(topicId))
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public CurriculumTaxonomySnapshot snapshot() {
        return SNAPSHOT;
    }
}
