package com.verifiedai.mistake;

import java.util.Set;

public record MistakeClassificationDecision(Status status, String code, MistakeTaxonomy.Category category,
    Authority authority, Strength evidenceStrength, Review reviewStatus, String skillId, String explanationKey) {
    public enum Status { SIGNATURE_SUPPORTED, INVALID_TRANSFORMATION, UNCERTAIN, UNSUPPORTED,
        SOURCE_REVIEW_REQUIRED, INCOMPLETE_WORK, NO_ERROR_ESTABLISHED }
    public enum Authority { DETERMINISTIC_EVIDENCE, MODEL_SUGGESTION, USER_CORRECTION, HUMAN_REVIEW }
    public enum Strength { STRUCTURAL_AND_DISPROOF, EXACT_DISPROOF, NONE }
    public enum Review { AUTO_DETECTED, REQUIRED, USER_DISPUTED, HUMAN_REVIEWED }
    public MistakeClassificationDecision {
        // Future suggestion/review authorities need their own acceptance policy and contract.
        if(status == null || authority != Authority.DETERMINISTIC_EVIDENCE || evidenceStrength == null
            || reviewStatus == null || explanationKey == null) invalid();
        if(status == Status.SIGNATURE_SUPPORTED) {
            if(code == null || category == null || category != MistakeTaxonomy.CATEGORIES.get(code)
                || evidenceStrength != Strength.STRUCTURAL_AND_DISPROOF || reviewStatus != Review.AUTO_DETECTED
                || !explanationKey.equals("attempt.mistake." + code)
                || skillId != null && !skillId.equals(MistakeTaxonomy.SKILLS.get(code))) invalid();
        } else if(status == Status.INVALID_TRANSFORMATION) {
            if(code == null || !Set.of("INVALID_TRANSFORMATION", "ALG_TRANSFORMATION_INVALID").contains(code)
                || category != null || skillId != null || evidenceStrength != Strength.EXACT_DISPROOF
                || reviewStatus != Review.REQUIRED || !explanationKey.equals("attempt.classification.INVALID_TRANSFORMATION")) invalid();
        } else if(code != null || category != null || skillId != null || evidenceStrength != Strength.NONE
            || reviewStatus != Review.REQUIRED || !explanationKey.equals("attempt.classification." + status)) invalid();
    }
    public boolean hasDisproof() { return evidenceStrength != Strength.NONE; }
    private static void invalid() { throw new IllegalArgumentException("Invalid classification decision"); }
}
