# E2EE/MLS — полное соответствие RFC 9420

**Статус:** `not_started`
**Теги:** `[e2ee]` `[core-api]` `[криптография]` `[web-client]` `[безопасность]`

---

## Цель

1. Реализовать полное соответствие RFC 9420 (MLS): дерево ключей, Welcome/Commit, wire-протокол, ротация эпох.
2. Миграция существующих E2EE-ключей на MLS.
3. Fallback-механизм для старых клиентов (без MLS).
4. Admin UI для мониторинга статуса MLS-ключей.
5. Benchmark-тесты производительности MLS-операций.

---

## Текущее состояние

- **`CryptoResource`** — REST эндпоинты для key packages.
- **`E2EEService`** — упрощённая E2EE (не полный MLS).
- **`SessionRepository`** — хранение сессий (`mls_sessions`).
- **`MlsService`** — базовый слой (Bouncy Castle, частичный MLS).
- **`KeyPackageRepository`** — хранение key packages.
- **E2EE типы сообщений:** `e2ee-*`.

---

## Зависимости

- **Нет блокирующих зависимостей от других эпиков.**
- Зависит от продукта: требуется решение о полноте MLS.

---

## Шаги реализации

### 1. Архитектурное решение

**1.1. Выбор MLS-библиотеки**
- [ ] Исследовать: `org.openmls:openmls-java`, Bouncy Castle, Wire.
- [ ] Принять решение: самописная реализация vs библиотека.
- [ ] `build.gradle.kts` — добавить зависимость.
- [ ] `docs/E2EE_ARCHITECTURE.md` — документ с решением.

### 2. Дерево ключей (Ratchet Tree)

**2.1. `MlsGroupManager.java`** (новый)
- [ ] `createGroup(List<UserId> members) → MlsGroupId`.
- [ ] `addMember(MlsGroupId groupId, KeyPackage keyPackage) → CommitOutput`.
- [ ] `removeMember(MlsGroupId groupId, UserId userId) → CommitOutput`.
- [ ] `encrypt(MlsGroupId groupId, byte[] plaintext) → MlsCiphertext`.
- [ ] `decrypt(MlsGroupId groupId, MlsCiphertext ciphertext) → byte[]`.

**2.2. `MlsGroupRepository.java`** (новый)
- [ ] `save(MlsGroupState state)`.
- [ ] `findByGroupId(MlsGroupId groupId) → Optional<MlsGroupState>`.
- [ ] `deleteByGroupId(MlsGroupId groupId)`.

**2.3. Миграция V026**
- [ ] `V026__mls_group_state.sql`:
  ```sql
  CREATE TABLE mls_group_state (
      group_id UUID PRIMARY KEY,
      chat_id UUID NOT NULL REFERENCES chats(id),
      epoch BIGINT NOT NULL,
      tree_data BYTEA NOT NULL,
      created_at TIMESTAMP NOT NULL DEFAULT now(),
      updated_at TIMESTAMP NOT NULL DEFAULT now()
  );
  CREATE INDEX idx_mls_group_chat ON mls_group_state(chat_id);
  ```

**2.4. Тесты дерева ключей**
- [ ] `MlsServiceTreeTest`:
  - [ ] `createGroup` → `encrypt` → `decrypt`.
  - [ ] `addMember` → новый участник может decrypt.
  - [ ] `removeMember` → удалённый участник не может decrypt.

### 3. Welcome / Commit

**3.1. `MlsService.handleWelcome()`**
- [ ] При добавлении участника: создать Welcome message.
- [ ] Сериализовать Welcome в JSON (base64).
- [ ] Сохранить в `messages` как `type=e2ee-mls-welcome`.

**3.2. `MlsService.handleCommit()`**
- [ ] При смене состава: создать Commit, опубликовать как `type=e2ee-mls-commit`.
- [ ] NATS subject `mls.commit` для рассылки.

**3.3. Тесты**
- [ ] `MlsServiceWelcomeTest` — проверить, что Welcome создаётся.
- [ ] `MlsServiceCommitTest` — проверить ротацию эпох.

### 4. Wire-протокол

**4.1. Сериализация**
- [ ] `MlsCiphertext` → byte[] → base64 → `content` в JSON.
- [ ] `SendMessageRequest` — новое поле `e2ee_scheme: "mls"`.
- [ ] `MessageService.send()` — при `e2ee_scheme=mls`: вызвать `MlsGroupManager.encrypt()`.

**4.2. DTO**
- [ ] `modules/common/src/main/java/.../common/dto/MlsMessage.java`:
  ```java
  public class MlsMessage {
      private String groupId;
      private long epoch;
      private String ciphertext; // base64
      private byte[] senderData; // MLS sender data
  }
  ```

**4.3. Web-client**
- [ ] `app.js` — расшифровка MLS при получении сообщения через WS.
- [ ] Обработка `e2ee-mls-welcome`, `e2ee-mls-commit`.

### 5. Ротация эпох

**5.1. Автоматическая ротация**
- [ ] После N сообщений (configurable `MLS_EPOCH_ROTATION_INTERVAL`, default 1000).
- [ ] После изменения состава чата.
- [ ] `MlsGroupManager.rotateEpoch() → CommitOutput`.

### 6. Миграция старых E2EE-ключей

**6.1. `E2EEService.migrateToMls()`**
- [ ] При первом сообщении после обновления:
  - [ ] Создать MLS группу для чата.
  - [ ] Разослать Welcome всем участникам.
  - [ ] Пометка `chat.e2ee_migrated_to_mls = true`.
- [ ] Старые E2EE-сообщения остаются читаемыми через старый `E2EEService`.

### 7. Fallback для старых клиентов

**7.1. Двойная поддержка**
- [ ] Если клиент прислал `e2ee_scheme=legacy` — использовать старый `E2EEService`.
- [ ] Если `e2ee_scheme=mls` — использовать `MlsService`.
- [ ] `CapabilitiesResource` / `GET /media/capabilities` — сообщать поддержку MLS.

### 8. NATS + ws-gateway

**8.1. Новые NATS subjects**
- [ ] `mls.welcome` — Welcome message.
- [ ] `mls.commit` — Commit.
- [ ] `mls.key_package` — обновление key packages.

**8.2. `PipelineFanoutLogic.java`**
- [ ] Подписка на `mls.*`, fan-out на участников чата.

### 9. Admin UI: статус MLS

**9.1. `CoreAdminUiContributor.java` — раздел «E2EE/MLS»**
- [ ] `core-e2ee-mls` — `json_panel` с `data_path = /admin/e2ee/status`.
- [ ] `AdminResource.java` — `GET /admin/e2ee/status`:
  - [ ] Количество MLS-групп.
  - [ ] Количество чатов, ожидающих миграции.
  - [ ] `mls_group_state` размер.

### 10. Benchmark

**10.1. `MlsBenchmarkTest.java`**
- [ ] `createGroup(N)` — для N = 2, 10, 50, 100.
- [ ] `encrypt/decrypt` — 1000 сообщений.
- [ ] `addMember` — 10 участников.
- **Ожидание:** encrypt < 50ms, createGroup(100) < 1s.

### 11. OpenAPI

**11.1. Новые эндпоинты**
- [ ] `POST /v1/crypto/key-packages` — `@ExampleObject`.
- [ ] `GET /v1/crypto/key-packages/{userId}` — `@Schema`.
- [ ] `POST /v1/crypto/mls/welcome` — обработка Welcome.

---

## Критерии завершения

- [ ] `MlsService` реализует RFC 9420 (Key Schedule, Ratchet Tree, Welcome, Commit).
- [ ] E2EE-сообщения проходят полный цикл: отправка → pipeline → расшифровка.
- [ ] Тесты interop: сообщение зашифрованное сервером расшифровывается OpenMLS клиентом.
- [ ] Старые E2EE-сообщения продолжают работать.
- [ ] Admin UI: статус MLS-групп отображается.
- [ ] Benchmark: encrypt < 50ms для группы до 100 участников.

---

## Риски

- **Высокая сложность:** MLS — один из самых сложных криптопротоколов.
- **Совместимость:** переход на полный MLS сломает старые клиенты.
- **Юридические риски:** E2EE может быть ограничен в некоторых регионах.
