import SwiftUI

struct ProblemVariableEditor: View {
    @Bindable var viewModel: ProblemReviewViewModel

    private let roles = ["VARIABLE", "PARAMETER", "UNKNOWN"]

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack {
                Label("Variables", systemImage: "x.squareroot")
                    .font(TypographyTokens.body.weight(.semibold))
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Button {
                    viewModel.addVariable()
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("Add variable")
            }

            ForEach(viewModel.draft?.problem.variables ?? []) { variable in
                HStack(spacing: SpacingTokens.sm) {
                    TextField("Symbol", text: Binding(
                        get: { viewModel.variable(symbol: variable.symbol)?.symbol ?? variable.symbol },
                        set: { viewModel.updateVariable(symbol: variable.symbol, newSymbol: $0) }
                    ))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .problemReviewTextField()

                    Picker("Role", selection: Binding(
                        get: { viewModel.variable(symbol: variable.symbol)?.role ?? variable.role },
                        set: { viewModel.updateVariable(symbol: variable.symbol, role: $0) }
                    )) {
                        ForEach(roles, id: \.self) { role in
                            Text(role.capitalized).tag(role)
                        }
                    }
                    .pickerStyle(.menu)

                    Button {
                        viewModel.removeVariable(symbol: variable.symbol)
                    } label: {
                        Image(systemName: "trash")
                    }
                    .accessibilityLabel("Remove variable")
                }
            }
        }
        .problemReviewPanel()
    }
}
