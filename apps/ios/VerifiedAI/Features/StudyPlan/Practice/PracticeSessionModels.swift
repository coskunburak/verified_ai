import Foundation

public enum PracticeSessionMode: String, Codable, Equatable, Sendable, CaseIterable {
    case daily121 = "DAILY_1_2_1"
    case trapVerificationDrill = "TRAP_VERIFICATION_DRILL"
    case spacedReview = "SPACED_REVIEW"
    case examRoiSynthesis = "EXAM_ROI_SYNTHESIS"
}

public enum PracticeSessionStatus: String, Codable, Equatable, Sendable, CaseIterable {
    case inProgress = "IN_PROGRESS"
    case completed = "COMPLETED"
    case abandoned = "ABANDONED"
}

public enum PracticeSlotRole: String, Codable, Equatable, Sendable, CaseIterable {
    case trapClosure = "TRAP_CLOSURE"
    case spacedDrill = "SPACED_DRILL"
    case examSynthesis = "EXAM_SYNTHESIS"

    public var localizedTitleKey: String {
        switch self {
        case .trapClosure: return "practice.slot.trap_closure"
        case .spacedDrill: return "practice.slot.spaced_drill"
        case .examSynthesis: return "practice.slot.exam_synthesis"
        }
    }
}

public enum PracticeQuestionSource: String, Codable, Equatable, Sendable, CaseIterable {
    case curatedBank = "CURATED_BANK"
    case deterministicTwinTier1 = "DETERMINISTIC_TWIN_TIER1"
    case synthesizedTwinTier2 = "SYNTHESIZED_TWIN_TIER2"
}

public enum PracticeItemStatus: String, Codable, Equatable, Sendable, CaseIterable {
    case pending = "PENDING"
    case submittedCorrect = "SUBMITTED_CORRECT"
    case submittedIncorrect = "SUBMITTED_INCORRECT"
    case skipped = "SKIPPED"
}

public struct ZpdWindow: Codable, Equatable, Sendable {
    public let lowerBound: Double
    public let upperBound: Double
    public let targetDifficulty: Double

    public init(lowerBound: Double, upperBound: Double, targetDifficulty: Double) {
        self.lowerBound = lowerBound
        self.upperBound = upperBound
        self.targetDifficulty = targetDifficulty
    }
}

public struct VerificationLockState: Codable, Equatable, Sendable {
    public let isLocked: Bool
    public let lockDurationSeconds: Int
    public let remainingLockSeconds: Int
    public let unlockedAt: Date?

    public init(isLocked: Bool, lockDurationSeconds: Int, remainingLockSeconds: Int, unlockedAt: Date? = nil) {
        self.isLocked = isLocked
        self.lockDurationSeconds = lockDurationSeconds
        self.remainingLockSeconds = remainingLockSeconds
        self.unlockedAt = unlockedAt
    }
}

public struct PracticeSessionItem: Codable, Equatable, Sendable, Identifiable {
    public let id: UUID
    public let sessionId: UUID
    public let userId: UUID
    public let ordinalIndex: Int
    public let slotRole: PracticeSlotRole
    public let questionSource: PracticeQuestionSource
    public let sourceQuestionId: String
    public let skillId: String
    public let trapFamily: String?
    public let targetDifficulty: Double
    public let itemDifficulty: Double
    public let zpdWindow: ZpdWindow
    public let promptLatex: String
    public let promptInstructionTr: String
    public let promptInstructionEn: String
    public let canonicalSolutionExpr: String
    public let trapDistractorExpr: String?
    public let sympyVerificationHash: String
    public let isTwinVerification: Bool
    public let lockState: VerificationLockState
    public let hintsUnlockedCount: Int
    public let attemptCount: Int
    public let itemStatus: PracticeItemStatus
    public let submittedAnswerLatex: String?
    public let detectedTrapTriggered: Bool
    public let verificationDurationMs: Int?
    public let submittedAt: Date?
    public let createdAt: Date

    public init(
        id: UUID,
        sessionId: UUID,
        userId: UUID,
        ordinalIndex: Int,
        slotRole: PracticeSlotRole,
        questionSource: PracticeQuestionSource,
        sourceQuestionId: String,
        skillId: String,
        trapFamily: String?,
        targetDifficulty: Double,
        itemDifficulty: Double,
        zpdWindow: ZpdWindow,
        promptLatex: String,
        promptInstructionTr: String,
        promptInstructionEn: String,
        canonicalSolutionExpr: String,
        trapDistractorExpr: String?,
        sympyVerificationHash: String,
        isTwinVerification: Bool,
        lockState: VerificationLockState,
        hintsUnlockedCount: Int,
        attemptCount: Int,
        itemStatus: PracticeItemStatus,
        submittedAnswerLatex: String?,
        detectedTrapTriggered: Bool,
        verificationDurationMs: Int?,
        submittedAt: Date?,
        createdAt: Date
    ) {
        self.id = id
        self.sessionId = sessionId
        self.userId = userId
        self.ordinalIndex = ordinalIndex
        self.slotRole = slotRole
        self.questionSource = questionSource
        self.sourceQuestionId = sourceQuestionId
        self.skillId = skillId
        self.trapFamily = trapFamily
        self.targetDifficulty = targetDifficulty
        self.itemDifficulty = itemDifficulty
        self.zpdWindow = zpdWindow
        self.promptLatex = promptLatex
        self.promptInstructionTr = promptInstructionTr
        self.promptInstructionEn = promptInstructionEn
        self.canonicalSolutionExpr = canonicalSolutionExpr
        self.trapDistractorExpr = trapDistractorExpr
        self.sympyVerificationHash = sympyVerificationHash
        self.isTwinVerification = isTwinVerification
        self.lockState = lockState
        self.hintsUnlockedCount = hintsUnlockedCount
        self.attemptCount = attemptCount
        self.itemStatus = itemStatus
        self.submittedAnswerLatex = submittedAnswerLatex
        self.detectedTrapTriggered = detectedTrapTriggered
        self.verificationDurationMs = verificationDurationMs
        self.submittedAt = submittedAt
        self.createdAt = createdAt
    }
}

public struct PracticeSession: Codable, Equatable, Sendable, Identifiable {
    public let id: UUID
    public let userId: UUID
    public let sourceRecommendationId: UUID?
    public let sessionMode: PracticeSessionMode
    public let status: PracticeSessionStatus
    public let targetSkillId: String
    public let activeTrapFamily: String?
    public let learnerMasterySnapshot: Double
    public let totalItems: Int
    public let completedItems: Int
    public let verifiedCorrectItems: Int
    public let trapClosureAchieved: Bool
    public let startedAt: Date
    public let completedAt: Date?
    public let abandonedAt: Date?
    public let updatedAt: Date
    public let items: [PracticeSessionItem]

    public init(
        id: UUID,
        userId: UUID,
        sourceRecommendationId: UUID?,
        sessionMode: PracticeSessionMode,
        status: PracticeSessionStatus,
        targetSkillId: String,
        activeTrapFamily: String?,
        learnerMasterySnapshot: Double,
        totalItems: Int,
        completedItems: Int,
        verifiedCorrectItems: Int,
        trapClosureAchieved: Bool,
        startedAt: Date,
        completedAt: Date?,
        abandonedAt: Date?,
        updatedAt: Date,
        items: [PracticeSessionItem]
    ) {
        self.id = id
        self.userId = userId
        self.sourceRecommendationId = sourceRecommendationId
        self.sessionMode = sessionMode
        self.status = status
        self.targetSkillId = targetSkillId
        self.activeTrapFamily = activeTrapFamily
        self.learnerMasterySnapshot = learnerMasterySnapshot
        self.totalItems = totalItems
        self.completedItems = completedItems
        self.verifiedCorrectItems = verifiedCorrectItems
        self.trapClosureAchieved = trapClosureAchieved
        self.startedAt = startedAt
        self.completedAt = completedAt
        self.abandonedAt = abandonedAt
        self.updatedAt = updatedAt
        self.items = items
    }
}

public struct PracticeSubmissionRequest: Codable, Equatable, Sendable {
    public let submittedAnswerLatex: String?
    public let clientElapsedSeconds: Int?
    public let unlockHintRequested: Bool?

    public init(submittedAnswerLatex: String?, clientElapsedSeconds: Int?, unlockHintRequested: Bool? = false) {
        self.submittedAnswerLatex = submittedAnswerLatex
        self.clientElapsedSeconds = clientElapsedSeconds
        self.unlockHintRequested = unlockHintRequested
    }
}

public struct PracticeSubmissionResponse: Codable, Equatable, Sendable {
    public let itemStatus: PracticeItemStatus
    public let isCorrect: Bool
    public let trapTriggered: Bool
    public let trapDiagnosticKey: String?
    public let trapDiagnosticMessageTr: String?
    public let trapDiagnosticMessageEn: String?
    public let verificationLockActive: Bool
    public let remainingLockSeconds: Int
    public let trapClosureAchieved: Bool
    public let session: PracticeSession

    public init(
        itemStatus: PracticeItemStatus,
        isCorrect: Bool,
        trapTriggered: Bool,
        trapDiagnosticKey: String?,
        trapDiagnosticMessageTr: String?,
        trapDiagnosticMessageEn: String?,
        verificationLockActive: Bool,
        remainingLockSeconds: Int,
        trapClosureAchieved: Bool,
        session: PracticeSession
    ) {
        self.itemStatus = itemStatus
        self.isCorrect = isCorrect
        self.trapTriggered = trapTriggered
        self.trapDiagnosticKey = trapDiagnosticKey
        self.trapDiagnosticMessageTr = trapDiagnosticMessageTr
        self.trapDiagnosticMessageEn = trapDiagnosticMessageEn
        self.verificationLockActive = verificationLockActive
        self.remainingLockSeconds = remainingLockSeconds
        self.trapClosureAchieved = trapClosureAchieved
        self.session = session
    }
}
