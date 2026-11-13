package com.verifiedai.tutoring.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.tutoring.domain.AttemptDiagnosisOwnership;
import com.verifiedai.tutoring.domain.MicroLessonCard;
import com.verifiedai.tutoring.domain.MicroLessonCompletionRecord;
import com.verifiedai.tutoring.domain.MicroLessonCoreRule;
import com.verifiedai.tutoring.domain.MicroLessonCounterexample;
import com.verifiedai.tutoring.domain.MicroLessonQuickCheck;
import com.verifiedai.tutoring.domain.MicroLessonRepository;
import com.verifiedai.tutoring.domain.MicroLessonTriggerSource;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMicroLessonRepository implements MicroLessonRepository {

    private final Map<String, MicroLessonCard> catalog = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> activeDiagnoses = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> attemptOwners = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> attemptMistakes = new ConcurrentHashMap<>();
    private final Map<UUID, Set<MicroLessonTriggerSource>> contextualTriggers = new ConcurrentHashMap<>();
    private final Map<String, MicroLessonCompletionRecord> completionsByKey = new ConcurrentHashMap<>();

    public InMemoryMicroLessonRepository() {
        loadCatalogFromClasspath();
    }

    private void loadCatalogFromClasspath() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        try (InputStream in = getClass().getResourceAsStream("/micro_lessons/micro_lesson_catalog_v1.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing /micro_lessons/micro_lesson_catalog_v1.json on classpath");
            }
            JsonNode cards = mapper.readTree(in).get("cards");
            for (JsonNode node : cards) {
                MicroLessonCard card = new MicroLessonCard(
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
                catalog.put(key(card.mistakeCode(), card.locale()), card);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load test catalog", ex);
        }
    }

    public void grantActiveDiagnosis(UUID userId, String mistakeCode) {
        activeDiagnoses.computeIfAbsent(userId, ignored -> new HashSet<>()).add(mistakeCode);
    }

    public void registerAttempt(UUID userId, UUID attemptId, String mistakeCodeOrNull) {
        attemptOwners.put(attemptId, userId);
        if (mistakeCodeOrNull != null) {
            attemptMistakes.computeIfAbsent(attemptId, ignored -> new HashSet<>()).add(mistakeCodeOrNull);
        }
    }

    public void grantContextualTrigger(UUID userId, MicroLessonTriggerSource source) {
        contextualTriggers.computeIfAbsent(userId, ignored -> new HashSet<>()).add(source);
    }

    public void putCatalogCardOverride(MicroLessonCard card) {
        catalog.put(key(card.mistakeCode(), card.locale()), card);
    }

    public int catalogCount() {
        return catalog.size();
    }

    @Override
    public Optional<MicroLessonCard> findVerifiedCard(String mistakeCode, String locale) {
        return Optional.ofNullable(catalog.get(key(mistakeCode, locale)))
                .filter(c -> "VERIFIED".equals(c.verificationStatus()));
    }

    @Override
    public boolean hasActiveMistakeDiagnosis(UUID userId, String mistakeCode) {
        return activeDiagnoses.getOrDefault(userId, Set.of()).contains(mistakeCode);
    }

    @Override
    public AttemptDiagnosisOwnership checkAttemptDiagnosisOwnership(UUID userId, UUID sourceAttemptId, String mistakeCode) {
        UUID owner = attemptOwners.get(sourceAttemptId);
        if (owner == null || !owner.equals(userId)) {
            return AttemptDiagnosisOwnership.NOT_FOUND_OR_FOREIGN;
        }
        if (attemptMistakes.getOrDefault(sourceAttemptId, Set.of()).contains(mistakeCode)) {
            return AttemptDiagnosisOwnership.OWNED_DIAGNOSIS_ELIGIBLE;
        }
        return AttemptDiagnosisOwnership.OWNED_NO_DIAGNOSIS;
    }

    @Override
    public boolean hasEligibleContextualTrigger(
            UUID userId,
            String mistakeCode,
            MicroLessonTriggerSource triggerSource,
            UUID sourceAttemptId
    ) {
        if (triggerSource == MicroLessonTriggerSource.COACH_REPAIR_DRAWER) {
            return hasActiveMistakeDiagnosis(userId, mistakeCode);
        }
        return contextualTriggers.getOrDefault(userId, Set.of()).contains(triggerSource);
    }

    @Override
    public Optional<MicroLessonCompletionRecord> findCompletionByIdempotencyKey(UUID userId, String idempotencyKey) {
        return Optional.ofNullable(completionsByKey.get(completionKey(userId, idempotencyKey)));
    }

    @Override
    public void saveCompletion(MicroLessonCompletionRecord record) {
        completionsByKey.put(completionKey(record.userId(), record.idempotencyKey()), record);
    }

    @Override
    public List<MicroLessonCompletionRecord> findCompletionsByUserId(UUID userId) {
        return completionsByKey.values().stream()
                .filter(r -> r.userId().equals(userId))
                .toList();
    }

    @Override
    public int deleteCompletionsByUserId(UUID userId) {
        List<String> toRemove = new HashMap<>(completionsByKey).entrySet().stream()
                .filter(e -> e.getValue().userId().equals(userId))
                .map(Map.Entry::getKey)
                .toList();
        toRemove.forEach(completionsByKey::remove);
        return toRemove.size();
    }

    private static String key(String mistakeCode, String locale) {
        return mistakeCode + "|" + locale;
    }

    private static String completionKey(UUID userId, String idempotencyKey) {
        return userId + "|" + idempotencyKey;
    }
}
