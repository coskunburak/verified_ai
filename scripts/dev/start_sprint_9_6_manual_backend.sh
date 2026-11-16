#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MODE="local-mvn"
if [[ "${1:-}" == "--docker" ]]; then
  MODE="docker"
fi

if [[ -z "${JAVA_HOME:-}" && -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker daemon is not running. Start Docker Desktop first, then re-run this script." >&2
  exit 1
fi

# Phase 9 local manual test flags (ADR-011 defaults remain false in application.yml & docker-compose.yml)
export VERIFIEDAI_EXAM_CATALOG_ENABLED="${VERIFIEDAI_EXAM_CATALOG_ENABLED:-true}"
export VERIFIEDAI_EXAM_TARGET_BINDING_ENABLED="${VERIFIEDAI_EXAM_TARGET_BINDING_ENABLED:-true}"
export VERIFIEDAI_EXAM_CANDIDATE_PROFILE_ENABLED="${VERIFIEDAI_EXAM_CANDIDATE_PROFILE_ENABLED:-true}"
export VERIFIEDAI_EXAM_SYLLABUS_INGESTION_ENABLED="${VERIFIEDAI_EXAM_SYLLABUS_INGESTION_ENABLED:-true}"
export VERIFIEDAI_EXAM_QUESTION_BANK_ENABLED="${VERIFIEDAI_EXAM_QUESTION_BANK_ENABLED:-true}"
export VERIFIEDAI_EXAM_BLUEPRINT_MANAGEMENT_ENABLED="${VERIFIEDAI_EXAM_BLUEPRINT_MANAGEMENT_ENABLED:-true}"
export VERIFIEDAI_EXAM_ASSESSMENT_ASSEMBLY_ENABLED="${VERIFIEDAI_EXAM_ASSESSMENT_ASSEMBLY_ENABLED:-true}"
export VERIFIEDAI_EXAM_SESSION_RUNTIME_ENABLED="${VERIFIEDAI_EXAM_SESSION_RUNTIME_ENABLED:-true}"
export VERIFIEDAI_EXAM_SCORING_ENGINE_ENABLED="${VERIFIEDAI_EXAM_SCORING_ENGINE_ENABLED:-true}"
export VERIFIEDAI_EXAM_READINESS_ENABLED="${VERIFIEDAI_EXAM_READINESS_ENABLED:-true}"

# Detect host-port conflicts (e.g. native Homebrew postgres on 5432 or redis on 6379)
if [[ -z "${POSTGRES_PORT:-}" ]]; then
  if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:5432 -sTCP:LISTEN 2>/dev/null | grep -qE '^postgres\b'; then
    echo "INFO: Native host 'postgres' detected on port 5432; routing Docker PostgreSQL to host port 5433."
    export POSTGRES_PORT="5433"
  else
    export POSTGRES_PORT="5432"
  fi
fi

if [[ -z "${REDIS_PORT:-}" ]]; then
  if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:6379 -sTCP:LISTEN 2>/dev/null | grep -qE '^redis-ser'; then
    echo "INFO: Native host 'redis-server' detected on port 6379; routing Docker Redis to host port 6380."
    export REDIS_PORT="6380"
  else
    export REDIS_PORT="6379"
  fi
fi

if [[ "$MODE" == "docker" ]]; then
  echo "==> Starting full Docker Compose stack (postgres:${POSTGRES_PORT}, redis:${REDIS_PORT}, minio, math-verifier, api) with Sprint 9.6 enabled..."
  exec docker compose up --build
fi

# Ensure an older compose 'api' container isn't occupying port 8080 when running via Maven on host
docker compose stop api >/dev/null 2>&1 || true

if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "ERROR: Port 8080 is already in use on localhost:" >&2
  lsof -nP -iTCP:8080 -sTCP:LISTEN >&2
  echo "Stop that process first (e.g. kill PID or API_PORT=8081), then retry." >&2
  exit 1
fi

echo "==> Starting backing services (postgres:${POSTGRES_PORT}, redis:${REDIS_PORT}, minio, math-verifier)..."
docker compose up -d --build postgres redis minio math-verifier

echo "==> Waiting for PostgreSQL health check..."
pg_ready="false"
for _ in $(seq 1 30); do
  if docker compose exec -T postgres pg_isready -U verified_ai_local -d verified_ai >/dev/null 2>&1; then
    pg_ready="true"
    break
  fi
  sleep 1
done

if [[ "$pg_ready" != "true" ]]; then
  echo "ERROR: Docker Postgres container did not become ready. Check 'docker compose logs postgres'." >&2
  exit 1
fi

export APP_ENV="local"
export SPRING_PROFILES_ACTIVE="local"
export DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:${POSTGRES_PORT}/verified_ai}"
export POSTGRES_USER="${POSTGRES_USER:-verified_ai_local}"
export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-local_dev_password_change_me}"
export REDIS_HOST="${REDIS_HOST:-localhost}"
export PROBLEM_ASSETS_S3_ENDPOINT="${PROBLEM_ASSETS_S3_ENDPOINT:-http://localhost:9000}"
export MATH_VERIFIER_BASE_URL="${MATH_VERIFIER_BASE_URL:-http://localhost:8090}"

echo "==> Starting Spring Boot API on http://localhost:8080 (postgres:${POSTGRES_PORT} + redis:${REDIS_PORT} + minio + math-verifier, VERIFIEDAI_EXAM_READINESS_ENABLED=true) ..."
exec mvn -f services/api/pom.xml spring-boot:run
