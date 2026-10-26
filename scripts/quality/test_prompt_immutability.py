"""Focused tests for the immutable-version gate. Owner/CI executed only."""
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import check_prompt_immutability as gate


class PromptImmutabilityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workspace = tempfile.TemporaryDirectory()
        self.addCleanup(self.workspace.cleanup)
        self.root = Path(self.workspace.name)
        self.prompt = "services/api/src/main/resources/prompts/vision-recognition/v001.md"
        self.schema = "packages/schemas/recognition-evidence.schema.json"
        self.originals = {self.prompt: b"original prompt\n", self.schema: b'{"schema": "v1"}\n'}
        for name, content in self.originals.items():
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
        self.root_patch = patch.object(gate, "ROOT", self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)
        self.git_patch = patch.object(gate, "git", side_effect=self.git)
        self.git_patch.start()
        self.addCleanup(self.git_patch.stop)

    def git(self, *args: str) -> bytes:
        if args[0] == "rev-parse":
            return b"a" * 40 + b"\n"
        if args[0] == "ls-tree":
            return "\n".join(self.originals).encode()
        if args[0] == "show":
            return self.originals[args[1].split(":", 1)[1]]
        raise AssertionError("Unexpected Git operation")

    def test_existing_versions_unchanged_and_new_versions_allowed(self) -> None:
        (self.root / self.prompt).with_name("v002.md").write_text("new candidate")
        self.assertEqual([], gate.changed_artifacts("trusted-base"))

    def test_prompt_and_registry_hash_cannot_be_rewritten_together(self) -> None:
        (self.root / self.prompt).write_text("rewritten instructions")
        (self.root / "registry-hash.txt").write_text("new hash")
        self.assertEqual([self.prompt], gate.changed_artifacts("trusted-base"))

    def test_schema_change_and_prompt_deletion_are_rejected(self) -> None:
        (self.root / self.schema).write_text("changed schema")
        (self.root / self.prompt).unlink()
        self.assertEqual([self.prompt, self.schema], gate.changed_artifacts("trusted-base"))

    def test_identical_bytes_via_symlink_are_rejected(self) -> None:
        replacement = self.root / "replacement.md"
        replacement.write_bytes(self.originals[self.prompt])
        (self.root / self.prompt).unlink()
        (self.root / self.prompt).symlink_to(replacement)
        self.assertEqual([self.prompt], gate.changed_artifacts("trusted-base"))

    def test_unavailable_base_fails_closed(self) -> None:
        with patch.object(gate, "git", side_effect=subprocess.CalledProcessError(128, "git")):
            with self.assertRaises(subprocess.CalledProcessError):
                gate.changed_artifacts("missing-base")


if __name__ == "__main__":
    unittest.main()
