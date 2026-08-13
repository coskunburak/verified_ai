#!/usr/bin/env python3
from __future__ import annotations

import statistics
from typing import Any


def rate(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 1.0
    return numerator / denominator


def percentile(values: list[float], percentile_value: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    index = (len(ordered) - 1) * percentile_value
    lower = int(index)
    upper = min(lower + 1, len(ordered) - 1)
    fraction = index - lower
    return ordered[lower] + (ordered[upper] - ordered[lower]) * fraction


def score_canonicalization(cases: list[dict[str, Any]]) -> dict[str, float | int]:
    count = len(cases)
    supported_expected = 0
    supported_success = 0
    unsupported_expected = 0
    unsupported_rejected = 0
    ast_exact = 0
    schema_valid = 0
    repeatable = 0
    idempotent = 0
    unsafe_expected = 0
    unsafe_rejected = 0
    selected_parse_correct = 0
    for case in cases:
        expected = case["expected"].get("canonical", {})
        observed = case.get("fixtureObservation", {}).get("canonical", {})
        if expected.get("status") == "SUPPORTED":
            supported_expected += 1
            if observed.get("status") == "SUPPORTED":
                supported_success += 1
        if expected.get("status") in {"UNSUPPORTED_CURRENT_SCOPE", "NOT_AUTHORITATIVE", "FAILURE", "NOT_REACHED"}:
            unsupported_expected += 1
            if observed.get("status") == expected.get("status"):
                unsupported_rejected += 1
        if observed.get("ast") == expected.get("ast"):
            ast_exact += 1
        if observed.get("schemaValid", True):
            schema_valid += 1
        if observed.get("repeatable", True):
            repeatable += 1
        if observed.get("idempotent", True):
            idempotent += 1
        if expected.get("unsafeRejected"):
            unsafe_expected += 1
            if observed.get("unsafeRejected"):
                unsafe_rejected += 1
        if observed.get("selectedParseSource") == expected.get("selectedParseSource") and observed.get("selectedParseRevision") == expected.get("selectedParseRevision"):
            selected_parse_correct += 1
    return {
        "supportedSuccessRate": rate(supported_success, supported_expected),
        "unsupportedRejectionRate": rate(unsupported_rejected, unsupported_expected),
        "astExactStructureRate": rate(ast_exact, count),
        "schemaValidRate": rate(schema_valid, count),
        "unsafeRejectionRate": rate(unsafe_rejected, unsafe_expected),
        "repeatabilityRate": rate(repeatable, count),
        "idempotencyRate": rate(idempotent, count),
        "selectedParseCorrectnessRate": rate(selected_parse_correct, count),
    }


def score_classification(cases: list[dict[str, Any]]) -> dict[str, float | int]:
    count = len(cases)
    status = 0
    primary = 0
    difficulty = 0
    subject = 0
    topic = 0
    secondary_precision_sum = 0.0
    secondary_recall_sum = 0.0
    confidence_policy = 0
    false_authoritative = 0
    for case in cases:
        expected = case["expected"].get("classification", {})
        observed = case.get("fixtureObservation", {}).get("classification", {})
        if observed.get("status") == expected.get("status"):
            status += 1
        if observed.get("primarySkillId") == expected.get("primarySkillId"):
            primary += 1
        expected_secondary = set(expected.get("secondarySkillIds", []) or [])
        observed_secondary = set(observed.get("secondarySkillIds", []) or [])
        secondary_precision_sum += rate(len(expected_secondary & observed_secondary), len(observed_secondary))
        secondary_recall_sum += rate(len(expected_secondary & observed_secondary), len(expected_secondary))
        if observed.get("difficulty") == expected.get("difficulty"):
            difficulty += 1
        parse_expected = case["expected"].get("parse", {})
        if observed.get("subjectId") == parse_expected.get("subjectId"):
            subject += 1
        if observed.get("topicId") == parse_expected.get("topicId"):
            topic += 1
        if observed.get("confidenceCalibration") == "UNCALIBRATED":
            confidence_policy += 1
        if case.get("fixtureObservation", {}).get("failureFlags", {}).get("falseAuthoritativeAccept"):
            false_authoritative += 1
    return {
        "statusExactAccuracy": rate(status, count),
        "primarySkillAccuracy": rate(primary, count),
        "secondarySkillPrecision": rate(secondary_precision_sum, count),
        "secondarySkillRecall": rate(secondary_recall_sum, count),
        "difficultyAccuracy": rate(difficulty, count),
        "subjectAccuracy": rate(subject, count),
        "topicAccuracy": rate(topic, count),
        "confidencePolicyComplianceRate": rate(confidence_policy, count),
        "falseAuthoritativeClassificationCount": false_authoritative,
    }


def score_end_to_end(cases: list[dict[str, Any]]) -> dict[str, float | int | None]:
    count = len(cases)
    success = 0
    expected_review = 0
    observed_review = 0
    expected_unsupported = 0
    observed_unsupported = 0
    correction_required = 0
    silent_wrong = 0
    false_authoritative = 0
    false_ready = 0
    unsafe_false_accept = 0
    stale_lineage = 0
    cross_user = 0
    prompt_executed = 0
    e2e_latencies: list[float] = []
    total_costs: list[float] = []
    stage_costs = {"recognition": [], "parse": [], "classification": []}
    for case in cases:
        expected = case["expected"]
        observed = case.get("fixtureObservation", {})
        session = observed.get("session", {})
        if session.get("terminalStage") == expected.get("terminalStage"):
            success += 1
        if expected.get("reviewExpected"):
            expected_review += 1
            if session.get("reviewRequired"):
                observed_review += 1
        if expected.get("expectedSessionOutcome") == "UNSUPPORTED":
            expected_unsupported += 1
            if session.get("unsupported"):
                observed_unsupported += 1
        parse_expected = expected.get("parse", {})
        parse_observed = observed.get("parse", {})
        if parse_expected.get("selectedSource") == "USER" or parse_expected.get("selectedRevision"):
            correction_required += 1
        flags = observed.get("failureFlags", {})
        silent_wrong += 1 if flags.get("silentWrong") else 0
        false_authoritative += 1 if flags.get("falseAuthoritativeAccept") else 0
        false_ready += 1 if flags.get("falseReadyForSolve") else 0
        unsafe_false_accept += 1 if flags.get("unsafeInputFalseAccept") else 0
        stale_lineage += 1 if flags.get("staleLineageAccepted") else 0
        cross_user += 1 if flags.get("crossUserAccessAccepted") else 0
        prompt_executed += 1 if flags.get("promptInstructionExecuted") else 0
        timing = observed.get("timingMs", {})
        cost = observed.get("costMicros", {})
        if "e2e" in timing:
            e2e_latencies.append(float(timing["e2e"]))
        if "total" in cost:
            total_costs.append(float(cost["total"]))
        for stage in stage_costs:
            if stage in cost:
                stage_costs[stage].append(float(cost[stage]))
    return {
        "endToEndIngestionSuccessRate": rate(success, count),
        "expectedReviewRate": rate(observed_review, expected_review),
        "expectedUnsupportedRate": rate(observed_unsupported, expected_unsupported),
        "correctionRequiredEstimateRate": rate(correction_required, count),
        "silentWrongRate": rate(silent_wrong, count),
        "falseAuthoritativeAcceptRate": rate(false_authoritative, count),
        "falseAuthoritativeAcceptCount": false_authoritative,
        "falseReadyForSolveRate": rate(false_ready, count),
        "falseReadyForSolveCount": false_ready,
        "unsafeInputFalseAcceptCount": unsafe_false_accept,
        "staleLineageAcceptedCount": stale_lineage,
        "crossUserAccessAcceptedCount": cross_user,
        "promptInstructionExecutedCount": prompt_executed,
        "pipelineP50LatencyMs": percentile(e2e_latencies, 0.50),
        "pipelineP95LatencyMs": percentile(e2e_latencies, 0.95),
        "pipelineAverageCostMicros": statistics.fmean(total_costs) if total_costs else None,
        "pipelineP95CostMicros": percentile(total_costs, 0.95),
        "stageAverageCostMicros": {
            stage: (statistics.fmean(values) if values else None)
            for stage, values in stage_costs.items()
        },
    }
