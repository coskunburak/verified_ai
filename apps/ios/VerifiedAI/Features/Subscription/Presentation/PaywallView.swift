import SwiftUI

struct PaywallView: View {
    @Bindable var viewModel: PaywallViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Subscription")
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "xmark")
                        }
                        .accessibilityLabel("Close")
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            Task { await viewModel.restore() }
                        } label: {
                            Image(systemName: "arrow.clockwise")
                        }
                        .accessibilityLabel("Restore Purchases")
                        .disabled(viewModel.state.isBusy)
                    }
                }
        }
        .task {
            await viewModel.load()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .idle, .loadingProducts:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(ColorTokens.background)
        case .ready, .cancelled, .failed:
            productList
        case .purchasing, .verifyingDeviceTransactions, .submittingToBackend, .refreshingEntitlement:
            VStack(spacing: SpacingTokens.md) {
                ProgressView()
                if let message = viewModel.message {
                    Text(message)
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(ColorTokens.background)
        case .pending:
            statusView(icon: "hourglass", title: "Purchase pending", actionTitle: "Done") {
                dismiss()
            }
        case .completed(let entitlement):
            statusView(icon: "checkmark.seal", title: "\(entitlement.tier.title) active", actionTitle: "Done") {
                dismiss()
            }
        case .offline:
            statusView(icon: "wifi.slash", title: viewModel.message ?? "Offline", actionTitle: "Retry") {
                Task { await viewModel.load(force: true) }
            }
        case .empty:
            statusView(icon: "cart", title: "No subscriptions available", actionTitle: "Retry") {
                Task { await viewModel.load(force: true) }
            }
        }
    }

    private var productList: some View {
        List {
            ForEach(viewModel.products) { product in
                VStack(alignment: .leading, spacing: SpacingTokens.sm) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(product.displayName)
                            .font(TypographyTokens.body.weight(.semibold))
                            .foregroundStyle(ColorTokens.textPrimary)
                        Spacer()
                        Text(product.displayPrice)
                            .font(TypographyTokens.body)
                            .foregroundStyle(ColorTokens.action)
                    }
                    Text(product.description)
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    if let period = product.subscriptionPeriod {
                        Text(period)
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                    }
                    Button {
                        Task { await viewModel.purchase(product) }
                    } label: {
                        Label("Choose \(product.displayName)", systemImage: "cart.badge.plus")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.state.isBusy)
                }
                .padding(.vertical, SpacingTokens.sm)
            }
        }
        .scrollContentBackground(.hidden)
        .background(ColorTokens.background)
        .safeAreaInset(edge: .bottom) {
            if let message = viewModel.message {
                Text(message)
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .padding(SpacingTokens.md)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(ColorTokens.surface)
            }
        }
    }

    private func statusView(icon: String, title: String, actionTitle: String, action: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            Label(title, systemImage: icon)
                .font(TypographyTokens.body.weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)
            if let message = viewModel.message {
                Text(message)
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            }
            Button(actionTitle, action: action)
                .buttonStyle(.borderedProminent)
        }
        .padding(SpacingTokens.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(ColorTokens.background)
    }
}
