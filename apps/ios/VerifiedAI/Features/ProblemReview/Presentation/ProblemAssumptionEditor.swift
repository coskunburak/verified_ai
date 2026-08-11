import SwiftUI

struct ProblemAssumptionEditor: View {
    @Bindable var viewModel: ProblemReviewViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack {
                Label("Assumptions", systemImage: "text.badge.checkmark")
                    .font(TypographyTokens.body.weight(.semibold))
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Button {
                    viewModel.addAssumption()
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Add assumption")
            }

            if (viewModel.draft?.problem.assumptions ?? []).isEmpty {
                Text("None")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            }

            ForEach(viewModel.draft?.problem.assumptions ?? []) { assumption in
                HStack(spacing: SpacingTokens.sm) {
                    TextField("Assumption", text: Binding(
                        get: { viewModel.assumption(id: assumption.id)?.text ?? "" },
                        set: { viewModel.updateAssumption(id: assumption.id, text: $0) }
                    ), axis: .vertical)
                    .lineLimit(1...3)
                    .problemReviewTextField()

                    Button {
                        viewModel.removeAssumption(id: assumption.id)
                    } label: {
                        Image(systemName: "trash")
                    }
                    .accessibilityLabel("Remove assumption")
                }
            }
        }
        .problemReviewPanel()
    }
}
