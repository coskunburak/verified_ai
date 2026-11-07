package com.verifiedai.mistake;

import java.util.Map;

/** Public, versioned semantics. Categories describe evidence, never a learner's mental intent. */
public final class MistakeTaxonomy {
    public static final String VERSION = "mistake-category-v1";
    public static final String POLICY = "evidence-classifier-v1";
    public enum Category { CONCEPT_ERROR, FORMULA_ERROR, ALGEBRA_ERROR, SIGN_ERROR, CALCULATION_ERROR,
        INTERPRETATION_ERROR, UNIT_ERROR, NOTATION_ERROR, INCOMPLETE_SOLUTION, OTHER_REVIEWED }
    public static final Map<String, Category> CATEGORIES = Map.ofEntries(
        Map.entry("ALG_SIGN_FLIP_DISTRIBUTE", Category.SIGN_ERROR),
        Map.entry("ALG_FRESHMAN_DREAM_POWER", Category.ALGEBRA_ERROR),
        Map.entry("ALG_FRACTION_ADD_DENOM", Category.ALGEBRA_ERROR),
        Map.entry("ALG_ROOT_LOST_BY_DIVISION", Category.ALGEBRA_ERROR),
        Map.entry("DOM_EXTRANEOUS_ROOT_DENOM", Category.CONCEPT_ERROR),
        Map.entry("DOM_EVEN_ROOT_NEGATIVE", Category.CONCEPT_ERROR),
        Map.entry("DOM_LOG_NON_POSITIVE", Category.CONCEPT_ERROR),
        Map.entry("INEQ_NEGATIVE_MULT_NO_FLIP", Category.SIGN_ERROR),
        Map.entry("INEQ_CROSS_MULT_VARIABLE", Category.ALGEBRA_ERROR),
        Map.entry("ABS_SINGLE_BRANCH_ONLY", Category.ALGEBRA_ERROR),
        Map.entry("FUNC_COMPOSITION_ORDER", Category.CONCEPT_ERROR),
        Map.entry("TRIG_PERIOD_OMITTED", Category.CONCEPT_ERROR),
        Map.entry("CALC_LIMIT_INDETERMINATE_SUB", Category.CONCEPT_ERROR),
        Map.entry("CALC_CHAIN_RULE_OMITTED", Category.FORMULA_ERROR),
        Map.entry("CALC_PRODUCT_QUOTIENT_NAIVE", Category.FORMULA_ERROR),
        Map.entry("CALC_INTEGRAL_BOUNDS_SWAPPED", Category.SIGN_ERROR),
        Map.entry("WORD_VARIABLE_MISASSIGNMENT", Category.INTERPRETATION_ERROR));
    // Only direct mappings to existing ontology skills. Product/quotient is deliberately ambiguous.
    public static final Map<String, String> SKILLS = Map.of(
        "ALG_SIGN_FLIP_DISTRIBUTE", "MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS",
        "ALG_FRACTION_ADD_DENOM", "MATH.ARITHMETIC.FRACTIONS",
        "INEQ_NEGATIVE_MULT_NO_FLIP", "MATH.EQUATIONS.INEQUALITIES_BASIC",
        "FUNC_COMPOSITION_ORDER", "MATH.FUNCTIONS.COMPOSITION",
        "CALC_CHAIN_RULE_OMITTED", "MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE");
    private MistakeTaxonomy() { }
}
