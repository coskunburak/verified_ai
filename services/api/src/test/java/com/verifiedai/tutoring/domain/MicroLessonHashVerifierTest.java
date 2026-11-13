package com.verifiedai.tutoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MicroLessonHashVerifierTest {

    private final MicroLessonHashVerifier verifier = new MicroLessonHashVerifier();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void all34CatalogCardsMatchPythonSha256CanonicalHashes() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/micro_lessons/micro_lesson_catalog_v1.json")) {
            assertThat(in).as("micro_lesson_catalog_v1.json on classpath").isNotNull();
            JsonNode root = mapper.readTree(in);
            JsonNode cards = root.get("cards");
            assertThat(cards).hasSize(34);

            for (JsonNode node : cards) {
                MicroLessonCard card = deserializeCard(node);
                String computed = verifier.computeCanonicalHash(card);
                assertThat(computed)
                        .as("SHA-256 parity for %s (%s)", card.mistakeCode(), card.locale())
                        .isEqualTo(card.verificationHash());
                assertThat(verifier.verify(card))
                        .as("verify() must pass for %s (%s)", card.mistakeCode(), card.locale())
                        .isTrue();
            }
        }
    }

    @Test
    void tamperedCardFailsSha256Verification() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/micro_lessons/micro_lesson_catalog_v1.json")) {
            JsonNode first = mapper.readTree(in).get("cards").get(0);
            MicroLessonCard original = deserializeCard(first);
            MicroLessonCoreRule tamperedRule = new MicroLessonCoreRule(
                    original.coreRule().ruleTitle(),
                    original.coreRule().conciseExplanation(),
                    "-a(b - c) = -ab - ac",
                    original.coreRule().sympyCheckExpr(),
                    original.coreRule().estimatedSeconds()
            );
            MicroLessonCard tampered = new MicroLessonCard(
                    original.schemaVersion(),
                    original.cardId(),
                    original.mistakeCode(),
                    original.locale(),
                    original.title(),
                    original.focusBadgeLabel(),
                    original.estimatedDurationSeconds(),
                    null,
                    null,
                    tamperedRule,
                    original.brokenVsValidCounterexample(),
                    original.interactiveQuickCheck(),
                    original.verificationStatus(),
                    original.verifierVersion(),
                    original.verificationHash()
            );
            assertThat(verifier.verify(tampered)).isFalse();
        }
    }

    private MicroLessonCard deserializeCard(JsonNode node) throws Exception {
        return new MicroLessonCard(
                node.get("schemaVersion").asText(),
                UUID.fromString(node.get("cardId").asText()),
                node.get("mistakeCode").asText(),
                node.get("locale").asText(),
                node.get("title").asText(),
                node.get("focusBadgeLabel").asText(),
                node.get("estimatedDurationSeconds").asInt(),
                null,
                null,
                mapper.treeToValue(node.get("coreRule"), MicroLessonCoreRule.class),
                mapper.treeToValue(node.get("brokenVsValidCounterexample"), MicroLessonCounterexample.class),
                mapper.treeToValue(node.get("interactiveQuickCheck"), MicroLessonQuickCheck.class),
                node.get("verificationStatus").asText(),
                node.get("verifierVersion").asText(),
                node.get("verificationHash").asText()
        );
    }
}
