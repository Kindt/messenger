# T079 / FR-120 FR-121 — ручная проверка SW cache tiers и lazy call modules

Автотестов для SW нет. Smoke: `npm run test:ws-events` в `modules/web-client/webui-build/`.

## 1. Трёхуровневый кэш (FR-120)

После деплоя открыть DevTools → Application → Cache Storage. Ожидаются три store:

| Cache | Содержимое | Стратегия |
|-------|------------|-----------|
| `korus-web-shell-v20` | CSS, manifest, icon | stale-while-revalidate |
| `korus-web-locales-v20` | `/locales/*.json` | network-first, offline fallback |
| `korus-web-static-v20` | прочие `.js`/assets | stale-while-revalidate |

Navigate (`/`, `/index.html`) **не** кэшируется — при offline показывается 503 «Сервер недоступен», не login shell.

Сброс: Настройки → «Сбросить кэш UI» или Unregister SW.

## 2. Lazy call modules (FR-121)

На первой загрузке в Network **нет** запросов `/ui-call-mesh.js` и `/ui-call-livekit.js`.

После открытия панели звонка (mesh или LiveKit SFU) должны подгрузиться соответствующие скрипты через `import("/ui-lazy-call.mjs")`.

## 3. Регрессия

```powershell
cd modules/web-client/webui-build
npm run test:ws-events
npm run build:js
```

На QEMU (`http://127.0.0.1:19088`): логин → чат → кнопка видео → mesh/LiveKit без ошибок в консоли.
