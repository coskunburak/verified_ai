"""Validate Sprint 10.6 analytics event catalog & product metrics schemas, OpenAPI dual mirror, SQL, config, and iOS contracts."""
import copy
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VENV_SITE = ROOT / "services/math-verifier/.venv/lib/python3.11/site-packages"
if VENV_SITE.exists() and str(VENV_SITE) not in sys.path:
    sys.path.insert(0, str(VENV_SITE))

import yaml
from jsonschema import Draft202012Validator, FormatChecker


def _load_json(rel_path: str) -> dict:
    return json.loads((ROOT / rel_path).read_text(encoding="utf-8"))


def validate() -> None:
    schema_specs = {
        "ingest_request": (
            "packages/schemas/analytics/analytics-event-ingest-request-v1.schema.json",
            "AnalyticsEventIngestRequest",
            "packages/schemas/analytics/fixtures/analytics-event-ingest-request-v1.valid.json",
            False,
        ),
        "batch_request": (
            "packages/schemas/analytics/analytics-event-batch-request-v1.schema.json",
            "AnalyticsEventBatchRequest",
            "packages/schemas/analytics/fixtures/analytics-event-batch-request-v1.valid.json",
            False,
        ),
        "ingest_response": (
            "packages/schemas/analytics/analytics-event-ingest-response-v1.schema.json",
            "AnalyticsEventIngestResponse",
            "packages/schemas/analytics/fixtures/analytics-event-ingest-response-v1.valid.json",
            True,
        ),
        "catalog": (
            "packages/schemas/analytics/analytics-event-catalog-v1.schema.json",
            "AnalyticsEventCatalogResponse",
            "packages/schemas/analytics/fixtures/analytics-event-catalog-v1.valid.json",
            True,
        ),
        "product_metrics": (
            "packages/schemas/analytics/product-metrics-summary-v1.schema.json",
            "ProductMetricsSummaryResponse",
            "packages/schemas/analytics/fixtures/product-metrics-summary-v1.valid.json",
            True,
        ),
    }

    schemas: dict[str, dict] = {}
    validators: dict[str, Draft202012Validator] = {}
    fixtures: dict[str, dict] = {}

    for key, (schema_rel, _, fixture_rel, is_response) in schema_specs.items():
        schema = _load_json(schema_rel)
        assert schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema", schema_rel
        assert schema.get("additionalProperties") is False, schema_rel
        assert schema["properties"]["trainingEligible"]["const"] is False, schema_rel
        if is_response:
            assert schema["properties"]["llmTokensUsed"]["const"] == 0, schema_rel
        for def_name, def_obj in schema.get("$defs", {}).items():
            if def_obj.get("type") == "object":
                assert def_obj.get("additionalProperties") is False, (schema_rel, def_name)
        Draft202012Validator.check_schema(schema)
        val = Draft202012Validator(schema, format_checker=FormatChecker())
        fx = _load_json(fixture_rel)
        val.validate(fx)
        schemas[key] = schema
        validators[key] = val
        fixtures[key] = fx

    assert fixtures["catalog"]["eventCount"] == 56
    assert len(fixtures["catalog"]["events"]) == 56

    # Negative mutations for Sprint 10.6 schemas
    for key in schema_specs:
        bad_te = copy.deepcopy(fixtures[key])
        bad_te["trainingEligible"] = True
        assert not validators[key].is_valid(bad_te), f"trainingEligible=true accepted by {key}"

        bad_extra = copy.deepcopy(fixtures[key])
        bad_extra["unexpectedExtra"] = "x"
        assert not validators[key].is_valid(bad_extra), f"additional property accepted by {key}"

    for key in ("ingest_response", "catalog", "product_metrics"):
        bad_tokens = copy.deepcopy(fixtures[key])
        bad_tokens["llmTokensUsed"] = 1
        assert not validators[key].is_valid(bad_tokens), f"llmTokensUsed=1 accepted by {key}"

    # Generic UI event & server-only event rejected on single and batch request schemas
    for bad_event in ("button_clicked", "purchase_completed", "twin_drill.completed"):
        bad_single = copy.deepcopy(fixtures["ingest_request"])
        bad_single["eventName"] = bad_event
        assert not validators["ingest_request"].is_valid(bad_single), bad_event

        bad_batch = copy.deepcopy(fixtures["batch_request"])
        bad_batch["events"][0]["eventName"] = bad_event
        assert not validators["batch_request"].is_valid(bad_batch), bad_event

    # PII/raw content keys & value patterns rejected
    for bad_key in ("rawProblemText", "studentEmail", "deviceToken"):
        bad_single = copy.deepcopy(fixtures["ingest_request"])
        bad_single["properties"][bad_key] = "redacted"
        assert not validators["ingest_request"].is_valid(bad_single), bad_key

        bad_batch = copy.deepcopy(fixtures["batch_request"])
        bad_batch["events"][0]["properties"][bad_key] = "redacted"
        assert not validators["batch_request"].is_valid(bad_batch), bad_key

    for bad_val in ("student@example.com", "https://s3.example.com/key", "\\frac{1}{2}"):
        bad_single = copy.deepcopy(fixtures["ingest_request"])
        bad_single["properties"]["primarySkillCode"] = bad_val
        assert not validators["ingest_request"].is_valid(bad_single), bad_val

    # Batch size > 50 and empty batch rejected
    bad_batch_51 = copy.deepcopy(fixtures["batch_request"])
    bad_batch_51["events"] = [copy.deepcopy(fixtures["batch_request"]["events"][0]) for _ in range(51)]
    assert not validators["batch_request"].is_valid(bad_batch_51), "batch size 51 accepted"

    bad_batch_0 = copy.deepcopy(fixtures["batch_request"])
    bad_batch_0["events"] = []
    assert not validators["batch_request"].is_valid(bad_batch_0), "empty batch accepted"

    # Invalid emittedBy & missing required fields rejected
    bad_emitted = copy.deepcopy(fixtures["ingest_request"])
    bad_emitted["emittedBy"] = "SERVER"
    assert not validators["ingest_request"].is_valid(bad_emitted), "emittedBy=SERVER accepted"

    for key, req_field in (
        ("ingest_request", "clientEventId"),
        ("batch_request", "events"),
        ("ingest_response", "results"),
        ("catalog", "events"),
        ("product_metrics", "fromDate"),
    ):
        missing = copy.deepcopy(fixtures[key])
        del missing[req_field]
        assert not validators[key].is_valid(missing), (key, req_field)

    # Dual OpenAPI byte-for-byte mirror & path/schema parity
    public_path = ROOT / "packages/contracts/openapi/public-api.yaml"
    core_path = ROOT / "packages/openapi/verified-ai-core-api.yaml"
    assert public_path.read_bytes() == core_path.read_bytes(), "OpenAPI dual mirrors must be byte-for-byte identical"

    spec = yaml.safe_load(public_path.read_text(encoding="utf-8"))
    endpoint_checks = [
        ("/analytics/catalog", "get", ("200",), "AnalyticsEventCatalogResponse"),
        ("/analytics/events", "post", ("200", "202"), "AnalyticsEventIngestResponse"),
        ("/analytics/events/batch", "post", ("200",), "AnalyticsEventIngestResponse"),
        ("/admin/analytics/product-metrics", "get", ("200",), "ProductMetricsSummaryResponse"),
    ]
    for prefix in ("/v1", "/api/v1"):
        for suffix, method, ok_codes, resp_schema in endpoint_checks:
            full_path = prefix + suffix
            assert full_path in spec["paths"], full_path
            op = spec["paths"][full_path][method]
            for code in ok_codes:
                assert "Cache-Control" in op["responses"][code]["headers"], (full_path, code)
                assert (
                    op["responses"][code]["content"]["application/json"]["schema"]["$ref"]
                    == f"#/components/schemas/{resp_schema}"
                ), (full_path, code)

    for key, (_, openapi_name, _, _) in schema_specs.items():
        published = {k: v for k, v in schemas[key].items() if k not in ("$schema", "$id")}
        assert spec["components"]["schemas"][openapi_name] == published, openapi_name

    # Migrations V065..V070 + V070 DDL tokens
    mig_dir = ROOT / "services/api/src/main/resources/db/migration/platform"
    for migration in (
        "V065__sprint_10_1_paywall_offer_catalog.sql",
        "V066__sprint_10_2_entitlement_state_and_transition_audit.sql",
        "V067__sprint_10_3_entitlement_quota_ledgers.sql",
        "V068__sprint_10_4_notification_infrastructure_and_preferences.sql",
        "V069__sprint_10_5_lifecycle_messaging_campaigns.sql",
        "V070__sprint_10_6_analytics_event_catalog_and_product_metrics.sql",
    ):
        assert (mig_dir / migration).is_file(), migration

    v070_sql = (mig_dir / "V070__sprint_10_6_analytics_event_catalog_and_product_metrics.sql").read_text(
        encoding="utf-8"
    )
    for token in (
        "analytics_events",
        "analytics_daily_metric_rollups",
        "uq_analytics_events_user_client_id",
        "CHECK (NOT training_eligible)",
        "ON DELETE CASCADE",
        "idx_analytics_events_user_occurred",
        "idx_analytics_events_name_occurred",
        "idx_analytics_events_domain_tier_occurred",
        "idx_analytics_events_latency_samples",
        "idx_analytics_daily_rollups_date_tier",
    ):
        assert token in v070_sql, token

    # Migration pins & zero @EnableScheduling
    mig_103_test = (
        ROOT / "services/api/src/test/java/com/verifiedai/billing/application/Sprint103QuotaMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("67")' in mig_103_test, 'Sprint103QuotaMigrationTest must pin .target("67")'

    mig_104_test = (
        ROOT / "services/api/src/test/java/com/verifiedai/notification/application/Sprint104NotificationMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("68")' in mig_104_test, 'Sprint104NotificationMigrationTest must pin .target("68")'

    mig_105_test = (
        ROOT / "services/api/src/test/java/com/verifiedai/notification/application/Sprint105LifecycleMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("69")' in mig_105_test, 'Sprint105LifecycleMigrationTest must pin .target("69")'

    mig_106_test = (
        ROOT / "services/api/src/test/java/com/verifiedai/analytics/application/Sprint106AnalyticsMigrationTest.java"
    ).read_text(encoding="utf-8")
    assert '.target("70")' in mig_106_test, 'Sprint106AnalyticsMigrationTest must pin .target("70")'

    app_java = (ROOT / "services/api/src/main/java/com/verifiedai/VerifiedAiApplication.java").read_text(
        encoding="utf-8"
    )
    assert "@EnableScheduling" not in app_java, "VerifiedAiApplication must not enable global scheduling"

    # Fail-closed application.yml flags
    app_yml = yaml.safe_load((ROOT / "services/api/src/main/resources/application.yml").read_text(encoding="utf-8"))
    anl_cfg = app_yml["verifiedai"]["analytics"]
    for flag in ("event-catalog-enabled", "product-metrics-enabled"):
        assert str(anl_cfg[flag]).endswith(":false}"), (flag, anl_cfg[flag])

    # project.pbxproj registration checks (== 2 each)
    pbxproj = (ROOT / "apps/ios/VerifiedAI.xcodeproj/project.pbxproj").read_text(encoding="utf-8")
    for swift_file in (
        "AnalyticsEventCatalog.swift",
        "AnalyticsPrivacySanitizer.swift",
        "AnalyticsServicing.swift",
        "AnalyticsOfflineQueueStore.swift",
        "AnalyticsClient.swift",
        "AnalyticsEventCatalogAndSanitizerTests.swift",
        "AnalyticsClientOfflineQueueTests.swift",
    ):
        count = pbxproj.count(f"/* {swift_file} in Sources */")
        assert count == 2, (swift_file, count)


if __name__ == "__main__":
    validate()
    print("Sprint 10.6 contracts checked")
