# CI и гигиена репозитория

## GitHub Actions

Файл **`.github/workflows/ci.yml`**:

| Элемент | Описание |
|--------|-----------|
| **Триггеры** | Push в ветки **`main`**, **`master`**, **`develop`**; все **pull request**; ручной запуск (**`workflow_dispatch`**) |
| **ОС** | **`ubuntu-latest`** |
| **JDK** | **25** (Temurin), кэш Gradle через **`setup-java`** (`**cache: gradle**`) |
| **Проверка wrapper** | **`gradle/actions/wrapper-validation@v4`** — контроль целостности **`gradle-wrapper.jar`** |
| **Команда** | **`chmod +x gradlew && ./gradlew buildIntegrity --no-daemon`** — корневая задача **`buildIntegrity`** вызывает **`build`** у всех subprojects (компиляция, тесты, **`jar`**) |
| **Права** | **`permissions: contents: read`** у job |
| **Concurrency** | Одна активная сборка на ветку; новый запуск отменяет предыдущий (**`cancel-in-progress: true`**) |

Локально тот же контур: **`./gradlew buildIntegrity`** (или **`.\gradlew.bat buildIntegrity`** на Windows). Для только тестов без сборки артефактов: **`./gradlew test`**.

Локаль **`core-api`** (тексты **`ApiError`** и подписи параметров UUID): **`app.locale`** в **`application.properties`** (по умолчанию **`ru`**) или переменная окружения **`APP_LOCALE`** (**`en`**, **`en-US`** и т.д.).

## Dependabot

Файл **`.github/dependabot.yml`**:

- Экосистема **Gradle** (`**directory: /**`): проверка зависимостей **раз в неделю**, до **10** открытых PR.
- Экосистема **GitHub Actions**: обновления экшенов **раз в месяц**.

## `.gitattributes`

- **`gradlew`** — текст с окончаниями строк **LF** (на Linux CI не должно быть «**bad interpreter**» из‑за CRLF).
- **`gradlew.bat`** — **CRLF**.
- Остальные файлы — **`* text=auto`**.

После добавления атрибутов при необходимости один раз нормализуйте **`gradlew`** в индексе git (**`git add --renormalize gradlew`**) на машине разработчика.

## Связанные документы

- Готовность стенда и ручные смоки: **`scripts/TEST_SERVER_READY.md`**
- Дорожная карта эпиков: **`docs/ROADMAP_EPICS.md`**
- Схема БД и миграции: **`docs/db/FLYWAY_AND_SCHEMA.md`**
