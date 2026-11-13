import SwiftUI

struct AccountSettingsView: View {
    @Bindable var viewModel: AccountSettingsViewModel
    var weeklyReportViewModel: WeeklyProgressReportViewModel? = nil
    let onAccountDeleted: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var confirmationText = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                accountSection
                if let weeklyReportViewModel {
                    weeklyReportSection(weeklyReportViewModel)
                }
                exportSection
                deletionSection
                statusSection
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: 620, alignment: .leading)
        }
        .background(ColorTokens.background)
        .navigationTitle("Account")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if viewModel.state == .idle {
                await viewModel.load()
            }
        }
        .onChange(of: viewModel.state) { _, state in
            if state == .deleted {
                onAccountDeleted()
                dismiss()
            }
        }
    }

    private var accountSection: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text("Account")
                .font(TypographyTokens.title)
                .foregroundStyle(ColorTokens.textPrimary)

            if let account = viewModel.account {
                row("Status", account.status.title)
                row("User ID", account.userId.uuidString)
            } else if viewModel.state == .loading {
                ProgressView()
                    .accessibilityLabel("Loading account")
            }
        }
    }

    private func weeklyReportSection(_ reportViewModel: WeeklyProgressReportViewModel) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text(LocalizedStringKey("weekly.report.entry.section"))
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textPrimary)

            Text(LocalizedStringKey("weekly.report.entry.subtitle"))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)

            NavigationLink {
                WeeklyProgressReportView(viewModel: reportViewModel)
            } label: {
                Label(LocalizedStringKey("weekly.report.entry.title"), systemImage: "chart.bar.doc.horizontal")
            }
            .buttonStyle(.borderedProminent)
            .tint(ColorTokens.action)
            .accessibilityIdentifier("account.weeklyReportLink")
        }
    }

    private var exportSection: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text("Data Export")
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textPrimary)

            Button {
                Task { await viewModel.requestDataExport() }
            } label: {
                Label("Export Data", systemImage: "square.and.arrow.down")
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.state.isBusy)

            if let exportRecord = viewModel.exportRecord {
                row("Export", exportRecord.status.rawValue)
                row("Schema", exportRecord.schemaVersion)
            }
            if let document = viewModel.exportDocument {
                row("Categories", document.categories.joined(separator: ", "))
            }
        }
    }

    private var deletionSection: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text("Deletion")
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textPrimary)

            Button(role: .destructive) {
                Task { await viewModel.requestDeletion() }
            } label: {
                Label("Request Deletion", systemImage: "trash")
            }
            .buttonStyle(.bordered)
            .disabled(viewModel.state.isBusy || viewModel.deletionRequest?.status == .deleted)

            if viewModel.deletionRequest?.status == .deletionRequested || viewModel.state == .deletionRequested {
                TextField("DELETE", text: $confirmationText)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)

                Button(role: .destructive) {
                    Task { await viewModel.confirmDeletion(confirmationText: confirmationText) }
                } label: {
                    Label("Confirm Deletion", systemImage: "checkmark.seal")
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.state.isBusy || confirmationText.isEmpty)
            }
        }
    }

    @ViewBuilder
    private var statusSection: some View {
        if let message = viewModel.message {
            Label(message, systemImage: statusIcon)
                .font(TypographyTokens.caption)
                .foregroundStyle(statusColor)
        }
    }

    private var statusIcon: String {
        switch viewModel.state {
        case .failed, .offline:
            return "exclamationmark.triangle"
        case .deleted:
            return "checkmark.seal"
        default:
            return "info.circle"
        }
    }

    private var statusColor: Color {
        switch viewModel.state {
        case .failed, .offline:
            return ColorTokens.warning
        default:
            return ColorTokens.textSecondary
        }
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top, spacing: SpacingTokens.md) {
            Text(label)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
                .frame(width: 96, alignment: .leading)
            Text(value)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textPrimary)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
