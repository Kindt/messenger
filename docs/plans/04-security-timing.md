# Безопасность — timing, унификация ответов, security headers

**Статус:** `completed`
**Теги:** `[безопасность]` `[core-api]` `[ws-gateway]` `[web-client]`

---

## Цель

1. Устранить timing-атаки (перечисление пользователей, чатов, сообщений).
2. Унифицировать ответы API для несуществующих ресурсов.
3. Дополнить rate limiting на критичных эндпоинтах.
4. Добавить security headers (HSTS, CSP, X-Frame-Options, X-Content-Type-Options).
5. Аудит CORS и WebSocket origin-проверок.

---

## Текущее состояние

- **Rate limit на `/auth`:** `AuthRateLimiter`.
- **Поиск пользователей** (`GET /users`) — учитывает блокировки.
- **`ApiError`** — унифицированный формат ошибки.
- **`InvalidUuidParameterException`** — обработка невалидных UUID.
- **WebSocket:** нет проверки origin.
- **Security headers:** `X-Content-Type-Options: nosniff` — только для `/admin/` статики.
- **CORS:** `CorsOriginPolicy` — базовая конфигурация.

---

## Зависимости

- **Нет блокирующих зависимостей.**
- Может конфликтовать с другими эпиками при изменении одних и тех же ресурсов.

---

## Шаги реализации

### 1. Аудит эндпоинтов на timing-атаки

**1.1. Измерительный скрипт `scripts/audit-timing.ps1`**
- [x] Для каждого эндпоинта (`GET /users/{id}`, `GET /chats/{id}`, `POST /auth/login`):
  - [x] Выполнить 100 запросов к существующему ресурсу, 100 к несуществующему.
  - [x] Замерить среднее время ответа.
  - [x] Если разница > 5% — отметить.
- [x] `docs/SECURITY_AUDIT.md` — результаты.

**1.2. Устранение timing-разницы**
- [x] Если есть разница:
  - [x] Добавить фиксированную задержку (например, `Thread.sleep(10ms)`) на пути "not found".
  - [x] Или: всегда выполнять одинаковые операции (проверять существование + права за один SQL).
- **Тесты:**
  - [x] `TimingAttackPreventionTest` — проверить, что разница < 1ms.

### 2. Унификация ответов на несуществующие ресурсы

**2.1. Аудит эндпоинтов**
- [x] Пройти по всем `*Resource.java`:
  - [x] `ChatResource.getChat()` — 404 vs 403.
  - [x] `MessageResource.getMessage()` — 404 vs 403.
  - [x] `UserResource.getUser()` — 404 vs 403.
  - [x] `FileResource.getFile()` — 404 vs 403.
  - [x] `ContactResource.*` — 404 vs 403.
  - [x] `BlockResource.*` — 404 vs 403.
- [x] Где отличается — унифицировать до `404` с телом `ApiError("error.resource.not_found")`.

**2.2. Ключи i18n**
- [x] `messages_core_api_ru.properties` — `error.resource.not_found=Ресурс не найден`.
- [x] `messages_core_api_en.properties` — `error.resource.not_found=Resource not found`.
- **Тесты:**
  - [x] `MessagesCoreApiBundleParityTest` — проверить новый ключ.

### 3. Rate limiting

**3.1. `AuthRateLimiter` → `RateLimiter` (обобщение)**
- [x] Переименовать в `com.avandocmsg.messenger.api.auth.RateLimiter`.
- [x] Сделать конфигурируемым:
  - [x] `endpoint` — какой эндпоинт лимитировать.
  - [x] `capacity` — токены.
  - [x] `refillRate` — восстановление в секунду.
- [x] Env: `RATE_LIMITER_ENABLED`, `RATE_LIMITER_DEFAULT_CAPACITY` (default `100`).
- [x] Применить к:
  - [x] `POST /v1/chats` (создание чатов).
  - [x] `POST /v1/chats/{id}/export` (экспорт).
  - [x] `POST /v1/users` (регистрация).
  - [x] `POST /v1/auth/login` (уже есть).
- **Тесты:**
  - [x] `RateLimiterTest` — токен-бакет.

### 4. Security headers

**4.1. `SecurityHeadersFilter.java`** (новый)
- [x] `ContainerResponseFilter`:
  - [x] `Strict-Transport-Security: max-age=31536000; includeSubDomains`.
  - [x] `X-Content-Type-Options: nosniff`.
  - [x] `X-Frame-Options: DENY`.
  - [x] `Referrer-Policy: no-referrer`.
- [x] `CSP` — опционально, через env `CSP_POLICY` (default `null` = не добавлять).
- [x] Env: `SECURITY_HEADERS_ENABLED` (default `true`).
- **Тесты:**
  - [x] `SecurityHeadersFilterTest` — проверить, что headers присутствуют.

### 5. Аудит CORS

**5.1. `CorsOriginPolicy.java` — проверка**
- [x] Убедиться, что `Access-Control-Allow-Origin` не `*` при `credentials=true`.
- [x] Env: `CORS_ALLOWED_ORIGINS` (default `*`).
- [x] В production: только конкретные originы.
- **Тесты:**
  - [x] `CorsOriginPolicyTest` — проверить restricted originы.

### 6. WebSocket origin

**6.1. `MessagingWebSocket.java` — `@OnOpen`**
- [x] Проверить `Origin` header при подключении.
- [x] Если `WS_ALLOWED_ORIGINS` задан и origin не в списке — отправить close frame (code 4001).
- [x] Env: `WS_ALLOWED_ORIGINS` (default `*`).
- **Тесты:**
  - [x] `MessagingWebSocketOriginTest` — mock `@OnOpen`.

### 7. HEAD метод

**7.1. Убедиться, что HEAD не возвращает body**
- [x] Jersey по умолчанию обрабатывает HEAD → GET без body. Проверить для всех ресурсов.
- [x] Добавить тест: `HEAD /v1/chats` → `200`, `Content-Length` без body.
- **Тесты:**
  - [x] `HeadMethodTest` — curl/jersey test.

### 8. Унификация обработки ошибок

**8.1. Мапперы исключений**
- [x] `RuntimeExceptionMapper` — всегда `ApiError` + `500`.
- [x] `WebApplicationExceptionMapper` — `ApiError` + `status`.
- [x] Убрать stacktrace из ответа: `@Override writeTo()` — не писать stacktrace.
- [x] Логировать stacktrace в SLF4J.
- **Тесты:**
  - [x] `ExceptionMapperTest` — проверить, что ответ не содержит stacktrace.

### 9. Smoke-тесты

**9.1. `scripts/smoke-security-headers.ps1`**
- [x] Проверить `Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options`.

**9.2. `scripts/smoke-rate-limit.ps1`**
- [x] 200 запросов к `POST /auth/login` — 101-й должен быть `429`.

---

## Критерии завершения

- [x] `scripts/audit-timing.ps1` — разница не более 5%.
- [x] Все эндпоинты отдают унифицированный `ApiError`.
- [x] Rate limit включён на критичных эндпоинтах.
- [x] Security headers присутствуют во всех ответах.
- [x] WebSocket проверяет origin.
- [x] Smoke: `smoke-security-headers.ps1`, `smoke-rate-limit.ps1` проходят.
- [x] Документация: `docs/SECURITY.md`.

---

## Риски

- Унификация ответов может сломать клиенты, которые полагаются на `404`/`403`.
- CSP может сломать web-client UI.
- Timing-атаки сложно полностью устранить.
