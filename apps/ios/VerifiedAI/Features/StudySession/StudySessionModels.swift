import Foundation

enum StudySessionState: String, Codable, Equatable, Sendable {
    case initialized = "INITIALIZED"
    case active = "ACTIVE"
    case paused = "PAUSED"
    case completed = "COMPLETED"
    case abandoned = "ABANDONED"

    var isTerminal: Bool {
        self == .completed || self == .abandoned
    }

    var isResumable: Bool {
        self == .active || self == .paused
    }
}

enum StudySessionSourceContext: String, Codable, Equatable, Sendable {
    case dailyCoach = "DAILY_COACH"
    case spacedReview = "SPACED_REVIEW"
    case adaptivePractice = "ADAPTIVE_PRACTICE"
    case mistakeRepair = "MISTAKE_REPAIR"
    case freeStudy = "FREE_STUDY"
}

enum StudySessionStepType: String, Codable, Equatable, Sendable {
    case conceptPrimer = "CONCEPT_PRIMER"
    case microLesson = "MICRO_LESSON"
    case solveItem = "SOLVE_ITEM"
    case verifyStep = "VERIFY_STEP"
    case spacedRecall = "SPACED_RECALL"
    case reflection = "REFLECTION"
}

enum StudySessionStepStatus: String, Codable, Equatable, Sendable {
    case inProgress = "IN_PROGRESS"
    case completed = "COMPLETED"
    case skipped = "SKIPPED"
}

enum StudySessionBlueprintStatus: String, Codable, Equatable, Sendable {
    case pending = "PENDING"
    case inProgress = "IN_PROGRESS"
    case completed = "COMPLETED"
    case skipped = "SKIPPED"
}

enum StudySessionInterruptionReason: String, Codable, Equatable, Sendable {
    case userPaused = "USER_PAUSED"
    case appBackgrounded = "APP_BACKGROUNDED"
    case networkLost = "NETWORK_LOST"
    case phoneCall = "PHONE_CALL"
    case osTerminated = "OS_TERMINATED"
    case expiredInactivity = "EXPIRED_INACTIVITY"
    case supersededByNewSession = "SUPERSEDED_BY_NEW_SESSION"
}

struct StudySessionStepBlueprintItem: Codable, Equatable, Sendable, Identifiable {
    let stepIndex: Int
    let stepType: StudySessionStepType
    let stepTitle: String
    let estimatedSeconds: Int
    var status: StudySessionBlueprintStatus
    let targetSkillCode: String?
    let problemId: String?

    var id: Int { stepIndex }

    enum CodingKeys: String, CodingKey {
        case stepIndex = "step_index"
        case stepType = "step_type"
        case stepTitle = "step_title"
        case estimatedSeconds = "estimated_seconds"
        case status
        case targetSkillCode = "target_skill_code"
        case problemId = "problem_id"
    }
}

struct StudySessionResumeSnapshot: Codable, Equatable, Sendable {
    let lastStepIndex: Int
    let draftStepNotes: String
    let offlineQueuedCount: Int
    let activeSubStep: String?
    let uiScrollAnchor: String?
    let mistakeCode: String?

    enum CodingKeys: String, CodingKey {
        case lastStepIndex = "last_step_index"
        case draftStepNotes = "draft_step_notes"
        case offlineQueuedCount = "offline_queued_count"
        case activeSubStep = "active_sub_step"
        case uiScrollAnchor = "ui_scroll_anchor"
        case mistakeCode = "mistake_code"
    }

    static func empty(stepIndex: Int = 0) -> StudySessionResumeSnapshot {
        StudySessionResumeSnapshot(
            lastStepIndex: stepIndex,
            draftStepNotes: "",
            offlineQueuedCount: 0,
            activeSubStep: nil,
            uiScrollAnchor: nil,
            mistakeCode: nil
        )
    }
}

struct StudySessionStateModel: Codable, Equatable, Sendable, Identifiable {
    let schemaVersion: String
    let sessionId: UUID
    let userId: UUID
    let coachSessionId: UUID?
    let sourceContext: StudySessionSourceContext
    let topicCode: String
    let locale: String
    let state: StudySessionState
    let totalSteps: Int
    let currentStepIndex: Int
    let completedStepsCount: Int
    let lastSequenceNumber: Int
    let activeDurationSeconds: Int
    let pausedDurationSeconds: Int
    let clientInstanceId: String
    let interruptionReason: StudySessionInterruptionReason?
    let stepBlueprint: [StudySessionStepBlueprintItem]
    let resumeSnapshot: StudySessionResumeSnapshot
    let startedAt: Date
    let lastCheckpointAt: Date?
    let pausedAt: Date?
    let resumedAt: Date?
    let completedAt: Date?
    let expiresAt: Date
    let trainingEligible: Bool

    var id: UUID { sessionId }

    enum CodingKeys: String, CodingKey {
        case schemaVersion = "schema_version"
        case sessionId = "session_id"
        case userId = "user_id"
        case coachSessionId = "coach_session_id"
        case sourceContext = "source_context"
        case topicCode = "topic_code"
        case locale
        case state
        case totalSteps = "total_steps"
        case currentStepIndex = "current_step_index"
        case completedStepsCount = "completed_steps_count"
        case lastSequenceNumber = "last_sequence_number"
        case activeDurationSeconds = "active_duration_seconds"
        case pausedDurationSeconds = "paused_duration_seconds"
        case clientInstanceId = "client_instance_id"
        case interruptionReason = "interruption_reason"
        case stepBlueprint = "step_blueprint"
        case resumeSnapshot = "resume_snapshot"
        case startedAt = "started_at"
        case lastCheckpointAt = "last_checkpoint_at"
        case pausedAt = "paused_at"
        case resumedAt = "resumed_at"
        case completedAt = "completed_at"
        case expiresAt = "expires_at"
        case trainingEligible = "training_eligible"
    }
}

struct StudySessionStartRequest: Codable, Equatable, Sendable {
    let coachSessionId: UUID?
    let sourceContext: StudySessionSourceContext
    let topicCode: String
    let locale: String
    let totalSteps: Int
    let clientInstanceId: String
    let stepBlueprint: [StudySessionStepBlueprintItem]
    let resumeSnapshot: StudySessionResumeSnapshot?

    enum CodingKeys: String, CodingKey {
        case coachSessionId = "coach_session_id"
        case sourceContext = "source_context"
        case topicCode = "topic_code"
        case locale
        case totalSteps = "total_steps"
        case clientInstanceId = "client_instance_id"
        case stepBlueprint = "step_blueprint"
        case resumeSnapshot = "resume_snapshot"
    }
}

struct StudySessionStartResponse: Codable, Equatable, Sendable {
    let schemaVersion: String
    let session: StudySessionStateModel
    let autoPausedPriorSessionId: UUID?
    let expiredPriorSessionId: UUID?
    let trainingEligible: Bool

    enum CodingKeys: String, CodingKey {
        case schemaVersion = "schema_version"
        case session
        case autoPausedPriorSessionId = "auto_paused_prior_session_id"
        case expiredPriorSessionId = "expired_prior_session_id"
        case trainingEligible = "training_eligible"
    }
}

struct StudySessionCheckpointRequest: Codable, Equatable, Sendable {
    let sequenceNumber: Int
    let stepIndex: Int
    let stepType: StudySessionStepType
    let stepStatus: StudySessionStepStatus
    let reportedActiveDurationSeconds: Int
    let offlineQueued: Bool
    let clientRecordedAt: Date
    let idempotencyKey: String
    let interruptionReason: StudySessionInterruptionReason?
    let clientInstanceId: String?
    let resumeSnapshot: StudySessionResumeSnapshot

    enum CodingKeys: String, CodingKey {
        case sequenceNumber = "sequence_number"
        case stepIndex = "step_index"
        case stepType = "step_type"
        case stepStatus = "step_status"
        case reportedActiveDurationSeconds = "reported_active_duration_seconds"
        case offlineQueued = "offline_queued"
        case clientRecordedAt = "client_recorded_at"
        case idempotencyKey = "idempotency_key"
        case interruptionReason = "interruption_reason"
        case clientInstanceId = "client_instance_id"
        case resumeSnapshot = "resume_snapshot"
    }
}

struct StudySessionCheckpointResponse: Codable, Equatable, Sendable {
    let schemaVersion: String
    let session: StudySessionStateModel
    let acceptedSequenceNumber: Int
    let acceptedActiveDurationSeconds: Int
    let durationClamped: Bool
    let idempotentReplay: Bool
    let staleCheckpointIgnored: Bool
    let trainingEligible: Bool

    enum CodingKeys: String, CodingKey {
        case schemaVersion = "schema_version"
        case session
        case acceptedSequenceNumber = "accepted_sequence_number"
        case acceptedActiveDurationSeconds = "accepted_active_duration_seconds"
        case durationClamped = "duration_clamped"
        case idempotentReplay = "idempotent_replay"
        case staleCheckpointIgnored = "stale_checkpoint_ignored"
        case trainingEligible = "training_eligible"
    }
}

struct StudySessionPauseRequest: Codable, Equatable, Sendable {
    let interruptionReason: StudySessionInterruptionReason?
    let reportedActiveDurationSeconds: Int?
    let resumeSnapshot: StudySessionResumeSnapshot?

    enum CodingKeys: String, CodingKey {
        case interruptionReason = "interruption_reason"
        case reportedActiveDurationSeconds = "reported_active_duration_seconds"
        case resumeSnapshot = "resume_snapshot"
    }
}

struct StudySessionResumeRequest: Codable, Equatable, Sendable {
    let clientInstanceId: String?
    let resumeSnapshot: StudySessionResumeSnapshot?

    enum CodingKeys: String, CodingKey {
        case clientInstanceId = "client_instance_id"
        case resumeSnapshot = "resume_snapshot"
    }
}

struct StudySessionCompleteRequest: Codable, Equatable, Sendable {
    let finalStepIndex: Int
    let reportedActiveDurationSeconds: Int
    let idempotencyKey: String
    let completionNotes: String?
    let resumeSnapshot: StudySessionResumeSnapshot?

    enum CodingKeys: String, CodingKey {
        case finalStepIndex = "final_step_index"
        case reportedActiveDurationSeconds = "reported_active_duration_seconds"
        case idempotencyKey = "idempotency_key"
        case completionNotes = "completion_notes"
        case resumeSnapshot = "resume_snapshot"
    }
}

struct StudySessionCompleteResponse: Codable, Equatable, Sendable {
    let schemaVersion: String
    let session: StudySessionStateModel
    let acceptedActiveDurationSeconds: Int
    let durationClamped: Bool
    let idempotentReplay: Bool
    let supportiveSummaryKey: String
    let trainingEligible: Bool

    enum CodingKeys: String, CodingKey {
        case schemaVersion = "schema_version"
        case session
        case acceptedActiveDurationSeconds = "accepted_active_duration_seconds"
        case durationClamped = "duration_clamped"
        case idempotentReplay = "idempotent_replay"
        case supportiveSummaryKey = "supportive_summary_key"
        case trainingEligible = "training_eligible"
    }
}

struct QueuedOfflineCheckpoint: Equatable, Sendable {
    let idempotencyKey: String
    let sessionId: UUID
    let userId: UUID
    let sequenceNumber: Int
    let stepIndex: Int
    let request: StudySessionCheckpointRequest
    let synced: Bool
}

enum StudySessionIdempotency {
    static func checkpointKey(sessionId: UUID, stepIndex: Int, sequenceNumber: Int) -> String {
        let shortSession = String(sessionId.uuidString.replacingOccurrences(of: "-", with: "").prefix(12)).lowercased()
        let paddedStep = String(format: "%02d", max(0, stepIndex))
        let paddedSeq = String(format: "%04d", max(1, sequenceNumber))
        return "sess_\(shortSession)_step_\(paddedStep)_seq_\(paddedSeq)"
    }

    static func completeKey(sessionId: UUID, sequenceNumber: Int) -> String {
        let shortSession = String(sessionId.uuidString.replacingOccurrences(of: "-", with: "").prefix(12)).lowercased()
        let paddedSeq = String(format: "%04d", max(1, sequenceNumber))
        return "sess_\(shortSession)_complete_\(paddedSeq)"
    }
}

enum StudySessionJSONCoding {
    static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }

    static func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}
