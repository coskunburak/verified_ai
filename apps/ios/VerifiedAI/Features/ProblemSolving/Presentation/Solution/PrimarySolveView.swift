import SwiftUI
import UIKit

struct PrimarySolveView: View {
    @State var viewModel: PrimarySolveViewModel
    let reviewProblem: () -> Void
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                if viewModel.isFixture || viewModel.snapshot?.candidate?.generationOrigin == .localFixture {
                    Label("solve.fixture", systemImage: "testtube.2")
                        .font(.callout.weight(.semibold)).foregroundStyle(ColorTokens.warning)
                        .accessibilityIdentifier("solve.fixture")
                }
                statusCard
                #if DEBUG
                if let state = viewModel.controlledFixtureState {
                    // Keep the test-server stimulus in the content's hit-test tree. A crowded
                    // navigation toolbar can expose the control without delivering its tap.
                    Button { [model = viewModel] in model.advanceFixture() } label: {
                        Text("Advance synthetic job")
                            .frame(maxWidth: .infinity, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("solve.fixture.advance.\(state.rawValue)")
                }
                #endif
                if let error = viewModel.errorKey {
                    Label(LocalizedStringKey(error), systemImage: "exclamationmark.triangle")
                        .foregroundStyle(ColorTokens.warning).fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("solve.error")
                }
                if let candidate = viewModel.visibleCandidate { candidateContent(candidate) }
                actions
            }
            .padding(SpacingTokens.lg)
        }
        .background(ColorTokens.background)
        .foregroundStyle(ColorTokens.textPrimary)
        .navigationTitle("solve.title")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("solve.close") { dismiss() }.accessibilityIdentifier("solve.close")
            }
        }
        .task { viewModel.resume() }
        .onDisappear { viewModel.suspend() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { viewModel.resume() } else { viewModel.suspend() }
        }
        .onChange(of: viewModel.errorKey) { _, key in
            if let key { UIAccessibility.post(notification: .announcement, argument: NSLocalizedString(key, comment: "Solve error")) }
        }
        .onChange(of: viewModel.snapshot?.state) { _, state in
            if let state, !state.isActive {
                UIAccessibility.post(notification: .announcement, argument: NSLocalizedString(state.titleKey, comment: "Solve state"))
            }
        }
    }

    private var statusCard: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            if let state = viewModel.snapshot?.state {
                Label(LocalizedStringKey(state.titleKey), systemImage: state.isActive ? "hourglass" : "doc.text")
                    .font(.title2.weight(.semibold)).accessibilityIdentifier("solve.status.\(state.rawValue)")
                Text(LocalizedStringKey(viewModel.verification == nil ? state.detailKey : "verification.generation")).fixedSize(horizontal: false, vertical: true)
                if state.isActive && !viewModel.pollingPaused && viewModel.errorKey == nil { ProgressView().accessibilityLabel("solve.progress") }
            } else {
                Text(LocalizedStringKey(viewModel.loaded ? "solve.ready" : "solve.loading")).font(.title2.weight(.semibold))
                if viewModel.busy { ProgressView().accessibilityLabel("solve.loading") }
            }
            if viewModel.pending != nil {
                Text("solve.pending").foregroundStyle(ColorTokens.warning).fixedSize(horizontal: false, vertical: true)
            }
            if viewModel.pollingPaused { Text("solve.pollPaused").fixedSize(horizontal: false, vertical: true) }
        }
        .solvePanel()
    }

    @ViewBuilder private func candidateContent(_ candidate: SolutionCandidate) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            if let decision = viewModel.verification {
                Label(LocalizedStringKey(decision.scope.calculusMethod == nil ? decision.status.titleKey : "verification.scopedStatus"), systemImage: "checkmark.shield")
                    .font(.callout.weight(.semibold)).accessibilityIdentifier("verification.status.\(decision.status.rawValue)")
                Text(LocalizedStringKey(decision.scope.titleKey)).fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("verification.scope.\(decision.scope.rawValue)")
                if decision.hasConflict {
                    Label("verification.conflict", systemImage: "exclamationmark.triangle")
                        .foregroundStyle(ColorTokens.warning).accessibilityIdentifier("verification.conflict")
                }
                Text("verification.checked").font(.headline)
                ForEach(decision.checked, id: \.rawValue) { method in Text(LocalizedStringKey(method.titleKey)) }
                Text("verification.unchecked").font(.headline)
                ForEach(decision.unchecked, id: \.rawValue) { method in Text(LocalizedStringKey(method.titleKey)) }
                Text("verification.stepsUnchecked")
                ForEach(decision.uncertainty.filter { $0 != "EXPLANATION_STEPS_UNCHECKED" }, id: \.self) { item in
                    Text(LocalizedStringKey("verification.uncertainty." + item))
                }
                if decision.reasons.contains("SECONDARY_AGREEMENT") { Text("verification.agreement") }
                Text(LocalizedStringKey(decision.recovery.titleKey)).accessibilityIdentifier("verification.recovery")
            } else {
                Label("solve.unverified", systemImage: "info.circle")
                    .font(.callout.weight(.semibold)).fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("solve.unverified")
                Text(LocalizedStringKey(viewModel.verificationUnavailable ? "verification.unavailable" : "verification.absent"))
            }
            Text("solve.proposedAnswer").font(.headline)
            MathematicalExpressionView(statement: candidate.finalAnswer).accessibilityIdentifier("solve.answer")
            Text(LocalizedStringKey(candidate.method.titleKey)).font(.subheadline).foregroundStyle(ColorTokens.textSecondary)
        }.solvePanel()
        if !candidate.ambiguities.isEmpty || !candidate.unresolvedIssues.isEmpty {
            VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                Label("solve.uncertainty", systemImage: "exclamationmark.triangle").font(.headline)
                ForEach(Array((candidate.ambiguities + candidate.unresolvedIssues).enumerated()), id: \.offset) { _, text in
                    Text(verbatim: text).fixedSize(horizontal: false, vertical: true)
                }
            }.foregroundStyle(ColorTokens.warning).solvePanel().accessibilityIdentifier("solve.uncertainty")
        }
        Text("solve.steps").font(.title2.weight(.semibold))
        ForEach(candidate.steps) { step in
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Text("solve.step \(step.order)").font(.headline)
                MathematicalExpressionView(statement: step.claim)
                Text(verbatim: step.explanation).fixedSize(horizontal: false, vertical: true)
                if !step.dependsOn.isEmpty {
                    let numbers = candidate.steps.filter { step.dependsOn.contains($0.id) }.map { String($0.order) }.joined(separator: ", ")
                    Text("solve.depends \(numbers)").font(.caption).foregroundStyle(ColorTokens.textSecondary)
                }
            }.solvePanel().accessibilityElement(children: .contain).accessibilityIdentifier("solve.step.\(step.order)")
        }
        if candidate.hasSourceReferences {
            Text("solve.referencesUnavailable").font(.callout).foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        if let job = viewModel.snapshot {
            DisclosureGroup("solve.details") {
                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    if let model = job.model { Text(verbatim: model) }
                    if let prompt = job.promptVersion { Text(verbatim: prompt) }
                    Text("solve.metadataNotice")
                }.font(.caption).fixedSize(horizontal: false, vertical: true)
            }.solvePanel()
        }
    }
    private var actions: some View {
        VStack(spacing: SpacingTokens.sm) {
            if viewModel.canStart { action("solve.start", id: "solve.start") { viewModel.start() } }
            if viewModel.canRetry { action("solve.retry", id: "solve.retry") { viewModel.retry() } }
            if viewModel.canRecover { action("solve.recover", id: "solve.recover") { viewModel.recoverRequest() } }
            action("solve.refresh", id: "solve.refresh") { viewModel.refresh() }.disabled(viewModel.busy)
            if viewModel.canCancel { action("solve.cancel", id: "solve.cancel") { viewModel.cancel() } }
            if viewModel.snapshot.map({ [SolveState.reviewRequired, .stale, .unsupported].contains($0.state) }) == true || viewModel.errorKey == "solve.error.source" {
                action("solve.review", id: "solve.review", perform: reviewProblem)
            }
        }
    }
    private func action(_ key: String, id: String, perform: @escaping () -> Void) -> some View {
        Button(action: perform) { Text(LocalizedStringKey(key)).frame(maxWidth: .infinity, minHeight: 44) }
            .buttonStyle(.bordered).tint(ColorTokens.action).accessibilityIdentifier(id)
    }
}

private extension View {
    func solvePanel() -> some View {
        padding(SpacingTokens.md).frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.surface)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
            .overlay(RoundedRectangle(cornerRadius: RadiusTokens.medium).stroke(ColorTokens.border, lineWidth: 1))
    }
}
