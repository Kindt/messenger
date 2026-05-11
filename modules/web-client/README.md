# Модуль `web-client`

Браузерный UI мессенджера на **встроенном Tomcat** (статика **`webui/`**, HTTP-прокси **`/api/*`** на **`WEB_CLIENT_API_UPSTREAM`**, конфиг WS — **`/web-client-env.js`**).

## Локально

```bash
# из корня репозитория
.\gradlew.bat :modules:web-client:run
```

Переменные: **`WEB_CLIENT_PORT`**, **`WEB_CLIENT_API_UPSTREAM`**, **`WEB_CLIENT_WS_PUBLIC_URL`** (см. **`WebClientApplication`**, **`WebClientEnvServlet`**).

## Docker и балансировщик

Готовый стек (**две реплики + nginx**): каталог **`../../korus-web/`** (см. **`korus-web/README.md`**). Образ собирается из **`docker/Dockerfile.web-client`** (контекст сборки — корень репозитория).

## Стенд с WebSocket

Профиль **`web`** в **`docker/docker-compose.dev-min.yml`** (**`scripts/dev-web-stack-up.ps1`** или **`scripts/dev-web-stack-up.sh`**: **`KORUS_*`**, проверка окружения, повтор **`docker compose`**) поднимает **ws-gateway** и **message-pipeline**; для UI из корня — **`scripts/korus-web-up.ps1`** или **`scripts/korus-web-up.sh`** (**`-Attach` / `--attach`**, **`-SkipEnsure`** / **`SKIP_KORUS_ENSURE=1`** — см. корневой **`README.md`**). Смоки: **`scripts/smoke-korus-web.ps1`** / **`scripts/smoke-korus-web.sh`**. Подробности: **`scripts/TEST_SERVER_READY.md`**.
