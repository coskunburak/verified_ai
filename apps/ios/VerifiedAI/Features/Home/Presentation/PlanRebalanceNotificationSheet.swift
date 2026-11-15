import SwiftUI

public struct PlanRebalanceNotificationSheet: View {
    @ObservedObject public var viewModel: StudyPlanRebalanceViewModel
    public var onPlanAccepted: ((StudyPlanRebalanceResponse) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dismiss) private var dismiss
    @ScaledMetric(relativeTo: .body) private var cardPadding: CGFloat = 20

    public init(
        viewModel: StudyPlanRebalanceViewModel,
        onPlanAccepted: ((StudyPlanRebalanceResponse) -> Void)? = nil
    ) {
        self.viewModel = viewModel
        self.onPlanAccepted = onPlanAccepted
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                    noBacklogBadge

                    Text(LocalizedStringKey(viewModel.recoveryStatus?.welcomeMessageKey ?? "studyplan.rebalance.welcome_recovered"))
                        .font(TypographyTokens.sectionTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)

                    summaryPillRow

                    actionButtons
                }
                .padding(cardPadding)
            }
            .background(ColorTokens.canvas)
            .navigationTitle(Text("studyplan.rebalance.sheet_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                    .accessibilityLabel(Text("Close"))
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("rebalance.sheet.root")
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: viewModel.isRebalancing)
    }

    private var noBacklogBadge: some View {
        AppStatusBadge("studyplan.rebalance.no_backlog_badge", systemImage: "sparkles", tone: .action)
            .accessibilityAddTraits(.isStaticText)
    }

    private var summaryPillRow: some View {
        let status = viewModel.recoveryStatus
        let budget = status?.recommendedDailyBudgetMinutes ?? 15
        let activeTasks = status?.activeTodayTasksCount ?? 3
        let streamlined = (status?.prunedTasksCount ?? 0) + (status?.rescheduledReviewsCount ?? 0)

        return HStack(spacing: SpacingTokens.sm) {
            summaryPill(
                titleKey: "studyplan.rebalance.summary_budget_label",
                valueText: "\(budget) min",
                tone: .action
            )
            summaryPill(
                titleKey: "studyplan.rebalance.summary_tasks_label",
                valueText: "\(activeTasks)",
                tone: .success
            )
            summaryPill(
                titleKey: "studyplan.rebalance.summary_streamlined_label",
                valueText: "\(streamlined)",
                tone: .neutral
            )
        }
    }

    private func summaryPill(titleKey: String, valueText: String, tone: AppBadgeTone) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(LocalizedStringKey(titleKey))
                .font(TypographyTokens.microLabel)
                .foregroundStyle(ColorTokens.textSecondary)
            Text(valueText)
                .font(TypographyTokens.sectionTitle)
                .monospacedDigit()
                .foregroundStyle(tone == .neutral ? ColorTokens.textPrimary : ColorTokens.action)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(SpacingTokens.compact)
        .background(ColorTokens.surface, in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
    }

    private var actionButtons: some View {
        let budget = viewModel.recoveryStatus?.recommendedDailyBudgetMinutes ?? 15
        let acceptFormat = NSLocalizedString("studyplan.rebalance.cta_accept_plan", comment: "")
        let acceptTitle = String(format: acceptFormat, budget)

        return VStack(spacing: SpacingTokens.compact) {
            Button {
                Task {
                    if let response = await viewModel.acceptRebalancedPlan() {
                        onPlanAccepted?(response)
                    }
                }
            } label: {
                HStack {
                    if viewModel.isRebalancing {
                        ProgressView()
                            .tint(ColorTokens.actionForeground)
                    }
                    Text(acceptTitle)
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(viewModel.isRebalancing)
            .accessibilityIdentifier("rebalance.cta.accept")
            .accessibilityLabel(Text(acceptTitle))
            .accessibilityHint(Text("studyplan.rebalance.no_backlog_badge"))

            Button {
                Task {
                    if let response = await viewModel.activateMiniModeDownshift() {
                        onPlanAccepted?(response)
                    }
                }
            } label: {
                Text("studyplan.rebalance.cta_mini_mode")
            }
            .buttonStyle(AppSecondaryButtonStyle())
            .disabled(viewModel.isRebalancing)
            .accessibilityIdentifier("rebalance.cta.miniMode")
            .accessibilityLabel(Text("studyplan.rebalance.cta_mini_mode"))
            .accessibilityHint(Text("studyplan.rebalance.welcome_mini_mode"))
        }
    }
}
