# PostgreSQL read-replica lab (W3-B3.1)

Three tiers for validating `DB_READ_JDBC_URL` routing (spec 006 FR-OPT-05).

## Tier 1 — Routing overlay (default lab)

**File:** [`docker-compose.replica.yml`](docker-compose.replica.yml)

Read pool points at `postgres-hot` (same host). Validates JDBC read pool wiring without streaming replication.

```bash
./scripts/replica-stack-up.sh
./scripts/smoke-read-replica-env.sh   # optional env probe
```

## Tier 2 — Separate read host (compose lab)

**File:** [`docker-compose.replica-lab.yml`](docker-compose.replica-lab.yml)

Adds `postgres-replica-lab` — independent Postgres instance for **read URL isolation** tests. Not streaming; use for connection-pool / failover drills before prod streaming.

```bash
./scripts/replica-lab-up.sh
export DB_READ_JDBC_URL=jdbc:postgresql://postgres-replica-lab:5432/avandocmsg_hot
# core-api picks up via replica-lab overlay
```

**Note:** replica-lab DB starts empty unless seeded; Tier 1 is sufficient for routing code smoke. Tier 2 is for ops rehearsal of distinct read hostname.

## Tier 3 — Production streaming

1. Enable `wal_level=replica`, replication slot on primary `postgres-hot`.
2. Standby: `pg_basebackup` + `primary_conninfo`.
3. Set `DB_READ_JDBC_URL` to standby host in Ansible `korus_server` group_vars.
4. Monitor lag (`pg_stat_replication`) &lt; 500 ms under normal load (design §6).

Document prod hostnames in inventory `group_vars` — not in git secrets.

## Smokes

| Script | Purpose |
|--------|---------|
| `scripts/smoke-read-replica-env.sh` | `DB_READ_JDBC_URL` set and ≠ `DB_JDBC_URL` when overlay active |
| `scripts/replica-stack-up.sh` | Apply Tier 1 overlay |
| `scripts/replica-lab-up.sh` | Apply Tier 2 overlay |

## QEMU

Run Tier 1 smokes on `korus-server` guest after `scale-stack-up` or full-server deploy.
