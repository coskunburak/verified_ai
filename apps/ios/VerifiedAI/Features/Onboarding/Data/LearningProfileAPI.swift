import Foundation

protocol LearningProfileServicing: Sendable {
    func currentProfile() async throws -> LearningProfile
    func saveProfile(_ draft: LearningProfileDraft, completeOnboarding: Bool) async throws -> LearningProfile
    func saveExplanationDepth(_ depth: ExplanationDepth, expectedVersion: Int64) async throws -> LearningProfile
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

    func saveExplanationDepth(_ depth: ExplanationDepth, expectedVersion: Int64) async throws -> LearningProfile {
        let body = try JSONEncoder().encode(ExplanationDepthPatch(depth: depth, expectedVersion: expectedVersion))
        let response: HTTPResponse<LearningProfileWireResponse> = try await apiClient.send(
            HTTPRequest(endpoint: Endpoint(path: "/api/v1/me/learning-profile", method: .patch),
                        body: body, allowsAuthRefreshRetry: false)
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

/// Omitting every unrelated field preserves server partial-PATCH semantics.
struct ExplanationDepthPatch: Encodable {
    let explanationDepth: ExplanationDepth
    let expectedVersion: Int64

    init(depth: ExplanationDepth, expectedVersion: Int64) {
        self.explanationDepth = depth
        self.expectedVersion = expectedVersion
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

#if DEBUG
/// Synthetic UI transport; only injected by the existing authenticated solve-fixture gate.
actor FixtureLearningProfileAPI: LearningProfileServicing {
    private var depth: ExplanationDepth = .standard
    private var version: Int64 = 1
    private let failsSave: Bool

    init(failsSave: Bool = false) { self.failsSave = failsSave }

    func currentProfile() async throws -> LearningProfile { profile() }
    func saveProfile(_ draft: LearningProfileDraft, completeOnboarding: Bool) async throws -> LearningProfile {
        try await saveExplanationDepth(draft.explanationDepth, expectedVersion: draft.expectedVersion ?? version)
    }
    func saveExplanationDepth(_ depth: ExplanationDepth, expectedVersion: Int64) async throws -> LearningProfile {
        if failsSave { throw NetworkError.transport("synthetic_offline") }
        guard expectedVersion == version else {
            throw NetworkError.server(problem: ProblemDetails(type: "fixture", title: "Conflict", status: 409,
                code: "OPTIMISTIC_CONFLICT", traceId: nil, details: nil))
        }
        self.depth = depth
        version += 1
        return profile()
    }
    private func profile() -> LearningProfile {
        LearningProfile(exists: true, id: UUID(uuidString: "00000000-0000-0000-0000-0000000000B1")!,
            userId: UUID(uuidString: "00000000-0000-0000-0000-0000000000A1")!, educationLevel: .highSchool,
            preferredLanguage: "en", explanationDepth: depth, dailyStudyMinutes: 25,
            timezone: "Europe/Istanbul", goalContext: "Synthetic fixture", onboardingStatus: .completed,
            version: version, createdAt: nil, updatedAt: nil)
    }
}
#endif
