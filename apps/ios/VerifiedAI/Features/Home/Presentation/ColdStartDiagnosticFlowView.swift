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
                VStack(alignment: .leading, spacing: 18) {
                    switch viewModel.flowStage {
                    case .preferences:
                        preferencesStage
                    case .question(let index):
                        questionStage(index: index)
                    case .result:
                        resultStage
                    }
                }
                .padding(16)
            }
            .navigationTitle(LocalizedStringKey("cold_start.flow.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(LocalizedStringKey("cold_start.flow.button.done")) {
                        viewModel.dismissFlow()
                    }
                    .frame(minWidth: 44, minHeight: 44)
                    .accessibilityIdentifier("coldStart.flow.close")
                }
            }
        }
    }

    private var preferencesStage: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(LocalizedStringKey("cold_start.flow.setup_title"))
                .font(.title3.weight(.semibold))

            VStack(alignment: .leading, spacing: 8) {
                Text(LocalizedStringKey("cold_start.flow.exam_target"))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(ColdStartExamTarget.allCases) { target in
                            Button {
                                viewModel.declaredExamTarget = target
                            } label: {
                                Text(target.rawValue)
                                    .font(.subheadline.weight(.semibold))
                                    .padding(.horizontal, 14)
                                    .frame(minHeight: 44)
                                    .background(viewModel.declaredExamTarget == target ? Color.accentColor : Color(UIColor.secondarySystemBackground))
                                    .foregroundStyle(viewModel.declaredExamTarget == target ? Color.white : Color.primary)
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                            }
                            .accessibilityIdentifier("coldStart.examTarget.\(target.rawValue)")
                        }
                    }
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                Text(LocalizedStringKey("cold_start.flow.daily_budget"))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                HStack(spacing: 8) {
                    ForEach(budgetChoices, id: \.self) { minutes in
                        Button {
                            viewModel.declaredDailyBudgetMinutes = minutes
                        } label: {
                            Text("\(minutes)m")
                                .font(.subheadline.weight(.semibold))
                                .frame(maxWidth: .infinity, minHeight: 44)
                                .background(viewModel.declaredDailyBudgetMinutes == minutes ? Color.accentColor : Color(UIColor.secondarySystemBackground))
                                .foregroundStyle(viewModel.declaredDailyBudgetMinutes == minutes ? Color.white : Color.primary)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                        .accessibilityIdentifier("coldStart.budget.\(minutes)")
                    }
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                Text(LocalizedStringKey("cold_start.flow.modality"))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                ForEach(ColdStartModality.allCases) { modality in
                    Button {
                        viewModel.preferredModality = modality
                    } label: {
                        HStack {
                            Text(LocalizedStringKey(modality.localizationKey))
                                .font(.subheadline.weight(.medium))
                            Spacer()
                            if viewModel.preferredModality == modality {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(Color.accentColor)
                            }
                        }
                        .padding(.horizontal, 12)
                        .frame(minHeight: 44)
                        .background(Color(UIColor.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("coldStart.modality.\(modality.rawValue)")
                }
            }

            Toggle(isOn: $viewModel.explicitPreferenceOverride) {
                Text(LocalizedStringKey("cold_start.flow.modality_override"))
                    .font(.subheadline)
            }
            .padding(.vertical, 4)

            Button {
                viewModel.beginQuestions()
            } label: {
                Label(LocalizedStringKey("cold_start.flow.button.begin"), systemImage: "play.fill")
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .accessibilityIdentifier("coldStart.flow.begin")
        }
    }

    @ViewBuilder
    private func questionStage(index: Int) -> some View {
        if let prompt = viewModel.currentPrompt {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text(String(format: NSLocalizedString("cold_start.flow.step_counter", comment: ""), index + 1, ColdStartDiagnosticBank.prompts.count))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Spacer()
                    let minutes = viewModel.remainingSeconds / 60
                    let seconds = viewModel.remainingSeconds % 60
                    Text(String(format: NSLocalizedString("cold_start.flow.timer_remaining", comment: ""), minutes, seconds))
                        .font(.caption.weight(.semibold).monospacedDigit())
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Color.accentColor.opacity(0.14))
                        .clipShape(Capsule())
                        .accessibilityIdentifier("coldStart.flow.timer")
                }

                Text(LocalizedStringKey(prompt.itemId.localizationTitleKey))
                    .font(.headline)

                ScrollView(.horizontal, showsIndicators: false) {
                    NativeMathLabel(
                        latex: prompt.promptLatex,
                        fallback: prompt.promptLatex,
                        pointSize: 18
                    )
                    .fixedSize()
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(UIColor.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 14))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(prompt.accessibilityPrompt)

                VStack(spacing: 10) {
                    ForEach(prompt.options) { option in
                        let isSelected = viewModel.selectedOptionByItem[prompt.itemId] == option.optionId
                        Button {
                            viewModel.selectOption(option.optionId, for: prompt.itemId)
                        } label: {
                            HStack(spacing: 12) {
                                Text(option.optionId)
                                    .font(.subheadline.weight(.bold))
                                    .frame(width: 28, height: 28)
                                    .background(isSelected ? Color.accentColor : Color.secondary.opacity(0.2))
                                    .foregroundStyle(isSelected ? Color.white : Color.primary)
                                    .clipShape(Circle())

                                NativeMathLabel(
                                    latex: option.labelLatex,
                                    fallback: option.accessibilityText,
                                    pointSize: 16
                                )
                                .fixedSize()

                                Spacer()
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
                            .background(isSelected ? Color.accentColor.opacity(0.14) : Color(UIColor.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                            .overlay {
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(isSelected ? Color.accentColor : Color.clear, lineWidth: 1.5)
                            }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(option.optionId): \(option.accessibilityText)")
                        .accessibilityIdentifier("coldStart.option.\(option.optionId)")
                    }
                }

                VStack(spacing: 10) {
                    let isLast = index + 1 == ColdStartDiagnosticBank.prompts.count
                    Button {
                        Task {
                            await viewModel.advanceToNextQuestionOrSubmit()
                        }
                    } label: {
                        if viewModel.isSubmitting {
                            ProgressView()
                                .frame(maxWidth: .infinity, minHeight: 48)
                        } else {
                            Text(LocalizedStringKey(isLast ? "cold_start.flow.button.finish" : "cold_start.flow.button.next"))
                                .font(.headline)
                                .frame(maxWidth: .infinity, minHeight: 48)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.selectedOptionByItem[prompt.itemId] == nil || viewModel.isSubmitting)
                    .accessibilityIdentifier("coldStart.flow.nextOrFinish")

                    Button {
                        Task {
                            await viewModel.skipCurrentQuestion()
                        }
                    } label: {
                        Text(LocalizedStringKey("cold_start.flow.button.skip"))
                            .font(.subheadline.weight(.medium))
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.borderless)
                    .disabled(viewModel.isSubmitting)
                    .accessibilityIdentifier("coldStart.flow.skip")
                }
            }
        }
    }

    @ViewBuilder
    private var resultStage: some View {
        if let result = viewModel.latestResult {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Text(LocalizedStringKey("cold_start.result.header"))
                        .font(.title3.weight(.semibold))
                    Spacer()
                    Text("\(result.correctCount)/\(result.totalItems)")
                        .font(.title3.weight(.bold))
                        .foregroundStyle(Color.accentColor)
                }

                VStack(alignment: .leading, spacing: 6) {
                    Text(LocalizedStringKey(result.baselineTier.localizationKey))
                        .font(.headline)
                    Text(LocalizedStringKey(result.confidenceTier.localizationKey))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(LocalizedStringKey("cold_start.result.zero_cost_note"))
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(Color.green)
                }
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(UIColor.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))

                if let syncNoticeKey = viewModel.syncNoticeKey {
                    Label(LocalizedStringKey(syncNoticeKey), systemImage: "icloud.and.arrow.up")
                        .font(.caption)
                        .foregroundStyle(ColorTokens.warning)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text(LocalizedStringKey("cold_start.result.priors_title"))
                        .font(.subheadline.weight(.semibold))
                    ForEach(result.initializedPriors) { prior in
                        HStack {
                            Text(prior.skillCode)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                            Spacer()
                            Text("\(Int((prior.priorProbability * 100).rounded()))%")
                                .font(.subheadline.weight(.semibold).monospacedDigit())
                        }
                        .padding(10)
                        .background(Color(UIColor.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text(LocalizedStringKey("cold_start.result.rationales_title"))
                        .font(.subheadline.weight(.semibold))
                    ForEach(result.rationales) { rationale in
                        Text(rationale.localizedMessage)
                            .font(.caption)
                            .padding(10)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(UIColor.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                }

                Button {
                    viewModel.dismissFlow()
                } label: {
                    Text(LocalizedStringKey("cold_start.flow.button.done"))
                        .font(.headline)
                        .frame(maxWidth: .infinity, minHeight: 48)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("coldStart.result.done")
            }
        }
    }
}
