#!/usr/bin/env bash
set -e
if command -v mvn >/dev/null 2>&1; then
    exec mvn "$@"
elif [[ -x /opt/homebrew/bin/mvn ]]; then
    exec /opt/homebrew/bin/mvn "$@"
else
    echo "Maven (mvn) not found in PATH or /opt/homebrew/bin/mvn" >&2
    exit 1
fi
