import Foundation

enum NetworkError: Error, Equatable {
    case invalidURL
    case transport(String)
    case invalidResponse
    case server(problem: ProblemDetails)
    case httpStatus(Int)
    case decoding(String)

    var recoverable: Bool {
        switch self {
        case .transport:
            return true
        case .server(let problem):
            return problem.details?.recoverable ?? false
        case .httpStatus(let status):
            return (500...599).contains(status)
        case .invalidURL, .invalidResponse, .decoding:
            return false
        }
    }
}

