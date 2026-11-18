import Foundation

public enum ExamProgressEvidenceStatus: String, Codable, Sendable, Equatable {
    case empty = "EMPTY"
    case insufficientForTrend = "INSUFFICIENT_FOR_TREND"
    case partialTrend = "PARTIAL_TREND"
    case sufficientEvidence = "SUFFICIENT_EVIDENCE"
}

public enum ExamTopicPacingClassification: String, Codable, Sendable, Equatable {
    case bottleneck = "BOTTLENECK"
    case onPace = "ON_PACE"
    case fast = "FAST"
    case insufficientData = "INSUFFICIENT_DATA"
}

public enum ExamTrapSourceKind: String, Codable, Sendable, Equatable {
    case sessionMatchedTrapIncidence = "SESSION_MATCHED_TRAP_INCIDENCE"
    case historicalActiveTrapSnapshots = "HISTORICAL_ACTIVE_TRAP_SNAPSHOTS"
}

public enum ExamTrapDataStatus: String, Codable, Sendable, Equatable {
    case computed = "COMPUTED"
    case insufficientData = "INSUFFICIENT_DATA"
    case zeroVariance = "ZERO_VARIANCE"
    case noHistoricalActiveTrapData = "NO_HISTORICAL_ACTIVE_TRAP_DATA"
}

public enum ExamConqueredTrapsEvidenceStatus: String, Codable, Sendable, Equatable {
    case verifiedTransitions = "VERIFIED_TRANSITIONS"
    case unavailable = "UNAVAILABLE"
}

public struct ExamScoreTrajectoryPoint: Codable, Sendable, Equatable, Identifiable {
    public let sessionId: UUID
    public let submittedAt: Date
    public let netScore: Double
    public let movingAverage3: Double
    public let scoreVersion: Int

    public var id: UUID { sessionId }
}

public struct ExamTopicPacingItem: Codable, Sendable, Equatable, Identifiable {
    public let topicCode: String
    public let subtopicCode: String?
    public let skillId: String?
    public let observedQuestionCount: Int
    public let excludedUnmeasuredCount: Int
    public let meanSecondsPerQuestion: Double?
    public let expectedSecondsPerQuestion: Double?
    public let ratio: Double?
    public let classification: ExamTopicPacingClassification

    public var id: String {
        "\(topicCode)|\(subtopicCode ?? "")|\(skillId ?? "")"
    }
}

public struct ExamTrapObservationPoint: Codable, Sendable, Equatable, Identifiable {
    public let sessionId: UUID
    public let submittedAt: Date
    public let netScore: Double
    public let matchedTrapIncidenceCount: Int

    public var id: UUID { sessionId }
}

public struct ExamTrapRemediationSummary: Codable, Sendable, Equatable {
    public let sourceKind: ExamTrapSourceKind
    public let dataStatus: ExamTrapDataStatus
    public let observations: [ExamTrapObservationPoint]
    public let activeTrapCountNow: Int?
    public let correlationCoefficient: Double?
    public let correlationSampleSize: Int
    public let conqueredTrapsEvidenceStatus: ExamConqueredTrapsEvidenceStatus
    public let associationIsCausal: Bool
}

public struct ExamProgressReport: Codable, Sendable, Equatable {
    public static let expectedSchemaVersion = "exam-analytics-progress-v1"
    public static let expectedAlgorithmVersion = "INV_EXAM_005_V1"
    public static let supportedExams: Set<String> = ["TR_YKS_TYT_MATH", "TR_YKS_AYT_MATH"]

    public static func canonicalExamCode(_ raw: String) -> String {
        switch raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "YKS_TYT", "YKS_TYT_MATH":
            return "TR_YKS_TYT_MATH"
        case "YKS_AYT_SAY", "YKS_AYT", "YKS_AYT_MATH":
            return "TR_YKS_AYT_MATH"
        default:
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            return trimmed.isEmpty ? "TR_YKS_TYT_MATH" : trimmed
        }
    }

    public let schemaVersion: String
    public let snapshotId: UUID
    public let userId: UUID
    public let examCode: String
    public let mockCount: Int
    public let evidenceStatus: ExamProgressEvidenceStatus
    public let deltaNet: Double?
    public let scoreTrajectory: [ExamScoreTrajectoryPoint]
    public let pacingByTopic: [ExamTopicPacingItem]
    public let trapRemediation: ExamTrapRemediationSummary
    public let conqueredTrapsCount: Int
    public let asOfDate: String
    public let algorithmVersion: String
    public let trainingEligible: Bool
    public let llmTokensUsed: Int
    public let inferenceCostUsd: Double
    public let generatedAt: Date
    public let expiresAt: Date

    public func validate(user: UUID, exam: String, now: Date = Date()) throws {
        guard schemaVersion == Self.expectedSchemaVersion,
              algorithmVersion == Self.expectedAlgorithmVersion,
              userId == user,
              examCode == exam,
              Self.supportedExams.contains(examCode),
              !trainingEligible,
              llmTokensUsed == 0,
              inferenceCostUsd == 0,
              !trapRemediation.associationIsCausal,
              (0...20).contains(mockCount),
              scoreTrajectory.count == mockCount,
              conqueredTrapsCount >= 0,
              expiresAt > generatedAt,
              expiresAt.timeIntervalSince(generatedAt) <= 86400,
              expiresAt > now,
              asOfDate.range(of: "^\\d{4}-\\d{2}-\\d{2}$", options: .regularExpression) != nil
        else {
            throw ExamProgressError.integrity
        }

        if trapRemediation.conqueredTrapsEvidenceStatus == .unavailable && conqueredTrapsCount != 0 {
            throw ExamProgressError.integrity
        }

        switch mockCount {
        case 0:
            guard evidenceStatus == .empty, deltaNet == nil, pacingByTopic.isEmpty else {
                throw ExamProgressError.integrity
            }
        case 1:
            guard evidenceStatus == .insufficientForTrend, deltaNet == nil else {
                throw ExamProgressError.integrity
            }
        case 2:
            guard evidenceStatus == .partialTrend, let deltaNet, deltaNet.isFinite else {
                throw ExamProgressError.integrity
            }
        default:
            guard evidenceStatus == .sufficientEvidence, let deltaNet, deltaNet.isFinite else {
                throw ExamProgressError.integrity
            }
        }

        for index in scoreTrajectory.indices {
            let point = scoreTrajectory[index]
            guard point.netScore.isFinite,
                  point.movingAverage3.isFinite,
                  point.scoreVersion >= 1
            else {
                throw ExamProgressError.integrity
            }
            if index > 0 {
                let prev = scoreTrajectory[index - 1]
                if point.submittedAt < prev.submittedAt {
                    throw ExamProgressError.integrity
                }
            }
        }

        for item in pacingByTopic {
            guard !item.topicCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  item.observedQuestionCount >= 0,
                  item.excludedUnmeasuredCount >= 0
            else {
                throw ExamProgressError.integrity
            }
            if item.classification == .insufficientData {
                guard item.meanSecondsPerQuestion == nil,
                      item.expectedSecondsPerQuestion == nil,
                      item.ratio == nil
                else {
                    throw ExamProgressError.integrity
                }
            } else {
                guard item.observedQuestionCount >= 2,
                      let mean = item.meanSecondsPerQuestion, mean.isFinite, mean > 0,
                      let expected = item.expectedSecondsPerQuestion, expected.isFinite, expected > 0,
                      let ratio = item.ratio, ratio.isFinite, ratio > 0
                else {
                    throw ExamProgressError.integrity
                }
            }
        }

        guard (0...20).contains(trapRemediation.correlationSampleSize),
              trapRemediation.observations.count <= 20
        else {
            throw ExamProgressError.integrity
        }
        if let activeNow = trapRemediation.activeTrapCountNow, activeNow < 0 {
            throw ExamProgressError.integrity
        }
        for obs in trapRemediation.observations {
            guard obs.netScore.isFinite, obs.matchedTrapIncidenceCount >= 0 else {
                throw ExamProgressError.integrity
            }
        }
        if trapRemediation.dataStatus == .computed {
            guard trapRemediation.correlationSampleSize >= 3,
                  let r = trapRemediation.correlationCoefficient,
                  r.isFinite,
                  (-1.0...1.0).contains(r)
            else {
                throw ExamProgressError.integrity
            }
        } else {
            guard trapRemediation.correlationCoefficient == nil else {
                throw ExamProgressError.integrity
            }
        }
    }
}

public enum ExamProgressError: Error, Equatable {
    case integrity
    case cache
    case unsupportedExam
}

public protocol ExamProgressServicing: Sendable {
    func progress(exam: String) async throws -> ExamProgressReport
}
