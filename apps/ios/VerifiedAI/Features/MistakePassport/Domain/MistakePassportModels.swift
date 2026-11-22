// apps/ios/VerifiedAI/Features/MistakePassport/Domain/MistakePassportModels.swift
import Foundation

public struct CounterexampleProofSnapshot: Codable, Sendable, Equatable {
    public let cardId: String
    public let faultyStepLatex: String
    public let validStepLatex: String
    public let witnessSubstitutionLatex: String
    public let lhsEvaluatedLatex: String
    public let rhsFaultyEvaluatedLatex: String
    public let contradictionStatementLatex: String
    public let accessibilitySpeechTr: String
    public let accessibilitySpeechEn: String

    public init(
        cardId: String = "H-01",
        faultyStepLatex: String,
        validStepLatex: String,
        witnessSubstitutionLatex: String,
        lhsEvaluatedLatex: String,
        rhsFaultyEvaluatedLatex: String,
        contradictionStatementLatex: String,
        accessibilitySpeechTr: String,
        accessibilitySpeechEn: String
    ) {
        self.cardId = cardId
        self.faultyStepLatex = faultyStepLatex
        self.validStepLatex = validStepLatex
        self.witnessSubstitutionLatex = witnessSubstitutionLatex
        self.lhsEvaluatedLatex = lhsEvaluatedLatex
        self.rhsFaultyEvaluatedLatex = rhsFaultyEvaluatedLatex
        self.contradictionStatementLatex = contradictionStatementLatex
        self.accessibilitySpeechTr = accessibilitySpeechTr
        self.accessibilitySpeechEn = accessibilitySpeechEn
    }
}

public enum TrapLifecycleStatus: String, Codable, Sendable, CaseIterable, Identifiable {
    case recurringPattern = "RECURRING_PATTERN"
    case inRemediation = "IN_REMEDIATION"
    case resolvedPendingRetention = "RESOLVED_PENDING_RETENTION"
    case stableResolved = "STABLE_RESOLVED"

    public var id: String { rawValue }

    public var localizedKey: String {
        switch self {
        case .recurringPattern: return "passport.filter.recurring"
        case .inRemediation: return "passport.filter.in_remediation"
        case .resolvedPendingRetention: return "passport.filter.retention_pending"
        case .stableResolved: return "passport.filter.stable_resolved"
        }
    }
}

public struct SkillFiveMetricsSnapshot: Codable, Sendable, Equatable {
    public let observedOutcomeScore: Double
    public let independentCompetenceScore: Double
    public let posteriorMasteryProbability: Double
    public let retentionProbability: Double
    public let scaffoldDependencyIndex: Double

    public init(
        observedOutcomeScore: Double,
        independentCompetenceScore: Double,
        posteriorMasteryProbability: Double,
        retentionProbability: Double,
        scaffoldDependencyIndex: Double
    ) {
        self.observedOutcomeScore = observedOutcomeScore
        self.independentCompetenceScore = independentCompetenceScore
        self.posteriorMasteryProbability = posteriorMasteryProbability
        self.retentionProbability = retentionProbability
        self.scaffoldDependencyIndex = scaffoldDependencyIndex
    }
}

public struct ActiveTwinRemediationLink: Codable, Sendable, Equatable {
    public let twinDrillId: UUID
    public let twinChallengeStatus: String
    public let deepLinkUri: String

    public init(
        twinDrillId: UUID,
        twinChallengeStatus: String,
        deepLinkUri: String
    ) {
        self.twinDrillId = twinDrillId
        self.twinChallengeStatus = twinChallengeStatus
        self.deepLinkUri = deepLinkUri
    }
}

public struct ExamTrapItemDto: Codable, Sendable, Identifiable, Equatable {
    public var id: UUID { mistakeDnaId }
    public let mistakeDnaId: UUID
    public let mistakeCode: String
    public let mistakeFamily: String
    public let skillNodeId: String
    public let localizedTrapTitleKey: String
    public let localizedCoachingFramingKey: String
    public let lifecycleStatus: TrapLifecycleStatus
    public let occurrenceCount: Int
    public let consecutiveSoloTwinPasses: Int
    public let lastDetectedAt: Date
    public let retentionUnlockEligibleAt: Date?
    public let conqueredAt: Date?
    public let skillMetrics: SkillFiveMetricsSnapshot
    public let activeTwinLink: ActiveTwinRemediationLink?
    public let latestCounterexample: CounterexampleProofSnapshot?

    public init(
        mistakeDnaId: UUID,
        mistakeCode: String,
        mistakeFamily: String,
        skillNodeId: String,
        localizedTrapTitleKey: String,
        localizedCoachingFramingKey: String,
        lifecycleStatus: TrapLifecycleStatus,
        occurrenceCount: Int,
        consecutiveSoloTwinPasses: Int,
        lastDetectedAt: Date,
        retentionUnlockEligibleAt: Date? = nil,
        conqueredAt: Date? = nil,
        skillMetrics: SkillFiveMetricsSnapshot,
        activeTwinLink: ActiveTwinRemediationLink? = nil,
        latestCounterexample: CounterexampleProofSnapshot? = nil
    ) {
        self.mistakeDnaId = mistakeDnaId
        self.mistakeCode = mistakeCode
        self.mistakeFamily = mistakeFamily
        self.skillNodeId = skillNodeId
        self.localizedTrapTitleKey = localizedTrapTitleKey
        self.localizedCoachingFramingKey = localizedCoachingFramingKey
        self.lifecycleStatus = lifecycleStatus
        self.occurrenceCount = occurrenceCount
        self.consecutiveSoloTwinPasses = consecutiveSoloTwinPasses
        self.lastDetectedAt = lastDetectedAt
        self.retentionUnlockEligibleAt = retentionUnlockEligibleAt
        self.conqueredAt = conqueredAt
        self.skillMetrics = skillMetrics
        self.activeTwinLink = activeTwinLink
        self.latestCounterexample = latestCounterexample
    }
}

public struct PassportSummaryMetrics: Codable, Sendable, Equatable {
    public let conqueredExamTrapsCount: Int
    public let activeExamTrapsCount: Int
    public let pendingRetentionVerificationCount: Int
    public let independentTransferRate: Double
    public let meanPosteriorMastery: Double
    public let meanRetentionProbability: Double

    public init(
        conqueredExamTrapsCount: Int,
        activeExamTrapsCount: Int,
        pendingRetentionVerificationCount: Int,
        independentTransferRate: Double,
        meanPosteriorMastery: Double,
        meanRetentionProbability: Double
    ) {
        self.conqueredExamTrapsCount = conqueredExamTrapsCount
        self.activeExamTrapsCount = activeExamTrapsCount
        self.pendingRetentionVerificationCount = pendingRetentionVerificationCount
        self.independentTransferRate = independentTransferRate
        self.meanPosteriorMastery = meanPosteriorMastery
        self.meanRetentionProbability = meanRetentionProbability
    }
}

public struct MistakePassportResponseDto: Codable, Sendable, Equatable {
    public let schemaVersion: String
    public let learnerId: UUID
    public let generatedAt: Date
    public var summaryTrapTitleKeys: [String]?
    public var earnedProofs: [CounterexampleProofSnapshot]?
    public let summaryMetrics: PassportSummaryMetrics
    public let conceptualTraps: [ExamTrapItemDto]
    public let arithmeticSlips: [ExamTrapItemDto]

    public init(
        schemaVersion: String = "mistake-passport.v1",
        learnerId: UUID,
        generatedAt: Date = Date(),
        summaryMetrics: PassportSummaryMetrics,
        conceptualTraps: [ExamTrapItemDto],
        arithmeticSlips: [ExamTrapItemDto]
    ) {
        self.schemaVersion = schemaVersion
        self.learnerId = learnerId
        self.generatedAt = generatedAt
        self.summaryMetrics = summaryMetrics
        self.conceptualTraps = conceptualTraps
        self.arithmeticSlips = arithmeticSlips
    }
}

public protocol MistakePassportAPIClientProtocol: Sendable {
    func fetchMistakePassport(status: TrapLifecycleStatus?) async throws -> MistakePassportResponseDto
}
