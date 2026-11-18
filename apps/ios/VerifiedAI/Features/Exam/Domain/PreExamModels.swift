import Foundation

struct PreExamCard: Codable, Sendable, Identifiable {
    let catalogId, skillCode, titleTr, titleEn, ruleLatex, ruleDisplay, domainTr, domainEn: String
    let mistakeCode, counterExampleLatex, counterExampleDisplay, quickCheckTr, quickCheckEn, answerDisplay: String?
    var id: String { catalogId }
}
enum PreExamTimePrecision: String, Codable, Sendable { case exact = "EXACT", dateOnly = "DATE_ONLY_ESTIMATE", unavailable = "UNAVAILABLE" }
enum PreExamWindowStatus: String, Codable, Sendable {
    case active = "ACTIVE_48H_WINDOW", early = "EARLY_PREVIEW_OUTSIDE_48H", dateOnly = "DATE_ONLY_ESTIMATE"
    case noDate = "NO_TARGET_DATE", passed = "EXAM_PASSED"
}
enum PreExamEvidenceStatus: String, Codable, Sendable { case sufficient = "SUFFICIENT_EVIDENCE", insufficient = "INSUFFICIENT_EVIDENCE" }
struct PreExamSnapshot: Codable, Sendable {
    let schemaVersion: String
    let sessionId, userId: UUID
    let examCode: String
    let generatedAt, expiresAt: Date
    let algorithmVersion, catalogVersion, catalogContentHash, etag: String
    let targetExamDate, targetExamAt: String?
    let timePrecision: PreExamTimePrecision
    let windowStatus: PreExamWindowStatus
    let evidenceStatus: PreExamEvidenceStatus
    let hoursRemaining: Double?
    let panicInhibitionActive: Bool
    let masteryFloor: Double
    let excludedLowMasteryTopicCount: Int
    let activeTraps, highYieldFormulas, genericReferences: [PreExamCard]
    let llmTokensUsed: Int
    let trainingEligible: Bool

    func validate(user: UUID, exam: String, schema: String, now: Date = Date()) throws {
        guard userId == user, examCode == exam, schemaVersion == schema,
              ["TR_YKS_TYT_MATH", "TR_YKS_AYT_MATH"].contains(exam),
              !trainingEligible, llmTokensUsed == 0, panicInhibitionActive, masteryFloor == 0.5,
              algorithmVersion == "H15_V1", catalogVersion == "pre-exam-catalog-v1", excludedLowMasteryTopicCount >= 0,
              catalogContentHash.range(of: "^[0-9a-f]{64}$", options: .regularExpression) != nil,
              activeTraps.allSatisfy({ $0.mistakeCode != nil && $0.counterExampleDisplay != nil && $0.quickCheckTr != nil && $0.quickCheckEn != nil && $0.answerDisplay != nil }),
              expiresAt > now, expiresAt > generatedAt, expiresAt.timeIntervalSince(generatedAt) <= 86400,
              activeTraps.count <= 10, highYieldFormulas.count <= 12, genericReferences.count <= 5,
              etag.range(of: "^[0-9a-f]{64}$", options: .regularExpression) != nil,
              timePrecision == .exact || (hoursRemaining == nil && targetExamAt == nil),
              evidenceStatus != .insufficient || (activeTraps.isEmpty && highYieldFormulas.isEmpty)
        else { throw PreExamError.integrity }
        if timePrecision == .exact {
            guard let hoursRemaining, hoursRemaining.isFinite, hoursRemaining >= 0, targetExamAt != nil else { throw PreExamError.integrity }
        }
        if timePrecision == .dateOnly && targetExamDate == nil { throw PreExamError.integrity }
        if windowStatus == .active {
            guard timePrecision == .exact, let hoursRemaining, hoursRemaining > 0, hoursRemaining <= 48 else { throw PreExamError.integrity }
        }
        if windowStatus == .early {
            guard timePrecision == .exact, let hoursRemaining, hoursRemaining > 48 else { throw PreExamError.integrity }
        }
        if windowStatus == .noDate && (targetExamDate != nil || timePrecision != .unavailable) { throw PreExamError.integrity }
    }
}
struct PreExamBundle: Codable, Sendable {
    let review, sheet: PreExamSnapshot
    func validate(user: UUID, exam: String, now: Date = Date()) throws {
        try review.validate(user: user, exam: exam, schema: "pre-exam-review-v1", now: now)
        try sheet.validate(user: user, exam: exam, schema: "exam-cheat-sheet-v1", now: now)
        guard review.sessionId == sheet.sessionId, review.catalogContentHash == sheet.catalogContentHash else { throw PreExamError.integrity }
    }
}
enum PreExamError: Error { case integrity, cache, unavailable }
protocol PreExamServicing: Sendable {
    func preExam(exam: String, sheet: Bool, etag: String?) async throws -> ConditionalHTTPResponse<PreExamSnapshot>
    func exported(session: UUID, key: String) async throws
}
