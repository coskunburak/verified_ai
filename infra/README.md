# Infrastructure

Phase 2 includes local Docker Compose infrastructure only. Staging and production infrastructure are represented by canonical documentation until their implementation phases.

Local services:

- PostgreSQL as the authoritative relational store.
- Redis as disposable cache/coordination.
- MinIO as S3-compatible object storage for local development.
- Spring API.
- Internal math verifier.

Only local loopback ports are exposed by `docker-compose.yml`; this does not imply production public access.

