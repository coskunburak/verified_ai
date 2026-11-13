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
                VStack(alignment: .leading, spacing: 18) {
                    noBacklogBadge

                    Text(LocalizedStringKey(viewModel.recoveryStatus?.welcomeMessageKey ?? "studyplan.rebalance.welcome_recovered"))
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: false, vertical: true)

                    summaryPillRow

                    actionButtons
                }
                .padding(cardPadding)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle(Text("studyplan.rebalance.sheet_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
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
        HStack(spacing: 8) {
            Image(systemName: "sparkles")
                .foregroundStyle(Color.teal)
                .accessibilityHidden(true)
            Text("studyplan.rebalance.no_backlog_badge")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color.teal)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Color.teal.opacity(0.14), in: Capsule())
        .accessibilityAddTraits(.isStaticText)
    }

    private var summaryPillRow: some View {
        let status = viewModel.recoveryStatus
        let budget = status?.recommendedDailyBudgetMinutes ?? 15
        let activeTasks = status?.activeTodayTasksCount ?? 3
        let streamlined = (status?.prunedTasksCount ?? 0) + (status?.rescheduledReviewsCount ?? 0)

        return HStack(spacing: 10) {
            summaryPill(
                titleKey: "studyplan.rebalance.summary_budget_label",
                valueText: "\(budget) min",
                tint: .teal
            )
            summaryPill(
                titleKey: "studyplan.rebalance.summary_tasks_label",
                valueText: "\(activeTasks)",
                tint: .mint
            )
            summaryPill(
                titleKey: "studyplan.rebalance.summary_streamlined_label",
                valueText: "\(streamlined)",
                tint: .cyan
            )
        }
    }

    private func summaryPill(titleKey: String, valueText: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(LocalizedStringKey(titleKey))
                .font(.caption2.weight(.medium))
                .foregroundStyle(.secondary)
            Text(valueText)
                .font(.headline.monospacedDigit())
                .foregroundStyle(.primary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(tint.opacity(0.10), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .accessibilityElement(children: .combine)
    }

    private var actionButtons: some View {
        let budget = viewModel.recoveryStatus?.recommendedDailyBudgetMinutes ?? 15
        let acceptFormat = NSLocalizedString("studyplan.rebalance.cta_accept_plan", comment: "")
        let acceptTitle = String(format: acceptFormat, budget)

        return VStack(spacing: 12) {
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
                            .tint(.white)
                    }
                    Text(acceptTitle)
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                }
                .padding(.vertical, 14)
                .foregroundStyle(.white)
                .background(Color.teal, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
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
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .foregroundStyle(Color.teal)
                    .background(Color.teal.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .disabled(viewModel.isRebalancing)
            .accessibilityIdentifier("rebalance.cta.miniMode")
            .accessibilityLabel(Text("studyplan.rebalance.cta_mini_mode"))
            .accessibilityHint(Text("studyplan.rebalance.welcome_mini_mode"))
        }
    }
}
