import SwiftUI

struct ProblemHistorySectionView: View {
    @Bindable var viewModel: ProblemHistoryViewModel
    let startProblemCapture: () -> Void

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
        .sheet(item: $presentedDetail) { detail in
            ProblemSessionRecoveryDetailView(
                detail: detail,
                performAction: {
                    Task {
                        await viewModel.performNextAction(for: detail)
                        presentedDetail = viewModel.selectedDetail
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
        }
        .buttonStyle(.plain)
    }

    private var iconName: String {
        switch item.stage {
        case .readyForSolve:
            "checkmark.seal"
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
            ColorTokens.success
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
    let performAction: () -> Void
    let startProblemCapture: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
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
                } else {
                    Button {
                        performAction()
                    } label: {
                        Label(detail.nextAction.buttonTitle, systemImage: detail.nextAction.isRefreshOnly ? "arrow.clockwise" : "arrow.right.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(detail.nextAction == .unsupported || detail.nextAction == .none)
                }
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(ColorTokens.background)
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
            }
    }
}
