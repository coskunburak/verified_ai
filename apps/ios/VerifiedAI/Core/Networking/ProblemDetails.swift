import Foundation

struct ProblemDetails: Decodable, Equatable {
    let type: String
    let title: String
    let status: Int
    let code: String
    let traceId: String?
    let details: RecoveryDetails?

    struct RecoveryDetails: Decodable, Equatable {
        let recoverable: Bool?
        let userAction: String?
    }
}

