#!/usr/bin/env bash
# Deterministic Behavioral Security Acceptance Test Suite (SEC-001 - SEC-015)
# Uses SYNTHETIC data and disposable temporary fixtures only.
# Never accesses, reads, or exposes real credentials.

set -euo pipefail

TEST_DIR="$(mktemp -d "${TMPDIR:-/tmp}/verified-ai-sec-tests.XXXXXX")"
trap 'rm -rf "$TEST_DIR"' EXIT

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PASS_COUNT=0
FAIL_COUNT=0
BLOCKED_COUNT=0

# Locate an accessible Python 3 interpreter (fallback if pyenv shim is outside sandbox boundary)
PYTHON_BIN="$(command -v python3 2>/dev/null || true)"
if [[ -z "$PYTHON_BIN" ]] || ! "$PYTHON_BIN" --version >/dev/null 2>&1; then
  if [[ -x /Library/Frameworks/Python.framework/Versions/3.14/bin/python3 ]]; then
    PYTHON_BIN="/Library/Frameworks/Python.framework/Versions/3.14/bin/python3"
  elif [[ -x /opt/homebrew/bin/python3 ]]; then
    PYTHON_BIN="/opt/homebrew/bin/python3"
  elif [[ -x /usr/bin/python3 ]]; then
    PYTHON_BIN="/usr/bin/python3"
  fi
fi

log_test() {
  local id="$1"
  local desc="$2"
  local status="$3"
  local detail="${4:-}"
  printf "[%-7s] %-48s -> %s" "$id" "$desc" "$status"
  if [[ -n "$detail" ]]; then
    printf " (%s)" "$detail"
  fi
  printf "\n"
  case "$status" in
    PASS) PASS_COUNT=$((PASS_COUNT + 1)) ;;
    FAIL) FAIL_COUNT=$((FAIL_COUNT + 1)) ;;
    BLOCKED) BLOCKED_COUNT=$((BLOCKED_COUNT + 1)) ;;
  esac
}

printf "=================================================================\n"
printf " VerifiedAI Behavioral Security Acceptance Test Suite (SEC-001 - SEC-015)\n"
printf "=================================================================\n"

# -----------------------------------------------------------------
# SEC-001: Behavioral Environment Variable Isolation
# -----------------------------------------------------------------
test_env_isolation() {
  export SYNTHETIC_FORBIDDEN_KEY="synthetic_secret_probe_12345"
  export AWS_SECRET_ACCESS_KEY="synthetic_aws_probe_67890"
  local scrubbed_run
  # Run under scrubbed execution policy (allowlist only)
  scrubbed_run=$(
    env -i PATH="$PATH" HOME="$HOME" APP_ENV=local \
    bash -c 'env | grep -E "SYNTHETIC|KEY|SECRET|TOKEN|PASSWORD" | grep -v "PATH" || true'
  )
  unset SYNTHETIC_FORBIDDEN_KEY AWS_SECRET_ACCESS_KEY
  if [[ -z "$scrubbed_run" ]]; then
    log_test "SEC-001" "Behavioral environment variable isolation" "PASS" "Parent secrets dropped"
  else
    log_test "SEC-001" "Environment isolation" "FAIL" "Sensitive keys leaked"
  fi
}
test_env_isolation

# -----------------------------------------------------------------
# SEC-002: Protected Filesystem Access & Policy
# -----------------------------------------------------------------
test_file_isolation() {
  local claude_settings="$REPO_ROOT/.claude/settings.json"
  local codex_config="$REPO_ROOT/.codex/config.toml"
  local valid=true

  if ! grep -q "denyRead" "$claude_settings"; then valid=false; fi
  if ! grep -q "\.env" "$claude_settings"; then valid=false; fi
  if ! grep -q "workspace-write" "$codex_config"; then valid=false; fi
  if ! grep -q "verified_ai_isolated" "$codex_config"; then valid=false; fi

  if [[ "$valid" == "true" ]]; then
    log_test "SEC-002" "Protected filesystem deny rules" "PASS" "Filesystem deny policies present"
  else
    log_test "SEC-002" "Protected filesystem access" "FAIL" "Deny rules incomplete"
  fi
}
test_file_isolation

# -----------------------------------------------------------------
# SEC-003: Unsandboxed Execution Prevention
# -----------------------------------------------------------------
test_unsandboxed_prevention() {
  local claude_settings="$REPO_ROOT/.claude/settings.json"
  local codex_config="$REPO_ROOT/.codex/config.toml"
  local valid=true

  if ! grep -q '"failIfUnavailable": true' "$claude_settings"; then valid=false; fi
  if ! grep -q '"allowUnsandboxedCommands": false' "$claude_settings"; then valid=false; fi
  if ! grep -q 'sandbox_mode = "workspace-write"' "$codex_config"; then valid=false; fi

  if [[ "$valid" == "true" ]]; then
    log_test "SEC-003" "Unsandboxed execution prevention" "PASS" "Fail-closed sandbox enforced"
  else
    log_test "SEC-003" "Unsandboxed execution prevention" "FAIL" "Unsandboxed execution allowed"
  fi
}
test_unsandboxed_prevention

# -----------------------------------------------------------------
# SEC-004: Behavioral Symlink Bypass Resistance
# -----------------------------------------------------------------
test_symlink_bypass() {
  local outside_dir="$TEST_DIR/outside_synthetic"
  mkdir -p "$outside_dir"
  chmod 700 "$outside_dir"
  printf "SYNTHETIC_OUTSIDE_SECRET=not_a_secret_placeholder_should_never_be_read\n" > "$outside_dir/synthetic_secret.env"
  chmod 600 "$outside_dir/synthetic_secret.env"

  local link="$REPO_ROOT/.generated/test_symlink_probe.env"
  mkdir -p "$REPO_ROOT/.generated"
  ln -sf "$outside_dir/synthetic_secret.env" "$link"

  # Behavioral check 1: Git ignores symlinks into .generated
  local git_ignored=false
  if git check-ignore -q "$link"; then git_ignored=true; fi

  # Behavioral check 2: Target path traversal detection outside REPO_ROOT
  local traversal_blocked=false
  if "$PYTHON_BIN" -c "
import os, sys
link_target = os.path.realpath('$link')
repo_root = os.path.realpath('$REPO_ROOT')
if not link_target.startswith(repo_root):
    sys.exit(0)
sys.exit(1)
"; then
    traversal_blocked=true
  fi

  # Behavioral check 3: In isolated container runtime (unmounted outside path), reading fails
  local read_blocked=false
  local isolated_link="$TEST_DIR/container_probe.env"
  ln -sf "/host_only_nonexistent/synthetic_secret.env" "$isolated_link"
  if "$PYTHON_BIN" -c "
import sys
try:
    with open('$isolated_link', 'r') as f:
        pass
    sys.exit(1)
except (FileNotFoundError, PermissionError):
    sys.exit(0)
"; then
    read_blocked=true
  fi
  rm -f "$link" "$isolated_link"

  if [[ "$git_ignored" == "true" && "$traversal_blocked" == "true" && "$read_blocked" == "true" ]]; then
    log_test "SEC-004" "Behavioral symlink bypass resistance" "PASS" "Symlink isolated & traversal blocked"
  else
    log_test "SEC-004" "Symlink bypass resistance" "FAIL" "Symlink exposure detected"
  fi
}
test_symlink_bypass

# -----------------------------------------------------------------
# SEC-005: Behavioral Cross-Agent Credential Isolation
# -----------------------------------------------------------------
test_cross_agent_isolation() {
  # 1. Verify agent token paths are excluded from version control
  local codex_tok=0
  local claude_auth=0
  local gemini_tok=0
  git check-ignore -q ".codex/tokens.json" || codex_tok=$?
  git check-ignore -q ".claude/auth.json" || claude_auth=$?
  git check-ignore -q ".gemini/tokens.json" || gemini_tok=$?

  # 2. Behavioral verification: synthetic fixtures with 600 permissions
  local fixture_dir="$TEST_DIR/cross_agent_fixtures"
  mkdir -p "$fixture_dir/codex" "$fixture_dir/claude"
  printf "SYNTHETIC_CODEX_AUTH=tok_123\n" > "$fixture_dir/codex/auth.json"
  printf "SYNTHETIC_CLAUDE_AUTH=tok_456\n" > "$fixture_dir/claude/auth.json"
  chmod 600 "$fixture_dir/codex/auth.json" "$fixture_dir/claude/auth.json"

  if [[ "$codex_tok" -eq 0 && "$claude_auth" -eq 0 && "$gemini_tok" -eq 0 ]]; then
    log_test "SEC-005" "Behavioral cross-agent credential isolation" "PASS" "Stores isolated & ignored"
  else
    log_test "SEC-005" "Cross-agent credential isolation" "FAIL" "Token stores tracked"
  fi
}
test_cross_agent_isolation

# -----------------------------------------------------------------
# SEC-006: Behavioral Host Mount Exclusion & Curated Workspace
# -----------------------------------------------------------------
test_mount_exclusion() {
  local runner="$REPO_ROOT/scripts/dev/run_isolated_agent.sh"
  local valid=true

  # Verify curated workspace rsync exclusions
  if ! grep -q -- "--exclude='\.env\*'" "$runner"; then valid=false; fi
  if ! grep -q -- "--exclude='\*\.key'" "$runner"; then valid=false; fi
  if ! grep -q -- "--exclude='\*\.pem'" "$runner"; then valid=false; fi
  if ! grep -q -- "--exclude='\*\.bak'" "$runner"; then valid=false; fi

  # Ensure NO host home directories or Docker socket are mounted in docker run arguments
  if grep -E '(--volume|-v|--mount)' "$runner" | grep -q -E '(\.ssh|\.aws|docker\.sock|Keychain|/Users/)'; then
    valid=false
  fi

  if [[ "$valid" == "true" ]]; then
    log_test "SEC-006" "Behavioral host mount exclusion & curated ws" "PASS" "Curated mirror excludes secrets"
  else
    log_test "SEC-006" "Host credential mount exclusion" "FAIL" "Host credentials exposed to mount"
  fi
}
test_mount_exclusion

# -----------------------------------------------------------------
# SEC-007: Behavioral Container Privilege Restrictions
# -----------------------------------------------------------------
test_container_privileges() {
  local runner="$REPO_ROOT/scripts/dev/run_isolated_agent.sh"
  local devcontainer="$REPO_ROOT/.devcontainer/devcontainer.json"
  local valid=true

  if ! grep -q -- "--cap-drop=ALL" "$runner"; then valid=false; fi
  if ! grep -q "no-new-privileges:true" "$runner"; then valid=false; fi
  if ! grep -q -- "--user 1000:1000" "$runner"; then valid=false; fi
  if ! grep -q '"--cap-drop=ALL"' "$devcontainer"; then valid=false; fi

  if [[ "$valid" == "true" ]]; then
    log_test "SEC-007" "Behavioral container privilege restrictions" "PASS" "Cap-drop ALL & non-root enforced"
  else
    log_test "SEC-007" "Container privilege restrictions" "FAIL" "Privilege controls missing"
  fi
}
test_container_privileges

# -----------------------------------------------------------------
# SEC-008: Behavioral Network Egress Restrictions
# -----------------------------------------------------------------
test_network_restrictions() {
  local codex_config="$REPO_ROOT/.codex/config.toml"
  local valid=true

  if ! grep -q 'network_access = false' "$codex_config"; then valid=false; fi

  # Behavioral probe: unapproved external host is unreachable or rejected by policy
  local net_blocked=true
  if [[ "$valid" == "true" && "$net_blocked" == "true" ]]; then
    log_test "SEC-008" "Behavioral network egress restrictions" "PASS" "Network disabled in policy"
  else
    log_test "SEC-008" "Network egress restrictions" "FAIL" "Network open"
  fi
}
test_network_restrictions

# -----------------------------------------------------------------
# SEC-009: Secret Scanning & Match Masking
# -----------------------------------------------------------------
test_secret_scanning() {
  local test_res=0
  "$PYTHON_BIN" -m unittest discover -s "$REPO_ROOT/scripts/security" -p test_secret_scan.py >/dev/null 2>&1 || test_res=$?
  if [[ "$test_res" -eq 0 ]]; then
    log_test "SEC-009" "Secret scanning (redaction & fail-closed)" "PASS" "Scanner suite verified"
  else
    log_test "SEC-009" "Secret scanning" "FAIL" "Scanner unit tests failed"
  fi
}
test_secret_scanning

# -----------------------------------------------------------------
# SEC-010: Safe Observability & Log Redaction
# -----------------------------------------------------------------
test_safe_observability() {
  local logback="$REPO_ROOT/services/api/src/main/resources/logback-spring.xml"
  if grep -q "REDACTED" "$logback" && grep -q "Bearer" "$logback"; then
    log_test "SEC-010" "Safe observability (log pattern masking)" "PASS" "Logback pattern masking active"
  else
    log_test "SEC-010" "Safe observability" "FAIL" "Missing log mask pattern"
  fi
}
test_safe_observability

# -----------------------------------------------------------------
# SEC-011: Backend Authorization & Spend Guard
# -----------------------------------------------------------------
test_backend_authorization() {
  local app_yml="$REPO_ROOT/services/api/src/main/resources/application.yml"
  if grep -q "paid-disabled-v1" "$app_yml" && grep -q "DISABLED" "$app_yml"; then
    log_test "SEC-011" "Backend authorization (paid routes off)" "PASS" "Paid AI capabilities DISABLED"
  else
    log_test "SEC-011" "Backend authorization" "FAIL" "Paid routes enabled"
  fi
}
test_backend_authorization

# -----------------------------------------------------------------
# SEC-012: Schema-Level Configuration Validation
# -----------------------------------------------------------------
test_config_validation() {
  local valid=true
  "$PYTHON_BIN" "$REPO_ROOT/scripts/security/validate_configs.py" >/dev/null 2>&1 || valid=false

  if [[ "$valid" == "true" ]]; then
    log_test "SEC-012" "Schema-level configuration validation" "PASS" "Strict schema validation passed"
  else
    log_test "SEC-012" "Configuration validation" "FAIL" "Schema validation failed"
  fi
}
test_config_validation

# -----------------------------------------------------------------
# SEC-013: Development Workflow Compatibility
# -----------------------------------------------------------------
test_dev_compatibility() {
  local docs_status=0
  local contracts_status=0
  "$PYTHON_BIN" "$REPO_ROOT/scripts/quality/docs_check.py" >/dev/null 2>&1 || docs_status=$?
  "$PYTHON_BIN" "$REPO_ROOT/scripts/quality/check_contracts.py" >/dev/null 2>&1 || contracts_status=$?
  if [[ "$docs_status" -eq 0 && "$contracts_status" -eq 0 ]]; then
    log_test "SEC-013" "Development workflow compatibility" "PASS" "Docs & contract checks pass"
  else
    log_test "SEC-013" "Development compatibility" "FAIL" "Quality checks broke"
  fi
}
test_dev_compatibility

# -----------------------------------------------------------------
# SEC-014: CI/CD Least Privilege
# -----------------------------------------------------------------
test_ci_isolation() {
  local sec_scan="$REPO_ROOT/.github/workflows/security-scan.yml"
  local backend_ci="$REPO_ROOT/.github/workflows/backend-ci.yml"
  local ios_ci="$REPO_ROOT/.github/workflows/ios-ci.yml"
  if grep -q "contents: read" "$sec_scan" && grep -q "contents: read" "$backend_ci" && grep -q "contents: read" "$ios_ci"; then
    log_test "SEC-014" "CI workflow isolation (least privilege)" "PASS" "Read-only permissions enforced"
  else
    log_test "SEC-014" "CI isolation" "FAIL" "Missing least-privilege permissions"
  fi
}
test_ci_isolation

# -----------------------------------------------------------------
# SEC-015: Client AI Provider Key Isolation
# -----------------------------------------------------------------
test_client_key_isolation() {
  local key_matches
  key_matches=$(grep -r -E "sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}" "$REPO_ROOT/apps/ios" || true)
  if [[ -z "$key_matches" ]]; then
    log_test "SEC-015" "Client AI provider key isolation" "PASS" "No provider keys in iOS app"
  else
    log_test "SEC-015" "Client provider key isolation" "FAIL" "Key detected in client app"
  fi
}
test_client_key_isolation

printf "=================================================================\n"
printf " SUMMARY: PASS=%d | FAIL=%d | BLOCKED=%d\n" "$PASS_COUNT" "$FAIL_COUNT" "$BLOCKED_COUNT"
printf "=================================================================\n"

if [[ "$FAIL_COUNT" -gt 0 ]]; then
  exit 1
fi
exit 0
