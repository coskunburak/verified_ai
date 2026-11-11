import Foundation

public enum SkillMasteryStatus: String, Codable, Sendable, CaseIterable {
    case introduced = "INTRODUCED"
    case practicing = "PRACTICING"
    case mastered = "MASTERED"
    case atRiskRemediationRequired = "AT_RISK_REMEDIATION_REQUIRED"

    public var localizedKey: String {
        switch self {
        case .introduced: return "mastery.graph.introduced"
        case .practicing: return "mastery.graph.practicing"
        case .mastered: return "mastery.graph.mastered"
        case .atRiskRemediationRequired: return "mastery.graph.at_risk"
        }
    }
}

public struct SkillGraphNode: Codable, Sendable, Identifiable, Equatable {
    public var id: String { skillCode }
    public let skillCode: String
    public let displayName: String
    public let generation: Int
    public let prerequisiteSkillCodes: [String]
    public let effectiveMastery: Double
    public let status: SkillMasteryStatus
    public let directPracticeCount: Int
    public let consecutiveFailures: Int
    public let blockedByPrerequisiteBottleneck: Bool

    public init(
        skillCode: String,
        displayName: String,
        generation: Int,
        prerequisiteSkillCodes: [String],
        effectiveMastery: Double,
        status: SkillMasteryStatus,
        directPracticeCount: Int,
        consecutiveFailures: Int,
        blockedByPrerequisiteBottleneck: Bool
    ) {
        self.skillCode = skillCode
        self.displayName = displayName
        self.generation = generation
        self.prerequisiteSkillCodes = prerequisiteSkillCodes
        self.effectiveMastery = effectiveMastery
        self.status = status
        self.directPracticeCount = directPracticeCount
        self.consecutiveFailures = consecutiveFailures
        self.blockedByPrerequisiteBottleneck = blockedByPrerequisiteBottleneck
    }
}

public struct SkillGraphEdge: Codable, Sendable, Identifiable, Equatable {
    public var id: String { "\(fromSkillCode)->\(toSkillCode)" }
    public let fromSkillCode: String
    public let toSkillCode: String
    public let edgeWeight: Double
    public let prerequisiteBottleneck: Bool

    public init(
        fromSkillCode: String,
        toSkillCode: String,
        edgeWeight: Double,
        prerequisiteBottleneck: Bool
    ) {
        self.fromSkillCode = fromSkillCode
        self.toSkillCode = toSkillCode
        self.edgeWeight = edgeWeight
        self.prerequisiteBottleneck = prerequisiteBottleneck
    }
}

public struct SkillGraphProjection: Codable, Sendable, Equatable {
    public let curriculumCode: String
    public let nodes: [SkillGraphNode]
    public let edges: [SkillGraphEdge]
    public let generatedAt: Date

    public init(
        curriculumCode: String,
        nodes: [SkillGraphNode],
        edges: [SkillGraphEdge],
        generatedAt: Date
    ) {
        self.curriculumCode = curriculumCode
        self.nodes = nodes
        self.edges = edges
        self.generatedAt = generatedAt
    }
}

public protocol SkillGraphServicing: Sendable {
    func fetchGraph(curriculum: String) async throws -> SkillGraphProjection
}
