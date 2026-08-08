import Foundation

enum EducationLevel: String, CaseIterable, Codable, Equatable, Identifiable, Sendable {
    case middleSchool = "MIDDLE_SCHOOL"
    case highSchool = "HIGH_SCHOOL"
    case university = "UNIVERSITY"
    case other = "OTHER"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .middleSchool:
            "Middle school"
        case .highSchool:
            "High school"
        case .university:
            "University"
        case .other:
            "Other"
        }
    }
}

enum ExplanationDepth: String, CaseIterable, Codable, Equatable, Identifiable, Sendable {
    case beginner = "BEGINNER"
    case quick = "QUICK"
    case standard = "STANDARD"
    case deep = "DEEP"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .beginner:
            "Beginner"
        case .quick:
            "Quick"
        case .standard:
            "Standard"
        case .deep:
            "Deep"
        }
    }
}

enum OnboardingStatus: String, Codable, Equatable, Sendable {
    case notStarted = "NOT_STARTED"
    case inProgress = "IN_PROGRESS"
    case completed = "COMPLETED"
}

enum OnboardingStep: Int, CaseIterable, Equatable, Identifiable, Sendable {
    case education
    case preferences
    case schedule
    case goal
    case review

    var id: Int { rawValue }

    var title: String {
        switch self {
        case .education:
            "Education"
        case .preferences:
            "Preferences"
        case .schedule:
            "Schedule"
        case .goal:
            "Goal"
        case .review:
            "Review"
        }
    }

    var next: OnboardingStep? {
        OnboardingStep(rawValue: rawValue + 1)
    }
}

struct LearningProfile: Equatable, Sendable {
    let exists: Bool
    let id: UUID?
    let userId: UUID
    let educationLevel: EducationLevel?
    let preferredLanguage: String?
    let explanationDepth: ExplanationDepth?
    let dailyStudyMinutes: Int?
    let timezone: String?
    let goalContext: String?
    let onboardingStatus: OnboardingStatus
    let version: Int64?
    let createdAt: Date?
    let updatedAt: Date?

    var isOnboardingComplete: Bool {
        onboardingStatus == .completed
    }

    var resumeStep: OnboardingStep {
        if educationLevel == nil {
            return .education
        }
        if preferredLanguage == nil || explanationDepth == nil {
            return .preferences
        }
        if dailyStudyMinutes == nil || timezone == nil {
            return .schedule
        }
        return .review
    }
}

struct LearningProfileDraft: Equatable, Sendable {
    var educationLevel: EducationLevel?
    var preferredLanguage: String
    var explanationDepth: ExplanationDepth
    var dailyStudyMinutes: Int
    var timezone: String
    var goalContext: String
    var expectedVersion: Int64?

    init(
        educationLevel: EducationLevel? = nil,
        preferredLanguage: String = "en",
        explanationDepth: ExplanationDepth = .standard,
        dailyStudyMinutes: Int = 25,
        timezone: String = TimeZone.current.identifier,
        goalContext: String = "",
        expectedVersion: Int64? = nil
    ) {
        self.educationLevel = educationLevel
        self.preferredLanguage = preferredLanguage
        self.explanationDepth = explanationDepth
        self.dailyStudyMinutes = dailyStudyMinutes
        self.timezone = timezone
        self.goalContext = goalContext
        self.expectedVersion = expectedVersion
    }

    init(profile: LearningProfile) {
        self.init(
            educationLevel: profile.educationLevel,
            preferredLanguage: profile.preferredLanguage ?? "en",
            explanationDepth: profile.explanationDepth ?? .standard,
            dailyStudyMinutes: profile.dailyStudyMinutes ?? 25,
            timezone: profile.timezone ?? TimeZone.current.identifier,
            goalContext: profile.goalContext ?? "",
            expectedVersion: profile.version
        )
    }

    var resumeStep: OnboardingStep {
        if educationLevel == nil {
            return .education
        }
        if preferredLanguage.isEmpty || timezone.isEmpty {
            return .preferences
        }
        return .review
    }

    func validationMessage(for step: OnboardingStep) -> String? {
        switch step {
        case .education:
            educationLevel == nil ? "Choose an education level." : nil
        case .preferences:
            supportedLanguages.contains(preferredLanguage) ? nil : "Choose a supported language."
        case .schedule:
            (5...240).contains(dailyStudyMinutes) ? nil : "Daily study time must be between 5 and 240 minutes."
        case .goal:
            goalContext.count <= 160 ? nil : "Learning goal must be 160 characters or fewer."
        case .review:
            completionValidationMessage
        }
    }

    var completionValidationMessage: String? {
        if let message = validationMessage(for: .education) {
            return message
        }
        if let message = validationMessage(for: .preferences) {
            return message
        }
        if let message = validationMessage(for: .schedule) {
            return message
        }
        if timezone.isEmpty || TimeZone(identifier: timezone) == nil {
            return "Choose a valid timezone."
        }
        return validationMessage(for: .goal)
    }

    private var supportedLanguages: Set<String> {
        ["en", "tr"]
    }
}
