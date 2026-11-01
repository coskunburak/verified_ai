---
name: credential-isolation
description: Behavioral instructions for zero-secret development and credential handling
trigger: always_on
---

# Credential Isolation and Zero-Secret Development Directives

> [!IMPORTANT]
> **Trust Boundary Notice:** This document is an agent instruction set, NOT an immutable operating system security boundary.
> Physical credential isolation is enforced by the Antigravity OS Sandbox (`BypassSandbox: false`), which restricts filesystem access to the authorized workspace and `/tmp`, blocking host directories (`~/.ssh`, `~/.aws`, `~/.codex`, Keychain).

## 1. Zero Credential Access Policy
- Never read, inspect, display, cat, grep, or copy secret-bearing `.env` files or actual API keys.
- Never dump environment variables (`env`, `printenv`, `export`, `os.environ`, `process.env`).
- Never query macOS Keychain (`security find-generic-password`, `security find-internet-password`).
- Never read SSH keys (`~/.ssh`), AWS credentials (`~/.aws`), or cloud tokens.
- Never output real secrets into chat, logs, files, tool outputs, or git commits.

## 2. Sandboxed Execution Only
- Execute all commands in Standard Sandbox Mode (`BypassSandbox: false`).
- Do not request sandbox bypass for commands unless explicitly authorized by the repository owner.
- Never attempt to elevate privileges (`sudo`, `su`) or alter system configuration files outside the project workspace.
- Do not make unapproved outbound network requests.

## 3. Boundary Preservation
- Maintain the isolation boundary: coding agents operate on code and sanitized configuration only.
- For all production secrets, deployment permissions, or host configuration, provide owner-executable instructions.
- If a diagnostic command would disclose secrets, substitute a metadata-only check (e.g., checking file existence or status without reading file contents).
