import Foundation

struct CounterfactualTwin: Codable, Identifiable, Sendable {
    let schemaVersion: String
    let id: UUID
    let attemptId: UUID
    let problem: AttemptJSON
    let options: [AttemptJSON]
    let domainLabel: String
    let difficulty: String
    let status: String
    let selectedOption: Int?
    let correct: Bool?
    let correctOption: Int?
    let explanationKey: String?
    let assistance: String?
    let createdAt: Date
    let completedAt: Date?
    let trainingEligible: Bool
    func validated(attempt: UUID? = nil) throws -> Self {
        guard schemaVersion == "counterfactual-twin-v1", !trainingEligible, options.count == 4,
              attempt == nil || attemptId == attempt,
              ["OPEN", "COMPLETED"].contains(status), (status == "COMPLETED") == (completedAt != nil) else { throw NetworkError.invalidResponse }
        if status == "OPEN" {
            guard correct == nil, correctOption == nil, selectedOption == nil, explanationKey == nil, assistance == nil else { throw NetworkError.invalidResponse }
        } else {
            guard let choice = selectedOption, let answer = correctOption, (0...3).contains(choice), (0...3).contains(answer),
                  correct == (choice == answer), ["INDEPENDENT", "HINT_ASSISTED"].contains(assistance ?? ""), explanationKey?.hasPrefix("attempt.mistake.") == true else { throw NetworkError.invalidResponse }
        }
        return self
    }
}
struct AttemptAlternativeMethods: Decodable, Sendable {
    struct Method: Decodable, Identifiable, Sendable {
        let methodId: String; let steps: [AttemptJSON]; let evidenceVersion: String
        var id: String { methodId }
    }
    let schemaVersion: String; let verifierVersion: String; let action: String; let status: String
    let methods: [Method]
}
struct MistakePassport: Decodable, Sendable {
    struct Event: Decodable, Identifiable, Sendable {
        let id: UUID; let attempt_id: UUID; let drill_id: UUID?; let mistake_code: String
        let correct: Bool; let assistance: String; let assessed_at: Date
    }
    struct Mastery: Decodable, Identifiable, Sendable {
        let skill_id: String; let mastery_version: String; let score: Double; let confidence: Double
        let evidence_count: Int; let half_life_days: Double; let last_assessed_at: Date
        var id: String { skill_id }
    }
    let schemaVersion: String; let trainingEligible: Bool; let events: [Event]; let mastery: [Mastery]
}
final class AttemptLearningAPI: @unchecked Sendable {
    private let client: APIClient
    init(client: APIClient) { self.client = client }
    private func path(_ problem: UUID) -> String { "/api/v1/problem-sessions/\(problem)/attempts" }
    func twins(problem: UUID) async throws -> [CounterfactualTwin] {
        let r: HTTPResponse<[CounterfactualTwin]> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/drills")))
        guard r.body.count <= 30 else { throw NetworkError.invalidResponse }
        return try r.body.map { try $0.validated() }
    }
    func create(problem: UUID, attempt: UUID) async throws -> CounterfactualTwin {
        let r: HTTPResponse<CounterfactualTwin> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/\(attempt)/counterfactual-twin", method: .post),
            body: Data("{}".utf8), idempotencyKey: "twin_\(attempt)", timeout: 15))
        return try r.body.validated(attempt: attempt)
    }
    func answer(problem: UUID, attempt: UUID, drill: UUID, choice: Int) async throws -> CounterfactualTwin {
        let r: HTTPResponse<CounterfactualTwin> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/\(attempt)/counterfactual-twin/answer", method: .post),
            body: try JSONEncoder().encode(["selectedOption": choice]), idempotencyKey: "answer_\(drill)", timeout: 15))
        let value = try r.body.validated(attempt: attempt)
        guard value.id == drill, value.selectedOption == choice, value.status == "COMPLETED" else { throw NetworkError.invalidResponse }
        return value
    }
    func methods(problem: UUID, attempt: UUID) async throws -> AttemptAlternativeMethods {
        let r: HTTPResponse<AttemptAlternativeMethods> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/\(attempt)/alternative-methods", method: .post),
            body: Data("{}".utf8), idempotencyKey: "methods_\(attempt)", timeout: 15))
        guard r.body.schemaVersion == "attempt-learning-result-v1", r.body.verifierVersion == "bounded-learning-v1",
              r.body.status == "VERIFIED", r.body.action == "METHODS", (1...3).contains(r.body.methods.count),
              r.body.methods.allSatisfy({ $0.evidenceVersion == "verified-method-v1" && (1...4).contains($0.steps.count) }) else { throw NetworkError.invalidResponse }
        return r.body
    }
    func passport(problem: UUID) async throws -> MistakePassport {
        let r: HTTPResponse<MistakePassport> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/mistake-passport")))
        guard r.body.schemaVersion == "mistake-passport-v1", !r.body.trainingEligible, r.body.events.count <= 60,
              r.body.mastery.count <= 17, r.body.mastery.allSatisfy({ $0.mastery_version == "bkt-lite-v1" && (0.05...0.99).contains($0.score) && (0...1).contains($0.confidence) && (1...90).contains($0.half_life_days) && (1...60).contains($0.evidence_count) }) else { throw NetworkError.invalidResponse }
        return r.body
    }
}
