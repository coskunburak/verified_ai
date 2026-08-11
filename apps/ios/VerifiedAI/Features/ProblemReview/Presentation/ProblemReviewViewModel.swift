import Foundation
import Observation

@MainActor
@Observable
final class ProblemReviewViewModel {
    private let reviewAPI: ProblemReviewServicing
    private let networkMonitor: NetworkMonitoring
    private let logger: AppLogger

    private(set) var state: ProblemReviewState = .idle
    private(set) var review: ProblemParseReview?
    private(set) var draft: ProblemParseCorrectionDraft?
    private(set) var revisionHistory: ProblemParseRevisionHistory?
    private(set) var message: String?

    init(
        reviewAPI: ProblemReviewServicing,
        networkMonitor: NetworkMonitoring,
        logger: AppLogger
    ) {
        self.reviewAPI = reviewAPI
        self.networkMonitor = networkMonitor
        self.logger = logger
    }

    var canSubmit: Bool {
        guard let review, let draft else {
            return false
        }
        return review.canCorrect && draft.problem != review.currentParse.normalizedProblem && !state.isBusy
    }

    func load(problemSessionId: UUID, force: Bool = false) async {
        if !force, review?.problemSessionId == problemSessionId, draft != nil {
            return
        }
        guard networkMonitor.isReachable else {
            state = .failed("Problem review needs a network connection.")
            message = "Problem review needs a network connection."
            return
        }

        state = .loading
        message = nil
        do {
            let loadedReview = try await reviewAPI.getParseReview(problemSessionId: problemSessionId)
            review = loadedReview
            draft = ProblemParseCorrectionDraft(currentParse: loadedReview.currentParse)
            revisionHistory = nil
            state = .ready
        } catch {
            logger.warning("problem_review.load_failed")
            let text = failureMessage(for: error)
            state = .failed(text)
            message = text
        }
    }

    func refreshHistory() async {
        guard let problemSessionId = review?.problemSessionId else {
            return
        }
        do {
            revisionHistory = try await reviewAPI.getRevisionHistory(problemSessionId: problemSessionId)
        } catch {
            logger.warning("problem_review.history_failed")
            message = failureMessage(for: error)
        }
    }

    func submitCorrection() async {
        guard let review, let draft else {
            return
        }
        guard networkMonitor.isReachable else {
            message = "Saving needs a network connection."
            return
        }
        guard canSubmit else {
            message = review.canCorrect ? "No parse changes to save." : "This parse cannot be corrected."
            return
        }

        state = .saving
        message = nil
        do {
            let outcome = try await reviewAPI.createCorrection(draft, problemSessionId: review.problemSessionId)
            let updatedReview = try await reviewAPI.getParseReview(problemSessionId: review.problemSessionId)
            self.review = updatedReview
            self.draft = ProblemParseCorrectionDraft(currentParse: updatedReview.currentParse)
            self.revisionHistory = try? await reviewAPI.getRevisionHistory(problemSessionId: review.problemSessionId)
            self.state = .saved(outcome)
            self.message = "Revision \(outcome.revision) selected."
            logger.info("problem_review.correction_saved")
        } catch {
            logger.warning("problem_review.save_failed")
            let text = failureMessage(for: error)
            state = .failed(text)
            message = text
        }
    }

    func reset() {
        state = .idle
        review = nil
        draft = nil
        revisionHistory = nil
        message = nil
    }

    func setCorrectionReason(_ reason: ProblemParseCorrectionReason) {
        mutateDraft { draft in
            draft.correctionReason = reason
        }
    }

    func setTaskType(_ taskType: String) {
        mutateProblem { problem in
            problem.with(taskType: taskType.trimmedOrNil)
        }
    }

    func setProblemType(_ problemType: String) {
        mutateProblem { problem in
            problem.with(problemType: problemType.trimmedOrNil)
        }
    }

    func expression(id: String) -> ProblemParseExpression? {
        draft?.problem.expressions.first { $0.id == id }
    }

    func updateExpression(
        id: String,
        role: String? = nil,
        sourceText: String? = nil,
        normalizedText: String? = nil,
        displayLatex: String? = nil,
        relation: String? = nil
    ) {
        mutateProblem { problem in
            let expressions = problem.expressions.map { expression in
                guard expression.id == id else {
                    return expression
                }
                let nextDisplayLatex = displayLatex == nil ? expression.displayLatex : displayLatex?.trimmedOrNil
                let nextRelation = relation == nil ? expression.relation : relation?.trimmedOrNil
                return ProblemParseExpression(
                    id: expression.id,
                    role: role ?? expression.role,
                    sourceText: sourceText ?? expression.sourceText,
                    normalizedText: normalizedText ?? expression.normalizedText,
                    displayLatex: nextDisplayLatex,
                    relation: nextRelation,
                    sourceBlockIds: expression.sourceBlockIds
                )
            }
            return problem.with(expressions: expressions)
        }
    }

    func addExpression() {
        mutateProblem { problem in
            let sourceBlockIds = defaultSourceBlockIds(from: problem)
            let expression = ProblemParseExpression(
                id: "expr-\(UUID().uuidString.prefix(8).lowercased())",
                role: problem.expressions.isEmpty ? "PRIMARY" : "GIVEN",
                sourceText: "",
                normalizedText: "",
                displayLatex: nil,
                relation: nil,
                sourceBlockIds: sourceBlockIds
            )
            return problem.with(expressions: problem.expressions + [expression])
        }
    }

    func removeExpression(id: String) {
        mutateProblem { problem in
            guard problem.expressions.count > 1 else {
                return problem
            }
            return problem.with(expressions: problem.expressions.filter { $0.id != id })
        }
    }

    func variable(symbol: String) -> ProblemParseVariable? {
        draft?.problem.variables.first { $0.symbol == symbol }
    }

    func updateVariable(symbol: String, newSymbol: String? = nil, role: String? = nil) {
        mutateProblem { problem in
            let variables = problem.variables.map { variable in
                guard variable.symbol == symbol else {
                    return variable
                }
                return ProblemParseVariable(
                    symbol: newSymbol?.trimmedOrNil ?? variable.symbol,
                    role: role ?? variable.role,
                    sourceBlockIds: variable.sourceBlockIds
                )
            }
            return problem.with(variables: variables)
        }
    }

    func addVariable() {
        mutateProblem { problem in
            let sourceBlockIds = defaultSourceBlockIds(from: problem)
            let symbol = nextVariableSymbol(existing: Set(problem.variables.map(\.symbol)))
            let variable = ProblemParseVariable(symbol: symbol, role: "VARIABLE", sourceBlockIds: sourceBlockIds)
            return problem.with(variables: problem.variables + [variable])
        }
    }

    func removeVariable(symbol: String) {
        mutateProblem { problem in
            problem.with(variables: problem.variables.filter { $0.symbol != symbol })
        }
    }

    func constraint(id: String) -> ProblemParseConstraint? {
        draft?.problem.constraints.first { $0.id == id }
    }

    func updateConstraint(id: String, sourceText: String? = nil, normalizedText: String? = nil, variables: [String]? = nil) {
        mutateProblem { problem in
            let constraints = problem.constraints.map { constraint in
                guard constraint.id == id else {
                    return constraint
                }
                return ProblemParseConstraint(
                    id: constraint.id,
                    sourceText: sourceText ?? constraint.sourceText,
                    normalizedText: normalizedText ?? constraint.normalizedText,
                    variables: variables ?? constraint.variables,
                    explicit: constraint.explicit,
                    sourceBlockIds: constraint.sourceBlockIds
                )
            }
            return problem.with(constraints: constraints)
        }
    }

    func addConstraint() {
        mutateProblem { problem in
            let constraint = ProblemParseConstraint(
                id: "constraint-\(UUID().uuidString.prefix(8).lowercased())",
                sourceText: "",
                normalizedText: "",
                variables: problem.variables.map(\.symbol),
                explicit: true,
                sourceBlockIds: defaultSourceBlockIds(from: problem)
            )
            return problem.with(constraints: problem.constraints + [constraint])
        }
    }

    func removeConstraint(id: String) {
        mutateProblem { problem in
            problem.with(constraints: problem.constraints.filter { $0.id != id })
        }
    }

    func assumption(id: String) -> ProblemParseAssumption? {
        draft?.problem.assumptions.first { $0.id == id }
    }

    func updateAssumption(id: String, text: String) {
        mutateProblem { problem in
            let assumptions = problem.assumptions.map { assumption in
                guard assumption.id == id else {
                    return assumption
                }
                return ProblemParseAssumption(
                    id: assumption.id,
                    text: text,
                    explicit: assumption.explicit,
                    sourceBlockIds: assumption.sourceBlockIds
                )
            }
            return problem.with(assumptions: assumptions)
        }
    }

    func addAssumption() {
        mutateProblem { problem in
            let assumption = ProblemParseAssumption(
                id: "assumption-\(UUID().uuidString.prefix(8).lowercased())",
                text: "",
                explicit: true,
                sourceBlockIds: defaultSourceBlockIds(from: problem)
            )
            return problem.with(assumptions: problem.assumptions + [assumption])
        }
    }

    func removeAssumption(id: String) {
        mutateProblem { problem in
            problem.with(assumptions: problem.assumptions.filter { $0.id != id })
        }
    }

    private func mutateDraft(_ mutation: (inout ProblemParseCorrectionDraft) -> Void) {
        guard var draft else {
            return
        }
        mutation(&draft)
        self.draft = draft
        if case .saved = state {
            state = .ready
        }
    }

    private func mutateProblem(_ mutation: (NormalizedProblemParse) -> NormalizedProblemParse) {
        mutateDraft { draft in
            draft.problem = mutation(draft.problem)
        }
    }

    private func failureMessage(for error: Error) -> String {
        if case NetworkError.server(let problem) = error {
            switch problem.code {
            case "PARSE_REVISION_CONFLICT":
                return "The parse changed. Reload the latest revision."
            case "PARSE_CORRECTION_SCHEMA_INVALID", "PARSE_CORRECTION_SEMANTIC_INVALID", "PARSE_CORRECTION_INVALID":
                return "The edited parse needs another check."
            case "RATE_LIMIT_EXCEEDED":
                return "Too many parse corrections. Try again later."
            default:
                return problem.title
            }
        }
        if case NetworkError.decoding = error {
            return "Problem review response could not be read."
        }
        return "Problem review could not continue."
    }
}

private func defaultSourceBlockIds(from problem: NormalizedProblemParse) -> [String] {
    if let blockId = problem.sourceEvidenceRefs.first?.blockId {
        return [blockId]
    }
    if let blockId = problem.expressions.first?.sourceBlockIds.first {
        return [blockId]
    }
    return []
}

private func nextVariableSymbol(existing: Set<String>) -> String {
    for symbol in ["x", "y", "z", "a", "b", "c"] where !existing.contains(symbol) {
        return symbol
    }
    return "v\(existing.count + 1)"
}

private extension NormalizedProblemParse {
    func with(
        taskType: String? = nil,
        problemType: String? = nil,
        expressions: [ProblemParseExpression]? = nil,
        variables: [ProblemParseVariable]? = nil,
        constraints: [ProblemParseConstraint]? = nil,
        assumptions: [ProblemParseAssumption]? = nil
    ) -> NormalizedProblemParse {
        NormalizedProblemParse(
            schemaVersion: schemaVersion,
            supportStatus: supportStatus,
            unsupportedReason: unsupportedReason,
            subjectId: subjectId,
            topicId: topicId,
            taskType: taskType ?? self.taskType,
            problemType: problemType ?? self.problemType,
            expressions: expressions ?? self.expressions,
            variables: variables ?? self.variables,
            constraints: constraints ?? self.constraints,
            assumptions: assumptions ?? self.assumptions,
            uncertainty: uncertainty,
            sourceEvidenceRefs: sourceEvidenceRefs,
            visualQualityRisks: visualQualityRisks,
            reviewRequired: reviewRequired
        )
    }
}

private extension String {
    var trimmedOrNil: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
