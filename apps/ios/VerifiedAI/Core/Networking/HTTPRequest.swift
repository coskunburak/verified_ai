import Foundation

struct HTTPRequest<Response: Decodable> {
    let endpoint: Endpoint
    let body: Data?
    let idempotencyKey: String?
    let allowsAuthRefreshRetry: Bool

    init(endpoint: Endpoint, body: Data? = nil, idempotencyKey: String? = nil, allowsAuthRefreshRetry: Bool? = nil) {
        self.endpoint = endpoint
        self.body = body
        self.idempotencyKey = idempotencyKey
        self.allowsAuthRefreshRetry = allowsAuthRefreshRetry ?? endpoint.method.allowsAutomaticAuthRefreshRetry
    }
}
