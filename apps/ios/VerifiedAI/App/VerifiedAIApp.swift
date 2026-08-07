import SwiftUI

@main
@MainActor
struct VerifiedAIApp: App {
    @Environment(\.scenePhase) private var scenePhase

    private let environment: AppEnvironment
    private let dependencies: AppDependencies
    private let lifecycleHandler: AppLifecycleHandler

    init() {
        let environment = AppEnvironment.current()
        self.environment = environment
        self.dependencies = AppDependencies.live(environment: environment)
        self.lifecycleHandler = AppLifecycleHandler(logger: dependencies.logger)
    }

    var body: some Scene {
        WindowGroup {
            RootView(environment: environment, dependencies: dependencies)
        }
        .onChange(of: scenePhase) { _, newPhase in
            lifecycleHandler.handle(scenePhase: newPhase)
        }
    }
}
