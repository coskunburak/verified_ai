import SwiftUI

struct HomePlaceholderView: View {
    let environmentName: String
    let launchState: AppLaunchState
    let entitlement: Entitlement?
    let entitlementMessage: String?
    let retry: () -> Void
    let startProblemCapture: () -> Void
    let manageSubscription: () -> Void
    let manageAccount: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                header
                primaryAction
                accessSection
                activitySection
            }
            .padding(SpacingTokens.lg)
        }
        .background(ColorTokens.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                    Text("Verified AI")
                        .font(.system(size: 32, weight: .semibold, design: .rounded))
                        .foregroundStyle(ColorTokens.textPrimary)
                        .accessibilityIdentifier("appTitle")
                    Text("Dashboard")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }

                Spacer()

                Button(action: manageAccount) {
                    Image(systemName: "person.crop.circle")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(ColorTokens.textPrimary)
                        .frame(width: 44, height: 44)
                        .background(ColorTokens.surface)
                        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
                }
                .accessibilityLabel("Account")
            }

            HStack(spacing: SpacingTokens.sm) {
                DashboardMetric(title: "Access", value: entitlement?.tier.title ?? "Loading", systemImage: "checkmark.seal")
                DashboardMetric(title: "Environment", value: environmentName, systemImage: "server.rack")
            }
        }
    }

    private var primaryAction: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack(alignment: .top, spacing: SpacingTokens.md) {
                Image(systemName: "camera.viewfinder")
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundStyle(ColorTokens.background)
                    .frame(width: 56, height: 56)
                    .background(ColorTokens.action)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))

                VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                    Text("Scan a Problem")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(ColorTokens.textPrimary)
                    Text("Capture homework, crop it, and prepare it for verification.")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }

            Button(action: startProblemCapture) {
                Label("Start Capture", systemImage: "arrow.up.right")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
            }
            .buttonStyle(.borderedProminent)
            .tint(ColorTokens.action)
            .accessibilityIdentifier("home.scanProblem")
        }
        .dashboardPanel()
    }

    private var accessSection: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack {
                Label("Plan Access", systemImage: "creditcard")
                    .font(.headline)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Button(action: manageSubscription) {
                    Image(systemName: "slider.horizontal.3")
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.bordered)
                .tint(ColorTokens.textSecondary)
                .accessibilityLabel("Subscription")
            }

            if let entitlement {
                HStack(spacing: SpacingTokens.sm) {
                    CapabilityBadge(title: entitlement.tier.title, systemImage: "person.crop.circle.badge.checkmark", color: ColorTokens.action)
                    CapabilityBadge(
                        title: entitlement.allows(.verifiedSolve) ? "Verified" : "Basic",
                        systemImage: entitlement.allows(.verifiedSolve) ? "checkmark.seal.fill" : "checkmark.circle",
                        color: entitlement.allows(.verifiedSolve) ? ColorTokens.accent : ColorTokens.textSecondary
                    )
                }

                if let entitlementMessage {
                    Text(entitlementMessage)
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            } else {
                Label("Access is loading", systemImage: "clock")
                    .foregroundStyle(ColorTokens.textSecondary)
            }
        }
        .dashboardPanel()
    }

    private var activitySection: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            Label("Today", systemImage: "chart.line.uptrend.xyaxis")
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: SpacingTokens.sm) {
                DashboardMetric(title: "Solved", value: "0", systemImage: "sum")
                DashboardMetric(title: "Review", value: "Ready", systemImage: "sparkle.magnifyingglass")
                DashboardMetric(title: "Accuracy", value: "New", systemImage: "scope")
                DashboardMetric(title: "Plan", value: statusValue, systemImage: statusIcon)
            }

            if case .degraded(let reason, let recoverable) = launchState {
                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    Label(reason, systemImage: "exclamationmark.triangle")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.warning)
                    if recoverable {
                        Button("Retry", action: retry)
                            .buttonStyle(.bordered)
                            .tint(ColorTokens.warning)
                    }
                }
            }
        }
        .dashboardPanel()
    }

    private var statusValue: String {
        switch launchState {
        case .initializing:
            return "Syncing"
        case .ready:
            return "Ready"
        case .degraded:
            return "Check"
        }
    }

    private var statusIcon: String {
        switch launchState {
        case .initializing:
            return "clock"
        case .ready:
            return "checkmark.circle"
        case .degraded:
            return "exclamationmark.triangle"
        }
    }
}

private struct DashboardMetric: View {
    let title: String
    let value: String
    let systemImage: String

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Image(systemName: systemImage)
                .foregroundStyle(ColorTokens.action)
            Text(value)
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Text(title)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textTertiary)
                .lineLimit(1)
        }
        .padding(SpacingTokens.md)
        .frame(maxWidth: .infinity, minHeight: 104, alignment: .leading)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
    }
}

private struct CapabilityBadge: View {
    let title: String
    let systemImage: String
    let color: Color

    var body: some View {
        Label(title, systemImage: systemImage)
            .font(.caption.weight(.semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(ColorTokens.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
    }
}

private extension View {
    func dashboardPanel() -> some View {
        padding(SpacingTokens.md)
            .background(ColorTokens.surface)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
            .overlay {
                RoundedRectangle(cornerRadius: RadiusTokens.medium)
                    .stroke(ColorTokens.border, lineWidth: 1)
            }
    }
}

#Preview {
    HomePlaceholderView(
        environmentName: "Development",
        launchState: .ready,
        entitlement: nil,
        entitlementMessage: nil,
        retry: {},
        startProblemCapture: {},
        manageSubscription: {},
        manageAccount: {}
    )
}
