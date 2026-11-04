import SwiftUI

struct AlternativeMethodComparisonView: View {
    var attempt: StudentAttempt? = nil
    let evidence: AttemptAlternativeMethods
    @State private var compact = false
    @State private var selected = ""
    private var method: AttemptAlternativeMethods.Method? { evidence.methods.first { $0.id == selected } ?? evidence.methods.first }
    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            Text("attempt.methods.scope").font(.callout).accessibilityIdentifier("attempt.methods.scope")
            if let source = evidence.source {
                Text("attempt.methods.source").font(.headline)
                math(source["original"])
                if evidence.status == "VERIFIED" { Text("attempt.methods.REAL_DOMAIN") }
                if let naturalDomain = evidence.naturalDomain, naturalDomain["kind"].text != "REALS" {
                    Label(LocalizedStringKey("attempt.methods.NATURAL_DOMAIN_EXCLUSIONS"), systemImage: "exclamationmark.triangle")
                    math(naturalDomain)
                }
                ForEach(Array(source["restrictions"].items.enumerated()), id: \.offset) { _, restriction in
                    math(.object(["kind": .string("RELATION"), "relation": restriction["relation"], "left": restriction["left"], "right": restriction["right"]]))
                }
            }
            if let attempt {
                DisclosureGroup("attempt.methods.mine") {
                    Text("attempt.methods.learnerSeparate").accessibilityIdentifier("attempt.methods.learnerSeparate")
                    Text(String(format: NSLocalizedString("attempt.methods.learnerCount", comment: "Submitted lines"), attempt.steps.count))
                    if let analysis = attempt.analysis {
                        if analysis.firstWrongStepIndex != nil || analysis.outcome == .error {
                            Label("attempt.methods.learnerInvalid", systemImage: "xmark.circle")
                                .foregroundColor(.red)
                                .accessibilityIdentifier("attempt.methods.learnerInvalid")
                        } else if analysis.outcome == .progress {
                            Label("attempt.methods.learnerVerified", systemImage: "checkmark.circle")
                                .foregroundColor(.green)
                                .accessibilityIdentifier("attempt.methods.learnerVerified")
                        } else {
                            Label("attempt.methods.learnerNeedsReview", systemImage: "questionmark.circle")
                                .foregroundColor(.orange)
                                .accessibilityIdentifier("attempt.methods.learnerNeedsReview")
                        }
                    }
                    ForEach(attempt.steps) { Text(verbatim: $0.content).textSelection(.enabled) }
                }
            }
            if evidence.status != "VERIFIED" {
                Label(LocalizedStringKey("attempt.methods.status." + evidence.status), systemImage: "info.circle")
                    .accessibilityIdentifier("attempt.methods.empty")
            } else if let method {
                if evidence.methods.count == 1 { Text("attempt.methods.single") }
                Picker("attempt.methods.select", selection: $selected) {
                    ForEach(evidence.methods) { Text(LocalizedStringKey("attempt.method." + $0.methodId)).tag($0.id) }
                }.pickerStyle(.menu).accessibilityIdentifier("attempt.methods.select")
                Toggle("attempt.methods.compact", isOn: $compact).accessibilityIdentifier("attempt.methods.compact")
                Text(compact ? "attempt.methods.compactHelp" : "attempt.methods.fullHelp")
                if let c = method.comparison {
                    Text(LocalizedStringKey("attempt.methods." + c.reasonCode)).font(.headline)
                    Text(LocalizedStringKey("attempt.methods." + c.applicability))
                    Text(LocalizedStringKey("attempt.methods.prerequisite." + c.prerequisite))
                    Text(LocalizedStringKey("attempt.methods." + c.advantage))
                    Text(LocalizedStringKey("attempt.methods." + c.limitation))
                    Text(LocalizedStringKey("attempt.methods." + c.recommendation))
                    Text(String(format: NSLocalizedString("attempt.methods.stepCount", comment: "Structural count"), c.structuralStepCount, method.indices(compact: compact).count))
                    Text(LocalizedStringKey("attempt.methods.complexity." + c.manipulationLevel))
                    ForEach(c.domainCautions, id: \.self) { Label(LocalizedStringKey("attempt.methods." + $0), systemImage: "exclamationmark.triangle") }
                }
                ForEach(method.indices(compact: compact), id: \.self) { index in
                    VStack(alignment: .leading) {
                        if let reasons = method.stepReasons { Text(LocalizedStringKey("attempt.methods.step." + reasons[index])).font(.callout) }
                        math(method.steps[index])
                    }.accessibilityIdentifier("attempt.methods.step.\(index)")
                }
                Label("attempt.methods.exactEvidence", systemImage: "checkmark.shield")
                    .accessibilityIdentifier("attempt.methods.evidence")
                Text("attempt.methods.heuristic").font(.caption)
            }
        }
        .onAppear { selected = evidence.methods.first?.id ?? "" }
        .onChange(of: evidence.executionId) { _, _ in selected = evidence.methods.first?.id ?? "" }
    }
    private func math(_ value: AttemptJSON) -> some View {
        AttemptMathView(json: String(data: (try? JSONEncoder().encode(value)) ?? Data(), encoding: .utf8) ?? "",
                        fallback: NSLocalizedString("attempt.math.expression", comment: "Mathematics"))
    }
}

struct SourceMethodComparisonView: View {
    @Bindable var model: AttemptLearningViewModel
    @Environment(\.scenePhase) private var phase
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                if model.busy { ProgressView("attempt.methods.loading").accessibilityIdentifier("attempt.methods.loading") }
                if let result = model.sourceMethods {
                    AlternativeMethodComparisonView(evidence: result.evidence)
                    Text("attempt.methods.freshness").font(.caption)
                }
                if let key = model.methodErrorKey {
                    Label(LocalizedStringKey(key), systemImage: "exclamationmark.triangle")
                        .accessibilityIdentifier("attempt.methods.error")
                }
                Button("attempt.methods.reload") { model.sourceAlternatives() }.disabled(model.busy)
                    .accessibilityIdentifier("attempt.methods.retry")
            }.padding(SpacingTokens.lg)
        }
        .navigationTitle("attempt.methods.title")
        .task { model.sourceAlternatives() }
        .onDisappear { model.suspend() }
        .onChange(of: phase) { _, value in
            if value == .active { model.sourceAlternatives() } else { model.suspend() }
        }
    }
}
