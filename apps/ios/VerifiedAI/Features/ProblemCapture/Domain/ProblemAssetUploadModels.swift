import Foundation

enum ProblemAssetUploadPhase: Equatable, Sendable {
    case idle
    case reserving
    case uploading(progress: Double)
    case confirming
    case preprocessing(DurableProblemAssetReference)
    case available(PreprocessedProblemAssetReference)
    case preprocessingWarning(PreprocessedProblemAssetReference, AcceptedCapturedAsset)
    case preprocessingFailed(DurableProblemAssetReference?, ProblemAssetPreprocessingResult?, AcceptedCapturedAsset)
    case recoverableFailure(ProblemAssetUploadFailure, AcceptedCapturedAsset)
}

enum ProblemAssetUploadFailure: Equatable, Sendable {
    case offline
    case reservationFailed(String?)
    case uploadFailed
    case completionFailed(String?)
    case preprocessingFailed(String?)
    case cancelled
}

struct ProblemAssetUploadReservation: Equatable, Sendable {
    let uploadId: UUID
    let problemSessionId: UUID
    let problemAssetId: UUID
    let assetStatus: String
    let uploadURL: URL
    let expiresAt: Date
    let requiredHeaders: [String: String]
}

struct DurableProblemAssetReference: Equatable, Sendable {
    let uploadId: UUID
    let problemSessionId: UUID
    let problemAssetId: UUID
    let problemSessionStatus: String
    let assetStatus: String
    let availableAt: Date
}

struct PreprocessedProblemAssetReference: Equatable, Sendable {
    let durableAsset: DurableProblemAssetReference
    let preprocessing: ProblemAssetPreprocessingResult
}

struct ProblemAssetPreprocessingResult: Equatable, Sendable {
    let sourceAssetId: UUID
    let problemSessionId: UUID
    let sourceAssetStatus: String
    let preprocessingStatus: String
    let qualityOutcome: String?
    let failureCode: String?
    let preferredRecognitionDerivativeId: UUID?
    let derivatives: [ProblemAssetDerivative]
    let qualitySignals: [ProblemAssetQualitySignal]
    let userRecoveryActions: [String]
    let completedAt: Date?

    var warningSignals: [ProblemAssetQualitySignal] {
        qualitySignals.filter { $0.severity == "WARNING" }
    }
}

struct ProblemAssetDerivative: Equatable, Sendable {
    let derivativeId: UUID
    let derivativeKind: String
    let status: String
    let selectedForRecognition: Bool
    let contentType: String?
    let sizeBytes: Int64?
    let checksumSha256: String?
    let width: Int?
    let height: Int?
    let processorName: String
    let processorVersion: String
    let configurationVersion: String
    let orientationNormalized: Bool
    let perspectiveApplied: Bool
    let contrastNormalized: Bool
    let resized: Bool
    let qualityOutcome: String
    let failureCode: String?
}

struct ProblemAssetQualitySignal: Equatable, Identifiable, Sendable {
    let signalType: String
    let severity: String
    let score: Double
    let threshold: Double
    let policyVersion: String
    let messageCode: String

    var id: String {
        signalType
    }
}

struct ProblemAssetUploadRequest: Equatable, Sendable {
    let source: String
    let assetKind: String
    let contentType: String
    let sizeBytes: Int64
    let checksumSha256: String
    let imageWidth: Int?
    let imageHeight: Int?
    let pageCount: Int?
    let cropX: Double
    let cropY: Double
    let cropWidth: Double
    let cropHeight: Double
}
