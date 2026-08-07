#!/usr/bin/env bash
set -euo pipefail

tmp_file="${TMPDIR:-/tmp}/verified-ai-secret-scan.txt"

matches="$(rg -n --hidden \
  --glob '!**/.git/**' \
  --glob '!**/.DS_Store' \
  --glob '!**/target/**' \
  --glob '!**/.venv/**' \
  --glob '!**/__pycache__/**' \
  --glob '!**/DerivedData/**' \
  -e 'AKIA[0-9A-Z]{16}' \
  -e 'AIza[0-9A-Za-z_-]{35}' \
  -e 'sk-[A-Za-z0-9_-]{20,}' \
  -e '-----BEGIN [A-Z ]*PRIVATE KEY-----' \
  -e '(^|[^A-Za-z])(API_KEY|SECRET|PASSWORD|TOKEN)[[:space:]]*=[[:space:]]*[^[:space:]#]+' \
  . || true)"

if [[ -n "$matches" ]]; then
  matches="$(printf "%s\n" "$matches" | rg -v 'change_me|placeholder|example|not_a_secret|local_dev_' || true)"
fi

if [[ -n "$matches" ]]; then
  printf "potential secret matches found:\n" >&2
  printf "%s\n" "$matches" | tee "$tmp_file" >&2
  exit 1
fi

printf "secret scan passed\n"
