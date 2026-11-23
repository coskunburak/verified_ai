import Foundation

protocol NotificationServicing: Sendable {
    func fetchPreferences() async throws -> NotificationPreferences
    func updatePreferences(_ patch: NotificationPreferencePatch) async throws -> NotificationPreferences
    func registerDevice(_ request: NotificationDeviceRegisterRequest) async throws -> RegisteredNotificationDevice
    func listActiveDevices() async throws -> [RegisteredNotificationDevice]
    func revokeDevice(deviceId: UUID) async throws -> RegisteredNotificationDevice
    func listDeliveries(limit: Int) async throws -> [NotificationDeliveryItem]
    func cancelDelivery(deliveryId: UUID) async throws -> NotificationDeliveryCancelResponse
    func fetchLifecycleSummary(limit: Int) async throws -> LifecycleSummaryResponse
}

protocol PushPermissionManaging: Sendable {
    func authorizationStatus() async -> OsNotificationPermissionStatus
    func requestAuthorization() async throws -> OsNotificationPermissionStatus
}
