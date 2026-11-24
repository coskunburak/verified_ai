import SwiftUI

struct SharedArtifactPreviewSheet: View {
    @Bindable var viewModel: GrowthSharingViewModel
    let isAuthenticated: Bool
    var onDismiss: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                    if let error = viewModel.publicPreviewErrorMessage {
                        AppNoticeBanner(
                            LocalizedStringKey(stringLiteral: error),
                            systemImage: "exclamationmark.triangle",
                            tone: .warning
                        )
                        .accessibilityIdentifier("growth.publicPreview.errorBanner")
                    } else if let preview = viewModel.publicPreview {
                        AppSurfaceCard {
                            AppSectionHeader(
                                LocalizedStringKey(preview.artifactType.localizationKey),
                                subtitle: preview.displayAlias.map { LocalizedStringKey(stringLiteral: $0) }
                            ) {
                                AppStatusBadge(
                                    LocalizedStringKey("growth.share.redacted_badge"),
                                    systemImage: "checkmark.shield",
                                    tone: .success
                                )
                            }

                            ForEach(preview.summaryPayload.keys.sorted(), id: \.self) { key in
                                if let value = preview.summaryPayload[key] {
                                    HStack(alignment: .top, spacing: SpacingTokens.md) {
                                        Text(key)
                                            .font(TypographyTokens.caption)
                                            .foregroundStyle(ColorTokens.textSecondary)
                                            .frame(width: 120, alignment: .leading)
                                        Text(value.displayValue)
                                            .font(TypographyTokens.monoCode)
                                            .foregroundStyle(ColorTokens.textPrimary)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                }
                            }

                            if isAuthenticated, let code = preview.referralCode {
                                Button {
                                    Task {
                                        await viewModel.attributeReferral(code: code, source: .shareCard)
                                    }
                                } label: {
                                    Label(LocalizedStringKey("growth.share.preview.accept_invite"), systemImage: "person.badge.plus")
                                }
                                .buttonStyle(AppPrimaryButtonStyle())
                                .disabled(viewModel.isBusy)
                                .accessibilityIdentifier("growth.publicPreview.acceptInviteButton")
                            }
                        }
                    } else {
                        ProgressView()
                            .tint(ColorTokens.action)
                            .frame(maxWidth: .infinity, alignment: .center)
                    }
                }
                .padding(SpacingTokens.lg)
            }
            .background(ColorTokens.canvas.ignoresSafeArea())
            .navigationTitle(LocalizedStringKey("growth.share.preview.title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(LocalizedStringKey("growth.share.preview.done")) {
                        viewModel.dismissPublicPreview()
                        onDismiss()
                    }
                    .accessibilityIdentifier("growth.publicPreview.doneButton")
                }
            }
        }
    }
}
