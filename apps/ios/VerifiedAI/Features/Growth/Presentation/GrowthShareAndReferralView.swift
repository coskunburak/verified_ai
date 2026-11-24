import SwiftUI

struct GrowthShareAndReferralView: View {
    @Bindable var viewModel: GrowthSharingViewModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var aliasInput = ""
    @State private var friendCodeInput = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                if let errorMessage = viewModel.errorMessage {
                    AppNoticeBanner(
                        LocalizedStringKey(stringLiteral: errorMessage),
                        systemImage: "exclamationmark.triangle",
                        tone: .warning
                    )
                    .accessibilityIdentifier("growth.errorBanner")
                }

                if let statusMessage = viewModel.statusMessage {
                    AppNoticeBanner(
                        LocalizedStringKey(stringLiteral: statusMessage),
                        systemImage: "checkmark.seal",
                        tone: .success
                    )
                    .accessibilityIdentifier("growth.statusBanner")
                }

                referralCard
                applyReferralCard
                createShareCard
                activeSharesSection
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: 620, alignment: .leading)
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
        .navigationTitle(LocalizedStringKey("growth.screen.title"))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if viewModel.referralSummary == nil && !viewModel.isLoading {
                await viewModel.loadDashboard()
            }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: viewModel.shares.count)
    }

    private var referralCard: some View {
        AppSurfaceCard {
            AppSectionHeader(
                LocalizedStringKey("growth.referral.section.title"),
                subtitle: LocalizedStringKey("growth.referral.section.subtitle")
            ) {
                AppStatusBadge(
                    LocalizedStringKey("growth.privacy.badge"),
                    systemImage: "lock.shield",
                    tone: .success
                )
            }

            if let summary = viewModel.referralSummary {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: SpacingTokens.sm) {
                        codeDisplay(summary.referralCode)
                        Spacer()
                        shareReferralButton(summary)
                    }
                    VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                        codeDisplay(summary.referralCode)
                        shareReferralButton(summary)
                    }
                }

                ViewThatFits(in: .horizontal) {
                    HStack(spacing: SpacingTokens.sm) {
                        AppMetricTile(
                            title: "growth.referral.metric.qualified",
                            value: "\(summary.qualifiedCount)",
                            systemImage: "checkmark.circle",
                            tone: .success
                        )
                        AppMetricTile(
                            title: "growth.referral.metric.credits",
                            value: "\(summary.totalRewardCredits)",
                            systemImage: "sparkles",
                            tone: .stepHighlight
                        )
                    }
                    VStack(spacing: SpacingTokens.sm) {
                        AppMetricTile(
                            title: "growth.referral.metric.qualified",
                            value: "\(summary.qualifiedCount)",
                            systemImage: "checkmark.circle",
                            tone: .success
                        )
                        AppMetricTile(
                            title: "growth.referral.metric.credits",
                            value: "\(summary.totalRewardCredits)",
                            systemImage: "sparkles",
                            tone: .stepHighlight
                        )
                    }
                }
            } else if viewModel.isLoading {
                ProgressView()
                    .tint(ColorTokens.action)
                    .accessibilityLabel(LocalizedStringKey("growth.loading"))
            } else {
                Button {
                    Task { await viewModel.loadDashboard() }
                } label: {
                    Label(LocalizedStringKey("growth.referral.load_button"), systemImage: "arrow.clockwise")
                }
                .buttonStyle(AppSecondaryButtonStyle())
                .accessibilityIdentifier("growth.referral.loadButton")
            }
        }
    }

    private var applyReferralCard: some View {
        AppSurfaceCard {
            AppSectionHeader(
                LocalizedStringKey("growth.referral.apply.title"),
                subtitle: LocalizedStringKey("growth.referral.apply.subtitle")
            )

            TextField(LocalizedStringKey("growth.referral.apply.placeholder"), text: $friendCodeInput)
                .textInputAutocapitalization(.characters)
                .autocorrectionDisabled()
                .padding(.horizontal, SpacingTokens.compact)
                .frame(minHeight: 44)
                .background(ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous)
                        .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                )
                .accessibilityIdentifier("growth.referral.codeField")
                .accessibilityLabel(LocalizedStringKey("growth.referral.apply.placeholder"))

            Button {
                let code = friendCodeInput
                Task { await viewModel.attributeReferral(code: code, source: .manualCode) }
            } label: {
                Label(LocalizedStringKey("growth.referral.apply.button"), systemImage: "person.badge.plus")
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(viewModel.isBusy || friendCodeInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .accessibilityIdentifier("growth.referral.applyButton")
            .accessibilityHint(LocalizedStringKey("growth.referral.apply.hint"))
        }
    }

    private var createShareCard: some View {
        AppSurfaceCard {
            AppSectionHeader(
                LocalizedStringKey("growth.share.create.title"),
                subtitle: LocalizedStringKey("growth.share.create.subtitle")
            )

            TextField(LocalizedStringKey("growth.share.alias.placeholder"), text: $aliasInput)
                .autocorrectionDisabled()
                .padding(.horizontal, SpacingTokens.compact)
                .frame(minHeight: 44)
                .background(ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous)
                        .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                )
                .accessibilityIdentifier("growth.share.aliasField")

            ViewThatFits(in: .horizontal) {
                HStack(spacing: SpacingTokens.sm) {
                    challengeShareButton
                    weeklyShareButton
                }
                VStack(spacing: SpacingTokens.sm) {
                    challengeShareButton
                    weeklyShareButton
                }
            }
        }
    }

    private var challengeShareButton: some View {
        Button {
            let alias = aliasInput
            Task { await viewModel.createChallengeShare(displayAlias: alias) }
        } label: {
            Label(LocalizedStringKey("growth.share.create.challenge_button"), systemImage: "square.and.arrow.up")
        }
        .buttonStyle(AppPrimaryButtonStyle())
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("growth.share.createChallengeButton")
    }

    private var weeklyShareButton: some View {
        Button {
            let alias = aliasInput
            Task { await viewModel.createWeeklyMilestoneShare(displayAlias: alias) }
        } label: {
            Label(LocalizedStringKey("growth.share.create.weekly_button"), systemImage: "rosette")
        }
        .buttonStyle(AppSecondaryButtonStyle())
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("growth.share.createWeeklyButton")
    }

    private var activeSharesSection: some View {
        AppSurfaceCard {
            AppSectionHeader(
                LocalizedStringKey("growth.share.list.title"),
                subtitle: LocalizedStringKey("growth.share.list.subtitle")
            )

            if viewModel.shares.isEmpty {
                AppEmptyState(
                    systemImage: "link.badge.plus",
                    title: LocalizedStringKey("growth.share.empty.title"),
                    subtitle: LocalizedStringKey("growth.share.empty.subtitle")
                )
            } else {
                ForEach(viewModel.shares) { share in
                    shareRow(share)
                }
            }
        }
    }

    private func shareRow(_ share: ShareableArtifactDTO) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            HStack(alignment: .firstTextBaseline, spacing: SpacingTokens.xs) {
                Text(LocalizedStringKey(share.artifactType.localizationKey))
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                AppStatusBadge(
                    verbatimTitle: share.status.rawValue,
                    systemImage: share.status == .active ? "checkmark.seal" : "nosign",
                    tone: share.status == .active ? .success : .neutral
                )
            }

            if let alias = share.displayAlias {
                Text(alias)
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            }

            Text(share.deepLinkUrl)
                .font(TypographyTokens.monoCode)
                .foregroundStyle(ColorTokens.textTertiary)
                .lineLimit(2)

            if share.status == .active {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: SpacingTokens.sm) {
                        if let url = URL(string: share.deepLinkUrl) {
                            ShareLink(item: url) {
                                Label(LocalizedStringKey("growth.share.action.share_link"), systemImage: "paperplane")
                            }
                            .buttonStyle(AppSecondaryButtonStyle())
                        }
                        revokeButton(share)
                    }
                    VStack(spacing: SpacingTokens.xs) {
                        if let url = URL(string: share.deepLinkUrl) {
                            ShareLink(item: url) {
                                Label(LocalizedStringKey("growth.share.action.share_link"), systemImage: "paperplane")
                            }
                            .buttonStyle(AppSecondaryButtonStyle())
                        }
                        revokeButton(share)
                    }
                }
            }
        }
        .padding(SpacingTokens.sm)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
    }

    private func revokeButton(_ share: ShareableArtifactDTO) -> some View {
        Button(role: .destructive) {
            Task { await viewModel.revokeShare(shareId: share.shareId) }
        } label: {
            Label(LocalizedStringKey("growth.share.action.revoke"), systemImage: "xmark.circle")
        }
        .buttonStyle(AppSecondaryButtonStyle())
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("growth.share.revokeButton.\(share.shareToken)")
    }

    private func codeDisplay(_ code: String) -> some View {
        Text(code)
            .font(TypographyTokens.formulaLarge)
            .foregroundStyle(ColorTokens.textPrimary)
            .textSelection(.enabled)
            .accessibilityIdentifier("growth.referral.myCode")
    }

    @ViewBuilder
    private func shareReferralButton(_ summary: ReferralSummaryDTO) -> some View {
        if let url = URL(string: summary.deepLinkUrl) {
            ShareLink(item: url) {
                Label(LocalizedStringKey("growth.referral.share_button"), systemImage: "square.and.arrow.up")
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .accessibilityIdentifier("growth.referral.shareLink")
        }
    }
}
