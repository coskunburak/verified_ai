import SwiftUI

struct ProblemReviewView: View {
    @Bindable var viewModel: ProblemReviewViewModel
    let problemSessionId: UUID
    let onFinished: () -> Void

    @State private var selection: ProblemReviewSelection = .parse

    var body: some View {
        content
            .background(ColorTokens.background.ignoresSafeArea())
            .navigationTitle("Problem Review")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        onFinished()
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel("Close problem review")
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await viewModel.submitCorrection() }
                    } label: {
                        if viewModel.state == .saving {
                            ProgressView()
                                .controlSize(.small)
                        } else {
                            Image(systemName: "checkmark")
                        }
                    }
                    .disabled(!viewModel.canSubmit)
                    .accessibilityLabel("Save parse correction")
                }
            }
            .task(id: problemSessionId) {
                await viewModel.load(problemSessionId: problemSessionId)
            }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .idle, .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .failed(let message):
            failureView(message)
        case .ready, .saving, .saved:
            reviewContent
        }
    }

    private var reviewContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                if let review = viewModel.review {
                    header(review)
                }

                Picker("Review section", selection: $selection) {
                    ForEach(ProblemReviewSelection.allCases) { selection in
                        Text(selection.title).tag(selection)
                    }
                }
                .pickerStyle(.segmented)

                sectionContent

                if let message = viewModel.message {
                    Label(message, systemImage: statusIcon)
                        .font(TypographyTokens.caption)
                        .foregroundStyle(statusColor)
                        .padding(.vertical, SpacingTokens.sm)
                        .accessibilityIdentifier("problemReview.status")
                }
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: 760, alignment: .leading)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    @ViewBuilder
    private var sectionContent: some View {
        switch selection {
        case .parse:
            ProblemExpressionEditor(viewModel: viewModel)
        case .details:
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                ProblemParseMetadataEditor(viewModel: viewModel)
                ProblemVariableEditor(viewModel: viewModel)
                ProblemConstraintEditor(viewModel: viewModel)
                ProblemAssumptionEditor(viewModel: viewModel)
            }
        case .revisions:
            ProblemRevisionHistoryView(viewModel: viewModel)
        }
    }

    private func header(_ review: ProblemParseReview) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack(alignment: .top, spacing: SpacingTokens.md) {
                Image(systemName: review.currentParse.reviewRequired ? "exclamationmark.triangle" : "checkmark.seal")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(review.currentParse.reviewRequired ? ColorTokens.warning : ColorTokens.action)
                    .frame(width: 44, height: 44)
                    .background(ColorTokens.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))

                VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                    Text(primaryExpressionText(review.currentParse.normalizedProblem))
                        .font(TypographyTokens.title)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Text("Revision \(review.currentParse.revision) · \(review.currentParse.source)")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }

                Spacer()
            }

            Picker("Correction reason", selection: correctionReasonBinding) {
                ForEach(ProblemParseCorrectionReason.allCases) { reason in
                    Text(reason.title).tag(reason)
                }
            }
            .pickerStyle(.menu)
            .disabled(!review.canCorrect)
        }
        .problemReviewPanel()
    }

    private var correctionReasonBinding: Binding<ProblemParseCorrectionReason> {
        Binding(
            get: { viewModel.draft?.correctionReason ?? .other },
            set: { viewModel.setCorrectionReason($0) }
        )
    }

    private func failureView(_ message: String) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.lg) {
            Label(message, systemImage: "exclamationmark.triangle")
                .font(TypographyTokens.body.weight(.semibold))
                .foregroundStyle(ColorTokens.warning)
            Button {
                Task { await viewModel.load(problemSessionId: problemSessionId, force: true) }
            } label: {
                Label("Reload", systemImage: "arrow.clockwise")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(SpacingTokens.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var statusIcon: String {
        if case .saved = viewModel.state {
            return "checkmark.circle"
        }
        return "info.circle"
    }

    private var statusColor: Color {
        if case .saved = viewModel.state {
            return ColorTokens.action
        }
        return ColorTokens.textSecondary
    }

    private func primaryExpressionText(_ problem: NormalizedProblemParse) -> String {
        problem.expressions.first?.normalizedText.trimmedForDisplay ?? problem.taskType ?? "Structured problem"
    }
}

struct ProblemParseMetadataEditor: View {
    @Bindable var viewModel: ProblemReviewViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            Label("Classification", systemImage: "tag")
                .font(TypographyTokens.body.weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)

            VStack(spacing: SpacingTokens.sm) {
                TextField("Task type", text: Binding(
                    get: { viewModel.draft?.problem.taskType ?? "" },
                    set: { viewModel.setTaskType($0) }
                ))
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .problemReviewTextField()

                TextField("Problem type", text: Binding(
                    get: { viewModel.draft?.problem.problemType ?? "" },
                    set: { viewModel.setProblemType($0) }
                ))
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .problemReviewTextField()
            }
        }
        .problemReviewPanel()
    }
}

extension View {
    func problemReviewPanel() -> some View {
        padding(SpacingTokens.md)
            .background(ColorTokens.surface)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
            .overlay {
                RoundedRectangle(cornerRadius: RadiusTokens.medium)
                    .stroke(ColorTokens.border, lineWidth: 1)
            }
    }

    func problemReviewTextField() -> some View {
        padding(SpacingTokens.sm)
            .background(ColorTokens.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
            .foregroundStyle(ColorTokens.textPrimary)
    }
}

extension String {
    var trimmedForDisplay: String {
        trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
