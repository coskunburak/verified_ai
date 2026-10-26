# Sprint 5.3 execution report

**VERIFIED_PASS — OWNER LOCAL ACCEPTANCE A–E**

Production readiness: READY_WITH_DOCUMENTED_LIMITATIONS; external rollout gates remain.

Date: 2026-10-26. Branch: `feature/sprint-5-2-prompt-registry-governance`.
HEAD: `65fcbea601b8587df5093cafeb464decea99267b`. Sprint 5.2 uncommitted work preserved;
no branch switch, commit, push or dependency installation. Prerequisite:
READY_WITH_DOCUMENTED_LIMITATIONS (owner local evidence exists; hosted gates external).

## Completed implementation, not runtime proof

Deterministic eligible-model selection; server entitlement boundary; fresh health port;
zero-default paid policy; PostgreSQL atomic attempt commitments; bounded transient
fallback; cumulative deadline/cost; conservative unknown-cost handling; safe metrics;
account-data anonymization; negative and transactional/concurrency tests. V017 is
additive; existing prompt/schema bytes and future capability disablement preserved.

[Implementation map](SPRINT_5.3_IMPLEMENTATION_MAP.md) records ownership, algorithms,
SQL invariants, retention/rollback, limits and Sprint 5.4 dependencies.
Research: [models](../../ai/72_OPEN_WEIGHT_MATH_MODEL_RESEARCH.md),
[evaluation design](../../ai/73_MATH_MODEL_EVALUATION_PLAN.md),
[TCO](../../ai/74_MODEL_ROUTING_COST_AND_TCO_DECISION.md).

STATIC_REVIEWED means source inspection only. Groups A–E are VERIFIED_PASS from owner
evidence. No Maven, Docker, pytest, xcodebuild, lint, validation or evaluation
command was executed by assistant. No provider call or model download occurred.

## External limitations

- Real provider/model approval, health telemetry, charge/deadline-enforcing adapter,
  business quota values, invoice reconciliation/refunds and retention approval remain
  prerequisites for paid rollout. Defaults intentionally block it.
- A health abstraction exists; no production observation or automatic circuit/probe
  controller is claimed. Adapters must cancel network/inference at transport deadlines.
- Candidate metrics/Turkish accuracy are unknown; corpus is designed, not yet a fully
  constructed/reviewed dataset. Solver benchmark adapter requires 5.4/provider approval.
- Aggregate commitment limits have owner PostgreSQL test evidence; real invoice
  guarantees still depend on adapter/provider contractual bounds.
- Hosted CI and branch protections remain BLOCKED_EXTERNAL. Local Compose image debt
  from Sprint 5.2 is unchanged; test image recovery remains available.

## Owner test protocol

Gruplar A–E kabul edildi. Aşağıdaki komutlar kabul kaydı olarak korunmuştur. Her grupta ilk hatada durup tam özeti ve ilk anlamlı hata/stack
trace'i gönderin; secret değerlerini paylaşmayın. Başarılı sonuç olmadan sonraki gruba
geçmeyin. PASS: BUILD SUCCESS, sıfır failure/error/skipped; eval için açık PASS ve kritik
regression olmaması; iOS için TEST SUCCEEDED. Komutlar assistant tarafından çalıştırılmadı.

### A — Focused routing/gateway/configuration

Java 21 ve Maven gerekir. Docker, credentials, ücretli inference gerekmez. Maven cache
eksikse dependency indirme için ağ gerekebilir. Modulith yeni billing bağımlılığını kontrol eder.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -Dtest=AiRouteSelectorTest,AiRoutingPolicyConfigurationTest,ConfiguredAiRoutePlannerTest,ConfiguredAiModelGatewayTest,ModularityTest test
```

### B — Persistence, migration and entitlement

Yalnız A kabulünden sonra. Java 21/Maven ve çalışan Docker Desktop gerekir;
PostgreSQL Testcontainers image'ı cache'de yoksa ağ gerekir. Test DB geçicidir.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -Dtest=AiSpendGuardIntegrationTest,AiUsageLedgerIntegrationTest,EntitlementApplicationServiceTest test
```

### C — Existing AI and ingestion compatibility

Yalnız B kabulünden sonra. Docker, Java 21, Maven; local eval mevcut Python ortamını
kullanır. Ücretli provider veya harici credentials gerektirmez.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor/services/api
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
mvn -Dtest=AiCapabilityRegistryTest,PromptRegistryValidatorTest,PromptReleasePolicyTest,ClasspathArtifactResolverTest,ArtifactIntegrityTest,AiProviderBoundaryTest,ProblemRecognitionApplicationServiceTest,ProblemParseApplicationServiceTest,ProblemClassificationGoldenEvaluationTest test
cd /Users/burakcoskun/Desktop/ai_verification_tutor
AI_EVAL_MODE=local make eval-ai
```

### D — Documentation and security

Python 3, Git, Bash ve mevcut bundled ripgrep. docs/manifest, contracts, immutability ve
scanner regression kontrol edilir; son belge kanıtı ayrıca kaydedilir.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
python3 scripts/quality/check_prompt_immutability.py --base-ref 65fcbea601b8587df5093cafeb464decea99267b
python3 -m unittest discover -s scripts/security -p test_secret_scan.py
PATH="/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path:$PATH" make docs-check contracts-check secret-scan
```

### E — Full regression (only after A–D accepted)

Java 21/Maven, Docker, mevcut verifier Python dependencies, Xcode/iPhone 16 Pro simulator
ve ripgrep gerekir. MinIO kaynak build'i cache kullanır; eksik dependency için ağ gerekir.

```sh
cd /Users/burakcoskun/Desktop/ai_verification_tutor
PATH="/Applications/ChatGPT.app/Contents/Resources/codex-cli/codex-path:$PATH" make check
```

## Evidence ledger

| Group | Status | Owner evidence |
| --- | --- | --- |
| A | VERIFIED_PASS | Owner log: 2026-10-26T11:27:53+03:00; 42 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; total 6.147 s. ConfiguredAiModelGatewayTest: 21 tests. |
| B | VERIFIED_PASS | Owner log: 2026-10-26T11:32:37+03:00; 16 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; total 13.495 s. EntitlementApplicationServiceTest: 5 tests. |
| C | VERIFIED_PASS | Owner log: 2026-10-26T11:35:31+03:00; 69 Java tests, 0 failures/errors/skips, BUILD SUCCESS, total 14.537 s. Local ingestion evaluation PASS: 18 cases, 13 critical, baseline production-ingestion-v1, exitCode 0, no reasons or regressions. Dataset SHA-256: 769befe32df0f8646aec4c5fa49c777bb4b39b990e92fd70af18811f6270a50f; report SHA-256: 085be07c0b3ff0bbc88374450e4fc734e594f03a18719c131c39ee8f20d1fd66. Near-duplicate normalized fixture text warnings were reported; the gate passed. |
| D | VERIFIED_PASS | Owner log received 2026-10-26: existing prompt/schema versions unchanged against 65fcbea601b8587df5093cafeb464decea99267b; secret-scanner regression 7/7 (5.385 s, OK); documentation check passed for 278 Markdown files; contracts and secret scan passed. Evidence-only documentation updates after this run will be covered by Group E docs checks. |
| E | VERIFIED_PASS | Owner make check log, 2026-10-26: doctor/lint/docs (278 Markdown files)/contracts/secret scan passed; local MinIO test image built; API 310 tests, zero failures/errors/skips, BUILD SUCCESS at 11:39:08+03:00 (30.579 s); verifier 20 passed, 1 warning (0.48 s); iOS TEST SUCCEEDED at 11:40:02+03:00 on iPhone 16 Pro. |

Sprint 5.3 implementation and local acceptance are VERIFIED_PASS across owner Groups A–E.
Production rollout remains subject to the external limitations above. No deployment performed.
This closure changes evidence documentation and manifest entries only; no runtime code changed
after the accepted run. Closure text itself has not been rerun through docs-check.

## Non-blocking observations in the accepted run

Repeated Netty/Lettuce RejectedExecutionException logs report an already-terminated event
executor during Redis handshake listener notification. Surefire still reports zero test
errors/failures; the lifecycle cause is not established and remains a maintenance follow-up.
Verifier emitted a Starlette test-client/httpx deprecation warning. Xcode reported matching
simulator architectures and no AppIntents metadata dependency. These did not fail make check.
Owner log attachment: 0f5f0169-7d04-4795-91c7-7fb54072caea/Yapıştırılan metin.txt.
iOS result: .generated/DerivedData/Logs/Test/Test-VerifiedAI-2026.10.26_11-39-10-+0300.xcresult.
