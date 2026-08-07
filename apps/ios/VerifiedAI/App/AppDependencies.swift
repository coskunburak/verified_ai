import Foundation

@MainActor
struct AppDependencies {
    let apiClient: APIClient
    let secureStorage: SecureStorage
    let logger: AppLogger
    let networkMonitor: NetworkMonitoring
    let router: AppRouter
    let deepLinkRouter: DeepLinkRouting
    let correlationContext: CorrelationContext

    static func live(environment: AppEnvironment) -> AppDependencies {
        let correlationContext = CorrelationContext()
        let logger = AppLogger(subsystem: "com.verifiedai.learning", category: "app")
        let secureStorage = KeychainStore(service: "com.verifiedai.learning")
        let networkMonitor = NetworkMonitor()
        let apiClient = APIClient(
            baseURL: environment.apiBaseURL,
            session: URLSession.shared,
            authTokenProvider: secureStorage,
            correlationContext: correlationContext
        )
        let router = AppRouter()
        let deepLinkRouter = DeepLinkRouter()

        return AppDependencies(
            apiClient: apiClient,
            secureStorage: secureStorage,
            logger: logger,
            networkMonitor: networkMonitor,
            router: router,
            deepLinkRouter: deepLinkRouter,
            correlationContext: correlationContext
        )
    }
}
