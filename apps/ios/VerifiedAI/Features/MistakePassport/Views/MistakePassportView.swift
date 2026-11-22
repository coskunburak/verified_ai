// apps/ios/VerifiedAI/Features/MistakePassport/Views/MistakePassportView.swift
import SwiftUI

public struct MistakePassportView: View {
    @Environment(\.learningOsAccess) private var access
    @Bindable public var viewModel: MistakePassportViewModel
    public var onLaunchTwinChallenge: ((UUID) -> Void)?

    public init(
        viewModel: MistakePassportViewModel,
        onLaunchTwinChallenge: ((UUID) -> Void)? = nil
    ) {
        self.viewModel = viewModel
        self.onLaunchTwinChallenge = onLaunchTwinChallenge
    }

    public var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                switch viewModel.state {
                case .loading:
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .accessibilityIdentifier("mistake_passport_loading")

                case .error(let errorKey):
                    VStack(spacing: SpacingTokens.md) {
                        AppNoticeBanner(LocalizedStringKey(errorKey), systemImage: "exclamationmark.triangle", tone: .warning)
                        Button(action: { viewModel.loadPassport(statusFilter: viewModel.selectedStatusFilter) }) {
                            Text("Retry")
                        }
                        .buttonStyle(AppPrimaryButtonStyle())
                    }
                    .padding(SpacingTokens.lg)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                case .empty(let status):
                    emptyStateView(status: status)

                case .loaded(let response, let isOfflineStale):
                    if access?.scope == nil || response.learnerId == access?.scope?.user {
                    ScrollView {
                        VStack(spacing: SpacingTokens.md) {
                            if isOfflineStale {
                                AppNoticeBanner("passport.banner.offline_stale", systemImage: "wifi.slash", tone: .warning)
                                    .padding(.horizontal, SpacingTokens.lg)
                                    .accessibilityIdentifier("passport_offline_banner")
                            }

                            heroSummaryCard(response.summaryMetrics)
                                .padding(.horizontal, SpacingTokens.lg)

                            if access?.decision(.mistakePassportFull) == .allowed && !isOfflineStale {
                                categoryPicker
                                    .padding(.horizontal, SpacingTokens.lg)

                                statusFilterScrollView

                                let items = viewModel.activeItems(from: response)
                                if items.isEmpty {
                                    emptyStateView(status: viewModel.selectedStatusFilter)
                                } else {
                                    LazyVStack(spacing: SpacingTokens.compact) {
                                        ForEach(items) { trap in
                                            ExamTrapRadarCardView(trap: trap, onLaunchTwin: onLaunchTwinChallenge)
                                        }
                                    }
                                    .padding(.horizontal, SpacingTokens.lg)
                                }
                            } else {
                                AppSurfaceCard {
                                    Text(LocalizedStringKey("billing.historyRetained"))
                                        .font(TypographyTokens.caption)
                                        .foregroundStyle(ColorTokens.textSecondary)
                                        .fixedSize(horizontal: false, vertical: true)
                                    ForEach(Array((response.summaryTrapTitleKeys ?? []).enumerated()), id: \.offset) { _, title in
                                        Text(LocalizedStringKey(title))
                                            .font(TypographyTokens.bodyMedium)
                                            .foregroundStyle(ColorTokens.textPrimary)
                                            .fixedSize(horizontal: false, vertical: true)
                                    }
                                    ForEach(Array((response.earnedProofs ?? []).enumerated()), id: \.offset) { _, proof in
                                        CounterexampleProofCardView(snapshot: proof)
                                    }
                                }
                                .padding(.horizontal, SpacingTokens.lg)
                                .accessibilityIdentifier("mistake.passport.readOnlySummary")
                            }
                        }
                        .padding(.vertical, SpacingTokens.lg)
                    }
                    .refreshable {
                        viewModel.loadPassport(statusFilter: viewModel.selectedStatusFilter)
                    }

                    }
                case .idle:
                    Color.clear
                        .onAppear {
                            viewModel.loadPassport()
                        }
                }
            }
            .background(ColorTokens.canvas)
            .navigationTitle(Text(LocalizedStringKey("passport.header.title")))
            .accessibilityIdentifier("mistake_passport_root_view")
            .onDisappear {
                viewModel.cancel()
            }
        }
    }

    private func heroSummaryCard(_ metrics: PassportSummaryMetrics) -> some View {
        AppSurfaceCard {
            HStack(spacing: SpacingTokens.md) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(LocalizedStringKey("passport.header.conquered_traps"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.seal.fill")
                            .foregroundStyle(ColorTokens.success)
                        Text("\(metrics.conqueredExamTrapsCount)")
                            .font(TypographyTokens.metricValue)
                            .monospacedDigit()
                            .foregroundStyle(ColorTokens.textPrimary)
                            .accessibilityIdentifier("hero_conquered_traps_count")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Rectangle()
                    .fill(ColorTokens.borderSubtle)
                    .frame(width: 1, height: 36)

                VStack(alignment: .leading, spacing: 4) {
                    Text(LocalizedStringKey("passport.header.independent_transfer"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    HStack(spacing: 6) {
                        Image(systemName: "shield.lefthalf.filled.badge.checkmark")
                            .foregroundStyle(ColorTokens.action)
                        Text("\(Int((metrics.independentTransferRate * 100).rounded()))%")
                            .font(TypographyTokens.metricValue)
                            .monospacedDigit()
                            .foregroundStyle(ColorTokens.textPrimary)
                            .accessibilityIdentifier("hero_independent_transfer_rate")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var categoryPicker: some View {
        Picker(
            "Category",
            selection: Binding(
                get: { viewModel.selectedCategoryTab },
                set: { viewModel.switchCategoryTab($0) }
            )
        ) {
            ForEach(MistakePassportViewModel.TrapCategoryTab.allCases) { tab in
                Text(LocalizedStringKey(tab.localizedKey)).tag(tab)
            }
        }
        .pickerStyle(.segmented)
        .accessibilityIdentifier("passport_category_segmented_picker")
    }

    private var statusFilterScrollView: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: SpacingTokens.xs) {
                filterChip(titleKey: "passport.filter.all", status: nil)

                ForEach(TrapLifecycleStatus.allCases) { status in
                    filterChip(titleKey: status.localizedKey, status: status)
                }
            }
            .padding(.horizontal, SpacingTokens.lg)
        }
    }

    private func filterChip(titleKey: String, status: TrapLifecycleStatus?) -> some View {
        let isSelected = viewModel.selectedStatusFilter == status
        return Button(action: { viewModel.loadPassport(statusFilter: status) }) {
            Text(LocalizedStringKey(titleKey))
                .font(TypographyTokens.caption.weight(isSelected ? .bold : .medium))
                .padding(.horizontal, SpacingTokens.compact)
                .padding(.vertical, 6)
                .background(
                    Capsule()
                        .fill(isSelected ? ColorTokens.action : ColorTokens.surfaceElevated)
                )
                .overlay(
                    Capsule()
                        .stroke(isSelected ? ColorTokens.action : ColorTokens.borderSubtle, lineWidth: 1)
                )
                .foregroundStyle(isSelected ? ColorTokens.actionForeground : ColorTokens.textPrimary)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("filter_chip_\(status?.rawValue ?? "ALL")")
    }

    private func emptyStateView(status: TrapLifecycleStatus?) -> some View {
        AppEmptyState(
            systemImage: "shield.lefthalf.filled.badge.checkmark",
            title: "passport.empty.title",
            subtitle: "passport.empty.description"
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
