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
            } else if attempt.finalAnswer != nil {
                Label("attempt.answer.saved", systemImage: "tray.and.arrow.down")
                    .accessibilityIdentifier("attempt.answer.saved")
            } else { Text("attempt.pending") }
            if attempt.analysis == nil {
                ForEach(attempt.steps) { step in Text(verbatim: step.content).font(.body.monospaced()).textSelection(.enabled) }
            }
            if let answer = attempt.finalAnswer {
                Text("attempt.answer.label").font(.headline)
                Text(verbatim: answer).textSelection(.enabled).accessibilityIdentifier("attempt.answer.original")
            }
        }
        // An identifier on this non-accessibility container propagates to its children,
        // replacing the verdict and counterexample identifiers in SwiftUI's AX tree.
    }
}

struct AttemptStructuredWorkView: View {
    let work: AttemptStructuredWork
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Label("attempt.work.saved", systemImage: "tray.and.arrow.down").font(.headline)
                .accessibilityIdentifier("attempt.work.saved")
            Text("attempt.work.scope")
            ForEach(work.steps) { step in
                VStack(alignment: .leading, spacing: 8) {
                    Text(LocalizedStringKey("attempt.work.role." + step.role.rawValue)).font(.headline)
                    Text(verbatim: step.reviewedText).textSelection(.enabled)
                    Label(LocalizedStringKey("attempt.work." + step.normalizationStatus), systemImage: step.normalizationStatus == "NORMALIZED" ? "text.alignleft" : "exclamationmark.triangle")
                    if let statement = step.normalizedStatement { AttemptMathView(json: statement, fallback: step.reviewedText) }
                    if !step.observationIds.isEmpty {
                        Text("attempt.work.originalOCR").font(.caption)
                        ForEach(work.observations.filter { step.observationIds.contains($0.id) }) { observation in
                            Text(verbatim: observation.rawText).font(.caption).textSelection(.enabled)
                        }
                    }
                }.padding().background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}
