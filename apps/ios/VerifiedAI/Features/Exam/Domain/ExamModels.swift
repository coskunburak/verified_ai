import Foundation

public enum ExamJurisdiction: String, Codable, CaseIterable, Equatable, Hashable, Sendable, Identifiable {
    case tr = "TR"
    case intl = "INTL"

    public var id: String { rawValue }
}

public enum ExamFamily: String, Codable, CaseIterable, Equatable, Hashable, Sendable, Identifiable {
    case yks = "YKS"
    case lgs = "LGS"
    case sat = "SAT"
    case ap = "AP"
    case ib = "IB"

    public var id: String { rawValue }
}

public enum ExamStatus: String, Codable, Equatable, Hashable, Sendable {
    case draft = "DRAFT"
    case active = "ACTIVE"
    case deprecated = "DEPRECATED"
    case archived = "ARCHIVED"
}

public enum ScoringFormulaType: String, Codable, Equatable, Hashable, Sendable {
    case correctMinusQuarterWrong = "CORRECT_MINUS_QUARTER_WRONG"
    case rawCorrectOnly = "RAW_CORRECT_ONLY"
    case weightedSectionRubric = "WEIGHTED_SECTION_RUBRIC"

    public var localizationKey: String {
        "exam.scoring.\(rawValue)"
    }
}

public enum ExamNavigationPolicy: String, Codable, Equatable, Hashable, Sendable {
    case freeJump = "FREE_JUMP"
    case sectionLocked = "SECTION_LOCKED"
    case linearOnly = "LINEAR_ONLY"
}

public enum ExamTargetStatus: String, Codable, Equatable, Hashable, Sendable {
    case active = "ACTIVE"
    case superseded = "SUPERSEDED"
    case achieved = "ACHIEVED"
    case archived = "ARCHIVED"
}

public struct ExamSectionSpec: Codable, Equatable, Hashable, Sendable, Identifiable {
    public let sectionCode: String
    public let titleEn: String
    public let titleTr: String
    public let questionCount: Int
    public let durationMinutes: Int
    public let order: Int

    public var id: String { sectionCode }

    public func localizedTitle(languageCode: String?) -> String {
        languageCode == "tr" ? titleTr : titleEn
    }
}

public struct SectionWeightEntry: Codable, Equatable, Hashable, Sendable {
    public let sectionCode: String
    public let weight: Double
}

public struct ScoringParameters: Codable, Equatable, Hashable, Sendable {
    public let scaleMin: Double?
    public let scaleMax: Double?
    public let moduleCount: Int?
    public let rubricScaleMin: Int?
    public let rubricScaleMax: Int?
    public let sectionWeights: [SectionWeightEntry]
}

public struct CognitiveLevelMix: Codable, Equatable, Hashable, Sendable {
    public let recall: Double
    public let application: Double
    public let analysis: Double
}

public struct ExamTopicWeightModel: Codable, Equatable, Hashable, Sendable, Identifiable {
    public let sectionCode: String
    public let skillId: String
    public let domainCode: String
    public let questionCountWeight: Double
    public let minQuestions: Int
    public let maxQuestions: Int
    public let cognitiveLevelMix: CognitiveLevelMix
    public let sortOrder: Int

    public var id: String { "\(sectionCode)::\(skillId)" }
}

public struct ExamDefinitionSummaryModel: Codable, Equatable, Hashable, Sendable, Identifiable {
    public let examDefinitionId: UUID
    public let examCode: String
    public let examVersion: String
    public let jurisdiction: ExamJurisdiction
    public let examFamily: ExamFamily
    public let titleEn: String
    public let titleTr: String
    public let status: ExamStatus
    public let totalQuestions: Int
    public let durationMinutes: Int
    public let scoringFormulaType: ScoringFormulaType
    public let wrongPenaltyFactor: Double
    public let minScore: Double
    public let maxScore: Double
    public let navigationPolicy: ExamNavigationPolicy
    public let blueprintHash: String
    public let effectiveFrom: String
    public let deprecatedAt: String?
    public let supersededByVersion: String?
    public let sectionCount: Int
    public let topicWeightCount: Int
    public let trainingEligible: Bool

    public var id: UUID { examDefinitionId }

    public func localizedTitle(languageCode: String?) -> String {
        languageCode == "tr" ? titleTr : titleEn
    }
}

public struct ExamDefinitionCatalogResponse: Codable, Equatable, Sendable {
    public let schemaVersion: String
    public let definitions: [ExamDefinitionSummaryModel]
    public let totalCount: Int
    public let includeDeprecated: Bool
    public let trainingEligible: Bool
    public let llmTokensUsed: Int
}

public struct ExamDefinitionModel: Codable, Equatable, Sendable, Identifiable {
    public let schemaVersion: String
    public let examDefinitionId: UUID
    public let examCode: String
    public let examVersion: String
    public let jurisdiction: ExamJurisdiction
    public let examFamily: ExamFamily
    public let titleEn: String
    public let titleTr: String
    public let status: ExamStatus
    public let totalQuestions: Int
    public let durationMinutes: Int
    public let scoringFormulaType: ScoringFormulaType
    public let wrongPenaltyFactor: Double
    public let minScore: Double
    public let maxScore: Double
    public let navigationPolicy: ExamNavigationPolicy
    public let blueprintHash: String
    public let effectiveFrom: String
    public let deprecatedAt: String?
    public let supersededByVersion: String?
    public let sections: [ExamSectionSpec]
    public let scoringParameters: ScoringParameters
    public let topicWeights: [ExamTopicWeightModel]
    public let trainingEligible: Bool
    public let llmTokensUsed: Int

    public var id: UUID { examDefinitionId }

    public func localizedTitle(languageCode: String?) -> String {
        languageCode == "tr" ? titleTr : titleEn
    }
}

public struct PinnedBlueprintSnapshot: Codable, Equatable, Sendable {
    public let examDefinitionId: UUID
    public let examCode: String
    public let examVersion: String
    public let jurisdiction: ExamJurisdiction
    public let examFamily: ExamFamily
    public let titleEn: String
    public let titleTr: String
    public let totalQuestions: Int
    public let durationMinutes: Int
    public let scoringFormulaType: ScoringFormulaType
    public let wrongPenaltyFactor: Double
    public let minScore: Double
    public let maxScore: Double
    public let navigationPolicy: ExamNavigationPolicy
    public let blueprintHash: String
    public let effectiveFrom: String
    public let sections: [ExamSectionSpec]
    public let scoringParameters: ScoringParameters
    public let topicWeights: [ExamTopicWeightModel]
    public let trainingEligible: Bool
}

public struct ReadinessGapPreview: Codable, Equatable, Sendable {
    public let schemaVersion: String
    public let currentEstimatedScore: Double
    public let targetScore: Double
    public let gapPoints: Double
    public let estimationBasis: String
    public let llmTokensUsed: Int
    public let trainingEligible: Bool
}

public struct UpsertStudentExamTargetRequestDTO: Codable, Equatable, Sendable {
    public let schemaVersion: String
    public let examCode: String
    public let examVersion: String?
    public let expectedBlueprintHash: String?
    public let targetScore: Double
    public let targetDate: String
    public let priorityRank: Int
    public let isPrimary: Bool
    public let idempotencyKey: String

    public init(
        schemaVersion: String = "student-exam-target-request-v1",
        examCode: String,
        examVersion: String?,
        expectedBlueprintHash: String?,
        targetScore: Double,
        targetDate: String,
        priorityRank: Int = 1,
        isPrimary: Bool = true,
        idempotencyKey: String
    ) {
        self.schemaVersion = schemaVersion
        self.examCode = examCode
        self.examVersion = examVersion
        self.expectedBlueprintHash = expectedBlueprintHash
        self.targetScore = targetScore
        self.targetDate = targetDate
        self.priorityRank = priorityRank
        self.isPrimary = isPrimary
        self.idempotencyKey = idempotencyKey
    }
}

public struct StudentExamTargetModel: Codable, Equatable, Sendable, Identifiable {
    public let schemaVersion: String
    public let targetId: UUID
    public let userId: UUID
    public let examDefinitionId: UUID
    public let examCode: String
    public let examVersion: String
    public let blueprintHash: String
    public let targetScore: Double
    public let targetDate: String
    public let priorityRank: Int
    public let targetStatus: ExamTargetStatus
    public let isPrimary: Bool
    public let idempotencyKey: String
    public let readinessGapPreview: ReadinessGapPreview
    public let pinnedBlueprint: PinnedBlueprintSnapshot
    public let supersededAt: String?
    public let idempotentReplay: Bool
    public let llmTokensUsed: Int
    public let trainingEligible: Bool
    public let createdAt: String
    public let updatedAt: String

    public var id: UUID { targetId }
}

public struct StudentExamTargetListResponse: Codable, Equatable, Sendable {
    public let schemaVersion: String
    public let userId: UUID
    public let targets: [StudentExamTargetModel]
    public let totalCount: Int
    public let llmTokensUsed: Int
    public let trainingEligible: Bool
}

// MARK: - Sprint 9.2 Candidate Profile, Accommodations & Snap-to-Plan Syllabus Models

public enum CandidateFeasibilityStatus: String, Sendable, Codable, CaseIterable, Equatable {
    case optimal = "OPTIMAL"
    case stretch = "STRETCH"
    case atRisk = "AT_RISK"
    case unrealistic = "UNREALISTIC"
}

public enum SyllabusUploadStatus: String, Sendable, Codable, CaseIterable, Equatable {
    case parsedAutoConfirmed = "PARSED_AUTO_CONFIRMED"
    case needsDisambiguation = "NEEDS_DISAMBIGUATION"
    case userConfirmed = "USER_CONFIRMED"
    case rejected = "REJECTED"
}

public struct AccessibilityAccommodations: Sendable, Codable, Equatable {
    public let extraTimeMultiplier: Double
    public let highContrastEnabled: Bool
    public let screenReaderOptimized: Bool
    public let reducedMotionEnabled: Bool

    public init(
        extraTimeMultiplier: Double = 1.0,
        highContrastEnabled: Bool = false,
        screenReaderOptimized: Bool = false,
        reducedMotionEnabled: Bool = false
    ) {
        self.extraTimeMultiplier = extraTimeMultiplier
        self.highContrastEnabled = highContrastEnabled
        self.screenReaderOptimized = screenReaderOptimized
        self.reducedMotionEnabled = reducedMotionEnabled
    }
}

public struct AdjustedExamSection: Sendable, Codable, Equatable, Identifiable {
    public var id: String { sectionCode }
    public let sectionCode: String
    public let titleEn: String
    public let titleTr: String
    public let questionCount: Int
    public let baseDurationMinutes: Int
    public let adjustedDurationMinutes: Int
    public let order: Int

    public init(
        sectionCode: String,
        titleEn: String,
        titleTr: String,
        questionCount: Int,
        baseDurationMinutes: Int,
        adjustedDurationMinutes: Int,
        order: Int
    ) {
        self.sectionCode = sectionCode
        self.titleEn = titleEn
        self.titleTr = titleTr
        self.questionCount = questionCount
        self.baseDurationMinutes = baseDurationMinutes
        self.adjustedDurationMinutes = adjustedDurationMinutes
        self.order = order
    }
}

public struct UpsertCandidateProfileRequest: Sendable, Codable, Equatable {
    public let schemaVersion: String
    public let examCode: String
    public let pinnedExamVersion: String?
    public let targetScore: Double
    public let targetDate: String
    public let dailyStudyMinutes: Int
    public let weeklyStudyMinutes: Int
    public let accommodations: AccessibilityAccommodations
    public let baselineMastery: Double?
    public let changeReason: String?
    public let idempotencyKey: String?

    public init(
        schemaVersion: String = "candidate-profile-request-v1",
        examCode: String,
        pinnedExamVersion: String? = nil,
        targetScore: Double,
        targetDate: String,
        dailyStudyMinutes: Int,
        weeklyStudyMinutes: Int,
        accommodations: AccessibilityAccommodations = AccessibilityAccommodations(),
        baselineMastery: Double? = nil,
        changeReason: String? = nil,
        idempotencyKey: String? = nil
    ) {
        self.schemaVersion = schemaVersion
        self.examCode = examCode
        self.pinnedExamVersion = pinnedExamVersion
        self.targetScore = targetScore
        self.targetDate = targetDate
        self.dailyStudyMinutes = dailyStudyMinutes
        self.weeklyStudyMinutes = weeklyStudyMinutes
        self.accommodations = accommodations
        self.baselineMastery = baselineMastery
        self.changeReason = changeReason
        self.idempotencyKey = idempotencyKey
    }
}

public struct CandidateProfileResponse: Sendable, Codable, Equatable, Identifiable {
    public var id: String { profileId }
    public let schemaVersion: String
    public let profileId: String
    public let userId: String
    public let activeExamDefinitionId: String
    public let activeExamCode: String
    public let pinnedExamVersion: String
    public let blueprintHash: String
    public let activeTargetId: String?
    public let jurisdiction: ExamJurisdiction
    public let targetDate: String
    public let targetScore: Double
    public let dailyStudyMinutes: Int
    public let weeklyStudyMinutes: Int
    public let accommodations: AccessibilityAccommodations
    public let baselineMastery: Double
    public let daysUntilExam: Int
    public let effectiveDailyCapacityMinutes: Int
    public let requiredDailyMinutes: Int
    public let feasibilityStatus: CandidateFeasibilityStatus
    public let adjustedSections: [AdjustedExamSection]
    public let idempotentReplay: Bool
    public let llmTokensUsed: Int
    public let trainingEligible: Bool
    public let createdAt: String
    public let updatedAt: String

    public init(
        schemaVersion: String,
        profileId: String,
        userId: String,
        activeExamDefinitionId: String,
        activeExamCode: String,
        pinnedExamVersion: String,
        blueprintHash: String,
        activeTargetId: String?,
        jurisdiction: ExamJurisdiction,
        targetDate: String,
        targetScore: Double,
        dailyStudyMinutes: Int,
        weeklyStudyMinutes: Int,
        accommodations: AccessibilityAccommodations,
        baselineMastery: Double,
        daysUntilExam: Int,
        effectiveDailyCapacityMinutes: Int,
        requiredDailyMinutes: Int,
        feasibilityStatus: CandidateFeasibilityStatus,
        adjustedSections: [AdjustedExamSection],
        idempotentReplay: Bool,
        llmTokensUsed: Int,
        trainingEligible: Bool,
        createdAt: String,
        updatedAt: String
    ) {
        self.schemaVersion = schemaVersion
        self.profileId = profileId
        self.userId = userId
        self.activeExamDefinitionId = activeExamDefinitionId
        self.activeExamCode = activeExamCode
        self.pinnedExamVersion = pinnedExamVersion
        self.blueprintHash = blueprintHash
        self.activeTargetId = activeTargetId
        self.jurisdiction = jurisdiction
        self.targetDate = targetDate
        self.targetScore = targetScore
        self.dailyStudyMinutes = dailyStudyMinutes
        self.weeklyStudyMinutes = weeklyStudyMinutes
        self.accommodations = accommodations
        self.baselineMastery = baselineMastery
        self.daysUntilExam = daysUntilExam
        self.effectiveDailyCapacityMinutes = effectiveDailyCapacityMinutes
        self.requiredDailyMinutes = requiredDailyMinutes
        self.feasibilityStatus = feasibilityStatus
        self.adjustedSections = adjustedSections
        self.idempotentReplay = idempotentReplay
        self.llmTokensUsed = llmTokensUsed
        self.trainingEligible = trainingEligible
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

public struct CandidateHorizonHistoryEntry: Sendable, Codable, Equatable, Identifiable {
    public var id: String { historyId }
    public let historyId: String
    public let profileId: String
    public let examCode: String
    public let pinnedExamVersion: String
    public let previousTargetDate: String?
    public let newTargetDate: String
    public let previousDailyMinutes: Int?
    public let newDailyMinutes: Int
    public let previousWeeklyMinutes: Int?
    public let newWeeklyMinutes: Int
    public let previousTargetScore: Double?
    public let newTargetScore: Double
    public let extraTimeMultiplier: Double
    public let requiredDailyMinutes: Int
    public let feasibilityStatus: CandidateFeasibilityStatus
    public let changeReason: String
    public let changedAt: String
}

public struct CandidateProfileHistoryResponse: Sendable, Codable, Equatable {
    public let schemaVersion: String
    public let userId: String
    public let items: [CandidateHorizonHistoryEntry]
    public let llmTokensUsed: Int
    public let trainingEligible: Bool
}

public struct DisambiguationCandidate: Sendable, Codable, Equatable, Identifiable {
    public var id: String { skillId }
    public let skillId: String
    public let topicId: String
    public let similarityScore: Double
}

public struct ParsedSyllabusTopicItem: Sendable, Codable, Equatable, Identifiable {
    public var id: Int { itemIndex }
    public let itemIndex: Int
    public let rawTopicLabel: String
    public let targetWeek: Int?
    public let targetMonth: String?
    public let extractionConfidence: Double
    public let matchedSkillId: String?
    public let matchedTopicId: String?
    public let similarityScore: Double
    public let autoConfirmed: Bool
    public let userConfirmed: Bool
    public let candidates: [DisambiguationCandidate]
}

public struct SyllabusMappingConfirmation: Sendable, Codable, Equatable {
    public let itemIndex: Int
    public let confirmedSkillId: String

    public init(itemIndex: Int, confirmedSkillId: String) {
        self.itemIndex = itemIndex
        self.confirmedSkillId = confirmedSkillId
    }
}

public struct SyllabusConfirmRequest: Sendable, Codable, Equatable {
    public let schemaVersion: String
    public let mappings: [SyllabusMappingConfirmation]

    public init(schemaVersion: String = "syllabus-confirm-request-v1", mappings: [SyllabusMappingConfirmation]) {
        self.schemaVersion = schemaVersion
        self.mappings = mappings
    }
}

public struct SyllabusIngestionResponse: Sendable, Codable, Equatable, Identifiable {
    public var id: String { uploadId }
    public let schemaVersion: String
    public let uploadId: String
    public let userId: String
    public let examDefinitionId: String
    public let examCode: String
    public let examVersion: String
    public let imageHash: String
    public let mimeType: String
    public let byteSize: Int
    public let status: SyllabusUploadStatus
    public let deduplicatedFromCache: Bool
    public let idempotentReplay: Bool
    public let remainingMonthlyQuota: Int
    public let parsedTopics: [ParsedSyllabusTopicItem]
    public let llmTokensUsed: Int
    public let trainingEligible: Bool
    public let expiresAt: String
    public let confirmedAt: String?
    public let createdAt: String
    public let updatedAt: String
}
