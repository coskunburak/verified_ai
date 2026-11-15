import SwiftUI

public struct ExamCatalogAndTargetView: View {
    @ObservedObject public var viewModel: ExamCatalogAndTargetViewModel
    public var candidateProfileViewModel: CandidateProfileViewModel?
    @FocusState private var isScoreFieldFocused: Bool

    public init(
        viewModel: ExamCatalogAndTargetViewModel,
        candidateProfileViewModel: CandidateProfileViewModel? = nil
    ) {
        self.viewModel = viewModel
        self.candidateProfileViewModel = candidateProfileViewModel
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
        HStack(spacing: 10) {
            Image(systemName: "wifi.slash")
                .foregroundStyle(ColorTokens.warning)
            Text(LocalizedStringKey("exam.catalog.offline_banner"))
                .font(.caption.weight(.medium))
                .foregroundStyle(ColorTokens.warning)
            Spacer()
        }
        .padding(12)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.offline.banner")
    }

    private var featureDisabledCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(LocalizedStringKey("exam.catalog.feature_disabled"), systemImage: "pause.circle")
                .font(.headline)
                .foregroundStyle(ColorTokens.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private var jurisdictionPicker: some View {
        HStack(spacing: 8) {
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
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 14)
                .frame(minHeight: 40)
                .background(isSelected ? ColorTokens.action : ColorTokens.surfaceElevated)
                .foregroundStyle(isSelected ? ColorTokens.background : ColorTokens.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
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
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(def.localizedTitle(languageCode: languageCode))
                                    .font(.headline)
                                    .foregroundStyle(ColorTokens.textPrimary)
                                    .multilineTextAlignment(.leading)
                                Text("\(def.jurisdiction.rawValue) • \(def.examFamily.rawValue) • v\(def.examVersion)")
                                    .font(.caption.weight(.medium))
                                    .foregroundStyle(ColorTokens.textSecondary)
                            }
                            Spacer()
                            if isSelected {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(ColorTokens.accent)
                            }
                        }

                        HStack(spacing: 12) {
                            Text(String(format: NSLocalizedString("exam.catalog.questions_count", comment: ""), def.totalQuestions))
                                .font(.caption2.weight(.semibold))
                            Text(String(format: NSLocalizedString("exam.catalog.minutes_count", comment: ""), def.durationMinutes))
                                .font(.caption2.weight(.semibold))
                            Text(LocalizedStringKey(def.scoringFormulaType.localizationKey))
                                .font(.caption2.weight(.semibold))
                                .lineLimit(1)
                        }
                        .foregroundStyle(ColorTokens.textTertiary)
                    }
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(ColorTokens.surfaceElevated)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(isSelected ? ColorTokens.action : Color.clear, lineWidth: 1.5)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 14))
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
                .font(.headline)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(ColorTokens.action)
            .disabled(viewModel.isSaving || viewModel.isOfflineBannerVisible)
            .accessibilityIdentifier("exam.target.saveButton")
        }
        .padding(16)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func activeTargetReadinessCard(target: StudentExamTargetModel) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(LocalizedStringKey("exam.target.readiness_gap_title"))
                    .font(.headline)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text(LocalizedStringKey("exam.target.active_badge"))
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(ColorTokens.accent.opacity(0.2))
                    .foregroundStyle(ColorTokens.accent)
                    .clipShape(Capsule())
            }

            Text("\(target.examCode) v\(target.examVersion) • \(target.targetDate)")
                .font(.caption.weight(.medium))
                .foregroundStyle(ColorTokens.textSecondary)

            HStack {
                Text(String(format: "%@: %.1f", NSLocalizedString("exam.target.estimated_score_label", comment: ""), target.readinessGapPreview.currentEstimatedScore))
                    .font(.caption.monospacedDigit())
                Spacer()
                Text(String(format: "%@: %+.1f", NSLocalizedString("exam.target.gap_points_label", comment: ""), target.readinessGapPreview.gapPoints))
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .foregroundStyle(ColorTokens.action)
            }
            .foregroundStyle(ColorTokens.textPrimary)

            Text(LocalizedStringKey("exam.target.zero_cost_note"))
                .font(.caption2)
                .foregroundStyle(ColorTokens.textTertiary)
        }
        .padding(16)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}
