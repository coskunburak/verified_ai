import SwiftUI

public struct ExamBlueprintAssemblyView: View {
    @ObservedObject public var viewModel: ExamBlueprintAssemblyViewModel
    @FocusState private var isSeedFieldFocused: Bool
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion

    public init(viewModel: ExamBlueprintAssemblyViewModel) {
        self.viewModel = viewModel
    }

    private var effectiveReduceMotion: Bool {
        systemReduceMotion
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if viewModel.isOfflineCached {
                    offlineCachedBanner
                }

                if let errorKey = viewModel.errorLocalizationKey {
                    Text(LocalizedStringKey(errorKey))
                        .font(.callout.weight(.medium))
                        .foregroundStyle(ColorTokens.warning)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(ColorTokens.surfaceElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .accessibilityIdentifier("exam.assembly.errorBanner")
                } else if let errorMsg = viewModel.errorMessage {
                    Text(errorMsg)
                        .font(.callout.weight(.medium))
                        .foregroundStyle(ColorTokens.warning)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(ColorTokens.surfaceElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .accessibilityIdentifier("exam.assembly.errorBanner")
                }

                blueprintListSection

                if let blueprint = viewModel.selectedBlueprint {
                    topicDistributionSection(blueprint: blueprint)
                    difficultyDistributionSection(blueprint: blueprint)
                    timingAndAccommodationSection(blueprint: blueprint)
                    assemblySeedAndActionSection
                }

                if let assembled = viewModel.assembledAssessment {
                    assembledReadinessSection(assembled: assembled)
                }
            }
            .padding(16)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(ColorTokens.background.ignoresSafeArea())
        .navigationTitle(LocalizedStringKey("exam.assembly.title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(LocalizedStringKey("exam.target.keyboard_done")) {
                    isSeedFieldFocused = false
                }
                .accessibilityIdentifier("exam.assembly.keyboardDone")
            }
        }
        .task {
            if viewModel.blueprints.isEmpty {
                await viewModel.loadBlueprints()
            }
        }
    }

    private var offlineCachedBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "icloud.slash")
                .foregroundStyle(ColorTokens.warning)
            Text(LocalizedStringKey("exam.assembly.offline.cached_banner"))
                .font(.caption.weight(.medium))
                .foregroundStyle(ColorTokens.warning)
            Spacer()
        }
        .padding(12)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("exam.assembly.offlineBanner")
    }

    private var blueprintListSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(LocalizedStringKey("exam.assembly.blueprint.section_header"))
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)

            if viewModel.isLoading && viewModel.blueprints.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
            } else {
                ForEach(viewModel.blueprints) { bp in
                    let isSelected = viewModel.selectedBlueprint?.id == bp.id
                    Button {
                        isSeedFieldFocused = false
                        viewModel.selectBlueprint(bp)
                    } label: {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .top) {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(bp.title)
                                        .font(.headline)
                                        .foregroundStyle(ColorTokens.textPrimary)
                                        .multilineTextAlignment(.leading)
                                    HStack(spacing: 6) {
                                        Text(LocalizedStringKey(bp.blueprintMode.localizationKey))
                                            .font(.caption.weight(.semibold))
                                            .foregroundStyle(ColorTokens.action)
                                        Text("• v\(bp.examVersionNumber)")
                                            .font(.caption.monospaced())
                                            .foregroundStyle(ColorTokens.textSecondary)
                                    }
                                }
                                Spacer()
                                if isSelected {
                                    Image(systemName: "checkmark.circle.fill")
                                        .foregroundStyle(ColorTokens.accent)
                                }
                            }

                            HStack(spacing: 12) {
                                Text(String(format: NSLocalizedString("exam.catalog.questions_count", comment: ""), bp.totalQuestionCount))
                                    .font(.caption2.weight(.semibold))
                                Text(String(format: NSLocalizedString("exam.catalog.minutes_count", comment: ""), bp.baseDurationMinutes))
                                    .font(.caption2.weight(.semibold))
                                Text("SHA-256: \(String(bp.blueprintHash.prefix(10)))…")
                                    .font(.caption2.monospaced())
                            }
                            .foregroundStyle(ColorTokens.textTertiary)
                        }
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(ColorTokens.surfaceElevated)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(isSelected ? ColorTokens.action : Color.clear, lineWidth: viewModel.highContrastMode ? 2.5 : 1.5)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .accessibilityLabel("\(bp.title), \(bp.totalQuestionCount) questions, \(bp.baseDurationMinutes) minutes")
                    .accessibilityHint("Selects this blueprint for deterministic assessment assembly")
                    .accessibilityIdentifier("exam.assembly.blueprintCard.\(bp.blueprintCode)")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.assembly.blueprintList")
    }

    private func topicDistributionSection(blueprint: ExamBlueprintDTO) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(LocalizedStringKey("exam.assembly.distribution.topics"))
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)

            ForEach(blueprint.topicRules) { rule in
                let weightPct = Double(rule.targetWeightBps) / 100.0
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(rule.topicCode)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(ColorTokens.textPrimary)
                        Spacer()
                        Text("\(String(format: "%.1f%%", weightPct)) (\(rule.minQuestionCount)–\(rule.maxQuestionCount))")
                            .font(.caption.monospacedDigit().weight(.semibold))
                            .foregroundStyle(ColorTokens.stepHighlight)
                    }
                    ProgressView(value: Double(rule.targetWeightBps), total: 10000.0)
                        .tint(ColorTokens.stepHighlight)
                }
                .padding(.vertical, 2)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("\(rule.topicCode), weight \(Int(weightPct.rounded())) percent, \(rule.minQuestionCount) to \(rule.maxQuestionCount) questions")
            }
        }
        .padding(SpacingTokens.md)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.assembly.topicDistributionSection")
    }

    private func difficultyDistributionSection(blueprint: ExamBlueprintDTO) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(LocalizedStringKey("exam.assembly.distribution.difficulty"))
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)

            difficultyRow(label: "EASY", bps: blueprint.easyTargetBps, color: ColorTokens.success)
            difficultyRow(label: "MEDIUM", bps: blueprint.mediumTargetBps, color: ColorTokens.stepHighlight)
            difficultyRow(label: "HARD", bps: blueprint.hardTargetBps, color: ColorTokens.warning)
            difficultyRow(label: "EXPERT", bps: blueprint.expertTargetBps, color: ColorTokens.danger)
        }
        .padding(SpacingTokens.md)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.assembly.difficultyDistributionSection")
    }

    private func difficultyRow(label: String, bps: Int, color: Color) -> some View {
        let pct = Double(bps) / 100.0
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(label)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text(String(format: "%.1f%%", pct))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(ColorTokens.textSecondary)
            }
            ProgressView(value: Double(bps), total: 10000.0)
                .tint(color)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label) target \(Int(pct.rounded())) percent")
    }

    private func timingAndAccommodationSection(blueprint: ExamBlueprintDTO) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(LocalizedStringKey("exam.candidate.accommodations_section"))
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)

            Picker(LocalizedStringKey("exam.candidate.extra_time_label"), selection: $viewModel.selectedTimeMultiplier) {
                Text("1.00x").tag(1.00)
                Text("1.25x").tag(1.25)
                Text("1.50x").tag(1.50)
                Text("2.00x").tag(2.00)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("exam.assembly.timeMultiplierPicker")

            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(LocalizedStringKey("exam.assembly.duration.base"))
                        .font(.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    Text("\(blueprint.baseDurationMinutes) min (\(blueprint.baseDurationSeconds)s)")
                        .font(.subheadline.monospacedDigit().weight(.semibold))
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(LocalizedStringKey("exam.assembly.duration.effective_accommodated"))
                        .font(.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    Text("\(viewModel.previewEffectiveDurationMinutes) min (\(viewModel.previewEffectiveDurationSeconds)s)")
                        .font(.subheadline.monospacedDigit().weight(.bold))
                        .foregroundStyle(ColorTokens.accent)
                        .accessibilityIdentifier("exam.assembly.effectiveDurationLabel")
                }
            }

            Toggle(isOn: $viewModel.highContrastMode) {
                Text(LocalizedStringKey("exam.candidate.high_contrast"))
                    .foregroundStyle(ColorTokens.textPrimary)
            }
            .accessibilityIdentifier("exam.assembly.highContrastToggle")

            Toggle(isOn: $viewModel.screenReaderOptimized) {
                Text(LocalizedStringKey("exam.candidate.screen_reader"))
                    .foregroundStyle(ColorTokens.textPrimary)
            }
            .accessibilityIdentifier("exam.assembly.screenReaderToggle")
        }
        .padding(14)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
    }

    private var assemblySeedAndActionSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(LocalizedStringKey("exam.assembly.seed.label"))
                .font(.caption.weight(.semibold))
                .foregroundStyle(ColorTokens.textSecondary)

            TextField("TYT-DIAG-SEED-2026-ALPHA", text: $viewModel.assemblySeed)
                .font(.subheadline.monospaced())
                .focused($isSeedFieldFocused)
                .padding(12)
                .background(ColorTokens.background)
                .foregroundStyle(ColorTokens.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .accessibilityIdentifier("exam.assembly.seedField")

            Button {
                isSeedFieldFocused = false
                Task {
                    await viewModel.assembleSelectedBlueprint()
                }
            } label: {
                HStack {
                    if viewModel.isAssembling {
                        ProgressView()
                    }
                    Text(LocalizedStringKey("exam.assembly.action.assemble"))
                        .font(.headline)
                }
                .frame(maxWidth: .infinity)
                .frame(minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(ColorTokens.action)
            .disabled(viewModel.isAssembling || viewModel.selectedBlueprint == nil)
            .animation(effectiveReduceMotion ? nil : .easeInOut(duration: 0.2), value: viewModel.isAssembling)
            .accessibilityIdentifier("exam.assembly.assembleButton")
        }
        .padding(14)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
    }

    private func assembledReadinessSection(assembled: AssembledAssessmentDTO) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(LocalizedStringKey("exam.assembly.status.ready"), systemImage: "checkmark.seal.fill")
                    .font(.headline)
                    .foregroundStyle(ColorTokens.success)
                    .accessibilityIdentifier("exam.assembly.statusReadyBanner")
                Spacer()
                Text("\(assembled.totalQuestionCount) Q • \(assembled.effectiveDurationMinutes) min")
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .foregroundStyle(ColorTokens.textSecondary)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(LocalizedStringKey("exam.assembly.reproducibility_hash.label"))
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(ColorTokens.textSecondary)
                Text(assembled.reproducibilityHash)
                    .font(.caption2.monospaced())
                    .foregroundStyle(ColorTokens.textPrimary)
                    .lineLimit(2)
                    .accessibilityIdentifier("exam.assembly.reproducibilityHashLabel")
            }

            Divider()

            VStack(alignment: .leading, spacing: 8) {
                ForEach(assembled.items) { item in
                    HStack(alignment: .top, spacing: 10) {
                        Text("#\(item.sequenceNumber)")
                            .font(.caption.monospacedDigit().weight(.bold))
                            .foregroundStyle(ColorTokens.action)
                            .frame(width: 28, alignment: .leading)

                        VStack(alignment: .leading, spacing: 2) {
                            Text("\(item.questionCode) • \(item.topicCode)")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(ColorTokens.textPrimary)
                            Text("\(item.difficultyBand.rawValue) • \(item.cognitiveLevel.rawValue) • Options: \(item.optionOrder.joined(separator: ", "))")
                                .font(.caption2)
                                .foregroundStyle(ColorTokens.textSecondary)
                            if let altText = item.altTextDescription, !altText.isEmpty {
                                Text(altText)
                                    .font(.caption2)
                                    .foregroundStyle(ColorTokens.textTertiary)
                            }
                        }
                        Spacer()
                    }
                    .padding(.vertical, 4)
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("Question \(item.sequenceNumber), \(item.questionCode), topic \(item.topicCode), difficulty \(item.difficultyBand.rawValue)")
                    .accessibilityIdentifier("exam.assembly.itemRow.\(item.sequenceNumber)")
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("exam.assembly.itemPreviewList")
        }
        .padding(16)
        .background(ColorTokens.surfaceElevated)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(ColorTokens.accent, lineWidth: viewModel.highContrastMode ? 2.5 : 1.0)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}
