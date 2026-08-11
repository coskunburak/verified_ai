import Foundation

protocol ProblemReviewServicing: Sendable {
    func getParseReview(problemSessionId: UUID) async throws -> ProblemParseReview
    func createCorrection(_ draft: ProblemParseCorrectionDraft, problemSessionId: UUID) async throws -> ProblemParseCorrectionOutcome
    func getRevisionHistory(problemSessionId: UUID) async throws -> ProblemParseRevisionHistory
}

final class ProblemReviewAPI: ProblemReviewServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func getParseReview(problemSessionId: UUID) async throws -> ProblemParseReview {
        let response: HTTPResponse<ProblemReviewWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)/parse-review"),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.review()
    }

    func createCorrection(_ draft: ProblemParseCorrectionDraft, problemSessionId: UUID) async throws -> ProblemParseCorrectionOutcome {
        let body = try encoder.encode(ProblemReviewCorrectionWireRequest(from: draft))
        let response: HTTPResponse<ProblemReviewCorrectionWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)/parse-revisions", method: .post),
                body: body,
                idempotencyKey: draft.idempotencyKey,
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.outcome()
    }

    func getRevisionHistory(problemSessionId: UUID) async throws -> ProblemParseRevisionHistory {
        let response: HTTPResponse<ProblemReviewRevisionHistoryWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)/parse-revisions"),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.history()
    }
}

private struct ProblemReviewWireResponse: Decodable {
    let problemSessionId: UUID
    let currentParse: ProblemReviewCurrentParseWireResponse
    let revisionCount: Int
    let canCorrect: Bool

    func review() throws -> ProblemParseReview {
        ProblemParseReview(
            problemSessionId: problemSessionId,
            currentParse: try currentParse.currentParse(),
            revisionCount: revisionCount,
            canCorrect: canCorrect
        )
    }
}

private struct ProblemReviewCurrentParseWireResponse: Decodable {
    let problemParseId: UUID
    let revision: Int
    let source: String
    let supportStatus: String
    let reviewRequired: Bool
    let normalizedProblem: ProblemReviewNormalizedProblemWireResponse
    let createdAt: String

    func currentParse() throws -> ProblemParseCurrent {
        guard let createdAtDate = ProblemReviewISO8601WireDate.parse(createdAt) else {
            throw NetworkError.decoding("unsupported_parse_review_created_date")
        }
        return ProblemParseCurrent(
            problemParseId: problemParseId,
            revision: revision,
            source: source,
            supportStatus: supportStatus,
            reviewRequired: reviewRequired,
            normalizedProblem: normalizedProblem.parse,
            createdAt: createdAtDate
        )
    }
}

private struct ProblemReviewCorrectionWireRequest: Encodable {
    let baseParseId: UUID
    let baseRevision: Int
    let correctionReason: String
    let problem: ProblemReviewNormalizedProblemWireRequest

    init(from draft: ProblemParseCorrectionDraft) {
        baseParseId = draft.baseParseId
        baseRevision = draft.baseRevision
        correctionReason = draft.correctionReason.rawValue
        problem = ProblemReviewNormalizedProblemWireRequest(from: draft.problem)
    }
}

private struct ProblemReviewCorrectionWireResponse: Decodable {
    let problemSessionId: UUID
    let problemParseId: UUID
    let revision: Int
    let source: String
    let parentParseId: UUID
    let selected: Bool
    let supportStatus: String
    let reviewRequired: Bool
    let canonicalizationRequired: Bool
    let createdAt: String

    func outcome() throws -> ProblemParseCorrectionOutcome {
        guard let createdAtDate = ProblemReviewISO8601WireDate.parse(createdAt) else {
            throw NetworkError.decoding("unsupported_parse_correction_created_date")
        }
        return ProblemParseCorrectionOutcome(
            problemSessionId: problemSessionId,
            problemParseId: problemParseId,
            revision: revision,
            source: source,
            parentParseId: parentParseId,
            selected: selected,
            supportStatus: supportStatus,
            reviewRequired: reviewRequired,
            canonicalizationRequired: canonicalizationRequired,
            createdAt: createdAtDate
        )
    }
}

private struct ProblemReviewRevisionHistoryWireResponse: Decodable {
    let problemSessionId: UUID
    let selectedParseId: UUID?
    let revisions: [ProblemReviewRevisionEntryWireResponse]

    func history() throws -> ProblemParseRevisionHistory {
        ProblemParseRevisionHistory(
            problemSessionId: problemSessionId,
            selectedParseId: selectedParseId,
            revisions: try revisions.map { try $0.entry() }
        )
    }
}

private struct ProblemReviewRevisionEntryWireResponse: Decodable {
    let problemParseId: UUID
    let revision: Int
    let source: String
    let parentParseId: UUID?
    let selected: Bool
    let supportStatus: String
    let reviewRequired: Bool
    let correctionReason: String?
    let correctedFieldCategories: [String]
    let createdAt: String

    func entry() throws -> ProblemParseRevisionEntry {
        guard let createdAtDate = ProblemReviewISO8601WireDate.parse(createdAt) else {
            throw NetworkError.decoding("unsupported_parse_revision_created_date")
        }
        return ProblemParseRevisionEntry(
            problemParseId: problemParseId,
            revision: revision,
            source: source,
            parentParseId: parentParseId,
            selected: selected,
            supportStatus: supportStatus,
            reviewRequired: reviewRequired,
            correctionReason: correctionReason,
            correctedFieldCategories: correctedFieldCategories,
            createdAt: createdAtDate
        )
    }
}

private struct ProblemReviewNormalizedProblemWireResponse: Decodable {
    let schemaVersion: String
    let supportStatus: String
    let unsupportedReason: String?
    let subjectId: String?
    let topicId: String?
    let taskType: String?
    let problemType: String?
    let expressions: [ProblemReviewExpressionWireResponse]
    let variables: [ProblemReviewVariableWireResponse]
    let constraints: [ProblemReviewConstraintWireResponse]
    let assumptions: [ProblemReviewAssumptionWireResponse]
    let uncertainty: ProblemReviewUncertaintyWireResponse
    let sourceEvidenceRefs: [ProblemReviewSourceEvidenceRefWireResponse]
    let visualQualityRisks: [ProblemReviewVisualQualityRiskWireResponse]
    let reviewRequired: Bool

    var parse: NormalizedProblemParse {
        NormalizedProblemParse(
            schemaVersion: schemaVersion,
            supportStatus: supportStatus,
            unsupportedReason: unsupportedReason,
            subjectId: subjectId,
            topicId: topicId,
            taskType: taskType,
            problemType: problemType,
            expressions: expressions.map(\.expression),
            variables: variables.map(\.variable),
            constraints: constraints.map(\.constraint),
            assumptions: assumptions.map(\.assumption),
            uncertainty: uncertainty.uncertainty,
            sourceEvidenceRefs: sourceEvidenceRefs.map(\.ref),
            visualQualityRisks: visualQualityRisks.map(\.risk),
            reviewRequired: reviewRequired
        )
    }
}

private struct ProblemReviewNormalizedProblemWireRequest: Encodable {
    let schemaVersion: String
    let supportStatus: String
    let unsupportedReason: String?
    let subjectId: String?
    let topicId: String?
    let taskType: String?
    let problemType: String?
    let expressions: [ProblemReviewExpressionWireRequest]
    let variables: [ProblemReviewVariableWireRequest]
    let constraints: [ProblemReviewConstraintWireRequest]
    let assumptions: [ProblemReviewAssumptionWireRequest]
    let uncertainty: ProblemReviewUncertaintyWireRequest
    let sourceEvidenceRefs: [ProblemReviewSourceEvidenceRefWireRequest]
    let visualQualityRisks: [ProblemReviewVisualQualityRiskWireRequest]
    let reviewRequired: Bool

    init(from parse: NormalizedProblemParse) {
        schemaVersion = parse.schemaVersion
        supportStatus = parse.supportStatus
        unsupportedReason = parse.unsupportedReason
        subjectId = parse.subjectId
        topicId = parse.topicId
        taskType = parse.taskType
        problemType = parse.problemType
        expressions = parse.expressions.map(ProblemReviewExpressionWireRequest.init)
        variables = parse.variables.map(ProblemReviewVariableWireRequest.init)
        constraints = parse.constraints.map(ProblemReviewConstraintWireRequest.init)
        assumptions = parse.assumptions.map(ProblemReviewAssumptionWireRequest.init)
        uncertainty = ProblemReviewUncertaintyWireRequest(from: parse.uncertainty)
        sourceEvidenceRefs = parse.sourceEvidenceRefs.map(ProblemReviewSourceEvidenceRefWireRequest.init)
        visualQualityRisks = parse.visualQualityRisks.map(ProblemReviewVisualQualityRiskWireRequest.init)
        reviewRequired = parse.reviewRequired
    }
}

private struct ProblemReviewExpressionWireResponse: Decodable {
    let id: String
    let role: String
    let sourceText: String
    let normalizedText: String
    let displayLatex: String?
    let relation: String?
    let sourceBlockIds: [String]

    var expression: ProblemParseExpression {
        ProblemParseExpression(
            id: id,
            role: role,
            sourceText: sourceText,
            normalizedText: normalizedText,
            displayLatex: displayLatex,
            relation: relation,
            sourceBlockIds: sourceBlockIds
        )
    }
}

private struct ProblemReviewExpressionWireRequest: Encodable {
    let id: String
    let role: String
    let sourceText: String
    let normalizedText: String
    let displayLatex: String?
    let relation: String?
    let sourceBlockIds: [String]

    init(from expression: ProblemParseExpression) {
        id = expression.id
        role = expression.role
        sourceText = expression.sourceText
        normalizedText = expression.normalizedText
        displayLatex = expression.displayLatex
        relation = expression.relation
        sourceBlockIds = expression.sourceBlockIds
    }
}

private struct ProblemReviewVariableWireResponse: Decodable {
    let symbol: String
    let role: String
    let sourceBlockIds: [String]

    var variable: ProblemParseVariable {
        ProblemParseVariable(symbol: symbol, role: role, sourceBlockIds: sourceBlockIds)
    }
}

private struct ProblemReviewVariableWireRequest: Encodable {
    let symbol: String
    let role: String
    let sourceBlockIds: [String]

    init(from variable: ProblemParseVariable) {
        symbol = variable.symbol
        role = variable.role
        sourceBlockIds = variable.sourceBlockIds
    }
}

private struct ProblemReviewConstraintWireResponse: Decodable {
    let id: String
    let sourceText: String
    let normalizedText: String
    let variables: [String]
    let explicit: Bool
    let sourceBlockIds: [String]

    var constraint: ProblemParseConstraint {
        ProblemParseConstraint(
            id: id,
            sourceText: sourceText,
            normalizedText: normalizedText,
            variables: variables,
            explicit: explicit,
            sourceBlockIds: sourceBlockIds
        )
    }
}

private struct ProblemReviewConstraintWireRequest: Encodable {
    let id: String
    let sourceText: String
    let normalizedText: String
    let variables: [String]
    let explicit: Bool
    let sourceBlockIds: [String]

    init(from constraint: ProblemParseConstraint) {
        id = constraint.id
        sourceText = constraint.sourceText
        normalizedText = constraint.normalizedText
        variables = constraint.variables
        explicit = constraint.explicit
        sourceBlockIds = constraint.sourceBlockIds
    }
}

private struct ProblemReviewAssumptionWireResponse: Decodable {
    let id: String
    let text: String
    let explicit: Bool
    let sourceBlockIds: [String]

    var assumption: ProblemParseAssumption {
        ProblemParseAssumption(id: id, text: text, explicit: explicit, sourceBlockIds: sourceBlockIds)
    }
}

private struct ProblemReviewAssumptionWireRequest: Encodable {
    let id: String
    let text: String
    let explicit: Bool
    let sourceBlockIds: [String]

    init(from assumption: ProblemParseAssumption) {
        id = assumption.id
        text = assumption.text
        explicit = assumption.explicit
        sourceBlockIds = assumption.sourceBlockIds
    }
}

private struct ProblemReviewUncertaintyWireResponse: Decodable {
    let recognition: [String]
    let parse: [String]
    let reviewRequired: Bool

    var uncertainty: ProblemParseUncertainty {
        ProblemParseUncertainty(recognition: recognition, parse: parse, reviewRequired: reviewRequired)
    }
}

private struct ProblemReviewUncertaintyWireRequest: Encodable {
    let recognition: [String]
    let parse: [String]
    let reviewRequired: Bool

    init(from uncertainty: ProblemParseUncertainty) {
        recognition = uncertainty.recognition
        parse = uncertainty.parse
        reviewRequired = uncertainty.reviewRequired
    }
}

private struct ProblemReviewSourceEvidenceRefWireResponse: Decodable {
    let blockId: String
    let fieldPath: String

    var ref: ProblemParseSourceEvidenceRef {
        ProblemParseSourceEvidenceRef(blockId: blockId, fieldPath: fieldPath)
    }
}

private struct ProblemReviewSourceEvidenceRefWireRequest: Encodable {
    let blockId: String
    let fieldPath: String

    init(from ref: ProblemParseSourceEvidenceRef) {
        blockId = ref.blockId
        fieldPath = ref.fieldPath
    }
}

private struct ProblemReviewVisualQualityRiskWireResponse: Decodable {
    let signalType: String
    let severity: String
    let messageCode: String?

    var risk: ProblemParseVisualQualityRisk {
        ProblemParseVisualQualityRisk(signalType: signalType, severity: severity, messageCode: messageCode)
    }
}

private struct ProblemReviewVisualQualityRiskWireRequest: Encodable {
    let signalType: String
    let severity: String
    let messageCode: String?

    init(from risk: ProblemParseVisualQualityRisk) {
        signalType = risk.signalType
        severity = risk.severity
        messageCode = risk.messageCode
    }
}

private enum ProblemReviewISO8601WireDate {
    static func parse(_ value: String?) -> Date? {
        guard let value else {
            return nil
        }

        let fractionalFormatter = ISO8601DateFormatter()
        fractionalFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fractionalFormatter.date(from: value) {
            return date
        }

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)
    }
}
