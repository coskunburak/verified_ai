import SwiftUI
import Charts

/// Concept B flat longitudinal exam analytics view (INV-EXAM-005).
/// Zero gradients, zero shadows, zero glassmorphism; accessible at Dynamic Type AX3 and 320pt width.
struct ExamProgressHistoryView: View {
    @StateObject var viewModel: ExamProgressHistoryViewModel
    @Environment(\.locale) private var locale
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private var isTurkish: Bool {
        locale.language.languageCode?.identifier == "tr"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                examSwitcherStrip

                if viewModel.isOffline {
                    statusBanner(
                        icon: "wifi.slash",
                        color: ColorTokens.warning,
                        background: ColorTokens.warningSoft,
                        key: "exam.progress.offlineBanner"
                    )
                }

                if viewModel.state == .disabled {
                    statusBanner(
                        icon: "lock.shield",
                        color: ColorTokens.textSecondary,
                        background: ColorTokens.surfaceElevated,
                        key: "exam.progress.disabled"
                    )
                } else if case .failed(let key) = viewModel.state {
                    VStack(alignment: .leading, spacing: 10) {
                        statusBanner(
                            icon: "exclamationmark.triangle",
                            color: ColorTokens.danger,
                            background: ColorTokens.dangerSoft,
                            key: key
                        )
                        Button(LocalizedStringKey("exam.progress.retry")) {
                            Task { await viewModel.load() }
                        }
                        .buttonStyle(.bordered)
                        .tint(ColorTokens.action)
                    }
                } else if viewModel.isLoading && viewModel.report == nil {
                    loadingCard
                } else if let report = viewModel.report {
                    summaryCard(report)
                    trajectoryCard(report)
                    pacingCard(report)
                    trapRemediationCard(report)
                }
            }
            .padding(16)
        }
        .background(ColorTokens.background.ignoresSafeArea())
        .navigationTitle(Text(LocalizedStringKey("exam.progress.title")))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityIdentifier("exam.progress.screen")
        .task {
            if viewModel.report == nil && viewModel.state == .idle {
                await viewModel.load()
            }
        }
    }

    private var examSwitcherStrip: some View {
        HStack(spacing: 8) {
            examChip(label: "TYT", code: "TR_YKS_TYT_MATH")
            examChip(label: "AYT", code: "TR_YKS_AYT_MATH")
        }
    }

    private func examChip(label: String, code: String) -> some View {
        let selected = viewModel.exam == code
        return Button {
            Task { await viewModel.switchExam(to: code) }
        } label: {
            Text(label)
                .font(TypographyTokens.caption.weight(.semibold))
                .foregroundStyle(selected ? ColorTokens.actionForeground : ColorTokens.textPrimary)
                .frame(maxWidth: .infinity, minHeight: 40)
                .background(selected ? ColorTokens.action : ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(selected ? ColorTokens.action : ColorTokens.borderSubtle, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("exam.progress.chip.\(code)")
    }

    private func statusBanner(icon: String, color: Color, background: Color, key: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .foregroundStyle(color)
                .accessibilityHidden(true)
            Text(LocalizedStringKey(key))
                .font(TypographyTokens.caption.weight(.semibold))
                .foregroundStyle(color)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(background)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke(color, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
    }

    private var loadingCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            ProgressView()
                .tint(ColorTokens.action)
            Text(LocalizedStringKey("exam.progress.loading"))
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardSurface()
    }

    private func summaryCard(_ report: ExamProgressReport) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(LocalizedStringKey("exam.progress.summary.title"))
                .font(TypographyTokens.sectionTitle)
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            Text("\(report.examCode) · \(report.mockCount)")
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)

            if let delta = report.deltaNet {
                HStack(spacing: 6) {
                    Image(systemName: delta >= 0 ? "arrow.up.right" : "arrow.down.right")
                        .foregroundStyle(delta >= 0 ? ColorTokens.success : ColorTokens.warning)
                        .accessibilityHidden(true)
                    Text(String(format: "%+.2f", locale: locale, delta))
                        .font(TypographyTokens.metricValue)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Text(LocalizedStringKey("exam.progress.summary.deltaLabel"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
            }

            Text(LocalizedStringKey("exam.progress.zeroCostNote"))
                .font(TypographyTokens.microLabel)
                .foregroundStyle(ColorTokens.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardSurface()
        .accessibilityElement(children: .combine)
    }

    private func trajectoryCard(_ report: ExamProgressReport) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(LocalizedStringKey("exam.progress.trajectory.title"))
                .font(TypographyTokens.sectionTitle)
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            if report.scoreTrajectory.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text(LocalizedStringKey("exam.progress.empty.title"))
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                    Text(LocalizedStringKey("exam.progress.empty.body"))
                        .font(TypographyTokens.body)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            } else {
                if report.mockCount == 1 {
                    Text(LocalizedStringKey("exam.progress.insufficient.oneMock"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                } else if report.mockCount == 2 {
                    Text(LocalizedStringKey("exam.progress.insufficient.twoMocks"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }

                trajectoryChart(report.scoreTrajectory)
                    .frame(minHeight: 190)
                    .transaction { transaction in
                        transaction.animation = nil
                    }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(LocalizedStringKey("exam.progress.trajectory.title")))
                    .accessibilityValue(trajectoryAccessibilitySummary(report.scoreTrajectory))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardSurface()
    }

    @ViewBuilder
    private func trajectoryChart(_ points: [ExamScoreTrajectoryPoint]) -> some View {
        Chart {
            ForEach(points) { point in
                if points.count >= 2 {
                    LineMark(
                        x: .value("Date", point.submittedAt),
                        y: .value("Net", point.netScore),
                        series: .value("Series", "Net")
                    )
                    .foregroundStyle(ColorTokens.action)

                    LineMark(
                        x: .value("Date", point.submittedAt),
                        y: .value("MA3", point.movingAverage3),
                        series: .value("Series", "MA3")
                    )
                    .lineStyle(StrokeStyle(lineWidth: 2, dash: [5, 4]))
                    .foregroundStyle(ColorTokens.stepHighlight)
                }

                PointMark(
                    x: .value("Date", point.submittedAt),
                    y: .value("Net", point.netScore)
                )
                .foregroundStyle(ColorTokens.action)
            }
        }
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 4))
        }
        .chartYAxis {
            AxisMarks(position: .leading)
        }
        .animation(reduceMotion ? nil : .none, value: points)
    }

    private func pacingCard(_ report: ExamProgressReport) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(LocalizedStringKey("exam.progress.pacing.title"))
                .font(TypographyTokens.sectionTitle)
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            if report.pacingByTopic.isEmpty {
                Text(LocalizedStringKey("exam.progress.pacing.empty"))
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                let measuredItems = report.pacingByTopic.filter {
                    $0.meanSecondsPerQuestion != nil && $0.expectedSecondsPerQuestion != nil
                }
                if !measuredItems.isEmpty {
                    pacingBarChart(measuredItems)
                        .frame(minHeight: 180)
                        .transaction { $0.animation = nil }
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(Text(LocalizedStringKey("exam.progress.pacing.title")))
                        .accessibilityValue(pacingAccessibilitySummary(measuredItems))
                }

                ForEach(report.pacingByTopic) { item in
                    pacingRow(item)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardSurface()
    }

    private struct PacingBarEntry: Identifiable {
        let id: String
        let topic: String
        let kind: String
        let seconds: Double
    }

    private func pacingBarChart(_ items: [ExamTopicPacingItem]) -> some View {
        let entries: [PacingBarEntry] = items.prefix(6).flatMap { item in
            [
                PacingBarEntry(
                    id: "\(item.id)-actual",
                    topic: item.topicCode,
                    kind: isTurkish ? "Ortalama" : "Actual",
                    seconds: item.meanSecondsPerQuestion ?? 0
                ),
                PacingBarEntry(
                    id: "\(item.id)-expected",
                    topic: item.topicCode,
                    kind: isTurkish ? "Hedef" : "Expected",
                    seconds: item.expectedSecondsPerQuestion ?? 0
                )
            ]
        }
        return Chart(entries) { entry in
            BarMark(
                x: .value("Topic", entry.topic),
                y: .value("Seconds", entry.seconds)
            )
            .foregroundStyle(by: .value("Kind", entry.kind))
            .position(by: .value("Kind", entry.kind))
        }
        .chartForegroundStyleScale([
            (isTurkish ? "Ortalama" : "Actual"): ColorTokens.action,
            (isTurkish ? "Hedef" : "Expected"): ColorTokens.borderStrong
        ])
    }

    private func pacingRow(_ item: ExamTopicPacingItem) -> some View {
        let meta = pacingMetadata(item.classification)
        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Image(systemName: meta.icon)
                    .foregroundStyle(meta.color)
                    .accessibilityHidden(true)
                Text(item.subtopicCode.map { "\(item.topicCode) · \($0)" } ?? item.topicCode)
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 4)
                Text(LocalizedStringKey(meta.key))
                    .font(TypographyTokens.badge)
                    .foregroundStyle(meta.color)
            }

            if let mean = item.meanSecondsPerQuestion,
               let expected = item.expectedSecondsPerQuestion,
               let ratio = item.ratio {
                Text(String(format: "%.0fs / %.0fs (%.2fx) · %d", locale: locale, mean, expected, ratio, item.observedQuestionCount))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            } else {
                Text(LocalizedStringKey("exam.progress.pacing.insufficientDetail"))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
    }

    private func trapRemediationCard(_ report: ExamProgressReport) -> some View {
        let traps = report.trapRemediation
        return VStack(alignment: .leading, spacing: 10) {
            Text(LocalizedStringKey("exam.progress.traps.title"))
                .font(TypographyTokens.sectionTitle)
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)

            if let activeNow = traps.activeTrapCountNow {
                Text(String(format: NSLocalizedString("exam.progress.traps.activeNowFormat", comment: ""), activeNow))
                    .font(TypographyTokens.bodyMedium)
                    .foregroundStyle(ColorTokens.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if traps.conqueredTrapsEvidenceStatus == .verifiedTransitions {
                Text("\(report.conqueredTrapsCount)")
                    .font(TypographyTokens.metricValue)
                    .foregroundStyle(ColorTokens.success)
            } else {
                Text(LocalizedStringKey("exam.progress.traps.conqueredUnavailable"))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if traps.dataStatus == .computed, let r = traps.correlationCoefficient {
                Text(String(format: "r = %.2f (n = %d)", locale: locale, r, traps.correlationSampleSize))
                    .font(TypographyTokens.metricValue)
                    .foregroundStyle(ColorTokens.action)
            } else {
                Text(LocalizedStringKey("exam.progress.traps.status.\(traps.dataStatus.rawValue)"))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Text(LocalizedStringKey("exam.progress.traps.nonCausalNote"))
                .font(TypographyTokens.microLabel)
                .foregroundStyle(ColorTokens.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardSurface()
        .accessibilityElement(children: .combine)
    }

    private func pacingMetadata(_ classification: ExamTopicPacingClassification) -> (icon: String, color: Color, key: String) {
        switch classification {
        case .fast:
            return ("bolt.fill", ColorTokens.success, "exam.progress.pacing.fast")
        case .onPace:
            return ("checkmark.circle.fill", ColorTokens.action, "exam.progress.pacing.onPace")
        case .bottleneck:
            return ("clock.badge.exclamationmark", ColorTokens.warning, "exam.progress.pacing.bottleneck")
        case .insufficientData:
            return ("questionmark.circle", ColorTokens.textSecondary, "exam.progress.pacing.insufficientData")
        }
    }

    private func trajectoryAccessibilitySummary(_ points: [ExamScoreTrajectoryPoint]) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.locale = locale
        return points.map { pt in
            "\(formatter.string(from: pt.submittedAt)): \(String(format: "%.2f", locale: locale, pt.netScore)) (MA3 \(String(format: "%.2f", locale: locale, pt.movingAverage3)))"
        }.joined(separator: "; ")
    }

    private func pacingAccessibilitySummary(_ items: [ExamTopicPacingItem]) -> String {
        items.map { item in
            let mean = item.meanSecondsPerQuestion ?? 0
            let expected = item.expectedSecondsPerQuestion ?? 0
            return "\(item.topicCode): \(Int(mean.rounded()))s / \(Int(expected.rounded()))s"
        }.joined(separator: "; ")
    }
}

private extension View {
    func cardSurface() -> some View {
        self.padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.surface)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(ColorTokens.borderSubtle, lineWidth: 1))
    }
}
