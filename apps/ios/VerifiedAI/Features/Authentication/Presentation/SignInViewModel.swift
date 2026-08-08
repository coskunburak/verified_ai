import AuthenticationServices
import CryptoKit
import Foundation
import Observation
import Security

@MainActor
@Observable
final class SignInViewModel {
    private let authenticationAPI: AuthenticationAPI
    private let sessionStore: AuthenticationSessionStore
    private let networkMonitor: NetworkMonitoring
    private let logger: AppLogger
    private var currentNonce: String?

    private(set) var state: AuthenticationState = .unknown

    init(
        authenticationAPI: AuthenticationAPI,
        sessionStore: AuthenticationSessionStore,
        networkMonitor: NetworkMonitoring,
        logger: AppLogger
    ) {
        self.authenticationAPI = authenticationAPI
        self.sessionStore = sessionStore
        self.networkMonitor = networkMonitor
        self.logger = logger
    }

    func restore() async {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-testing-authenticated") {
            state = .authenticated(AuthSession(
                userId: UUID(uuidString: "00000000-0000-0000-0000-0000000000A1")!,
                sessionId: UUID(uuidString: "00000000-0000-0000-0000-0000000000A2")!,
                accessToken: "ui-testing-access-token",
                accessTokenExpiresAt: Date().addingTimeInterval(3_600),
                refreshToken: "ui-testing-refresh-token",
                refreshTokenExpiresAt: Date().addingTimeInterval(86_400)
            ))
            return
        }
        #endif

        do {
            if let session = try await sessionStore.loadSession() {
                state = session.refreshTokenExpiresAt > Date() ? .authenticated(session) : .expired
            } else {
                state = .unauthenticated
            }
        } catch {
            logger.warning("auth.restore_failed")
            state = .unauthenticated
        }
    }

    func configureAppleRequest(_ request: ASAuthorizationAppleIDRequest) {
        guard networkMonitor.isReachable else {
            state = .offline
            return
        }

        do {
            let nonce = try NonceGenerator.randomNonceString()
            currentNonce = nonce
            request.requestedScopes = [.fullName, .email]
            request.nonce = NonceGenerator.sha256(nonce)
            state = .authorizing
            logger.info("auth.apple.started")
        } catch {
            logger.error("auth.apple.nonce_failed")
            state = .failed(.unknown("nonce"))
        }
    }

    func handleAppleCompletion(_ result: Result<ASAuthorization, Error>) {
        Task {
            await exchangeAppleCompletion(result)
        }
    }

    func logout() {
        Task {
            do {
                try await authenticationAPI.logout()
            } catch {
                logger.warning("auth.logout.server_failed")
            }
            try? await sessionStore.clear()
            state = .unauthenticated
        }
    }

    func discardLocalSession() {
        currentNonce = nil
        Task {
            try? await sessionStore.clear()
        }
        state = .unauthenticated
    }

    private func exchangeAppleCompletion(_ result: Result<ASAuthorization, Error>) async {
        switch result {
        case .failure(let error):
            if let authorizationError = error as? ASAuthorizationError, authorizationError.code == .canceled {
                logger.info("auth.apple.client_cancelled")
                state = .cancelled
            } else {
                logger.warning("auth.apple.failed")
                state = .failed(.unknown("apple_authorization"))
            }
        case .success(let authorization):
            guard
                let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                let identityTokenData = credential.identityToken,
                let identityToken = String(data: identityTokenData, encoding: .utf8),
                let nonce = currentNonce
            else {
                logger.warning("auth.apple.invalid_credential")
                state = .failed(.invalidCredential)
                return
            }

            let authorizationCode = credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
            currentNonce = nil
            state = .exchangingCredential
            logger.info("auth.apple.credential_received")

            do {
                let session = try await authenticationAPI.exchangeAppleCredential(
                    identityToken: identityToken,
                    authorizationCode: authorizationCode,
                    nonce: nonce
                )
                try await sessionStore.save(session)
                logger.info("auth.apple.exchange_success")
                state = .authenticated(session)
            } catch NetworkError.server(let problem) {
                logger.warning("auth.apple.exchange_failure")
                state = .failed(.server(problem.code))
            } catch {
                logger.warning("auth.apple.exchange_failure")
                state = .failed(.unknown("exchange"))
            }
        }
    }
}

enum NonceGenerator {
    private static let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")

    static func randomNonceString(length: Int = 32) throws -> String {
        precondition(length > 0)
        var bytes = [UInt8](repeating: 0, count: length)
        let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        guard status == errSecSuccess else {
            throw AuthenticationClientError.unknown("secure_random")
        }
        return String(bytes.map { charset[Int($0) % charset.count] })
    }

    static func sha256(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
