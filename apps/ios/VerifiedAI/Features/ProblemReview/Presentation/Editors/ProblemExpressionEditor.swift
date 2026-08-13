import SwiftUI

struct ProblemExpressionEditor: View {
    @Bindable var viewModel: ProblemReviewViewModel

    private let roles = ["PRIMARY", "GIVEN", "TARGET", "CONSTRAINT"]
    private let relations = ["", "EQUALS", "LESS_THAN", "LESS_THAN_OR_EQUAL", "GREATER_THAN", "GREATER_THAN_OR_EQUAL"]

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack {
                Label("Expressions", systemImage: "function")
                    .font(TypographyTokens.body.weight(.semibold))
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Button {
                    viewModel.addExpression()
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Add expression")
            }

            ForEach(viewModel.draft?.problem.expressions ?? []) { expression in
                expressionFields(expression)
                    .padding(.vertical, SpacingTokens.sm)
            }
        }
        .problemReviewPanel()
    }

    private func expressionFields(_ expression: ProblemParseExpression) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            HStack {
                Picker("Role", selection: Binding(
                    get: { viewModel.expression(id: expression.id)?.role ?? expression.role },
                    set: { viewModel.updateExpression(id: expression.id, role: $0) }
                )) {
                    ForEach(roles, id: \.self) { role in
                        Text(role.capitalized).tag(role)
                    }
                }
                .pickerStyle(.menu)

                Picker("Relation", selection: Binding(
                    get: { viewModel.expression(id: expression.id)?.relation ?? "" },
                    set: { viewModel.updateExpression(id: expression.id, relation: $0) }
                )) {
                    ForEach(relations, id: \.self) { relation in
                        Text(relation.isEmpty ? "None" : relation.capitalized).tag(relation)
                    }
                }
                .pickerStyle(.menu)

                Spacer()

                Button {
                    viewModel.removeExpression(id: expression.id)
                } label: {
                    Image(systemName: "trash")
                }
                .disabled((viewModel.draft?.problem.expressions.count ?? 0) <= 1)
                .accessibilityLabel("Remove expression")
            }

            TextField("Source text", text: Binding(
                get: { viewModel.expression(id: expression.id)?.sourceText ?? "" },
                set: { viewModel.updateExpression(id: expression.id, sourceText: $0) }
            ), axis: .vertical)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .lineLimit(1...3)
            .problemReviewTextField()

            TextField("Normalized text", text: Binding(
                get: { viewModel.expression(id: expression.id)?.normalizedText ?? "" },
                set: { viewModel.updateExpression(id: expression.id, normalizedText: $0) }
            ), axis: .vertical)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .lineLimit(1...3)
            .problemReviewTextField()

            TextField("LaTeX", text: Binding(
                get: { viewModel.expression(id: expression.id)?.displayLatex ?? "" },
                set: { viewModel.updateExpression(id: expression.id, displayLatex: $0) }
            ))
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .problemReviewTextField()
        }
    }
}
