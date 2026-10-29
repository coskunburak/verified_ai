import SwiftUI

private enum RecoveryDestination: Hashable { case solve, review }

struct ProblemHistorySectionView: View {
    @Bindable var viewModel: ProblemHistoryViewModel
    let startProblemCapture: () -> Void
    var makeSolveModel: ((UUID) -> PrimarySolveViewModel)? = nil
    var reviewViewModel: ProblemReviewViewModel? = nil
    @State private var recoveryPath: [RecoveryDestination] = []

    @State private var presentedDetail: ProblemSessionDetail?

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack {
                Label("Problem History", systemImage: "clock.arrow.circlepath")
                    .font(.headline)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Button {
                    Task { await viewModel.refresh() }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .frame(width: 36, height: 36)
                }
                .buttonStyle(.bordered)
                .tint(ColorTokens.textSecondary)
                .accessibilityLabel("Refresh problem history")
            }

            if let message = viewModel.message {
                Text(message)
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if viewModel.items.isEmpty {
                emptyContent
            } else {
                historyRows
            }
        }
        .problemHistoryPanel()
        .task {
            await viewModel.bootstrap()
        }
        .onChange(of: viewModel.selectedDetail) { _, detail in
            if presentedDetail == nil { recoveryPath = [] }
            presentedDetail = detail
        }
        .sheet(item: $presentedDetail) { detail in
            ProblemSessionRecoveryDetailView(
                detail: detail,
                path: $recoveryPath,
                makeSolveModel: makeSolveModel,
                reviewViewModel: reviewViewModel,
                performAction: {
                    Task {
                        await viewModel.performNextAction(for: detail)
                        presentedDetail = viewModel.selectedDetail
                        if viewModel.solveSessionId == detail.problemSessionId, makeSolveModel != nil {
                            recoveryPath = [.solve]
                        }
                    }
                },
                startProblemCapture: {
                    presentedDetail = nil
                    startProblemCapture()
                }
            )
        }
    }

    @ViewBuilder
    private var emptyContent: some View {
        switch viewModel.state {
        case .idle, .loading:
            ProgressView()
                .tint(ColorTokens.action)
                .frame(maxWidth: .infinity, minHeight: 96)
        case .failed(let text):
            ProblemHistoryEmptyView(text: text, startProblemCapture: startProblemCapture)
        case .ready, .offline:
            ProblemHistoryEmptyView(text: "No problem sessions yet.", startProblemCapture: startProblemCapture)
        }
    }

    private var historyRows: some View {
        VStack(spacing: SpacingTokens.sm) {
            ForEach(viewModel.items.prefix(5)) { item in
                ProblemHistoryRow(item: item) {
                    Task {
                        recoveryPath = []
                        await viewModel.reconnect(to: item)
                        presentedDetail = viewModel.selectedDetail
                    }
                }
            }

            if viewModel.canLoadMore {
                Button {
                    Task { await viewModel.loadMore() }
                } label: {
                    Label("Load More", systemImage: "chevron.down")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
        }
    }
}

private struct ProblemHistoryRow: View {
    let item: ProblemSessionHistoryItem
    let reconnect: () -> Void

    var body: some View {
        Button(action: reconnect) {
            HStack(spacing: SpacingTokens.md) {
                Image(systemName: iconName)
                    .font(.headline)
                    .foregroundStyle(iconColor)
                    .frame(width: 38, height: 38)
                    .background(ColorTokens.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))

                VStack(alignment: .leading, spacing: 4) {
                    Text(item.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(ColorTokens.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                    Text(item.subtitle)
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .lineLimit(1)
                    Text(item.updatedAt, style: .relative)
                        .font(.caption2)
                        .foregroundStyle(ColorTokens.textTertiary)
                }

                Spacer()

                Text(item.nextAction.buttonTitle)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.action)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .padding(SpacingTokens.sm)
            .background(ColorTokens.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
            // A plain button must also accept taps between its text and icon content.
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("history.session." + item.problemSessionId.uuidString)
    }

    private var iconName: String {
        switch item.stage {
        case .readyForSolve:
            "doc.text"
        case .parseReview:
            "text.magnifyingglass"
        case .terminal:
            "exclamationmark.triangle"
        case .recognition, .parsing, .classification, .canonicalization:
            "hourglass"
        case .awaitingUpload, .preprocessing:
            "camera.viewfinder"
        }
    }

    private var iconColor: Color {
        switch item.stage {
        case .readyForSolve:
            ColorTokens.action
        case .terminal:
            ColorTokens.warning
        case .parseReview:
            ColorTokens.accent
        default:
            ColorTokens.action
        }
    }
}

private struct ProblemSessionRecoveryDetailView: View {
    let detail: ProblemSessionDetail
    @Binding var path: [RecoveryDestination]
    let makeSolveModel: ((UUID) -> PrimarySolveViewModel)?
    let reviewViewModel: ProblemReviewViewModel?
    let performAction: () -> Void
    let startProblemCapture: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    Text(detail.stage.label)
                        .font(TypographyTokens.title)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Text(detail.problemSessionId.uuidString)
                        .font(.caption2.monospaced())
                        .foregroundStyle(ColorTokens.textTertiary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                }

                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    RecoveryDetailRow(title: "Status", value: detail.status.rawValue)
                    RecoveryDetailRow(title: "Next", value: detail.nextAction.rawValue)
                    if let failureCode = detail.failureCode {
                        RecoveryDetailRow(title: "Failure", value: failureCode)
                    }
                    if let activeJob = detail.activeJob {
                        RecoveryDetailRow(title: "Job", value: "\(activeJob.type.rawValue) · \(activeJob.status)")
                    }
                    if let currentParse = detail.currentParse {
                        RecoveryDetailRow(title: "Parse", value: "\(currentParse.source) revision \(currentParse.revision)")
                    }
                    if let classification = detail.classification {
                        RecoveryDetailRow(title: "Classification", value: classification.status)
                    }
                }

                Spacer()

                if detail.nextAction == .recaptureOrReimport || detail.nextAction == .resumeUpload {
                    Button {
                        dismiss()
                        startProblemCapture()
                    } label: {
                        Label(detail.nextAction.buttonTitle, systemImage: "camera.viewfinder")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                } else if detail.nextAction == .readyForSolve && makeSolveModel == nil {
                    Text("solve.error.disabled").fixedSize(horizontal: false, vertical: true)
                } else {
                    Button {
                        performAction()
                    } label: {
                        Label(detail.nextAction == .readyForSolve ? String(localized: "solve.start") : detail.nextAction.buttonTitle, systemImage: detail.nextAction.isRefreshOnly ? "arrow.clockwise" : "arrow.right.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier(detail.nextAction == .readyForSolve ? "history.solve" : "history.next")
                    .disabled(detail.nextAction == .unsupported || detail.nextAction == .none)
                }
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: .infinity, alignment: .topLeading)
            }
            .background(ColorTokens.background)
            .navigationDestination(for: RecoveryDestination.self) { destination in
                switch destination {
                case .solve:
                    if let makeSolveModel {
                        PrimarySolveView(viewModel: makeSolveModel(detail.problemSessionId), reviewProblem: {
                            if let reviewViewModel { reviewViewModel.reset(); path = [.review] } else { path = [] }
                        })
                    }
                case .review:
                    if let reviewViewModel {
                        ProblemReviewView(viewModel: reviewViewModel, problemSessionId: detail.problemSessionId,
                            onFinished: { path = [] })
                    }
                }
            }
            .navigationTitle("Session")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

private struct RecoveryDetailRow: View {
    let title: String
    let value: String

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(title)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textTertiary)
                .frame(width: 96, alignment: .leading)
            Text(value)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
                .lineLimit(2)
                .minimumScaleFactor(0.75)
            Spacer(minLength: 0)
        }
    }
}

private struct ProblemHistoryEmptyView: View {
    let text: String
    let startProblemCapture: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            Label(text, systemImage: "tray")
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
            Button(action: startProblemCapture) {
                Label("Start Capture", systemImage: "camera.viewfinder")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
        }
    }
}

extension ProblemSessionDetail: Identifiable {
    var id: UUID { problemSessionId }
}

private extension View {
    func problemHistoryPanel() -> some View {
        padding(SpacingTokens.md)
            .background(ColorTokens.surface)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
            .overlay {
                RoundedRectangle(cornerRadius: RadiusTokens.medium)
                    .stroke(ColorTokens.border, lineWidth: 1)
                    .allowsHitTesting(false)
            }
    }
}
