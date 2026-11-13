import SwiftUI

private struct MicroLessonFormulaView: View {
    let latex: String
    let fallback: String
    let pointSize: CGFloat

    var body: some View {
        #if canImport(SwiftMath)
        NativeMathLabel(latex: latex, fallback: fallback, pointSize: pointSize)
            .frame(maxWidth: .infinity, alignment: .leading)
        #else
        Text(latex.isEmpty ? fallback : latex)
            .font(.system(size: pointSize, design: .monospaced))
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityLabel(fallback)
        #endif
    }
}

public struct MicroLessonDrawerView: View {
    @ObservedObject var viewModel: MicroLessonViewModel
    let onReturnToPractice: () -> Void
    let onFallbackToHints: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dismiss) private var dismiss
    @ScaledMetric(relativeTo: .body) private var formulaPointSize: CGFloat = 18
    @ScaledMetric(relativeTo: .title3) private var cardCornerRadius: CGFloat = 16

    public init(
        viewModel: MicroLessonViewModel,
        onReturnToPractice: @escaping () -> Void = {},
        onFallbackToHints: @escaping () -> Void = {}
    ) {
        self.viewModel = viewModel
        self.onReturnToPractice = onReturnToPractice
        self.onFallbackToHints = onFallbackToHints
    }

    public var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .accessibilityIdentifier("micro_lesson.loading")
                } else if let errorKey = viewModel.errorLocalizationKey {
                    fallbackView(errorKey: errorKey)
                } else if let card = viewModel.card {
                    lessonContent(card: card)
                }
            }
            .navigationTitle(LocalizedStringKey("micro_lesson.header.focus_opportunity"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .frame(minWidth: 44, minHeight: 44)
                    }
                    .accessibilityLabel(LocalizedStringKey("micro_lesson.cta.back_to_twin"))
                    .accessibilityIdentifier("micro_lesson.close")
                }
            }
        }
        .accessibilityLabel(LocalizedStringKey("micro_lesson.accessibility.drawer_label"))
        .transaction { if reduceMotion { $0.animation = nil } }
    }

    private func lessonContent(card: MicroLessonCard) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerBanner(card: card)
                beatSegmentedControl

                Group {
                    switch viewModel.currentBeat {
                    case .coreRule:
                        beatOneCoreRule(card: card)
                    case .counterexample:
                        beatTwoCounterexample(card: card)
                    case .quickCheck:
                        beatThreeQuickCheck(card: card)
                    }
                }
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.22), value: viewModel.currentBeat)

                navigationFooter
            }
            .padding()
        }
    }

    private func headerBanner(card: MicroLessonCard) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(card.focusBadgeLabel)
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.accentColor.opacity(0.14), in: Capsule())
                    .accessibilityIdentifier("micro_lesson.focus_badge")

                Spacer()

                Text("~\(card.estimatedDurationSeconds)s")
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)
                    .accessibilityIdentifier("micro_lesson.duration_badge")
            }

            Text(card.title)
                .font(.title3.bold())
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("micro_lesson.title")
        }
    }

    private var beatSegmentedControl: some View {
        HStack(spacing: 8) {
            ForEach(MicroLessonBeat.allCases, id: \.rawValue) { beat in
                let isSelected = viewModel.currentBeat == beat
                Button {
                    viewModel.selectBeat(beat)
                } label: {
                    VStack(spacing: 4) {
                        Text("\(beat.rawValue + 1)")
                            .font(.caption.bold())
                        Text(LocalizedStringKey(beat.titleKey))
                            .font(.caption2)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .padding(.vertical, 6)
                    .background(
                        isSelected ? Color.accentColor.opacity(0.18) : Color.secondary.opacity(0.08),
                        in: RoundedRectangle(cornerRadius: 12)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
                .accessibilityIdentifier("micro_lesson.beat_tab.\(beat.rawValue + 1)")
            }
        }
    }

    private func beatOneCoreRule(card: MicroLessonCard) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(card.coreRule.ruleTitle)
                .font(.headline)
                .fixedSize(horizontal: false, vertical: true)

            Text(card.coreRule.conciseExplanation)
                .font(.body)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)

            MicroLessonFormulaView(
                latex: card.coreRule.canonicalLatexFormula,
                fallback: card.coreRule.canonicalLatexFormula,
                pointSize: formulaPointSize
            )
            .padding()
            .background(Color.accentColor.opacity(0.08), in: RoundedRectangle(cornerRadius: cardCornerRadius))
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("micro_lesson.beat1")
    }

    private func beatTwoCounterexample(card: MicroLessonCard) -> some View {
        let ce = card.brokenVsValidCounterexample
        return VStack(alignment: .leading, spacing: 14) {
            MicroLessonFormulaView(
                latex: ce.promptLatex,
                fallback: ce.promptLatex,
                pointSize: formulaPointSize
            )
            .padding(12)
            .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))

            VStack(alignment: .leading, spacing: 8) {
                Text(LocalizedStringKey("micro_lesson.beat2.common_trap_label"))
                    .font(.caption.bold())
                    .foregroundStyle(.orange)
                MicroLessonFormulaView(
                    latex: ce.commonTrapLatex,
                    fallback: ce.commonTrapLatex,
                    pointSize: formulaPointSize
                )
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.orange.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))

            VStack(alignment: .leading, spacing: 8) {
                Text(LocalizedStringKey("micro_lesson.beat2.verified_step_label"))
                    .font(.caption.bold())
                    .foregroundStyle(.green)
                MicroLessonFormulaView(
                    latex: ce.verifiedStepLatex,
                    fallback: ce.verifiedStepLatex,
                    pointSize: formulaPointSize
                )
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.green.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))

            Text(ce.whyTrapFailsExplanation)
                .font(.body)
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: 6) {
                Text(LocalizedStringKey("micro_lesson.beat2.counterexample_label"))
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
                MicroLessonFormulaView(
                    latex: ce.numericCounterexampleLatex,
                    fallback: ce.numericCounterexampleLatex,
                    pointSize: max(14, formulaPointSize - 2)
                )
            }
            .padding(12)
            .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("micro_lesson.beat2")
    }

    private func beatThreeQuickCheck(card: MicroLessonCard) -> some View {
        let qc = card.interactiveQuickCheck
        return VStack(alignment: .leading, spacing: 14) {
            Text(qc.questionPrompt)
                .font(.headline)
                .fixedSize(horizontal: false, vertical: true)

            MicroLessonFormulaView(
                latex: qc.questionLatex,
                fallback: qc.questionLatex,
                pointSize: formulaPointSize
            )
            .padding(12)
            .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))

            ForEach(qc.options) { option in
                let isSelected = viewModel.selectedOptionId == option.optionId
                Button {
                    viewModel.selectedOptionId = option.optionId
                } label: {
                    HStack(spacing: 12) {
                        Text(option.optionId)
                            .font(.subheadline.bold())
                            .frame(width: 28, height: 28)
                            .background(
                                isSelected ? Color.accentColor : Color.secondary.opacity(0.18),
                                in: Circle()
                            )
                            .foregroundStyle(isSelected ? Color.white : Color.primary)

                        MicroLessonFormulaView(
                            latex: option.latexOrText,
                            fallback: option.latexOrText,
                            pointSize: max(15, formulaPointSize - 1)
                        )
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                    .background(
                        isSelected ? Color.accentColor.opacity(0.14) : Color.secondary.opacity(0.07),
                        in: RoundedRectangle(cornerRadius: 12)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(isSelected ? Color.accentColor : Color.clear, lineWidth: 1.5)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Option \(option.optionId): \(option.latexOrText)")
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
                .accessibilityIdentifier("micro_lesson.option.\(option.optionId)")
            }

            Button {
                Task { await viewModel.submitQuickCheck() }
            } label: {
                Text(LocalizedStringKey("micro_lesson.beat3.submit_cta"))
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.selectedOptionId == nil || viewModel.isSubmitting)
            .accessibilityIdentifier("micro_lesson.quick_check.submit")

            if viewModel.hasSubmittedQuickCheck {
                VStack(alignment: .leading, spacing: 8) {
                    Text(
                        LocalizedStringKey(
                            viewModel.quickCheckPassed
                                ? "micro_lesson.beat3.success_feedback"
                                : "micro_lesson.beat3.retry_feedback"
                        )
                    )
                    .font(.subheadline.bold())
                    .foregroundStyle(viewModel.quickCheckPassed ? .green : .orange)

                    if let rationale = viewModel.selectedOption?.rationale {
                        Text(rationale)
                            .font(.body)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    Button {
                        dismiss()
                        onReturnToPractice()
                    } label: {
                        Text(LocalizedStringKey("micro_lesson.cta.back_to_twin"))
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("micro_lesson.cta.back_to_twin")
                }
                .padding(12)
                .background(
                    (viewModel.quickCheckPassed ? Color.green : Color.orange).opacity(0.10),
                    in: RoundedRectangle(cornerRadius: 12)
                )
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("micro_lesson.beat3")
    }

    private var navigationFooter: some View {
        HStack {
            if viewModel.currentBeat != .coreRule {
                Button {
                    viewModel.previousBeat()
                } label: {
                    Text(LocalizedStringKey("micro_lesson.cta.prev_beat"))
                        .frame(minHeight: 44)
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("micro_lesson.cta.prev_beat")
            }

            Spacer()

            if viewModel.currentBeat != .quickCheck {
                Button {
                    viewModel.nextBeat()
                } label: {
                    Text(LocalizedStringKey("micro_lesson.cta.next_beat"))
                        .frame(minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("micro_lesson.cta.next_beat")
            }
        }
        .padding(.top, 8)
    }

    private func fallbackView(errorKey: String) -> some View {
        VStack(spacing: 16) {
            Text(LocalizedStringKey(errorKey))
                .font(.body)
                .multilineTextAlignment(.center)
                .padding()

            Button {
                dismiss()
                onFallbackToHints()
            } label: {
                Text(LocalizedStringKey("micro_lesson.cta.back_to_twin"))
                    .frame(minHeight: 44)
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
        .accessibilityIdentifier("micro_lesson.fallback")
    }
}
