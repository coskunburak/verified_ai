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
