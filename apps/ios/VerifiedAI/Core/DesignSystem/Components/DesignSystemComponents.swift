import SwiftUI

/// Flat, zero-gradient surface card with 1pt hairline border and tonal elevation.
struct AppSurfaceCard<Content: View>: View {
    let background: Color
    let borderColor: Color
    let cornerRadius: CGFloat
    let padding: CGFloat
    @ViewBuilder let content: () -> Content

    init(
        elevated: Bool = false,
        isSelected: Bool = false,
        cornerRadius: CGFloat = RadiusTokens.large,
        padding: CGFloat = SpacingTokens.md,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.background = elevated ? ColorTokens.surfaceElevated : ColorTokens.surface
        self.borderColor = isSelected ? ColorTokens.action : ColorTokens.borderSubtle
        self.cornerRadius = cornerRadius
        self.padding = padding
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.compact) {
            content()
        }
        .padding(padding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(background)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(borderColor, lineWidth: 1)
        )
    }
}

/// Primary flat CTA button style matching Astra-style high-contrast matte surfaces.
struct AppPrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(TypographyTokens.cardTitle)
            .foregroundStyle(ColorTokens.actionForeground)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 48)
            .padding(.horizontal, SpacingTokens.md)
            .background(isEnabled ? ColorTokens.action : ColorTokens.surfaceElevated)
            .opacity(configuration.isPressed ? 0.86 : (isEnabled ? 1.0 : 0.6))
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
    }
}

/// Secondary flat button with subtle 1pt hairline border.
struct AppSecondaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(TypographyTokens.cardTitle)
            .foregroundStyle(isEnabled ? ColorTokens.textPrimary : ColorTokens.textTertiary)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 44)
            .padding(.horizontal, SpacingTokens.md)
            .background(configuration.isPressed ? ColorTokens.surfacePressed : ColorTokens.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.borderSubtle, lineWidth: 1)
            )
    }
}

enum AppBadgeTone {
    case neutral
    case action
    case stepHighlight
    case success
    case warning
    case danger

    var foreground: Color {
        switch self {
        case .neutral: return ColorTokens.textSecondary
        case .action: return ColorTokens.action
        case .stepHighlight: return ColorTokens.stepHighlight
        case .success: return ColorTokens.success
        case .warning: return ColorTokens.warning
        case .danger: return ColorTokens.danger
        }
    }

    var background: Color {
        switch self {
        case .neutral: return ColorTokens.surfaceElevated
        case .action: return ColorTokens.actionSoft
        case .stepHighlight: return ColorTokens.stepHighlightSoft
        case .success: return ColorTokens.successSoft
        case .warning: return ColorTokens.warningSoft
        case .danger: return ColorTokens.dangerSoft
        }
    }
}

struct AppStatusBadge: View {
    let title: LocalizedStringKey
    let systemImage: String?
    let tone: AppBadgeTone

    init(_ title: LocalizedStringKey, systemImage: String? = nil, tone: AppBadgeTone = .neutral) {
        self.title = title
        self.systemImage = systemImage
        self.tone = tone
    }

    init(verbatimTitle: String, systemImage: String? = nil, tone: AppBadgeTone = .neutral) {
        self.title = LocalizedStringKey(stringLiteral: verbatimTitle)
        self.systemImage = systemImage
        self.tone = tone
    }

    var body: some View {
        HStack(spacing: 5) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(TypographyTokens.microLabel)
            }
            Text(title)
                .font(TypographyTokens.microLabel)
                .lineLimit(1)
        }
        .foregroundStyle(tone.foreground)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(tone.background)
        .clipShape(Capsule())
    }
}

struct AppMetricTile: View {
    let title: String
    let value: String
    let systemImage: String
    var tone: AppBadgeTone = .action

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            HStack(spacing: 6) {
                Image(systemName: systemImage)
                    .font(TypographyTokens.caption.weight(.semibold))
                    .foregroundStyle(tone.foreground)
                Text(LocalizedStringKey(title))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .lineLimit(1)
            }
            Text(value)
                .font(TypographyTokens.metricValue)
                .foregroundStyle(ColorTokens.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
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

struct AppSectionHeader<Trailing: View>: View {
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey?
    @ViewBuilder let trailing: () -> Trailing

    init(
        _ title: LocalizedStringKey,
        subtitle: LocalizedStringKey? = nil,
        @ViewBuilder trailing: @escaping () -> Trailing = { EmptyView() }
    ) {
        self.title = title
        self.subtitle = subtitle
        self.trailing = trailing
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: SpacingTokens.sm) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(TypographyTokens.sectionTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                if let subtitle {
                    Text(subtitle)
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
            }
            Spacer()
            trailing()
        }
    }
}

struct AppNoticeBanner: View {
    let message: LocalizedStringKey
    let systemImage: String
    let tone: AppBadgeTone

    init(_ message: LocalizedStringKey, systemImage: String, tone: AppBadgeTone = .warning) {
        self.message = message
        self.systemImage = systemImage
        self.tone = tone
    }

    var body: some View {
        HStack(alignment: .top, spacing: SpacingTokens.sm) {
            Image(systemName: systemImage)
                .font(TypographyTokens.bodyMedium)
                .foregroundStyle(tone.foreground)
                .padding(.top, 1)
            Text(message)
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(SpacingTokens.compact)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tone.background)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke(tone.foreground.opacity(0.28), lineWidth: 1)
        )
    }
}

struct AppEmptyState: View {
    let systemImage: String
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey?

    init(systemImage: String, title: LocalizedStringKey, subtitle: LocalizedStringKey? = nil) {
        self.systemImage = systemImage
        self.title = title
        self.subtitle = subtitle
    }

    var body: some View {
        VStack(spacing: SpacingTokens.sm) {
            Image(systemName: systemImage)
                .font(.title3.weight(.semibold))
                .foregroundStyle(ColorTokens.action)
                .frame(width: 44, height: 44)
                .background(ColorTokens.actionSoft)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            Text(title)
                .font(TypographyTokens.cardTitle)
                .foregroundStyle(ColorTokens.textPrimary)
                .multilineTextAlignment(.center)
            if let subtitle {
                Text(subtitle)
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(SpacingTokens.lg)
        .frame(maxWidth: .infinity)
    }
}

/// Flat Astra-style conversation bubble for AI Tutor and Learner turns.
struct AppChatBubble<Content: View>: View {
    let isLearner: Bool
    let speakerLabel: LocalizedStringKey
    let footnote: LocalizedStringKey
    @ViewBuilder let content: () -> Content

    init(
        isLearner: Bool,
        speakerLabel: LocalizedStringKey,
        footnote: LocalizedStringKey,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.isLearner = isLearner
        self.speakerLabel = speakerLabel
        self.footnote = footnote
        self.content = content
    }

    var body: some View {
        HStack {
            if isLearner { Spacer(minLength: 32) }
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Image(systemName: isLearner ? "person.fill" : "sparkles")
                        .font(TypographyTokens.microLabel)
                        .foregroundStyle(isLearner ? ColorTokens.textSecondary : ColorTokens.action)
                    Text(speakerLabel)
                        .font(TypographyTokens.microLabel)
                        .foregroundStyle(isLearner ? ColorTokens.textSecondary : ColorTokens.action)
                }

                content()
                    .font(TypographyTokens.body)
                    .foregroundStyle(ColorTokens.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)

                Text(footnote)
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textTertiary)
            }
            .padding(SpacingTokens.compact)
            .frame(maxWidth: .infinity, alignment: isLearner ? .trailing : .leading)
            .background(isLearner ? ColorTokens.surfaceElevated : ColorTokens.surface)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                    .stroke(isLearner ? ColorTokens.borderStrong : ColorTokens.borderSubtle, lineWidth: 1)
            )
            if !isLearner { Spacer(minLength: 20) }
        }
    }
}

/// Zero-gradient segmented control pill built for 4-tab/3-beat switching and Analytics views.
struct AppSegmentedPill<Selection: Hashable>: View {
    struct Option: Identifiable, Hashable {
        let selection: Selection
        let title: String
        let systemImage: String?

        var id: Selection { selection }

        init(selection: Selection, title: String, systemImage: String? = nil) {
            self.selection = selection
            self.title = title
            self.systemImage = systemImage
        }
    }

    @Binding var selection: Selection
    let options: [Option]
    var accessibilityPrefix: String?

    init(
        selection: Binding<Selection>,
        options: [Option],
        accessibilityPrefix: String? = nil
    ) {
        self._selection = selection
        self.options = options
        self.accessibilityPrefix = accessibilityPrefix
    }

    var body: some View {
        HStack(spacing: SpacingTokens.xs) {
            ForEach(options) { option in
                let isSelected = option.selection == selection
                Button {
                    selection = option.selection
                } label: {
                    HStack(spacing: 6) {
                        if let systemImage = option.systemImage {
                            Image(systemName: systemImage)
                                .font(TypographyTokens.caption.weight(.semibold))
                        }
                        Text(LocalizedStringKey(option.title))
                            .font(TypographyTokens.caption.weight(isSelected ? .bold : .medium))
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                    }
                    .foregroundStyle(isSelected ? ColorTokens.textPrimary : ColorTokens.textSecondary)
                    .frame(maxWidth: .infinity, minHeight: 38)
                    .padding(.horizontal, SpacingTokens.sm)
                    .background(isSelected ? ColorTokens.surface : Color.clear)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous)
                            .stroke(isSelected ? ColorTokens.borderStrong : Color.clear, lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
                .contentShape(Rectangle())
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
                .modifier(OptionalAccessibilityIdentifierModifier(identifier: accessibilityPrefix.map { "\($0).\(option.title)" }))
            }
        }
        .padding(4)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
    }
}

private struct OptionalAccessibilityIdentifierModifier: ViewModifier {
    let identifier: String?

    func body(content: Content) -> some View {
        if let identifier {
            content.accessibilityIdentifier(identifier)
        } else {
            content
        }
    }
}

/// Concept B elevated math expression container: `surfaceElevated` + 1pt border + 3pt `stepHighlight` left bar.
struct AppFormulaBox<Content: View>: View {
    let label: String?
    let accentColor: Color
    let background: Color
    @ViewBuilder let content: () -> Content

    init(
        label: String? = nil,
        accentColor: Color = ColorTokens.stepHighlight,
        background: Color = ColorTokens.surfaceElevated,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.label = label
        self.accentColor = accentColor
        self.background = background
        self.content = content
    }

    var body: some View {
        HStack(spacing: 0) {
            Rectangle()
                .fill(accentColor)
                .frame(width: 3)

            VStack(alignment: .leading, spacing: SpacingTokens.xxs) {
                if let label {
                    Text(LocalizedStringKey(label))
                        .font(TypographyTokens.microLabel)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
                content()
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, SpacingTokens.sm)
            .padding(.vertical, SpacingTokens.xs + 2)
        }
        .background(background)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
    }
}

struct AppFormulaText: View {
    let formula: String

    var body: some View {
        Text(formula)
            .font(TypographyTokens.formula)
            .foregroundStyle(ColorTokens.textPrimary)
            .fixedSize(horizontal: false, vertical: true)
    }
}

extension AppFormulaBox where Content == AppFormulaText {
    init(
        formula: String,
        label: String? = nil,
        accentColor: Color = ColorTokens.stepHighlight,
        background: Color = ColorTokens.surfaceElevated
    ) {
        self.init(label: label, accentColor: accentColor, background: background) {
            AppFormulaText(formula: formula)
        }
    }
}

/// Concept B collapsible "Why?" rationale panel highlighted in `stepHighlightSoft`.
struct AppWhyDisclosure<Content: View>: View {
    let title: LocalizedStringKey
    let accentColor: Color
    let background: Color
    @State private var isExpanded: Bool
    @ViewBuilder let content: () -> Content

    init(
        title: LocalizedStringKey = "Why?",
        initiallyExpanded: Bool = false,
        accentColor: Color = ColorTokens.stepHighlight,
        background: Color = ColorTokens.stepHighlightSoft,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.title = title
        self._isExpanded = State(initialValue: initiallyExpanded)
        self.accentColor = accentColor
        self.background = background
        self.content = content
    }

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            Button {
                withAnimation(.easeInOut(duration: 0.18)) {
                    isExpanded.toggle()
                }
            } label: {
                HStack(spacing: SpacingTokens.xxs + 2) {
                    Image(systemName: "questionmark.circle.fill")
                        .font(TypographyTokens.microLabel)
                    Text(title)
                        .font(TypographyTokens.microLabel)
                    Spacer(minLength: 4)
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(TypographyTokens.microLabel)
                }
                .foregroundStyle(accentColor)
                .padding(.horizontal, SpacingTokens.xs + 2)
                .padding(.vertical, 6)
                .background(background)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.xs, style: .continuous))
            }
            .buttonStyle(.plain)

            if isExpanded {
                content()
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(SpacingTokens.sm)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(background)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.sm, style: .continuous)
                            .stroke(accentColor.opacity(0.24), lineWidth: 1)
                    )
            }
        }
    }
}

enum AppStepTimelineState {
    case normal
    case active
    case verified
    case flagged

    var badgeForeground: Color {
        switch self {
        case .normal: return ColorTokens.stepHighlight
        case .active: return ColorTokens.actionForeground
        case .verified: return ColorTokens.success
        case .flagged: return ColorTokens.danger
        }
    }

    var badgeBackground: Color {
        switch self {
        case .normal: return ColorTokens.stepHighlightSoft
        case .active: return ColorTokens.action
        case .verified: return ColorTokens.successSoft
        case .flagged: return ColorTokens.dangerSoft
        }
    }

    var cardBorder: Color {
        switch self {
        case .normal: return ColorTokens.borderSubtle
        case .active: return ColorTokens.borderStrong
        case .verified: return ColorTokens.borderSubtle
        case .flagged: return ColorTokens.danger.opacity(0.45)
        }
    }

    var connectorColor: Color {
        switch self {
        case .active: return ColorTokens.stepHighlight.opacity(0.45)
        case .flagged: return ColorTokens.danger.opacity(0.45)
        case .normal, .verified: return ColorTokens.borderStrong
        }
    }
}

/// Photomath Editorial Crisp vertical timeline step row with numbered circle badge, 1pt connector rail,
/// formula container, and optional collapsible "Why?" drawer.
struct AppStepTimelineRow<BodyContent: View, FooterContent: View>: View {
    let stepNumber: Int
    let title: String
    let isLast: Bool
    let state: AppStepTimelineState
    let badgeText: String?
    let badgeTone: AppBadgeTone
    @ViewBuilder let bodyContent: () -> BodyContent
    @ViewBuilder let footerContent: () -> FooterContent

    init(
        stepNumber: Int,
        title: String,
        isLast: Bool = false,
        state: AppStepTimelineState = .normal,
        badgeText: String? = nil,
        badgeTone: AppBadgeTone = .stepHighlight,
        @ViewBuilder bodyContent: @escaping () -> BodyContent,
        @ViewBuilder footerContent: @escaping () -> FooterContent = { EmptyView() }
    ) {
        self.stepNumber = stepNumber
        self.title = title
        self.isLast = isLast
        self.state = state
        self.badgeText = badgeText
        self.badgeTone = badgeTone
        self.bodyContent = bodyContent
        self.footerContent = footerContent
    }

    var body: some View {
        HStack(alignment: .top, spacing: SpacingTokens.sm) {
            VStack(spacing: 0) {
                Text("\(stepNumber)")
                    .font(TypographyTokens.stepNumber)
                    .foregroundStyle(state.badgeForeground)
                    .frame(width: 28, height: 28)
                    .background(state.badgeBackground)
                    .clipShape(Circle())
                    .overlay(
                        Circle()
                            .stroke(state == .active ? ColorTokens.action : state.badgeForeground.opacity(0.28), lineWidth: 1)
                    )

                if !isLast {
                    Rectangle()
                        .fill(state.connectorColor)
                        .frame(width: 1)
                        .frame(maxHeight: .infinity)
                        .padding(.top, 4)
                }
            }
            .frame(width: 28)

            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                HStack(alignment: .firstTextBaseline, spacing: SpacingTokens.xs) {
                    Text(title)
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                        .fixedSize(horizontal: false, vertical: true)
                    Spacer(minLength: 8)
                    if let badgeText {
                        AppStatusBadge(verbatimTitle: badgeText, tone: badgeTone)
                    }
                }

                bodyContent()
                footerContent()
            }
            .padding(SpacingTokens.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.surface)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.lg, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.lg, style: .continuous)
                    .stroke(state.cardBorder, lineWidth: state == .active || state == .flagged ? 1.5 : 1)
            )
            .padding(.bottom, isLast ? 0 : SpacingTokens.sm)
        }
    }
}

struct DesignSystemComponents_Previews: PreviewProvider {
    static var previews: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                AppSectionHeader("Design System 2.1", subtitle: "Concept B — Photomath Editorial Crisp") {
                    AppStatusBadge(verbatimTitle: "Verified", systemImage: "checkmark.seal.fill", tone: .success)
                }
                AppNoticeBanner("Offline cached workspace active.", systemImage: "wifi.slash", tone: .warning)
                AppSurfaceCard {
                    Text("Editorial Paper & Royal Blue Math Focus")
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                    HStack(spacing: SpacingTokens.sm) {
                        AppMetricTile(title: "Accuracy", value: "92%", systemImage: "scope", tone: .success)
                        AppMetricTile(title: "Time", value: "15m", systemImage: "timer", tone: .stepHighlight)
                    }
                    AppFormulaBox(formula: "2x + 6 = 14  ->  x = 4", label: "Key Step")
                    AppWhyDisclosure(initiallyExpanded: true) {
                        Text("Subtract 6 from both sides first to isolate the 2x term cleanly.")
                    }
                    Button("Solve Next Problem") {}
                        .buttonStyle(AppPrimaryButtonStyle())
                }
                AppStepTimelineRow(stepNumber: 1, title: "Isolate the variable term", isLast: true, state: .active, badgeText: "Verified", badgeTone: .success) {
                    AppFormulaBox(formula: "2x = 8")
                }
            }
            .padding(SpacingTokens.lg)
        }
        .background(ColorTokens.canvas)
    }
}
