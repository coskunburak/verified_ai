import Foundation

struct Endpoint: Equatable {
    let path: String
    let method: HTTPMethod
    let queryItems: [URLQueryItem]

    init(path: String, method: HTTPMethod = .get, queryItems: [URLQueryItem] = []) {
        self.path = path
        self.method = method
        self.queryItems = queryItems
    }

    static let platformHealth = Endpoint(path: "/api/v1/platform/health")
}

