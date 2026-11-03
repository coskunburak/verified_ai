import SwiftUI
struct AttemptAnalysisResultsView: View {
    let attempt: StudentAttempt
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if attempt.status == .stale { Label("attempt.stale", systemImage: "clock.badge.exclamationmark") }
            else if let analysis = attempt.analysis {
                Text(LocalizedStringKey("attempt.outcome." + analysis.outcome.rawValue)).font(.headline)
                    .accessibilityIdentifier("attempt.outcome")
                Text(LocalizedStringKey("attempt.domain." + analysis.domain))
                    .accessibilityIdentifier("attempt.domain")
                if let domain = analysis.naturalDomain {
                    Text("attempt.domain.natural").font(.caption)
                    Text(verbatim: AttemptMathPresentation.setText(domain)).textSelection(.enabled)
                    if analysis.domainBasis == "REVIEWED_REAL_TASK" { Text("attempt.domain.reviewed").font(.caption) }
                }
                ForEach(analysis.steps) { step in
                    VStack(alignment: .leading, spacing: 8) {
                        Label(LocalizedStringKey(step.verdict.titleKey), systemImage: step.verdict.symbol)
                            .foregroundStyle(step.verdict == .wrong ? Color.red : Color.primary)
                            .accessibilityIdentifier("attempt.step.\(step.index).\(step.verdict.rawValue)")
                        let submitted = attempt.steps[step.index - 1]
                        if let normalized = submitted.normalizedStatement {
                            AttemptMathView(json: normalized, fallback: submitted.content,
                                highlight: step.diagnosis?.displayFallback == "SUBEXPRESSION" ? step.diagnosis?.changedExpressionPath : nil)
                        } else { Text(verbatim: submitted.content).font(.body.monospaced()).textSelection(.enabled) }
                        if step.verdict == .wrong {
                            FirstWrongStepDiffView(code: step.mistakeCode ?? "ALG_TRANSFORMATION_INVALID")
                            if let proof = step.counterexample { CounterexampleProofCardView(proof: proof) }
                            else { Text("attempt.counterexample.unavailable") }
                        }
                    }.padding().background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
                }
            } else { Text("attempt.pending") }
        }
        // An identifier on this non-accessibility container propagates to its children,
        // replacing the verdict and counterexample identifiers in SwiftUI's AX tree.
    }
}
