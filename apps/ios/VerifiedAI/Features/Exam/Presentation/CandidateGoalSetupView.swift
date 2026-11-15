import SwiftUI

public struct CandidateGoalSetupView: View {
    @ObservedObject private var viewModel: CandidateProfileViewModel
    private let availableExams: [(code: String, label: String, baseMinutes: Int)] = [
        ("TR_YKS_TYT_MATH", "TR_YKS_TYT_MATH — YKS TYT Math", 65),
        ("TR_YKS_AYT_MATH", "TR_YKS_AYT_MATH — YKS AYT Math", 80),
        ("INTL_SAT_MATH", "INTL_SAT_MATH — Digital SAT Math", 70),
        ("INTL_AP_CALC_BC", "INTL_AP_CALC_BC — AP Calculus BC", 105),
        ("INTL_IB_MATH_AA_HL", "INTL_IB_MATH_AA_HL — IB Math AA HL", 120)
    ]

    @State private var isSyllabusSheetPresented = false
    @FocusState private var isScoreFocused: Bool
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion

    public init(viewModel: CandidateProfileViewModel) {
        self.viewModel = viewModel
    }

    private var minSelectableDate: Date {
        Calendar.current.date(byAdding: .day, value: 1, to: Date()) ?? Date().addingTimeInterval(86400)
    }

    private var selectedExamBaseMinutes: Int {
        availableExams.first(where: { $0.code == viewModel.selectedExamCode })?.baseMinutes ?? 65
    }

    private var effectiveReduceMotion: Bool {
        systemReduceMotion || viewModel.reducedMotionEnabled
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerBanner
                statusAndErrorSection
                examAndDateCard
                studyBudgetCard
                feasibilityPreviewCard
                accommodationsCard
                actionButtonsSection
                horizonHistoryCard
            }
            .padding()
        }
        .scrollDismissesKeyboard(.interactively)
        .background(ColorTokens.canvas.ignoresSafeArea())
        .navigationTitle(Text("exam.candidate.title"))
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(LocalizedStringKey("exam.target.keyboard_done")) {
                    isScoreFocused = false
                }
                .accessibilityIdentifier("candidate.profile.keyboardDone")
            }
        }
        .sheet(isPresented: $isSyllabusSheetPresented) {
            NavigationStack {
                SyllabusSnapToPlanSheet(viewModel: viewModel)
            }
        }
        .task {
            let offline = ProcessInfo.processInfo.arguments.contains("--exam-offline")
            await viewModel.loadProfile(offlineMode: offline)
        }
    }

    @ViewBuilder
    private var headerBanner: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("exam.candidate.subtitle")
                .font(.subheadline)
                .foregroundStyle(ColorTokens.textSecondary)
            if viewModel.isOfflineFallback {
                Label("exam.catalog.offline_banner", systemImage: "icloud.slash")
                    .font(.caption)
                    .foregroundStyle(ColorTokens.warning)
                    .accessibilityIdentifier("candidate.profile.offlineBanner")
            }
        }
    }

    @ViewBuilder
    private var statusAndErrorSection: some View {
        if let errKey = viewModel.errorLocalizationKey {
            Text(LocalizedStringKey(errKey))
                .font(.callout)
                .foregroundStyle(ColorTokens.danger)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(ColorTokens.dangerSoft, in: RoundedRectangle(cornerRadius: RadiusTokens.sm))
                .accessibilityIdentifier("candidate.profile.errorBanner")
        } else if let errMsg = viewModel.errorMessage {
            Text(errMsg)
                .font(.callout)
                .foregroundStyle(.red)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.red.opacity(0.10), in: RoundedRectangle(cornerRadius: 10))
                .accessibilityIdentifier("candidate.profile.errorBanner")
        }

        if let statusKey = viewModel.statusLocalizationKey {
            Label(LocalizedStringKey(statusKey), systemImage: "checkmark.circle.fill")
                .font(.callout)
                .foregroundStyle(.green)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.green.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                .accessibilityIdentifier("candidate.profile.savedBanner")
        }
    }

    private var examAndDateCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("exam.candidate.goal_section")
                .font(.headline)

            Picker(LocalizedStringKey("exam.candidate.exam_picker"), selection: $viewModel.selectedExamCode) {
                ForEach(availableExams, id: \.code) { exam in
                    Text(exam.label).tag(exam.code)
                }
            }
            .pickerStyle(.menu)
            .accessibilityIdentifier("candidate.profile.examPicker")

            DatePicker(
                LocalizedStringKey("exam.target.date_label"),
                selection: $viewModel.targetDate,
                in: minSelectableDate...,
                displayedComponents: [.date]
            )
            .accessibilityIdentifier("candidate.profile.targetDatePicker")

            ViewThatFits {
                HStack {
                    Text("exam.target.score_label")
                    Spacer()
                    scoreField
                }
                VStack(alignment: .leading) {
                    Text("exam.target.score_label")
                    scoreField
                }
            }
        }
        .padding()
        .background(cardBackground, in: RoundedRectangle(cornerRadius: 14))
    }

    private var scoreField: some View {
        TextField("35.5", text: $viewModel.targetScoreText)
            .textFieldStyle(.roundedBorder)
            .frame(maxWidth: 140)
            .focused($isScoreFocused)
            #if os(iOS)
            .keyboardType(.decimalPad)
            #endif
            .accessibilityIdentifier("candidate.profile.targetScoreField")
    }

    private var studyBudgetCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("exam.candidate.budget_section")
                .font(.headline)

            Stepper(value: $viewModel.dailyStudyMinutes, in: 15...960, step: 15) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("exam.candidate.daily_minutes_label")
                    Text("\(viewModel.dailyStudyMinutes) min")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("candidate.profile.dailyMinutesStepper")

            Stepper(value: $viewModel.weeklyStudyMinutes, in: max(15, viewModel.dailyStudyMinutes)...6720, step: 30) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("exam.candidate.weekly_minutes_label")
                    Text("\(viewModel.weeklyStudyMinutes) min")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier("candidate.profile.weeklyMinutesStepper")
        }
        .padding()
        .background(cardBackground, in: RoundedRectangle(cornerRadius: 14))
    }

    private var feasibilityPreviewCard: some View {
        let status = viewModel.savedProfile?.feasibilityStatus ?? viewModel.previewFeasibilityStatus
        let requiredDaily = viewModel.savedProfile?.requiredDailyMinutes ?? viewModel.previewRequiredDailyMinutes
        let effectiveDaily = viewModel.savedProfile?.effectiveDailyCapacityMinutes ?? viewModel.effectiveDailyCapacityMinutes
        let days = viewModel.savedProfile?.daysUntilExam ?? viewModel.previewDaysUntilExam

        return VStack(alignment: .leading, spacing: 8) {
            ViewThatFits {
                HStack {
                    Text("exam.candidate.feasibility_title")
                        .font(.headline)
                    Spacer()
                    feasibilityBadge(status)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("exam.candidate.feasibility_title")
                        .font(.headline)
                    feasibilityBadge(status)
                }
            }

            Text("Required ~\(requiredDaily) min/day vs capacity \(effectiveDaily) min/day (\(days) days remaining)")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier("candidate.profile.feasibilitySummary")

            Text("exam.target.zero_cost_note")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .padding()
        .background(feasibilityTint(status).opacity(0.10), in: RoundedRectangle(cornerRadius: 14))
    }

    private func feasibilityBadge(_ status: CandidateFeasibilityStatus) -> some View {
        Text(LocalizedStringKey("exam.candidate.feasibility.\(status.rawValue)"))
            .font(.caption.bold())
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .foregroundStyle(feasibilityTint(status))
            .background(feasibilityTint(status).opacity(0.16), in: Capsule())
            .accessibilityIdentifier("candidate.profile.feasibilityBadge")
    }

    private func feasibilityTint(_ status: CandidateFeasibilityStatus) -> Color {
        switch status {
        case .optimal: return ColorTokens.success
        case .stretch: return ColorTokens.stepHighlight
        case .atRisk: return ColorTokens.warning
        case .unrealistic: return ColorTokens.danger
        }
    }

    private var accommodationsCard: some View {
        let adjusted = viewModel.adjustedDurationMinutes(baseMinutes: selectedExamBaseMinutes)

        return VStack(alignment: .leading, spacing: 12) {
            Text("exam.candidate.accommodations_section")
                .font(.headline)

            Picker(LocalizedStringKey("exam.candidate.extra_time_label"), selection: $viewModel.extraTimeMultiplier) {
                Text("1.00x").tag(1.00)
                Text("1.25x").tag(1.25)
                Text("1.50x").tag(1.50)
                Text("2.00x").tag(2.00)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("candidate.profile.extraTimePicker")

            Text("Adjusted section timer: \(adjusted) min (base \(selectedExamBaseMinutes) min)")
                .font(.caption.monospacedDigit())
                .foregroundStyle(ColorTokens.textSecondary)
                .accessibilityIdentifier("candidate.profile.adjustedTimerLabel")

            Toggle(isOn: $viewModel.highContrastEnabled) {
                Text("exam.candidate.high_contrast")
            }
            .accessibilityIdentifier("candidate.profile.highContrastToggle")

            Toggle(isOn: $viewModel.screenReaderOptimized) {
                Text("exam.candidate.screen_reader")
            }
            .accessibilityIdentifier("candidate.profile.screenReaderToggle")

            Toggle(isOn: $viewModel.reducedMotionEnabled) {
                Text("exam.candidate.reduced_motion")
            }
            .accessibilityIdentifier("candidate.profile.reducedMotionToggle")
        }
        .padding()
        .background(cardBackground, in: RoundedRectangle(cornerRadius: RadiusTokens.md))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.md)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
    }

    private var actionButtonsSection: some View {
        VStack(spacing: 12) {
            Button {
                isScoreFocused = false
                Task { await viewModel.saveProfile() }
            } label: {
                HStack {
                    if viewModel.isSaving {
                        ProgressView()
                    }
                    Text("exam.candidate.save_button")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(ColorTokens.action)
            .disabled(viewModel.isSaving)
            .animation(effectiveReduceMotion ? nil : .easeInOut(duration: 0.2), value: viewModel.isSaving)
            .accessibilityIdentifier("candidate.profile.saveButton")

            Button {
                isScoreFocused = false
                isSyllabusSheetPresented = true
            } label: {
                Label(LocalizedStringKey("exam.syllabus.open_sheet_button"), systemImage: "doc.viewfinder")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .tint(ColorTokens.stepHighlight)
            .accessibilityIdentifier("candidate.profile.openSyllabusSheetButton")
        }
    }

    @ViewBuilder
    private var horizonHistoryCard: some View {
        if !viewModel.historyEntries.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("exam.candidate.history_title")
                    .font(.headline)
                    .foregroundStyle(ColorTokens.textPrimary)
                ForEach(viewModel.historyEntries.prefix(5)) { item in
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(item.examCode) • \(item.newTargetDate)")
                            .font(.subheadline.bold().monospacedDigit())
                            .foregroundStyle(ColorTokens.textPrimary)
                        Text("\(item.newDailyMinutes) min/d • \(item.changeReason) • \(item.feasibilityStatus.rawValue)")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                    Divider()
                }
            }
            .padding()
            .background(cardBackground, in: RoundedRectangle(cornerRadius: RadiusTokens.md))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.md)
                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
            )
            .accessibilityIdentifier("candidate.profile.historyCard")
        }
    }

    private var cardBackground: Color {
        viewModel.highContrastEnabled
            ? ColorTokens.surfacePressed
            : ColorTokens.surfaceElevated
    }
}
