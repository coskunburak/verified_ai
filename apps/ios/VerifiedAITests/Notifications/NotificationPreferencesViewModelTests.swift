import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class NotificationPreferencesViewModelTests: XCTestCase {
    func testLoadFetchesDefaultOptOutPreferencesWithoutMutatingCategories() async {
        let service = MockNotificationService()
        let permissions = StubPushPermissionManager(initialStatus: .notDetermined, nextAuthorizationStatus: .authorized)
        let viewModel = makeViewModel(service: service, permissionManager: permissions)

        await viewModel.load()

        XCTAssertEqual(viewModel.state, .ready)
        XCTAssertEqual(viewModel.preferences?.pushEnabled, false)
        XCTAssertEqual(viewModel.draft.pushEnabled, false)
        XCTAssertEqual(viewModel.draft.studyPlanEnabled, false)
        XCTAssertEqual(viewModel.draft.spacedReviewEnabled, false)
        XCTAssertEqual(viewModel.draft.examDeadlineEnabled, false)
        XCTAssertEqual(viewModel.draft.billingTrialReminderEnabled, false)
        XCTAssertEqual(viewModel.preferences?.accountSecurityEnabled, true)
        XCTAssertEqual(viewModel.draft.quietHoursEnabled, true)
        XCTAssertEqual(viewModel.draft.quietHoursStartLocal, "22:00")
        XCTAssertEqual(viewModel.draft.quietHoursEndLocal, "08:00")
        XCTAssertEqual(viewModel.draft.dailyCap, 2)
        XCTAssertEqual(viewModel.preferences?.version, 0)
    }

    func testSaveUpdatesCategoriesQuietHoursAndDailyCap() async {
        let service = MockNotificationService()
        let permissions = StubPushPermissionManager(initialStatus: .authorized, nextAuthorizationStatus: .authorized)
        let viewModel = makeViewModel(service: service, permissionManager: permissions)

        await viewModel.load()
        viewModel.togglePushMaster(true)
        viewModel.draft.studyPlanEnabled = true
        viewModel.draft.billingTrialReminderEnabled = true
        viewModel.setQuietHoursWindow(start: "23:00", end: "07:30")
        viewModel.setDailyCap(4)
        viewModel.draft.timezone = "Europe/Istanbul"

        await viewModel.save()

        XCTAssertEqual(viewModel.state, .ready)
        XCTAssertEqual(viewModel.preferences?.version, 1)
        XCTAssertEqual(viewModel.preferences?.pushEnabled, true)
        XCTAssertEqual(viewModel.preferences?.studyPlanEnabled, true)
        XCTAssertEqual(viewModel.preferences?.billingTrialReminderEnabled, true)
        XCTAssertEqual(viewModel.preferences?.quietHoursStartLocal, "23:00")
        XCTAssertEqual(viewModel.preferences?.quietHoursEndLocal, "07:30")
        XCTAssertEqual(viewModel.preferences?.dailyCap, 4)
        XCTAssertEqual(viewModel.preferences?.timezone, "Europe/Istanbul")
        XCTAssertEqual(viewModel.noticeKey, "notification.preferences.saved_banner")

        let patches = await service.capturedPatches
        XCTAssertEqual(patches.count, 1)
        XCTAssertEqual(patches.first?.expectedVersion, 0)
        XCTAssertEqual(patches.first?.accountSecurityEnabled, true)
        XCTAssertEqual(patches.first?.trainingEligible, false)
    }

    func testOptimisticConflictAutomaticallyReloadsLatestVersionAndAllowsRecovery() async {
        let service = MockNotificationService()
        await service.setSimulateConflictOnce(true)
        let permissions = StubPushPermissionManager(initialStatus: .authorized, nextAuthorizationStatus: .authorized)
        let viewModel = makeViewModel(service: service, permissionManager: permissions)

        await viewModel.load()
        XCTAssertEqual(viewModel.preferences?.version, 0)

        viewModel.togglePushMaster(true)
        viewModel.draft.spacedReviewEnabled = true
        await viewModel.save()

        XCTAssertEqual(viewModel.state, .ready)
        XCTAssertTrue(viewModel.hasPendingConflictRecovery)
        XCTAssertEqual(viewModel.noticeKey, "notification.preferences.error.conflict_reloaded")
        XCTAssertEqual(viewModel.preferences?.version, 1)
        XCTAssertEqual(viewModel.draft.versionSafeCheck(viewModel.preferences), true)

        // Retry with reloaded latest version succeeds
        viewModel.draft.billingTrialReminderEnabled = true
        await viewModel.save()

        XCTAssertFalse(viewModel.hasPendingConflictRecovery)
        XCTAssertEqual(viewModel.preferences?.version, 2)
        XCTAssertEqual(viewModel.preferences?.billingTrialReminderEnabled, true)
        XCTAssertEqual(viewModel.noticeKey, "notification.preferences.saved_banner")
    }

    func testPermissionPrePromptAuthorizedAndDeniedFlowsWithoutSystemDialogs() async {
        let service = MockNotificationService()
        let authStub = StubPushPermissionManager(initialStatus: .notDetermined, nextAuthorizationStatus: .authorized)
        let vmAuthorized = makeViewModel(service: service, permissionManager: authStub)

        await vmAuthorized.load()
        vmAuthorized.togglePushMaster(true)
        XCTAssertTrue(vmAuthorized.showPrePromptBanner)
        XCTAssertFalse(vmAuthorized.draft.pushEnabled)

        await vmAuthorized.confirmPushPrePrompt()
        XCTAssertFalse(vmAuthorized.showPrePromptBanner)
        XCTAssertEqual(vmAuthorized.osPermissionStatus, .authorized)
        XCTAssertTrue(vmAuthorized.draft.pushEnabled)
        let authRequests = await authStub.requestCount
        XCTAssertEqual(authRequests, 1)

        // Denied flow shows calm banner and keeps pushEnabled disabled
        let deniedStub = StubPushPermissionManager(initialStatus: .notDetermined, nextAuthorizationStatus: .denied)
        let vmDenied = makeViewModel(service: service, permissionManager: deniedStub)
        await vmDenied.load()
        vmDenied.togglePushMaster(true)
        await vmDenied.confirmPushPrePrompt()

        XCTAssertEqual(vmDenied.osPermissionStatus, .denied)
        XCTAssertFalse(vmDenied.draft.pushEnabled)
        XCTAssertEqual(vmDenied.noticeKey, "notification.preferences.permission.denied_banner")
    }

    func testOfflineRecoveryAndResetState() async {
        let service = MockNotificationService()
        let network = StubNotificationNetworkMonitor(isReachable: false)
        let permissions = StubPushPermissionManager(initialStatus: .authorized, nextAuthorizationStatus: .authorized)
        let viewModel = makeViewModel(service: service, permissionManager: permissions, networkMonitor: network)

        await viewModel.load()
        XCTAssertEqual(viewModel.state, .offline)
        XCTAssertEqual(viewModel.errorKey, "notification.preferences.error.offline")

        network.isReachable = true
        await viewModel.load()
        XCTAssertEqual(viewModel.state, .ready)
        XCTAssertNil(viewModel.errorKey)

        viewModel.reset()
        XCTAssertEqual(viewModel.state, .idle)
        XCTAssertNil(viewModel.preferences)
        XCTAssertEqual(viewModel.activeDevices, [])
        XCTAssertEqual(viewModel.recentDeliveries, [])
        XCTAssertFalse(viewModel.hasPendingConflictRecovery)
    }

    func testFeatureDisabledMapsToDegradedState() async {
        let service = MockNotificationService()
        await service.setSimulateFeatureDisabled(true)
        let permissions = StubPushPermissionManager(initialStatus: .authorized, nextAuthorizationStatus: .authorized)
        let viewModel = makeViewModel(service: service, permissionManager: permissions)

        await viewModel.load()

        XCTAssertEqual(viewModel.state, .featureDisabled)
        XCTAssertEqual(viewModel.noticeKey, "notification.preferences.notice.feature_disabled")
    }

    func testModelsDecodeCanonicalPayloadsWithoutRawDeviceToken() throws {
        let prefJSON = """
        {
          "schemaVersion": "notification-preferences-v1",
          "userId": "00000000-0000-4000-8000-000000000101",
          "pushEnabled": false,
          "studyPlanEnabled": false,
          "spacedReviewEnabled": false,
          "examDeadlineEnabled": false,
          "billingTrialReminderEnabled": true,
          "accountSecurityEnabled": true,
          "quietHoursEnabled": true,
          "quietHoursStartLocal": "22:00",
          "quietHoursEndLocal": "08:00",
          "timezone": "Europe/Istanbul",
          "dailyCap": 2,
          "version": 0,
          "trainingEligible": false,
          "updatedAt": "2026-11-22T10:00:00.123Z"
        }
        """.data(using: .utf8)!
        let prefs = try APIClient.decodeISO8601Response(NotificationPreferences.self, from: prefJSON)
        XCTAssertEqual(prefs.billingTrialReminderEnabled, true)
        XCTAssertEqual(prefs.accountSecurityEnabled, true)
        XCTAssertEqual(prefs.trainingEligible, false)

        let deviceJSON = """
        {
          "schemaVersion": "notification-device-v1",
          "deviceId": "00000000-0000-4000-8000-000000000201",
          "platform": "IOS_APNS",
          "environment": "SANDBOX",
          "tokenLast4": "cdef",
          "osPermissionStatus": "AUTHORIZED",
          "appVersion": "1.0.0",
          "locale": "tr",
          "active": true,
          "version": 0,
          "trainingEligible": false,
          "lastSeenAt": "2026-11-22T10:05:00Z",
          "revokedAt": null,
          "updatedAt": "2026-11-22T10:05:00Z"
        }
        """.data(using: .utf8)!
        let device = try APIClient.decodeISO8601Response(RegisteredNotificationDevice.self, from: deviceJSON)
        XCTAssertEqual(device.tokenLast4, "cdef")
        XCTAssertEqual(device.osPermissionStatus, .authorized)
        XCTAssertNil(device.revokedAt)

        let deliveriesJSON = """
        {
          "schemaVersion": "notification-delivery-list-v1",
          "items": [
            {
              "deliveryId": "00000000-0000-4000-8000-000000000301",
              "category": "BILLING_TRIAL_REMINDER",
              "intentType": "TRIAL_ENDING_48H_REMINDER",
              "channel": "PUSH",
              "locale": "tr",
              "templateKey": "billing.trial.ending.48h",
              "deepLinkPath": "verified-ai://subscription/manage",
              "status": "SENT",
              "suppressionReason": null,
              "scheduledFor": "2026-11-22T09:00:00Z",
              "effectiveSendAt": "2026-11-22T09:00:00Z",
              "expiresAt": "2026-11-22T15:00:00Z",
              "sentAt": "2026-11-22T09:00:05Z",
              "attemptCount": 1,
              "createdAt": "2026-11-22T08:59:55Z"
            }
          ],
          "trainingEligible": false
        }
        """.data(using: .utf8)!
        let list = try APIClient.decodeISO8601Response(NotificationDeliveryListResponse.self, from: deliveriesJSON)
        XCTAssertEqual(list.items.count, 1)
        XCTAssertEqual(list.items[0].category, .billingTrialReminder)
        XCTAssertEqual(list.items[0].intentType, .trialEnding48hReminder)
        XCTAssertEqual(list.items[0].status, .sent)
    }

    func testDeepLinkRouterMapsNotificationRoutes() throws {
        let router = DeepLinkRouter()
        XCTAssertEqual(
            router.route(for: try XCTUnwrap(URL(string: "verified-ai://notifications"))),
            .notificationPreferences
        )
        XCTAssertEqual(
            router.route(for: try XCTUnwrap(URL(string: "verified-ai://notification-preferences"))),
            .notificationPreferences
        )
    }

    private func makeViewModel(
        service: MockNotificationService,
        permissionManager: StubPushPermissionManager,
        networkMonitor: StubNotificationNetworkMonitor = StubNotificationNetworkMonitor()
    ) -> NotificationPreferencesViewModel {
        NotificationPreferencesViewModel(
            service: service,
            permissionManager: permissionManager,
            networkMonitor: networkMonitor,
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "notifications")
        )
    }
}

private extension NotificationPreferencesDraft {
    func versionSafeCheck(_ preferences: NotificationPreferences?) -> Bool {
        guard let preferences else { return false }
        return pushEnabled == preferences.pushEnabled && quietHoursStartLocal == preferences.quietHoursStartLocal
    }
}

private actor MockNotificationService: NotificationServicing {
    private var current = NotificationPreferences(
        schemaVersion: "notification-preferences-v1",
        userId: UUID(uuidString: "00000000-0000-4000-8000-000000000101")!,
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
        updatedAt: Date(timeIntervalSince1970: 1_795_341_600)
    )
    private(set) var capturedPatches: [NotificationPreferencePatch] = []
    private var simulateConflictOnce = false
    private var simulateFeatureDisabled = false

    func setSimulateConflictOnce(_ value: Bool) {
        simulateConflictOnce = value
    }

    func setSimulateFeatureDisabled(_ value: Bool) {
        simulateFeatureDisabled = value
    }

    func fetchPreferences() async throws -> NotificationPreferences {
        if simulateFeatureDisabled {
            throw NetworkError.server(problem: ProblemDetails(
                type: "https://errors.verified-ai-learning.example/notification-feature-disabled",
                title: "Notification infrastructure is disabled",
                status: 503,
                code: "NOTIFICATION_FEATURE_DISABLED",
                traceId: "test-disabled",
                details: ProblemDetails.RecoveryDetails(recoverable: true, userAction: "RETRY_LATER")
            ))
        }
        return current
    }

    func updatePreferences(_ patch: NotificationPreferencePatch) async throws -> NotificationPreferences {
        capturedPatches.append(patch)
        if simulateConflictOnce {
            simulateConflictOnce = false
            current = NotificationPreferences(
                schemaVersion: current.schemaVersion,
                userId: current.userId,
                pushEnabled: current.pushEnabled,
                studyPlanEnabled: current.studyPlanEnabled,
                spacedReviewEnabled: current.spacedReviewEnabled,
                examDeadlineEnabled: current.examDeadlineEnabled,
                billingTrialReminderEnabled: current.billingTrialReminderEnabled,
                accountSecurityEnabled: true,
                quietHoursEnabled: current.quietHoursEnabled,
                quietHoursStartLocal: current.quietHoursStartLocal,
                quietHoursEndLocal: current.quietHoursEndLocal,
                timezone: current.timezone,
                dailyCap: current.dailyCap,
                version: current.version + 1,
                trainingEligible: false,
                updatedAt: Date()
            )
            throw NetworkError.server(problem: ProblemDetails(
                type: "https://errors.verified-ai-learning.example/optimistic-conflict",
                title: "Conflict",
                status: 409,
                code: "OPTIMISTIC_CONFLICT",
                traceId: "test-conflict",
                details: ProblemDetails.RecoveryDetails(recoverable: true, userAction: "REFRESH")
            ))
        }

        current = NotificationPreferences(
            schemaVersion: current.schemaVersion,
            userId: current.userId,
            pushEnabled: patch.pushEnabled ?? current.pushEnabled,
            studyPlanEnabled: patch.studyPlanEnabled ?? current.studyPlanEnabled,
            spacedReviewEnabled: patch.spacedReviewEnabled ?? current.spacedReviewEnabled,
            examDeadlineEnabled: patch.examDeadlineEnabled ?? current.examDeadlineEnabled,
            billingTrialReminderEnabled: patch.billingTrialReminderEnabled ?? current.billingTrialReminderEnabled,
            accountSecurityEnabled: true,
            quietHoursEnabled: patch.quietHoursEnabled ?? current.quietHoursEnabled,
            quietHoursStartLocal: patch.quietHoursStartLocal ?? current.quietHoursStartLocal,
            quietHoursEndLocal: patch.quietHoursEndLocal ?? current.quietHoursEndLocal,
            timezone: patch.timezone ?? current.timezone,
            dailyCap: patch.dailyCap ?? current.dailyCap,
            version: current.version + 1,
            trainingEligible: false,
            updatedAt: Date()
        )
        return current
    }

    func registerDevice(_ request: NotificationDeviceRegisterRequest) async throws -> RegisteredNotificationDevice {
        RegisteredNotificationDevice(
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
            lastSeenAt: Date(),
            revokedAt: nil,
            updatedAt: Date()
        )
    }

    func listActiveDevices() async throws -> [RegisteredNotificationDevice] {
        []
    }

    func revokeDevice(deviceId: UUID) async throws -> RegisteredNotificationDevice {
        RegisteredNotificationDevice(
            schemaVersion: "notification-device-v1",
            deviceId: deviceId,
            platform: "IOS_APNS",
            environment: "SANDBOX",
            tokenLast4: "cdef",
            osPermissionStatus: .authorized,
            appVersion: "1.0.0",
            locale: "en",
            active: false,
            version: 1,
            trainingEligible: false,
            lastSeenAt: Date(),
            revokedAt: Date(),
            updatedAt: Date()
        )
    }

    func listDeliveries(limit: Int) async throws -> [NotificationDeliveryItem] {
        []
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

    func fetchLifecycleSummary(limit: Int) async throws -> LifecycleSummaryResponse {
        LifecycleSummaryResponse(
            schemaVersion: "lifecycle-summary-v1",
            userId: current.userId,
            lifecycleCampaignsEnabled: true,
            activeCooldowns: [],
            recentEvaluations: [],
            llmTokensUsed: 0,
            trainingEligible: false,
            generatedAt: Date()
        )
    }
}

@MainActor
private final class StubNotificationNetworkMonitor: NetworkMonitoring {
    var isReachable: Bool

    init(isReachable: Bool = true) {
        self.isReachable = isReachable
    }
}
