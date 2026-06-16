# Quickstart: Spec 011 — Korus Cloud (Phase 0–1)

**Prerequisite:** [`deploy/ansible/README.md`](../../deploy/ansible/README.md), [`docs/DEV_STACK_PROFILES.md`](../../docs/DEV_STACK_PROFILES.md).

Windows dev host: runtime smokes **QEMU only** — не host Docker. Stage/prod host **не раньше Sep 2026**.

---

## 1. Validate & expand Cell manifest

```powershell
python scripts/validate-cell-manifest.py deploy/cloud/cells/internal-dev.yaml
python scripts/cell-manifest-expand.py deploy/cloud/cells/internal-dev.yaml
python scripts/test_cell_manifest.py
.\gradlew.bat checkCellManifest
```

Expected: exit 0; expanded YAML shows `backup.targets` for preset `default`.

All manifests in CI via `./gradlew buildIntegrity` → `checkCellManifest`.

Pre-commit (optional):

```powershell
python scripts/precommit-validate-cell-manifests.py
```

---

## 2. Worked example — internal-dev Cell (QEMU parity)

Manifest: [`deploy/cloud/cells/internal-dev.yaml`](../../deploy/cloud/cells/internal-dev.yaml)  
Inventory: [`deploy/ansible/inventory/cells/internal-dev/`](../../deploy/ansible/inventory/cells/internal-dev/)

```powershell
# Windows: QEMU stack (maps to manifest IPs 192.168.76.10 / .20)
.\scripts\qemu-dev-mode.ps1 -Mode warm
# API http://127.0.0.1:18080  UI http://127.0.0.1:19088

# Cell smokes (host → forwarded ports)
.\scripts\smoke-cell-multi-org-qemu.ps1
.\scripts\playwright-dev-loop.ps1 -Tier api
```

Linux (when Cell VMs exist):

```bash
cd deploy/ansible
ansible-playbook -i inventory/cells/internal-dev/hosts.yml playbooks/cell-provision.yml \
  -e cell_id=internal-dev
./scripts/smoke-deploy-acceptance.sh
```

Upgrade / destroy (Phase 2 scaffold):

```bash
ansible-playbook -i inventory/cells/internal-dev/hosts.yml playbooks/cell-upgrade.yml \
  -e cell_id=internal-dev -e images_tag=0.2.0
ansible-playbook -i inventory/cells/internal-dev/hosts.yml playbooks/cell-destroy.yml \
  -e cell_id=internal-dev -e cell_destroy_wipe_volumes=false
```

Restore drill: [`docs/runbooks/cell-restore.md`](../../docs/runbooks/cell-restore.md).  
Bank S3 + air-gap: [`docs/runbooks/cell-backup-s3.md`](../../docs/runbooks/cell-backup-s3.md).

---

## 3. Observability (platform layer)

Prometheus scrape fragment: [`deploy/observability/prometheus/cells/internal-dev.yml`](../../deploy/observability/prometheus/cells/internal-dev.yml)

```bash
cd deploy/ansible
ansible-playbook -i inventory/local/hosts.yml playbooks/observability-only.yml
# Prometheus http://127.0.0.1:9090  Grafana http://127.0.0.1:3001
```

Import cell file_sd into parent `prometheus.yml` (see comment in scrape file).

---

## 4. Example B Cell (customer CNAME)

[`deploy/cloud/cells/acme-prod-example.yaml`](../../deploy/cloud/cells/acme-prod-example.yaml) — `dns.mode: customer_cname`, `backup.preset: bank`. **Do not provision** without contract.

Terraform template: [`deploy/cloud/cells/_template/terraform/`](../../deploy/cloud/cells/_template/terraform/).

---

## 5. Sales / TCO materials

- [`competitor_comparison.html`](../../competitor_comparison.html) — full TCO
- [`competitor_comparison_segment_bank.html`](../../competitor_comparison_segment_bank.html) — hosted bank pitch

See [`spec.md`](spec.md) US3 for B Cell acceptance.
