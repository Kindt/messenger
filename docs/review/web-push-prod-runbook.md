# Web Push production runbook (P1-1)

**Scope:** VAPID keys from Ansible vault → push-worker + korus-web `WEB_CLIENT_VAPID_PUBLIC_KEY`.

## 1. Generate keys (once per environment)

```bash
./scripts/generate-vapid.sh
# or .\scripts\generate-vapid.ps1
```

Copy output into vault (do **not** commit plaintext):

```yaml
korus_vapid_public_key: "<base64url public>"
korus_vapid_private_key: "<base64url private>"
korus_vapid_subject: "mailto:notify@your-domain.example"
```

## 2. Ansible mapping

| Vault key | Rendered to |
|-----------|-------------|
| `korus_vapid_public_key` | `korus-web/.env` → `WEB_CLIENT_VAPID_PUBLIC_KEY` |
| `korus_vapid_public_key` | `docker/.env.korus-server` → `PUSH_VAPID_PUBLIC_KEY` |
| `korus_vapid_private_key` | `PUSH_VAPID_PRIVATE_KEY` |
| `korus_vapid_subject` | `PUSH_VAPID_SUBJECT` |

Templates: `deploy/ansible/roles/korus_web/templates/korus-web.env.j2`, `korus_server/templates/korus-server.env.j2`.

## 3. Deploy

```bash
cd deploy/ansible
ansible-playbook -i inventory/stage/hosts.yml playbooks/site.yml --ask-vault-pass
```

Server role sources `docker/.env.korus-server` before `full-stack-up.sh`; compose substitutes `${PUSH_VAPID_*}` on push-worker.

## 4. Smoke

```powershell
.\scripts\smoke-push-worker.ps1 -HealthUrl "http://<server-lan>:9194/health"
# Browser: enable notifications in web UI; verify subscription registered (dev hints in locales).
```

## 5. Sign-off

Record in `docs/review/ops-signoff-log.md` when prod delivery verified.
