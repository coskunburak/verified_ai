import Foundation

struct RequestInterceptor {
    let tokenProvider: AuthTokenProvider
    let correlationContext: CorrelationContext

    func intercept(_ request: inout URLRequest, idempotencyKey: String?) async throws {
        request.setValue(correlationContext.requestId(), forHTTPHeaderField: "X-Request-Id")
        if let idempotencyKey {
            request.setValue(idempotencyKey, forHTTPHeaderField: "Idempotency-Key")
        }
        if let token = try await tokenProvider.accessToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
    }
}

