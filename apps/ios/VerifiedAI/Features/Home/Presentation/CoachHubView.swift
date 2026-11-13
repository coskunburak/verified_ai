import SwiftUI

public struct CoachHubView: View {
    @ObservedObject public var viewModel: DailyCoachViewModel
    public let onStartCapture: () -> Void
    public let onOpenTask: (DailyCoachTaskModel) -> Void

    public init(
        viewModel: DailyCoachViewModel,
        onStartCapture: @escaping () -> Void = {},
        onOpenTask: @escaping (DailyCoachTaskModel) -> Void = { _ in }
    ) {
        self.viewModel = viewModel
        self.onStartCapture = onStartCapture
        self.onOpenTask = onOpenTask
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                topStatusBar

                if viewModel.isOfflineBannerVisible {
                    offlineBanner
                }

                DualModeSwitcher(
                    selectedMode: viewModel.selectedMode,
                    onSelectMode: { mode in
                        Task {
                            await viewModel.toggleMode(mode)
                        }
                    }
                )

                if let summary = viewModel.summary {
                    if summary.isColdStart {
                        coldStartHeroCard(summary: summary)
                    } else {
                        heroMissionCard(summary: summary)
                    }

                    quickCaptureCTA

                    if !summary.activeTrapRadar.isEmpty {
                        activeTrapRadarSection(items: summary.activeTrapRadar)
                    }
                } else if viewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.vertical, 28)
                }
            }
            .padding(16)
        }
        .sheet(isPresented: $viewModel.showingStudyPlanSheet) {
            DailyStudyPlanView(
                viewModel: viewModel,
                onSelectTask: onOpenTask
            )
        }
        .task {
            if viewModel.summary == nil {
                await viewModel.loadTodaySummary()
            }
        }
    }

    private var topStatusBar: some View {
        HStack {
            HStack(spacing: 6) {
                Image(systemName: "target")
                    .foregroundColor(.accentColor)
                Text(LocalizedStringKey("coach.hub.exam_target"))
                    .font(.caption.weight(.semibold))
                    .foregroundColor(.primary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color(UIColor.secondarySystemBackground))
            .clipShape(Capsule())

            Spacer()

            let streakDays = viewModel.summary?.streakDays ?? 0
            HStack(spacing: 4) {
                Text("🔥")
                Text("\(streakDays)")
                    .font(.caption.weight(.bold))
                Text(LocalizedStringKey("coach.hub.streak_days"))
                    .font(.caption.weight(.semibold))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color.orange.opacity(0.16))
            .foregroundColor(.orange)
            .clipShape(Capsule())
        }
    }

    private var offlineBanner: some View {
        HStack(spacing: 8) {
            Image(systemName: "wifi.slash")
                .foregroundColor(.orange)
            Text(LocalizedStringKey("coach.hub.offline_banner"))
                .font(.caption)
                .foregroundColor(.primary)
            Spacer()
        }
        .padding(10)
        .background(Color.orange.opacity(0.14))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func heroMissionCard(summary: DailyCoachSummaryModel) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(LocalizedStringKey("coach.hub.mission_title"))
                    .font(.headline)
                    .foregroundColor(.primary)
                Spacer()
                Text(summary.isDownshifted
                     ? LocalizedStringKey("coach.downshift.badge")
                     : LocalizedStringKey("coach.hub.budget_15"))
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(summary.isDownshifted ? Color.orange.opacity(0.18) : Color.accentColor.opacity(0.16))
                    .foregroundColor(summary.isDownshifted ? .orange : .accentColor)
                    .clipShape(Capsule())
            }

            Text(summary.briefingText)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: 6) {
                ProgressView(
                    value: Double(summary.completedTasksCount),
                    total: Double(max(1, summary.totalTasksCount))
                )
                .tint(.accentColor)

                Text("\(summary.completedTasksCount) / \(summary.totalTasksCount)")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(.secondary)
            }

            if let firstActiveTask = summary.tasks.first(where: { $0.status == .pending }) ?? summary.tasks.first {
                VStack(alignment: .leading, spacing: 6) {
                    Text(LocalizedStringKey("coach.hub.preview_label"))
                        .font(.caption.weight(.semibold))
                        .foregroundColor(.secondary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        NativeMathLabel(
                            latex: firstActiveTask.promptPreviewLatex,
                            fallback: firstActiveTask.promptPreviewLatex,
                            pointSize: 16
                        )
                        .fixedSize()
                    }
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(UIColor.tertiarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            Button {
                viewModel.showingStudyPlanSheet = true
            } label: {
                Label(LocalizedStringKey("coach.hub.start_mission_cta"), systemImage: "play.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("coach.hub.startMission")
        }
        .padding(16)
        .background(Color(UIColor.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    private func coldStartHeroCard(summary: DailyCoachSummaryModel) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(LocalizedStringKey("coach.hub.coldstart_title"))
                    .font(.headline)
                    .foregroundColor(.primary)
                Spacer()
                Text(LocalizedStringKey("coach.hub.budget_15"))
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.green.opacity(0.16))
                    .foregroundColor(.green)
                    .clipShape(Capsule())
            }

            Text(summary.briefingText)
                .font(.subheadline)
                .foregroundColor(.secondary)

            if let demo = summary.coldStartDemo {
                VStack(alignment: .leading, spacing: 8) {
                    Text(demo.title)
                        .font(.caption.weight(.semibold))
                        .foregroundColor(.primary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        NativeMathLabel(
                            latex: demo.samplePromptLatex,
                            fallback: demo.samplePromptLatex,
                            pointSize: 16
                        )
                        .fixedSize()
                    }
                    Text(demo.hintText)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(UIColor.tertiarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            Button {
                viewModel.showingStudyPlanSheet = true
            } label: {
                Label(LocalizedStringKey("coach.hub.coldstart_cta"), systemImage: "sparkles")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("coach.hub.startColdStart")
        }
        .padding(16)
        .background(Color(UIColor.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }

    private var quickCaptureCTA: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                Image(systemName: "camera.viewfinder")
                    .font(.title2.weight(.semibold))
                    .foregroundColor(.accentColor)
                    .frame(width: 44, height: 44)
                    .background(Color.accentColor.opacity(0.14))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 2) {
                    Text(LocalizedStringKey("coach.hub.capture_title"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.primary)
                    Text(LocalizedStringKey("coach.hub.capture_subtitle"))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
            }

            Button(action: onStartCapture) {
                Label(LocalizedStringKey("coach.hub.capture_cta"), systemImage: "arrow.up.right")
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
            }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("coach.hub.quickCapture")
        }
        .padding(14)
        .background(Color(UIColor.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private func activeTrapRadarSection(items: [TrapRadarPreviewModel]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(LocalizedStringKey("coach.hub.radar_title"))
                .font(.headline)
                .foregroundColor(.primary)

            ForEach(items.prefix(3)) { item in
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(.primary)
                        Text(item.skillCode)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Text(badgeDisplayLabel(item.badgeKey))
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.accentColor.opacity(0.14))
                        .foregroundColor(.accentColor)
                        .clipShape(Capsule())
                }
                .padding(12)
                .background(Color(UIColor.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 14))
            }
        }
    }

    private func badgeDisplayLabel(_ badgeKey: String) -> LocalizedStringKey {
        switch badgeKey {
        case "retention_refresh":
            return LocalizedStringKey("coach.hub.badge.retention_refresh")
        case "immunity_check":
            return LocalizedStringKey("coach.hub.badge.immunity_check")
        default:
            return LocalizedStringKey("coach.hub.badge.growth_opportunity")
        }
    }
}
