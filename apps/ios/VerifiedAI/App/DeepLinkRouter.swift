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
        default:
            return nil
        }
    }
}
