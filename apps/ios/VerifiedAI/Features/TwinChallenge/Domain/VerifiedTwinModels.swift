import Foundation

public enum VerifiedTwinStatus: String, Codable, Sendable {
    case activeLocked = "ACTIVE_LOCKED"
    case activeUnlocked = "ACTIVE_UNLOCKED"
    case verifiedUnassistedSuccess = "VERIFIED_UNASSISTED_SUCCESS"
    case solutionRevealedAssisted = "SOLUTION_REVEALED_ASSISTED"
}

public struct VerifiedTwinChallenge: Codable, Sendable, Identifiable, Equatable {
    public var id: UUID { twinId }
    public let twinId: UUID
    public let attemptId: UUID
    public let targetSkillCode: String
    public let rootCauseSkillCode: String
    public let problemStatement: String
    public let problemLatex: String
    public let canonicalProblemAst: [String: String]
    public let lockUntil: Date
    public let remainingLockSeconds: Int
    public let status: VerifiedTwinStatus
    public let evidenceClassification: String
    public let remediationCreditGranted: Double
    public let createdAt: Date

    public init(
        twinId: UUID,
        attemptId: UUID,
        targetSkillCode: String,
        rootCauseSkillCode: String,
        problemStatement: String,
        problemLatex: String,
        canonicalProblemAst: [String: String] = [:],
        lockUntil: Date,
        remainingLockSeconds: Int,
        status: VerifiedTwinStatus,
        evidenceClassification: String,
        remediationCreditGranted: Double,
        createdAt: Date
    ) {
        self.twinId = twinId
        self.attemptId = attemptId
        self.targetSkillCode = targetSkillCode
        self.rootCauseSkillCode = rootCauseSkillCode
        self.problemStatement = problemStatement
        self.problemLatex = problemLatex
        self.canonicalProblemAst = canonicalProblemAst
        self.lockUntil = lockUntil
        self.remainingLockSeconds = remainingLockSeconds
        self.status = status
        self.evidenceClassification = evidenceClassification
        self.remediationCreditGranted = remediationCreditGranted
        self.createdAt = createdAt
    }
}

public struct TwinSubmissionPayload: Codable, Sendable {
    public let submissionType: String
    public let learnerAnswerLatex: String?
    public let forceEarlyForfeit: Bool

    public init(
        submissionType: String,
        learnerAnswerLatex: String? = nil,
        forceEarlyForfeit: Bool = false
    ) {
        self.submissionType = submissionType
        self.learnerAnswerLatex = learnerAnswerLatex
        self.forceEarlyForfeit = forceEarlyForfeit
    }
}

public protocol TwinChallengeServicing: Sendable {
    func generateTwin(attemptId: UUID) async throws -> VerifiedTwinChallenge
    func submitTwin(twinId: UUID, payload: TwinSubmissionPayload) async throws -> VerifiedTwinChallenge
}
