#!/usr/bin/env bash
# Owner-only reconciliation of existing full-run evidence plus the focused repair retest.
set -Eeuo pipefail
s66_reconcile_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$s66_reconcile_root"
exec python3 scripts/quality/sprint_6_6_reconcile_evidence.py "$@"
