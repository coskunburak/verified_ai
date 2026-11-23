import Foundation

public enum AnalyticsSanitizationError: Error, Equatable, Sendable {
    case eventUnknown
    case sourceForbidden
    case trainingEligibleForbidden
    case invalidClientEventId
    case invalidSessionRefHash
    case tooManyProperties
    case propertyKeyNotAllowed(String)
    case piiOrRawContentRejected
    case propertiesPayloadTooLarge
}

/// Defense-in-depth client sanitizer mirroring server INV-ANL-001..003 and ADR-006 bounds.
public struct AnalyticsPrivacySanitizer: Sendable {

    public static let maxPropertyCount = 16
    public static let maxPropertiesBytes = 2048
    public static let maxPropertyValueLength = 80

    public static let clientAllowedPropertyAllowlists: [String: Set<String>] = [
        "onboarding_started": ["authMethod", "locale", "educationLevel", "dailyBudgetMinutes", "latencyBucket"],
        "onboarding_completed": ["authMethod", "locale", "educationLevel", "dailyBudgetMinutes", "latencyBucket"],
        "sign_in_completed": ["authMethod", "locale", "educationLevel", "dailyBudgetMinutes", "latencyBucket"],
        "problem_capture_started": ["captureSource", "assetKind", "qualityWarningCode", "latencyBucket"],
        "problem_capture_source_selected": ["captureSource", "assetKind", "qualityWarningCode", "latencyBucket"],
        "problem_capture_quality_warning": ["captureSource", "assetKind", "qualityWarningCode", "latencyBucket"],
        "problem_capture_accepted": ["captureSource", "assetKind", "qualityWarningCode", "latencyBucket"],
        "first_verified_solution_viewed": ["problemType", "primarySkillCode", "difficultyBand", "verificationStatus", "latencyBucket", "entitlementTier"],
        "verification_details_viewed": ["problemType", "primarySkillCode", "difficultyBand", "verificationStatus", "entitlementTier"],
        "solution_alternative_viewed": ["problemType", "primarySkillCode", "difficultyBand", "verificationStatus", "entitlementTier"],
        "tutor_session_started": ["hintLevel", "pedagogicalPhase", "turnCountBucket", "entitlementTier", "latencyBucket"],
        "tutor_turn_submitted": ["hintLevel", "pedagogicalPhase", "turnCountBucket", "entitlementTier", "latencyBucket"],
        "hint_requested": ["hintLevel", "pedagogicalPhase", "turnCountBucket", "entitlementTier", "latencyBucket"],
        "answer_revealed": ["hintLevel", "pedagogicalPhase", "turnCountBucket", "entitlementTier", "latencyBucket"],
        "tutor_session_completed": ["hintLevel", "pedagogicalPhase", "turnCountBucket", "entitlementTier", "latencyBucket"],
        "mistake_reviewed": ["canonicalSkillCode", "mistakeCode", "fromBand", "toBand", "evidenceClass", "slotRole", "durationBucket", "entitlementTier"],
        "practice_session_started": ["canonicalSkillCode", "mistakeCode", "fromBand", "toBand", "evidenceClass", "slotRole", "durationBucket", "entitlementTier"],
        "exam_readiness_viewed": ["examCode", "jurisdiction", "confidenceTier", "mode", "durationBucket", "entitlementTier"],
        "paywall_viewed": ["placement", "packageCode", "tierCode", "storefront", "failureReason", "entitlementTier"],
        "purchase_started": ["placement", "packageCode", "tierCode", "storefront", "failureReason", "entitlementTier"],
        "purchase_failed": ["placement", "packageCode", "tierCode", "storefront", "failureReason", "entitlementTier"],
        "restore_completed": ["placement", "packageCode", "tierCode", "storefront", "failureReason", "entitlementTier"],
        "paywall.mistake_moment_shown": ["placement", "packageCode", "tierCode", "storefront", "failureReason", "entitlementTier"],
        "paywall.twin_success_shown": ["placement", "packageCode", "tierCode", "storefront", "failureReason", "entitlementTier"],
        "lifecycle_notification_opened": ["category", "intentType", "weekIso", "locale", "entitlementTier"],
        "weekly_digest_viewed": ["category", "intentType", "weekIso", "locale", "entitlementTier"],
    ]

    private static let serverOnlyEventNames: Set<String> = [
        "problem_asset_uploaded",
        "problem_recognition_completed",
        "problem_parse_completed",
        "problem_parse_review_required",
        "problem_parse_unsupported",
        "first_solution_completed",
        "solve_requested",
        "solve_completed",
        "solve_failed",
        "parse_corrected",
        "attempt_submitted",
        "mistake_detected",
        "practice_session_completed",
        "mastery_band_changed",
        "study_plan_item_completed",
        "mistake_passport.item_created",
        "mistake_passport.state_transitioned",
        "twin_drill.issued",
        "twin_drill.completed",
        "retrieval_check.scheduled",
        "retrieval_check.completed",
        "daily_coach.plan_generated",
        "daily_coach.item_completed",
        "daily_coach.plan_rebalanced",
        "exam_target_configured",
        "exam_mock_started",
        "exam_mock_submitted",
        "purchase_completed",
        "entitlement_changed",
        "paywall.trial_started",
    ]

    private static let forbiddenExactNormalizedKeys: Set<String> = [
        "email", "studentemail", "emailaddress",
        "phone", "phonenumber", "mobile",
        "studentname", "fullname", "firstname", "lastname", "username",
        "devicetoken", "pushtoken", "token", "accesstoken", "refreshtoken",
        "jwt", "authorization", "cookie", "password", "secret", "jws", "signedpayload",
        "rawproblemtext", "problemtext", "ocrtext", "recognizedtext",
        "latex", "rawlatex", "solutionsteps", "prompt", "systemprompt", "rawprovideroutput",
        "imagebase64", "imagebytes", "pdfbytes",
        "objectkey", "storagekey", "presignedurl", "signedurl", "filename", "localpath",
        "boundingbox", "checksum",
        "exactmasteryfloat", "masteryscore",
    ]

    private static let forbiddenKeyTokens: [String] = [
        "email", "phone", "password", "secret", "jwt", "token", "authorization",
        "signedpayload", "rawproblem", "problemtext", "ocrtext", "recognizedtext",
        "rawlatex", "latex", "solutionsteps", "systemprompt", "rawprovider",
        "imagebase64", "imagebytes", "pdfbytes", "objectkey", "storagekey",
        "presignedurl", "signedurl", "localpath", "boundingbox", "checksum",
        "exactmastery", "masteryscore",
    ]

    private static let clientEventIdPattern = "^[A-Za-z0-9:._-]{8,128}$"
    private static let sha256HexPattern = "^[0-9a-f]{64}$"
    private static let emailValuePattern = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
    private static let jwtValuePattern = "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*"
    private static let urlValuePattern = "https?://"
    private static let rawLatexPattern = "\\\\(?:frac|int|sqrt|sum|lim|prod|begin|end)\\b|\\$\\$"

    public static func validate(_ dto: AnalyticsEventIngestRequestDTO) throws -> AnalyticsEventIngestRequestDTO {
        if serverOnlyEventNames.contains(dto.eventName) || dto.emittedBy != "CLIENT" || dto.sourceModule != "ios_client" {
            throw AnalyticsSanitizationError.sourceForbidden
        }
        guard let allowedKeys = clientAllowedPropertyAllowlists[dto.eventName] else {
            throw AnalyticsSanitizationError.eventUnknown
        }
        if dto.trainingEligible {
            throw AnalyticsSanitizationError.trainingEligibleForbidden
        }
        if dto.clientEventId.range(of: clientEventIdPattern, options: .regularExpression) == nil {
            throw AnalyticsSanitizationError.invalidClientEventId
        }
        if let hash = dto.sessionRefHash, hash.range(of: sha256HexPattern, options: .regularExpression) == nil {
            throw AnalyticsSanitizationError.invalidSessionRefHash
        }
        if dto.properties.count > maxPropertyCount {
            throw AnalyticsSanitizationError.tooManyProperties
        }

        for (key, value) in dto.properties {
            let normalizedKey = String(key.lowercased().unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) })
            if forbiddenExactNormalizedKeys.contains(normalizedKey)
                || forbiddenKeyTokens.contains(where: { normalizedKey.contains($0) }) {
                throw AnalyticsSanitizationError.piiOrRawContentRejected
            }
            if !allowedKeys.contains(key) {
                throw AnalyticsSanitizationError.propertyKeyNotAllowed(key)
            }
            if case .string(let str) = value {
                if str.count > maxPropertyValueLength
                    || str.range(of: emailValuePattern, options: .regularExpression) != nil
                    || str.range(of: jwtValuePattern, options: .regularExpression) != nil
                    || str.range(of: urlValuePattern, options: [.regularExpression, .caseInsensitive]) != nil
                    || str.range(of: rawLatexPattern, options: .regularExpression) != nil {
                    throw AnalyticsSanitizationError.piiOrRawContentRejected
                }
            } else if case .double(let d) = value, !d.isFinite {
                throw AnalyticsSanitizationError.piiOrRawContentRejected
            }
        }

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        if let jsonData = try? encoder.encode(dto.properties), jsonData.count > maxPropertiesBytes {
            throw AnalyticsSanitizationError.propertiesPayloadTooLarge
        }

        return dto
    }

    public static func buildValidatedDTO(
        from event: AnalyticsClientEvent,
        clientEventId: String = UUID().uuidString.lowercased(),
        appVersion: String? = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
        sessionRefHash: String? = nil,
        latencyMs: Int? = nil,
        occurredAt: Date = Date()
    ) throws -> AnalyticsEventIngestRequestDTO {
        let dto = AnalyticsEventIngestRequestDTO(
            clientEventId: clientEventId,
            eventName: event.eventName,
            emittedBy: "CLIENT",
            sourceModule: "ios_client",
            entitlementTier: event.entitlementTier.rawValue,
            locale: event.locale.rawValue,
            appVersion: sanitizeAppVersion(appVersion),
            sessionRefHash: sessionRefHash,
            latencyMs: latencyMs.map { max(0, min($0, 600_000)) },
            properties: event.properties,
            occurredAt: occurredAt,
            trainingEligible: false
        )
        return try validate(dto)
    }

    private static func sanitizeAppVersion(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.count <= 32 else { return nil }
        let allowed = CharacterSet(charactersIn: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-")
        guard trimmed.unicodeScalars.allSatisfy({ allowed.contains($0) }) else { return nil }
        return trimmed
    }
}
