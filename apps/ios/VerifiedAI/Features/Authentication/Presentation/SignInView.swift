import AuthenticationServices
import SwiftUI

struct SignInView: View {
    @Bindable var viewModel: SignInViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.lg) {
            Spacer()

            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Text("Verified AI")
                    .font(.system(size: 34, weight: .semibold, design: .rounded))
                    .foregroundStyle(ColorTokens.textPrimary)

                Text(statusText)
                    .font(.body)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .frame(minHeight: 44, alignment: .leading)
            }

            SignInWithAppleButton(.signIn) { request in
                viewModel.configureAppleRequest(request)
            } onCompletion: { result in
                viewModel.handleAppleCompletion(result)
            }
            .signInWithAppleButtonStyle(.black)
            .frame(height: 52)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium))
            .disabled(isBusy)

            Spacer()
        }
        .padding(SpacingTokens.lg)
        .background(ColorTokens.background.ignoresSafeArea())
    }

    private var isBusy: Bool {
        switch viewModel.state {
        case .authorizing, .exchangingCredential, .refreshing:
            return true
        default:
            return false
        }
    }

    private var statusText: String {
        switch viewModel.state {
        case .unknown:
            return ""
        case .unauthenticated, .cancelled:
            return "Sign in to continue."
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
            return "Sign in failed. Try again."
        }
    }
}
