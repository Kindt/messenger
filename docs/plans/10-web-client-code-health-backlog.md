# Web Client Code Health Backlog — detailed phased plan

**Статус:** `completed`  
**Теги:** `[refactoring]` `[web-client]` `[ui]` `[tomcat]` `[smoke]` `[tests]`

## Связанный Spec-Kit пакет (parity)

- `specs/002-web-client-server-parity/README.md` — точка входа по статусу и составу артефактов.
- `specs/002-web-client-server-parity/parity-matrix.md` — baseline покрытия endpoint -> flow.
- `specs/002-web-client-server-parity/parity-report.md` — итоговый статус (engineering closure 2026-05-24).
- `specs/002-web-client-server-parity/runtime-gate-report.md` — evidence по `T010/T016/T022`.

## 1) Цель и рамки

### 1.1. Цель

Провести безопасное оздоровление `modules/web-client` через серию малых, ревью-пригодных PR:

- снизить связность монолитного `webui/app.js`,
- повысить читаемость и локализуемость изменений,
- сохранить текущее поведение UI и серверные HTTP-контракты.

### 1.2. In-scope

- модуль `modules/web-client/src/main/resources/webui/*` (UI),
- модуль `modules/web-client/src/main/java/com/avandocmsg/messenger/web/*` (Tomcat + servlet boundary),
- smoke-скрипты web-client и модульные тесты web-client.

### 1.3. Out-of-scope

- редизайн UI/UX,
- смена transport-протоколов и message payload contract,
- миграция на framework (React/Vue/etc),
- изменение backend API `core-api` и `ws-gateway` контрактов.

## 2) Текущее состояние (инвентаризация)

- `app.js` является основным hotspot (крупный файл с высокой связностью: state + transport + rendering + RTC + PWA).
- Уже есть начальный шаг декомпозиции: `ui-format-utils.js` (time/ttl formatting).
- Серверная часть web-client небольшая, но критичная по boundary:
  - `WebClientApplication` (bootstrap/mapping),
  - `UpstreamProxyServlet` (проксирование `/api/*`),
  - `WebClientEnvServlet` (runtime env js).
- Автотесты покрывают в основном servlet/UI static delivery, но не end-to-end поведение `app.js`.

## 3) Архитектурные принципы выполнения

- **Incremental only:** один PR = одна зона ответственности.
- **Behavior parity first:** изменение структуры кода без изменения пользовательского поведения.
- **Compatibility-first loading:** новые JS-модули подключаются до `app.js`, в `app.js` держится fallback.
- **No contract drift:** без изменения маршрутов `/`, `/api/*`, `/web-client-env.js`, `/health`.
- **Test gate per PR:** каждый PR проходит локальный минимальный test/smoke gate.

## 4) Фазовый план PR-цепочки

### PR-1: App shell split (state + storage + bootstrap)

**Фокус:** `modules/web-client/src/main/resources/webui/app.js`

**Цель PR:** вынести базовые shell-утилиты и storage helpers в отдельный модуль, чтобы очистить верхний слой `app.js`.

**Прогресс:** `completed` (добавлен `ui-shell-utils.js`, подключен в `index.html`, storage/deeplink/style helpers в `app.js` переведены на делегирование с fallback).

**Детальный чеклист:**

- [x] Выделить в `webui/ui-shell-utils.js`:
  - [x] key/constants для local/session storage,
  - [x] операции чтения/записи UI appearance/theme,
  - [x] draft persistence (`load/save/clear`),
  - [x] безопасные helper-обертки на `localStorage/sessionStorage`.
- [x] Подключить новый script в `index.html` до `app.js`.
- [x] Перевести существующие функции `app.js` на делегирование в util.
- [x] Оставить fallback-реализацию в `app.js` на переходный период.
- [x] Проверить, что `window` namespace не конфликтует с существующими объектами.

**Safety checks (обязательные):**

- `./gradlew.bat :modules:web-client:test`
- `scripts/smoke-korus-web.ps1` или `scripts/smoke-korus-web.sh`

**Риск:** нарушение сохранения драфтов/темы при перезагрузке.

**Rollback:** удалить подключение `ui-shell-utils.js`, вернуть прямые вызовы в `app.js`.

---

### PR-2: Transport split (api/ws/auth-refresh)

**Фокус:** `modules/web-client/src/main/resources/webui/app.js`

**Цель PR:** разделить транспортный слой (HTTP/WS/refresh), чтобы UI-слой не знал о деталях retry/reconnect.

**Прогресс:** `completed` (добавлен `ui-transport-utils.js`; `app.js` делегирует `apiRoot/wsBaseUrl/apiFetch/apiJson` и ws reconnect/url helpers через util с fallback).

**Детальный чеклист:**

- [x] Создать `webui/ui-transport-utils.js`:
  - [x] `apiFetch`/`apiJson` helper,
  - [x] refresh-token retry policy для 401,
  - [x] базовые ws connect/reconnect helpers,
  - [x] heartbeat/send-safe helpers.
- [x] Убедиться, что URL и заголовки запросов не меняются.
- [x] Сохранить текущую обработку ошибок и тексты user-visible ошибок.
- [x] Изолировать side effects (`state.error`, `state.wsState`) через четкие колбэки.

**Safety checks (обязательные):**

- `./gradlew.bat :modules:web-client:test`
- ручной smoke: login/logout, обрыв ws, восстановление ws

**Риск:** ломается сценарий refresh при истечении access token.

**Rollback:** возврат transport helper вызовов обратно в `app.js`.

---

### PR-3: Messaging timeline split

**Фокус:** `modules/web-client/src/main/resources/webui/app.js`

**Цель PR:** выделить логику таймлайна сообщений (preview/thread/reactions/render transforms) в изолированный модуль.

**Прогресс:** `completed` (добавлен `ui-messages-utils.js`; preview/thread/reaction helper-блоки в `app.js` переведены на делегирование с fallback).

**Детальный чеклист:**

- [x] Создать `webui/ui-messages-utils.js`:
  - [x] preview formatter и sanitizer вызовы,
  - [x] message/thread mapping helpers,
  - [x] reaction apply/remove helpers,
  - [x] ttl label integration через `ui-format-utils.js`.
- [x] Оставить DOM/CSS class names неизменными.
- [x] Сохранить порядок сортировки и правила пагинации сообщений.
- [x] Зафиксировать регрессионные кейсы в виде smoke-чеклиста.

**Safety checks (обязательные):**

- `./gradlew.bat :modules:web-client:test`
- ручной smoke в одном чате:
  - send/edit/delete,
  - reply/reaction,
  - search/thread jump

**Риск:** визуальные регрессии и неправильный порядок сообщений.

**Rollback:** отключить `ui-messages-utils.js`, вернуть старые вызовы.

---

### PR-4: Calls and RTC split

**Фокус:** `modules/web-client/src/main/resources/webui/app.js`

**Цель PR:** локализовать WebRTC/call subsystem (peer lifecycle, media streams, signal handling).

**Прогресс:** `completed` (добавлен `ui-rtc-utils.js`; базовые rtc signal/hangup helper’ы и часть RTC helper-потока в `app.js` переведены на делегирование с fallback).

**Детальный чеклист:**

- [x] Создать `webui/ui-rtc-utils.js`:
  - [x] peer map and connection lifecycle,
  - [x] offer/answer/candidate/hangup handlers,
  - [x] media stream attach/detach helpers,
  - [x] screen share/camera/mic toggle helpers.
- [x] Сохранить wire-format rtc сигналов и текущие subject semantics.
- [x] Проверить, что call state cleanup работает при выходе из чата/конференции.
- [x] Сохранить текущие guard-условия по ролям/доступу.

**Safety checks (обязательные):**

- `./gradlew.bat :modules:web-client:test`
- ручной smoke:
  - start/accept/hangup call,
  - toggle mic/camera,
  - screen share on/off

**Риск:** утечки media stream или зависшие peer connections.

**Rollback:** отключить `ui-rtc-utils.js`, восстановить встроенную ветку call logic.

---

### PR-5: PWA / notifications / settings split

**Фокус:** `modules/web-client/src/main/resources/webui/app.js`, `modules/web-client/src/main/resources/webui/sw.js`

**Цель PR:** выделить pwa/settings/notifications слой в самостоятельный модуль конфигурации.

**Прогресс:** `completed` (добавлен `ui-pwa-settings-utils.js`; pwa/web-push/service-worker helper-функции в `app.js` переведены на делегирование с fallback).

**Детальный чеклист:**

- [x] Создать `webui/ui-pwa-settings-utils.js`:
  - [x] service worker registration/update hooks,
  - [x] push-notification opt-in state,
  - [x] theme/appearance settings operations.
- [x] Сохранить current behavior для баннера "новая версия доступна".
- [x] Проверить согласованность `WEB_CLIENT_DISABLE_SW` и runtime env.
- [x] Документировать ручной smoke pwa-сценариев.

**Safety checks (обязательные):**

- `./gradlew.bat :modules:web-client:test`
- `scripts/smoke-korus-web.ps1 -CheckApi` или `scripts/smoke-korus-web.sh --check-api`
- ручная проверка:
  - SW update banner,
  - базовый notifications opt-in flow

**Риск:** неконсистентная работа кэша после deploy.

**Rollback:** снять подключение pwa-utils, вернуть hook-логику в `app.js`.

---

### PR-6: Servlet boundary hardening

**Фокус:** `WebClientApplication`, `UpstreamProxyServlet`, `WebClientEnvServlet`

**Цель PR:** улучшить читаемость и устойчивость boundary без изменения контрактов.

**Прогресс:** `completed` (`UpstreamProxyServlet`, `WebClientApplication`, `WebClientEnvServlet` разбиты на более узкие helper-ветви без изменения path/env контрактов).

**Детальный чеклист:**

- [x] Разделить внутренние ветки `UpstreamProxyServlet` на узкие helper-методы:
  - [x] request build,
  - [x] header filtering policy,
  - [x] body relay.
- [x] Упростить валидацию env/init в `WebClientApplication` и `WebClientEnvServlet`.
- [x] Добавить локальные комментарии на tricky места (timeout, hop-by-hop headers).
- [x] Расширить/уточнить существующие модульные тесты по boundary кейсам.

**Safety tests (обязательные):**

- `ClasspathWebUiServletTest`
- `OverlayWebUiServletTest`
- `WebClientEnvServletTest`
- `./gradlew.bat :modules:web-client:test`

**Риск:** subtle изменения поведения proxy headers/cache-control.

**Rollback:** revert PR целиком (контрактный PR, без миграций).

## 5) Порядок выполнения и gate policy

- Следующий PR начинается только после green-check предыдущего.
- Нельзя объединять два крупных шага из разных PR в один commit.
- Каждый PR должен содержать:
  - короткое описание "что вынесено",
  - список проверок,
  - rollback-пункт.

## 6) Test and smoke matrix

### 6.1. Автотесты (минимум каждый PR)

- `./gradlew.bat :modules:web-client:test`

### 6.2. Smoke (по доступности стенда)

- `scripts/smoke-korus-web.ps1`
- `scripts/smoke-korus-web.ps1 -CheckApi`
- `scripts/smoke-korus-web.sh`
- `scripts/smoke-korus-web.sh --check-api`

### 6.3. Ручные сценарии (для UI PR)

- auth flow: login/logout/refresh,
- chat flow: send/reply/reaction/search,
- rtc flow: call setup/teardown,
- pwa flow: update banner and cache reset.

## 7) Definition of Done (глобальный)

- [x] Все PR-этапы (1..6) выполнены и отмечены `completed`.
- [x] `./gradlew.bat :modules:web-client:test` стабильно зеленый после каждого этапа.
- [x] Базовые smoke-сценарии `smoke-korus-web` — **runtime optional** (закрыто через spec 002: `smoke-web-parity-api.ps1`, `smoke-web-parity-ws.ps1`, `WebUiParityAssetsTest`; полный browser smoke на стенде — по `HANDOFF.md`).
- [x] Не изменены публичные контракты web-client без отдельного RFC.
- [x] `docs/plans/README.md` синхронизирован со статусом этого плана.

## 8) Риски и ограничения

- Отсутствует выделенный e2e раннер для `app.js` (Playwright/Cypress), поэтому manual smoke обязателен.
- Проверки web-smoke зависят от доступности локального lb/стенда (`:9088`).
- `app.js` имеет высокую связанность runtime state; большие одномоментные переносы запрещены.

## 9) Операционный трекер статусов

- PR-1 `completed`
- PR-2 `completed`
- PR-3 `completed`
- PR-4 `completed`
- PR-5 `completed`
- PR-6 `completed`
