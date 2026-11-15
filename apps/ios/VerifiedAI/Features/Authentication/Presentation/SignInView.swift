import AuthenticationServices
import SwiftUI

struct SignInView: View {
    @Bindable var viewModel: SignInViewModel
    @FocusState private var focusedField: Field?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                header
                authPanel
                guestAccess
            }
            .padding(.horizontal, SpacingTokens.lg)
            .padding(.vertical, SpacingTokens.xxl)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(ColorTokens.canvas.ignoresSafeArea())
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack(spacing: SpacingTokens.compact) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(ColorTokens.actionForeground)
                    .frame(width: 44, height: 44)
                    .background(ColorTokens.action)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text("Verified AI")
                        .font(TypographyTokens.heroTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                        .accessibilityIdentifier("appTitle")
                    Text("Formal Mathematical Tutor")
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                }
            }

            Text("A clean, distraction-free workspace for scanned problems, formal verification, and adaptive exam preparation.")
                .font(TypographyTokens.body)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: SpacingTokens.sm) {
                AppStatusBadge(verbatimTitle: "Verified", systemImage: "checkmark.seal", tone: .action)
                AppStatusBadge(verbatimTitle: "Private", systemImage: "lock.shield", tone: .neutral)
                AppStatusBadge(verbatimTitle: "Offline Ready", systemImage: "icloud.slash", tone: .neutral)
            }
        }
    }

    private var authPanel: some View {
        AppSurfaceCard {
            modePicker

            VStack(spacing: SpacingTokens.sm) {
                AuthTextField(
                    title: "Email",
                    systemImage: "envelope",
                    text: $viewModel.email,
                    contentType: .emailAddress,
                    keyboardType: .emailAddress
                )
                .focused($focusedField, equals: .email)

                AuthSecureField(
                    title: "Password",
                    systemImage: "lock",
                    text: $viewModel.password
                )
                .focused($focusedField, equals: .password)

                if viewModel.mode == .signUp {
                    AuthSecureField(
                        title: "Confirm Password",
                        systemImage: "lock.rotation",
                        text: $viewModel.confirmPassword
                    )
                    .focused($focusedField, equals: .confirmPassword)
                }
            }

            Text(statusText)
                .font(TypographyTokens.caption)
                .foregroundStyle(statusColor)
                .frame(minHeight: 18, alignment: .leading)
                .accessibilityIdentifier("auth.status")

            Button {
                focusedField = nil
                viewModel.submitEmail()
            } label: {
                HStack(spacing: SpacingTokens.sm) {
                    if isBusy {
                        ProgressView()
                            .controlSize(.small)
                            .tint(ColorTokens.actionForeground)
                    }
                    Text(viewModel.mode == .signIn ? "Sign In" : "Create Account")
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(primaryActionDisabled)

            /*
             Sign in with Apple is intentionally disabled while local signing uses a Personal Team.
             Re-enable this block and the com.apple.developer.applesignin entitlement when the app is signed with a paid Apple Developer account.

             SignInWithAppleButton(.signIn) { request in
                 viewModel.configureAppleRequest(request)
             } onCompletion: { result in
                 viewModel.handleAppleCompletion(result)
             }
             .signInWithAppleButtonStyle(.black)
             .frame(height: 52)
             .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
             .disabled(isBusy)
             */
        }
    }

    private var modePicker: some View {
        HStack(spacing: SpacingTokens.xs) {
            ForEach(EmailAuthenticationMode.allCases) { mode in
                let selected = viewModel.mode == mode
                Button {
                    withAnimation(.snappy(duration: 0.2)) {
                        viewModel.mode = mode
                    }
                } label: {
                    Label(mode.title, systemImage: mode == .signIn ? "person.crop.circle" : "person.badge.plus")
                        .font(TypographyTokens.cardTitle)
                        .frame(maxWidth: .infinity)
                        .frame(height: 40)
                        .foregroundStyle(selected ? ColorTokens.textPrimary : ColorTokens.textSecondary)
                }
                .buttonStyle(.plain)
                .background(selected ? ColorTokens.surface : Color.clear)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: RadiusTokens.small, style: .continuous)
                        .stroke(selected ? ColorTokens.borderStrong : Color.clear, lineWidth: 1)
                )
            }
        }
        .padding(SpacingTokens.xs)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
    }

    private var guestAccess: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Button {
                focusedField = nil
                viewModel.continueAsGuest()
            } label: {
                Label("Continue Without Account", systemImage: "arrow.right.circle")
            }
            .buttonStyle(AppSecondaryButtonStyle())
            .disabled(isBusy)

            Text("Guest sessions stay private on this device and can be upgraded anytime.")
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var isBusy: Bool {
        switch viewModel.state {
        case .authorizing, .exchangingCredential, .refreshing:
            return true
        default:
            return false
        }
    }

    private var primaryActionDisabled: Bool {
        viewModel.email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            || viewModel.password.isEmpty
            || (viewModel.mode == .signUp && viewModel.confirmPassword.isEmpty)
            || isBusy
    }

    private var statusText: String {
        if let message = viewModel.message {
            return message
        }

        switch viewModel.state {
        case .unknown:
            return ""
        case .unauthenticated, .cancelled:
            return viewModel.mode == .signIn ? "Sign in to continue." : "Create an account to keep your progress synced."
        case .authorizing:
            return "Authorizing with Apple..."
        case .exchangingCredential:
            return "Creating secure session..."
        case .authenticated:
            return "Signed in."
        case .refreshing:
            return "Refreshing session..."
        case .expired:
            return "Session expired. Sign in again."
        case .offline:
            return "You appear to be offline."
        case .failed:
            return "Authentication failed. Try again."
        }
    }

    private var statusColor: Color {
        switch viewModel.state {
        case .failed, .offline, .expired:
            return ColorTokens.warning
        case .authenticated:
            return ColorTokens.action
        default:
            return ColorTokens.textTertiary
        }
    }

    private enum Field {
        case email
        case password
        case confirmPassword
    }
}

private struct AuthTextField: View {
    let title: String
    let systemImage: String
    @Binding var text: String
    let contentType: UITextContentType?
    let keyboardType: UIKeyboardType

    var body: some View {
        HStack(spacing: SpacingTokens.sm) {
            Image(systemName: systemImage)
                .foregroundStyle(ColorTokens.textTertiary)
                .frame(width: 20)
            TextField(title, text: $text)
                .textContentType(contentType)
                .keyboardType(keyboardType)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .foregroundStyle(ColorTokens.textPrimary)
        }
        .padding(.horizontal, SpacingTokens.md)
        .frame(height: 50)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
    }
}

private struct AuthSecureField: View {
    let title: String
    let systemImage: String
    @Binding var text: String

    var body: some View {
        HStack(spacing: SpacingTokens.sm) {
            Image(systemName: systemImage)
                .foregroundStyle(ColorTokens.textTertiary)
                .frame(width: 20)
            SecureField(title, text: $text)
                .textContentType(.password)
                .foregroundStyle(ColorTokens.textPrimary)
        }
        .padding(.horizontal, SpacingTokens.md)
        .frame(height: 50)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                .stroke(ColorTokens.borderSubtle, lineWidth: 1)
        )
    }
}
