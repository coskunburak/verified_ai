import Foundation

enum ProblemSessionStatus: String, CaseIterable, Sendable {
    case created = "CREATED"
    case assetUploaded = "ASSET_UPLOADED"
    case parsing = "PARSING"
    case parsed = "PARSED"
    case solving = "SOLVING"
    case verifying = "VERIFYING"
    case completed = "COMPLETED"
    case reviewRequired = "REVIEW_REQUIRED"
    case failed = "FAILED"
    case cancelled = "CANCELLED"
}

enum ProblemSessionStage: String, CaseIterable, Sendable {
    case awaitingUpload = "AWAITING_UPLOAD"
    case preprocessing = "PREPROCESSING"
    case recognition = "RECOGNITION"
    case parsing = "PARSING"
    case parseReview = "PARSE_REVIEW"
    case canonicalization = "CANONICALIZATION"
    case classification = "CLASSIFICATION"
    case readyForSolve = "READY_FOR_SOLVE"
    case terminal = "TERMINAL"
}

enum ProblemSessionNextAction: String, CaseIterable, Sendable {
    case none = "NONE"
    case resumeUpload = "RESUME_UPLOAD"
    case startPreprocessing = "START_PREPROCESSING"
    case retryPreprocessing = "RETRY_PREPROCESSING"
    case startRecognition = "START_RECOGNITION"
    case waitRecognition = "WAIT_RECOGNITION"
    case retryRecognition = "RETRY_RECOGNITION"
    case startParse = "START_PARSE"
    case waitParse = "WAIT_PARSE"
    case retryParse = "RETRY_PARSE"
    case reviewParse = "REVIEW_PARSE"
    case canonicalize = "CANONICALIZE"
    case startClassification = "START_CLASSIFICATION"
    case waitClassification = "WAIT_CLASSIFICATION"
    case retryClassification = "RETRY_CLASSIFICATION"
    case readyForSolve = "READY_FOR_SOLVE"
    case recaptureOrReimport = "RECAPTURE_OR_REIMPORT"
    case unsupported = "UNSUPPORTED"
}

enum ProblemSessionActiveJobType: String, Sendable {
    case recognition = "RECOGNITION"
    case parse = "PARSE"
    case classification = "CLASSIFICATION"
}

struct ProblemSessionHistoryPage: Equatable, Sendable {
    let items: [ProblemSessionHistoryItem]
    let nextCursor: String?
}

struct ProblemSessionHistoryItem: Identifiable, Equatable, Sendable {
    var id: UUID { problemSessionId }

    let problemSessionId: UUID
    let status: ProblemSessionStatus
    let stage: ProblemSessionStage
    let inputMode: String
    let nextAction: ProblemSessionNextAction
    let retryable: Bool
    let reviewRequired: Bool
    let currentParseRevision: Int?
    let currentParseSource: String?
    let classificationStatus: String?
    let primarySkillId: String?
    let difficulty: String?
    let createdAt: Date
    let updatedAt: Date
    let completedAt: Date?

    var title: String {
        if let primarySkillId, !primarySkillId.isEmpty {
            return primarySkillId.replacingOccurrences(of: "_", with: " ")
        }
        if let currentParseRevision {
            return "Parse revision \(currentParseRevision)"
        }
        return inputMode.replacingOccurrences(of: "_", with: " ").capitalized
    }

    var subtitle: String {
        if let difficulty, let classificationStatus {
            return "\(classificationStatus) · \(difficulty)"
        }
        if let classificationStatus {
            return classificationStatus
        }
        return stage.label
    }
}

struct ProblemSessionDetail: Equatable, Sendable {
    let problemSessionId: UUID
    let status: ProblemSessionStatus
    let stage: ProblemSessionStage
    let inputMode: String
    let nextAction: ProblemSessionNextAction
    let retryable: Bool
    let reviewRequired: Bool
    let failureCode: String?
    let currentParse: ProblemSessionCurrentParseSummary?
    let canonicalProblem: ProblemSessionCanonicalSummary?
    let classification: ProblemSessionClassificationSummary?
    let activeJob: ProblemSessionActiveJob?
    let createdAt: Date
    let updatedAt: Date
    let completedAt: Date?
    let version: Int64

    var historyItem: ProblemSessionHistoryItem {
        ProblemSessionHistoryItem(
            problemSessionId: problemSessionId,
            status: status,
            stage: stage,
            inputMode: inputMode,
            nextAction: nextAction,
            retryable: retryable,
            reviewRequired: reviewRequired,
            currentParseRevision: currentParse?.revision,
            currentParseSource: currentParse?.source,
            classificationStatus: classification?.status,
            primarySkillId: classification?.primarySkillId,
            difficulty: classification?.difficulty,
            createdAt: createdAt,
            updatedAt: updatedAt,
            completedAt: completedAt
        )
    }
}

struct ProblemSessionCurrentParseSummary: Equatable, Sendable {
    let problemParseId: UUID
    let revision: Int
    let source: String
    let supportStatus: String
    let reviewRequired: Bool
}

struct ProblemSessionCanonicalSummary: Equatable, Sendable {
    let canonicalProblemId: UUID
    let revision: Int
    let problemParseId: UUID
    let problemParseRevision: Int
    let problemType: String
    let taskType: String
}

struct ProblemSessionClassificationSummary: Equatable, Sendable {
    let classificationId: UUID
    let revision: Int
    let status: String
    let primarySkillId: String?
    let difficulty: String?
    let reviewReason: String?
}

struct ProblemSessionActiveJob: Equatable, Sendable {
    let type: ProblemSessionActiveJobType
    let id: UUID
    let status: String
    let attemptCount: Int
    let maxAttempts: Int
    let failureCode: String?
}

extension ProblemSessionStage {
    var label: String {
        switch self {
        case .awaitingUpload:
            "Awaiting upload"
        case .preprocessing:
            "Preprocessing"
        case .recognition:
            "Reading"
        case .parsing:
            "Understanding"
        case .parseReview:
            "Needs review"
        case .canonicalization:
            "Preparing verification"
        case .classification:
            "Classifying"
        case .readyForSolve:
            "Ready"
        case .terminal:
            "Stopped"
        }
    }
}

extension ProblemSessionNextAction {
    var buttonTitle: String {
        switch self {
        case .startRecognition, .retryRecognition:
            "Read Problem"
        case .startParse, .retryParse:
            "Understand Problem"
        case .reviewParse:
            "Review Parse"
        case .canonicalize:
            "Prepare Verification"
        case .startClassification, .retryClassification:
            "Classify"
        case .readyForSolve:
            "Continue"
        case .resumeUpload, .startPreprocessing, .retryPreprocessing, .recaptureOrReimport:
            "Recapture"
        case .waitRecognition, .waitParse, .waitClassification:
            "Refresh"
        case .unsupported, .none:
            "Details"
        }
    }

    var isRefreshOnly: Bool {
        switch self {
        case .waitRecognition, .waitParse, .waitClassification:
            true
        default:
            false
        }
    }
}
