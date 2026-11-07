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
    var evaluated = false
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Label(LocalizedStringKey(evaluated ? "attempt.evaluation.captureSaved" : "attempt.work.saved"), systemImage: "tray.and.arrow.down").font(.headline)
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
                }.padding()
                    .foregroundStyle(ColorTokens.textPrimary)
                    .background(ColorTokens.surface, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}

struct AttemptEvaluationResultsView: View {
    let value: AttemptEvaluationView
    let work: AttemptStructuredWork
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if value.historical { Label("attempt.evaluation.historical", systemImage: "clock.badge.exclamationmark") }
            Label(LocalizedStringKey("attempt.evaluation." + value.status.rawValue), systemImage: "doc.text.magnifyingglass")
                .accessibilityIdentifier("attempt.evaluation.status")
            if let e = value.evaluation {
                Text("attempt.evaluation.scope").font(.caption)
                if e.analysis.domainBasis == "REVIEWED_REAL_TASK" { Text("attempt.domain.reviewed").font(.caption) }
                ForEach(e.analysis.steps) { step in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(verbatim: work.steps[step.index - 1].reviewedText).textSelection(.enabled)
                        Label(LocalizedStringKey(step.verdict.titleKey), systemImage: step.verdict.symbol)
                            .accessibilityIdentifier("attempt.evaluation.step.\(step.index).\(step.verdict.rawValue)")
                        Text(LocalizedStringKey("attempt.evaluation.reason." + step.reasonCode))
                        if work.steps[step.index - 1].reviewStatus != "CONFIRMED" { Text("attempt.work.REVIEW_REQUIRED") }
                        if let proof = step.counterexample { CounterexampleProofCardView(proof: proof) }
                    }
                }
                Label(LocalizedStringKey("attempt.evaluation.final." + e.finalAnswerStatus.rawValue), systemImage: "flag")
                    .font(.headline).accessibilityIdentifier("attempt.evaluation.final")
                if let proof = e.finalAnswerEvidence?.steps.first?.counterexample { CounterexampleProofCardView(proof: proof) }
            }
        }.padding()
            .foregroundStyle(ColorTokens.textPrimary)
            .background(ColorTokens.surface, in: RoundedRectangle(cornerRadius: 12))
    }
}

struct AttemptMistakeResultsView: View {
    let value: AttemptMistakeView
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("attempt.classification.title", systemImage: "text.magnifyingglass").font(.headline)
            if value.historical { Text("attempt.evaluation.historical") }
            if let result = value.classification {
                let decision = result.decision
                if let category = decision.category {
                    Text(LocalizedStringKey("attempt.classification.category." + category.rawValue)).font(.headline)
                }
                if let index = result.firstWrongStepIndex {
                    HStack { Text("attempt.classification.firstStep"); Text(verbatim: String(index)) }
                        .accessibilityElement(children: .combine)
                }
                Text(LocalizedStringKey(decision.explanationKey))
                    .accessibilityIdentifier("attempt.classification.explanation")
                Text(LocalizedStringKey("attempt.classification.strength." + decision.evidenceStrength.rawValue))
                Text(LocalizedStringKey("attempt.classification.review." + decision.reviewStatus.rawValue))
                if let skill = decision.skillId { Text(LocalizedStringKey("attempt.classification.skill." + skill)) }
                if decision.status == .signature, let code = decision.code {
                    Text(LocalizedStringKey("attempt.classification.correction." + code))
                }
            } else { Text("attempt.classification.NOT_CLASSIFIED") }
        }.padding().foregroundStyle(ColorTokens.textPrimary)
            .background(ColorTokens.surface, in: RoundedRectangle(cornerRadius: 12))
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("attempt.classification.card")
    }
}
