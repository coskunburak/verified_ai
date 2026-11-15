import SwiftUI

struct PaywallView: View {
    @Bindable var viewModel: PaywallViewModel
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
                    Text(message)
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
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                AppSurfaceCard {
                    HStack(spacing: SpacingTokens.compact) {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.title2.weight(.semibold))
                            .foregroundStyle(ColorTokens.action)
                            .frame(width: 44, height: 44)
                            .background(ColorTokens.actionSoft)
                            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Verified AI Pro")
                                .font(TypographyTokens.sectionTitle)
                                .foregroundStyle(ColorTokens.textPrimary)
                            Text("Full formal verification, unlimited tutoring, and exam blueprint drills.")
                                .font(TypographyTokens.caption)
                                .foregroundStyle(ColorTokens.textSecondary)
                        }
                    }
                }

                if let message = viewModel.message {
                    AppNoticeBanner(LocalizedStringKey(stringLiteral: message), systemImage: "info.circle", tone: .warning)
                }

                ForEach(viewModel.products) { product in
                    AppSurfaceCard {
                        HStack(alignment: .firstTextBaseline) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(product.displayName)
                                    .font(TypographyTokens.sectionTitle)
                                    .foregroundStyle(ColorTokens.textPrimary)
                                if let period = product.subscriptionPeriod {
                                    Text(period)
                                        .font(TypographyTokens.caption)
                                        .foregroundStyle(ColorTokens.textSecondary)
                                }
                            }
                            Spacer()
                            Text(product.displayPrice)
                                .font(TypographyTokens.metricValue)
                                .foregroundStyle(ColorTokens.action)
                        }

                        Text(product.description)
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)

                        Button {
                            Task { await viewModel.purchase(product) }
                        } label: {
                            Label("Choose \(product.displayName)", systemImage: "cart.badge.plus")
                        }
                        .buttonStyle(AppPrimaryButtonStyle())
                        .disabled(viewModel.state.isBusy)
                    }
                }
            }
            .padding(SpacingTokens.lg)
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
    }

    private func statusView(icon: String, title: String, actionTitle: String, action: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            AppSurfaceCard {
                Label(title, systemImage: icon)
                    .font(TypographyTokens.sectionTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                if let message = viewModel.message {
                    Text(message)
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
                Button(actionTitle, action: action)
                    .buttonStyle(AppPrimaryButtonStyle())
            }
        }
        .padding(SpacingTokens.lg)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(ColorTokens.canvas.ignoresSafeArea())
    }
}
