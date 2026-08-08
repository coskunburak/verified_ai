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
