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
            .padding(.vertical, 34)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(ColorTokens.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
            HStack(spacing: SpacingTokens.sm) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 25, weight: .semibold))
                    .foregroundStyle(ColorTokens.action)
                    .frame(width: 44, height: 44)
                    .background(ColorTokens.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))

                Text("Verified AI")
                    .font(.system(size: 34, weight: .semibold, design: .rounded))
                    .foregroundStyle(ColorTokens.textPrimary)
                    .accessibilityIdentifier("appTitle")
            }

            Text("A secure study workspace for scanned problems, verified reasoning, and progress that stays tied to your account.")
                .font(.body)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            HStack(spacing: SpacingTokens.sm) {
                StatusPill(title: "Secure", systemImage: "lock.shield")
                StatusPill(title: "Fast", systemImage: "bolt.fill")
                StatusPill(title: "Private", systemImage: "eye.slash")
            }
        }
    }

    private var authPanel: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.md) {
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
                HStack {
                    if isBusy {
                        ProgressView()
                            .controlSize(.small)
                    }
                    Text(viewModel.mode == .signIn ? "Sign In" : "Create Account")
                        .font(.headline)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 52)
            }
            .buttonStyle(.borderedProminent)
            .tint(ColorTokens.action)
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
        .padding(SpacingTokens.md)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
        .overlay {
            RoundedRectangle(cornerRadius: RadiusTokens.medium)
                .stroke(ColorTokens.border, lineWidth: 1)
        }
    }

    private var modePicker: some View {
        HStack(spacing: SpacingTokens.sm) {
            ForEach(EmailAuthenticationMode.allCases) { mode in
                Button {
                    withAnimation(.snappy(duration: 0.2)) {
                        viewModel.mode = mode
                    }
                } label: {
                    Label(mode.title, systemImage: mode == .signIn ? "person.crop.circle" : "person.badge.plus")
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 42)
                        .foregroundStyle(viewModel.mode == mode ? ColorTokens.background : ColorTokens.textSecondary)
                }
                .buttonStyle(.plain)
                .background(viewModel.mode == mode ? ColorTokens.textPrimary : ColorTokens.surfaceElevated)
                .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
            }
        }
    }

    private var guestAccess: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            Button {
                focusedField = nil
                viewModel.continueAsGuest()
            } label: {
                Label("Continue Without Account", systemImage: "arrow.right.circle")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
            }
            .buttonStyle(.bordered)
            .tint(ColorTokens.textPrimary)
            .disabled(isBusy)

            Text("Guest sessions are private to this device and can be upgraded later by creating an account.")
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
                .frame(width: 22)
            TextField(title, text: $text)
                .textContentType(contentType)
                .keyboardType(keyboardType)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .foregroundStyle(ColorTokens.textPrimary)
        }
        .padding(.horizontal, SpacingTokens.md)
        .frame(height: 52)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
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
                .frame(width: 22)
            SecureField(title, text: $text)
                .textContentType(.password)
                .foregroundStyle(ColorTokens.textPrimary)
        }
        .padding(.horizontal, SpacingTokens.md)
        .frame(height: 52)
        .background(ColorTokens.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
    }
}

private struct StatusPill: View {
    let title: String
    let systemImage: String

    var body: some View {
        Label(title, systemImage: systemImage)
            .font(.caption.weight(.semibold))
            .foregroundStyle(ColorTokens.textSecondary)
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(ColorTokens.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.small))
    }
}
