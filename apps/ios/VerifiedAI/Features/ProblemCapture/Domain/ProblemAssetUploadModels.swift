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
    case startingParse(RecognizedProblemReference)
    case parsing(RecognizedProblemReference, ProblemParseResult)
    case parsed(ParsedProblemReference)
    case parseReviewRequired(ParsedProblemReference)
    case parseUnsupported(RecognizedProblemReference, ProblemParseResult)
    case parseFailed(RecognizedProblemReference, ProblemParseResult?)
    case canonicalizing(ParsedProblemReference)
    case canonicalized(CanonicalizedProblemReference)
    case canonicalizationFailed(ParsedProblemReference, CanonicalProblemResult?)
    case startingClassification(CanonicalizedProblemReference)
    case classifying(CanonicalizedProblemReference, ProblemClassificationResult)
    case classified(ClassifiedProblemReference)
    case classificationReviewRequired(ClassifiedProblemReference)
    case classificationUnsupported(CanonicalizedProblemReference, ProblemClassificationResult)
    case classificationFailed(CanonicalizedProblemReference, ProblemClassificationResult?)
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

struct ParsedProblemReference: Equatable, Sendable {
    let recognizedProblem: RecognizedProblemReference
    let parse: ProblemParseResult
}

struct CanonicalizedProblemReference: Equatable, Sendable {
    let parsedProblem: ParsedProblemReference
    let canonicalProblem: CanonicalProblemResult
}

struct ClassifiedProblemReference: Equatable, Sendable {
    let canonicalizedProblem: CanonicalizedProblemReference
    let classification: ProblemClassificationResult
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

struct ProblemParseResult: Equatable, Sendable {
    let parseJobId: UUID?
    let problemSessionId: UUID
    let recognitionEvidenceId: UUID?
    let recognitionEvidenceRevision: Int?
    let jobStatus: String
    let capability: String
    let attemptCount: Int
    let maxAttempts: Int
    let lastErrorCode: String?
    let lastFailureClass: String?
    let problemParseId: UUID?
    let parseRevision: Int?
    let supportStatus: String?
    let unsupportedReason: String?
    let reviewRequired: Bool
    let schemaVersion: String?
    let promptId: String?
    let promptVersion: String?
    let routePolicyVersion: String?
    let provider: String?
    let model: String?
    let normalizedProblem: NormalizedProblemParse?
    let createdAt: Date?
    let updatedAt: Date?
    let completedAt: Date?

    var isTerminalSuccess: Bool {
        jobStatus == "SUCCEEDED"
    }

    var isUnsupported: Bool {
        jobStatus == "UNSUPPORTED" || supportStatus == "UNSUPPORTED"
    }

    var isRetryableFailure: Bool {
        jobStatus == "FAILED_RETRYABLE"
    }

    var isTerminalFailure: Bool {
        jobStatus == "FAILED_TERMINAL"
    }
}

struct CanonicalProblemResult: Equatable, Sendable {
    let canonicalProblemId: UUID
    let problemSessionId: UUID
    let problemParseId: UUID
    let problemParseRevision: Int
    let canonicalRevision: Int
    let schemaVersion: String
    let verifierSchemaVersion: String
    let problemType: String
    let taskType: String
    let normalizedText: String?
    let displayLatex: String?
    let variables: [String]
    let sourceConstraintCount: Int
    let derivedRestrictionCount: Int
    let createdAt: Date
}

struct ProblemClassificationResult: Equatable, Sendable {
    let classificationJobId: UUID?
    let problemSessionId: UUID
    let canonicalProblemId: UUID
    let canonicalProblemRevision: Int
    let jobStatus: String
    let capability: String
    let attemptCount: Int
    let maxAttempts: Int
    let lastErrorCode: String?
    let lastFailureClass: String?
    let classificationId: UUID?
    let classificationRevision: Int?
    let classificationSource: String?
    let classificationStatus: String?
    let reviewReason: String?
    let subjectId: String?
    let topicId: String?
    let primarySkillId: String?
    let secondarySkillIds: [String]
    let difficulty: String?
    let confidenceBand: String?
    let confidenceCalibration: String?
    let provider: String?
    let model: String?
    let fallbackUsed: Bool?
    let ontologyVersion: String
    let projectionVersion: String
    let schemaVersion: String
    let difficultyPolicyVersion: String
    let confidencePolicyVersion: String
    let createdAt: Date?
    let updatedAt: Date?
    let completedAt: Date?
    let classificationCreatedAt: Date?

    var isTerminalSuccess: Bool {
        jobStatus == "SUCCEEDED"
    }

    var isInProgress: Bool {
        jobStatus == "NOT_STARTED" || jobStatus == "QUEUED" || jobStatus == "RUNNING"
    }

    var isRetryableFailure: Bool {
        jobStatus == "FAILED_RETRYABLE"
    }

    var isTerminalFailure: Bool {
        jobStatus == "FAILED_TERMINAL"
    }

    var isClassified: Bool {
        classificationStatus == "CLASSIFIED"
    }

    var needsReview: Bool {
        classificationStatus == "REVIEW_REQUIRED" || classificationStatus == "UNKNOWN"
    }

    var isUnsupported: Bool {
        classificationStatus == "UNSUPPORTED"
    }
}

struct NormalizedProblemParse: Equatable, Sendable {
    let schemaVersion: String
    let supportStatus: String
    let unsupportedReason: String?
    let subjectId: String?
    let topicId: String?
    let taskType: String?
    let problemType: String?
    let expressions: [ProblemParseExpression]
    let variables: [ProblemParseVariable]
    let constraints: [ProblemParseConstraint]
    let assumptions: [ProblemParseAssumption]
    let uncertainty: ProblemParseUncertainty
    let sourceEvidenceRefs: [ProblemParseSourceEvidenceRef]
    let visualQualityRisks: [ProblemParseVisualQualityRisk]
    let reviewRequired: Bool
}

struct ProblemParseExpression: Equatable, Identifiable, Sendable {
    let id: String
    let role: String
    let sourceText: String
    let normalizedText: String
    let displayLatex: String?
    let relation: String?
    let sourceBlockIds: [String]
}

struct ProblemParseVariable: Equatable, Identifiable, Sendable {
    let symbol: String
    let role: String
    let sourceBlockIds: [String]

    var id: String {
        symbol
    }
}

struct ProblemParseConstraint: Equatable, Identifiable, Sendable {
    let id: String
    let sourceText: String
    let normalizedText: String
    let variables: [String]
    let explicit: Bool
    let sourceBlockIds: [String]
}

struct ProblemParseAssumption: Equatable, Identifiable, Sendable {
    let id: String
    let text: String
    let explicit: Bool
    let sourceBlockIds: [String]
}

struct ProblemParseUncertainty: Equatable, Sendable {
    let recognition: [String]
    let parse: [String]
    let reviewRequired: Bool
}

struct ProblemParseSourceEvidenceRef: Equatable, Sendable {
    let blockId: String
    let fieldPath: String
}

struct ProblemParseVisualQualityRisk: Equatable, Sendable {
    let signalType: String
    let severity: String
    let messageCode: String?
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
