import Foundation

enum ProblemParseCorrectionReason: String, CaseIterable, Identifiable, Sendable {
    case ocrTextError = "OCR_TEXT_ERROR"
    case mathExpressionError = "MATH_EXPRESSION_ERROR"
    case variableError = "VARIABLE_ERROR"
    case constraintError = "CONSTRAINT_ERROR"
    case assumptionError = "ASSUMPTION_ERROR"
    case taskTypeError = "TASK_TYPE_ERROR"
    case problemTypeError = "PROBLEM_TYPE_ERROR"
    case other = "OTHER"

    var id: String {
        rawValue
    }

    var title: String {
        switch self {
        case .ocrTextError:
            "OCR text"
        case .mathExpressionError:
            "Expression"
        case .variableError:
            "Variable"
        case .constraintError:
            "Constraint"
        case .assumptionError:
            "Assumption"
        case .taskTypeError:
            "Task type"
        case .problemTypeError:
            "Problem type"
        case .other:
            "Other"
        }
    }
}

struct ProblemParseReview: Equatable, Sendable {
    let problemSessionId: UUID
    let currentParse: ProblemParseCurrent
    let revisionCount: Int
    let canCorrect: Bool
}

struct ProblemParseCurrent: Equatable, Sendable {
    let problemParseId: UUID
    let revision: Int
    let source: String
    let supportStatus: String
    let reviewRequired: Bool
    let normalizedProblem: NormalizedProblemParse
    let createdAt: Date
}

struct ProblemParseCorrectionDraft: Equatable, Sendable {
    let baseParseId: UUID
    let baseRevision: Int
    let localCorrectionId: UUID
    var correctionReason: ProblemParseCorrectionReason
    var problem: NormalizedProblemParse

    init(currentParse: ProblemParseCurrent, localCorrectionId: UUID = UUID()) {
        self.baseParseId = currentParse.problemParseId
        self.baseRevision = currentParse.revision
        self.localCorrectionId = localCorrectionId
        self.correctionReason = .mathExpressionError
        self.problem = currentParse.normalizedProblem
    }

    var idempotencyKey: String {
        "problem-parse-correction-\(localCorrectionId.uuidString)"
    }
}

struct ProblemParseCorrectionOutcome: Equatable, Sendable {
    let problemSessionId: UUID
    let problemParseId: UUID
    let revision: Int
    let source: String
    let parentParseId: UUID
    let selected: Bool
    let supportStatus: String
    let reviewRequired: Bool
    let canonicalizationRequired: Bool
    let createdAt: Date
}

struct ProblemParseRevisionHistory: Equatable, Sendable {
    let problemSessionId: UUID
    let selectedParseId: UUID?
    let revisions: [ProblemParseRevisionEntry]
}

struct ProblemParseRevisionEntry: Equatable, Identifiable, Sendable {
    let problemParseId: UUID
    let revision: Int
    let source: String
    let parentParseId: UUID?
    let selected: Bool
    let supportStatus: String
    let reviewRequired: Bool
    let correctionReason: String?
    let correctedFieldCategories: [String]
    let createdAt: Date

    var id: UUID {
        problemParseId
    }
}
