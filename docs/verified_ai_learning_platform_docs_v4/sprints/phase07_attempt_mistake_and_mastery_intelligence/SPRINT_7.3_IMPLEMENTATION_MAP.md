# Sprint 7.3 implementation map

Baseline: main, 5e248fe1c62140809ea974b81eaba0913a6a18eb; clean working tree.
Owner-only tests. Sprint 7.2 repaired-source acceptance stays PENDING_OWNER_TEST.

Owning context: attempt. Reuse immutable structured work, source/account locks,
VersionedJsonContract, bounded HTTP verifier, canonical real-domain v2 and iOS account fences.
Do not publish StudentAttempt analysis or learning/mastery events for structured work.

New slice: independent AttemptEvaluation, explicit POST and historical GET, V035 derived
artifact and durable retry lease, strict request/response schemas, protected iOS retry identity.
Prepare and publish use separate short transactions; no transaction crosses HTTP. Concurrent
same-key requests observe the same operation; expired leases permit explicit retry, with an
execution fence preventing late publication. Source and work revision are rechecked at publish.

Final intent is independently compared with the original, using the same v2 engine. Only
supported solved equation/inequality sets and numeric EVALUATE forms can establish completion.
SIMPLIFY/calculus/unknown assumptions remain inconclusive. Missing/ambiguous work is never
silently removed or renumbered. No new symbolic engine or provider/model route is added.
At most two bounded verifier requests per explicit evaluation; zero paid inference calls.

V035 keeps immutable result evidence separate from mutable recovery state, uses parent-owner
FKs, bounded JSON, unique attempt and owner/problem/key, exact source/work linkage and cascade
privacy deletion. Export includes results. Training eligibility is always false.

UI: saved, in progress/retry, per-step proof/uncertainty, independent final status and historical
badge. Resume reads only; explicit action performs computation. EN/TR and non-color labels.

Rollout: default-off evaluation capability plus existing production authorization; reads and
privacy survive disable. Rollback disables creation, retains migration/readers and artifacts.
No production authorization, hosted CI, device accessibility or ADR-011 release evidence exists.

Evidence: A inventory, B backend/PostgreSQL, C strict contracts, D iOS, E security/privacy,
F documentation, G verifier/goldens/evidence integrity. Existing 7.2 regressions remain required.

Real-domain scope: canonical v1/v3 normalization currently records UNKNOWN variable domains. The optional learner action
is explicit in the UI and mandatory as a boolean in the wire request. realDomainConfirmed=true reuses REVIEWED_REAL_TASK,
never changes original source/structured evidence, and is frozen into the operation and accepted artifact. Different scope
on the same key/work conflicts; a new work revision is needed after an immutable assessment. Integer/assumption restrictions
cannot be overridden by this flag. The UI labels reviewed scope and defaults confirmation to false for each new work.
