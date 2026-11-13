import Foundation

public enum GamificationStreakStatus: String, Codable, Sendable, CaseIterable {
    case activeVerifiedToday = "ACTIVE_VERIFIED_TODAY"
    case continuityPreservedToday = "CONTINUITY_PRESERVED_TODAY"
    case freezeProtected = "FREEZE_PROTECTED"
    case atRiskToday = "AT_RISK_TODAY"
    case freshStartReady = "FRESH_START_READY"

    public var supportiveHeadlineKey: String {
        switch self {
        case .activeVerifiedToday:
            return "gamification.streak.status.active_verified_today.headline"
        case .continuityPreservedToday:
            return "gamification.streak.status.continuity_preserved_today.headline"
        case .freezeProtected:
            return "gamification.streak.status.freeze_protected.headline"
        case .atRiskToday:
            return "gamification.streak.status.at_risk_today.headline"
        case .freshStartReady:
            return "gamification.streak.status.fresh_start_ready.headline"
        }
    }

    public var supportiveBodyKey: String {
        switch self {
        case .activeVerifiedToday:
            return "gamification.streak.status.active_verified_today.body"
        case .continuityPreservedToday:
            return "gamification.streak.status.continuity_preserved_today.body"
        case .freezeProtected:
            return "gamification.streak.status.freeze_protected.body"
        case .atRiskToday:
            return "gamification.streak.status.at_risk_today.body"
        case .freshStartReady:
            return "gamification.streak.status.fresh_start_ready.body"
        }
    }
}

public enum GamificationEvidenceType: String, Codable, Sendable {
    case independent = "INDEPENDENT"
    case hintAssisted = "HINT_ASSISTED"
    case solutionRevealedAssisted = "SOLUTION_REVEALED_ASSISTED"
}

public enum DailyStreakLedgerStatus: String, Codable, Sendable {
    case verifiedIndependent = "VERIFIED_INDEPENDENT"
    case assistedContinuity = "ASSISTED_CONTINUITY"
    case freezeApplied = "FREEZE_APPLIED"
}

public enum PedagogicalBadgeCode: String, Codable, Sendable, CaseIterable {
    case firstIndependentTwin = "FIRST_INDEPENDENT_TWIN"
    case threeDayIndependentStreak = "THREE_DAY_INDEPENDENT_STREAK"
    case sevenDayIndependentStreak = "SEVEN_DAY_INDEPENDENT_STREAK"
    case misconceptionOvercome = "MISCONCEPTION_OVERCOME"
    case retentionGuardian = "RETENTION_GUARDIAN"
    case conceptArchitect = "CONCEPT_ARCHITECT"

    public var category: PedagogicalBadgeCategory {
        switch self {
        case .firstIndependentTwin:
            return .independentTransfer
        case .threeDayIndependentStreak, .sevenDayIndependentStreak:
            return .habitConsistency
        case .misconceptionOvercome:
            return .misconceptionRepair
        case .retentionGuardian:
            return .spacedRetention
        case .conceptArchitect:
            return .conceptMastery
        }
    }

    public var titleKey: String {
        switch self {
        case .firstIndependentTwin:
            return "gamification.badge.first_independent_twin.title"
        case .threeDayIndependentStreak:
            return "gamification.badge.three_day_independent_streak.title"
        case .sevenDayIndependentStreak:
            return "gamification.badge.seven_day_independent_streak.title"
        case .misconceptionOvercome:
            return "gamification.badge.misconception_overcome.title"
        case .retentionGuardian:
            return "gamification.badge.retention_guardian.title"
        case .conceptArchitect:
            return "gamification.badge.concept_architect.title"
        }
    }

    public var descriptionKey: String {
        switch self {
        case .firstIndependentTwin:
            return "gamification.badge.first_independent_twin.description"
        case .threeDayIndependentStreak:
            return "gamification.badge.three_day_independent_streak.description"
        case .sevenDayIndependentStreak:
            return "gamification.badge.seven_day_independent_streak.description"
        case .misconceptionOvercome:
            return "gamification.badge.misconception_overcome.description"
        case .retentionGuardian:
            return "gamification.badge.retention_guardian.description"
        case .conceptArchitect:
            return "gamification.badge.concept_architect.description"
        }
    }
}

public enum PedagogicalBadgeCategory: String, Codable, Sendable {
    case independentTransfer = "INDEPENDENT_TRANSFER"
    case habitConsistency = "HABIT_CONSISTENCY"
    case misconceptionRepair = "MISCONCEPTION_REPAIR"
    case spacedRetention = "SPACED_RETENTION"
    case conceptMastery = "CONCEPT_MASTERY"
}

public enum GamificationJSONCoding {
    public static func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }

    public static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }

    static func parseISO8601Date(_ string: String) -> Date? {
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let parsed = fractional.date(from: string) {
            return parsed
        }
        let standard = ISO8601DateFormatter()
        standard.formatOptions = [.withInternetDateTime]
        return standard.date(from: string)
    }

    static func formatISO8601Date(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.string(from: date)
    }

    static func decodeFlexibleDate<K: CodingKey>(
        from container: KeyedDecodingContainer<K>,
        forKey key: K
    ) throws -> Date {
        if let rawString = try? container.decode(String.self, forKey: key),
           let parsed = parseISO8601Date(rawString) {
            return parsed
        }
        return try container.decode(Date.self, forKey: key)
    }

    static func decodeOptionalFlexibleDate<K: CodingKey>(
        from container: KeyedDecodingContainer<K>,
        forKey key: K
    ) throws -> Date? {
        guard container.contains(key) else {
            return nil
        }
        if (try? container.decodeNil(forKey: key)) == true {
            return nil
        }
        if let rawString = try? container.decode(String.self, forKey: key),
           let parsed = parseISO8601Date(rawString) {
            return parsed
        }
        return try container.decodeIfPresent(Date.self, forKey: key)
    }
}

public struct GamificationActivityRecordRequest: Codable, Equatable, Sendable {
    public let idempotencyKey: String
    public let evidenceType: GamificationEvidenceType
    public let evidenceReference: String
    public let learnerTimezone: String
    public let occurredAtUtc: Date?
    public let conceptId: String?
    public let misconceptionRepaired: Bool?
    public let spacedReviewCompleted: Bool?
    public let verifiedMasteredConceptCount: Int?

    public init(
        idempotencyKey: String,
        evidenceType: GamificationEvidenceType,
        evidenceReference: String,
        learnerTimezone: String,
        occurredAtUtc: Date? = nil,
        conceptId: String? = nil,
        misconceptionRepaired: Bool? = nil,
        spacedReviewCompleted: Bool? = nil,
        verifiedMasteredConceptCount: Int? = nil
    ) {
        self.idempotencyKey = idempotencyKey
        self.evidenceType = evidenceType
        self.evidenceReference = evidenceReference
        self.learnerTimezone = learnerTimezone
        self.occurredAtUtc = occurredAtUtc
        self.conceptId = conceptId
        self.misconceptionRepaired = misconceptionRepaired
        self.spacedReviewCompleted = spacedReviewCompleted
        self.verifiedMasteredConceptCount = verifiedMasteredConceptCount
    }

    private enum CodingKeys: String, CodingKey {
        case idempotencyKey
        case evidenceType
        case evidenceReference
        case learnerTimezone
        case occurredAtUtc
        case conceptId
        case misconceptionRepaired
        case spacedReviewCompleted
        case verifiedMasteredConceptCount
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        idempotencyKey = try container.decode(String.self, forKey: .idempotencyKey)
        evidenceType = try container.decode(GamificationEvidenceType.self, forKey: .evidenceType)
        evidenceReference = try container.decode(String.self, forKey: .evidenceReference)
        learnerTimezone = try container.decode(String.self, forKey: .learnerTimezone)
        occurredAtUtc = try GamificationJSONCoding.decodeOptionalFlexibleDate(from: container, forKey: .occurredAtUtc)
        conceptId = try container.decodeIfPresent(String.self, forKey: .conceptId)
        misconceptionRepaired = try container.decodeIfPresent(Bool.self, forKey: .misconceptionRepaired)
        spacedReviewCompleted = try container.decodeIfPresent(Bool.self, forKey: .spacedReviewCompleted)
        verifiedMasteredConceptCount = try container.decodeIfPresent(Int.self, forKey: .verifiedMasteredConceptCount)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(idempotencyKey, forKey: .idempotencyKey)
        try container.encode(evidenceType, forKey: .evidenceType)
        try container.encode(evidenceReference, forKey: .evidenceReference)
        try container.encode(learnerTimezone, forKey: .learnerTimezone)
        if let occurredAtUtc {
            try container.encode(GamificationJSONCoding.formatISO8601Date(occurredAtUtc), forKey: .occurredAtUtc)
        }
        try container.encodeIfPresent(conceptId, forKey: .conceptId)
        try container.encodeIfPresent(misconceptionRepaired, forKey: .misconceptionRepaired)
        try container.encodeIfPresent(spacedReviewCompleted, forKey: .spacedReviewCompleted)
        try container.encodeIfPresent(verifiedMasteredConceptCount, forKey: .verifiedMasteredConceptCount)
    }
}

public struct DailyStreakLedgerEntry: Codable, Equatable, Sendable, Identifiable {
    public var id: String { activityLocalDate }
    public let activityLocalDate: String
    public let dailyStatus: DailyStreakLedgerStatus
    public let highestEvidenceType: String
    public let independentActivityCount: Int
    public let assistedActivityCount: Int
    public let streakIncremented: Bool
    public let freezeConsumed: Bool
    public let streakDaysSnapshot: Int
    public let evidenceReference: String

    public init(
        activityLocalDate: String,
        dailyStatus: DailyStreakLedgerStatus,
        highestEvidenceType: String,
        independentActivityCount: Int,
        assistedActivityCount: Int,
        streakIncremented: Bool,
        freezeConsumed: Bool,
        streakDaysSnapshot: Int,
        evidenceReference: String
    ) {
        self.activityLocalDate = activityLocalDate
        self.dailyStatus = dailyStatus
        self.highestEvidenceType = highestEvidenceType
        self.independentActivityCount = independentActivityCount
        self.assistedActivityCount = assistedActivityCount
        self.streakIncremented = streakIncremented
        self.freezeConsumed = freezeConsumed
        self.streakDaysSnapshot = streakDaysSnapshot
        self.evidenceReference = evidenceReference
    }
}

public struct PedagogicalAchievementItem: Codable, Equatable, Sendable, Identifiable {
    public var id: String { badgeCode.rawValue }
    public let badgeCode: PedagogicalBadgeCode
    public let badgeCategory: PedagogicalBadgeCategory
    public let titleKey: String
    public let descriptionKey: String
    public let unlocked: Bool
    public let achievementId: UUID?
    public let evidenceReference: String?
    public let unlockedLocalDate: String?
    public let unlockedAtUtc: Date?

    public init(
        badgeCode: PedagogicalBadgeCode,
        badgeCategory: PedagogicalBadgeCategory,
        titleKey: String,
        descriptionKey: String,
        unlocked: Bool,
        achievementId: UUID? = nil,
        evidenceReference: String? = nil,
        unlockedLocalDate: String? = nil,
        unlockedAtUtc: Date? = nil
    ) {
        self.badgeCode = badgeCode
        self.badgeCategory = badgeCategory
        self.titleKey = titleKey
        self.descriptionKey = descriptionKey
        self.unlocked = unlocked
        self.achievementId = achievementId
        self.evidenceReference = evidenceReference
        self.unlockedLocalDate = unlockedLocalDate
        self.unlockedAtUtc = unlockedAtUtc
    }

    private enum CodingKeys: String, CodingKey {
        case badgeCode
        case badgeCategory
        case titleKey
        case descriptionKey
        case unlocked
        case achievementId
        case evidenceReference
        case unlockedLocalDate
        case unlockedAtUtc
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        badgeCode = try container.decode(PedagogicalBadgeCode.self, forKey: .badgeCode)
        badgeCategory = try container.decode(PedagogicalBadgeCategory.self, forKey: .badgeCategory)
        titleKey = try container.decode(String.self, forKey: .titleKey)
        descriptionKey = try container.decode(String.self, forKey: .descriptionKey)
        unlocked = try container.decode(Bool.self, forKey: .unlocked)
        achievementId = try container.decodeIfPresent(UUID.self, forKey: .achievementId)
        evidenceReference = try container.decodeIfPresent(String.self, forKey: .evidenceReference)
        unlockedLocalDate = try container.decodeIfPresent(String.self, forKey: .unlockedLocalDate)
        unlockedAtUtc = try GamificationJSONCoding.decodeOptionalFlexibleDate(from: container, forKey: .unlockedAtUtc)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(badgeCode, forKey: .badgeCode)
        try container.encode(badgeCategory, forKey: .badgeCategory)
        try container.encode(titleKey, forKey: .titleKey)
        try container.encode(descriptionKey, forKey: .descriptionKey)
        try container.encode(unlocked, forKey: .unlocked)
        try container.encodeIfPresent(achievementId, forKey: .achievementId)
        try container.encodeIfPresent(evidenceReference, forKey: .evidenceReference)
        try container.encodeIfPresent(unlockedLocalDate, forKey: .unlockedLocalDate)
        if let unlockedAtUtc {
            try container.encode(GamificationJSONCoding.formatISO8601Date(unlockedAtUtc), forKey: .unlockedAtUtc)
        }
    }
}

public struct GamificationSummaryResponse: Codable, Equatable, Sendable {
    public let userId: UUID
    public let learnerTimezone: String
    public let serverLocalDate: String
    public let currentStreakDays: Int
    public let longestStreakDays: Int
    public let streakFreezesRemaining: Int
    public let maxMonthlyStreakFreezes: Int
    public let freezesUsedThisMonth: Int
    public let freezeQuotaMonth: String
    public let streakStatus: GamificationStreakStatus
    public let verifiedToday: Bool
    public let continuityPreservedToday: Bool
    public let freezeAppliedOnEvaluation: Bool
    public let streakIncrementedOnEvaluation: Bool
    public let lastVerifiedIndependentDate: String?
    public let lastActivityLocalDate: String?
    public let lastActivityEvidenceType: GamificationEvidenceType?
    public let totalIndependentDays: Int
    public let totalAssistedContinuityDays: Int
    public let supportiveHeadlineKey: String
    public let supportiveBodyKey: String
    public let recentDailyLedger: [DailyStreakLedgerEntry]
    public let achievements: [PedagogicalAchievementItem]
    public let newlyUnlockedBadgeCodes: [PedagogicalBadgeCode]
    public let trainingEligible: Bool
    public let evaluatedAtUtc: Date

    public init(
        userId: UUID,
        learnerTimezone: String,
        serverLocalDate: String,
        currentStreakDays: Int,
        longestStreakDays: Int,
        streakFreezesRemaining: Int,
        maxMonthlyStreakFreezes: Int = 2,
        freezesUsedThisMonth: Int,
        freezeQuotaMonth: String,
        streakStatus: GamificationStreakStatus,
        verifiedToday: Bool,
        continuityPreservedToday: Bool,
        freezeAppliedOnEvaluation: Bool,
        streakIncrementedOnEvaluation: Bool,
        lastVerifiedIndependentDate: String? = nil,
        lastActivityLocalDate: String? = nil,
        lastActivityEvidenceType: GamificationEvidenceType? = nil,
        totalIndependentDays: Int,
        totalAssistedContinuityDays: Int,
        supportiveHeadlineKey: String,
        supportiveBodyKey: String,
        recentDailyLedger: [DailyStreakLedgerEntry],
        achievements: [PedagogicalAchievementItem],
        newlyUnlockedBadgeCodes: [PedagogicalBadgeCode],
        trainingEligible: Bool = false,
        evaluatedAtUtc: Date = Date()
    ) {
        self.userId = userId
        self.learnerTimezone = learnerTimezone
        self.serverLocalDate = serverLocalDate
        self.currentStreakDays = currentStreakDays
        self.longestStreakDays = longestStreakDays
        self.streakFreezesRemaining = streakFreezesRemaining
        self.maxMonthlyStreakFreezes = maxMonthlyStreakFreezes
        self.freezesUsedThisMonth = freezesUsedThisMonth
        self.freezeQuotaMonth = freezeQuotaMonth
        self.streakStatus = streakStatus
        self.verifiedToday = verifiedToday
        self.continuityPreservedToday = continuityPreservedToday
        self.freezeAppliedOnEvaluation = freezeAppliedOnEvaluation
        self.streakIncrementedOnEvaluation = streakIncrementedOnEvaluation
        self.lastVerifiedIndependentDate = lastVerifiedIndependentDate
        self.lastActivityLocalDate = lastActivityLocalDate
        self.lastActivityEvidenceType = lastActivityEvidenceType
        self.totalIndependentDays = totalIndependentDays
        self.totalAssistedContinuityDays = totalAssistedContinuityDays
        self.supportiveHeadlineKey = supportiveHeadlineKey
        self.supportiveBodyKey = supportiveBodyKey
        self.recentDailyLedger = recentDailyLedger
        self.achievements = achievements
        self.newlyUnlockedBadgeCodes = newlyUnlockedBadgeCodes
        self.trainingEligible = trainingEligible
        self.evaluatedAtUtc = evaluatedAtUtc
    }

    private enum CodingKeys: String, CodingKey {
        case userId
        case learnerTimezone
        case serverLocalDate
        case currentStreakDays
        case longestStreakDays
        case streakFreezesRemaining
        case maxMonthlyStreakFreezes
        case freezesUsedThisMonth
        case freezeQuotaMonth
        case streakStatus
        case verifiedToday
        case continuityPreservedToday
        case freezeAppliedOnEvaluation
        case streakIncrementedOnEvaluation
        case lastVerifiedIndependentDate
        case lastActivityLocalDate
        case lastActivityEvidenceType
        case totalIndependentDays
        case totalAssistedContinuityDays
        case supportiveHeadlineKey
        case supportiveBodyKey
        case recentDailyLedger
        case achievements
        case newlyUnlockedBadgeCodes
        case trainingEligible
        case evaluatedAtUtc
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        userId = try container.decode(UUID.self, forKey: .userId)
        learnerTimezone = try container.decode(String.self, forKey: .learnerTimezone)
        serverLocalDate = try container.decode(String.self, forKey: .serverLocalDate)
        currentStreakDays = try container.decode(Int.self, forKey: .currentStreakDays)
        longestStreakDays = try container.decode(Int.self, forKey: .longestStreakDays)
        streakFreezesRemaining = try container.decode(Int.self, forKey: .streakFreezesRemaining)
        maxMonthlyStreakFreezes = try container.decode(Int.self, forKey: .maxMonthlyStreakFreezes)
        freezesUsedThisMonth = try container.decode(Int.self, forKey: .freezesUsedThisMonth)
        freezeQuotaMonth = try container.decode(String.self, forKey: .freezeQuotaMonth)
        streakStatus = try container.decode(GamificationStreakStatus.self, forKey: .streakStatus)
        verifiedToday = try container.decode(Bool.self, forKey: .verifiedToday)
        continuityPreservedToday = try container.decode(Bool.self, forKey: .continuityPreservedToday)
        freezeAppliedOnEvaluation = try container.decode(Bool.self, forKey: .freezeAppliedOnEvaluation)
        streakIncrementedOnEvaluation = try container.decode(Bool.self, forKey: .streakIncrementedOnEvaluation)
        lastVerifiedIndependentDate = try container.decodeIfPresent(String.self, forKey: .lastVerifiedIndependentDate)
        lastActivityLocalDate = try container.decodeIfPresent(String.self, forKey: .lastActivityLocalDate)
        lastActivityEvidenceType = try container.decodeIfPresent(GamificationEvidenceType.self, forKey: .lastActivityEvidenceType)
        totalIndependentDays = try container.decode(Int.self, forKey: .totalIndependentDays)
        totalAssistedContinuityDays = try container.decode(Int.self, forKey: .totalAssistedContinuityDays)
        supportiveHeadlineKey = try container.decode(String.self, forKey: .supportiveHeadlineKey)
        supportiveBodyKey = try container.decode(String.self, forKey: .supportiveBodyKey)
        recentDailyLedger = try container.decode([DailyStreakLedgerEntry].self, forKey: .recentDailyLedger)
        achievements = try container.decode([PedagogicalAchievementItem].self, forKey: .achievements)
        newlyUnlockedBadgeCodes = try container.decode([PedagogicalBadgeCode].self, forKey: .newlyUnlockedBadgeCodes)
        trainingEligible = try container.decode(Bool.self, forKey: .trainingEligible)
        evaluatedAtUtc = try GamificationJSONCoding.decodeFlexibleDate(from: container, forKey: .evaluatedAtUtc)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(userId, forKey: .userId)
        try container.encode(learnerTimezone, forKey: .learnerTimezone)
        try container.encode(serverLocalDate, forKey: .serverLocalDate)
        try container.encode(currentStreakDays, forKey: .currentStreakDays)
        try container.encode(longestStreakDays, forKey: .longestStreakDays)
        try container.encode(streakFreezesRemaining, forKey: .streakFreezesRemaining)
        try container.encode(maxMonthlyStreakFreezes, forKey: .maxMonthlyStreakFreezes)
        try container.encode(freezesUsedThisMonth, forKey: .freezesUsedThisMonth)
        try container.encode(freezeQuotaMonth, forKey: .freezeQuotaMonth)
        try container.encode(streakStatus, forKey: .streakStatus)
        try container.encode(verifiedToday, forKey: .verifiedToday)
        try container.encode(continuityPreservedToday, forKey: .continuityPreservedToday)
        try container.encode(freezeAppliedOnEvaluation, forKey: .freezeAppliedOnEvaluation)
        try container.encode(streakIncrementedOnEvaluation, forKey: .streakIncrementedOnEvaluation)
        try container.encodeIfPresent(lastVerifiedIndependentDate, forKey: .lastVerifiedIndependentDate)
        try container.encodeIfPresent(lastActivityLocalDate, forKey: .lastActivityLocalDate)
        try container.encodeIfPresent(lastActivityEvidenceType, forKey: .lastActivityEvidenceType)
        try container.encode(totalIndependentDays, forKey: .totalIndependentDays)
        try container.encode(totalAssistedContinuityDays, forKey: .totalAssistedContinuityDays)
        try container.encode(supportiveHeadlineKey, forKey: .supportiveHeadlineKey)
        try container.encode(supportiveBodyKey, forKey: .supportiveBodyKey)
        try container.encode(recentDailyLedger, forKey: .recentDailyLedger)
        try container.encode(achievements, forKey: .achievements)
        try container.encode(newlyUnlockedBadgeCodes, forKey: .newlyUnlockedBadgeCodes)
        try container.encode(trainingEligible, forKey: .trainingEligible)
        try container.encode(GamificationJSONCoding.formatISO8601Date(evaluatedAtUtc), forKey: .evaluatedAtUtc)
    }

    public static func defaultCatalogue(streakDays: Int = 0) -> GamificationSummaryResponse {
        let now = Date()
        let dateFormatter = DateFormatter()
        dateFormatter.calendar = Calendar(identifier: .gregorian)
        dateFormatter.locale = Locale(identifier: "en_US_POSIX")
        dateFormatter.dateFormat = "yyyy-MM-dd"
        let todayString = dateFormatter.string(from: now)
        let monthString = String(todayString.prefix(7))
        let status: GamificationStreakStatus = streakDays > 0 ? .activeVerifiedToday : .freshStartReady

        return GamificationSummaryResponse(
            userId: UUID(),
            learnerTimezone: TimeZone.current.identifier,
            serverLocalDate: todayString,
            currentStreakDays: streakDays,
            longestStreakDays: streakDays,
            streakFreezesRemaining: 2,
            maxMonthlyStreakFreezes: 2,
            freezesUsedThisMonth: 0,
            freezeQuotaMonth: monthString,
            streakStatus: status,
            verifiedToday: streakDays > 0,
            continuityPreservedToday: streakDays > 0,
            freezeAppliedOnEvaluation: false,
            streakIncrementedOnEvaluation: false,
            lastVerifiedIndependentDate: streakDays > 0 ? todayString : nil,
            lastActivityLocalDate: streakDays > 0 ? todayString : nil,
            lastActivityEvidenceType: streakDays > 0 ? .independent : nil,
            totalIndependentDays: streakDays,
            totalAssistedContinuityDays: 0,
            supportiveHeadlineKey: status.supportiveHeadlineKey,
            supportiveBodyKey: status.supportiveBodyKey,
            recentDailyLedger: [],
            achievements: PedagogicalBadgeCode.allCases.map { code in
                PedagogicalAchievementItem(
                    badgeCode: code,
                    badgeCategory: code.category,
                    titleKey: code.titleKey,
                    descriptionKey: code.descriptionKey,
                    unlocked: false
                )
            },
            newlyUnlockedBadgeCodes: [],
            trainingEligible: false,
            evaluatedAtUtc: now
        )
    }
}
