#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  cat <<'EOF'
Usage:
  TARGET_BASE_URL="https://staging-api.example.com" \
  LEARNER_JWT="<learner_access_jwt>" \
  ADMIN_JWT="<role_admin_access_jwt>" \
  [ELEVATED_ADMIN_JWT="<role_admin_plus_raw_read_jwt>"] \
  [USER_LOOKUP_ID="<uuid>"] \
  [PROBLEM_SESSION_ID="<uuid>"] \
  bash scripts/quality/sprint_10_8_staging_smoke.sh

Verifies live Sprint 10.8 staging/local controls without embedding credentials:
  1. Unauthenticated /api/v1/admin/support/audit -> 401
  2. Learner JWT -> 403 application/problem+json code=ADMIN_SUPPORT_FORBIDDEN
  3. Plain ROLE_ADMIN JWT -> 200 OK + Cache-Control: no-store, private + default redaction
  4. Plain ROLE_ADMIN raw-content grant request -> 403 code=ADMIN_RAW_CONTENT_FORBIDDEN
  5. Optional elevated admin + problemSessionId -> grant creation, raw read, and audit trail check
EOF
  exit 0
fi

for cmd in curl python3; do
  command -v "$cmd" >/dev/null || { echo "Missing required command: $cmd" >&2; exit 2; }
done

: "${TARGET_BASE_URL:?Set TARGET_BASE_URL (e.g. http://localhost:8080 or https://staging-api.example.com)}"
: "${LEARNER_JWT:?Set LEARNER_JWT to a valid non-admin active learner Bearer token}"
: "${ADMIN_JWT:?Set ADMIN_JWT to a valid ROLE_ADMIN Bearer token}"

if [[ ! "$TARGET_BASE_URL" =~ ^https?:// ]] || [[ "$TARGET_BASE_URL" == *"<"* || "$TARGET_BASE_URL" == *">"* ]]; then
  echo "ERROR: TARGET_BASE_URL must be a real http(s):// URL, not a placeholder (got: $TARGET_BASE_URL)" >&2
  exit 2
fi
for jwt_name in LEARNER_JWT ADMIN_JWT; do
  jwt_val="${!jwt_name}"
  if [[ "$jwt_val" == *"<"* || "$jwt_val" == *">"* || "$jwt_val" != *.*.* ]]; then
    echo "ERROR: ${jwt_name} must be a real JWT (header.payload.signature), not a placeholder." >&2
    exit 2
  fi
done
if [[ -n "${ELEVATED_ADMIN_JWT:-}" ]] && { [[ "$ELEVATED_ADMIN_JWT" == *"<"* || "$ELEVATED_ADMIN_JWT" == *">"* || "$ELEVATED_ADMIN_JWT" != *.*.* ]]; }; then
  echo "ERROR: ELEVATED_ADMIN_JWT must be a real JWT (header.payload.signature), not a placeholder." >&2
  exit 2
fi

BASE="${TARGET_BASE_URL%/}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

request() {
  local method="$1"
  local path="$2"
  local token="${3:-}"
  local body="${4:-}"
  local idem="${5:-}"
  local hdr_file="$TMP_DIR/headers.txt"
  local out_file="$TMP_DIR/body.txt"
  local -a args=(-sS -D "$hdr_file" -o "$out_file" -w "%{http_code}" -X "$method")
  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  if [[ -n "$idem" ]]; then
    args+=(-H "Idempotency-Key: $idem")
  fi
  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" -d "$body")
  fi
  local code
  code="$(curl "${args[@]}" "${BASE}${path}")"
  echo "$code"
}

assert_no_store_private() {
  local hdr_file="$TMP_DIR/headers.txt"
  if ! grep -iq '^cache-control:.*no-store' "$hdr_file" || ! grep -iq '^cache-control:.*private' "$hdr_file"; then
    echo "FAIL: Missing Cache-Control: no-store, private header" >&2
    cat "$hdr_file" >&2
    exit 1
  fi
}

echo "[1/5] Checking unauthenticated rejection (expect 401)..."
status_unauth="$(request GET "/api/v1/admin/support/audit")"
[[ "$status_unauth" == "401" ]] || { echo "FAIL: Expected 401, got $status_unauth" >&2; exit 1; }

echo "[2/5] Checking learner RBAC denial (expect 403 ADMIN_SUPPORT_FORBIDDEN)..."
status_learner="$(request GET "/api/v1/admin/support/audit" "$LEARNER_JWT")"
[[ "$status_learner" == "403" ]] || { echo "FAIL: Expected 403 for learner, got $status_learner" >&2; exit 1; }
python3 - "$TMP_DIR/body.txt" "ADMIN_SUPPORT_FORBIDDEN" <<'PY'
import json, sys
data = json.loads(open(sys.argv[1], encoding="utf-8").read())
assert data.get("code") == sys.argv[2], f"Expected {sys.argv[2]}, got {data}"
PY

echo "[3/5] Checking ROLE_ADMIN AI ops & audit endpoints (expect 200 + Cache-Control + zero LLM spend)..."
status_aiops="$(request GET "/api/v1/admin/ai-ops/overview" "$ADMIN_JWT")"
[[ "$status_aiops" == "200" ]] || { echo "FAIL: Expected 200 on ai-ops/overview, got $status_aiops" >&2; exit 1; }
assert_no_store_private
python3 - "$TMP_DIR/body.txt" <<'PY'
import json, sys
data = json.loads(open(sys.argv[1], encoding="utf-8").read())
assert data.get("rawContentRedacted") is True, "rawContentRedacted must be true"
assert data.get("llmTokensUsed") == 0, "llmTokensUsed must be 0"
assert data.get("trainingEligible") is False, "trainingEligible must be false"
PY

status_audit="$(request GET "/api/v1/admin/support/audit?limit=10" "$ADMIN_JWT")"
[[ "$status_audit" == "200" ]] || { echo "FAIL: Expected 200 on audit, got $status_audit" >&2; exit 1; }
assert_no_store_private

if [[ -n "${USER_LOOKUP_ID:-}" ]]; then
  echo "  -> Checking redacted user lookup for USER_LOOKUP_ID..."
  status_lookup="$(request GET "/api/v1/admin/support/users/lookup?type=USER_ID&value=${USER_LOOKUP_ID}" "$ADMIN_JWT")"
  [[ "$status_lookup" == "200" ]] || { echo "FAIL: Expected 200 on users/lookup, got $status_lookup" >&2; exit 1; }
  assert_no_store_private
  python3 - "$TMP_DIR/body.txt" <<'PY'
import json, sys
data = json.loads(open(sys.argv[1], encoding="utf-8").read())
assert data.get("rawContentRedacted") is True
assert data.get("trainingEligible") is False
PY
fi

TARGET_SESSION="${PROBLEM_SESSION_ID:-00000000-0000-0000-0000-000000000000}"
if [[ -n "${PROBLEM_SESSION_ID:-}" ]]; then
  echo "  -> Checking redacted problem trace for PROBLEM_SESSION_ID..."
  status_trace="$(request GET "/api/v1/admin/support/problems/trace?problemSessionId=${PROBLEM_SESSION_ID}" "$ADMIN_JWT")"
  [[ "$status_trace" == "200" ]] || { echo "FAIL: Expected 200 on problems/trace, got $status_trace" >&2; exit 1; }
  assert_no_store_private
  python3 - "$TMP_DIR/body.txt" <<'PY'
import json, sys
raw = open(sys.argv[1], encoding="utf-8").read()
data = json.loads(raw)
assert data.get("rawContentRedacted") is True
for forbidden in ("objectKey", "rawOutputJsonb", "normalizedProblemJsonb"):
    assert forbidden not in raw, f"Forbidden raw field leaked: {forbidden}"
PY
fi

echo "[4/5] Checking plain ROLE_ADMIN raw-content denial (expect 403 ADMIN_RAW_CONTENT_FORBIDDEN)..."
grant_body='{"ticketReference":"SMOKE-108","reason":"Staging smoke RBAC verification check","ttlMinutes":15,"trainingEligible":false}'
status_plain_raw="$(request POST "/api/v1/admin/support/problems/${TARGET_SESSION}/raw-content-access" "$ADMIN_JWT" "$grant_body" "smoke-plain-$(date +%s)")"
[[ "$status_plain_raw" == "403" ]] || { echo "FAIL: Expected 403 for plain admin raw access, got $status_plain_raw" >&2; exit 1; }
python3 - "$TMP_DIR/body.txt" "ADMIN_RAW_CONTENT_FORBIDDEN" <<'PY'
import json, sys
data = json.loads(open(sys.argv[1], encoding="utf-8").read())
assert data.get("code") == sys.argv[2], f"Expected {sys.argv[2]}, got {data}"
PY

if [[ -n "${ELEVATED_ADMIN_JWT:-}" && -n "${PROBLEM_SESSION_ID:-}" ]]; then
  echo "[5/5] Checking elevated ROLE_ADMIN raw-content grant + inspection + audit..."
  idem_key="smoke-elev-$(date +%s)"
  status_grant="$(request POST "/api/v1/admin/support/problems/${PROBLEM_SESSION_ID}/raw-content-access" "$ELEVATED_ADMIN_JWT" "$grant_body" "$idem_key")"
  [[ "$status_grant" == "201" || "$status_grant" == "200" ]] || { echo "FAIL: Expected 201/200 on grant, got $status_grant" >&2; exit 1; }
  assert_no_store_private
  grant_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["grantId"])' "$TMP_DIR/body.txt")"

  status_inspect="$(request GET "/api/v1/admin/support/problems/${PROBLEM_SESSION_ID}/raw-content?grantId=${grant_id}" "$ELEVATED_ADMIN_JWT")"
  [[ "$status_inspect" == "200" ]] || { echo "FAIL: Expected 200 on raw-content read, got $status_inspect" >&2; exit 1; }
  assert_no_store_private

  status_audit_post="$(request GET "/api/v1/admin/support/audit?limit=10" "$ADMIN_JWT")"
  [[ "$status_audit_post" == "200" ]] || { echo "FAIL: Expected 200 on post-inspection audit, got $status_audit_post" >&2; exit 1; }
  python3 - "$TMP_DIR/body.txt" <<'PY'
raw = open(__import__("sys").argv[1], encoding="utf-8").read()
assert "RAW_CONTENT_GRANT_CREATED" in raw and "RAW_CONTENT_INSPECTED" in raw, "Missing raw-content audit entries"
PY
else
  echo "[5/5] Skipping elevated raw-content roundtrip (set ELEVATED_ADMIN_JWT and PROBLEM_SESSION_ID to execute)."
fi

echo "SPRINT_10_8_STAGING_SMOKE_PASSED"
