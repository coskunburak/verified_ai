import Foundation
final class AttemptAnalysisAPI: AttemptAnalysisServicing, @unchecked Sendable {
    private let client: APIClient
    init(client: APIClient) { self.client = client }
    private func path(_ problem: UUID) -> String { "/api/v1/problem-sessions/\(problem.uuidString)/attempts" }
    func context(problem: UUID) async throws -> AttemptContext {
        let r: HTTPResponse<AttemptContext> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/context")))
        guard ["attempt-context-v1", "attempt-context-v2"].contains(r.body.schemaVersion), r.body.source.canonicalRevision > 0, r.body.source.parseRevision > 0,
              ["UNAVAILABLE", "NATIVE_REVIEW", "NATIVE_REVIEW_AND_GATEWAY"].contains(r.body.handwriting), ["UNAVAILABLE", "VERIFIED_TEMPLATES"].contains(r.body.twinDrill), ["UNAVAILABLE", "VERIFIED_METHODS"].contains(r.body.methodComparison) else { throw NetworkError.invalidResponse }
        return r.body
    }
    func extraction(problem: UUID, assetSession: UUID) async throws -> AttemptExtraction {
        let r: HTTPResponse<AttemptExtraction> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/extractions", method: .post),
            body: try JSONEncoder().encode(["assetSessionId": assetSession.uuidString]), timeout: 15))
        guard r.body.schemaVersion == "attempt-extraction-v1", !r.body.trainingEligible,
              r.body.assetSessionId == assetSession, r.body.status == "REVIEW_REQUIRED", (1...12).contains(r.body.steps.count),
              r.body.steps.allSatisfy({ $0.reviewRequired && !$0.rawTranscription.isEmpty && $0.rawTranscription.utf16.count <= 256 }) else { throw NetworkError.invalidResponse }
        return r.body
    }
    func list(problem: UUID) async throws -> [StudentAttempt] {
        let r: HTTPResponse<[StudentAttempt]> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem), queryItems: [URLQueryItem(name: "representationVersion", value: "2")])))
        guard r.body.count <= 30 else { throw NetworkError.invalidResponse }
        return try r.body.map { try $0.validated(problem: problem) }
    }
    func submit(problem: UUID, key: String, submission: AttemptSubmission) async throws -> StudentAttempt {
        guard submission.isValid else { throw NetworkError.invalidResponse }
        let r: HTTPResponse<StudentAttempt> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem), method: .post),
            body: try JSONEncoder().encode(submission), idempotencyKey: key, timeout: 15))
        guard r.body.source == submission.source, r.body.steps.map(\.content) == submission.steps, r.body.finalAnswer == submission.finalAnswer else { throw NetworkError.invalidResponse }
        return try r.body.validated(problem: problem)
    }
}

struct AttemptExtraction: Decodable, Sendable {
    struct Line: Decodable, Sendable {
        struct Box: Decodable, Sendable { let x: Double; let y: Double; let width: Double; let height: Double }
        let index: Int; let rawTranscription: String; let normalizedStatement: String?; let latex: String?
        let boundingBox: Box; let confidence: Double?; let reviewRequired: Bool
    }
    let schemaVersion: String; let assetSessionId: UUID; let recognitionJobId: UUID; let status: String
    let provider: String; let model: String; let upstreamSchemaVersion: String; let promptVersion: String
    let steps: [Line]; let trainingEligible: Bool
}
