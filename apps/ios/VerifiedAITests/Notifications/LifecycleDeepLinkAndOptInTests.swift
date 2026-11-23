import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class LifecycleDeepLinkAndOptInTests: XCTestCase {

    func testDeepLinkRouterMapsAllFiveLifecycleRoutesAndRejectsUnsafeUrls() throws {
        let router = DeepLinkRouter()

        XCTAssertEqual(
            router.route(for: try XCTUnwrap(URL(string: "verified-ai://study-plan/today"))),
            .studyPlanToday
        )
        XCTAssertEqual(
            router.route(for: try XCTUnwrap(URL(string: "verified-ai://spaced-review"))),
            .spacedReview
        )
        XCTAssertEqual(
            router.route(for: try XCTUnwrap(URL(string: "verified-ai://exam-readiness"))),
            .examReadiness
        )
        XCTAssertEqual(
            router.route(for: try XCTUnwrap(URL(string: "verified-ai://subscription/manage"))),
            .subscriptionManage
        )
        XCTAssertEqual(
            router.route(for: try XCTUnwrap(URL(string: "verified-ai://account/security"))),
            .accountSecurity
        )

        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "https://evil.example/study-plan/today"))))
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "verified-ai://study-plan/../admin"))))
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "verified-ai://study-plan/%2e%2e/admin"))))
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "verified-ai://unknown"))))
    }

    func testOneTapTrialEndReminderRequestsPermissionAndEnablesOptIn() async {
        let service = StubLifecycleNotificationService()
        let permissions = StubPushPermissionManager(initialStatus: .notDetermined, nextAuthorizationStatus: .authorized)
        let viewModel = makeViewModel(service: service, permissionManager: permissions)

        await viewModel.load()
        XCTAssertFalse(viewModel.isTrialEndReminderActive)

        await viewModel.enableTrialEndReminderOneTap()

        let requestCount = await permissions.requestCount
        XCTAssertEqual(requestCount, 1)
        XCTAssertEqual(viewModel.state, .ready)
        XCTAssertEqual(viewModel.osPermissionStatus, .authorized)
        XCTAssertTrue(viewModel.isTrialEndReminderActive)
        XCTAssertEqual(viewModel.preferences?.pushEnabled, true)
        XCTAssertEqual(viewModel.preferences?.billingTrialReminderEnabled, true)
        XCTAssertEqual(viewModel.noticeKey, "notification.trial_optin.enabled_banner")

        let patches = await service.capturedPatches
        XCTAssertEqual(patches.count, 1)
        XCTAssertEqual(patches.first?.pushEnabled, true)
        XCTAssertEqual(patches.first?.billingTrialReminderEnabled, true)
        XCTAssertEqual(patches.first?.expectedVersion, 0)
        XCTAssertEqual(patches.first?.trainingEligible, false)
    }

    func testOneTapTrialEndReminderDeniedShowsCalmBannerWithoutFakeAuthorized() async {
        let service = StubLifecycleNotificationService()
        let permissions = StubPushPermissionManager(initialStatus: .denied, nextAuthorizationStatus: .denied)
        let viewModel = makeViewModel(service: service, permissionManager: permissions)

        await viewModel.load()
        await viewModel.enableTrialEndReminderOneTap()

        let requestCount = await permissions.requestCount
        XCTAssertEqual(requestCount, 0)
        XCTAssertEqual(viewModel.osPermissionStatus, .denied)
        XCTAssertFalse(viewModel.isTrialEndReminderActive)
        XCTAssertEqual(viewModel.preferences?.billingTrialReminderEnabled, false)
        XCTAssertEqual(viewModel.noticeKey, "notification.preferences.permission.denied_banner")

        let patches = await service.capturedPatches
        XCTAssertTrue(patches.isEmpty)
    }

    func testOneTapTrialEndReminderRecoversFromOptimisticConflictAutomatically() async {
        let service = StubLifecycleNotificationService()
        await service.setSimulateConflictOnce(true)
        let permissions = StubPushPermissionManager(initialStatus: .authorized, nextAuthorizationStatus: .authorized)
        let viewModel = makeViewModel(service: service, permissionManager: permissions)

        await viewModel.load()
        await viewModel.enableTrialEndReminderOneTap()

        XCTAssertEqual(viewModel.state, .ready)
        XCTAssertFalse(viewModel.hasPendingConflictRecovery)
        XCTAssertTrue(viewModel.isTrialEndReminderActive)
        XCTAssertEqual(viewModel.preferences?.version, 2)
        XCTAssertEqual(viewModel.noticeKey, "notification.trial_optin.enabled_banner")

        let patches = await service.capturedPatches
        XCTAssertEqual(patches.count, 2)
        XCTAssertEqual(patches[0].expectedVersion, 0)
        XCTAssertEqual(patches[1].expectedVersion, 1)
    }

    func testLifecycleSummaryFixtureDecodesWithZeroTokensAndTrainingIneligible() throws {
        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let fixtureURL = repoRoot.appendingPathComponent(
            "packages/schemas/notification/fixtures/lifecycle-summary-v1.valid.json"
        )
        let data = try Data(contentsOf: fixtureURL)
        let decoded = try APIClient.decodeISO8601Response(LifecycleSummaryResponse.self, from: data)

        XCTAssertEqual(decoded.schemaVersion, "lifecycle-summary-v1")
        XCTAssertTrue(decoded.lifecycleCampaignsEnabled)
        XCTAssertEqual(decoded.llmTokensUsed, 0)
        XCTAssertFalse(decoded.trainingEligible)
        XCTAssertEqual(decoded.activeCooldowns.count, 1)
        XCTAssertEqual(decoded.recentEvaluations.count, 4)
        XCTAssertEqual(decoded.recentEvaluations[1].decision, .cancelledObsolete)
        XCTAssertEqual(decoded.recentEvaluations[2].decision, .skippedAlreadyCompleted)
        XCTAssertEqual(decoded.recentEvaluations[3].decision, .skippedCooldownActive)
    }

    private func makeViewModel(
        service: StubLifecycleNotificationService,
        permissionManager: StubPushPermissionManager
    ) -> NotificationPreferencesViewModel {
        NotificationPreferencesViewModel(
            service: service,
            permissionManager: permissionManager,
            networkMonitor: StubReachableNetworkMonitor(),
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "notifications-lifecycle")
        )
    }
}

private actor StubLifecycleNotificationService: NotificationServicing {
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

    func setSimulateConflictOnce(_ value: Bool) {
        simulateConflictOnce = value
    }

    func fetchPreferences() async throws -> NotificationPreferences {
        current
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
private final class StubReachableNetworkMonitor: NetworkMonitoring {
    var isReachable: Bool = true
}
