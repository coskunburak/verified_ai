#!/usr/bin/env python3
"""Owner/CI gate: previously committed prompt/schema versions cannot change in place.

Reads the trusted base revision and current worktree. No checkout, mutation, network,
provider calls, or acceptance-evidence generation. New versions remain reviewable.
"""
from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROMPT_VERSION = re.compile(r"services/api/src/main/resources/prompts/[a-z0-9-]+/v[0-9]{3}\.md")
SCHEMAS = {
    "packages/schemas/recognition-evidence.schema.json",
    "packages/schemas/problem-parse.schema.json",
    "packages/schemas/problem-classification.schema.json",
}


def git(*args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=ROOT)


def changed_artifacts(base_ref: str) -> list[str]:
    # Resolve options/revision syntax once, then use the immutable object ID only.
    base = git("rev-parse", "--verify", "--end-of-options", base_ref + "^{commit}").decode().strip()
    paths = git("ls-tree", "-r", "--name-only", base, "--",
                "services/api/src/main/resources/prompts", "packages/schemas").decode().splitlines()
    changed = []
    for relative in paths:
        if not PROMPT_VERSION.fullmatch(relative) and relative not in SCHEMAS:
            continue
        current = ROOT / relative
        if current.is_symlink() or not current.is_file() or current.read_bytes() != git("show", f"{base}:{relative}"):
            changed.append(relative)
    return changed


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-ref", required=True, help="Trusted merge target or last released commit")
    args = parser.parse_args()
    try:
        changed = changed_artifacts(args.base_ref)
    except (OSError, subprocess.CalledProcessError):
        print("Prompt immutability gate could not inspect the trusted base; fail closed.")
        return 2
    if changed:
        print("Immutable artifacts changed; create new versions instead:")
        print("\n".join(changed))
        return 1
    print("Prompt/schema immutability gate: unchanged existing versions.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
