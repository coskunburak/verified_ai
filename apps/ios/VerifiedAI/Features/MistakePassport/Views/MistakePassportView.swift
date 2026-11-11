// apps/ios/VerifiedAI/Features/MistakePassport/Views/MistakePassportView.swift
import SwiftUI

public struct MistakePassportView: View {
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
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundStyle(.orange)
                        Text(LocalizedStringKey(errorKey))
                            .font(.headline)
                            .multilineTextAlignment(.center)
                        Button(action: { viewModel.loadPassport(statusFilter: viewModel.selectedStatusFilter) }) {
                            Text("Retry")
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                case .empty(let status):
                    emptyStateView(status: status)

                case .loaded(let response, let isOfflineStale):
                    ScrollView {
                        VStack(spacing: 16) {
                            if isOfflineStale {
                                HStack(spacing: 8) {
                                    Image(systemName: "wifi.slash")
                                        .foregroundStyle(.orange)
                                    Text(LocalizedStringKey("passport.banner.offline_stale"))
                                        .font(.caption.weight(.medium))
                                        .foregroundStyle(.secondary)
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                                .padding(.horizontal)
                                .accessibilityIdentifier("passport_offline_banner")
                            }

                            // Hero Summary Card
                            heroSummaryCard(response.summaryMetrics)
                                .padding(.horizontal)

                            // Category Tabs: Conceptual Traps vs Arithmetic Slips
                            categoryPicker
                                .padding(.horizontal)

                            // Status Filter Chips
                            statusFilterScrollView

                            // Items List
                            let items = viewModel.activeItems(from: response)
                            if items.isEmpty {
                                emptyStateView(status: viewModel.selectedStatusFilter)
                            } else {
                                LazyVStack(spacing: 12) {
                                    ForEach(items) { trap in
                                        ExamTrapRadarCardView(trap: trap, onLaunchTwin: onLaunchTwinChallenge)
                                    }
                                }
                                .padding(.horizontal)
                            }
                        }
                        .padding(.vertical)
                    }
                    .refreshable {
                        viewModel.loadPassport(statusFilter: viewModel.selectedStatusFilter)
                    }

                case .idle:
                    Color.clear
                        .onAppear {
                            viewModel.loadPassport()
                        }
                }
            }
            .navigationTitle(Text(LocalizedStringKey("passport.header.title")))
            .accessibilityIdentifier("mistake_passport_root_view")
            .onDisappear {
                viewModel.cancel()
            }
        }
    }

    private func heroSummaryCard(_ metrics: PassportSummaryMetrics) -> some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text(LocalizedStringKey("passport.header.conquered_traps"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(.green)
                    Text("\(metrics.conqueredExamTrapsCount)")
                        .font(.title2.bold())
                        .accessibilityIdentifier("hero_conquered_traps_count")
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Divider()
                .frame(height: 36)

            VStack(alignment: .leading, spacing: 4) {
                Text(LocalizedStringKey("passport.header.independent_transfer"))
                    .font(.caption)
                    .foregroundStyle(.secondary)
                HStack(spacing: 4) {
                    Image(systemName: "shield.lefthalf.filled.badge.checkmark")
                        .foregroundStyle(.indigo)
                    Text("\(Int((metrics.independentTransferRate * 100).rounded()))%")
                        .font(.title2.bold())
                        .accessibilityIdentifier("hero_independent_transfer_rate")
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Color(.secondarySystemBackground))
        )
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
            HStack(spacing: 8) {
                filterChip(titleKey: "passport.filter.all", status: nil)

                ForEach(TrapLifecycleStatus.allCases) { status in
                    filterChip(titleKey: status.localizedKey, status: status)
                }
            }
            .padding(.horizontal)
        }
    }

    private func filterChip(titleKey: String, status: TrapLifecycleStatus?) -> some View {
        let isSelected = viewModel.selectedStatusFilter == status
        return Button(action: { viewModel.loadPassport(statusFilter: status) }) {
            Text(LocalizedStringKey(titleKey))
                .font(.caption.weight(isSelected ? .bold : .regular))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                    Capsule()
                        .fill(isSelected ? Color.indigo : Color(.tertiarySystemBackground))
                )
                .foregroundStyle(isSelected ? Color.white : Color.primary)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("filter_chip_\(status?.rawValue ?? "ALL")")
    }

    private func emptyStateView(status: TrapLifecycleStatus?) -> some View {
        ContentUnavailableView(
            LocalizedStringKey("passport.empty.title"),
            systemImage: "shield.lefthalf.filled.badge.checkmark",
            description: Text(LocalizedStringKey("passport.empty.description"))
        )
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .accessibilityIdentifier("passport_empty_view")
    }
}
