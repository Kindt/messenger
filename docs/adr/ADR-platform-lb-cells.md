# ADR: Platform load balancer for multi-Cell deployments

**Status:** `accepted` (2026-06-18)  
**Date:** 2026-06-18  
**Related spec:** [`specs/011-korus-cloud-platform/spec.md`](../../specs/011-korus-cloud-platform/spec.md)  
**Related task:** T01127 (Phase 3 — E2 platform LB evaluation)

---

## Context

Korus Cloud Platform deploys isolated **Cells** (server VM + web VM) per tenant or client segment. Phases 0–2 use **E1 — Direct edge**: each Cell exposes a public IP or DNS A-record on its web VM with nginx TLS termination (reuse `deploy/ansible/roles/tls`).

When the platform operates **>15 Cells** or requires central WAF, anycast, or shared rate limits, a **platform-level load balancer (E2)** becomes necessary. Without an explicit decision, teams may prematurely introduce shared LB complexity or, conversely, hit operational limits on per-Cell public IPs.

---

## Decision

1. **Default until ~15 Cells:** remain on **E1 Direct** — no platform LB in the critical path.
2. **Trigger E2 evaluation** when any of:
   - Active Cell count exceeds **15**, or
   - Product requires **single anycast IP** / central WAF for all Cells, or
   - Customer contract mandates **shared edge** (bank WAF integration).
3. **E2 shape (when adopted):**
   - HAProxy or cloud LB (provider-agnostic Terraform module `edge_mode = platform_lb`).
   - Backend pool = web VM private IPs across Cells; **sticky** routing for `/ws` (WebSocket).
   - TLS termination at platform LB **or** passthrough to Cell nginx — per customer DNS mode (`platform_subdomain` vs `customer_cname`).
4. **Blast radius:** Cells remain **network-isolated**; platform LB is forward-only ingress, not cross-Cell DB/messaging peering.
5. **Upgrade path:** existing Cells keep manifest `dns.mode`; only `edge_mode` inventory var changes — no Cell stack rewrite.

---

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| **E1 Direct (default)** | Reuses current Ansible/TLS; minimal ops | Many public IPs; no central WAF |
| **E2 Platform LB (conditional)** | Central edge, WAF, anycast | Ops complexity; stickiness for WS |
| **Per-Cell cloud LB** | Simple per tenant | Cost scales linearly; no shared WAF |

**Chosen:** E1 now; E2 when scale trigger met (this ADR documents E2 criteria and constraints).

---

## Consequences

### Positive

- Clear gate prevents premature platform LB cost/complexity.
- WebSocket stickiness and DNS modes documented before implementation.
- Aligns with spec 011 Phase 3 deliverable T01127.

### Negative / risks

- Migration from E1 → E2 requires DNS cutover planning per Cell.
- Central LB becomes shared failure domain — requires HA pair and health checks on web backends.

---

## Acceptance

- [x] ADR published with E1/E2 criteria and WS stickiness note.
- [ ] Terraform/Ansible `edge_mode=platform_lb` module — when Cell count triggers E2 (ops backlog post Phase 2).
