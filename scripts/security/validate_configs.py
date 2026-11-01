#!/usr/bin/env python3
"""Strict Schema-Level Configuration Validator for AI Coding Agents.

Validates that all generated configurations (.codex/config.toml,
.claude/settings.json, codex.requirements.toml, devcontainer.json)
conform to their actual supported schemas without unknown fields.
"""
from __future__ import annotations

import json
from pathlib import Path
import sys
import tomllib

REPO_ROOT = Path(__file__).resolve().parents[2]


def validate_codex_config(repo: Path) -> None:
    config_path = repo / ".codex/config.toml"
    with open(config_path, "rb") as f:
        data = tomllib.load(f)

    supported_top_level = {
        "model",
        "approval_policy",
        "sandbox_mode",
        "default_permissions",
        "allow_login_shell",
        "sandbox_workspace_write",
        "permissions",
        "shell_environment_policy",
        "developer_instructions",
    }
    unknown = set(data.keys()) - supported_top_level
    if unknown:
        raise ValueError(f"Unknown fields in {config_path}: {sorted(unknown)}")

    # Validate sandbox_workspace_write
    if "sandbox_workspace_write" in data:
        sww_keys = set(data["sandbox_workspace_write"].keys()) - {
            "network_access",
            "writable_roots",
            "exclude_tmpdir_env_var",
            "exclude_slash_tmp",
        }
        if sww_keys:
            raise ValueError(f"Unknown fields in sandbox_workspace_write: {sorted(sww_keys)}")

    # Validate shell_environment_policy
    if "shell_environment_policy" in data:
        sep_keys = set(data["shell_environment_policy"].keys()) - {
            "inherit",
            "ignore_default_excludes",
            "include_only",
            "exclude",
            "set",
            "filters",
            "experimental_use_profile",
        }
        if sep_keys:
            raise ValueError(f"Unknown fields in shell_environment_policy: {sorted(sep_keys)}")


def validate_codex_requirements(repo: Path) -> None:
    req_path = repo / "infra/security/agent-templates/codex.requirements.toml"
    with open(req_path, "rb") as f:
        data = tomllib.load(f)

    supported_req_keys = {
        "allowed_sandbox_modes",
        "allowed_approval_policies",
        "allow_login_shell",
        "permissions",
        "allowed_permission_profiles",
        "default_permissions",
        "allowed_login_methods",
        "allowed_chatgpt_workspaces",
    }
    unknown = set(data.keys()) - supported_req_keys
    if unknown:
        raise ValueError(f"Unknown fields in {req_path}: {sorted(unknown)}")


def validate_claude_settings(repo: Path) -> None:
    settings_path = repo / ".claude/settings.json"
    with open(settings_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    supported_keys = {"$schema", "sandbox", "permissions", "_comment"}
    unknown = set(data.keys()) - supported_keys
    if unknown:
        raise ValueError(f"Unknown fields in {settings_path}: {sorted(unknown)}")

    if "sandbox" in data:
        sandbox_keys = set(data["sandbox"].keys()) - {
            "enabled",
            "failIfUnavailable",
            "allowUnsandboxedCommands",
            "filesystem",
            "credentials",
        }
        if sandbox_keys:
            raise ValueError(f"Unknown fields in claude sandbox: {sorted(sandbox_keys)}")


def validate_devcontainer(repo: Path) -> None:
    dc_path = repo / ".devcontainer/devcontainer.json"
    with open(dc_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    required_keys = {"name", "build", "workspaceMount", "runArgs"}
    missing = required_keys - set(data.keys())
    if missing:
        raise ValueError(f"Missing required fields in {dc_path}: {sorted(missing)}")


def main() -> int:
    try:
        validate_codex_config(REPO_ROOT)
        validate_codex_requirements(REPO_ROOT)
        validate_claude_settings(REPO_ROOT)
        validate_devcontainer(REPO_ROOT)
        print("Schema validation passed: all configurations conform to supported schemas.")
        return 0
    except Exception as exc:
        print(f"Schema validation failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
