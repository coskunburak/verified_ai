# Sprint 9.8 context ledger

| Source | Scope inspected | Decision |
| --- | --- | --- |
| Revised primary prompt and architecture audit | Requirements and source manifest | Five dependency-ordered packages; owner-only tests |
| Sprint 9.8 canonical specification | Production specification lines 398–460 | H-15, V062, routes, feature flag |
| Sprint 9.7 execution report | Owner acceptance and integration patterns | Preserve accepted predecessor |
| CandidateProfileModels | CandidateProfileRecord targetDate | LocalDate cannot supply an exact start |
| ExamReadinessSignalReader | Entire small port | Actual mastery/trap readers |
| ExamReadinessCalculator | ActiveTrapSignal declaration | posteriorRate and occurrenceCount |
| V061 | Table/index/check declaration | PostgreSQL privacy constraints |
| exam-attribution-report.schema.json | Header/required/property conventions | Strict Draft 2020-12 |
| Both OpenAPI mirrors | Attribution routes, components, security | bearerAuth, dual prefixes, inline schema mirrors |
| validate_sprint_9_7_contracts.py | Validator | Positive/negative fixtures and mutation gates |
| Architecture 90 | CNT-LOS-15 and historical migration row | Explicit contract correction |
| ExamRepository / ExamModels | Definition lookup, ExamDefinition and ExamTopicWeight | Weight by actual skillId/questionCountWeight |
| CandidateProfileRepository | findProfileByUserId | Actual profile read port |
| ExamAttributionController | Auth and ProblemDetail patterns | Existing principal handling and dual prefix |
| ExamProperties | Flag methods | Existing mutable configuration pattern |
| ExamPrivacyLifecycleContributor | Export/delete/schedule signatures | Existing lifecycle integration still required |
| ExamReadinessCalculatorTest | JUnit conventions | Synthetic domain fixtures |

| ExamAttributionRepository | JDBC insert, JSON codec, owner export/delete | Jackson 3 and parameterized SQL |
| ExamPrivacyLifecycleContributor | Optional setter, exportMap, delete order, scheduled expiry | Preserve existing constructors |
| PostgresIntegrationTestSupport | Spring test context and Testcontainers initialization | Owner-only PostgreSQL tests |
| ExamPrivacyLifecycleContributorTest | JUnit/Mockito setup | Synthetic lifecycle verification |

| Current git inventory | Sprint-related paths and diffs | Confirm missing services, catalog, iOS and acceptance entrypoint |
| Owner-generated Surefire privacy report | Suite summary only | Retain existing privacy PASS without rerun |
| ModulithArchitectureTest | Exact class declaration | Outstanding module gate command |
| sprint_9_7_owner_acceptance.sh | VerifyHost and fail-fast convention | Final entrypoint still blocked by missing deliverables |

| ExamReadinessProjectionService | Existing alias map and new accessor | Reuse canonical exam identifiers |
| CandidateProfileModels | activeExamCode and targetDate | Apply date only to the matching exam |
| V036 / V060 | Existing mistake codes and factoring skill | Whitelist two initial catalog cards |
| APIClient / HTTPRequest / HTTPResponse / NetworkError | Current transport interfaces | Confirm remaining conditional-GET client gap; no changes yet |

| Swift networking | HTTPRequest, APIClient, HTTPResponse, ProblemDetails and existing APIClientTests | Opt-in 304/204 and status-preserving failure classification |
| SQLite store | Creation, purge, attribution save/load pattern | Atomic bundle and logout generation guard |
| Readiness/score view models and views | User/API/store DI and navigation entrypoints | Preserve authenticated dependencies |
| Xcode project | Existing H-03 source/test registration anchors | Explicit new file and build-phase registration |
| Final hardening audit | Global contract scripts, ViewModels, SQLite store | Wired Sprint 9.8 into global contract/localization gates; hardened corrupted-cache online load, score examCode alias, 401 XCTest, and static Swift check |
| Localizable.xcstrings | Existing container/sourceLanguage | Add only new tr/en H-15 keys |
