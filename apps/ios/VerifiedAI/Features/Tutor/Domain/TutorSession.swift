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
    func turns(problem: UUID, session: UUID, after: Int) async throws -> [TutorTurn]
    func submitTurn(problem: UUID, session: UUID, key: String, content: String, intent: TutorIntent) async throws -> TutorTurn
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

// Separate from ACTIVE/CLOSED lifecycle. Completion means reflection, never verified mathematics.
enum TutorPhase: String, Codable, Sendable {
    case clarifyGoal = "CLARIFY_GOAL", elicitConcept = "ELICIT_CONCEPT", evaluateStep = "EVALUATE_LEARNER_STEP"
    case minimalSupport = "OFFER_MINIMAL_SUPPORT", advance = "ADVANCE_ONE_STEP", summarize = "SUMMARIZE_CONCEPT"
    case completed = "COMPLETED", paused = "PAUSED", needsReview = "NEEDS_REVIEW", unavailable = "UNAVAILABLE"
    var terminal: Bool { [.completed, .paused, .needsReview, .unavailable].contains(self) }
    var localizationKey: String { "tutor.phase." + rawValue }
}
enum TutorIntent: String, Codable, CaseIterable, Sendable {
    case unknown = "UNKNOWN", attemptStep = "ATTEMPT_STEP", explainConcept = "EXPLAIN_CONCEPT"
    case smallHint = "SMALL_HINT", revealAnswer = "REVEAL_ANSWER", confused = "CONFUSED"
    case challenge = "CHALLENGE", offTopic = "OFF_TOPIC", summarize = "SUMMARIZE"
    var localizationKey: String { "tutor.intent." + rawValue }
}
enum TutorAction: String, Codable, Sendable {
    case askGoal = "ASK_GOAL", askConcept = "ASK_CONCEPT", askStep = "ASK_STEP", nudge = "CONCEPTUAL_NUDGE"
    case advance = "ADVANCE", reflect = "REFLECT", finish = "FINISH", pause = "PAUSE"
    case review = "REQUEST_REVIEW", unavailable = "CAPABILITY_UNAVAILABLE"
}
enum TutorOutcome: String, Codable, Sendable {
    case guidance = "GUIDANCE", paused = "PAUSED", needsReview = "NEEDS_REVIEW", completed = "COMPLETED", unavailable = "UNAVAILABLE"
}
struct TutorProgress: Codable, Equatable, Sendable {
    let phase: TutorPhase
    let unproductiveCount: Int
    let turnCount: Int
}
struct TutorTurn: Codable, Equatable, Identifiable, Sendable {
    let schemaVersion: String
    let id: UUID
    let tutorSessionId: UUID
    let learnerMessageId: UUID
    let learnerSequenceNumber: Int
    let replyId: UUID
    let role: String
    let content: String
    let language: String
    let policyVersion: String
    let previousPhase: TutorPhase
    let progress: TutorProgress
    let intent: TutorIntent
    let action: TutorAction
    let outcome: TutorOutcome
    let correctness: String
    let referenceStatus: String
    let uncertain: Bool
    let provenance: String
    let createdAt: String
    let trainingEligible: Bool
    func validated(session: UUID) throws -> Self {
        guard schemaVersion == "tutor-turn-v1", tutorSessionId == session, role == "TUTOR",
              policyVersion == "socratic-policy-v1", correctness == "UNKNOWN", referenceStatus == "UNAVAILABLE",
              uncertain, !trainingEligible, provenance == "DETERMINISTIC_POLICY", ["en", "tr"].contains(language),
              (1...100).contains(learnerSequenceNumber), (1...12).contains(progress.turnCount),
              (0...3).contains(progress.unproductiveCount), !previousPhase.terminal,
              !content.isEmpty, content.unicodeScalars.count <= 2000, content.utf8.count <= 8000,
              TutorTimestamp.parse(createdAt) != nil else { throw NetworkError.invalidResponse }
        return self
    }
}
