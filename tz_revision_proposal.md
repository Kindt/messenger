# Предложения по переработке ТЗ: устранение критических проблем

## 1. Регистрация: логин+пароль + модульная система аутентификации

### 1.1 Базовая регистрация (логин + пароль)

**Замена раздела 7 ТЗ:**

| Параметр | Значение |
|----------|----------|
| Идентификатор | Уникальный логин (username) в системе, min 3, max 32 символа |
| Допустимые символы | `[a-zA-Z0-9._-]` (латиница, цифры, точка, дефис, подчёркивание) |
| Пароль | min 8 символов, Argon2id |
| Подтверждение | Captcha (cloud-turnstile или reCAPTCHA v3) при регистрации |
| Rate-limit регистрации | не более 3 аккаунтов с одного IP за 24 часа |
| Total lifetime | аккаунт inactive > 12 мес — блокировка + уведомление по email |

### 1.2 Модульная система аутентификации (Auth Provider Layer)

```
┌─────────────────────────────────────────────────────┐
│                   Client                             │
│  POST /api/v1/auth/login  { provider, credentials }  │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│              messenger-core-api                      │
│         /api/v1/auth/* endpoint                     │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│            Auth Provider Router                      │
│   Выбирает провайдера по:                            │
│    • provider field из запроса                       │
│    • domain (если login содержит @domain)            │
│    • default (логин+пароль, KeyCloak local)          │
└──┬──────────┬──────────┬──────────┬─────────────────┘
   │          │          │          │
   ▼          ▼          ▼          ▼
┌──────┐ ┌────────┐ ┌────────┐ ┌──────────┐
│Local │ │Google  │ │Yandex  │ │LDAP/AD   │
│DB    │ │IdP     │ │IdP     │ │IdP       │
│(pwd) │ │(OIDC)  │ │(OIDC)  │ │(OIDC)    │
└──────┘ └────────┘ └────────┘ └──────────┘
   │          │          │          │
   └──────────┴──────────┴──────────┘
                      │
           ┌──────────▼──────────┐
           │   KeyCloak (IDP)    │
           │  Token Issuance     │
           │  JWT (access)       │
           │  Refresh Token      │
           │  Session Mgmt       │
           └─────────────────────┘
```

**Архитектура:**

1. **KeyCloak** — центральный Identity Provider. Отвечает за:
   - Выпуск JWT access token + refresh token
   - Federation с внешними IdP (Google, Yandex, любые OIDC)
   - Domain mapping (login@example.com → IdP для example.com)
   - User federation (LDAP/AD при необходимости)
   - Session management и logout (включая logout со всех устройств)

2. **Auth Provider Router** — лёгкий слой внутри core-api:
   - Принимает `{ provider: "google" | "yandex" | "keycloak" | "ldap", credentials }`
   - Если login содержит `@domain` — маршрутизирует по домену (из конфигурации)
   - Делегирует аутентификацию выбранному провайдеру
   - На выходе — единый JWT от KeyCloak (unified token)

3. **Типы провайдеров (подключаемые модули):**

   | Модуль | Механизм | Конфигурация |
   |--------|----------|--------------|
   | `password` (local DB) | Проверка login + Argon2id hash | Встроен всегда |
   | `google` | OIDC (authorization_code flow) | client_id, client_secret, allowed_domains |
   | `yandex` | OIDC | client_id, client_secret |
   | `oidc-generic` | Любой OIDC-провайдер | discovery_url, client_id, client_secret |
   | `ldap` | LDAP bind | url, base_dn, bind_dn, user_filter |

4. **Domain-привязка (domain records):**

   Конфигурационный файл (YAML):
   ```yaml
   auth:
     default_provider: password
     domain_providers:
       "company.com":     { provider: keycloak, realm: company, allow_register: false }
       "example.org":     { provider: google, allow_register: true }
       "edu.ru":          { provider: ldap, ldap_config: "edu_ldap", allow_register: false }
     providers:
       password:
         enabled: true
       google:
         enabled: true
         client_id: "..."
         client_secret: "..."
         allowed_domains: ["example.org"]
   ```

5. **Unified token:**

   ```json
   {
     "sub": "uuid-пользователя",
     "login": "ivanov",
     "email": "ivanov@company.com",
     "auth_provider": "ldap",
     "iat": 1700000000,
     "exp": 1700003600,
     "iss": "avandocmsg/keycloak"
   }
   ```

6. **Device management (без изменений):**
   - device_id обязателен
   - Один refresh token на устройство
   - Logout с устройства / со всех устройств

---

## 2. Двойная модель блокировок в группах

**Замена/уточнение раздела 10 ТЗ.**

### 2.1 Два независимых механизма

```
┌─────────────────────────────────────────────┐
│           Блокировки в группах              │
├──────────────────────────┬──────────────────┤
│  Personal Filter (Mute)  │  Chat Ban        │
│  "Я не хочу видеть"     │  "Пользователю   │
│                          │   отказано"      │
│  Инициатор: пользователь │  Инициатор:      │
│  Эффект: только для     │  admin/owner      │
│  инициатора             │  Эффект: для всех │
└──────────────────────────┴──────────────────┘
```

### 2.2 Personal Filter (скрытие сообщений)

| Параметр | Значение |
|----------|----------|
| Кто устанавливает | Любой участник группы на любого другого участника |
| Эффект для инициатора | Сообщения фильтруемого не отображаются, уведомления не приходят |
| Эффект для фильтруемого | **Никакого** — не знает о фильтре, продолжает писать |
| Эффект для остальных | Никакого |
| Quotes/Replies | Если фильтруемый отвечает на сообщение того, кто его фильтрует — реплай показывается (иначе контекст теряется), но исходное сообщение заменяется на `[message hidden]` |
| Реализация | **Серверная**: при доставке событий WS — исключить сообщения; при запросе истории — фильтр на сервере; поиск — ACL post-filter |
| Отмена | В любой момент |

### 2.3 Chat Ban (отказ в доступе)

| Параметр | Значение |
|----------|----------|
| Кто устанавливает | owner, admin (по ролям группы) |
| Эффект для забаненного | Не может: читать историю, писать, видеть участников, получать уведомления о новых сообщениях, упоминать |
| Эффект для остальных | Сообщения забаненного не видны никому (кроме admin) |
| Soft-ban vs Hard-ban | **Soft**: не может писать, но читает; **Hard**: полная изоляция (по конфигурации группы) |
| Добавление в группу | Забаненный не может быть добавлен повторно без снятия бана |
| Снятие бана | Только установивший admin или owner |

### 2.4 Матрица эффектов

```
                    Personal Filter активен
                    Да                  Нет
Chat Ban    Да  [Chat Ban]         [Chat Ban]
Активен         Сообщения не        Сообщения не
                видны никому,       видны никому,
                + инициатор лично  забаненный не
                скрыл              имеет доступа

            Нет [Personal Filter]  [No blocks]
                Только инициатор   Нормальная
                скрыл сообщения    работа
```

### 2.5 Quoted/Preview при блокировках (уточнение)

- **Personal filter**: цитата сообщения фильтруемого отображается как `[message hidden]` только для инициатора фильтра. Для всех остальных — нормально.
- **Chat ban**: цитата сообщения забаненного заменяется на `[banned message]` для всех не-admin участников.

---

## 3. Выбор протокола E2EE

### 3.1 Рекомендация: MLS (Messaging Layer Security, RFC 9420) + SFrame

```
┌─────────────────────────────────────────────────────────────┐
│                    E2EE Architecture                        │
├────────────────┬────────────────┬──────────────────────────┤
│  1:1 Messaging  │ Group Messaging │  Media (Calls/Live)     │
│   MLS           │   MLS           │   SFrame + DTLS-SRTP    │
│   (Ratchet)     │   (TreeKEM)     │                         │
└────────────────┴────────────────┴──────────────────────────┘
```

### 3.2 Обоснование выбора MLS

| Критерий | MLS (RFC 9420) | Signal Protocol | OMEMO |
|----------|---------------|-----------------|-------|
| Групповая эффективность | **O(log N)** — TreeKEM | O(N) — каждый участнику отдельно | O(N) |
| Forward Secrecy (FS) | Да | Да | Да |
| Post-Compromise Security (PCS) | Да (commit) | Только 1:1 | Только 1:1 |
| Асинхронность | Да | Да | Да |
| IETF Standard | **RFC 9420** (2023) | Де-факто | XEP |
| Поддержка добавления/удаления участников | **O(log N)** | O(N) | O(N) |
| Экосистема | libmls (C), OpenMLS (Rust), mls-rs | libsignal (Rust) | libomemo |

**MLS выбирается как единый протокол** для всех текстовых коммуникаций по причинам:
- Эффективность для групп (TreeKEM — логарифмическая сложность)
- Единый протокол для 1:1 и групп — меньше кода, меньше ошибок
- Пост-компромиссная безопасность (автоматическое восстановление)
- Стандартизирован IETF

### 3.3 Media E2EE: SFrame

Для аудио/видео (calls и live):
- **SFrame** (Secure Frame, IETF draft) — лёгкий фреймовый шифр для медиа
- **DTLS 1.3 + SRTP** — для transport-layer между SFU и участниками
- Ключи распространяются через MLS group (text channel несёт media keys)
- **DVR encryption**: client-side encryption, 1 ключ на запись, ключ хранится в защищённом контуре сервера (custody)

### 3.4 HLS (Live >200)

- Без E2EE, только TLS (как в исходном ТЗ)
- Шифрование HLS-сегментов через AES-128 (Transport Stream encryption)
- Ключи — через авторизованный endpoint

### 3.5 Key management

| Тип ключа | Хранение на сервере | Доступность |
|-----------|-------------------|-------------|
| MLS identity keys | Нет | Только на устройствах |
| MLS group state | Нет | Только на устройствах участников |
| DVR encryption keys | Да (защищённый контур) | Сервер (custody) |
| E2EE backup | Нет | Не поддерживается (в соответствии с ТЗ) |

---

## 4. Rate-limit стратегия

### 4.1 Слои rate-limiting

```
Layer 1: Global (per IP)
Layer 2: Per-endpoint (per path + method)
Layer 3: Per-user (per authenticated token)
Layer 4: Per-action (тип операции)
Layer 5: Per-bot (отдельные лимиты для Bot API)
```

### 4.2 Алгоритм: Sliding Window Counter (Redis + Lua)

```
Ключ: ratelimit:{layer}:{key}:{window_id}
Окно: фиксированные windows (1s / 1m / 1h / 1d)
Скользящий подсчёт: prev_window_count * weight + current_window_count
```

**Redis-память**: O(N) где N = число активных клиентов × число лимитов

### 4.3 Таблица лимитов

#### 4.3.1 Global (per IP, до аутентификации)

| Endpoint | Limit | Window | Response |
|----------|-------|--------|----------|
| `POST /auth/login` | 10 | 1 min | 429 + Retry-After |
| `POST /auth/register` | 3 | 24 h | 429 |
| `POST /auth/refresh` | 20 | 1 min | 429 |
| Весь API (unauthenticated) | 100 | 1 min | 429 |

#### 4.3.2 Authenticated per-user

| Action | Limit | Window | Notes |
|--------|-------|--------|-------|
| Send message (1:1) | 60 | 1 min | Per chat pair |
| Send message (group) | 30 | 1 min | Per group |
| Read history | 120 | 1 min | Per chat |
| Search | 30 | 1 min | Global |
| Upload file | 10 | 1 min | Per user |
| Create group | 10 | 1 h | Per user |
| Edit message | 60 | 1 min | Per user |
| Delete message | 30 | 1 min | Per user |
| Reactions | 60 | 1 min | Per user |
| Contact import | 5 | 1 h | Per user |
| WS connect | 5 | 10 sec | Per token |
| WS messages out | 120 | 1 min | Per connection |

#### 4.3.3 Bot API

| Action | Limit | Window |
|--------|-------|--------|
| SendMessage (бот) | 30 | 1 min |
| Webhook delivery | 100 | 1 min |
| Long-poll requests | 60 | 1 min |
| API methods total | 120 | 1 min |

#### 4.3.4 File proxy (public links)

| Action | Limit | Window |
|--------|-------|--------|
| Resize request | 60 | 1 min |
| Download (unauth) | 30 | 1 min |
| Download (auth) | 120 | 1 min |

### 4.4 Response headers

```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 42
X-RateLimit-Reset: 1700000100
Retry-After: 3
```

### 4.5 Burst handling

- **Token bucket** поверх sliding window для коротких burst: 2× базового лимита
- Превышение burst → 429 + Retry-After

### 4.6 Backpressure для WS

- При превышении лимита WS сообщений — закрытие соединения с кодом `4008 (rate limited)` + Retry-After
- Альтернатива: задержка доставки (queuing) — опционально

### 4.7 Graceful degradation

- При недоступности Redis — использовать in-memory counter с degraded accuracy
- Лимиты — конфиг (hot-reload через NATS или watchdog файла)

---

## 5. File Proxy: разрешение конфликта

### 5.1 Решение: отдельный сервис с dev-режимом in-process

```
Production:                 Dev / single-node:
┌──────────────┐           ┌──────────────┐
│  Core API    │           │  Core API    │
│  (stateless) │           │  + FileProxy │
└──────┬───────┘           │  (in-process)│
       │ REST call         └──────────────┘
┌──────▼───────┐
│  File Proxy  │
│  (separate   │
│   service)   │
└──────┬───────┘
       │ S3 protocol
┌──────▼───────┐
│    MinIO     │
└──────────────┘
```

### 5.2 Почему отдельный сервис

| Аргумент | Детали |
|----------|--------|
| **Нагрузка** | Resize on-the-fly — CPU-bound. Core API — I/O-bound. Разные профили потребления. |
| **Stateless core** | Core API должен оставаться stateless для горизонтального масштабирования. File Proxy с ресайзом требует кэша. |
| **DoS-защита** | Публичные ссылки (A/B/C) — атакуемая поверхность. Отдельный сервис изолирует риск. |
| **Rate-limit** | У file proxy — свои лимиты (по трафику, по resize). Легче конфигурировать отдельно. |
| **CDN-интеграция** | File proxy может быть заменён/дополнен CDN. Отдельный сервис упрощает это. |

### 5.3 Dev-режим

- **in-process module** внутри core-api для dev-min и dev-full стендов
- Интерфейс `FileProxyProvider` с двумя реализациями: `EmbeddedFileProxy` (dev) и `RemoteFileProxyClient` (prod)
- Переключение через конфиг: `file-proxy.mode: embedded | remote`

## 6. Dual TTL model: visibility + archive

**Уточнение раздела 5/6 ТЗ (TTL сообщений).**

### 6.1 Две независимые модели TTL

| Параметр | `visibility_ttl_seconds` | `archive_ttl_seconds` |
|----------|-------------------------|----------------------|
| Назначение | Скрыть сообщение из UI | Перенести тело в deep-archive |
| Эффект в Hot DB | Сообщение не показывается в ленте/поиске, строка сохраняется | `content` очищается, в hot остаётся каркас (id, метаданные) |
| Эффект в Deep Archive | Не влияет | JSON снимок в MinIO |
| Для кого | Все участники чата | Все участники чата |
| Отображение для админа | Видно в админке (audit) | Каркас + ссылка на deep-archive |
| Origin | Отправитель сообщения | Политика организации/чата |
| Поведение при обоих | `visibility_ttl` < `archive_ttl`: сначала скрывается из UI, позже — deep-archive | |
| Совместимость с existing | Старое поле `ttl_seconds` → `visibility_ttl_seconds` (Jackson alias) | Новое поле |

### 6.2 Формат в API

```json
{
  "text": "Hello",
  "visibility_ttl_seconds": 3600,
  "archive_ttl_seconds": 86400
}
```

- Если указаны оба: `visibility_ttl` должен быть <= `archive_ttl` (проверка на сервере).
- Лимиты: `MESSAGE_VISIBILITY_TTL_MAX_SECONDS`, `MESSAGE_ARCHIVE_TTL_MAX_SECONDS`.

### 6.3 Миграция существующих данных

- Старое поле `messages.ttl_seconds` → `messages.visibility_ttl_seconds` (rename).
- Существующие сообщения с `ttl_seconds` получают семантику `visibility_ttl`.

---

### 5.4 Конфигурация

```yaml
file-proxy:
  mode: remote              # embedded | remote
  # remote mode:
  endpoint: "http://file-proxy:8082"
  connection-pool: 100
  # embedded mode:
  resize-enabled: true
  max-image-size: 10MB
  cache:
    max-size: 512MB
    ttl: 1h
```

### 5.5 API file proxy (отдельный сервис)

| Endpoint | Описание |
|----------|----------|
| `GET /files/{fileId}` | Скачивание оригинального файла (auth check) |
| `GET /files/{fileId}/resize?w=200&h=200` | Ресайз on-the-fly |
| `GET /public/{linkId}` | Публичная ссылка (A/B/C) |
| `POST /upload` | Загрузка в MinIO (авторизованная) |

### 5.6 Преимущества решения

- Production: полное разделение, независимое масштабирование
- Разработка: один процесс без Docker-композиции
- Интерфейс единый — код core-api не зависит от режима
- Тестирование: можно тестировать обе реализации

---

## Сводка изменений в разделы ТЗ

| Раздел ТЗ | Действие |
|-----------|----------|
| 7 (Регистрация) | Полная замена: SMS → логин+пароль + KeyCloak + модульные провайдеры |
| 10 (Блокировки) | Дополнение: Personal Filter + Chat Ban как отдельные механизмы |
| 24 (Безопасность, E2EE) | Замена: MLS (RFC 9420) как единый протокол + SFrame для медиа |
| Новый раздел (Rate-limit) | Добавить как отдельный раздел или подраздел 4 |
| 15 vs 6.X.1 (File proxy) | Фиксация: отдельный сервис + dev-режим in-process |
