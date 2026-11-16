#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

API_BASE="${API_BASE:-http://localhost:8080}"
TARGET_USER_ID="${1:-}"
GUEST_JWT=""

if ! curl -fsS "${API_BASE}/actuator/health/liveness" >/dev/null 2>&1; then
  echo "ERROR: Local API is not reachable at ${API_BASE}. Run 'bash scripts/dev/start_sprint_9_6_manual_backend.sh' first." >&2
  exit 1
fi

if [[ -z "$TARGET_USER_ID" ]]; then
  echo "==> Creating temporary local guest learner via POST ${API_BASE}/api/v1/auth/guest ..."
  AUTH_JSON="$(curl -fsS -X POST "${API_BASE}/api/v1/auth/guest")"
  read -r TARGET_USER_ID GUEST_JWT <<<"$(python3 -c '
import json, sys
data = json.loads(sys.argv[1])
print(data["userId"], data["accessToken"])
' "$AUTH_JSON")"
  echo "Created guest userId: ${TARGET_USER_ID}"
else
  echo "==> Using supplied userId: ${TARGET_USER_ID}"
fi

echo "==> Seeding active target, BKT-lite mastery signals, and active mistake traps into PostgreSQL ..."
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -v manual_user_id="${TARGET_USER_ID}" -U verified_ai_local -d verified_ai \
  < infra/local/seed/sprint_9_6_manual_readiness_seed.sql

echo "==> PostgreSQL seed completed for userId=${TARGET_USER_ID}."

if [[ -n "$GUEST_JWT" ]]; then
  echo ""
  echo "==> Fetching GET ${API_BASE}/api/v1/exams/readiness?examCode=YKS_TYT ..."
  curl -fsS -H "Authorization: Bearer ${GUEST_JWT}" \
    "${API_BASE}/api/v1/exams/readiness?examCode=YKS_TYT" | python3 -m json.tool

  echo ""
  echo "==> Recomputing AYT Math via POST ${API_BASE}/api/v1/exams/readiness/recompute ..."
  curl -fsS -X POST -H "Authorization: Bearer ${GUEST_JWT}" \
    -H "Content-Type: application/json" \
    -d '{"examCode":"TR_YKS_AYT_MATH","trainingEligible":false}' \
    "${API_BASE}/api/v1/exams/readiness/recompute" | python3 -m json.tool

  echo ""
  echo "Manual curl helper:"
  echo "  USER_ID=${TARGET_USER_ID}"
  echo "  curl -s -H \"Authorization: Bearer ${GUEST_JWT}\" \"${API_BASE}/api/v1/exams/readiness?examCode=TR_YKS_TYT_MATH\" | python3 -m json.tool"
else
  echo ""
  echo "Seed applied for existing Simulator user ${TARGET_USER_ID}."
  echo "Tap 'Recalculate Projection / Tahmini Yeniden Hesapla' inside Screen 9 in the Simulator to see ESTABLISHED data immediately."
fi
