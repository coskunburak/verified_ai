import Foundation

public enum SpacedTriggerType: String, Codable, Sendable {
    case retentionDecay = "RETENTION_DECAY"
    case trapImmunity7D = "TRAP_IMMUNITY_7D"

    public var localizedBadgeKey: String {
        switch self {
        case .retentionDecay:
            return "studyplan.spaced.badge.retention_refresh"
        case .trapImmunity7D:
            return "studyplan.spaced.badge.trap_immunity_7d"
        }
    }

    public var systemIcon: String {
        switch self {
        case .retentionDecay:
            return "clock.arrow.circlepath"
        case .trapImmunity7D:
            return "shield.checkered"
        }
    }
}

public enum SpacedQueueStatus: String, Codable, Sendable {
    case pending = "PENDING"
    case served = "SERVED"
    case completed = "COMPLETED"
    case dismissed = "DISMISSED"
    case expired = "EXPIRED"
}

public enum MistakeResolutionStatus: String, Codable, Sendable {
    case none = "NONE"
    case stableResolved = "STABLE_RESOLVED"
    case relapsed = "RELAPSED"
}

public struct SpacedQueueItem: Codable, Identifiable, Equatable, Sendable {
    public let itemId: UUID
    public var id: UUID { itemId }
    public let canonicalSkillCode: String
    public let skillDisplayName: String
    public let triggerType: SpacedTriggerType
    public let targetMistakeCode: String?
    public let estimatedRetention: Double
    public var retentionPercentageInt: Int {
        Int((estimatedRetention * 100.0).rounded())
    }
    public let halfLifeDays: Double
    public let dueAt: Date
    public let assignedTwinId: UUID?
    public let promptPreviewLatex: String
    public let status: SpacedQueueStatus
    public let pedagogicalBadgeKey: String

    public init(
        itemId: UUID,
        canonicalSkillCode: String,
        skillDisplayName: String,
        triggerType: SpacedTriggerType,
        targetMistakeCode: String? = nil,
        estimatedRetention: Double,
        halfLifeDays: Double,
        dueAt: Date,
        assignedTwinId: UUID? = nil,
        promptPreviewLatex: String,
        status: SpacedQueueStatus = .pending,
        pedagogicalBadgeKey: String? = nil
    ) {
        self.itemId = itemId
        self.canonicalSkillCode = canonicalSkillCode
        self.skillDisplayName = skillDisplayName
        self.triggerType = triggerType
        self.targetMistakeCode = targetMistakeCode
        self.estimatedRetention = estimatedRetention
        self.halfLifeDays = halfLifeDays
        self.dueAt = dueAt
        self.assignedTwinId = assignedTwinId
        self.promptPreviewLatex = promptPreviewLatex
        self.status = status
        self.pedagogicalBadgeKey = pedagogicalBadgeKey ?? triggerType.localizedBadgeKey
    }
}

public struct SpacedQueueResponse: Codable, Equatable, Sendable {
    public let schemaVersion: String
    public let userId: UUID
    public let evaluatedAt: Date
    public let dailyCap: Int
    public let surfacedCount: Int
    public let interleavedSkillCount: Int
    public let interleavingSatisfied: Bool
    public let items: [SpacedQueueItem]

    public init(
        schemaVersion: String = "1.0.0",
        userId: UUID,
        evaluatedAt: Date,
        dailyCap: Int = 3,
        surfacedCount: Int,
        interleavedSkillCount: Int,
        interleavingSatisfied: Bool,
        items: [SpacedQueueItem]
    ) {
        self.schemaVersion = schemaVersion
        self.userId = userId
        self.evaluatedAt = evaluatedAt
        self.dailyCap = dailyCap
        self.surfacedCount = surfacedCount
        self.interleavedSkillCount = interleavedSkillCount
        self.interleavingSatisfied = interleavingSatisfied
        self.items = items
    }
}

public struct SpacedItemCompleteRequest: Codable, Equatable, Sendable {
    public let passed: Bool
    public let independentVerification: Bool
    public let responseTimeMs: Int
    public let triggeredMistakeCode: String?

    public init(
        passed: Bool,
        independentVerification: Bool,
        responseTimeMs: Int,
        triggeredMistakeCode: String? = nil
    ) {
        self.passed = passed
        self.independentVerification = independentVerification
        self.responseTimeMs = responseTimeMs
        self.triggeredMistakeCode = triggeredMistakeCode
    }
}

public struct SpacedItemCompletionResponse: Codable, Equatable, Sendable {
    public let itemId: UUID
    public let previousHalfLifeDays: Double
    public let updatedHalfLifeDays: Double
    public let updatedRetention: Double
    public let nextDueAt: Date
    public let mistakeResolutionStatus: MistakeResolutionStatus

    public init(
        itemId: UUID,
        previousHalfLifeDays: Double,
        updatedHalfLifeDays: Double,
        updatedRetention: Double,
        nextDueAt: Date,
        mistakeResolutionStatus: MistakeResolutionStatus
    ) {
        self.itemId = itemId
        self.previousHalfLifeDays = previousHalfLifeDays
        self.updatedHalfLifeDays = updatedHalfLifeDays
        self.updatedRetention = updatedRetention
        self.nextDueAt = nextDueAt
        self.mistakeResolutionStatus = mistakeResolutionStatus
    }
}

public struct SpacedItemDismissRequest: Codable, Equatable, Sendable {
    public let reasonCode: String

    public init(reasonCode: String = "SCHEDULE_LATER") {
        self.reasonCode = reasonCode
    }
}
