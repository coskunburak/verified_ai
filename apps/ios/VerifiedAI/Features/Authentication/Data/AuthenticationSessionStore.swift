import Foundation

actor AuthenticationSessionStore: AuthTokenProvider {
    typealias RefreshHandler = @Sendable (String) async throws -> AuthSession

    private enum Key {
        static let userId = "auth.userId"
        static let sessionId = "auth.sessionId"
        static let accessToken = "accessToken"
        static let accessTokenExpiresAt = "auth.accessTokenExpiresAt"
        static let refreshToken = "refreshToken"
        static let refreshTokenExpiresAt = "auth.refreshTokenExpiresAt"
    }

    private let secureStorage: SecureStorage
    private let dateFormatter: ISO8601DateFormatter
    private var refreshHandler: RefreshHandler?
    private var refreshTask: Task<AuthSession, Error>?

    init(secureStorage: SecureStorage) {
        self.secureStorage = secureStorage
        self.dateFormatter = ISO8601DateFormatter()
        self.dateFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    }

    func setRefreshHandler(_ handler: @escaping RefreshHandler) {
        self.refreshHandler = handler
    }

    func accessToken() async throws -> String? {
        try secureStorage.string(forKey: Key.accessToken)
    }

    func refreshAccessToken() async throws -> String? {
        if let refreshTask {
            let session = try await refreshTask.value
            try save(session)
            return session.accessToken
        }

        guard let refreshToken = try secureStorage.string(forKey: Key.refreshToken),
              let refreshHandler else {
            return nil
        }

        let task = Task<AuthSession, Error> {
            try await refreshHandler(refreshToken)
        }
        refreshTask = task
        defer { refreshTask = nil }

        do {
            let session = try await task.value
            try save(session)
            return session.accessToken
        } catch {
            try clear()
            throw error
        }
    }

    func save(_ session: AuthSession) throws {
        try secureStorage.setString(session.userId.uuidString, forKey: Key.userId)
        try secureStorage.setString(session.sessionId.uuidString, forKey: Key.sessionId)
        try secureStorage.setString(session.accessToken, forKey: Key.accessToken)
        try secureStorage.setString(dateFormatter.string(from: session.accessTokenExpiresAt), forKey: Key.accessTokenExpiresAt)
        try secureStorage.setString(session.refreshToken, forKey: Key.refreshToken)
        try secureStorage.setString(dateFormatter.string(from: session.refreshTokenExpiresAt), forKey: Key.refreshTokenExpiresAt)
    }

    func loadSession() throws -> AuthSession? {
        guard
            let userIdValue = try secureStorage.string(forKey: Key.userId),
            let sessionIdValue = try secureStorage.string(forKey: Key.sessionId),
            let accessToken = try secureStorage.string(forKey: Key.accessToken),
            let accessTokenExpiresAtValue = try secureStorage.string(forKey: Key.accessTokenExpiresAt),
            let refreshToken = try secureStorage.string(forKey: Key.refreshToken),
            let refreshTokenExpiresAtValue = try secureStorage.string(forKey: Key.refreshTokenExpiresAt),
            let userId = UUID(uuidString: userIdValue),
            let sessionId = UUID(uuidString: sessionIdValue),
            let accessTokenExpiresAt = dateFormatter.date(from: accessTokenExpiresAtValue),
            let refreshTokenExpiresAt = dateFormatter.date(from: refreshTokenExpiresAtValue)
        else {
            return nil
        }

        return AuthSession(
            userId: userId,
            sessionId: sessionId,
            accessToken: accessToken,
            accessTokenExpiresAt: accessTokenExpiresAt,
            refreshToken: refreshToken,
            refreshTokenExpiresAt: refreshTokenExpiresAt
        )
    }

    func clear() throws {
        try secureStorage.removeValue(forKey: Key.userId)
        try secureStorage.removeValue(forKey: Key.sessionId)
        try secureStorage.removeValue(forKey: Key.accessToken)
        try secureStorage.removeValue(forKey: Key.accessTokenExpiresAt)
        try secureStorage.removeValue(forKey: Key.refreshToken)
        try secureStorage.removeValue(forKey: Key.refreshTokenExpiresAt)
    }
}
