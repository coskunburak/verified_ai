# Admin, Support, and Internal Tools

## Why admin tooling is production-critical

An AI product cannot be debugged from generic server logs alone. Support must trace a user's reported problem through parse, solve, verification and entitlement state safely.

## Admin capabilities

### User lookup
Search by safe identifiers. Show account/entitlement status and limited profile metadata.

### Problem trace
Display:
- problem session state,
- asset metadata,
- parse revisions,
- solver runs,
- verification run/signals,
- trace ID,
- latency/cost.

Raw content access requires elevated permission and audit.

### Wrong-answer reports
Queue with:
- report reason,
- verification status,
- policy/model versions,
- problem type,
- reviewer resolution.

### Billing support
View authoritative entitlement and external transaction references. Support cannot manually flip DB booleans; corrections go through billing commands.

### AI operations
- provider health,
- route config,
- prompt/model active versions,
- cost dashboards,
- schema failure samples.

### Feature flags
Controlled access to release/kill switches.

## Admin security

- dedicated strong authentication,
- least privilege,
- audited actions,
- session timeout,
- raw student content hidden by default,
- production mutation heavily restricted.

## Support workflow

User supplies support reference/trace ID. Support can find logical flow without needing screenshots of internal systems.

<!-- HYBRID_AI_STRATEGY_V3:START -->
## AI route and economics support tooling

Authorized internal tooling should expose, by trace/problem session:

- route policy version;
- provider/model provenance;
- primary/secondary invocation and escalation reason;
- verification signals/status;
- latency and cost units;
- fallback/retry history;
- prompt/schema versions.

Default support views should avoid raw student content. Content access, when genuinely required for a reported issue, must be separately authorized/audited.

Future ModelOps views may show model-release status, evaluation report and rollback controls but must not grant unrestricted dataset access to general support roles.
<!-- HYBRID_AI_STRATEGY_V3:END -->
