#!/usr/bin/env bash
# Rollback script for Zero-Secret Security Hardening.
# Restores backed-up configuration files and removes newly created security artifacts.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

printf "==> Rolling back security hardening changes in %s...\n" "$REPO_ROOT"

restore_file() {
  local target="$1"
  local backup="${target}.bak"
  if [[ -f "$backup" ]]; then
    printf "Restoring %s from %s\n" "$target" "$backup"
    cp "$backup" "$target"
    rm -f "$backup"
  else
    printf "Notice: No backup found for %s, skipping.\n" "$target"
  fi
}

# 1. Restore modified files from backups
restore_file "$REPO_ROOT/scripts/security/secret_scan.sh"
restore_file "$REPO_ROOT/.gitignore"
restore_file "$REPO_ROOT/services/api/src/main/resources/logback-spring.xml"
restore_file "$REPO_ROOT/.github/workflows/security-scan.yml"
restore_file "$REPO_ROOT/.github/workflows/backend-ci.yml"
restore_file "$REPO_ROOT/.github/workflows/ios-ci.yml"

# 2. Remove newly created security artifacts
printf "Removing newly added configuration files...\n"
rm -f "$REPO_ROOT/.codex/config.toml" && rmdir "$REPO_ROOT/.codex" 2>/dev/null || true
rm -f "$REPO_ROOT/.claude/settings.json" "$REPO_ROOT/.claude/permissions.json" "$REPO_ROOT/.claude/config.json" && rmdir "$REPO_ROOT/.claude" 2>/dev/null || true
rm -f "$REPO_ROOT/.agents/rules/credential-isolation.md" && rmdir -p "$REPO_ROOT/.agents/rules" 2>/dev/null || true
rm -f "$REPO_ROOT/GEMINI.md" "$REPO_ROOT/AGENTS.md"
rm -f "$REPO_ROOT/.devcontainer/Dockerfile" "$REPO_ROOT/.devcontainer/devcontainer.json" && rmdir "$REPO_ROOT/.devcontainer" 2>/dev/null || true
rm -f "$REPO_ROOT/scripts/dev/run_isolated_agent.sh"
rm -f "$REPO_ROOT/docs/architecture/zero_secret_development_architecture.md"
rm -f "$REPO_ROOT/scripts/security/install_git_hooks.sh"
rm -f "$REPO_ROOT/scripts/security/test_credential_isolation.sh"
rm -rf "$REPO_ROOT/infra/security/agent-templates"

# 3. Remove pre-commit hook if installed
if [[ -f "$REPO_ROOT/.git/hooks/pre-commit" ]]; then
  printf "Removing .git/hooks/pre-commit...\n"
  rm -f "$REPO_ROOT/.git/hooks/pre-commit"
fi

printf "==> Rollback complete. Repository restored to previous state.\n"
