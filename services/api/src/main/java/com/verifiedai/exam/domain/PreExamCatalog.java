package com.verifiedai.exam.domain;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** One bundled catalog is shared with the offline SymPy verification gate. */
@Component
public final class PreExamCatalog {
    public record Card(String catalogId, String skillCode, String mistakeCode, String titleTr, String titleEn,
                       String ruleLatex, String ruleDisplay, String domainTr, String domainEn,
                       String counterExampleLatex, String counterExampleDisplay,
                       String quickCheckTr, String quickCheckEn, String answerDisplay) {}
    public record Formula(String catalogId, String skillCode, String titleTr, String titleEn,
                          String ruleLatex, String ruleDisplay, String domainTr, String domainEn) {}
    public record Catalog(String catalogVersion, List<Card> cards) {}
    private final Catalog catalog;
    private final String hash;
    public PreExamCatalog(JsonMapper mapper) {
        try (var stream = PreExamCatalog.class.getResourceAsStream("/exam/pre-exam-catalog-v1.json")) {
            if (stream == null) throw new IllegalStateException("Missing H-15 catalog");
            byte[] bytes = stream.readAllBytes();
            catalog = mapper.readValue(bytes, Catalog.class);
            hash = hash(bytes);
            try (var expected = PreExamCatalog.class.getResourceAsStream("/exam/pre-exam-catalog-v1.sha256")) {
                if (expected == null || !hash.equals(new String(expected.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim()))
                    throw new IllegalStateException("Unapproved H-15 catalog content");
            }
            if (!"pre-exam-catalog-v1".equals(catalog.catalogVersion()) || catalog.cards().isEmpty())
                throw new IllegalStateException("Invalid H-15 catalog");
            if (catalog.cards().stream().map(Card::catalogId).distinct().count() != catalog.cards().size())
                throw new IllegalStateException("Duplicate H-15 card");
        } catch (IOException ex) { throw new IllegalStateException("Cannot load H-15 catalog", ex); }
    }
    public String contentHash() { return hash; }
    public List<Card> cards() { return List.copyOf(catalog.cards()); }
    public Set<PreExamReviewPolicy.CatalogKey> keys() {
        Set<PreExamReviewPolicy.CatalogKey> keys = new HashSet<>();
        for (Card c : cards()) keys.add(new PreExamReviewPolicy.CatalogKey(c.skillCode(), c.mistakeCode()));
        return Set.copyOf(keys);
    }
    public static Formula formula(Card c) {
        return new Formula(c.catalogId(), c.skillCode(), c.titleTr(), c.titleEn(), c.ruleLatex(), c.ruleDisplay(), c.domainTr(), c.domainEn());
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
