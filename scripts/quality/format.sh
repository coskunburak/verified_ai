#!/usr/bin/env bash
set -euo pipefail

ran=0

if command -v swift-format >/dev/null 2>&1 && [[ -d apps/ios/VerifiedAI ]]; then
  swift-format --in-place --recursive apps/ios/VerifiedAI apps/ios/VerifiedAITests apps/ios/VerifiedAIUITests
  ran=1
fi

if command -v ruff >/dev/null 2>&1 && [[ -d services/math-verifier ]]; then
  (cd services/math-verifier && ruff format app tests)
  ran=1
fi

if [[ -d services/api && -f services/api/pom.xml ]]; then
  printf "Java formatting is governed by IDE/import-order policy until a formatter plugin is accepted.\n"
fi

if [[ "$ran" -eq 0 ]]; then
  printf "No supported formatter executable found; install swift-format or ruff for automatic formatting.\n" >&2
  exit 2
fi

