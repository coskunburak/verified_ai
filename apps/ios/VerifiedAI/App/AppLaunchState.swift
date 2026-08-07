import Foundation

enum AppLaunchState: Equatable {
    case initializing
    case ready
    case degraded(reason: String, recoverable: Bool)
}

