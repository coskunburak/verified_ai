import SwiftUI

struct ProblemRevisionHistoryView: View {
    @Bindable var viewModel: ProblemReviewViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack {
                Label("Revision History", systemImage: "clock.arrow.circlepath")
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Button {
                    Task { await viewModel.refreshHistory() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .foregroundStyle(ColorTokens.action)
                }
                .accessibilityLabel("Refresh revision history")
            }

            if let history = viewModel.revisionHistory {
                ForEach(history.revisions) { revision in
                    revisionRow(revision)
                }
            } else {
                ProgressView()
                    .task {
                        await viewModel.refreshHistory()
                    }
            }
        }
        .problemReviewPanel()
    }

    private func revisionRow(_ revision: ProblemParseRevisionEntry) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            HStack {
                Label("Revision \(revision.revision)", systemImage: revision.selected ? "checkmark.circle.fill" : "circle")
                    .font(TypographyTokens.body.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(revision.selected ? ColorTokens.action : ColorTokens.textPrimary)
                Spacer()
                AppStatusBadge(verbatimTitle: revision.source, tone: revision.selected ? .action : .neutral)
            }

            if let reason = revision.correctionReason {
                Text(reason)
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            }

            if !revision.correctedFieldCategories.isEmpty {
                Text(revision.correctedFieldCategories.joined(separator: ", "))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textTertiary)
            }
        }
        .padding(.vertical, SpacingTokens.sm)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(ColorTokens.borderSubtle)
                .frame(height: 1)
        }
    }
}
