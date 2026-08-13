import SwiftUI

struct ProblemConstraintEditor: View {
    @Bindable var viewModel: ProblemReviewViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack {
                Label("Constraints", systemImage: "line.3.horizontal.decrease.circle")
                    .font(TypographyTokens.body.weight(.semibold))
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Button {
                    viewModel.addConstraint()
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Add constraint")
            }

            if (viewModel.draft?.problem.constraints ?? []).isEmpty {
                Text("None")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            }

            ForEach(viewModel.draft?.problem.constraints ?? []) { constraint in
                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    HStack {
                        Text("Constraint")
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                        Spacer()
                        Button {
                            viewModel.removeConstraint(id: constraint.id)
                        } label: {
                            Image(systemName: "trash")
                        }
                        .accessibilityLabel("Remove constraint")
                    }

                    TextField("Source text", text: Binding(
                        get: { viewModel.constraint(id: constraint.id)?.sourceText ?? "" },
                        set: { viewModel.updateConstraint(id: constraint.id, sourceText: $0) }
                    ), axis: .vertical)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .lineLimit(1...3)
                    .problemReviewTextField()

                    TextField("Normalized text", text: Binding(
                        get: { viewModel.constraint(id: constraint.id)?.normalizedText ?? "" },
                        set: { viewModel.updateConstraint(id: constraint.id, normalizedText: $0) }
                    ), axis: .vertical)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .lineLimit(1...3)
                    .problemReviewTextField()

                    TextField("Variables", text: Binding(
                        get: { (viewModel.constraint(id: constraint.id)?.variables ?? []).joined(separator: ", ") },
                        set: { viewModel.updateConstraint(id: constraint.id, variables: parseVariables($0)) }
                    ))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .problemReviewTextField()
                }
                .padding(.vertical, SpacingTokens.sm)
            }
        }
        .problemReviewPanel()
    }

    private func parseVariables(_ text: String) -> [String] {
        text.split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }
}
