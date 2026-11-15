import SwiftUI

public struct DailyStudyPlanView: View {
    @ObservedObject public var viewModel: DailyCoachViewModel
    public let onSelectTask: (DailyCoachTaskModel) -> Void

    @State private var expandedTaskIds: Set<UUID> = []

    public init(
        viewModel: DailyCoachViewModel,
        onSelectTask: @escaping (DailyCoachTaskModel) -> Void = { _ in }
    ) {
        self.viewModel = viewModel
        self.onSelectTask = onSelectTask
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: SpacingTokens.md) {
                    if let summary = viewModel.summary {
                        planHeaderCard(summary: summary)

                        ForEach(summary.tasks) { task in
                            taskRowCard(task: task)
                        }

                        if !summary.isDownshifted && summary.status == .inProgress {
                            downshiftCard
                        }
                    } else {
                        ProgressView()
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 32)
                    }
                }
                .padding(SpacingTokens.lg)
            }
            .background(ColorTokens.canvas)
            .navigationTitle(Text(LocalizedStringKey("coach.plan.title")))
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func planHeaderCard(summary: DailyCoachSummaryModel) -> some View {
        AppSurfaceCard(elevated: true) {
            HStack {
                Text(LocalizedStringKey("coach.plan.subtitle"))
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                AppStatusBadge(verbatimTitle: "\(summary.budgetMinutes) dk", systemImage: "clock", tone: .action)
                    .monospacedDigit()
            }

            Text(summary.briefingText)
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textSecondary)
        }
    }

    private func taskRowCard(task: DailyCoachTaskModel) -> some View {
        let isExpanded = expandedTaskIds.contains(task.taskId)

        return AppSurfaceCard {
            HStack(alignment: .center) {
                AppStatusBadge(taskBlockLabel(task.taskType), tone: .action)

                Spacer()

                Text("\(task.estimatedMinutes) dk")
                    .font(TypographyTokens.caption.weight(.medium))
                    .monospacedDigit()
                    .foregroundStyle(ColorTokens.textSecondary)

                statusBadge(task.status)
            }

            Text(task.targetSkillCode)
                .font(TypographyTokens.cardTitle)
                .foregroundStyle(ColorTokens.textPrimary)

            AppFormulaBox(accentColor: ColorTokens.stepHighlight) {
                ScrollView(.horizontal, showsIndicators: false) {
                    NativeMathLabel(
                        latex: task.promptPreviewLatex,
                        fallback: task.promptPreviewLatex,
                        pointSize: 16
                    )
                    .fixedSize()
                }
            }

            Button {
                if isExpanded {
                    expandedTaskIds.remove(task.taskId)
                } else {
                    expandedTaskIds.insert(task.taskId)
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: isExpanded ? "chevron.up" : "questionmark.circle.fill")
                    Text(LocalizedStringKey("coach.plan.why_this_task"))
                        .font(TypographyTokens.caption.weight(.semibold))
                }
                .foregroundStyle(ColorTokens.stepHighlight)
                .padding(.horizontal, SpacingTokens.sm)
                .frame(minHeight: 36)
                .background(ColorTokens.stepHighlightSoft)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous))
            }
            .buttonStyle(.plain)

            if isExpanded {
                Text(task.whyThisTaskRationale)
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textPrimary)
                    .padding(SpacingTokens.compact)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(ColorTokens.stepHighlightSoft)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                            .stroke(ColorTokens.stepHighlight.opacity(0.24), lineWidth: 1)
                    )
            }

            if task.status == .pending {
                Button {
                    onSelectTask(task)
                    Task {
                        await viewModel.completeTask(taskId: task.taskId)
                    }
                } label: {
                    Label(LocalizedStringKey("coach.plan.complete_task_cta"), systemImage: "checkmark.circle.fill")
                }
                .buttonStyle(AppPrimaryButtonStyle())
            }
        }
        .opacity(task.status == .skipped ? 0.55 : 1.0)
    }

    private var downshiftCard: some View {
        AppSurfaceCard(elevated: true) {
            Text(LocalizedStringKey("coach.downshift.title"))
                .font(TypographyTokens.cardTitle)
                .foregroundStyle(ColorTokens.textPrimary)
            Text(LocalizedStringKey("coach.downshift.subtitle"))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)

            Button {
                Task {
                    await viewModel.triggerBusyDayDownshift()
                }
            } label: {
                Label(LocalizedStringKey("coach.downshift.button"), systemImage: "clock.arrow.circlepath")
            }
            .buttonStyle(AppSecondaryButtonStyle())
            .accessibilityIdentifier("coach.plan.downshiftButton")
        }
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                .stroke(ColorTokens.warning.opacity(0.45), lineWidth: 1)
        )
    }

    private func taskBlockLabel(_ type: DailyCoachTaskType) -> LocalizedStringKey {
        switch type {
        case .trapRepair:
            return LocalizedStringKey("coach.plan.block.trap_repair")
        case .spacedRetention:
            return LocalizedStringKey("coach.plan.block.spaced_retention")
        case .examSynthesis:
            return LocalizedStringKey("coach.plan.block.exam_synthesis")
        }
    }

    private func statusBadge(_ status: DailyCoachTaskStatus) -> some View {
        let tone: AppBadgeTone
        let labelKey: String
        switch status {
        case .completed:
            tone = .success
            labelKey = "coach.plan.status.completed"
        case .skipped:
            tone = .neutral
            labelKey = "coach.plan.status.skipped"
        case .pending:
            tone = .action
            labelKey = "coach.plan.status.pending"
        }
        return AppStatusBadge(LocalizedStringKey(labelKey), tone: tone)
    }
}
