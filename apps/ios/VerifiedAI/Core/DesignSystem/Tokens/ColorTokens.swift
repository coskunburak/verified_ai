import SwiftUI
import UIKit

/// Semantic system colors follow light/dark appearance and increased contrast.
/// Correctness always has an accompanying text label or symbol.
enum ColorTokens {
    static let background = Color(uiColor: .systemGroupedBackground)
    static let surface = Color(uiColor: .secondarySystemGroupedBackground)
    static let surfaceElevated = Color(uiColor: .tertiarySystemGroupedBackground)
    static let border = Color(uiColor: .separator)
    static let textPrimary = Color(uiColor: .label)
    static let textSecondary = Color(uiColor: .secondaryLabel)
    static let textTertiary = Color(uiColor: .secondaryLabel)
    static let action = Color(uiColor: UIColor { traits in
        if traits.accessibilityContrast == .high { return traits.userInterfaceStyle == .dark ? .white : .black }
        return traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.17, green: 0.77, blue: 0.66, alpha: 1)
            : UIColor(red: 0.00, green: 0.36, blue: 0.29, alpha: 1)
    })
    static let accent = semantic(light: (0.45, 0.25, 0.00), dark: (1.00, 0.75, 0.35))
    static let warning = semantic(light: (0.70, 0.10, 0.08), dark: (1.00, 0.55, 0.50))
    static let success = semantic(light: (0.12, 0.40, 0.18), dark: (0.45, 0.90, 0.55))

    private static func semantic(light: (CGFloat, CGFloat, CGFloat), dark: (CGFloat, CGFloat, CGFloat)) -> Color {
        Color(uiColor: UIColor { traits in
            if traits.accessibilityContrast == .high {
                // Text/symbols retain the state meaning when contrast takes priority over hue.
                return traits.userInterfaceStyle == .dark ? .white : .black
            }
            let rgb = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: rgb.0, green: rgb.1, blue: rgb.2, alpha: 1)
        })
    }
}
