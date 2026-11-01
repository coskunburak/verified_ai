#!/usr/bin/env bash
set -euo pipefail

if ! command -v rg >/dev/null 2>&1; then
  printf "secret scan failed: ripgrep (rg) is required on PATH\n" >&2
  exit 2
fi

tmp_file="${TMPDIR:-/tmp}/verified-ai-secret-scan.txt"

scan_status=0
matches="$(rg -n --hidden \
  --glob '!**/.git/**' \
  --glob '!**/.DS_Store' \
  --glob '!**/target/**' \
  --glob '!**/.venv/**' \
  --glob '!**/__pycache__/**' \
  --glob '!**/DerivedData/**' \
  --glob '!**/*.bak' \
  -e '(A3T[A-Z0-9]|AKIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASIA)[A-Z0-9]{16}' \
  -e 'AIza[0-9A-Za-z_-]{35}' \
  -e 'sk-[A-Za-z0-9_-]{20,}' \
  -e 'sk-ant-[A-Za-z0-9_-]{20,}' \
  -e '(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{36,}' \
  -e '-----BEGIN [A-Z ]*PRIVATE KEY-----' \
  -e '(^|[^A-Za-z])(API_KEY|SECRET|PASSWORD|TOKEN)[[:space:]]*=[[:space:]]*[^[:space:]#]+' \
  .)" || scan_status=$?

# ripgrep: 0 = matches, 1 = no matches, any other status = scan failure.
if [[ "$scan_status" -ne 0 && "$scan_status" -ne 1 ]]; then
  printf "secret scan failed: ripgrep exited with status %s\n" "$scan_status" >&2
  exit "$scan_status"
fi

if [[ -n "$matches" ]]; then
  filter_status=0
  matches="$(printf "%s\n" "$matches" | rg -v 'change_me|placeholder|example|not_a_secret|local_dev_')" || filter_status=$?
  if [[ "$filter_status" -ne 0 && "$filter_status" -ne 1 ]]; then
    printf "secret scan failed: ripgrep filter exited with status %s\n" "$filter_status" >&2
    exit "$filter_status"
  fi
fi

if [[ -n "$matches" ]]; then
  printf "potential secret matches found:\n" >&2
  # Mask output using pure bash builtins to prevent credential leaking in CI logs or terminal history
  redacted_matches=""
  while IFS= read -r match_line; do
    if [[ -n "$match_line" ]]; then
      prefix="${match_line%%:*}"
      rest="${match_line#*:}"
      line_num="${rest%%:*}"
      if [[ -n "$prefix" && -n "$line_num" && "$prefix" != "$match_line" ]]; then
        redacted_matches+="${prefix}:${line_num}: [POTENTIAL_SECRET_DETECTED_REDACTED]"$'\n'
      else
        redacted_matches+="${match_line}"$'\n'
      fi
    fi
  done <<< "$matches"
  printf "%s" "$redacted_matches" | tee "$tmp_file" >&2
  exit 1
fi

printf "secret scan passed\n"
