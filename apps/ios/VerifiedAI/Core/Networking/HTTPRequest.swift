import Foundation

struct HTTPRequest<Response: Decodable> {
    let endpoint: Endpoint
    let body: Data?
    let idempotencyKey: String?
    let allowsAuthRefreshRetry: Bool
    let timeout: TimeInterval

    init(endpoint: Endpoint, body: Data? = nil, idempotencyKey: String? = nil, allowsAuthRefreshRetry: Bool? = nil, timeout: TimeInterval = 60) {
        self.timeout = timeout
        self.endpoint = endpoint
        self.body = body
        self.idempotencyKey = idempotencyKey
        self.allowsAuthRefreshRetry = allowsAuthRefreshRetry ?? endpoint.method.allowsAutomaticAuthRefreshRetry
    }
}
