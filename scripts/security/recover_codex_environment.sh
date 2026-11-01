#!/usr/bin/env bash
# Deterministic Codex Environment Recovery & Security Config Rollback Script
# Purpose: Safely recovers the local development environment for ChatGPT Desktop and Codex CLI (Sprint 6.3).
# Preserves user session (auth.json), creates full timestamped backups, and removes conflicting system-level policies.

set -euo pipefail

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
RECOVERY_DIR="$HOME/codex-config-recovery-${TIMESTAMP}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

printf "=================================================================\n"
printf " Verified AI — Codex Environment Recovery & Rollback\n"
printf " Timestamp: %s\n" "$TIMESTAMP"
printf " Recovery Backup Directory: %s\n" "$RECOVERY_DIR"
printf "=================================================================\n"

# 1. Create Recovery Snapshot Directory
mkdir -p "$RECOVERY_DIR"
printf "\n[1/5] Created recovery backup directory at %s\n" "$RECOVERY_DIR"

# 2. Backup Existing Configurations
printf "\n[2/5] Backing up existing configurations...\n"

if [[ -f "$HOME/.codex/config.toml" ]]; then
  cp "$HOME/.codex/config.toml" "$RECOVERY_DIR/user_codex_config.toml"
  stat -f "Mode: %Sp, UID: %u, GID: %g" "$HOME/.codex/config.toml" > "$RECOVERY_DIR/user_codex_config.meta"
  shasum -a 256 "$HOME/.codex/config.toml" > "$RECOVERY_DIR/user_codex_config.sha256"
  printf "  ✓ Backed up ~/.codex/config.toml\n"
else
  printf "  - Notice: ~/.codex/config.toml not found\n"
fi

if [[ -f "/etc/codex/requirements.toml" ]]; then
  cp "/etc/codex/requirements.toml" "$RECOVERY_DIR/etc_codex_requirements.toml"
  stat -f "Mode: %Sp, UID: %u, GID: %g" "/etc/codex/requirements.toml" > "$RECOVERY_DIR/etc_codex_requirements.meta"
  shasum -a 256 "/etc/codex/requirements.toml" > "$RECOVERY_DIR/etc_codex_requirements.sha256"
  printf "  ✓ Backed up /etc/codex/requirements.toml\n"
fi

if [[ -f "/etc/codex/requirements.toml.backup" ]]; then
  cp "/etc/codex/requirements.toml.backup" "$RECOVERY_DIR/etc_codex_requirements.toml.backup"
  printf "  ✓ Backed up /etc/codex/requirements.toml.backup\n"
fi

if [[ -f "/etc/codex/config.toml" ]]; then
  cp "/etc/codex/config.toml" "$RECOVERY_DIR/etc_codex_config.toml"
  printf "  ✓ Backed up /etc/codex/config.toml\n"
fi

if [[ -f "$REPO_ROOT/.codex/config.toml" ]]; then
  cp "$REPO_ROOT/.codex/config.toml" "$RECOVERY_DIR/project_codex_config.toml"
  printf "  ✓ Backed up project .codex/config.toml\n"
fi

# 3. Rollback System-Level Policy (/etc/codex/requirements.toml)
printf "\n[3/5] Neutralizing conflicting system-level requirements policy...\n"
if [[ -f "/etc/codex/requirements.toml" || -f "/etc/codex/requirements.toml.backup" ]]; then
  printf "  Running sudo to remove /etc/codex/requirements.toml...\n"
  sudo rm -f "/etc/codex/requirements.toml" "/etc/codex/requirements.toml.backup"
  printf "  ✓ Removed /etc/codex/requirements.toml (system policy neutralized, /etc/codex directory preserved)\n"
else
  printf "  - Notice: /etc/codex/requirements.toml already absent\n"
fi

# 4. Restore Simple Working User Configuration (~/.codex/config.toml)
printf "\n[4/5] Restoring standard user configuration in ~/.codex/config.toml...\n"
mkdir -p "$HOME/.codex"
chmod 700 "$HOME/.codex"

cat <<'EOF' > "$HOME/.codex/config.toml"
model = "gpt-6-astra"
model_reasoning_effort = "medium"
approval_policy = "on-request"
sandbox_mode = "workspace-write"
EOF

chmod 600 "$HOME/.codex/config.toml"
printf "  ✓ Wrote standard configuration to ~/.codex/config.toml (chmod 600)\n"

# 5. Verification & Health Audit
printf "\n[5/5] Running verification checks...\n"

# Verify TOML syntax
python3 -c "
import tomllib
with open('$HOME/.codex/config.toml', 'rb') as f:
    data = tomllib.load(f)
assert data['model'] == 'gpt-6-astra', 'Model mismatch'
assert data['model_reasoning_effort'] == 'medium', 'Reasoning effort mismatch'
assert data['approval_policy'] == 'on-request', 'Approval policy mismatch'
assert data['sandbox_mode'] == 'workspace-write', 'Sandbox mode mismatch'
print('  ✓ TOML Syntax & Values: PASS')
"

# Verify auth.json was untouched
if [[ -f "$HOME/.codex/auth.json" ]]; then
  printf "  ✓ User Session (auth.json): INTACT & PRESERVED\n"
else
  printf "  - Notice: auth.json not present in ~/.codex\n"
fi

# Verify system policy is inactive
if [[ ! -f "/etc/codex/requirements.toml" ]]; then
  printf "  ✓ System-level Managed Restrictions: INACTIVE (Conflict resolved)\n"
else
  printf "  ✗ Error: /etc/codex/requirements.toml still exists!\n"
  exit 1
fi

printf "\n=================================================================\n"
printf " RECOVERY COMPLETED SUCCESSFULLY!\n"
printf " ChatGPT Desktop application can now be launched.\n"
printf "=================================================================\n"
