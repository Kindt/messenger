# Prod-контроли FSTEC (stage/prod engineering)

Инженерная подготовка к развёртыванию на **stage/prod** (реальный хост — после Sep 2026+ или по явному заказу; см. [`specs/015-live-server-ops-backlog/`](../specs/015-live-server-ops-backlog/)).

Verify offline/lab: `.\scripts\smoke-fstec-prod-prep.ps1`

## FSTEC-15 TLS и trust store

| Контроль | Где | Verify |
|----------|-----|--------|
| TLS termination (nginx) | `deploy/ansible/inventory/stage|prod/group_vars/all.yml` → `korus_tls_enabled: true` | `.\scripts\smoke-tls-redirect.ps1 -HttpUrl … -HttpsUrl …` |
| Let's Encrypt (stage) | `korus_tls_use_letsencrypt: true` | certbot role в `deploy/ansible/roles/korus_web/` |
| Корп. cert (prod) | `korus_tls_cert_path`, `korus_tls_key_path` | vault + ручная проверка Subject |
| CORS/CSP prod | `korus_cors_allowed_origins`, `korus_csp_policy` | `smoke-security-headers.ps1` на HTTPS FQDN |
| API proxy TLS | `korus_tls_proxy_api: true` | health `https://<domain>/api/v1/health` |

Lab/QEMU: HTTP-only — `smoke-tls-redirect.ps1 -SkipTls` (exit 0).

## FSTEC-16 Geo deny

| Контроль | Где | Verify |
|----------|-----|--------|
| nginx GeoIP → заголовок | prod nginx: `add_header X-Geo-Country $geoip2_data_country_code;` (пример) | stage curl с `-H X-Geo-Country: XX` |
| App enforce | `ORG_GEO_DENY_ENFORCE=1`, `ORG_GEO_DENY_COUNTRIES=RU,CN` | `OrgGeoDenyFilter`, `smoke-org-geo-deny.ps1` |
| Audit | `access.geo.denied` | admin audit-events query |

**Не включать на lab по умолчанию** — только stage/prod ansible extra vars или host env.

## FSTEC-17 Passkeys / WebAuthn

| Контроль | Где | Verify |
|----------|-----|--------|
| Scaffold API | `GET/POST /api/v1/platform/passkeys` | `smoke-passkeys-scaffold.ps1` |
| Schema | `user_passkey_credentials`, V060/V063 Flyway | H2 migration tests |
| Full WebAuthn ceremony | backlog G-SEC-06 | LSO после stage |

## Связанные smokes (strict gate)

```powershell
.\scripts\security-gate.ps1 -Strict
.\scripts\smoke-fstec-prod-prep.ps1
.\scripts\smoke-denied-access-audit.ps1
.\scripts\smoke-passkeys-scaffold.ps1
# stage only:
.\scripts\smoke-tls-redirect.ps1 -HttpUrl http://messenger.stage.example.com -HttpsUrl https://messenger.stage.example.com
```

## Ansible inventory checklist

- [ ] `korus_tls_enabled: true` в stage и prod `group_vars/all.yml`
- [ ] `korus_tls_domain` совпадает с DNS
- [ ] secrets в `group_vars/vault.yml` (prod)
- [ ] UFW + LAN publish (`korus_configure_ufw`, `korus_use_lan_publish`)
- [ ] TURN prod overlay (`korus_web_turn_prod`, `korus_turn_host`)

## Экспертиза ФСТEC

Формальная экспертиза и pentest на stage — **LSO-071** в spec 015 (не блокирует инженерный чеклист).

См. также [`fstec-engineering-checklist.md`](fstec-engineering-checklist.md), [`threat-model-outline.md`](threat-model-outline.md).
