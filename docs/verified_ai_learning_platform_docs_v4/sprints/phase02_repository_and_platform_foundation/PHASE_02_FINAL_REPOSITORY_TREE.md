# Phase 2 Final Repository Tree

Date: 2026-08-07
Phase: Phase 2 - Production Repository and Platform Foundation
Status: Complete

This file records the implemented repository foundation after Phase 2. It intentionally excludes generated build/test artifacts such as `.generated/`, `target/`, `.venv/`, `__pycache__/`, `.pytest_cache/`, `.ruff_cache/`, and Docker runtime state.

## Root

```text
.
|-- .editorconfig
|-- .env.example
|-- .gitattributes
|-- .github/
|-- .gitignore
|-- CONTRIBUTING.md
|-- Makefile
|-- README.md
|-- SECURITY.md
|-- apps/
|-- docker-compose.yml
|-- docs/
|-- evaluations/
|-- infra/
|-- packages/
|-- prompts/
|-- scripts/
|-- services/
`-- tools/
```

## GitHub and CI

```text
.github/
|-- CODEOWNERS
|-- dependabot.yml
|-- ISSUE_TEMPLATE/
|   |-- bug_report.yml
|   `-- security_report.yml
|-- pull_request_template.md
`-- workflows/
    |-- backend-ci.yml
    |-- contract-ci.yml
    |-- docs-link-check.yml
    |-- ios-ci.yml
    |-- math-verifier-ci.yml
    `-- security-scan.yml
```

## iOS App Shell

```text
apps/ios/
|-- Config/
|   |-- Base.xcconfig
|   |-- Debug.xcconfig
|   |-- Release.xcconfig
|   |-- Secrets.xcconfig.example
|   `-- Staging.xcconfig
|-- README.md
|-- VerifiedAI.xcodeproj/
|   `-- project.pbxproj
|-- VerifiedAI/
|   |-- App/
|   |   |-- AppDependencies.swift
|   |   |-- AppEnvironment.swift
|   |   |-- AppLaunchState.swift
|   |   |-- AppLifecycleHandler.swift
|   |   |-- AppRouter.swift
|   |   |-- DeepLinkRouter.swift
|   |   |-- RootView.swift
|   |   `-- VerifiedAIApp.swift
|   |-- Core/
|   |   |-- DesignSystem/Tokens/
|   |   |   |-- ColorTokens.swift
|   |   |   |-- RadiusTokens.swift
|   |   |   |-- SpacingTokens.swift
|   |   |   `-- TypographyTokens.swift
|   |   |-- FeatureFlags/
|   |   |   `-- FeatureFlag.swift
|   |   |-- Networking/
|   |   |   |-- APIClient.swift
|   |   |   |-- AuthTokenProvider.swift
|   |   |   |-- Endpoint.swift
|   |   |   |-- HTTPMethod.swift
|   |   |   |-- HTTPRequest.swift
|   |   |   |-- HTTPResponse.swift
|   |   |   |-- NetworkError.swift
|   |   |   |-- NetworkMonitor.swift
|   |   |   |-- ProblemDetails.swift
|   |   |   `-- RequestInterceptor.swift
|   |   |-- Observability/
|   |   |   |-- AppLogger.swift
|   |   |   `-- CorrelationContext.swift
|   |   `-- Security/
|   |       |-- KeychainStore.swift
|   |       `-- SecureStorage.swift
|   |-- Features/Home/Presentation/
|   |   `-- HomePlaceholderView.swift
|   |-- Resources/
|   |   |-- Assets.xcassets/Contents.json
|   |   |-- Localizable.xcstrings
|   |   `-- PrivacyInfo.xcprivacy
|   `-- SharedDomain/Identifiers/
|       `-- Identifier.swift
|-- VerifiedAITests/
|   |-- APIClientTests.swift
|   |-- AppEnvironmentTests.swift
|   |-- AppRouterTests.swift
|   `-- SecureStorageTests.swift
`-- VerifiedAIUITests/
    `-- VerifiedAIUITests.swift
```

## Spring Boot API

```text
services/api/
|-- Dockerfile
|-- README.md
|-- pom.xml
`-- src/
    |-- main/
    |   |-- java/com/verifiedai/
    |   |   |-- VerifiedAiApplication.java
    |   |   |-- bootstrap/
    |   |   |   |-- api/PlatformHealthController.java
    |   |   |   |-- api/PlatformHealthResponse.java
    |   |   |   `-- package-info.java
    |   |   |-- configuration/
    |   |   |   |-- ObservabilityConfiguration.java
    |   |   |   |-- PlatformConfiguration.java
    |   |   |   |-- SecurityConfiguration.java
    |   |   |   `-- package-info.java
    |   |   |-- sharedkernel/
    |   |   |   |-- error/ApiErrorCode.java
    |   |   |   |-- error/ApiExceptionHandler.java
    |   |   |   |-- error/ProblemDetailsResponse.java
    |   |   |   |-- observability/CorrelationIdFilter.java
    |   |   |   |-- observability/CorrelationIds.java
    |   |   |   |-- observability/package-info.java
    |   |   |   `-- package-info.java
    |   |   |-- admin/package-info.java
    |   |   |-- ai/package-info.java
    |   |   |-- analytics/package-info.java
    |   |   |-- attempt/package-info.java
    |   |   |-- billing/package-info.java
    |   |   |-- curriculum/package-info.java
    |   |   |-- exam/package-info.java
    |   |   |-- identity/package-info.java
    |   |   |-- mastery/package-info.java
    |   |   |-- mistake/package-info.java
    |   |   |-- notification/package-info.java
    |   |   |-- problem/package-info.java
    |   |   |-- profile/package-info.java
    |   |   |-- solving/package-info.java
    |   |   |-- studyplan/package-info.java
    |   |   |-- tutoring/package-info.java
    |   |   `-- verification/package-info.java
    |   `-- resources/
    |       |-- application-local.yml
    |       |-- application-prod.yml
    |       |-- application-staging.yml
    |       |-- application-test.yml
    |       |-- application.yml
    |       |-- db/migration/platform/V001__create_platform_foundation_marker.sql
    |       `-- logback-spring.xml
    `-- test/java/com/verifiedai/
        |-- architecture/ModularityTest.java
        |-- architecture/RepositoryHierarchyTest.java
        |-- integration/PostgresIntegrationTestSupport.java
        |-- platform/ApplicationContextTest.java
        |-- platform/FlywayMigrationTest.java
        `-- platform/PlatformHealthControllerTest.java
```

## Python Math Verifier

```text
services/math-verifier/
|-- Dockerfile
|-- README.md
|-- pyproject.toml
|-- app/
|   |-- __init__.py
|   |-- config.py
|   |-- main.py
|   |-- api/
|   |   |-- dependencies.py
|   |   |-- routes/health.py
|   |   |-- routes/metrics.py
|   |   |-- routes/verify.py
|   |   |-- schemas/expression.py
|   |   |-- schemas/verification_request.py
|   |   `-- schemas/verification_response.py
|   |-- domain/errors.py
|   |-- domain/verification.py
|   |-- observability/logging.py
|   |-- observability/metrics.py
|   |-- observability/tracing.py
|   |-- parsing/parser_limits.py
|   |-- parsing/safe_parser.py
|   |-- parsing/symbol_table.py
|   |-- policies/execution_budget.py
|   |-- security/internal_auth.py
|   |-- security/resource_limits.py
|   `-- verifiers/equivalence.py
`-- tests/
    |-- contract/test_api_contract.py
    |-- security/test_internal_auth.py
    |-- unit/test_equivalence.py
    `-- unit/test_safe_parser.py
```

## Contracts, Infra, Scripts, and Support Packages

```text
packages/
|-- contracts/
|   |-- README.md
|   `-- openapi/
|       |-- internal-math-verifier.yaml
|       `-- public-api.yaml
|-- curriculum/
|   |-- README.md
|   `-- versions/README.md
|-- schemas/
|   |-- README.md
|   `-- domain/problem-details.schema.json
`-- test-fixtures/
    `-- README.md

infra/
|-- README.md
`-- local/
    |-- README.md
    `-- env/.env.example

scripts/
|-- dev/bootstrap.sh
|-- dev/doctor.sh
|-- quality/check_contracts.py
|-- quality/docs_check.py
|-- quality/format.sh
|-- quality/lint.sh
`-- security/secret_scan.sh

evaluations/README.md
prompts/README.md
tools/README.md
```

## Phase 2 Documentation Evidence

```text
docs/verified_ai_learning_platform_docs_v4/sprints/phase02_repository_and_platform_foundation/
|-- PHASE_02_EXECUTION_REPORT.md
|-- PHASE_02_FINAL_REPOSITORY_TREE.md
|-- PHASE_02_IMPLEMENTATION_MAP.md
|-- PHASE_02_PRE_IMPLEMENTATION_BASELINE.md
|-- PHASE_02_REPOSITORY_AND_PLATFORM_FOUNDATION.md
|-- SPRINT_2.1_MONOREPO_BOOTSTRAP_AND_DEVELOPER_EXPERIENCE.md
|-- SPRINT_2.2_IOS_WORKSPACE_APP_SHELL_DEPENDENCY_INJECTION_AND_NAVIGATION_FOUNDATION.md
|-- SPRINT_2.3_SPRING_BOOT_MODULAR_MONOLITH_BOOTSTRAP.md
|-- SPRINT_2.4_POSTGRESQL_FLYWAY_PERSISTENCE_AND_TESTCONTAINERS_FOUNDATION.md
|-- SPRINT_2.5_INTERNAL_PYTHON_MATH_VERIFIER_BOOTSTRAP.md
|-- SPRINT_2.6_LOCAL_INFRASTRUCTURE_WITH_POSTGRESQL_REDIS_AND_OBJECT_STORAGE.md
|-- SPRINT_2.7_CONTINUOUS_INTEGRATION_BASELINE.md
`-- SPRINT_2.8_OBSERVABILITY_STRUCTURED_LOGGING_TRACING_AND_DEVELOPER_DIAGNOSTICS.md
```

## Intentionally Not Implemented In Phase 2

- End-user authentication, Sign in with Apple, sessions, entitlement, StoreKit, billing, and account deletion.
- AI provider gateway, prompt registry, model routing, AI solving, capture/OCR, tutoring, mastery, mistake intelligence, and study planning.
- Production schema beyond the platform foundation Flyway marker.
- Mobile feature screens beyond the app shell and launch placeholder.

Phase 2 created the repository and platform foundation only.
