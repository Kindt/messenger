# Korus Cloud platform (spec 011)

Git-only Cell registry v1: manifests + Ansible/Terraform scaffold.

| Path | Purpose |
|------|---------|
| [`registry.yaml`](registry.yaml) | Cell index (status, fqdn, sku) |
| [`cells/`](cells/) | Per-Cell manifests |
| [`schemas/`](schemas/) | JSON Schema (documentation) |
| [`modules/`](modules/) | Terraform cell-vm skeleton (`generic` provider) |

## Quick commands

```powershell
python scripts/validate-cell-manifest.py deploy/cloud/cells/internal-dev.yaml
python scripts/cell-manifest-expand.py deploy/cloud/cells/_template/cell.yaml.example
```

Windows dev: runtime smokes on **QEMU** only — see [`specs/011-korus-cloud-platform/quickstart.md`](../specs/011-korus-cloud-platform/quickstart.md).

Provision (after inventory generated):

```bash
cd deploy/ansible
ansible-playbook -i inventory/cells/<cell_id>/hosts.yml playbooks/cell-provision.yml
```
