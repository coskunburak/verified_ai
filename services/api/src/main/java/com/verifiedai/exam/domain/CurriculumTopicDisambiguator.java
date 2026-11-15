package com.verifiedai.exam.domain;

import com.verifiedai.curriculum.application.CurriculumTaxonomySnapshot;
import com.verifiedai.exam.domain.CandidateProfileModels.DisambiguationCandidate;
import com.verifiedai.exam.domain.CandidateProfileModels.ParsedSyllabusTopicItem;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusExtractionCandidate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic curriculum disambiguator ($0.00 LLM spend) mapping raw OCR
 * syllabus labels to canonical curriculum skill IDs using bilingual TR/EN
 * normalization, leaf-weighted token recall + Jaccard similarity + character
 * trigrams, and a configurable auto-confirmation threshold (default >= 0.70).
 */
public final class CurriculumTopicDisambiguator {

    private static final Map<String, String> BILINGUAL_PHRASE_REPLACEMENTS = new LinkedHashMap<>();

    static {
        BILINGUAL_PHRASE_REPLACEMENTS.put("tam sayilarla islemler", "integer operations");
        BILINGUAL_PHRASE_REPLACEMENTS.put("tam sayilar", "integer operations");
        BILINGUAL_PHRASE_REPLACEMENTS.put("islem onceligi", "order operations");
        BILINGUAL_PHRASE_REPLACEMENTS.put("birinci derece denklemler", "linear equations");
        BILINGUAL_PHRASE_REPLACEMENTS.put("birinci dereceden denklemler", "linear equations");
        BILINGUAL_PHRASE_REPLACEMENTS.put("ikinci derece denklemler", "quadratic equations");
        BILINGUAL_PHRASE_REPLACEMENTS.put("ikinci dereceden denklemler", "quadratic equations");
        BILINGUAL_PHRASE_REPLACEMENTS.put("mutlak deger", "absolute value");
        BILINGUAL_PHRASE_REPLACEMENTS.put("carpanlara ayirma", "factoring polynomials");
        BILINGUAL_PHRASE_REPLACEMENTS.put("analitik geometri", "coordinate geometry");
        BILINGUAL_PHRASE_REPLACEMENTS.put("rasyonel sayilar", "rational numbers");
        BILINGUAL_PHRASE_REPLACEMENTS.put("uslu ifadeler", "exponents");
        BILINGUAL_PHRASE_REPLACEMENTS.put("uslu sayilar", "exponents");
        BILINGUAL_PHRASE_REPLACEMENTS.put("koklu ifadeler", "radicals");
        BILINGUAL_PHRASE_REPLACEMENTS.put("koklu sayilar", "radicals");
        BILINGUAL_PHRASE_REPLACEMENTS.put("tamsayilar", "integer operations");
        BILINGUAL_PHRASE_REPLACEMENTS.put("tamsayi", "integer");
        BILINGUAL_PHRASE_REPLACEMENTS.put("rasyonel", "rational");
        BILINGUAL_PHRASE_REPLACEMENTS.put("kesirler", "fractions");
        BILINGUAL_PHRASE_REPLACEMENTS.put("kesir", "fractions");
        BILINGUAL_PHRASE_REPLACEMENTS.put("ondalik", "decimals");
        BILINGUAL_PHRASE_REPLACEMENTS.put("uslu", "exponents");
        BILINGUAL_PHRASE_REPLACEMENTS.put("koklu", "radicals");
        BILINGUAL_PHRASE_REPLACEMENTS.put("esitsizlikler", "inequalities");
        BILINGUAL_PHRASE_REPLACEMENTS.put("esitsizlik", "inequalities");
        BILINGUAL_PHRASE_REPLACEMENTS.put("denklemler", "equations solving");
        BILINGUAL_PHRASE_REPLACEMENTS.put("denklem", "equations solving");
        BILINGUAL_PHRASE_REPLACEMENTS.put("dogrusal", "linear");
        BILINGUAL_PHRASE_REPLACEMENTS.put("kuadratik", "quadratic");
        BILINGUAL_PHRASE_REPLACEMENTS.put("fonksiyonlar", "functions");
        BILINGUAL_PHRASE_REPLACEMENTS.put("fonksiyon", "functions");
        BILINGUAL_PHRASE_REPLACEMENTS.put("polinomlar", "polynomials");
        BILINGUAL_PHRASE_REPLACEMENTS.put("polinom", "polynomials");
        BILINGUAL_PHRASE_REPLACEMENTS.put("oranti", "proportions");
        BILINGUAL_PHRASE_REPLACEMENTS.put("oran", "ratios");
        BILINGUAL_PHRASE_REPLACEMENTS.put("yuzde", "percentages");
        BILINGUAL_PHRASE_REPLACEMENTS.put("trigonometri", "trigonometry");
        BILINGUAL_PHRASE_REPLACEMENTS.put("logaritma", "logarithms");
        BILINGUAL_PHRASE_REPLACEMENTS.put("turev", "derivatives");
        BILINGUAL_PHRASE_REPLACEMENTS.put("integral", "integrals");
        BILINGUAL_PHRASE_REPLACEMENTS.put("limit", "limits");
        BILINGUAL_PHRASE_REPLACEMENTS.put("olasilik", "probability");
        BILINGUAL_PHRASE_REPLACEMENTS.put("kombinasyon", "combinatorics");
        BILINGUAL_PHRASE_REPLACEMENTS.put("permutasyon", "combinatorics");
        BILINGUAL_PHRASE_REPLACEMENTS.put("kumeler", "sets");
        BILINGUAL_PHRASE_REPLACEMENTS.put("kume", "sets");
        BILINGUAL_PHRASE_REPLACEMENTS.put("ucgenler", "triangles");
        BILINGUAL_PHRASE_REPLACEMENTS.put("ucgen", "triangles");
        BILINGUAL_PHRASE_REPLACEMENTS.put("cember", "circles");
        BILINGUAL_PHRASE_REPLACEMENTS.put("islemler", "operations");
        BILINGUAL_PHRASE_REPLACEMENTS.put("islem", "operations");
    }

    private static final Set<String> GENERIC_STOP_TOKENS = Set.of(
            "math", "topic", "unit", "week", "hafta", "konu", "unite", "and", "ve", "ile", "the", "of", "for", "part", "bolum"
    );

    private static final Set<String> SKILL_FILLER_TOKENS = Set.of(
            "solving", "basics", "concepts", "applications"
    );

    private CurriculumTopicDisambiguator() {
    }

    public static List<ParsedSyllabusTopicItem> disambiguateAll(
            List<SyllabusExtractionCandidate> extracted,
            CurriculumTaxonomySnapshot taxonomy,
            Set<String> examWeightedSkills,
            double similarityThreshold
    ) {
        if (extracted == null || extracted.isEmpty()) {
            return List.of();
        }
        List<ParsedSyllabusTopicItem> result = new ArrayList<>(extracted.size());
        for (int i = 0; i < extracted.size(); i++) {
            result.add(disambiguateSingle(i, extracted.get(i), taxonomy, examWeightedSkills, similarityThreshold));
        }
        return List.copyOf(result);
    }

    public static ParsedSyllabusTopicItem disambiguateSingle(
            int index,
            SyllabusExtractionCandidate item,
            CurriculumTaxonomySnapshot taxonomy,
            Set<String> examWeightedSkills,
            double similarityThreshold
    ) {
        String normalizedLabel = normalizeText(item.rawTopicLabel());
        String translatedLabel = translatePhrases(normalizedLabel);
        Set<String> labelTokens = significantTokens(translatedLabel);
        Set<String> labelTrigrams = charTrigrams(String.join("", labelTokens));

        List<DisambiguationCandidate> ranked = new ArrayList<>();
        for (String skillId : taxonomy.activeSkillIds()) {
            String topicId = taxonomy.skillToTopicMap().getOrDefault(skillId, "MATH.GENERAL");
            double score = computeSimilarity(
                    item.rawTopicLabel(),
                    labelTokens,
                    labelTrigrams,
                    skillId,
                    examWeightedSkills != null && examWeightedSkills.contains(skillId)
            );
            if (score > 0.05d) {
                ranked.add(new DisambiguationCandidate(
                        skillId,
                        topicId,
                        BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP)
                ));
            }
        }

        ranked.sort(Comparator.comparing(DisambiguationCandidate::similarityScore).reversed()
                .thenComparing(DisambiguationCandidate::skillId));
        List<DisambiguationCandidate> top3 = ranked.size() > 3 ? List.copyOf(ranked.subList(0, 3)) : List.copyOf(ranked);

        if (top3.isEmpty()) {
            return new ParsedSyllabusTopicItem(
                    index,
                    item.rawTopicLabel(),
                    item.targetWeek(),
                    item.targetMonth(),
                    normalizeConfidence(item.extractionConfidence()),
                    null,
                    null,
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    false,
                    false,
                    List.of()
            );
        }

        DisambiguationCandidate best = top3.getFirst();
        boolean autoConfirmed = best.similarityScore().doubleValue() >= similarityThreshold;
        return new ParsedSyllabusTopicItem(
                index,
                item.rawTopicLabel(),
                item.targetWeek(),
                item.targetMonth(),
                normalizeConfidence(item.extractionConfidence()),
                autoConfirmed ? best.skillId() : null,
                autoConfirmed ? best.topicId() : null,
                best.similarityScore(),
                autoConfirmed,
                false,
                top3
        );
    }

    private static double computeSimilarity(
            String rawLabel,
            Set<String> labelTokens,
            Set<String> labelTrigrams,
            String skillId,
            boolean inExamBlueprint
    ) {
        if (rawLabel != null && rawLabel.trim().equalsIgnoreCase(skillId)) {
            return 1.0d;
        }
        if (labelTokens.isEmpty()) {
            return 0.0d;
        }

        String[] parts = skillId.split("\\.");
        String leafPart = parts.length > 0 ? parts[parts.length - 1] : skillId;
        String categoryPart = parts.length >= 2 ? parts[1] : "";

        Set<String> leafTokens = significantTokens(normalizeText(leafPart.replace('_', ' ')));
        Set<String> coreLeafTokens = new LinkedHashSet<>(leafTokens);
        coreLeafTokens.removeAll(SKILL_FILLER_TOKENS);
        if (coreLeafTokens.isEmpty()) {
            coreLeafTokens = leafTokens;
        }

        Set<String> fullSkillTokens = new LinkedHashSet<>(coreLeafTokens);
        fullSkillTokens.addAll(significantTokens(normalizeText(categoryPart.replace('_', ' '))));

        long leafOverlap = countMatchedTokens(labelTokens, coreLeafTokens);
        if (leafOverlap == 0) {
            long catOverlap = countMatchedTokens(labelTokens, fullSkillTokens);
            if (catOverlap == 0) {
                return trigramJaccard(labelTrigrams, charTrigrams(String.join("", coreLeafTokens))) * 0.40d;
            }
            // Category-only match without specific skill leaf should never auto-confirm (>= 0.70)
            return Math.min(0.55d, 0.25d + 0.15d * catOverlap);
        }

        double leafRecall = (double) leafOverlap / coreLeafTokens.size();
        long fullOverlap = countMatchedTokens(labelTokens, fullSkillTokens);
        double leafUnion = (double) (labelTokens.size() + coreLeafTokens.size() - Math.min(labelTokens.size(), leafOverlap));
        double leafJaccard = leafUnion > 0 ? (double) leafOverlap / leafUnion : 0.0d;
        double fullJaccard = (double) fullOverlap / (labelTokens.size() + fullSkillTokens.size() - Math.min(labelTokens.size(), fullOverlap));
        double jaccard = Math.max(leafJaccard, fullJaccard);
        double trigram = trigramJaccard(labelTrigrams, charTrigrams(String.join("", coreLeafTokens)));

        double base = (0.55d * leafRecall) + (0.25d * jaccard) + (0.20d * trigram);
        if (inExamBlueprint) {
            base = Math.min(1.0d, base + 0.06d);
        }
        // Penalize multi-topic / ambiguous labels that contain extra unrelated tokens
        if (labelTokens.size() > coreLeafTokens.size() + 2) {
            base *= 0.65d;
        }
        return Math.min(1.0d, Math.max(0.0d, base));
    }

    private static long countMatchedTokens(Set<String> labelTokens, Set<String> targetTokens) {
        long matched = 0;
        for (String target : targetTokens) {
            boolean direct = labelTokens.contains(target);
            boolean prefix = !direct && labelTokens.stream()
                    .anyMatch(lt -> lt.length() >= 4 && target.length() >= 4
                            && (lt.startsWith(target) || target.startsWith(lt)));
            if (direct || prefix) {
                matched++;
            }
        }
        return matched;
    }

    private static BigDecimal normalizeConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return new BigDecimal("0.8500");
        }
        double clamped = Math.max(0.0d, Math.min(1.0d, confidence.doubleValue()));
        return BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP);
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String asciiFolded = text
                .replace('ı', 'i')
                .replace('İ', 'i')
                .replace('ş', 's')
                .replace('Ş', 's')
                .replace('ğ', 'g')
                .replace('Ğ', 'g')
                .replace('ü', 'u')
                .replace('Ü', 'u')
                .replace('ö', 'o')
                .replace('Ö', 'o')
                .replace('ç', 'c')
                .replace('Ç', 'c');
        String decomposed = Normalizer.normalize(asciiFolded, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return decomposed.replaceAll("\\s+", " ");
    }

    private static String translatePhrases(String normalized) {
        if (normalized.isBlank()) {
            return "";
        }
        String out = " " + normalized + " ";
        for (Map.Entry<String, String> entry : BILINGUAL_PHRASE_REPLACEMENTS.entrySet()) {
            String key = " " + entry.getKey() + " ";
            if (out.contains(key)) {
                out = out.replace(key, " " + entry.getValue() + " ");
            }
        }
        return out.trim().replaceAll("\\s+", " ");
    }

    private static Set<String> significantTokens(String normalized) {
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 2 && !GENERIC_STOP_TOKENS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static Set<String> charTrigrams(String s) {
        Set<String> trigrams = new HashSet<>();
        if (s.length() < 3) {
            if (!s.isBlank()) {
                trigrams.add(s);
            }
            return trigrams;
        }
        for (int i = 0; i <= s.length() - 3; i++) {
            trigrams.add(s.substring(i, i + 3));
        }
        return trigrams;
    }

    private static double trigramJaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0d;
        }
        int intersection = 0;
        Map<String, Boolean> lookup = new HashMap<>();
        for (String item : a) {
            lookup.put(item, Boolean.TRUE);
        }
        for (String item : b) {
            if (lookup.containsKey(item)) {
                intersection++;
            }
        }
        int union = a.size() + b.size() - intersection;
        return union == 0 ? 0.0d : (double) intersection / union;
    }
}
