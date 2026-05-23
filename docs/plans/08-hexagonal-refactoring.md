# Ports & Adapters рефакторинг (Phase 2-3)

**Статус:** `not_started`
**Теги:** `[рефакторинг]` `[core-api]` `[архитектура]` `[тесты]` `[CI]`

---

## Цель

1. Миграция `core-api` к Hexagonal Architecture: вынести реализации из `api.*` в `core.domain`, `core.application`, `core.port`, `core.adapter.*`.
2. Рефакторинг по одному агрегату за раз (Chat → Message → User → ...).
3. Performance benchmark для регрессии производительности.
4. Отдельный CI job для регрессионных тестов.

---

## Текущее состояние

- **Phase 0-1:** Созданы пакеты `core.domain`, `core.application`, `core.port`, `core.adapter.*`.
- Примеры: `NatsOutboundPort`, `UuidGenerator`, `NatsConnectionOutbound`.
- **Осталось:** основной код в `api.*`, сервисы вместе с ресурсами, репозитории в `api.repository.*`.
- Прямые зависимости от JAX-RS в бизнес-логике.

---

## Зависимости

- **Нет блокирующих зависимостей от других эпиков.**
- Может конфликтовать с feature-ветками.
- Рекомендуется после завершения feature-работ.

---

## Шаги реализации

Каждая фаза выделяет один агрегат (domain + port + adapter + service).

### Phase 2a — Chat

**2a.1. Domain модель `Chat.java`**
- [ ] `core/domain/Chat.java`:
  ```java
  public class Chat {
      private ChatId id;
      private String name;
      private UserId ownerId;
      private ChatType type;
      private Instant createdAt;
      private Instant updatedAt;
      // без JAX-RS аннотаций
  }
  ```
- [ ] `core/domain/ChatMember.java`.
- [ ] `core/domain/ChatType.java` (enum).

**2a.2. Port `ChatRepository.java`**
- [ ] `core/port/ChatRepository.java`:
  ```java
  public interface ChatRepository {
      Optional<Chat> findById(ChatId id);
      List<Chat> findByUser(UserId userId, Page page);
      Chat save(Chat chat);
      void delete(ChatId id);
  }
  ```
- [ ] Существующий `ChatRepository` в `api.repository.*` может extends новый port.

**2a.3. Adapter `JdbcChatRepositoryAdapter.java`**
- [ ] `core/adapter/persistence/JdbcChatRepositoryAdapter.java`:
  - [ ] Реализует `ChatRepository`.
  - [ ] Перенести SQL из `api.repository.ChatRepositoryImpl`.
- [ ] `ChatRepositoryImpl` → удалить или делегировать адаптеру.
- **Тесты:**
  - [ ] `ChatRepositoryAdapterH2Test` — переименовать/переписать `ChatRepositoryH2Test`.

**2a.4. Application `ChatService.java`**
- [ ] `core/application/ChatService.java`:
  - [ ] `createChat(UserId owner, CreateChatRequest request) → Chat`.
  - [ ] `getChat(ChatId id) → Chat`.
  - [ ] `deleteChat(ChatId id)`.
  - [ ] Без REST-зависимостей (нет `@Context`, `UriInfo`).
- **Тесты:**
  - [ ] `ChatServiceTest` — mock `ChatRepository`.

**2a.5. Resource `ChatResource.java` — делегирование**
- [ ] `ChatResource.getChat()` → `chatService.getChat()`.
- [ ] `ChatResource.createChat()` → `chatService.createChat()`.
- [ ] Убрать прямые вызовы репозитория.
- **Тесты:**
  - [ ] `ChatResourceTest` — mock `ChatService`.

**2a.6. Composition Root**
- [ ] `CoreModule.java` — bind `ChatRepository` → `JdbcChatRepositoryAdapter`.
- [ ] `MessengerApplication.java` — регистрация модуля.

### Phase 2b — Message

**2b.1. Domain `Message.java`**
- [ ] `core/domain/Message.java`.

**2b.2. Port `MessageRepository.java`**

**2b.3. Adapter `JdbcMessageRepositoryAdapter.java`**

**2b.4. Application `MessageService.java`**

**2b.5. Resource `MessageResource.java` — делегирование**

(Структура аналогична Phase 2a)

### Phase 2c — User

(Структура аналогично, включает User, Device, Contact, Block)

### Phase 2d — File

(Структура аналогично, включает File, FileMetadata, MinIO)

### Phase 2e — Остальные (Organization, Conference, Export)

### 3. Performance benchmark

**3.1. `CoreApiBenchmark.java`** (JMH-стиль или JUnit)
- [ ] `benchmarkGetChat` — 1000 запросов к `GET /v1/chats/{id}`.
- [ ] `benchmarkGetMessages` — 1000 запросов к `GET /v1/chats/{id}/messages`.
- [ ] `benchmarkCreateChat` — 500 запросов к `POST /v1/chats`.
- [ ] Замер: p50, p95, p99 latency, throughput.
- [ ] Запускать до и после каждой фазы рефакторинга.
- **Тесты:**
  - [ ] Запуск: `./gradlew benchmark` (отдельная task).

### 4. CI job регрессии

**4.1. `.github/workflows/ci.yml` — новый job `regression`**
- [ ] Триггер: push в `main`, `develop` при изменении `modules/core-api/`.
- [ ] Steps:
  - [ ] `./gradlew buildIntegrity`.
  - [ ] `./gradlew benchmark` — сохранить результаты.
  - [ ] Сравнить с предыдущим прогоном (скачать artifact).
  - [ ] Если latency > 5% — пометить job как failed.

**4.2. GitHub Actions artifacts**
- [ ] Сохранять benchmark результаты как artifact.
- [ ] `benchmark-results/previous` → `benchmark-results/current` → diff.

### 5. Обновление документации

**5.1. `docs/ARCHITECTURE_CORE_PACKAGES.md`**
- [ ] Обновить диаграмму пакетов после Phase 2-3.
- [ ] Добавить пример: ChatAggregate.

**5.2. `docs/PARALLEL_DEVELOPMENT.md`**
- [ ] Уточнить правила для Hexagonal: новые файлы — в `core.*`, не в `api.*`.

---

## Критерии завершения

- [ ] `api.*` содержит только JAX-RS ресурсы + DTO (без бизнес-логики).
- [ ] Все репозитории реализуют port-интерфейсы.
- [ ] `core.domain` содержит domain-модели.
- [ ] `core.application` содержит бизнес-логику.
- [ ] Все существующие тесты проходят.
- [ ] Performance benchmark: регрессии нет (> 5%).
- [ ] CI job `regression` настроен.
- [ ] `buildIntegrity` проходит на CI.

---

## Риски

- **Высокая трудоёмкость:** ~20+ файлов + тесты.
- **Риск регрессии:** при переписывании DI легко сломать код.
- **HK2 сложность:** не так популярен, как Spring DI.
- Рекомендуется делать **поэтапно** (Chat → Message → User → ...), каждый этап — отдельный PR.
