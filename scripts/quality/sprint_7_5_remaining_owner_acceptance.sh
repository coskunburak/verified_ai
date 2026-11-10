#!/usr/bin/env bash
# Owner execution only. Keep both original evidence and the current working tree intact.
set -Eeuo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"
baseline=e39bafe88ea369f98c96a65497a94462dc79ef33
mkdir -p .generated/sprint-7-4-baselines .generated/sprint-7-4-owner
baseline_parent="$(mktemp -d "$repo_root/.generated/sprint-7-4-baselines/owner-XXXXXX")"
baseline_tree="$baseline_parent/source"
git worktree add --detach "$baseline_tree" "$baseline"
# Reuse the owner's installed development interpreter; install nothing automatically.
if [[ -x "$repo_root/services/math-verifier/.venv/bin/python" ]]; then
    ln -s "$repo_root/services/math-verifier/.venv" "$baseline_tree/services/math-verifier/.venv"
fi
baseline_code=0
(cd "$baseline_tree" && bash scripts/quality/sprint_7_4_owner_acceptance.sh --full) || baseline_code=$?
# Preserve the historical result in the entry gate's evidence directory, without replacing prior runs.
python3 - "$repo_root" "$baseline_tree" <<'PY'
from pathlib import Path
import shutil
import sys
root, tree = map(Path, sys.argv[1:])
evidence = tree / '.generated/sprint-7-4-owner'
if evidence.is_dir():
    for run in sorted(evidence.iterdir()):
        if run.is_symlink() or not run.is_dir() or not (run / 'summary.json').is_file():
            continue
        destination = root / '.generated/sprint-7-4-owner' / (tree.parent.name + '-' + run.name)
        # Build intermediates are not evidence. Keep reports, result bundles and logs.
        shutil.copytree(run, destination, ignore=shutil.ignore_patterns('DerivedData'), symlinks=True)
print('Baseline worktree retained:', tree)
PY
printf 'Sprint 7.4 owner runner exit: %s\n' "$baseline_code"
current_code=0
bash scripts/quality/sprint_7_5_owner_acceptance.sh --backend-only || current_code=$?
if [[ "$baseline_code" -ne 0 ]]; then exit "$baseline_code"; fi
exit "$current_code"
