import SwiftUI
struct AttemptCounterexampleProofCardView: View {
    let proof: AttemptCounterexample
    private func display(_ value: String) -> String {
        proof.witnessType == "EQUATION_TRUTH" ? NSLocalizedString("attempt.truth." + value, comment: "Equation truth") : value
    }
    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            Label("attempt.counterexample.title", systemImage: "equal.circle")
                .font(TypographyTokens.caption.weight(.semibold))
                .foregroundStyle(ColorTokens.danger)
            Text(LocalizedStringKey("attempt.witness." + proof.explanationCode))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
            AppFormulaBox(accentColor: ColorTokens.danger) {
                VStack(alignment: .leading, spacing: SpacingTokens.xxs) {
                    ForEach(proof.variableValues.keys.sorted(), id: \.self) { variable in
                        if let value = proof.variableValues[variable] {
                            MathematicalExpressionView(statement: .relation(.equal, .variable(variable),
                                .binary(.divide, .number(value.numerator, .integer), .number(value.denominator, .integer))))
                        }
                    }
                }
            }
            Text("attempt.counterexample.previous \(display(proof.previousStepEvaluation))")
                .font(TypographyTokens.caption.monospacedDigit())
                .foregroundStyle(ColorTokens.success)
            Text("attempt.counterexample.submitted \(display(proof.submittedStepEvaluation))")
                .font(TypographyTokens.caption.monospacedDigit())
                .foregroundStyle(ColorTokens.danger)
        }
        .padding(SpacingTokens.compact)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.dangerSoft, in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke(ColorTokens.danger.opacity(0.45), lineWidth: 1)
        )
        .accessibilityElement(children: .combine).accessibilityIdentifier("attempt.counterexample")
    }
}
