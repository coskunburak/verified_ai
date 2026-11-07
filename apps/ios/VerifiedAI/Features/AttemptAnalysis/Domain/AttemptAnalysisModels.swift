import Foundation

struct AttemptSourceIdentity: Codable, Equatable, Sendable {
    let canonicalId: UUID
    let canonicalRevision: Int
    let parseId: UUID
    let parseRevision: Int
}
struct AttemptContext: Codable, Sendable {
    let schemaVersion: String
    let source: AttemptSourceIdentity
    let originalStatement: String?
    let enabled: Bool
    let handwriting: String
    let twinDrill: String
    let methodComparison: String
    var structuredWorkEnabled: Bool? = nil
}
enum AttemptStepVerdict: String, Codable, Sendable {
    case valid = "VALID_EQUIVALENT", wrong = "FIRST_WRONG_STEP", uncertain = "UNCERTAIN", notAssessed = "NOT_ASSESSED"
    var titleKey: String { "attempt.verdict.\(rawValue)" }
    var symbol: String {
        switch self { case .valid: "checkmark.circle"; case .wrong: "exclamationmark.circle"; case .uncertain: "questionmark.circle"; case .notAssessed: "minus.circle" }
    }
}
struct AttemptCounterexample: Codable, Sendable {
    struct Exact: Codable, Sendable { let numerator: String; let denominator: String }
    let schemaVersion: String
    let witnessType: String
    let variableValues: [String: Exact]
    let previousStepEvaluation: String
    let submittedStepEvaluation: String
    let claimRefuted: String
    let domainSatisfied: Bool
    let verifierVersion: String
    let explanationCode: String
}
struct AttemptAnalysisResult: Codable, Sendable {
    enum Outcome: String, Codable, Sendable {
        case progress = "INCOMPLETE_VALID_SO_FAR", error = "STEP_ERROR_FOUND", review = "NEEDS_REVIEW"
    }
    struct StepDiff: Codable, Sendable {
        struct TokenRange: Codable, Sendable { let start: Int; let end: Int }
        let schemaVersion: String; let originalStepId: String; let submittedStepId: String
        let changedExpressionPath: String; let originalTokenRange: TokenRange?; let submittedTokenRange: TokenRange?
        let diffCategory: String; let supportingEvidenceId: String; let confidence: String; let displayFallback: String
    }
    struct Diagnosis: Codable, Sendable {
        let code: String; let evidenceId: String; let explanationKey: String
        let changedExpressionPath: String; let displayFallback: String
        var classifierPolicyVersion: String? = nil
    }
    struct Step: Codable, Identifiable, Sendable {
        let index: Int
        let verdict: AttemptStepVerdict
        let reasonCode: String
        let mistakeCode: String?
        let counterexampleStatus: String
        let counterexample: AttemptCounterexample?
        var diagnosis: Diagnosis? = nil
        var diff: StepDiff? = nil
        var id: Int { index }
    }
    let schemaVersion: String
    let verifierVersion: String
    let taxonomyVersion: String
    let executionId: UUID
    let correlationId: String
    let outcome: Outcome
    let firstWrongStepIndex: Int?
    let domain: String
    let steps: [Step]
    var naturalDomain: AttemptJSON? = nil
    var domainBasis: String? = nil
    var solutionSet: AttemptJSON? = nil
}
struct StudentAttempt: Codable, Identifiable, Sendable {
    enum Status: String, Codable, Sendable { case submitted = "SUBMITTED", analyzed = "ANALYZED", stale = "STALE_SOURCE" }
    struct Step: Codable, Identifiable, Sendable {
        let index: Int
        let content: String
        let normalizedStatement: String?
        var id: Int { index }
    }
    let schemaVersion: String
    let id: UUID
    let problemSessionId: UUID
    let source: AttemptSourceIdentity
    let sourceType: String
    let normalizationVersion: String
    let status: Status
    let steps: [Step]
    let analysis: AttemptAnalysisResult?
    let createdAt: Date
    let updatedAt: Date
    let trainingEligible: Bool
    var captureReview: AttemptCaptureReview? = nil
    var finalAnswer: String? = nil

    func validated(problem: UUID) throws -> Self {
        let answer = schemaVersion == "student-attempt-v2"
        guard ["student-attempt-v1", "student-attempt-v2"].contains(schemaVersion), problemSessionId == problem, !trainingEligible,
              updatedAt >= createdAt,
              (answer ? (finalAnswer.map(AttemptSubmission.validText) == true && analysis == nil && captureReview == nil) : finalAnswer == nil),
              sourceType == "MANUAL", ["manual-canonical-v1", "manual-canonical-v2"].contains(normalizationVersion), source.canonicalRevision > 0, source.parseRevision > 0,
              (answer ? (0...11).contains(steps.count) : (1...12).contains(steps.count)),
              steps.map(\.index) == steps.indices.map({ $0 + 1 }),
              !answer || steps.allSatisfy({ $0.normalizedStatement == nil }),
              steps.allSatisfy({ !$0.content.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                  && $0.content.utf16.count <= 256 && $0.content.utf8.count <= 1024 }),
              (status == .analyzed) == (analysis != nil) else { throw NetworkError.invalidResponse }
        if let a = analysis {
            let v2 = a.schemaVersion == "attempt-analysis-v2"
            guard a.schemaVersion == (v2 ? "attempt-analysis-v2" : "attempt-analysis-v1"),
                  a.verifierVersion == (v2 ? "bounded-attempt-v2" : "bounded-attempt-v1"),
                  a.taxonomyVersion == (v2 ? "attempt-taxonomy-v2" : "attempt-taxonomy-v1"), a.executionId == id,
                  a.correlationId.lowercased() == id.uuidString.lowercased(), a.steps.count == steps.count,
                  a.steps.map(\.index) == steps.map(\.index), [v2 ? "REAL_DOMAIN" : "REAL_POLYNOMIAL", "UNSUPPORTED"].contains(a.domain) else { throw NetworkError.invalidResponse }
            var stopped = false
            var stopReason: String?
            for step in a.steps {
                guard stopped == (step.verdict == .notAssessed),
                      (step.verdict == .wrong) == (step.mistakeCode != nil),
                      step.mistakeCode == nil || (v2 ? AttemptMistakes.codes.contains(step.mistakeCode!) : step.mistakeCode == "ALG_TRANSFORMATION_INVALID"),
                      ["AVAILABLE", "COUNTEREXAMPLE_UNAVAILABLE", "NOT_APPLICABLE"].contains(step.counterexampleStatus),
                      (step.verdict == .wrong) == (step.counterexampleStatus != "NOT_APPLICABLE"),
                      (step.counterexampleStatus == "AVAILABLE") == (step.counterexample != nil) else { throw NetworkError.invalidResponse }
                if v2 && step.verdict == .wrong {
                    guard let d = step.diagnosis, d.code == step.mistakeCode,
                          d.classifierPolicyVersion == nil || d.classifierPolicyVersion == "structural-signatures-v1",
                          d.evidenceId.lowercased() == id.uuidString.lowercased() + ":\(step.index)",
                          d.explanationKey == "attempt.mistake." + d.code,
                          ["STEP_LEVEL", "SUBEXPRESSION"].contains(d.displayFallback) else { throw NetworkError.invalidResponse }
                }
                if let diff = step.diff {
                    guard v2, step.verdict == .wrong, diff.schemaVersion == "step-diff-v1",
                          diff.supportingEvidenceId == step.diagnosis?.evidenceId,
                          diff.changedExpressionPath == step.diagnosis?.changedExpressionPath,
                          diff.displayFallback == step.diagnosis?.displayFallback,
                          diff.submittedStepId.lowercased() == id.uuidString.lowercased() + ":\(step.index)",
                          diff.originalStepId.lowercased() == id.uuidString.lowercased() + ":\(step.index - 1)",
                          ["STEP_ONLY", "EXACT_STRUCTURAL"].contains(diff.confidence) else { throw NetworkError.invalidResponse }
                }
                if step.verdict == .valid {
                    guard a.domain == (v2 ? "REAL_DOMAIN" : "REAL_POLYNOMIAL"), ["EXACT_IDENTITY", "SAME_SOLUTION_SET"].contains(step.reasonCode) else { throw NetworkError.invalidResponse }
                }
                if step.verdict == .wrong {
                    guard a.domain == (v2 ? "REAL_DOMAIN" : "REAL_POLYNOMIAL"), step.reasonCode == "NON_EQUIVALENT" else { throw NetworkError.invalidResponse }
                }
                if step.verdict == .uncertain {
                    guard ["UNSUPPORTED_MATH", "TIMEOUT", "RESOURCE_FAILURE", "WORKER_FAILURE", "ADMISSION_LIMIT", "DISABLED", "INVALID_RESPONSE", "UNKNOWN_DOMAIN", "UNDEFINED_SOURCE"].contains(step.reasonCode) else { throw NetworkError.invalidResponse }
                }
                if step.verdict == .notAssessed {
                    guard step.reasonCode == stopReason else { throw NetworkError.invalidResponse }
                }
                if step.verdict == .wrong || step.verdict == .uncertain {
                    stopped = true
                    stopReason = step.verdict == .wrong ? "EARLIER_DIVERGENCE" : "EARLIER_UNCERTAINTY"
                }
                if let witness = step.counterexample {
                    guard step.verdict == .wrong, witness.schemaVersion == "attempt-witness-v1", witness.domainSatisfied,
                          witness.verifierVersion == a.verifierVersion, witness.variableValues.count <= 1,
                          witness.previousStepEvaluation.count <= 650, witness.submittedStepEvaluation.count <= 650,
                          witness.variableValues.allSatisfy({ key, value in
                              key.range(of: #"^[A-Za-z][A-Za-z0-9_]{0,15}$"#, options: .regularExpression) != nil
                              && value.numerator.count <= 320 && value.denominator.count <= 320
                              && value.numerator.range(of: #"^-?(0|[1-9][0-9]*)$"#, options: .regularExpression) != nil
                              && value.denominator.range(of: #"^[1-9][0-9]*$"#, options: .regularExpression) != nil
                          }), witness.previousStepEvaluation != witness.submittedStepEvaluation else { throw NetworkError.invalidResponse }
                    if witness.witnessType == "EQUATION_TRUTH" {
                        guard witness.claimRefuted == "EQUATION_EQUIVALENCE", witness.explanationCode == "DIFFERENT_TRUTH",
                              Set([witness.previousStepEvaluation, witness.submittedStepEvaluation]) == Set(["true", "false"]) else { throw NetworkError.invalidResponse }
                    } else {
                        guard witness.witnessType == "EXPRESSION_VALUES", witness.claimRefuted == "EXPRESSION_IDENTITY",
                              witness.explanationCode == "UNEQUAL_VALUES",
                              [witness.previousStepEvaluation, witness.submittedStepEvaluation].allSatisfy({
                                  $0.range(of: #"^-?(0|[1-9][0-9]*)(/[1-9][0-9]*)?$"#, options: .regularExpression) != nil
                              }) else { throw NetworkError.invalidResponse }
                    }
                }
            }
            let wrong = a.steps.filter { $0.verdict == .wrong }.map(\.index)
            guard wrong == (a.firstWrongStepIndex.map { [$0] } ?? []),
                  (a.outcome == .error) == !wrong.isEmpty,
                  (a.outcome == .progress) == a.steps.allSatisfy({ $0.verdict == .valid }) else { throw NetworkError.invalidResponse }
        }
        return self
    }
}
struct AttemptSubmission: Codable, Equatable, Sendable {
    let source: AttemptSourceIdentity
    let steps: [String]
    var realDomainConfirmed: Bool? = nil
    var captureReview: AttemptCaptureReview? = nil
    var schemaVersion: String? = nil
    var finalAnswer: String? = nil
    static func validText(_ text: String) -> Bool {
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && text.utf16.count <= 256
            && text.utf8.count <= 1024 && !text.contains("\0")
    }
    var isValid: Bool {
        guard source.canonicalRevision > 0, source.parseRevision > 0, steps.allSatisfy(Self.validText) else { return false }
        if schemaVersion == "attempt-submission-v3" {
            return steps.count <= 11 && finalAnswer.map(Self.validText) == true && captureReview == nil && realDomainConfirmed == nil
        }
        return schemaVersion == nil && finalAnswer == nil && (1...12).contains(steps.count)
    }
}
protocol AttemptAnalysisServicing: Sendable {
    func readEvaluation(problem: UUID, attempt: UUID) async throws -> AttemptEvaluationView
    func readClassification(problem: UUID, attempt: UUID) async throws -> AttemptMistakeView
    func classify(problem: UUID, attempt: UUID, key: String, input: AttemptMistakeRequest) async throws -> AttemptMistakeView
    func evaluate(problem: UUID, attempt: UUID, key: String, input: AttemptEvaluationRequest) async throws -> AttemptEvaluationView
    func submitWork(problem: UUID, key: String, input: AttemptWorkRequest) async throws -> AttemptStructuredWork
    func readWork(problem: UUID, attempt: UUID) async throws -> AttemptStructuredWork?
    func context(problem: UUID) async throws -> AttemptContext
    func list(problem: UUID) async throws -> [StudentAttempt]
    func submit(problem: UUID, key: String, submission: AttemptSubmission) async throws -> StudentAttempt
}

enum AttemptMistakes {
    static let codes: Set<String> = ["INVALID_TRANSFORMATION", "ALG_SIGN_FLIP_DISTRIBUTE", "ALG_FRESHMAN_DREAM_POWER", "ALG_FRACTION_ADD_DENOM", "ALG_ROOT_LOST_BY_DIVISION", "DOM_EXTRANEOUS_ROOT_DENOM", "DOM_EVEN_ROOT_NEGATIVE", "DOM_LOG_NON_POSITIVE", "INEQ_NEGATIVE_MULT_NO_FLIP", "INEQ_CROSS_MULT_VARIABLE", "ABS_SINGLE_BRANCH_ONLY", "FUNC_COMPOSITION_ORDER", "TRIG_PERIOD_OMITTED", "CALC_LIMIT_INDETERMINATE_SUB", "CALC_CHAIN_RULE_OMITTED", "CALC_PRODUCT_QUOTIENT_NAIVE", "CALC_INTEGRAL_BOUNDS_SWAPPED", "WORD_VARIABLE_MISASSIGNMENT"]
}

struct AttemptCaptureReview: Codable, Equatable, Sendable {
    let schemaVersion: String
    let engine: String
    let rawLines: [String]
    let confirmed: Bool
    let assetSessionId: UUID?
    let recognitionJobId: UUID?
}

/// Structured syntax is a separate artifact; it never upgrades an attempt's assessment.
struct AttemptWorkRequest: Codable, Equatable, Sendable {
    struct Box: Codable, Equatable, Sendable {
        let x: Double; let y: Double; let width: Double; let height: Double
        var isValid: Bool { [x, y, width, height].allSatisfy { $0.isFinite && (0...1).contains($0) } && x + width <= 1 && y + height <= 1 }
    }
    struct Observation: Codable, Equatable, Identifiable, Sendable {
        let id: UUID; let rawText: String; let confidence: Double?; let box: Box?
        enum CodingKeys: String, CodingKey { case id, rawText, confidence, box }
        func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: CodingKeys.self)
            try c.encode(id, forKey: .id); try c.encode(rawText, forKey: .rawText)
            try c.encode(confidence, forKey: .confidence); try c.encode(box, forKey: .box)
        }
    }
    enum Role: String, Codable, CaseIterable, Sendable { case intermediate = "INTERMEDIATE", finalAnswer = "FINAL_ANSWER", ambiguous = "AMBIGUOUS" }
    struct Step: Codable, Equatable, Identifiable, Sendable {
        var id: UUID; var text: String; var role: Role; var observationIds: [UUID]; var confirmed: Bool
    }
    var schemaVersion = "attempt-structured-work-request-v1"
    var source: AttemptSourceIdentity
    var engine: String
    var captureId: UUID?
    var assetSessionId: UUID?
    var recognitionJobId: UUID?
    var supersedesAttemptId: UUID?
    var observations: [Observation]
    var steps: [Step]
    enum CodingKeys: String, CodingKey { case schemaVersion, source, engine, captureId, assetSessionId, recognitionJobId, supersedesAttemptId, observations, steps }
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(schemaVersion, forKey: .schemaVersion); try c.encode(source, forKey: .source); try c.encode(engine, forKey: .engine)
        try c.encode(captureId, forKey: .captureId); try c.encode(assetSessionId, forKey: .assetSessionId)
        try c.encode(recognitionJobId, forKey: .recognitionJobId); try c.encode(supersedesAttemptId, forKey: .supersedesAttemptId)
        try c.encode(observations, forKey: .observations); try c.encode(steps, forKey: .steps)
    }
    var isValid: Bool {
        guard schemaVersion == "attempt-structured-work-request-v1", source.canonicalRevision > 0, source.parseRevision > 0,
              (1...12).contains(steps.count), observations.count <= 12, Set(steps.map(\.id)).count == steps.count,
              Set(observations.map(\.id)).count == observations.count,
              steps.filter({ $0.role == .finalAnswer }).count <= 1,
              steps.allSatisfy({ AttemptSubmission.validText($0.text) && Set($0.observationIds).count == $0.observationIds.count }),
              observations.allSatisfy({ AttemptSubmission.validText($0.rawText) && ($0.confidence.map { $0.isFinite && (0...1).contains($0) } ?? true) && ($0.box?.isValid ?? true) }),
              Set(steps.flatMap(\.observationIds)) == Set(observations.map(\.id)) else { return false }
        switch engine {
        case "MANUAL": return observations.isEmpty && captureId == nil && assetSessionId == nil && recognitionJobId == nil && steps.allSatisfy { $0.observationIds.isEmpty }
        case "NATIVE_VISION": return !observations.isEmpty && captureId != nil && assetSessionId == nil && recognitionJobId == nil && steps.allSatisfy { !$0.observationIds.isEmpty }
        case "GATEWAY_RECOGNITION": return !observations.isEmpty && captureId == nil && assetSessionId != nil && recognitionJobId != nil && steps.allSatisfy { !$0.observationIds.isEmpty }
        default: return false
        }
    }
}
struct AttemptStructuredWork: Codable, Sendable {
    struct Step: Codable, Identifiable, Sendable {
        let id: UUID; let index: Int; let role: AttemptWorkRequest.Role
        let originalText: String?; let reviewedText: String; let observationIds: [UUID]
        let normalizedStatement: String?; let normalizationStatus: String; let reviewStatus: String
    }
    let schemaVersion: String; let attemptId: UUID; let ownerId: UUID; let problemSessionId: UUID
    let source: AttemptSourceIdentity; let revision: Int; let supersedesAttemptId: UUID?; let normalizationVersion: String
    let createdAt: String; let engine: String; let provenance: String; let captureId: UUID?; let assetSessionId: UUID?; let recognitionJobId: UUID?
    let provider: String?; let model: String?; let promptVersion: String?
    let observations: [AttemptWorkRequest.Observation]; let steps: [Step]; let trainingEligible: Bool
    func validated(problem: UUID) throws -> Self {
        let input = AttemptWorkRequest(source: source, engine: engine, captureId: captureId, assetSessionId: assetSessionId,
            recognitionJobId: recognitionJobId, supersedesAttemptId: supersedesAttemptId, observations: observations,
            steps: steps.map { .init(id: $0.id, text: $0.reviewedText, role: $0.role, observationIds: $0.observationIds, confirmed: $0.reviewStatus == "CONFIRMED") })
        guard schemaVersion == "attempt-structured-work-v1", normalizationVersion == "structured-canonical-v1", !trainingEligible,
              problemSessionId == problem, input.isValid, (1...30).contains(revision), (revision == 1) == (supersedesAttemptId == nil),
              provenance == (engine == "GATEWAY_RECOGNITION" ? "SERVER_RECORDED" : "CLIENT_ATTESTED"),
              engine == "GATEWAY_RECOGNITION" ? (provider != nil && model != nil && promptVersion != nil) : (provider == nil && model == nil && promptVersion == nil),
              steps.map(\.index) == steps.indices.map({ $0 + 1 }),
              steps.allSatisfy({ step in
                  ["NORMALIZED", "UNSUPPORTED", "REVIEW_REQUIRED"].contains(step.normalizationStatus)
                  && ["CONFIRMED", "REQUIRED"].contains(step.reviewStatus)
                  && (step.normalizationStatus == "NORMALIZED") == (step.normalizedStatement != nil)
                  && (step.normalizedStatement?.utf16.count ?? 0) <= 1800
                  && ((step.reviewStatus == "REQUIRED") == (step.normalizationStatus == "REVIEW_REQUIRED"))
                  && (!(step.role == .ambiguous || step.reviewedText.contains("\n") || step.reviewedText.contains("\r")) || step.reviewStatus == "REQUIRED")
                  && (engine == "MANUAL" ? step.originalText == step.reviewedText : step.originalText == nil)
              }) else { throw NetworkError.invalidResponse }
        return self
    }
}
extension AttemptAnalysisServicing {
    func submitWork(problem: UUID, key: String, input: AttemptWorkRequest) async throws -> AttemptStructuredWork { throw NetworkError.invalidResponse }
    func readWork(problem: UUID, attempt: UUID) async throws -> AttemptStructuredWork? { nil }
}

struct AttemptEvaluationRequest: Codable, Equatable, Sendable {
    let schemaVersion: String
    let source: AttemptSourceIdentity
    let workRevision: Int
    let realDomainConfirmed: Bool
    init(work: AttemptStructuredWork, realDomainConfirmed: Bool = false) {
        schemaVersion = "attempt-evaluation-request-v1"; source = work.source; workRevision = work.revision
        self.realDomainConfirmed = realDomainConfirmed
    }
}
struct AttemptEvaluation: Codable, Sendable {
    enum FinalStatus: String, Codable, Sendable {
        case verified = "VERIFIED", disproven = "DISPROVEN", inconclusive = "INCONCLUSIVE"
        case notProvided = "NOT_PROVIDED", reviewRequired = "REVIEW_REQUIRED"
    }
    let schemaVersion: String; let id: UUID; let attemptId: UUID; let ownerId: UUID; let problemSessionId: UUID
    let source: AttemptSourceIdentity; let workRevision: Int; let policyVersion: String
    let executionId: UUID; let createdAt: String; let stepIds: [UUID]; let analysis: AttemptAnalysisResult
    let realDomainConfirmed: Bool
    let finalStepId: UUID?; let finalAnswerStatus: FinalStatus; let finalAnswerEvidence: AttemptAnalysisResult?
    let trainingEligible: Bool
}
struct AttemptEvaluationView: Codable, Sendable {
    enum Status: String, Codable, Sendable {
        case notEvaluated = "NOT_EVALUATED", inProgress = "IN_PROGRESS", retryable = "RETRYABLE", completed = "COMPLETED", stale = "STALE"
    }
    let schemaVersion: String; let attemptId: UUID; let status: Status
    let historical: Bool; let creationEnabled: Bool; let evaluation: AttemptEvaluation?
    func validated(work: AttemptStructuredWork) throws -> Self {
        guard schemaVersion == "attempt-evaluation-view-v1", attemptId == work.attemptId,
              (status == .completed) == (evaluation != nil), status != .stale || historical else { throw NetworkError.invalidResponse }
        if let e = evaluation {
            guard e.schemaVersion == "attempt-evaluation-v1", e.attemptId == work.attemptId, e.ownerId == work.ownerId,
                  e.problemSessionId == work.problemSessionId, e.source == work.source, e.workRevision == work.revision,
                  e.policyVersion == "structured-evaluation-v1", !e.trainingEligible, e.stepIds == work.steps.map(\.id),
                  e.finalStepId == work.steps.first(where: { $0.role == .finalAnswer })?.id,
                  (e.finalStepId == nil) == (e.finalAnswerStatus == .notProvided),
                  e.analysis.schemaVersion == "attempt-analysis-v2" else { throw NetworkError.invalidResponse }
            guard e.realDomainConfirmed || e.analysis.domainBasis != "REVIEWED_REAL_TASK" else { throw NetworkError.invalidResponse }
            try validateEvidence(e.analysis, execution: e.executionId, work: work, count: work.steps.count)
            if let final = e.finalAnswerEvidence {
                try validateEvidence(final, execution: e.executionId, work: work, count: 1)
            }
            if e.finalAnswerStatus == .verified {
                guard e.finalAnswerEvidence?.steps.first?.verdict == .valid else { throw NetworkError.invalidResponse }
            }
            if e.finalAnswerStatus == .disproven {
                guard e.finalAnswerEvidence?.steps.first?.verdict == .wrong else { throw NetworkError.invalidResponse }
            }
            if [.notProvided, .reviewRequired].contains(e.finalAnswerStatus), e.finalAnswerEvidence != nil { throw NetworkError.invalidResponse }
            for (index, step) in work.steps.enumerated() where step.normalizedStatement == nil {
                guard [.uncertain, .notAssessed].contains(e.analysis.steps[index].verdict) else { throw NetworkError.invalidResponse }
            }
        }
        return self
    }
    private func validateEvidence(_ evidence: AttemptAnalysisResult, execution: UUID, work: AttemptStructuredWork, count: Int) throws {
        guard evidence.schemaVersion == "attempt-analysis-v2", evidence.steps.count == count else { throw NetworkError.invalidResponse }
        // Reuse the existing strict v2 evidence validator with a transient transport envelope.
        // This is never saved or presented as the learner's original StudentAttempt.
        let date = Date(timeIntervalSince1970: 0)
        let envelope = StudentAttempt(schemaVersion: "student-attempt-v1", id: execution, problemSessionId: work.problemSessionId,
            source: work.source, sourceType: "MANUAL", normalizationVersion: "manual-canonical-v2", status: .analyzed,
            steps: (1...count).map { .init(index: $0, content: "evidence", normalizedStatement: nil) }, analysis: evidence,
            createdAt: date, updatedAt: date, trainingEligible: false)
        _ = try envelope.validated(problem: work.problemSessionId)
    }
}
extension AttemptAnalysisServicing {
    func readEvaluation(problem: UUID, attempt: UUID) async throws -> AttemptEvaluationView {
        AttemptEvaluationView(schemaVersion: "attempt-evaluation-view-v1", attemptId: attempt, status: .notEvaluated, historical: false, creationEnabled: false, evaluation: nil)
    }
    func evaluate(problem: UUID, attempt: UUID, key: String, input: AttemptEvaluationRequest) async throws -> AttemptEvaluationView {
        throw NetworkError.invalidResponse
    }
}

struct AttemptMistakeRequest: Codable, Equatable, Sendable {
    let schemaVersion: String
    let evaluationId: UUID
    let source: AttemptSourceIdentity
    let workRevision: Int
    init(evaluation: AttemptEvaluation) {
        schemaVersion = "attempt-mistake-request-v1"; evaluationId = evaluation.id
        source = evaluation.source; workRevision = evaluation.workRevision
    }
    // Stable across process death; server binds this key to owned immutable evidence.
    var retryKey: String { "classification-v1-" + evaluationId.uuidString.lowercased() }
}
struct AttemptMistakeClassification: Codable, Sendable {
    enum Category: String, Codable, Sendable {
        case concept = "CONCEPT_ERROR", formula = "FORMULA_ERROR", algebra = "ALGEBRA_ERROR", sign = "SIGN_ERROR"
        case calculation = "CALCULATION_ERROR", interpretation = "INTERPRETATION_ERROR", unit = "UNIT_ERROR"
        case notation = "NOTATION_ERROR", incomplete = "INCOMPLETE_SOLUTION", otherReviewed = "OTHER_REVIEWED"
    }
    enum Status: String, Codable, Sendable {
        case signature = "SIGNATURE_SUPPORTED", invalid = "INVALID_TRANSFORMATION", uncertain = "UNCERTAIN"
        case unsupported = "UNSUPPORTED", sourceReview = "SOURCE_REVIEW_REQUIRED", incomplete = "INCOMPLETE_WORK", noError = "NO_ERROR_ESTABLISHED"
    }
    enum Strength: String, Codable, Sendable { case structural = "STRUCTURAL_AND_DISPROOF", disproof = "EXACT_DISPROOF", none = "NONE" }
    enum Review: String, Codable, Sendable { case automatic = "AUTO_DETECTED", required = "REQUIRED" }
    enum Authority: String, Codable, Sendable {
        case deterministic = "DETERMINISTIC_EVIDENCE", model = "MODEL_SUGGESTION", user = "USER_CORRECTION", human = "HUMAN_REVIEW"
    }
    struct Decision: Codable, Sendable {
        let status: Status; let code: String?; let category: Category?; let authority: Authority
        let evidenceStrength: Strength; let reviewStatus: Review; let skillId: String?; let explanationKey: String
    }
    let schemaVersion: String; let id: UUID; let attemptId: UUID; let ownerId: UUID; let problemSessionId: UUID
    let evaluationId: UUID; let executionId: UUID; let source: AttemptSourceIdentity; let workRevision: Int
    let taxonomyVersion: String; let categoryVersion: String; let policyVersion: String; let verifierVersion: String
    let firstWrongStepId: UUID?; let firstWrongStepIndex: Int?; let supportingEvidenceId: String?
    let decision: Decision; let createdAt: String; let trainingEligible: Bool

    func validated(evaluation e: AttemptEvaluation) throws -> Self {
        guard schemaVersion == "attempt-mistake-classification-v1", attemptId == e.attemptId, ownerId == e.ownerId,
              problemSessionId == e.problemSessionId, evaluationId == e.id, executionId == e.executionId,
              source == e.source, workRevision == e.workRevision, taxonomyVersion == "attempt-taxonomy-v2",
              categoryVersion == "mistake-category-v1", policyVersion == "evidence-classifier-v1",
              verifierVersion == e.analysis.verifierVersion, !trainingEligible, decision.authority == .deterministic,
              Self.validTimestamp(createdAt) else { throw NetworkError.invalidResponse }
        if decision.status == .signature || decision.status == .invalid {
            guard let index = firstWrongStepIndex, index == e.analysis.firstWrongStepIndex,
                  (1...e.stepIds.count).contains(index), firstWrongStepId == e.stepIds[index - 1],
                  e.analysis.steps[index - 1].verdict == .wrong,
                  supportingEvidenceId == e.analysis.steps[index - 1].diagnosis?.evidenceId,
                  supportingEvidenceId == executionId.uuidString.lowercased() + ":\(index)",
                  (decision.code == e.analysis.steps[index - 1].mistakeCode ||
                    decision.status == .invalid && decision.code == "INVALID_TRANSFORMATION" && e.analysis.steps[index - 1].diagnosis?.classifierPolicyVersion == nil) else { throw NetworkError.invalidResponse }
            if decision.status == .signature {
                guard let code = decision.code, let category = decision.category, Self.categories[code] == category,
                      e.analysis.steps[index - 1].diagnosis?.classifierPolicyVersion == "structural-signatures-v1",
                      decision.evidenceStrength == .structural, decision.reviewStatus == .automatic,
                      decision.explanationKey == "attempt.mistake." + code,
                      decision.skillId == nil || decision.skillId == Self.skills[code] else { throw NetworkError.invalidResponse }
            } else {
                guard ["INVALID_TRANSFORMATION", "ALG_TRANSFORMATION_INVALID"].contains(decision.code ?? ""),
                      decision.category == nil, decision.skillId == nil, decision.evidenceStrength == .disproof,
                      decision.reviewStatus == .required, decision.explanationKey == "attempt.classification.INVALID_TRANSFORMATION" else { throw NetworkError.invalidResponse }
            }
        } else {
            guard e.analysis.firstWrongStepIndex == nil, firstWrongStepId == nil, firstWrongStepIndex == nil, supportingEvidenceId == nil,
                  decision.code == nil, decision.category == nil, decision.skillId == nil,
                  decision.evidenceStrength == .none, decision.reviewStatus == .required,
                  decision.explanationKey == "attempt.classification." + decision.status.rawValue else { throw NetworkError.invalidResponse }
        }
        return self
    }
    private static func validTimestamp(_ value: String) -> Bool {
        guard !value.isEmpty, value.count <= 40 else { return false }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.date(from: value) != nil || ISO8601DateFormatter().date(from: value) != nil
    }
    static let categories: [String: Category] = [
        "ALG_SIGN_FLIP_DISTRIBUTE": .sign,
        "ALG_FRESHMAN_DREAM_POWER": .algebra,
        "ALG_FRACTION_ADD_DENOM": .algebra,
        "ALG_ROOT_LOST_BY_DIVISION": .algebra,
        "DOM_EXTRANEOUS_ROOT_DENOM": .concept,
        "DOM_EVEN_ROOT_NEGATIVE": .concept,
        "DOM_LOG_NON_POSITIVE": .concept,
        "INEQ_NEGATIVE_MULT_NO_FLIP": .sign,
        "INEQ_CROSS_MULT_VARIABLE": .algebra,
        "ABS_SINGLE_BRANCH_ONLY": .algebra,
        "FUNC_COMPOSITION_ORDER": .concept,
        "TRIG_PERIOD_OMITTED": .concept,
        "CALC_LIMIT_INDETERMINATE_SUB": .concept,
        "CALC_CHAIN_RULE_OMITTED": .formula,
        "CALC_PRODUCT_QUOTIENT_NAIVE": .formula,
        "CALC_INTEGRAL_BOUNDS_SWAPPED": .sign,
        "WORD_VARIABLE_MISASSIGNMENT": .interpretation
    ]
    static let skills: [String: String] = [
        "ALG_SIGN_FLIP_DISTRIBUTE": "MATH.ALGEBRA.SIMPLIFY_EXPRESSIONS",
        "ALG_FRACTION_ADD_DENOM": "MATH.ARITHMETIC.FRACTIONS",
        "INEQ_NEGATIVE_MULT_NO_FLIP": "MATH.EQUATIONS.INEQUALITIES_BASIC",
        "FUNC_COMPOSITION_ORDER": "MATH.FUNCTIONS.COMPOSITION",
        "CALC_CHAIN_RULE_OMITTED": "MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE"
    ]
}
struct AttemptMistakeView: Codable, Sendable {
    enum Status: String, Codable, Sendable { case notClassified = "NOT_CLASSIFIED", completed = "COMPLETED", stale = "STALE" }
    let schemaVersion: String; let attemptId: UUID; let status: Status; let historical: Bool; let creationEnabled: Bool
    let classification: AttemptMistakeClassification?
    func validated(evaluation: AttemptEvaluation) throws -> Self {
        guard schemaVersion == "attempt-mistake-view-v1", attemptId == evaluation.attemptId,
              (status == .completed) == (classification != nil), status != .stale || historical else { throw NetworkError.invalidResponse }
        if let classification { _ = try classification.validated(evaluation: evaluation) }
        return self
    }
}
extension AttemptAnalysisServicing {
    func readClassification(problem: UUID, attempt: UUID) async throws -> AttemptMistakeView {
        .init(schemaVersion: "attempt-mistake-view-v1", attemptId: attempt, status: .notClassified, historical: false, creationEnabled: false, classification: nil)
    }
    func classify(problem: UUID, attempt: UUID, key: String, input: AttemptMistakeRequest) async throws -> AttemptMistakeView { throw NetworkError.invalidResponse }
}
