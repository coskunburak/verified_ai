import Foundation
import Observation

enum NotificationPreferencesViewState: Equatable, Sendable {
    case idle
    case loading
    case ready
    case saving
    case offline
    case featureDisabled
    case error
}

struct NotificationPreferencesDraft: Equatable, Sendable {
    var pushEnabled: Bool
    var studyPlanEnabled: Bool
    var spacedReviewEnabled: Bool
    var examDeadlineEnabled: Bool
    var billingTrialReminderEnabled: Bool
    var quietHoursEnabled: Bool
    var quietHoursStartLocal: String
    var quietHoursEndLocal: String
    var timezone: String
    var dailyCap: Int

    static func defaultOptOut(timezone: String = TimeZone.current.identifier) -> NotificationPreferencesDraft {
        NotificationPreferencesDraft(
            pushEnabled: false,
            studyPlanEnabled: false,
            spacedReviewEnabled: false,
            examDeadlineEnabled: false,
            billingTrialReminderEnabled: false,
            quietHoursEnabled: true,
            quietHoursStartLocal: "22:00",
            quietHoursEndLocal: "08:00",
            timezone: timezone.isEmpty ? "UTC" : timezone,
            dailyCap: 2
        )
    }

    static func from(_ preferences: NotificationPreferences) -> NotificationPreferencesDraft {
        NotificationPreferencesDraft(
            pushEnabled: preferences.pushEnabled,
            studyPlanEnabled: preferences.studyPlanEnabled,
            spacedReviewEnabled: preferences.spacedReviewEnabled,
            examDeadlineEnabled: preferences.examDeadlineEnabled,
            billingTrialReminderEnabled: preferences.billingTrialReminderEnabled,
            quietHoursEnabled: preferences.quietHoursEnabled,
            quietHoursStartLocal: preferences.quietHoursStartLocal,
            quietHoursEndLocal: preferences.quietHoursEndLocal,
            timezone: preferences.timezone,
            dailyCap: preferences.dailyCap
        )
    }
}

@MainActor
@Observable
final class NotificationPreferencesViewModel {
    private let service: NotificationServicing
    private let permissionManager: PushPermissionManaging
    private let networkMonitor: NetworkMonitoring
    private let logger: AppLogger

    private(set) var state: NotificationPreferencesViewState = .idle
    private(set) var preferences: NotificationPreferences?
    var draft: NotificationPreferencesDraft = .defaultOptOut()
    private(set) var osPermissionStatus: OsNotificationPermissionStatus = .notDetermined
    private(set) var activeDevices: [RegisteredNotificationDevice] = []
    private(set) var recentDeliveries: [NotificationDeliveryItem] = []
    private(set) var lifecycleSummary: LifecycleSummaryResponse?
    private(set) var showPrePromptBanner: Bool = false
    private(set) var hasPendingConflictRecovery: Bool = false
    private(set) var noticeKey: String?
    private(set) var errorKey: String?

    init(
        service: NotificationServicing,
        permissionManager: PushPermissionManaging,
        networkMonitor: NetworkMonitoring,
        logger: AppLogger
    ) {
        self.service = service
        self.permissionManager = permissionManager
        self.networkMonitor = networkMonitor
        self.logger = logger
    }

    var hasUnsavedChanges: Bool {
        guard let preferences else { return false }
        return draft != NotificationPreferencesDraft.from(preferences)
    }

    var isTrialEndReminderActive: Bool {
        draft.pushEnabled && draft.billingTrialReminderEnabled && osPermissionStatus.isDeliverable
    }

    func load() async {
        osPermissionStatus = await permissionManager.authorizationStatus()

        guard networkMonitor.isReachable else {
            state = .offline
            errorKey = "notification.preferences.error.offline"
            return
        }

        state = .loading
        noticeKey = nil
        errorKey = nil
        hasPendingConflictRecovery = false

        do {
            let loaded = try await service.fetchPreferences()
            preferences = loaded
            draft = NotificationPreferencesDraft.from(loaded)
            activeDevices = (try? await service.listActiveDevices()) ?? []
            recentDeliveries = (try? await service.listDeliveries(limit: 10)) ?? []
            lifecycleSummary = try? await service.fetchLifecycleSummary(limit: 20)
            state = .ready
            if osPermissionStatus == .denied {
                noticeKey = "notification.preferences.permission.denied_banner"
            }
            logger.info("notifications.preferences.loaded")
        } catch NetworkError.server(let problem) where problem.code == "NOTIFICATION_FEATURE_DISABLED" {
            state = .featureDisabled
            noticeKey = "notification.preferences.notice.feature_disabled"
            logger.info("notifications.preferences.disabled")
        } catch {
            state = .error
            errorKey = "notification.preferences.error.load"
            logger.warning("notifications.preferences.load_error")
        }
    }

    func togglePushMaster(_ requestedEnabled: Bool) {
        errorKey = nil
        if requestedEnabled {
            switch osPermissionStatus {
            case .notDetermined:
                showPrePromptBanner = true
            case .denied:
                draft.pushEnabled = false
                noticeKey = "notification.preferences.permission.denied_banner"
            case .authorized, .provisional, .ephemeral:
                draft.pushEnabled = true
                if noticeKey == "notification.preferences.permission.denied_banner" {
                    noticeKey = nil
                }
            }
        } else {
            showPrePromptBanner = false
            draft.pushEnabled = false
        }
    }

    func confirmPushPrePrompt() async {
        showPrePromptBanner = false
        do {
            let updatedStatus = try await permissionManager.requestAuthorization()
            osPermissionStatus = updatedStatus
            if updatedStatus.isDeliverable {
                draft.pushEnabled = true
                noticeKey = nil
            } else {
                draft.pushEnabled = false
                noticeKey = "notification.preferences.permission.denied_banner"
            }
        } catch {
            osPermissionStatus = await permissionManager.authorizationStatus()
            draft.pushEnabled = false
            noticeKey = "notification.preferences.permission.denied_banner"
            logger.warning("notifications.permission.request_error")
        }
    }

    func dismissPushPrePrompt() {
        showPrePromptBanner = false
        draft.pushEnabled = false
    }

    func setDailyCap(_ value: Int) {
        draft.dailyCap = min(5, max(0, value))
    }

    func setQuietHoursWindow(start: String, end: String) {
        draft.quietHoursStartLocal = start
        draft.quietHoursEndLocal = end
    }

    func save() async {
        guard networkMonitor.isReachable else {
            state = .offline
            errorKey = "notification.preferences.error.offline"
            return
        }

        if draft.quietHoursStartLocal == draft.quietHoursEndLocal {
            errorKey = "notification.preferences.error.quiet_hours_invalid"
            return
        }

        guard let current = preferences else {
            await load()
            return
        }

        state = .saving
        errorKey = nil
        noticeKey = nil
        hasPendingConflictRecovery = false

        let patch = NotificationPreferencePatch(
            pushEnabled: draft.pushEnabled,
            studyPlanEnabled: draft.studyPlanEnabled,
            spacedReviewEnabled: draft.spacedReviewEnabled,
            examDeadlineEnabled: draft.examDeadlineEnabled,
            billingTrialReminderEnabled: draft.billingTrialReminderEnabled,
            accountSecurityEnabled: true,
            quietHoursEnabled: draft.quietHoursEnabled,
            quietHoursStartLocal: draft.quietHoursStartLocal,
            quietHoursEndLocal: draft.quietHoursEndLocal,
            timezone: draft.timezone,
            dailyCap: draft.dailyCap,
            expectedVersion: current.version,
            trainingEligible: false
        )

        do {
            let saved = try await service.updatePreferences(patch)
            preferences = saved
            draft = NotificationPreferencesDraft.from(saved)
            state = .ready
            noticeKey = "notification.preferences.saved_banner"
            logger.info("notifications.preferences.saved")
        } catch NetworkError.server(let problem) where problem.code == "OPTIMISTIC_CONFLICT" {
            logger.warning("notifications.preferences.optimistic_conflict")
            do {
                let latest = try await service.fetchPreferences()
                preferences = latest
                draft = NotificationPreferencesDraft.from(latest)
                hasPendingConflictRecovery = true
                noticeKey = "notification.preferences.error.conflict_reloaded"
                state = .ready
            } catch {
                state = .error
                errorKey = "notification.preferences.error.load"
            }
        } catch NetworkError.server(let problem) where problem.code == "NOTIFICATION_FEATURE_DISABLED" {
            state = .featureDisabled
            noticeKey = "notification.preferences.notice.feature_disabled"
        } catch NetworkError.server(let problem) where problem.code == "NOTIFICATION_TIMEZONE_INVALID" {
            state = .ready
            errorKey = "notification.preferences.error.timezone_invalid"
        } catch NetworkError.server(let problem) where problem.code == "NOTIFICATION_QUIET_HOURS_INVALID" {
            state = .ready
            errorKey = "notification.preferences.error.quiet_hours_invalid"
        } catch {
            state = .error
            errorKey = "notification.preferences.error.save"
            logger.warning("notifications.preferences.save_error")
        }
    }

    func revokeDevice(deviceId: UUID) async {
        guard networkMonitor.isReachable else {
            state = .offline
            errorKey = "notification.preferences.error.offline"
            return
        }
        do {
            _ = try await service.revokeDevice(deviceId: deviceId)
            activeDevices.removeAll { $0.deviceId == deviceId }
            logger.info("notifications.device.revoked")
        } catch {
            errorKey = "notification.preferences.error.save"
        }
    }

    func enableTrialEndReminderOneTap() async {
        guard networkMonitor.isReachable else {
            state = .offline
            errorKey = "notification.preferences.error.offline"
            return
        }

        errorKey = nil
        noticeKey = nil
        hasPendingConflictRecovery = false
        showPrePromptBanner = false

        var currentStatus = await permissionManager.authorizationStatus()
        if currentStatus == .notDetermined {
            do {
                currentStatus = try await permissionManager.requestAuthorization()
            } catch {
                currentStatus = await permissionManager.authorizationStatus()
                logger.warning("notifications.trial_optin.permission_error")
            }
        }
        osPermissionStatus = currentStatus

        guard currentStatus.isDeliverable else {
            draft.pushEnabled = false
            noticeKey = "notification.preferences.permission.denied_banner"
            return
        }

        state = .saving
        let baseline: NotificationPreferences
        do {
            if let existing = preferences {
                baseline = existing
            } else {
                let loaded = try await service.fetchPreferences()
                preferences = loaded
                draft = NotificationPreferencesDraft.from(loaded)
                baseline = loaded
            }
        } catch NetworkError.server(let problem) where problem.code == "NOTIFICATION_FEATURE_DISABLED" {
            state = .featureDisabled
            noticeKey = "notification.preferences.notice.feature_disabled"
            return
        } catch {
            state = .error
            errorKey = "notification.preferences.error.load"
            return
        }

        let initialPatch = makeTrialEndOptInPatch(expectedVersion: baseline.version)
        do {
            let saved = try await service.updatePreferences(initialPatch)
            preferences = saved
            draft = NotificationPreferencesDraft.from(saved)
            state = .ready
            noticeKey = "notification.trial_optin.enabled_banner"
            logger.info("notifications.trial_optin.enabled")
        } catch NetworkError.server(let problem) where problem.code == "OPTIMISTIC_CONFLICT" {
            logger.warning("notifications.trial_optin.optimistic_conflict")
            do {
                let latest = try await service.fetchPreferences()
                preferences = latest
                draft = NotificationPreferencesDraft.from(latest)
                hasPendingConflictRecovery = true

                let retryPatch = makeTrialEndOptInPatch(expectedVersion: latest.version)
                do {
                    let retried = try await service.updatePreferences(retryPatch)
                    preferences = retried
                    draft = NotificationPreferencesDraft.from(retried)
                    hasPendingConflictRecovery = false
                    state = .ready
                    noticeKey = "notification.trial_optin.enabled_banner"
                    logger.info("notifications.trial_optin.conflict_recovered")
                } catch NetworkError.server(let retryProblem) where retryProblem.code == "OPTIMISTIC_CONFLICT" {
                    if let refreshed = try? await service.fetchPreferences() {
                        preferences = refreshed
                        draft = NotificationPreferencesDraft.from(refreshed)
                    }
                    state = .ready
                    noticeKey = "notification.preferences.error.conflict_reloaded"
                } catch NetworkError.server(let retryProblem) where retryProblem.code == "NOTIFICATION_FEATURE_DISABLED" {
                    hasPendingConflictRecovery = false
                    state = .featureDisabled
                    noticeKey = "notification.preferences.notice.feature_disabled"
                } catch {
                    hasPendingConflictRecovery = false
                    state = .error
                    errorKey = "notification.preferences.error.save"
                }
            } catch {
                state = .error
                errorKey = "notification.preferences.error.load"
            }
        } catch NetworkError.server(let problem) where problem.code == "NOTIFICATION_FEATURE_DISABLED" {
            state = .featureDisabled
            noticeKey = "notification.preferences.notice.feature_disabled"
        } catch {
            state = .error
            errorKey = "notification.preferences.error.save"
            logger.warning("notifications.trial_optin.save_error")
        }
    }

    private func makeTrialEndOptInPatch(expectedVersion: Int64) -> NotificationPreferencePatch {
        NotificationPreferencePatch(
            pushEnabled: true,
            studyPlanEnabled: nil,
            spacedReviewEnabled: nil,
            examDeadlineEnabled: nil,
            billingTrialReminderEnabled: true,
            accountSecurityEnabled: true,
            quietHoursEnabled: nil,
            quietHoursStartLocal: nil,
            quietHoursEndLocal: nil,
            timezone: nil,
            dailyCap: nil,
            expectedVersion: expectedVersion,
            trainingEligible: false
        )
    }

    func reset() {
        state = .idle
        preferences = nil
        draft = .defaultOptOut()
        osPermissionStatus = .notDetermined
        activeDevices = []
        recentDeliveries = []
        lifecycleSummary = nil
        showPrePromptBanner = false
        hasPendingConflictRecovery = false
        noticeKey = nil
        errorKey = nil
    }
}
