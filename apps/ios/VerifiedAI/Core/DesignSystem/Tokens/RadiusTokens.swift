import Foundation

enum RadiusTokens {
    static let xs: CGFloat = 8
    static let sm: CGFloat = 10
    static let md: CGFloat = 14
    static let lg: CGFloat = 18
    static let xl: CGFloat = 24
    static let pill: CGFloat = 999

    // Preserved aliases for existing callers across modules and standalone gates.
    static let small: CGFloat = sm
    static let medium: CGFloat = md
    static let large: CGFloat = lg
}
