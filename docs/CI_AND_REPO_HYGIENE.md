# CI и гигиена репозитория

## GitHub Actions

Файл **`.github/workflows/ci.yml`**:

| Элемент | Описание |
|--------|-----------|
| **Триггеры** | Push в ветки **`main`**, **`master`**, **`develop`**; все **pull request**; ручной запуск (**`workflow_dispatch`**) |
| **ОС** | **`ubuntu-latest`** |
| **JDK** | **25** (Temurin), кэш Gradle через **`setup-java@v5`** (`**cache: gradle**`) |
| **Проверка wrapper** | **`gradle/actions/wrapper-validation@v5`** — контроль целостности **`gradle-wrapper.jar`** (файл **должен** быть в репозитории; не игнорировать **`*.jar`** для `gradle/wrapper/`, см. **`!gradle/wrapper/gradle-wrapper.jar`** в **`.gitignore`**) |
| **Checkout** | **`actions/checkout@v6`** (рантайм экшенов на Node.js **24**, см. [changelog GitHub Actions](https://github.blog/changelog/)) |
| **Команда** | **`chmod +x gradlew && ./gradlew buildIntegrity --no-daemon`** — корневая задача **`buildIntegrity`** вызывает **`build`** у всех subprojects (компиляция, тесты, **`jar`**) |
| **Права** | **`permissions: contents: read`** у job |
| **Concurrency** | Одна активная сборка на ветку; новый запуск отменяет предыдущий (**`cancel-in-progress: true`**) |

Локально тот же контур: **`./gradlew buildIntegrity`** (или **`.\gradlew.bat buildIntegrity`** на Windows). Для только тестов без сборки артефактов: **`./gradlew test`**.

**JDK в корневом `gradle.properties`:** не задавайте **`org.gradle.java.home`** с путём одной ОС в репозитории — на **Linux** (GitHub Actions) такой путь сломает любой **`./gradlew`**. Локально укажите JDK в **`JAVA_HOME`**, в **`~/.gradle/gradle.properties`** или в настройках IDE.

Локаль **`core-api`** (тексты **`ApiError`** и подписи параметров UUID): **`app.locale`** в **`application.properties`** (по умолчанию **`ru`**) или переменная окружения **`APP_LOCALE`** (**`en`**, **`en-US`** и т.д.).

## Dependabot

Файл **`.github/dependabot.yml`**:

- Экосистема **Gradle** (`**directory: /**`): проверка зависимостей **раз в неделю**, до **10** открытых PR.
- Экосистема **GitHub Actions**: обновления экшенов **раз в месяц**.

## Smoke scripts policy

- Канонический индекс smoke-сценариев: **`scripts/SMOKE_INDEX.md`**.
- Для CI по умолчанию использовать `.sh` скрипты; `.ps1` / `.cmd` считаются совместимыми обертками для Windows.
- Удаление smoke-скриптов допустимо только после проверки ссылок в workflow и документации.

## `.gitattributes`

- **`gradlew`** — текст с окончаниями строк **LF** (на Linux CI не должно быть «**bad interpreter**» из‑за CRLF).
- **`gradlew.bat`** — **CRLF**.
- Остальные файлы — **`* text=auto`**.

После добавления атрибутов при необходимости один раз нормализуйте **`gradlew`** в индексе git (**`git add --renormalize gradlew`**) на машине разработчика.

## Eclipse (Buildship) и classpath

Проект собирается **Gradle**, язык **Java 25**. Ошибки вроде **`The import com.fasterxml cannot be resolved`** или **`The import java.nio cannot be resolved`** в Eclipse почти всегда означают, что IDE **не видит classpath Gradle** или подключён **не JDK 25**.

| Шаг | Действие |
|-----|----------|
| Плагин | Установить **Buildship Gradle Integration** (в дистрибутивах *Enterprise Java* обычно уже есть). |
| Импорт | **File → Import → Gradle → Existing Gradle Project** — каталог **корня** репозитория (где **`settings.gradle.kts`** и **`gradlew`**), не отдельный **`modules/...`** как сырой Java-проект. |
| JDK | **Window → Preferences → Java → Installed JREs** — добавить **JDK 25** (не «обрезанный» JRE без `java.compiler`/`jmods`). В **Java → Compiler** для workspace или проекта — **25**. |
| Обновление classpath | ПКМ по корневому Gradle-проекту → **Gradle → Refresh Gradle Project** (после смены ветки или **`build.gradle.kts`**). |
| «Unbound» JRE | **Project Properties → Java Build Path → Libraries** — убрать битую **JRE System Library**, при необходимости **Gradle → Refresh** заново. |

Если **`./gradlew buildIntegrity`** из терминала проходит, а Eclipse ругается на импорты — проблема в настройке Eclipse/Buildship, а не в зависимостях репозитория.

## Связанные документы

- Готовность стенда и ручные смоки: **`scripts/TEST_SERVER_READY.md`**
- Индекс smoke-сценариев: **`scripts/SMOKE_INDEX.md`**
- Дорожная карта эпиков: **`docs/ROADMAP_EPICS.md`**
- Схема БД и миграции: **`docs/db/FLYWAY_AND_SCHEMA.md`**
