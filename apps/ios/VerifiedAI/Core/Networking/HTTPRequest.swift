import Foundation

struct HTTPRequest<Response: Decodable> {
    let endpoint: Endpoint
    let body: Data?
    let contentType: String
    let idempotencyKey: String?
    let allowsAuthRefreshRetry: Bool
    let timeout: TimeInterval

    init(
        endpoint: Endpoint,
        body: Data? = nil,
        contentType: String = "application/json",
        idempotencyKey: String? = nil,
        allowsAuthRefreshRetry: Bool? = nil,
        timeout: TimeInterval = 60
    ) {
        self.timeout = timeout
        self.endpoint = endpoint
        self.body = body
        self.contentType = contentType
        self.idempotencyKey = idempotencyKey
        self.allowsAuthRefreshRetry = allowsAuthRefreshRetry ?? endpoint.method.allowsAutomaticAuthRefreshRetry
    }
}
