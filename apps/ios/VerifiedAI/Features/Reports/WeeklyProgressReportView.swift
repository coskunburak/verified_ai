import SwiftUI

struct WeeklyProgressReportView: View {
    @ObservedObject var viewModel: WeeklyProgressReportViewModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ScaledMetric(relativeTo: .title2) private var kpiFontSize: CGFloat = 24
    @ScaledMetric(relativeTo: .caption) private var barMaxHeight: CGFloat = 64

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                weekSelectorHeader

                switch viewModel.state {
                case .idle, .loading:
                    loadingPlaceholder
                case .error(let message):
                    errorCard(message: message)
                case .degraded(let message, let retryable, let cachedDigest):
                    degradedBanner(message: message, retryable: retryable)
                    if let cachedDigest {
                        digestContent(cachedDigest)
                    }
                case .loaded(let digest):
                    digestContent(digest)
                }
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: 680, alignment: .leading)
        }
        .background(ColorTokens.background)
        .navigationTitle(LocalizedStringKey("weekly.report.title"))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if case .idle = viewModel.state {
                await viewModel.loadWeeklyDigest()
            }
        }
        .sheet(isPresented: $viewModel.isShowingShareScopePreview) {
            ShareScopePreviewSheet(viewModel: viewModel)
        }
        .accessibilityIdentifier("weekly.report.screen")
    }

    private var weekSelectorHeader: some View {
        HStack(spacing: SpacingTokens.md) {
            Button {
                Task { await viewModel.selectPreviousWeek() }
            } label: {
                Image(systemName: "chevron.left.circle.fill")
                    .font(TypographyTokens.title)
                    .foregroundStyle(ColorTokens.action)
            }
            .accessibilityLabel(LocalizedStringKey("weekly.report.previous_week"))
            .accessibilityIdentifier("weekly.report.prevWeekButton")

            VStack(alignment: .leading, spacing: 2) {
                Text(viewModel.selectedWeekIso)
                    .font(TypographyTokens.title)
                    .foregroundStyle(ColorTokens.textPrimary)
                if let digest = viewModel.state.digest {
                    Text("\(digest.weekStartDate) – \(digest.weekEndDate)")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
            }

            Spacer()

            Button {
                Task { await viewModel.selectNextWeek() }
            } label: {
                Image(systemName: "chevron.right.circle.fill")
                    .font(TypographyTokens.title)
                    .foregroundStyle(viewModel.canSelectNextWeek ? ColorTokens.action : ColorTokens.textTertiary)
            }
            .disabled(!viewModel.canSelectNextWeek)
            .accessibilityLabel(LocalizedStringKey("weekly.report.next_week"))
            .accessibilityIdentifier("weekly.report.nextWeekButton")
        }
        .padding(SpacingTokens.md)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium)
                .strokeBorder(ColorTokens.border, lineWidth: 1)
        )
    }

    @ViewBuilder
    private func digestContent(_ digest: WeeklyDigestV1) -> some View {
        executiveSummarySection(digest.executiveSummary, status: digest.status)
        sharePacketCallout
        winsSection(digest.wins)
        consistencyAndMomentumSection(digest.masteryMomentum)
        recurringMistakesSection(digest.recurringMistakes)
        recommendationsSection(digest.recommendations)
    }

    private func executiveSummarySection(_ summary: ExecutiveSummary, status: WeeklyDigestStatus) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack {
                Text(LocalizedStringKey("weekly.report.section.summary"))
                    .font(TypographyTokens.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.textSecondary)
                Spacer()
                if status == .emptyWeek {
                    Text(LocalizedStringKey("weekly.report.empty_week.badge"))
                        .font(TypographyTokens.caption.weight(.semibold))
                        .padding(.horizontal, SpacingTokens.sm)
                        .padding(.vertical, 4)
                        .background(ColorTokens.action.opacity(0.12))
                        .foregroundStyle(ColorTokens.action)
                        .clipShape(Capsule())
                }
            }

            Text(summary.headline)
                .font(TypographyTokens.title)
                .foregroundStyle(ColorTokens.textPrimary)

            Text(summary.encouragementNote)
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textSecondary)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: SpacingTokens.sm) {
                kpiTile(
                    labelKey: "weekly.report.kpi.solved",
                    value: "\(summary.solvedAttemptsCount)",
                    accent: ColorTokens.textPrimary
                )
                kpiTile(
                    labelKey: "weekly.report.kpi.independent_twins",
                    value: "\(summary.independentTwinsSolvedCount)",
                    accent: ColorTokens.action
                )
                kpiTile(
                    labelKey: "weekly.report.kpi.study_minutes",
                    value: "\(summary.studyMinutes)",
                    accent: ColorTokens.textPrimary
                )
                kpiTile(
                    labelKey: "weekly.report.kpi.active_days",
                    value: "\(summary.activeDaysCount)/7",
                    accent: ColorTokens.action
                )
            }
        }
        .padding(SpacingTokens.md)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium)
                .strokeBorder(ColorTokens.border, lineWidth: 1)
        )
    }

    private func kpiTile(labelKey: String, value: String, accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.system(size: kpiFontSize, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(accent)
            Text(LocalizedStringKey(labelKey))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
        }
        .padding(SpacingTokens.sm)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.background)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
    }

    private var sharePacketCallout: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            HStack(spacing: SpacingTokens.sm) {
                Image(systemName: "doc.richtext")
                    .foregroundStyle(ColorTokens.action)
                Text(LocalizedStringKey("weekly.share.card.title"))
                    .font(TypographyTokens.body.weight(.semibold))
                    .foregroundStyle(ColorTokens.textPrimary)
            }
            Text(LocalizedStringKey("weekly.share.card.subtitle"))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)

            Button {
                viewModel.openShareScopePreview(role: .tutor)
            } label: {
                Label(LocalizedStringKey("weekly.share.button.preview"), systemImage: "square.and.arrow.up")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .tint(ColorTokens.action)
            .accessibilityIdentifier("weekly.report.shareButton")
        }
        .padding(SpacingTokens.md)
        .background(ColorTokens.action.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium)
                .strokeBorder(ColorTokens.action.opacity(0.3), lineWidth: 1)
        )
    }

    private func winsSection(_ wins: [WeeklyWinItem]) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text(LocalizedStringKey("weekly.report.section.wins"))
                .font(TypographyTokens.body.weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)

            ForEach(wins) { win in
                HStack(alignment: .top, spacing: SpacingTokens.sm) {
                    Image(systemName: "sparkles")
                        .foregroundStyle(ColorTokens.action)
                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(win.title)
                                .font(TypographyTokens.body.weight(.medium))
                                .foregroundStyle(ColorTokens.textPrimary)
                            Spacer()
                            if let delta = win.metricDelta {
                                Text(delta)
                                    .font(TypographyTokens.caption.weight(.bold))
                                    .foregroundStyle(ColorTokens.action)
                            }
                        }
                        Text(win.detail)
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                }
                .padding(SpacingTokens.sm)
                .background(ColorTokens.surface)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
            }
        }
    }

    private func consistencyAndMomentumSection(_ momentum: MasteryMomentum) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            Text(LocalizedStringKey("weekly.report.section.momentum"))
                .font(TypographyTokens.body.weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)

            let maxMinutes = max(20, momentum.dailyActivityBuckets.map(\.studyMinutes).max() ?? 20)
            HStack(alignment: .bottom, spacing: SpacingTokens.sm) {
                ForEach(momentum.dailyActivityBuckets) { bucket in
                    let height = bucket.active
                        ? max(12, barMaxHeight * CGFloat(bucket.studyMinutes) / CGFloat(maxMinutes))
                        : 6
                    VStack(spacing: 4) {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(bucket.active ? ColorTokens.action : ColorTokens.border)
                            .frame(height: height)
                            .animation(reduceMotion ? nil : .easeOut(duration: 0.2), value: bucket.studyMinutes)
                        Text(bucket.dayLabel)
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                    .frame(maxWidth: .infinity)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(bucket.dayLabel): \(bucket.studyMinutes) min")
                }
            }
            .padding(SpacingTokens.md)
            .background(ColorTokens.surface)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))

            ForEach(momentum.topSkillDeltas) { item in
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(item.skillTitle)
                            .font(TypographyTokens.caption.weight(.semibold))
                            .foregroundStyle(ColorTokens.textPrimary)
                        Spacer()
                        let pct = Int((item.masteryScore * 100).rounded())
                        Text("\(pct)%")
                            .font(TypographyTokens.caption.weight(.bold))
                            .monospacedDigit()
                            .foregroundStyle(ColorTokens.action)
                    }
                    ProgressView(value: min(1.0, max(0.0, item.masteryScore)))
                        .tint(ColorTokens.action)
                }
                .padding(SpacingTokens.sm)
                .background(ColorTokens.surface)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
            }
        }
    }

    private func recurringMistakesSection(_ mistakes: [WeeklyRecurringMistakeItem]) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text(LocalizedStringKey("weekly.report.section.mistakes"))
                .font(TypographyTokens.body.weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)

            if mistakes.isEmpty {
                Text(LocalizedStringKey("weekly.mistake.none"))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .padding(SpacingTokens.sm)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(ColorTokens.surface)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
            } else {
                ForEach(mistakes) { mistake in
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(mistake.label)
                                .font(TypographyTokens.body.weight(.medium))
                                .foregroundStyle(ColorTokens.textPrimary)
                            Spacer()
                            Text(LocalizedStringKey(trendKey(for: mistake.trendDirection)))
                                .font(TypographyTokens.caption.weight(.semibold))
                                .foregroundStyle(mistake.trendDirection == "IMPROVING" ? ColorTokens.action : ColorTokens.warning)
                        }
                        Text(mistake.supportiveGuidance)
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                    .padding(SpacingTokens.sm)
                    .background(ColorTokens.surface)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
                }
            }
        }
    }

    private func recommendationsSection(_ recommendations: [ExplainableRecommendationItem]) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text(LocalizedStringKey("weekly.report.section.recommendations"))
                .font(TypographyTokens.body.weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)

            ForEach(recommendations) { rec in
                let isExpanded = viewModel.expandedRecommendationIds.contains(rec.recommendationId)
                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(rec.headline)
                                .font(TypographyTokens.body.weight(.semibold))
                                .foregroundStyle(ColorTokens.textPrimary)
                            Text("\(rec.targetSkillTitle) • ~\(rec.estimatedMinutes)m")
                                .font(TypographyTokens.caption)
                                .foregroundStyle(ColorTokens.textSecondary)
                        }
                        Spacer()
                        Text("\(Int((rec.priorityScore * 100).rounded()))%")
                            .font(TypographyTokens.caption.weight(.bold))
                            .monospacedDigit()
                            .foregroundStyle(ColorTokens.action)
                    }

                    Button {
                        if reduceMotion {
                            viewModel.toggleRecommendationExplanation(rec.recommendationId)
                        } else {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                viewModel.toggleRecommendationExplanation(rec.recommendationId)
                            }
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Text(LocalizedStringKey("weekly.recommendation.why_button"))
                                .font(TypographyTokens.caption.weight(.semibold))
                            Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                                .font(.caption2)
                        }
                        .foregroundStyle(ColorTokens.action)
                    }
                    .accessibilityIdentifier("weekly.recommendation.why.\(rec.recommendationId)")

                    if isExpanded {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(rec.explanation.summaryText)
                                .font(TypographyTokens.caption)
                                .foregroundStyle(ColorTokens.textPrimary)

                            Text(evidenceLine(rec.explanation.factorBreakdown))
                                .font(.caption2)
                                .foregroundStyle(ColorTokens.textSecondary)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    ForEach(rec.evidenceRefs, id: \.self) { ref in
                                        Text(ref)
                                            .font(.caption2)
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(ColorTokens.background)
                                            .clipShape(Capsule())
                                    }
                                }
                            }
                        }
                        .padding(.top, 4)
                    }
                }
                .padding(SpacingTokens.md)
                .background(ColorTokens.surface)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium)
                        .strokeBorder(ColorTokens.border, lineWidth: 1)
                )
            }
        }
    }

    private var loadingPlaceholder: some View {
        VStack(spacing: SpacingTokens.md) {
            ProgressView()
            Text(LocalizedStringKey("weekly.report.loading"))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(SpacingTokens.lg)
    }

    private func degradedBanner(message: String, retryable: Bool) -> some View {
        HStack(spacing: SpacingTokens.sm) {
            Image(systemName: "wifi.exclamationmark")
                .foregroundStyle(ColorTokens.warning)
            Text(message)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textPrimary)
            Spacer()
            if retryable {
                Button(LocalizedStringKey("weekly.report.retry")) {
                    Task { await viewModel.loadWeeklyDigest(forceRefresh: true) }
                }
                .font(TypographyTokens.caption.weight(.semibold))
            }
        }
        .padding(SpacingTokens.sm)
        .background(ColorTokens.warning.opacity(0.14))
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
    }

    private func errorCard(message: String) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text(message)
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textPrimary)
            Button(LocalizedStringKey("weekly.report.retry")) {
                Task { await viewModel.loadWeeklyDigest(forceRefresh: true) }
            }
            .buttonStyle(.bordered)
        }
        .padding(SpacingTokens.md)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
    }

    private func trendKey(for raw: String) -> String {
        switch raw {
        case "IMPROVING":
            return "weekly.mistake.trend.improving"
        case "NEEDS_GENTLE_FOCUS":
            return "weekly.mistake.trend.gentle_focus"
        default:
            return "weekly.mistake.trend.stable"
        }
    }

    private func evidenceLine(_ breakdown: RecommendationFactorBreakdown) -> String {
        let masteryPct = Int((breakdown.observedMasteryScore * 100).rounded())
        let retentionPct = Int((breakdown.observedRetentionScore * 100).rounded())
        return "Mastery: \(masteryPct)% • Retention: \(retentionPct)% • Mistakes: \(breakdown.observedMistakeCount) • Unlocks: \(breakdown.dependentBlockedSkillsCount)"
    }
}

private struct ShareScopePreviewSheet: View {
    @ObservedObject var viewModel: WeeklyProgressReportViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: SpacingTokens.md) {
                    Text(LocalizedStringKey("weekly.share.sheet.heading"))
                        .font(TypographyTokens.title)
                        .foregroundStyle(ColorTokens.textPrimary)

                    Picker(LocalizedStringKey("weekly.share.recipient_role"), selection: $viewModel.selectedRecipientRole) {
                        Text(LocalizedStringKey("weekly.share.role.tutor")).tag(ShareRecipientRole.tutor)
                        Text(LocalizedStringKey("weekly.share.role.parent")).tag(ShareRecipientRole.parent)
                    }
                    .pickerStyle(.segmented)

                    TextField(
                        LocalizedStringKey("weekly.share.alias.placeholder"),
                        text: $viewModel.customDisplayAlias
                    )
                    .textFieldStyle(.roundedBorder)

                    VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                        Text(LocalizedStringKey("weekly.privacy.attestation.title"))
                            .font(TypographyTokens.body.weight(.semibold))
                            .foregroundStyle(ColorTokens.textPrimary)
                        Label(LocalizedStringKey("weekly.privacy.included_summary"), systemImage: "checkmark.shield.fill")
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.action)
                        Label(LocalizedStringKey("weekly.privacy.excluded_raw"), systemImage: "lock.shield.fill")
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                    .padding(SpacingTokens.md)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(ColorTokens.surface)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))

                    if let err = viewModel.exportErrorMessage {
                        Text(err)
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.warning)
                    }

                    Button {
                        Task { _ = await viewModel.exportSharePacketPDF() }
                    } label: {
                        if viewModel.isExportingPDF {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            Label(LocalizedStringKey("weekly.share.button.generate_pdf"), systemImage: "doc.fill")
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(ColorTokens.action)
                    .disabled(viewModel.isExportingPDF)
                    .accessibilityIdentifier("weekly.report.generatePdfButton")

                    if let pdfURL = viewModel.exportedPDFURL {
                        ShareLink(item: pdfURL) {
                            Label(LocalizedStringKey("weekly.share.button.share_sheet"), systemImage: "square.and.arrow.up")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                        .accessibilityIdentifier("weekly.report.nativeShareLink")
                    }
                }
                .padding(SpacingTokens.lg)
            }
            .background(ColorTokens.background)
            .navigationTitle(LocalizedStringKey("weekly.share.sheet.nav_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(LocalizedStringKey("weekly.report.close")) {
                        dismiss()
                    }
                }
            }
        }
        .accessibilityIdentifier("weekly.report.shareScopeSheet")
    }
}
