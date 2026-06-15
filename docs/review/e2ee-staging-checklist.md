# E2EE / MLS staging checklist (spec 004 US7)

**Purpose:** ops rows 4–6 in [`specs/004-deferred-phase2-closure/ops-signoff-log.md`](../004-deferred-phase2-closure/ops-signoff-log.md) before prod `MLS_STATUS=active`.

Engineering baseline: [`e2ee-security-gate-signoff-2026-06-10.md`](e2ee-security-gate-signoff-2026-06-10.md), ADR [`docs/adr/ADR-e2ee-mls-library.md`](../adr/ADR-e2ee-mls-library.md).

---

## Pre-flight (staging host)

| # | Check | How |
|---|-------|-----|
| P1 | Stack healthy | `GET /api/v1/health` → 200 |
| P2 | TLS if enabled | `smoke-tls-redirect.ps1` with stage URLs |
| P3 | Playwright E2EE on QEMU already green | `e2ee-capabilities.spec.ts` (reference) |

---

## Row 4 — NATS `mls.*` consumer

| Env / config | Expected on staging |
|--------------|---------------------|
| `MLS_WIRE_SUBSCRIBER_ENABLED` | `true` |
| `MLS_STATUS` | `pilot` or `active` (per policy; prod needs sign-off) |
| NATS reachable | core-api logs: MLS wire subscriber started |

**Verify:**

```bash
# on server host / guest
curl -sS http://127.0.0.1:8080/api/v1/health
docker logs docker-core-api-1 2>&1 | tail -100 | grep -i mls || true
```

Sign when subscriber stable 24h without consumer lag alerts.

---

## Row 5 — `POST /admin/e2ee/migrate-batch`

| Step | Command / action |
|------|------------------|
| Admin token | Keycloak admin or service account with admin API |
| Dry batch | `POST /api/v1/admin/e2ee/migrate-batch` with small `limit` |
| Response | 200, counts sane (`migrated`, `skipped`, `errors`) |

Document batch size and duration in ops-signoff notes.

---

## Row 6 — `GET /admin/e2ee/status`

| Field | Sane range |
|-------|------------|
| `mls_status` | matches env |
| `active_chats` / `pending_migration` | no negative; trend stable after batch |

```bash
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://127.0.0.1:8080/api/v1/admin/e2ee/status | jq .
```

---

## Human sign-off (rows 1–3, 8)

| Role | Artifact |
|------|----------|
| Product + Engineering | ADR hybrid accepted |
| Security | `/plaintext-preview` 403; client skips preview when MLS active |
| QA | Formal Playwright sign on **staging** URL |

**Do not** set prod `MLS_STATUS=active` until all 8 rows signed in `ops-signoff-log.md`.
