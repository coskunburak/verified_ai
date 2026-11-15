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

// MARK: - Sprint 9.3 Question Bank, Blueprint & Assessment Assembly Models

public enum ExamItemFormat: String, Sendable, Codable, CaseIterable, Equatable, Hashable, Identifiable {
    case singleChoiceMcq = "SINGLE_CHOICE_MCQ"
    case multiSelectMcq = "MULTI_SELECT_MCQ"
    case numericGridIn = "NUMERIC_GRID_IN"
    case shortStructuredResponse = "SHORT_STRUCTURED_RESPONSE"

    public var id: String { rawValue }
}

public typealias ExamQuestionItemFormat = ExamItemFormat

public enum ExamDifficultyBand: String, Sendable, Codable, CaseIterable, Equatable, Hashable, Identifiable {
    case easy = "EASY"
    case medium = "MEDIUM"
    case hard = "HARD"
    case expert = "EXPERT"

    public var id: String { rawValue }
}

public enum ExamCognitiveLevel: String, Sendable, Codable, CaseIterable, Equatable, Hashable, Identifiable {
    case recall = "RECALL"
    case comprehension = "COMPREHENSION"
    case application = "APPLICATION"
    case analysis = "ANALYSIS"
    case evaluation = "EVALUATION"

    public var id: String { rawValue }
}

public enum ExamProvenanceSourceType: String, Sendable, Codable, CaseIterable, Equatable, Hashable, Identifiable {
    case originalAuthored = "ORIGINAL_AUTHORED"
    case publicDomainPastPaper = "PUBLIC_DOMAIN_PAST_PAPER"
    case licensedPublisherBank = "LICENSED_PUBLISHER_BANK"
    case smeCuratedSynthetic = "SME_CURATED_SYNTHETIC"

    public var id: String { rawValue }
}

public enum ExamBlueprintMode: String, Sendable, Codable, CaseIterable, Equatable, Hashable, Identifiable {
    case diagnostic = "DIAGNOSTIC"
    case sectionDrill = "SECTION_DRILL"
    case fullSimulation = "FULL_SIMULATION"
    case topicMastery = "TOPIC_MASTERY"

    public var id: String { rawValue }

    public var localizationKey: String {
        switch self {
        case .diagnostic:
            "exam.assembly.mode.diagnostic"
        case .sectionDrill:
            "exam.assembly.mode.section_drill"
        case .fullSimulation:
            "exam.assembly.mode.full_simulation"
        case .topicMastery:
            "exam.assembly.mode.topic_mastery"
        }
    }
}

public typealias ExamAssessmentMode = ExamBlueprintMode

public enum ExamBlueprintStatus: String, Sendable, Codable, CaseIterable, Equatable, Hashable, Identifiable {
    case draft = "DRAFT"
    case active = "ACTIVE"
    case archived = "ARCHIVED"

    public var id: String { rawValue }
}

public enum AssembledAssessmentStatus: String, Sendable, Codable, CaseIterable, Equatable, Hashable, Identifiable {
    case ready = "READY"
    case inProgress = "IN_PROGRESS"
    case submitted = "SUBMITTED"
    case expired = "EXPIRED"

    public var id: String { rawValue }
}

public typealias ExamAssembledStatus = AssembledAssessmentStatus

public struct ExamQuestionOptionModel: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let optionKey: String
    public let displayOrder: Int
    public let contentMarkdown: String
    public let contentLatex: String?
    public let isCorrect: Bool
    public let distractorRationale: String?
    public let trainingEligible: Bool

    public init(
        id: UUID,
        optionKey: String,
        displayOrder: Int,
        contentMarkdown: String,
        contentLatex: String? = nil,
        isCorrect: Bool,
        distractorRationale: String? = nil,
        trainingEligible: Bool = false
    ) {
        self.id = id
        self.optionKey = optionKey
        self.displayOrder = displayOrder
        self.contentMarkdown = contentMarkdown
        self.contentLatex = contentLatex
        self.isCorrect = isCorrect
        self.distractorRationale = distractorRationale
        self.trainingEligible = trainingEligible
    }
}

public struct ExamQuestionProvenanceModel: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let sourceType: ExamProvenanceSourceType
    public let licenseClassification: String
    public let copyrightHolder: String
    public let sourceReference: String
    public let authorId: String
    public let reviewerId: String
    public let verificationStatus: String
    public let provenanceHash: String
    public let verifiedAt: String
    public let trainingEligible: Bool
    public let createdAt: String

    public init(
        id: UUID,
        sourceType: ExamProvenanceSourceType,
        licenseClassification: String,
        copyrightHolder: String,
        sourceReference: String,
        authorId: String,
        reviewerId: String,
        verificationStatus: String,
        provenanceHash: String,
        verifiedAt: String,
        trainingEligible: Bool = false,
        createdAt: String
    ) {
        self.id = id
        self.sourceType = sourceType
        self.licenseClassification = licenseClassification
        self.copyrightHolder = copyrightHolder
        self.sourceReference = sourceReference
        self.authorId = authorId
        self.reviewerId = reviewerId
        self.verificationStatus = verificationStatus
        self.provenanceHash = provenanceHash
        self.verifiedAt = verifiedAt
        self.trainingEligible = trainingEligible
        self.createdAt = createdAt
    }
}

public struct ExamQuestionBankItemModel: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let examDefinitionId: UUID
    public let examVersionNumber: Int
    public let examSectionId: UUID?
    public let questionCode: String
    public let topicCode: String
    public let subtopicCode: String?
    public let itemFormat: ExamItemFormat
    public let difficultyBand: ExamDifficultyBand
    public let difficultyBValue: Double
    public let discriminationAValue: Double
    public let guessingCValue: Double
    public let cognitiveLevel: ExamCognitiveLevel
    public let stemMarkdown: String
    public let stemLatex: String?
    public let altTextDescription: String?
    public let hasVisualDiagram: Bool
    public let numericCorrectAnswer: Double?
    public let numericTolerance: Double?
    public let solutionExplanationMarkdown: String
    public let estimatedTimeSeconds: Int
    public let maxPoints: Double
    public let negativeMarkingWeight: Double
    public let status: String
    public let contentSha256: String
    public let exposureCount: Int
    public let options: [ExamQuestionOptionModel]
    public let provenance: ExamQuestionProvenanceModel
    public let trainingEligible: Bool
    public let createdAt: String
    public let updatedAt: String
}

public struct ExamBlueprintTopicRuleDTO: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let topicCode: String
    public let targetWeightBps: Int
    public let minQuestionCount: Int
    public let maxQuestionCount: Int
    public let requiredItemFormats: [ExamItemFormat]
    public let ruleOrder: Int
    public let trainingEligible: Bool

    public init(
        id: UUID,
        topicCode: String,
        targetWeightBps: Int,
        minQuestionCount: Int,
        maxQuestionCount: Int,
        requiredItemFormats: [ExamItemFormat],
        ruleOrder: Int,
        trainingEligible: Bool = false
    ) {
        self.id = id
        self.topicCode = topicCode
        self.targetWeightBps = targetWeightBps
        self.minQuestionCount = minQuestionCount
        self.maxQuestionCount = maxQuestionCount
        self.requiredItemFormats = requiredItemFormats
        self.ruleOrder = ruleOrder
        self.trainingEligible = trainingEligible
    }
}

public typealias ExamBlueprintTopicRuleModel = ExamBlueprintTopicRuleDTO

public struct ExamBlueprintDTO: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let examDefinitionId: UUID
    public let examVersionNumber: Int
    public let examSectionId: UUID?
    public let blueprintCode: String
    public let title: String
    public let description: String?
    public let blueprintMode: ExamBlueprintMode
    public let totalQuestionCount: Int
    public let baseDurationSeconds: Int
    public let easyTargetBps: Int
    public let mediumTargetBps: Int
    public let hardTargetBps: Int
    public let expertTargetBps: Int
    public let difficultyToleranceBps: Int
    public let recallTargetBps: Int
    public let comprehensionTargetBps: Int
    public let applicationTargetBps: Int
    public let analysisTargetBps: Int
    public let evaluationTargetBps: Int
    public let allowRecentlySeenCooldownDays: Int
    public let shuffleQuestions: Bool
    public let shuffleOptions: Bool
    public let status: ExamBlueprintStatus
    public let blueprintHash: String
    public let topicRules: [ExamBlueprintTopicRuleDTO]
    public let trainingEligible: Bool
    public let createdAt: String
    public let updatedAt: String

    public var baseDurationMinutes: Int {
        max(1, Int(ceil(Double(baseDurationSeconds) / 60.0)))
    }

    public init(
        id: UUID,
        examDefinitionId: UUID,
        examVersionNumber: Int,
        examSectionId: UUID? = nil,
        blueprintCode: String,
        title: String,
        description: String? = nil,
        blueprintMode: ExamBlueprintMode,
        totalQuestionCount: Int,
        baseDurationSeconds: Int,
        easyTargetBps: Int,
        mediumTargetBps: Int,
        hardTargetBps: Int,
        expertTargetBps: Int,
        difficultyToleranceBps: Int,
        recallTargetBps: Int,
        comprehensionTargetBps: Int,
        applicationTargetBps: Int,
        analysisTargetBps: Int,
        evaluationTargetBps: Int,
        allowRecentlySeenCooldownDays: Int,
        shuffleQuestions: Bool,
        shuffleOptions: Bool,
        status: ExamBlueprintStatus,
        blueprintHash: String,
        topicRules: [ExamBlueprintTopicRuleDTO],
        trainingEligible: Bool = false,
        createdAt: String,
        updatedAt: String
    ) {
        self.id = id
        self.examDefinitionId = examDefinitionId
        self.examVersionNumber = examVersionNumber
        self.examSectionId = examSectionId
        self.blueprintCode = blueprintCode
        self.title = title
        self.description = description
        self.blueprintMode = blueprintMode
        self.totalQuestionCount = totalQuestionCount
        self.baseDurationSeconds = baseDurationSeconds
        self.easyTargetBps = easyTargetBps
        self.mediumTargetBps = mediumTargetBps
        self.hardTargetBps = hardTargetBps
        self.expertTargetBps = expertTargetBps
        self.difficultyToleranceBps = difficultyToleranceBps
        self.recallTargetBps = recallTargetBps
        self.comprehensionTargetBps = comprehensionTargetBps
        self.applicationTargetBps = applicationTargetBps
        self.analysisTargetBps = analysisTargetBps
        self.evaluationTargetBps = evaluationTargetBps
        self.allowRecentlySeenCooldownDays = allowRecentlySeenCooldownDays
        self.shuffleQuestions = shuffleQuestions
        self.shuffleOptions = shuffleOptions
        self.status = status
        self.blueprintHash = blueprintHash
        self.topicRules = topicRules
        self.trainingEligible = trainingEligible
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

public typealias ExamAssessmentBlueprintModel = ExamBlueprintDTO

public struct AssessmentAssemblyRequestDTO: Sendable, Codable, Equatable {
    public let userId: UUID
    public let candidateProfileId: UUID?
    public let blueprintId: UUID
    public let assemblySeed: String
    public let overrideTimeMultiplier: Double?
    public let highContrastMode: Bool
    public let screenReaderOptimized: Bool
    public let extendedBreakPrompts: Bool
    public let trainingEligible: Bool

    public init(
        userId: UUID,
        candidateProfileId: UUID? = nil,
        blueprintId: UUID,
        assemblySeed: String,
        overrideTimeMultiplier: Double? = nil,
        highContrastMode: Bool = false,
        screenReaderOptimized: Bool = false,
        extendedBreakPrompts: Bool = false,
        trainingEligible: Bool = false
    ) {
        self.userId = userId
        self.candidateProfileId = candidateProfileId
        self.blueprintId = blueprintId
        self.assemblySeed = assemblySeed
        self.overrideTimeMultiplier = overrideTimeMultiplier
        self.highContrastMode = highContrastMode
        self.screenReaderOptimized = screenReaderOptimized
        self.extendedBreakPrompts = extendedBreakPrompts
        self.trainingEligible = trainingEligible
    }
}

public typealias ExamAssessmentAssemblyRequest = AssessmentAssemblyRequestDTO

public struct AppliedAccommodationSnapshotDTO: Sendable, Codable, Equatable {
    public let extraTimeMultiplier: Double
    public let highContrastMode: Bool
    public let screenReaderOptimized: Bool
    public let extendedBreakPrompts: Bool

    public init(
        extraTimeMultiplier: Double = 1.0,
        highContrastMode: Bool = false,
        screenReaderOptimized: Bool = false,
        extendedBreakPrompts: Bool = false
    ) {
        self.extraTimeMultiplier = extraTimeMultiplier
        self.highContrastMode = highContrastMode
        self.screenReaderOptimized = screenReaderOptimized
        self.extendedBreakPrompts = extendedBreakPrompts
    }
}

public typealias ExamAppliedAccommodationSummary = AppliedAccommodationSnapshotDTO

public struct AssemblyDistributionSummaryDTO: Sendable, Codable, Equatable {
    public let topicCounts: [String: Int]
    public let difficultyCounts: [String: Int]
    public let cognitiveCounts: [String: Int]
    public let easyActualBps: Int
    public let mediumActualBps: Int
    public let hardActualBps: Int
    public let expertActualBps: Int

    public init(
        topicCounts: [String: Int],
        difficultyCounts: [String: Int],
        cognitiveCounts: [String: Int],
        easyActualBps: Int,
        mediumActualBps: Int,
        hardActualBps: Int,
        expertActualBps: Int
    ) {
        self.topicCounts = topicCounts
        self.difficultyCounts = difficultyCounts
        self.cognitiveCounts = cognitiveCounts
        self.easyActualBps = easyActualBps
        self.mediumActualBps = mediumActualBps
        self.hardActualBps = hardActualBps
        self.expertActualBps = expertActualBps
    }
}

public typealias ExamDistributionSummary = AssemblyDistributionSummaryDTO

public struct AssembledQuestionOptionDTO: Sendable, Codable, Equatable, Identifiable {
    public var id: String { optionKey }
    public let optionKey: String
    public let displayOrder: Int
    public let contentMarkdown: String
    public let contentLatex: String?
    public let trainingEligible: Bool

    public init(
        optionKey: String,
        displayOrder: Int,
        contentMarkdown: String,
        contentLatex: String? = nil,
        trainingEligible: Bool = false
    ) {
        self.optionKey = optionKey
        self.displayOrder = displayOrder
        self.contentMarkdown = contentMarkdown
        self.contentLatex = contentLatex
        self.trainingEligible = trainingEligible
    }
}

public struct AssembledAssessmentItemDTO: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let questionItemId: UUID
    public let questionCode: String
    public let sequenceNumber: Int
    public let topicCode: String
    public let subtopicCode: String?
    public let itemFormat: ExamItemFormat
    public let difficultyBand: ExamDifficultyBand
    public let cognitiveLevel: ExamCognitiveLevel
    public let stemMarkdown: String
    public let stemLatex: String?
    public let altTextDescription: String?
    public let hasVisualDiagram: Bool
    public let optionOrder: [String]
    public let options: [AssembledQuestionOptionDTO]
    public let allocatedPoints: Double
    public let estimatedTimeSeconds: Int
    public let trainingEligible: Bool

    public init(
        id: UUID,
        questionItemId: UUID,
        questionCode: String,
        sequenceNumber: Int,
        topicCode: String,
        subtopicCode: String? = nil,
        itemFormat: ExamItemFormat,
        difficultyBand: ExamDifficultyBand,
        cognitiveLevel: ExamCognitiveLevel,
        stemMarkdown: String,
        stemLatex: String? = nil,
        altTextDescription: String? = nil,
        hasVisualDiagram: Bool = false,
        optionOrder: [String],
        options: [AssembledQuestionOptionDTO],
        allocatedPoints: Double,
        estimatedTimeSeconds: Int,
        trainingEligible: Bool = false
    ) {
        self.id = id
        self.questionItemId = questionItemId
        self.questionCode = questionCode
        self.sequenceNumber = sequenceNumber
        self.topicCode = topicCode
        self.subtopicCode = subtopicCode
        self.itemFormat = itemFormat
        self.difficultyBand = difficultyBand
        self.cognitiveLevel = cognitiveLevel
        self.stemMarkdown = stemMarkdown
        self.stemLatex = stemLatex
        self.altTextDescription = altTextDescription
        self.hasVisualDiagram = hasVisualDiagram
        self.optionOrder = optionOrder
        self.options = options
        self.allocatedPoints = allocatedPoints
        self.estimatedTimeSeconds = estimatedTimeSeconds
        self.trainingEligible = trainingEligible
    }
}

public typealias ExamAssembledQuestionItemModel = AssembledAssessmentItemDTO

public struct AssembledAssessmentDTO: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let userId: UUID
    public let candidateProfileId: UUID?
    public let blueprintId: UUID
    public let blueprintCode: String
    public let blueprintTitle: String
    public let blueprintMode: ExamBlueprintMode
    public let examDefinitionId: UUID
    public let examVersionNumber: Int
    public let assemblySeed: String
    public let reproducibilityHash: String
    public let totalQuestionCount: Int
    public let baseDurationSeconds: Int
    public let timeMultiplier: Double
    public let effectiveDurationSeconds: Int
    public let accommodationsSnapshot: AppliedAccommodationSnapshotDTO
    public let distributionSummary: AssemblyDistributionSummaryDTO
    public let status: AssembledAssessmentStatus
    public let assembledAt: String
    public let expiresAt: String
    public let items: [AssembledAssessmentItemDTO]
    public let trainingEligible: Bool

    public var baseDurationMinutes: Int {
        max(1, Int(ceil(Double(baseDurationSeconds) / 60.0)))
    }

    public var effectiveDurationMinutes: Int {
        max(1, Int(ceil(Double(effectiveDurationSeconds) / 60.0)))
    }

    public init(
        id: UUID,
        userId: UUID,
        candidateProfileId: UUID? = nil,
        blueprintId: UUID,
        blueprintCode: String,
        blueprintTitle: String,
        blueprintMode: ExamBlueprintMode,
        examDefinitionId: UUID,
        examVersionNumber: Int,
        assemblySeed: String,
        reproducibilityHash: String,
        totalQuestionCount: Int,
        baseDurationSeconds: Int,
        timeMultiplier: Double,
        effectiveDurationSeconds: Int,
        accommodationsSnapshot: AppliedAccommodationSnapshotDTO,
        distributionSummary: AssemblyDistributionSummaryDTO,
        status: AssembledAssessmentStatus,
        assembledAt: String,
        expiresAt: String,
        items: [AssembledAssessmentItemDTO],
        trainingEligible: Bool = false
    ) {
        self.id = id
        self.userId = userId
        self.candidateProfileId = candidateProfileId
        self.blueprintId = blueprintId
        self.blueprintCode = blueprintCode
        self.blueprintTitle = blueprintTitle
        self.blueprintMode = blueprintMode
        self.examDefinitionId = examDefinitionId
        self.examVersionNumber = examVersionNumber
        self.assemblySeed = assemblySeed
        self.reproducibilityHash = reproducibilityHash
        self.totalQuestionCount = totalQuestionCount
        self.baseDurationSeconds = baseDurationSeconds
        self.timeMultiplier = timeMultiplier
        self.effectiveDurationSeconds = effectiveDurationSeconds
        self.accommodationsSnapshot = accommodationsSnapshot
        self.distributionSummary = distributionSummary
        self.status = status
        self.assembledAt = assembledAt
        self.expiresAt = expiresAt
        self.items = items
        self.trainingEligible = trainingEligible
    }
}

public typealias ExamAssembledAssessmentModel = AssembledAssessmentDTO

// MARK: - Sprint 9.4 Timed Mock Exam Runtime & F28 Scratchpad Ingestion Models (CNT-LOS-10 / ADR-005 / ADR-006)

public enum ExamSessionStatus: String, Sendable, Codable, Equatable, CaseIterable {
    case inProgress = "IN_PROGRESS"
    case offlineGraceBuffer = "OFFLINE_GRACE_BUFFER"
    case submitted = "SUBMITTED"
    case expiredAutoSubmitted = "EXPIRED_AUTO_SUBMITTED"
    case abandoned = "ABANDONED"

    public var isActive: Bool {
        self == .inProgress || self == .offlineGraceBuffer
    }
}

public enum ExamItemResponseState: String, Sendable, Codable, Equatable, CaseIterable {
    case unvisited = "UNVISITED"
    case viewedOmitted = "VIEWED_OMITTED"
    case answered = "ANSWERED"
    case flaggedAnswered = "FLAGGED_ANSWERED"
    case flaggedOmitted = "FLAGGED_OMITTED"
}

public enum ExamSessionItemAction: String, Sendable, Codable, Equatable, CaseIterable {
    case viewOnly = "VIEW_ONLY"
    case selectOption = "SELECT_OPTION"
    case clearAnswer = "CLEAR_ANSWER"
    case toggleFlag = "TOGGLE_FLAG"
    case setEliminatedOptions = "SET_ELIMINATED_OPTIONS"
    case batchPatch = "BATCH_PATCH"
}

public enum ExamScratchpadCaptureSource: String, Sendable, Codable, Equatable, CaseIterable {
    case digitalMockScratchpad = "DIGITAL_MOCK_SCRATCHPAD"
    case paperDenemeBooklet = "PAPER_DENEME_BOOKLET"
}

public enum ExamSessionSubmissionReason: String, Sendable, Codable, Equatable, CaseIterable {
    case userInitiated = "USER_INITIATED"
    case serverDeadlineExpired = "SERVER_DEADLINE_EXPIRED"
    case offlineGraceExhausted = "OFFLINE_GRACE_EXHAUSTED"
    case userAbandoned = "USER_ABANDONED"
}

public struct ExamServerClockSyncDTO: Sendable, Codable, Equatable {
    public let serverNow: String
    public let remainingSeconds: Int
    public let graceRemainingSeconds: Int
    public let clientClockSkewMs: Int64
    public let clockSkewAnomaly: Bool
    public let deadlineExpired: Bool
    public let offlineGraceActive: Bool

    public init(
        serverNow: String,
        remainingSeconds: Int,
        graceRemainingSeconds: Int,
        clientClockSkewMs: Int64 = 0,
        clockSkewAnomaly: Bool = false,
        deadlineExpired: Bool = false,
        offlineGraceActive: Bool = false
    ) {
        self.serverNow = serverNow
        self.remainingSeconds = remainingSeconds
        self.graceRemainingSeconds = graceRemainingSeconds
        self.clientClockSkewMs = clientClockSkewMs
        self.clockSkewAnomaly = clockSkewAnomaly
        self.deadlineExpired = deadlineExpired
        self.offlineGraceActive = offlineGraceActive
    }
}

public struct ExamSectionProgressSummaryDTO: Sendable, Codable, Equatable, Identifiable {
    public var id: String { sectionCode }
    public let sectionCode: String
    public let sectionOrder: Int
    public let totalItems: Int
    public let answeredItems: Int
    public let flaggedItems: Int
    public let omittedItems: Int
    public let locked: Bool

    public init(
        sectionCode: String,
        sectionOrder: Int,
        totalItems: Int,
        answeredItems: Int,
        flaggedItems: Int,
        omittedItems: Int,
        locked: Bool = false
    ) {
        self.sectionCode = sectionCode
        self.sectionOrder = sectionOrder
        self.totalItems = totalItems
        self.answeredItems = answeredItems
        self.flaggedItems = flaggedItems
        self.omittedItems = omittedItems
        self.locked = locked
    }
}

public struct ExamScratchpadAssetDTO: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let sessionId: UUID
    public let sessionItemId: UUID
    public let itemPosition: Int
    public let pageNumber: Int
    public let storageKey: String
    public let mimeType: String
    public let byteSize: Int64
    public let sha256Checksum: String
    public let widthPx: Int?
    public let heightPx: Int?
    public let captureSource: ExamScratchpadCaptureSource
    public let bookletPublisher: String?
    public let f28VerificationStatus: String
    public let capturedAt: String
    public let trainingEligible: Bool

    public init(
        id: UUID,
        sessionId: UUID,
        sessionItemId: UUID,
        itemPosition: Int,
        pageNumber: Int,
        storageKey: String,
        mimeType: String,
        byteSize: Int64,
        sha256Checksum: String,
        widthPx: Int? = nil,
        heightPx: Int? = nil,
        captureSource: ExamScratchpadCaptureSource,
        bookletPublisher: String? = nil,
        f28VerificationStatus: String = "QUEUED_FOR_POST_SUBMIT_BATCH",
        capturedAt: String,
        trainingEligible: Bool = false
    ) {
        self.id = id
        self.sessionId = sessionId
        self.sessionItemId = sessionItemId
        self.itemPosition = itemPosition
        self.pageNumber = pageNumber
        self.storageKey = storageKey
        self.mimeType = mimeType
        self.byteSize = byteSize
        self.sha256Checksum = sha256Checksum
        self.widthPx = widthPx
        self.heightPx = heightPx
        self.captureSource = captureSource
        self.bookletPublisher = bookletPublisher
        self.f28VerificationStatus = f28VerificationStatus
        self.capturedAt = capturedAt
        self.trainingEligible = trainingEligible
    }
}

public struct ExamSessionItemDTO: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let itemPosition: Int
    public let questionItemId: UUID
    public let questionCode: String
    public let sectionCode: String
    public let sectionOrder: Int
    public let topicCode: String
    public let itemFormat: String
    public let difficultyBand: ExamDifficultyBand
    public let stemMarkdown: String
    public let optionsSnapshot: [AssembledQuestionOptionDTO]
    public let figureAltText: String?
    public let maxPoints: Double
    public let negativeMarkingWeight: Double
    public var responseState: ExamItemResponseState
    public var selectedOptionKey: String?
    public var numericResponseValue: Double?
    public var flaggedForReview: Bool
    public var eliminatedOptionKeys: [String]
    public var timeSpentSeconds: Int
    public var autosaveVersion: Int
    public var clientAutosaveSeq: Int64
    public var lastAutosavedAt: String?
    public let scratchpadRequired: Bool
    public var scratchpadAssets: [ExamScratchpadAssetDTO]
    public let trainingEligible: Bool

    public init(
        id: UUID,
        itemPosition: Int,
        questionItemId: UUID,
        questionCode: String,
        sectionCode: String,
        sectionOrder: Int,
        topicCode: String,
        itemFormat: String,
        difficultyBand: ExamDifficultyBand,
        stemMarkdown: String,
        optionsSnapshot: [AssembledQuestionOptionDTO],
        figureAltText: String? = nil,
        maxPoints: Double,
        negativeMarkingWeight: Double,
        responseState: ExamItemResponseState,
        selectedOptionKey: String? = nil,
        numericResponseValue: Double? = nil,
        flaggedForReview: Bool = false,
        eliminatedOptionKeys: [String] = [],
        timeSpentSeconds: Int = 0,
        autosaveVersion: Int = 1,
        clientAutosaveSeq: Int64 = 0,
        lastAutosavedAt: String? = nil,
        scratchpadRequired: Bool = false,
        scratchpadAssets: [ExamScratchpadAssetDTO] = [],
        trainingEligible: Bool = false
    ) {
        self.id = id
        self.itemPosition = itemPosition
        self.questionItemId = questionItemId
        self.questionCode = questionCode
        self.sectionCode = sectionCode
        self.sectionOrder = sectionOrder
        self.topicCode = topicCode
        self.itemFormat = itemFormat
        self.difficultyBand = difficultyBand
        self.stemMarkdown = stemMarkdown
        self.optionsSnapshot = optionsSnapshot
        self.figureAltText = figureAltText
        self.maxPoints = maxPoints
        self.negativeMarkingWeight = negativeMarkingWeight
        self.responseState = responseState
        self.selectedOptionKey = selectedOptionKey
        self.numericResponseValue = numericResponseValue
        self.flaggedForReview = flaggedForReview
        self.eliminatedOptionKeys = eliminatedOptionKeys
        self.timeSpentSeconds = timeSpentSeconds
        self.autosaveVersion = autosaveVersion
        self.clientAutosaveSeq = clientAutosaveSeq
        self.lastAutosavedAt = lastAutosavedAt
        self.scratchpadRequired = scratchpadRequired
        self.scratchpadAssets = scratchpadAssets
        self.trainingEligible = trainingEligible
    }
}

public struct F28VerificationBatchEntryDTO: Sendable, Codable, Equatable, Identifiable {
    public var id: UUID { assetId }
    public let assetId: UUID
    public let sessionItemId: UUID
    public let itemPosition: Int
    public let pageNumber: Int
    public let questionCode: String
    public let topicCode: String
    public let storageKey: String
    public let sha256Checksum: String
    public let captureSource: ExamScratchpadCaptureSource
    public let bookletPublisher: String?
    public let f28VerificationStatus: String

    public init(
        assetId: UUID,
        sessionItemId: UUID,
        itemPosition: Int,
        pageNumber: Int,
        questionCode: String,
        topicCode: String,
        storageKey: String,
        sha256Checksum: String,
        captureSource: ExamScratchpadCaptureSource,
        bookletPublisher: String? = nil,
        f28VerificationStatus: String = "QUEUED_FOR_POST_SUBMIT_BATCH"
    ) {
        self.assetId = assetId
        self.sessionItemId = sessionItemId
        self.itemPosition = itemPosition
        self.pageNumber = pageNumber
        self.questionCode = questionCode
        self.topicCode = topicCode
        self.storageKey = storageKey
        self.sha256Checksum = sha256Checksum
        self.captureSource = captureSource
        self.bookletPublisher = bookletPublisher
        self.f28VerificationStatus = f28VerificationStatus
    }
}

public struct F28VerificationBatchManifestDTO: Sendable, Codable, Equatable {
    public let batchVersion: String
    public let batchStatus: String
    public let sessionId: UUID
    public let userId: UUID
    public let examCode: String
    public let totalAssets: Int
    public let llmTokensUsed: Int
    public let trainingEligible: Bool
    public let generatedAt: String
    public let assets: [F28VerificationBatchEntryDTO]

    public init(
        batchVersion: String = "f28-batch-manifest-v1",
        batchStatus: String,
        sessionId: UUID,
        userId: UUID,
        examCode: String,
        totalAssets: Int,
        llmTokensUsed: Int = 0,
        trainingEligible: Bool = false,
        generatedAt: String,
        assets: [F28VerificationBatchEntryDTO]
    ) {
        self.batchVersion = batchVersion
        self.batchStatus = batchStatus
        self.sessionId = sessionId
        self.userId = userId
        self.examCode = examCode
        self.totalAssets = totalAssets
        self.llmTokensUsed = llmTokensUsed
        self.trainingEligible = trainingEligible
        self.generatedAt = generatedAt
        self.assets = assets
    }
}

public struct ExamSessionDTO: Sendable, Codable, Equatable, Identifiable {
    public var id: UUID { sessionId }
    public let schemaVersion: String
    public let sessionId: UUID
    public let userId: UUID
    public let assemblyId: UUID
    public let blueprintId: UUID
    public let examCode: String
    public let examVersionNumber: Int
    public var status: ExamSessionStatus
    public let navigationPolicy: String
    public let noHintPolicyEnforced: Bool
    public let accommodationsSnapshot: AppliedAccommodationSnapshotDTO
    public let baseDurationSeconds: Int
    public let extraTimeMultiplier: Double
    public let effectiveDurationSeconds: Int
    public let offlineGraceBufferSeconds: Int
    public let startedAt: String
    public let serverDeadlineAt: String
    public let graceDeadlineAt: String
    public var lastHeartbeatAt: String
    public var submittedAt: String?
    public var submissionReason: String?
    public let totalItemCount: Int
    public var answeredItemCount: Int
    public var flaggedItemCount: Int
    public var omittedItemCount: Int
    public var scratchpadAttachmentCount: Int
    public var clockSync: ExamServerClockSyncDTO
    public var sectionProgress: [ExamSectionProgressSummaryDTO]
    public var items: [ExamSessionItemDTO]
    public var f28VerificationBatchManifest: F28VerificationBatchManifestDTO?
    public let llmTokensUsed: Int
    public let trainingEligible: Bool

    public init(
        schemaVersion: String = "exam-session-response-v1",
        sessionId: UUID,
        userId: UUID,
        assemblyId: UUID,
        blueprintId: UUID,
        examCode: String,
        examVersionNumber: Int,
        status: ExamSessionStatus,
        navigationPolicy: String,
        noHintPolicyEnforced: Bool = true,
        accommodationsSnapshot: AppliedAccommodationSnapshotDTO,
        baseDurationSeconds: Int,
        extraTimeMultiplier: Double,
        effectiveDurationSeconds: Int,
        offlineGraceBufferSeconds: Int = 120,
        startedAt: String,
        serverDeadlineAt: String,
        graceDeadlineAt: String,
        lastHeartbeatAt: String,
        submittedAt: String? = nil,
        submissionReason: String? = nil,
        totalItemCount: Int,
        answeredItemCount: Int,
        flaggedItemCount: Int,
        omittedItemCount: Int,
        scratchpadAttachmentCount: Int,
        clockSync: ExamServerClockSyncDTO,
        sectionProgress: [ExamSectionProgressSummaryDTO],
        items: [ExamSessionItemDTO],
        f28VerificationBatchManifest: F28VerificationBatchManifestDTO? = nil,
        llmTokensUsed: Int = 0,
        trainingEligible: Bool = false
    ) {
        self.schemaVersion = schemaVersion
        self.sessionId = sessionId
        self.userId = userId
        self.assemblyId = assemblyId
        self.blueprintId = blueprintId
        self.examCode = examCode
        self.examVersionNumber = examVersionNumber
        self.status = status
        self.navigationPolicy = navigationPolicy
        self.noHintPolicyEnforced = noHintPolicyEnforced
        self.accommodationsSnapshot = accommodationsSnapshot
        self.baseDurationSeconds = baseDurationSeconds
        self.extraTimeMultiplier = extraTimeMultiplier
        self.effectiveDurationSeconds = effectiveDurationSeconds
        self.offlineGraceBufferSeconds = offlineGraceBufferSeconds
        self.startedAt = startedAt
        self.serverDeadlineAt = serverDeadlineAt
        self.graceDeadlineAt = graceDeadlineAt
        self.lastHeartbeatAt = lastHeartbeatAt
        self.submittedAt = submittedAt
        self.submissionReason = submissionReason
        self.totalItemCount = totalItemCount
        self.answeredItemCount = answeredItemCount
        self.flaggedItemCount = flaggedItemCount
        self.omittedItemCount = omittedItemCount
        self.scratchpadAttachmentCount = scratchpadAttachmentCount
        self.clockSync = clockSync
        self.sectionProgress = sectionProgress
        self.items = items
        self.f28VerificationBatchManifest = f28VerificationBatchManifest
        self.llmTokensUsed = llmTokensUsed
        self.trainingEligible = trainingEligible
    }
}

public struct ExamSessionStartRequestDTO: Sendable, Codable, Equatable {
    public let userId: UUID?
    public let assemblyId: UUID
    public let offlineGraceBufferSeconds: Int?
    public let clientTimestamp: String?
    public let trainingEligible: Bool

    public init(
        userId: UUID? = nil,
        assemblyId: UUID,
        offlineGraceBufferSeconds: Int? = 120,
        clientTimestamp: String? = nil,
        trainingEligible: Bool = false
    ) {
        self.userId = userId
        self.assemblyId = assemblyId
        self.offlineGraceBufferSeconds = offlineGraceBufferSeconds
        self.clientTimestamp = clientTimestamp
        self.trainingEligible = trainingEligible
    }
}

public struct ExamSessionItemAutosaveRequestDTO: Sendable, Codable, Equatable {
    public let action: ExamSessionItemAction
    public let selectedOptionKey: String?
    public let numericResponseValue: Double?
    public let flaggedForReview: Bool?
    public let eliminatedOptionKeys: [String]?
    public let deltaTimeSpentSeconds: Int
    public let expectedAutosaveVersion: Int
    public let clientAutosaveSeq: Int64
    public let offlineReplay: Bool?
    public let clientTimestamp: String?
    public let trainingEligible: Bool

    public init(
        action: ExamSessionItemAction,
        selectedOptionKey: String? = nil,
        numericResponseValue: Double? = nil,
        flaggedForReview: Bool? = nil,
        eliminatedOptionKeys: [String]? = nil,
        deltaTimeSpentSeconds: Int = 0,
        expectedAutosaveVersion: Int,
        clientAutosaveSeq: Int64,
        offlineReplay: Bool? = false,
        clientTimestamp: String? = nil,
        trainingEligible: Bool = false
    ) {
        self.action = action
        self.selectedOptionKey = selectedOptionKey
        self.numericResponseValue = numericResponseValue
        self.flaggedForReview = flaggedForReview
        self.eliminatedOptionKeys = eliminatedOptionKeys
        self.deltaTimeSpentSeconds = deltaTimeSpentSeconds
        self.expectedAutosaveVersion = expectedAutosaveVersion
        self.clientAutosaveSeq = clientAutosaveSeq
        self.offlineReplay = offlineReplay
        self.clientTimestamp = clientTimestamp
        self.trainingEligible = trainingEligible
    }
}

public struct ExamSessionItemAutosaveResponseDTO: Sendable, Codable, Equatable {
    public let schemaVersion: String
    public let sessionId: UUID
    public let status: ExamSessionStatus
    public let itemPosition: Int
    public let questionCode: String
    public let responseState: ExamItemResponseState
    public let selectedOptionKey: String?
    public let numericResponseValue: Double?
    public let flaggedForReview: Bool
    public let eliminatedOptionKeys: [String]
    public let timeSpentSeconds: Int
    public let autosaveVersion: Int
    public let clientAutosaveSeq: Int64
    public let answeredItemCount: Int
    public let flaggedItemCount: Int
    public let omittedItemCount: Int
    public let clockSync: ExamServerClockSyncDTO
    public let llmTokensUsed: Int
    public let trainingEligible: Bool

    public init(
        schemaVersion: String = "exam-session-item-autosave-response-v1",
        sessionId: UUID,
        status: ExamSessionStatus,
        itemPosition: Int,
        questionCode: String,
        responseState: ExamItemResponseState,
        selectedOptionKey: String? = nil,
        numericResponseValue: Double? = nil,
        flaggedForReview: Bool,
        eliminatedOptionKeys: [String],
        timeSpentSeconds: Int,
        autosaveVersion: Int,
        clientAutosaveSeq: Int64,
        answeredItemCount: Int,
        flaggedItemCount: Int,
        omittedItemCount: Int,
        clockSync: ExamServerClockSyncDTO,
        llmTokensUsed: Int = 0,
        trainingEligible: Bool = false
    ) {
        self.schemaVersion = schemaVersion
        self.sessionId = sessionId
        self.status = status
        self.itemPosition = itemPosition
        self.questionCode = questionCode
        self.responseState = responseState
        self.selectedOptionKey = selectedOptionKey
        self.numericResponseValue = numericResponseValue
        self.flaggedForReview = flaggedForReview
        self.eliminatedOptionKeys = eliminatedOptionKeys
        self.timeSpentSeconds = timeSpentSeconds
        self.autosaveVersion = autosaveVersion
        self.clientAutosaveSeq = clientAutosaveSeq
        self.answeredItemCount = answeredItemCount
        self.flaggedItemCount = flaggedItemCount
        self.omittedItemCount = omittedItemCount
        self.clockSync = clockSync
        self.llmTokensUsed = llmTokensUsed
        self.trainingEligible = trainingEligible
    }
}

public struct ExamScratchpadUploadRequestDTO: Sendable, Codable, Equatable {
    public let pageNumber: Int
    public let storageKey: String
    public let mimeType: String
    public let byteSize: Int64
    public let sha256Checksum: String
    public let widthPx: Int?
    public let heightPx: Int?
    public let captureSource: ExamScratchpadCaptureSource
    public let bookletPublisher: String?
    public let capturedAt: String?
    public let trainingEligible: Bool

    public init(
        pageNumber: Int,
        storageKey: String,
        mimeType: String = "image/jpeg",
        byteSize: Int64,
        sha256Checksum: String,
        widthPx: Int? = nil,
        heightPx: Int? = nil,
        captureSource: ExamScratchpadCaptureSource,
        bookletPublisher: String? = nil,
        capturedAt: String? = nil,
        trainingEligible: Bool = false
    ) {
        self.pageNumber = pageNumber
        self.storageKey = storageKey
        self.mimeType = mimeType
        self.byteSize = byteSize
        self.sha256Checksum = sha256Checksum
        self.widthPx = widthPx
        self.heightPx = heightPx
        self.captureSource = captureSource
        self.bookletPublisher = bookletPublisher
        self.capturedAt = capturedAt
        self.trainingEligible = trainingEligible
    }
}

public struct ExamScratchpadUploadResponseDTO: Sendable, Codable, Equatable {
    public let schemaVersion: String
    public let sessionId: UUID
    public let itemPosition: Int
    public let scratchpadAttachmentCount: Int
    public let itemScratchpadCount: Int
    public let asset: ExamScratchpadAssetDTO
    public let clockSync: ExamServerClockSyncDTO
    public let llmTokensUsed: Int
    public let trainingEligible: Bool

    public init(
        schemaVersion: String = "exam-scratchpad-upload-response-v1",
        sessionId: UUID,
        itemPosition: Int,
        scratchpadAttachmentCount: Int,
        itemScratchpadCount: Int,
        asset: ExamScratchpadAssetDTO,
        clockSync: ExamServerClockSyncDTO,
        llmTokensUsed: Int = 0,
        trainingEligible: Bool = false
    ) {
        self.schemaVersion = schemaVersion
        self.sessionId = sessionId
        self.itemPosition = itemPosition
        self.scratchpadAttachmentCount = scratchpadAttachmentCount
        self.itemScratchpadCount = itemScratchpadCount
        self.asset = asset
        self.clockSync = clockSync
        self.llmTokensUsed = llmTokensUsed
        self.trainingEligible = trainingEligible
    }
}

public struct ExamSessionSubmitRequestDTO: Sendable, Codable, Equatable {
    public let submissionReason: ExamSessionSubmissionReason
    public let clientTimestamp: String?
    public let trainingEligible: Bool

    public init(
        submissionReason: ExamSessionSubmissionReason = .userInitiated,
        clientTimestamp: String? = nil,
        trainingEligible: Bool = false
    ) {
        self.submissionReason = submissionReason
        self.clientTimestamp = clientTimestamp
        self.trainingEligible = trainingEligible
    }
}

public struct ExamSessionSubmitResponseDTO: Sendable, Codable, Equatable {
    public let schemaVersion: String
    public let sessionId: UUID
    public let assemblyId: UUID
    public let blueprintId: UUID
    public let examCode: String
    public let status: ExamSessionStatus
    public let submissionReason: String
    public let submittedAt: String
    public let submitIdempotencyKey: String
    public let totalItemCount: Int
    public let answeredItemCount: Int
    public let flaggedItemCount: Int
    public let omittedItemCount: Int
    public let scratchpadAttachmentCount: Int
    public let f28VerificationBatchManifest: F28VerificationBatchManifestDTO
    public let clockSync: ExamServerClockSyncDTO
    public let llmTokensUsed: Int
    public let trainingEligible: Bool

    public init(
        schemaVersion: String = "exam-session-submit-response-v1",
        sessionId: UUID,
        assemblyId: UUID,
        blueprintId: UUID,
        examCode: String,
        status: ExamSessionStatus,
        submissionReason: String,
        submittedAt: String,
        submitIdempotencyKey: String,
        totalItemCount: Int,
        answeredItemCount: Int,
        flaggedItemCount: Int,
        omittedItemCount: Int,
        scratchpadAttachmentCount: Int,
        f28VerificationBatchManifest: F28VerificationBatchManifestDTO,
        clockSync: ExamServerClockSyncDTO,
        llmTokensUsed: Int = 0,
        trainingEligible: Bool = false
    ) {
        self.schemaVersion = schemaVersion
        self.sessionId = sessionId
        self.assemblyId = assemblyId
        self.blueprintId = blueprintId
        self.examCode = examCode
        self.status = status
        self.submissionReason = submissionReason
        self.submittedAt = submittedAt
        self.submitIdempotencyKey = submitIdempotencyKey
        self.totalItemCount = totalItemCount
        self.answeredItemCount = answeredItemCount
        self.flaggedItemCount = flaggedItemCount
        self.omittedItemCount = omittedItemCount
        self.scratchpadAttachmentCount = scratchpadAttachmentCount
        self.f28VerificationBatchManifest = f28VerificationBatchManifest
        self.clockSync = clockSync
        self.llmTokensUsed = llmTokensUsed
        self.trainingEligible = trainingEligible
    }
}

public struct ExamNoHintGuardResponseDTO: Sendable, Codable, Equatable {
    public let schemaVersion: String
    public let sessionId: UUID
    public let status: ExamSessionStatus
    public let hintAllowed: Bool
    public let policyCode: String
    public let requestedSurface: String
    public let neutralExplanationKey: String
    public let serverNow: String
    public let llmTokensUsed: Int
    public let trainingEligible: Bool

    public init(
        schemaVersion: String = "exam-no-hint-guard-response-v1",
        sessionId: UUID,
        status: ExamSessionStatus,
        hintAllowed: Bool,
        policyCode: String,
        requestedSurface: String,
        neutralExplanationKey: String,
        serverNow: String,
        llmTokensUsed: Int = 0,
        trainingEligible: Bool = false
    ) {
        self.schemaVersion = schemaVersion
        self.sessionId = sessionId
        self.status = status
        self.hintAllowed = hintAllowed
        self.policyCode = policyCode
        self.requestedSurface = requestedSurface
        self.neutralExplanationKey = neutralExplanationKey
        self.serverNow = serverNow
        self.llmTokensUsed = llmTokensUsed
        self.trainingEligible = trainingEligible
    }
}

public struct ExamAutosaveOutboxEntry: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let sessionId: UUID
    public let userId: UUID
    public let itemPosition: Int
    public let idempotencyKey: String
    public let request: ExamSessionItemAutosaveRequestDTO
    public let enqueuedAt: String

    public init(
        id: UUID = UUID(),
        sessionId: UUID,
        userId: UUID,
        itemPosition: Int,
        idempotencyKey: String,
        request: ExamSessionItemAutosaveRequestDTO,
        enqueuedAt: String
    ) {
        self.id = id
        self.sessionId = sessionId
        self.userId = userId
        self.itemPosition = itemPosition
        self.idempotencyKey = idempotencyKey
        self.request = request
        self.enqueuedAt = enqueuedAt
    }
}

public struct ExamLocalScratchpadDraft: Sendable, Codable, Equatable, Identifiable {
    public let id: UUID
    public let sessionId: UUID
    public let userId: UUID
    public let itemPosition: Int
    public let pageNumber: Int
    public let captureSource: ExamScratchpadCaptureSource
    public let bookletPublisher: String?
    public let storageKey: String
    public let mimeType: String
    public let byteSize: Int64
    public let sha256Checksum: String
    public let uploadState: String
    public let idempotencyKey: String
    public let createdAt: String

    public init(
        id: UUID = UUID(),
        sessionId: UUID,
        userId: UUID,
        itemPosition: Int,
        pageNumber: Int,
        captureSource: ExamScratchpadCaptureSource,
        bookletPublisher: String? = nil,
        storageKey: String,
        mimeType: String = "image/jpeg",
        byteSize: Int64,
        sha256Checksum: String,
        uploadState: String = "QUEUED_LOCAL",
        idempotencyKey: String,
        createdAt: String
    ) {
        self.id = id
        self.sessionId = sessionId
        self.userId = userId
        self.itemPosition = itemPosition
        self.pageNumber = pageNumber
        self.captureSource = captureSource
        self.bookletPublisher = bookletPublisher
        self.storageKey = storageKey
        self.mimeType = mimeType
        self.byteSize = byteSize
        self.sha256Checksum = sha256Checksum
        self.uploadState = uploadState
        self.idempotencyKey = idempotencyKey
        self.createdAt = createdAt
    }
}
