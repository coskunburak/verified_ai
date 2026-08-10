import AuthenticationServices
import CryptoKit
import Foundation
import Observation
import Security

@MainActor
@Observable
final class SignInViewModel {
    private let authenticationAPI: AuthenticationServicing
    private let sessionStore: AuthenticationSessionStore
    private let networkMonitor: NetworkMonitoring
    private let logger: AppLogger
    private var currentNonce: String?

    private(set) var state: AuthenticationState = .unknown
    private(set) var message: String?
    var mode: EmailAuthenticationMode = .signIn {
        didSet {
            message = nil
            if case .failed = state {
                state = .unauthenticated
            }
        }
    }
    var email = ""
    var password = ""
    var confirmPassword = ""

    init(
        authenticationAPI: AuthenticationServicing,
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
            message = nil
            return
        }
        #endif

        do {
            if let session = try await sessionStore.loadSession() {
                state = session.refreshTokenExpiresAt > Date() ? .authenticated(session) : .expired
            } else {
                state = .unauthenticated
            }
            message = nil
        } catch {
            logger.warning("auth.restore_failed")
            state = .unauthenticated
            message = nil
        }
    }

    func submitEmail() {
        Task {
            await authenticateWithEmail()
        }
    }

    func continueAsGuest() {
        Task {
            await createGuestSession()
        }
    }

    func configureAppleRequest(_ request: ASAuthorizationAppleIDRequest) {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Connection unavailable. Check your network and try again."
            return
        }

        do {
            let nonce = try NonceGenerator.randomNonceString()
            currentNonce = nonce
            request.requestedScopes = [.fullName, .email]
            request.nonce = NonceGenerator.sha256(nonce)
            state = .authorizing
            message = nil
            logger.info("auth.apple.started")
        } catch {
            logger.error("auth.apple.nonce_failed")
            state = .failed(.unknown("nonce"))
            message = "Secure sign-in could not start."
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
            clearCredentials()
            state = .unauthenticated
            message = nil
        }
    }

    func discardLocalSession() {
        currentNonce = nil
        Task {
            try? await sessionStore.clear()
        }
        state = .unauthenticated
        message = nil
    }

    private func authenticateWithEmail() async {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Connection unavailable. Check your network and try again."
            logger.warning("auth.email.offline")
            return
        }
        guard validateEmailForm() else {
            return
        }

        state = .exchangingCredential
        message = nil
        logger.info(mode == .signIn ? "auth.email.sign_in.started" : "auth.email.sign_up.started")

        do {
            let session: AuthSession
            switch mode {
            case .signIn:
                session = try await authenticationAPI.signInWithEmail(email: email.trimmingCharacters(in: .whitespacesAndNewlines), password: password)
            case .signUp:
                session = try await authenticationAPI.signUpWithEmail(email: email.trimmingCharacters(in: .whitespacesAndNewlines), password: password)
            }
            try await sessionStore.save(session)
            clearCredentials()
            state = .authenticated(session)
            logger.info(mode == .signIn ? "auth.email.sign_in.success" : "auth.email.sign_up.success")
        } catch NetworkError.server(let problem) {
            logger.warning("auth.email.server_failed")
            state = .failed(.server(problem.code))
            message = Self.message(for: problem.code)
        } catch {
            logger.warning("auth.email.failed")
            state = .failed(.unknown("email"))
            message = "We could not create a secure session. Try again."
        }
    }

    private func createGuestSession() async {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Connection unavailable. Check your network and try again."
            logger.warning("auth.guest.offline")
            return
        }

        state = .exchangingCredential
        message = nil
        logger.info("auth.guest.started")

        do {
            let session = try await authenticationAPI.continueAsGuest()
            try await sessionStore.save(session)
            clearCredentials()
            state = .authenticated(session)
            logger.info("auth.guest.success")
        } catch NetworkError.server(let problem) {
            logger.warning("auth.guest.server_failed")
            state = .failed(.server(problem.code))
            message = Self.message(for: problem.code)
        } catch {
            logger.warning("auth.guest.failed")
            state = .failed(.unknown("guest"))
            message = "Guest access could not start. Try again."
        }
    }

    private func exchangeAppleCompletion(_ result: Result<ASAuthorization, Error>) async {
        switch result {
        case .failure(let error):
            if let authorizationError = error as? ASAuthorizationError, authorizationError.code == .canceled {
                logger.info("auth.apple.client_cancelled")
                state = .cancelled
                message = "Apple sign-in was cancelled."
            } else {
                logger.warning("auth.apple.failed")
                state = .failed(.unknown("apple_authorization"))
                message = "Apple sign-in could not complete."
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
                message = "Apple returned an invalid credential."
                return
            }

            let authorizationCode = credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
            currentNonce = nil
            state = .exchangingCredential
            message = nil
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
                message = nil
            } catch NetworkError.server(let problem) {
                logger.warning("auth.apple.exchange_failure")
                state = .failed(.server(problem.code))
                message = Self.message(for: problem.code)
            } catch {
                logger.warning("auth.apple.exchange_failure")
                state = .failed(.unknown("exchange"))
                message = "Apple sign-in could not create a session."
            }
        }
    }

    private func validateEmailForm() -> Bool {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmedEmail.contains("@"), trimmedEmail.contains(".") else {
            state = .failed(.invalidCredential)
            message = "Enter a valid email address."
            return false
        }
        guard Self.isAcceptablePassword(password) else {
            state = .failed(.invalidCredential)
            message = "Use at least 8 characters with a letter and a number."
            return false
        }
        if mode == .signUp, password != confirmPassword {
            state = .failed(.invalidCredential)
            message = "Passwords do not match."
            return false
        }
        return true
    }

    private func clearCredentials() {
        password = ""
        confirmPassword = ""
    }

    private static func isAcceptablePassword(_ value: String) -> Bool {
        guard value.count >= 8, value.count <= 128, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return false
        }
        let hasLetter = value.rangeOfCharacter(from: .letters) != nil
        let hasNumber = value.rangeOfCharacter(from: .decimalDigits) != nil
        return hasLetter && hasNumber
    }

    private static func message(for code: String) -> String {
        switch code {
        case "AUTH_EMAIL_ALREADY_REGISTERED":
            return "That email already has an account. Sign in instead."
        case "AUTH_CREDENTIALS_INVALID":
            return "Email or password is invalid."
        case "AUTH_PASSWORD_REJECTED":
            return "Use at least 8 characters with a letter and a number."
        case "REQUEST_VALIDATION_FAILED":
            return "Check the email and password fields."
        case "RATE_LIMIT_EXCEEDED":
            return "Too many attempts. Wait a moment and try again."
        default:
            return "Authentication failed. Try again."
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
