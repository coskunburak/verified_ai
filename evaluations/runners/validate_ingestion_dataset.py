#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / "evaluations/golden-datasets/parsing/ingestion-v1/manifest.yaml"
DEFAULT_COVERAGE = ROOT / "evaluations/rubrics/ingestion-coverage-v1.yaml"
CASE_SCHEMA_VERSION = "ingestion-evaluation-case-v1"
SHA256_RE = re.compile(r"^[a-f0-9]{64}$")
PROHIBITED_PRIVACY_PATTERNS = {
    "email": re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"),
    "phone": re.compile(r"\+?\d[\d .().-]{8,}\d"),
    "jwt": re.compile(r"eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+"),
    "production_object_key": re.compile(r"\bproblem-assets/[0-9a-fA-F-]{20,}/"),
    "bearer_token": re.compile(r"\bBearer\s+[A-Za-z0-9._-]+", re.IGNORECASE),
}


@dataclass(frozen=True)
class ValidationResult:
    manifest: dict[str, Any]
    coverage: dict[str, Any]
    cases: list[dict[str, Any]]
    dataset_sha256: str
    warnings: list[str]


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"{path.relative_to(ROOT)} is not valid JSON/YAML subset: {exc}") from exc


def display_path(path: Path) -> str:
    try:
        return path.relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical_dataset_sha256(case_files: list[Path], manifest_path: Path, coverage_path: Path) -> str:
    digest = hashlib.sha256()
    for path in [manifest_path, coverage_path, *case_files]:
        digest.update(path.relative_to(ROOT).as_posix().encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def load_cases(dataset_dir: Path, manifest: dict[str, Any]) -> tuple[list[dict[str, Any]], list[Path]]:
    cases: list[dict[str, Any]] = []
    case_files: list[Path] = []
    for partition, rel_path in manifest.get("caseFiles", {}).items():
        path = dataset_dir / rel_path
        case_files.append(path)
        if not path.exists():
            raise ValueError(f"missing case file for {partition}: {display_path(path)}")
        declared_sha = manifest.get("caseFileSha256", {}).get(rel_path)
        if declared_sha and sha256_file(path) != declared_sha:
            raise ValueError(f"case file checksum mismatch: {display_path(path)}")
        with path.open("r", encoding="utf-8") as handle:
            for line_number, line in enumerate(handle, start=1):
                stripped = line.strip()
                if not stripped:
                    continue
                try:
                    case = json.loads(stripped)
                except json.JSONDecodeError as exc:
                    raise ValueError(f"{display_path(path)}:{line_number} invalid JSONL: {exc}") from exc
                if case.get("partition") != partition:
                    raise ValueError(f"{case.get('id')} partition mismatch: expected {partition}")
                cases.append(case)
    return cases, case_files


def require(condition: bool, failures: list[str], message: str) -> None:
    if not condition:
        failures.append(message)


def validate_case_shape(case: dict[str, Any], failures: list[str]) -> None:
    case_id = case.get("id", "<missing-id>")
    required_top = ("schemaVersion", "id", "partition", "criticality", "origin", "input", "slices", "expected")
    for field in required_top:
        require(field in case, failures, f"{case_id} missing field {field}")
    require(case.get("schemaVersion") == CASE_SCHEMA_VERSION, failures, f"{case_id} has invalid schemaVersion")
    require(case.get("partition") in {"DEVELOPMENT", "REGRESSION", "HARD_TAIL", "PROTECTED_HOLDOUT"}, failures, f"{case_id} invalid partition")
    require(case.get("criticality") in {"NORMAL", "CRITICAL"}, failures, f"{case_id} invalid criticality")
    slices = case.get("slices")
    require(isinstance(slices, list) and len(slices) > 0, failures, f"{case_id} requires non-empty slices")
    if isinstance(slices, list):
        require(len(slices) == len(set(slices)), failures, f"{case_id} duplicate slice")
    origin = case.get("origin", {})
    require(origin.get("type") in {"SYNTHETIC", "INTERNALLY_AUTHORED", "PERMISSIVELY_LICENSED_PUBLIC"}, failures, f"{case_id} invalid origin")
    require(origin.get("license"), failures, f"{case_id} missing license")
    require(origin.get("sourceId"), failures, f"{case_id} missing sourceId")
    require(origin.get("trainingEligibility") == "NOT_ELIGIBLE", failures, f"{case_id} is training eligible")
    input_data = case.get("input", {})
    require(input_data.get("assetPath"), failures, f"{case_id} missing assetPath")
    require(input_data.get("contentType") in {"text/plain", "image/jpeg", "image/png", "application/pdf"}, failures, f"{case_id} invalid contentType")
    require(input_data.get("locale") in {"en-US", "tr-TR"}, failures, f"{case_id} invalid locale")
    require(bool(SHA256_RE.match(str(input_data.get("assetSha256", "")))), failures, f"{case_id} invalid assetSha256")
    expected = case.get("expected", {})
    for field in (
        "expectedRecognitionOutcome",
        "expectedParseOutcome",
        "expectedCanonicalOutcome",
        "expectedClassificationOutcome",
        "expectedSessionOutcome",
        "terminalStage",
        "reviewExpected",
    ):
        require(field in expected, failures, f"{case_id} missing expected.{field}")


def validate_dataset(manifest_path: Path = DEFAULT_MANIFEST, coverage_path: Path = DEFAULT_COVERAGE) -> ValidationResult:
    failures: list[str] = []
    warnings: list[str] = []
    manifest_path = manifest_path.resolve()
    coverage_path = coverage_path.resolve()
    dataset_dir = manifest_path.parent

    manifest = read_json(manifest_path)
    coverage = read_json(coverage_path)
    cases, case_files = load_cases(dataset_dir, manifest)

    declared_counts = manifest.get("caseCounts", {})
    require(declared_counts.get("totalCommitted") == len(cases), failures, "manifest totalCommitted does not match case files")
    require(coverage.get("minimumTotalCases", 0) <= len(cases), failures, "dataset below minimum total cases")
    critical_count = sum(1 for case in cases if case.get("criticality") == "CRITICAL")
    require(declared_counts.get("criticalCommitted") == critical_count, failures, "manifest criticalCommitted does not match case files")
    require(coverage.get("minimumCriticalCases", 0) <= critical_count, failures, "dataset below minimum critical cases")

    ids = [case.get("id") for case in cases]
    for case_id, count in Counter(ids).items():
        require(count == 1, failures, f"duplicate case id: {case_id}")

    asset_hashes: Counter[str] = Counter()
    normalized_texts: dict[str, set[str]] = {}
    all_asset_paths: set[Path] = set()
    for case in cases:
        validate_case_shape(case, failures)
        case_id = case.get("id", "<missing-id>")
        input_data = case.get("input", {})
        asset_rel = input_data.get("assetPath", "")
        asset_path = (dataset_dir / asset_rel).resolve()
        all_asset_paths.add(asset_path)
        if not asset_path.exists():
            failures.append(f"{case_id} missing asset: {asset_rel}")
        else:
            actual_sha = sha256_file(asset_path)
            if actual_sha != input_data.get("assetSha256"):
                failures.append(f"{case_id} checksum mismatch for {asset_rel}")
            asset_hashes[actual_sha] += 1
            text = asset_path.read_text(encoding="utf-8", errors="ignore")
            for label, pattern in PROHIBITED_PRIVACY_PATTERNS.items():
                if pattern.search(text):
                    failures.append(f"{case_id} asset privacy scan matched {label}")
        for text_value in candidate_texts(case):
            normalized = " ".join(str(text_value).casefold().split())
            if normalized:
                normalized_texts.setdefault(normalized, set()).add(case_id)
            for label, pattern in PROHIBITED_PRIVACY_PATTERNS.items():
                if pattern.search(str(text_value)):
                    failures.append(f"{case_id} metadata privacy scan matched {label}")
        origin_type = case.get("origin", {}).get("type")
        require(origin_type != "PRODUCTION_USER_CONTENT", failures, f"{case_id} uses prohibited production-user origin")
        if case.get("partition") == "PROTECTED_HOLDOUT":
            failures.append(f"{case_id} protected holdout payload must not be committed")

    for asset_sha, count in asset_hashes.items():
        require(count == 1, failures, f"duplicate asset sha256: {asset_sha}")

    prompt_dirs = [
        ROOT / "services/api/src/main/resources/prompts",
        ROOT / "prompts",
    ]
    prompt_text = "\n".join(path.read_text(encoding="utf-8", errors="ignore") for directory in prompt_dirs if directory.exists() for path in directory.rglob("*") if path.is_file())
    emitted_duplicate_sets: set[tuple[str, ...]] = set()
    for normalized, duplicate_ids in normalized_texts.items():
        if len(duplicate_ids) > 1:
            duplicate_key = tuple(sorted(duplicate_ids))
            if duplicate_key not in emitted_duplicate_sets:
                emitted_duplicate_sets.add(duplicate_key)
                warnings.append(f"near duplicate normalized fixture text across cases: {', '.join(duplicate_key)}")
        if normalized and normalized in " ".join(prompt_text.casefold().split()):
            failures.append(f"fixture text leaks into prompt text: {', '.join(sorted(duplicate_ids))}")

    declared_asset_files = {
        (dataset_dir / case.get("input", {}).get("assetPath", "")).resolve()
        for case in cases
    }
    actual_asset_files = {
        path.resolve()
        for path in (dataset_dir / "assets").rglob("*")
        if path.is_file()
    }
    for undeclared in sorted(actual_asset_files - declared_asset_files):
        failures.append(f"undeclared asset file: {display_path(undeclared)}")

    asset_lines = []
    for path in sorted(actual_asset_files):
        asset_lines.append(f"{sha256_file(path)}  {path.relative_to(dataset_dir).as_posix()}\n")
    asset_manifest_sha = hashlib.sha256("".join(asset_lines).encode("utf-8")).hexdigest()
    require(asset_manifest_sha == manifest.get("assetManifestSha256"), failures, "assetManifestSha256 mismatch")

    slice_counts: Counter[str] = Counter()
    partition_counts: Counter[str] = Counter()
    for case in cases:
        partition_counts[case.get("partition")] += 1
        slice_counts.update(case.get("slices", []))
    for partition, minimum in coverage.get("requiredPartitions", {}).items():
        require(partition_counts[partition] >= minimum, failures, f"partition {partition} below minimum {minimum}")
    for group_name, group in coverage.get("requiredSlices", {}).items():
        for slice_name, minimum in group.items():
            require(slice_counts[slice_name] >= minimum, failures, f"slice {slice_name} in {group_name} below minimum {minimum}")

    active = coverage.get("activeTaxonomy", {})
    active_subjects = set(active.get("subjects", []))
    active_topics = set(active.get("topics", []))
    active_skills = set(active.get("skills", []))
    for case in cases:
        case_id = case.get("id")
        parse = case.get("expected", {}).get("parse", {})
        classification = case.get("expected", {}).get("classification", {})
        subject = parse.get("subjectId")
        topic = parse.get("topicId")
        if subject:
            require(subject in active_subjects, failures, f"{case_id} unknown subjectId {subject}")
        if topic:
            require(topic in active_topics, failures, f"{case_id} unknown topicId {topic}")
        primary_skill = classification.get("primarySkillId")
        if primary_skill:
            require(primary_skill in active_skills, failures, f"{case_id} unknown primarySkillId {primary_skill}")
        for skill_id in classification.get("secondarySkillIds", []) or []:
            require(skill_id in active_skills, failures, f"{case_id} unknown secondarySkillId {skill_id}")

    holdout = manifest.get("protectedHoldout", {})
    require(holdout.get("payloadAvailableInRepository") is False, failures, "protected holdout payload must remain outside repository")
    require(holdout.get("status") == "BLOCKED_PROTECTED_HOLDOUT", failures, "protected holdout status must be explicit")

    if failures:
        details = "\n".join(f"- {failure}" for failure in failures)
        raise ValueError(f"ingestion dataset validation failed:\n{details}")

    dataset_sha = canonical_dataset_sha256(case_files, manifest_path, coverage_path)
    return ValidationResult(manifest, coverage, cases, dataset_sha, warnings)


def candidate_texts(value: Any) -> list[str]:
    result: list[str] = []
    if isinstance(value, dict):
        for key, nested in value.items():
            if key in {"normalizedText", "expression", "ast", "sourceText", "assetPath"} and isinstance(nested, str):
                result.append(nested)
            result.extend(candidate_texts(nested))
    elif isinstance(value, list):
        for item in value:
            result.extend(candidate_texts(item))
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate Sprint 4.10 ingestion evaluation dataset")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--coverage", type=Path, default=DEFAULT_COVERAGE)
    parser.add_argument("--json", action="store_true", help="print machine-readable validation summary")
    args = parser.parse_args()
    try:
        result = validate_dataset(args.manifest, args.coverage)
    except ValueError as exc:
        print(exc, file=sys.stderr)
        return 1
    summary = {
        "status": "PASS",
        "datasetId": result.manifest["datasetId"],
        "datasetVersion": result.manifest["datasetVersion"],
        "caseCount": len(result.cases),
        "criticalCaseCount": sum(1 for case in result.cases if case["criticality"] == "CRITICAL"),
        "datasetSha256": result.dataset_sha256,
        "warnings": result.warnings,
    }
    if args.json:
        print(json.dumps(summary, indent=2, sort_keys=True))
    else:
        print(
            f"ingestion dataset validation PASS: {summary['caseCount']} cases, "
            f"{summary['criticalCaseCount']} critical, sha256={summary['datasetSha256']}"
        )
        for warning in result.warnings:
            print(f"warning: {warning}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
