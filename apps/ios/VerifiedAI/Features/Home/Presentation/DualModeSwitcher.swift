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
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
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
            .padding(4)
            .background(Color(UIColor.tertiarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12))

            Text(LocalizedStringKey(subtitleKey))
                .font(.caption)
                .foregroundColor(.secondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(14)
        .background(Color(UIColor.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
                .font(.caption.weight(.semibold))
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 44)
                .padding(.horizontal, 8)
                .foregroundColor(isSelected ? .white : .primary)
                .background(isSelected ? Color.accentColor : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(fallbackTitle))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
        .accessibilityIdentifier(accessibilityId)
    }
}
