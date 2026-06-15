# Stage TLS smoke runbook (spec 004 US1 row 4)

**Deploy-ready (2026-06-16):** скрипты и Ansible role `tls` в репозитории. Ops row 4 выполняется **после** `site.yml` на реальном FQDN.  
**Master checklist:** [`stage-prod-deploy-runbook.md`](stage-prod-deploy-runbook.md)

**When:** after `ansible-playbook -i inventory/stage/hosts.yml playbooks/site.yml` with `korus_tls_enabled: true`.

**Script:** [`scripts/smoke-tls-redirect.ps1`](../../scripts/smoke-tls-redirect.ps1)

---

## Placeholder → production values

Replace `messenger.stage.example.com` with your `korus_tls_domain` from [`deploy/ansible/inventory/stage/group_vars/all.yml`](../../deploy/ansible/inventory/stage/group_vars/all.yml).

| Variable | Example placeholder | Your value |
|----------|---------------------|------------|
| `HttpUrl` | `http://messenger.stage.example.com` | `http://<korus_tls_domain>` |
| `HttpsUrl` | `https://messenger.stage.example.com` | `https://<korus_tls_domain>` |
| `ExpectedCertSubject` | `messenger.stage.example.com` | CN or SAN substring from cert |

---

## Operator command (PowerShell)

```powershell
cd <repo-root>
.\scripts\smoke-tls-redirect.ps1 `
  -HttpUrl "http://messenger.stage.example.com" `
  -HttpsUrl "https://messenger.stage.example.com" `
  -ExpectedCertSubject "messenger.stage.example.com"
```

**Expected:** exit code `0`; HTTP → HTTPS redirect; `GET https://…/health` returns 2xx; optional cert subject match.

---

## Dev / QEMU (HTTP-only)

```powershell
.\scripts\smoke-tls-redirect.ps1 -SkipTls
```

Records in [`ops-signoff-log.md`](../../docs/review/ops-signoff-log.md) as **US1 ops row 4** — **not** engineering dev path (`-SkipTls`).

---

## Ansible alternative

```bash
cd deploy/ansible
ansible-playbook -i inventory/stage/hosts.yml playbooks/site.yml --tags tls_smoke --ask-vault-pass
```

---

## Failure triage

| Symptom | Check |
|---------|--------|
| HTTP not redirecting | nginx `korus-tls` role on web host; `korus_tls_enabled` |
| HTTPS cert error | Let's Encrypt paths or BYO `korus_tls_cert_path` |
| `/health` 502 | upstream web lb; `korus_tls_proxy_api` if API via same host |
| Subject mismatch | `-ExpectedCertSubject` must match cert CN/SAN |

---

## Sign-off

Record pass in `ops-signoff-log.md` US1 row 4 with date and operator name.
