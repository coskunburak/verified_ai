import Foundation

enum EntitlementTier: String, CaseIterable, Codable, Equatable, Sendable {
    case free = "FREE"
    case pro = "PRO"
    case proPlus = "PRO_PLUS"

    var title: String {
        switch self {
        case .free:
            "Free"
        case .pro:
            "Pro"
        case .proPlus:
            "Pro Plus"
        }
    }

    func includes(_ requiredTier: EntitlementTier) -> Bool {
        Self.allCases.firstIndex(of: self)! >= Self.allCases.firstIndex(of: requiredTier)!
    }
}

enum EntitlementStatus: String, Codable, Equatable, Sendable {
    case active = "ACTIVE"
    case gracePeriod = "GRACE_PERIOD"
    case billingRetry = "BILLING_RETRY"
    case expired = "EXPIRED"
    case revoked = "REVOKED"

    var grantsAccess: Bool {
        self == .active || self == .gracePeriod || self == .billingRetry
    }
}

enum EntitlementSource: String, Codable, Equatable, Sendable {
    case defaultFree = "DEFAULT_FREE"
    case appStoreSubscription = "APP_STORE_SUBSCRIPTION"
    case promotional = "PROMOTIONAL"
    case adminSupport = "ADMIN_SUPPORT"
}

enum PremiumCapability: String, CaseIterable, Codable, Equatable, Identifiable, Sendable {
    case basicSolve = "BASIC_SOLVE"
    case verifiedSolve = "VERIFIED_SOLVE"
    case advancedTutor = "ADVANCED_TUTOR"
    case mistakeHistory = "MISTAKE_HISTORY"
    case adaptivePlan = "ADAPTIVE_PLAN"
    case mockExam = "MOCK_EXAM"
    case premiumModelFallback = "PREMIUM_MODEL_FALLBACK"

    var id: String { rawValue }

    var minimumTier: EntitlementTier {
        switch self {
        case .basicSolve:
            .free
        case .verifiedSolve, .advancedTutor, .mistakeHistory, .adaptivePlan:
            .pro
        case .mockExam, .premiumModelFallback:
            .proPlus
        }
    }

    var title: String {
        switch self {
        case .basicSolve:
            "Basic solve"
        case .verifiedSolve:
            "Verified solve"
        case .advancedTutor:
            "Advanced tutor"
        case .mistakeHistory:
            "Mistake history"
        case .adaptivePlan:
            "Adaptive plan"
        case .mockExam:
            "Mock exam"
        case .premiumModelFallback:
            "Premium model fallback"
        }
    }
}

struct Entitlement: Codable, Equatable, Sendable {
    let id: UUID
    let userId: UUID
    let tier: EntitlementTier
    let source: EntitlementSource
    let status: EntitlementStatus
    let effectiveAt: Date
    let expiresAt: Date?
    let capabilities: Set<PremiumCapability>
    let version: Int64?

    func allows(_ capability: PremiumCapability) -> Bool {
        status.grantsAccess && tier.includes(capability.minimumTier) && capabilities.contains(capability)
    }
}

enum LearningOsEffectiveTier: String, Codable, Equatable, Sendable {
    case free = "FREE", pro = "PRO", coachPro = "COACH_PRO"
}

enum LearningOsCapability: String, Codable, Equatable, Sendable, CaseIterable {
    case attemptVerify = "ATTEMPT_VERIFY", deterministicTwin = "DETERMINISTIC_TWIN", hybridTwin = "HYBRID_TWIN"
    case mistakePassportFull = "MISTAKE_PASSPORT_FULL", dailyCoachMission = "DAILY_COACH_MISSION"
    case microLesson = "MICRO_LESSON", examReadinessSimulator = "EXAM_READINESS_SIMULATOR", timedMockSession = "TIMED_MOCK_SESSION"
}

enum LearningPackage: String, Decodable, Equatable, Sendable {
    case free = "FREE", pro = "PRO", coach = "COACH_PRO", season = "YKS_SEASON_PASS"
}

enum LearningOsAddOn: String, Equatable, Sendable {
    case tutorCopilotPack = "TUTOR_COPILOT_PACK"
    var isAvailable: Bool { false }
}

public struct QuotaSummary: Codable, Sendable, Equatable {
    public let periodKey: String
    public let aiVerificationsUsed: Int
    public let aiVerificationsLimit: Int
    public let remainingAiChecks: Int
    public let hybridTwinsUsed: Int
    public let hybridTwinsLimit: Int
    public let deterministicTwinsUnlimited: Bool
    public let costCircuitBreakerTripped: Bool
    public let degradedToDeterministicOnly: Bool
    public let resetsAt: String

    public init(
        periodKey: String,
        aiVerificationsUsed: Int,
        aiVerificationsLimit: Int,
        remainingAiChecks: Int,
        hybridTwinsUsed: Int,
        hybridTwinsLimit: Int,
        deterministicTwinsUnlimited: Bool = true,
        costCircuitBreakerTripped: Bool,
        degradedToDeterministicOnly: Bool,
        resetsAt: String
    ) {
        self.periodKey = periodKey
        self.aiVerificationsUsed = aiVerificationsUsed
        self.aiVerificationsLimit = aiVerificationsLimit
        self.remainingAiChecks = remainingAiChecks
        self.hybridTwinsUsed = hybridTwinsUsed
        self.hybridTwinsLimit = hybridTwinsLimit
        self.deterministicTwinsUnlimited = deterministicTwinsUnlimited
        self.costCircuitBreakerTripped = costCircuitBreakerTripped
        self.degradedToDeterministicOnly = degradedToDeterministicOnly
        self.resetsAt = resetsAt
    }

    public var usageProgress: Double {
        guard aiVerificationsLimit > 0 else { return 0 }
        return min(1.0, max(0.0, Double(aiVerificationsUsed) / Double(aiVerificationsLimit)))
    }

    public var isQuotaExhausted: Bool {
        remainingAiChecks == 0 || degradedToDeterministicOnly
    }

    public var resetsAtDate: Date? {
        let fractionalFormatter = ISO8601DateFormatter()
        fractionalFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fractionalFormatter.date(from: resetsAt) {
            return date
        }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: resetsAt)
    }
}

struct LearningOsEntitlementV2: Decodable, Equatable, Sendable {
    enum HistoryLevel: String, Decodable, Equatable, Sendable { case full = "FULL", summary = "SUMMARY", none = "NONE" }
    struct History: Decodable, Equatable, Sendable {
        let mistakePassport: HistoryLevel
        let skillMastery: HistoryLevel
        let priorExams: HistoryLevel
    }
    let effectiveTier: LearningOsEffectiveTier
    let legacyTier: EntitlementTier
    let status: EntitlementStatus
    let source: EntitlementSource?
    let expiresAt: String?
    let capabilities: Set<PremiumCapability>?
    let packageCode: LearningPackage?
    let featureEnabled: Bool?
    let learningOsCapabilities: Set<LearningOsCapability>?
    let historyAccess: History?
    let accessEvaluatedAt: String?
    let gracePeriodExpiresAt: String?
    let paidAccessActive: Bool?
    public let quotaSummary: QuotaSummary?

    init(
        effectiveTier: LearningOsEffectiveTier,
        legacyTier: EntitlementTier,
        status: EntitlementStatus,
        source: EntitlementSource? = nil,
        expiresAt: String? = nil,
        capabilities: Set<PremiumCapability>? = nil,
        packageCode: LearningPackage? = nil,
        featureEnabled: Bool? = nil,
        learningOsCapabilities: Set<LearningOsCapability>? = nil,
        historyAccess: History? = nil,
        accessEvaluatedAt: String? = nil,
        gracePeriodExpiresAt: String? = nil,
        paidAccessActive: Bool? = nil,
        quotaSummary: QuotaSummary? = nil
    ) {
        self.effectiveTier = effectiveTier
        self.legacyTier = legacyTier
        self.status = status
        self.source = source
        self.expiresAt = expiresAt
        self.capabilities = capabilities
        self.packageCode = packageCode
        self.featureEnabled = featureEnabled
        self.learningOsCapabilities = learningOsCapabilities
        self.historyAccess = historyAccess
        self.accessEvaluatedAt = accessEvaluatedAt
        self.gracePeriodExpiresAt = gracePeriodExpiresAt
        self.paidAccessActive = paidAccessActive
        self.quotaSummary = quotaSummary
    }
}
