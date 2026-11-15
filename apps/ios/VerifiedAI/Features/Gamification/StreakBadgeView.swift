import SwiftUI

public struct StreakBadgeView: View {
    public let streakDays: Int
    public let freezesRemaining: Int
    public let streakStatus: GamificationStreakStatus
    public let onTap: () -> Void

    @ScaledMetric(relativeTo: .caption) private var symbolSize: CGFloat = 14
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(
        streakDays: Int,
        freezesRemaining: Int,
        streakStatus: GamificationStreakStatus,
        onTap: @escaping () -> Void = {}
    ) {
        self.streakDays = streakDays
        self.freezesRemaining = freezesRemaining
        self.streakStatus = streakStatus
        self.onTap = onTap
    }

    public var body: some View {
        Button(action: onTap) {
            HStack(spacing: SpacingTokens.xs) {
                Image(systemName: statusSymbolName)
                    .font(.system(size: symbolSize, weight: .semibold))
                    .foregroundStyle(statusForegroundColor)

                Text(daysLabelText)
                    .font(TypographyTokens.caption.weight(.bold))
                    .foregroundStyle(statusForegroundColor)
                    .monospacedDigit()

                if freezesRemaining > 0 {
                    HStack(spacing: 2) {
                        Image(systemName: "shield.lefthalf.filled")
                            .font(.system(size: max(10, symbolSize - 3), weight: .medium))
                        Text("\(freezesRemaining)")
                            .font(.caption2.weight(.semibold))
                            .monospacedDigit()
                    }
                    .foregroundStyle(ColorTokens.textSecondary)
                    .padding(.leading, 2)
                }
            }
            .padding(.horizontal, SpacingTokens.sm + 4)
            .padding(.vertical, SpacingTokens.sm)
            .background(statusBackgroundColor)
            .overlay(
                Capsule()
                    .strokeBorder(statusBorderColor, lineWidth: 1)
            )
            .clipShape(Capsule())
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: streakStatus)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text(LocalizedStringKey("gamification.streak.badge.accessibility_label")))
        .accessibilityValue(accessibilityValueText)
        .accessibilityHint(Text(LocalizedStringKey("gamification.streak.badge.accessibility_hint")))
        .accessibilityIdentifier("gamification.streakBadgeButton")
    }

    private var daysLabelText: String {
        let format = NSLocalizedString("gamification.streak.badge.days_format", comment: "")
        let resolvedFormat = format == "gamification.streak.badge.days_format" ? "%lld d" : format
        return String.localizedStringWithFormat(resolvedFormat, Int64(streakDays))
    }

    private var accessibilityValueText: Text {
        Text("\(daysLabelText), \(freezesRemaining) ") + Text(LocalizedStringKey(streakStatus.supportiveHeadlineKey))
    }

    private var statusSymbolName: String {
        switch streakStatus {
        case .activeVerifiedToday:
            return "flame.fill"
        case .continuityPreservedToday:
            return "arrow.triangle.2.circlepath.circle.fill"
        case .freezeProtected:
            return "shield.lefthalf.filled"
        case .atRiskToday, .freshStartReady:
            return "flame"
        }
    }

    private var statusForegroundColor: Color {
        switch streakStatus {
        case .activeVerifiedToday:
            return ColorTokens.warning
        case .continuityPreservedToday:
            return ColorTokens.action
        case .freezeProtected:
            return ColorTokens.action
        case .atRiskToday, .freshStartReady:
            return ColorTokens.textSecondary
        }
    }

    private var statusBackgroundColor: Color {
        switch streakStatus {
        case .activeVerifiedToday:
            return ColorTokens.warningSoft
        case .continuityPreservedToday:
            return ColorTokens.actionSoft
        case .freezeProtected:
            return ColorTokens.actionSoft
        case .atRiskToday, .freshStartReady:
            return ColorTokens.surface
        }
    }

    private var statusBorderColor: Color {
        switch streakStatus {
        case .activeVerifiedToday:
            return ColorTokens.warning.opacity(0.28)
        case .continuityPreservedToday:
            return ColorTokens.action.opacity(0.25)
        case .freezeProtected:
            return ColorTokens.action.opacity(0.25)
        case .atRiskToday, .freshStartReady:
            return ColorTokens.borderSubtle
        }
    }
}
