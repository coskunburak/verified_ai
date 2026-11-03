import SwiftUI
struct CounterexampleProofCardView: View {
    let proof: AttemptCounterexample
    private func display(_ value: String) -> String {
        proof.witnessType == "EQUATION_TRUTH" ? NSLocalizedString("attempt.truth." + value, comment: "Equation truth") : value
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("attempt.counterexample.title", systemImage: "equal.circle")
            Text(LocalizedStringKey("attempt.witness." + proof.explanationCode))
            ForEach(proof.variableValues.keys.sorted(), id: \.self) { variable in
                if let value = proof.variableValues[variable] {
                    MathematicalExpressionView(statement: .relation(.equal, .variable(variable),
                        .binary(.divide, .number(value.numerator, .integer), .number(value.denominator, .integer))))
                }
            }
            Text("attempt.counterexample.previous \(display(proof.previousStepEvaluation))")
            Text("attempt.counterexample.submitted \(display(proof.submittedStepEvaluation))")
        }.accessibilityElement(children: .combine).accessibilityIdentifier("attempt.counterexample")
    }
}
