#!/usr/bin/env python3
"""Sprint 10.8 static contract, schema, OpenAPI dual-mirror, migration, and backend invariant validator."""

from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VENV_SITE = ROOT / "services" / "math-verifier" / ".venv" / "lib" / "python3.11" / "site-packages"
if VENV_SITE.exists() and str(VENV_SITE) not in sys.path:
    sys.path.insert(0, str(VENV_SITE))

from jsonschema import Draft202012Validator, FormatChecker, ValidationError  # noqa: E402
import yaml  # noqa: E402

ADMIN_SCHEMA_DIR = ROOT / "packages" / "schemas" / "admin"
FIXTURES_DIR = ADMIN_SCHEMA_DIR / "fixtures"

SCHEMA_FIXTURE_PAIRS = [
    ("admin-user-lookup-v1.schema.json", "admin-user-lookup-v1.valid.json"),
    ("admin-problem-trace-v1.schema.json", "admin-problem-trace-v1.valid.json"),
    ("admin-raw-content-grant-request-v1.schema.json", "admin-raw-content-grant-request-v1.valid.json"),
    ("admin-billing-support-v1.schema.json", "admin-billing-support-v1.valid.json"),
    ("admin-ai-ops-overview-v1.schema.json", "admin-ai-ops-overview-v1.valid.json"),
    ("wrong-answer-report-create-request-v1.schema.json", "wrong-answer-report-create-request-v1.valid.json"),
    ("wrong-answer-report-review-request-v1.schema.json", "wrong-answer-report-review-request-v1.valid.json"),
    ("wrong-answer-report-queue-v1.schema.json", "wrong-answer-report-queue-v1.valid.json"),
    ("admin-remediation-result-v1.schema.json", "admin-remediation-result-v1.valid.json"),
    ("admin-support-audit-list-v1.schema.json", "admin-support-audit-list-v1.valid.json"),
]

EXPECTED_OPENAPI_PATHS = [
    ("/v1/admin/support/users/lookup", "get"),
    ("/api/v1/admin/support/users/lookup", "get"),
    ("/v1/admin/support/users/{userId}/billing", "get"),
    ("/api/v1/admin/support/users/{userId}/billing", "get"),
    ("/v1/admin/support/problems/trace", "get"),
    ("/api/v1/admin/support/problems/trace", "get"),
    ("/v1/admin/support/problems/{problemSessionId}/raw-content-access", "post"),
    ("/api/v1/admin/support/problems/{problemSessionId}/raw-content-access", "post"),
    ("/v1/admin/support/problems/{problemSessionId}/raw-content", "get"),
    ("/api/v1/admin/support/problems/{problemSessionId}/raw-content", "get"),
    ("/v1/support/wrong-answer-reports", "post"),
    ("/api/v1/support/wrong-answer-reports", "post"),
    ("/v1/admin/support/wrong-answer-reports", "get"),
    ("/api/v1/admin/support/wrong-answer-reports", "get"),
    ("/v1/admin/support/wrong-answer-reports/{reportId}", "patch"),
    ("/api/v1/admin/support/wrong-answer-reports/{reportId}", "patch"),
    ("/v1/admin/support/remediations/billing-reconcile", "post"),
    ("/api/v1/admin/support/remediations/billing-reconcile", "post"),
    ("/v1/admin/support/remediations/quota-sync", "post"),
    ("/api/v1/admin/support/remediations/quota-sync", "post"),
    ("/v1/admin/support/remediations/kill-switch", "post"),
    ("/api/v1/admin/support/remediations/kill-switch", "post"),
    ("/v1/admin/ai-ops/overview", "get"),
    ("/api/v1/admin/ai-ops/overview", "get"),
    ("/v1/admin/support/audit", "get"),
    ("/api/v1/admin/support/audit", "get"),
]

EXPECTED_OPENAPI_SCHEMAS = [
    "AdminUserLookupResponse",
    "AdminProblemTraceResponse",
    "RawContentGrantRequest",
    "AdminRawContentGrantResponse",
    "AdminRawContentInspectionResponse",
    "AdminBillingSupportResponse",
    "AdminAiOpsOverviewResponse",
    "WrongAnswerReportCreateRequest",
    "WrongAnswerReportReviewRequest",
    "WrongAnswerReportResponse",
    "WrongAnswerReportQueueResponse",
    "BillingReconcileRemediationRequest",
    "QuotaSyncRemediationRequest",
    "KillSwitchRemediationRequest",
    "AdminRemediationResultResponse",
    "AdminSupportAuditListResponse",
]


def _load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _assert_rejected(validator: Draft202012Validator, payload: dict, label: str) -> None:
    try:
        validator.validate(payload)
    except ValidationError:
        return
    raise AssertionError(f"Expected ValidationError for negative mutation: {label}")


def validate_schemas_and_fixtures() -> None:
    format_checker = FormatChecker()
    for schema_name, fixture_name in SCHEMA_FIXTURE_PAIRS:
        schema_path = ADMIN_SCHEMA_DIR / schema_name
        fixture_path = FIXTURES_DIR / fixture_name
        assert schema_path.is_file(), f"Missing schema: {schema_path}"
        assert fixture_path.is_file(), f"Missing fixture: {fixture_path}"

        schema = _load_json(schema_path)
        assert schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema", (
            f"{schema_name} must be Draft 2020-12"
        )
        assert schema.get("additionalProperties") is False, f"{schema_name} must set additionalProperties=false"
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema, format_checker=format_checker)

        fixture = _load_json(fixture_path)
        validator.validate(fixture)

        # Common negative mutations across all 10 schemas
        mutated_train = copy.deepcopy(fixture)
        mutated_train["trainingEligible"] = True
        _assert_rejected(validator, mutated_train, f"{schema_name}:trainingEligible=true")

        mutated_extra = copy.deepcopy(fixture)
        mutated_extra["unexpectedExtra"] = True
        _assert_rejected(validator, mutated_extra, f"{schema_name}:unexpectedExtra")

        first_required = schema["required"][0]
        mutated_missing = copy.deepcopy(fixture)
        mutated_missing.pop(first_required, None)
        _assert_rejected(validator, mutated_missing, f"{schema_name}:missing:{first_required}")

        if "llmTokensUsed" in fixture:
            mutated_llm = copy.deepcopy(fixture)
            mutated_llm["llmTokensUsed"] = 1
            _assert_rejected(validator, mutated_llm, f"{schema_name}:llmTokensUsed=1")

        if schema_name == "admin-problem-trace-v1.schema.json":
            mutated_unredacted = copy.deepcopy(fixture)
            mutated_unredacted["rawContentRedacted"] = False
            _assert_rejected(validator, mutated_unredacted, f"{schema_name}:rawContentRedacted=false")

            mutated_obj_key = copy.deepcopy(fixture)
            mutated_obj_key["assets"][0]["objectKey"] = "users/u1/raw.jpg"
            _assert_rejected(validator, mutated_obj_key, f"{schema_name}:asset.objectKey")

            mutated_raw_parse = copy.deepcopy(fixture)
            mutated_raw_parse["parseRevisions"][0]["rawOutputJsonb"] = "{}"
            _assert_rejected(validator, mutated_raw_parse, f"{schema_name}:parse.rawOutputJsonb")

            mutated_norm_parse = copy.deepcopy(fixture)
            mutated_norm_parse["parseRevisions"][0]["normalizedProblemJsonb"] = "{}"
            _assert_rejected(validator, mutated_norm_parse, f"{schema_name}:parse.normalizedProblemJsonb")

        if schema_name == "admin-raw-content-grant-request-v1.schema.json":
            mutated_reason = copy.deepcopy(fixture)
            mutated_reason["reason"] = "ok"
            _assert_rejected(validator, mutated_reason, f"{schema_name}:reason=ok")

            mutated_ticket = copy.deepcopy(fixture)
            mutated_ticket["ticketReference"] = "bad ticket!"
            _assert_rejected(validator, mutated_ticket, f"{schema_name}:invalid_ticketReference")

            mutated_ttl = copy.deepcopy(fixture)
            mutated_ttl["ttlMinutes"] = 61
            _assert_rejected(validator, mutated_ttl, f"{schema_name}:ttlMinutes=61")

        if schema_name == "wrong-answer-report-review-request-v1.schema.json":
            mutated_reason = copy.deepcopy(fixture)
            mutated_reason["reason"] = "ok"
            _assert_rejected(validator, mutated_reason, f"{schema_name}:reason=ok")

            mutated_status = copy.deepcopy(fixture)
            mutated_status["status"] = "OPEN"
            _assert_rejected(validator, mutated_status, f"{schema_name}:status=OPEN")

        if schema_name == "wrong-answer-report-queue-v1.schema.json":
            mutated_item_status = copy.deepcopy(fixture)
            mutated_item_status["reports"][0]["status"] = "INVALID_STATUS"
            _assert_rejected(validator, mutated_item_status, f"{schema_name}:report.status=INVALID_STATUS")

        if schema_name == "admin-support-audit-list-v1.schema.json":
            mutated_entry_reason = copy.deepcopy(fixture)
            mutated_entry_reason["entries"][0]["reason"] = "ok"
            _assert_rejected(validator, mutated_entry_reason, f"{schema_name}:entry.reason=ok")


def validate_openapi_dual_mirrors() -> None:
    public_path = ROOT / "packages" / "contracts" / "openapi" / "public-api.yaml"
    core_path = ROOT / "packages" / "openapi" / "verified-ai-core-api.yaml"
    public_bytes = public_path.read_bytes()
    core_bytes = core_path.read_bytes()
    assert public_bytes == core_bytes, "OpenAPI dual mirrors public-api.yaml and verified-ai-core-api.yaml diverged"

    spec = yaml.safe_load(public_bytes.decode("utf-8"))
    paths = spec.get("paths", {})
    assert len(EXPECTED_OPENAPI_PATHS) == 26
    for path, method in EXPECTED_OPENAPI_PATHS:
        assert path in paths, f"Missing Sprint 10.8 path in OpenAPI: {path}"
        op = paths[path].get(method)
        assert op is not None, f"Missing method {method.upper()} on {path}"
        responses = op.get("responses", {})
        success_codes = [c for c in ("200", "201") if c in responses]
        assert success_codes, f"Missing 200/201 response on {method.upper()} {path}"
        for code in success_codes:
            headers = responses[code].get("headers", {})
            assert "Cache-Control" in headers, f"Missing Cache-Control header on {method.upper()} {path} {code}"

    schemas = spec.get("components", {}).get("schemas", {})
    for schema_name in EXPECTED_OPENAPI_SCHEMAS:
        assert schema_name in schemas, f"Missing OpenAPI component schema: {schema_name}"


def validate_migrations_and_pins() -> None:
    mig_dir = ROOT / "services" / "api" / "src" / "main" / "resources" / "db" / "migration" / "platform"
    for num in range(65, 73):
        matches = list(mig_dir.glob(f"V0{num}__*.sql"))
        assert matches, f"Missing migration V0{num} in {mig_dir}"

    v072_path = mig_dir / "V072__sprint_10_8_admin_and_support_console_foundations.sql"
    assert v072_path.is_file(), f"Missing {v072_path}"
    v072 = v072_path.read_text(encoding="utf-8")

    for table in (
        "admin_support_audit_logs",
        "admin_raw_content_access_grants",
        "wrong_answer_reports",
    ):
        assert f"CREATE TABLE {table}" in v072, f"Missing table {table} in V072"

    for ck_name in (
        "ck_adm_audit_training_ineligible CHECK (NOT training_eligible)",
        "ck_adm_raw_grant_training_ineligible CHECK (NOT training_eligible)",
        "ck_war_training_ineligible CHECK (NOT training_eligible)",
    ):
        assert ck_name in v072, f"V072 must enforce '{ck_name}'"
    assert "ON DELETE CASCADE" in v072, "V072 must define ON DELETE CASCADE"

    mig_106 = (
        ROOT / "services" / "api" / "src" / "test" / "java" / "com" / "verifiedai"
        / "analytics" / "application" / "Sprint106AnalyticsMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("70")' in mig_106, "Sprint106AnalyticsMigrationTest must remain pinned at .target(\"70\")"

    mig_107 = (
        ROOT / "services" / "api" / "src" / "test" / "java" / "com" / "verifiedai"
        / "featureflags" / "application" / "Sprint107FeatureFlagMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("70")' in mig_107 and '.target("71")' in mig_107, (
        "Sprint107FeatureFlagMigrationTest must remain pinned at .target(\"70\") -> .target(\"71\")"
    )

    mig_108 = (
        ROOT / "services" / "api" / "src" / "test" / "java" / "com" / "verifiedai"
        / "admin" / "application" / "Sprint108AdminMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("71")' in mig_108 and '.target("72")' in mig_108, (
        "Sprint108AdminMigrationTest must test .target(\"71\") -> .target(\"72\") upgrade"
    )


def validate_backend_invariants() -> None:
    app_java = (
        ROOT / "services" / "api" / "src" / "main" / "java" / "com" / "verifiedai" / "VerifiedAiApplication.java"
    ).read_text(encoding="utf-8")
    assert "@EnableScheduling" not in app_java, "VerifiedAiApplication must never declare @EnableScheduling"

    admin_main_dir = ROOT / "services" / "api" / "src" / "main" / "java" / "com" / "verifiedai" / "admin"
    for java_file in admin_main_dir.rglob("*.java"):
        text = java_file.read_text(encoding="utf-8")
        assert "@Autowired" not in text, f"Field injection (@Autowired) prohibited in {java_file}"

    app_yml = (ROOT / "services" / "api" / "src" / "main" / "resources" / "application.yml").read_text(
        encoding="utf-8"
    )
    for snippet in (
        "admin:",
        "VERIFIEDAI_ADMIN_CONSOLE_ENABLED:false}",
        "VERIFIEDAI_ADMIN_REMEDIATION_ENABLED:false}",
        "VERIFIEDAI_ADMIN_RAW_CONTENT_ACCESS_ENABLED:false}",
    ):
        assert snippet in app_yml, f"Missing fail-closed admin config snippet in application.yml: {snippet}"


def validate_operational_artifacts() -> None:
    alerts_path = ROOT / "infra" / "monitoring" / "alerts" / "admin_support_alerts.yaml"
    assert alerts_path.is_file(), f"Missing Prometheus alert rules: {alerts_path}"
    alerts_doc = yaml.safe_load(alerts_path.read_text(encoding="utf-8"))
    groups = alerts_doc.get("groups", [])
    assert groups, "admin_support_alerts.yaml must define alert groups"
    alert_names = {
        rule.get("alert")
        for group in groups
        for rule in group.get("rules", [])
        if isinstance(rule, dict)
    }
    for expected_alert in (
        "AdminSupportRawContentAccessElevatedBurst",
        "AdminSupportRawContentForbiddenAttempt",
        "AdminSupportRbacForbiddenSpike",
        "AdminSupportConsole5xxErrors",
        "AdminSupportRemediationConflictSpike",
        "AdminWrongAnswerReportSurge",
    ):
        assert expected_alert in alert_names, f"Missing alert rule: {expected_alert}"

    dashboard_path = ROOT / "infra" / "monitoring" / "dashboards" / "admin_support_console.json"
    assert dashboard_path.is_file(), f"Missing Grafana dashboard: {dashboard_path}"
    dashboard = _load_json(dashboard_path)
    assert len(dashboard.get("panels", [])) >= 6, "admin_support_console.json must define at least 6 panels"

    compose_text = (ROOT / "docker-compose.yml").read_text(encoding="utf-8")
    for snippet in (
        "VERIFIEDAI_ADMIN_CONSOLE_ENABLED: ${VERIFIEDAI_ADMIN_CONSOLE_ENABLED:-false}",
        "VERIFIEDAI_ADMIN_REMEDIATION_ENABLED: ${VERIFIEDAI_ADMIN_REMEDIATION_ENABLED:-false}",
        "VERIFIEDAI_ADMIN_RAW_CONTENT_ACCESS_ENABLED: ${VERIFIEDAI_ADMIN_RAW_CONTENT_ACCESS_ENABLED:-false}",
    ):
        assert snippet in compose_text, f"Missing fail-closed compose env pass-through: {snippet}"

    smoke_path = ROOT / "scripts" / "quality" / "sprint_10_8_staging_smoke.sh"
    assert smoke_path.is_file(), f"Missing staging smoke script: {smoke_path}"
    assert smoke_path.stat().st_mode & 0o111, f"Staging smoke script must be executable: {smoke_path}"


def validate() -> None:
    validate_schemas_and_fixtures()
    validate_openapi_dual_mirrors()
    validate_migrations_and_pins()
    validate_backend_invariants()
    validate_operational_artifacts()
    print(
        "validate_sprint_10_8_contracts: OK "
        "(10 schemas + negative mutations, 26 OpenAPI paths dual-mirror, V072, pins, alerts/dashboard/smoke)"
    )


if __name__ == "__main__":
    validate()
