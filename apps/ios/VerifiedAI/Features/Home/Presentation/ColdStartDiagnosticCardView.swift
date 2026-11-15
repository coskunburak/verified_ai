import SwiftUI

public struct ColdStartDiagnosticCardView: View {
    @ObservedObject public var viewModel: ColdStartDiagnosticViewModel

    public init(viewModel: ColdStartDiagnosticViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        AppSurfaceCard(isSelected: true) {
            HStack(spacing: SpacingTokens.xs) {
                badgePill
                Spacer()
                if viewModel.hasPendingOfflineSync {
                    Label(LocalizedStringKey("cold_start.card.cta.offline_pending"), systemImage: "icloud.and.arrow.up")
                        .font(TypographyTokens.microLabel)
                        .foregroundStyle(ColorTokens.warning)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            }

            Text(LocalizedStringKey("cold_start.card.title"))
                .font(TypographyTokens.cardTitle)
                .foregroundStyle(ColorTokens.textPrimary)

            if let result = viewModel.latestResult {
                calibratedSummaryView(result: result)
            } else {
                Text(LocalizedStringKey("cold_start.card.subtitle"))
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textSecondary)
            }

            if viewModel.requiresRevalidation {
                AppNoticeBanner("cold_start.revalidation.banner", systemImage: "leaf.arrow.triangle.circlepath", tone: .action)
            }

            HStack(spacing: SpacingTokens.sm) {
                Button {
                    viewModel.startDiagnosticFlow()
                } label: {
                    Label(
                        LocalizedStringKey(viewModel.latestResult == nil ? "cold_start.card.cta.start" : "cold_start.card.cta.retake"),
                        systemImage: viewModel.latestResult == nil ? "sparkles" : "arrow.clockwise"
                    )
                }
                .buttonStyle(AppPrimaryButtonStyle())
                .accessibilityIdentifier("coldStart.card.cta")

                if viewModel.hasPendingOfflineSync {
                    Button {
                        Task {
                            await viewModel.flushPendingIfNeeded()
                        }
                    } label: {
                        Text(LocalizedStringKey("cold_start.error.retry"))
                    }
                    .buttonStyle(AppSecondaryButtonStyle())
                    .accessibilityIdentifier("coldStart.card.retrySync")
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("coachHub.coldStartDiagnosticCard")
    }

    private var badgePill: some View {
        let badgeKey: String
        let tone: AppBadgeTone
        if viewModel.requiresRevalidation {
            badgeKey = "cold_start.card.badge.revalidation"
            tone = .action
        } else if let result = viewModel.latestResult {
            badgeKey = result.confidenceTier == .preliminary
                ? "cold_start.card.badge.preliminary"
                : "cold_start.card.badge.calibrated"
            tone = result.confidenceTier == .preliminary ? .warning : .success
        } else {
            badgeKey = "cold_start.card.badge.exploration"
            tone = .action
        }

        return AppStatusBadge(LocalizedStringKey(badgeKey), tone: tone)
    }

    @ViewBuilder
    private func calibratedSummaryView(result: ColdStartDiagnosticResult) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            HStack {
                Text(LocalizedStringKey(result.baselineTier.localizationKey))
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text("\(result.correctCount)/\(result.totalItems)")
                    .font(TypographyTokens.sectionTitle)
                    .monospacedDigit()
                    .foregroundStyle(ColorTokens.action)
            }

            Text(LocalizedStringKey(result.confidenceTier.localizationKey))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)

            if result.goalConflictResolved {
                Label(LocalizedStringKey("cold_start.conflict.resolved"), systemImage: "calendar.badge.clock")
                    .font(TypographyTokens.microLabel)
                    .foregroundStyle(ColorTokens.action)
            }
        }
    }
}
