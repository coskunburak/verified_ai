package com.verifiedai.ai.application;

/** Ownership identifiers only. Validation remains in the owning problem or solving module. */
public enum OutputSemanticContract {
    SOLUTION_CANDIDATE(AiCapability.SOLVE, "solution-candidate-v1",
        "solving.application.SolutionCandidateValidator"),
    RECOGNITION_EVIDENCE(AiCapability.VISION_PARSE, "recognition-evidence-v1",
        "problem.application.recognition.RecognitionEvidenceNormalizer"),
    PROBLEM_PARSE(AiCapability.PROBLEM_NORMALIZE, "problem-parse-v1",
        "problem.application.parse.ProblemParseNormalizer"),
    PROBLEM_CLASSIFICATION(AiCapability.PROBLEM_CLASSIFY, "problem-classification-v1",
        "problem.application.classification.ProblemClassificationValidator");

    private final AiCapability capability;
    private final String supportedSchemaVersion;
    private final String validatorOwner;

    OutputSemanticContract(AiCapability capability, String supportedSchemaVersion, String validatorOwner) {
        this.capability = capability;
        this.supportedSchemaVersion = supportedSchemaVersion;
        this.validatorOwner = validatorOwner;
    }

    public AiCapability capability() { return capability; }
    public boolean supports(String schemaVersion) { return supportedSchemaVersion.equals(schemaVersion); }
    public String validatorOwner() { return validatorOwner; }
}
