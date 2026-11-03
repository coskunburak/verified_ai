#if DEBUG
import Foundation
/// Synthetic UI transport only; does not claim handwriting or real verifier evidence.
actor FixtureAttemptAnalysisAPI: AttemptAnalysisServicing {
    private let source = AttemptSourceIdentity(canonicalId: UUID(uuidString: "00000000-0000-0000-0000-000000006701")!, canonicalRevision: 1,
        parseId: UUID(uuidString: "00000000-0000-0000-0000-000000006702")!, parseRevision: 1)
    private var committed: [String: (AttemptSubmission, StudentAttempt)] = [:]
    private var loseAcknowledgement: Bool
    private let enabled: Bool
    init(loseAcknowledgement: Bool = false, enabled: Bool = true) { self.loseAcknowledgement = loseAcknowledgement; self.enabled = enabled }
    func context(problem: UUID) async throws -> AttemptContext {
        AttemptContext(schemaVersion: "attempt-context-v1", source: source, originalStatement: nil, enabled: enabled, handwriting: "UNAVAILABLE", twinDrill: "UNAVAILABLE", methodComparison: "UNAVAILABLE")
    }
    func list(problem: UUID) async throws -> [StudentAttempt] { committed.values.map(\.1).filter { $0.problemSessionId == problem } }
    func submit(problem: UUID, key: String, submission: AttemptSubmission) async throws -> StudentAttempt {
        if let existing = committed[key] {
            guard existing.0 == submission, existing.1.problemSessionId == problem else { throw NetworkError.invalidResponse }
            return existing.1
        }
        guard enabled, submission.source == source else { throw NetworkError.invalidResponse }
        let id = UUID()
        let valid = submission.steps == ["x=4"]
        let wrong = submission.steps == ["x=5"]
        let proof = wrong ? AttemptCounterexample(schemaVersion: "attempt-witness-v1", witnessType: "EQUATION_TRUTH",
            variableValues: ["x": .init(numerator: "4", denominator: "1")], previousStepEvaluation: "true", submittedStepEvaluation: "false",
            claimRefuted: "EQUATION_EQUIVALENCE", domainSatisfied: true, verifierVersion: "bounded-attempt-v1", explanationCode: "DIFFERENT_TRUTH") : nil
        let steps = submission.steps.enumerated().map { offset, _ in
            AttemptAnalysisResult.Step(index: offset + 1, verdict: offset > 0 ? .notAssessed : valid ? .valid : wrong ? .wrong : .uncertain,
                reasonCode: offset > 0 ? "EARLIER_UNCERTAINTY" : valid ? "SAME_SOLUTION_SET" : wrong ? "NON_EQUIVALENT" : "UNSUPPORTED_MATH",
                mistakeCode: wrong ? "ALG_TRANSFORMATION_INVALID" : nil,
                counterexampleStatus: wrong ? "AVAILABLE" : "NOT_APPLICABLE", counterexample: proof)
        }
        let analysis = AttemptAnalysisResult(schemaVersion: "attempt-analysis-v1", verifierVersion: "bounded-attempt-v1", taxonomyVersion: "attempt-taxonomy-v1",
            executionId: id, correlationId: id.uuidString.lowercased(), outcome: valid ? .progress : wrong ? .error : .review,
            firstWrongStepIndex: wrong ? 1 : nil, domain: "REAL_POLYNOMIAL", steps: steps)
        let attempt = StudentAttempt(schemaVersion: "student-attempt-v1", id: id, problemSessionId: problem, source: source, sourceType: "MANUAL", normalizationVersion: "manual-canonical-v1",
            status: .analyzed, steps: submission.steps.enumerated().map { .init(index: $0.offset + 1, content: $0.element, normalizedStatement: nil) },
            analysis: analysis, createdAt: Date(), updatedAt: Date(), trainingEligible: false)
        committed[key] = (submission, attempt)
        if loseAcknowledgement { loseAcknowledgement = false; throw URLError(.networkConnectionLost) }
        return attempt
    }
}
#endif
