#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
verifier_python="${VERIFIER_PYTHON:-$root_dir/services/math-verifier/.venv/bin/python}"

if [[ -d "$root_dir/services/math-verifier/app" ]]; then
  python3 -m compileall -q "$root_dir/services/math-verifier/app" "$root_dir/services/math-verifier/tests"
  if [[ -x "$verifier_python" ]]; then
    (cd "$root_dir/services/math-verifier" && "$verifier_python" -m ruff check app tests)
  fi
fi

if [[ -d "$root_dir/services/api/src/main/java" ]]; then
  if command -v mvn >/dev/null 2>&1; then
    (cd "$root_dir/services/api" && mvn -q -DskipTests compile)
  else
    printf "mvn not found; backend compile lint cannot run\n" >&2
    exit 1
  fi
fi

printf "lint checks completed\n"
