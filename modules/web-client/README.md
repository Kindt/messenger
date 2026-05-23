# Модуль `web-client`

Браузерный UI мессенджера на **встроенном Tomcat** (статика **`webui/`**, HTTP-прокси **`/api/*`** на **`WEB_CLIENT_API_UPSTREAM`**, конфиг WS — **`/web-client-env.js`**).

## Возможности UI (`webui/app.js`)

- Экран входа / регистрации и основное окно в оформлении **КОРУС Консалтинг**: список чатов с поиском, область переписки, поле ввода.
- После обновления статики: баннер «Доступна новая версия» (Service Worker) или **Настройки → Сбросить кэш UI**.
- Сообщения рендерятся как **безопасное подмножество Markdown** (экранирование HTML, **жирный**, *курсив*, `` `код` ``, блоки ` ``` `, ссылки только **http/https**).
- Панель **«Видео / конференция»**: локальный поток, миниатюры, демонстрация экрана (в mesh у удалённых участников), **mesh WebRTC** с другими участниками чата: сигналы (**offer / answer / candidate / hangup**) уходят по тому же WebSocket в NATS **`rtc.signal`** и рассылаются **message-pipeline** на **`msg.deliver.{userId}`** (проверка членства в чате). Для жёсткого NAT может понадобиться **TURN** (сейчас только публичный STUN).
- Сессия: при **401** — автоматический **`POST /auth/refresh`** и повтор запроса; при обрыве WebSocket — переподключение с backoff (индикатор **WS переподкл.**).
- Вложения: кнопка **Файл** в композере — **`POST /files/upload`** (multipart), сообщение с типом **image** / **video** / **file** и **`content`** = **file_id**; превью картинок и скачивание через API с JWT.

## Локально

```bash
# из корня репозитория
.\gradlew.bat :modules:web-client:run
```

Переменные: **`WEB_CLIENT_PORT`**, **`WEB_CLIENT_API_UPSTREAM`**, **`WEB_CLIENT_WS_PUBLIC_URL`**, опционально **`WEB_CLIENT_RTC_ICE_SERVERS`** — JSON-массив ICE-серверов для WebRTC (см. **`WebClientEnvServlet`**, поле **`iceServersJson`** в **`/web-client-env.js`**). Пример: `[{"urls":"stun:stun.l.google.com:19302"},{"urls":"turn:turn.example.com:3478","username":"u","credential":"p"}]`.

## Docker и балансировщик

Готовый стек (**две реплики + nginx**): каталог **`../../korus-web/`** (см. **`korus-web/README.md`**). Образ собирается из **`docker/Dockerfile.web-client`** (контекст сборки — корень репозитория).

## Стенд с WebSocket

Профиль **`web`** в **`docker/docker-compose.dev-min.yml`** (**`scripts/dev-web-stack-up.ps1`** или **`scripts/dev-web-stack-up.sh`**: **`KORUS_*`**, проверка окружения, повтор **`docker compose`**) поднимает **ws-gateway** и **message-pipeline**; остановка того же профиля: **`scripts/dev-web-stack-down.ps1`** / **`scripts/dev-web-stack-down.sh`**. Для UI из корня — **`scripts/korus-web-up.ps1`** или **`scripts/korus-web-up.sh`** (**`-Attach` / `--attach`**, **`-Turn` / `--turn`**, **`-SkipEnsure`** / **`SKIP_KORUS_ENSURE=1`** — см. корневой **`README.md`**); остановка — **`scripts/korus-web-down.ps1`** / **`.sh`** с теми же флагами. Смоки: **`scripts/smoke-korus-web.ps1`** / **`scripts/smoke-korus-web.sh`**. Подробности: **`scripts/TEST_SERVER_READY.md`**.

## Parity пакет (Spec-Kit)

Для полного пакета доработки web-client до текущего серверного состояния используйте:

- **`specs/002-web-client-server-parity/README.md`** (точка входа),
- **`specs/002-web-client-server-parity/parity-matrix.md`** (baseline покрытия),
- **`specs/002-web-client-server-parity/parity-report.md`** (итог + deferred runtime gates),
- **`specs/002-web-client-server-parity/runtime-gate-report.md`** (operator-run шаблон фиксации `T010`/`T016`/`T022`).
