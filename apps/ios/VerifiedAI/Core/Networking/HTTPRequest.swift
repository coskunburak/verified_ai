import Foundation

struct HTTPRequest<Response: Decodable> {
    let endpoint: Endpoint
    let body: Data?
    let contentType: String
    let idempotencyKey: String?
    let allowsAuthRefreshRetry: Bool
    let timeout: TimeInterval
    let decodesISO8601Dates: Bool

    init(
        endpoint: Endpoint,
        body: Data? = nil,
        contentType: String = "application/json",
        idempotencyKey: String? = nil,
        allowsAuthRefreshRetry: Bool? = nil,
        timeout: TimeInterval = 60,
        decodesISO8601Dates: Bool = false
    ) {
        self.timeout = timeout
        self.decodesISO8601Dates = decodesISO8601Dates
        self.endpoint = endpoint
        self.body = body
        self.contentType = contentType
        self.idempotencyKey = idempotencyKey
        self.allowsAuthRefreshRetry = allowsAuthRefreshRetry ?? endpoint.method.allowsAutomaticAuthRefreshRetry
    }
}
