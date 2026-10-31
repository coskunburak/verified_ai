import Foundation

final class PrimarySolveAPI: PrimarySolveServicing, @unchecked Sendable {
    private let client: APIClient
    init(client: APIClient) { self.client = client }
    private func path(_ sessionId: UUID) -> String { "/api/v1/problem-sessions/\(sessionId.uuidString)/solves" }
    func verification(sessionId: UUID, jobId: UUID) async throws -> VerificationDecisionSnapshot? {
        let response: HTTPResponse<VerificationReadResponse> = try await client.send(HTTPRequest(
            endpoint: Endpoint(path: path(sessionId) + "/\(jobId.uuidString)/verification")))
        return response.body.decision
    }
    func current(sessionId: UUID) async throws -> SolveJobSnapshot? {
        let response: HTTPResponse<CurrentSolveResponse> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(sessionId) + "/current")))
        return try response.body.job?.validated()
    }
    func start(sessionId: UUID, key: String) async throws -> SolveJobSnapshot {
        try await send(path(sessionId), method: .post, key: key)
    }
    func read(sessionId: UUID, jobId: UUID) async throws -> SolveJobSnapshot {
        let result = try await send(path(sessionId) + "/\(jobId.uuidString)", method: .get)
        guard result.jobId == jobId else { throw SolveContractError.invalidResponse }
        return result
    }
    func retry(sessionId: UUID, jobId: UUID, key: String) async throws -> SolveJobSnapshot {
        try await send(path(sessionId) + "/\(jobId.uuidString)/retry", method: .post, key: key)
    }
    func cancel(sessionId: UUID, jobId: UUID) async throws -> SolveJobSnapshot {
        let result = try await send(path(sessionId) + "/\(jobId.uuidString)/cancel", method: .post)
        guard result.jobId == jobId else { throw SolveContractError.invalidResponse }
        return result
    }
    private func send(_ path: String, method: HTTPMethod, key: String? = nil) async throws -> SolveJobSnapshot {
        if let key, key.range(of: #"^[A-Za-z0-9_-]{8,128}$"#, options: .regularExpression) == nil { throw SolveContractError.invalidResponse }
        let response: HTTPResponse<SolveJobSnapshot> = try await client.send(HTTPRequest(
            endpoint: Endpoint(path: path, method: method), idempotencyKey: key,
            allowsAuthRefreshRetry: method == .get))
        return try response.body.validated()
    }
}

private struct CurrentSolveResponse: Decodable {
    let job: SolveJobSnapshot?
    private enum Keys: String, CodingKey { case job }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        guard c.contains(.job) else { throw SolveContractError.invalidResponse }
        job = try c.decodeIfPresent(SolveJobSnapshot.self, forKey: .job)
    }
}


private struct VerificationReadResponse: Decodable {
    let decision: VerificationDecisionSnapshot?
    private enum Keys: String, CodingKey { case decision }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        guard c.contains(.decision) else { throw SolveContractError.invalidResponse }
        decision = try c.decodeIfPresent(VerificationDecisionSnapshot.self, forKey: .decision)
    }
}
