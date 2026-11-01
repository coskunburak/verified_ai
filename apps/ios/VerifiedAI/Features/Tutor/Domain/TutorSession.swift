import Foundation

enum TutorStatus: String, Codable, Sendable { case active = "ACTIVE", closed = "CLOSED" }
struct TutorSession: Codable, Equatable, Identifiable, Sendable {
    let schemaVersion: String
    let id: UUID
    let problemSessionId: UUID
    let canonicalId: UUID
    let canonicalRevision: Int
    let parseId: UUID
    let parseRevision: Int
    let primarySkillId: String
    let contextSchemaVersion: String
    let profileVersion: Int?
    let explanationDepth: String?
    let language: String?
    let mastery: String
    let safetyPolicyVersion: String
    let status: TutorStatus
    let sourceCurrent: Bool
    let messageCount: Int
    let version: Int
    let createdAt: String
    let updatedAt: String
    let trainingEligible: Bool
    func validated(problem: UUID) throws -> Self {
        guard schemaVersion == "tutor-session-v1", problemSessionId == problem,
              contextSchemaVersion == "tutor-context-v1", safetyPolicyVersion == "learner-persistence-v1",
              TutorTimestamp.parse(createdAt) != nil, TutorTimestamp.parse(updatedAt) != nil, !trainingEligible, mastery == "NOT_AVAILABLE", (0...100).contains(messageCount),
              canonicalRevision > 0, parseRevision > 0, version >= 0 else { throw NetworkError.invalidResponse }
        return self
    }
}
struct TutorMessage: Codable, Equatable, Identifiable, Sendable {
    let schemaVersion: String
    let id: UUID
    let tutorSessionId: UUID
    let role: String
    let sequenceNumber: Int
    let content: String
    let createdAt: String
    func validated(session: UUID) throws -> Self {
        guard schemaVersion == "tutor-message-v1", tutorSessionId == session, role == "LEARNER",
              (1...100).contains(sequenceNumber), !content.isEmpty, content.unicodeScalars.count <= 2000,
              content.utf8.count <= 8000, TutorTimestamp.parse(createdAt) != nil else { throw NetworkError.invalidResponse }
        return self
    }
}
protocol TutorServicing: Sendable {
    func list(problem: UUID) async throws -> [TutorSession]
    func create(problem: UUID, key: String) async throws -> TutorSession
    func read(problem: UUID, session: UUID) async throws -> TutorSession
    func messages(problem: UUID, session: UUID, after: Int) async throws -> [TutorMessage]
    func append(problem: UUID, session: UUID, key: String, content: String) async throws -> TutorMessage
    func close(problem: UUID, session: UUID) async throws -> TutorSession
}

private enum TutorTimestamp {
    static func parse(_ value: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: value) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)
    }
}
