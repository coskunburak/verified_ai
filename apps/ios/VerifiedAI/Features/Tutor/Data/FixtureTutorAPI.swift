#if DEBUG
import Foundation

/// Explicit UI-test transport. No model output or production persistence claims.
actor FixtureTutorAPI: TutorServicing {
    private var session: TutorSession?
    private var history: [TutorMessage] = []
    private var keys: [String: TutorMessage] = [:]
    private var replies: [TutorTurn] = []
    private var hintRecords: [TutorHint] = []
    private var hintKeys: [String: TutorHint] = [:]
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
    private var progress: TutorProgress {
        let turn = replies.last?.progress
        let hint = hintRecords.last?.progress
        if let hint, hint.turnCount > (turn?.turnCount ?? 0) { return hint }
        return turn ?? TutorProgress(phase: .clarifyGoal, unproductiveCount: 0, turnCount: 0)
    }
    func hints(problem: UUID, session: UUID) async throws -> TutorHintHistory {
        let current = try await read(problem: problem, session: session)
        let state: TutorHintAvailability = current.status == .closed ? .closed : (!current.sourceCurrent ? .stale : ((progress.phase.terminal || progress.turnCount >= 11) ? .stopped : (hintRecords.count == 3 ? .exhausted : (available ? .available : .unavailable))))
        return TutorHintHistory(schemaVersion: "tutor-hint-history-v1", tutorSessionId: session, hints: hintRecords,
            currentLevel: hintRecords.last?.level, nextLevel: state == .available ? TutorHintLevel.allCases[hintRecords.count] : nil,
            availability: state, progress: progress, revealAvailability: "UNAVAILABLE")
    }
    func requestHint(problem: UUID, session: UUID, key: String, level: TutorHintLevel) async throws -> TutorHint {
        if let existing = hintKeys[key] {
            guard existing.level == level else { throw failure("IDEMPOTENCY_KEY_REUSED") }
            return existing
        }
        guard keys[key] == nil else { throw failure("IDEMPOTENCY_KEY_REUSED") }
        let state = try await hints(problem: problem, session: session)
        guard state.availability == .available, state.nextLevel == level else { throw failure("TUTOR_HINT_LEVEL_BLOCKED") }
        let tr = self.session?.language == "tr"
        let en = ["Think about isolating the unknown while keeping both sides of the equation balanced.", "Use inverse operations: remove the added constant before undoing multiplication.", "For the first step, subtract the added constant from both sides. Write the resulting equation yourself, without dividing yet."]
        let turkish = ["Bilinmeyeni yalnız bırakmayı düşün. Eşitliğin iki tarafının dengesi korunmalı.", "Ters işlemleri kullan: önce eklenen sabit terimi kaldır, ardından çarpma işlemini geri al.", "İlk adım olarak iki taraftan da eklenen sabit terimi çıkar. Henüz bölme yapmadan oluşan denklemi sen yaz."]
        let hint = TutorHint(schemaVersion: "tutor-hint-v1", id: UUID(), tutorSessionId: session, sequenceNumber: hintRecords.count + 1,
            level: level, content: (tr ? turkish : en)[hintRecords.count], language: tr ? "tr" : "en",
            policyVersion: "progressive-hints-v1", costPolicyVersion: "hint-assistance-v1", assistance: ["CONCEPTUAL","METHOD","FIRST_STEP"][hintRecords.count],
            masteryEvidence: "ASSISTANCE_ONLY", inference: "DETERMINISTIC_NO_INFERENCE", commercial: "NO_DEBIT", previousPhase: progress.phase,
            progress: TutorProgress(phase: .minimalSupport, unproductiveCount: progress.unproductiveCount, turnCount: progress.turnCount + 1),
            referenceStatus: "UNAVAILABLE", uncertain: true, createdAt: Date().ISO8601Format(), trainingEligible: false)
        hintRecords.append(hint); hintKeys[key] = hint
        if failFirst { failFirst = false; throw NetworkError.transport("Synthetic lost hint acknowledgement") }
        return hint
    }
    func reveal(problem: UUID, session: UUID, key: String) async throws {
        _ = try await read(problem: problem, session: session)
        throw failure("TUTOR_REVEAL_UNAVAILABLE")
    }
    func read(problem: UUID, session: UUID) async throws -> TutorSession {
        guard let current = self.session, current.id == session else { throw NetworkError.invalidResponse }
        return current
    }
    func messages(problem: UUID, session: UUID, after: Int) async throws -> [TutorMessage] { Array(history.filter { $0.sequenceNumber > after }.prefix(50)) }
    func append(problem: UUID, session: UUID, key: String, content: String) async throws -> TutorMessage {
        guard hintKeys[key] == nil else { throw failure("IDEMPOTENCY_KEY_REUSED") }
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
        guard !progress.phase.terminal else { throw failure("TUTOR_PEDAGOGY_STOPPED") }
        guard available else {
            throw NetworkError.server(problem: ProblemDetails(type: "about:blank", title: "Unavailable", status: 503, code: "TUTOR_CAPABILITY_UNAVAILABLE", traceId: nil, details: nil))
        }
        let previous = progress
        let loseAck = failFirst
        failFirst = false
        let learner = try await append(problem: problem, session: session, key: key, content: content)
        let tr = self.session?.language == "tr"
        let phase: TutorPhase = intent == .challenge ? .needsReview : (replies.isEmpty ? .elicitConcept : .evaluateStep)
        let turn = TutorTurn(schemaVersion: "tutor-turn-v1", id: UUID(), tutorSessionId: session,
            learnerMessageId: learner.id, learnerSequenceNumber: learner.sequenceNumber, replyId: UUID(), role: "TUTOR",
            content: tr ? "Hangi kavram veya kuralı kullanmayı önerirsin?" : "Which concept or rule would you propose using?",
            language: tr ? "tr" : "en", policyVersion: "socratic-policy-v1", previousPhase: previous.phase,
            progress: TutorProgress(phase: phase, unproductiveCount: 0, turnCount: previous.turnCount + 1), intent: intent,
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
