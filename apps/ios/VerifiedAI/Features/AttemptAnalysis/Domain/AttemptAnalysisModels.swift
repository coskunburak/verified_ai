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
