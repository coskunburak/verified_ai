# Coding Agent Security Directives (Codex, Claude Code, Antigravity)

## Universal Security Boundary

1. **Strict Credential Prohibitions**:
   - Do NOT read `.env` files or secret stores.
   - Do NOT dump process environment variables or shell state.
   - Do NOT query the macOS Keychain, SSH keys (`~/.ssh`), or cloud configuration directories (`~/.aws`, `~/.config/gcloud`).
   - Do NOT transmit code, data, or credentials to unauthorized third-party services.

2. **Execution Boundary**:
   - Operate exclusively within the repository workspace.
   - Run commands inside the active tool sandbox (`workspace-write` or standard sandbox mode).
   - Do not bypass sandbox restrictions or invoke `sudo`.

3. **Production vs Development Separation**:
   - Production secrets reside strictly in the owner-managed secret manager or backend trusted runtime.
   - The coding agent operates with sanitized placeholders (`change_me`, `local_dev_`, etc.) and mock adapters.
   - All tests must use synthetic fixtures only.
