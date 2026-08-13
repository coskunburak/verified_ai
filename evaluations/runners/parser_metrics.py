#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class ParserMetrics:
    json_valid_rate: float
    schema_valid_rate: float
    semantic_valid_rate: float
    support_status_exact_accuracy: float
    subject_accuracy: float
    topic_accuracy: float
    task_type_accuracy: float
    problem_type_accuracy: float
    expression_normalized_match_rate: float
    variable_set_accuracy: float
    variable_precision: float
    variable_recall: float
    source_reference_valid_rate: float
    assumption_policy_accuracy: float
    review_required_recall: float
    unsupported_accuracy: float
    invented_source_reference_count: int
    invented_assumption_count: int


def rate(numerator: float, denominator: float) -> float:
    if denominator == 0:
        return 1.0
    return numerator / denominator


def normalize_expression(expression: str | None) -> str:
    if expression is None:
        return ""
    return " ".join(expression.strip().split())


def score_cases(cases: list[dict[str, Any]]) -> ParserMetrics:
    totals = {
        "json": 0.0,
        "schema": 0.0,
        "semantic": 0.0,
        "support": 0.0,
        "subject": 0.0,
        "topic": 0.0,
        "task": 0.0,
        "problem": 0.0,
        "expression": 0.0,
        "variable_set": 0.0,
        "assumptions": 0.0,
        "source": 0.0,
        "unsupported": 0.0,
    }
    variable_precision_sum = 0.0
    variable_recall_sum = 0.0
    review_expected = 0
    review_observed = 0
    invented_source = 0
    invented_assumption = 0
    count = 0
    for case in cases:
        expected = case["expected"].get("parse", {})
        observed = case.get("fixtureObservation", {}).get("parse", {})
        expected_support = expected.get("supportStatus")
        observed_support = observed.get("supportStatus")
        totals["json"] += 1.0 if observed.get("jsonValid", True) else 0.0
        totals["schema"] += 1.0 if observed.get("schemaValid", True) else 0.0
        totals["semantic"] += 1.0 if observed.get("semanticValid", True) else 0.0
        totals["support"] += 1.0 if observed_support == expected_support else 0.0
        totals["subject"] += 1.0 if observed.get("subjectId") == expected.get("subjectId") else 0.0
        totals["topic"] += 1.0 if observed.get("topicId") == expected.get("topicId") else 0.0
        totals["task"] += 1.0 if observed.get("taskType") == expected.get("taskType") else 0.0
        totals["problem"] += 1.0 if observed.get("problemType") == expected.get("problemType") else 0.0
        totals["expression"] += 1.0 if normalize_expression(observed.get("expression")) == normalize_expression(expected.get("expression")) else 0.0
        expected_vars = set(expected.get("variables", []) or [])
        observed_vars = set(observed.get("variables", []) or [])
        totals["variable_set"] += 1.0 if expected_vars == observed_vars else 0.0
        variable_precision_sum += rate(len(expected_vars & observed_vars), len(observed_vars))
        variable_recall_sum += rate(len(expected_vars & observed_vars), len(expected_vars))
        expected_source_ids = set(expected.get("sourceBlockIds", []) or [])
        observed_source_ids = set(observed.get("sourceBlockIds", []) or [])
        if observed_source_ids - expected_source_ids:
            invented_source += 1
        totals["source"] += 1.0 if observed_source_ids <= expected_source_ids else 0.0
        expected_assumptions = set(expected.get("assumptions", []) or [])
        observed_assumptions = set(observed.get("assumptions", []) or [])
        if observed_assumptions - expected_assumptions:
            invented_assumption += 1
        totals["assumptions"] += 1.0 if observed_assumptions <= expected_assumptions else 0.0
        if case["expected"].get("reviewExpected"):
            review_expected += 1
            if observed_support == "REVIEW_REQUIRED":
                review_observed += 1
        if expected_support == "UNSUPPORTED":
            totals["unsupported"] += 1.0 if observed_support == "UNSUPPORTED" else 0.0
        else:
            totals["unsupported"] += 1.0
        count += 1
    return ParserMetrics(
        json_valid_rate=rate(totals["json"], count),
        schema_valid_rate=rate(totals["schema"], count),
        semantic_valid_rate=rate(totals["semantic"], count),
        support_status_exact_accuracy=rate(totals["support"], count),
        subject_accuracy=rate(totals["subject"], count),
        topic_accuracy=rate(totals["topic"], count),
        task_type_accuracy=rate(totals["task"], count),
        problem_type_accuracy=rate(totals["problem"], count),
        expression_normalized_match_rate=rate(totals["expression"], count),
        variable_set_accuracy=rate(totals["variable_set"], count),
        variable_precision=rate(variable_precision_sum, count),
        variable_recall=rate(variable_recall_sum, count),
        source_reference_valid_rate=rate(totals["source"], count),
        assumption_policy_accuracy=rate(totals["assumptions"], count),
        review_required_recall=rate(review_observed, review_expected),
        unsupported_accuracy=rate(totals["unsupported"], count),
        invented_source_reference_count=invented_source,
        invented_assumption_count=invented_assumption,
    )


def to_dict(metrics: ParserMetrics) -> dict[str, float | int]:
    return {
        "jsonValidRate": metrics.json_valid_rate,
        "schemaValidRate": metrics.schema_valid_rate,
        "semanticValidRate": metrics.semantic_valid_rate,
        "supportStatusExactAccuracy": metrics.support_status_exact_accuracy,
        "subjectAccuracy": metrics.subject_accuracy,
        "topicAccuracy": metrics.topic_accuracy,
        "taskTypeAccuracy": metrics.task_type_accuracy,
        "problemTypeAccuracy": metrics.problem_type_accuracy,
        "expressionNormalizedMatchRate": metrics.expression_normalized_match_rate,
        "variableSetAccuracy": metrics.variable_set_accuracy,
        "variablePrecision": metrics.variable_precision,
        "variableRecall": metrics.variable_recall,
        "sourceReferenceValidRate": metrics.source_reference_valid_rate,
        "assumptionPolicyAccuracy": metrics.assumption_policy_accuracy,
        "reviewRequiredRecall": metrics.review_required_recall,
        "unsupportedAccuracy": metrics.unsupported_accuracy,
        "inventedSourceReferenceCount": metrics.invented_source_reference_count,
        "inventedAssumptionCount": metrics.invented_assumption_count,
    }
