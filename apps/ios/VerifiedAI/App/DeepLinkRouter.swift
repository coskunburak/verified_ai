import Foundation

protocol DeepLinkRouting: Sendable {
    func route(for url: URL) -> AppRoute?
}

struct DeepLinkRouter: DeepLinkRouting {
    func route(for url: URL) -> AppRoute? {
        guard url.scheme == "verified-ai" else { return nil }

        let raw = url.absoluteString
        if raw.contains("..") || raw.lowercased().contains("%2e") {
            return nil
        }
        guard url.user == nil, url.password == nil, url.port == nil else {
            return nil
        }

        let normalizedPath = url.path == "/" ? "" : url.path
        switch (url.host, normalizedPath) {
        case ("home", ""):
            return .home
        case ("platform-health", ""):
            return .platformHealth
        case ("notifications", ""), ("notification-preferences", ""):
            return .notificationPreferences
        case ("study-plan", "/today"):
            return .studyPlanToday
        case ("spaced-review", ""):
            return .spacedReview
        case ("exam-readiness", ""):
            return .examReadiness
        case ("subscription", "/manage"):
            return .subscriptionManage
        case ("account", "/security"):
            return .accountSecurity
        case ("share", let path):
            guard path.hasPrefix("/") else { return nil }
            let token = String(path.dropFirst())
            guard !token.isEmpty,
                  !token.contains("/"),
                  Self.matchesRegex(token, pattern: "^[A-Za-z0-9_-]{16,48}$") else {
                return nil
            }
            return .sharedArtifact(token: token)
        case ("referral", let path):
            guard path.hasPrefix("/") else { return nil }
            let code = String(path.dropFirst())
            guard !code.isEmpty,
                  !code.contains("/"),
                  Self.matchesRegex(code, pattern: "^[A-Z2-9]{6,16}$") else {
                return nil
            }
            return .referralInvite(code: code)
        default:
            return nil
        }
    }

    private static func matchesRegex(_ value: String, pattern: String) -> Bool {
        guard let range = value.range(of: pattern, options: .regularExpression) else {
            return false
        }
        return range.lowerBound == value.startIndex && range.upperBound == value.endIndex
    }
}
