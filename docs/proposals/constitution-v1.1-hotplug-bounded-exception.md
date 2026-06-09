# Proposal: Constitution v1.1 Hot-Plug Bounded Exception

**Status:** `accepted` (2026-06-09)
**Date:** 2026-05-23  
**Related ADR:** `docs/adr/ADR-hotplug-deployment-split.md`  
**Current constitution:** `.specify/memory/constitution.md` (v1.0.0)

---

## Why this proposal exists

Feature `001-system-review-refactoring` introduces optional hot-plug deployment for selected
workers. This can be interpreted as tension with Principle V (modular monolith).

To avoid informal exceptions, this note proposes a bounded, explicit interpretation:

- Deployment split is allowed for specific workers.
- Source dependency direction and contract discipline remain mandatory.
- This is not a blanket migration to microservices.

---

## Proposed amendment (MINOR bump)

Target version: `1.1.0` (new bounded exception section, no removal of existing principles).

Suggested addition under Principle V or as a dedicated subsection:

> **Bounded Deployment Split Exception**  
> Selected workers may run as separate deployable processes when all conditions are met:
> (1) compile-time dependency direction remains unchanged;  
> (2) all integration stays contract-first via documented NATS subjects/payloads;  
> (3) core-api supports graceful degradation if worker is absent;  
> (4) observability and smoke-test parity are provided;  
> (5) scope is explicitly approved via ADR and is feature-bounded.

---

## Non-goals

- No redefinition of modular-monolith baseline.
- No full microservices migration policy.
- No relaxation of backward-compatibility requirements.

---

## Acceptance criteria

1. Architecture owner confirms wording does not dilute Principle V.
2. Product owner confirms operational value and bounded scope.
3. Ops/SRE confirms monitoring/runbook sufficiency.
4. ADR approval log is fully signed.

See **`docs/review/hotplug-governance-handoff-2026-05-24.md`** for evidence pack and sign-off template.

---

## Rollback strategy

If approvals are not reached, keep constitution at `1.0.0` and execute Option A
(in-process workers) for current release scope.
