# Antigravity Workspace Security Directives

## Mission: Zero-Secret Development & Credential Isolation

1. **No Real Credentials**: Under no circumstances should any AI agent read, dump, display, or commit live credentials, API keys, private keys, or `.env` file contents.
2. **Sandbox Enforcement**: All operations must execute in sandboxed mode (`BypassSandbox: false`). Unsandboxed execution or privilege escalation (`sudo`) is strictly forbidden.
3. **Cross-Agent Isolation**: Never inspect configuration or credential stores belonging to other AI agents (e.g. `~/.codex`, `~/.claude`, `~/.gemini`).
4. **Owner-Gated Operations**: Any credential migration, cloud deployment, Keychain interaction, or production configuration requires explicit owner execution.
5. **Safe Observability**: All logging, testing, and secret scanning must use sanitized placeholders and never expose credential strings in logs or stdout.
