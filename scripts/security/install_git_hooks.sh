#!/usr/bin/env bash
# Installs security pre-commit hook to block accidental credential leaks.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HOOK_PATH="$REPO_ROOT/.git/hooks/pre-commit"

printf "==> Installing pre-commit secret scanning hook...\n"

cat <<'EOF' > "$HOOK_PATH"
#!/usr/bin/env bash
set -euo pipefail

# Ensure standard tools (ripgrep) on macOS are discoverable in non-interactive git hook subshell
export PATH="/opt/homebrew/bin:/usr/local/bin:/opt/homebrew/lib/node_modules/@openai/codex/node_modules/@openai/codex-darwin-arm64/vendor/aarch64-apple-darwin/codex-path:$PATH"

# Run secret scan before any commit is finalized
if [[ -x "scripts/security/secret_scan.sh" ]]; then
  printf "==> Running pre-commit secret scan...\n"
  if ! scripts/security/secret_scan.sh; then
    printf "\n[SECURITY ERROR] Secret scanner detected potential credentials in staged files!\n" >&2
    printf "Commit rejected to prevent credential leakage. Check findings above.\n" >&2
    exit 1
  fi
fi
EOF

chmod +x "$HOOK_PATH"
printf "==> Pre-commit hook successfully installed at %s\n" "$HOOK_PATH"
