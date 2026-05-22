# dev-overlay — hot-swap для webui

Рабочие копии статики веб-клиента **вне** `modules/web-client/src/main/resources/webui/`, чтобы правки не попадали в основную сборку Git.

## Инициализация

```powershell
.\scripts\dev-overlay-init.ps1
```

```bash
./scripts/dev-overlay-init.sh
```

Копирует `index.html`, `app.js`, `styles.css` из модуля в `dev-overlay/webui/`.

## Запуск с overlay

На веб-машине настройте `korus-web/.env` (см. `deploy/two-host/web.env.example`), затем:

```powershell
.\scripts\dev-overlay-up.ps1
```

Один контейнер `web-dev` на порту **9088** (или `KORUS_WEB_DEV_PORT`), каталог `dev-overlay/webui` смонтирован read-only. Переменная `WEB_CLIENT_WEBUI_OVERLAY` включает отдачу файлов с диска с fallback на classpath.

**WebSocket:** в hot-swap нет nginx `/ws`. На двух хостах в `korus-web/.env` задайте `WEB_CLIENT_WS_PUBLIC_URL=ws://<SERVER_LAN_IP>:8082/ws` (прямо на ws-gateway). Для стека с lb (`web-host-up`) — `ws://<WEB_LAN_IP>:9088/ws` (см. `deploy/two-host/web.env.example`).

После правки JS/CSS — обновите страницу в браузере (для CSS/JS иногда нужен жёсткий refresh).

## Ограничения

- Только статика **webui**; изменения Java в `modules/web-client` требуют пересборки образа или `gradlew :modules:web-client:run`.
- Для production-стека с двумя репликами и nginx используйте `korus-web-up` без hot-swap.
