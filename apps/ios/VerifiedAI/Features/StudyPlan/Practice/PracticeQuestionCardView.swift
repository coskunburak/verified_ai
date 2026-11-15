import SwiftUI

public struct PracticeQuestionCardView: View {
    public let item: PracticeSessionItem
    public let remainingLockSeconds: Int
    public let isLockActive: Bool
    public let urgencyPhase: VerificationUrgencyPhase
    public let onHintRequested: () -> Void

    public init(
        item: PracticeSessionItem,
        remainingLockSeconds: Int,
        isLockActive: Bool,
        urgencyPhase: VerificationUrgencyPhase,
        onHintRequested: @escaping () -> Void
    ) {
        self.item = item
        self.remainingLockSeconds = remainingLockSeconds
        self.isLockActive = isLockActive
        self.urgencyPhase = urgencyPhase
        self.onHintRequested = onHintRequested
    }

    public var body: some View {
        AppSurfaceCard {
            HStack {
                AppStatusBadge(LocalizedStringKey(item.slotRole.localizedTitleKey), tone: slotBadgeTone)

                Spacer()

                Text(String(format: "ZPD: %.2f - %.2f", item.zpdWindow.lowerBound, item.zpdWindow.upperBound))
                    .font(TypographyTokens.caption.monospacedDigit())
                    .foregroundStyle(ColorTokens.textSecondary)
            }

            if item.isTwinVerification {
                HStack(spacing: SpacingTokens.compact) {
                    ZStack {
                        Circle()
                            .stroke(ColorTokens.borderStrong, lineWidth: 3)
                            .frame(width: 36, height: 36)

                        if isLockActive {
                            Circle()
                                .trim(from: 0, to: CGFloat(remainingLockSeconds) / 60.0)
                                .stroke(timerColor, lineWidth: 3)
                                .frame(width: 36, height: 36)
                                .rotationEffect(.degrees(-90))

                            Text("\(remainingLockSeconds)")
                                .font(.system(size: 13, weight: .bold, design: .monospaced))
                                .monospacedDigit()
                                .foregroundStyle(timerColor)
                        } else {
                            Image(systemName: "checkmark")
                                .font(TypographyTokens.caption.bold())
                                .foregroundStyle(ColorTokens.success)
                        }
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text(isLockActive ? LocalizedStringKey("practice.lock.active") : LocalizedStringKey("practice.lock.unlocked"))
                            .font(TypographyTokens.cardTitle)
                            .foregroundStyle(isLockActive ? ColorTokens.textPrimary : ColorTokens.textSecondary)

                        if isLockActive {
                            Text("Independent reasoning window")
                                .font(TypographyTokens.microLabel)
                                .foregroundStyle(ColorTokens.textSecondary)
                        }
                    }

                    Spacer()

                    Button(action: onHintRequested) {
                        HStack(spacing: 4) {
                            Image(systemName: isLockActive ? "lock.fill" : "lightbulb.fill")
                            Text(LocalizedStringKey("practice.action.hint"))
                        }
                        .font(TypographyTokens.caption.bold())
                        .padding(.horizontal, SpacingTokens.compact)
                        .padding(.vertical, 6)
                        .background(isLockActive ? ColorTokens.surfacePressed : ColorTokens.warningSoft)
                        .foregroundStyle(isLockActive ? ColorTokens.textSecondary : ColorTokens.warning)
                        .clipShape(Capsule())
                    }
                    .disabled(isLockActive)
                }
                .padding(SpacingTokens.compact)
                .background(ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                        .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                )
            }

            Text(item.promptInstructionEn)
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textSecondary)

            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                NativeMathLabel(
                    latex: item.promptLatex,
                    fallback: item.promptLatex,
                    pointSize: 20
                )
                .frame(minHeight: 50)
                .padding(.vertical, SpacingTokens.xs)
            }
            .padding(SpacingTokens.compact)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
            )
        }
    }

    private var slotBadgeTone: AppBadgeTone {
        switch item.slotRole {
        case .trapClosure: return .warning
        case .spacedDrill: return .action
        case .examSynthesis: return .success
        }
    }

    private var timerColor: Color {
        switch urgencyPhase {
        case .calm: return ColorTokens.action
        case .focused: return ColorTokens.warning
        case .finalCountdown: return ColorTokens.danger
        case .unlocked: return ColorTokens.success
        }
    }
}
