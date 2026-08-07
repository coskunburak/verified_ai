import SwiftUI

struct HomePlaceholderView: View {
    let environmentName: String
    let launchState: AppLaunchState
    let retry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            Text("Verified AI")
                .font(TypographyTokens.title)
                .foregroundStyle(ColorTokens.textPrimary)
                .accessibilityIdentifier("appTitle")

            Text("Environment: \(environmentName)")
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)

            switch launchState {
            case .initializing:
                ProgressView()
                    .accessibilityLabel("Initializing")
            case .ready:
                Label("Platform shell ready", systemImage: "checkmark.circle")
                    .foregroundStyle(ColorTokens.action)
            case .degraded(let reason, let recoverable):
                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    Label(reason, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(ColorTokens.warning)
                    if recoverable {
                        Button("Retry", action: retry)
                            .buttonStyle(.borderedProminent)
                    }
                }
            }
        }
        .padding(SpacingTokens.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(ColorTokens.background)
    }
}

#Preview {
    HomePlaceholderView(environmentName: "Development", launchState: .ready, retry: {})
}

