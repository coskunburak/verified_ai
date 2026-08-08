import SwiftUI

struct HomePlaceholderView: View {
    let environmentName: String
    let launchState: AppLaunchState
    let entitlement: Entitlement?
    let entitlementMessage: String?
    let retry: () -> Void
    let manageSubscription: () -> Void
    let manageAccount: () -> Void

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

            if let entitlement {
                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    Label("\(entitlement.tier.title) access", systemImage: "person.crop.circle.badge.checkmark")
                        .foregroundStyle(ColorTokens.action)
                    if let entitlementMessage {
                        Text(entitlementMessage)
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                    CapabilityGate(entitlement: entitlement, capability: .basicSolve) {
                        Label("Basic solve available", systemImage: "checkmark.circle")
                            .foregroundStyle(ColorTokens.action)
                    } denied: {
                        UpgradeRequiredView(capability: .basicSolve)
                    }
                    CapabilityGate(entitlement: entitlement, capability: .verifiedSolve) {
                        Label("Verified solve available", systemImage: "checkmark.seal")
                            .foregroundStyle(ColorTokens.action)
                    } denied: {
                        UpgradeRequiredView(capability: .verifiedSolve)
                    }
                    Button {
                        manageSubscription()
                    } label: {
                        Label("Subscription", systemImage: "creditcard")
                    }
                    .buttonStyle(.borderedProminent)

                    Button {
                        manageAccount()
                    } label: {
                        Label("Account", systemImage: "person.crop.circle")
                    }
                    .buttonStyle(.bordered)
                }
            }
        }
        .padding(SpacingTokens.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(ColorTokens.background)
    }
}

#Preview {
    HomePlaceholderView(
        environmentName: "Development",
        launchState: .ready,
        entitlement: nil,
        entitlementMessage: nil,
        retry: {},
        manageSubscription: {},
        manageAccount: {}
    )
}
