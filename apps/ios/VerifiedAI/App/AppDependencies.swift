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
            baseURL: environment.apiBaseURL,
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
            presignedObjectUploader: presignedObjectUploader,
            logger: logger,
            networkMonitor: networkMonitor,
            router: router,
            deepLinkRouter: deepLinkRouter,
            correlationContext: correlationContext
        )
    }
}
