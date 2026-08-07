import Foundation

protocol DeepLinkRouting {
    func route(for url: URL) -> AppRoute?
}

struct DeepLinkRouter: DeepLinkRouting {
    func route(for url: URL) -> AppRoute? {
        guard url.scheme == "verified-ai" else { return nil }

        switch url.host {
        case "home":
            return .home
        case "platform-health":
            return .platformHealth
        default:
            return nil
        }
    }
}

