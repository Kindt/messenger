# Korus Web — развёртывание веб-клиента (Docker)

Автономный стек: **две реплики** приложения **`modules/web-client`** (Java 25 + встроенный Tomcat, образ из **`../docker/Dockerfile.web-client`**) и **nginx** как **балансировщик** поверх HTTP.

Балансировщик:

- проксирует **`/`** на реплики **web-a** / **web-b** (порт приложения **9080** внутри контейнера);
- проксирует **`/ws`** на **ws-gateway** (по умолчанию `host.docker.internal:8081`), чтобы браузер мог использовать **`ws://localhost:<порт lb>/ws`** совместно с **`WEB_CLIENT_WS_PUBLIC_URL`**.

## Запуск

Из **этого каталога** (родитель репозитория — в **`context: ..`** для сборки образа клиента):

```bash
docker compose up --build -d
```

Из корня репозитория на Linux/macOS: **`./scripts/korus-web-up.sh --build`** (при необходимости **`chmod +x scripts/korus-web-up.sh`**).

## Остановка

Те же overlay-файлы (**`attach`**, **`turn`**), что и при запуске:

- **`.\scripts\korus-web-down.ps1`** / **`./scripts/korus-web-down.sh`** — по умолчанию базовый **`docker-compose.yml`**;
- с теми же флагами, что у **`up`**: **`-Attach -Turn`** / **`--attach --turn`**;
- опционально **`-Volumes`** / **`--volumes`** / **`-V`** — **`down -v`** (удаление анонимных томов проекта).

Если поднимали **полный стенд** (**`scripts/full-stack-up`**) и **korus-web** с **`attach`**: перед **`full-stack-down`** остановите UI (**`korus-web-down`** с теми же **`-Attach` / `-Turn`**), иначе контейнеры **korus-web** останутся в общей сети.

UI: **`http://localhost:9088`** (если не меняли **`KORUS_WEB_LB_PORT`**).

Переменные окружения см. **`.env.example`**. Рекомендуется скопировать в **`.env`** и выставить **`WEB_CLIENT_WS_PUBLIC_URL`** в соответствии с публичным адресом lb (по умолчанию **`ws://localhost:9088/ws`**). Ссылки и тестовые логины realm (запуск из корня репозитория): **`.\scripts\dev-ui-hints.ps1`** или **`./scripts/dev-ui-hints.sh`**.

### Вместе с `docker-compose.dev-min.yml`

1. Поднимите стенд с профилем **`web`** (**ws-gateway** на порту хоста **8082**, воркер **message-pipeline** для fan-out **`msg.send` → `msg.deliver.*`**):

   ```powershell
   .\scripts\dev-web-stack-up.ps1
   ```

   На Linux/macOS: **`./scripts/dev-web-stack-up.sh`** (**`--build`** при необходимости).

   Либо вручную: **`docker compose -f docker/docker-compose.dev-min.yml --profile web up -d`** из корня репозитория (ключ **`-Build`** у скрипта — пересборка образов).

2. В **`korus-web/.env`** задайте **`KORUS_WS_GATEWAY_PORT=8082`** (и при необходимости **`WEB_CLIENT_API_UPSTREAM=http://host.docker.internal:8080`**, если **core-api** на хосте **8080**).

3. Остановка только профиля **`web`** (без остановки всего **dev-min**): **`.\scripts\dev-web-stack-down.ps1`** / **`./scripts/dev-web-stack-down.sh`** (опционально **`-Volumes`** / **`--volumes`**).

Без профиля **`web`** доставка в сокет не работает: нужны и **ws-gateway**, и **message-pipeline** (или их ручной запуск из Gradle).

### Общая сеть с `dev-min` (без `host.docker.internal`)

Если **core-api** и **ws-gateway** уже в Docker-сети **`docker-compose.dev-min.yml`**, можно подключить **web-a** / **web-b** / **lb** к той же сети:

1. Сеть **`docker/docker-compose.dev-min.yml`** по умолчанию называется **`korus_messenger_dev_min`** (задано в compose). Другой override — задайте **`KORUS_DEV_MIN_NETWORK`** в **`korus-web/.env`** (см. **`.env.example`**).
2. Запуск с override:

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.attach.yml --env-file .env up -d --build
   ```

   Из корня репозитория: **`.\scripts\korus-web-up.ps1 -Attach`** (**`-Build`** при первой сборке) или **`./scripts/korus-web-up.sh --attach`** (**`--build`**).

3. Если после обновления compose сеть переименовалась, пересоздайте стенд dev-min (**`docker compose … down`** / **`up`**) или проверьте **`docker network ls`**.

В **`docker-compose.attach.yml`** по умолчанию **`WEB_CLIENT_API_UPSTREAM=http://core-api:8080`**, **`KORUS_WS_GATEWAY_HOST=ws-gateway`**, **`KORUS_WS_GATEWAY_PORT=8081`** (внутренний порт контейнера в общей сети). **`WEB_CLIENT_WS_PUBLIC_URL`** для браузера по-прежнему указывает на lb (например **`ws://localhost:9088/ws`**).

Проверка с хоста: **`..\scripts\smoke-korus-web.ps1`** (или **`.\scripts\smoke-korus-web.ps1`** из корня репозитория); на Linux/macOS: **`./scripts/smoke-korus-web.sh`** (**`--check-api`**).

### Локальный TURN (coturn) для WebRTC

Для сценариев за симметричным NAT поднимите **coturn** вместе со стеком и готовый **`WEB_CLIENT_RTC_ICE_SERVERS`** для браузера на **127.0.0.1:3478**:

- **`docker-compose.turn.yml`** — сервис **`coturn`** (порты **3478/tcp+udp** на хост) и переопределение **`WEB_CLIENT_RTC_ICE_SERVERS`** у **web-a** / **web-b**: **STUN** (Google) + **TURN** на **127.0.0.1** (логин **`korus`**, пароль **`korus-turn-demo-secret`** — только для стенда).
- Из корня: **`.\scripts\korus-web-up.ps1 -Turn`** или **`./scripts/korus-web-up.sh --turn`**; вместе с attach: **`-Attach -Turn`** / **`--attach --turn`**.
- Вручную: **`docker compose -f docker-compose.yml -f docker-compose.turn.yml up -d`** (при attach добавьте **`docker-compose.attach.yml`** между базой и **turn**).

В продакшене задайте свои учётные данные, **TLS** при необходимости и **`--external-ip`** для coturn (см. документацию образа **`coturn/coturn`**).

## Локальный запуск без Docker

```bash
cd ..
.\gradlew.bat :modules:web-client:run
```

Порт **`WEB_CLIENT_PORT`** (по умолчанию **9080**), см. корневой **`README.md`**.
