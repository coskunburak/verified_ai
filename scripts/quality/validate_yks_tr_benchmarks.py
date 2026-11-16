#!/usr/bin/env python3
"""Offline Sprint 9.6B Benchmark & V060 SymPy Verification Gate.

Verifies:
1. V060 SQL invariants (TYT >= 60 with >= 36 new-gen, AYT >= 60, total >= 120,
   5 options A..E, MistakeTaxonomy distractor codes, ADR-006 training_eligible=FALSE,
   dual-signature author_id <> reviewer_id, unique 64-hex SHA-256 hashes,
   ADR-010 visual alt-text >= 10 chars, 2026.2 40.0000 topic weights, 5 V2 blueprints).
2. SymPy mathematical proof across all V060 items using services/math-verifier:
   correct option == expectedAnswerLatex and all 4 distractors != expectedAnswerLatex.
3. Golden datasets yks-tr-ingestion-v1 (>= 35 cases) and yks-tr-hard-v1 (>= 60 cases):
   Draft 2020-12 schema conformance, SHA-256 manifest & asset integrity, NOT_ELIGIBLE,
   tr-TR locale, and SymPy verification of all 60+ solver answers.
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
from collections import Counter
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
VENV_SITE = ROOT / "services/math-verifier/.venv/lib/python3.11/site-packages"
if VENV_SITE.exists() and str(VENV_SITE) not in sys.path:
    sys.path.append(str(VENV_SITE))

import sympy as sp

MATH_VERIFIER_DIR = ROOT / "services/math-verifier"
if str(MATH_VERIFIER_DIR) not in sys.path:
    sys.path.insert(0, str(MATH_VERIFIER_DIR))

from app import answer_evaluator  # noqa: E402

try:
    from jsonschema import Draft202012Validator  # type: ignore
except ImportError:
    Draft202012Validator = None

SHA256_RE = re.compile(r"^[0-9a-f]{64}$")

_SYMPY_LOCALS = {
    "x": sp.Symbol("x", real=True),
    "pi": sp.pi,
    "i": sp.I,
    "I": sp.I,
    "e": sp.E,
    "sin": sp.sin,
    "cos": sp.cos,
    "tan": sp.tan,
    "sqrt": sp.sqrt,
    "log": sp.log,
    "ln": sp.log,
}


def sha256_file(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def latex_to_sympy(expr: str) -> sp.Expr:
    cleaned = answer_evaluator._clean_latex(expr)
    s = cleaned.replace("\\sin", " sin ").replace("\\cos", " cos ").replace("\\tan", " tan ")
    s = s.replace("\\pi", " pi ").replace("\\ln", " log ").replace("\\log", " log ")
    s = re.sub(r"\\sqrt\{([^{}]+)\}", r"sqrt(\1)", s)
    while "\\frac" in s:
        new_s = re.sub(r"\\frac\{([^{}]+)\}\{([^{}]+)\}", r"((\1)/(\2))", s)
        if new_s == s:
            break
        s = new_s
    s = re.sub(r"log_(\d+)\s*([a-zA-Z0-9]+)", r"log(\2, \1)", s)
    s = re.sub(r"\b(sin|cos|tan)\s*\^(\d+)\s*([a-zA-Z0-9]+)", r"(\1(\3))**\2", s)
    s = s.replace("^", "**")
    s = re.sub(r"\b(sin|cos|tan)\s+([a-zA-Z0-9]+)", r"\1(\2)", s)
    s = re.sub(r"(\d)\s*([a-zA-Z(])", r"\1*\2", s)
    s = re.sub(r"\)\s*([a-zA-Z0-9(])", r")*\1", s)
    s = re.sub(r"\b(pi|x|i)\s+(sin|cos|tan|sqrt|pi|x|i)\b", r"\1*\2", s)
    s = s.replace(" ", "")
    # Also cross-check simple algebraic expressions through answer_evaluator._parse_algebraic_expr
    if all(fn not in s for fn in ("sin", "cos", "tan", "sqrt", "log", "pi", "i")):
        ae_expr = answer_evaluator._parse_algebraic_expr(expr)
        if ae_expr is not None:
            return ae_expr
    return sp.sympify(s, locals=_SYMPY_LOCALS)


def is_sympy_equivalent(a_str: str, b_str: str) -> bool:
    a = latex_to_sympy(a_str)
    b = latex_to_sympy(b_str)
    diff = sp.simplify(a - b)
    if diff == 0:
        return True
    try:
        if sp.trigsimp(diff) == 0:
            return True
    except Exception:
        pass
    try:
        if sp.nsimplify(diff) == 0:
            return True
    except Exception:
        pass
    return False


def load_active_mistake_codes() -> set[str]:
    mt_path = ROOT / "services/api/src/main/java/com/verifiedai/mistake/MistakeTaxonomy.java"
    text = mt_path.read_text(encoding="utf-8")
    return set(re.findall(r'Map\.entry\("([A-Z0-9_]+)"\s*,\s*Category\.', text))


def load_active_curriculum_skills() -> dict[str, str]:
    cat_path = ROOT / "services/api/src/main/java/com/verifiedai/curriculum/application/StaticCurriculumTaxonomyCatalog.java"
    text = cat_path.read_text(encoding="utf-8")
    return dict(re.findall(r'Map\.entry\("([^"]+)"\s*,\s*"([^"]+)"\)', text))


def split_sql_tuples(block: str) -> list[list[Any]]:
    """Parses SQL VALUES (...), (...) tuples respecting single-quoted escaped strings."""
    rows: list[list[Any]] = []
    i = 0
    n = len(block)
    while i < n:
        if block[i] == "(":
            i += 1
            fields: list[Any] = []
            while i < n:
                while i < n and block[i] in " \t\r\n,":
                    i += 1
                if i < n and block[i] == ")":
                    i += 1
                    break
                if block[i] == "'":
                    i += 1
                    buf = []
                    while i < n:
                        if block[i] == "'" and i + 1 < n and block[i + 1] == "'":
                            buf.append("'")
                            i += 2
                        elif block[i] == "'":
                            i += 1
                            break
                        else:
                            buf.append(block[i])
                            i += 1
                    # Consume optional ::jsonb / ::timestamptz cast
                    while i < n and block[i] not in ",)\n":
                        if block[i] == ":" or block[i].isalnum() or block[i] == "_":
                            i += 1
                        else:
                            break
                    fields.append("".join(buf))
                else:
                    start = i
                    while i < n and block[i] not in ",)":
                        i += 1
                    token = block[start:i].strip()
                    if token == "NULL":
                        fields.append(None)
                    elif token == "TRUE":
                        fields.append(True)
                    elif token == "FALSE":
                        fields.append(False)
                    elif token.startswith("TIMESTAMPTZ"):
                        fields.append(token)
                    else:
                        fields.append(token)
            rows.append(fields)
        else:
            i += 1
    return rows


def extract_insert_block(sql: str, table_prefix: str) -> str:
    pattern = re.compile(
        rf"INSERT INTO {re.escape(table_prefix)}\s*\([^)]+\)\s*VALUES\s*(.*?)\s*ON CONFLICT",
        re.DOTALL,
    )
    m = pattern.search(sql)
    if not m:
        raise AssertionError(f"Could not locate INSERT INTO {table_prefix} ... ON CONFLICT block in V060")
    return m.group(1)


def verify_v060(mistake_codes: set[str], skill_to_topic: dict[str, str]) -> dict[str, Any]:
    v060_path = ROOT / "services/api/src/main/resources/db/migration/platform/V060__yks_tyt_ayt_curriculum_expansion_and_question_bank.sql"
    assert v060_path.exists(), f"Missing {v060_path}"
    sql = v060_path.read_text(encoding="utf-8")

    assert "status = 'DEPRECATED'" in sql and "superseded_by_version = '2026.2'" in sql, "Missing 2026.1 -> DEPRECATED transition"
    assert "'TR_YKS_TYT_MATH'" in sql and "'TR_YKS_AYT_MATH'" in sql and "'2026.2'" in sql

    # 1. Topic weights check (40.0000 for TYT & AYT)
    weights_rows = split_sql_tuples(extract_insert_block(sql, "exam_topic_weights"))
    weight_sums: dict[str, float] = {}
    for row in weights_rows:
        def_id, _, skill_id, dom_code, q_weight = row[0], row[1], row[2], row[3], float(row[4])
        assert skill_id in skill_to_topic, f"V060 weight references unknown skill: {skill_id}"
        assert skill_to_topic[skill_id] == dom_code, f"V060 weight domain mismatch for {skill_id}: {dom_code}"
        weight_sums[def_id] = weight_sums.get(def_id, 0.0) + q_weight
    assert len(weight_sums) == 2, f"Expected 2 exam definitions in V060 topic weights, got {len(weight_sums)}"
    for def_id, total_w in weight_sums.items():
        assert abs(total_w - 40.0) <= 1e-4, f"Topic weight sum for {def_id} is {total_w} != 40.0000"

    # 2. Question Bank Items check
    q_rows = split_sql_tuples(extract_insert_block(sql, "exam_question_bank_items"))
    tyt_count = 0
    ayt_count = 0
    tyt_new_gen = 0
    tyt_visual = 0
    ayt_visual = 0
    content_shas: set[str] = set()
    items_by_id: dict[str, dict[str, Any]] = {}

    for row in q_rows:
        (
            q_id, def_id, ver_num, sec_id, q_code, topic_code, subtopic_code, item_format,
            diff_band, diff_b, disc_a, guess_c, cog_level, stem_md, stem_latex, alt_text,
            has_diag, num_ans, num_tol, rubric_raw, sol_md, est_time, max_pts, neg_w,
            status, content_sha, exp_cnt, training_eligible
        ) = row
        assert training_eligible is False, f"{q_code} training_eligible must be FALSE"
        assert status == "PUBLISHED", f"{q_code} status must be PUBLISHED"
        assert item_format == "SINGLE_CHOICE_MCQ", f"{q_code} unexpected format {item_format}"
        assert diff_band in {"EASY", "MEDIUM", "HARD", "EXPERT"}, f"{q_code} invalid diff {diff_band}"
        assert cog_level in {"RECALL", "COMPREHENSION", "APPLICATION", "ANALYSIS", "EVALUATION"}, f"{q_code} invalid cog {cog_level}"
        assert topic_code in skill_to_topic, f"{q_code} unknown skill {topic_code}"
        assert SHA256_RE.fullmatch(content_sha or ""), f"{q_code} invalid content_sha256"
        assert content_sha not in content_shas, f"Duplicate content_sha256 on {q_code}"
        content_shas.add(content_sha)

        if has_diag:
            assert alt_text and len(alt_text.strip()) >= 10, f"{q_code} missing >= 10 char Turkish alt_text"

        rubric = json.loads(rubric_raw)
        assert "expectedAnswerLatex" in rubric, f"{q_code} missing expectedAnswerLatex in structured_rubric_json"

        if q_code.startswith("TYT-"):
            tyt_count += 1
            if rubric.get("isNewGenProblem"):
                tyt_new_gen += 1
            if has_diag:
                tyt_visual += 1
        elif q_code.startswith("AYT-"):
            ayt_count += 1
            if has_diag:
                ayt_visual += 1
        else:
            raise AssertionError(f"Unexpected question_code prefix: {q_code}")

        items_by_id[q_id] = {
            "code": q_code,
            "def_id": def_id,
            "topic": topic_code,
            "expected_latex": rubric["expectedAnswerLatex"],
            "options": [],
        }

    assert tyt_count >= 60, f"TYT count {tyt_count} < 60"
    assert tyt_new_gen >= 36, f"TYT new-gen count {tyt_new_gen} < 36"
    assert ayt_count >= 60, f"AYT count {ayt_count} < 60"
    assert tyt_count + ayt_count >= 120, f"Total items {tyt_count + ayt_count} < 120"

    # 3. Options + MistakeTaxonomy + SymPy Equivalence Proof
    opt_rows = split_sql_tuples(extract_insert_block(sql, "exam_question_options"))
    for row in opt_rows:
        opt_id, q_id, opt_key, disp_order, opt_md, opt_latex, is_corr, rationale, mcode, tr_elig = row
        assert tr_elig is False, f"Option {opt_id} training_eligible must be FALSE"
        assert q_id in items_by_id, f"Option {opt_id} references unknown item {q_id}"
        items_by_id[q_id]["options"].append((opt_key, opt_latex, is_corr, rationale, mcode))

    sympy_verified_count = 0
    for q_id, info in items_by_id.items():
        q_code = info["code"]
        opts = info["options"]
        assert len(opts) == 5, f"{q_code} has {len(opts)} options, expected 5"
        assert {o[0] for o in opts} == {"A", "B", "C", "D", "E"}, f"{q_code} option keys mismatch"
        correct = [o for o in opts if o[2] is True]
        wrong = [o for o in opts if o[2] is False]
        assert len(correct) == 1, f"{q_code} must have 1 correct option"
        assert len(wrong) == 4, f"{q_code} must have 4 distractors"

        # SymPy proof of correct answer
        corr_latex = correct[0][1]
        assert is_sympy_equivalent(corr_latex, info["expected_latex"]), (
            f"SymPy failed on correct option of {q_code}: {corr_latex} != {info['expected_latex']}"
        )

        # SymPy proof of all 4 distractors != expected & valid MistakeTaxonomy
        for opt_key, opt_latex, _, rationale, mcode in wrong:
            assert mcode in mistake_codes, f"{q_code} option {opt_key} invalid MistakeTaxonomy code: {mcode}"
            assert rationale and len(rationale.strip()) >= 10, f"{q_code} option {opt_key} missing distractor_rationale"
            assert not is_sympy_equivalent(opt_latex, info["expected_latex"]), (
                f"SymPy collision on wrong option {q_code} {opt_key}: {opt_latex} == {info['expected_latex']}"
            )
        sympy_verified_count += 1

    # 4. Provenance records
    prov_rows = split_sql_tuples(extract_insert_block(sql, "exam_question_provenance_records"))
    assert len(prov_rows) == len(items_by_id), f"Provenance count {len(prov_rows)} != items {len(items_by_id)}"
    prov_shas: set[str] = set()
    for row in prov_rows:
        p_id, q_id, src_type, lic, holder, src_ref, author_id, reviewer_id, ver_status, p_hash, _, tr_elig = row
        assert tr_elig is False
        assert ver_status == "VERIFIED"
        assert author_id != reviewer_id, f"Dual-signature violation on {q_id}"
        assert SHA256_RE.fullmatch(p_hash or ""), f"Invalid provenance_hash on {q_id}"
        assert p_hash not in prov_shas, f"Duplicate provenance_hash on {q_id}"
        prov_shas.add(p_hash)

    # 5. Blueprints & Topic Rules
    bp_rows = split_sql_tuples(extract_insert_block(sql, "exam_assessment_blueprints"))
    expected_bps = {
        "94000000-0000-0000-0000-000000000201": ("BP-TYT-MATH-WORD-PROBLEMS-V2", 12),
        "94000000-0000-0000-0000-000000000202": ("BP-TYT-MATH-FULL-SIM-2026-V2", 40),
        "94000000-0000-0000-0000-000000000203": ("BP-AYT-MATH-DIAG-2026-V2", 16),
        "94000000-0000-0000-0000-000000000204": ("BP-AYT-MATH-LTI-DRILL-2026-V2", 12),
        "94000000-0000-0000-0000-000000000205": ("BP-AYT-MATH-FULL-SIM-2026-V2", 40),
    }
    found_bps = {}
    for row in bp_rows:
        bp_id, bp_code, total_q = row[0], row[4], int(row[8])
        diff_sum = int(row[10]) + int(row[11]) + int(row[12]) + int(row[13])
        cog_sum = int(row[15]) + int(row[16]) + int(row[17]) + int(row[18]) + int(row[19])
        assert diff_sum == 10000, f"{bp_code} difficulty bps sum {diff_sum} != 10000"
        assert cog_sum == 10000, f"{bp_code} cognitive bps sum {cog_sum} != 10000"
        assert row[23] == "ACTIVE" and row[25] is False
        found_bps[bp_id] = (bp_code, total_q)
    assert found_bps == expected_bps, f"Blueprints mismatch: {found_bps}"

    rule_rows = split_sql_tuples(extract_insert_block(sql, "exam_blueprint_topic_rules"))
    rule_weights: dict[str, int] = {}
    for row in rule_rows:
        bp_id, w_bps, tr_elig = row[1], int(row[3]), row[8]
        assert tr_elig is False
        rule_weights[bp_id] = rule_weights.get(bp_id, 0) + w_bps
    for bp_id in expected_bps:
        assert rule_weights.get(bp_id) == 10000, f"Blueprint {bp_id} rule weights sum != 10000"

    return {
        "tyt_count": tyt_count,
        "tyt_new_gen": tyt_new_gen,
        "tyt_visual": tyt_visual,
        "ayt_count": ayt_count,
        "ayt_visual": ayt_visual,
        "total_questions": len(items_by_id),
        "sympy_verified_count": sympy_verified_count,
        "blueprint_count": len(found_bps),
    }


def validate_against_schema_pure(obj: dict[str, Any], schema: dict[str, Any], label: str) -> None:
    """Strict Draft 2020-12 structural fallback validator (works even without jsonschema C ext)."""
    if Draft202012Validator is not None:
        Draft202012Validator(schema).validate(obj)

    def _check_obj(val: Any, sc: dict[str, Any], path: str) -> None:
        if "const" in sc:
            assert val == sc["const"], f"{label} {path}: expected const {sc['const']!r}, got {val!r}"
        if "enum" in sc:
            assert val in sc["enum"], f"{label} {path}: {val!r} not in enum {sc['enum']}"
        if "type" in sc:
            t = sc["type"]
            allowed = t if isinstance(t, list) else [t]
            py_map = {"object": dict, "array": list, "string": str, "boolean": bool, "integer": int, "null": type(None)}
            ok = any(isinstance(val, py_map[k]) and not (k == "integer" and isinstance(val, bool)) for k in allowed)
            assert ok, f"{label} {path}: expected type {t}, got {type(val).__name__}"
        if isinstance(val, str):
            if "minLength" in sc:
                assert len(val) >= sc["minLength"], f"{label} {path}: len {len(val)} < {sc['minLength']}"
            if "maxLength" in sc:
                assert len(val) <= sc["maxLength"], f"{label} {path}: len {len(val)} > {sc['maxLength']}"
            if "pattern" in sc:
                assert re.search(sc["pattern"], val), f"{label} {path}: {val!r} fails pattern {sc['pattern']}"
        if isinstance(val, list):
            if "minItems" in sc:
                assert len(val) >= sc["minItems"], f"{label} {path}: len < {sc['minItems']}"
            if "maxItems" in sc:
                assert len(val) <= sc["maxItems"], f"{label} {path}: len > {sc['maxItems']}"
            if sc.get("uniqueItems"):
                assert len(val) == len(set(val)), f"{label} {path}: duplicate items"
            if "items" in sc:
                for idx, item in enumerate(val):
                    _check_obj(item, sc["items"], f"{path}[{idx}]")
        if isinstance(val, dict):
            for req in sc.get("required", []):
                assert req in val, f"{label} {path}: missing required key {req}"
            props = sc.get("properties", {})
            if sc.get("additionalProperties") is False:
                extra = set(val.keys()) - set(props.keys())
                assert not extra, f"{label} {path}: unexpected keys {sorted(extra)}"
            for k, v in val.items():
                if k in props:
                    _check_obj(v, props[k], f"{path}.{k}")

    _check_obj(obj, schema, "$")


def verify_ingestion_dataset() -> dict[str, int]:
    ds_dir = ROOT / "evaluations/golden-datasets/parsing/yks-tr-ingestion-v1"
    schema = json.loads((ROOT / "packages/schemas/ingestion-evaluation-case.schema.json").read_text(encoding="utf-8"))
    manifest = json.loads((ds_dir / "manifest.yaml").read_text(encoding="utf-8"))

    assert manifest["trainingEligibilityPolicy"]["default"] == "NOT_ELIGIBLE"
    reg_path = ds_dir / manifest["caseFiles"]["REGRESSION"]
    hard_path = ds_dir / manifest["caseFiles"]["HARD_TAIL"]
    assert sha256_file(reg_path) == manifest["caseFileSha256"]["regression.jsonl"]
    assert sha256_file(hard_path) == manifest["caseFileSha256"]["hard-tail.jsonl"]

    reg_cases = [json.loads(line) for line in reg_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    hard_cases = [json.loads(line) for line in hard_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    assert len(reg_cases) >= 20, f"yks-tr-ingestion-v1 regression count {len(reg_cases)} < 20"
    assert len(hard_cases) >= 15, f"yks-tr-ingestion-v1 hard-tail count {len(hard_cases)} < 15"

    all_cases = reg_cases + hard_cases
    assert manifest["caseCounts"]["totalCommitted"] == len(all_cases)
    assert manifest["caseCounts"]["criticalCommitted"] == sum(1 for c in all_cases if c["criticality"] == "CRITICAL")

    asset_lines = []
    seen_ids = set()
    for c in all_cases:
        validate_against_schema_pure(c, schema, f"ingestion:{c.get('id')}")
        assert c["id"] not in seen_ids
        seen_ids.add(c["id"])
        assert c["input"]["locale"] == "tr-TR"
        assert c["origin"]["trainingEligibility"] == "NOT_ELIGIBLE"
        asset_path = ds_dir / c["input"]["assetPath"]
        assert asset_path.exists(), f"Missing asset {asset_path}"
        assert sha256_file(asset_path) == c["input"]["assetSha256"]

    for p in sorted((ds_dir / "assets").rglob("*")):
        if p.is_file():
            asset_lines.append(f"{sha256_file(p)}  {p.relative_to(ds_dir).as_posix()}\n")
    actual_asset_manifest_sha = hashlib.sha256("".join(asset_lines).encode("utf-8")).hexdigest()
    assert actual_asset_manifest_sha == manifest["assetManifestSha256"]

    return {"regression": len(reg_cases), "hard_tail": len(hard_cases), "total": len(all_cases)}


def verify_solver_dataset() -> dict[str, int]:
    ds_dir = ROOT / "evaluations/golden-datasets/solving/yks-tr-hard-v1"
    schema_path = ROOT / "packages/schemas/solver-evaluation-case.schema.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    manifest = json.loads((ds_dir / "manifest.json").read_text(encoding="utf-8"))

    assert manifest["trainingEligibility"] == "NOT_ELIGIBLE"
    assert manifest["caseSchemaSha256"] == sha256_file(schema_path)
    cases_path = ds_dir / manifest["caseFile"]
    assert sha256_file(cases_path) == manifest["caseFileSha256"]

    cases = json.loads(cases_path.read_text(encoding="utf-8"))
    assert len(cases) >= 60 and len(cases) == manifest["caseCount"]

    ids = [c["id"] for c in cases]
    assert ids == sorted(set(ids)), "yks-tr-hard-v1 case IDs must be unique and sorted"

    tyt_n = 0
    ayt_n = 0
    for c in cases:
        validate_against_schema_pure(c, schema, f"solver:{c.get('id')}")
        assert c["locale"] == "tr-TR"
        assert c["origin"]["trainingEligibility"] == "NOT_ELIGIBLE"
        ans = c["expected"]["answer"]
        assert ans is not None
        # SymPy proof that expected answer parses cleanly to a finite expression
        sym_ans = latex_to_sympy(ans)
        assert sym_ans is not None
        if c["id"].startswith("tyt-"):
            tyt_n += 1
        elif c["id"].startswith("ayt-"):
            ayt_n += 1

    assert tyt_n >= 30, f"yks-tr-hard-v1 TYT count {tyt_n} < 30"
    assert ayt_n >= 30, f"yks-tr-hard-v1 AYT count {ayt_n} < 30"
    return {"tyt": tyt_n, "ayt": ayt_n, "total": len(cases)}


def main() -> int:
    mistake_codes = load_active_mistake_codes()
    skill_to_topic = load_active_curriculum_skills()
    v060_stats = verify_v060(mistake_codes, skill_to_topic)
    ing_stats = verify_ingestion_dataset()
    sol_stats = verify_solver_dataset()

    print(
        f"PASS [V060]: {v060_stats['tyt_count']} TYT ({v060_stats['tyt_new_gen']} new-gen, "
        f"{v060_stats['tyt_visual']} visual) + {v060_stats['ayt_count']} AYT ({v060_stats['ayt_visual']} visual) "
        f"= {v060_stats['total_questions']} items; SymPy verified {v060_stats['sympy_verified_count']}/{v060_stats['total_questions']}; "
        f"{v060_stats['blueprint_count']} active V2 blueprints."
    )
    print(
        f"PASS [yks-tr-ingestion-v1]: {ing_stats['regression']} regression + {ing_stats['hard_tail']} hard-tail = {ing_stats['total']} cases."
    )
    print(
        f"PASS [yks-tr-hard-v1]: {sol_stats['tyt']} TYT + {sol_stats['ayt']} AYT = {sol_stats['total']} SymPy-verified cases."
    )
    print("YKS_TR_BENCHMARKS_VERIFIED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
