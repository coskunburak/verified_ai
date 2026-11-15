import SwiftUI

public struct MilestoneSheetView: View {
    public let summary: GamificationSummaryResponse

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ScaledMetric(relativeTo: .title2) private var statValueFontSize: CGFloat = 28
    @ScaledMetric(relativeTo: .body) private var badgeIconDimension: CGFloat = 36

    public init(summary: GamificationSummaryResponse) {
        self.summary = summary
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: SpacingTokens.md) {
                    supportiveHeaderCard
                    monthlyFreezeCard
                    recentLedgerSection
                    achievementsSection
                }
                .padding(SpacingTokens.md)
            }
            .background(ColorTokens.canvas)
            .navigationTitle(LocalizedStringKey("gamification.sheet.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                    .accessibilityLabel(Text("Done"))
                }
            }
        }
        .accessibilityIdentifier("gamification.milestoneSheet")
    }

    private var supportiveHeaderCard: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text(LocalizedStringKey(summary.supportiveHeadlineKey))
                .font(TypographyTokens.title)
                .foregroundStyle(ColorTokens.textPrimary)

            Text(LocalizedStringKey(summary.supportiveBodyKey))
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textSecondary)

            HStack(spacing: SpacingTokens.lg) {
                VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                    Text("\(summary.currentStreakDays)")
                        .font(.system(size: statValueFontSize, weight: .bold, design: .rounded))
                        .foregroundStyle(ColorTokens.action)
                        .monospacedDigit()
                    Text(LocalizedStringKey("gamification.streak.badge.accessibility_label"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: SpacingTokens.xs) {
                    Text("\(summary.longestStreakDays)")
                        .font(.system(size: statValueFontSize, weight: .bold, design: .rounded))
                        .foregroundStyle(ColorTokens.textPrimary)
                        .monospacedDigit()
                    Text("Best verified run")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
            }
            .padding(.top, SpacingTokens.xs)
        }
        .padding(SpacingTokens.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.surface)
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium)
                .strokeBorder(ColorTokens.border, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
    }

    private var monthlyFreezeCard: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            HStack(spacing: SpacingTokens.sm) {
                Image(systemName: "shield.lefthalf.filled")
                    .foregroundStyle(ColorTokens.action)
                Text(freezeRemainingText)
                    .font(TypographyTokens.body.weight(.semibold))
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text(summary.freezeQuotaMonth)
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textTertiary)
                    .monospacedDigit()
            }

            HStack(spacing: SpacingTokens.sm) {
                ForEach(0..<summary.maxMonthlyStreakFreezes, id: \.self) { index in
                    let isAvailable = index < summary.streakFreezesRemaining
                    Label {
                        Text(isAvailable ? "Protected" : "Used silently")
                            .font(TypographyTokens.caption)
                    } icon: {
                        Image(systemName: isAvailable ? "snowflake.circle.fill" : "checkmark.circle")
                    }
                    .foregroundStyle(isAvailable ? ColorTokens.action : ColorTokens.textSecondary)
                    .padding(.horizontal, SpacingTokens.sm)
                    .padding(.vertical, SpacingTokens.xs)
                    .background(isAvailable ? ColorTokens.actionSoft : ColorTokens.surface)
                    .clipShape(Capsule())
                }
            }

            Text(LocalizedStringKey("gamification.streak.status.freeze_protected.body"))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
        }
        .padding(SpacingTokens.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.surfaceElevated)
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                .strokeBorder(ColorTokens.action.opacity(0.35), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("gamification.freezeStatusCard")
    }

    private var recentLedgerSection: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text(LocalizedStringKey("gamification.sheet.section.ledger"))
                .font(TypographyTokens.body.weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)

            if summary.recentDailyLedger.isEmpty {
                Text(LocalizedStringKey("gamification.streak.status.fresh_start_ready.body"))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .padding(SpacingTokens.md)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(ColorTokens.surface)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
            } else {
                VStack(spacing: SpacingTokens.xs) {
                    ForEach(summary.recentDailyLedger) { entry in
                        HStack(spacing: SpacingTokens.sm) {
                            Image(systemName: ledgerIcon(for: entry.dailyStatus))
                                .foregroundStyle(ledgerTint(for: entry.dailyStatus))
                                .frame(width: 22)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(entry.activityLocalDate)
                                    .font(TypographyTokens.caption.weight(.semibold))
                                    .foregroundStyle(ColorTokens.textPrimary)
                                    .monospacedDigit()
                                Text(ledgerSubtitle(for: entry.dailyStatus))
                                    .font(TypographyTokens.caption)
                                    .foregroundStyle(ColorTokens.textSecondary)
                            }

                            Spacer()

                            if entry.streakIncremented {
                                Text("+\(entry.streakDaysSnapshot)")
                                    .font(TypographyTokens.caption.weight(.bold))
                                    .foregroundStyle(ColorTokens.success)
                                    .monospacedDigit()
                            } else if entry.freezeConsumed {
                                Image(systemName: "shield.fill")
                                    .font(.caption)
                                    .foregroundStyle(ColorTokens.action)
                            } else {
                                Text("Continuity")
                                    .font(.caption2.weight(.medium))
                                    .foregroundStyle(ColorTokens.action)
                            }
                        }
                        .padding(.horizontal, SpacingTokens.sm + 4)
                        .padding(.vertical, SpacingTokens.sm)
                        .background(ColorTokens.surface)
                        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
                    }
                }
            }
        }
        .accessibilityIdentifier("gamification.recentLedgerSection")
    }

    private var achievementsSection: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Text(LocalizedStringKey("gamification.sheet.section.achievements"))
                .font(TypographyTokens.body.weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)

            LazyVStack(spacing: SpacingTokens.sm) {
                ForEach(summary.achievements) { item in
                    HStack(alignment: .top, spacing: SpacingTokens.sm + 4) {
                        ZStack {
                            Circle()
                                .fill(item.unlocked ? ColorTokens.success.opacity(0.16) : ColorTokens.background)
                            Image(systemName: item.unlocked ? "checkmark.seal.fill" : "seal")
                                .foregroundStyle(item.unlocked ? ColorTokens.success : ColorTokens.textTertiary)
                        }
                        .frame(width: badgeIconDimension, height: badgeIconDimension)

                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(LocalizedStringKey(item.titleKey))
                                    .font(TypographyTokens.body.weight(.semibold))
                                    .foregroundStyle(ColorTokens.textPrimary)
                                Spacer()
                                if let unlockedDate = item.unlockedLocalDate, item.unlocked {
                                    Text(unlockedDate)
                                        .font(TypographyTokens.caption)
                                        .foregroundStyle(ColorTokens.success)
                                        .monospacedDigit()
                                }
                            }
                            Text(LocalizedStringKey(item.descriptionKey))
                                .font(TypographyTokens.caption)
                                .foregroundStyle(ColorTokens.textSecondary)
                        }
                    }
                    .padding(SpacingTokens.sm + 4)
                    .background(ColorTokens.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.medium)
                            .strokeBorder(item.unlocked ? ColorTokens.success.opacity(0.35) : ColorTokens.border, lineWidth: 1)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
                    .opacity(item.unlocked ? 1.0 : 0.78)
                    .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: item.unlocked)
                }
            }
        }
        .accessibilityIdentifier("gamification.achievementsSection")
    }

    private var freezeRemainingText: String {
        let format = NSLocalizedString("gamification.sheet.freezes_remaining_format", comment: "")
        let resolvedFormat = format == "gamification.sheet.freezes_remaining_format"
            ? "%lld of %lld monthly rest freezes available"
            : format
        return String.localizedStringWithFormat(
            resolvedFormat,
            Int64(summary.streakFreezesRemaining),
            Int64(summary.maxMonthlyStreakFreezes)
        )
    }

    private func ledgerIcon(for status: DailyStreakLedgerStatus) -> String {
        switch status {
        case .verifiedIndependent:
            return "checkmark.seal.fill"
        case .assistedContinuity:
            return "arrow.triangle.2.circlepath.circle.fill"
        case .freezeApplied:
            return "shield.lefthalf.filled"
        }
    }

    private func ledgerTint(for status: DailyStreakLedgerStatus) -> Color {
        switch status {
        case .verifiedIndependent:
            return ColorTokens.success
        case .assistedContinuity:
            return ColorTokens.action
        case .freezeApplied:
            return ColorTokens.action
        }
    }

    private func ledgerSubtitle(for status: DailyStreakLedgerStatus) -> LocalizedStringKey {
        switch status {
        case .verifiedIndependent:
            return LocalizedStringKey("gamification.streak.status.active_verified_today.headline")
        case .assistedContinuity:
            return LocalizedStringKey("gamification.streak.status.continuity_preserved_today.headline")
        case .freezeApplied:
            return LocalizedStringKey("gamification.streak.status.freeze_protected.headline")
        }
    }
}
