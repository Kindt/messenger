# Terraform modules (spec 011)

| Module | Provider | Phase |
|--------|----------|-------|
| [`cell-vm/generic`](cell-vm/generic/) | Manual IPs → Ansible | 0 |
| `cell-vm/proxmox` | Proxmox API | backlog T01126 |
| `cell-vm/openstack` | OpenStack | backlog T01126 |

Cell manifest `compute.provider` selects submodule in future phases.
