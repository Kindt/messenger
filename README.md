# Korus Messenger (AvandocMsg)

Корпоративный мессенджер: Java-сервер, workers, web-client, ws-gateway. Развёртывание — Docker Compose + Ansible.

**Версия:** `0.0.1-SNAPSHOT`

## Сборка

```powershell
.\gradlew.bat buildIntegrity
```

Linux:

```bash
./gradlew buildIntegrity
```

## Структура (в Git)

```
modules/       core-api, web-client, ws-gateway, workers/*, common
services/      indexer (hot-plug)
docker/        compose-профили, Dockerfile*
deploy/        ansible, two-host, cloud cells, observability
korus-web/     nginx + реплики web-client
integrations/  mock/sidecar для узла интеграций
keycloak/      примеры realm / federation
scripts/       stack-up, smoke, cell manifest (не presentation/E2E)
```

## Развёртывание

- [`deploy/ansible/DEPLOY_QUICKSTART.md`](deploy/ansible/DEPLOY_QUICKSTART.md)
- [`deploy/ansible/README.md`](deploy/ansible/README.md)
- [`scripts/SMOKE_INDEX.md`](scripts/SMOKE_INDEX.md)

Профили compose: `docker/docker-compose.dev-min.yml`, `docker-compose.full-server.yml`, overlays pilot/scale/replica.

## Локально (не в Git)

Агенты Cursor (`.cursor/`), spec-kit (`specs/`, `.specify/`), документация (`docs/`), product deck, Playwright E2E, QEMU, hot-swap `dev-overlay/` — см. `.gitignore`. В Git: исходники и сборка **сервера и web-client** (`modules/`, `docker/`, `korus-web/`, Ansible).
