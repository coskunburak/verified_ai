import Foundation

enum ProblemReviewState: Equatable, Sendable {
    case idle
    case loading
    case ready
    case saving
    case saved(ProblemParseCorrectionOutcome)
    case failed(String)

    var isBusy: Bool {
        switch self {
        case .loading, .saving:
            true
        default:
            false
        }
    }
}

enum ProblemReviewSelection: String, CaseIterable, Identifiable, Sendable {
    case parse
    case details
    case revisions

    var id: String {
        rawValue
    }

    var title: String {
        switch self {
        case .parse:
            "Parse"
        case .details:
            "Details"
        case .revisions:
            "Revisions"
        }
    }
}
