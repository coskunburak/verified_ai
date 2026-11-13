import Foundation

enum HTTPMethod: String, Equatable {
    case get = "GET"
    case post = "POST"
    case put = "PUT"
    case patch = "PATCH"
    case delete = "DELETE"

    var allowsAutomaticAuthRefreshRetry: Bool {
        self == .get
    }
}
