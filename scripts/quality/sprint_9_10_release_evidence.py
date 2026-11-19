#!/usr/bin/env python3
"""
Sprint 9.10 Fail-Closed Owner Release Evidence Helper.

Strictly owner-executed on staging / physical-device sign-off. Never fabricates
latency numbers, device verification, defect counts, or owner authorization.

Outputs into .generated/sprint-9-10-owner/<RUN_ID>/:
  1. staging-latency-sla.json   (when --run-staging-sla is executed against live target)
  2. owner-evidence-manifest.json (compatible with ExamQualityGateService.OwnerEvidenceManifest)
"""
from __future__ import annotations

import argparse
import getpass
import json
import math
import re
import ssl
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SLA_LIMIT_MS = 350.0
MIN_WARMED_SAMPLES = 200
EXPECTED_FORMULA_COUNT = 50


def read_bearer_token(args: argparse.Namespace) -> str:
    if args.token_stdin:
        token = sys.stdin.read().strip()
        if not token:
            raise ValueError("Empty bearer token received from stdin.")
        return token
    token = getpass.getpass("Owner/Learner Bearer JWT (input hidden): ").strip()
    if not token:
        raise ValueError("Bearer token is required to probe authenticated CNT-LOS-10/11 routes.")
    return token


def percentile(sorted_values: list[float], pct: float) -> float:
    if not sorted_values:
        raise ValueError("Cannot compute percentile of empty samples.")
    idx = max(0, min(len(sorted_values) - 1, math.ceil((pct / 100.0) * len(sorted_values)) - 1))
    return round(sorted_values[idx], 3)


def probe_endpoint(
    base_url: str,
    path: str,
    bearer_token: str,
    warmup: int,
    samples: int,
    timeout_seconds: float,
    allow_insecure_tls: bool,
) -> dict[str, object]:
    url = base_url.rstrip("/") + path
    ctx = ssl._create_unverified_context() if allow_insecure_tls else ssl.create_default_context()
    headers = {
        "Authorization": f"Bearer {bearer_token}",
        "Accept": "application/json",
        "Cache-Control": "no-store",
    }

    for i in range(warmup):
        req = urllib.request.Request(url, headers=headers, method="GET")
        try:
            with urllib.request.urlopen(req, timeout=timeout_seconds, context=ctx) as resp:
                _ = resp.read()
                if resp.status < 200 or resp.status >= 300:
                    raise RuntimeError(f"Warmup {i+1}/{warmup} on {path} returned HTTP {resp.status}")
        except urllib.error.HTTPError as exc:
            raise RuntimeError(f"Warmup {i+1}/{warmup} on {path} failed HTTP {exc.code}") from exc

    durations_ms: list[float] = []
    for i in range(samples):
        req = urllib.request.Request(url, headers=headers, method="GET")
        t0 = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=timeout_seconds, context=ctx) as resp:
                _ = resp.read()
                elapsed_ms = (time.perf_counter() - t0) * 1000.0
                if resp.status < 200 or resp.status >= 300:
                    raise RuntimeError(f"Sample {i+1}/{samples} on {path} returned HTTP {resp.status}")
                durations_ms.append(elapsed_ms)
        except urllib.error.HTTPError as exc:
            raise RuntimeError(f"Sample {i+1}/{samples} on {path} failed HTTP {exc.code}") from exc

    ordered = sorted(durations_ms)
    p50 = percentile(ordered, 50.0)
    p95 = percentile(ordered, 95.0)
    p99 = percentile(ordered, 99.0)
    passed = len(ordered) >= MIN_WARMED_SAMPLES and p95 < SLA_LIMIT_MS
    return {
        "endpoint": path,
        "warmupCount": warmup,
        "sampleCount": len(ordered),
        "p50Ms": p50,
        "p95Ms": p95,
        "p99Ms": p99,
        "maxMs": round(ordered[-1], 3),
        "thresholdMs": SLA_LIMIT_MS,
        "passed": passed,
        "measuredAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }


def verify_local_run(run_dir: Path) -> dict[str, object]:
    meta_path = run_dir / "local-run-metadata.json"
    if not meta_path.is_file():
        raise FileNotFoundError(f"Missing local run metadata: {meta_path}")
    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    if meta.get("localAutomatedGatesPassed") is not True:
        raise ValueError(f"Local gates have not passed in {meta_path}")

    logs = meta.get("logs", {})
    required_keys = ["contracts", "schemas", "docs", "mavenUnit", "mavenPostgres", "swiftStatic", "xcodeTest"]
    for key in required_keys:
        rel = logs.get(key)
        if not rel:
            raise ValueError(f"Missing log entry '{key}' in {meta_path}")
        log_path = REPO_ROOT / rel
        if not log_path.is_file():
            raise FileNotFoundError(f"Missing log file for '{key}': {log_path}")

    unit_text = (REPO_ROOT / logs["mavenUnit"]).read_text(encoding="utf-8", errors="replace")
    pg_text = (REPO_ROOT / logs["mavenPostgres"]).read_text(encoding="utf-8", errors="replace")
    xcode_text = (REPO_ROOT / logs["xcodeTest"]).read_text(encoding="utf-8", errors="replace")

    if "Tests run: 22, Failures: 0, Errors: 0, Skipped: 0" not in unit_text or "BUILD SUCCESS" not in unit_text:
        raise ValueError("maven_unit_modulith.log does not confirm 22/22 passing tests.")
    if "Tests run: 2, Failures: 0, Errors: 0, Skipped: 0" not in pg_text or "BUILD SUCCESS" not in pg_text:
        raise ValueError("maven_postgres_it.log does not confirm 2/2 PostgreSQL integration tests.")
    if "** TEST SUCCEEDED **" not in xcode_text or "Executed 11 tests, with 0 failures" not in xcode_text:
        raise ValueError("xcode_test.log does not confirm 11/11 XCTests succeeded.")

    return meta


def main() -> int:
    parser = argparse.ArgumentParser(description="Sprint 9.10 fail-closed release evidence collector")
    parser.add_argument(
        "--run-dir",
        required=True,
        help="Path to passed local acceptance directory (e.g. .generated/sprint-9-10-owner/s910-20261119T111201Z)",
    )
    parser.add_argument("--run-staging-sla", action="store_true", help="Benchmark CNT-LOS-10 and CNT-LOS-11 live")
    parser.add_argument("--base-url", help="Live staging/local base URL (e.g. https://staging.verifiedai.example)")
    parser.add_argument("--session-id", help="Existing exam session UUID owned by authenticated test user (for CNT-LOS-10)")
    parser.add_argument("--exam-code", default="TR_YKS_TYT_MATH", help="Exam code for CNT-LOS-11 readiness query")
    parser.add_argument("--warmup", type=int, default=20, help="Warmup requests before timing (default: 20)")
    parser.add_argument("--samples", type=int, default=220, help="Measured requests per endpoint >= 200 (default: 220)")
    parser.add_argument("--timeout", type=float, default=10.0, help="HTTP request timeout seconds (default: 10)")
    parser.add_argument("--allow-insecure-tls", action="store_true", help="Skip TLS cert check for local self-signed dev")
    parser.add_argument("--token-stdin", action="store_true", help="Read bearer JWT from stdin instead of prompt")
    parser.add_argument("--staging-sla-json", help="Existing staging-latency-sla.json to build manifest from")
    parser.add_argument("--device-voiceover-evidence", help="Physical iOS device VoiceOver verification summary string")
    parser.add_argument("--confirm-device-voiceover", action="store_true", help="Confirm physical device VoiceOver tr-TR passed")
    parser.add_argument("--open-p0", type=int, default=None, help="Verified open P0 count from issue tracker (must be 0)")
    parser.add_argument("--open-p1", type=int, default=None, help="Verified open P1 count from issue tracker (must be 0)")
    parser.add_argument("--defect-ledger-source", help="Defect ledger query reference / URL / snapshot ID")
    parser.add_argument("--authorize-owner-signoff", action="store_true", help="Explicit owner sign-off flag for Phase 9 exit")
    args = parser.parse_args()

    run_dir = Path(args.run_dir)
    if not run_dir.is_absolute():
        run_dir = (REPO_ROOT / run_dir).resolve()

    local_meta = verify_local_run(run_dir)
    commit_sha = str(local_meta["commitSha"])
    run_id = str(local_meta["runId"])
    if not re.fullmatch(r"[0-9a-fA-F]{7,64}", commit_sha):
        raise ValueError(f"Invalid commitSha in local metadata: {commit_sha}")

    sla_data: dict[str, object] | None = None
    sla_out_path = run_dir / "staging-latency-sla.json"

    if args.run_staging_sla:
        if not args.base_url or not args.session_id:
            raise ValueError("--run-staging-sla requires --base-url and --session-id")
        if args.samples < MIN_WARMED_SAMPLES:
            raise ValueError(f"--samples must be >= {MIN_WARMED_SAMPLES}")
        bearer = read_bearer_token(args)
        cnt_los_10 = probe_endpoint(
            args.base_url,
            f"/v1/exams/sessions/{args.session_id}",
            bearer,
            args.warmup,
            args.samples,
            args.timeout,
            args.allow_insecure_tls,
        )
        cnt_los_11 = probe_endpoint(
            args.base_url,
            f"/v1/exams/readiness?examCode={args.exam_code}",
            bearer,
            args.warmup,
            args.samples,
            args.timeout,
            args.allow_insecure_tls,
        )
        sla_data = {
            "runId": run_id,
            "commitSha": commit_sha,
            "baseUrl": args.base_url,
            "cntLos10": cnt_los_10,
            "cntLos11": cnt_los_11,
        }
        sla_out_path.write_text(json.dumps(sla_data, indent=2) + "\n", encoding="utf-8")
        print(f"Wrote staging SLA benchmark report: {sla_out_path}")
        print(f"  CNT-LOS-10 p95={cnt_los_10['p95Ms']}ms (n={cnt_los_10['sampleCount']}, pass={cnt_los_10['passed']})")
        print(f"  CNT-LOS-11 p95={cnt_los_11['p95Ms']}ms (n={cnt_los_11['sampleCount']}, pass={cnt_los_11['passed']})")
    elif args.staging_sla_json:
        sla_data = json.loads(Path(args.staging_sla_json).read_text(encoding="utf-8"))
    elif sla_out_path.is_file():
        sla_data = json.loads(sla_out_path.read_text(encoding="utf-8"))

    if sla_data is None:
        print("STAGING_SLA_PENDING: Run with --run-staging-sla --base-url ... --session-id ... to collect real P95 latency.")
        return 0

    los10 = sla_data["cntLos10"]
    los11 = sla_data["cntLos11"]
    assert isinstance(los10, dict) and isinstance(los11, dict)

    device_passed = bool(args.confirm_device_voiceover and args.device_voiceover_evidence and args.device_voiceover_evidence.strip())
    accessibility_detail = (
        f"50 golden tr-TR formulas + AX3/320pt XCTest passed ({run_id}); device={args.device_voiceover_evidence.strip()}"
        if device_passed
        else f"PENDING_OWNER_DEVICE_EVIDENCE (Simulator 11/11 passed in {run_id})"
    )

    manifest = {
        "sourceCommitSha": commit_sha,
        "evidenceRunId": run_id,
        "evidenceGeneratedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "cntLos10P95Ms": los10["p95Ms"],
        "cntLos10SampleCount": los10["sampleCount"],
        "cntLos10MeasurementSource": f"owner-staging-benchmark:{los10['endpoint']}@{sla_data.get('baseUrl', 'staging')}",
        "cntLos11P95Ms": los11["p95Ms"],
        "cntLos11SampleCount": los11["sampleCount"],
        "cntLos11MeasurementSource": f"owner-staging-benchmark:{los11['endpoint']}@{sla_data.get('baseUrl', 'staging')}",
        "openP0Count": args.open_p0,
        "openP1Count": args.open_p1,
        "defectLedgerSource": args.defect_ledger_source or "UNSUPPLIED_OWNER_DEFECT_LEDGER",
        "securityAndReplayPassed": True,
        "securityAndReplayDetail": f"22/22 unit/Modulith + PG V064 replay/concurrency IT ({run_id})",
        "accessibilityTrTRPassed": device_passed,
        "verifiedFormulaCount": EXPECTED_FORMULA_COUNT,
        "accessibilityDetail": accessibility_detail,
        "offlineRecoveryPassed": True,
        "offlineRecoveryDetail": f"ExamSessionViewModelTests + ExamSessionSQLiteStoreTests ({run_id})",
        "privacyLifecyclePassed": True,
        "privacyLifecycleDetail": f"ExamIntegrityAuditRepositoryIntegrationTest + ExamPrivacyLifecycleContributorTest ({run_id})",
        "ownerSignOffAuthorized": bool(
            args.authorize_owner_signoff
            and device_passed
            and bool(los10.get("passed"))
            and bool(los11.get("passed"))
            and args.open_p0 == 0
            and args.open_p1 == 0
            and bool(args.defect_ledger_source)
        ),
    }

    manifest_path = run_dir / "owner-evidence-manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote ExamQualityGateService manifest: {manifest_path}")
    print(f"  ownerSignOffAuthorized={manifest['ownerSignOffAuthorized']}")
    if not manifest["ownerSignOffAuthorized"]:
        print("PHASE_9_EXIT_STILL_BLOCKED: Check SLA < 350ms, n >= 200, openP0=0, openP1=0, --confirm-device-voiceover, --authorize-owner-signoff.")
        return 1
    print("OWNER_EVIDENCE_MANIFEST_READY: Configure VERIFIEDAI_EXAM_QUALITY_GATE_EVIDENCE_PATH and query GET /v1/admin/exams/quality-gate.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
