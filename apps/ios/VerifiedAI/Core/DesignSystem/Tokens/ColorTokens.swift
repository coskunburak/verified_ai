import SwiftUI
import UIKit

/// User-selectable appearance mode. Concept B ("Photomath Editorial Crisp") defaults cleanly to Light Editorial Paper while supporting Carbon Crisp Dark and System.
enum AppThemePreference: String, CaseIterable, Identifiable {
    case light
    case dark
    case system

    var id: String { rawValue }

    var title: String {
        switch self {
        case .light: return "Light"
        case .dark: return "Dark"
        case .system: return "System"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .light: return .light
        case .dark: return .dark
        case .system: return nil
        }
    }
}

/// Design System 2.1 — Concept B ("Photomath Editorial Crisp").
/// Zero-gradient flat tonal layering with high-contrast Editorial Paper / Carbon Crisp surfaces,
/// 1pt hairline borders, Signal Crimson actions, and Royal Blue math step focus.
enum ColorTokens {
    static let canvas = dynamic(
        light: (0xF4 / 255.0, 0xF5 / 255.0, 0xF7 / 255.0),
        dark: (0x12 / 255.0, 0x13 / 255.0, 0x16 / 255.0)
    )
    static let background = canvas
    static let surface = dynamic(
        light: (1.0, 1.0, 1.0),
        dark: (0x1A / 255.0, 0x1C / 255.0, 0x20 / 255.0)
    )
    static let surfaceElevated = dynamic(
        light: (0xEB / 255.0, 0xED / 255.0, 0xF1 / 255.0),
        dark: (0x23 / 255.0, 0x26 / 255.0, 0x2C / 255.0)
    )
    static let surfacePressed = dynamic(
        light: (0xE1 / 255.0, 0xE4 / 255.0, 0xEA / 255.0),
        dark: (0x2C / 255.0, 0x30 / 255.0, 0x38 / 255.0)
    )

    static let borderSubtle = Color(uiColor: UIColor { traits in
        if traits.accessibilityContrast == .high {
            return traits.userInterfaceStyle == .dark ? UIColor.white.withAlphaComponent(0.32) : UIColor.black.withAlphaComponent(0.32)
        }
        return traits.userInterfaceStyle == .dark
            ? UIColor.white.withAlphaComponent(0.09)
            : UIColor(red: 13.0 / 255.0, green: 15.0 / 255.0, blue: 18.0 / 255.0, alpha: 0.08)
    })
    static let border = borderSubtle
    static let borderStrong = Color(uiColor: UIColor { traits in
        if traits.accessibilityContrast == .high {
            return traits.userInterfaceStyle == .dark ? UIColor.white.withAlphaComponent(0.45) : UIColor.black.withAlphaComponent(0.45)
        }
        return traits.userInterfaceStyle == .dark
            ? UIColor.white.withAlphaComponent(0.18)
            : UIColor(red: 13.0 / 255.0, green: 15.0 / 255.0, blue: 18.0 / 255.0, alpha: 0.18)
    })

    static let textPrimary = dynamic(
        light: (0x0D / 255.0, 0x0F / 255.0, 0x12 / 255.0),
        dark: (0xF5 / 255.0, 0xF6 / 255.0, 0xF8 / 255.0)
    )
    static let textSecondary = dynamic(
        light: (0x52 / 255.0, 0x58 / 255.0, 0x66 / 255.0),
        dark: (0xA0 / 255.0, 0xA6 / 255.0, 0xB2 / 255.0)
    )
    static let textTertiary = dynamic(
        light: (0x77 / 255.0, 0x7E / 255.0, 0x8E / 255.0),
        dark: (0x70 / 255.0, 0x77 / 255.0, 0x85 / 255.0)
    )

    /// Primary brand CTA / camera shutter / active tab: Signal Crimson (Light) / Coral Rose (Dark).
    static let action = semantic(
        light: (0xE1 / 255.0, 0x1D / 255.0, 0x48 / 255.0),
        dark: (0xFB / 255.0, 0x71 / 255.0, 0x85 / 255.0)
    )
    static let actionForeground = dynamic(
        light: (1.0, 1.0, 1.0),
        dark: (0x1F / 255.0, 0x07 / 255.0, 0x0E / 255.0)
    )
    static let actionSoft = dynamicAlpha(
        light: (0xE1 / 255.0, 0x1D / 255.0, 0x48 / 255.0, 0.10),
        dark: (0xFB / 255.0, 0x71 / 255.0, 0x85 / 255.0, 0.15)
    )

    /// Math expression, equation box, and step focus highlight: Royal Blue (Light) / Crisp Sky Blue (Dark).
    static let stepHighlight = semantic(
        light: (0x1D / 255.0, 0x4E / 255.0, 0xD8 / 255.0),
        dark: (0x60 / 255.0, 0xA5 / 255.0, 0xFA / 255.0)
    )
    static let stepHighlightSoft = dynamicAlpha(
        light: (0x1D / 255.0, 0x4E / 255.0, 0xD8 / 255.0, 0.10),
        dark: (0x60 / 255.0, 0xA5 / 255.0, 0xFA / 255.0, 0.14)
    )

    /// Backwards-compatible accent alias mapped cleanly to Royal Blue math step focus.
    static let accent = stepHighlight
    static let accentSoft = stepHighlightSoft

    static let success = semantic(
        light: (0x15 / 255.0, 0x80 / 255.0, 0x3D / 255.0),
        dark: (0x4A / 255.0, 0xDE / 255.0, 0x80 / 255.0)
    )
    static let successSoft = dynamicAlpha(
        light: (0x15 / 255.0, 0x80 / 255.0, 0x3D / 255.0, 0.12),
        dark: (0x4A / 255.0, 0xDE / 255.0, 0x80 / 255.0, 0.14)
    )

    static let warning = semantic(
        light: (0xCA / 255.0, 0x8A / 255.0, 0x04 / 255.0),
        dark: (0xFA / 255.0, 0xCC / 255.0, 0x15 / 255.0)
    )
    static let warningSoft = dynamicAlpha(
        light: (0xCA / 255.0, 0x8A / 255.0, 0x04 / 255.0, 0.12),
        dark: (0xFA / 255.0, 0xCC / 255.0, 0x15 / 255.0, 0.14)
    )

    static let danger = semantic(
        light: (0xBE / 255.0, 0x12 / 255.0, 0x3C / 255.0),
        dark: (0xFB / 255.0, 0x71 / 255.0, 0x85 / 255.0)
    )
    static let dangerSoft = dynamicAlpha(
        light: (0xBE / 255.0, 0x12 / 255.0, 0x3C / 255.0, 0.12),
        dark: (0xFB / 255.0, 0x71 / 255.0, 0x85 / 255.0, 0.15)
    )

    private static func dynamic(light: (CGFloat, CGFloat, CGFloat), dark: (CGFloat, CGFloat, CGFloat)) -> Color {
        Color(uiColor: UIColor { traits in
            let rgb = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: rgb.0, green: rgb.1, blue: rgb.2, alpha: 1)
        })
    }

    private static func dynamicAlpha(
        light: (CGFloat, CGFloat, CGFloat, CGFloat),
        dark: (CGFloat, CGFloat, CGFloat, CGFloat)
    ) -> Color {
        Color(uiColor: UIColor { traits in
            let rgba = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: rgba.0, green: rgba.1, blue: rgba.2, alpha: rgba.3)
        })
    }

    private static func semantic(light: (CGFloat, CGFloat, CGFloat), dark: (CGFloat, CGFloat, CGFloat)) -> Color {
        Color(uiColor: UIColor { traits in
            if traits.accessibilityContrast == .high {
                return traits.userInterfaceStyle == .dark ? .white : .black
            }
            let rgb = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: rgb.0, green: rgb.1, blue: rgb.2, alpha: 1)
        })
    }
}
