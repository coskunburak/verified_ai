import Foundation

enum OsNotificationPermissionStatus: String, Codable, Sendable, Equatable, CaseIterable {
    case notDetermined = "NOT_DETERMINED"
    case denied = "DENIED"
    case authorized = "AUTHORIZED"
    case provisional = "PROVISIONAL"
    case ephemeral = "EPHEMERAL"

    var isDeliverable: Bool {
        switch self {
        case .authorized, .provisional, .ephemeral:
            return true
        case .notDetermined, .denied:
            return false
        }
    }
}

enum NotificationCategory: String, Codable, Sendable, Equatable, CaseIterable {
    case studyPlan = "STUDY_PLAN"
    case spacedReview = "SPACED_REVIEW"
    case examDeadline = "EXAM_DEADLINE"
    case billingTrialReminder = "BILLING_TRIAL_REMINDER"
    case accountSecurity = "ACCOUNT_SECURITY"
}

enum NotificationIntentType: String, Codable, Sendable, Equatable, CaseIterable {
    case studyPlanDailyMission = "STUDY_PLAN_DAILY_MISSION"
    case spacedReviewDue = "SPACED_REVIEW_DUE"
    case examDeadlineCountdown = "EXAM_DEADLINE_COUNTDOWN"
    case trialEnding48hReminder = "TRIAL_ENDING_48H_REMINDER"
    case accountSecurityAlert = "ACCOUNT_SECURITY_ALERT"
}

enum NotificationDeliveryStatus: String, Codable, Sendable, Equatable, CaseIterable {
    case scheduled = "SCHEDULED"
    case deferredQuietHours = "DEFERRED_QUIET_HOURS"
    case dispatching = "DISPATCHING"
    case sent = "SENT"
    case retryScheduled = "RETRY_SCHEDULED"
    case suppressed = "SUPPRESSED"
    case failedPermanent = "FAILED_PERMANENT"
    case cancelled = "CANCELLED"

    var isCancellable: Bool {
        switch self {
        case .scheduled, .deferredQuietHours, .retryScheduled:
            return true
        case .dispatching, .sent, .suppressed, .failedPermanent, .cancelled:
            return false
        }
    }
}

struct NotificationPreferences: Codable, Sendable, Equatable {
    let schemaVersion: String
    let userId: UUID
    let pushEnabled: Bool
    let studyPlanEnabled: Bool
    let spacedReviewEnabled: Bool
    let examDeadlineEnabled: Bool
    let billingTrialReminderEnabled: Bool
    let accountSecurityEnabled: Bool
    let quietHoursEnabled: Bool
    let quietHoursStartLocal: String
    let quietHoursEndLocal: String
    let timezone: String
    let dailyCap: Int
    let version: Int64
    let trainingEligible: Bool
    let updatedAt: Date
}

struct NotificationPreferencePatch: Codable, Sendable, Equatable {
    let pushEnabled: Bool?
    let studyPlanEnabled: Bool?
    let spacedReviewEnabled: Bool?
    let examDeadlineEnabled: Bool?
    let billingTrialReminderEnabled: Bool?
    let accountSecurityEnabled: Bool?
    let quietHoursEnabled: Bool?
    let quietHoursStartLocal: String?
    let quietHoursEndLocal: String?
    let timezone: String?
    let dailyCap: Int?
    let expectedVersion: Int64
    let trainingEligible: Bool?
}

struct NotificationDeviceRegisterRequest: Codable, Sendable, Equatable {
    let platform: String
    let environment: String
    let deviceToken: String
    let osPermissionStatus: OsNotificationPermissionStatus
    let appVersion: String
    let locale: String
    let trainingEligible: Bool
}

struct RegisteredNotificationDevice: Codable, Sendable, Equatable, Identifiable {
    let schemaVersion: String
    let deviceId: UUID
    let platform: String
    let environment: String
    let tokenLast4: String
    let osPermissionStatus: OsNotificationPermissionStatus
    let appVersion: String
    let locale: String
    let active: Bool
    let version: Int64
    let trainingEligible: Bool
    let lastSeenAt: Date
    let revokedAt: Date?
    let updatedAt: Date

    var id: UUID { deviceId }
}

struct NotificationDeviceListResponse: Codable, Sendable, Equatable {
    let schemaVersion: String
    let items: [RegisteredNotificationDevice]
    let trainingEligible: Bool
}

struct NotificationDeliveryItem: Codable, Sendable, Equatable, Identifiable {
    let deliveryId: UUID
    let category: NotificationCategory
    let intentType: NotificationIntentType
    let channel: String
    let locale: String
    let templateKey: String
    let deepLinkPath: String
    let status: NotificationDeliveryStatus
    let suppressionReason: String?
    let scheduledFor: Date
    let effectiveSendAt: Date
    let expiresAt: Date
    let sentAt: Date?
    let attemptCount: Int
    let createdAt: Date

    var id: UUID { deliveryId }
}

struct NotificationDeliveryListResponse: Codable, Sendable, Equatable {
    let schemaVersion: String
    let items: [NotificationDeliveryItem]
    let trainingEligible: Bool
}

struct NotificationDeliveryCancelResponse: Codable, Sendable, Equatable {
    let schemaVersion: String
    let deliveryId: UUID
    let status: NotificationDeliveryStatus
    let cancelledAt: Date
    let trainingEligible: Bool
}
