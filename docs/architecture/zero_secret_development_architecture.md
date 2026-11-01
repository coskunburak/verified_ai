# Zero-Secret Development Architecture & Credential Isolation Guide

## 1. Executive Summary

This architecture establishes strict physical and cryptographic boundaries preventing AI coding agents (OpenAI Codex, Anthropic Claude Code, Google Antigravity) from accessing production credentials, host identity material, or third-party AI provider tokens.

```
+-------------------------------------------------------------------------------+
|                             DEVELOPMENT WORKSTATION                           |
|                                                                               |
|  +---------------------------+          +----------------------------------+  |
|  |     AI Coding Agents      |          |    Host Credentials (PROHIBITED) |  |
|  | (Codex, Claude, Gemini)   |          |  ~/.ssh, ~/.aws, Keychain, .env  |  |
|  +-------------+-------------+          +----------------------------------+  |
|                |                                          X                   |
|                | (Seatbelt / Sandboxed Subprocess)        |                   |
|                v                                          |                   |
|  +---------------------------+                            |                   |
|  |     Isolated Workspace    |                            |                   |
|  |   - Mock/Local Fixtures   |                            |                   |
|  |   - Synthetic Unit Tests  |                            |                   |
|  |   - Scrubbed Environment  |                            |                   |
|  +-------------+-------------+                            |                   |
|                |                                          |                   |
+----------------|------------------------------------------|-------------------+
                 | (Client-Mediated API Calls Only)         |
                 v                                          |
+-------------------------------------------------------+   |
|                 APPLICATION BACKEND                   |   |
|            (verified-ai-api / Spring Boot)            |   |
|                                                       |   |
|  - Request Authentication (JWT / Apple Identity)      |   |
|  - Budget & Spend Guards (AiSpendGuard)               |   |
|  - Route Planners & Capability Registry               |   |
|  - Strictly Scoped Provider Adapters                  |   |
+---------------------------+---------------------------+   |
                            |                               |
                            | (Production Secrets Injected  |
                            |  via Cloud Workload Identity) |
                            v                               v
+-------------------------------------------------------+
|             SECURE CLOUD RUNTIME (AWS / GCP)          |
|                                                       |
|  - AWS Secrets Manager / GCP Secret Manager           |
|  - IAM Workload Identity (No long-lived API keys)     |
|  - External AI Providers (OpenAI, Anthropic, Gemini)  |
+-------------------------------------------------------+
```

---

## 2. The Four Pillars of Credential Isolation

### Pillar 1: No Secrets in the Client or Agent Workspace
- The iOS client (`apps/ios`) NEVER embeds AI provider API keys. All communication routes through `API_BASE_URL`.
- Coding agents operate strictly in sandboxed environments (`workspace-write` or standard sandbox) with scrubbed shell environments.
- Local configuration (`.env`) is excluded via `.gitignore`, denied by Claude and Codex sandbox rules, and isolated from OS seatbelt profiles.

### Pillar 2: Architectural Mediation (No Arbitrary LLM Gateway)
- The backend (`services/api`) never exposes an unauthenticated, unmetered, or arbitrary LLM endpoint.
- All AI operations are encapsulated behind domain-specific capabilities (`VISION_RECOGNITION`, `PROBLEM_PARSER`, `PROBLEM_CLASSIFIER`).
- `AiSpendGuard` enforces per-user, per-tier, and global budget caps.

### Pillar 3: Workload Identity Federation
- In production, services authenticate to cloud providers (e.g., AWS Secrets Manager, Google Secret Manager) via Workload Identity (IAM Roles for Service Accounts / Task Execution Roles).
- No human developer, CI pipeline, or AI coding agent possesses long-lived production secrets.

### Pillar 4: Safe Observability & Centralized Redaction
- Log outputs (`logback-spring.xml`) apply automatic pattern masking to tokens, authorization headers, and keys.
- Secret scanners (`scripts/security/secret_scan.sh`) output file references without disclosing matched secret text.

---

## 3. Trust Boundary Differentiation

| Credential Type | Storage Mechanism | Accessible by Coding Agent? | Accessible by Production API? |
|---|---|---|---|
| **Agent Authentication** (Codex/Claude/Gemini login) | User host home / Keychain (`~/.codex`, `~/.claude`) | Inherent to agent binary; cross-tool access blocked | No |
| **Development Fixtures** (`local_dev_`, mock tokens) | Tracked templates (`.env.example`, `application.yml`) | Yes (Synthetic defaults) | No |
| **Local Service DB / MinIO** | Local Docker Compose | Yes (Local development only) | No |
| **Third-Party AI Keys** (OpenAI, Anthropic, Gemini) | Cloud Secret Manager | **NO** | Yes (Trusted cloud runtime only) |
| **Apple App Store Connect Key** | Cloud Secret Manager / Encrypted CI Secret | **NO** | Yes (Production billing worker) |
| **JWT Signing Private Key** | Cloud Secret Manager / KMS | **NO** | Yes (Authentication service) |

---

## 4. Owner Migration and Secret Deployment Runbook

### Step 1: Provision Cloud Secret Manager
Store production credentials in AWS Secrets Manager or GCP Secret Manager:
```bash
# Example AWS Secrets Manager entry
aws secretsmanager create-secret \
    --name "verified-ai/production/ai-provider-keys" \
    --description "Production external AI model keys" \
    --secret-string '{"OPENAI_API_KEY":"...","ANTHROPIC_API_KEY":"..."}'
```

### Step 2: Grant Least-Privilege IAM Workload Identity
Attach an IAM policy granting `secretsmanager:GetSecretValue` ONLY to the production ECS task role or Kubernetes service account. Never grant this role to developers or agents.

### Step 3: Local Development
Developers and AI coding agents continue using mock adapters (`LOCAL_FIXTURE`) and local test containers (`docker compose up`) without requiring live cloud secrets.
