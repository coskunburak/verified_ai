import SwiftUI

struct RootView: View {
    let environment: AppEnvironment
    let dependencies: AppDependencies

    @State private var launchState: AppLaunchState = .ready
    @State private var signInViewModel: SignInViewModel
    @State private var onboardingViewModel: OnboardingViewModel
    @State private var accountSettingsViewModel: AccountSettingsViewModel
    @State private var entitlementViewModel: EntitlementViewModel
    @State private var paywallViewModel: PaywallViewModel
    @State private var problemCaptureViewModel: ProblemCaptureViewModel
    @State private var isPaywallPresented = false
    @State private var isAccountSettingsPresented = false
    @State private var isProblemCapturePresented = false

    init(environment: AppEnvironment, dependencies: AppDependencies) {
        self.environment = environment
        self.dependencies = dependencies
        _signInViewModel = State(initialValue: SignInViewModel(
            authenticationAPI: dependencies.authenticationAPI,
            sessionStore: dependencies.authenticationSessionStore,
            networkMonitor: dependencies.networkMonitor,
            logger: dependencies.logger
        ))
        _onboardingViewModel = State(initialValue: OnboardingViewModel(
            learningProfileAPI: dependencies.learningProfileAPI,
            networkMonitor: dependencies.networkMonitor,
            logger: dependencies.logger
        ))
        _accountSettingsViewModel = State(initialValue: AccountSettingsViewModel(
            accountPrivacyAPI: dependencies.accountPrivacyAPI,
            sessionStore: dependencies.authenticationSessionStore,
            networkMonitor: dependencies.networkMonitor,
            logger: dependencies.logger
        ))
        _entitlementViewModel = State(initialValue: EntitlementViewModel(
            entitlementAPI: dependencies.entitlementAPI,
            displayCache: dependencies.entitlementDisplayCache,
            networkMonitor: dependencies.networkMonitor,
            logger: dependencies.logger
        ))
        _paywallViewModel = State(initialValue: PaywallViewModel(
            billingAPI: dependencies.appleBillingAPI,
            storeRepository: dependencies.storeProductRepository,
            entitlementAPI: dependencies.entitlementAPI,
            networkMonitor: dependencies.networkMonitor,
            logger: dependencies.logger
        ))
        _problemCaptureViewModel = State(initialValue: ProblemCaptureViewModel(
            cameraClient: dependencies.problemCameraClient,
            assetStore: dependencies.capturedAssetStore,
            qualityAnalyzer: dependencies.captureQualityAnalyzer,
            logger: dependencies.logger
        ))
    }

    var body: some View {
        Group {
            switch signInViewModel.state {
            case .authenticated(let session):
                authenticatedContent(session: session)
            default:
                SignInView(viewModel: signInViewModel)
            }
        }
        .tint(ColorTokens.action)
        .task {
            await signInViewModel.restore()
        }
    }

    @ViewBuilder
    private func authenticatedContent(session: AuthSession) -> some View {
        NavigationStack {
            switch onboardingViewModel.state {
            case .ready:
                entitledHomeContent()
            case .loading, .idle:
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(ColorTokens.background)
                    .navigationTitle("Verified AI")
            default:
                OnboardingFlowView(viewModel: onboardingViewModel)
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button {
                    isAccountSettingsPresented = true
                } label: {
                    Label("Account", systemImage: "person.crop.circle")
                }
            }

            ToolbarItem(placement: .topBarTrailing) {
                Button("Sign Out") {
                    resetAuthenticatedState()
                    signInViewModel.logout()
                }
            }
        }
        .sheet(isPresented: $isAccountSettingsPresented) {
            NavigationStack {
                AccountSettingsView(
                    viewModel: accountSettingsViewModel,
                    onAccountDeleted: handleAccountDeleted
                )
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Done") {
                            isAccountSettingsPresented = false
                        }
                    }
                }
            }
        }
        .task(id: session.sessionId) {
            accountSettingsViewModel.reset()
            dependencies.storeKitTransactionObserver.start()
            await onboardingViewModel.bootstrap()
        }
        .onChange(of: onboardingViewModel.state) { _, state in
            if case .ready = state {
                Task {
                    await entitlementViewModel.bootstrap()
                }
            }
        }
    }

    @ViewBuilder
    private func entitledHomeContent() -> some View {
        switch entitlementViewModel.state {
        case .ready, .offline:
            HomePlaceholderView(
                environmentName: environment.displayName,
                launchState: launchState,
                entitlement: entitlementViewModel.entitlement,
                entitlementMessage: entitlementViewModel.message,
                retry: { launchState = .ready },
                startProblemCapture: {
                    Task {
                        await problemCaptureViewModel.cancel()
                        problemCaptureViewModel.open()
                        isProblemCapturePresented = true
                    }
                },
                manageSubscription: { isPaywallPresented = true },
                manageAccount: { isAccountSettingsPresented = true }
            )
            .navigationTitle("Verified AI")
            .sheet(isPresented: $isPaywallPresented, onDismiss: {
                Task { await entitlementViewModel.bootstrap(force: true) }
            }) {
                PaywallView(viewModel: paywallViewModel)
            }
            .fullScreenCover(isPresented: $isProblemCapturePresented, onDismiss: {
                Task {
                    if case .readyForHandoff = problemCaptureViewModel.state {
                        return
                    }
                    await problemCaptureViewModel.cancel()
                }
            }) {
                ProblemCaptureView(
                    viewModel: problemCaptureViewModel,
                    cameraClient: dependencies.problemCameraClient,
                    onDismiss: { isProblemCapturePresented = false }
                )
            }
        case .loading, .idle:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(ColorTokens.background)
                .navigationTitle("Verified AI")
                .task {
                    await entitlementViewModel.bootstrap()
                }
        case .failed:
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Label(entitlementViewModel.message ?? "Entitlement could not be loaded.", systemImage: "exclamationmark.triangle")
                    .foregroundStyle(ColorTokens.warning)
                Button("Retry") {
                    Task { await entitlementViewModel.bootstrap(force: true) }
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(ColorTokens.background)
            .navigationTitle("Verified AI")
        }
    }

    private func resetAuthenticatedState() {
        onboardingViewModel.reset()
        accountSettingsViewModel.reset()
        entitlementViewModel.reset()
        paywallViewModel.reset()
        Task { await problemCaptureViewModel.cancel() }
    }

    private func handleAccountDeleted() {
        isAccountSettingsPresented = false
        resetAuthenticatedState()
        signInViewModel.discardLocalSession()
    }
}
