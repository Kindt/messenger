# Contract: Stage TLS prep (007)

**Applies to:** ops deploy on real stage host (not QEMU)

## Ready-to-run kit (engineering — satisfied)

| # | Artifact | Purpose |
|---|----------|---------|
| 1 | `deploy/ansible/inventory/stage/hosts.yml` | two-host scaffold |
| 2 | `deploy/ansible/inventory/stage/group_vars/all.yml` | TLS vars template |
| 3 | `deploy/ansible/inventory/stage/group_vars/vault.yml.example` | secrets template |
| 4 | `deploy/ansible/inventory/stage/README.md` | deploy + dry-run steps |
| 5 | `docs/review/stage-tls-smoke-runbook.md` | US1 row 4 command |

## Ops acceptance (pending stage host)

| # | Gate | Command / action |
|---|------|------------------|
| 1 | DNS → stage web host | inventory `ansible_host` updated |
| 2 | `ansible-vault encrypt` vault.yml | secrets not in git |
| 3 | `ansible-playbook -i inventory/stage playbooks/site.yml` | exit 0 |
| 4 | TLS smoke | `smoke-tls-redirect.ps1` with real domain → exit 0 |
| 5 | Sign-off | `ops-signoff-log.md` US1 table |

## Blocker documentation

Until stage host is assigned, T601–T607 in `tasks.md` remain open. Engineering prep kit is **complete** per this contract.
