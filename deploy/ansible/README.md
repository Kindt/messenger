# Ansible deployment for Korus Messenger

Idempotent Linux deployment over existing Docker Compose stacks.

## Layout

- `inventory/local/` — single node (CI, dev)
- `inventory/two-host/` — server + web LAN topology
- `playbooks/ci-local.yml` — full-server on localhost
- `playbooks/site.yml` — two-host site + optional `--tags smoke`

## Quick start

```bash
cd deploy/ansible
ansible-playbook -i inventory/local/hosts.yml playbooks/ci-local.yml
ansible-playbook -i inventory/local/hosts.yml playbooks/ci-local.yml -e run_smoke=true
```

See [specs/003-docker-ansible-autotest/quickstart.md](../../specs/003-docker-ansible-autotest/quickstart.md).

Windows dev: continue using `scripts/full-stack-up.ps1` and `scripts/server-host-up.ps1`.

## Optional playbooks

| Playbook | Purpose |
|----------|---------|
| `observability-only.yml` | Prometheus + Grafana (`deploy/observability/`) |
| `server-only.yml` / `web-only.yml` | Partial deploy |

## Secrets (prod)

Copy `group_vars/vault.example.yml` → encrypt with `ansible-vault encrypt group_vars/vault.yml`.

## CI

GitHub Actions: **Deploy messaging smoke** (Ansible + acceptance) and **Playwright** job in the same workflow.
