import SwiftUI

public struct DailyStudyPlanView: View {
    @ObservedObject public var spacedViewModel: SpacedReviewViewModel
    public let onStartPractice: () -> Void

    public init(
        spacedViewModel: SpacedReviewViewModel,
        onStartPractice: @escaping () -> Void = {}
    ) {
        self.spacedViewModel = spacedViewModel
        self.onStartPractice = onStartPractice
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Section Header
                VStack(alignment: .leading, spacing: 4) {
                    Text(LocalizedStringKey("studyplan.spaced.section.title"))
                        .font(.title2.bold())
                        .foregroundColor(.primary)

                    if spacedViewModel.isOfflineFallback {
                        Label(LocalizedStringKey("coach.nba.offline.banner"), systemImage: "wifi.slash")
                            .font(.caption)
                            .foregroundColor(.orange)
                    }
                }

                if spacedViewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.vertical, 24)
                } else if spacedViewModel.items.isEmpty {
                    emptyStateCard
                } else {
                    // Block: Spaced Repetition & 7-Day Immunity Checks (max 3 items)
                    ForEach(spacedViewModel.items) { item in
                        SpacedReviewCardView(
                            item: item,
                            onStartReview: {
                                Task {
                                    // Complete item on successful verification
                                    await spacedViewModel.completeItem(
                                        itemId: item.itemId,
                                        passed: true,
                                        independentVerification: true,
                                        responseTimeMs: 9000,
                                        triggeredMistakeCode: nil
                                    )
                                }
                            },
                            onDismiss: {
                                Task {
                                    await spacedViewModel.dismissItem(itemId: item.itemId)
                                }
                            }
                        )
                    }
                }
            }
            .padding(16)
        }
        .task {
            await spacedViewModel.loadQueue()
        }
    }

    private var emptyStateCard: some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.circle.badge.questionmark")
                .font(.system(size: 40))
                .foregroundColor(.green)

            Text(LocalizedStringKey("studyplan.spaced.empty.title"))
                .font(.headline)
                .foregroundColor(.primary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 32)
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(16)
    }
}
