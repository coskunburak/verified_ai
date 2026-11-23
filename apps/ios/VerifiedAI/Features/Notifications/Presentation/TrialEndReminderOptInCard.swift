import SwiftUI

struct TrialEndReminderOptInCard: View {
    @Bindable var viewModel: NotificationPreferencesViewModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        AppSurfaceCard(elevated: false, isSelected: viewModel.isTrialEndReminderActive) {
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: SpacingTokens.sm) {
                    headerText
                    Spacer(minLength: SpacingTokens.xs)
                    statusBadge
                }

                VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                    headerText
                    statusBadge
                }
            }

            Button {
                Task { await viewModel.enableTrialEndReminderOneTap() }
            } label: {
                if viewModel.state == .saving {
                    ProgressView()
                        .tint(ColorTokens.actionForeground)
                } else {
                    Label(
                        LocalizedStringKey(viewModel.isTrialEndReminderActive
                            ? "notification.trial_optin.action.enabled"
                            : "notification.trial_optin.action.enable"),
                        systemImage: viewModel.isTrialEndReminderActive ? "checkmark.circle.fill" : "bell.badge"
                    )
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .frame(minHeight: 44)
            .disabled(
                viewModel.state == .saving
                    || viewModel.state == .featureDisabled
                    || viewModel.isTrialEndReminderActive
            )
            .accessibilityIdentifier("notifications.trialReminderOneTapButton")
            .accessibilityLabel(LocalizedStringKey(viewModel.isTrialEndReminderActive
                ? "notification.trial_optin.action.enabled"
                : "notification.trial_optin.action.enable"))
            .accessibilityHint(LocalizedStringKey("notification.trial_optin.hint"))
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.18), value: viewModel.isTrialEndReminderActive)
    }

    private var headerText: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(LocalizedStringKey("notification.trial_optin.title"))
                .font(TypographyTokens.cardTitle)
                .foregroundStyle(ColorTokens.textPrimary)
            Text(LocalizedStringKey("notification.trial_optin.subtitle"))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var statusBadge: some View {
        AppStatusBadge(
            LocalizedStringKey(viewModel.isTrialEndReminderActive
                ? "notification.trial_optin.badge.active"
                : "notification.trial_optin.badge.optional"),
            systemImage: viewModel.isTrialEndReminderActive ? "checkmark.seal" : "clock.badge",
            tone: viewModel.isTrialEndReminderActive ? .success : .neutral
        )
    }
}
