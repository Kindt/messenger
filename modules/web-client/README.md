# Модуль `web-client`

Браузерный UI мессенджера на **встроенном Tomcat** (статика **`webui/`**, HTTP-прокси **`/api/*`** на **`WEB_CLIENT_API_UPSTREAM`**, конфиг WS — **`/web-client-env.js`**).

## Возможности UI (`webui/app.js`)

- Экран входа / регистрации и основное окно в стиле мессенджера: список чатов с поиском, область переписки, поле ввода.
- Сообщения рендерятся как **безопасное подмножество Markdown** (экранирование HTML, **жирный**, *курсив*, `` `код` ``, блоки ` ``` `, ссылки только **http/https**).
- Панель **«Видео / конференция»**: локальный поток, миниатюры, демонстрация экрана, **mesh WebRTC** с другими участниками чата: сигналы (**offer / answer / candidate / hangup**) уходят по тому же WebSocket в NATS **`rtc.signal`** и рассылаются **message-pipeline** на **`msg.deliver.{userId}`** (проверка членства в чате). Для жёсткого NAT может понадобиться **TURN** (сейчас только публичный STUN).

## Локально

```bash
# из корня репозитория
.\gradlew.bat :modules:web-client:run
```

Переменные: **`WEB_CLIENT_PORT`**, **`WEB_CLIENT_API_UPSTREAM`**, **`WEB_CLIENT_WS_PUBLIC_URL`**, опционально **`WEB_CLIENT_RTC_ICE_SERVERS`** — JSON-массив ICE-серверов для WebRTC (см. **`WebClientEnvServlet`**, поле **`iceServersJson`** в **`/web-client-env.js`**). Пример: `[{"urls":"stun:stun.l.google.com:19302"},{"urls":"turn:turn.example.com:3478","username":"u","credential":"p"}]`.

## Docker и балансировщик

Готовый стек (**две реплики + nginx**): каталог **`../../korus-web/`** (см. **`korus-web/README.md`**). Образ собирается из **`docker/Dockerfile.web-client`** (контекст сборки — корень репозитория).

## Стенд с WebSocket

Профиль **`web`** в **`docker/docker-compose.dev-min.yml`** (**`scripts/dev-web-stack-up.ps1`** или **`scripts/dev-web-stack-up.sh`**: **`KORUS_*`**, проверка окружения, повтор **`docker compose`**) поднимает **ws-gateway** и **message-pipeline**; для UI из корня — **`scripts/korus-web-up.ps1`** или **`scripts/korus-web-up.sh`** (**`-Attach` / `--attach`**, **`-SkipEnsure`** / **`SKIP_KORUS_ENSURE=1`** — см. корневой **`README.md`**). Смоки: **`scripts/smoke-korus-web.ps1`** / **`scripts/smoke-korus-web.sh`**. Подробности: **`scripts/TEST_SERVER_READY.md`**.
