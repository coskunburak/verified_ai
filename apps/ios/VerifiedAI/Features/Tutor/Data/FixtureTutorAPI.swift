#if DEBUG
import Foundation

/// Explicit UI-test transport. No model output or production persistence claims.
actor FixtureTutorAPI: TutorServicing {
    private var session: TutorSession?
    private var history: [TutorMessage] = []
    private var keys: [String: TutorMessage] = [:]
    private var failFirst: Bool
    init(failFirst: Bool = false) { self.failFirst = failFirst }
    func list(problem: UUID) async throws -> [TutorSession] { session.map { [$0] } ?? [] }
    func create(problem: UUID, key: String) async throws -> TutorSession {
        if let session { return session }
        let created = TutorSession(schemaVersion: "tutor-session-v1", id: UUID(), problemSessionId: problem,
            canonicalId: UUID(), canonicalRevision: 1, parseId: UUID(), parseRevision: 1, primarySkillId: "NOT_AVAILABLE",
            contextSchemaVersion: "tutor-context-v1", profileVersion: nil, explanationDepth: nil, language: nil,
            mastery: "NOT_AVAILABLE", safetyPolicyVersion: "learner-persistence-v1", status: .active, sourceCurrent: true,
            messageCount: 0, version: 0, createdAt: Date().ISO8601Format(), updatedAt: Date().ISO8601Format(), trainingEligible: false)
        session = created; return created
    }
    func read(problem: UUID, session: UUID) async throws -> TutorSession {
        guard let current = self.session, current.id == session else { throw NetworkError.invalidResponse }
        return current
    }
    func messages(problem: UUID, session: UUID, after: Int) async throws -> [TutorMessage] { Array(history.filter { $0.sequenceNumber > after }.prefix(50)) }
    func append(problem: UUID, session: UUID, key: String, content: String) async throws -> TutorMessage {
        if let existing = keys[key] { return existing }
        let message = TutorMessage(schemaVersion: "tutor-message-v1", id: UUID(), tutorSessionId: session, role: "LEARNER", sequenceNumber: history.count + 1, content: content, createdAt: Date().ISO8601Format())
        history.append(message); keys[key] = message
        update(.active)
        if failFirst { failFirst = false; throw NetworkError.transport("Synthetic lost acknowledgement") }
        return message
    }
    func close(problem: UUID, session: UUID) async throws -> TutorSession { update(.closed); return try await read(problem: problem, session: session) }
    func markStale() { update(session?.status ?? .active, current: false) }
    private func update(_ status: TutorStatus, current: Bool = true) {
        guard let s = session else { return }
        session = TutorSession(schemaVersion: s.schemaVersion, id: s.id, problemSessionId: s.problemSessionId,
            canonicalId: s.canonicalId, canonicalRevision: s.canonicalRevision, parseId: s.parseId, parseRevision: s.parseRevision,
            primarySkillId: s.primarySkillId, contextSchemaVersion: s.contextSchemaVersion, profileVersion: nil, explanationDepth: nil,
            language: nil, mastery: s.mastery, safetyPolicyVersion: s.safetyPolicyVersion, status: status, sourceCurrent: current,
            messageCount: history.count, version: s.version + 1, createdAt: s.createdAt, updatedAt: Date().ISO8601Format(), trainingEligible: false)
    }
}
#endif
