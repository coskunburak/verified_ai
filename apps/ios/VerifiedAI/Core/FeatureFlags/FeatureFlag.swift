import Foundation

enum FeatureFlag: String, CaseIterable, Sendable, Hashable {
    case platformHealthScreen
    case pdfUploadEnabled
    case mistakeMomentPaywallUI

    var remoteKey: String {
        switch self {
        case .platformHealthScreen:
            return "client_platform_health_screen"
        case .pdfUploadEnabled:
            return "pdf_upload_enabled"
        case .mistakeMomentPaywallUI:
            return "client_mistake_moment_paywall_ui"
        }
    }

    var defaultEnabled: Bool {
        switch self {
        case .platformHealthScreen:
            return false
        case .pdfUploadEnabled:
            return true
        case .mistakeMomentPaywallUI:
            return false
        }
    }

    init?(remoteKey: String) {
        guard let match = Self.allCases.first(where: { $0.remoteKey == remoteKey }) else {
            return nil
        }
        self = match
    }
}

protocol FeatureFlagClient: Sendable {
    func isEnabled(_ flag: FeatureFlag) -> Bool
    func isKillSwitchActive(_ flag: FeatureFlag) -> Bool
    func experimentVariant(for experimentKey: String) -> String?
}

extension FeatureFlagClient {
    func isKillSwitchActive(_ flag: FeatureFlag) -> Bool {
        false
    }

    func experimentVariant(for experimentKey: String) -> String? {
        nil
    }
}

struct StaticFeatureFlagClient: FeatureFlagClient, Sendable {
    let enabledFlags: Set<FeatureFlag>
    let activeKillSwitches: Set<FeatureFlag>
    let experimentVariants: [String: String]

    init(
        enabledFlags: Set<FeatureFlag> = [],
        activeKillSwitches: Set<FeatureFlag> = [],
        experimentVariants: [String: String] = [:]
    ) {
        self.enabledFlags = enabledFlags
        self.activeKillSwitches = activeKillSwitches
        self.experimentVariants = experimentVariants
    }

    func isEnabled(_ flag: FeatureFlag) -> Bool {
        if activeKillSwitches.contains(flag) {
            return false
        }
        return enabledFlags.contains(flag)
    }

    func isKillSwitchActive(_ flag: FeatureFlag) -> Bool {
        activeKillSwitches.contains(flag)
    }

    func experimentVariant(for experimentKey: String) -> String? {
        experimentVariants[experimentKey]
    }
}

