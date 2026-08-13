#!/usr/bin/env python3
from __future__ import annotations

import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import ingestion_metrics
import parser_metrics
import recognition_metrics
import validate_ingestion_dataset


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "evaluations/golden-datasets/parsing/ingestion-v1/manifest.yaml"
COVERAGE = ROOT / "evaluations/rubrics/ingestion-coverage-v1.yaml"


class DatasetValidatorTest(unittest.TestCase):
    def test_valid_dataset_passes(self) -> None:
        result = validate_ingestion_dataset.validate_dataset(MANIFEST, COVERAGE)
        self.assertEqual(18, len(result.cases))
        self.assertRegex(result.dataset_sha256, r"^[a-f0-9]{64}$")

    def test_duplicate_id_fails(self) -> None:
        with copied_dataset() as temp_root:
            regression = temp_root / "evaluations/golden-datasets/parsing/ingestion-v1/regression.jsonl"
            first_line = regression.read_text(encoding="utf-8").splitlines()[0]
            regression.write_text(regression.read_text(encoding="utf-8") + first_line + "\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "checksum mismatch|duplicate case id"):
                validate_ingestion_dataset.validate_dataset(
                    temp_root / MANIFEST.relative_to(ROOT),
                    temp_root / COVERAGE.relative_to(ROOT),
                )

    def test_missing_asset_fails(self) -> None:
        with copied_dataset() as temp_root:
            asset = temp_root / "evaluations/golden-datasets/parsing/ingestion-v1/assets/synthetic/e2e-02-linear-equation.txt"
            asset.unlink()
            with self.assertRaisesRegex(ValueError, "missing asset"):
                validate_ingestion_dataset.validate_dataset(
                    temp_root / MANIFEST.relative_to(ROOT),
                    temp_root / COVERAGE.relative_to(ROOT),
                )

    def test_checksum_mismatch_fails(self) -> None:
        with copied_dataset() as temp_root:
            asset = temp_root / "evaluations/golden-datasets/parsing/ingestion-v1/assets/synthetic/e2e-02-linear-equation.txt"
            asset.write_text("tampered\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "checksum mismatch|assetManifestSha256 mismatch"):
                validate_ingestion_dataset.validate_dataset(
                    temp_root / MANIFEST.relative_to(ROOT),
                    temp_root / COVERAGE.relative_to(ROOT),
                )

    def test_invalid_schema_fails(self) -> None:
        with copied_dataset() as temp_root:
            development = temp_root / "evaluations/golden-datasets/parsing/ingestion-v1/development.jsonl"
            cases = [json.loads(line) for line in development.read_text(encoding="utf-8").splitlines()]
            cases[0]["schemaVersion"] = "wrong"
            development.write_text("\n".join(json.dumps(case) for case in cases) + "\n", encoding="utf-8")
            update_manifest_case_sha(temp_root, "development.jsonl")
            with self.assertRaisesRegex(ValueError, "invalid schemaVersion"):
                validate_ingestion_dataset.validate_dataset(
                    temp_root / MANIFEST.relative_to(ROOT),
                    temp_root / COVERAGE.relative_to(ROOT),
                )

    def test_training_violation_fails(self) -> None:
        with copied_dataset() as temp_root:
            development = temp_root / "evaluations/golden-datasets/parsing/ingestion-v1/development.jsonl"
            cases = [json.loads(line) for line in development.read_text(encoding="utf-8").splitlines()]
            cases[0]["origin"]["trainingEligibility"] = "ELIGIBLE"
            development.write_text("\n".join(json.dumps(case) for case in cases) + "\n", encoding="utf-8")
            update_manifest_case_sha(temp_root, "development.jsonl")
            with self.assertRaisesRegex(ValueError, "training eligible"):
                validate_ingestion_dataset.validate_dataset(
                    temp_root / MANIFEST.relative_to(ROOT),
                    temp_root / COVERAGE.relative_to(ROOT),
                )

    def test_missing_required_slice_fails(self) -> None:
        with copied_dataset() as temp_root:
            coverage = temp_root / COVERAGE.relative_to(ROOT)
            policy = json.loads(coverage.read_text(encoding="utf-8"))
            policy["requiredSlices"]["hardTail"]["NON_EXISTENT_SLICE"] = 1
            coverage.write_text(json.dumps(policy), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "NON_EXISTENT_SLICE"):
                validate_ingestion_dataset.validate_dataset(
                    temp_root / MANIFEST.relative_to(ROOT),
                    coverage,
                )

    def test_holdout_payload_in_repo_fails(self) -> None:
        with copied_dataset() as temp_root:
            development = temp_root / "evaluations/golden-datasets/parsing/ingestion-v1/development.jsonl"
            case = json.loads(development.read_text(encoding="utf-8").splitlines()[0])
            case["id"] = "holdout-should-not-be-committed"
            case["partition"] = "PROTECTED_HOLDOUT"
            holdout = temp_root / "evaluations/golden-datasets/parsing/ingestion-v1/holdout.jsonl"
            holdout.write_text(json.dumps(case) + "\n", encoding="utf-8")
            manifest = temp_root / MANIFEST.relative_to(ROOT)
            data = json.loads(manifest.read_text(encoding="utf-8"))
            data["caseFiles"]["PROTECTED_HOLDOUT"] = "holdout.jsonl"
            data["caseFileSha256"]["holdout.jsonl"] = validate_ingestion_dataset.sha256_file(holdout)
            data["caseCounts"]["totalCommitted"] += 1
            manifest.write_text(json.dumps(data), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "protected holdout payload"):
                validate_ingestion_dataset.validate_dataset(
                    manifest,
                    temp_root / COVERAGE.relative_to(ROOT),
                )


class MetricTest(unittest.TestCase):
    def test_recognition_tokenizer_preserves_relations(self) -> None:
        self.assertEqual(
            ["2", "x", "-", "1", ">=", "7"],
            recognition_metrics.tokenize_math("2x - 1 >= 7"),
        )

    def test_recognition_cer_is_hand_computable(self) -> None:
        cases = [
            {
                "expected": {"recognition": {"normalizedText": "x + 1 = 2", "criticalSymbols": ["="], "blockCount": 1, "coordinateValid": True}, "reviewExpected": False},
                "fixtureObservation": {"recognition": {"normalizedText": "x + 1 = 3", "blockCount": 1, "blockKindAccuracy": 1, "readingOrderAccuracy": 1, "coordinateValid": True, "reviewRequired": False}},
            }
        ]
        metrics = recognition_metrics.score_cases(cases)
        self.assertGreater(metrics.character_error_rate, 0)
        self.assertEqual(0.0, metrics.critical_symbol_error_rate)

    def test_parser_invented_source_reference_counts(self) -> None:
        cases = [
            {
                "expected": {"reviewExpected": False, "parse": {"supportStatus": "SUPPORTED", "subjectId": "MATH", "topicId": "MATH.EQUATIONS", "taskType": "SOLVE_EQUATION", "problemType": "EQUATION", "expression": "x = 1", "variables": ["x"], "sourceBlockIds": ["block-1"], "assumptions": []}},
                "fixtureObservation": {"parse": {"jsonValid": True, "schemaValid": True, "semanticValid": False, "supportStatus": "SUPPORTED", "subjectId": "MATH", "topicId": "MATH.EQUATIONS", "taskType": "SOLVE_EQUATION", "problemType": "EQUATION", "expression": "x = 1", "variables": ["x"], "sourceBlockIds": ["block-missing"], "assumptions": []}},
            }
        ]
        self.assertEqual(1, parser_metrics.score_cases(cases).invented_source_reference_count)

    def test_percentile_policy(self) -> None:
        self.assertEqual(3, ingestion_metrics.percentile([1, 2, 3, 4, 5], 0.5))
        self.assertAlmostEqual(4.8, ingestion_metrics.percentile([1, 2, 3, 4, 5], 0.95))


def copied_dataset():
    temp_dir = tempfile.TemporaryDirectory()
    temp_root = Path(temp_dir.name)
    shutil.copytree(ROOT / "evaluations", temp_root / "evaluations")
    shutil.copytree(ROOT / "services/api/src/main/resources/prompts", temp_root / "services/api/src/main/resources/prompts")
    return TempRoot(temp_dir, temp_root)


class TempRoot:
    def __init__(self, temp_dir: tempfile.TemporaryDirectory[str], path: Path) -> None:
        self.temp_dir = temp_dir
        self.path = path

    def __enter__(self) -> Path:
        return self.path

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.temp_dir.cleanup()


def update_manifest_case_sha(temp_root: Path, case_file: str) -> None:
    manifest = temp_root / MANIFEST.relative_to(ROOT)
    data = json.loads(manifest.read_text(encoding="utf-8"))
    path = temp_root / "evaluations/golden-datasets/parsing/ingestion-v1" / case_file
    data["caseFileSha256"][case_file] = validate_ingestion_dataset.sha256_file(path)
    manifest.write_text(json.dumps(data), encoding="utf-8")


if __name__ == "__main__":
    raise SystemExit(unittest.main())
