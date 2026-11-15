package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record ExamSessionItem(
        UUID id,
        UUID sessionId,
        int itemPosition,
        UUID questionItemId,
        String questionCode,
        String sectionCode,
        int sectionOrder,
        String topicCode,
        String itemFormat,
        QuestionDifficultyBand difficultyBand,
        String stemMarkdown,
        List<SessionItemOptionSnapshot> optionsSnapshot,
        String figureAltText,
        BigDecimal maxPoints,
        BigDecimal negativeMarkingWeight,
        ExamItemResponseState responseState,
        String selectedOptionKey,
        BigDecimal numericResponseValue,
        boolean flaggedForReview,
        List<String> eliminatedOptionKeys,
        int timeSpentSeconds,
        int autosaveVersion,
        long clientAutosaveSeq,
        Instant lastAutosavedAt,
        boolean scratchpadRequired,
        boolean trainingEligible,
        Instant createdAt,
        Instant updatedAt,
        List<ExamScratchpadAsset> scratchpadAssets
) {
    private static final Set<String> VALID_OPTION_KEYS = Set.of("A", "B", "C", "D", "E");
    private static final Set<String> CHOICE_FORMATS = Set.of(
            "SINGLE_CHOICE_MCQ",
            "MULTI_SELECT_MCQ",
            "SINGLE_CHOICE_4_OPTION",
            "SINGLE_CHOICE_5_OPTION"
    );
    private static final Set<String> VALID_ACTIONS = Set.of(
            "VIEW_ONLY",
            "SELECT_OPTION",
            "CLEAR_ANSWER",
            "TOGGLE_FLAG",
            "SET_ELIMINATED_OPTIONS",
            "BATCH_PATCH"
    );

    public record SessionItemOptionSnapshot(
            String optionKey,
            int displayOrder,
            String contentMarkdown,
            String contentLatex,
            boolean trainingEligible
    ) {
        public SessionItemOptionSnapshot {
            Objects.requireNonNull(optionKey, "optionKey is required");
            optionKey = optionKey.trim();
            if (!VALID_OPTION_KEYS.contains(optionKey)) {
                throw new IllegalArgumentException("Invalid optionKey: " + optionKey);
            }
            if (displayOrder < 1 || displayOrder > 5) {
                throw new IllegalArgumentException("displayOrder must be between 1 and 5");
            }
            if (contentMarkdown == null || contentMarkdown.isBlank()) {
                throw new IllegalArgumentException("contentMarkdown must not be blank");
            }
            if (trainingEligible) {
                throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
            }
        }
    }

    public ExamSessionItem {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(questionItemId, "questionItemId is required");
        Objects.requireNonNull(questionCode, "questionCode is required");
        Objects.requireNonNull(sectionCode, "sectionCode is required");
        Objects.requireNonNull(topicCode, "topicCode is required");
        Objects.requireNonNull(itemFormat, "itemFormat is required");
        Objects.requireNonNull(difficultyBand, "difficultyBand is required");
        Objects.requireNonNull(stemMarkdown, "stemMarkdown is required");
        Objects.requireNonNull(responseState, "responseState is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        if (itemPosition < 1) {
            throw new IllegalArgumentException("itemPosition must be >= 1");
        }
        if (sectionOrder < 1) {
            throw new IllegalArgumentException("sectionOrder must be >= 1");
        }
        optionsSnapshot = optionsSnapshot == null ? List.of() : List.copyOf(optionsSnapshot);
        maxPoints = maxPoints == null ? new BigDecimal("1.00") : maxPoints;
        negativeMarkingWeight = negativeMarkingWeight == null ? new BigDecimal("0.2500") : negativeMarkingWeight;
        eliminatedOptionKeys = eliminatedOptionKeys == null ? List.of() : List.copyOf(eliminatedOptionKeys);
        scratchpadAssets = scratchpadAssets == null ? List.of() : List.copyOf(scratchpadAssets);
        if (timeSpentSeconds < 0 || timeSpentSeconds > ExamClockPolicy.MAX_EFFECTIVE_DURATION_SECONDS) {
            throw new IllegalArgumentException("timeSpentSeconds out of range");
        }
        if (autosaveVersion < 0 || clientAutosaveSeq < 0) {
            throw new IllegalArgumentException("autosave counters must be >= 0");
        }
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
    }

    public ExamSessionItem applyAutosave(
            String action,
            String newSelectedOptionKey,
            BigDecimal newNumericResponseValue,
            Boolean newFlaggedForReview,
            List<String> newEliminatedOptionKeys,
            int deltaTimeSpentSeconds,
            int expectedAutosaveVersion,
            long newClientAutosaveSeq,
            Instant serverNow
    ) {
        Objects.requireNonNull(serverNow, "serverNow is required");

        String effectiveAction = action == null ? "BATCH_PATCH" : action.trim();
        if (!VALID_ACTIONS.contains(effectiveAction)) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_ITEM_INVALID_ANSWER_PAYLOAD,
                    "Unsupported autosave action: " + effectiveAction,
                    true,
                    "Send a supported autosave action."
            );
        }

        if (expectedAutosaveVersion != this.autosaveVersion) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_ITEM_STALE_VERSION,
                    "Stale autosave version: expected " + this.autosaveVersion + " but received " + expectedAutosaveVersion,
                    true,
                    "Refresh the latest session state and retry autosave with the current version."
            );
        }

        if (newClientAutosaveSeq < this.clientAutosaveSeq) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_ITEM_STALE_VERSION,
                    "Out-of-order clientAutosaveSeq: current " + this.clientAutosaveSeq + " > " + newClientAutosaveSeq,
                    true,
                    "Refresh the latest session state and retry."
            );
        }

        if (deltaTimeSpentSeconds < 0 || deltaTimeSpentSeconds > 3600) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_ITEM_INVALID_ANSWER_PAYLOAD,
                    "deltaTimeSpentSeconds must be between 0 and 3600",
                    true,
                    "Send a valid elapsed time increment."
            );
        }

        String updatedOptionKey = this.selectedOptionKey;
        BigDecimal updatedNumeric = this.numericResponseValue;
        boolean updatedFlagged = this.flaggedForReview;
        List<String> updatedEliminated = this.eliminatedOptionKeys;

        boolean isChoiceItem = CHOICE_FORMATS.contains(itemFormat);
        boolean isNumericItem = "NUMERIC_GRID_IN".equals(itemFormat);

        if ("CLEAR_ANSWER".equals(effectiveAction)) {
            updatedOptionKey = null;
            updatedNumeric = null;
        } else if ("SELECT_OPTION".equals(effectiveAction)) {
            if (!isChoiceItem) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_ITEM_INVALID_ANSWER_PAYLOAD,
                        "selectedOptionKey is only valid for multiple-choice items",
                        true,
                        "Provide a numeric response instead of an option key."
                );
            }
            if (newSelectedOptionKey == null || newSelectedOptionKey.isBlank()) {
                updatedOptionKey = null;
            } else {
                String normalized = validateOptionKeyPresent(newSelectedOptionKey);
                updatedOptionKey = normalized;
                updatedNumeric = null;
                updatedEliminated = updatedEliminated.stream()
                        .filter(k -> !k.equals(normalized))
                        .toList();
            }
        } else if ("TOGGLE_FLAG".equals(effectiveAction)) {
            updatedFlagged = newFlaggedForReview != null ? newFlaggedForReview : !this.flaggedForReview;
        } else if ("SET_ELIMINATED_OPTIONS".equals(effectiveAction)) {
            updatedEliminated = validateEliminatedOptionKeys(newEliminatedOptionKeys);
            if (updatedOptionKey != null && updatedEliminated.contains(updatedOptionKey)) {
                updatedOptionKey = null;
            }
        } else if ("BATCH_PATCH".equals(effectiveAction)) {
            if (newEliminatedOptionKeys != null) {
                updatedEliminated = validateEliminatedOptionKeys(newEliminatedOptionKeys);
            }
            if (newSelectedOptionKey != null && !newSelectedOptionKey.isBlank()) {
                if (!isChoiceItem) {
                    throw new ApiProblemException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            ApiErrorCode.EXAM_ITEM_INVALID_ANSWER_PAYLOAD,
                            "selectedOptionKey is not valid for format " + itemFormat,
                            true,
                            "Submit a numeric response for grid-in items."
                    );
                }
                String normalized = validateOptionKeyPresent(newSelectedOptionKey);
                updatedOptionKey = normalized;
                updatedNumeric = null;
                updatedEliminated = updatedEliminated.stream()
                        .filter(k -> !k.equals(normalized))
                        .toList();
            } else if (isChoiceItem && "".equals(newSelectedOptionKey)) {
                updatedOptionKey = null;
            }
            if (newNumericResponseValue != null) {
                if (!isNumericItem && !"SHORT_STRUCTURED_RESPONSE".equals(itemFormat)) {
                    throw new ApiProblemException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            ApiErrorCode.EXAM_ITEM_INVALID_ANSWER_PAYLOAD,
                            "numericResponseValue is only valid for NUMERIC_GRID_IN items",
                            true,
                            "Select an option key A-E for multiple-choice items."
                    );
                }
                updatedNumeric = newNumericResponseValue;
                updatedOptionKey = null;
            }
            if (newFlaggedForReview != null) {
                updatedFlagged = newFlaggedForReview;
            }
        }

        if (newFlaggedForReview != null && !"TOGGLE_FLAG".equals(effectiveAction) && !"BATCH_PATCH".equals(effectiveAction)) {
            updatedFlagged = newFlaggedForReview;
        }

        boolean hasAnswer = updatedOptionKey != null || updatedNumeric != null;
        ExamItemResponseState nextState = ExamItemResponseState.compute(true, hasAnswer, updatedFlagged);
        int nextTimeSpent = Math.min(ExamClockPolicy.MAX_EFFECTIVE_DURATION_SECONDS, this.timeSpentSeconds + deltaTimeSpentSeconds);

        return new ExamSessionItem(
                this.id,
                this.sessionId,
                this.itemPosition,
                this.questionItemId,
                this.questionCode,
                this.sectionCode,
                this.sectionOrder,
                this.topicCode,
                this.itemFormat,
                this.difficultyBand,
                this.stemMarkdown,
                this.optionsSnapshot,
                this.figureAltText,
                this.maxPoints,
                this.negativeMarkingWeight,
                nextState,
                updatedOptionKey,
                updatedNumeric,
                updatedFlagged,
                updatedEliminated,
                nextTimeSpent,
                this.autosaveVersion + 1,
                Math.max(this.clientAutosaveSeq, newClientAutosaveSeq),
                serverNow,
                this.scratchpadRequired,
                false,
                this.createdAt,
                serverNow,
                this.scratchpadAssets
        );
    }

    public ExamSessionItem withScratchpadAssetAdded(ExamScratchpadAsset asset, Instant serverNow) {
        Objects.requireNonNull(asset, "asset is required");
        if (!asset.sessionItemId().equals(this.id)) {
            throw new IllegalArgumentException("Scratchpad asset does not belong to this session item");
        }
        List<ExamScratchpadAsset> nextAssets = new ArrayList<>(this.scratchpadAssets.size() + 1);
        for (ExamScratchpadAsset existing : this.scratchpadAssets) {
            if (existing.pageNumber() == asset.pageNumber()) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_SCRATCHPAD_INVALID_ASSET,
                        "Scratchpad pageNumber " + asset.pageNumber() + " is already attached for item " + itemPosition,
                        true,
                        "Use the next page number (1-4) for additional scratchpad photos."
                );
            }
            nextAssets.add(existing);
        }
        if (nextAssets.size() >= ExamScratchpadAsset.MAX_PAGES_PER_ITEM) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_SCRATCHPAD_INVALID_ASSET,
                    "Maximum of " + ExamScratchpadAsset.MAX_PAGES_PER_ITEM + " scratchpad pages reached for item " + itemPosition,
                    false,
                    "Each question supports up to 4 scratchpad pages."
            );
        }
        nextAssets.add(asset);
        return new ExamSessionItem(
                this.id,
                this.sessionId,
                this.itemPosition,
                this.questionItemId,
                this.questionCode,
                this.sectionCode,
                this.sectionOrder,
                this.topicCode,
                this.itemFormat,
                this.difficultyBand,
                this.stemMarkdown,
                this.optionsSnapshot,
                this.figureAltText,
                this.maxPoints,
                this.negativeMarkingWeight,
                this.responseState,
                this.selectedOptionKey,
                this.numericResponseValue,
                this.flaggedForReview,
                this.eliminatedOptionKeys,
                this.timeSpentSeconds,
                this.autosaveVersion,
                this.clientAutosaveSeq,
                this.lastAutosavedAt,
                this.scratchpadRequired,
                false,
                this.createdAt,
                serverNow != null ? serverNow : this.updatedAt,
                nextAssets
        );
    }

    private String validateOptionKeyPresent(String rawKey) {
        String key = rawKey.trim().toUpperCase();
        boolean exists = optionsSnapshot.stream().anyMatch(o -> o.optionKey().equals(key));
        if (!VALID_OPTION_KEYS.contains(key) || !exists) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_ITEM_INVALID_ANSWER_PAYLOAD,
                    "Option key '" + rawKey + "' is not present in item " + itemPosition,
                    true,
                    "Choose one of the valid option keys shown for this question."
            );
        }
        return key;
    }

    private List<String> validateEliminatedOptionKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        Set<String> valid = new LinkedHashSet<>();
        for (String raw : keys) {
            if (raw != null && !raw.isBlank()) {
                valid.add(validateOptionKeyPresent(raw));
            }
        }
        return List.copyOf(valid);
    }
}
