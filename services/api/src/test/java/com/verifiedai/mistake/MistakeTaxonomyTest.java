package com.verifiedai.mistake;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

final class MistakeTaxonomyTest {

    @Test
    void containsAllOsym2026_2TrapCodesWithValidCategories() {
        List<String> requiredOsymTrapCodes = List.of(
            "WORD_VARIABLE_MISASSIGNMENT",
            "DOM_LOG_NON_POSITIVE",
            "DOM_EXTRANEOUS_ROOT_DENOM",
            "TRIG_PERIOD_OMITTED",
            "ABS_SINGLE_BRANCH_ONLY",
            "CALC_LIMIT_INDETERMINATE_SUB",
            "CALC_CHAIN_RULE_OMITTED",
            "CALC_INTEGRAL_BOUNDS_SWAPPED"
        );

        assertThat(MistakeTaxonomy.CATEGORIES)
            .containsKeys(requiredOsymTrapCodes.toArray(String[]::new));

        assertThat(MistakeTaxonomy.CATEGORIES.get("WORD_VARIABLE_MISASSIGNMENT"))
            .isEqualTo(MistakeTaxonomy.Category.INTERPRETATION_ERROR);
        assertThat(MistakeTaxonomy.CATEGORIES.get("DOM_LOG_NON_POSITIVE"))
            .isEqualTo(MistakeTaxonomy.Category.CONCEPT_ERROR);
        assertThat(MistakeTaxonomy.CATEGORIES.get("DOM_EXTRANEOUS_ROOT_DENOM"))
            .isEqualTo(MistakeTaxonomy.Category.CONCEPT_ERROR);
        assertThat(MistakeTaxonomy.CATEGORIES.get("TRIG_PERIOD_OMITTED"))
            .isEqualTo(MistakeTaxonomy.Category.CONCEPT_ERROR);
        assertThat(MistakeTaxonomy.CATEGORIES.get("ABS_SINGLE_BRANCH_ONLY"))
            .isEqualTo(MistakeTaxonomy.Category.ALGEBRA_ERROR);
        assertThat(MistakeTaxonomy.CATEGORIES.get("CALC_LIMIT_INDETERMINATE_SUB"))
            .isEqualTo(MistakeTaxonomy.Category.CONCEPT_ERROR);
        assertThat(MistakeTaxonomy.CATEGORIES.get("CALC_CHAIN_RULE_OMITTED"))
            .isEqualTo(MistakeTaxonomy.Category.FORMULA_ERROR);
        assertThat(MistakeTaxonomy.CATEGORIES.get("CALC_INTEGRAL_BOUNDS_SWAPPED"))
            .isEqualTo(MistakeTaxonomy.Category.SIGN_ERROR);
    }
}
