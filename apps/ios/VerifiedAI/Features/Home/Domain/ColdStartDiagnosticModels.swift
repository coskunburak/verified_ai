import Foundation

public enum ColdStartItemID: String, Codable, CaseIterable, Sendable, Identifiable {
    case numberSense = "DIAG_ITEM_1_NUMBER_SENSE"
    case quadraticInequality = "DIAG_ITEM_2_QUADRATIC_INEQUALITY"
    case functionComposition = "DIAG_ITEM_3_FUNCTION_COMPOSITION"

    public var id: String { rawValue }

    public var localizationTitleKey: String {
        "cold_start.item.\(rawValue).title"
    }

    public var skillCode: String {
        switch self {
        case .numberSense:
            return "MATH.ARITHMETIC.ORDER_OF_OPERATIONS"
        case .quadraticInequality:
            return "MATH.EQUATIONS.INEQUALITIES_BASIC"
        case .functionComposition:
            return "MATH.FUNCTIONS.COMPOSITION"
        }
    }
}

public enum ColdStartExamTarget: String, Codable, CaseIterable, Sendable, Identifiable {
    case tyt = "TYT"
    case ayt = "AYT"
    case lgs = "LGS"
    case sat = "SAT"
    case general = "GENERAL"

    public var id: String { rawValue }
}

public enum ColdStartModality: String, Codable, CaseIterable, Sendable, Identifiable {
    case practiceFirst = "PRACTICE_FIRST"
    case socraticCoach = "SOCRATIC_COACH"
    case mixed = "MIXED"
    case videoHintsOff = "VIDEO_HINTS_OFF"

    public var id: String { rawValue }

    public var localizationKey: String {
        "cold_start.modality.\(rawValue)"
    }
}

public enum ColdStartDiagnosticStatus: String, Codable, Sendable {
    case completed = "COMPLETED"
    case revalidationRequired = "REVALIDATION_REQUIRED"
}

public enum ColdStartBaselineTier: String, Codable, Sendable {
    case foundationSolid = "FOUNDATION_SOLID"
    case calibratedMixed = "CALIBRATED_MIXED"
    case foundationRepair = "FOUNDATION_REPAIR"

    public var localizationKey: String {
        "cold_start.tier.\(rawValue)"
    }
}

public enum ColdStartCalibrationMode: String, Codable, Sendable {
    case exploration = "EXPLORATION"
    case activeCalibrated = "ACTIVE_CALIBRATED"
    case revalidationRequired = "REVALIDATION_REQUIRED"
}

public enum ColdStartConfidenceTier: String, Codable, Sendable {
    case preliminary = "PRELIMINARY"
    case calibrated = "CALIBRATED"

    public var localizationKey: String {
        self == .preliminary ? "cold_start.confidence.preliminary" : "cold_start.confidence.calibrated"
    }
}

public enum ColdStartItemVerdict: String, Codable, Sendable {
    case correctFast = "CORRECT_FAST"
    case correctSlow = "CORRECT_SLOW"
    case trapDistractor = "TRAP_DISTRACTOR"
    case incorrectOther = "INCORRECT_OTHER"
    case timedOut = "TIMED_OUT"
    case skipped = "SKIPPED"
}

public enum ColdStartRationaleCode: String, Codable, Sendable {
    case preliminarySparseEvidence = "PRELIMINARY_SPARSE_EVIDENCE"
    case goalCapacityConflictResolved = "GOAL_CAPACITY_CONFLICT_RESOLVED"
    case inactiveDecayApplied = "INACTIVE_DECAY_APPLIED"
    case explicitPreferenceOverride = "EXPLICIT_PREFERENCE_OVERRIDE"
    case trapSeededWarmup = "TRAP_SEEDED_WARMUP"
    case baselineSolidAccelerated = "BASELINE_SOLID_ACCELERATED"
    case baselineRepairFoundation = "BASELINE_REPAIR_FOUNDATION"
}

public struct ColdStartDiagnosticOption: Identifiable, Sendable, Equatable {
    public let optionId: String
    public let labelLatex: String
    public let accessibilityText: String

    public var id: String { optionId }

    public init(optionId: String, labelLatex: String, accessibilityText: String) {
        self.optionId = optionId
        self.labelLatex = labelLatex
        self.accessibilityText = accessibilityText
    }
}

public struct ColdStartDiagnosticPrompt: Identifiable, Sendable, Equatable {
    public let itemId: ColdStartItemID
    public let promptLatex: String
    public let accessibilityPrompt: String
    public let options: [ColdStartDiagnosticOption]

    public var id: ColdStartItemID { itemId }

    public init(
        itemId: ColdStartItemID,
        promptLatex: String,
        accessibilityPrompt: String,
        options: [ColdStartDiagnosticOption]
    ) {
        self.itemId = itemId
        self.promptLatex = promptLatex
        self.accessibilityPrompt = accessibilityPrompt
        self.options = options
    }
}

public enum ColdStartDiagnosticBank {
    public static let totalTimeLimitSeconds: Int = 180

    public static let prompts: [ColdStartDiagnosticPrompt] = [
        ColdStartDiagnosticPrompt(
            itemId: .numberSense,
            promptLatex: #"\frac{3}{4} - \left(-\frac{2}{5}\right) = ?"#,
            accessibilityPrompt: "Three quarters minus negative two fifths equals what?",
            options: [
                ColdStartDiagnosticOption(optionId: "B", labelLatex: #"\frac{7}{20}"#, accessibilityText: "7 over 20"),
                ColdStartDiagnosticOption(optionId: "A", labelLatex: #"\frac{23}{20}"#, accessibilityText: "23 over 20"),
                ColdStartDiagnosticOption(optionId: "C", labelLatex: #"\frac{5}{9}"#, accessibilityText: "5 over 9"),
                ColdStartDiagnosticOption(optionId: "D", labelLatex: #"\frac{1}{9}"#, accessibilityText: "1 over 9")
            ]
        ),
        ColdStartDiagnosticPrompt(
            itemId: .quadraticInequality,
            promptLatex: #"-x^2 + 5x - 6 \ge 0"#,
            accessibilityPrompt: "Solve negative x squared plus 5 x minus 6 is greater than or equal to 0",
            options: [
                ColdStartDiagnosticOption(optionId: "C", labelLatex: #"(2, 3)"#, accessibilityText: "open interval 2 to 3"),
                ColdStartDiagnosticOption(optionId: "A", labelLatex: #"[2, 3]"#, accessibilityText: "closed interval 2 to 3"),
                ColdStartDiagnosticOption(optionId: "B", labelLatex: #"(-\infty, 2] \cup [3, \infty)"#, accessibilityText: "negative infinity to 2 union 3 to infinity"),
                ColdStartDiagnosticOption(optionId: "D", labelLatex: #"[-3, -2]"#, accessibilityText: "closed interval negative 3 to negative 2")
            ]
        ),
        ColdStartDiagnosticPrompt(
            itemId: .functionComposition,
            promptLatex: #"f(x) = 2x + 3,\; g(x) = x^2 \implies (f \circ g)(3) = ?"#,
            accessibilityPrompt: "Given f of x equals 2 x plus 3 and g of x equals x squared, find f of g of 3",
            options: [
                ColdStartDiagnosticOption(optionId: "D", labelLatex: "15", accessibilityText: "15"),
                ColdStartDiagnosticOption(optionId: "B", labelLatex: "81", accessibilityText: "81"),
                ColdStartDiagnosticOption(optionId: "A", labelLatex: "21", accessibilityText: "21"),
                ColdStartDiagnosticOption(optionId: "C", labelLatex: "24", accessibilityText: "24")
            ]
        )
    ]
}

public struct ColdStartItemSubmission: Codable, Sendable, Equatable {
    public let itemId: ColdStartItemID
    public let selectedOptionId: String?
    public let answer: String?
    public let elapsedSeconds: Int
    public let timedOut: Bool
    public let skipped: Bool

    public init(
        itemId: ColdStartItemID,
        selectedOptionId: String? = nil,
        answer: String? = nil,
        elapsedSeconds: Int,
        timedOut: Bool = false,
        skipped: Bool = false
    ) {
        self.itemId = itemId
        self.selectedOptionId = selectedOptionId
        self.answer = answer
        self.elapsedSeconds = max(0, elapsedSeconds)
        self.timedOut = timedOut
        self.skipped = skipped
    }
}

public struct ColdStartDiagnosticRequest: Codable, Sendable, Equatable {
    public let schemaVersion: String
    public let idempotencyKey: String
    public let locale: String
    public let declaredExamTarget: String?
    public let declaredDailyBudgetMinutes: Int?
    public let preferredModality: String?
    public let explicitPreferenceOverride: Bool?
    public let daysSinceLastVerifiedAttempt: Int?
    public let responses: [ColdStartItemSubmission]

    public init(
        schemaVersion: String = "cold-start-v1",
        idempotencyKey: String,
        locale: String,
        declaredExamTarget: String? = nil,
        declaredDailyBudgetMinutes: Int? = nil,
        preferredModality: String? = nil,
        explicitPreferenceOverride: Bool? = nil,
        daysSinceLastVerifiedAttempt: Int? = nil,
        responses: [ColdStartItemSubmission]
    ) {
        self.schemaVersion = schemaVersion
        self.idempotencyKey = idempotencyKey
        self.locale = locale
        self.declaredExamTarget = declaredExamTarget
        self.declaredDailyBudgetMinutes = declaredDailyBudgetMinutes
        self.preferredModality = preferredModality
        self.explicitPreferenceOverride = explicitPreferenceOverride
        self.daysSinceLastVerifiedAttempt = daysSinceLastVerifiedAttempt
        self.responses = responses
    }
}

public struct ColdStartItemOutcome: Codable, Sendable, Equatable, Identifiable {
    public let itemId: ColdStartItemID
    public let skillCode: String
    public let correct: Bool
    public let verdict: ColdStartItemVerdict
    public let detectedTrapArchetype: String?
    public let assignedPrior: Double
    public let elapsedSeconds: Int

    public var id: ColdStartItemID { itemId }

    public init(
        itemId: ColdStartItemID,
        skillCode: String,
        correct: Bool,
        verdict: ColdStartItemVerdict,
        detectedTrapArchetype: String?,
        assignedPrior: Double,
        elapsedSeconds: Int
    ) {
        self.itemId = itemId
        self.skillCode = skillCode
        self.correct = correct
        self.verdict = verdict
        self.detectedTrapArchetype = detectedTrapArchetype
        self.assignedPrior = assignedPrior
        self.elapsedSeconds = elapsedSeconds
    }
}

public struct ColdStartSkillPrior: Codable, Sendable, Equatable, Identifiable {
    public let skillCode: String
    public let priorProbability: Double
    public let evidenceCount: Int
    public let confidenceTier: ColdStartConfidenceTier
    public let decayApplied: Bool

    public var id: String { skillCode }

    public init(
        skillCode: String,
        priorProbability: Double,
        evidenceCount: Int,
        confidenceTier: ColdStartConfidenceTier,
        decayApplied: Bool
    ) {
        self.skillCode = skillCode
        self.priorProbability = priorProbability
        self.evidenceCount = evidenceCount
        self.confidenceTier = confidenceTier
        self.decayApplied = decayApplied
    }
}

public struct ColdStartRationale: Codable, Sendable, Equatable, Identifiable {
    public let code: ColdStartRationaleCode
    public let localizedMessage: String

    public var id: String { "\(code.rawValue):\(localizedMessage)" }

    public init(code: ColdStartRationaleCode, localizedMessage: String) {
        self.code = code
        self.localizedMessage = localizedMessage
    }
}

public struct ColdStartDiagnosticResult: Codable, Sendable, Equatable {
    public let schemaVersion: String
    public let diagnosticId: UUID
    public let learnerId: UUID
    public let locale: String
    public let status: ColdStartDiagnosticStatus
    public let baselineTier: ColdStartBaselineTier
    public let correctCount: Int
    public let totalItems: Int
    public let calibrationMode: ColdStartCalibrationMode
    public let confidenceTier: ColdStartConfidenceTier
    public let goalConflictResolved: Bool
    public let recommendedDailyBudgetMinutes: Int
    public let inactiveDecayApplied: Bool
    public let explicitPreferenceOverride: Bool
    public let declaredExamTarget: String
    public let declaredDailyBudgetMinutes: Int?
    public let preferredModality: String
    public let revalidationPromptKey: String?
    public let detectedTrapArchetypes: [String]
    public let itemOutcomes: [ColdStartItemOutcome]
    public let initializedPriors: [ColdStartSkillPrior]
    public let rationales: [ColdStartRationale]
    public let attemptDurationSeconds: Int
    public let llmTokensUsed: Int
    public let trainingEligible: Bool
    public let idempotentReplay: Bool
    public let createdAt: String
    public let updatedAt: String

    public init(
        schemaVersion: String = "cold-start-v1",
        diagnosticId: UUID,
        learnerId: UUID,
        locale: String,
        status: ColdStartDiagnosticStatus,
        baselineTier: ColdStartBaselineTier,
        correctCount: Int,
        totalItems: Int,
        calibrationMode: ColdStartCalibrationMode,
        confidenceTier: ColdStartConfidenceTier,
        goalConflictResolved: Bool,
        recommendedDailyBudgetMinutes: Int,
        inactiveDecayApplied: Bool,
        explicitPreferenceOverride: Bool,
        declaredExamTarget: String,
        declaredDailyBudgetMinutes: Int?,
        preferredModality: String,
        revalidationPromptKey: String?,
        detectedTrapArchetypes: [String],
        itemOutcomes: [ColdStartItemOutcome],
        initializedPriors: [ColdStartSkillPrior],
        rationales: [ColdStartRationale],
        attemptDurationSeconds: Int,
        llmTokensUsed: Int = 0,
        trainingEligible: Bool = false,
        idempotentReplay: Bool = false,
        createdAt: String,
        updatedAt: String
    ) {
        self.schemaVersion = schemaVersion
        self.diagnosticId = diagnosticId
        self.learnerId = learnerId
        self.locale = locale
        self.status = status
        self.baselineTier = baselineTier
        self.correctCount = correctCount
        self.totalItems = totalItems
        self.calibrationMode = calibrationMode
        self.confidenceTier = confidenceTier
        self.goalConflictResolved = goalConflictResolved
        self.recommendedDailyBudgetMinutes = recommendedDailyBudgetMinutes
        self.inactiveDecayApplied = inactiveDecayApplied
        self.explicitPreferenceOverride = explicitPreferenceOverride
        self.declaredExamTarget = declaredExamTarget
        self.declaredDailyBudgetMinutes = declaredDailyBudgetMinutes
        self.preferredModality = preferredModality
        self.revalidationPromptKey = revalidationPromptKey
        self.detectedTrapArchetypes = detectedTrapArchetypes
        self.itemOutcomes = itemOutcomes
        self.initializedPriors = initializedPriors
        self.rationales = rationales
        self.attemptDurationSeconds = attemptDurationSeconds
        self.llmTokensUsed = llmTokensUsed
        self.trainingEligible = trainingEligible
        self.idempotentReplay = idempotentReplay
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

public enum ColdStartLocalEvaluator {
    private static let fastLatencyThresholdSeconds = 60

    public static func buildOfflinePreview(
        learnerId: UUID,
        request: ColdStartDiagnosticRequest,
        nowIso: String = ISO8601DateFormatter().string(from: Date())
    ) -> ColdStartDiagnosticResult {
        var outcomes: [ColdStartItemOutcome] = []
        var priors: [ColdStartSkillPrior] = []
        var detectedTraps: [String] = []
        var correctCount = 0
        var totalDuration = 0

        let inactiveDays = request.daysSinceLastVerifiedAttempt ?? 0
        let decayApplied = inactiveDays > 14

        for response in request.responses {
            totalDuration += max(0, response.elapsedSeconds)
            let evaluation = evaluateItem(response)
            if evaluation.correct {
                correctCount += 1
            }
            if let trap = evaluation.trapArchetype, !detectedTraps.contains(trap) {
                detectedTraps.append(trap)
            }
            let adjustedPrior = decayApplied ? max(0.15, (evaluation.prior * 0.85 * 100).rounded() / 100) : evaluation.prior
            outcomes.append(ColdStartItemOutcome(
                itemId: response.itemId,
                skillCode: response.itemId.skillCode,
                correct: evaluation.correct,
                verdict: evaluation.verdict,
                detectedTrapArchetype: evaluation.trapArchetype,
                assignedPrior: adjustedPrior,
                elapsedSeconds: response.elapsedSeconds
            ))
            priors.append(ColdStartSkillPrior(
                skillCode: response.itemId.skillCode,
                priorProbability: adjustedPrior,
                evidenceCount: 1,
                confidenceTier: .preliminary,
                decayApplied: decayApplied
            ))
        }

        let tier: ColdStartBaselineTier
        switch correctCount {
        case 3:
            tier = .foundationSolid
        case 1, 2:
            tier = .calibratedMixed
        default:
            tier = .foundationRepair
        }

        let examTarget = request.declaredExamTarget ?? "GENERAL"
        let budget = request.declaredDailyBudgetMinutes ?? 15
        let conflictResolved = budget <= 15 && ["TYT", "AYT", "SAT"].contains(examTarget)
        let isTurkish = request.locale.lowercased().hasPrefix("tr")

        var rationales: [ColdStartRationale] = [
            ColdStartRationale(
                code: .preliminarySparseEvidence,
                localizedMessage: isTurkish
                    ? "Beceri başına ilk üç doğrulanmış deneme tamamlanana kadar ön kalibrasyon uygulanır."
                    : "Preliminary calibration is active while verified evidence builds across your first three attempts per skill."
            )
        ]

        if conflictResolved {
            rationales.append(ColdStartRationale(
                code: .goalCapacityConflictResolved,
                localizedMessage: isTurkish
                    ? "\(examTarget) hedefin, temponu korumak için günde \(budget) dakikalık tek beceri odak bloklarına bölündü."
                    : "Your \(examTarget) goal has been paced into focused single-skill \(budget)-minute daily blocks so momentum stays sustainable."
            ))
        }

        return ColdStartDiagnosticResult(
            schemaVersion: "cold-start-v1",
            diagnosticId: UUID(),
            learnerId: learnerId,
            locale: request.locale,
            status: .completed,
            baselineTier: tier,
            correctCount: correctCount,
            totalItems: outcomes.count,
            calibrationMode: .exploration,
            confidenceTier: .preliminary,
            goalConflictResolved: conflictResolved,
            recommendedDailyBudgetMinutes: budget,
            inactiveDecayApplied: decayApplied,
            explicitPreferenceOverride: request.explicitPreferenceOverride ?? false,
            declaredExamTarget: examTarget,
            declaredDailyBudgetMinutes: budget,
            preferredModality: request.preferredModality ?? "MIXED",
            revalidationPromptKey: nil,
            detectedTrapArchetypes: detectedTraps,
            itemOutcomes: outcomes,
            initializedPriors: priors,
            rationales: rationales,
            attemptDurationSeconds: totalDuration,
            llmTokensUsed: 0,
            trainingEligible: false,
            idempotentReplay: false,
            createdAt: nowIso,
            updatedAt: nowIso
        )
    }

    private static func evaluateItem(_ response: ColdStartItemSubmission) -> (correct: Bool, verdict: ColdStartItemVerdict, trapArchetype: String?, prior: Double) {
        if response.timedOut {
            return (false, .timedOut, nil, 0.20)
        }
        if response.skipped {
            return (false, .skipped, nil, 0.20)
        }

        let option = response.selectedOptionId?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if option == "A" {
            if response.elapsedSeconds <= fastLatencyThresholdSeconds {
                return (true, .correctFast, nil, 0.65)
            }
            return (true, .correctSlow, nil, 0.50)
        }

        switch (response.itemId, option) {
        case (.numberSense, "B"):
            return (false, .trapDistractor, "SIGN_INVERSION_TRAP", 0.15)
        case (.numberSense, "C"):
            return (false, .trapDistractor, "FRACTION_DENOMINATOR_TRAP", 0.15)
        case (.quadraticInequality, "B"):
            return (false, .trapDistractor, "INEQUALITY_POLARITY_TRAP", 0.15)
        case (.quadraticInequality, "C"):
            return (false, .trapDistractor, "ROOT_BOUNDARY_TRAP", 0.15)
        case (.functionComposition, "B"):
            return (false, .trapDistractor, "COMPOSITION_ORDER_TRAP", 0.15)
        case (.functionComposition, "C"):
            return (false, .trapDistractor, "COMPOSITION_LINEARITY_TRAP", 0.15)
        default:
            return (false, .incorrectOther, nil, 0.20)
        }
    }
}
