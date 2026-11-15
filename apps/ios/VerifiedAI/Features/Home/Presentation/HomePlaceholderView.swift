import SwiftUI

struct HomePlaceholderView: View {
    let environmentName: String
    let launchState: AppLaunchState
    let entitlement: Entitlement?
    let entitlementMessage: String?
    let retry: () -> Void
    let startProblemCapture: () -> Void
    let problemHistoryViewModel: ProblemHistoryViewModel?
    let manageSubscription: () -> Void
    let manageAccount: () -> Void
    var isSolveFixture = false
    var makeSolveModel: ((UUID) -> PrimarySolveViewModel)? = nil
    var reviewViewModel: ProblemReviewViewModel? = nil
    var coachHubViewModel: CoachHubViewModel? = nil
    var spacedReviewViewModel: SpacedReviewViewModel? = nil
    var dailyCoachViewModel: DailyCoachViewModel? = nil
    var coldStartViewModel: ColdStartDiagnosticViewModel? = nil
    var examViewModel: ExamCatalogAndTargetViewModel? = nil
    var candidateProfileViewModel: CandidateProfileViewModel? = nil
    var blueprintAssemblyViewModel: ExamBlueprintAssemblyViewModel? = nil

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.xl) {
                topBar

                if case .degraded(let reason, let recoverable) = launchState {
                    AppSurfaceCard {
                        Label(reason, systemImage: "exclamationmark.triangle")
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.warning)
                        if recoverable {
                            Button("Retry", action: retry)
                                .buttonStyle(AppSecondaryButtonStyle())
                        }
                    }
                }

                heroScanCard

                if let problemHistoryViewModel {
                    ProblemHistorySectionView(
                        viewModel: problemHistoryViewModel,
                        startProblemCapture: startProblemCapture,
                        makeSolveModel: makeSolveModel,
                        reviewViewModel: reviewViewModel
                    )
                }
            }
            .padding(.horizontal, SpacingTokens.lg)
            .padding(.top, SpacingTokens.sm)
            .padding(.bottom, SpacingTokens.xl)
        }
        .refreshable {
            await problemHistoryViewModel?.refresh()
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
    }

    private var topBar: some View {
        HStack(alignment: .center) {
            Text("Verified AI")
                .font(.system(size: 20, weight: .bold))
                .foregroundStyle(ColorTokens.textPrimary)
                .accessibilityIdentifier("appTitle")

            Spacer()

            Button(action: manageAccount) {
                Image(systemName: "person.circle")
                    .font(.system(size: 18, weight: .medium))
                    .foregroundStyle(ColorTokens.textPrimary)
                    .frame(width: 36, height: 36)
                    .background(ColorTokens.surface)
                    .clipShape(Circle())
                    .overlay(
                        Circle()
                            .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Account")
            .accessibilityIdentifier("account.settings.button")
        }
        .frame(minHeight: 44)
    }

    private var heroScanCard: some View {
        AppSurfaceCard(padding: SpacingTokens.lg) {
            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                Label(LocalizedStringKey("home.scan.title"), systemImage: "camera.viewfinder")
                    .font(TypographyTokens.sectionTitle)
                    .foregroundStyle(ColorTokens.textPrimary)

                Text(LocalizedStringKey("home.scan.subtitle"))
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.bottom, SpacingTokens.xs)

            Button(action: startProblemCapture) {
                Label(LocalizedStringKey("home.scan.cta"), systemImage: "arrow.up.right")
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .accessibilityIdentifier("home.scanProblem")
        }
    }
}

struct HomePlaceholderView_Previews: PreviewProvider {
    static var previews: some View {
        HomePlaceholderView(
            environmentName: "Development",
            launchState: .ready,
            entitlement: nil,
            entitlementMessage: nil,
            retry: {},
            startProblemCapture: {},
            problemHistoryViewModel: nil,
            manageSubscription: {},
            manageAccount: {}
        )
        .preferredColorScheme(.dark)
    }
}
