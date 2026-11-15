import SwiftUI
import UIKit

struct PrimarySolveView: View {
    @State var viewModel: PrimarySolveViewModel
    let reviewProblem: () -> Void
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var typeSize
    @AccessibilityFocusState private var statusFocused: Bool
    @State private var isEvidenceExpanded = false
    @State private var isDetailsExpanded = false
    @State private var showsAllSteps = false
    @State private var expandedExplanationIDs: Set<String> = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                if viewModel.isFixture || viewModel.snapshot?.candidate?.generationOrigin == .localFixture {
                    AppNoticeBanner("solve.fixture", systemImage: "testtube.2", tone: .warning)
                        .accessibilityIdentifier("solve.fixture")
                }
                solveToolsGrid
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
                        .solvePanel()
                        .accessibilityIdentifier("solve.error")
                }
                if let candidate = viewModel.visibleCandidate { candidateContent(candidate) }
                actions
            }
            .padding(SpacingTokens.lg)
        }
        .accessibilityIdentifier("solve.scroll")
        .background(ColorTokens.background)
        .foregroundStyle(ColorTokens.textPrimary)
        .navigationTitle("solve.title")
        .navigationBarTitleDisplayMode(.inline)
        .transaction { transaction in
            if reduceMotion { transaction.animation = nil }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("solve.close") { dismiss() }.accessibilityIdentifier("solve.close")
            }
        }
        .task { viewModel.resume() }
        .onDisappear {
            isEvidenceExpanded = false
            viewModel.suspend()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                viewModel.resume()
            } else {
                isEvidenceExpanded = false
                viewModel.suspend()
            }
        }
        .onChange(of: viewModel.snapshot?.jobId) { _, _ in
            isEvidenceExpanded = false
            resetExplanationDisclosure()
        }
        .onChange(of: viewModel.explanationDepth) { _, mode in
            resetExplanationDisclosure()
            UIAccessibility.post(notification: .announcement,
                argument: NSLocalizedString(mode.title, comment: "Active explanation mode"))
        }
        .onChange(of: viewModel.verification?.id) { _, _ in
            isEvidenceExpanded = false
        }
        .onChange(of: viewModel.errorKey) { _, key in
            if let key { UIAccessibility.post(notification: .announcement, argument: NSLocalizedString(key, comment: "Solve error")) }
        }
        .onChange(of: viewModel.snapshot?.state) { _, state in
            if let state { statusFocused = !state.isActive }
        }
        .onChange(of: viewModel.verification) { _, decision in
            guard let decision else { return }
            let presentation = decision.evidencePresentation
            let statusText = NSLocalizedString(presentation.statusTitleKey, comment: "Verification status")
            let scopeText = NSLocalizedString(presentation.scopeKey, comment: "Verification scope")
            if let conflictKey = presentation.conflictWarningKey {
                let conflictText = NSLocalizedString(conflictKey, comment: "Verification conflict")
                UIAccessibility.post(notification: .announcement, argument: "\(statusText). \(conflictText). \(scopeText)")
            } else {
                UIAccessibility.post(notification: .announcement, argument: "\(statusText). \(scopeText)")
            }
        }
    }

    private var adaptiveRow: AnyLayout {
        typeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: SpacingTokens.xs))
            : AnyLayout(HStackLayout(alignment: .firstTextBaseline, spacing: SpacingTokens.xs))
    }

    private var statusCard: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            switch viewModel.screenPhase {
            case .job(let state):
                Label(LocalizedStringKey(state.titleKey), systemImage: state.symbolName)
                    .font(.title2.weight(.semibold))
                    .accessibilityIdentifier("solve.status.\(state.rawValue)")
                    .accessibilityFocused($statusFocused)
                Text(LocalizedStringKey(viewModel.verification == nil ? state.detailKey : "verification.generation"))
                    .foregroundStyle(ColorTokens.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                if let attempt = viewModel.snapshot?.attempt, attempt > 1 || state == .retryableFailure {
                    Text("solve.attempt \(attempt)")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textTertiary)
                        .accessibilityIdentifier("solve.attempt")
                }
                if state.isActive && !viewModel.pollingPaused && viewModel.errorKey == nil {
                    ProgressView()
                        .tint(ColorTokens.action)
                        .accessibilityLabel("solve.progress")
                }
            case .recoveryPending:
                Label("solve.recover.title", systemImage: "arrow.triangle.2.circlepath")
                    .font(.title2.weight(.semibold))
                if viewModel.busy {
                    ProgressView()
                        .tint(ColorTokens.action)
                        .accessibilityLabel("solve.loading")
                }
            case .degraded:
                Label("solve.status.degraded", systemImage: "exclamationmark.triangle")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(ColorTokens.warning)
                if viewModel.busy {
                    ProgressView()
                        .tint(ColorTokens.action)
                        .accessibilityLabel("solve.loading")
                }
            case .readyToStart:
                Text("solve.ready").font(.title2.weight(.semibold))
                    .accessibilityIdentifier("solve.ready")
                if viewModel.busy {
                    ProgressView()
                        .tint(ColorTokens.action)
                        .accessibilityLabel("solve.loading")
                }
            case .checkingExisting:
                Text("solve.loading").font(.title2.weight(.semibold))
                if viewModel.busy {
                    ProgressView()
                        .tint(ColorTokens.action)
                        .accessibilityLabel("solve.loading")
                }
            }
            if viewModel.pending != nil {
                Text("solve.pending")
                    .foregroundStyle(ColorTokens.warning)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if viewModel.pollingPaused {
                Text("solve.pollPaused")
                    .foregroundStyle(ColorTokens.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .solvePanel()
    }

    @ViewBuilder private func candidateContent(_ candidate: SolutionCandidate) -> some View {
        let presentation = viewModel.verificationPresentation
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            verificationPrimarySection(presentation)

            VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                Text("solve.proposedAnswer")
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textSecondary)
                AppFormulaBox(accentColor: ColorTokens.stepHighlight) {
                    MathematicalExpressionView(statement: candidate.finalAnswer, style: .prominent)
                        .accessibilityIdentifier("solve.answer")
                }
            }

            adaptiveRow {
                Text("solve.methodLabel")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(ColorTokens.textSecondary)
                Text(LocalizedStringKey(candidate.method.titleKey))
                    .font(.subheadline)
                    .foregroundStyle(ColorTokens.textPrimary)
            }
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("solve.method")

            if presentation.hasExpandableEvidence {
                verificationEvidenceDisclosure(presentation)
            }
        }
        .solvePanel()

        if !candidate.ambiguities.isEmpty || !candidate.unresolvedIssues.isEmpty {
            VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                Label("solve.uncertainty", systemImage: "exclamationmark.triangle").font(.headline)
                ForEach(Array((candidate.ambiguities + candidate.unresolvedIssues).enumerated()), id: \.offset) { _, text in
                    Text(verbatim: text).fixedSize(horizontal: false, vertical: true)
                }
            }
            .foregroundStyle(ColorTokens.warning)
            .solvePanel()
            .accessibilityIdentifier("solve.uncertainty")
        }

        // Source-reference limitations remain outside every condensed/disclosed sequence.
        if candidate.hasSourceReferences {
            Text("solve.referencesUnavailable")
                .font(.callout)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .solvePanel()
                .accessibilityIdentifier("solve.referencesUnavailable")
        }

        if let explanation = viewModel.explanationPresentation {
            explanationControls(explanation)
            Text("solve.steps")
                .font(TypographyTokens.screenTitle)
                .foregroundStyle(ColorTokens.textPrimary)
            let visibleSteps = showsAllSteps ? candidate.steps : explanation.visibleSteps
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(visibleSteps.enumerated()), id: \.element.id) { index, step in
                    explanationStep(step, presentation: explanation, isLast: index == visibleSteps.count - 1)
                }
            }
        }

        if let job = viewModel.snapshot {
            DisclosureGroup("solve.details", isExpanded: $isDetailsExpanded) {
                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    if let model = job.model { Text(verbatim: model) }
                    if let prompt = job.promptVersion { Text(verbatim: prompt) }
                    Text("solve.metadataNotice")
                }
                .font(.caption)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, SpacingTokens.xs)
            }
            .solvePanel()
            .accessibilityIdentifier("solve.details")
        }
    }

    private func resetExplanationDisclosure() {
        showsAllSteps = false
        expandedExplanationIDs.removeAll()
    }

    private func explanationControls(_ presentation: ExplanationPresentation) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text("Explanation depth").font(.headline)
            // Vertical choices keep localized labels usable at accessibility text sizes.
            ForEach(ExplanationDepth.allCases) { mode in
                Button { viewModel.selectExplanationDepth(mode) } label: {
                    Label(LocalizedStringKey(mode.title), systemImage: mode == presentation.mode ? "largecircle.fill.circle" : "circle")
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(ColorTokens.action)
                .accessibilityAddTraits(mode == presentation.mode ? .isSelected : [])
                .accessibilityValue(LocalizedStringKey(mode == presentation.mode ? "explanation.selected" : "explanation.notSelected"))
                .accessibilityIdentifier("explanation.mode.\(mode.rawValue)")
            }
            Text(LocalizedStringKey(viewModel.explanationPreferenceStatus.messageKey))
                .font(TypographyTokens.caption)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("explanation.preference.status")
            if viewModel.explanationPreferenceStatus.canReload {
                action("explanation.preference.reload", id: "explanation.preference.reload") { viewModel.reloadExplanationPreference() }
            }
            if viewModel.explanationPreferenceStatus == .unsaved {
                action("explanation.preference.save", id: "explanation.preference.save") { viewModel.saveExplanationPreference() }
            }
            Text(LocalizedStringKey(presentation.availabilityKey))
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("explanation.availability")
            if presentation.mode == .beginner {
                Text(LocalizedStringKey(presentation.prerequisiteKey ?? "explanation.term.unavailable"))
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("explanation.prerequisite")
            }
            if presentation.omittedStepCount > 0 {
                if !showsAllSteps {
                    Text("explanation.condensed \(presentation.omittedStepCount)")
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityIdentifier("explanation.condensed")
                }
                action(showsAllSteps ? "explanation.steps.hide" : "explanation.steps.show", id: "explanation.steps.toggle") {
                    showsAllSteps.toggle()
                }
            }
        }
        .solvePanel()
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("explanation.selector")
    }

    private func explanationStep(_ step: SolutionStep, presentation: ExplanationPresentation, isLast: Bool) -> some View {
        HStack(alignment: .top, spacing: SpacingTokens.sm) {
            VStack(spacing: 0) {
                Text("\(step.order)")
                    .font(TypographyTokens.stepNumber)
                    .foregroundStyle(ColorTokens.stepHighlight)
                    .frame(width: 28, height: 28)
                    .background(ColorTokens.stepHighlightSoft)
                    .clipShape(Circle())
                    .overlay(
                        Circle()
                            .stroke(ColorTokens.stepHighlight.opacity(0.32), lineWidth: 1)
                    )

                if !isLast {
                    Rectangle()
                        .fill(ColorTokens.borderStrong)
                        .frame(width: 1)
                        .frame(maxHeight: .infinity)
                        .padding(.top, 4)
                }
            }
            .frame(width: 28)
            .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: SpacingTokens.compact) {
                adaptiveRow {
                    Text("solve.step \(step.order)")
                        .font(TypographyTokens.sectionTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Spacer()
                    AppStatusBadge(
                        LocalizedStringKey(step.category.titleKey),
                        tone: .stepHighlight
                    )
                    .accessibilityIdentifier("solve.step.\(step.order).category")
                }

                AppFormulaBox(accentColor: ColorTokens.stepHighlight) {
                    MathematicalExpressionView(statement: step.claim, style: .step)
                }

                if presentation.usesIndividualDisclosure {
                    let expanded = expandedExplanationIDs.contains(step.id)
                    // Keep the control and content as siblings: DisclosureGroup can propagate
                    // its accessibility identifier over the expanded content's own identifier.
                    Button {
                        if expanded { expandedExplanationIDs.remove(step.id) }
                        else { expandedExplanationIDs.insert(step.id) }
                    } label: {
                        HStack(spacing: SpacingTokens.xs) {
                            Image(systemName: "questionmark.circle.fill")
                                .font(TypographyTokens.microLabel)
                            Text("explanation.step.show \(step.order)")
                                .font(TypographyTokens.caption.weight(.semibold))
                            Spacer()
                            Image(systemName: expanded ? "chevron.up" : "chevron.down")
                                .font(TypographyTokens.microLabel)
                                .accessibilityHidden(true)
                        }
                        .padding(.horizontal, SpacingTokens.sm)
                        .frame(maxWidth: .infinity, minHeight: 40, alignment: .leading)
                        .background(ColorTokens.stepHighlightSoft)
                        .foregroundStyle(ColorTokens.stepHighlight)
                        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous))
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityValue(LocalizedStringKey(expanded ? "explanation.expanded" : "explanation.collapsed"))
                    .accessibilityIdentifier("explanation.disclosure.\(step.order)")

                    if expanded {
                        Text(verbatim: step.explanation)
                            .font(TypographyTokens.body)
                            .foregroundStyle(ColorTokens.textPrimary)
                            .fixedSize(horizontal: false, vertical: true)
                            .padding(SpacingTokens.sm)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(ColorTokens.stepHighlightSoft)
                            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous)
                                    .stroke(ColorTokens.stepHighlight.opacity(0.24), lineWidth: 1)
                            )
                            .accessibilityIdentifier("explanation.content.\(step.order)")
                    }
                } else {
                    Text(verbatim: step.explanation)
                        .font(TypographyTokens.body)
                        .foregroundStyle(ColorTokens.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(SpacingTokens.sm)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(ColorTokens.stepHighlightSoft)
                        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous)
                                .stroke(ColorTokens.stepHighlight.opacity(0.24), lineWidth: 1)
                        )
                        .accessibilityIdentifier("explanation.content.\(step.order)")
                }

                if let numbers = presentation.candidate.dependencyNumbers(for: step) {
                    Text("solve.depends \(numbers)")
                        .font(TypographyTokens.caption)
                        .monospacedDigit()
                        .foregroundStyle(ColorTokens.textSecondary)
                }
                if presentation.mode == .deep {
                    VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                        Text("explanation.dependencies")
                            .font(TypographyTokens.cardTitle)
                            .foregroundStyle(ColorTokens.textPrimary)
                        let dependencies = presentation.dependencies(for: step)
                        if dependencies.isEmpty {
                            Text("explanation.dependencies.none")
                                .font(TypographyTokens.caption)
                                .foregroundStyle(ColorTokens.textSecondary)
                        }
                        ForEach(dependencies) { dependency in
                            Text("solve.step \(dependency.order)")
                                .font(TypographyTokens.microLabel)
                                .foregroundStyle(ColorTokens.stepHighlight)
                            AppFormulaBox(accentColor: ColorTokens.borderStrong) {
                                MathematicalExpressionView(statement: dependency.claim)
                            }
                        }
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("explanation.dependencies.\(step.order)")
                }
            }
            .solvePanel()
            .padding(.bottom, isLast ? 0 : SpacingTokens.compact)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("solve.step.\(step.order)")
    }

    @ViewBuilder private func verificationPrimarySection(_ presentation: VerificationEvidencePresentation) -> some View {
        Label(
            LocalizedStringKey(presentation.statusTitleKey),
            systemImage: presentation.statusSymbolName
        )
        .font(.callout.weight(.semibold))
        .foregroundStyle(verificationColor(for: presentation.severity))
        .fixedSize(horizontal: false, vertical: true)
        .accessibilityIdentifier(presentation.statusIdentifier)

        if let scopeIdentifier = presentation.scopeIdentifier {
            Text(LocalizedStringKey(presentation.scopeKey))
                .font(.subheadline)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier(scopeIdentifier)
        } else {
            Text(LocalizedStringKey(presentation.scopeKey))
                .font(.subheadline)
                .foregroundStyle(presentation.severity == .warning ? ColorTokens.warning : ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier(presentation.scopeKey)
        }

        Text(LocalizedStringKey(presentation.summaryKey))
            .font(.subheadline)
            .foregroundStyle(ColorTokens.textSecondary)
            .fixedSize(horizontal: false, vertical: true)
            .accessibilityIdentifier("verification.summary")

        if let conflictWarningKey = presentation.conflictWarningKey {
            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                Label(LocalizedStringKey(conflictWarningKey), systemImage: "exclamationmark.triangle")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(ColorTokens.warning)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityIdentifier("verification.conflict")

                if !presentation.contradictedMethodKeys.isEmpty {
                    Text("verification.contradictions")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(ColorTokens.warning)
                    ForEach(presentation.contradictedMethodKeys, id: \.self) { methodKey in
                        Label(LocalizedStringKey(methodKey), systemImage: "xmark.octagon")
                            .font(.caption)
                            .foregroundStyle(ColorTokens.warning)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .padding(SpacingTokens.sm)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("verification.contradictions.section")
        }

        if let recoveryKey = presentation.recoveryKey {
            Text(LocalizedStringKey(recoveryKey))
                .font(.subheadline.weight(.medium))
                .foregroundStyle(presentation.hasConflict ? ColorTokens.warning : ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityIdentifier("verification.recovery")
        }

        if viewModel.verificationUnavailable && viewModel.canRefreshVerification {
            Button { [model = viewModel] in
                model.refreshVerification()
            } label: {
                Label("verification.refresh", systemImage: "arrow.clockwise")
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.bordered)
            .tint(ColorTokens.action)
            .accessibilityIdentifier("verification.refresh")
        }
    }

    @ViewBuilder private func verificationEvidenceDisclosure(_ presentation: VerificationEvidencePresentation) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Button {
                isEvidenceExpanded.toggle()
            } label: {
                HStack(spacing: SpacingTokens.sm) {
                    Text(LocalizedStringKey(isEvidenceExpanded ? "verification.evidence.hide" : "verification.evidence.show"))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(ColorTokens.action)
                    Spacer()
                    Image(systemName: isEvidenceExpanded ? "chevron.up" : "chevron.down")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(ColorTokens.action)
                }
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint(LocalizedStringKey("verification.evidence.hint"))
            .accessibilityIdentifier("verification.evidence.toggle")

            if isEvidenceExpanded {
                VStack(alignment: .leading, spacing: SpacingTokens.md) {
                    if !presentation.checkedMethodKeys.isEmpty {
                        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                            Text("verification.checked").font(.headline)
                            ForEach(presentation.checkedMethodKeys, id: \.self) { methodKey in
                                Label(LocalizedStringKey(methodKey), systemImage: "checkmark.circle")
                                    .font(.subheadline)
                                    .foregroundStyle(ColorTokens.textSecondary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier("verification.checked.section")
                    }

                    if !presentation.uncheckedMethodKeys.isEmpty {
                        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                            Text("verification.unchecked").font(.headline)
                            ForEach(presentation.uncheckedMethodKeys, id: \.self) { methodKey in
                                let isContradicted = presentation.contradictedMethodKeys.contains(methodKey)
                                Label(
                                    LocalizedStringKey(methodKey),
                                    systemImage: isContradicted ? "exclamationmark.triangle" : "minus.circle"
                                )
                                .font(.subheadline)
                                .foregroundStyle(isContradicted ? ColorTokens.warning : ColorTokens.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier("verification.unchecked.section")
                    }

                    if !presentation.reasonKeys.isEmpty {
                        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                            Text("verification.reasons").font(.headline)
                            ForEach(presentation.reasonKeys, id: \.self) { reasonKey in
                                Text(LocalizedStringKey(reasonKey))
                                    .font(.subheadline)
                                    .foregroundStyle(ColorTokens.textSecondary)
                                    .fixedSize(horizontal: false, vertical: true)
                                    .accessibilityIdentifier(reasonKey)
                            }
                        }
                        .accessibilityElement(children: .contain)
                        .accessibilityIdentifier("verification.reasons.section")
                    }

                    VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                        Text("verification.limitations").font(.headline)
                        ForEach(presentation.uncertaintyKeys, id: \.self) { uncertaintyKey in
                            Text(LocalizedStringKey(uncertaintyKey))
                                .font(.subheadline)
                                .foregroundStyle(ColorTokens.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier(uncertaintyKey)
                        }
                        if presentation.showsSecondaryAgreementNotice {
                            Text("verification.agreement")
                                .font(.subheadline)
                                .foregroundStyle(ColorTokens.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier("verification.agreement")
                        }
                        if presentation.showsNumericSamplingNotice {
                            Text("verification.numericLimitation")
                                .font(.subheadline)
                                .foregroundStyle(ColorTokens.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityIdentifier("verification.numericLimitation")
                        }
                    }
                    .accessibilityElement(children: .contain)
                    .accessibilityIdentifier("verification.limitations.section")
                }
                .padding(.top, SpacingTokens.xs)
                .accessibilityElement(children: .contain)
                .accessibilityIdentifier("verification.evidence.content")
            }
        }
    }

    private func verificationColor(for severity: VerificationEvidencePresentation.Severity) -> Color {
        switch severity {
        case .verified: return ColorTokens.success
        case .caution: return ColorTokens.warning
        case .warning: return ColorTokens.danger
        case .neutral: return ColorTokens.textSecondary
        }
    }

    private var actions: some View {
        VStack(spacing: SpacingTokens.sm) {
            if viewModel.canStart { action("solve.start", id: "solve.start") { viewModel.start() } }
            if viewModel.canRetry { action("solve.retry", id: "solve.retry") { viewModel.retry() } }
            if viewModel.canRecover { action("solve.recover", id: "solve.recover") { viewModel.recoverRequest() } }
            action("solve.refresh", id: "solve.refresh") { viewModel.refresh() }.disabled(viewModel.busy)
            if viewModel.canCancel { action("solve.cancel", id: "solve.cancel") { viewModel.cancel() } }
            if viewModel.needsProblemReview {
                action("solve.review", id: "solve.review", perform: reviewProblem)
            }
        }
    }

    private func action(_ key: String, id: String, perform: @escaping () -> Void) -> some View {
        Button(action: perform) {
            Text(LocalizedStringKey(key))
                .frame(maxWidth: .infinity, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(AppSecondaryButtonStyle())
        .accessibilityIdentifier(id)
    }

    @ViewBuilder
    private var solveToolsGrid: some View {
        let hasAnyTool = viewModel.tutorModel != nil || viewModel.attemptModel != nil || viewModel.visualizationModel != nil
        if hasAnyTool {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: SpacingTokens.sm) {
                if let tutor = viewModel.tutorModel {
                    solveToolTile(title: "tutor.title", systemImage: "sparkles", tone: .action, id: "tutor.open") {
                        TutorView(viewModel: tutor)
                    }
                }
                if let attempt = viewModel.attemptModel {
                    solveToolTile(title: "attempt.title", systemImage: "doc.text.magnifyingglass", tone: .stepHighlight, id: "attempt.open") {
                        AnalyzeMyWorkCaptureView(viewModel: attempt, tutor: viewModel.tutorModel)
                    }
                }
                if let learning = viewModel.attemptModel?.learning {
                    solveToolTile(title: "attempt.methods.title", systemImage: "arrow.triangle.branch", tone: .stepHighlight, id: "attempt.methods.open") {
                        SourceMethodComparisonView(model: learning, visualization: viewModel.visualizationModel)
                    }
                }
                if let visualization = viewModel.visualizationModel {
                    solveToolTile(title: "visualization.title", systemImage: "chart.xyaxis.line", tone: .stepHighlight, id: "visualization.open") {
                        MathVisualizationView(model: visualization)
                    }
                }
            }
        }
    }

    private func solveToolTile<Destination: View>(
        title: LocalizedStringKey,
        systemImage: String,
        tone: AppBadgeTone = .stepHighlight,
        id: String,
        @ViewBuilder destination: () -> Destination
    ) -> some View {
        NavigationLink {
            destination()
        } label: {
            AppSurfaceCard(elevated: true, cornerRadius: RadiusTokens.medium, padding: SpacingTokens.compact) {
                HStack(spacing: SpacingTokens.sm) {
                    Image(systemName: systemImage)
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(tone.foreground)
                        .frame(width: 28, height: 28)
                        .background(tone.background)
                        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
                    Text(title)
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(id)
    }
}

private extension View {
    func solvePanel() -> some View {
        padding(SpacingTokens.md).frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.surface)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous).stroke(ColorTokens.borderSubtle, lineWidth: 1))
    }
}
