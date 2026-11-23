import SwiftUI

enum MainTab: String, Hashable, CaseIterable {
    case solve
    case plan
    case exams
    case progress
}

/// 4-Tab Astra-style flat editorial shell replacing the single scrolling placeholder screen.
struct MainTabView: View {
    let learnerId: UUID
    let environmentName: String
    let launchState: AppLaunchState
    let entitlement: Entitlement?
    let entitlementMessage: String?
    let retry: () -> Void
    let startProblemCapture: () -> Void
    let problemHistoryViewModel: ProblemHistoryViewModel?
    let manageSubscription: () -> Void
    let manageAccount: () -> Void
    let signOut: () -> Void
    let isSolveFixture: Bool
    let makeSolveModel: ((UUID) -> PrimarySolveViewModel)?
    let reviewViewModel: ProblemReviewViewModel?
    let coldStartViewModel: ColdStartDiagnosticViewModel?
    let examViewModel: ExamCatalogAndTargetViewModel
    let candidateProfileViewModel: CandidateProfileViewModel
    let blueprintAssemblyViewModel: ExamBlueprintAssemblyViewModel
    let weeklyReportViewModel: WeeklyProgressReportViewModel
    @Binding var selectedTab: MainTab

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                HomePlaceholderView(
                    environmentName: environmentName,
                    launchState: launchState,
                    entitlement: entitlement,
                    entitlementMessage: entitlementMessage,
                    retry: retry,
                    startProblemCapture: startProblemCapture,
                    problemHistoryViewModel: problemHistoryViewModel,
                    manageSubscription: manageSubscription,
                    manageAccount: manageAccount,
                    isSolveFixture: isSolveFixture,
                    makeSolveModel: makeSolveModel,
                    reviewViewModel: reviewViewModel,
                    coldStartViewModel: coldStartViewModel,
                    examViewModel: examViewModel,
                    candidateProfileViewModel: candidateProfileViewModel,
                    blueprintAssemblyViewModel: blueprintAssemblyViewModel
                )
                .toolbar(.hidden, for: .navigationBar)
            }
            .tabItem {
                Label(LocalizedStringKey("tab.solve"), systemImage: "viewfinder")
            }
            .tag(MainTab.solve)

            NavigationStack {
                PlanWorkspaceView(
                    learnerId: learnerId,
                    coldStartViewModel: coldStartViewModel,
                    examViewModel: examViewModel,
                    candidateProfileViewModel: candidateProfileViewModel,
                    blueprintAssemblyViewModel: blueprintAssemblyViewModel,
                    startProblemCapture: startProblemCapture
                )
                .navigationTitle(LocalizedStringKey("tab.plan"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { accountToolbar }
            }
            .tabItem {
                Label(LocalizedStringKey("tab.plan"), systemImage: "calendar")
            }
            .tag(MainTab.plan)

            NavigationStack {
                ExamsWorkspaceView(
                    examViewModel: examViewModel,
                    candidateProfileViewModel: candidateProfileViewModel,
                    blueprintAssemblyViewModel: blueprintAssemblyViewModel
                )
                .navigationTitle(LocalizedStringKey("tab.exams"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { accountToolbar }
            }
            .tabItem {
                Label(LocalizedStringKey("tab.exams"), systemImage: "checklist")
            }
            .tag(MainTab.exams)

            NavigationStack {
                ProgressWorkspaceView(
                    weeklyReportViewModel: weeklyReportViewModel,
                    onJumpToSolve: { selectedTab = .solve }
                )
                .navigationTitle(LocalizedStringKey("tab.progress"))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { accountToolbar }
            }
            .tabItem {
                Label(LocalizedStringKey("tab.progress"), systemImage: "chart.bar.xaxis")
            }
            .tag(MainTab.progress)
        }
        .tint(ColorTokens.action)
    }

    @ToolbarContentBuilder
    private var accountToolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Button(action: manageAccount) {
                Image(systemName: "person.circle")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(ColorTokens.textPrimary)
            }
            .accessibilityLabel("Account")
            .accessibilityIdentifier("root.accountButton")
        }
    }
}

private enum PlanWorkspaceSection: Hashable {
    case coach
    case goals
}

private struct PlanWorkspaceView: View {
    let learnerId: UUID
    var coldStartViewModel: ColdStartDiagnosticViewModel?
    @ObservedObject var examViewModel: ExamCatalogAndTargetViewModel
    @ObservedObject var candidateProfileViewModel: CandidateProfileViewModel
    @ObservedObject var blueprintAssemblyViewModel: ExamBlueprintAssemblyViewModel
    let startProblemCapture: () -> Void

    @StateObject private var dailyCoachViewModel: DailyCoachViewModel
    @StateObject private var practiceViewModel: PracticeSessionViewModel
    @State private var selectedSection: PlanWorkspaceSection = .coach
    @State private var isPracticeSheetPresented = false

    init(
        learnerId: UUID,
        coldStartViewModel: ColdStartDiagnosticViewModel?,
        examViewModel: ExamCatalogAndTargetViewModel,
        candidateProfileViewModel: CandidateProfileViewModel,
        blueprintAssemblyViewModel: ExamBlueprintAssemblyViewModel,
        startProblemCapture: @escaping () -> Void
    ) {
        self.learnerId = learnerId
        self.coldStartViewModel = coldStartViewModel
        self.examViewModel = examViewModel
        self.candidateProfileViewModel = candidateProfileViewModel
        self.blueprintAssemblyViewModel = blueprintAssemblyViewModel
        self.startProblemCapture = startProblemCapture
        _dailyCoachViewModel = StateObject(wrappedValue: DailyCoachViewModel(userId: learnerId))
        _practiceViewModel = StateObject(wrappedValue: PracticeSessionViewModel(userId: learnerId))
    }

    var body: some View {
        VStack(spacing: 0) {
            AppSegmentedPill(
                selection: $selectedSection,
                options: [
                    .init(selection: .coach, title: "Daily Coach", systemImage: "sparkles"),
                    .init(selection: .goals, title: "Budget & Drills", systemImage: "slider.horizontal.3")
                ],
                accessibilityPrefix: "plan.section"
            )
            .padding(.horizontal, SpacingTokens.lg)
            .padding(.top, SpacingTokens.sm)
            .padding(.bottom, SpacingTokens.compact)
            .background(ColorTokens.canvas)

            Divider()
                .overlay(ColorTokens.borderSubtle)

            switch selectedSection {
            case .coach:
                CoachHubView(
                    viewModel: dailyCoachViewModel,
                    coldStartViewModel: coldStartViewModel,
                    onStartCapture: startProblemCapture,
                    onOpenTask: { task in
                        launchPractice(skillCode: task.targetSkillCode)
                    }
                )
            case .goals:
                goalsAndActionsView
            }
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
        .task(id: learnerId) {
            if dailyCoachViewModel.summary == nil {
                await dailyCoachViewModel.loadTodaySummary()
            }
        }
        .sheet(isPresented: coldStartBinding) {
            if let coldStartViewModel {
                ColdStartDiagnosticFlowView(viewModel: coldStartViewModel)
            }
        }
        .sheet(isPresented: $isPracticeSheetPresented) {
            PracticeSessionView(viewModel: practiceViewModel)
                .presentationDragIndicator(.visible)
        }
    }

    private var goalsAndActionsView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                if let coldStartViewModel, !coldStartViewModel.isFeatureDisabled {
                    ColdStartDiagnosticCardView(viewModel: coldStartViewModel)
                }

                VStack(alignment: .leading, spacing: SpacingTokens.compact) {
                    AppSectionHeader("Daily Budget & Target", subtitle: "Structured around your exam horizon") {
                        if let activeTarget = examViewModel.primaryActiveTarget {
                            AppStatusBadge(verbatimTitle: activeTarget.examCode, systemImage: "scope", tone: .stepHighlight)
                        }
                    }

                    HStack(spacing: SpacingTokens.sm) {
                        AppMetricTile(
                            title: "Daily Goal",
                            value: "\(candidateProfileViewModel.dailyStudyMinutes)m",
                            systemImage: "clock",
                            tone: .stepHighlight
                        )
                        AppMetricTile(
                            title: "Weekly",
                            value: "\(candidateProfileViewModel.weeklyStudyMinutes)m",
                            systemImage: "calendar",
                            tone: .neutral
                        )
                        AppMetricTile(
                            title: "Timing",
                            value: String(format: "%.2fx", candidateProfileViewModel.extraTimeMultiplier),
                            systemImage: "timer",
                            tone: .warning
                        )
                    }

                    NavigationLink {
                        CandidateGoalSetupView(viewModel: candidateProfileViewModel)
                    } label: {
                        HStack {
                            Label("Adjust Study Budget & Syllabus", systemImage: "slider.horizontal.3")
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(TypographyTokens.caption)
                        }
                    }
                    .buttonStyle(AppSecondaryButtonStyle())
                }

                VStack(alignment: .leading, spacing: SpacingTokens.compact) {
                    AppSectionHeader("Quick Actions")
                    Button {
                        launchPractice(skillCode: "MATH.ALG.RATIONAL_EQ")
                    } label: {
                        Label("Start Focused 1-on-1 Practice", systemImage: "bolt.shield")
                    }
                    .buttonStyle(AppPrimaryButtonStyle())

                    Button(action: startProblemCapture) {
                        Label("Scan a Problem Now", systemImage: "camera.viewfinder")
                    }
                    .buttonStyle(AppSecondaryButtonStyle())

                    NavigationLink {
                        ExamBlueprintAssemblyView(viewModel: blueprintAssemblyViewModel)
                    } label: {
                        HStack {
                            Label("Launch Timed Diagnostic Drill", systemImage: "play.circle")
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(TypographyTokens.caption)
                        }
                    }
                    .buttonStyle(AppSecondaryButtonStyle())
                }
            }
            .padding(SpacingTokens.lg)
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
    }

    private func launchPractice(skillCode: String) {
        if dailyCoachViewModel.showingStudyPlanSheet {
            dailyCoachViewModel.showingStudyPlanSheet = false
        }
        isPracticeSheetPresented = true
        Task {
            await practiceViewModel.loadOrCreateSession(targetSkillId: skillCode)
        }
    }

    private var coldStartBinding: Binding<Bool> {
        Binding(
            get: { coldStartViewModel?.isFlowPresented ?? false },
            set: { newValue in coldStartViewModel?.isFlowPresented = newValue }
        )
    }
}

private enum ProgressWorkspaceSection: Hashable {
    case report
    case mastery
    case traps
}

private struct ProgressWorkspaceView: View {
    @ObservedObject var weeklyReportViewModel: WeeklyProgressReportViewModel
    let onJumpToSolve: () -> Void

    @State private var selectedSection: ProgressWorkspaceSection = .report

    var body: some View {
        VStack(spacing: 0) {
            AppSegmentedPill(
                selection: $selectedSection,
                options: [
                    .init(selection: .report, title: "Report", systemImage: "doc.text.magnifyingglass"),
                    .init(selection: .mastery, title: "Mastery", systemImage: "circle.grid.cross"),
                    .init(selection: .traps, title: "Trap Radar", systemImage: "shield.lefthalf.filled")
                ],
                accessibilityPrefix: "progress.section"
            )
            .padding(.horizontal, SpacingTokens.lg)
            .padding(.top, SpacingTokens.sm)
            .padding(.bottom, SpacingTokens.compact)
            .background(ColorTokens.canvas)

            Divider()
                .overlay(ColorTokens.borderSubtle)

            switch selectedSection {
            case .report:
                WeeklyProgressReportView(viewModel: weeklyReportViewModel)
            case .mastery:
                telemetryUnavailableView(
                    systemImage: "circle.grid.cross",
                    titleKey: "Mastery Graph Sync Pending",
                    subtitleKey: "Skill Retention and Transfer rings sync once your backend Mastery Graph client feed is provisioned on this build. Your weekly mastery momentum remains available in Report."
                )
            case .traps:
                telemetryUnavailableView(
                    systemImage: "shield.lefthalf.filled.badge.checkmark",
                    titleKey: "Mistake Passport Sync Pending",
                    subtitleKey: "Recurring exam trap telemetry connects when the Mistake Passport API feed is enabled. Scan a worked attempt in Solve to analyze steps immediately."
                )
            }
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
    }

    private func telemetryUnavailableView(
        systemImage: String,
        titleKey: String,
        subtitleKey: String
    ) -> some View {
        ScrollView {
            VStack(spacing: SpacingTokens.lg) {
                AppEmptyState(
                    systemImage: systemImage,
                    title: LocalizedStringKey(stringLiteral: titleKey),
                    subtitle: LocalizedStringKey(stringLiteral: subtitleKey)
                )
                Button("Open Weekly Report") {
                    selectedSection = .report
                }
                .buttonStyle(AppPrimaryButtonStyle())

                Button("Go to Solve Tab", action: onJumpToSolve)
                    .buttonStyle(AppSecondaryButtonStyle())
            }
            .padding(SpacingTokens.lg)
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
    }
}

private struct ExamsWorkspaceView: View {
    @ObservedObject var examViewModel: ExamCatalogAndTargetViewModel
    @ObservedObject var candidateProfileViewModel: CandidateProfileViewModel
    @ObservedObject var blueprintAssemblyViewModel: ExamBlueprintAssemblyViewModel
    @State private var isCalibrationSheetPresented = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                HStack(spacing: SpacingTokens.sm) {
                    AppMetricTile(
                        title: "Active Target",
                        value: examViewModel.primaryActiveTarget?.examCode ?? candidateProfileViewModel.selectedExamCode,
                        systemImage: "flag",
                        tone: .stepHighlight
                    )
                    AppMetricTile(
                        title: "Blueprints",
                        value: "\(blueprintAssemblyViewModel.blueprints.count)",
                        systemImage: "square.stack.3d.up",
                        tone: .neutral
                    )
                }

                VStack(spacing: SpacingTokens.compact) {
                    examStepLink(
                        step: "1",
                        titleKey: "home.exam_catalog.card_title",
                        subtitleKey: "home.exam_catalog.card_subtitle",
                        systemImage: "checklist"
                    ) {
                        ExamCatalogAndTargetView(
                            viewModel: examViewModel,
                            candidateProfileViewModel: candidateProfileViewModel,
                            blueprintAssemblyViewModel: blueprintAssemblyViewModel
                        )
                    }
                    .accessibilityIdentifier("home.examCatalog.button")

                    examStepLink(
                        step: "2",
                        titleKey: "home.candidate_profile.card_title",
                        subtitleKey: "home.candidate_profile.card_subtitle",
                        systemImage: "calendar.badge.clock"
                    ) {
                        CandidateGoalSetupView(viewModel: candidateProfileViewModel)
                    }
                    .accessibilityIdentifier("home.candidateProfile.button")

                    examStepLink(
                        step: "3",
                        titleKey: "exam.assembly.title",
                        subtitleKey: "exam.assembly.blueprint.section_header",
                        systemImage: "square.stack.3d.up.badge.automatic"
                    ) {
                        ExamBlueprintAssemblyView(viewModel: blueprintAssemblyViewModel)
                    }
                    .accessibilityIdentifier("home.blueprintAssembly.button")

                    examStepLink(
                        step: "4",
                        titleKey: "exam.readiness.title",
                        subtitleKey: "exam.readiness.subtitle",
                        systemImage: "chart.line.uptrend.xyaxis"
                    ) {
                        ExamReadinessHubView(
                            viewModel: examViewModel.makeReadinessViewModel(),
                            onStartCalibration: {
                                isCalibrationSheetPresented = true
                            }
                        )
                    }
                    .accessibilityIdentifier("home.examReadiness.button")
                }
            }
            .padding(SpacingTokens.lg)
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
        .sheet(isPresented: $isCalibrationSheetPresented) {
            NavigationStack {
                ExamBlueprintAssemblyView(viewModel: blueprintAssemblyViewModel)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Done") {
                                isCalibrationSheetPresented = false
                            }
                        }
                    }
            }
        }
    }

    private func examStepLink<Destination: View>(
        step: String,
        titleKey: String,
        subtitleKey: String,
        systemImage: String,
        @ViewBuilder destination: () -> Destination
    ) -> some View {
        NavigationLink {
            destination()
        } label: {
            AppSurfaceCard {
                HStack(alignment: .center, spacing: SpacingTokens.compact) {
                    Text(step)
                        .font(TypographyTokens.metricValue)
                        .foregroundStyle(ColorTokens.stepHighlight)
                        .frame(width: 36, height: 36)
                        .background(ColorTokens.stepHighlightSoft)
                        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))

                    VStack(alignment: .leading, spacing: 3) {
                        Label(LocalizedStringKey(titleKey), systemImage: systemImage)
                            .font(TypographyTokens.cardTitle)
                            .foregroundStyle(ColorTokens.textPrimary)
                        Text(LocalizedStringKey(subtitleKey))
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                            .multilineTextAlignment(.leading)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textTertiary)
                }
            }
        }
        .buttonStyle(.plain)
    }
}
