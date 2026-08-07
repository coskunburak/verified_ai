import Foundation

enum FeatureFlag: String, CaseIterable {
    case platformHealthScreen
}

protocol FeatureFlagClient {
    func isEnabled(_ flag: FeatureFlag) -> Bool
}

struct StaticFeatureFlagClient: FeatureFlagClient {
    let enabledFlags: Set<FeatureFlag>

    func isEnabled(_ flag: FeatureFlag) -> Bool {
        enabledFlags.contains(flag)
    }
}

