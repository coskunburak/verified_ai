import SwiftUI

enum TypographyTokens {
    static let heroTitle = Font.system(size: 28, weight: .bold, design: .default)
    static let screenTitle = Font.title2.weight(.bold)
    static let title = screenTitle
    static let sectionTitle = Font.headline.weight(.semibold)
    static let cardTitle = Font.subheadline.weight(.semibold)
    static let body = Font.body
    static let bodyMedium = Font.subheadline.weight(.medium)
    static let caption = Font.caption
    static let microLabel = Font.caption2.weight(.semibold)
    static let badge = Font.caption2.weight(.bold)
    static let metricValue = Font.title3.weight(.bold).monospacedDigit()
    static let metricLarge = Font.system(size: 26, weight: .bold, design: .default).monospacedDigit()
    static let stepNumber = Font.caption.weight(.bold).monospacedDigit()
    static let formula = Font.system(.callout, design: .monospaced).weight(.medium)
    static let formulaLarge = Font.system(.title3, design: .monospaced).weight(.semibold)
    static let monoCode = Font.system(.caption, design: .monospaced)
}
