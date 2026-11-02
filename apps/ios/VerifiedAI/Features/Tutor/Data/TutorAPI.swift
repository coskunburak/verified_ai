import Foundation

final class TutorAPI: TutorServicing, @unchecked Sendable {
    private let client: APIClient
    init(client: APIClient) { self.client = client }
    private func path(_ problem: UUID, _ session: UUID? = nil) -> String {
        "/api/v1/problem-sessions/\(problem.uuidString)/tutor-sessions" + (session.map { "/\($0.uuidString)" } ?? "")
    }
    func list(problem: UUID) async throws -> [TutorSession] {
        let r: HTTPResponse<[TutorSession]> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem))))
        guard r.body.count <= 30 else { throw NetworkError.invalidResponse }
        return try r.body.map { try $0.validated(problem: problem) }
    }
    func create(problem: UUID, key: String) async throws -> TutorSession {
        let r: HTTPResponse<TutorSession> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem), method: .post), idempotencyKey: key))
        return try r.body.validated(problem: problem)
    }
    func read(problem: UUID, session: UUID) async throws -> TutorSession { try await sessionRequest(problem, session, close: false) }
    func close(problem: UUID, session: UUID) async throws -> TutorSession { try await sessionRequest(problem, session, close: true) }
    private func sessionRequest(_ problem: UUID, _ session: UUID, close: Bool) async throws -> TutorSession {
        let r: HTTPResponse<TutorSession> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem, session) + (close ? "/close" : ""), method: close ? .post : .get)))
        guard r.body.id == session else { throw NetworkError.invalidResponse }
        return try r.body.validated(problem: problem)
    }
    func messages(problem: UUID, session: UUID, after: Int) async throws -> [TutorMessage] {
        let r: HTTPResponse<[TutorMessage]> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem, session) + "/messages", queryItems: [URLQueryItem(name: "after", value: String(after)), URLQueryItem(name: "limit", value: "50")]), timeout: 15))
        guard r.body.count <= 50 else { throw NetworkError.invalidResponse }
        var expected = after + 1
        return try r.body.map {
            guard $0.sequenceNumber == expected else { throw NetworkError.invalidResponse }
            expected += 1
            return try $0.validated(session: session)
        }
    }
    func append(problem: UUID, session: UUID, key: String, content: String) async throws -> TutorMessage {
        let body = try JSONEncoder().encode(["content": content])
        let r: HTTPResponse<TutorMessage> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem, session) + "/messages", method: .post), body: body, idempotencyKey: key))
        return try r.body.validated(session: session)
    }
    func turns(problem: UUID, session: UUID, after: Int) async throws -> [TutorTurn] {
        let r: HTTPResponse<[TutorTurn]> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem, session) + "/turns", queryItems: [URLQueryItem(name: "after", value: String(after)), URLQueryItem(name: "limit", value: "50")]), timeout: 15))
        guard r.body.count <= 12 else { throw NetworkError.invalidResponse }
        var previous = after
        return try r.body.map {
            guard $0.learnerSequenceNumber > previous else { throw NetworkError.invalidResponse }
            previous = $0.learnerSequenceNumber
            return try $0.validated(session: session)
        }
    }
    func submitTurn(problem: UUID, session: UUID, key: String, content: String, intent: TutorIntent) async throws -> TutorTurn {
        let body = try JSONEncoder().encode(["content": content, "intent": intent.rawValue])
        let r: HTTPResponse<TutorTurn> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem, session) + "/turns", method: .post), body: body, idempotencyKey: key, timeout: 15))
        return try r.body.validated(session: session)
    }

}
