#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DOCS_ROOT = ROOT / "docs" / "verified_ai_learning_platform_docs_v4"
MANIFEST = DOCS_ROOT / "DOCUMENTATION_MANIFEST.md"

GOVERNANCE_DOCS = {
    "roadmap/67_PRODUCT_PROGRAM_STRUCTURE.md": [
        "Program 0 - Product Definition",
        "Program 4 - Commercial Production",
        "Phase count is not a quality signal.",
    ],
    "roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md": [
        "CAP-ID-001",
        "CAP-VERIFY-001",
        "CAP-LAUNCH-002",
        "V1 Coverage Gate",
    ],
    "quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md": [
        "REQ-TRUST-001",
        "REQ-VERIFY-001",
        "REQ-BILL-001",
        "REQ-PRIV-001",
        "PR Usage Rule",
    ],
    "operations/70_RELEASE_TRAINS_AND_BACKLOG_PROMOTION.md": [
        "Release Trains",
        "Backlog Promotion States",
        "Post-Launch Phase Expansion Policy",
        "Release Exception Policy",
    ],
    "operations/71_TECHNICAL_DEBT_AND_MAINTENANCE_REGISTER.md": [
        "TD-DEP-001",
        "Starlette/httpx deprecation warning",
        "Debt Rules",
    ],
    "DOCUMENTATION_V5_CHANGELOG.md": [
        "program governance",
        "Production V1 completeness",
    ],
}

REQUIRED_CAPABILITY_IDS = {
    "CAP-FOUND-001",
    "CAP-FOUND-002",
    "CAP-ID-001",
    "CAP-BILL-001",
    "CAP-PRIV-001",
    "CAP-PROBLEM-002",
    "CAP-PROBLEM-006",
    "CAP-AI-001",
    "CAP-SOLVE-002",
    "CAP-VERIFY-001",
    "CAP-EVAL-001",
    "CAP-LEARN-003",
    "CAP-ADAPT-001",
    "CAP-EXAM-001",
    "CAP-LAUNCH-002",
    "CAP-ML-001",
}

REQUIRED_REQUIREMENT_IDS = {
    "REQ-TRUST-001",
    "REQ-VERIFY-001",
    "REQ-MATH-001",
    "REQ-AI-001",
    "REQ-EVAL-001",
    "REQ-ID-001",
    "REQ-AUTH-001",
    "REQ-BILL-001",
    "REQ-PRIV-001",
    "REQ-DATA-001",
    "REQ-PROBLEM-001",
    "REQ-INGEST-EVAL-001",
    "REQ-ATTEMPT-001",
    "REQ-MASTERY-001",
    "REQ-ADAPT-001",
    "REQ-LAUNCH-001",
    "REQ-ML-001",
}


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def manifest_entries() -> dict[str, tuple[int, int]]:
    text = read_text(MANIFEST)
    entries: dict[str, tuple[int, int]] = {}
    for match in re.finditer(r"^- `([^`]+)` — ([0-9]+) lines — ([0-9]+) bytes", text, re.MULTILINE):
        entries[match.group(1)] = (int(match.group(2)), int(match.group(3)))
    total_match = re.search(r"Total Markdown files: \*\*([0-9]+)\*\*", text)
    declared_total = int(total_match.group(1)) if total_match else -1
    if declared_total != len(entries):
        raise AssertionError(f"manifest declared {declared_total} files but lists {len(entries)} entries")
    return entries


def actual_entries() -> dict[str, tuple[int, int]]:
    entries: dict[str, tuple[int, int]] = {}
    for path in sorted(DOCS_ROOT.rglob("*.md")):
        rel = path.relative_to(DOCS_ROOT).as_posix()
        data = path.read_bytes()
        line_count = data.decode("utf-8").count("\n")
        if data and not data.endswith(b"\n"):
            line_count += 1
        entries[rel] = (line_count, len(data))
    return entries


def check_manifest() -> list[str]:
    problems: list[str] = []
    expected = manifest_entries()
    actual = actual_entries()

    for rel in sorted(set(actual) - set(expected)):
        problems.append(f"manifest missing entry: {rel}")
    for rel in sorted(set(expected) - set(actual)):
        problems.append(f"manifest extra entry: {rel}")
    for rel in sorted(set(actual) & set(expected)):
        if actual[rel] != expected[rel]:
            problems.append(f"manifest mismatch: {rel} expected {expected[rel]} actual {actual[rel]}")
    return problems


def check_markdown_references() -> list[str]:
    problems: list[str] = []
    path_like = re.compile(r"`([^`\n]+\.md)`|\]\(([^)\n]+\.md)\)")

    for path in sorted(DOCS_ROOT.rglob("*.md")):
        text = read_text(path)
        base = path.parent
        for match in path_like.finditer(text):
            raw = (match.group(1) or match.group(2) or "").split("#", 1)[0]
            if "://" in raw:
                continue
            if any(token in raw for token in ("<", ">", "*", "NNN")):
                continue
            if raw.startswith("."):
                candidate = (base / raw).resolve()
            else:
                local_candidate = (base / raw).resolve()
                docs_candidate = (DOCS_ROOT / raw).resolve()
                root_candidate = (ROOT / raw).resolve()
                if local_candidate.exists():
                    candidate = local_candidate
                elif docs_candidate.exists():
                    candidate = docs_candidate
                else:
                    candidate = root_candidate
            try:
                candidate.relative_to(ROOT)
            except ValueError:
                continue
            if not candidate.exists():
                rel = path.relative_to(DOCS_ROOT).as_posix()
                problems.append(f"missing markdown reference in {rel}: {raw}")
    return problems


def check_forbidden_metadata() -> list[str]:
    return [f"forbidden metadata file: {p.relative_to(ROOT)}" for p in ROOT.rglob(".DS_Store")]


def markdown_table_ids(text: str, prefix: str) -> list[str]:
    ids: list[str] = []
    pattern = re.compile(rf"^\|\s*({re.escape(prefix)}(?:-[A-Z]+)+-[0-9]{{3}})\s*\|", re.MULTILINE)
    for match in pattern.finditer(text):
        ids.append(match.group(1))
    return ids


def check_unique_ids(ids: list[str], label: str) -> list[str]:
    problems: list[str] = []
    seen: set[str] = set()
    for item in ids:
        if item in seen:
            problems.append(f"duplicate {label} id: {item}")
        seen.add(item)
    return problems


def check_governance_docs() -> list[str]:
    problems: list[str] = []
    for relative_path, required_terms in GOVERNANCE_DOCS.items():
        path = DOCS_ROOT / relative_path
        if not path.exists():
            problems.append(f"missing governance doc: {relative_path}")
            continue
        text = read_text(path)
        for term in required_terms:
            if term not in text:
                problems.append(f"{relative_path} missing governance term: {term}")

    coverage_path = DOCS_ROOT / "roadmap/68_PRODUCTION_V1_CAPABILITY_COVERAGE_MATRIX.md"
    if coverage_path.exists():
        capability_ids = markdown_table_ids(read_text(coverage_path), "CAP")
        problems.extend(check_unique_ids(capability_ids, "capability"))
        missing = REQUIRED_CAPABILITY_IDS - set(capability_ids)
        for capability_id in sorted(missing):
            problems.append(f"coverage matrix missing capability id: {capability_id}")

    traceability_path = DOCS_ROOT / "quality/69_REQUIREMENTS_TRACEABILITY_MATRIX.md"
    if traceability_path.exists():
        requirement_ids = markdown_table_ids(read_text(traceability_path), "REQ")
        problems.extend(check_unique_ids(requirement_ids, "requirement"))
        missing = REQUIRED_REQUIREMENT_IDS - set(requirement_ids)
        for requirement_id in sorted(missing):
            problems.append(f"traceability matrix missing requirement id: {requirement_id}")

    return problems


def main() -> int:
    if not DOCS_ROOT.exists():
        print(f"missing documentation root: {DOCS_ROOT}", file=sys.stderr)
        return 1

    problems = check_manifest()
    problems.extend(check_markdown_references())
    problems.extend(check_forbidden_metadata())
    problems.extend(check_governance_docs())

    if problems:
        print("documentation check failed:")
        for problem in problems:
            print(f"- {problem}")
        return 1

    print(f"documentation check passed: {len(actual_entries())} Markdown files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
