import Foundation

final class ProblemSessionHistoryAPI: ProblemSessionHistoryServicing, @unchecked Sendable {
    private let apiClient: APIClient

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func history(limit: Int, cursor: String?) async throws -> ProblemSessionHistoryPage {
        var queryItems = [URLQueryItem(name: "limit", value: "\(limit)")]
        if let cursor, !cursor.isEmpty {
            queryItems.append(URLQueryItem(name: "cursor", value: cursor))
        }
        let response: HTTPResponse<ProblemSessionHistoryWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions", queryItems: queryItems),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.page()
    }

    func detail(problemSessionId: UUID) async throws -> ProblemSessionDetail {
        let response: HTTPResponse<ProblemSessionDetailWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)"),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.detail()
    }
}

private struct ProblemSessionHistoryWireResponse: Decodable {
    let items: [ProblemSessionSummaryWireResponse]
    let nextCursor: String?

    func page() throws -> ProblemSessionHistoryPage {
        ProblemSessionHistoryPage(items: try items.map { try $0.item() }, nextCursor: nextCursor)
    }
}

private struct ProblemSessionSummaryWireResponse: Decodable {
    let problemSessionId: UUID
    let status: String
    let stage: String
    let inputMode: String
    let nextAction: String
    let retryable: Bool
    let reviewRequired: Bool
    let currentParseRevision: Int?
    let currentParseSource: String?
    let classificationStatus: String?
    let primarySkillId: String?
    let difficulty: String?
    let createdAt: String
    let updatedAt: String
    let completedAt: String?

    func item() throws -> ProblemSessionHistoryItem {
        guard let status = ProblemSessionStatus(rawValue: status),
              let stage = ProblemSessionStage(rawValue: stage),
              let nextAction = ProblemSessionNextAction(rawValue: nextAction),
              let createdAt = ProblemSessionHistoryWireDate.parse(createdAt),
              let updatedAt = ProblemSessionHistoryWireDate.parse(updatedAt) else {
            throw NetworkError.decoding("unsupported_problem_session_summary")
        }
        return ProblemSessionHistoryItem(
            problemSessionId: problemSessionId,
            status: status,
            stage: stage,
            inputMode: inputMode,
            nextAction: nextAction,
            retryable: retryable,
            reviewRequired: reviewRequired,
            currentParseRevision: currentParseRevision,
            currentParseSource: currentParseSource,
            classificationStatus: classificationStatus,
            primarySkillId: primarySkillId,
            difficulty: difficulty,
            createdAt: createdAt,
            updatedAt: updatedAt,
            completedAt: ProblemSessionHistoryWireDate.parse(completedAt)
        )
    }
}

private struct ProblemSessionDetailWireResponse: Decodable {
    let problemSessionId: UUID
    let status: String
    let stage: String
    let inputMode: String
    let nextAction: String
    let retryable: Bool
    let reviewRequired: Bool
    let failureCode: String?
    let currentParse: ProblemSessionCurrentParseWireResponse?
    let canonicalProblem: ProblemSessionCanonicalWireResponse?
    let classification: ProblemSessionClassificationWireResponse?
    let activeJob: ProblemSessionActiveJobWireResponse?
    let createdAt: String
    let updatedAt: String
    let completedAt: String?
    let version: Int64

    func detail() throws -> ProblemSessionDetail {
        guard let status = ProblemSessionStatus(rawValue: status),
              let stage = ProblemSessionStage(rawValue: stage),
              let nextAction = ProblemSessionNextAction(rawValue: nextAction),
              let createdAt = ProblemSessionHistoryWireDate.parse(createdAt),
              let updatedAt = ProblemSessionHistoryWireDate.parse(updatedAt) else {
            throw NetworkError.decoding("unsupported_problem_session_detail")
        }
        return ProblemSessionDetail(
            problemSessionId: problemSessionId,
            status: status,
            stage: stage,
            inputMode: inputMode,
            nextAction: nextAction,
            retryable: retryable,
            reviewRequired: reviewRequired,
            failureCode: failureCode,
            currentParse: currentParse?.summary,
            canonicalProblem: canonicalProblem?.summary,
            classification: classification?.summary,
            activeJob: try activeJob?.job(),
            createdAt: createdAt,
            updatedAt: updatedAt,
            completedAt: ProblemSessionHistoryWireDate.parse(completedAt),
            version: version
        )
    }
}

private struct ProblemSessionCurrentParseWireResponse: Decodable {
    let parseId: UUID
    let revision: Int
    let source: String
    let supportStatus: String
    let reviewRequired: Bool

    var summary: ProblemSessionCurrentParseSummary {
        ProblemSessionCurrentParseSummary(
            problemParseId: parseId,
            revision: revision,
            source: source,
            supportStatus: supportStatus,
            reviewRequired: reviewRequired
        )
    }
}

private struct ProblemSessionCanonicalWireResponse: Decodable {
    let canonicalProblemId: UUID
    let canonicalRevision: Int
    let problemParseId: UUID
    let problemParseRevision: Int
    let problemType: String
    let taskType: String

    var summary: ProblemSessionCanonicalSummary {
        ProblemSessionCanonicalSummary(
            canonicalProblemId: canonicalProblemId,
            revision: canonicalRevision,
            problemParseId: problemParseId,
            problemParseRevision: problemParseRevision,
            problemType: problemType,
            taskType: taskType
        )
    }
}

private struct ProblemSessionClassificationWireResponse: Decodable {
    let classificationId: UUID
    let revision: Int
    let status: String
    let primarySkillId: String?
    let difficulty: String?
    let reviewReason: String?

    var summary: ProblemSessionClassificationSummary {
        ProblemSessionClassificationSummary(
            classificationId: classificationId,
            revision: revision,
            status: status,
            primarySkillId: primarySkillId,
            difficulty: difficulty,
            reviewReason: reviewReason
        )
    }
}

private struct ProblemSessionActiveJobWireResponse: Decodable {
    let type: String
    let jobId: UUID
    let status: String
    let attemptCount: Int
    let maxAttempts: Int
    let failureCode: String?

    func job() throws -> ProblemSessionActiveJob {
        guard let type = ProblemSessionActiveJobType(rawValue: type) else {
            throw NetworkError.decoding("unsupported_problem_session_active_job")
        }
        return ProblemSessionActiveJob(
            type: type,
            id: jobId,
            status: status,
            attemptCount: attemptCount,
            maxAttempts: maxAttempts,
            failureCode: failureCode
        )
    }
}

private enum ProblemSessionHistoryWireDate {
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
