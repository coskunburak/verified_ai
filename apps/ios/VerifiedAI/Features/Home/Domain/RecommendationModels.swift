import Foundation

public enum RecommendationType: String, Codable, CaseIterable, Sendable {
    case prerequisiteRepair = "PREREQUISITE_REPAIR"
    case mistakeRepair = "MISTAKE_REPAIR"
    case spacedRetention = "SPACED_RETENTION"
    case highRoiTopic = "HIGH_ROI_TOPIC"

    public var localizedKey: String {
        switch self {
        case .prerequisiteRepair: return "coach.nba.type.prerequisite_repair"
        case .mistakeRepair: return "coach.nba.type.mistake_repair"
        case .spacedRetention: return "coach.nba.type.spaced_retention"
        case .highRoiTopic: return "coach.nba.type.high_roi_topic"
        }
    }

    public var systemIcon: String {
        switch self {
        case .prerequisiteRepair: return "hammer.fill"
        case .mistakeRepair: return "shield.lefthalf.filled"
        case .spacedRetention: return "clock.arrow.circlepath"
        case .highRoiTopic: return "chart.line.uptrend.xyaxis.circle.fill"
        }
    }
}

public enum RecommendationRationaleCode: String, Codable, Sendable {
    case prerequisiteRepair = "PREREQUISITE_REPAIR"
    case activeRecurringTrap = "ACTIVE_RECURRING_TRAP"
    case retentionHalfLifeDue = "RETENTION_HALF_LIFE_DUE"
    case highExamNetRoi = "HIGH_EXAM_NET_ROI"

    public var localizedKey: String {
        switch self {
        case .prerequisiteRepair: return "coach.nba.rationale.prerequisite_repair"
        case .activeRecurringTrap: return "coach.nba.rationale.active_recurring_trap"
        case .retentionHalfLifeDue: return "coach.nba.rationale.retention_half_life_due"
        case .highExamNetRoi: return "coach.nba.rationale.high_exam_net_roi"
        }
    }
}

public enum RecommendationStatus: String, Codable, Sendable {
    case active = "ACTIVE"
    case accepted = "ACCEPTED"
    case dismissed = "DISMISSED"
    case expired = "EXPIRED"
}

public struct RecommendationRationaleParams: Codable, Equatable, Sendable {
    public let skillCode: String?
    public let mistakeCode: String?
    public let mistakePropensity: Double?
    public let unresolvedTrapCount: Int?
    public let currentRetention: Double?
    public let retentionThreshold: Double?
    public let halfLifeDays: Double?
    public let prerequisiteSkillCode: String?
    public let blockedSkillCode: String?
    public let currentMastery: Double?
    public let requiredMasteryThreshold: Double?
    public let examCode: String?
    public let examWeight: Double?
    public let estimatedNetGain: Double?
    public let opportunityAreaScore: Double?

    public init(
        skillCode: String? = nil,
        mistakeCode: String? = nil,
        mistakePropensity: Double? = nil,
        unresolvedTrapCount: Int? = nil,
        currentRetention: Double? = nil,
        retentionThreshold: Double? = nil,
        halfLifeDays: Double? = nil,
        prerequisiteSkillCode: String? = nil,
        blockedSkillCode: String? = nil,
        currentMastery: Double? = nil,
        requiredMasteryThreshold: Double? = nil,
        examCode: String? = nil,
        examWeight: Double? = nil,
        estimatedNetGain: Double? = nil,
        opportunityAreaScore: Double? = nil
    ) {
        self.skillCode = skillCode
        self.mistakeCode = mistakeCode
        self.mistakePropensity = mistakePropensity
        self.unresolvedTrapCount = unresolvedTrapCount
        self.currentRetention = currentRetention
        self.retentionThreshold = retentionThreshold
        self.halfLifeDays = halfLifeDays
        self.prerequisiteSkillCode = prerequisiteSkillCode
        self.blockedSkillCode = blockedSkillCode
        self.currentMastery = currentMastery
        self.requiredMasteryThreshold = requiredMasteryThreshold
        self.examCode = examCode
        self.examWeight = examWeight
        self.estimatedNetGain = estimatedNetGain
        self.opportunityAreaScore = opportunityAreaScore
    }
}

public struct LearnerRecommendationDto: Identifiable, Codable, Equatable, Sendable {
    public let id: UUID
    public let userId: UUID
    public let recommendationType: RecommendationType
    public let targetSkillCode: String
    public let targetMistakeCode: String?
    public let priorityScore: Double
    public let rationaleCode: RecommendationRationaleCode
    public let rationaleParams: RecommendationRationaleParams
    public let status: RecommendationStatus
    public let expiresAt: Date
    public let createdAt: Date

    public init(
        id: UUID,
        userId: UUID,
        recommendationType: RecommendationType,
        targetSkillCode: String,
        targetMistakeCode: String? = nil,
        priorityScore: Double,
        rationaleCode: RecommendationRationaleCode,
        rationaleParams: RecommendationRationaleParams,
        status: RecommendationStatus = .active,
        expiresAt: Date,
        createdAt: Date
    ) {
        self.id = id
        self.userId = userId
        self.recommendationType = recommendationType
        self.targetSkillCode = targetSkillCode
        self.targetMistakeCode = targetMistakeCode
        self.priorityScore = priorityScore
        self.rationaleCode = rationaleCode
        self.rationaleParams = rationaleParams
        self.status = status
        self.expiresAt = expiresAt
        self.createdAt = createdAt
    }

    public enum UrgencyLevel: String, Sendable {
        case high
        case medium
        case standard
    }

    public var urgencyLevel: UrgencyLevel {
        if priorityScore >= 70.0 {
            return .high
        } else if priorityScore >= 45.0 {
            return .medium
        } else {
            return .standard
        }
    }

    public var mathPreviewLatex: String {
        if targetSkillCode.contains("LIMIT") {
            return "\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1"
        } else if targetSkillCode.contains("DERIV") || targetSkillCode.contains("CHAIN") {
            return "\\frac{d}{dx}[f(g(x))] = f'(g(x)) \\cdot g'(x)"
        } else if targetSkillCode.contains("QUADRAT") || targetSkillCode.contains("FACTOR") {
            return "ax^2 + bx + c = 0"
        } else if targetSkillCode.contains("TRIG") || targetSkillCode.contains("PYTHAG") {
            return "\\sin^2(\\theta) + \\cos^2(\\theta) = 1"
        } else if targetSkillCode.contains("BAYES") || targetSkillCode.contains("PROB") {
            return "P(A|B) = \\frac{P(B|A) \\cdot P(A)}{P(B)}"
        } else {
            return "\\int f(x) \\, dx"
        }
    }

    public var explainableRationaleText: String {
        switch rationaleCode {
        case .prerequisiteRepair:
            let blocked = rationaleParams.blockedSkillCode ?? "advanced topics"
            let req = Int((rationaleParams.requiredMasteryThreshold ?? 0.50) * 100)
            let curr = Int((rationaleParams.currentMastery ?? 0.0) * 100)
            return "Strengthening foundational prerequisites unlocks \(blocked) (target \(req)%, currently \(curr)%)."
        case .activeRecurringTrap:
            let trap = targetMistakeCode ?? "recurring trap"
            let count = rationaleParams.unresolvedTrapCount ?? 1
            return "Targeted practice resolves \(trap) observed across \(count) recent attempts."
        case .retentionHalfLifeDue:
            let days = Int(rationaleParams.halfLifeDays ?? 14.0)
            return "Optimal memory refresh interval reached based on estimated \(days)-day decay curve."
        case .highExamNetRoi:
            let exam = rationaleParams.examCode ?? "target exam"
            return "High topic weighting on \(exam) offers maximized expected score improvement."
        }
    }
}

public struct LearnerRecommendationsResponseDto: Codable, Equatable, Sendable {
    public let userId: UUID
    public let generatedAt: Date
    public let deterministicEngineVersion: String
    public let llmTokenCostUsd: Double
    public let recommendations: [LearnerRecommendationDto]

    public init(
        userId: UUID,
        generatedAt: Date,
        deterministicEngineVersion: String = "NBA-v1.0",
        llmTokenCostUsd: Double = 0.0,
        recommendations: [LearnerRecommendationDto]
    ) {
        self.userId = userId
        self.generatedAt = generatedAt
        self.deterministicEngineVersion = deterministicEngineVersion
        self.llmTokenCostUsd = llmTokenCostUsd
        self.recommendations = recommendations
    }
}

public struct RecommendationActionResponseDto: Codable, Equatable, Sendable {
    public let recommendationId: UUID
    public let status: String
    public let nextSessionType: String?
    public let targetSkillCode: String?
    public let targetMistakeCode: String?
    public let deepLinkUri: String?

    public init(
        recommendationId: UUID,
        status: String,
        nextSessionType: String? = nil,
        targetSkillCode: String? = nil,
        targetMistakeCode: String? = nil,
        deepLinkUri: String? = nil
    ) {
        self.recommendationId = recommendationId
        self.status = status
        self.nextSessionType = nextSessionType
        self.targetSkillCode = targetSkillCode
        self.targetMistakeCode = targetMistakeCode
        self.deepLinkUri = deepLinkUri
    }
}
