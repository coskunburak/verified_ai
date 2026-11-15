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
            .foregroundStyle(ColorTokens.textPrimary)
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
            .background(ColorTokens.canvas)
            .navigationTitle(LocalizedStringKey("micro_lesson.header.focus_opportunity"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(ColorTokens.textSecondary)
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
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
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
            .padding(SpacingTokens.lg)
        }
    }

    private func headerBanner(card: MicroLessonCard) -> some View {
        AppSurfaceCard(elevated: true) {
            HStack(spacing: SpacingTokens.sm) {
                AppStatusBadge(verbatimTitle: card.focusBadgeLabel, systemImage: "scope", tone: .action)
                    .accessibilityIdentifier("micro_lesson.focus_badge")

                Spacer()

                Text("~\(card.estimatedDurationSeconds)s")
                    .font(TypographyTokens.caption.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(ColorTokens.textSecondary)
                    .accessibilityIdentifier("micro_lesson.duration_badge")
            }

            Text(card.title)
                .font(TypographyTokens.sectionTitle)
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("micro_lesson.title")
        }
    }

    private var beatSegmentedControl: some View {
        HStack(spacing: SpacingTokens.xs) {
            ForEach(MicroLessonBeat.allCases, id: \.rawValue) { beat in
                let isSelected = viewModel.currentBeat == beat
                Button {
                    viewModel.selectBeat(beat)
                } label: {
                    VStack(spacing: 4) {
                        Text("\(beat.rawValue + 1)")
                            .font(TypographyTokens.caption.bold())
                            .monospacedDigit()
                        Text(LocalizedStringKey(beat.titleKey))
                            .font(TypographyTokens.microLabel)
                            .multilineTextAlignment(.center)
                    }
                    .foregroundStyle(isSelected ? ColorTokens.action : ColorTokens.textSecondary)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .padding(.vertical, 6)
                    .background(
                        isSelected ? ColorTokens.actionSoft : ColorTokens.surfaceElevated,
                        in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                            .stroke(isSelected ? ColorTokens.action.opacity(0.6) : ColorTokens.borderSubtle, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
                .accessibilityIdentifier("micro_lesson.beat_tab.\(beat.rawValue + 1)")
            }
        }
    }

    private func beatOneCoreRule(card: MicroLessonCard) -> some View {
        AppSurfaceCard {
            Text(card.coreRule.ruleTitle)
                .font(TypographyTokens.cardTitle)
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            Text(card.coreRule.conciseExplanation)
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            MicroLessonFormulaView(
                latex: card.coreRule.canonicalLatexFormula,
                fallback: card.coreRule.canonicalLatexFormula,
                pointSize: formulaPointSize
            )
            .padding(SpacingTokens.compact)
            .background(ColorTokens.actionSoft, in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.action.opacity(0.35), lineWidth: 1)
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("micro_lesson.beat1")
    }

    private func beatTwoCounterexample(card: MicroLessonCard) -> some View {
        let ce = card.brokenVsValidCounterexample
        return AppSurfaceCard {
            MicroLessonFormulaView(
                latex: ce.promptLatex,
                fallback: ce.promptLatex,
                pointSize: formulaPointSize
            )
            .padding(SpacingTokens.compact)
            .background(ColorTokens.surfaceElevated, in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
            )

            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                Text(LocalizedStringKey("micro_lesson.beat2.common_trap_label"))
                    .font(TypographyTokens.microLabel)
                    .foregroundStyle(ColorTokens.warning)
                MicroLessonFormulaView(
                    latex: ce.commonTrapLatex,
                    fallback: ce.commonTrapLatex,
                    pointSize: formulaPointSize
                )
            }
            .padding(SpacingTokens.compact)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.warningSoft, in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.warning.opacity(0.45), lineWidth: 1)
            )

            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                Text(LocalizedStringKey("micro_lesson.beat2.verified_step_label"))
                    .font(TypographyTokens.microLabel)
                    .foregroundStyle(ColorTokens.success)
                MicroLessonFormulaView(
                    latex: ce.verifiedStepLatex,
                    fallback: ce.verifiedStepLatex,
                    pointSize: formulaPointSize
                )
            }
            .padding(SpacingTokens.compact)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.successSoft, in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.success.opacity(0.45), lineWidth: 1)
            )

            Text(ce.whyTrapFailsExplanation)
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                Text(LocalizedStringKey("micro_lesson.beat2.counterexample_label"))
                    .font(TypographyTokens.microLabel)
                    .foregroundStyle(ColorTokens.textSecondary)
                MicroLessonFormulaView(
                    latex: ce.numericCounterexampleLatex,
                    fallback: ce.numericCounterexampleLatex,
                    pointSize: max(14, formulaPointSize - 2)
                )
            }
            .padding(SpacingTokens.compact)
            .background(ColorTokens.surfaceElevated, in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("micro_lesson.beat2")
    }

    private func beatThreeQuickCheck(card: MicroLessonCard) -> some View {
        let qc = card.interactiveQuickCheck
        return AppSurfaceCard {
            Text(qc.questionPrompt)
                .font(TypographyTokens.cardTitle)
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            MicroLessonFormulaView(
                latex: qc.questionLatex,
                fallback: qc.questionLatex,
                pointSize: formulaPointSize
            )
            .padding(SpacingTokens.compact)
            .background(ColorTokens.surfaceElevated, in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
            )

            ForEach(qc.options) { option in
                let isSelected = viewModel.selectedOptionId == option.optionId
                Button {
                    viewModel.selectedOptionId = option.optionId
                } label: {
                    HStack(spacing: SpacingTokens.compact) {
                        Text(option.optionId)
                            .font(TypographyTokens.caption.bold())
                            .monospacedDigit()
                            .frame(width: 28, height: 28)
                            .background(
                                isSelected ? ColorTokens.action : ColorTokens.surfacePressed,
                                in: Circle()
                            )
                            .foregroundStyle(isSelected ? ColorTokens.actionForeground : ColorTokens.textPrimary)

                        MicroLessonFormulaView(
                            latex: option.latexOrText,
                            fallback: option.latexOrText,
                            pointSize: max(15, formulaPointSize - 1)
                        )
                    }
                    .padding(SpacingTokens.compact)
                    .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                    .background(
                        isSelected ? ColorTokens.actionSoft : ColorTokens.surfaceElevated,
                        in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    )
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                            .stroke(isSelected ? ColorTokens.action : ColorTokens.borderSubtle, lineWidth: isSelected ? 1.5 : 1)
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
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(viewModel.selectedOptionId == nil || viewModel.isSubmitting)
            .accessibilityIdentifier("micro_lesson.quick_check.submit")

            if viewModel.hasSubmittedQuickCheck {
                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    Text(
                        LocalizedStringKey(
                            viewModel.quickCheckPassed
                                ? "micro_lesson.beat3.success_feedback"
                                : "micro_lesson.beat3.retry_feedback"
                        )
                    )
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(viewModel.quickCheckPassed ? ColorTokens.success : ColorTokens.warning)

                    if let rationale = viewModel.selectedOption?.rationale {
                        Text(rationale)
                            .font(TypographyTokens.body)
                            .foregroundStyle(ColorTokens.textPrimary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    Button {
                        dismiss()
                        onReturnToPractice()
                    } label: {
                        Text(LocalizedStringKey("micro_lesson.cta.back_to_twin"))
                    }
                    .buttonStyle(AppSecondaryButtonStyle())
                    .accessibilityIdentifier("micro_lesson.cta.back_to_twin")
                }
                .padding(SpacingTokens.compact)
                .background(
                    viewModel.quickCheckPassed ? ColorTokens.successSoft : ColorTokens.warningSoft,
                    in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke((viewModel.quickCheckPassed ? ColorTokens.success : ColorTokens.warning).opacity(0.5), lineWidth: 1)
                )
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("micro_lesson.beat3")
    }

    private var navigationFooter: some View {
        HStack(spacing: SpacingTokens.sm) {
            if viewModel.currentBeat != .coreRule {
                Button {
                    viewModel.previousBeat()
                } label: {
                    Text(LocalizedStringKey("micro_lesson.cta.prev_beat"))
                }
                .buttonStyle(AppSecondaryButtonStyle())
                .accessibilityIdentifier("micro_lesson.cta.prev_beat")
            }

            if viewModel.currentBeat != .quickCheck {
                Button {
                    viewModel.nextBeat()
                } label: {
                    Text(LocalizedStringKey("micro_lesson.cta.next_beat"))
                }
                .buttonStyle(AppPrimaryButtonStyle())
                .accessibilityIdentifier("micro_lesson.cta.next_beat")
            }
        }
        .padding(.top, SpacingTokens.sm)
    }

    private func fallbackView(errorKey: String) -> some View {
        AppSurfaceCard(elevated: true) {
            Text(LocalizedStringKey(errorKey))
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textSecondary)
                .multilineTextAlignment(.center)

            Button {
                dismiss()
                onFallbackToHints()
            } label: {
                Text(LocalizedStringKey("micro_lesson.cta.back_to_twin"))
            }
            .buttonStyle(AppPrimaryButtonStyle())
        }
        .padding(SpacingTokens.lg)
        .accessibilityIdentifier("micro_lesson.fallback")
    }
}
