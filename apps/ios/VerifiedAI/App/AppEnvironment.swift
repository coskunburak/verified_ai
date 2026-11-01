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
        let candidate = ProcessInfo.processInfo.environment["API_BASE_URL"] ?? configuredValue
        #if !targetEnvironment(simulator)
        if let candidate,
           let configuredURL = URL(string: candidate),
           configuredURL.scheme != nil,
           let host = configuredURL.host,
           !host.isEmpty,
           host != "localhost",
           host != "127.0.0.1" {
            return configuredURL
        }
        return URL(string: "http://192.168.1.126:8080")!
        #else
        if let candidate,
           let configuredURL = URL(string: candidate),
           configuredURL.scheme != nil,
           configuredURL.host != nil {
            return configuredURL
        }
        return environment.apiBaseURL
        #endif
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
            #if !targetEnvironment(simulator)
            return URL(string: "http://192.168.1.126:8080")!
            #else
            return URL(string: "http://localhost:8080")!
            #endif
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
