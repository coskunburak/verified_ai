import Foundation
import Observation

enum AppRoute: Equatable {
    case home
    case platformHealth
    case notificationPreferences
}

@MainActor
@Observable
final class AppRouter {
    private(set) var path: [AppRoute] = []

    var currentRoute: AppRoute {
        path.last ?? .home
    }

    func navigate(to route: AppRoute) {
        guard currentRoute != route else { return }
        path.append(route)
    }

    func reset(to route: AppRoute = .home) {
        path = route == .home ? [] : [route]
    }
}

