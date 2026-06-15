# Per-Cell Ansible inventory (spec 011)

Each Cell gets `inventory/cells/<cell_id>/`:

```
<cell_id>/
  hosts.yml          # korus_server + korus_web hosts
  group_vars/
    all.yml          # LAN IPs, TLS, manifest-derived vars
```

## Generate from manifest (manual Phase 0)

1. Copy `_template/hosts.yml.example` to `inventory/cells/<cell_id>/hosts.yml`.
2. Set `ansible_host` from manifest `compute.*_ip`.
3. Copy `group_vars/all.yml.example` → `group_vars/all.yml`; set:
   - `korus_server_lan_ip`, `korus_web_lan_ip`
   - `korus_tls_domain` from `dns.fqdn` (if TLS enabled)
   - `korus_browser_ws_host` from public IP or fqdn

## Provision

```bash
cd deploy/ansible
ansible-playbook -i inventory/cells/internal-dev/hosts.yml playbooks/cell-provision.yml \
  -e cell_id=internal-dev \
  -e @../../cloud/cells/internal-dev.yaml
```

**Secrets:** Ansible Vault `inventory/cells/<cell_id>/vault.yml` — never in Cell manifest plaintext.

## QEMU parity (Windows dev)

Use [`inventory/qemu/`](../../inventory/qemu/) for local two-VM stack; map to `internal-dev` manifest IPs when dogfooding.

Future: `scripts/cell-inventory-generate.py` (Phase 1).
