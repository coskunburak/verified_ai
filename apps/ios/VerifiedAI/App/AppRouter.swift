import Foundation
import Observation

enum AppRoute: Equatable, Sendable {
    case home
    case platformHealth
    case notificationPreferences
    case studyPlanToday
    case spacedReview
    case examReadiness
    case subscriptionManage
    case accountSecurity
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
