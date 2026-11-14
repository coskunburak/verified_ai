import SwiftUI

public struct ColdStartDiagnosticCardView: View {
    @ObservedObject public var viewModel: ColdStartDiagnosticViewModel

    public init(viewModel: ColdStartDiagnosticViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                badgePill
                Spacer()
                if viewModel.hasPendingOfflineSync {
                    Label(LocalizedStringKey("cold_start.card.cta.offline_pending"), systemImage: "icloud.and.arrow.up")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(ColorTokens.warning)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
            }

            Text(LocalizedStringKey("cold_start.card.title"))
                .font(.headline)
                .foregroundStyle(Color.primary)

            if let result = viewModel.latestResult {
                calibratedSummaryView(result: result)
            } else {
                Text(LocalizedStringKey("cold_start.card.subtitle"))
                    .font(.subheadline)
                    .foregroundStyle(Color.secondary)
            }

            if viewModel.requiresRevalidation {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "leaf.arrow.triangle.circlepath")
                        .foregroundStyle(Color.teal)
                        .accessibilityHidden(true)
                    Text(LocalizedStringKey("cold_start.revalidation.banner"))
                        .font(.caption)
                        .foregroundStyle(Color.primary)
                }
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.teal.opacity(0.12))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }

            HStack(spacing: 10) {
                Button {
                    viewModel.startDiagnosticFlow()
                } label: {
                    Label(
                        LocalizedStringKey(viewModel.latestResult == nil ? "cold_start.card.cta.start" : "cold_start.card.cta.retake"),
                        systemImage: viewModel.latestResult == nil ? "sparkles" : "arrow.clockwise"
                    )
                    .font(.subheadline.weight(.semibold))
                    .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityIdentifier("coldStart.card.cta")

                if viewModel.hasPendingOfflineSync {
                    Button {
                        Task {
                            await viewModel.flushPendingIfNeeded()
                        }
                    } label: {
                        Text(LocalizedStringKey("cold_start.error.retry"))
                            .font(.subheadline.weight(.semibold))
                            .frame(minHeight: 44)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("coldStart.card.retrySync")
                }
            }
        }
        .padding(16)
        .background(Color(UIColor.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay {
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.accentColor.opacity(0.24), lineWidth: 1)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("coachHub.coldStartDiagnosticCard")
    }

    private var badgePill: some View {
        let badgeKey: String
        let badgeColor: Color
        if viewModel.requiresRevalidation {
            badgeKey = "cold_start.card.badge.revalidation"
            badgeColor = .teal
        } else if let result = viewModel.latestResult {
            badgeKey = result.confidenceTier == .preliminary
                ? "cold_start.card.badge.preliminary"
                : "cold_start.card.badge.calibrated"
            badgeColor = result.confidenceTier == .preliminary ? .orange : .green
        } else {
            badgeKey = "cold_start.card.badge.exploration"
            badgeColor = .accentColor
        }

        return Text(LocalizedStringKey(badgeKey))
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(badgeColor.opacity(0.16))
            .foregroundStyle(badgeColor)
            .clipShape(Capsule())
    }

    @ViewBuilder
    private func calibratedSummaryView(result: ColdStartDiagnosticResult) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(LocalizedStringKey(result.baselineTier.localizationKey))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.primary)
                Spacer()
                Text("\(result.correctCount)/\(result.totalItems)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Color.accentColor)
            }

            Text(LocalizedStringKey(result.confidenceTier.localizationKey))
                .font(.caption)
                .foregroundStyle(Color.secondary)

            if result.goalConflictResolved {
                Label(LocalizedStringKey("cold_start.conflict.resolved"), systemImage: "calendar.badge.clock")
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(Color.teal)
            }
        }
    }
}
