import SwiftUI

struct RootView: View {
    let environment: AppEnvironment
    let dependencies: AppDependencies

    @State private var launchState: AppLaunchState = .ready

    var body: some View {
        NavigationStack {
            HomePlaceholderView(
                environmentName: environment.displayName,
                launchState: launchState,
                retry: { launchState = .ready }
            )
            .navigationTitle("Verified AI")
        }
        .tint(ColorTokens.action)
    }
}

