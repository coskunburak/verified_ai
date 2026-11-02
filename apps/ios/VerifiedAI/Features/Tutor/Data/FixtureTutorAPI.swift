#if DEBUG
import Foundation

/// Explicit UI-test transport. No model output or production persistence claims.
actor FixtureTutorAPI: TutorServicing {
    private var session: TutorSession?
    private var history: [TutorMessage] = []
    private var keys: [String: TutorMessage] = [:]
    private var replies: [TutorTurn] = []
    private var turnKeys: [String: TutorTurn] = [:]
    private let available: Bool
    private var failFirst: Bool
    init(failFirst: Bool = false, available: Bool = true) { self.failFirst = failFirst; self.available = available }
    func list(problem: UUID) async throws -> [TutorSession] { session.map { [$0] } ?? [] }
    func create(problem: UUID, key: String) async throws -> TutorSession {
        if let session { return session }
        let created = TutorSession(schemaVersion: "tutor-session-v1", id: UUID(), problemSessionId: problem,
            canonicalId: UUID(), canonicalRevision: 1, parseId: UUID(), parseRevision: 1, primarySkillId: "NOT_AVAILABLE",
            contextSchemaVersion: "tutor-context-v1", profileVersion: nil, explanationDepth: nil, language: Locale.preferredLanguages.first?.hasPrefix("tr") == true ? "tr" : "en",
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
        if let existing = keys[key] {
            guard existing.content == content else { throw failure("IDEMPOTENCY_KEY_REUSED") }
            return existing
        }
        let current = try await read(problem: problem, session: session)
        guard current.status == .active else { throw failure("TUTOR_SESSION_CLOSED") }
        guard current.sourceCurrent else { throw failure("TUTOR_SOURCE_STALE") }
        let message = TutorMessage(schemaVersion: "tutor-message-v1", id: UUID(), tutorSessionId: session, role: "LEARNER", sequenceNumber: history.count + 1, content: content, createdAt: Date().ISO8601Format())
        history.append(message); keys[key] = message
        update(.active)
        if failFirst { failFirst = false; throw NetworkError.transport("Synthetic lost acknowledgement") }
        return message
    }
    func turns(problem: UUID, session: UUID, after: Int) async throws -> [TutorTurn] {
        replies.filter { $0.learnerSequenceNumber > after }
    }
    func submitTurn(problem: UUID, session: UUID, key: String, content: String, intent: TutorIntent) async throws -> TutorTurn {
        if let existing = turnKeys[key] {
            guard keys[key]?.content == content, existing.intent == intent else { throw failure("IDEMPOTENCY_KEY_REUSED") }
            return existing
        }
        guard replies.last?.progress.phase.terminal != true else { throw failure("TUTOR_PEDAGOGY_STOPPED") }
        guard available else {
            throw NetworkError.server(problem: ProblemDetails(type: "about:blank", title: "Unavailable", status: 503, code: "TUTOR_CAPABILITY_UNAVAILABLE", traceId: nil, details: nil))
        }
        let loseAck = failFirst
        failFirst = false
        let learner = try await append(problem: problem, session: session, key: key, content: content)
        let tr = self.session?.language == "tr"
        let phase: TutorPhase = intent == .challenge ? .needsReview : (replies.isEmpty ? .elicitConcept : .evaluateStep)
        let turn = TutorTurn(schemaVersion: "tutor-turn-v1", id: UUID(), tutorSessionId: session,
            learnerMessageId: learner.id, learnerSequenceNumber: learner.sequenceNumber, replyId: UUID(), role: "TUTOR",
            content: tr ? "Hangi kavram veya kuralı kullanmayı önerirsin?" : "Which concept or rule would you propose using?",
            language: tr ? "tr" : "en", policyVersion: "socratic-policy-v1", previousPhase: replies.last?.progress.phase ?? .clarifyGoal,
            progress: TutorProgress(phase: phase, unproductiveCount: 0, turnCount: replies.count + 1), intent: intent,
            action: intent == .challenge ? .review : (replies.isEmpty ? .askConcept : .askStep),
            outcome: intent == .challenge ? .needsReview : .guidance, correctness: "UNKNOWN", referenceStatus: "UNAVAILABLE",
            uncertain: true, provenance: "DETERMINISTIC_POLICY", createdAt: Date().ISO8601Format(), trainingEligible: false)
        replies.append(turn); turnKeys[key] = turn
        if loseAck { throw NetworkError.transport("Synthetic lost acknowledgement") }
        return turn
    }
    func close(problem: UUID, session: UUID) async throws -> TutorSession { update(.closed); return try await read(problem: problem, session: session) }
    func markStale() { update(session?.status ?? .active, current: false) }
    private func failure(_ code: String) -> NetworkError {
        .server(problem: ProblemDetails(type: "about:blank", title: "Synthetic rejection", status: 409, code: code, traceId: nil, details: nil))
    }
    private func update(_ status: TutorStatus, current: Bool = true) {
        guard let s = session else { return }
        session = TutorSession(schemaVersion: s.schemaVersion, id: s.id, problemSessionId: s.problemSessionId,
            canonicalId: s.canonicalId, canonicalRevision: s.canonicalRevision, parseId: s.parseId, parseRevision: s.parseRevision,
            primarySkillId: s.primarySkillId, contextSchemaVersion: s.contextSchemaVersion, profileVersion: nil, explanationDepth: nil,
            language: s.language, mastery: s.mastery, safetyPolicyVersion: s.safetyPolicyVersion, status: status, sourceCurrent: current,
            messageCount: history.count, version: s.version + 1, createdAt: s.createdAt, updatedAt: Date().ISO8601Format(), trainingEligible: false)
    }
}
#endif
