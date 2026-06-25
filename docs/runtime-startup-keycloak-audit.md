# Runtime Startup And Keycloak Audit

Дата аудита: 2026-06-24.

Цель: зафиксировать, где в репозитории находятся скрипты запуска стендов, как они включают общие настройки, и как именно запускается Keycloak. Этот файл нужен как быстрый старт для следующего чата/агента.

## Короткий вывод

- Runtime в этом проекте поднимается только в QEMU guests. Windows host не запускает `docker compose`, `full-stack-up.ps1`, `start.ps1`, `server-host-up.ps1` и похожие runtime-скрипты.
- `docker/docker-compose.dev-min.yml`, `docker/docker-compose.full-server.yml` и overlays — это guest-side compose definitions. Их запускают из Linux/QEMU guest через Ansible/bootstrap или guest shell.
- Минимальный серверный состав описан в `docker/docker-compose.dev-min.yml`; для web-клиента нужен профиль `web` у `dev-min` и attach-режим `korus-web`, но запускать это нужно внутри guest, не на Windows host.
- Уже есть краткий профильный документ `docs/DEV_STACK_PROFILES.md`; этот файл дополняет его деталями по entrypoint-скриптам и Keycloak.
- QEMU facade-скрипты в `scripts/` есть, но их зависимости сейчас отсутствуют в checkout:
  - отсутствует `scripts/qemu-up.ps1`
  - отсутствует `scripts/qemu-down.ps1`
  - отсутствует `scripts/qemu-redeploy.ps1`
  - отсутствует `deploy/qemu/lib/*`

## Найденные entrypoint-скрипты

### Guest-side Docker/compose scripts

- `scripts/start.ps1`
  - Старый короткий launcher: `scripts/start.ps1 [min|full]`.
  - На Windows host не использовать для runtime; допустим только как reference или если вызван внутри подготовленного guest/CI окружения с Docker.
  - `min` запускает `docker/docker-compose.dev-min.yml` без profile `web`.
  - `full` запускает `docker/docker-compose.full-server.yml` без явного `--profile full`.

- `scripts/full-stack-up.ps1`
  - PowerShell wrapper для `docker/docker-compose.full-server.yml`.
  - На Windows host запрещен правилами проекта; в QEMU path серверный guest использует bash-аналог `scripts/full-stack-up.sh`.
  - Запускает `docker compose -f docker/docker-compose.full-server.yml --profile full up -d`.
  - Подсказывает отдельно запускать web: `scripts/korus-web-up.ps1 -Attach -Build`.

- `scripts/full-stack-up.sh`
  - Bash-версия для Linux/QEMU guest.
  - Источник общих настроек: `scripts/lib/korus-env.sh`.
  - Запускает `docker-compose.full-server.yml` с профилями `full` и `push`.
  - Учитывает `KORUS_QEMU_CONSOLE=1`, `COMPOSE_PARALLEL_LIMIT`, `KORUS_FLEET_LAB`.

- `scripts/lean-stack-up.sh`
  - Облегченный/pilot stack.
  - Источник общих настроек: `scripts/lib/korus-env.sh`.
  - Использует compose chain:
    - `docker/docker-compose.full-server.yml`
    - `docker/docker-compose.pilot-overrides.yml`
    - `docker/docker-compose.keycloak-prod.yml`
    - `docker/docker-compose.resource-limits.yml`
  - Именно здесь Keycloak переключается в prod-like mode через overlay `docker-compose.keycloak-prod.yml`.

- `scripts/pilot-stack-up.sh`
  - Deprecated wrapper, просто вызывает `scripts/lean-stack-up.sh`.

- `scripts/korus-web-up.ps1`
  - Запускает web UI из `korus-web/`.
  - На Windows host не использовать для runtime; QEMU web guest/Ansible должны запускать web stack внутри guest.
  - По умолчанию nginx-only static UI: `docker-compose.yml + docker-compose.nginx-only.yml`.
  - `-Attach` добавляет `docker-compose.attach.yml` и подключает web к сети `korus_messenger_dev_min`.
  - Для minimal/guest stack нужен `-Attach`, потому что API и ws-gateway доступны внутри Docker network как `core-api:8080` и `ws-gateway:8081`.

- `scripts/server-host-up.ps1`
  - Two-host server launcher.
  - На Windows dev host не использовать: это runtime через host Docker. Для текущего проекта вместо него нужен QEMU server guest.
  - Использует `docker/docker-compose.full-server.yml` + `docker/docker-compose.lan-publish.yml`.
  - Публикует backend в LAN: `core-api :8080`, `Keycloak :8081`, `ws-gateway :8082`.

- `scripts/web-host-up.ps1`
  - Two-host web launcher для отдельной web-машины.
  - На Windows dev host не использовать: это runtime через host Docker. Для текущего проекта вместо него нужен QEMU web guest.
  - Проверяет `korus-web/.env` через `scripts/lib/Test-KorusWebHostEnv.ps1`.
  - Вызывает `scripts/korus-web-up.ps1` без `-Attach`, потому что backend доступен через LAN/server host env.

### QEMU facade

- `scripts/qemu-full-stack-up.ps1`
  - Полный QEMU сценарий.
  - Ставит активный stack profile `full` через `deploy/qemu/lib/Get-KorusQemuStackProfile.ps1`.
  - Ожидает `scripts/qemu-up.ps1 -StackProfile full [-KeepDisks]`.
  - Затем вызывает `scripts/qemu-redeploy.ps1`.
  - Затем `scripts/qemu-stack-wait.ps1`.
  - Для server guest документирован путь: `scripts/full-stack-up.sh`.
  - Для web guest документирован путь: `docker-compose.yml + qemu-hotswap-overlay`.

- `scripts/qemu-dev-mode.ps1`
  - Warm dev mode ожидает `scripts/qemu-up.ps1 -KeepDisks -StackProfile dev`.

- `scripts/qemu-status-minute.ps1`
  - Ожидает `deploy/qemu/lib/Get-KorusQemuMinuteReport.ps1` и `deploy/qemu/lib/Get-KorusQemuStackProfile.ps1`.

Текущий checkout содержит QEMU wrappers, но не содержит core QEMU entrypoint/library bundle. Его нужно восстановить отдельно, если запускать через QEMU.

## Общие настройки

- `scripts/lib/korus-env.ps1`
  - Задает:
    - `KORUS_COMPOSE_DEV_MIN=docker/docker-compose.dev-min.yml`
    - `KORUS_COMPOSE_FULL_SERVER=docker/docker-compose.full-server.yml`
    - `KORUS_KORUS_WEB_DIR=korus-web`
    - пути к `korus-web` compose overlays.
  - Содержит `Invoke-KorusDockerComposeInvoke` с retry.

- `scripts/lib/korus-env.sh`
  - Bash-аналог для Linux/QEMU guest.
  - Дополнительно задает `KORUS_COMPOSE_KEYCLOAK_PROD=docker/docker-compose.keycloak-prod.yml`.

- `deploy/ansible/group_vars/korus_server.yml`
  - `korus_product_addons` — список включённых модулей.
  - Пустой список → lean stack (Base only), непустой → full stack.
  - `korus_server_env_path=docker/.env.korus-server`.

- `deploy/ansible/inventory/qemu/group_vars/all.yml`
  - QEMU-specific inventory.
  - Host-forwarded ports: API `18080`, WS `18082`, UI `19088`.
  - `korus_fleet_lab: true`.
  - Fleet probes include Keycloak at `http://keycloak:8080/realms/avandocmsg`.

- `deploy/ansible/roles/korus_server/templates/korus-server.env.j2`
  - Ansible может переопределить секреты и issuer:
    - `KEYCLOAK_ADMIN_PASSWORD`
    - `KEYCLOAK_MASTER_PASSWORD`
    - `KEYCLOAK_ISSUER`
    - `KEYCLOAK_JWKS_URL`

## Как запускается Keycloak

### `docker/docker-compose.dev-min.yml`

Сервис: `keycloak`.

- Image: `quay.io/keycloak/keycloak:24.0`
- Command: `start-dev --import-realm`
- Host port: `8081:8080`
- Admin:
  - `KEYCLOAK_ADMIN=admin`
  - `KEYCLOAK_ADMIN_PASSWORD=admin`
- DB:
  - `KC_DB=postgres`
  - `KC_DB_URL=jdbc:postgresql://postgres-hot:5432/avandocmsg_hot`
  - `KC_DB_USERNAME=avandocmsg`
  - `KC_DB_PASSWORD=avandocmsg`
- Realm import:
  - `../keycloak/avandocmsg-realm.json:/opt/keycloak/data/import/avandocmsg-realm.json:ro`
- Depends on:
  - `postgres-hot` with `service_healthy`

`core-api` in this compose uses:

- `KEYCLOAK_ISSUER=http://keycloak:8080/realms/avandocmsg`
- `KEYCLOAK_JWKS_URL=http://keycloak:8080/realms/avandocmsg/protocol/openid-connect/certs`
- `KEYCLOAK_MASTER_USER=admin`
- `KEYCLOAK_MASTER_PASSWORD=admin`

### `docker/docker-compose.full-server.yml`

Keycloak base service is effectively the same as `dev-min`:

- Image: `quay.io/keycloak/keycloak:24.0`
- Command: `start-dev --import-realm`
- Host port: `8081:8080`
- Same admin credentials and realm import.
- Same internal issuer/JWKS for `core-api` and `ws-gateway`.

Full stack additionally has full/push/worker profiles and optional services, but Keycloak base mode is still dev mode unless an overlay changes it.

### `docker/docker-compose.keycloak-prod.yml`

This overlay changes only Keycloak mode/build:

- Build:
  - `docker/Dockerfile.keycloak-prod`
- Image:
  - `korus-keycloak-prod:24`
- Command:
  - `start --optimized --import-realm`
- DB env changes to host/port/database form:
  - `KC_DB_URL_HOST=postgres-hot`
  - `KC_DB_URL_PORT=5432`
  - `KC_DB_URL_DATABASE=avandocmsg_hot`
- Health/HTTP:
  - `KC_HEALTH_ENABLED=true`
  - `KC_HTTP_ENABLED=true`
  - `KC_HOSTNAME_STRICT=false`
  - `KC_HOSTNAME_STRICT_HTTPS=false`
- Memory:
  - `JAVA_OPTS_KC=-Xms128m -Xmx256m -XX:MaxMetaspaceSize=96m -XX:+UseContainerSupport`
  - `mem_limit: 512m`
- Healthcheck:
  - `curl -fsS http://127.0.0.1:8080/health/ready`

`scripts/lean-stack-up.sh` always includes this overlay.

### `docker/docker-compose.lan-publish.yml`

This overlay is used by `scripts/server-host-up.ps1` for two-host LAN dev:

- Keycloak port becomes explicitly published as `0.0.0.0:8081:8080`.
- `core-api` is published as `0.0.0.0:8080:8080`.
- `ws-gateway` is published as `0.0.0.0:8082:8081`.

## Ansible/QEMU path

`deploy/ansible/roles/korus_server/tasks/main.yml` starts the server stack inside the server guest:

1. Sources `deploy/qemu/vm-bootstrap/korus-plain-build-env.sh`.
2. Exports `SKIP_KORUS_ENSURE=1`.
3. Sources `docker/.env.korus-server` when Ansible rendered it.
4. Chooses script by `korus_product_addons`:
   - empty → `scripts/lean-stack-up.sh --down-full-first`
   - scale enabled → `scripts/enterprise-stack-up.sh --no-wait-ready`
   - otherwise → `scripts/full-stack-up.sh --no-wait-ready`
5. Waits:
   - `GET {{ korus_api_base_url }}/api/v1/health`
   - `GET {{ korus_keycloak_url }}/realms/avandocmsg`
6. Runs `KEYCLOAK_URL={{ korus_keycloak_url }} bash scripts/keycloak-ensure-dev-users.sh`.
7. Runs `scripts/wait-stack-ready.sh` for full stacks (non-empty addons).

QEMU inventory details found in `deploy/ansible/inventory/qemu/group_vars/all.yml`:

- Browser/host API: `127.0.0.1:18080`.
- Browser/host WS: `127.0.0.1:18082`.
- Browser/host UI: `127.0.0.1:19088`.
- Web guest talks to API through slirp gateway: `http://10.0.2.2:18080`.
- `korus_product_addons: []` → `scripts/lean-stack-up.sh`; non-empty → `scripts/full-stack-up.sh`.

## Dev users and realm verification

- `scripts/keycloak-ensure-dev-users.sh`
  - Default Keycloak URL: `http://127.0.0.1:8081`.
  - Uses master realm password grant with `admin/admin`.
  - Updates existing realm users:
    - `admin/admin`
    - `csadmin/csadmin`
  - Sets email/emailVerified and resets passwords.
  - Then calls `scripts/keycloak-verify-realm.sh`.

- `scripts/keycloak-verify-realm.sh`
  - Checks `GET /realms/avandocmsg/.well-known/openid-configuration`.
  - Checks API login via `POST /api/v1/auth/login` for `csadmin/csadmin`.

## QEMU seed command map

После восстановления QEMU launcher bundle и поднятия guests seed запускается с Windows host только как HTTP-клиент к forwarded ports:

```powershell
.\scripts\seed-demo-users.ps1 -BaseUrl http://127.0.0.1:18080 -KeycloakUrl http://127.0.0.1:18081
```

Expected QEMU forwarded ports:

- API: `http://127.0.0.1:18080`
- Keycloak: `http://127.0.0.1:18081`
- WS gateway: `ws://127.0.0.1:18082/ws`
- Web UI: `http://127.0.0.1:19088/`

Не запускать `docker compose` на Windows host для этого сценария.

## Что важно восстановить для QEMU

Если следующий чат продолжает QEMU path, сначала нужно восстановить missing local bundle:

- `scripts/qemu-up.ps1`
- `scripts/qemu-down.ps1`
- `scripts/qemu-redeploy.ps1`
- `deploy/qemu/lib/*`
- вероятно также `deploy/qemu/vm-bootstrap/*`, если отсутствует в checkout

Без них `scripts/qemu-full-stack-up.ps1` и `scripts/qemu-dev-mode.ps1` не могут стартовать VMs.
