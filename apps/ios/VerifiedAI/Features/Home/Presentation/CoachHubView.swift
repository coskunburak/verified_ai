import SwiftUI

public struct CoachHubView: View {
    @ObservedObject public var viewModel: DailyCoachViewModel
    public var rebalanceViewModel: StudyPlanRebalanceViewModel?
    public var gamificationViewModel: GamificationViewModel?
    public var coldStartViewModel: ColdStartDiagnosticViewModel?
    public var examViewModel: ExamCatalogAndTargetViewModel?
    public var candidateProfileViewModel: CandidateProfileViewModel?
    public var blueprintAssemblyViewModel: ExamBlueprintAssemblyViewModel?
    public let onStartCapture: () -> Void
    public let onOpenTask: (DailyCoachTaskModel) -> Void

    @State private var isFallbackMilestoneSheetPresented: Bool = false
    @State private var isUnusedColdStartSheetPresented: Bool = false

    public init(
        viewModel: DailyCoachViewModel,
        rebalanceViewModel: StudyPlanRebalanceViewModel? = nil,
        gamificationViewModel: GamificationViewModel? = nil,
        coldStartViewModel: ColdStartDiagnosticViewModel? = nil,
        examViewModel: ExamCatalogAndTargetViewModel? = nil,
        candidateProfileViewModel: CandidateProfileViewModel? = nil,
        blueprintAssemblyViewModel: ExamBlueprintAssemblyViewModel? = nil,
        onStartCapture: @escaping () -> Void = {},
        onOpenTask: @escaping (DailyCoachTaskModel) -> Void = { _ in }
    ) {
        self.viewModel = viewModel
        self.rebalanceViewModel = rebalanceViewModel
        self.gamificationViewModel = gamificationViewModel
        self.coldStartViewModel = coldStartViewModel
        self.examViewModel = examViewModel
        self.candidateProfileViewModel = candidateProfileViewModel
        self.blueprintAssemblyViewModel = blueprintAssemblyViewModel
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

                if let rebalanceViewModel {
                    RebalanceCoachHubSection(
                        rebalanceViewModel: rebalanceViewModel,
                        onPlanAccepted: { response in
                            Task {
                                await viewModel.applyRebalancedPlan(response)
                            }
                        }
                    )
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
                    if let coldStartViewModel,
                       !coldStartViewModel.isFeatureDisabled,
                       (summary.isColdStart || coldStartViewModel.requiresRevalidation || coldStartViewModel.latestResult != nil) {
                        ColdStartDiagnosticCardView(viewModel: coldStartViewModel)
                    }
                    if let examViewModel, !examViewModel.isFeatureDisabled {
                        NavigationLink {
                            ExamCatalogAndTargetView(
                                viewModel: examViewModel,
                                candidateProfileViewModel: candidateProfileViewModel,
                                blueprintAssemblyViewModel: blueprintAssemblyViewModel
                            )
                        } label: {
                            HStack {
                                Label(LocalizedStringKey("home.exam_catalog.open_button"), systemImage: "checklist")
                                    .font(TypographyTokens.cardTitle)
                                    .foregroundStyle(ColorTokens.textPrimary)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(TypographyTokens.caption.weight(.semibold))
                                    .foregroundStyle(ColorTokens.textTertiary)
                            }
                            .padding(SpacingTokens.compact)
                            .frame(maxWidth: .infinity)
                            .background(ColorTokens.surface)
                            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                            )
                        }
                        .accessibilityIdentifier("home.examCatalog.button")
                    }
                    if let candidateProfileViewModel {
                        NavigationLink {
                            CandidateGoalSetupView(viewModel: candidateProfileViewModel)
                        } label: {
                            HStack {
                                Label(LocalizedStringKey("home.candidate_profile.open_button"), systemImage: "calendar.badge.clock")
                                    .font(TypographyTokens.cardTitle)
                                    .foregroundStyle(ColorTokens.textPrimary)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(TypographyTokens.caption.weight(.semibold))
                                    .foregroundStyle(ColorTokens.textTertiary)
                            }
                            .padding(SpacingTokens.compact)
                            .frame(maxWidth: .infinity)
                            .background(ColorTokens.surface)
                            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                            )
                        }
                        .accessibilityIdentifier("coachHub.candidateProfileLink")
                    }
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
                } else if let coldStartViewModel, !coldStartViewModel.isFeatureDisabled {
                    ColdStartDiagnosticCardView(viewModel: coldStartViewModel)
                }
            }
            .padding(16)
        }
        .background(ColorTokens.canvas)
        .sheet(isPresented: $viewModel.showingStudyPlanSheet) {
            DailyStudyPlanView(
                viewModel: viewModel,
                onSelectTask: onOpenTask
            )
        }
        .sheet(isPresented: coldStartSheetBinding) {
            if let coldStartViewModel {
                ColdStartDiagnosticFlowView(viewModel: coldStartViewModel)
            }
        }
        .task {
            if viewModel.summary == nil {
                await viewModel.loadTodaySummary()
            }
            if let coldStartViewModel, coldStartViewModel.latestResult == nil {
                await coldStartViewModel.load()
            }
        }
    }

    private var topStatusBar: some View {
        HStack {
            HStack(spacing: 6) {
                Image(systemName: "target")
                    .foregroundStyle(ColorTokens.stepHighlight)
                Text(LocalizedStringKey("coach.hub.exam_target"))
                    .font(TypographyTokens.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.textPrimary)
            }
            .padding(.horizontal, SpacingTokens.compact)
            .padding(.vertical, SpacingTokens.xs)
            .background(ColorTokens.surfaceElevated)
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
            )

            Spacer()

            let activeSummary = resolvedGamificationSummary
            StreakBadgeView(
                streakDays: activeSummary.currentStreakDays,
                freezesRemaining: activeSummary.streakFreezesRemaining,
                streakStatus: activeSummary.streakStatus
            ) {
                if let gamificationViewModel {
                    gamificationViewModel.isMilestoneSheetPresented = true
                } else {
                    isFallbackMilestoneSheetPresented = true
                }
            }
        }
        .sheet(isPresented: milestoneSheetBinding) {
            MilestoneSheetView(summary: resolvedGamificationSummary)
        }
        .task {
            if let gamificationViewModel {
                await gamificationViewModel.loadSummary()
            }
        }
    }

    private var resolvedGamificationSummary: GamificationSummaryResponse {
        if let loadedSummary = gamificationViewModel?.summary {
            return loadedSummary
        }
        return GamificationSummaryResponse.defaultCatalogue(streakDays: viewModel.summary?.streakDays ?? 0)
    }

    private var milestoneSheetBinding: Binding<Bool> {
        if let gamificationViewModel {
            return Binding(
                get: { gamificationViewModel.isMilestoneSheetPresented },
                set: { gamificationViewModel.isMilestoneSheetPresented = $0 }
            )
        }
        return $isFallbackMilestoneSheetPresented
    }

    private var coldStartSheetBinding: Binding<Bool> {
        if let coldStartViewModel {
            return Binding(
                get: { coldStartViewModel.isFlowPresented },
                set: { coldStartViewModel.isFlowPresented = $0 }
            )
        }
        return $isUnusedColdStartSheetPresented
    }

    private var offlineBanner: some View {
        AppNoticeBanner("coach.hub.offline_banner", systemImage: "wifi.slash", tone: .warning)
    }

    private func heroMissionCard(summary: DailyCoachSummaryModel) -> some View {
        AppSurfaceCard {
            HStack {
                Text(LocalizedStringKey("coach.hub.mission_title"))
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                AppStatusBadge(
                    summary.isDownshifted
                        ? LocalizedStringKey("coach.downshift.badge")
                        : LocalizedStringKey("coach.hub.budget_15"),
                    tone: summary.isDownshifted ? .warning : .stepHighlight
                )
            }

            Text(summary.briefingText)
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: 6) {
                ProgressView(
                    value: Double(summary.completedTasksCount),
                    total: Double(max(1, summary.totalTasksCount))
                )
                .tint(ColorTokens.stepHighlight)

                Text("\(summary.completedTasksCount) / \(summary.totalTasksCount)")
                    .font(TypographyTokens.microLabel)
                    .monospacedDigit()
                    .foregroundStyle(ColorTokens.textSecondary)
            }

            if let firstActiveTask = summary.tasks.first(where: { $0.status == .pending }) ?? summary.tasks.first {
                VStack(alignment: .leading, spacing: 6) {
                    Text(LocalizedStringKey("coach.hub.preview_label"))
                        .font(TypographyTokens.caption.weight(.semibold))
                        .foregroundStyle(ColorTokens.textSecondary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        NativeMathLabel(
                            latex: firstActiveTask.promptPreviewLatex,
                            fallback: firstActiveTask.promptPreviewLatex,
                            pointSize: 16
                        )
                        .fixedSize()
                    }
                }
                .padding(SpacingTokens.sm)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                )
            }

            Button {
                viewModel.showingStudyPlanSheet = true
            } label: {
                Label(LocalizedStringKey("coach.hub.start_mission_cta"), systemImage: "play.fill")
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .accessibilityIdentifier("coach.hub.startMission")
        }
    }

    private func coldStartHeroCard(summary: DailyCoachSummaryModel) -> some View {
        AppSurfaceCard {
            HStack {
                Text(LocalizedStringKey("coach.hub.coldstart_title"))
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                AppStatusBadge(LocalizedStringKey("coach.hub.budget_15"), tone: .success)
            }

            Text(summary.briefingText)
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textSecondary)

            if let demo = summary.coldStartDemo {
                VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                    Text(demo.title)
                        .font(TypographyTokens.caption.weight(.semibold))
                        .foregroundStyle(ColorTokens.textPrimary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        NativeMathLabel(
                            latex: demo.samplePromptLatex,
                            fallback: demo.samplePromptLatex,
                            pointSize: 16
                        )
                        .fixedSize()
                    }
                    Text(demo.hintText)
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
                .padding(SpacingTokens.compact)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                )
            }

            Button {
                viewModel.showingStudyPlanSheet = true
            } label: {
                Label(LocalizedStringKey("coach.hub.coldstart_cta"), systemImage: "sparkles")
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .accessibilityIdentifier("coach.hub.startColdStart")
        }
    }

    private var quickCaptureCTA: some View {
        AppSurfaceCard {
            HStack(spacing: SpacingTokens.compact) {
                Image(systemName: "camera.viewfinder")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(ColorTokens.action)
                    .frame(width: 44, height: 44)
                    .background(ColorTokens.actionSoft)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(LocalizedStringKey("coach.hub.capture_title"))
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Text(LocalizedStringKey("coach.hub.capture_subtitle"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
                Spacer()
            }

            Button(action: onStartCapture) {
                Label(LocalizedStringKey("coach.hub.capture_cta"), systemImage: "arrow.up.right")
            }
            .buttonStyle(AppSecondaryButtonStyle())
            .accessibilityIdentifier("coach.hub.quickCapture")
        }
    }

    private func activeTrapRadarSection(items: [TrapRadarPreviewModel]) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.compact) {
            Text(LocalizedStringKey("coach.hub.radar_title"))
                .font(TypographyTokens.cardTitle)
                .foregroundStyle(ColorTokens.textPrimary)

            ForEach(items.prefix(3)) { item in
                AppSurfaceCard(elevated: true) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.title)
                                .font(TypographyTokens.cardTitle)
                                .foregroundStyle(ColorTokens.textPrimary)
                            Text(item.skillCode)
                                .font(TypographyTokens.microLabel.monospaced())
                                .foregroundStyle(ColorTokens.textSecondary)
                        }
                        Spacer()
                        AppStatusBadge(badgeDisplayLabel(item.badgeKey), tone: .action)
                    }
                }
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

private struct RebalanceCoachHubSection: View {
    @ObservedObject var rebalanceViewModel: StudyPlanRebalanceViewModel
    let onPlanAccepted: (StudyPlanRebalanceResponse) -> Void

    var body: some View {
        Group {
            if let status = rebalanceViewModel.recoveryStatus, status.recoveryNeeded {
                HStack(spacing: SpacingTokens.compact) {
                    Image(systemName: "leaf.circle.fill")
                        .font(.title3)
                        .foregroundStyle(ColorTokens.action)
                        .accessibilityHidden(true)

                    VStack(alignment: .leading, spacing: 2) {
                        Text("studyplan.rebalance.no_backlog_badge")
                            .font(TypographyTokens.caption.weight(.semibold))
                            .foregroundStyle(ColorTokens.action)
                        Text(LocalizedStringKey(status.welcomeMessageKey))
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                            .lineLimit(2)
                    }

                    Spacer()

                    Button {
                        rebalanceViewModel.isPresentingRecoverySheet = true
                    } label: {
                        Text("studyplan.rebalance.banner_cta")
                            .font(TypographyTokens.caption.weight(.semibold))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(ColorTokens.action, in: Capsule())
                            .foregroundStyle(ColorTokens.actionForeground)
                    }
                    .accessibilityIdentifier("rebalance.banner.openSheet")
                }
                .padding(SpacingTokens.compact)
                .background(ColorTokens.actionSoft, in: RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                        .stroke(ColorTokens.action.opacity(0.4), lineWidth: 1)
                )
                .accessibilityElement(children: .contain)
            }
        }
        .sheet(isPresented: $rebalanceViewModel.isPresentingRecoverySheet) {
            PlanRebalanceNotificationSheet(
                viewModel: rebalanceViewModel,
                onPlanAccepted: onPlanAccepted
            )
        }
    }
}
