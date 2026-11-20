# Sprint 10.1 acceptance matrix

Owner local acceptance completed on 2026-11-20. Backend and contract evidence: `.generated/sprint-10-1-owner/20261120T120320Z-34808/`. Simulator evidence: `.generated/sprint-10-1-ios/20261120T120525Z-36077/Subscription.xcresult` and the adjacent `xcode.log`.

| Group | Scope and source count | Owner command or evidence | Expected result | Status |
|---|---|---|---|---|
| A | 5 entitlement service tests, 1 Modulith test, tier/flag/catalog cases shared with B | owner_acceptance, Maven suite logs | Legacy authorization and module dependencies preserved; malformed flags disable offers | PASS (local owner run) |
| B | 8 catalog/domain cases, 1 clean V064 upgrade case, 7 Apple billing cases including retention/isolation and seasonal replay | owner_acceptance, PostgreSQL 16 Testcontainers | V065 additive and repeatable; disabled seeds, subject isolation, stable receipt identity and stored term | PASS (local owner run) |
| C | 9 endpoint cases across legacy, twin routes and MockMvc | owner_acceptance, EntitlementControllerTest | Private reads; anonymous/revoked callers rejected; query parameter cannot select another user; disabled impressions ignored | PASS (local owner run) |
| D | 13 paywall cases and 5 entitlement view model cases | swift_check, Subscription.xcresult | Real simulator product metadata, verified sync, retry/cancel/pending/offline, compact layout, timer suppression, cooldown | PASS (local owner run) |
| E | 2 schemas, 9 positive fixtures, negative mutations, twin parity, EN/TR and target membership | owner_acceptance, schemas.log and openapi.log | Valid fixtures accepted, malformed and privacy-unsafe examples rejected | PASS (local owner run) |
| F | 2 owner scripts, 1 documentation gate | local status logs and docs.log | Preflight and documentation gates completed; manual acceptance stays pending | PASS (local owner run) |

## Manual release evidence

| Gate | Owner evidence required | Status |
|---|---|---|
| Staging and defect ledger | Existing 9.10 warmed P95 and P0/P1 sign-off | PENDING_MANUAL |
| Physical VoiceOver | Real device, both languages, xSmall through AX3, 320pt equivalent, dark/light, Bold Text and Reduce Motion. Read full price and renewal terms; activate Close, Restore, legal links, return to the free result. | PENDING_MANUAL |
| Commercial metadata | Approved SKU, storefront/localizations, period, trial eligibility and verified expiry for each enabled offer; open both owner-published HTTPS legal pages from the signed app. | PENDING_MANUAL |
| Rollout | Confirm legal retention of minimized billing history, legacy restore, DB upgrade/rollback compatibility and explicit owner release signature | PENDING_MANUAL |
