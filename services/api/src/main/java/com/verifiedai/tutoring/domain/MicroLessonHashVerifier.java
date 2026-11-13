package com.verifiedai.tutoring.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic SHA-256 canonical hash verifier matching Python's micro_lesson_verifier.py.
 * Enforces INV-MICRO-002 (tamper-evident pre-verified catalog integrity).
 */
@Component
public class MicroLessonHashVerifier {

    private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper();

    public String computeCanonicalHash(MicroLessonCard card) {
        if (card == null
                || card.coreRule() == null
                || card.brokenVsValidCounterexample() == null
                || card.interactiveQuickCheck() == null) {
            throw new IllegalArgumentException("MicroLessonCard missing required canonical sections");
        }

        Map<String, Object> envelope = new TreeMap<>();
        envelope.put("schemaVersion", card.schemaVersion());
        envelope.put("mistakeCode", card.mistakeCode());
        envelope.put("locale", card.locale());
        envelope.put("title", card.title());
        envelope.put("focusBadgeLabel", card.focusBadgeLabel());
        envelope.put("estimatedDurationSeconds", card.estimatedDurationSeconds());
        envelope.put("coreRule", coreRuleMap(card.coreRule()));
        envelope.put("brokenVsValidCounterexample", counterexampleMap(card.brokenVsValidCounterexample()));
        envelope.put("interactiveQuickCheck", quickCheckMap(card.interactiveQuickCheck()));

        try {
            String canonicalJson = CANONICAL_MAPPER.writeValueAsString(envelope);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to compute canonical micro-lesson SHA-256 hash", ex);
        }
    }

    public boolean verify(MicroLessonCard card) {
        if (card == null
                || !"VERIFIED".equals(card.verificationStatus())
                || card.verificationHash() == null
                || !HEX_64.matcher(card.verificationHash()).matches()) {
            return false;
        }
        if (!hasValidBeatDurations(card)) {
            return false;
        }
        String computed = computeCanonicalHash(card);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                card.verificationHash().getBytes(StandardCharsets.UTF_8)
        );
    }

    public boolean hasValidBeatDurations(MicroLessonCard card) {
        if (card == null
                || card.coreRule() == null
                || card.brokenVsValidCounterexample() == null
                || card.interactiveQuickCheck() == null) {
            return false;
        }
        int b1 = card.coreRule().estimatedSeconds();
        int b2 = card.brokenVsValidCounterexample().estimatedSeconds();
        int b3 = card.interactiveQuickCheck().estimatedSeconds();
        int total = card.estimatedDurationSeconds();
        return b1 >= 15 && b1 <= 60
                && b2 >= 15 && b2 <= 60
                && b3 >= 15 && b3 <= 60
                && total >= 60 && total <= 180
                && total == (b1 + b2 + b3);
    }

    private static Map<String, Object> coreRuleMap(MicroLessonCoreRule rule) {
        Map<String, Object> map = new TreeMap<>();
        map.put("ruleTitle", rule.ruleTitle());
        map.put("conciseExplanation", rule.conciseExplanation());
        map.put("canonicalLatexFormula", rule.canonicalLatexFormula());
        map.put("sympyCheckExpr", rule.sympyCheckExpr());
        map.put("estimatedSeconds", rule.estimatedSeconds());
        return map;
    }

    private static Map<String, Object> counterexampleMap(MicroLessonCounterexample ce) {
        Map<String, Object> map = new TreeMap<>();
        map.put("promptLatex", ce.promptLatex());
        map.put("commonTrapLatex", ce.commonTrapLatex());
        map.put("whyTrapFailsExplanation", ce.whyTrapFailsExplanation());
        map.put("verifiedStepLatex", ce.verifiedStepLatex());
        map.put("numericCounterexampleLatex", ce.numericCounterexampleLatex());
        map.put("sympyEquivalenceCheckExpr", ce.sympyEquivalenceCheckExpr());
        map.put("sympyTargetExpr", ce.sympyTargetExpr());
        map.put("sympyValidExpr", ce.sympyValidExpr());
        map.put("sympyTrapExpr", ce.sympyTrapExpr());
        map.put("counterexampleBindings", canonicalizeObject(ce.counterexampleBindings() == null ? Map.of() : ce.counterexampleBindings()));
        map.put("estimatedSeconds", ce.estimatedSeconds());
        return map;
    }

    private static Map<String, Object> quickCheckMap(MicroLessonQuickCheck qc) {
        Map<String, Object> map = new TreeMap<>();
        map.put("questionPrompt", qc.questionPrompt());
        map.put("questionLatex", qc.questionLatex());
        List<Map<String, Object>> opts = new ArrayList<>();
        if (qc.options() != null) {
            for (MicroLessonQuickCheckOption opt : qc.options()) {
                Map<String, Object> optMap = new TreeMap<>();
                optMap.put("optionId", opt.optionId());
                optMap.put("latexOrText", opt.latexOrText());
                optMap.put("rationale", opt.rationale());
                optMap.put("isCorrect", opt.isCorrect());
                opts.add(optMap);
            }
        }
        map.put("options", opts);
        map.put("correctOptionId", qc.correctOptionId());
        map.put("estimatedSeconds", qc.estimatedSeconds());
        return map;
    }

    @SuppressWarnings("unchecked")
    private static Object canonicalizeObject(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalizeObject(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> rawList) {
            List<Object> normalized = new ArrayList<>(rawList.size());
            for (Object item : rawList) {
                normalized.add(canonicalizeObject(item));
            }
            return normalized;
        }
        return value;
    }
}
