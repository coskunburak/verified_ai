import Foundation

struct HTTPRequest<Response: Decodable> {
    let endpoint: Endpoint
    let body: Data?
    let idempotencyKey: String?

    init(endpoint: Endpoint, body: Data? = nil, idempotencyKey: String? = nil) {
        self.endpoint = endpoint
        self.body = body
        self.idempotencyKey = idempotencyKey
    }
}

