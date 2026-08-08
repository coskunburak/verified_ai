import Foundation

protocol LearningProfileServicing: Sendable {
    func currentProfile() async throws -> LearningProfile
    func saveProfile(_ draft: LearningProfileDraft, completeOnboarding: Bool) async throws -> LearningProfile
}

final class LearningProfileAPI: LearningProfileServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func currentProfile() async throws -> LearningProfile {
        let response: HTTPResponse<LearningProfileWireResponse> = try await apiClient.send(
            HTTPRequest(endpoint: Endpoint(path: "/api/v1/me/learning-profile", method: .get))
        )
        return try response.body.profile()
    }

    func saveProfile(_ draft: LearningProfileDraft, completeOnboarding: Bool) async throws -> LearningProfile {
        let body = try encoder.encode(UpdateLearningProfileWireRequest(draft: draft, completeOnboarding: completeOnboarding))
        let response: HTTPResponse<LearningProfileWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/me/learning-profile", method: .patch),
                body: body,
                idempotencyKey: UUID().uuidString
            )
        )
        return try response.body.profile()
    }
}

private struct UpdateLearningProfileWireRequest: Encodable {
    let educationLevel: String?
    let preferredLanguage: String?
    let explanationDepth: String?
    let dailyStudyMinutes: Int?
    let timezone: String?
    let goalContext: String?
    let completeOnboarding: Bool
    let expectedVersion: Int64?

    init(draft: LearningProfileDraft, completeOnboarding: Bool) {
        self.educationLevel = draft.educationLevel?.rawValue
        self.preferredLanguage = draft.preferredLanguage
        self.explanationDepth = draft.explanationDepth.rawValue
        self.dailyStudyMinutes = draft.dailyStudyMinutes
        self.timezone = draft.timezone
        self.goalContext = draft.goalContext.isEmpty ? nil : draft.goalContext
        self.completeOnboarding = completeOnboarding
        self.expectedVersion = draft.expectedVersion
    }
}

private struct LearningProfileWireResponse: Decodable, Sendable {
    let exists: Bool
    let id: UUID?
    let userId: UUID
    let educationLevel: String?
    let preferredLanguage: String?
    let explanationDepth: String?
    let dailyStudyMinutes: Int?
    let timezone: String?
    let goalContext: String?
    let onboardingStatus: String
    let version: Int64?
    let createdAt: String?
    let updatedAt: String?

    func profile() throws -> LearningProfile {
        LearningProfile(
            exists: exists,
            id: id,
            userId: userId,
            educationLevel: try enumValue(EducationLevel.self, from: educationLevel),
            preferredLanguage: preferredLanguage,
            explanationDepth: try enumValue(ExplanationDepth.self, from: explanationDepth),
            dailyStudyMinutes: dailyStudyMinutes,
            timezone: timezone,
            goalContext: goalContext,
            onboardingStatus: try enumValue(OnboardingStatus.self, from: onboardingStatus) ?? .notStarted,
            version: version,
            createdAt: Self.parseDate(createdAt),
            updatedAt: Self.parseDate(updatedAt)
        )
    }

    private func enumValue<T: RawRepresentable>(_ type: T.Type, from value: String?) throws -> T? where T.RawValue == String {
        guard let value else {
            return nil
        }
        guard let parsed = T(rawValue: value) else {
            throw NetworkError.decoding("unsupported_profile_value")
        }
        return parsed
    }

    private static func parseDate(_ value: String?) -> Date? {
        guard let value else {
            return nil
        }

        let fractionalFormatter = ISO8601DateFormatter()
        fractionalFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fractionalFormatter.date(from: value) {
            return date
        }

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)
    }
}
