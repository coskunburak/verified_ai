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

## Sprint 6.10 implemented speech and locale boundary

Attempt math speech admits the same supported renderer vocabulary, groups unary negation and binary operands, preserves interval endpoint inclusion/exclusion and uses an explicit EN/TR fallback for unsupported math. It never reads a missing internal localization key or substitutes an invented mathematical interpretation. Original visible fallback content remains intact. Chart marks identify function/derivative and approximate coordinates; exact features and domain summaries remain separate. Locale-aware numeric display never changes canonical strings.

Critical prefix inventory: solve, verification, explanation, tutor, attempt, visualization. The owner checker verifies explicit EN/TR units (including plural variants), format argument positions/types, and literal source calls. Count-neutral English copy is used where a numeric label avoids grammatical plurality. Mathematical glossary (owner bilingual review pending):

| English | Turkish |
|---|---|
| Verified / unverified | Doğrulanmış / doğrulanmamış |
| Exact / approximate | Kesin / yaklaşık |
| Domain / excluded values | Tanım kümesi / dışlanan değerler |
| Extraneous root | Yabancı kök |
| Inequality direction | Eşitsizlik yönü |
| Interval / set | Aralık / küme |
| Step reasoning | Adım gerekçesi |
| Hint / answer reveal | İpucu / cevabı gösterme |
| Saved / assessed | Kaydedildi / değerlendirildi |

A saved, structured answer is not automatically assessed or verified. Physical-device mathematical pronunciation and reading order are separate acceptance evidence.
