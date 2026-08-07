#!/usr/bin/env bash
set -euo pipefail

printf "Running Phase 2 bootstrap preflight...\n"
scripts/dev/doctor.sh
printf "Bootstrap preflight completed. Install project dependencies with the language-specific package managers when their prerequisites are present.\n"

