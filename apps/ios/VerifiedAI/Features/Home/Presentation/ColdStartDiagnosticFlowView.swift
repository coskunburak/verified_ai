import SwiftUI

public struct ColdStartDiagnosticFlowView: View {
    @ObservedObject public var viewModel: ColdStartDiagnosticViewModel

    private let budgetChoices = [10, 15, 25, 40]

    public init(viewModel: ColdStartDiagnosticViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                    switch viewModel.flowStage {
                    case .preferences:
                        preferencesStage
                    case .question(let index):
                        questionStage(index: index)
                    case .result:
                        resultStage
                    }
                }
                .padding(SpacingTokens.lg)
            }
            .background(ColorTokens.canvas)
            .navigationTitle(LocalizedStringKey("cold_start.flow.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(LocalizedStringKey("cold_start.flow.button.done")) {
                        viewModel.dismissFlow()
                    }
                    .foregroundStyle(ColorTokens.action)
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityIdentifier("coldStart.flow.close")
                }
            }
        }
    }

    private var preferencesStage: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.lg) {
            Text(LocalizedStringKey("cold_start.flow.setup_title"))
                .font(TypographyTokens.sectionTitle)
                .foregroundStyle(ColorTokens.textPrimary)

            AppSurfaceCard {
                Text(LocalizedStringKey("cold_start.flow.exam_target"))
                    .font(TypographyTokens.microLabel)
                    .foregroundStyle(ColorTokens.textSecondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: SpacingTokens.xs) {
                        ForEach(ColdStartExamTarget.allCases) { target in
                            let selected = viewModel.declaredExamTarget == target
                            Button {
                                viewModel.declaredExamTarget = target
                            } label: {
                                Text(target.rawValue)
                                    .font(TypographyTokens.caption.weight(.semibold))
                                    .padding(.horizontal, SpacingTokens.compact)
                                    .frame(minHeight: 44)
                                    .background(selected ? ColorTokens.action : ColorTokens.surfaceElevated)
                                    .foregroundStyle(selected ? ColorTokens.actionForeground : ColorTokens.textPrimary)
                                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                                    .overlay(
                                        RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                                            .stroke(selected ? ColorTokens.action : ColorTokens.borderSubtle, lineWidth: 1)
                                    )
                            }
                            .accessibilityIdentifier("coldStart.examTarget.\(target.rawValue)")
                        }
                    }
                }
            }

            AppSurfaceCard {
                Text(LocalizedStringKey("cold_start.flow.daily_budget"))
                    .font(TypographyTokens.microLabel)
                    .foregroundStyle(ColorTokens.textSecondary)
                HStack(spacing: SpacingTokens.xs) {
                    ForEach(budgetChoices, id: \.self) { minutes in
                        let selected = viewModel.declaredDailyBudgetMinutes == minutes
                        Button {
                            viewModel.declaredDailyBudgetMinutes = minutes
                        } label: {
                            Text("\(minutes)m")
                                .font(TypographyTokens.cardTitle)
                                .monospacedDigit()
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(selected ? ColorTokens.action : ColorTokens.surfaceElevated)
                                .foregroundStyle(selected ? ColorTokens.actionForeground : ColorTokens.textPrimary)
                                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                                        .stroke(selected ? ColorTokens.action : ColorTokens.borderSubtle, lineWidth: 1)
                                )
                        }
                        .accessibilityIdentifier("coldStart.budget.\(minutes)")
                    }
                }
            }

            AppSurfaceCard {
                Text(LocalizedStringKey("cold_start.flow.modality"))
                    .font(TypographyTokens.microLabel)
                    .foregroundStyle(ColorTokens.textSecondary)
                ForEach(ColdStartModality.allCases) { modality in
                    let selected = viewModel.preferredModality == modality
                    Button {
                        viewModel.preferredModality = modality
                    } label: {
                        HStack {
                            Text(LocalizedStringKey(modality.localizationKey))
                                .font(TypographyTokens.body.weight(.medium))
                                .foregroundStyle(ColorTokens.textPrimary)
                            Spacer()
                            if selected {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(ColorTokens.action)
                            }
                        }
                        .padding(.horizontal, SpacingTokens.compact)
                        .frame(minHeight: 44)
                        .background(selected ? ColorTokens.actionSoft : ColorTokens.surfaceElevated)
                        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                                .stroke(selected ? ColorTokens.action : ColorTokens.borderSubtle, lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("coldStart.modality.\(modality.rawValue)")
                }

                Toggle(isOn: $viewModel.explicitPreferenceOverride) {
                    Text(LocalizedStringKey("cold_start.flow.modality_override"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
                .tint(ColorTokens.action)
                .padding(.top, SpacingTokens.xs)
            }

            Button {
                viewModel.beginQuestions()
            } label: {
                Label(LocalizedStringKey("cold_start.flow.button.begin"), systemImage: "play.fill")
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .accessibilityIdentifier("coldStart.flow.begin")
        }
    }

    @ViewBuilder
    private func questionStage(index: Int) -> some View {
        if let prompt = viewModel.currentPrompt {
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                HStack {
                    Text(String(format: NSLocalizedString("cold_start.flow.step_counter", comment: ""), index + 1, ColdStartDiagnosticBank.prompts.count))
                        .font(TypographyTokens.caption.weight(.semibold))
                        .monospacedDigit()
                        .foregroundStyle(ColorTokens.textSecondary)
                    Spacer()
                    let minutes = viewModel.remainingSeconds / 60
                    let seconds = viewModel.remainingSeconds % 60
                    AppStatusBadge(
                        verbatimTitle: String(format: NSLocalizedString("cold_start.flow.timer_remaining", comment: ""), minutes, seconds),
                        systemImage: "timer",
                        tone: viewModel.remainingSeconds <= 30 ? .warning : .action
                    )
                    .monospacedDigit()
                    .accessibilityIdentifier("coldStart.flow.timer")
                }

                Text(LocalizedStringKey(prompt.itemId.localizationTitleKey))
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)

                AppSurfaceCard {
                    ScrollView(.horizontal, showsIndicators: false) {
                        NativeMathLabel(
                            latex: prompt.promptLatex,
                            fallback: prompt.promptLatex,
                            pointSize: 18
                        )
                        .fixedSize()
                    }
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(prompt.accessibilityPrompt)

                VStack(spacing: SpacingTokens.sm) {
                    ForEach(prompt.options) { option in
                        let isSelected = viewModel.selectedOptionByItem[prompt.itemId] == option.optionId
                        Button {
                            viewModel.selectOption(option.optionId, for: prompt.itemId)
                        } label: {
                            HStack(spacing: SpacingTokens.compact) {
                                Text(option.optionId)
                                    .font(TypographyTokens.caption.weight(.bold))
                                    .monospacedDigit()
                                    .frame(width: 28, height: 28)
                                    .background(isSelected ? ColorTokens.action : ColorTokens.surfacePressed)
                                    .foregroundStyle(isSelected ? ColorTokens.actionForeground : ColorTokens.textPrimary)
                                    .clipShape(Circle())

                                NativeMathLabel(
                                    latex: option.labelLatex,
                                    fallback: option.accessibilityText,
                                    pointSize: 16
                                )
                                .fixedSize()

                                Spacer()
                            }
                            .padding(.horizontal, SpacingTokens.md)
                            .padding(.vertical, SpacingTokens.compact)
                            .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
                            .background(isSelected ? ColorTokens.actionSoft : ColorTokens.surface)
                            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                            .overlay {
                                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                                    .stroke(isSelected ? ColorTokens.action : ColorTokens.borderSubtle, lineWidth: isSelected ? 1.5 : 1)
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(option.optionId): \(option.accessibilityText)")
                        .accessibilityIdentifier("coldStart.option.\(option.optionId)")
                    }
                }

                VStack(spacing: SpacingTokens.sm) {
                    let isLast = index + 1 == ColdStartDiagnosticBank.prompts.count
                    Button {
                        Task {
                            await viewModel.advanceToNextQuestionOrSubmit()
                        }
                    } label: {
                        if viewModel.isSubmitting {
                            ProgressView()
                                .tint(ColorTokens.actionForeground)
                        } else {
                            Text(LocalizedStringKey(isLast ? "cold_start.flow.button.finish" : "cold_start.flow.button.next"))
                        }
                    }
                    .buttonStyle(AppPrimaryButtonStyle())
                    .disabled(viewModel.selectedOptionByItem[prompt.itemId] == nil || viewModel.isSubmitting)
                    .accessibilityIdentifier("coldStart.flow.nextOrFinish")

                    Button {
                        Task {
                            await viewModel.skipCurrentQuestion()
                        }
                    } label: {
                        Text(LocalizedStringKey("cold_start.flow.button.skip"))
                    }
                    .buttonStyle(AppSecondaryButtonStyle())
                    .disabled(viewModel.isSubmitting)
                    .accessibilityIdentifier("coldStart.flow.skip")
                }
            }
        }
    }

    @ViewBuilder
    private var resultStage: some View {
        if let result = viewModel.latestResult {
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                HStack {
                    Text(LocalizedStringKey("cold_start.result.header"))
                        .font(TypographyTokens.sectionTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Spacer()
                    Text("\(result.correctCount)/\(result.totalItems)")
                        .font(TypographyTokens.metricValue)
                        .monospacedDigit()
                        .foregroundStyle(ColorTokens.action)
                }

                AppSurfaceCard {
                    Text(LocalizedStringKey(result.baselineTier.localizationKey))
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Text(LocalizedStringKey(result.confidenceTier.localizationKey))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    Text(LocalizedStringKey("cold_start.result.zero_cost_note"))
                        .font(TypographyTokens.microLabel)
                        .foregroundStyle(ColorTokens.success)
                }

                if let syncNoticeKey = viewModel.syncNoticeKey {
                    AppNoticeBanner(LocalizedStringKey(syncNoticeKey), systemImage: "icloud.and.arrow.up", tone: .warning)
                }

                AppSurfaceCard(elevated: true) {
                    Text(LocalizedStringKey("cold_start.result.priors_title"))
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                    ForEach(result.initializedPriors) { prior in
                        HStack {
                            Text(prior.skillCode)
                                .font(TypographyTokens.caption.monospaced())
                                .foregroundStyle(ColorTokens.textSecondary)
                                .lineLimit(1)
                            Spacer()
                            Text("\(Int((prior.priorProbability * 100).rounded()))%")
                                .font(TypographyTokens.cardTitle)
                                .monospacedDigit()
                                .foregroundStyle(ColorTokens.textPrimary)
                        }
                        .padding(SpacingTokens.sm)
                        .background(ColorTokens.surface)
                        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
                    }
                }

                AppSurfaceCard(elevated: true) {
                    Text(LocalizedStringKey("cold_start.result.rationales_title"))
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                    ForEach(result.rationales) { rationale in
                        Text(rationale.localizedMessage)
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                            .padding(SpacingTokens.sm)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(ColorTokens.surface)
                            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
                    }
                }

                Button {
                    viewModel.dismissFlow()
                } label: {
                    Text(LocalizedStringKey("cold_start.flow.button.done"))
                }
                .buttonStyle(AppPrimaryButtonStyle())
                .accessibilityIdentifier("coldStart.result.done")
            }
        }
    }
}
