import Foundation

enum ShareableArtifactType: String, Codable, Sendable, CaseIterable, Identifiable {
    case weeklyProgressSummary = "WEEKLY_PROGRESS_SUMMARY"
    case masteryMilestone = "MASTERY_MILESTONE"
    case counterfactualChallengeCard = "COUNTERFACTUAL_CHALLENGE_CARD"
    case examReadinessMilestone = "EXAM_READINESS_MILESTONE"

    var id: String { rawValue }

    var localizationKey: String {
        switch self {
        case .weeklyProgressSummary:
            return "growth.share.type.weekly_progress"
        case .masteryMilestone:
            return "growth.share.type.mastery_milestone"
        case .counterfactualChallengeCard:
            return "growth.share.type.challenge_card"
        case .examReadinessMilestone:
            return "growth.share.type.exam_readiness"
        }
    }
}

enum ShareableArtifactStatus: String, Codable, Sendable {
    case active = "ACTIVE"
    case revoked = "REVOKED"
    case expired = "EXPIRED"
}

enum ReferralAttributionSource: String, Codable, Sendable {
    case deepLink = "DEEP_LINK"
    case shareCard = "SHARE_CARD"
    case manualCode = "MANUAL_CODE"
    case weeklyPdfQr = "WEEKLY_PDF_QR"
}

enum ReferralAttributionStatus: String, Codable, Sendable {
    case attributed = "ATTRIBUTED"
    case qualified = "QUALIFIED"
    case rewardGranted = "REWARD_GRANTED"
    case rejectedAbuse = "REJECTED_ABUSE"
    case expired = "EXPIRED"
}

enum StoreReviewTriggerMoment: String, Codable, Sendable {
    case independentTwinMastered = "INDEPENDENT_TWIN_MASTERED"
    case masteryMilestoneUnlocked = "MASTERY_MILESTONE_UNLOCKED"
    case weeklyProgressWin = "WEEKLY_PROGRESS_WIN"
    case mockExamCompleted = "MOCK_EXAM_COMPLETED"
}

enum StoreReviewOutcome: String, Codable, Sendable {
    case promptShown = "PROMPT_SHOWN"
    case promptDismissed = "PROMPT_DISMISSED"
    case promptDeferred = "PROMPT_DEFERRED"
    case promptDeclinedPermanently = "PROMPT_DECLINED_PERMANENTLY"
}

enum StoreReviewIneligibilityReason: String, Codable, Sendable {
    case eligible = "ELIGIBLE"
    case activeTimer = "INELIGIBLE_ACTIVE_TIMER"
    case userDeclined = "INELIGIBLE_USER_DECLINED"
    case recentFriction = "INELIGIBLE_RECENT_FRICTION"
    case insufficientEngagement = "INELIGIBLE_INSUFFICIENT_ENGAGEMENT"
    case annualCapReached = "INELIGIBLE_ANNUAL_CAP_REACHED"
    case cooldownActive = "INELIGIBLE_COOLDOWN_ACTIVE"
}

enum GrowthSummaryValue: Codable, Sendable, Equatable {
    case string(String)
    case int(Int)
    case bool(Bool)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let boolVal = try? container.decode(Bool.self) {
            self = .bool(boolVal)
        } else if let intVal = try? container.decode(Int.self) {
            self = .int(intVal)
        } else if let strVal = try? container.decode(String.self) {
            self = .string(strVal)
        } else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unsupported summaryPayload value type"
            )
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let value):
            try container.encode(value)
        case .int(let value):
            try container.encode(value)
        case .bool(let value):
            try container.encode(value)
        }
    }

    var displayValue: String {
        switch self {
        case .string(let value):
            return value
        case .int(let value):
            return String(value)
        case .bool(let value):
            return value ? "true" : "false"
        }
    }
}

struct CreateShareArtifactRequestDTO: Codable, Sendable, Equatable {
    let artifactType: ShareableArtifactType
    let locale: String
    let displayAlias: String?
    let referralCode: String?
    let summaryPayload: [String: GrowthSummaryValue]
    let ttlDays: Int?
    let trainingEligible: Bool

    init(
        artifactType: ShareableArtifactType,
        locale: String = "en",
        displayAlias: String? = nil,
        referralCode: String? = nil,
        summaryPayload: [String: GrowthSummaryValue],
        ttlDays: Int? = 30
    ) {
        self.artifactType = artifactType
        self.locale = locale
        self.displayAlias = displayAlias
        self.referralCode = referralCode
        self.summaryPayload = summaryPayload
        self.ttlDays = ttlDays
        self.trainingEligible = false
    }
}

struct ShareableArtifactDTO: Codable, Sendable, Equatable, Identifiable {
    let shareId: UUID
    let shareToken: String
    let artifactType: ShareableArtifactType
    let locale: String
    let displayAlias: String?
    let referralCode: String?
    let summaryPayload: [String: GrowthSummaryValue]
    let deepLinkUrl: String
    let status: ShareableArtifactStatus
    let rawContentRedacted: Bool
    let openCount: Int
    let correlationId: String
    let trainingEligible: Bool
    let llmTokensUsed: Int
    let expiresAt: Date
    let revokedAt: Date?
    let createdAt: Date
    let replayed: Bool

    var id: UUID { shareId }
}

struct ShareableArtifactListResponseDTO: Codable, Sendable, Equatable {
    let items: [ShareableArtifactDTO]
    let totalCount: Int
    let trainingEligible: Bool
    let llmTokensUsed: Int
}

struct PublicShareArtifactDTO: Codable, Sendable, Equatable {
    let shareToken: String
    let artifactType: ShareableArtifactType
    let locale: String
    let displayAlias: String?
    let referralCode: String?
    let summaryPayload: [String: GrowthSummaryValue]
    let deepLinkUrl: String
    let rawContentRedacted: Bool
    let trainingEligible: Bool
    let llmTokensUsed: Int
    let expiresAt: Date
    let createdAt: Date
}

struct ReferralSummaryDTO: Codable, Sendable, Equatable {
    let referralCode: String
    let deepLinkUrl: String
    let active: Bool
    let totalAttributions: Int
    let qualifiedCount: Int
    let rewardedCount: Int
    let totalRewardCredits: Int
    let monthlyAttributionsRemaining: Int
    let annualRewardsRemaining: Int
    let trainingEligible: Bool
    let llmTokensUsed: Int
    let createdAt: Date
}

struct ReferralAttributeRequestDTO: Codable, Sendable, Equatable {
    let referralCode: String
    let attributionSource: ReferralAttributionSource
    let deviceFingerprintHash: String?
    let trainingEligible: Bool

    init(
        referralCode: String,
        attributionSource: ReferralAttributionSource,
        deviceFingerprintHash: String?
    ) {
        self.referralCode = referralCode
        self.attributionSource = attributionSource
        self.deviceFingerprintHash = deviceFingerprintHash
        self.trainingEligible = false
    }
}

struct ReferralAttributionResultDTO: Codable, Sendable, Equatable {
    let attributionId: UUID
    let referralCode: String
    let attributionSource: ReferralAttributionSource
    let status: ReferralAttributionStatus
    let rewardType: String?
    let rewardUnits: Int
    let correlationId: String
    let trainingEligible: Bool
    let llmTokensUsed: Int
    let attributedAt: Date
    let qualifiedAt: Date?
    let rewardGrantedAt: Date?
    let replayed: Bool
}

struct StoreReviewEligibilityDTO: Codable, Sendable, Equatable {
    let eligible: Bool
    let reasonCode: StoreReviewIneligibilityReason
    let nextEligibleAt: Date?
    let triggerMoment: StoreReviewTriggerMoment
    let promptsShownLast365Days: Int
    let trainingEligible: Bool
    let llmTokensUsed: Int
}

struct StoreReviewEventRequestDTO: Codable, Sendable, Equatable {
    let triggerMoment: StoreReviewTriggerMoment
    let outcome: StoreReviewOutcome
    let appVersion: String
    let locale: String
    let trainingEligible: Bool

    init(
        triggerMoment: StoreReviewTriggerMoment,
        outcome: StoreReviewOutcome,
        appVersion: String,
        locale: String
    ) {
        self.triggerMoment = triggerMoment
        self.outcome = outcome
        self.appVersion = appVersion
        self.locale = locale
        self.trainingEligible = false
    }
}

struct StoreReviewEventResponseDTO: Codable, Sendable, Equatable {
    let eventId: UUID
    let triggerMoment: StoreReviewTriggerMoment
    let outcome: StoreReviewOutcome
    let appVersion: String
    let locale: String
    let correlationId: String
    let trainingEligible: Bool
    let llmTokensUsed: Int
    let occurredAt: Date
    let replayed: Bool
}
