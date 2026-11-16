import SwiftUI

public struct ExamCatalogAndTargetView: View {
    @ObservedObject public var viewModel: ExamCatalogAndTargetViewModel
    public var candidateProfileViewModel: CandidateProfileViewModel?
    public var blueprintAssemblyViewModel: ExamBlueprintAssemblyViewModel?
    public var readinessViewModel: ExamReadinessViewModel?
    @FocusState private var isScoreFieldFocused: Bool
    @State private var isCalibrationSheetPresented = false

    public init(
        viewModel: ExamCatalogAndTargetViewModel,
        candidateProfileViewModel: CandidateProfileViewModel? = nil,
        blueprintAssemblyViewModel: ExamBlueprintAssemblyViewModel? = nil,
        readinessViewModel: ExamReadinessViewModel? = nil
    ) {
        self.viewModel = viewModel
        self.candidateProfileViewModel = candidateProfileViewModel
        self.blueprintAssemblyViewModel = blueprintAssemblyViewModel
        self.readinessViewModel = readinessViewModel
    }

    private var languageCode: String? {
        Locale.current.language.languageCode?.identifier
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                headerSection

                if viewModel.isOfflineBannerVisible {
                    offlineBanner
                }

                if viewModel.isFeatureDisabled {
                    featureDisabledCard
                } else {
                    jurisdictionPicker

                    if viewModel.isLoading && viewModel.definitions.isEmpty {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 24)
                    } else {
                        catalogListSection
                    }

                    if let detail = viewModel.selectedDetail {
                        topicWeightsSection(detail: detail)
                        targetBindingSection(detail: detail)
                    }

                    if let activeTarget = viewModel.primaryActiveTarget {
                        activeTargetReadinessCard(target: activeTarget)
                    }

                    if let candidateProfileViewModel {
                        NavigationLink {
                            CandidateGoalSetupView(viewModel: candidateProfileViewModel)
                        } label: {
                            HStack {
                                Label(LocalizedStringKey("exam.catalog.open_candidate_profile"), systemImage: "calendar.badge.clock")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(ColorTokens.textPrimary)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(ColorTokens.textSecondary)
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity)
                            .background(ColorTokens.surfaceElevated)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                        .accessibilityIdentifier("exam.catalog.openCandidateProfileButton")
                    }

                    if let blueprintAssemblyViewModel {
                        NavigationLink {
                            ExamBlueprintAssemblyView(viewModel: blueprintAssemblyViewModel)
                        } label: {
                            HStack {
                                Label(LocalizedStringKey("exam.assembly.title"), systemImage: "square.stack.3d.up.badge.automatic")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(ColorTokens.textPrimary)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(ColorTokens.textSecondary)
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity)
                            .background(ColorTokens.surfaceElevated)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                        .accessibilityIdentifier("exam.catalog.openBlueprintAssemblyButton")
                    }

                    NavigationLink {
                        ExamReadinessHubView(
                            viewModel: readinessViewModel ?? viewModel.makeReadinessViewModel(),
                            onStartCalibration: blueprintAssemblyViewModel == nil ? nil : {
                                isCalibrationSheetPresented = true
                            }
                        )
                    } label: {
                        HStack {
                            Label(LocalizedStringKey("exam.readiness.open_from_hub"), systemImage: "chart.line.uptrend.xyaxis")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(ColorTokens.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(ColorTokens.textSecondary)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity)
                        .background(ColorTokens.surfaceElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                        )
                    }
                    .accessibilityIdentifier("exam.hub.openReadinessButton")
                }
            }
            .padding(16)
        }
        .scrollDismissesKeyboard(.immediately)
        .background(ColorTokens.background.ignoresSafeArea())
        .navigationTitle(LocalizedStringKey("exam.catalog.title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(LocalizedStringKey("exam.target.keyboard_done")) {
                    isScoreFieldFocused = false
                }
                .accessibilityIdentifier("exam.target.keyboardDone")
            }
        }
        .task {
            if viewModel.definitions.isEmpty {
                await viewModel.loadCatalogAndTargets()
            }
        }
        .sheet(isPresented: $isCalibrationSheetPresented) {
            if let blueprintAssemblyViewModel {
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
    }

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(LocalizedStringKey("exam.catalog.title"))
                .font(.title2.weight(.bold))
                .foregroundStyle(ColorTokens.textPrimary)
            Text(LocalizedStringKey("exam.catalog.subtitle"))
                .font(.subheadline)
                .foregroundStyle(ColorTokens.textSecondary)
        }
    }

    private var offlineBanner: some View {
        HStack(spacing: SpacingTokens.xs) {
            Image(systemName: "wifi.slash")
                .font(.caption.weight(.semibold))
                .foregroundStyle(ColorTokens.accent)
            Text(LocalizedStringKey("exam.catalog.offline_banner"))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, SpacingTokens.compact)
        .padding(.vertical, SpacingTokens.sm)
        .background(ColorTokens.accentSoft)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke(ColorTokens.accent.opacity(0.28), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.offline.banner")
    }

    private var featureDisabledCard: some View {
        Label(LocalizedStringKey("exam.catalog.feature_disabled"), systemImage: "pause.circle")
            .font(TypographyTokens.cardTitle)
            .foregroundStyle(ColorTokens.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(SpacingTokens.md)
            .background(ColorTokens.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
            )
    }

    private var jurisdictionPicker: some View {
        HStack(spacing: SpacingTokens.xs) {
            jurisdictionChip(titleKey: "exam.catalog.filter.all", jurisdiction: nil)
            jurisdictionChip(titleKey: "exam.catalog.filter.tr", jurisdiction: .tr)
            jurisdictionChip(titleKey: "exam.catalog.filter.intl", jurisdiction: .intl)
        }
    }

    private func jurisdictionChip(titleKey: String, jurisdiction: ExamJurisdiction?) -> some View {
        let isSelected = viewModel.selectedJurisdiction == jurisdiction
        return Button {
            isScoreFieldFocused = false
            Task {
                await viewModel.applyJurisdictionFilter(jurisdiction)
            }
        } label: {
            Text(LocalizedStringKey(titleKey))
                .font(TypographyTokens.caption.weight(.semibold))
                .padding(.horizontal, SpacingTokens.compact)
                .frame(minHeight: 40)
                .background(isSelected ? ColorTokens.action : ColorTokens.surfaceElevated)
                .foregroundStyle(isSelected ? ColorTokens.actionForeground : ColorTokens.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(isSelected ? ColorTokens.action : ColorTokens.borderSubtle, lineWidth: 1)
                )
        }
    }

    private var catalogListSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(viewModel.definitions) { def in
                let isSelected = viewModel.selectedDetail?.examCode == def.examCode
                Button {
                    isScoreFieldFocused = false
                    Task {
                        await viewModel.selectExam(def)
                    }
                } label: {
                    VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(def.localizedTitle(languageCode: languageCode))
                                    .font(TypographyTokens.cardTitle)
                                    .foregroundStyle(ColorTokens.textPrimary)
                                    .multilineTextAlignment(.leading)
                                Text("\(def.jurisdiction.rawValue) • \(def.examFamily.rawValue) • v\(def.examVersion)")
                                    .font(TypographyTokens.caption.weight(.medium))
                                    .monospacedDigit()
                                    .foregroundStyle(ColorTokens.textSecondary)
                            }
                            Spacer()
                            if isSelected {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(ColorTokens.action)
                            }
                        }

                        HStack(spacing: SpacingTokens.compact) {
                            Text(String(format: NSLocalizedString("exam.catalog.questions_count", comment: ""), def.totalQuestions))
                                .font(TypographyTokens.microLabel)
                                .monospacedDigit()
                            Text(String(format: NSLocalizedString("exam.catalog.minutes_count", comment: ""), def.durationMinutes))
                                .font(TypographyTokens.microLabel)
                                .monospacedDigit()
                            Text(LocalizedStringKey(def.scoringFormulaType.localizationKey))
                                .font(TypographyTokens.microLabel)
                                .lineLimit(1)
                        }
                        .foregroundStyle(ColorTokens.textTertiary)
                    }
                    .padding(SpacingTokens.md)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(isSelected ? ColorTokens.actionSoft : ColorTokens.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                            .stroke(isSelected ? ColorTokens.action : ColorTokens.borderSubtle, lineWidth: isSelected ? 1.5 : 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
                }
                .accessibilityIdentifier("exam.card.\(def.examCode)")
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.catalog.list")
    }

    private func topicWeightsSection(detail: ExamDefinitionModel) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(LocalizedStringKey("exam.catalog.topic_weights_title"))
                    .font(.headline)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text("v\(detail.examVersion)")
                    .font(.caption.monospaced())
                    .foregroundStyle(ColorTokens.textSecondary)
            }

            Text("\(NSLocalizedString("exam.catalog.hash_label", comment: "")) \(String(detail.blueprintHash.prefix(12)))…")
                .font(.caption2.monospaced())
                .foregroundStyle(ColorTokens.textTertiary)

            ForEach(detail.topicWeights) { weight in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(weight.skillId)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(ColorTokens.textPrimary)
                        Text("\(weight.sectionCode) • \(weight.domainCode)")
                            .font(.caption2)
                            .foregroundStyle(ColorTokens.textTertiary)
                    }
                    Spacer()
                    Text("\(Int(weight.questionCountWeight.rounded())) (\(weight.minQuestions)–\(weight.maxQuestions))")
                        .font(.caption.monospacedDigit().weight(.semibold))
                        .foregroundStyle(ColorTokens.action)
                }
                .padding(.vertical, 4)
            }
        }
        .padding(16)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.topicWeights.section")
    }

    private func targetBindingSection(detail: ExamDefinitionModel) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(LocalizedStringKey("exam.target.section_title"))
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)

            VStack(alignment: .leading, spacing: 6) {
                Text("\(NSLocalizedString("exam.target.score_label", comment: "")) (\(Int(detail.minScore)) – \(Int(detail.maxScore)))")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(ColorTokens.textSecondary)

                TextField("35", text: $viewModel.targetScoreInput)
                    .keyboardType(.decimalPad)
                    .focused($isScoreFieldFocused)
                    .padding(12)
                    .background(ColorTokens.background)
                    .foregroundStyle(ColorTokens.textPrimary)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .accessibilityIdentifier("exam.target.scoreField")
            }

            DatePicker(
                LocalizedStringKey("exam.target.date_label"),
                selection: $viewModel.targetDateInput,
                displayedComponents: .date
            )
            .foregroundStyle(ColorTokens.textSecondary)

            if let errorKey = viewModel.errorMessageKey {
                VStack(alignment: .leading, spacing: 2) {
                    Text(LocalizedStringKey(errorKey))
                        .font(.caption.weight(.medium))
                        .foregroundStyle(ColorTokens.warning)
                    if let details = viewModel.errorDetailsText {
                        Text(details)
                            .font(.caption2)
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                }
            }

            if let statusKey = viewModel.statusMessageKey {
                Text(LocalizedStringKey(statusKey))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.accent)
                    .accessibilityIdentifier("exam.target.savedBanner")
            }

            Button {
                isScoreFieldFocused = false
                Task {
                    await viewModel.savePrimaryTarget()
                }
            } label: {
                Group {
                    if viewModel.isSaving {
                        Text(LocalizedStringKey("exam.target.saving_button"))
                    } else {
                        Text(LocalizedStringKey("exam.target.save_button"))
                    }
                }
                .font(TypographyTokens.bodyMedium.weight(.semibold))
                .foregroundStyle(ColorTokens.actionForeground)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(ColorTokens.action)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isSaving || viewModel.isOfflineBannerVisible)
            .accessibilityIdentifier("exam.target.saveButton")
        }
        .padding(SpacingTokens.md)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
    }

    private func activeTargetReadinessCard(target: StudentExamTargetModel) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            HStack {
                Text(LocalizedStringKey("exam.target.readiness_gap_title"))
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text(LocalizedStringKey("exam.target.active_badge"))
                    .font(TypographyTokens.microLabel)
                    .foregroundStyle(ColorTokens.action)
                    .padding(.horizontal, SpacingTokens.xs)
                    .padding(.vertical, SpacingTokens.xxs)
                    .background(ColorTokens.actionSoft)
                    .clipShape(Capsule())
                    .overlay(
                        Capsule()
                            .stroke(ColorTokens.action.opacity(0.28), lineWidth: 1)
                    )
            }

            Text("\(target.examCode) v\(target.examVersion) • \(target.targetDate)")
                .font(TypographyTokens.caption.weight(.medium))
                .monospacedDigit()
                .foregroundStyle(ColorTokens.textSecondary)

            HStack {
                Text(String(format: "%@: %.1f", NSLocalizedString("exam.target.estimated_score_label", comment: ""), target.readinessGapPreview.currentEstimatedScore))
                    .font(TypographyTokens.caption.monospacedDigit())
                Spacer()
                Text(String(format: "%@: %+.1f", NSLocalizedString("exam.target.gap_points_label", comment: ""), target.readinessGapPreview.gapPoints))
                    .font(TypographyTokens.caption.monospacedDigit().weight(.semibold))
                    .foregroundStyle(ColorTokens.action)
            }
            .foregroundStyle(ColorTokens.textPrimary)

            Text(LocalizedStringKey("exam.target.zero_cost_note"))
                .font(TypographyTokens.microLabel)
                .foregroundStyle(ColorTokens.textTertiary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(SpacingTokens.md)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
    }
}
