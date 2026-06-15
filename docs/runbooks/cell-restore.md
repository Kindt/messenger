# Cell restore runbook (spec 011 Phase 2 T01119)

**Audience:** Platform / Ops  
**Prerequisite:** Valid backup from `cell_backup` role or manual pg_dump + MinIO snapshot.

---

## 1. Preconditions

- Cell manifest in `deploy/cloud/cells/<cell_id>.yaml` with `status: maintenance`.
- Backup artifact path (S3 prefix or local `/var/backups/korus/<cell_id>/`).
- Ansible inventory `deploy/ansible/inventory/cells/<cell_id>/`.

---

## 2. Restore PostgreSQL (server host)

```bash
# On korus-server guest
BACKUP=/var/backups/korus/<cell_id>/<timestamp>/postgres_hot.dump
PG_CID=$(docker ps -q -f name=postgres | head -1)
docker exec -i "$PG_CID" pg_restore -U korus -d korus --clean --if-exists < "$BACKUP"
```

Verify: `GET /api/v1/health` → 200.

---

## 3. Restore MinIO (optional)

Use MinIO client (`mc`) mirror from backup bucket prefix `{cell_id}/daily/minio/`.

---

## 4. Re-deploy stack

```bash
cd deploy/ansible
ansible-playbook -i inventory/cells/<cell_id>/hosts.yml playbooks/cell-provision.yml -e cell_id=<cell_id>
./scripts/smoke-deploy-acceptance.sh
```

---

## 5. Quarterly drill log

Record in `deploy/cloud/registry.yaml`:

```yaml
restore_drill:
  enabled: true
  last_drill_utc: "2026-09-01T12:00:00Z"
  cell_id: internal-dev
  outcome: success
  operator: ops@example.com
```

---

## Rollback

If restore fails mid-flight: set manifest `status: maintenance`, re-run from last known-good backup, open incident per ops-signoff-log.
