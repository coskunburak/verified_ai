import Foundation

protocol AuthenticationServicing: Sendable {
    func exchangeAppleCredential(identityToken: String, authorizationCode: String?, nonce: String) async throws -> AuthSession
    func signUpWithEmail(email: String, password: String) async throws -> AuthSession
    func signInWithEmail(email: String, password: String) async throws -> AuthSession
    func continueAsGuest() async throws -> AuthSession
    func refresh(refreshToken: String) async throws -> AuthSession
    func logout() async throws
}

final class AuthenticationAPI: AuthenticationServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func exchangeAppleCredential(identityToken: String, authorizationCode: String?, nonce: String) async throws -> AuthSession {
        let body = try encoder.encode(AppleSignInRequest(identityToken: identityToken, authorizationCode: authorizationCode, nonce: nonce))
        let response: HTTPResponse<AuthSessionWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/auth/apple", method: .post),
                body: body,
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.session()
    }

    func signUpWithEmail(email: String, password: String) async throws -> AuthSession {
        try await exchangeEmailCredential(path: "/api/v1/auth/email/sign-up", email: email, password: password)
    }

    func signInWithEmail(email: String, password: String) async throws -> AuthSession {
        try await exchangeEmailCredential(path: "/api/v1/auth/email/sign-in", email: email, password: password)
    }

    func continueAsGuest() async throws -> AuthSession {
        let response: HTTPResponse<AuthSessionWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/auth/guest", method: .post),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.session()
    }

    func refresh(refreshToken: String) async throws -> AuthSession {
        let body = try encoder.encode(RefreshSessionRequest(refreshToken: refreshToken))
        let response: HTTPResponse<AuthSessionWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/auth/refresh", method: .post),
                body: body,
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.session()
    }

    func logout() async throws {
        let response: HTTPResponse<EmptyResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/auth/logout", method: .post),
                allowsAuthRefreshRetry: false
            )
        )
        _ = response.statusCode
    }

    private func exchangeEmailCredential(path: String, email: String, password: String) async throws -> AuthSession {
        let body = try encoder.encode(EmailAuthRequest(email: email, password: password))
        let response: HTTPResponse<AuthSessionWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: path, method: .post),
                body: body,
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.session()
    }
}

private struct AppleSignInRequest: Encodable {
    let identityToken: String
    let authorizationCode: String?
    let nonce: String
}

private struct EmailAuthRequest: Encodable {
    let email: String
    let password: String
}

private struct RefreshSessionRequest: Encodable {
    let refreshToken: String
}

private struct AuthSessionWireResponse: Decodable {
    let userId: UUID
    let sessionId: UUID
    let accessToken: String
    let accessTokenExpiresAt: String
    let refreshToken: String
    let refreshTokenExpiresAt: String

    func session() throws -> AuthSession {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let accessExpiry = formatter.date(from: accessTokenExpiresAt),
              let refreshExpiry = formatter.date(from: refreshTokenExpiresAt) else {
            throw AuthenticationClientError.server("invalid_date")
        }
        return AuthSession(
            userId: userId,
            sessionId: sessionId,
            accessToken: accessToken,
            accessTokenExpiresAt: accessExpiry,
            refreshToken: refreshToken,
            refreshTokenExpiresAt: refreshExpiry
        )
    }
}

private struct EmptyResponse: Decodable {}
