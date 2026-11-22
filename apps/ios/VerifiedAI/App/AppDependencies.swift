import Foundation

@MainActor
struct AppDependencies {
    let apiClient: APIClient
    let secureStorage: SecureStorage
    let authenticationSessionStore: AuthenticationSessionStore
    let authenticationAPI: AuthenticationAPI
    let accountPrivacyAPI: AccountPrivacyAPI
    let learningProfileAPI: LearningProfileServicing
    let notificationAPI: NotificationServicing
    let pushPermissionManager: PushPermissionManaging
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
    var visualizationAPI: any MathVisualizationServicing {
        #if DEBUG
        if solveFixture && ProcessInfo.processInfo.arguments.contains("--visualization-fixture") {
            return FixtureVisualizationAPI(failFirst: ProcessInfo.processInfo.arguments.contains("--visualization-fail-first"))
        }
        #endif
        return MathVisualizationAPI(client: apiClient)
    }
    let attemptPending: AttemptAnalysisPendingStore
    var attemptAPI: AttemptAnalysisServicing {
        #if DEBUG
        if solveFixture && ProcessInfo.processInfo.arguments.contains("--attempt-fixture") {
            return FixtureAttemptAnalysisAPI(loseAcknowledgement: ProcessInfo.processInfo.arguments.contains("--attempt-lost-ack"),
                enabled: !ProcessInfo.processInfo.arguments.contains("--attempt-disabled"))
        }
        #endif
        return AttemptAnalysisAPI(client: apiClient)
    }
    var attemptLearningAPI: (any AttemptLearningServicing)? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if solveFixture && args.contains("--methods-fixture") {
            return FixtureMethodComparisonAPI(failFirst: args.contains("--methods-fail-first"),
                status: args.contains("--methods-unsupported") ? "UNSUPPORTED" : args.contains("--methods-empty") ? "NO_ALTERNATIVE" : "VERIFIED",
                single: args.contains("--methods-single"))
        }
        #endif
        return solveFixture ? nil : AttemptLearningAPI(client: apiClient)
    }
    let tutorPending = TutorPendingStore()
    var tutorAPI: TutorServicing {
        #if DEBUG
        if solveFixture && ProcessInfo.processInfo.arguments.contains("--tutor-fixture") {
            return FixtureTutorAPI(failFirst: ProcessInfo.processInfo.arguments.contains("--tutor-send-failure"),
                available: !ProcessInfo.processInfo.arguments.contains("--tutor-unavailable"))
        }
        #endif
        return TutorAPI(client: apiClient)
    }
    var weeklyReportAPI: WeeklyReportServicing {
        HTTPWeeklyReportService(apiClient: apiClient)
    }
    var coldStartDiagnosticAPI: ColdStartDiagnosticServicing {
        HTTPColdStartDiagnosticService(apiClient: apiClient)
    }
    let examCacheStore: ExamOfflineSQLiteStore
    let examAPI: ExamAPIServicing

    func makeCandidateProfileViewModel() -> CandidateProfileViewModel {
        CandidateProfileViewModel(apiService: examAPI, offlineStore: examCacheStore)
    }

    func makeExamBlueprintAssemblyViewModel() -> ExamBlueprintAssemblyViewModel {
        ExamBlueprintAssemblyViewModel(apiService: examAPI, offlineStore: examCacheStore)
    }

    func makeExamReadinessViewModel(
        userId: UUID = ExamBlueprintAssemblyViewModel.defaultLearnerId,
        examCode: String = "TR_YKS_TYT_MATH"
    ) -> ExamReadinessViewModel {
        ExamReadinessViewModel(
            userId: userId,
            selectedExamCode: examCode,
            apiService: examAPI,
            offlineStore: examCacheStore
        )
    }

    #if DEBUG
    func seedOfflineExamFixturesIfNeeded() async {
        let defs = FixtureExamAPIService.seedDefinitions()
        let summaries = defs.map { def in
            ExamDefinitionSummaryModel(
                examDefinitionId: def.examDefinitionId,
                examCode: def.examCode,
                examVersion: def.examVersion,
                jurisdiction: def.jurisdiction,
                examFamily: def.examFamily,
                titleEn: def.titleEn,
                titleTr: def.titleTr,
                status: def.status,
                totalQuestions: def.totalQuestions,
                durationMinutes: def.durationMinutes,
                scoringFormulaType: def.scoringFormulaType,
                wrongPenaltyFactor: def.wrongPenaltyFactor,
                minScore: def.minScore,
                maxScore: def.maxScore,
                navigationPolicy: def.navigationPolicy,
                blueprintHash: def.blueprintHash,
                effectiveFrom: def.effectiveFrom,
                deprecatedAt: def.deprecatedAt,
                supersededByVersion: def.supersededByVersion,
                sectionCount: def.sections.count,
                topicWeightCount: def.topicWeights.count,
                trainingEligible: false
            )
        }
        let catalog = ExamDefinitionCatalogResponse(
            schemaVersion: "exam-definition-catalog-v1",
            definitions: summaries,
            totalCount: summaries.count,
            includeDeprecated: false,
            trainingEligible: false,
            llmTokensUsed: 0
        )
        await examCacheStore.saveCatalog(catalog)
        for def in defs {
            await examCacheStore.saveDefinitionDetail(def)
        }
        let blueprints = FixtureExamAPIService.seedBlueprints()
        if let firstDef = defs.first {
            await examCacheStore.saveBlueprints(blueprints, examDefinitionId: firstDef.examDefinitionId)
        }
    }
    #endif
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

    var solveFlowNetworkMonitor: NetworkMonitoring {
        Self.networkForSolveFlow(service: primarySolveAPI, history: problemSessionHistoryAPI,
            isFixture: solveFixture, live: networkMonitor)
    }

    static func networkForSolveFlow(service: PrimarySolveServicing, history: ProblemSessionHistoryServicing,
                                    isFixture: Bool, live: NetworkMonitoring) -> NetworkMonitoring {
        #if DEBUG
        // Both transports are local fixtures. Host path changes cannot make their data unavailable.
        if isFixture, service is FixturePrimarySolveAPI, history is FixtureSolveHistoryAPI {
            return FixtureSolveNetworkMonitor()
        }
        #endif
        return live
    }

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
        var learningProfileAPI: LearningProfileServicing = LearningProfileAPI(apiClient: apiClient)
        var notificationAPI: NotificationServicing = NotificationAPI(apiClient: apiClient)
        var pushPermissionManager: PushPermissionManaging = PushPermissionManager()
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
        var attemptPending = AttemptAnalysisPendingStore()
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        solveFixture = environment == .development && arguments.contains("--solve-fixture") && arguments.contains("--ui-testing-authenticated")
        solveEnabled = environment == .development
        if solveFixture {
            learningProfileAPI = FixtureLearningProfileAPI(failsSave: arguments.contains("--profile-save-failure"))
            notificationAPI = FixtureNotificationAPI()
            pushPermissionManager = StubPushPermissionManager(initialStatus: .authorized, nextAuthorizationStatus: .authorized)
            if let scopeArgument = arguments.first(where: { $0.hasPrefix("--solve-fixture-scope=") }),
               let scope = UUID(uuidString: String(scopeArgument.dropFirst("--solve-fixture-scope=".count))) {
                solveFixtureScope += "-" + scope.uuidString
            }
            // Synthetic accounts/session IDs repeat between UI tests. Their protected
            // drafts and retry keys must share the solve fixture's isolated scope,
            // never the real account store or another test's persisted learner work.
            let attemptDirectory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("AttemptFixtures", isDirectory: true)
                .appendingPathComponent(solveFixtureScope, isDirectory: true)
            attemptPending = AttemptAnalysisPendingStore(directory: attemptDirectory)
            if arguments.contains("--solve-reset") {
                do { try attemptPending.clearAll() }
                catch { preconditionFailure("Synthetic attempt fixture persistence could not be reset") }
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
        let examCacheStore = ExamOfflineSQLiteStore()
        let examAPI: ExamAPIServicing
        #if DEBUG
        if arguments.contains("--exam-fixture"),
           solveFixture || (environment == .development && arguments.contains("--ui-testing-authenticated") && (arguments.contains("--ui-testing-candidate-profile") || arguments.contains("-ui-testing-candidate-profile"))) {
            let simulateOffline = arguments.contains("--exam-offline")
            let simulatePreliminary = arguments.contains("--exam-readiness-preliminary")
            examAPI = FixtureExamAPIService(
                simulateOffline: simulateOffline,
                simulatePreliminaryReadiness: simulatePreliminary
            )
        } else {
            examAPI = HTTPExamAPIService(apiClient: apiClient)
        }
        #else
        examAPI = HTTPExamAPIService(apiClient: apiClient)
        #endif

        return AppDependencies(
            apiClient: apiClient,
            secureStorage: secureStorage,
            authenticationSessionStore: authenticationSessionStore,
            authenticationAPI: authenticationAPI,
            accountPrivacyAPI: accountPrivacyAPI,
            learningProfileAPI: learningProfileAPI,
            notificationAPI: notificationAPI,
            pushPermissionManager: pushPermissionManager,
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
            attemptPending: attemptPending,
            examCacheStore: examCacheStore,
            examAPI: examAPI,
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
