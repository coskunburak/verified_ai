package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Pure deterministic, zero-LLM ($0.00 spend) assessment assembly engine for Sprint 9.3.
 * Implements Largest Remainder (Hamilton) topic/difficulty/cognitive quota allocation,
 * cooldown priority, seeded Fisher-Yates shuffles, difficulty tolerance verification,
 * accommodation time scaling, and SHA-256 reproducibility hashing.
 */
@Component
public class DeterministicAssessmentAssemblyEngine {

    public record AssemblyInput(
            UUID assessmentId,
            UUID userId,
            UUID candidateProfileId,
            AssessmentBlueprint blueprint,
            List<QuestionBankItem> questionPool,
            Set<UUID> recentlySeenItemIds,
            String assemblySeed,
            AppliedAccommodationSnapshot accommodations,
            Instant assembledAt
    ) {
        public AssemblyInput {
            Objects.requireNonNull(userId, "userId is required");
            Objects.requireNonNull(blueprint, "blueprint is required");
            questionPool = questionPool == null ? List.of() : List.copyOf(questionPool);
            recentlySeenItemIds = recentlySeenItemIds == null ? Set.of() : Set.copyOf(recentlySeenItemIds);
            if (assemblySeed == null || assemblySeed.trim().length() < 4) {
                throw new IllegalArgumentException("assemblySeed must be at least 4 characters");
            }
            assemblySeed = assemblySeed.trim();
            accommodations = accommodations == null
                    ? AppliedAccommodationSnapshot.standardDefaults()
                    : accommodations;
            assembledAt = assembledAt == null ? Instant.now() : assembledAt;
        }
    }

    public AssembledAssessment assemble(AssemblyInput input) {
        Objects.requireNonNull(input, "input is required");
        AssessmentBlueprint blueprint = input.blueprint();
        int totalQuestions = blueprint.totalQuestionCount();

        // 1. Allocate Topic Quotas using Largest Remainder (Hamilton) method with min/max bounds
        Map<String, Integer> topicQuotas = allocateTopicQuotas(blueprint);

        // 2. Allocate Global Difficulty & Cognitive Quotas using Largest Remainder (Hamilton) method
        EnumMap<QuestionDifficultyBand, Integer> targetDifficultyQuotas = allocateDifficultyQuotas(blueprint);
        EnumMap<CognitiveTaxonomyLevel, Integer> targetCognitiveQuotas = allocateCognitiveQuotas(blueprint);

        // 3. Filter Question Pool to PUBLISHED, matching exam definition + version, and SME-VERIFIED provenance
        List<QuestionBankItem> eligibleGlobalPool = input.questionPool().stream()
                .filter(item -> item.status() == QuestionItemStatus.PUBLISHED)
                .filter(item -> item.examDefinitionId().equals(blueprint.examDefinitionId()))
                .filter(item -> item.examVersionNumber() == blueprint.examVersionNumber())
                .filter(item -> item.provenance() != null && item.provenance().isSmeVerified())
                .toList();

        // 4. Select items per topic rule with cooldown priority & difficulty/cognitive alignment
        EnumMap<QuestionDifficultyBand, Integer> remainingDiffQuota = new EnumMap<>(targetDifficultyQuotas);
        EnumMap<CognitiveTaxonomyLevel, Integer> remainingCogQuota = new EnumMap<>(targetCognitiveQuotas);

        Map<String, List<QuestionBankItem>> selectedByTopic = new LinkedHashMap<>();
        Map<String, List<QuestionBankItem>> allowedSwapPoolByTopic = new LinkedHashMap<>();
        Set<UUID> chosenIds = new HashSet<>();

        List<BlueprintTopicRule> orderedRules = blueprint.topicRules().stream()
                .sorted(Comparator.comparingInt(BlueprintTopicRule::ruleOrder))
                .toList();

        for (BlueprintTopicRule rule : orderedRules) {
            int quota = topicQuotas.getOrDefault(rule.topicCode(), 0);
            if (quota == 0) {
                selectedByTopic.put(rule.topicCode(), new ArrayList<>());
                allowedSwapPoolByTopic.put(rule.topicCode(), List.of());
                continue;
            }

            List<QuestionBankItem> topicCandidates = eligibleGlobalPool.stream()
                    .filter(item -> item.topicCode().equals(rule.topicCode()))
                    .filter(item -> rule.requiredItemFormats().contains(item.itemFormat()))
                    .filter(item -> !chosenIds.contains(item.id()))
                    .toList();

            List<QuestionBankItem> freshPool = topicCandidates.stream()
                    .filter(item -> !input.recentlySeenItemIds().contains(item.id()))
                    .toList();
            List<QuestionBankItem> cooldownFallbackPool = topicCandidates.stream()
                    .filter(item -> input.recentlySeenItemIds().contains(item.id()))
                    .toList();

            if (freshPool.size() + cooldownFallbackPool.size() < quota) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_QUESTION_POOL_INSUFFICIENT,
                        "Insufficient eligible published questions for topic " + rule.topicCode()
                                + " (required " + quota + ", available " + (freshPool.size() + cooldownFallbackPool.size()) + ")",
                        true,
                        "Add and publish more SME-verified questions for topic " + rule.topicCode() + " or adjust blueprint quotas."
                );
            }

            List<QuestionBankItem> topicSelected = new ArrayList<>(quota);
            int freshToPick = Math.min(freshPool.size(), quota);
            List<QuestionBankItem> remainingFresh = new ArrayList<>(freshPool);
            for (int k = 0; k < freshToPick; k++) {
                QuestionBankItem best = pickBestCandidate(
                        remainingFresh,
                        remainingDiffQuota,
                        remainingCogQuota,
                        input.userId(),
                        blueprint,
                        input.assemblySeed()
                );
                remainingFresh.remove(best);
                topicSelected.add(best);
                chosenIds.add(best.id());
                remainingDiffQuota.merge(best.difficultyBand(), -1, Integer::sum);
                remainingCogQuota.merge(best.cognitiveLevel(), -1, Integer::sum);
            }

            int fallbackToPick = quota - freshToPick;
            List<QuestionBankItem> remainingFallback = new ArrayList<>(cooldownFallbackPool);
            for (int k = 0; k < fallbackToPick; k++) {
                QuestionBankItem best = pickBestCandidate(
                        remainingFallback,
                        remainingDiffQuota,
                        remainingCogQuota,
                        input.userId(),
                        blueprint,
                        input.assemblySeed()
                );
                remainingFallback.remove(best);
                topicSelected.add(best);
                chosenIds.add(best.id());
                remainingDiffQuota.merge(best.difficultyBand(), -1, Integer::sum);
                remainingCogQuota.merge(best.cognitiveLevel(), -1, Integer::sum);
            }

            selectedByTopic.put(rule.topicCode(), topicSelected);
            // If freshPool had enough items, local swaps only consider freshPool so cooldown priority is preserved
            allowedSwapPoolByTopic.put(
                    rule.topicCode(),
                    freshPool.size() >= quota ? freshPool : topicCandidates
            );
        }

        // Deterministic local refinement pass to minimize max difficulty deviation bps within topic pools
        refineSelectionForDifficultyTolerance(
                orderedRules,
                selectedByTopic,
                allowedSwapPoolByTopic,
                chosenIds,
                blueprint,
                input.userId(),
                input.assemblySeed()
        );

        // Flatten in canonical ruleOrder + questionCode order before optional shuffle
        List<QuestionBankItem> selectedItems = new ArrayList<>(totalQuestions);
        for (BlueprintTopicRule rule : orderedRules) {
            List<QuestionBankItem> topicItems = new ArrayList<>(selectedByTopic.getOrDefault(rule.topicCode(), List.of()));
            topicItems.sort(Comparator.comparing(QuestionBankItem::questionCode));
            selectedItems.addAll(topicItems);
        }

        // Verify difficulty tolerance across all bands
        AssemblyDistributionSummary distributionSummary = buildAndVerifyDistributionSummary(selectedItems, blueprint);

        // 5. Question & Option Ordering (Deterministic Fisher-Yates shuffle)
        if (blueprint.shuffleQuestions()) {
            long questionShuffleSeed = deriveSeedLong(
                    "QUESTIONS|" + input.userId() + "|" + blueprint.id() + "|" + blueprint.examVersionNumber() + "|" + input.assemblySeed()
            );
            fisherYatesShuffle(selectedItems, new Random(questionShuffleSeed));
        }

        UUID assessmentId = input.assessmentId() != null
                ? input.assessmentId()
                : UUID.nameUUIDFromBytes(
                        ("ASSESSMENT|" + input.userId() + "|" + blueprint.id() + "|" + input.assemblySeed())
                                .getBytes(StandardCharsets.UTF_8)
                );

        List<AssembledAssessmentItem> assembledItems = new ArrayList<>(selectedItems.size());
        for (int i = 0; i < selectedItems.size(); i++) {
            QuestionBankItem qItem = selectedItems.get(i);
            int seq = i + 1;
            List<QuestionOption> orderedOptions = new ArrayList<>(qItem.options());
            orderedOptions.sort(Comparator.comparingInt(QuestionOption::displayOrder));

            if (blueprint.shuffleOptions() && !orderedOptions.isEmpty()) {
                long optionShuffleSeed = deriveSeedLong(
                        "OPTIONS|" + input.userId() + "|" + blueprint.id() + "|" + input.assemblySeed() + "|" + qItem.id()
                );
                fisherYatesShuffle(orderedOptions, new Random(optionShuffleSeed));
            }

            List<String> optionOrder = orderedOptions.isEmpty()
                    ? List.of()
                    : orderedOptions.stream().map(QuestionOption::optionKey).toList();

            UUID assembledItemId = UUID.nameUUIDFromBytes(
                    ("ASSEMBLED_ITEM|" + assessmentId + "|" + seq + "|" + qItem.id()).getBytes(StandardCharsets.UTF_8)
            );

            assembledItems.add(new AssembledAssessmentItem(
                    assembledItemId,
                    assessmentId,
                    qItem.id(),
                    qItem.questionCode(),
                    seq,
                    qItem.topicCode(),
                    qItem.subtopicCode(),
                    qItem.itemFormat(),
                    qItem.difficultyBand(),
                    qItem.cognitiveLevel(),
                    qItem.stemMarkdown(),
                    qItem.stemLatex(),
                    qItem.altTextDescription(),
                    qItem.hasVisualDiagram(),
                    optionOrder,
                    orderedOptions,
                    qItem.maxPoints(),
                    qItem.estimatedTimeSeconds(),
                    false
            ));
        }

        // 6. Accommodation Time Scaling
        BigDecimal timeMultiplier = validateAndNormalizeTimeMultiplier(input.accommodations().timeMultiplier());
        int effectiveDurationSeconds = (int) Math.ceil(blueprint.baseDurationSeconds() * timeMultiplier.doubleValue());

        AppliedAccommodationSnapshot normalizedAccommodations = new AppliedAccommodationSnapshot(
                timeMultiplier,
                input.accommodations().highContrastMode(),
                input.accommodations().screenReaderOptimized(),
                input.accommodations().extendedBreakPrompts()
        );

        // 7. Reproducibility Hash
        String orderedItemIds = assembledItems.stream()
                .map(item -> item.questionItemId().toString())
                .collect(Collectors.joining(","));
        String orderedOptionOrders = assembledItems.stream()
                .map(item -> String.join("", item.optionOrder()))
                .collect(Collectors.joining(";"));
        String reproducibilityHash = sha256Hex(
                blueprint.blueprintHash()
                        + "|" + blueprint.examVersionNumber()
                        + "|" + input.assemblySeed()
                        + "|" + effectiveDurationSeconds
                        + "|" + orderedItemIds
                        + "|" + orderedOptionOrders
        );

        Instant assembledAt = input.assembledAt();
        Instant expiresAt = assembledAt.plus(7, ChronoUnit.DAYS);

        return new AssembledAssessment(
                assessmentId,
                input.userId(),
                input.candidateProfileId(),
                blueprint.id(),
                blueprint.blueprintCode(),
                blueprint.title(),
                blueprint.blueprintMode(),
                blueprint.examDefinitionId(),
                blueprint.examVersionNumber(),
                input.assemblySeed(),
                reproducibilityHash,
                totalQuestions,
                blueprint.baseDurationSeconds(),
                timeMultiplier,
                effectiveDurationSeconds,
                normalizedAccommodations,
                distributionSummary,
                AssembledAssessmentStatus.ASSEMBLED,
                assembledAt,
                expiresAt,
                false,
                assembledItems
        );
    }

    public Map<String, Integer> allocateTopicQuotas(AssessmentBlueprint blueprint) {
        int totalQuestions = blueprint.totalQuestionCount();
        List<BlueprintTopicRule> rules = blueprint.topicRules().stream()
                .sorted(Comparator.comparingInt(BlueprintTopicRule::ruleOrder))
                .toList();

        record RuleAllocation(
                BlueprintTopicRule rule,
                double exactQuota,
                double remainder
        ) {
        }

        List<RuleAllocation> allocations = new ArrayList<>(rules.size());
        Map<String, Integer> quotas = new LinkedHashMap<>();
        int sumAllocated = 0;

        for (BlueprintTopicRule rule : rules) {
            double exact = (totalQuestions * (double) rule.targetWeightBps()) / 10000.0;
            int floor = (int) Math.floor(exact);
            double remainder = exact - floor;
            int clamped = Math.max(rule.minQuestionCount(), Math.min(rule.maxQuestionCount(), floor));
            quotas.put(rule.topicCode(), clamped);
            sumAllocated += clamped;
            allocations.add(new RuleAllocation(rule, exact, remainder));
        }

        if (sumAllocated < totalQuestions) {
            List<RuleAllocation> orderedForIncrement = allocations.stream()
                    .sorted(Comparator.comparingDouble(RuleAllocation::remainder).reversed()
                            .thenComparing(Comparator.comparingInt((RuleAllocation a) -> a.rule().targetWeightBps()).reversed())
                            .thenComparing(a -> a.rule().topicCode()))
                    .toList();

            while (sumAllocated < totalQuestions) {
                boolean incremented = false;
                for (RuleAllocation alloc : orderedForIncrement) {
                    String topic = alloc.rule().topicCode();
                    int current = quotas.get(topic);
                    if (current < alloc.rule().maxQuestionCount()) {
                        quotas.put(topic, current + 1);
                        sumAllocated++;
                        incremented = true;
                        if (sumAllocated == totalQuestions) {
                            break;
                        }
                    }
                }
                if (!incremented) {
                    break;
                }
            }
        } else if (sumAllocated > totalQuestions) {
            List<RuleAllocation> orderedForDecrement = allocations.stream()
                    .sorted(Comparator.comparingDouble(RuleAllocation::remainder)
                            .thenComparingInt(a -> a.rule().targetWeightBps())
                            .thenComparing(Comparator.comparing((RuleAllocation a) -> a.rule().topicCode()).reversed()))
                    .toList();

            while (sumAllocated > totalQuestions) {
                boolean decremented = false;
                for (RuleAllocation alloc : orderedForDecrement) {
                    String topic = alloc.rule().topicCode();
                    int current = quotas.get(topic);
                    if (current > alloc.rule().minQuestionCount()) {
                        quotas.put(topic, current - 1);
                        sumAllocated--;
                        decremented = true;
                        if (sumAllocated == totalQuestions) {
                            break;
                        }
                    }
                }
                if (!decremented) {
                    break;
                }
            }
        }

        if (sumAllocated != totalQuestions) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "Unable to allocate exact totalQuestionCount=" + totalQuestions + " within topic rule min/max bounds",
                    true,
                    "Review topic rule minQuestionCount and maxQuestionCount bounds."
            );
        }

        return quotas;
    }

    public EnumMap<QuestionDifficultyBand, Integer> allocateDifficultyQuotas(AssessmentBlueprint blueprint) {
        int n = blueprint.totalQuestionCount();
        Map<QuestionDifficultyBand, Integer> bpsMap = Map.of(
                QuestionDifficultyBand.EASY, blueprint.easyTargetBps(),
                QuestionDifficultyBand.MEDIUM, blueprint.mediumTargetBps(),
                QuestionDifficultyBand.HARD, blueprint.hardTargetBps(),
                QuestionDifficultyBand.EXPERT, blueprint.expertTargetBps()
        );
        EnumMap<QuestionDifficultyBand, Integer> result = new EnumMap<>(QuestionDifficultyBand.class);
        record BandPart(QuestionDifficultyBand band, int bps, int floor, double rem) {
        }
        List<BandPart> parts = new ArrayList<>(4);
        int sum = 0;
        for (QuestionDifficultyBand band : QuestionDifficultyBand.values()) {
            int bps = bpsMap.getOrDefault(band, 0);
            double exact = (n * (double) bps) / 10000.0;
            int floor = (int) Math.floor(exact);
            parts.add(new BandPart(band, bps, floor, exact - floor));
            result.put(band, floor);
            sum += floor;
        }
        List<BandPart> sorted = parts.stream()
                .sorted(Comparator.comparingDouble(BandPart::rem).reversed()
                        .thenComparing(Comparator.comparingInt(BandPart::bps).reversed())
                        .thenComparing(p -> p.band().ordinal()))
                .toList();
        int idx = 0;
        while (sum < n) {
            BandPart part = sorted.get(idx % sorted.size());
            result.merge(part.band(), 1, Integer::sum);
            sum++;
            idx++;
        }
        return result;
    }

    public EnumMap<CognitiveTaxonomyLevel, Integer> allocateCognitiveQuotas(AssessmentBlueprint blueprint) {
        int n = blueprint.totalQuestionCount();
        Map<CognitiveTaxonomyLevel, Integer> bpsMap = Map.of(
                CognitiveTaxonomyLevel.RECALL, blueprint.recallTargetBps(),
                CognitiveTaxonomyLevel.COMPREHENSION, blueprint.comprehensionTargetBps(),
                CognitiveTaxonomyLevel.APPLICATION, blueprint.applicationTargetBps(),
                CognitiveTaxonomyLevel.ANALYSIS, blueprint.analysisTargetBps(),
                CognitiveTaxonomyLevel.EVALUATION, blueprint.evaluationTargetBps()
        );
        EnumMap<CognitiveTaxonomyLevel, Integer> result = new EnumMap<>(CognitiveTaxonomyLevel.class);
        record CogPart(CognitiveTaxonomyLevel level, int bps, int floor, double rem) {
        }
        List<CogPart> parts = new ArrayList<>(5);
        int sum = 0;
        for (CognitiveTaxonomyLevel level : CognitiveTaxonomyLevel.values()) {
            int bps = bpsMap.getOrDefault(level, 0);
            double exact = (n * (double) bps) / 10000.0;
            int floor = (int) Math.floor(exact);
            parts.add(new CogPart(level, bps, floor, exact - floor));
            result.put(level, floor);
            sum += floor;
        }
        List<CogPart> sorted = parts.stream()
                .sorted(Comparator.comparingDouble(CogPart::rem).reversed()
                        .thenComparing(Comparator.comparingInt(CogPart::bps).reversed())
                        .thenComparing(p -> p.level().ordinal()))
                .toList();
        int idx = 0;
        while (sum < n) {
            CogPart part = sorted.get(idx % sorted.size());
            result.merge(part.level(), 1, Integer::sum);
            sum++;
            idx++;
        }
        return result;
    }

    private QuestionBankItem pickBestCandidate(
            List<QuestionBankItem> pool,
            EnumMap<QuestionDifficultyBand, Integer> remainingDiffQuota,
            EnumMap<CognitiveTaxonomyLevel, Integer> remainingCogQuota,
            UUID userId,
            AssessmentBlueprint blueprint,
            String assemblySeed
    ) {
        Comparator<QuestionBankItem> comparator = Comparator
                .comparingInt((QuestionBankItem item) -> remainingDiffQuota.getOrDefault(item.difficultyBand(), 0) > 0 ? 1 : 0)
                .thenComparingInt(item -> remainingDiffQuota.getOrDefault(item.difficultyBand(), 0))
                .thenComparingInt(item -> remainingCogQuota.getOrDefault(item.cognitiveLevel(), 0) > 0 ? 1 : 0)
                .thenComparingInt(item -> remainingCogQuota.getOrDefault(item.cognitiveLevel(), 0))
                .thenComparing(Comparator.comparingLong(QuestionBankItem::exposureCount).reversed())
                .thenComparing(
                        item -> sha256Hex("ITEM_TIE|" + userId + "|" + blueprint.id() + "|" + blueprint.examVersionNumber() + "|" + assemblySeed + "|" + item.id()),
                        Comparator.reverseOrder()
                )
                .thenComparing(QuestionBankItem::questionCode, Comparator.reverseOrder());

        return pool.stream()
                .max(comparator)
                .orElseThrow();
    }

    private void refineSelectionForDifficultyTolerance(
            List<BlueprintTopicRule> orderedRules,
            Map<String, List<QuestionBankItem>> selectedByTopic,
            Map<String, List<QuestionBankItem>> allowedSwapPoolByTopic,
            Set<UUID> chosenIds,
            AssessmentBlueprint blueprint,
            UUID userId,
            String assemblySeed
    ) {
        int n = blueprint.totalQuestionCount();
        for (int pass = 0; pass < n * 2; pass++) {
            List<QuestionBankItem> currentAll = selectedByTopic.values().stream().flatMap(List::stream).toList();
            int currentMaxDev = computeMaxDifficultyDeviationBps(currentAll, blueprint);
            int currentTotalDev = computeTotalDifficultyDeviationBps(currentAll, blueprint);
            if (currentMaxDev == 0 && currentTotalDev == 0) {
                break;
            }

            boolean improved = false;
            for (BlueprintTopicRule rule : orderedRules) {
                List<QuestionBankItem> topicSelected = selectedByTopic.get(rule.topicCode());
                List<QuestionBankItem> topicPool = new ArrayList<>(allowedSwapPoolByTopic.getOrDefault(rule.topicCode(), List.of()));
                topicPool.sort(Comparator
                        .comparingLong(QuestionBankItem::exposureCount)
                        .thenComparing(item -> sha256Hex("SWAP_TIE|" + userId + "|" + blueprint.id() + "|" + assemblySeed + "|" + item.id()))
                        .thenComparing(QuestionBankItem::questionCode));

                for (int idx = 0; idx < topicSelected.size(); idx++) {
                    QuestionBankItem existing = topicSelected.get(idx);
                    for (QuestionBankItem replacement : topicPool) {
                        if (chosenIds.contains(replacement.id()) || existing.difficultyBand() == replacement.difficultyBand()) {
                            continue;
                        }
                        topicSelected.set(idx, replacement);
                        List<QuestionBankItem> candidateAll = selectedByTopic.values().stream().flatMap(List::stream).toList();
                        int candidateMaxDev = computeMaxDifficultyDeviationBps(candidateAll, blueprint);
                        int candidateTotalDev = computeTotalDifficultyDeviationBps(candidateAll, blueprint);

                        if (candidateMaxDev < currentMaxDev
                                || (candidateMaxDev == currentMaxDev && candidateTotalDev < currentTotalDev)) {
                            chosenIds.remove(existing.id());
                            chosenIds.add(replacement.id());
                            currentMaxDev = candidateMaxDev;
                            currentTotalDev = candidateTotalDev;
                            improved = true;
                            break;
                        } else {
                            topicSelected.set(idx, existing);
                        }
                    }
                }
            }
            if (!improved) {
                break;
            }
        }
    }

    private AssemblyDistributionSummary buildAndVerifyDistributionSummary(
            List<QuestionBankItem> selectedItems,
            AssessmentBlueprint blueprint
    ) {
        int n = blueprint.totalQuestionCount();
        Map<String, Integer> topicCounts = new LinkedHashMap<>();
        for (BlueprintTopicRule rule : blueprint.topicRules()) {
            topicCounts.put(rule.topicCode(), 0);
        }
        Map<String, Integer> difficultyCounts = new LinkedHashMap<>();
        for (QuestionDifficultyBand band : QuestionDifficultyBand.values()) {
            difficultyCounts.put(band.name(), 0);
        }
        Map<String, Integer> cognitiveCounts = new LinkedHashMap<>();
        for (CognitiveTaxonomyLevel level : CognitiveTaxonomyLevel.values()) {
            cognitiveCounts.put(level.name(), 0);
        }

        for (QuestionBankItem item : selectedItems) {
            topicCounts.merge(item.topicCode(), 1, Integer::sum);
            difficultyCounts.merge(item.difficultyBand().name(), 1, Integer::sum);
            cognitiveCounts.merge(item.cognitiveLevel().name(), 1, Integer::sum);
        }

        Map<QuestionDifficultyBand, Integer> targetBps = Map.of(
                QuestionDifficultyBand.EASY, blueprint.easyTargetBps(),
                QuestionDifficultyBand.MEDIUM, blueprint.mediumTargetBps(),
                QuestionDifficultyBand.HARD, blueprint.hardTargetBps(),
                QuestionDifficultyBand.EXPERT, blueprint.expertTargetBps()
        );

        Map<String, Integer> realizedBpsMap = new LinkedHashMap<>();
        int maxDeviation = 0;
        for (QuestionDifficultyBand band : QuestionDifficultyBand.values()) {
            int count = difficultyCounts.getOrDefault(band.name(), 0);
            int realizedBps = (int) Math.round((count * 10000.0) / n);
            realizedBpsMap.put(band.name(), realizedBps);
            int deviation = Math.abs(realizedBps - targetBps.get(band));
            if (deviation > maxDeviation) {
                maxDeviation = deviation;
            }
        }

        if (maxDeviation > blueprint.difficultyToleranceBps()) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_ASSEMBLY_DIFFICULTY_TOLERANCE_EXCEEDED,
                    "Realized difficulty distribution deviation (" + maxDeviation
                            + " bps) exceeds blueprint tolerance (" + blueprint.difficultyToleranceBps() + " bps)",
                    true,
                    "Publish additional question bank items matching the target difficulty distribution or widen difficultyToleranceBps."
            );
        }

        return new AssemblyDistributionSummary(
                topicCounts,
                difficultyCounts,
                realizedBpsMap,
                cognitiveCounts,
                maxDeviation
        );
    }

    private int computeMaxDifficultyDeviationBps(List<QuestionBankItem> items, AssessmentBlueprint blueprint) {
        int n = blueprint.totalQuestionCount();
        EnumMap<QuestionDifficultyBand, Integer> counts = new EnumMap<>(QuestionDifficultyBand.class);
        for (QuestionBankItem item : items) {
            counts.merge(item.difficultyBand(), 1, Integer::sum);
        }
        int easyDev = Math.abs((int) Math.round((counts.getOrDefault(QuestionDifficultyBand.EASY, 0) * 10000.0) / n) - blueprint.easyTargetBps());
        int medDev = Math.abs((int) Math.round((counts.getOrDefault(QuestionDifficultyBand.MEDIUM, 0) * 10000.0) / n) - blueprint.mediumTargetBps());
        int hardDev = Math.abs((int) Math.round((counts.getOrDefault(QuestionDifficultyBand.HARD, 0) * 10000.0) / n) - blueprint.hardTargetBps());
        int expDev = Math.abs((int) Math.round((counts.getOrDefault(QuestionDifficultyBand.EXPERT, 0) * 10000.0) / n) - blueprint.expertTargetBps());
        return Math.max(Math.max(easyDev, medDev), Math.max(hardDev, expDev));
    }

    private int computeTotalDifficultyDeviationBps(List<QuestionBankItem> items, AssessmentBlueprint blueprint) {
        int n = blueprint.totalQuestionCount();
        EnumMap<QuestionDifficultyBand, Integer> counts = new EnumMap<>(QuestionDifficultyBand.class);
        for (QuestionBankItem item : items) {
            counts.merge(item.difficultyBand(), 1, Integer::sum);
        }
        int easyDev = Math.abs((int) Math.round((counts.getOrDefault(QuestionDifficultyBand.EASY, 0) * 10000.0) / n) - blueprint.easyTargetBps());
        int medDev = Math.abs((int) Math.round((counts.getOrDefault(QuestionDifficultyBand.MEDIUM, 0) * 10000.0) / n) - blueprint.mediumTargetBps());
        int hardDev = Math.abs((int) Math.round((counts.getOrDefault(QuestionDifficultyBand.HARD, 0) * 10000.0) / n) - blueprint.hardTargetBps());
        int expDev = Math.abs((int) Math.round((counts.getOrDefault(QuestionDifficultyBand.EXPERT, 0) * 10000.0) / n) - blueprint.expertTargetBps());
        return easyDev + medDev + hardDev + expDev;
    }

    private static BigDecimal validateAndNormalizeTimeMultiplier(BigDecimal multiplier) {
        if (multiplier == null) {
            return new BigDecimal("1.00");
        }
        if (multiplier.compareTo(new BigDecimal("1.00")) < 0 || multiplier.compareTo(new BigDecimal("2.00")) > 0) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.CANDIDATE_ACCOMMODATION_INVALID,
                    "Accommodation timeMultiplier must be between 1.00 and 2.00",
                    true,
                    "Specify a valid extra-time accommodation multiplier (1.00, 1.25, 1.50, or 2.00)."
            );
        }
        return multiplier.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static <T> void fisherYatesShuffle(List<T> list, Random rng) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    private static long deriveSeedLong(String seedMaterial) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seedMaterial.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash, 0, Long.BYTES).getLong();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
