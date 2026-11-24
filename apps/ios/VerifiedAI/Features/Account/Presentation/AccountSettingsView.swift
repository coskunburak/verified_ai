import SwiftUI

struct AccountSettingsView: View {
    @Bindable var viewModel: AccountSettingsViewModel
    var weeklyReportViewModel: WeeklyProgressReportViewModel? = nil
    var notificationPreferencesViewModel: NotificationPreferencesViewModel? = nil
    var growthSharingViewModel: GrowthSharingViewModel? = nil
    var environmentName: String? = nil
    var entitlement: Entitlement? = nil
    var quotaSummary: QuotaSummary? = nil
    let onAccountDeleted: () -> Void
    var onSignOut: (() -> Void)? = nil

    @Environment(\.dismiss) private var dismiss
    @State private var confirmationText = ""
    @AppStorage("verifiedai.theme_preference") private var themePreferenceRaw: String = AppThemePreference.light.rawValue

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                accountSection
                if let quotaSummary {
                    QuotaUsageCardView(summary: quotaSummary)
                }
                appearanceSection
                if let notificationPreferencesViewModel {
                    notificationsSection(notificationPreferencesViewModel)
                }
                if let weeklyReportViewModel {
                    weeklyReportSection(weeklyReportViewModel)
                }
                if let growthSharingViewModel {
                    growthSection(growthSharingViewModel)
                }
                exportSection
                deletionSection
                statusSection
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: 620, alignment: .leading)
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
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
        AppSurfaceCard {
            AppSectionHeader("Account", subtitle: "Workspace identity & session state")

            if let account = viewModel.account {
                row("Status", account.status.title)
                row("User ID", account.userId.uuidString)
            } else if viewModel.state == .loading {
                ProgressView()
                    .tint(ColorTokens.textSecondary)
                    .accessibilityLabel("Loading account")
            }

            if let entitlement {
                row("Plan", entitlement.tier.title)
            }
            if let environmentName {
                row("Environment", environmentName)
            }

            if let onSignOut {
                Button(role: .destructive) {
                    dismiss()
                    onSignOut()
                } label: {
                    Label(LocalizedStringKey("account.sign_out"), systemImage: "rectangle.portrait.and.arrow.right")
                }
                .buttonStyle(AppSecondaryButtonStyle())
                .accessibilityIdentifier("account.signOutButton")
            }
        }
    }

    private var appearanceSection: some View {
        AppSurfaceCard {
            AppSectionHeader("Appearance", subtitle: "Photomath Editorial Crisp — live canvas theme")
            Picker("Appearance", selection: $themePreferenceRaw) {
                ForEach(AppThemePreference.allCases) { option in
                    Text(option.title).tag(option.rawValue)
                }
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("account.themePicker")

            HStack(spacing: SpacingTokens.xs) {
                Circle()
                    .fill(ColorTokens.canvas)
                    .frame(width: 14, height: 14)
                    .overlay(Circle().stroke(ColorTokens.borderStrong, lineWidth: 1))
                Circle()
                    .fill(ColorTokens.action)
                    .frame(width: 14, height: 14)
                Circle()
                    .fill(ColorTokens.stepHighlight)
                    .frame(width: 14, height: 14)
                Text((AppThemePreference(rawValue: themePreferenceRaw) ?? .light) == .dark
                     ? "Carbon Crisp (#121316 + Coral Rose + Sky Blue)"
                     : "Editorial Paper (#F4F5F7 + Signal Crimson + Royal Blue)")
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            }
        }
    }

    private func notificationsSection(_ preferencesViewModel: NotificationPreferencesViewModel) -> some View {
        AppSurfaceCard {
            AppSectionHeader(
                LocalizedStringKey("notification.preferences.entry.section"),
                subtitle: LocalizedStringKey("notification.preferences.entry.subtitle")
            )

            NavigationLink {
                NotificationPreferencesView(viewModel: preferencesViewModel)
            } label: {
                Label(LocalizedStringKey("notification.preferences.entry.title"), systemImage: "bell.badge")
            }
            .buttonStyle(AppSecondaryButtonStyle())
            .accessibilityIdentifier("account.notificationPreferencesLink")
        }
    }

    private func weeklyReportSection(_ reportViewModel: WeeklyProgressReportViewModel) -> some View {
        AppSurfaceCard {
            AppSectionHeader(
                LocalizedStringKey("weekly.report.entry.section"),
                subtitle: LocalizedStringKey("weekly.report.entry.subtitle")
            )

            NavigationLink {
                WeeklyProgressReportView(viewModel: reportViewModel)
            } label: {
                Label(LocalizedStringKey("weekly.report.entry.title"), systemImage: "chart.bar.doc.horizontal")
            }
            .buttonStyle(AppSecondaryButtonStyle())
            .accessibilityIdentifier("account.weeklyReportLink")
        }
    }

    private func growthSection(_ growthViewModel: GrowthSharingViewModel) -> some View {
        AppSurfaceCard {
            AppSectionHeader(
                LocalizedStringKey("growth.entry.section"),
                subtitle: LocalizedStringKey("growth.entry.subtitle")
            )

            NavigationLink {
                GrowthShareAndReferralView(viewModel: growthViewModel)
            } label: {
                Label(LocalizedStringKey("growth.entry.title"), systemImage: "person.2.wave.2")
            }
            .buttonStyle(AppSecondaryButtonStyle())
            .accessibilityIdentifier("account.growthShareAndReferralLink")
        }
    }

    private var exportSection: some View {
        AppSurfaceCard {
            AppSectionHeader("Data Export", subtitle: "Request a portable archive of your study data")

            Button {
                Task { await viewModel.requestDataExport() }
            } label: {
                Label("Export Data", systemImage: "square.and.arrow.down")
            }
            .buttonStyle(AppSecondaryButtonStyle())
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
        AppSurfaceCard {
            AppSectionHeader("Account Deletion", subtitle: "Permanently remove workspace & records")

            Button(role: .destructive) {
                Task { await viewModel.requestDeletion() }
            } label: {
                Label("Request Deletion", systemImage: "trash")
            }
            .buttonStyle(AppSecondaryButtonStyle())
            .disabled(viewModel.state.isBusy || viewModel.deletionRequest?.status == .deleted)

            if viewModel.deletionRequest?.status == .deletionRequested || viewModel.state == .deletionRequested {
                TextField("DELETE", text: $confirmationText)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .padding(.horizontal, SpacingTokens.compact)
                    .frame(height: 44)
                    .background(ColorTokens.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous)
                            .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                    )

                Button(role: .destructive) {
                    Task { await viewModel.confirmDeletion(confirmationText: confirmationText) }
                } label: {
                    Label("Confirm Deletion", systemImage: "checkmark.seal")
                }
                .buttonStyle(AppPrimaryButtonStyle())
                .disabled(viewModel.state.isBusy || confirmationText.isEmpty)
            }
        }
    }

    @ViewBuilder
    private var statusSection: some View {
        if let message = viewModel.message {
            AppNoticeBanner(
                LocalizedStringKey(stringLiteral: message),
                systemImage: statusIcon,
                tone: viewModel.state == .failed || viewModel.state == .offline ? .warning : .neutral
            )
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

    private func row(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top, spacing: SpacingTokens.md) {
            Text(label)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
                .frame(width: 96, alignment: .leading)
            Text(value)
                .font(TypographyTokens.monoCode)
                .foregroundStyle(ColorTokens.textPrimary)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
