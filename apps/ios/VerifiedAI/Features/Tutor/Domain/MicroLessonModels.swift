import CryptoKit
import Foundation

public enum MicroLessonTriggerSource: String, Codable, Sendable, CaseIterable {
    case twinSecondFailure = "TWIN_SECOND_FAILURE"
    case socraticRung2 = "SOCRATIC_RUNG_2"
    case coachRepairDrawer = "COACH_REPAIR_DRAWER"
}

public enum MicroLessonBeat: Int, CaseIterable, Sendable {
    case coreRule = 0
    case counterexample = 1
    case quickCheck = 2

    public var titleKey: String {
        switch self {
        case .coreRule: return "micro_lesson.beat1.tab_title"
        case .counterexample: return "micro_lesson.beat2.tab_title"
        case .quickCheck: return "micro_lesson.beat3.tab_title"
        }
    }
}

public enum MicroLessonBindingValue: Codable, Equatable, Sendable {
    case int(Int)
    case double(Double)
    case string(String)

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let intVal = try? container.decode(Int.self) {
            self = .int(intVal)
        } else if let doubleVal = try? container.decode(Double.self) {
            self = .double(doubleVal)
        } else {
            self = .string(try container.decode(String.self))
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .int(let v): try container.encode(v)
        case .double(let v): try container.encode(v)
        case .string(let v): try container.encode(v)
        }
    }
}

public struct MicroLessonCoreRule: Codable, Equatable, Sendable {
    public let ruleTitle: String
    public let conciseExplanation: String
    public let canonicalLatexFormula: String
    public let sympyCheckExpr: String
    public let estimatedSeconds: Int

    public init(
        ruleTitle: String,
        conciseExplanation: String,
        canonicalLatexFormula: String,
        sympyCheckExpr: String,
        estimatedSeconds: Int
    ) {
        self.ruleTitle = ruleTitle
        self.conciseExplanation = conciseExplanation
        self.canonicalLatexFormula = canonicalLatexFormula
        self.sympyCheckExpr = sympyCheckExpr
        self.estimatedSeconds = estimatedSeconds
    }
}

public struct MicroLessonCounterexample: Codable, Equatable, Sendable {
    public let promptLatex: String
    public let commonTrapLatex: String
    public let whyTrapFailsExplanation: String
    public let verifiedStepLatex: String
    public let numericCounterexampleLatex: String
    public let sympyEquivalenceCheckExpr: String
    public let sympyTargetExpr: String
    public let sympyValidExpr: String
    public let sympyTrapExpr: String
    public let counterexampleBindings: [String: MicroLessonBindingValue]
    public let estimatedSeconds: Int

    public init(
        promptLatex: String,
        commonTrapLatex: String,
        whyTrapFailsExplanation: String,
        verifiedStepLatex: String,
        numericCounterexampleLatex: String,
        sympyEquivalenceCheckExpr: String,
        sympyTargetExpr: String,
        sympyValidExpr: String,
        sympyTrapExpr: String,
        counterexampleBindings: [String: MicroLessonBindingValue],
        estimatedSeconds: Int
    ) {
        self.promptLatex = promptLatex
        self.commonTrapLatex = commonTrapLatex
        self.whyTrapFailsExplanation = whyTrapFailsExplanation
        self.verifiedStepLatex = verifiedStepLatex
        self.numericCounterexampleLatex = numericCounterexampleLatex
        self.sympyEquivalenceCheckExpr = sympyEquivalenceCheckExpr
        self.sympyTargetExpr = sympyTargetExpr
        self.sympyValidExpr = sympyValidExpr
        self.sympyTrapExpr = sympyTrapExpr
        self.counterexampleBindings = counterexampleBindings
        self.estimatedSeconds = estimatedSeconds
    }
}

public struct MicroLessonQuickCheckOption: Codable, Equatable, Identifiable, Sendable {
    public var id: String { optionId }
    public let optionId: String
    public let latexOrText: String
    public let rationale: String
    public let isCorrect: Bool

    public init(optionId: String, latexOrText: String, rationale: String, isCorrect: Bool) {
        self.optionId = optionId
        self.latexOrText = latexOrText
        self.rationale = rationale
        self.isCorrect = isCorrect
    }
}

public struct MicroLessonQuickCheck: Codable, Equatable, Sendable {
    public let questionPrompt: String
    public let questionLatex: String
    public let options: [MicroLessonQuickCheckOption]
    public let correctOptionId: String
    public let estimatedSeconds: Int

    public init(
        questionPrompt: String,
        questionLatex: String,
        options: [MicroLessonQuickCheckOption],
        correctOptionId: String,
        estimatedSeconds: Int
    ) {
        self.questionPrompt = questionPrompt
        self.questionLatex = questionLatex
        self.options = options
        self.correctOptionId = correctOptionId
        self.estimatedSeconds = estimatedSeconds
    }
}

public struct MicroLessonCard: Codable, Equatable, Identifiable, Sendable {
    public var id: UUID { cardId }
    public let schemaVersion: String
    public let cardId: UUID
    public let mistakeCode: String
    public let locale: String
    public let title: String
    public let focusBadgeLabel: String
    public let estimatedDurationSeconds: Int
    public let triggerSource: MicroLessonTriggerSource?
    public let triggeredByAttemptId: UUID?
    public let coreRule: MicroLessonCoreRule
    public let brokenVsValidCounterexample: MicroLessonCounterexample
    public let interactiveQuickCheck: MicroLessonQuickCheck
    public let verificationStatus: String
    public let verifierVersion: String
    public let verificationHash: String

    public init(
        schemaVersion: String = "1.0.0",
        cardId: UUID,
        mistakeCode: String,
        locale: String,
        title: String,
        focusBadgeLabel: String,
        estimatedDurationSeconds: Int,
        triggerSource: MicroLessonTriggerSource? = nil,
        triggeredByAttemptId: UUID? = nil,
        coreRule: MicroLessonCoreRule,
        brokenVsValidCounterexample: MicroLessonCounterexample,
        interactiveQuickCheck: MicroLessonQuickCheck,
        verificationStatus: String = "VERIFIED",
        verifierVersion: String = "sympy-1.12-v1",
        verificationHash: String
    ) {
        self.schemaVersion = schemaVersion
        self.cardId = cardId
        self.mistakeCode = mistakeCode
        self.locale = locale
        self.title = title
        self.focusBadgeLabel = focusBadgeLabel
        self.estimatedDurationSeconds = estimatedDurationSeconds
        self.triggerSource = triggerSource
        self.triggeredByAttemptId = triggeredByAttemptId
        self.coreRule = coreRule
        self.brokenVsValidCounterexample = brokenVsValidCounterexample
        self.interactiveQuickCheck = interactiveQuickCheck
        self.verificationStatus = verificationStatus
        self.verifierVersion = verifierVersion
        self.verificationHash = verificationHash
    }

    public var isDurationBounded: Bool {
        let beats = [
            coreRule.estimatedSeconds,
            brokenVsValidCounterexample.estimatedSeconds,
            interactiveQuickCheck.estimatedSeconds
        ]
        let sum = beats.reduce(0, +)
        return beats.allSatisfy { $0 >= 15 && $0 <= 60 }
            && estimatedDurationSeconds >= 60
            && estimatedDurationSeconds <= 180
            && estimatedDurationSeconds == sum
    }

    public var hasValidHexHash: Bool {
        verificationHash.count == 64
            && verificationHash.unicodeScalars.allSatisfy { scalar in
                (scalar >= "0" && scalar <= "9") || (scalar >= "a" && scalar <= "f")
            }
    }

    public var hasZeroExternalMediaUrls: Bool {
        let strings = [
            title,
            focusBadgeLabel,
            coreRule.ruleTitle,
            coreRule.conciseExplanation,
            brokenVsValidCounterexample.whyTrapFailsExplanation,
            interactiveQuickCheck.questionPrompt
        ] + interactiveQuickCheck.options.map(\.rationale)
        let forbidden = ["http://", "https://", ".mp4", ".mov", ".m3u8", "youtube", "vimeo"]
        for s in strings {
            let lower = s.lowercased()
            if forbidden.contains(where: { lower.contains($0) }) {
                return false
            }
        }
        return true
    }

    public var passesLocalIntegrityChecks: Bool {
        verificationStatus == "VERIFIED"
            && hasValidHexHash
            && isDurationBounded
            && hasZeroExternalMediaUrls
            && interactiveQuickCheck.options.contains(where: { $0.optionId == interactiveQuickCheck.correctOptionId && $0.isCorrect })
    }
}

public struct MicroLessonCompleteRequest: Codable, Equatable, Sendable {
    public let locale: String
    public let triggerSource: MicroLessonTriggerSource
    public let sourceAttemptId: UUID?
    public let selectedOptionId: String
    public let dwellTimeMs: Int
    public let idempotencyKey: String

    public init(
        locale: String,
        triggerSource: MicroLessonTriggerSource,
        sourceAttemptId: UUID?,
        selectedOptionId: String,
        dwellTimeMs: Int,
        idempotencyKey: String
    ) {
        self.locale = locale
        self.triggerSource = triggerSource
        self.sourceAttemptId = sourceAttemptId
        self.selectedOptionId = selectedOptionId
        self.dwellTimeMs = dwellTimeMs
        self.idempotencyKey = idempotencyKey
    }
}

public struct MicroLessonCompleteResponse: Codable, Equatable, Sendable {
    public let schemaVersion: String
    public let completionId: UUID
    public let cardId: UUID
    public let mistakeCode: String
    public let locale: String
    public let quickCheckPassed: Bool
    public let dwellTimeMs: Int
    public let completedAt: Date
    public let idempotentReplay: Bool
    public let trainingEligible: Bool

    public init(
        schemaVersion: String = "1.0.0",
        completionId: UUID,
        cardId: UUID,
        mistakeCode: String,
        locale: String,
        quickCheckPassed: Bool,
        dwellTimeMs: Int,
        completedAt: Date,
        idempotentReplay: Bool,
        trainingEligible: Bool = false
    ) {
        self.schemaVersion = schemaVersion
        self.completionId = completionId
        self.cardId = cardId
        self.mistakeCode = mistakeCode
        self.locale = locale
        self.quickCheckPassed = quickCheckPassed
        self.dwellTimeMs = dwellTimeMs
        self.completedAt = completedAt
        self.idempotentReplay = idempotentReplay
        self.trainingEligible = trainingEligible
    }
}

public enum MicroLessonClientError: Error, Equatable, Sendable {
    case featureDisabled
    case diagnosisNotFound
    case triggerNotEligible
    case verificationFailed
    case invalidRequest
    case networkFailure(String)
}

public protocol MicroLessonClientProtocol: Sendable {
    func fetchMicroLesson(
        mistakeCode: String,
        locale: String,
        triggerSource: MicroLessonTriggerSource,
        sourceAttemptId: UUID?
    ) async throws -> MicroLessonCard

    func completeMicroLesson(
        mistakeCode: String,
        request: MicroLessonCompleteRequest
    ) async throws -> MicroLessonCompleteResponse
}

public final class URLSessionMicroLessonClient: MicroLessonClientProtocol, @unchecked Sendable {
    private let baseURL: URL
    private let session: URLSession
    private let bearerTokenProvider: @Sendable () -> String?

    public init(
        baseURL: URL,
        session: URLSession = .shared,
        bearerTokenProvider: @escaping @Sendable () -> String?
    ) {
        self.baseURL = baseURL
        self.session = session
        self.bearerTokenProvider = bearerTokenProvider
    }

    public func fetchMicroLesson(
        mistakeCode: String,
        locale: String,
        triggerSource: MicroLessonTriggerSource,
        sourceAttemptId: UUID?
    ) async throws -> MicroLessonCard {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("v1/micro-lessons/\(mistakeCode)"),
            resolvingAgainstBaseURL: false
        )!
        var queryItems = [
            URLQueryItem(name: "locale", value: locale),
            URLQueryItem(name: "triggerSource", value: triggerSource.rawValue)
        ]
        if let sourceAttemptId {
            queryItems.append(URLQueryItem(name: "sourceAttemptId", value: sourceAttemptId.uuidString))
        }
        components.queryItems = queryItems

        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        request.setValue(locale, forHTTPHeaderField: "Accept-Language")
        if let token = bearerTokenProvider() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw MicroLessonClientError.networkFailure("Invalid HTTP response")
        }
        try mapHttpStatus(http.statusCode)
        let card = try JSONDecoder().decode(MicroLessonCard.self, from: data)
        guard card.passesLocalIntegrityChecks else {
            throw MicroLessonClientError.verificationFailed
        }
        return card
    }

    public func completeMicroLesson(
        mistakeCode: String,
        request payload: MicroLessonCompleteRequest
    ) async throws -> MicroLessonCompleteResponse {
        let url = baseURL.appendingPathComponent("v1/micro-lessons/\(mistakeCode)/complete")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let token = bearerTokenProvider() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try JSONEncoder().encode(payload)

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw MicroLessonClientError.networkFailure("Invalid HTTP response")
        }
        try mapHttpStatus(http.statusCode)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(MicroLessonCompleteResponse.self, from: data)
    }

    private func mapHttpStatus(_ statusCode: Int) throws {
        switch statusCode {
        case 200...299:
            return
        case 404:
            throw MicroLessonClientError.diagnosisNotFound
        case 422:
            throw MicroLessonClientError.triggerNotEligible
        case 503:
            throw MicroLessonClientError.featureDisabled
        case 500:
            throw MicroLessonClientError.verificationFailed
        default:
            throw MicroLessonClientError.networkFailure("HTTP \(statusCode)")
        }
    }
}

public enum MicroLessonDigestHelper {
    public static func sha256Hex(ofUtf8 text: String) -> String {
        let digest = SHA256.hash(data: Data(text.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
