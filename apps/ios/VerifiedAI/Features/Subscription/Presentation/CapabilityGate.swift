import SwiftUI

struct CapabilityGate<AllowedContent: View, DeniedContent: View>: View {
    let entitlement: Entitlement?
    let capability: PremiumCapability
    @ViewBuilder let allowed: () -> AllowedContent
    @ViewBuilder let denied: () -> DeniedContent

    var body: some View {
        if entitlement?.allows(capability) == true {
            allowed()
        } else {
            denied()
        }
    }
}

struct UpgradeRequiredView: View {
    let capability: PremiumCapability

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Label(capability.title, systemImage: "lock")
                .foregroundStyle(ColorTokens.textPrimary)
            Text("\(capability.minimumTier.title) required")
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
        }
        .padding(SpacingTokens.md)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
    }
}
