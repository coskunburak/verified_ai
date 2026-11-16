import SwiftUI

public struct ExamScoreSummaryView: View {
    @ObservedObject private var viewModel: ExamScoreSummaryViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion

    public init(viewModel: ExamScoreSummaryViewModel) {
        self.viewModel = viewModel
    }

    private var strokeColor: Color {
        viewModel.highContrastMode ? ColorTokens.borderStrong : ColorTokens.borderSubtle
    }

    private var strokeWidth: CGFloat {
        viewModel.highContrastMode ? 2 : 1
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if viewModel.showOfflineBanner {
                    offlineBannerView
                }

                if let errorKey = viewModel.errorLocalizationKey {
                    errorBannerView(localizationKey: errorKey, message: viewModel.errorMessage)
                }

                if viewModel.isLoading && viewModel.scoreSummary == nil {
                    loadingStateView
                } else if let summary = viewModel.scoreSummary {
                    netScoreCard(summary)
                    countsRow(summary)
                    topicBreakdownSection(summary.topicBreakdowns)
                    difficultyBreakdownSection(summary.difficultyBreakdowns)
                    itemEvidenceSection(summary)
                    regradeAuditSection(summary)
                }
            }
            .padding(16)
        }
        .background(ColorTokens.background)
        .accessibilityIdentifier("exam.score.screen")
        .navigationTitle(Text(LocalizedStringKey("exam.score.title")))
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(LocalizedStringKey("exam.score.action.close")) {
                    dismiss()
                }
                .tint(ColorTokens.action)
            }
        }
        .task {
            if viewModel.scoreSummary == nil {
                await viewModel.loadScoreSummary(computeIfMissing: true)
            }
        }
    }

    private var offlineBannerView: some View {
        HStack(spacing: 10) {
            Image(systemName: "wifi.slash")
                .foregroundStyle(ColorTokens.warning)
            Text(LocalizedStringKey("exam.score.offline_banner"))
                .font(.caption.weight(.semibold))
                .foregroundStyle(ColorTokens.warning)
            Spacer()
        }
        .padding(12)
        .background(ColorTokens.warningSoft)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(ColorTokens.warning, lineWidth: strokeWidth)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("exam.score.offlineBanner")
    }

    private func errorBannerView(localizationKey: String, message: String?) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle")
                .foregroundStyle(ColorTokens.danger)
            VStack(alignment: .leading, spacing: 4) {
                Text(LocalizedStringKey(localizationKey))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.danger)
                if let message, !message.isEmpty {
                    Text(message)
                        .font(.caption2)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
            }
            Spacer()
        }
        .padding(12)
        .background(ColorTokens.dangerSoft)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(ColorTokens.danger, lineWidth: strokeWidth)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var loadingStateView: some View {
        VStack(spacing: 12) {
            ProgressView()
                .tint(ColorTokens.action)
            Text(LocalizedStringKey("exam.score.loading"))
                .font(.subheadline)
                .foregroundStyle(ColorTokens.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(ColorTokens.surface)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func netScoreCard(_ summary: ExamSessionScoreSummaryResponse) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(summary.examCode)
                        .font(.caption.monospaced().weight(.semibold))
                        .foregroundStyle(ColorTokens.textSecondary)
                    Text(LocalizedStringKey("exam.score.net_score.label"))
                        .font(.headline)
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                Spacer()
                Text(LocalizedStringKey(viewModel.formulaDescriptionKey))
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(ColorTokens.action)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(ColorTokens.actionSoft)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(ColorTokens.action, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .accessibilityIdentifier("exam.score.formulaBadge")
            }

            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(String(format: "%.2f", summary.netScore))
                    .font(.system(size: 36, weight: .bold, design: .rounded).monospacedDigit())
                    .foregroundStyle(ColorTokens.textPrimary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(String(format: "%.1f%%", summary.normalizedPercentageScore))
                        .font(.subheadline.weight(.semibold).monospacedDigit())
                        .foregroundStyle(ColorTokens.stepHighlight)
                    Text(String(format: "Accuracy %.1f%%", summary.accuracyPercentage))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(ColorTokens.textSecondary)
                }
                Spacer()
                Text("v\(summary.scoreVersion)")
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .foregroundStyle(ColorTokens.textSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(ColorTokens.surfaceElevated)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(strokeColor, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 6))
            }

            Divider()
                .overlay(ColorTokens.borderSubtle)

            HStack {
                metricLabelValue(
                    key: "exam.score.raw_correct.label",
                    value: String(format: "%.2f", summary.rawCorrectScore)
                )
                Spacer()
                metricLabelValue(
                    key: "exam.score.offset.label",
                    value: String(format: "%.2f", summary.wrongPenaltyDeduction)
                )
                Spacer()
                metricLabelValue(
                    key: "exam.score.range.label",
                    value: String(format: "%.1f .. %.1f", summary.minPossibleNetScore, summary.maxPossibleNetScore)
                )
            }

            Text(
                String(
                    format: "LLM Tokens: %d • Cost: $%.2f • INV-EXAM-004 Deterministic",
                    viewModel.llmTokensUsedDisplay,
                    viewModel.inferenceCostUsdDisplay
                )
            )
            .font(.caption2.monospaced())
            .foregroundStyle(ColorTokens.textSecondary)
        }
        .padding(16)
        .background(ColorTokens.surface)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.score.netScoreCard")
    }

    private func metricLabelValue(key: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(LocalizedStringKey(key))
                .font(.caption2)
                .foregroundStyle(ColorTokens.textSecondary)
            Text(value)
                .font(.subheadline.monospacedDigit().weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)
        }
    }

    private func countsRow(_ summary: ExamSessionScoreSummaryResponse) -> some View {
        HStack(spacing: 10) {
            countChip(
                key: "exam.score.count.correct",
                value: "\(summary.correctCount)",
                accent: ColorTokens.success,
                fill: ColorTokens.successSoft
            )
            countChip(
                key: "exam.score.count.wrong",
                value: "\(summary.wrongCount)",
                accent: ColorTokens.danger,
                fill: ColorTokens.dangerSoft
            )
            countChip(
                key: "exam.score.count.blank",
                value: "\(summary.blankCount)",
                accent: ColorTokens.warning,
                fill: ColorTokens.warningSoft
            )
            countChip(
                key: "exam.score.count.time",
                value: "\(summary.totalTimeSpentSeconds)s",
                accent: ColorTokens.action,
                fill: ColorTokens.actionSoft
            )
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.score.countsRow")
    }

    private func countChip(key: String, value: String, accent: Color, fill: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(LocalizedStringKey(key))
                .font(.caption2.weight(.medium))
                .foregroundStyle(ColorTokens.textSecondary)
                .lineLimit(1)
            Text(value)
                .font(.headline.monospacedDigit().weight(.bold))
                .foregroundStyle(accent)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(fill)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(accent, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    private func topicBreakdownSection(_ topics: [ExamTopicScoreBreakdown]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(LocalizedStringKey("exam.score.section.topic_breakdown"))
                    .font(.headline)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                if viewModel.selectedTopicCode != nil {
                    Button(LocalizedStringKey("exam.score.action.clear_topic_filter")) {
                        viewModel.selectTopicFilter(nil)
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.action)
                }
            }

            ForEach(topics) { topic in
                let isSelected = viewModel.selectedTopicCode == topic.topicCode
                Button {
                    viewModel.selectTopicFilter(topic.topicCode)
                } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(topic.topicTitle)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(ColorTokens.textPrimary)
                                .multilineTextAlignment(.leading)
                            Spacer()
                            Text(String(format: "%.2f net", topic.netScore))
                                .font(.subheadline.monospacedDigit().weight(.bold))
                                .foregroundStyle(ColorTokens.action)
                        }
                        HStack(spacing: 12) {
                            Text("\(topic.correctCount)C / \(topic.wrongCount)W / \(topic.blankCount)B")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(ColorTokens.textSecondary)
                            Spacer()
                            Text(String(format: "%.0f%% • %.0fs/q", topic.accuracyPercentage, topic.averageSecondsPerItem))
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(ColorTokens.textSecondary)
                        }
                    }
                    .padding(12)
                    .background(isSelected ? ColorTokens.actionSoft : ColorTokens.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(isSelected ? ColorTokens.action : strokeColor, lineWidth: isSelected ? 2 : strokeWidth)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.score.topicBreakdownSection")
    }

    private func difficultyBreakdownSection(_ difficulties: [ExamDifficultyScoreBreakdown]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(LocalizedStringKey("exam.score.section.difficulty_breakdown"))
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(difficulties) { band in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(band.difficultyBand.rawValue)
                            .font(.caption.weight(.bold))
                            .foregroundStyle(ColorTokens.textPrimary)
                        Text(String(format: "%.2f net (%.0f%%)", band.netScore, band.accuracyPercentage))
                            .font(.caption.monospacedDigit().weight(.semibold))
                            .foregroundStyle(ColorTokens.stepHighlight)
                        Text("\(band.correctCount)C • \(band.wrongCount)W • \(band.blankCount)B")
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .background(ColorTokens.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(strokeColor, lineWidth: strokeWidth)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.score.difficultyBreakdownSection")
    }

    private func itemEvidenceSection(_ summary: ExamSessionScoreSummaryResponse) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(LocalizedStringKey("exam.score.section.item_evidence"))
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)

            Picker(LocalizedStringKey("exam.score.section.item_evidence"), selection: $viewModel.selectedFilter) {
                ForEach(ExamItemEvidenceFilter.allCases) { filter in
                    Text(LocalizedStringKey(filter.localizationKey))
                        .tag(filter)
                }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("exam.score.itemEvidenceFilterPicker")

            VStack(alignment: .leading, spacing: 10) {
                ForEach(viewModel.filteredItemEvaluations) { item in
                    itemEvidenceRow(item)
                }
            }
            .animation(systemReduceMotion ? nil : .easeInOut(duration: 0.15), value: viewModel.selectedFilter)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("exam.score.itemEvidenceList")
        }
    }

    private func itemEvidenceRow(_ item: ExamSessionItemEvaluation) -> some View {
        let outcomeAccent: Color = {
            switch item.evaluationOutcome {
            case .correct: return ColorTokens.success
            case .wrong: return ColorTokens.danger
            case .blank: return ColorTokens.warning
            }
        }()

        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("#\(item.positionIndex) • \(item.questionCode)")
                    .font(.caption.monospaced().weight(.bold))
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                if item.flaggedForReview {
                    Image(systemName: "flag.fill")
                        .font(.caption)
                        .foregroundStyle(ColorTokens.warning)
                }
                Text(item.evaluationOutcome.rawValue)
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(outcomeAccent)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(outcomeBackground(for: item.evaluationOutcome))
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(outcomeAccent, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 6))
            }

            Text("\(item.topicTitle) • \(item.difficultyBand.rawValue) • \(item.cognitiveLevel.rawValue)")
                .font(.caption2)
                .foregroundStyle(ColorTokens.textSecondary)

            HStack(spacing: 12) {
                if item.formatType == .numericGridIn {
                    let selectedStr = item.numericResponseValue.map { String(format: "%.2f", $0) } ?? "—"
                    let expectedStr = item.expectedNumericAnswer.map { String(format: "%.2f", $0) } ?? "—"
                    Text("Submitted: \(selectedStr) • Expected: \(expectedStr)")
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(ColorTokens.textPrimary)
                } else {
                    Text("Selected: \(item.selectedOptionKey ?? "—") • Key: \(item.correctOptionKey ?? "—")")
                        .font(.caption.monospaced())
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                Spacer()
                Text(String(format: "%+.2f", item.netPointsContribution))
                    .font(.caption.monospacedDigit().weight(.bold))
                    .foregroundStyle(outcomeAccent)
            }

            if let rationale = item.selectedOptionDistractorRationale, !rationale.isEmpty {
                Text(rationale)
                    .font(.caption)
                    .foregroundStyle(ColorTokens.danger)
            }

            Text(item.solutionExplanationMarkdown)
                .font(.caption)
                .foregroundStyle(ColorTokens.textSecondary)

            HStack {
                Text("Source: \(item.evidenceSourceRef) (v\(item.questionVersion))")
                    .font(.caption2.monospaced())
                    .foregroundStyle(ColorTokens.textTertiary)
                Spacer()
                if item.f28ScratchpadStatus == .attachedMetadataOnly {
                    Text("F28: \(item.scratchpadAssetCount) pages")
                        .font(.caption2.monospaced().weight(.semibold))
                        .foregroundStyle(ColorTokens.action)
                }
            }
        }
        .padding(12)
        .background(ColorTokens.surface)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func outcomeBackground(for outcome: ExamItemEvaluationOutcome) -> Color {
        switch outcome {
        case .correct: return ColorTokens.successSoft
        case .wrong: return ColorTokens.dangerSoft
        case .blank: return ColorTokens.warningSoft
        }
    }

    private func regradeAuditSection(_ summary: ExamSessionScoreSummaryResponse) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(LocalizedStringKey("exam.score.section.regrade_audits"))
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)

            Picker(LocalizedStringKey("exam.score.regrade.reason_label"), selection: $viewModel.regradeReasonCode) {
                ForEach(ExamScoreRegradeReasonCode.allCases) { code in
                    Text(code.rawValue)
                        .tag(code)
                }
            }
            .pickerStyle(.menu)

            TextField(
                LocalizedStringKey("exam.score.regrade.detail_placeholder"),
                text: $viewModel.regradeReasonDetail
            )
            .textFieldStyle(.roundedBorder)

            Button {
                let raw = viewModel.regradeReasonDetail.trimmingCharacters(in: .whitespacesAndNewlines)
                let detail = raw.isEmpty ? "Deterministic policy & key verification regrade" : raw
                Task {
                    await viewModel.triggerRegrade(
                        reasonCode: viewModel.regradeReasonCode,
                        reasonDetail: detail
                    )
                }
            } label: {
                HStack {
                    if viewModel.isRegrading {
                        ProgressView()
                    }
                    Text(LocalizedStringKey("exam.score.action.trigger_regrade"))
                        .font(.subheadline.weight(.semibold))
                }
                .frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(ColorTokens.actionForeground)
                .background(ColorTokens.action)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isRegrading)
            .accessibilityIdentifier("exam.score.regradeButton")

            if viewModel.regradeAudits.isEmpty {
                Text(LocalizedStringKey("exam.score.regrade.empty"))
                    .font(.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            } else {
                ForEach(viewModel.regradeAudits) { audit in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("v\(audit.previousScoreVersion) -> v\(audit.newScoreVersion) • \(audit.reasonCode.rawValue)")
                                .font(.caption.monospaced().weight(.semibold))
                                .foregroundStyle(ColorTokens.textPrimary)
                            Spacer()
                            Text(String(format: "%+.2f net", audit.deltaSummary.netScoreDelta))
                                .font(.caption.monospacedDigit().weight(.bold))
                                .foregroundStyle(ColorTokens.stepHighlight)
                        }
                        Text(audit.reasonDetail)
                            .font(.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                        Text("\(audit.triggeredBy) • \(audit.regradedAt)")
                            .font(.caption2.monospaced())
                            .foregroundStyle(ColorTokens.textTertiary)
                    }
                    .padding(10)
                    .background(ColorTokens.surfaceElevated)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(strokeColor, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
        }
        .padding(14)
        .background(ColorTokens.surface)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.score.regradeAuditSection")
    }
}
