# Curriculum, Skill Ontology, and Taxonomy

## Purpose

The skill ontology is the semantic backbone of mastery, mistake analysis, adaptive planning and exam mapping. A model-generated topic label is not enough. The platform requires stable canonical skill identities.

## Canonical hierarchy

Recommended shape:

```text
Subject
└── Domain/Topic
    └── Skill Family
        └── Skill
            └── Optional Micro-skill
```

Example:

```text
MATHEMATICS
└── CALCULUS
    └── DIFFERENTIATION
        ├── POWER_RULE
        ├── PRODUCT_RULE
        ├── QUOTIENT_RULE
        └── CHAIN_RULE
```

## Stable codes

Use semantic codes that survive UI copy and localization:
`MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE`

Never use localized display text as a database identity.

## Skill fields

- id
- code
- subject_id
- parent_topic_id
- canonical_name
- description
- active
- ontology_version
- default_difficulty_band
- verifier_capabilities
- created_at

Localized names/descriptions belong in localization/reference tables or resource bundles.

## Prerequisite edges

A skill may depend on other skills.

Example:
Chain Rule may depend on:
- function composition understanding,
- basic derivative rules.

Edges have type:
- HARD_PREREQUISITE
- SOFT_PREREQUISITE
- SUPPORTING

Planner can use these differently.

## Problem-to-skill mapping

A Problem has:
- one primary skill,
- zero or more secondary skills.

Primary skill is the main capability being tested. Do not assign ten broad skills to every question.

## AI classification

The classifier receives the allowed ontology subset and must return canonical IDs. Unknown/ambiguous classifications trigger fallback or review, not new spontaneous taxonomy values.

## Ontology versioning

Skill taxonomy will evolve. Store ontology version and migration mapping when skills split/merge.

Example:
Old `BASIC_INTEGRATION` might later split into `POWER_RULE_INTEGRATION` and `BASIC_SUBSTITUTION`.

Historical mastery must be migrated or interpreted explicitly rather than silently remapped.

## Exam mapping

Exam definitions map canonical skills to weights. Multiple exams can reuse the same skill ontology.

This avoids duplicating `SAT_CHAIN_RULE`, `A_LEVEL_CHAIN_RULE`, etc. unless their semantics are genuinely different.

## Governance

Adding a new skill requires:
- definition,
- prerequisites,
- examples,
- supported problem types,
- verifier coverage status,
- test/evaluation cases,
- localization keys.

## Phase 1 V1 Ontology Seed

Production V1 starts with a deliberately narrow mathematics ontology. The list below is a seed contract for Phase 2+ schema/contracts and does not imply every skill has full solver/verifier coverage on day one.

### Subject

| Stable ID | Canonical label | V1 status |
|---|---|---|
| `MATH` | Mathematics | Active |

### Topics and initial skill families

| Topic ID | Topic label | Initial skill IDs |
|---|---|---|
| `MATH.ARITHMETIC` | Arithmetic | `MATH.ARITHMETIC.INTEGER_OPERATIONS`, `MATH.ARITHMETIC.FRACTIONS`, `MATH.ARITHMETIC.PERCENTAGES`, `MATH.ARITHMETIC.ORDER_OF_OPERATIONS` |
| `MATH.ALGEBRA` | Algebra | `MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS`, `MATH.ALGEBRA.FACTORING`, `MATH.ALGEBRA.EXPONENT_RULES`, `MATH.ALGEBRA.RADICALS` |
| `MATH.EQUATIONS` | Equations | `MATH.EQUATIONS.LINEAR_ONE_VARIABLE`, `MATH.EQUATIONS.LINEAR_SYSTEMS`, `MATH.EQUATIONS.QUADRATIC_SOLVING`, `MATH.EQUATIONS.INEQUALITIES_BASIC` |
| `MATH.FUNCTIONS` | Functions | `MATH.FUNCTIONS.NOTATION`, `MATH.FUNCTIONS.COMPOSITION`, `MATH.FUNCTIONS.GRAPH_FEATURES`, `MATH.FUNCTIONS.INVERSE_BASIC` |
| `MATH.CALCULUS.LIMITS` | Limits | `MATH.CALCULUS.LIMITS.DIRECT_SUBSTITUTION`, `MATH.CALCULUS.LIMITS.FACTOR_AND_CANCEL`, `MATH.CALCULUS.LIMITS.ONE_SIDED_BASIC` |
| `MATH.CALCULUS.DIFFERENTIATION` | Derivatives | `MATH.CALCULUS.DIFFERENTIATION.POWER_RULE`, `MATH.CALCULUS.DIFFERENTIATION.PRODUCT_RULE`, `MATH.CALCULUS.DIFFERENTIATION.QUOTIENT_RULE`, `MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE`, `MATH.CALCULUS.DIFFERENTIATION.TRIG_BASIC` |
| `MATH.CALCULUS.INTEGRATION` | Basic Integrals | `MATH.CALCULUS.INTEGRATION.POWER_RULE`, `MATH.CALCULUS.INTEGRATION.BASIC_SUBSTITUTION`, `MATH.CALCULUS.INTEGRATION.CONSTANT_OF_INTEGRATION` |

### V1 exclusions

The following are not Production V1 commitments unless a later sprint explicitly promotes them:

- advanced trigonometry beyond basic derivative identities required by calculus examples;
- linear algebra;
- probability and statistics;
- advanced integration techniques;
- proof-heavy mathematics;
- physics/unit-rich word problems;
- arbitrary course/PDF workspace semantics;
- regional exam-specific ontologies beyond mapping hooks.

## Prerequisite Semantics

Prerequisite edges are versioned, directed, and cycle-free.

| Edge rule | Requirement |
|---|---|
| Direction | `from_skill` is the prerequisite; `to_skill` is the dependent skill. |
| Edge type | One of `HARD_PREREQUISITE`, `SOFT_PREREQUISITE`, or `SUPPORTING`. |
| Cycle prevention | Import/admin tooling must reject cycles for active skills in the same ontology version. |
| Historical compatibility | Historical mastery keeps the skill version it was computed against until an explicit migration/mapping interprets it. |
| Deprecation | Deprecated skills remain resolvable for history and analytics; they are not assigned to new problems unless a compatibility policy says so. |

Example seed edges:

| Prerequisite | Dependent skill | Type |
|---|---|---|
| `MATH.ARITHMETIC.ORDER_OF_OPERATIONS` | `MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS` | `HARD_PREREQUISITE` |
| `MATH.ALGEBRA.EXPONENT_RULES` | `MATH.CALCULUS.DIFFERENTIATION.POWER_RULE` | `HARD_PREREQUISITE` |
| `MATH.FUNCTIONS.COMPOSITION` | `MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE` | `HARD_PREREQUISITE` |
| `MATH.EQUATIONS.LINEAR_ONE_VARIABLE` | `MATH.CALCULUS.LIMITS.FACTOR_AND_CANCEL` | `SOFT_PREREQUISITE` |
| `MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE` | `MATH.CALCULUS.INTEGRATION.BASIC_SUBSTITUTION` | `SUPPORTING` |

## Problem-to-Skill Mapping Contract

- A Problem has exactly one primary skill when classification succeeds.
- A Problem may have secondary skills only when they materially support solving or learning diagnosis.
- AI classifiers receive an allowed ontology subset and must return canonical IDs plus ambiguity/unsupported signals.
- Unknown AI labels are rejected or routed to review; they do not create new skills.
- Display labels and localized strings are derived from curriculum resources, not persisted as identifiers.
- Analytics uses canonical skill IDs or privacy-safe bands; it does not receive raw problem text by default.
