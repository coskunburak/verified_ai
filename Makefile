SHELL := /bin/bash

.DEFAULT_GOAL := help

.PHONY: help bootstrap doctor up down test test-minio-image test-api test-ios test-verifier eval-ai eval-solver lint format check docs-check contracts-check secret-scan

JAVA21_HOME ?= /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
JAVA21_ENV := JAVA_HOME="$(JAVA21_HOME)" PATH="$(JAVA21_HOME)/bin:$(PATH)"
VERIFIER_VENV_PYTHON := $(CURDIR)/services/math-verifier/.venv/bin/python
VERIFIER_PYTHON ?= $(if $(wildcard $(VERIFIER_VENV_PYTHON)),$(VERIFIER_VENV_PYTHON),python3)

help:
	@printf "Available targets:\\n"
	@printf "  make bootstrap     Validate local prerequisites for Phase 2 work\\n"
	@printf "  make doctor        Report required toolchain status\\n"
	@printf "  make up            Start local platform with Docker Compose\\n"
	@printf "  make down          Stop local platform\\n"
	@printf "  make test          Run API, verifier, iOS, docs, and secret checks\\n"
	@printf "  make eval-ai       Run deterministic ingestion quality evaluation\\n"
	@printf "  make eval-solver   Run Phase 5 deterministic solver evaluation (not production approval)\\n"
	@printf "  make check         Run doctor, lint, docs, contracts, secrets, and tests\\n"

bootstrap:
	@$(JAVA21_ENV) scripts/dev/bootstrap.sh

doctor:
	@$(JAVA21_ENV) scripts/dev/doctor.sh

up:
	@docker compose up --build

down:
	@docker compose down --remove-orphans

test: test-api test-verifier test-ios docs-check secret-scan

test-minio-image:
	@docker build -t verified-ai/minio-test:2024-07-16-3535197 infra/local/minio-test

test-api: test-minio-image
	@cd services/api && $(JAVA21_ENV) mvn -Dverifiedai.test.minio-image=verified-ai/minio-test:2024-07-16-3535197 test

test-ios:
	@xcodebuild test -project apps/ios/VerifiedAI.xcodeproj -scheme VerifiedAI -destination 'platform=iOS Simulator,name=iPhone 16 Pro' -derivedDataPath .generated/DerivedData

test-verifier:
	@cd services/math-verifier && "$(VERIFIER_PYTHON)" -m pytest

eval-ai:
	@scripts/evaluation/run-golden-suite.sh

eval-solver:
	@"$(VERIFIER_PYTHON)" evaluations/runners/solver_evaluation.py --mode local --output "$(or $(SOLVER_EVAL_OUTPUT),.generated/evaluations/solver-local)"

lint:
	@$(JAVA21_ENV) scripts/quality/lint.sh

format:
	@scripts/quality/format.sh

check: doctor lint docs-check contracts-check secret-scan test

docs-check:
	@python3 scripts/quality/docs_check.py

contracts-check:
	@python3 scripts/quality/check_contracts.py

secret-scan:
	@scripts/security/secret_scan.sh
