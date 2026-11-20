import SwiftUI

struct PaywallView: View {
    @Bindable var viewModel: PaywallViewModel
    var contextual = false
    var safeToPurchase: @MainActor () -> Bool = { true }
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Subscription")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "xmark").frame(minWidth: 44, minHeight: 44)
                        }
                        .accessibilityLabel("Close")
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            Task { await viewModel.restore() }
                        } label: {
                            Image(systemName: "arrow.clockwise").frame(minWidth: 44, minHeight: 44)
                        }
                        .accessibilityLabel("Restore Purchases")
                        .disabled(viewModel.state.isBusy)
                    }
                }
        }
        .task {
            if !contextual { await viewModel.load(force: true) }
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .idle, .loadingProducts:
            ProgressView()
                .tint(ColorTokens.action)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(ColorTokens.canvas.ignoresSafeArea())
        case .ready, .cancelled, .failed:
            productList
        case .purchasing, .verifyingDeviceTransactions, .submittingToBackend, .refreshingEntitlement:
            VStack(spacing: SpacingTokens.md) {
                ProgressView()
                    .tint(ColorTokens.action)
                if let message = viewModel.message {
                    Text(LocalizedStringKey(message))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(ColorTokens.canvas.ignoresSafeArea())
        case .pending:
            statusView(icon: "hourglass", title: "Purchase pending", actionTitle: "Done") {
                dismiss()
            }
        case .completed:
            statusView(icon: "checkmark.seal", title: "billing.updated", actionTitle: "Done") {
                dismiss()
            }
        case .offline:
            statusView(icon: "wifi.slash", title: viewModel.message ?? "Offline", actionTitle: contextual ? "Done" : "Retry") {
                if contextual { dismiss() } else { Task { await viewModel.load(force: true) } }
            }
        case .empty:
            statusView(icon: "cart", title: contextual ? "billing.priceUnavailable" : "No subscriptions available", actionTitle: contextual ? "Done" : "Retry") {
                if contextual { dismiss() } else { Task { await viewModel.load(force: true) } }
            }
        }
    }

    private var productList: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                if let message = viewModel.message {
                    AppNoticeBanner(LocalizedStringKey(stringLiteral: message), systemImage: "info.circle", tone: .warning)
                }

                ForEach(viewModel.products) { product in
                    AppSurfaceCard {
                        VStack(alignment: .leading) {
                            VStack(alignment: .leading, spacing: 2) {
                                if let copyKey = product.copyKey { Text(LocalizedStringKey(copyKey)) }
                                Text(product.displayName)
                                    .font(TypographyTokens.sectionTitle)
                                    .foregroundStyle(ColorTokens.textPrimary)
                                if let period = product.subscriptionPeriod {
                                    Text(period)
                                        .font(TypographyTokens.caption)
                                        .foregroundStyle(ColorTokens.textSecondary)
                                }
                            }

                            Text(product.displayPrice)
                                .font(TypographyTokens.metricValue)
                                .foregroundStyle(ColorTokens.action)
                        }

                        .accessibilityElement(children: .combine)

                        Text(product.description)
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)

                        if let terms = product.introductoryTerms { Text(terms) }
                        if product.subscriptionPeriod != nil { Text("billing.renewal") }
                        if let termsURL { Link("billing.terms", destination: termsURL).frame(minHeight: 44) }
                        if let privacyURL { Link("billing.privacy", destination: privacyURL).frame(minHeight: 44) }
                        if contextual && (termsURL == nil || privacyURL == nil) { Text("billing.legalUnavailable") }
                        Button {
                            Task { await viewModel.purchase(product, allowed: safeToPurchase) }
                        } label: {
                            Label("Choose \(product.displayName)", systemImage: "cart.badge.plus")
                        }
                        .buttonStyle(AppPrimaryButtonStyle())
                        .accessibilityHint(Text("billing.purchaseHint"))
                        .disabled(viewModel.state.isBusy || !product.supportedTerms || (contextual && (termsURL == nil || privacyURL == nil)))
                    }
                }
            }
            .padding(SpacingTokens.lg)
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
    }

    private var termsURL: URL? { legalURL("BillingTermsURL") }
    private var privacyURL: URL? { legalURL("BillingPrivacyURL") }

    private func legalURL(_ key: String) -> URL? {
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String,
              let url = URL(string: value), url.scheme == "https", url.host != nil else { return nil }
        return url
    }

    private func statusView(icon: String, title: String, actionTitle: String, action: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            AppSurfaceCard {
                Label(LocalizedStringKey(title), systemImage: icon)
                    .font(TypographyTokens.sectionTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                if let message = viewModel.message {
                    Text(LocalizedStringKey(message))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
                Button(LocalizedStringKey(actionTitle), action: action)
                    .buttonStyle(AppPrimaryButtonStyle())
            }
        }
        .padding(SpacingTokens.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(ColorTokens.canvas.ignoresSafeArea())
    }
}
