# Research / Decision log: Spec 011

**Date:** 2026-06-15  
**Method:** superpowers-brainstorming (sequential sections)

| # | Topic | Decision |
|---|-------|----------|
| 1 | Compute | VM + Docker; Terraform abstraction; provider **TBD** → `generic` first |
| 2 | Edge | **E1** direct (nginx TLS on web VM) |
| 3 | DNS | **C** — `platform_subdomain` + `customer_cname` via `dns.mode` |
| 4 | Isolation B vs A | B v1 commercial; A later — **hybrid PG** (RLS &lt;10k / schema or Cell &gt;10k) |
| 5 | Backup | **C** hybrid configurable; presets in manifest |
| 6 | Control plane | Git manifests + Ansible; **git-only registry** v1 |
| 7 | Billing | **`billing_model` required** in manifest; no platform default (per deal) |

**Strategic order:** D → C → B → A.

**Source transcript:** design sessions 2026-06-15; full narrative in [`design/cloud-platform-design.md`](design/cloud-platform-design.md).
