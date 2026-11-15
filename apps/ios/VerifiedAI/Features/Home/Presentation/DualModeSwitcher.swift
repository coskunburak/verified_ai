import SwiftUI

public struct DualModeSwitcher: View {
    public let selectedMode: DailyCoachMode
    public let onSelectMode: (DailyCoachMode) -> Void

    public init(
        selectedMode: DailyCoachMode,
        onSelectMode: @escaping (DailyCoachMode) -> Void
    ) {
        self.selectedMode = selectedMode
        self.onSelectMode = onSelectMode
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.compact) {
            HStack(spacing: SpacingTokens.sm) {
                modeButton(
                    mode: .solverMode,
                    titleKey: "coach.mode.solver.title",
                    fallbackTitle: "⚡ Hızlı Adım Kontrolü",
                    accessibilityId: "coach.mode.solver"
                )
                modeButton(
                    mode: .socraticCoachMode,
                    titleKey: "coach.mode.socratic.title",
                    fallbackTitle: "🎓 Koçluk & Tuzak Avcısı",
                    accessibilityId: "coach.mode.socratic"
                )
            }
            .padding(SpacingTokens.xs)
            .background(ColorTokens.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
            )

            Text(LocalizedStringKey(subtitleKey))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(SpacingTokens.compact)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
    }

    private var subtitleKey: String {
        switch selectedMode {
        case .solverMode:
            return "coach.mode.solver.subtitle"
        case .socraticCoachMode:
            return "coach.mode.socratic.subtitle"
        }
    }

    private func modeButton(
        mode: DailyCoachMode,
        titleKey: String,
        fallbackTitle: String,
        accessibilityId: String
    ) -> some View {
        let isSelected = selectedMode == mode
        return Button {
            onSelectMode(mode)
        } label: {
            Text(LocalizedStringKey(titleKey))
                .font(TypographyTokens.microLabel)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 44)
                .padding(.horizontal, SpacingTokens.sm)
                .foregroundStyle(isSelected ? ColorTokens.actionForeground : ColorTokens.textPrimary)
                .background(isSelected ? ColorTokens.action : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(fallbackTitle))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier(accessibilityId)
    }
}
