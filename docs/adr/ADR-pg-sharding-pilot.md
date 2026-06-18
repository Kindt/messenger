# ADR: PostgreSQL sharding pilot (org_id routing)

**Status:** `accepted` (2026-06-18)  
**Date:** 2026-06-18  
**Related spec:** [`specs/019-engineering-gap-closure/spec.md`](../../specs/019-engineering-gap-closure/spec.md) (US6)  
**Related design:** [`docs/plans/2026-06-15-infra-optimization-design.md`](../plans/2026-06-15-infra-optimization-design.md) §9

---

## Context

Enterprise tier (500k–1M users) may exceed single PostgreSQL hot-pool throughput. Natural tenant boundary is **`org_id`**. Spec 006 FR-OPT-09 introduced scaffold types (`OrganizationShardRouter`, `OrganizationRoutingDataSource`, `OrgRoutingContext`) but routing was not wired to authenticated API requests.

Pilot goal: enable **optional second JDBC pool** (`DB_SHARD_JDBC_URL`) with **request-scoped org routing** on core-api without Citus or full N-shard topology.

---

## Decision

1. **Shard key:** `users.org_id` (UUID) from authenticated JWT `sub` → `UserRepository`.
2. **Routing rule (pilot):** `hash(org_id) % 2 == 0` → primary pool; `== 1` → shard pool when configured. Unconfigured shard URL → primary only.
3. **Wiring:**
   - `MessengerApplication` wraps primary + shard in `OrganizationRoutingDataSource` when `DatabaseConfig.shardDataSource()` present.
   - `OrgRoutingFilter` (after JWT auth) sets `OrgRoutingContext`; `OrgRoutingClearFilter` clears after response.
4. **Scope:** core-api JDBC only in pilot. Workers retain dedicated pools; cross-shard admin/audit reads stay on primary until federated read path exists.
5. **Not in pilot:** automatic shard rebalancing, Citus, cross-shard transactions, Keycloak DB split.

---

## Prerequisites before production sharding

- Read replica + Redis read cache deployed (FR-OPT-03/05).
- Load test proving single PG bottleneck @ target tier.
- Flyway migrations applied identically on all shard databases.

---

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| **Single PG + replica (status quo)** | Simple | Ceiling @ ~500k+ |
| **App-level 2-pool pilot (chosen)** | Minimal infra; reversible | Manual shard assignment; no auto rebalance |
| **Citus / PG native sharding** | Elastic scale | Heavy ops; overkill for pilot |

---

## Consequences

### Positive

- Engineering can validate org-hash routing on QEMU/stage with `DB_SHARD_JDBC_URL` without schema fork.
- Zero impact when env unset (primary-only).

### Negative / risks

- Org without `org_id` always hits primary — data skew if many users lack org assignment.
- Cross-org admin queries may need explicit primary routing in future filters.

---

## Acceptance

- [x] `OrganizationRoutingDataSource` wired in `MessengerApplication`.
- [x] `OrgRoutingFilter` + context clear registered in `JerseyConfig`.
- [x] Unit tests: `OrganizationRoutingDataSourceTest`, `OrganizationShardRouterTest`.
- [ ] Dual-shard load validation on stage (deferred — spec 015 ops backlog).
