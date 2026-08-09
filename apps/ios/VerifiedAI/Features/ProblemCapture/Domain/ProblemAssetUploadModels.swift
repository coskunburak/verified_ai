import Foundation

enum ProblemAssetUploadPhase: Equatable, Sendable {
    case idle
    case reserving
    case uploading(progress: Double)
    case confirming
    case preprocessing(DurableProblemAssetReference)
    case available(PreprocessedProblemAssetReference)
    case startingRecognition(PreprocessedProblemAssetReference)
    case recognizing(PreprocessedProblemAssetReference, ProblemRecognitionResult)
    case recognized(RecognizedProblemReference)
    case recognitionReviewRequired(RecognizedProblemReference)
    case recognitionFailed(PreprocessedProblemAssetReference, ProblemRecognitionResult?)
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

struct RecognizedProblemReference: Equatable, Sendable {
    let preprocessedAsset: PreprocessedProblemAssetReference
    let recognition: ProblemRecognitionResult
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

struct ProblemRecognitionResult: Equatable, Sendable {
    let recognitionJobId: UUID?
    let problemSessionId: UUID
    let sourceAssetId: UUID?
    let inputDerivativeId: UUID?
    let status: String
    let capability: String
    let attemptCount: Int
    let maxAttempts: Int
    let lastErrorCode: String?
    let lastFailureClass: String?
    let reviewRequired: Bool
    let schemaVersion: String?
    let promptId: String?
    let promptVersion: String?
    let routePolicyVersion: String?
    let provider: String?
    let model: String?
    let blockCount: Int
    let blocks: [ProblemRecognitionBlock]
    let completedAt: Date?

    var isTerminalSuccess: Bool {
        status == "SUCCEEDED"
    }

    var isRetryableFailure: Bool {
        status == "FAILED_RETRYABLE"
    }

    var isTerminalFailure: Bool {
        status == "FAILED_TERMINAL"
    }
}

struct ProblemRecognitionBlock: Equatable, Identifiable, Sendable {
    let id: String
    let kind: String
    let text: String
    let boundingBox: ProblemRecognitionBoundingBox
    let readingOrder: Int
    let confidenceStatus: String
    let normalizedConfidence: Double?
    let uncertainty: [String]
    let layoutHints: [String]
}

struct ProblemRecognitionBoundingBox: Equatable, Sendable {
    let x: Double
    let y: Double
    let width: Double
    let height: Double
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
