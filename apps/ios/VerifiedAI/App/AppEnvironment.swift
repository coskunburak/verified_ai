import Foundation

enum AppEnvironment: String, CaseIterable, Equatable {
    case development
    case staging
    case production

    static func current(bundle: Bundle = .main, processInfo: ProcessInfo = .processInfo) -> AppEnvironment {
        if processInfo.arguments.contains("--ui-testing") {
            return .development
        }

        let configured = bundle.object(forInfoDictionaryKey: "APP_ENVIRONMENT") as? String
        return configured.flatMap(AppEnvironment.init(rawValue:)) ?? .development
    }

    static func resolvedAPIBaseURL(configuredValue: String?, environment: AppEnvironment) -> URL {
        if let configuredValue,
           let configuredURL = URL(string: configuredValue),
           configuredURL.scheme != nil,
           configuredURL.host != nil {
            return configuredURL
        }
        return environment.apiBaseURL
    }

    static func apiBaseURL(bundle: Bundle = .main, environment: AppEnvironment) -> URL {
        resolvedAPIBaseURL(
            configuredValue: bundle.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
            environment: environment
        )
    }

    var apiBaseURL: URL {
        switch self {
        case .development:
            return URL(string: "http://localhost:8080")!
        case .staging:
            return URL(string: "https://staging-api.verified-ai-learning.example")!
        case .production:
            return URL(string: "https://api.verified-ai-learning.example")!
        }
    }

    var displayName: String {
        rawValue.capitalized
    }
}
