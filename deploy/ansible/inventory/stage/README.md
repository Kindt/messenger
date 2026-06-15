# Stage inventory — deploy-ready prep kit

**Status (2026-06-16):** инженерная поставка US1/US7 **готова** — inventory, vault examples, Ansible roles, smokes.  
**При появлении хостов:** только кастомизация ниже + deploy. Полный чеклист: [`docs/review/stage-prod-deploy-runbook.md`](../../../docs/review/stage-prod-deploy-runbook.md).

Prod-like **two-host** topology with TLS on the web host. Placeholders (`example.com`) **намеренно** до первого deploy.

## 0. Windows host preflight (before Ansible on control node)

```powershell
.\scripts\preflight-stage-deploy.ps1 -Inventory stage
.\scripts\stage-ansible-dry-run.ps1 -Inventory stage
```

## 1. Prerequisites

| Item | Action |
|------|--------|
| DNS | `messenger.stage.example.com` → web host public IP (see `group_vars/all.yml` → `korus_tls_domain`) |
| SSH | Ansible user + key on `stage-server.example.com` and `stage-web.example.com` |
| Secrets | `group_vars/vault.yml` from `vault.yml.example` (encrypted) |
| Certs | Let's Encrypt on web host **or** BYO paths in `group_vars/all.yml` |

## 2. Customize hosts

Edit `hosts.yml`:

```yaml
korus-server:
  ansible_host: <stage-server-fqdn-or-ip>
korus-web:
  ansible_host: <stage-web-fqdn-or-ip>
```

Edit `group_vars/all.yml`:

- `korus_server_lan_ip` / `korus_web_lan_ip` — private LAN between hosts
- `korus_tls_domain` — public DNS name
- `korus_cors_allowed_origins` — `https://<domain>`

## 3. Vault (secrets)

```bash
cd deploy/ansible
cp inventory/stage/group_vars/vault.yml.example inventory/stage/group_vars/vault.yml
# edit plaintext secrets, then:
ansible-vault encrypt inventory/stage/group_vars/vault.yml
```

Run playbooks with `--ask-vault-pass` or `--vault-password-file`.

## 4. Dry-run (no changes)

```bash
cd deploy/ansible
ansible-playbook -i inventory/stage/hosts.yml playbooks/site.yml --ask-vault-pass --check --diff
```

Expect: template renders, no task failures. Fix inventory/vars before `--check` removal.

## 5. Deploy

```bash
ansible-playbook -i inventory/stage/hosts.yml playbooks/site.yml --ask-vault-pass
```

Post-deploy TLS smoke (operator workstation):

```powershell
.\scripts\stage-tls-smoke.ps1 -Inventory stage
# or explicit URLs:
.\scripts\smoke-tls-redirect.ps1 `
  -HttpUrl "http://messenger.stage.example.com" `
  -HttpsUrl "https://messenger.stage.example.com" `
  -ExpectedCertSubject "messenger.stage.example.com"
```

Or on control node:

```bash
ansible-playbook -i inventory/stage/hosts.yml playbooks/site.yml --tags tls_smoke --ask-vault-pass
```

## 6. E2EE on staging

See [`docs/review/e2ee-staging-checklist.md`](../../../docs/review/e2ee-staging-checklist.md) (ops-signoff US7 rows 4–6).

## 7. TLS smoke (US1 row 4)

See [`docs/review/stage-tls-smoke-runbook.md`](../../../docs/review/stage-tls-smoke-runbook.md).

## 8. Sign-off

Record gates in [`docs/review/ops-signoff-log.md`](../../../docs/review/ops-signoff-log.md) — разделы **US1 Ops execution** и **Signatures**.

**Deploy-only runbook:** [`docs/review/stage-prod-deploy-runbook.md`](../../../docs/review/stage-prod-deploy-runbook.md).
