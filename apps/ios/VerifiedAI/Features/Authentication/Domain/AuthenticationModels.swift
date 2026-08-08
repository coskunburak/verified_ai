import Foundation

struct AuthSession: Codable, Equatable, Sendable {
    let userId: UUID
    let sessionId: UUID
    let accessToken: String
    let accessTokenExpiresAt: Date
    let refreshToken: String
    let refreshTokenExpiresAt: Date
}

enum AuthenticationState: Equatable {
    case unknown
    case unauthenticated
    case authorizing
    case exchangingCredential
    case authenticated(AuthSession)
    case refreshing
    case expired
    case cancelled
    case offline
    case failed(AuthenticationClientError)
}

enum AuthenticationClientError: Error, Equatable, Sendable {
    case cancelled
    case offline
    case invalidCredential
    case server(String)
    case refreshFailed
    case storageFailed
    case unknown(String)
}
