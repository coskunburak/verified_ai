package com.verifiedai.problem.application.classification;

import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.curriculum.application.CurriculumTaxonomySnapshot;
import com.verifiedai.problem.domain.model.classification.ProblemClassificationStatus;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
final class ProblemClassificationCandidatePolicy {

    private static final String INTEGER_OPERATIONS =
        "MATH.ARITHMETIC.INTEGER_OPERATIONS";

    private static final String FRACTIONS =
        "MATH.ARITHMETIC.FRACTIONS";

    private static final String PERCENTAGES =
        "MATH.ARITHMETIC.PERCENTAGES";

    private static final String ORDER_OF_OPERATIONS =
        "MATH.ARITHMETIC.ORDER_OF_OPERATIONS";

    private static final String SIMPLIFY_EXPRESSIONS =
        "MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS";

    private static final String EXPONENT_RULES =
        "MATH.ALGEBRA.EXPONENT_RULES";

    private static final String RADICALS =
        "MATH.ALGEBRA.RADICALS";

    private static final String LINEAR_ONE_VARIABLE =
        "MATH.EQUATIONS.LINEAR_ONE_VARIABLE";

    private static final String LINEAR_SYSTEMS =
        "MATH.EQUATIONS.LINEAR_SYSTEMS";

    private static final String QUADRATIC_SOLVING =
        "MATH.EQUATIONS.QUADRATIC_SOLVING";

    private static final String INEQUALITIES_BASIC =
        "MATH.EQUATIONS.INEQUALITIES_BASIC";

    private final CurriculumTaxonomyCatalog taxonomyCatalog;

    ProblemClassificationCandidatePolicy(
        CurriculumTaxonomyCatalog taxonomyCatalog
    ) {
        this.taxonomyCatalog = taxonomyCatalog;
    }

    ProblemClassificationCandidates candidates(
        CurriculumTaxonomySnapshot snapshot,
        String problemType,
        String taskType
    ) {
        Set<String> primary = new TreeSet<>();

        switch (problemType + ":" + taskType) {
            case "ARITHMETIC_EXPRESSION:EVALUATE",
                 "ARITHMETIC_EXPRESSION:SIMPLIFY" -> {
                addIfActive(primary, INTEGER_OPERATIONS);
                addIfActive(primary, FRACTIONS);
                addIfActive(primary, PERCENTAGES);
                addIfActive(primary, ORDER_OF_OPERATIONS);
            }

            case "ALGEBRAIC_EXPRESSION:EVALUATE",
                 "ALGEBRAIC_EXPRESSION:SIMPLIFY" -> {
                addIfActive(primary, SIMPLIFY_EXPRESSIONS);
                addIfActive(primary, EXPONENT_RULES);
                addIfActive(primary, RADICALS);
            }

            case "EQUATION:SOLVE_EQUATION" -> {
                addIfActive(primary, LINEAR_ONE_VARIABLE);
                addIfActive(primary, LINEAR_SYSTEMS);
                addIfActive(primary, QUADRATIC_SOLVING);
            }

            case "INEQUALITY:SOLVE_INEQUALITY" ->
                addIfActive(primary, INEQUALITIES_BASIC);

            default -> {
                // Eligibility policy handles this before provider execution.
            }
        }

        if (primary.isEmpty()) {
            throw new ProblemClassificationCandidateException(
                "No active primary classification candidates exist"
            );
        }

        Set<String> secondary = new TreeSet<>();

        secondary.addAll(
            taxonomyCatalog.activeSkillIdsForTopic(
                "MATH.ARITHMETIC"
            )
        );

        secondary.addAll(
            taxonomyCatalog.activeSkillIdsForTopic(
                "MATH.ALGEBRA"
            )
        );

        secondary.addAll(
            taxonomyCatalog.activeSkillIdsForTopic(
                "MATH.EQUATIONS"
            )
        );

        secondary.retainAll(snapshot.activeSkillIds());

        return new ProblemClassificationCandidates(
            snapshot.ontologyVersion(),
            List.copyOf(primary),
            List.copyOf(secondary)
        );
    }

    void validateSelection(
        ValidatedProblemClassification classification,
        ProblemClassificationCandidates candidates
    ) {
        if (
            classification.status()
                != ProblemClassificationStatus.CLASSIFIED
        ) {
            return;
        }

        if (
            !candidates.primarySkillIds().contains(
                classification.primarySkillId()
            )
        ) {
            throw new ProblemClassificationCandidateException(
                "AI selected a primary skill outside the request candidate set"
            );
        }

        for (
            String skillId :
            classification.secondarySkillIds()
        ) {
            if (
                !candidates.secondarySkillIds()
                    .contains(skillId)
            ) {
                throw new ProblemClassificationCandidateException(
                    "AI selected a secondary skill outside the request candidate set"
                );
            }
        }
    }

    private void addIfActive(
        Set<String> target,
        String skillId
    ) {
        if (taxonomyCatalog.isActiveSkill(skillId)) {
            target.add(skillId);
        }
    }
}
