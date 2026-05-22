# Два хоста: dev-сервер и веб-клиент

Развёртывание Korus Messenger на **двух машинах** в одной LAN: backend (полный Docker-стек) и UI (`korus-web`) отдельно. С третьего ПК в сети открывают UI по IP веб-машины.

## Схема

| Машина | Роль | Скрипт подъёма |
|--------|------|----------------|
| 1 | PostgreSQL, NATS, core-api, ws-gateway, workers | `scripts/server-host-up.ps1` / `server-host-up.sh` |
| 2 | web-client (+ nginx lb или hot-swap) | `scripts/web-host-up.ps1` / `web-host-up.sh` |

На машине 2 **не** используйте `-Attach` / `--attach` — общая Docker-сеть возможна только на одном демоне.

## Быстрый старт

### Машина 1 (сервер)

1. Установите Docker.
2. Скопируйте при необходимости `deploy/two-host/server.env.example` в корень как подсказку для `CORS_ALLOWED_ORIGINS`.
3. Из корня репозитория:

   ```powershell
   .\scripts\server-host-up.ps1 -Build
   ```

   ```bash
   ./scripts/server-host-up.sh --build
   ```

4. Узнайте LAN-IP: `ipconfig` (Windows) или `hostname -I` (Linux).
5. Откройте порты в брандмауэре (см. ниже).
6. С другого ПК: `curl http://<SERVER_IP>:8080/api/v1/health`

### Машина 2 (веб)

1. Клон репозитория (достаточно каталогов `korus-web/`, `docker/`, `modules/web-client/` для сборки образа — проще весь репозиторий).
2. Скопируйте `deploy/two-host/web.env.example` → `korus-web/.env`, подставьте `<SERVER_LAN_IP>` и `<WEB_LAN_IP>`.
3. Задайте в `.env` или в окружении `KORUS_SERVER_HOST=<SERVER_LAN_IP>` (для проверки скрипта).
4. Запуск:

   ```powershell
   .\scripts\web-host-up.ps1 -Build
   ```

5. Hot-swap UI (опционально): `.\scripts\dev-overlay-init.ps1`, правки в `dev-overlay/webui/`, затем `.\scripts\dev-overlay-up.ps1`.

Браузер: `http://<WEB_LAN_IP>:9088/`

## Порты для LAN

| Порт | Машина | Сервис |
|------|--------|--------|
| 8080 | 1 | core-api, `/admin/` |
| 8082 | 1 | ws-gateway (хост → контейнер 8081) |
| 8081 | 1 | Keycloak (опционально) |
| 9088 | 2 | nginx lb или web-dev (hot-swap) |

Не открывайте в интернет без VPN: 5432, 6379, 4222, 9000, 8983.

## Брандмауэр

### Windows (PowerShell от администратора)

```powershell
New-NetFirewallRule -DisplayName "Korus core-api" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
New-NetFirewallRule -DisplayName "Korus ws-gateway" -Direction Inbound -Protocol TCP -LocalPort 8082 -Action Allow
# на веб-машине:
New-NetFirewallRule -DisplayName "Korus web lb" -Direction Inbound -Protocol TCP -LocalPort 9088 -Action Allow
```

### Linux (ufw)

```bash
sudo ufw allow 8080/tcp comment 'korus core-api'
sudo ufw allow 8082/tcp comment 'korus ws-gateway'
# на веб-машине:
sudo ufw allow 9088/tcp comment 'korus web'
```

## Проверка

```powershell
.\scripts\smoke-korus-web.ps1 -CheckApi
# с другого ПК:
.\scripts\smoke-korus-web.ps1 -CheckApi -Url "http://<WEB_IP>:9088"
```

```bash
./scripts/smoke-korus-web.sh --check-api --url "http://<WEB_IP>:9088"
```

Подсказки URL: `.\scripts\dev-ui-hints.ps1 -LanIp <WEB_IP>` (на веб-машине укажите IP сервера для API/WS в `.env`).

## Hot-swap

Каталог [`dev-overlay/webui/`](../../dev-overlay/) — копии `app.js`, `styles.css`, `index.html` вне основного модуля. См. [`dev-overlay/README.md`](../../dev-overlay/README.md).
