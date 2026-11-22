#if DEBUG
import Foundation

actor FixtureNotificationAPI: NotificationServicing {
    private var preferences: NotificationPreferences
    private var devices: [RegisteredNotificationDevice]
    private var deliveries: [NotificationDeliveryItem]

    init(
        userId: UUID = UUID(uuidString: "00000000-0000-4000-8000-000000000101")!,
        now: Date = Date(timeIntervalSince1970: 1_795_341_600)
    ) {
        self.preferences = NotificationPreferences(
            schemaVersion: "notification-preferences-v1",
            userId: userId,
            pushEnabled: false,
            studyPlanEnabled: false,
            spacedReviewEnabled: false,
            examDeadlineEnabled: false,
            billingTrialReminderEnabled: false,
            accountSecurityEnabled: true,
            quietHoursEnabled: true,
            quietHoursStartLocal: "22:00",
            quietHoursEndLocal: "08:00",
            timezone: "UTC",
            dailyCap: 2,
            version: 0,
            trainingEligible: false,
            updatedAt: now
        )
        self.devices = []
        self.deliveries = []
    }

    func fetchPreferences() async throws -> NotificationPreferences {
        preferences
    }

    func updatePreferences(_ patch: NotificationPreferencePatch) async throws -> NotificationPreferences {
        if patch.expectedVersion != preferences.version {
            throw NetworkError.server(problem: ProblemDetails(
                type: "https://errors.verified-ai-learning.example/optimistic-conflict",
                title: "Notification preferences changed since they were loaded",
                status: 409,
                code: "OPTIMISTIC_CONFLICT",
                traceId: "fixture-conflict",
                details: ProblemDetails.RecoveryDetails(recoverable: true, userAction: "REFRESH")
            ))
        }
        preferences = NotificationPreferences(
            schemaVersion: "notification-preferences-v1",
            userId: preferences.userId,
            pushEnabled: patch.pushEnabled ?? preferences.pushEnabled,
            studyPlanEnabled: patch.studyPlanEnabled ?? preferences.studyPlanEnabled,
            spacedReviewEnabled: patch.spacedReviewEnabled ?? preferences.spacedReviewEnabled,
            examDeadlineEnabled: patch.examDeadlineEnabled ?? preferences.examDeadlineEnabled,
            billingTrialReminderEnabled: patch.billingTrialReminderEnabled ?? preferences.billingTrialReminderEnabled,
            accountSecurityEnabled: true,
            quietHoursEnabled: patch.quietHoursEnabled ?? preferences.quietHoursEnabled,
            quietHoursStartLocal: patch.quietHoursStartLocal ?? preferences.quietHoursStartLocal,
            quietHoursEndLocal: patch.quietHoursEndLocal ?? preferences.quietHoursEndLocal,
            timezone: patch.timezone ?? preferences.timezone,
            dailyCap: patch.dailyCap ?? preferences.dailyCap,
            version: preferences.version + 1,
            trainingEligible: false,
            updatedAt: Date()
        )
        return preferences
    }

    func registerDevice(_ request: NotificationDeviceRegisterRequest) async throws -> RegisteredNotificationDevice {
        let now = Date()
        let device = RegisteredNotificationDevice(
            schemaVersion: "notification-device-v1",
            deviceId: UUID(),
            platform: request.platform,
            environment: request.environment,
            tokenLast4: String(request.deviceToken.suffix(4)),
            osPermissionStatus: request.osPermissionStatus,
            appVersion: request.appVersion,
            locale: request.locale,
            active: true,
            version: 0,
            trainingEligible: false,
            lastSeenAt: now,
            revokedAt: nil,
            updatedAt: now
        )
        devices.insert(device, at: 0)
        return device
    }

    func listActiveDevices() async throws -> [RegisteredNotificationDevice] {
        devices.filter(\.active)
    }

    func revokeDevice(deviceId: UUID) async throws -> RegisteredNotificationDevice {
        guard let index = devices.firstIndex(where: { $0.deviceId == deviceId }) else {
            throw NetworkError.server(problem: ProblemDetails(
                type: "https://errors.verified-ai-learning.example/notification-device-not-found",
                title: "Notification device not found",
                status: 404,
                code: "NOTIFICATION_DEVICE_NOT_FOUND",
                traceId: "fixture-not-found",
                details: ProblemDetails.RecoveryDetails(recoverable: false, userAction: "REFRESH")
            ))
        }
        let existing = devices[index]
        let now = Date()
        let revoked = RegisteredNotificationDevice(
            schemaVersion: existing.schemaVersion,
            deviceId: existing.deviceId,
            platform: existing.platform,
            environment: existing.environment,
            tokenLast4: existing.tokenLast4,
            osPermissionStatus: existing.osPermissionStatus,
            appVersion: existing.appVersion,
            locale: existing.locale,
            active: false,
            version: existing.version + 1,
            trainingEligible: false,
            lastSeenAt: existing.lastSeenAt,
            revokedAt: now,
            updatedAt: now
        )
        devices[index] = revoked
        return revoked
    }

    func listDeliveries(limit: Int) async throws -> [NotificationDeliveryItem] {
        Array(deliveries.prefix(max(1, min(limit, 50))))
    }

    func cancelDelivery(deliveryId: UUID) async throws -> NotificationDeliveryCancelResponse {
        NotificationDeliveryCancelResponse(
            schemaVersion: "notification-delivery-cancel-v1",
            deliveryId: deliveryId,
            status: .cancelled,
            cancelledAt: Date(),
            trainingEligible: false
        )
    }
}
#endif
