# Localization, Mathematical Notation, and Accessible Content

## Localization model

UI strings use String Catalogs. Domain codes remain language-independent.

Supported future languages can include English, Turkish, German, Spanish and French, but release should expand only after mathematical terminology is reviewed.

## Math notation versus language

Do not localize canonical math syntax blindly. Localize explanatory terminology while preserving correct notation.

Examples that require care:
- decimal separator,
- thousands separator,
- interval notation conventions,
- derivative naming,
- school-specific terminology.

## AI language behavior

User explanation language comes from LearningProfile. Parser should preserve original problem language and canonical math structure.

## Localized curriculum labels

Skill canonical code:
`MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE`

Localized display names are separate resources/data.

## Accessibility content

Every equation-heavy view should provide meaningful VoiceOver representation where feasible.

Images:
- instructional diagram has descriptive alt semantics,
- decorative content hidden from accessibility tree.

Verification status includes text, not color-only state.

## Translation quality

Machine translation alone is insufficient for critical mathematical terminology. Maintain review glossary per supported language.
