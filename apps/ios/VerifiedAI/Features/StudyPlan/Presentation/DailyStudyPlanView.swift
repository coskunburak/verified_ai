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
                VStack(alignment: .leading, spacing: 16) {
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
                .padding(16)
            }
            .navigationTitle(Text(LocalizedStringKey("coach.plan.title")))
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func planHeaderCard(summary: DailyCoachSummaryModel) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(LocalizedStringKey("coach.plan.subtitle"))
                    .font(.headline)
                Spacer()
                Text("\(summary.budgetMinutes) dk")
                    .font(.caption.weight(.bold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Color.accentColor.opacity(0.16))
                    .foregroundColor(.accentColor)
                    .clipShape(Capsule())
            }

            Text(summary.briefingText)
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .padding(14)
        .background(Color(UIColor.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func taskRowCard(task: DailyCoachTaskModel) -> some View {
        let isExpanded = expandedTaskIds.contains(task.taskId)

        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center) {
                Text(taskBlockLabel(task.taskType))
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.accentColor.opacity(0.14))
                    .foregroundColor(.accentColor)
                    .clipShape(Capsule())

                Spacer()

                Text("\(task.estimatedMinutes) dk")
                    .font(.caption.weight(.medium))
                    .foregroundColor(.secondary)

                statusBadge(task.status)
            }

            Text(task.targetSkillCode)
                .font(.subheadline.weight(.semibold))
                .foregroundColor(.primary)

            ScrollView(.horizontal, showsIndicators: false) {
                NativeMathLabel(
                    latex: task.promptPreviewLatex,
                    fallback: task.promptPreviewLatex,
                    pointSize: 16
                )
                .fixedSize()
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(UIColor.tertiarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 10))

            Button {
                if isExpanded {
                    expandedTaskIds.remove(task.taskId)
                } else {
                    expandedTaskIds.insert(task.taskId)
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: isExpanded ? "chevron.up" : "info.circle")
                    Text(LocalizedStringKey("coach.plan.why_this_task"))
                        .font(.caption.weight(.semibold))
                }
                .foregroundColor(.accentColor)
                .frame(minHeight: 36)
            }
            .buttonStyle(.plain)

            if isExpanded {
                Text(task.whyThisTaskRationale)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(UIColor.tertiarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            }

            if task.status == .pending {
                HStack(spacing: 10) {
                    Button {
                        onSelectTask(task)
                        Task {
                            await viewModel.completeTask(taskId: task.taskId)
                        }
                    } label: {
                        Label(LocalizedStringKey("coach.plan.complete_task_cta"), systemImage: "checkmark.circle.fill")
                            .font(.subheadline.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
        .padding(14)
        .background(Color(UIColor.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .opacity(task.status == .skipped ? 0.55 : 1.0)
    }

    private var downshiftCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(LocalizedStringKey("coach.downshift.title"))
                .font(.subheadline.weight(.semibold))
            Text(LocalizedStringKey("coach.downshift.subtitle"))
                .font(.caption)
                .foregroundColor(.secondary)

            Button {
                Task {
                    await viewModel.triggerBusyDayDownshift()
                }
            } label: {
                Label(LocalizedStringKey("coach.downshift.button"), systemImage: "clock.arrow.circlepath")
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
            }
            .buttonStyle(.bordered)
            .tint(.orange)
            .accessibilityIdentifier("coach.plan.downshiftButton")
        }
        .padding(14)
        .background(Color.orange.opacity(0.10))
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
        let color: Color
        let labelKey: String
        switch status {
        case .completed:
            color = .green
            labelKey = "coach.plan.status.completed"
        case .skipped:
            color = .secondary
            labelKey = "coach.plan.status.skipped"
        case .pending:
            color = .accentColor
            labelKey = "coach.plan.status.pending"
        }
        return Text(LocalizedStringKey(labelKey))
            .font(.caption2.weight(.semibold))
            .foregroundColor(color)
    }
}
