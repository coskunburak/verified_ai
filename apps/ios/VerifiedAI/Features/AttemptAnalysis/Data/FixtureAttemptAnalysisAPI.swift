#if DEBUG
import Foundation
/// Synthetic UI transport only; does not claim handwriting or real verifier evidence.
actor FixtureAttemptAnalysisAPI: AttemptAnalysisServicing {
    private let source = AttemptSourceIdentity(canonicalId: UUID(uuidString: "00000000-0000-0000-0000-000000006701")!, canonicalRevision: 1,
        parseId: UUID(uuidString: "00000000-0000-0000-0000-000000006702")!, parseRevision: 1)
    private var committed: [String: (AttemptSubmission, StudentAttempt)] = [:]
    private var classifications: [UUID: AttemptMistakeView] = [:]
    private var evaluations: [UUID: AttemptEvaluationView] = [:]
    private var workCommitted: [String: (AttemptWorkRequest, AttemptStructuredWork)] = [:]
    private var loseAcknowledgement: Bool
    private let enabled: Bool
    init(loseAcknowledgement: Bool = false, enabled: Bool = true) { self.loseAcknowledgement = loseAcknowledgement; self.enabled = enabled }
    func context(problem: UUID) async throws -> AttemptContext {
        AttemptContext(schemaVersion: "attempt-context-v2", source: source, originalStatement: nil, enabled: enabled, handwriting: "UNAVAILABLE", twinDrill: "UNAVAILABLE", methodComparison: "UNAVAILABLE", structuredWorkEnabled: enabled)
    }
    func list(problem: UUID) async throws -> [StudentAttempt] { committed.values.map(\.1).filter { $0.problemSessionId == problem } }
    func readWork(problem: UUID, attempt: UUID) async throws -> AttemptStructuredWork? {
        workCommitted.values.map(\.1).first { $0.problemSessionId == problem && $0.attemptId == attempt }
    }
    func readClassification(problem: UUID, attempt: UUID) async throws -> AttemptMistakeView {
        let evaluation = try await readEvaluation(problem: problem, attempt: attempt)
        return .init(schemaVersion: "attempt-mistake-view-v1", attemptId: attempt,
            status: classifications[attempt] != nil ? .completed : evaluation.historical ? .stale : .notClassified,
            historical: evaluation.historical, creationEnabled: enabled, classification: classifications[attempt]?.classification)
    }
    func classify(problem: UUID, attempt: UUID, key: String, input: AttemptMistakeRequest) async throws -> AttemptMistakeView {
        guard let e = evaluations[attempt]?.evaluation, input == AttemptMistakeRequest(evaluation: e), input.retryKey == key else { throw NetworkError.invalidResponse }
        if let saved = classifications[attempt] { return saved }
        guard enabled, !(try await readEvaluation(problem: problem, attempt: attempt)).historical else { throw NetworkError.invalidResponse }
        let artifact = AttemptMistakeClassification(schemaVersion: "attempt-mistake-classification-v1", id: UUID(), attemptId: attempt,
            ownerId: e.ownerId, problemSessionId: problem, evaluationId: e.id, executionId: e.executionId, source: e.source,
            workRevision: e.workRevision, taxonomyVersion: "attempt-taxonomy-v2", categoryVersion: "mistake-category-v1",
            policyVersion: "evidence-classifier-v1", verifierVersion: "bounded-attempt-v2", firstWrongStepId: nil,
            firstWrongStepIndex: nil, supportingEvidenceId: nil,
            decision: .init(status: .unsupported, code: nil, category: nil, authority: .deterministic, evidenceStrength: .none,
                reviewStatus: .required, skillId: nil, explanationKey: "attempt.classification.UNSUPPORTED"),
            createdAt: "2026-11-07T09:00:00Z", trainingEligible: false)
        let view = AttemptMistakeView(schemaVersion: "attempt-mistake-view-v1", attemptId: attempt, status: .completed,
            historical: false, creationEnabled: true, classification: artifact)
        classifications[attempt] = view; return view
    }
    func readEvaluation(problem: UUID, attempt: UUID) async throws -> AttemptEvaluationView {
        guard let work = try await readWork(problem: problem, attempt: attempt) else { throw NetworkError.invalidResponse }
        let historical = workCommitted.values.contains { $0.1.supersedesAttemptId == work.attemptId }
        if let saved = evaluations[attempt] {
            return .init(schemaVersion: saved.schemaVersion, attemptId: attempt, status: saved.status, historical: historical, creationEnabled: enabled, evaluation: saved.evaluation)
        }
        return .init(schemaVersion: "attempt-evaluation-view-v1", attemptId: attempt, status: historical ? .stale : .notEvaluated, historical: historical, creationEnabled: enabled, evaluation: nil)
    }
    func evaluate(problem: UUID, attempt: UUID, key: String, input: AttemptEvaluationRequest) async throws -> AttemptEvaluationView {
        if let result = evaluations[attempt] { return result }
        guard enabled, let work = try await readWork(problem: problem, attempt: attempt), input == AttemptEvaluationRequest(work: work, realDomainConfirmed: input.realDomainConfirmed) else { throw NetworkError.invalidResponse }
        let execution = UUID()
        let analysis = AttemptAnalysisResult(schemaVersion: "attempt-analysis-v2", verifierVersion: "bounded-attempt-v2", taxonomyVersion: "attempt-taxonomy-v2",
            executionId: execution, correlationId: execution.uuidString.lowercased(), outcome: .review, firstWrongStepIndex: nil, domain: "UNSUPPORTED",
            steps: work.steps.map { .init(index: $0.index, verdict: $0.index == 1 ? .uncertain : .notAssessed,
                reasonCode: $0.index == 1 ? "UNSUPPORTED_MATH" : "EARLIER_UNCERTAINTY", mistakeCode: nil, counterexampleStatus: "NOT_APPLICABLE", counterexample: nil) }, domainBasis: "UNRESOLVED")
        let answer = work.steps.first { $0.role == .finalAnswer }
        let evidence = AttemptEvaluation(schemaVersion: "attempt-evaluation-v1", id: UUID(), attemptId: attempt, ownerId: work.ownerId,
            problemSessionId: problem, source: work.source, workRevision: work.revision, policyVersion: "structured-evaluation-v1", executionId: execution,
            createdAt: "2026-11-06T12:00:00Z", stepIds: work.steps.map(\.id), analysis: analysis, realDomainConfirmed: input.realDomainConfirmed, finalStepId: answer?.id,
            finalAnswerStatus: answer == nil ? .notProvided : answer?.reviewStatus == "CONFIRMED" ? .inconclusive : .reviewRequired,
            finalAnswerEvidence: nil, trainingEligible: false)
        let result = AttemptEvaluationView(schemaVersion: "attempt-evaluation-view-v1", attemptId: attempt, status: .completed, historical: false, creationEnabled: enabled, evaluation: evidence)
        evaluations[attempt] = result; return result
    }
    func submitWork(problem: UUID, key: String, input: AttemptWorkRequest) async throws -> AttemptStructuredWork {
        if let saved = workCommitted[key] {
            guard saved.0 == input, saved.1.problemSessionId == problem else { throw NetworkError.invalidResponse }; return saved.1
        }
        guard enabled, input.isValid, input.source == source else { throw NetworkError.invalidResponse }
        let id = UUID(), now = Date()
        let prior = workCommitted.values.map(\.1).first { $0.attemptId == input.supersedesAttemptId }
        let work = AttemptStructuredWork(schemaVersion: "attempt-structured-work-v1", attemptId: id,
            ownerId: UUID(uuidString: "00000000-0000-0000-0000-000000007200")!, problemSessionId: problem,
            source: source, revision: (prior?.revision ?? 0) + 1, supersedesAttemptId: input.supersedesAttemptId,
            normalizationVersion: "structured-canonical-v1", createdAt: ISO8601DateFormatter().string(from: now),
            engine: input.engine, provenance: "CLIENT_ATTESTED", captureId: input.captureId, assetSessionId: nil, recognitionJobId: nil,
            provider: nil, model: nil, promptVersion: nil, observations: input.observations,
            steps: input.steps.enumerated().map { index, step in
                .init(id: step.id, index: index + 1, role: step.role, originalText: input.engine == "MANUAL" ? step.text : nil,
                    reviewedText: step.text, observationIds: step.observationIds, normalizedStatement: nil,
                    normalizationStatus: step.confirmed && step.role != .ambiguous && !step.text.contains("\n") ? "UNSUPPORTED" : "REVIEW_REQUIRED",
                    reviewStatus: step.confirmed && step.role != .ambiguous && !step.text.contains("\n") ? "CONFIRMED" : "REQUIRED")
            }, trainingEligible: false)
        let answer = input.steps.first { $0.role == .finalAnswer }?.text
        let lines = input.steps.filter { $0.role != .finalAnswer }.map(\.text)
        let submission = AttemptSubmission(source: source, steps: lines, schemaVersion: answer == nil ? nil : "attempt-submission-v3", finalAnswer: answer)
        let attempt = StudentAttempt(schemaVersion: answer == nil ? "student-attempt-v1" : "student-attempt-v2", id: id,
            problemSessionId: problem, source: source, sourceType: "MANUAL", normalizationVersion: "manual-canonical-v1", status: .submitted,
            steps: lines.enumerated().map { .init(index: $0.offset + 1, content: $0.element, normalizedStatement: nil) }, analysis: nil,
            createdAt: now, updatedAt: now, trainingEligible: false, finalAnswer: answer)
        committed[key] = (submission, attempt); workCommitted[key] = (input, work)
        if loseAcknowledgement { loseAcknowledgement = false; throw URLError(.networkConnectionLost) }
        return work
    }
    func submit(problem: UUID, key: String, submission: AttemptSubmission) async throws -> StudentAttempt {
        if let existing = committed[key] {
            guard existing.0 == submission, existing.1.problemSessionId == problem else { throw NetworkError.invalidResponse }
            return existing.1
        }
        guard enabled, submission.source == source, submission.isValid else { throw NetworkError.invalidResponse }
        let id = UUID()
        if let answer = submission.finalAnswer {
            let now = Date()
            let attempt = StudentAttempt(schemaVersion: "student-attempt-v2", id: id, problemSessionId: problem, source: source,
                sourceType: "MANUAL", normalizationVersion: "manual-canonical-v1", status: .submitted,
                steps: submission.steps.enumerated().map { .init(index: $0.offset + 1, content: $0.element, normalizedStatement: nil) },
                analysis: nil, createdAt: now, updatedAt: now, trainingEligible: false, finalAnswer: answer)
            committed[key] = (submission, attempt)
            if loseAcknowledgement { loseAcknowledgement = false; throw URLError(.networkConnectionLost) }
            return attempt
        }
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
