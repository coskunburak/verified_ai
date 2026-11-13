import Foundation

enum WeeklyDigestStatus: String, Codable, Equatable, Sendable {
    case generated = "GENERATED"
    case emptyWeek = "EMPTY_WEEK"
    case degraded = "DEGRADED"
}

enum RecommendationReasonCode: String, Codable, Equatable, Sendable {
    case lowMasteryFoundation = "LOW_MASTERY_FOUNDATION"
    case recurringMistakePattern = "RECURRING_MISTAKE_PATTERN"
    case retentionDecayRisk = "RETENTION_DECAY_RISK"
    case prerequisiteBlocker = "PREREQUISITE_BLOCKER"
    case coldStartOnboarding = "COLD_START_ONBOARDING"
}

enum ShareRecipientRole: String, Codable, Equatable, CaseIterable, Identifiable, Sendable {
    case tutor = "TUTOR"
    case parent = "PARENT"

    var id: String { rawValue }
}

struct WeeklyDigestV1: Codable, Equatable, Sendable {
    let schemaVersion: String
    let digestId: String
    let userId: String
    let weekIso: String
    let weekStartDate: String
    let weekEndDate: String
    let locale: String
    let status: WeeklyDigestStatus
    let trainingEligible: Bool
    let generatedAt: String
    let executiveSummary: ExecutiveSummary
    let wins: [WeeklyWinItem]
    let recurringMistakes: [WeeklyRecurringMistakeItem]
    let masteryMomentum: MasteryMomentum
    let recommendations: [ExplainableRecommendationItem]

    enum CodingKeys: String, CodingKey {
        case schemaVersion
        case digestId
        case userId
        case weekIso
        case weekStartDate
        case weekEndDate
        case locale
        case status
        case trainingEligible = "training_eligible"
        case generatedAt
        case executiveSummary
        case wins
        case recurringMistakes
        case masteryMomentum
        case recommendations
    }
}

struct ExecutiveSummary: Codable, Equatable, Sendable {
    let headline: String
    let encouragementNote: String
    let solvedAttemptsCount: Int
    let independentTwinsSolvedCount: Int
    let studyMinutes: Int
    let activeDaysCount: Int
    let independentShareRate: Double
    let completedPlanItemsCount: Int
    let currentStreakDays: Int
}

struct WeeklyWinItem: Codable, Equatable, Identifiable, Sendable {
    let winId: String
    let category: String
    let title: String
    let detail: String
    let skillId: String?
    let metricDelta: String?
    let evidenceRef: String

    var id: String { winId }
}

struct WeeklyRecurringMistakeItem: Codable, Equatable, Identifiable, Sendable {
    let mistakeCode: String
    let label: String
    let skillId: String
    let skillTitle: String
    let occurrenceCountThisWeek: Int
    let previousWeekOccurrenceCount: Int
    let trendDirection: String
    let microLessonCompleted: Bool
    let supportiveGuidance: String
    let evidenceRefs: [String]

    var id: String { mistakeCode }
}

struct DailyActivityBucket: Codable, Equatable, Identifiable, Sendable {
    let date: String
    let dayLabel: String
    let studyMinutes: Int
    let solvedCount: Int
    let independentCount: Int
    let active: Bool

    var id: String { date }
}

struct SkillMomentumItem: Codable, Equatable, Identifiable, Sendable {
    let skillId: String
    let skillTitle: String
    let masteryScore: Double
    let retentionScore: Double
    let masteryDelta: Double
    let independentEvidenceCount: Int

    var id: String { skillId }
}

struct MasteryMomentum: Codable, Equatable, Sendable {
    let overallMasteryAvg: Double
    let overallRetentionAvg: Double
    let skillsImprovedCount: Int
    let skillsPracticedCount: Int
    let dueReviewsCount: Int
    let dailyActivityBuckets: [DailyActivityBucket]
    let topSkillDeltas: [SkillMomentumItem]
}

struct RecommendationFactorBreakdown: Codable, Equatable, Sendable {
    let masteryGapWeight: Double
    let mistakeWeight: Double
    let retentionWeight: Double
    let prerequisiteWeight: Double
    let observedMasteryScore: Double
    let observedMistakeCount: Int
    let observedRetentionScore: Double
    let dependentBlockedSkillsCount: Int
}

struct RecommendationExplanation: Codable, Equatable, Sendable {
    let summaryText: String
    let factorBreakdown: RecommendationFactorBreakdown
}

struct ExplainableRecommendationItem: Codable, Equatable, Identifiable, Sendable {
    let recommendationId: String
    let targetSkillId: String
    let targetSkillTitle: String
    let reasonCode: RecommendationReasonCode
    let priorityScore: Double
    let estimatedMinutes: Int
    let recommendedActionType: String
    let headline: String
    let explanation: RecommendationExplanation
    let evidenceRefs: [String]

    var id: String { recommendationId }
}

struct WeeklySharePacketRequest: Codable, Equatable, Sendable {
    let recipientRole: ShareRecipientRole
    let displayAlias: String?
    let locale: String?
}

struct WeeklySharePacketV1: Codable, Equatable, Sendable {
    let schemaVersion: String
    let packetId: String
    let digestId: String
    let weekIso: String
    let weekStartDate: String
    let weekEndDate: String
    let locale: String
    let recipientRole: ShareRecipientRole
    let displayAlias: String
    let trainingEligible: Bool
    let generatedAt: String
    let executiveMetrics: ShareExecutiveMetrics
    let masterySnapshot: [ShareMasterySnapshotItem]
    let focusThemes: [ShareFocusThemeItem]
    let recommendedNextActions: [ShareRecommendedActionItem]
    let privacyAttestation: PrivacyAttestation

    enum CodingKeys: String, CodingKey {
        case schemaVersion
        case packetId
        case digestId
        case weekIso
        case weekStartDate
        case weekEndDate
        case locale
        case recipientRole
        case displayAlias
        case trainingEligible = "training_eligible"
        case generatedAt
        case executiveMetrics
        case masterySnapshot
        case focusThemes
        case recommendedNextActions
        case privacyAttestation
    }
}

struct ShareExecutiveMetrics: Codable, Equatable, Sendable {
    let solvedAttemptsCount: Int
    let independentTwinsSolvedCount: Int
    let studyMinutes: Int
    let activeDaysCount: Int
    let independentShareRate: Double
}

struct ShareMasterySnapshotItem: Codable, Equatable, Identifiable, Sendable {
    let skillId: String
    let skillTitle: String
    let masteryScore: Double
    let masteryDelta: Double

    var id: String { skillId }
}

struct ShareFocusThemeItem: Codable, Equatable, Identifiable, Sendable {
    let mistakeCode: String
    let label: String
    let skillTitle: String
    let trendDirection: String
    let guidance: String

    var id: String { mistakeCode }
}

struct ShareRecommendedActionItem: Codable, Equatable, Identifiable, Sendable {
    let recommendationId: String
    let targetSkillTitle: String
    let reasonCode: RecommendationReasonCode
    let priorityScore: Double
    let estimatedMinutes: Int
    let headline: String
    let explanationSummary: String
    let evidenceRefs: [String]

    var id: String { recommendationId }
}

struct PrivacyAttestation: Codable, Equatable, Sendable {
    let policyId: String
    let piiExcluded: Bool
    let rawTutorTranscriptsIncluded: Bool
    let rawStudentPhotosIncluded: Bool
    let exactSessionTimestampsIncluded: Bool
    let trainingEligible: Bool
    let shareableScopeSummary: String
}

enum ISOWeekCalendarHelper {
    static func currentWeekIso(for date: Date = Date(), timeZone: TimeZone = .current) -> String {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = timeZone
        let year = calendar.component(.yearForWeekOfYear, from: date)
        let week = calendar.component(.weekOfYear, from: date)
        return String(format: "%04d-W%02d", year, week)
    }

    static func mondayDate(fromWeekIso weekIso: String, timeZone: TimeZone = .current) -> Date? {
        let parts = weekIso.split(separator: "W")
        guard parts.count == 2,
              parts[0].hasSuffix("-"),
              let year = Int(parts[0].dropLast()),
              let week = Int(parts[1]) else {
            return nil
        }
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = timeZone
        var comps = DateComponents()
        comps.yearForWeekOfYear = year
        comps.weekOfYear = week
        comps.weekday = 2 // Monday in ISO-8601 calendar
        guard let candidate = calendar.date(from: comps) else {
            return nil
        }
        let roundTripYear = calendar.component(.yearForWeekOfYear, from: candidate)
        let roundTripWeek = calendar.component(.weekOfYear, from: candidate)
        guard roundTripYear == year, roundTripWeek == week else {
            return nil
        }
        return candidate
    }

    static func shiftWeekIso(_ weekIso: String, byWeeks delta: Int, timeZone: TimeZone = .current) -> String? {
        guard let monday = mondayDate(fromWeekIso: weekIso, timeZone: timeZone) else {
            return nil
        }
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = timeZone
        guard let shifted = calendar.date(byAdding: .day, value: delta * 7, to: monday) else {
            return nil
        }
        return currentWeekIso(for: shifted, timeZone: timeZone)
    }
}

enum WeeklyReportFixtures {
    static func sampleDigest(weekIso: String = "2026-W46", locale: String = "en") -> WeeklyDigestV1 {
        WeeklyDigestV1(
            schemaVersion: "weekly-digest-v1",
            digestId: "00000000-0000-4000-8000-000000000891",
            userId: "00000000-0000-4000-8000-000000000890",
            weekIso: weekIso,
            weekStartDate: "2026-11-09",
            weekEndDate: "2026-11-15",
            locale: locale,
            status: .generated,
            trainingEligible: false,
            generatedAt: "2026-11-13T10:30:00Z",
            executiveSummary: ExecutiveSummary(
                headline: "Steady independent momentum across linear and quadratic foundations",
                encouragementNote: "Practiced across 4 days this week and verified 3 twin checks independently.",
                solvedAttemptsCount: 9,
                independentTwinsSolvedCount: 3,
                studyMinutes: 74,
                activeDaysCount: 4,
                independentShareRate: 0.67,
                completedPlanItemsCount: 5,
                currentStreakDays: 4
            ),
            wins: [
                WeeklyWinItem(
                    winId: "win-twin-01",
                    category: "INDEPENDENT_TRANSFER",
                    title: "Independent Twin Transfer",
                    detail: "Solved 3 counterfactual twin checks independently.",
                    skillId: "MATH.EQUATIONS.LINEAR_ONE_VAR",
                    metricDelta: "+3 verified",
                    evidenceRef: "twin_attempt:00000000-0000-4000-8000-000000000892"
                )
            ],
            recurringMistakes: [
                WeeklyRecurringMistakeItem(
                    mistakeCode: "SIGN_DISTRIBUTION_FLIP",
                    label: "Distributing a negative sign across parentheses",
                    skillId: "MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY",
                    skillTitle: "Distributive Property & Expanding Expressions",
                    occurrenceCountThisWeek: 2,
                    previousWeekOccurrenceCount: 4,
                    trendDirection: "IMPROVING",
                    microLessonCompleted: true,
                    supportiveGuidance: "Rewriting -(a - b) explicitly is cutting slips in half.",
                    evidenceRefs: ["mistake_dna:SIGN_DISTRIBUTION_FLIP"]
                )
            ],
            masteryMomentum: MasteryMomentum(
                overallMasteryAvg: 0.68,
                overallRetentionAvg: 0.74,
                skillsImprovedCount: 3,
                skillsPracticedCount: 4,
                dueReviewsCount: 1,
                dailyActivityBuckets: [
                    DailyActivityBucket(date: "2026-11-09", dayLabel: "Mon", studyMinutes: 18, solvedCount: 2, independentCount: 1, active: true),
                    DailyActivityBucket(date: "2026-11-10", dayLabel: "Tue", studyMinutes: 22, solvedCount: 3, independentCount: 1, active: true),
                    DailyActivityBucket(date: "2026-11-11", dayLabel: "Wed", studyMinutes: 0, solvedCount: 0, independentCount: 0, active: false),
                    DailyActivityBucket(date: "2026-11-12", dayLabel: "Thu", studyMinutes: 19, solvedCount: 2, independentCount: 1, active: true),
                    DailyActivityBucket(date: "2026-11-13", dayLabel: "Fri", studyMinutes: 15, solvedCount: 2, independentCount: 0, active: true),
                    DailyActivityBucket(date: "2026-11-14", dayLabel: "Sat", studyMinutes: 0, solvedCount: 0, independentCount: 0, active: false),
                    DailyActivityBucket(date: "2026-11-15", dayLabel: "Sun", studyMinutes: 0, solvedCount: 0, independentCount: 0, active: false)
                ],
                topSkillDeltas: [
                    SkillMomentumItem(
                        skillId: "MATH.EQUATIONS.LINEAR_ONE_VAR",
                        skillTitle: "Single-Variable Linear Equations",
                        masteryScore: 0.78,
                        retentionScore: 0.81,
                        masteryDelta: 0.14,
                        independentEvidenceCount: 2
                    )
                ]
            ),
            recommendations: [
                ExplainableRecommendationItem(
                    recommendationId: "rec-01-distributive",
                    targetSkillId: "MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY",
                    targetSkillTitle: "Distributive Property & Expanding Expressions",
                    reasonCode: .recurringMistakePattern,
                    priorityScore: 0.6285,
                    estimatedMinutes: 12,
                    recommendedActionType: "TWIN_DRILL",
                    headline: "Practice one short sign-check twin set",
                    explanation: RecommendationExplanation(
                        summaryText: "Suggested because negative-sign distribution appeared twice this week and unlocks multi-step equations.",
                        factorBreakdown: RecommendationFactorBreakdown(
                            masteryGapWeight: 0.4,
                            mistakeWeight: 0.25,
                            retentionWeight: 0.2,
                            prerequisiteWeight: 0.15,
                            observedMasteryScore: 0.56,
                            observedMistakeCount: 2,
                            observedRetentionScore: 0.63,
                            dependentBlockedSkillsCount: 2
                        )
                    ),
                    evidenceRefs: [
                        "mastery:MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY",
                        "mistake_dna:SIGN_DISTRIBUTION_FLIP",
                        "prereq:MATH.EQUATIONS.LINEAR_ONE_VAR"
                    ]
                )
            ]
        )
    }

    static func sampleSharePacket(
        weekIso: String = "2026-W46",
        role: ShareRecipientRole = .tutor,
        alias: String = "Learner A.",
        locale: String = "en"
    ) -> WeeklySharePacketV1 {
        WeeklySharePacketV1(
            schemaVersion: "weekly-share-packet-v1",
            packetId: "00000000-0000-4000-8000-000000000893",
            digestId: "00000000-0000-4000-8000-000000000891",
            weekIso: weekIso,
            weekStartDate: "2026-11-09",
            weekEndDate: "2026-11-15",
            locale: locale,
            recipientRole: role,
            displayAlias: alias,
            trainingEligible: false,
            generatedAt: "2026-11-13T10:35:00Z",
            executiveMetrics: ShareExecutiveMetrics(
                solvedAttemptsCount: 9,
                independentTwinsSolvedCount: 3,
                studyMinutes: 74,
                activeDaysCount: 4,
                independentShareRate: 0.67
            ),
            masterySnapshot: [
                ShareMasterySnapshotItem(
                    skillId: "MATH.EQUATIONS.LINEAR_ONE_VAR",
                    skillTitle: "Single-Variable Linear Equations",
                    masteryScore: 0.78,
                    masteryDelta: 0.14
                ),
                ShareMasterySnapshotItem(
                    skillId: "MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY",
                    skillTitle: "Distributive Property & Expanding Expressions",
                    masteryScore: 0.56,
                    masteryDelta: 0.08
                )
            ],
            focusThemes: [
                ShareFocusThemeItem(
                    mistakeCode: "SIGN_DISTRIBUTION_FLIP",
                    label: "Distributing a negative sign across parentheses",
                    skillTitle: "Distributive Property & Expanding Expressions",
                    trendDirection: "IMPROVING",
                    guidance: "Encourage rewriting -(a - b) explicitly before combining terms."
                )
            ],
            recommendedNextActions: [
                ShareRecommendedActionItem(
                    recommendationId: "rec-01-distributive",
                    targetSkillTitle: "Distributive Property & Expanding Expressions",
                    reasonCode: .recurringMistakePattern,
                    priorityScore: 0.6285,
                    estimatedMinutes: 12,
                    headline: "Practice one short sign-check twin set",
                    explanationSummary: "Negative-sign distribution appeared twice this week and unlocks multi-step equations.",
                    evidenceRefs: [
                        "mastery:MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY",
                        "mistake_dna:SIGN_DISTRIBUTION_FLIP"
                    ]
                )
            ],
            privacyAttestation: PrivacyAttestation(
                policyId: "G-PRIV-03",
                piiExcluded: true,
                rawTutorTranscriptsIncluded: false,
                rawStudentPhotosIncluded: false,
                exactSessionTimestampsIncluded: false,
                trainingEligible: false,
                shareableScopeSummary: "Contains weekly skill mastery percentages, aggregate mistake themes, and explainable next-step targets only. Raw tutor turns, student photos, and direct identifiers are excluded."
            )
        )
    }
}
