import Foundation
import UserNotifications

final class PushPermissionManager: PushPermissionManaging, @unchecked Sendable {
    func authorizationStatus() async -> OsNotificationPermissionStatus {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        return Self.mapStatus(settings.authorizationStatus)
    }

    func requestAuthorization() async throws -> OsNotificationPermissionStatus {
        let center = UNUserNotificationCenter.current()
        _ = try await center.requestAuthorization(options: [.alert, .sound, .badge])
        let settings = await center.notificationSettings()
        return Self.mapStatus(settings.authorizationStatus)
    }

    private static func mapStatus(_ status: UNAuthorizationStatus) -> OsNotificationPermissionStatus {
        switch status {
        case .notDetermined:
            return .notDetermined
        case .denied:
            return .denied
        case .authorized:
            return .authorized
        case .provisional:
            return .provisional
        case .ephemeral:
            return .ephemeral
        @unknown default:
            return .notDetermined
        }
    }
}

#if DEBUG
actor StubPushPermissionManager: PushPermissionManaging {
    private var currentStatus: OsNotificationPermissionStatus
    private var nextAuthorizationStatus: OsNotificationPermissionStatus
    private(set) var requestCount = 0

    init(
        initialStatus: OsNotificationPermissionStatus = .notDetermined,
        nextAuthorizationStatus: OsNotificationPermissionStatus = .authorized
    ) {
        self.currentStatus = initialStatus
        self.nextAuthorizationStatus = nextAuthorizationStatus
    }

    func setStatus(_ status: OsNotificationPermissionStatus) {
        currentStatus = status
    }

    func setNextAuthorizationStatus(_ status: OsNotificationPermissionStatus) {
        nextAuthorizationStatus = status
    }

    func authorizationStatus() async -> OsNotificationPermissionStatus {
        currentStatus
    }

    func requestAuthorization() async throws -> OsNotificationPermissionStatus {
        requestCount += 1
        currentStatus = nextAuthorizationStatus
        return currentStatus
    }
}
#endif
