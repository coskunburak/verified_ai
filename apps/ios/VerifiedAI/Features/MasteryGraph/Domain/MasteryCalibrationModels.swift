// apps/ios/VerifiedAI/Features/MasteryGraph/Domain/MasteryCalibrationModels.swift
import Foundation

public enum CalibrationRegimeStatus: String, Codable, Sendable {
    case calibrated = "CALIBRATED"
    case overconfident = "OVERCONFIDENT"
    case underconfident = "UNDERCONFIDENT"
}

public struct ReliabilityBinModel: Codable, Sendable, Identifiable, Equatable {
    public var id: Int { binIndex }
    public let binIndex: Int
    public let lowerBound: Double
    public let upperBound: Double
    public let sampleCount: Int
    public let meanPredictedConfidence: Double
    public let observedIndependentAccuracy: Double
    public let absoluteCalibrationGap: Double

    public init(
        binIndex: Int,
        lowerBound: Double,
        upperBound: Double,
        sampleCount: Int,
        meanPredictedConfidence: Double,
        observedIndependentAccuracy: Double,
        absoluteCalibrationGap: Double
    ) {
        self.binIndex = binIndex
        self.lowerBound = lowerBound
        self.upperBound = upperBound
        self.sampleCount = sampleCount
        self.meanPredictedConfidence = meanPredictedConfidence
        self.observedIndependentAccuracy = observedIndependentAccuracy
        self.absoluteCalibrationGap = absoluteCalibrationGap
    }
}

public struct SkillCalibrationSliceModel: Codable, Sendable, Identifiable, Equatable {
    public var id: String { canonicalSkillCode }
    public let canonicalSkillCode: String
    public let sampleSize: Int
    public let brierScore: Double
    public let expectedCalibrationError: Double
    public let calibrationStatus: CalibrationRegimeStatus
    public let gatePassed: Bool

    public init(
        canonicalSkillCode: String,
        sampleSize: Int,
        brierScore: Double,
        expectedCalibrationError: Double,
        calibrationStatus: CalibrationRegimeStatus,
        gatePassed: Bool
    ) {
        self.canonicalSkillCode = canonicalSkillCode
        self.sampleSize = sampleSize
        self.brierScore = brierScore
        self.expectedCalibrationError = expectedCalibrationError
        self.calibrationStatus = calibrationStatus
        self.gatePassed = gatePassed
    }
}

public struct MasteryCalibrationSummaryModel: Codable, Sendable, Equatable {
    public let schemaVersion: String
    public let evaluatedAt: Date
    public let canonicalSkillCodeFilter: String?
    public let windowDays: Int
    public let sampleSize: Int
    public let brierScore: Double
    public let brierThreshold: Double
    public let expectedCalibrationError: Double
    public let eceThreshold: Double
    public let signedCalibrationBias: Double
    public let calibrationStatus: CalibrationRegimeStatus
    public let gatePassed: Bool
    public let reliabilityBins: [ReliabilityBinModel]
    public let skillSlices: [SkillCalibrationSliceModel]

    public init(
        schemaVersion: String = "1.0.0",
        evaluatedAt: Date,
        canonicalSkillCodeFilter: String? = nil,
        windowDays: Int = 30,
        sampleSize: Int,
        brierScore: Double,
        brierThreshold: Double = 0.20,
        expectedCalibrationError: Double,
        eceThreshold: Double = 0.08,
        signedCalibrationBias: Double,
        calibrationStatus: CalibrationRegimeStatus,
        gatePassed: Bool,
        reliabilityBins: [ReliabilityBinModel],
        skillSlices: [SkillCalibrationSliceModel]
    ) {
        self.schemaVersion = schemaVersion
        self.evaluatedAt = evaluatedAt
        self.canonicalSkillCodeFilter = canonicalSkillCodeFilter
        self.windowDays = windowDays
        self.sampleSize = sampleSize
        self.brierScore = brierScore
        self.brierThreshold = brierThreshold
        self.expectedCalibrationError = expectedCalibrationError
        self.eceThreshold = eceThreshold
        self.signedCalibrationBias = signedCalibrationBias
        self.calibrationStatus = calibrationStatus
        self.gatePassed = gatePassed
        self.reliabilityBins = reliabilityBins
        self.skillSlices = skillSlices
    }
}
