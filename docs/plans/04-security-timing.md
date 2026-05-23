# Безопасность — timing, унификация ответов, security headers

**Статус:** `not_started`
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
- [ ] Для каждого эндпоинта (`GET /users/{id}`, `GET /chats/{id}`, `POST /auth/login`):
  - [ ] Выполнить 100 запросов к существующему ресурсу, 100 к несуществующему.
  - [ ] Замерить среднее время ответа.
  - [ ] Если разница > 5% — отметить.
- [ ] `docs/SECURITY_AUDIT.md` — результаты.

**1.2. Устранение timing-разницы**
- [ ] Если есть разница:
  - [ ] Добавить фиксированную задержку (например, `Thread.sleep(10ms)`) на пути "not found".
  - [ ] Или: всегда выполнять одинаковые операции (проверять существование + права за один SQL).
- **Тесты:**
  - [ ] `TimingAttackPreventionTest` — проверить, что разница < 1ms.

### 2. Унификация ответов на несуществующие ресурсы

**2.1. Аудит эндпоинтов**
- [ ] Пройти по всем `*Resource.java`:
  - [ ] `ChatResource.getChat()` — 404 vs 403.
  - [ ] `MessageResource.getMessage()` — 404 vs 403.
  - [ ] `UserResource.getUser()` — 404 vs 403.
  - [ ] `FileResource.getFile()` — 404 vs 403.
  - [ ] `ContactResource.*` — 404 vs 403.
  - [ ] `BlockResource.*` — 404 vs 403.
- [ ] Где отличается — унифицировать до `404` с телом `ApiError("error.resource.not_found")`.

**2.2. Ключи i18n**
- [ ] `messages_core_api_ru.properties` — `error.resource.not_found=Ресурс не найден`.
- [ ] `messages_core_api_en.properties` — `error.resource.not_found=Resource not found`.
- **Тесты:**
  - [ ] `MessagesCoreApiBundleParityTest` — проверить новый ключ.

### 3. Rate limiting

**3.1. `AuthRateLimiter` → `RateLimiter` (обобщение)**
- [ ] Переименовать в `com.avandocmsg.messenger.api.auth.RateLimiter`.
- [ ] Сделать конфигурируемым:
  - [ ] `endpoint` — какой эндпоинт лимитировать.
  - [ ] `capacity` — токены.
  - [ ] `refillRate` — восстановление в секунду.
- [ ] Env: `RATE_LIMITER_ENABLED`, `RATE_LIMITER_DEFAULT_CAPACITY` (default `100`).
- [ ] Применить к:
  - [ ] `POST /v1/chats` (создание чатов).
  - [ ] `POST /v1/chats/{id}/export` (экспорт).
  - [ ] `POST /v1/users` (регистрация).
  - [ ] `POST /v1/auth/login` (уже есть).
- **Тесты:**
  - [ ] `RateLimiterTest` — токен-бакет.

### 4. Security headers

**4.1. `SecurityHeadersFilter.java`** (новый)
- [ ] `ContainerResponseFilter`:
  - [ ] `Strict-Transport-Security: max-age=31536000; includeSubDomains`.
  - [ ] `X-Content-Type-Options: nosniff`.
  - [ ] `X-Frame-Options: DENY`.
  - [ ] `Referrer-Policy: no-referrer`.
- [ ] `CSP` — опционально, через env `CSP_POLICY` (default `null` = не добавлять).
- [ ] Env: `SECURITY_HEADERS_ENABLED` (default `true`).
- **Тесты:**
  - [ ] `SecurityHeadersFilterTest` — проверить, что headers присутствуют.

### 5. Аудит CORS

**5.1. `CorsOriginPolicy.java` — проверка**
- [ ] Убедиться, что `Access-Control-Allow-Origin` не `*` при `credentials=true`.
- [ ] Env: `CORS_ALLOWED_ORIGINS` (default `*`).
- [ ] В production: только конкретные originы.
- **Тесты:**
  - [ ] `CorsOriginPolicyTest` — проверить restricted originы.

### 6. WebSocket origin

**6.1. `MessagingWebSocket.java` — `@OnOpen`**
- [ ] Проверить `Origin` header при подключении.
- [ ] Если `WS_ALLOWED_ORIGINS` задан и origin не в списке — отправить close frame (code 4001).
- [ ] Env: `WS_ALLOWED_ORIGINS` (default `*`).
- **Тесты:**
  - [ ] `MessagingWebSocketOriginTest` — mock `@OnOpen`.

### 7. HEAD метод

**7.1. Убедиться, что HEAD не возвращает body**
- [ ] Jersey по умолчанию обрабатывает HEAD → GET без body. Проверить для всех ресурсов.
- [ ] Добавить тест: `HEAD /v1/chats` → `200`, `Content-Length` без body.
- **Тесты:**
  - [ ] `HeadMethodTest` — curl/jersey test.

### 8. Унификация обработки ошибок

**8.1. Мапперы исключений**
- [ ] `RuntimeExceptionMapper` — всегда `ApiError` + `500`.
- [ ] `WebApplicationExceptionMapper` — `ApiError` + `status`.
- [ ] Убрать stacktrace из ответа: `@Override writeTo()` — не писать stacktrace.
- [ ] Логировать stacktrace в SLF4J.
- **Тесты:**
  - [ ] `ExceptionMapperTest` — проверить, что ответ не содержит stacktrace.

### 9. Smoke-тесты

**9.1. `scripts/smoke-security-headers.ps1`**
- [ ] Проверить `Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options`.

**9.2. `scripts/smoke-rate-limit.ps1`**
- [ ] 200 запросов к `POST /auth/login` — 101-й должен быть `429`.

---

## Критерии завершения

- [ ] `scripts/audit-timing.ps1` — разница не более 5%.
- [ ] Все эндпоинты отдают унифицированный `ApiError`.
- [ ] Rate limit включён на критичных эндпоинтах.
- [ ] Security headers присутствуют во всех ответах.
- [ ] WebSocket проверяет origin.
- [ ] Smoke: `smoke-security-headers.ps1`, `smoke-rate-limit.ps1` проходят.
- [ ] Документация: `docs/SECURITY.md`.

---

## Риски

- Унификация ответов может сломать клиенты, которые полагаются на `404`/`403`.
- CSP может сломать web-client UI.
- Timing-атаки сложно полностью устранить.
