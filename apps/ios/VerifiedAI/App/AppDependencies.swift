import Foundation

@MainActor
struct AppDependencies {
    let apiClient: APIClient
    let secureStorage: SecureStorage
    let authenticationSessionStore: AuthenticationSessionStore
    let authenticationAPI: AuthenticationAPI
    let accountPrivacyAPI: AccountPrivacyAPI
    let learningProfileAPI: LearningProfileAPI
    let entitlementAPI: EntitlementAPI
    let appleBillingAPI: AppleBillingAPI
    let storeProductRepository: StoreProductRepository
    let storeKitTransactionObserver: StoreKitTransactionObserver
    let entitlementDisplayCache: EntitlementDisplayCache
    let problemCameraClient: AVFoundationProblemCameraClient
    let capturedAssetStore: CapturedAssetStoring
    let captureQualityAnalyzer: CaptureQualityAnalyzing
    let problemAssetUploadAPI: ProblemAssetUploadServicing
    let problemReviewAPI: ProblemReviewServicing
    let problemSessionHistoryAPI: ProblemSessionHistoryServicing
    let problemSessionCache: ProblemSessionCaching
    let primarySolveAPI: PrimarySolveServicing
    let solveRecovery: SolveRecoveryStoring
    let solveEnabled: Bool
    let solveFixture: Bool
    let presignedObjectUploader: PresignedObjectUploading
    let logger: AppLogger
    let networkMonitor: NetworkMonitoring
    let router: AppRouter
    let deepLinkRouter: DeepLinkRouting
    let correlationContext: CorrelationContext

    static func live(environment: AppEnvironment) -> AppDependencies {
        let correlationContext = CorrelationContext()
        let logger = AppLogger(subsystem: "com.verifiedai.learning", category: "app")
        let secureStorage = KeychainStore(service: "com.verifiedai.learning")
        let authenticationSessionStore = AuthenticationSessionStore(secureStorage: secureStorage)
        let networkMonitor = NetworkMonitor()
        let apiClient = APIClient(
            baseURL: AppEnvironment.apiBaseURL(environment: environment),
            session: URLSession.shared,
            authTokenProvider: authenticationSessionStore,
            correlationContext: correlationContext
        )
        let authenticationAPI = AuthenticationAPI(apiClient: apiClient)
        let accountPrivacyAPI = AccountPrivacyAPI(apiClient: apiClient)
        let learningProfileAPI = LearningProfileAPI(apiClient: apiClient)
        let entitlementAPI = EntitlementAPI(apiClient: apiClient)
        let appleBillingAPI = AppleBillingAPI(apiClient: apiClient)
        let storeProductRepository = StoreKitProductRepository()
        let storeKitTransactionObserver = StoreKitTransactionObserver(
            storeRepository: storeProductRepository,
            billingAPI: appleBillingAPI,
            logger: logger
        )
        let entitlementDisplayCache = EntitlementDisplayCache(secureStorage: secureStorage)
        let problemCameraClient = AVFoundationProblemCameraClient(logger: logger)
        let capturedAssetStore = DefaultCapturedAssetStore()
        let captureQualityAnalyzer = DefaultCaptureQualityAnalyzer()
        let problemAssetUploadAPI = ProblemAssetUploadAPI(apiClient: apiClient)
        let problemReviewAPI = ProblemReviewAPI(apiClient: apiClient)
        var problemSessionHistoryAPI: ProblemSessionHistoryServicing = ProblemSessionHistoryAPI(apiClient: apiClient)
        let problemSessionCache: ProblemSessionCaching
        var primarySolveAPI: PrimarySolveServicing = PrimarySolveAPI(client: apiClient)
        var solveEnabled = false
        var solveFixture = false
        var solveFixtureScope = "synthetic-fixture"
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        solveFixture = environment == .development && arguments.contains("--solve-fixture") && arguments.contains("--ui-testing-authenticated")
        solveEnabled = environment == .development && (arguments.contains("--enable-primary-solve") || solveFixture)
        if solveFixture {
            if let scopeArgument = arguments.first(where: { $0.hasPrefix("--solve-fixture-scope=") }),
               let scope = UUID(uuidString: String(scopeArgument.dropFirst("--solve-fixture-scope=".count))) {
                solveFixtureScope += "-" + scope.uuidString
            }
            let scenario = arguments.first(where: { $0.hasPrefix("--solve-scenario=") })?.components(separatedBy: "=").last ?? "GENERATED_UNVERIFIED"
            guard let defaults = UserDefaults(suiteName: "com.verifiedai." + solveFixtureScope) else {
                preconditionFailure("Synthetic solve fixture persistence is unavailable")
            }
            primarySolveAPI = FixturePrimarySolveAPI(scenario: scenario, defaults: defaults,
                reset: arguments.contains("--solve-reset"),
                controlledProgression: arguments.contains("--ui-testing") && arguments.contains("--solve-controlled-progression"))
            problemSessionHistoryAPI = FixtureSolveHistoryAPI()
            problemSessionCache = FixtureSolveHistoryCache()
        } else {
            problemSessionCache = try! SwiftDataProblemSessionCache()
        }
        #else
        problemSessionCache = try! SwiftDataProblemSessionCache()
        #endif
        let solveRecovery = SolveRecoveryStore(scope: solveFixture ? solveFixtureScope : "server-" + environment.rawValue)
        #if DEBUG
        if solveFixture && ProcessInfo.processInfo.arguments.contains("--solve-reset") {
            try? solveRecovery.remove(userId: UUID(uuidString: "00000000-0000-0000-0000-0000000000A1")!, sessionId: FixturePrimarySolveAPI.sessionId)
        }
        #endif
        let presignedObjectUploader = URLSessionPresignedObjectUploader()
        Task {
            await authenticationSessionStore.setRefreshHandler { refreshToken in
                try await authenticationAPI.refresh(refreshToken: refreshToken)
            }
        }
        let router = AppRouter()
        let deepLinkRouter = DeepLinkRouter()

        return AppDependencies(
            apiClient: apiClient,
            secureStorage: secureStorage,
            authenticationSessionStore: authenticationSessionStore,
            authenticationAPI: authenticationAPI,
            accountPrivacyAPI: accountPrivacyAPI,
            learningProfileAPI: learningProfileAPI,
            entitlementAPI: entitlementAPI,
            appleBillingAPI: appleBillingAPI,
            storeProductRepository: storeProductRepository,
            storeKitTransactionObserver: storeKitTransactionObserver,
            entitlementDisplayCache: entitlementDisplayCache,
            problemCameraClient: problemCameraClient,
            capturedAssetStore: capturedAssetStore,
            captureQualityAnalyzer: captureQualityAnalyzer,
            problemAssetUploadAPI: problemAssetUploadAPI,
            problemReviewAPI: problemReviewAPI,
            problemSessionHistoryAPI: problemSessionHistoryAPI,
            problemSessionCache: problemSessionCache,
            primarySolveAPI: primarySolveAPI,
            solveRecovery: solveRecovery,
            solveEnabled: solveEnabled,
            solveFixture: solveFixture,
            presignedObjectUploader: presignedObjectUploader,
            logger: logger,
            networkMonitor: networkMonitor,
            router: router,
            deepLinkRouter: deepLinkRouter,
            correlationContext: correlationContext
        )
    }
}
