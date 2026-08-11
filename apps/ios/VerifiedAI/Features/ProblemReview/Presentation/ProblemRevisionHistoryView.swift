import SwiftUI

struct ProblemRevisionHistoryView: View {
    @Bindable var viewModel: ProblemReviewViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack {
                Label("Revision History", systemImage: "clock.arrow.circlepath")
                    .font(TypographyTokens.body.weight(.semibold))
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Button {
                    Task { await viewModel.refreshHistory() }
                } label: {
                    Image(systemName: "arrow.clockwise")
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
                    .foregroundStyle(revision.selected ? ColorTokens.action : ColorTokens.textPrimary)
                Spacer()
                Text(revision.source)
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
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
    }
}
