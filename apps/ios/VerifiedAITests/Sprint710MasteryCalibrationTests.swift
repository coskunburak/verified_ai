// apps/ios/VerifiedAITests/Sprint710MasteryCalibrationTests.swift
import XCTest
@testable import VerifiedAI

final class Sprint710MasteryCalibrationTests: XCTestCase {

    // MARK: - Test 1: Calibration Summary Model Decoding
    func testCalibrationSummaryDecodingFromValidFixture() throws {
        let fixtureJSON = """
        {
          "schemaVersion": "1.0.0",
          "evaluatedAt": "2026-11-11T12:00:00Z",
          "canonicalSkillCodeFilter": null,
          "windowDays": 30,
          "sampleSize": 250,
          "brierScore": 0.13840,
          "brierThreshold": 0.2,
          "expectedCalibrationError": 0.04120,
          "eceThreshold": 0.08,
          "signedCalibrationBias": 0.01250,
          "calibrationStatus": "CALIBRATED",
          "gatePassed": true,
          "reliabilityBins": [
            {
              "binIndex": 1,
              "lowerBound": 0.0,
              "upperBound": 0.1,
              "sampleCount": 15,
              "meanPredictedConfidence": 0.0750,
              "observedIndependentAccuracy": 0.0667,
              "absoluteCalibrationGap": 0.0083
            },
            {
              "binIndex": 2,
              "lowerBound": 0.1,
              "upperBound": 0.2,
              "sampleCount": 20,
              "meanPredictedConfidence": 0.1550,
              "observedIndependentAccuracy": 0.1500,
              "absoluteCalibrationGap": 0.0050
            },
            {
              "binIndex": 3,
              "lowerBound": 0.2,
              "upperBound": 0.3,
              "sampleCount": 25,
              "meanPredictedConfidence": 0.2520,
              "observedIndependentAccuracy": 0.2400,
              "absoluteCalibrationGap": 0.0120
            },
            {
              "binIndex": 4,
              "lowerBound": 0.3,
              "upperBound": 0.4,
              "sampleCount": 22,
              "meanPredictedConfidence": 0.3480,
              "observedIndependentAccuracy": 0.3636,
              "absoluteCalibrationGap": 0.0156
            },
            {
              "binIndex": 5,
              "lowerBound": 0.4,
              "upperBound": 0.5,
              "sampleCount": 30,
              "meanPredictedConfidence": 0.4520,
              "observedIndependentAccuracy": 0.4667,
              "absoluteCalibrationGap": 0.0147
            },
            {
              "binIndex": 6,
              "lowerBound": 0.5,
              "upperBound": 0.6,
              "sampleCount": 28,
              "meanPredictedConfidence": 0.5510,
              "observedIndependentAccuracy": 0.5714,
              "absoluteCalibrationGap": 0.0204
            },
            {
              "binIndex": 7,
              "lowerBound": 0.6,
              "upperBound": 0.7,
              "sampleCount": 32,
              "meanPredictedConfidence": 0.6490,
              "observedIndependentAccuracy": 0.6250,
              "absoluteCalibrationGap": 0.0240
            },
            {
              "binIndex": 8,
              "lowerBound": 0.7,
              "upperBound": 0.8,
              "sampleCount": 35,
              "meanPredictedConfidence": 0.7480,
              "observedIndependentAccuracy": 0.7714,
              "absoluteCalibrationGap": 0.0234
            },
            {
              "binIndex": 9,
              "lowerBound": 0.8,
              "upperBound": 0.9,
              "sampleCount": 25,
              "meanPredictedConfidence": 0.8450,
              "observedIndependentAccuracy": 0.8800,
              "absoluteCalibrationGap": 0.0350
            },
            {
              "binIndex": 10,
              "lowerBound": 0.9,
              "upperBound": 1.0,
              "sampleCount": 18,
              "meanPredictedConfidence": 0.9420,
              "observedIndependentAccuracy": 0.9444,
              "absoluteCalibrationGap": 0.0024
            }
          ],
          "skillSlices": [
            {
              "canonicalSkillCode": "ALG_LIN_EQ_01",
              "sampleSize": 120,
              "brierScore": 0.12500,
              "expectedCalibrationError": 0.03800,
              "calibrationStatus": "CALIBRATED",
              "gatePassed": true
            }
          ]
        }
        """.data(using: .utf8)!

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        let summary = try decoder.decode(MasteryCalibrationSummaryModel.self, from: fixtureJSON)

        XCTAssertEqual(summary.schemaVersion, "1.0.0")
        XCTAssertEqual(summary.sampleSize, 250)
        XCTAssertEqual(summary.brierScore, 0.13840)
        XCTAssertEqual(summary.expectedCalibrationError, 0.04120)
        XCTAssertEqual(summary.calibrationStatus, .calibrated)
        XCTAssertTrue(summary.gatePassed)
        XCTAssertEqual(summary.reliabilityBins.count, 10)
        XCTAssertEqual(summary.skillSlices.count, 1)
        XCTAssertEqual(summary.skillSlices.first?.canonicalSkillCode, "ALG_LIN_EQ_01")
    }

    // MARK: - Test 2: Dual-Ring Retention Decay Threshold Calculation
    func testDualRingRetentionDecayThreshold() {
        // High mastery (M >= 0.65) with retention decayed below 0.60 triggers warning
        let decayedMetrics = SkillFiveMetricsSnapshot(
            observedOutcomeScore: 1.0,
            independentCompetenceScore: 0.80,
            posteriorMasteryProbability: 0.85,
            retentionProbability: 0.52,
            scaffoldDependencyIndex: 0.10
        )
        let isDecayedAtRisk = (decayedMetrics.posteriorMasteryProbability >= 0.65)
            && (decayedMetrics.retentionProbability < 0.60)
        XCTAssertTrue(isDecayedAtRisk, "Retention probability < 0.60 on mastered skill must trigger warning")

        // Stable retention (R >= 0.60)
        let stableMetrics = SkillFiveMetricsSnapshot(
            observedOutcomeScore: 1.0,
            independentCompetenceScore: 0.85,
            posteriorMasteryProbability: 0.85,
            retentionProbability: 0.78,
            scaffoldDependencyIndex: 0.10
        )
        let isStableAtRisk = (stableMetrics.posteriorMasteryProbability >= 0.65)
            && (stableMetrics.retentionProbability < 0.60)
        XCTAssertFalse(isStableAtRisk, "Retention probability >= 0.60 must remain stable")
    }

    // MARK: - Test 3: DOWNSTREAM_PROPAGATION Non-Penalizing Presentation State
    func testDownstreamPropagationNonPenalizingPresentation() {
        let downstreamRole = MathHighlightRole.downstreamPropagation
        XCTAssertFalse(downstreamRole.isPenalizing, "DOWNSTREAM_PROPAGATION steps must not be penalizing")

        let trapRole = MathHighlightRole.trapStepDiff
        XCTAssertTrue(trapRole.isPenalizing, "Direct trap step diff must be penalizing")

        let contradictionRole = MathHighlightRole.contradiction
        XCTAssertTrue(contradictionRole.isPenalizing, "Contradiction step must be penalizing")

        let correctRole = MathHighlightRole.verifiedCorrect
        XCTAssertFalse(correctRole.isPenalizing, "Verified correct steps must not be penalizing")
    }

    // MARK: - Test 4: Session Logout Local Cache Purge Completeness
    func testSessionLogoutCachePurgeCompleteness() async {
        let cacheStore = MistakePassportCacheStore()

        let sampleResponse = MistakePassportResponseDto(
            schemaVersion: "mistake-passport.v1",
            learnerId: UUID(),
            generatedAt: Date(),
            summaryMetrics: PassportSummaryMetrics(
                conqueredExamTrapsCount: 3,
                activeExamTrapsCount: 1,
                pendingRetentionVerificationCount: 0,
                independentTransferRate: 0.80,
                meanPosteriorMastery: 0.75,
                meanRetentionProbability: 0.70
            ),
            conceptualTraps: [],
            arithmeticSlips: []
        )

        await cacheStore.savePassportSnapshot(sampleResponse, statusFilter: nil)
        let cachedBefore = await cacheStore.loadCachedPassportSnapshot(statusFilter: nil)
        XCTAssertNotNil(cachedBefore, "Cache should hold snapshot before logout purge")

        // Perform session logout cache eviction
        await cacheStore.purgeAllCachedPassports()

        let cachedAfter = await cacheStore.loadCachedPassportSnapshot(statusFilter: nil)
        XCTAssertNil(cachedAfter, "All cached snapshots must be purged on session logout")
    }
}
