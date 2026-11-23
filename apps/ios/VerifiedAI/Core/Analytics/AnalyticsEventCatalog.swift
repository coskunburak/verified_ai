import Foundation

public enum AnalyticsAuthMethod: String, Sendable, CaseIterable {
    case apple = "APPLE"
    case email = "EMAIL"
    case guest = "GUEST"
}

public enum AnalyticsLocale: String, Sendable, CaseIterable {
    case en = "en"
    case tr = "tr"

    public static func current() -> AnalyticsLocale {
        let code = Locale.current.language.languageCode?.identifier.lowercased() ?? "en"
        return code == "tr" ? .tr : .en
    }

    public static func from(_ raw: String?) -> AnalyticsLocale {
        guard let raw else { return current() }
        return raw.lowercased().hasPrefix("tr") ? .tr : .en
    }
}

public enum AnalyticsLatencyBucket: String, Sendable, CaseIterable {
    case sub200ms = "LT_200MS"
    case ms200To1000 = "MS_200_1000"
    case sec1To3 = "S_1_3"
    case over3s = "GT_3S"
}

public enum AnalyticsCaptureSource: String, Sendable, CaseIterable {
    case camera = "CAMERA"
    case photoLibrary = "PHOTO_LIBRARY"
    case file = "FILE"
    case pdf = "PDF"
}

public enum AnalyticsAssetKind: String, Sendable, CaseIterable {
    case image = "IMAGE"
    case pdf = "PDF"
}

public enum AnalyticsQualityWarningCode: String, Sendable, CaseIterable {
    case blur = "BLUR"
    case glare = "GLARE"
    case framing = "FRAMING"
    case multiple = "MULTIPLE"
}

public enum AnalyticsVerificationStatus: String, Sendable, CaseIterable {
    case verified = "VERIFIED"
    case partiallyVerified = "PARTIALLY_VERIFIED"
    case unverified = "UNVERIFIED"
    case unavailable = "UNAVAILABLE"
}

public enum AnalyticsEntitlementTier: String, Sendable, CaseIterable {
    case free = "FREE"
    case pro = "PRO"
    case coachPro = "COACH_PRO"
    case yksSeasonPass = "YKS_SEASON_PASS"
    case unknown = "UNKNOWN"
}

public enum AnalyticsHintLevel: String, Sendable, CaseIterable {
    case h1 = "H1"
    case h2 = "H2"
    case h3 = "H3"
}

public enum AnalyticsPaywallPlacement: String, Sendable, CaseIterable {
    case settings = "SETTINGS"
    case contextual = "CONTEXTUAL"
    case mistakeMoment = "MISTAKE_MOMENT"
    case twinSuccess = "TWIN_SUCCESS"
    case quotaGate = "QUOTA_GATE"
}

public enum AnalyticsNotificationCategory: String, Sendable, CaseIterable {
    case studyPlan = "STUDY_PLAN"
    case spacedReview = "SPACED_REVIEW"
    case examDeadline = "EXAM_DEADLINE"
    case billingTrialReminder = "BILLING_TRIAL_REMINDER"
    case accountSecurity = "ACCOUNT_SECURITY"
}

public enum AnalyticsPropertyValue: Sendable, Equatable, Codable {
    case string(String)
    case int(Int)
    case double(Double)
    case bool(Bool)

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let boolVal = try? container.decode(Bool.self) {
            self = .bool(boolVal)
        } else if let intVal = try? container.decode(Int.self) {
            self = .int(intVal)
        } else if let doubleVal = try? container.decode(Double.self) {
            self = .double(doubleVal)
        } else {
            self = .string(try container.decode(String.self))
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let value):
            try container.encode(value)
        case .int(let value):
            try container.encode(value)
        case .double(let value):
            try container.encode(value)
        case .bool(let value):
            try container.encode(value)
        }
    }
}

/// Compile-time closed catalog of the 26 client-emittable analytics events.
/// Raw problem text, images, LaTeX, tokens, and PII are structurally unrepresentable.
public enum AnalyticsClientEvent: Sendable, Equatable {
    case onboardingStarted(authMethod: AnalyticsAuthMethod? = nil, locale: AnalyticsLocale = .current(), educationLevel: String? = nil, dailyBudgetMinutes: Int? = nil)
    case onboardingCompleted(locale: AnalyticsLocale = .current(), educationLevel: String? = nil, dailyBudgetMinutes: Int? = nil)
    case signInCompleted(authMethod: AnalyticsAuthMethod, locale: AnalyticsLocale = .current(), latencyBucket: AnalyticsLatencyBucket? = nil)
    case problemCaptureStarted(locale: AnalyticsLocale = .current())
    case problemCaptureSourceSelected(source: AnalyticsCaptureSource, assetKind: AnalyticsAssetKind, locale: AnalyticsLocale = .current())
    case problemCaptureQualityWarning(source: AnalyticsCaptureSource, warningCode: AnalyticsQualityWarningCode, locale: AnalyticsLocale = .current())
    case problemCaptureAccepted(source: AnalyticsCaptureSource, assetKind: AnalyticsAssetKind, locale: AnalyticsLocale = .current())
    case firstVerifiedSolutionViewed(verificationStatus: AnalyticsVerificationStatus, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case verificationDetailsViewed(verificationStatus: AnalyticsVerificationStatus, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case solutionAlternativeViewed(verificationStatus: AnalyticsVerificationStatus = .unverified, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case tutorSessionStarted(pedagogicalPhase: String? = nil, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case tutorTurnSubmitted(pedagogicalPhase: String? = nil, turnCountBucket: String? = nil, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case hintRequested(hintLevel: AnalyticsHintLevel, pedagogicalPhase: String? = nil, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case answerRevealed(pedagogicalPhase: String? = nil, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case tutorSessionCompleted(pedagogicalPhase: String? = nil, turnCountBucket: String? = nil, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case mistakeReviewed(canonicalSkillCode: String? = nil, mistakeCode: String? = nil, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case practiceSessionStarted(canonicalSkillCode: String? = nil, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case examReadinessViewed(examCode: String, jurisdiction: String = "TR", confidenceTier: String? = nil, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case paywallViewed(placement: AnalyticsPaywallPlacement, tierCode: String? = nil, entitlementTier: AnalyticsEntitlementTier = .free, locale: AnalyticsLocale = .current())
    case purchaseStarted(placement: AnalyticsPaywallPlacement, packageCode: String, tierCode: String? = nil, entitlementTier: AnalyticsEntitlementTier = .free, locale: AnalyticsLocale = .current())
    case purchaseFailed(placement: AnalyticsPaywallPlacement, packageCode: String? = nil, failureReason: String, entitlementTier: AnalyticsEntitlementTier = .free, locale: AnalyticsLocale = .current())
    case restoreCompleted(placement: AnalyticsPaywallPlacement = .settings, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case paywallMistakeMomentShown(placement: AnalyticsPaywallPlacement = .mistakeMoment, tierCode: String? = nil, entitlementTier: AnalyticsEntitlementTier = .free, locale: AnalyticsLocale = .current())
    case paywallTwinSuccessShown(placement: AnalyticsPaywallPlacement = .twinSuccess, tierCode: String? = nil, entitlementTier: AnalyticsEntitlementTier = .free, locale: AnalyticsLocale = .current())
    case lifecycleNotificationOpened(category: AnalyticsNotificationCategory, intentType: String, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())
    case weeklyDigestViewed(weekIso: String, entitlementTier: AnalyticsEntitlementTier = .unknown, locale: AnalyticsLocale = .current())

    public var eventName: String {
        switch self {
        case .onboardingStarted: "onboarding_started"
        case .onboardingCompleted: "onboarding_completed"
        case .signInCompleted: "sign_in_completed"
        case .problemCaptureStarted: "problem_capture_started"
        case .problemCaptureSourceSelected: "problem_capture_source_selected"
        case .problemCaptureQualityWarning: "problem_capture_quality_warning"
        case .problemCaptureAccepted: "problem_capture_accepted"
        case .firstVerifiedSolutionViewed: "first_verified_solution_viewed"
        case .verificationDetailsViewed: "verification_details_viewed"
        case .solutionAlternativeViewed: "solution_alternative_viewed"
        case .tutorSessionStarted: "tutor_session_started"
        case .tutorTurnSubmitted: "tutor_turn_submitted"
        case .hintRequested: "hint_requested"
        case .answerRevealed: "answer_revealed"
        case .tutorSessionCompleted: "tutor_session_completed"
        case .mistakeReviewed: "mistake_reviewed"
        case .practiceSessionStarted: "practice_session_started"
        case .examReadinessViewed: "exam_readiness_viewed"
        case .paywallViewed: "paywall_viewed"
        case .purchaseStarted: "purchase_started"
        case .purchaseFailed: "purchase_failed"
        case .restoreCompleted: "restore_completed"
        case .paywallMistakeMomentShown: "paywall.mistake_moment_shown"
        case .paywallTwinSuccessShown: "paywall.twin_success_shown"
        case .lifecycleNotificationOpened: "lifecycle_notification_opened"
        case .weeklyDigestViewed: "weekly_digest_viewed"
        }
    }

    public var eventDomain: String {
        switch self {
        case .onboardingStarted, .onboardingCompleted, .signInCompleted,
             .problemCaptureStarted, .problemCaptureSourceSelected,
             .problemCaptureQualityWarning, .problemCaptureAccepted,
             .firstVerifiedSolutionViewed:
            "ACQUISITION_ACTIVATION"
        case .verificationDetailsViewed, .solutionAlternativeViewed:
            "SOLVING"
        case .tutorSessionStarted, .tutorTurnSubmitted, .hintRequested,
             .answerRevealed, .tutorSessionCompleted:
            "TUTOR"
        case .mistakeReviewed, .practiceSessionStarted:
            "LEARNING_MASTERY"
        case .examReadinessViewed:
            "EXAM"
        case .paywallViewed, .purchaseStarted, .purchaseFailed, .restoreCompleted,
             .paywallMistakeMomentShown, .paywallTwinSuccessShown:
            "BILLING_MONETIZATION"
        case .lifecycleNotificationOpened, .weeklyDigestViewed:
            "LIFECYCLE_RETENTION"
        }
    }

    public var locale: AnalyticsLocale {
        switch self {
        case .onboardingStarted(_, let locale, _, _),
             .onboardingCompleted(let locale, _, _),
             .signInCompleted(_, let locale, _),
             .problemCaptureStarted(let locale),
             .problemCaptureSourceSelected(_, _, let locale),
             .problemCaptureQualityWarning(_, _, let locale),
             .problemCaptureAccepted(_, _, let locale),
             .firstVerifiedSolutionViewed(_, _, let locale),
             .verificationDetailsViewed(_, _, let locale),
             .solutionAlternativeViewed(_, _, let locale),
             .tutorSessionStarted(_, _, let locale),
             .tutorTurnSubmitted(_, _, _, let locale),
             .hintRequested(_, _, _, let locale),
             .answerRevealed(_, _, let locale),
             .tutorSessionCompleted(_, _, _, let locale),
             .mistakeReviewed(_, _, _, let locale),
             .practiceSessionStarted(_, _, let locale),
             .examReadinessViewed(_, _, _, _, let locale),
             .paywallViewed(_, _, _, let locale),
             .purchaseStarted(_, _, _, _, let locale),
             .purchaseFailed(_, _, _, _, let locale),
             .restoreCompleted(_, _, let locale),
             .paywallMistakeMomentShown(_, _, _, let locale),
             .paywallTwinSuccessShown(_, _, _, let locale),
             .lifecycleNotificationOpened(_, _, _, let locale),
             .weeklyDigestViewed(_, _, let locale):
            locale
        }
    }

    public var entitlementTier: AnalyticsEntitlementTier {
        switch self {
        case .firstVerifiedSolutionViewed(_, let tier, _),
             .verificationDetailsViewed(_, let tier, _),
             .solutionAlternativeViewed(_, let tier, _),
             .tutorSessionStarted(_, let tier, _),
             .tutorTurnSubmitted(_, _, let tier, _),
             .hintRequested(_, _, let tier, _),
             .answerRevealed(_, let tier, _),
             .tutorSessionCompleted(_, _, let tier, _),
             .mistakeReviewed(_, _, let tier, _),
             .practiceSessionStarted(_, let tier, _),
             .examReadinessViewed(_, _, _, let tier, _),
             .paywallViewed(_, _, let tier, _),
             .purchaseStarted(_, _, _, let tier, _),
             .purchaseFailed(_, _, _, let tier, _),
             .restoreCompleted(_, let tier, _),
             .paywallMistakeMomentShown(_, _, let tier, _),
             .paywallTwinSuccessShown(_, _, let tier, _),
             .lifecycleNotificationOpened(_, _, let tier, _),
             .weeklyDigestViewed(_, let tier, _):
            tier
        case .onboardingStarted, .onboardingCompleted, .signInCompleted,
             .problemCaptureStarted, .problemCaptureSourceSelected,
             .problemCaptureQualityWarning, .problemCaptureAccepted:
            .unknown
        }
    }

    public var properties: [String: AnalyticsPropertyValue] {
        var props: [String: AnalyticsPropertyValue] = [:]
        switch self {
        case .onboardingStarted(let authMethod, let locale, let educationLevel, let dailyBudgetMinutes):
            if let authMethod { props["authMethod"] = .string(authMethod.rawValue) }
            props["locale"] = .string(locale.rawValue)
            if let educationLevel { props["educationLevel"] = .string(educationLevel) }
            if let dailyBudgetMinutes { props["dailyBudgetMinutes"] = .int(dailyBudgetMinutes) }
        case .onboardingCompleted(let locale, let educationLevel, let dailyBudgetMinutes):
            props["locale"] = .string(locale.rawValue)
            if let educationLevel { props["educationLevel"] = .string(educationLevel) }
            if let dailyBudgetMinutes { props["dailyBudgetMinutes"] = .int(dailyBudgetMinutes) }
        case .signInCompleted(let authMethod, let locale, let latencyBucket):
            props["authMethod"] = .string(authMethod.rawValue)
            props["locale"] = .string(locale.rawValue)
            if let latencyBucket { props["latencyBucket"] = .string(latencyBucket.rawValue) }
        case .problemCaptureStarted:
            break
        case .problemCaptureSourceSelected(let source, let assetKind, _):
            props["captureSource"] = .string(source.rawValue)
            props["assetKind"] = .string(assetKind.rawValue)
        case .problemCaptureQualityWarning(let source, let warningCode, _):
            props["captureSource"] = .string(source.rawValue)
            props["qualityWarningCode"] = .string(warningCode.rawValue)
        case .problemCaptureAccepted(let source, let assetKind, _):
            props["captureSource"] = .string(source.rawValue)
            props["assetKind"] = .string(assetKind.rawValue)
        case .firstVerifiedSolutionViewed(let status, let tier, _),
             .verificationDetailsViewed(let status, let tier, _),
             .solutionAlternativeViewed(let status, let tier, _):
            props["verificationStatus"] = .string(status.rawValue)
            props["entitlementTier"] = .string(tier.rawValue)
        case .tutorSessionStarted(let phase, let tier, _):
            if let phase { props["pedagogicalPhase"] = .string(phase) }
            props["entitlementTier"] = .string(tier.rawValue)
        case .tutorTurnSubmitted(let phase, let turnCountBucket, let tier, _):
            if let phase { props["pedagogicalPhase"] = .string(phase) }
            if let turnCountBucket { props["turnCountBucket"] = .string(turnCountBucket) }
            props["entitlementTier"] = .string(tier.rawValue)
        case .hintRequested(let hintLevel, let phase, let tier, _):
            props["hintLevel"] = .string(hintLevel.rawValue)
            if let phase { props["pedagogicalPhase"] = .string(phase) }
            props["entitlementTier"] = .string(tier.rawValue)
        case .answerRevealed(let phase, let tier, _):
            if let phase { props["pedagogicalPhase"] = .string(phase) }
            props["entitlementTier"] = .string(tier.rawValue)
        case .tutorSessionCompleted(let phase, let turnCountBucket, let tier, _):
            if let phase { props["pedagogicalPhase"] = .string(phase) }
            if let turnCountBucket { props["turnCountBucket"] = .string(turnCountBucket) }
            props["entitlementTier"] = .string(tier.rawValue)
        case .mistakeReviewed(let skillCode, let mistakeCode, let tier, _):
            if let skillCode { props["canonicalSkillCode"] = .string(skillCode) }
            if let mistakeCode { props["mistakeCode"] = .string(mistakeCode) }
            props["entitlementTier"] = .string(tier.rawValue)
        case .practiceSessionStarted(let skillCode, let tier, _):
            if let skillCode { props["canonicalSkillCode"] = .string(skillCode) }
            props["entitlementTier"] = .string(tier.rawValue)
        case .examReadinessViewed(let examCode, let jurisdiction, let confidenceTier, let tier, _):
            props["examCode"] = .string(examCode)
            props["jurisdiction"] = .string(jurisdiction)
            if let confidenceTier { props["confidenceTier"] = .string(confidenceTier) }
            props["entitlementTier"] = .string(tier.rawValue)
        case .paywallViewed(let placement, let tierCode, let tier, _),
             .paywallMistakeMomentShown(let placement, let tierCode, let tier, _),
             .paywallTwinSuccessShown(let placement, let tierCode, let tier, _):
            props["placement"] = .string(placement.rawValue)
            if let tierCode { props["tierCode"] = .string(tierCode) }
            props["storefront"] = .string("APP_STORE")
            props["entitlementTier"] = .string(tier.rawValue)
        case .purchaseStarted(let placement, let packageCode, let tierCode, let tier, _):
            props["placement"] = .string(placement.rawValue)
            props["packageCode"] = .string(packageCode)
            if let tierCode { props["tierCode"] = .string(tierCode) }
            props["storefront"] = .string("APP_STORE")
            props["entitlementTier"] = .string(tier.rawValue)
        case .purchaseFailed(let placement, let packageCode, let failureReason, let tier, _):
            props["placement"] = .string(placement.rawValue)
            if let packageCode { props["packageCode"] = .string(packageCode) }
            props["failureReason"] = .string(failureReason)
            props["storefront"] = .string("APP_STORE")
            props["entitlementTier"] = .string(tier.rawValue)
        case .restoreCompleted(let placement, let tier, _):
            props["placement"] = .string(placement.rawValue)
            props["storefront"] = .string("APP_STORE")
            props["entitlementTier"] = .string(tier.rawValue)
        case .lifecycleNotificationOpened(let category, let intentType, let tier, let locale):
            props["category"] = .string(category.rawValue)
            props["intentType"] = .string(intentType)
            props["locale"] = .string(locale.rawValue)
            props["entitlementTier"] = .string(tier.rawValue)
        case .weeklyDigestViewed(let weekIso, let tier, let locale):
            props["weekIso"] = .string(weekIso)
            props["locale"] = .string(locale.rawValue)
            props["entitlementTier"] = .string(tier.rawValue)
        }
        return props
    }
}

public struct AnalyticsEventIngestRequestDTO: Codable, Sendable, Equatable {
    public let clientEventId: String
    public let eventName: String
    public let emittedBy: String
    public let sourceModule: String
    public let entitlementTier: String
    public let locale: String
    public let appVersion: String?
    public let sessionRefHash: String?
    public let latencyMs: Int?
    public let properties: [String: AnalyticsPropertyValue]
    public let occurredAt: Date
    public let trainingEligible: Bool

    public init(
        clientEventId: String,
        eventName: String,
        emittedBy: String = "CLIENT",
        sourceModule: String = "ios_client",
        entitlementTier: String,
        locale: String,
        appVersion: String?,
        sessionRefHash: String?,
        latencyMs: Int?,
        properties: [String: AnalyticsPropertyValue],
        occurredAt: Date,
        trainingEligible: Bool = false
    ) {
        self.clientEventId = clientEventId
        self.eventName = eventName
        self.emittedBy = emittedBy
        self.sourceModule = sourceModule
        self.entitlementTier = entitlementTier
        self.locale = locale
        self.appVersion = appVersion
        self.sessionRefHash = sessionRefHash
        self.latencyMs = latencyMs
        self.properties = properties
        self.occurredAt = occurredAt
        self.trainingEligible = trainingEligible
    }
}

public struct AnalyticsEventBatchRequestDTO: Codable, Sendable, Equatable {
    public let events: [AnalyticsEventIngestRequestDTO]
    public let trainingEligible: Bool

    public init(events: [AnalyticsEventIngestRequestDTO], trainingEligible: Bool = false) {
        self.events = events
        self.trainingEligible = trainingEligible
    }
}

public struct AnalyticsEventIngestResultItemDTO: Codable, Sendable, Equatable {
    public let clientEventId: String?
    public let eventName: String?
    public let status: String
    public let eventId: UUID?
    public let errorCode: String?
    public let processedAt: Date
}

public struct AnalyticsEventIngestResponseDTO: Codable, Sendable, Equatable {
    public let schemaVersion: String
    public let acceptedCount: Int
    public let replayedCount: Int
    public let rejectedCount: Int
    public let results: [AnalyticsEventIngestResultItemDTO]
    public let llmTokensUsed: Int
    public let trainingEligible: Bool
    public let processedAt: Date
}

public struct AnalyticsEventCatalogEntryDTO: Codable, Sendable, Equatable {
    public let eventName: String
    public let eventDomain: String
    public let clientAllowed: Bool
    public let serverAllowed: Bool
    public let allowedPropertyKeys: [String]
}

public struct AnalyticsEventCatalogResponseDTO: Codable, Sendable, Equatable {
    public let schemaVersion: String
    public let catalogVersion: String
    public let eventCount: Int
    public let events: [AnalyticsEventCatalogEntryDTO]
    public let llmTokensUsed: Int
    public let trainingEligible: Bool
    public let generatedAt: Date
}
