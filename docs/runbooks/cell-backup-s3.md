# Cell backup — S3 and bank preset (spec 011 T01120)

**Audience:** Platform / Ops  
**Related:** [`cell-restore.md`](cell-restore.md), `deploy/ansible/roles/cell_backup/`

---

## 1. Presets

Expand manifest before provisioning inventory:

```bash
python scripts/cell-manifest-expand.py deploy/cloud/cells/acme-prod-example.yaml
```

| Preset | S3 daily | Air-gap weekly |
|--------|----------|----------------|
| `default` | ✓ 90d | — |
| `bank` | ✓ 90d | ✓ filesystem `/mnt/airgap/{cell_id}/` |
| `enterprise` | ✓ + WAL stream | — |

Copy expanded `backup.targets` into cell inventory `group_vars/all.yml` as `cell_backup_targets`.

---

## 2. Enable S3 upload (ops)

On **server host** (not Windows dev host):

1. Install AWS CLI v2.
2. Configure credentials via vault / `/etc/korus/cell-backup.env` host env:
   - `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, optional `AWS_DEFAULT_REGION`
   - Or `AWS_PROFILE=korus-cells`
3. Ansible var `cell_backup_s3_enabled: 1` in cell inventory.
4. Optional MinIO-compatible endpoint: `cell_backup_s3_endpoint: https://s3.example.com`

Re-run `cell-provision.yml` or role `cell_backup` only.

---

## 3. Manual test

```bash
set -a && . /etc/korus/cell-backup.env && set +a
/usr/local/bin/korus-cell-backup.sh
tail -20 /var/log/korus-cell-backup.log
aws s3 ls s3://korus-cells/<cell_id>/daily/   # when S3 enabled
```

Without credentials script **exits 0** after local dump — safe on QEMU dev.

---

## 4. Bank air-gap weekly

Target `airgap_weekly` copies local backup dir to `/mnt/airgap/{cell_id}/{timestamp}/`.  
Mount air-gap volume on server host; verify offline copy quarterly (see `deploy/cloud/registry.yaml` `restore_drill`).

---

## 5. Blockers

- Live S3 bucket + IAM — **ops**, not in git.
- First commercial B Cell — **Sep 2026+** (see `AGENTS.md`).
