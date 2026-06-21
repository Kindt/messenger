# Feature Specification: Strict Base + Add-ons Conformance

**Feature Branch**: `024-strict-base-addons`

**Created**: 2026-06-21

**Status**: Implemented

**Input**: User description: "Довести Product Modules до идеального соответствия модели: утвердить lean Base, атомарные дополнения, горячее включение, временное отключение из админки, optional migrations и строгие gates."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Base-only поставка работает без дополнений (Priority: P1)

Администратор или QA запускает минимальную поставку Korus Messenger без выбранных add-ons. Пользователь может авторизоваться, видеть организации и пользователей, создавать чаты, отправлять сообщения, прикладывать файлы, получать realtime-доставку и пользоваться базовым поиском. Интерфейс не показывает функции, которые относятся к отключенным дополнениям.

**Why this priority**: Это фундамент модели. Если Base-only не работает чисто, add-on модель остается декларативной, а не продуктовой.

**Independent Test**: Запустить Base-only профиль и пройти smoke: auth, users/orgs, chats, messages, files, websocket delivery, SQL search, basic admin, health and metrics.

**Acceptance Scenarios**:

1. **Given** включена только базовая поставка, **When** пользователь открывает web UI, **Then** он видит только базовые элементы интерфейса.
2. **Given** отключены все add-ons, **When** пользователь создает чат и отправляет сообщение с файлом, **Then** сообщение и файл доступны без ошибок.
3. **Given** отключен add-on полнотекстового поиска, **When** пользователь ищет сообщения, **Then** доступен базовый поиск без зависимости от расширенного поискового backend-а.
4. **Given** отключены compliance/media/integration add-ons, **When** пользователь открывает UI, **Then** export/live/integrations/E2EE/retention controls не отображаются как доступные функции.

---

### User Story 2 - Каждый add-on управляется единообразно (Priority: P1)

Администратор включает конкретный add-on и получает только соответствующие функции, UI-элементы, backend-поведение, workers и required secrets. Отключенные add-ons не просачиваются через API, UI или фоновые обработчики.

**Why this priority**: Это основной критерий соответствия Product Modules модели.

**Independent Test**: Для каждого add-on запустить профиль Base + этот add-on и проверить positive/negative сценарии.

**Acceptance Scenarios**:

1. **Given** включен один add-on, **When** пользователь открывает UI, **Then** отображаются только controls этого add-on и Base.
2. **Given** add-on отключен, **When** клиент обращается к его функциональной поверхности, **Then** система применяет поведение из каталога: hide, fallback, queue, reject или controlled unavailable state.
3. **Given** add-on требует секреты, **When** секреты не заданы, **Then** add-on получает состояние degraded или disabled с понятной причиной.
4. **Given** add-on отключен, **When** появляются события для его домена, **Then** система не обрабатывает их как активную пользовательскую функцию.

---

### User Story 3 - Оператор видит честное состояние модулей (Priority: P2)

Оператор или администратор открывает публичный capabilities view или admin product modules view и видит, какие модули включены, отключены или деградированы, почему, какие компоненты, секреты и фоновые процессы нужны.

**Why this priority**: Без операционной прозрачности Base/Add-ons модель трудно сопровождать и демонстрировать.

**Independent Test**: Изменить набор выбранных add-ons и проверить, что публичный и admin views показывают одинаковую эффективную картину с разным уровнем детализации.

**Acceptance Scenarios**:

1. **Given** add-on включен и все зависимости доступны, **When** оператор открывает статус модулей, **Then** add-on отображается как enabled.
2. **Given** add-on включен, но не хватает обязательного секрета, **When** оператор открывает статус, **Then** он видит degraded или disabled и причину `secrets_missing`.
3. **Given** add-on выключен администратором, **When** UI и API проверяют capabilities, **Then** состояние совпадает с admin override.

---

### User Story 4 - QA проверяет матрицу поставки (Priority: P2)

QA может проверить Base-only, Base + selected add-ons и Full add-ons без ручной сверки списка сервисов, UI controls и функциональной поверхности.

**Why this priority**: Это превращает модель в проверяемый контракт, пригодный для регресса.

**Independent Test**: Запустить автоматизированную матрицу smoke/contract checks для Base-only, каждого add-on отдельно и полного набора.

**Acceptance Scenarios**:

1. **Given** Base-only профиль, **When** запускается smoke, **Then** проходят только базовые сценарии.
2. **Given** Base + add-on профиль, **When** запускается add-on smoke, **Then** positive сценарий включенного add-on проходит.
3. **Given** add-on disabled profile, **When** запускается negative smoke, **Then** функциональная поверхность ведет себя по degradation contract.

---

### User Story 5 - Product owner собирает коммерческую конфигурацию (Priority: P3)

Product owner или presale выбирает Base и набор add-ons и получает понятный состав функций, required services, secrets, limits and sizing impact.

**Why this priority**: Это полезно для продажи и документации, но не блокирует строгую runtime-модель.

**Independent Test**: Сгенерировать summary для нескольких конфигураций и проверить совпадение с каталогом.

**Acceptance Scenarios**:

1. **Given** выбран Base + несколько add-ons, **When** формируется summary, **Then** список функций, сервисов и секретов соответствует каталогу.
2. **Given** выбран Base-only, **When** формируется summary, **Then** не отображаются enterprise/media/compliance/integration зависимости.

### Edge Cases

- Add-on указан в конфигурации, но отсутствует в каталоге.
- Add-on есть в каталоге, но не имеет определенного degradation behavior.
- Функциональная поверхность расширения доступна, но не привязана к add-on.
- UI показывает control add-on до загрузки effective capabilities.
- Add-on включен, но required secret отсутствует.
- Add-on включен, но required background capability unavailable.
- Background processor активен, но add-on disabled.
- Search работает в базовом fallback, когда расширенный search add-on disabled или degraded.
- E2EE disabled, но в истории уже есть E2EE-сообщения.
- Legacy deploy profile и явный список add-ons задают разные наборы; явный список должен иметь приоритет.

## Requirements *(mandatory)*

### Functional Requirements

#### P0 - Mandatory conformance

- **FR-001**: System MUST define one canonical Base product surface that is always available when the core product is healthy.
- **FR-002**: Base MUST include users, organizations, authentication/session basics, chats, messages, files, realtime delivery, contacts/blocks, basic search, basic administration, health and metrics.
- **FR-003**: System MUST classify every non-Base user-facing feature as either an existing add-on, a new add-on, or explicitly accepted Base behavior.
- **FR-004**: System MUST treat polls, scheduled messages, reminders and stickers/GIFs as `addon-productivity`; kanban and whiteboard as `addon-collaboration`; AI assist, speech recognition and captions as `addon-ai`.
- **FR-005**: System MUST expose effective add-on states as enabled, disabled or degraded with a reason that users and operators can understand.
- **FR-006**: System MUST apply the catalog degradation mode consistently across user interface, functional surface, background processing and deploy composition.
- **FR-007**: System MUST prevent disabled add-ons from appearing as available user actions.
- **FR-008**: System MUST prevent disabled add-ons from accepting user-initiated operations except where the catalog explicitly defines a fallback behavior.
- **FR-009**: System MUST keep Base workflows functional when any add-on is disabled, degraded or missing required configuration.
- **FR-010**: System MUST treat enhanced search as an add-on and basic search as Base.
- **FR-011**: System MUST treat SFU/live broadcast as an add-on and keep any accepted Base calling/conference capability explicitly documented.
- **FR-012**: System MUST treat bots, integrations, directory sync, compliance export, archive, deep archive, retention and E2EE/MLS according to explicit add-on state.
- **FR-013**: System MUST report missing required secrets as degraded or disabled add-on state instead of silently exposing broken user actions.
- **FR-014**: System MUST define the precedence between explicit selected add-ons, legacy profile shims and admin overrides.
- **FR-015**: System MUST provide Base-only acceptance coverage that proves no optional backend, worker or external app is required for Base workflows.
- **FR-016**: System MUST provide positive and disabled/degraded acceptance coverage for every add-on in the catalog.
- **FR-028**: System MUST support hot add-on installation after a Base-only deployment through a deploy/pre-migration lifecycle before user-facing gates open.
- **FR-029**: System MUST support temporary admin disablement of an installed add-on without dropping schema, deleting data or changing the deploy bundle.
- **FR-030**: System MUST distinguish selected, installed, schema_installed, runtime_ready, admin_enabled and effective_state for each add-on.
- **FR-031**: System MUST treat optional add-on database migrations as deploy/pre-migration owned; core runtime MUST NOT silently create optional add-on schema.
- **FR-032**: System MUST ensure every feature key, database object, seed row, API gate, UI gate, job gate and hook gate has exactly one owner: Base, one add-on, or one substrate.
- **FR-033**: System MUST keep plugin platform as an internal substrate, not as a user-facing add-on.
- **FR-034**: System MUST support feature-level dependencies, so one feature can be hidden or degraded without disabling the whole add-on.

#### P1 - Additional quality and maintainability

- **FR-017**: System SHOULD group optional rich-chat functions into clear commercial modules if they are not accepted as Base.
- **FR-018**: System SHOULD expose enough admin detail to explain why an add-on is disabled or degraded, including missing configuration and required capabilities.
- **FR-019**: System SHOULD provide a conformance report that detects catalog entries without tests, tests without catalog entries and functional surfaces without module classification.
- **FR-020**: System SHOULD align profile names, smoke names and module names so operators can reason about the same add-on across docs, deploy and testing.
- **FR-021**: System SHOULD keep public capabilities concise while allowing admin views to show operational details.
- **FR-022**: System SHOULD make disabled add-on behavior user-friendly and localized wherever user-facing messaging is required.

#### P2 - Optional enhancements

- **FR-023**: System MAY provide a visual product composer for selecting Base plus add-ons and viewing resulting functions, dependencies and operational notes.
- **FR-024**: System MAY provide an add-on dependency graph for presale, QA and operator review.
- **FR-025**: System MAY provide a generated customer-facing summary of what is included in a selected configuration.
- **FR-026**: System MAY provide sizing or cost deltas between Base-only and selected add-on configurations.
- **FR-027**: System MAY prepare future metering or billing hooks, but MUST NOT enforce billing in this feature.

### Key Entities *(include if feature involves data)*

- **Product Base**: The mandatory Korus Messenger capability set that is always present in a healthy deployment.
- **Add-on**: Optional capability group with label, lifecycle state, degradation behavior, required capabilities, operational limits and effective state.
- **Effective Add-on State**: Runtime result for an add-on: enabled, disabled or degraded, with a reason and user/admin visibility rules.
- **Degradation Behavior**: Expected behavior when an add-on is unavailable: hide, fallback, queue, reject or controlled unavailable state.
- **Functional Surface**: Any user-visible action, admin action, public capability, API operation, UI control or background behavior that belongs to Base or an add-on.
- **Configuration Profile**: A named selection of Base plus selected add-ons used by deployment, QA, docs and product presentation.
- **Conformance Report**: Evidence that catalog, capabilities, UI, functional surface, background behavior, deploy composition and tests agree.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of catalog add-ons have documented enabled, disabled and degraded behavior.
- **SC-002**: 100% of catalog add-ons have at least one positive acceptance check and one disabled/degraded acceptance check.
- **SC-003**: 100% of known non-Base functional surfaces are classified as an existing add-on, a new add-on or explicitly accepted Base behavior.
- **SC-004**: Base-only acceptance passes without requiring optional search, media, compliance, bot, integration, directory or E2EE add-ons.
- **SC-005**: Disabled add-ons expose no available user actions except explicitly documented fallback behavior.
- **SC-006**: Operator-facing module status explains the effective state and reason for every add-on in the catalog.
- **SC-007**: Full add-ons acceptance still passes after Base-only and selective add-on acceptance are introduced.
- **SC-008**: Product, QA and deploy documentation use the same add-on IDs and labels for every module in scope.

## Assumptions

- The existing Product Modules catalog remains the source of truth for Base and add-on metadata.
- Base is intentionally a minimal corporate messenger surface, not the full feature set.
- Optional production/live-server acceptance remains deferred outside this feature; repo-local and lab/QEMU acceptance are sufficient.
- External backend choice is handled by the external stack model and is not redefined here.
- Product is pre-release (`0.0.1-SNAPSHOT`), so existing non-released scaffolds, Flyway history and profile shims may be broken, renamed or rebuilt to reach a clean model.
- Explicit product add-on selection has priority over any temporary dev/deploy profile convenience.
- Billing, license enforcement and commercial metering are out of scope for this feature.

## Product Boundary

### Base Product

Base is the modern corporate messenger core. It MUST remain fully usable without optional add-ons.

Base includes:

- users, user profiles, organizations and organization routing;
- basic authentication, sessions and JWT validation;
- contacts and blocked users;
- chats, members, roles, channels, mute, folders and chat archive;
- messages: send, read, edit, delete, forward, versions, reactions, pins, threads and mentions;
- read receipts, typing indicators and voice message metadata;
- files, attachments, image resize and public file links;
- WebSocket/realtime delivery and presence;
- SQL search for users and messages;
- basic administration, health, readiness, metrics and product capabilities;
- basic conference link / mesh hook / Jitsi link;
- user locale, privacy settings, read receipt privacy, custom status and do-not-disturb.

Base MUST NOT require schemas, workers, external services or UI controls owned by add-ons.

### Add-ons

The product add-ons are:

- `addon-productivity` — "Продуктивность": polls, scheduled messages, reminders, stickers and GIF catalog.
- `addon-engage` — "Уведомления и превью": push notifications and link previews.
- `addon-search` — "Полнотекстовый поиск": full-text message search and indexing with SQL fallback.
- `addon-collaboration` — "Совместная работа": kanban and whiteboard.
- `addon-ai` — "AI и распознавание": AI assist, speech-to-text and captions.
- `addon-live` — "Групповые звонки и эфиры": SFU calls, broadcasts, ingress, DVR, moderation, recordings, guests, waiting room, breakout rooms and SIP/H.323.
- `addon-retention` — "Ретенция и TTL": retention policies, TTL, legal hold and purge.
- `addon-archive` — "Архив сообщений": archive database/worker and archive reads.
- `addon-deep-archive` — "Долгосрочное хранение": long-term snapshots and deep archive purge safety.
- `addon-export` — "Комплаенс-экспорт": export jobs, bundles and downloads.
- `addon-enterprise-auth` — "Enterprise-аутентификация": auth policies, LDAP/AD, SCIM, IP allowlist and passkeys.
- `addon-e2ee` — "Сквозное шифрование": E2EE/MLS.
- `addon-bots` — "Боты": Bot API, tokens, subscriptions and webhook delivery.
- `addon-integrations` — "Интеграции": integrations sidebar, marketplace, connectors and bridges.
- `addon-federation` — "Федерация": cross-organization trust, status, directory and member guard.
- `addon-dlp` — "DLP и контроль отправки": message/file preflight checks, policy and decision audit.
- `addon-migration-import` — "Миграция и импорт": administrative import jobs and processing.

### Substrates

Substrates are internal technical foundations, not user-facing product add-ons:

- `substrate-plugin-platform`: plugin presets, instances, policies and bridge invocation used by integrations, AI, STT and DLP.
- `substrate-product-modules`: product catalog, runtime state, install requests and capabilities.
- external stack model: backend component profiles, manifests and validation.

## Atomic Feature Ownership

Every feature key MUST be owned by exactly one owner.

### Base Feature Keys

- `identity.users` — Пользователи.
- `identity.user_profiles` — Профили пользователей.
- `identity.organizations` — Организации.
- `identity.organization_routing` — Маршрутизация по организации.
- `auth.sessions` — Сессии.
- `auth.jwt_validation` — Проверка JWT.
- `auth.basic_login` — Базовый вход.
- `contacts.list` — Список контактов.
- `contacts.add` — Добавление контакта.
- `contacts.remove` — Удаление контакта.
- `contacts.search` — Поиск пользователей.
- `blocks.user_block` — Блокировка пользователя.
- `blocks.user_unblock` — Разблокировка пользователя.
- `blocks.list` — Список блокировок.
- `chat.create` — Создание чата.
- `chat.list` — Список чатов.
- `chat.read` — Просмотр чата.
- `chat.update` — Обновление чата.
- `chat.members.list` — Список участников.
- `chat.members.add` — Добавление участника.
- `chat.members.remove` — Удаление участника.
- `chat.members.roles` — Роли участников.
- `chat.mute` — Отключение уведомлений чата.
- `chat.archive` — Архивирование чата.
- `chat.folders` — Папки чатов.
- `chat.channels` — Каналы.
- `chat.typing` — Индикатор набора текста.
- `message.send` — Отправка сообщения.
- `message.read` — Чтение сообщений.
- `message.edit` — Редактирование сообщения.
- `message.delete` — Удаление сообщения.
- `message.forward` — Пересылка сообщения.
- `message.versions` — Версии сообщения.
- `message.reactions` — Реакции.
- `message.pins` — Закрепления.
- `message.threads` — Треды.
- `message.mentions` — Упоминания.
- `message.read_receipts` — Статусы прочтения.
- `message.voice_metadata` — Метаданные голосового сообщения.
- `file.upload` — Загрузка файла.
- `file.download` — Скачивание файла.
- `file.metadata` — Метаданные файла.
- `file.message_attachment` — Вложение к сообщению.
- `file.image_resize` — Изменение размера изображения.
- `file.public_links` — Публичные ссылки на файлы.
- `file.public_link_auth` — Авторизованные ссылки на файлы.
- `realtime.websocket` — WebSocket.
- `realtime.message_events` — События сообщений.
- `realtime.typing_events` — События набора текста.
- `realtime.read_events` — События прочтения.
- `realtime.presence` — Присутствие.
- `search.sql.users` — SQL-поиск пользователей.
- `search.sql.messages` — SQL-поиск сообщений.
- `conference.basic_link` — Базовая ссылка на конференцию.
- `conference.mesh_hook` — Mesh hook.
- `conference.jitsi_link` — Jitsi-ссылка.
- `conference.participants_basic` — Базовый список участников.
- `settings.locale` — Язык пользователя.
- `settings.privacy` — Базовые настройки приватности.
- `settings.read_receipts_privacy` — Приватность статусов прочтения.
- `settings.custom_status` — Пользовательский статус.
- `settings.do_not_disturb` — Не беспокоить.
- `admin.basic` — Базовая админка.
- `admin.organizations` — Управление организациями.
- `admin.users` — Управление пользователями.
- `admin.product_modules_view` — Просмотр модулей продукта.
- `observability.health` — Health.
- `observability.ready` — Ready.
- `observability.metrics` — Метрики.
- `platform.capabilities` — Capabilities.
- `platform.external_stack_status` — Статус внешнего стека.

### Add-on Feature Keys

The catalog MUST include feature keys for every add-on capability listed below.

- `addon-productivity`: `productivity.polls.create`, `productivity.polls.list`, `productivity.polls.vote`, `productivity.polls.close`, `productivity.scheduled_messages.create`, `productivity.scheduled_messages.list`, `productivity.scheduled_messages.cancel`, `productivity.scheduled_messages.send_due`, `productivity.reminders.create`, `productivity.reminders.list`, `productivity.reminders.cancel`, `productivity.reminders.fire_due`, `productivity.stickers.packs_list`, `productivity.stickers.packs_create`, `productivity.stickers.use`, `productivity.gifs.search`, `productivity.gifs.use`.
- `addon-engage`: `engage.push.subscribe`, `engage.push.unsubscribe`, `engage.push.deliver`, `engage.push.device_state`, `engage.link_preview.extract`, `engage.link_preview.fetch`, `engage.link_preview.store`, `engage.link_preview.render`.
- `addon-search`: `search.fulltext.messages`, `search.fulltext.index`, `search.fulltext.reindex`, `search.fulltext.health`, `search.fulltext.fallback_sql`.
- `addon-collaboration`: `collaboration.kanban.list`, `collaboration.kanban.create`, `collaboration.kanban.update`, `collaboration.kanban.move`, `collaboration.kanban.delete`, `collaboration.whiteboard.open`, `collaboration.whiteboard.save`, `collaboration.whiteboard.snapshot`.
- `addon-ai`: `ai.assist.request`, `ai.assist.response`, `ai.assist.plugin_bridge`, `ai.speech_to_text.start`, `ai.speech_to_text.stop`, `ai.speech_to_text.transcript`, `ai.captions.start`, `ai.captions.view`, `ai.captions.append`.
- `addon-live`: `live.sfu_join`, `live.sfu_leave`, `live.room_token`, `live.session.create`, `live.session.list`, `live.session.end`, `live.broadcast.start`, `live.broadcast.stop`, `live.ingress.create`, `live.dvr.enable`, `live.dvr.disable`, `live.moderation.update`, `live.recordings.start`, `live.recordings.list`, `live.recordings.download`, `live.guest_links.create`, `live.guest_links.list`, `live.guest_links.redeem`, `live.waiting_room.admit`, `live.breakout_rooms.create`, `live.breakout_rooms.list`, `live.breakout_rooms.close`, `live.sip.status`, `live.sip.configure`.
- `addon-retention`: `retention.org_policy.read`, `retention.org_policy.update`, `retention.chat_policy.read`, `retention.chat_policy.update`, `retention.legal_hold.enable`, `retention.legal_hold.disable`, `retention.legal_hold.status`, `retention.purge.scan`, `retention.purge.execute`, `retention.purge.status`, `retention.worker.run`.
- `addon-archive`: `archive.message_archive`, `archive.message_read`, `archive.sync_state`, `archive.worker.run`, `archive.health`.
- `addon-deep-archive`: `deep_archive.snapshot_create`, `deep_archive.snapshot_store`, `deep_archive.snapshot_read`, `deep_archive.inventory_build`, `deep_archive.purge_after_snapshot`, `deep_archive.worker.run`.
- `addon-export`: `export.job.create`, `export.job.list`, `export.job.status`, `export.job.cancel`, `export.bundle.build`, `export.bundle.download`, `export.attachments.include`, `export.admin.suggest`, `export.worker.run`.
- `addon-enterprise-auth`: `enterprise.auth_policy.read`, `enterprise.auth_policy.update`, `enterprise.auth_policy.test`, `enterprise.directory_sync.status`, `enterprise.directory_sync.run`, `enterprise.scim_users.list`, `enterprise.scim_users.create`, `enterprise.scim_users.update`, `enterprise.scim_users.delete`, `enterprise.scim_groups.list`, `enterprise.scim_groups.create`, `enterprise.scim_groups.update`, `enterprise.scim_groups.delete`, `enterprise.ip_allowlist.read`, `enterprise.ip_allowlist.update`, `enterprise.passkeys.list`, `enterprise.passkeys.register`.
- `addon-e2ee`: `e2ee.key_packages.upload`, `e2ee.key_packages.list`, `e2ee.key_packages.delete`, `e2ee.session.create`, `e2ee.session.read`, `e2ee.message_encrypt`, `e2ee.message_decrypt`, `e2ee.admin_status`, `e2ee.migration_run`.
- `addon-bots`: `bots.create`, `bots.list`, `bots.read`, `bots.webhook.configure`, `bots.token.rotate`, `bots.chat_subscribe`, `bots.chat_unsubscribe`, `bots.send_message`, `bots.receive_updates`, `bots.delivery_worker`.
- `addon-integrations`: `integrations.sidebar.open`, `integrations.marketplace.list`, `integrations.marketplace.search`, `integrations.connection.create`, `integrations.connection.list`, `integrations.connection.delete`, `integrations.outbound.send`, `integrations.bridge.exchange`, `integrations.bridge.storage`, `integrations.bridge.one_c`.
- `addon-federation`: `federation.trust.create`, `federation.trust.list`, `federation.trust.delete`, `federation.status.read`, `federation.directory.read`, `federation.member_guard`.
- `addon-dlp`: `dlp.message_check`, `dlp.attachment_check`, `dlp.policy.read`, `dlp.policy.update`, `dlp.decision_audit`, `dlp.bridge.invoke`.
- `addon-migration-import`: `migration_import.job.create`, `migration_import.job.status`, `migration_import.job.process`, `migration_import.result.read`.

## Database Ownership

Every database table, column, index and seed row MUST have exactly one owner.

Base-owned objects are always present in Base-only schema. Add-on-owned objects are present only after the corresponding add-on migration bundle is installed. Substrate-owned objects are internal shared foundations and are not user-facing add-ons.

### Base-owned Database Objects

Base owns core identity, chat, message, file, conference, session, device, audit and product-module runtime objects. Base also owns core extension columns required by the approved Base boundary, including message threads, message mentions, voice metadata, chat folders/archive, channels, custom status, do-not-disturb, read receipt privacy and organization slug if used by base routing.

### Add-on-owned Database Objects

- `addon-productivity`: `chat_polls`, `chat_poll_votes`, `scheduled_messages`, `message_reminders`, `sticker_packs`, `stickers`, `gif_catalog_entries`.
- `addon-engage`: `device_push_subscriptions`, `message_link_previews`.
- `addon-search`: optional `search_index_state`, `search_reindex_jobs`, `search_backend_status`.
- `addon-collaboration`: `chat_kanban_tasks`, `chat_whiteboards`.
- `addon-ai`: `live_caption_sessions`, future AI assist/session/transcript objects, plus `ai-chat-gateway` and `stt-mock` seed rows in plugin substrate.
- `addon-live`: `live_sessions`, `live_session_viewers`, `live_session_moderation_events`, `call_recordings`, `conference_guest_links`, `conference_breakout_rooms`, `org_sip_gateway`.
- `addon-retention`: retention policies, chat retention policies, legal hold targets, purge runs and purge items.
- `addon-archive`: archive sync state, archive failures, archive pointers and archive database objects.
- `addon-deep-archive`: deep archive snapshots, object inventory and deep purge state.
- `addon-export`: export jobs, export job files, export audit and export suggested jobs.
- `addon-enterprise-auth`: auth policy, directory sync runs, SCIM groups, IP allowlist, passkey credentials and external user links.
- `addon-e2ee`: key packages, MLS sessions, MLS group state and MLS migration state.
- `addon-bots`: bots, bot tokens, bot chat subscriptions, bot webhook outbox and bot updates.
- `addon-integrations`: user integration connections, marketplace entries, connector install state and integration outbound state.
- `addon-federation`: federation trust and federation directory cache.
- `addon-dlp`: DLP policies, DLP decision audit and `dlp-mock` seed row in plugin substrate.
- `addon-migration-import`: migration import jobs and import artifact metadata.

### Substrate-owned Database Objects

- `substrate-plugin-platform`: plugin presets, plugin instances, organization plugin policies, plugin outbound events and plugin invocation audit.
- `substrate-product-modules`: product module runtime state, install requests, admin overrides and capabilities support data.

### Database Rules

- Base MUST NOT have foreign keys or required joins to add-on-owned objects.
- Add-on objects MAY reference Base objects.
- Add-on-to-add-on hard foreign keys SHOULD be avoided; feature-level dependencies or substrate references SHOULD be used instead.
- Seed rows MUST have owners even when their table is substrate-owned.
- Disabling an add-on MUST NOT drop tables, delete data or roll back migrations.

## Migration Model

Target migration layout:

```text
db/migration/base
db/migration/addons/productivity
db/migration/addons/engage
db/migration/addons/search
db/migration/addons/collaboration
db/migration/addons/ai
db/migration/addons/live
db/migration/addons/retention
db/migration/addons/archive
db/migration/addons/deep_archive
db/migration/addons/export
db/migration/addons/enterprise_auth
db/migration/addons/e2ee
db/migration/addons/bots
db/migration/addons/integrations
db/migration/addons/federation
db/migration/addons/dlp
db/migration/addons/migration_import
db/migration/substrate/plugin_platform
```

Each bundle MUST have its own history table, for example:

```text
flyway_schema_history
flyway_schema_history_addon_productivity
flyway_schema_history_addon_live
flyway_schema_history_substrate_plugin_platform
```

Deploy/pre-migration MUST apply Base migrations first, then selected substrates, then selected add-ons. Core runtime MUST validate schema readiness and report degraded/schema_missing or degraded/schema_contract_failed when required add-on schema is missing.

## Add-on Lifecycle

Public states:

- `enabled`
- `disabled`
- `degraded`
- `installing`

Internal state dimensions:

- `selected`
- `installed`
- `schema_installed`
- `runtime_ready`
- `admin_enabled`
- `effective_state`
- `reason`

Supported reasons include:

- `not_selected`
- `install_requested`
- `migration_running`
- `migration_failed`
- `schema_missing`
- `schema_contract_failed`
- `dependency_missing`
- `secrets_missing`
- `backend_unavailable`
- `worker_unavailable`
- `admin_override`
- `health_stale`

### Hot Installation

Hot installation MUST follow this lifecycle:

1. Administrator requests installation.
2. Runtime records install request.
3. Deploy/pre-migration applies schema bundles and substrate dependencies.
4. Deploy starts required services/workers.
5. Runtime validates schema, dependencies, secrets and health.
6. Gates open only after the add-on or feature becomes enabled.

During installation, user UI MUST hide the add-on feature and API MUST return a controlled installing/degraded response rather than SQL errors.

### Temporary Admin Disablement

Temporary disablement MUST:

- set effective state to disabled/admin_override;
- close API gates;
- hide or disable user UI controls;
- stop, pause, drain, drop or queue jobs according to add-on policy;
- preserve schema, configuration and data;
- allow fast re-enable without reinstallation when runtime is healthy.

## Gate Model

The catalog MUST define declarative gates. Manual hardcoded path-prefix gating is not sufficient.

Gate types:

- API gates: HTTP path, method, feature key and disabled/degraded/installing behavior.
- UI gates: feature key and behavior such as hide, disable, disable_with_tooltip, badge_degraded, readonly or fallback_badge.
- Job gates: scheduler/worker behavior such as run, stop, pause, drain, drop, queue or reject.
- Hook gates: application hooks such as message send preflight, response enrichment, indexing, retention purge, bot delivery and federation member guard.

UI and API SHOULD reason by feature key, not by local add-on knowledge. `/platform/capabilities` MUST expose feature-level state, owner, reason and UI behavior.

Feature-level dependencies MUST be supported. Example: live calls can be enabled while captions are hidden when `addon-ai` is disabled.

## Add-on Specific Behavior

### `addon-productivity`

- API surfaces: polls, scheduled messages, personal scheduled message list/cancel, reminders, stickers and GIFs.
- Jobs: scheduled message scheduler and reminder scheduler.
- Admin disabled behavior: API closed, UI hidden, schedulers paused, data retained.
- Catch-up policy: reminders become overdue after re-enable; scheduled messages are sent only within a configurable grace period, recommended 24 hours, otherwise marked expired or failed.

### `addon-engage`

- Push and link preview features have independent feature state.
- Missing VAPID secrets MUST degrade push features but MUST NOT disable link previews.
- Push events while disabled SHOULD be dropped.
- Link preview hooks while disabled SHOULD be skipped.
- Existing preview data MUST be retained during admin disablement.
- Hot installation MUST NOT automatically backfill old links; backfill is an explicit admin action.

### `addon-search`

- Base SQL search remains available in all states.
- Full-text search disabled/degraded/installing MUST fall back to SQL.
- Hot installation MUST NOT automatically run full reindex unless explicitly configured.
- Admin reindex is a separate operation.
- Search index data MUST be retained during admin disablement.

### `addon-collaboration`

- Owns kanban and whiteboard only.
- Requires no workers and no external backend.
- Serves as the simple add-on template: API + UI + DB with no required cross-add-on dependency.

### `addon-ai`

- Owns AI assist, speech-to-text and captions.
- Requires `substrate-plugin-platform`.
- AI assist and captions MUST have independent feature-level health.
- Captions in live UI require both `addon-ai` and relevant live/conference context.

### `addon-live`

- Owns SFU, broadcasts, ingress, DVR, moderation, recordings, guest links, waiting room, breakout rooms and SIP/H.323.
- Does not require `addon-ai`; captions are feature-level integration with `addon-ai`.
- Admin disablement SHOULD drain active sessions before full disablement, with a recommended grace period of 15 minutes.

### `addon-retention`

- Dangerous purge operations MUST pause when schema, dependency, archive, deep archive, legal hold or object storage safety checks fail.
- Policy read/write MAY remain available in degraded state when safe.
- Hot installation SHOULD start in monitor-only mode until admin enables purge.

### `addon-archive`

- Archive is a safe dependency for retention policies that require archive before purge.
- Archive backfill after hot installation MUST be explicit, not automatic.
- If archive is unavailable, retention purge requiring archive MUST pause.

### `addon-deep-archive`

- Deep archive snapshots protect purge flows that require long-term snapshots.
- If deep archive is unavailable, purge requiring snapshot MUST pause.

### `addon-export`

- Owns export jobs, bundles and downloads.
- Admin disablement SHOULD stop accepting new jobs and drain or pause active jobs by policy.

### `addon-enterprise-auth`

- Base login MUST work without enterprise auth tables.
- Enterprise policies, LDAP/AD, SCIM, IP allowlist and passkeys are hidden/disabled when add-on is disabled.

### `addon-e2ee`

- Base messaging MUST work without E2EE.
- E2EE data MUST be retained during admin disablement.
- If E2EE is disabled after encrypted data exists, encrypted content MAY be shown as unavailable/encrypted until re-enabled.

### `addon-bots`

- Bot API is separate from Base message send.
- Bot-originated messages MAY use Base message send after Bot API authorization.
- Tokens, subscriptions and outbox data MUST be retained during admin disablement.

### `addon-integrations`

- Uses plugin substrate but does not own it.
- Bridges/connectors are user-facing integration features.
- Other add-ons may use plugin substrate without enabling user-facing integrations.

### `addon-federation`

- Federation guard MUST NOT break same-organization Base membership.
- Cross-organization trust/directory/status belong to federation.

### `addon-dlp`

- Base send path MUST work without DLP.
- When enabled, DLP policy controls fail-open/fail-closed behavior.
- Enterprise default SHOULD be fail-closed; dev/lab may explicitly use fail-open.

### `addon-migration-import`

- Migration import is an administrative add-on.
- Base admin MUST work without import schema.

## Catalog v2 Requirements

Catalog v2 MUST be the single source of truth for:

- Base features;
- add-ons and labels in Russian;
- substrates;
- feature keys and owners;
- migration bundles and schema contracts;
- API/UI/job/hook gates;
- required and optional dependencies;
- deploy services, environment variables and secrets;
- acceptance coverage metadata.

Runtime, deploy, docs and deck MUST use the same source or verified generated artifacts. Divergent independent catalogs are not allowed.

## Acceptance Matrix

The feature MUST define acceptance coverage for:

- Base-only: only Base schema/functions are available, add-on tables are absent, add-on UI/API/jobs/hooks are disabled safely.
- Each add-on positive path: selected, migrated, runtime healthy and feature usable.
- Each add-on disabled path: not selected and admin disabled both close user-facing surfaces without data deletion.
- Each add-on degraded path: schema missing, dependency missing, secret missing, backend unavailable or worker unavailable are reported without Base failures.
- Hot installation: Base-only deployment gains a selected add-on after deploy/pre-migration and gates open only after readiness.
- Re-enable after admin disablement: data remains and features return.
- All-addons regression: full product still passes after modularization.
- Catalog conformance: every feature, DB object, seed row, gate and acceptance item has an owner.

## Scope Tiers

### P0 - Main Work

- Final Base/Add-ons/substrate taxonomy.
- Catalog v2 as the single source of truth.
- Atomic feature ownership.
- Database ownership and optional migration bundle model.
- Hot installation and temporary admin disablement lifecycle.
- Declarative API/UI/job/hook gates.
- Feature-level dependencies and capabilities.
- Base-only and per-add-on acceptance matrix.

### P1 - Additional Work

- Conformance report across catalog, UI, functional surface, background behavior, deploy and tests.
- Improved admin diagnostics for missing configuration and degraded dependencies.
- Naming alignment across product modules, smoke profiles, deploy profiles and documentation.
- Generated customer-facing product composition summary.

### P2 - Non-essential Nice-to-haves

- Visual product composer.
- Add-on dependency graph.
- Sizing/cost deltas by selected add-ons.
- Future metering hooks without billing enforcement.

## Out of Scope

- Stage/prod/live-server validation and human sign-off ceremonies.
- Production cutover to alternative external backends.
- Billing, licensing or entitlement enforcement.
- Runtime hot-plug without restart unless already supported by existing behavior.
- Rewriting unrelated messenger features outside module conformance.

## Implementation Closure

P0/P1 scope is implemented repo-locally through catalog v2, runtime lifecycle state, declarative gates, feature-level capabilities, admin diagnostics, smoke profile naming alignment and focused tests. Live-server/stage/prod validation remains out of scope by project rule; lab/QEMU smoke can be run separately when runtime evidence beyond repo-local checks is needed.
