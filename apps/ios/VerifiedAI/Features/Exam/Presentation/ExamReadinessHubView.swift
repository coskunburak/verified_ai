import SwiftUI

/// Screen 9: YKS / Exam Readiness & Net-Gain Simulator Hub (Concept B Design System 2.0).
/// Pure flat surfaces, 1pt hairline borders, ColorTokens only (zero gradients, zero shadows, zero purple/indigo).
public struct ExamReadinessHubView: View {
    @ObservedObject public var viewModel: ExamReadinessViewModel
    public var onStartCalibration: (() -> Void)?

    public init(
        viewModel: ExamReadinessViewModel,
        onStartCalibration: (() -> Void)? = nil
    ) {
        self.viewModel = viewModel
        self.onStartCalibration = onStartCalibration
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
                if let preExam = viewModel.makePreExamViewModel() {
                    NavigationLink { PreExamCramView(viewModel: preExam) } label: { Text("exam.preExam.title") }
                        .accessibilityHint(Text("exam.preExam.openHint"))
                }
                if let progressVM = viewModel.makeProgressHistoryViewModel() {
                    NavigationLink {
                        ExamProgressHistoryView(viewModel: progressVM)
                    } label: {
                        Label(LocalizedStringKey("exam.progress.title"), systemImage: "chart.xyaxis.line")
                            .font(TypographyTokens.cardTitle)
                            .foregroundStyle(ColorTokens.action)
                    }
                    .accessibilityIdentifier("exam.readiness.openProgressButton")
                    .accessibilityHint(Text("exam.progress.openHint"))
                } else {
                    Text(LocalizedStringKey("exam.progress.unsupported"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("exam.readiness.progressUnsupported")
                }
                examSelectorStrip

                if viewModel.isOfflineFallback {
                    offlineBanner
                }

                if let errorKey = viewModel.errorLocalizationKey {
                    errorBanner(key: errorKey, message: viewModel.errorMessage)
                }

                if viewModel.isLoading && viewModel.projection == nil {
                    loadingCard
                } else if let projection = viewModel.projection {
                    intervalCard(projection)

                    if projection.calibrationRequired || projection.confidenceTier == .preliminary {
                        preliminaryCalibrationBanner(projection)
                    }

                    trapsCard(projection)
                    simulatorCard(projection)
                    sectionsListCard(projection)
                    roiListCard(projection)

                    recomputeButton
                }
            }
            .padding(16)
        }
        .background(ColorTokens.background.ignoresSafeArea())
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.readiness.screen")
        .navigationTitle(Text(LocalizedStringKey("exam.readiness.title")))
        .navigationBarTitleDisplayMode(.inline)
        .task(id: ObjectIdentifier(viewModel)) {
            if viewModel.projection == nil && !viewModel.isLoading {
                await viewModel.loadReadiness(forceRecompute: false)
            }
        }
        .onAppear {
            if viewModel.projection == nil && !viewModel.isLoading {
                Task {
                    if viewModel.projection == nil && !viewModel.isLoading {
                        await viewModel.loadReadiness(forceRecompute: false)
                    }
                }
            }
        }
    }

    private var examSelectorStrip: some View {
        HStack(spacing: 8) {
            examCodeChip(title: "TYT Math", code: "TR_YKS_TYT_MATH")
            examCodeChip(title: "AYT Math", code: "TR_YKS_AYT_MATH")
            examCodeChip(title: "SAT Math", code: "INTL_SAT_MATH")
        }
    }

    private func examCodeChip(title: String, code: String) -> some View {
        let isSelected = ExamReadinessViewModel.canonicalExamCode(viewModel.selectedExamCode) == code
        return Button {
            Task {
                await viewModel.switchExam(to: code)
            }
        } label: {
            Text(title)
                .font(TypographyTokens.caption.weight(.semibold))
                .foregroundStyle(isSelected ? ColorTokens.actionForeground : ColorTokens.textPrimary)
                .padding(.horizontal, 12)
                .frame(minHeight: 38)
                .background(isSelected ? ColorTokens.action : ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(isSelected ? ColorTokens.action : strokeColor, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("exam.readiness.examChip.\(code)")
    }

    private var offlineBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "wifi.slash")
                .foregroundStyle(ColorTokens.warning)
            Text(LocalizedStringKey("exam.readiness.offline_banner"))
                .font(.caption.weight(.semibold))
                .foregroundStyle(ColorTokens.warning)
            Spacer()
        }
        .padding(12)
        .background(ColorTokens.warningSoft)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(ColorTokens.warning, lineWidth: strokeWidth)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("exam.readiness.offlineBanner")
    }

    private func errorBanner(key: String, message: String?) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle")
                .foregroundStyle(ColorTokens.danger)
            VStack(alignment: .leading, spacing: 4) {
                Text(LocalizedStringKey(key))
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
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(ColorTokens.danger, lineWidth: strokeWidth)
        )
    }

    private var loadingCard: some View {
        VStack(spacing: 12) {
            ProgressView()
                .tint(ColorTokens.action)
            Text(LocalizedStringKey("exam.readiness.loading"))
                .font(.subheadline)
                .foregroundStyle(ColorTokens.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
    }

    private func intervalCard(_ projection: ExamReadinessProjection) -> some View {
        let badgeTone = confidenceTone(for: projection.confidenceTier)
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(projection.examCode)
                        .font(.caption.monospaced().weight(.semibold))
                        .foregroundStyle(ColorTokens.textSecondary)
                    Text(LocalizedStringKey("exam.readiness.interval.title"))
                        .font(.headline)
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                Spacer()
                Text(LocalizedStringKey(projection.confidenceTier.localizationKey))
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(badgeTone.foreground)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(badgeTone.background)
                    .clipShape(Capsule())
                    .overlay(
                        Capsule()
                            .stroke(badgeTone.foreground, lineWidth: 1)
                    )
                    .accessibilityIdentifier("exam.readiness.confidenceBadge")
            }

            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(String(format: "%.1f – %.1f Net", projection.estimatedNetLow, projection.estimatedNetHigh))
                    .font(.system(size: 28, weight: .bold, design: .rounded).monospacedDigit())
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text(String(format: "%.0f%% conf", projection.epistemicConfidence * 100.0))
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .foregroundStyle(ColorTokens.stepHighlight)
            }

            intervalProgressBar(projection)

            HStack {
                metricColumn(
                    key: "exam.readiness.interval.mid_label",
                    value: String(format: "%.2f", projection.estimatedNetMid)
                )
                Spacer()
                if let targetNet = projection.targetNetScore {
                    metricColumn(
                        key: "exam.readiness.interval.target_label",
                        value: String(format: "%.1f", targetNet)
                    )
                    Spacer()
                }
                if let gap = projection.netGapToTarget {
                    metricColumn(
                        key: "exam.readiness.interval.gap_label",
                        value: String(format: "%+.2f", gap)
                    )
                }
            }

            Divider()
                .overlay(ColorTokens.borderSubtle)

            Text(
                String(
                    format: "%d evidence • %d mocks • Pacing %.2f • Retention %.0f%%",
                    projection.totalEvidenceCount,
                    projection.completedMockCount,
                    projection.pacingFactor,
                    projection.meanRetentionProbability * 100.0
                )
            )
            .font(.caption2.monospacedDigit())
            .foregroundStyle(ColorTokens.textSecondary)

            Text(LocalizedStringKey("exam.readiness.zero_cost_note"))
                .font(.caption2.monospaced())
                .foregroundStyle(ColorTokens.textTertiary)
        }
        .padding(16)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.readiness.intervalCard")
    }

    private func intervalProgressBar(_ projection: ExamReadinessProjection) -> some View {
        let maxQ = max(1.0, Double(projection.totalQuestionCount))
        let lowRatio = min(1.0, max(0.0, projection.estimatedNetLow / maxQ))
        let highRatio = min(1.0, max(lowRatio, projection.estimatedNetHigh / maxQ))
        let midRatio = min(1.0, max(0.0, projection.estimatedNetMid / maxQ))

        return GeometryReader { geo in
            let width = max(1, geo.size.width)
            let rangeX = width * lowRatio
            let rangeW = max(8, width * (highRatio - lowRatio))
            let midX = min(width - 3, max(0, width * midRatio))

            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(ColorTokens.surfaceElevated)
                    .frame(height: 10)

                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(ColorTokens.stepHighlightSoft)
                    .frame(width: rangeW, height: 10)
                    .offset(x: rangeX)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4, style: .continuous)
                            .stroke(ColorTokens.stepHighlight, lineWidth: 1)
                            .frame(width: rangeW, height: 10)
                            .offset(x: rangeX)
                    )

                Rectangle()
                    .fill(ColorTokens.action)
                    .frame(width: 3, height: 14)
                    .offset(x: midX)
            }
        }
        .frame(height: 14)
    }

    private func metricColumn(key: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(LocalizedStringKey(key))
                .font(.caption2)
                .foregroundStyle(ColorTokens.textSecondary)
            Text(value)
                .font(.subheadline.monospacedDigit().weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)
        }
    }

    private func preliminaryCalibrationBanner(_ projection: ExamReadinessProjection) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: "waveform.path.ecg")
                    .foregroundStyle(ColorTokens.warning)
                Text(LocalizedStringKey("exam.readiness.preliminary.title"))
                    .font(.subheadline.weight(.bold))
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text("\(projection.recommendedDiagnosticQuestionCount)Q")
                    .font(.caption.monospacedDigit().weight(.bold))
                    .foregroundStyle(ColorTokens.warning)
            }

            Text(LocalizedStringKey("exam.readiness.preliminary.body"))
                .font(.caption)
                .foregroundStyle(ColorTokens.textSecondary)

            Button {
                onStartCalibration?()
            } label: {
                Label(LocalizedStringKey("exam.readiness.preliminary.cta"), systemImage: "play.circle.fill")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(ColorTokens.actionForeground)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(ColorTokens.action)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("exam.readiness.startCalibrationButton")
        }
        .padding(14)
        .background(ColorTokens.warningSoft)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(ColorTokens.warning, lineWidth: strokeWidth)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.readiness.preliminaryBanner")
    }

    private func trapsCard(_ projection: ExamReadinessProjection) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(LocalizedStringKey("exam.readiness.traps.title"))
                    .font(.headline)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text(String(format: "%.2f Net", projection.netLostToTraps))
                    .font(.headline.monospacedDigit().weight(.bold))
                    .foregroundStyle(projection.netLostToTraps > 0 ? ColorTokens.danger : ColorTokens.success)
            }

            if projection.activeTrapBreakdown.isEmpty {
                Text(LocalizedStringKey("exam.readiness.traps.empty"))
                    .font(.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            } else {
                ForEach(projection.activeTrapBreakdown) { trap in
                    HStack(alignment: .center, spacing: 10) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(trap.trapFamilyCode)
                                .font(.caption.monospaced().weight(.bold))
                                .foregroundStyle(ColorTokens.textPrimary)
                            Text(
                                String(
                                    format: "Posterior %.0f%% • %d skills",
                                    trap.posteriorRate * 100.0,
                                    trap.affectedSkillCount
                                )
                            )
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(ColorTokens.textSecondary)
                        }
                        Spacer()
                        Text(String(format: "-%.2f Net", trap.estimatedNetLoss))
                            .font(.subheadline.monospacedDigit().weight(.bold))
                            .foregroundStyle(ColorTokens.danger)
                    }
                    .padding(10)
                    .background(ColorTokens.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .stroke(strokeColor, lineWidth: 1)
                    )
                }
            }
        }
        .padding(14)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.readiness.trapsCard")
    }

    private func simulatorCard(_ projection: ExamReadinessProjection) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(LocalizedStringKey("exam.readiness.simulator.title"))
                    .font(.headline)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text(String(format: "%+.2f Net", viewModel.simulatedTrapRecoveryNet + viewModel.simulatedStudyGainNet))
                    .font(.subheadline.monospacedDigit().weight(.bold))
                    .foregroundStyle(ColorTokens.success)
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(LocalizedStringKey("exam.readiness.simulator.trap_slider"))
                        .font(.caption.weight(.medium))
                        .foregroundStyle(ColorTokens.textSecondary)
                    Spacer()
                    Text(String(format: "%.0f%% (+%.2f)", viewModel.simulatedTrapReductionRatio * 100.0, viewModel.simulatedTrapRecoveryNet))
                        .font(.caption.monospacedDigit().weight(.semibold))
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                Slider(value: $viewModel.simulatedTrapReductionRatio, in: 0...1, step: 0.1)
                    .tint(ColorTokens.action)
                    .accessibilityIdentifier("exam.readiness.simulator.trapSlider")
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(LocalizedStringKey("exam.readiness.simulator.hours_slider"))
                        .font(.caption.weight(.medium))
                        .foregroundStyle(ColorTokens.textSecondary)
                    Spacer()
                    Text(String(format: "%.1fh (+%.2f)", viewModel.simulatedAdditionalStudyHours, viewModel.simulatedStudyGainNet))
                        .font(.caption.monospacedDigit().weight(.semibold))
                        .foregroundStyle(ColorTokens.textPrimary)
                }
                Slider(value: $viewModel.simulatedAdditionalStudyHours, in: 0...20, step: 1)
                    .tint(ColorTokens.stepHighlight)
                    .accessibilityIdentifier("exam.readiness.simulator.hoursSlider")
            }

            Divider()
                .overlay(ColorTokens.borderSubtle)

            HStack {
                Text(LocalizedStringKey("exam.readiness.simulator.projected_label"))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.textSecondary)
                Spacer()
                Text(
                    String(
                        format: "%.1f – %.1f (Mid %.2f)",
                        viewModel.simulatedProjectedNetLow,
                        viewModel.simulatedProjectedNetHigh,
                        viewModel.simulatedProjectedNetMid
                    )
                )
                .font(.subheadline.monospacedDigit().weight(.bold))
                .foregroundStyle(ColorTokens.stepHighlight)
            }
        }
        .padding(14)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.readiness.simulatorCard")
    }

    private func sectionsListCard(_ projection: ExamReadinessProjection) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(LocalizedStringKey("exam.readiness.sections.title"))
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)

            ForEach(projection.sectionReadiness) { section in
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text(section.sectionTitleKey)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(ColorTokens.textPrimary)
                        Spacer()
                        Text(String(format: "%.1f – %.1f Net", section.estimatedNetLow, section.estimatedNetHigh))
                            .font(.caption.monospacedDigit().weight(.bold))
                            .foregroundStyle(ColorTokens.stepHighlight)
                    }
                    HStack {
                        Text("\(section.questionCount)Q • Mid \(String(format: "%.2f", section.estimatedNetMid))")
                            .font(.caption2.monospacedDigit())
                            .foregroundStyle(ColorTokens.textSecondary)
                        Spacer()
                        Text(
                            String(
                                format: "Mastery %.0f%% • Retention %.0f%%",
                                section.meanMastery * 100.0,
                                section.meanRetention * 100.0
                            )
                        )
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(ColorTokens.textSecondary)
                    }
                }
                .padding(10)
                .background(ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(strokeColor, lineWidth: 1)
                )
            }
        }
        .padding(14)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.readiness.sectionsList")
    }

    private func roiListCard(_ projection: ExamReadinessProjection) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(LocalizedStringKey("exam.readiness.roi.title"))
                .font(.headline)
                .foregroundStyle(ColorTokens.textPrimary)

            ForEach(projection.topRoiAllocations) { item in
                VStack(alignment: .leading, spacing: 6) {
                    HStack(alignment: .top) {
                        Text("#\(item.priorityRank)")
                            .font(.caption.monospacedDigit().weight(.bold))
                            .foregroundStyle(ColorTokens.action)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(ColorTokens.actionSoft)
                            .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))

                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.skillCode)
                                .font(.caption.monospaced().weight(.semibold))
                                .foregroundStyle(ColorTokens.textPrimary)
                            Text(LocalizedStringKey(item.rationaleCode.localizationKey))
                                .font(.caption2.weight(.medium))
                                .foregroundStyle(ColorTokens.stepHighlight)
                        }
                        Spacer()
                        Text(String(format: "+%.2f Net/h", item.expectedNetGainPerHour))
                            .font(.caption.monospacedDigit().weight(.bold))
                            .foregroundStyle(ColorTokens.success)
                    }

                    HStack {
                        Text(
                            String(
                                format: "Weight %.1fQ • Mastery %.0f%% • Retention %.0f%%",
                                item.blueprintQuestionWeight,
                                item.currentMastery * 100.0,
                                item.retentionProbability * 100.0
                            )
                        )
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(ColorTokens.textSecondary)
                        Spacer()
                        Text("\(item.recommendedStudyMinutes)m")
                            .font(.caption2.monospacedDigit().weight(.semibold))
                            .foregroundStyle(ColorTokens.textPrimary)
                    }
                }
                .padding(10)
                .background(ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(strokeColor, lineWidth: 1)
                )
            }
        }
        .padding(14)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.readiness.roiList")
    }

    private var recomputeButton: some View {
        Button {
            Task {
                await viewModel.recompute()
            }
        } label: {
            HStack {
                if viewModel.isRecomputing {
                    ProgressView()
                }
                Text(LocalizedStringKey("exam.readiness.recompute_button"))
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(ColorTokens.actionForeground)
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(ColorTokens.action)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isRecomputing)
        .accessibilityIdentifier("exam.readiness.recomputeButton")
            .modifier(LearningOsActionGate(capability: .examReadinessSimulator))
    }

    private func confidenceTone(for tier: ExamReadinessConfidenceTier) -> AppBadgeTone {
        switch tier {
        case .preliminary: return .warning
        case .calibrating: return .stepHighlight
        case .established: return .action
        case .highConfidence: return .success
        }
    }
}
