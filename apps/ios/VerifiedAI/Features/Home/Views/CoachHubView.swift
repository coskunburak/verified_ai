import SwiftUI

public struct CoachHubView: View {
    @ObservedObject public var coachHubViewModel: CoachHubViewModel
    @ObservedObject public var spacedReviewViewModel: SpacedReviewViewModel
    public let onStartPracticeSession: () -> Void

    public init(
        coachHubViewModel: CoachHubViewModel,
        spacedReviewViewModel: SpacedReviewViewModel,
        onStartPracticeSession: @escaping () -> Void = {}
    ) {
        self.coachHubViewModel = coachHubViewModel
        self.spacedReviewViewModel = spacedReviewViewModel
        self.onStartPracticeSession = onStartPracticeSession
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                // Block 1: Next-Best-Action Recommendations (Sprint 8.1)
                recommendationsBlock

                // Block 2: Spaced Repetition & 7-Day Trap Immunity Checks (Sprint 8.3)
                spacedReviewBlock
            }
            .padding(16)
        }
    }

    @ViewBuilder
    private var recommendationsBlock: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(LocalizedStringKey("coach.nba.section.title"), systemImage: "sparkles")
                    .font(.headline)
                Spacer()
                if coachHubViewModel.isOfflineFallback {
                    Label(LocalizedStringKey("coach.nba.offline.banner"), systemImage: "wifi.slash")
                        .font(.caption2)
                        .foregroundColor(.orange)
                }
            }

            if coachHubViewModel.recommendations.isEmpty {
                Text(LocalizedStringKey("coach.nba.empty.title"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 16)
            } else {
                ForEach(coachHubViewModel.recommendations) { rec in
                    RecommendationCardView(
                        recommendation: rec,
                        onAccept: {
                            Task {
                                await coachHubViewModel.acceptRecommendation(rec.id)
                            }
                        },
                        onDismiss: {
                            Task {
                                await coachHubViewModel.dismissRecommendation(rec.id)
                            }
                        }
                    )
                }
            }
        }
    }

    @ViewBuilder
    private var spacedReviewBlock: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(LocalizedStringKey("studyplan.spaced.section.title"), systemImage: "arrow.triangle.2.circlepath")
                    .font(.headline)
                Spacer()
                if spacedReviewViewModel.isOfflineFallback {
                    Label(LocalizedStringKey("coach.nba.offline.banner"), systemImage: "wifi.slash")
                        .font(.caption2)
                        .foregroundColor(.orange)
                }
            }

            if spacedReviewViewModel.items.isEmpty {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundColor(.green)
                    Text(LocalizedStringKey("studyplan.spaced.empty.title"))
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.vertical, 16)
            } else {
                ForEach(spacedReviewViewModel.items) { item in
                    SpacedReviewCardView(
                        item: item,
                        onStartReview: {
                            Task {
                                await spacedReviewViewModel.completeItem(
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
                                await spacedReviewViewModel.dismissItem(itemId: item.itemId)
                            }
                        }
                    )
                }
            }
        }
    }
}
