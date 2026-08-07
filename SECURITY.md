# Security Policy

## Supported Scope

This repository is pre-production during Phase 2. Security issues in repository configuration, source code, dependency declarations, CI, local infrastructure, and documentation should still be treated seriously.

## Reporting

Report suspected vulnerabilities privately to the repository owner. Do not file public issues containing exploit details, secrets, personal data, or proof-of-concept attacks against production-like systems.

## Secret Handling

Production secrets must never be committed. Use environment variables, local ignored files, or a secret manager depending on environment.

Forbidden in git:

- AI provider keys;
- database, Redis, object-storage, or verifier credentials;
- Apple/private keys;
- refresh/access tokens;
- certificates and provisioning profiles;
- raw student images, typed questions, tutor transcripts, or private learning history.

## Testing Boundaries

Security testing should use local or explicitly authorized environments only. Do not attack third-party providers, App Store services, production infrastructure, or accounts you do not own.

## Required Baselines

Run:

```sh
make secret-scan
make docs-check
```

Backend, verifier, iOS, and infrastructure checks should be run when their prerequisites are available.

