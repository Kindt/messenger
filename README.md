# Korus Messenger (AvandocMsg)

Корпоративный мессенджер: чаты, файлы, звонки, push, E2EE, ретенция и экспорт для комплаенса, админ-консоль, мультитенантность (организации).

**Версия:** `0.0.1-SNAPSHOT` — рабочая болванка для доработок, не релиз.  
**Статус:** рабочий прототип на лабораторном стенде; промышленная эксплуатация не заявлена.

## Стек

Java 25 · Gradle · Tomcat + Jersey (не Spring Boot) · PostgreSQL · NATS · Redis · Solr · MinIO · Keycloak · vanilla JS webui · Docker Compose · Ansible

## Структура

```
modules/          core-api, web-client, ws-gateway, workers/*, common
deploy/           ansible, qemu (Windows dev), two-host
docker/           compose-профили (dev-min, full-server, …)
tests/e2e-web/    Playwright
scripts/          smoke, presentation deck, QEMU-оркестрация
specs/            spec-kit: фичи, контракты, tasks
```

## Быстрый старт

**Сборка и тесты (CI gate):**

```powershell
.\gradlew.bat buildIntegrity
```

**Dev на Windows** — runtime только в QEMU (две Ubuntu VM), не Docker на хосте:

```powershell
.\scripts\qemu-dev-mode.ps1 -Mode warm      # поднять стек
.\scripts\qemu-dev-mode.ps1 -Mode status    # проверка
```

| Сервис | URL на хосте |
|--------|----------------|
| API | http://127.0.0.1:18080 |
| Web UI | http://127.0.0.1:19088 |

Подробнее: [`deploy/qemu/README.md`](deploy/qemu/README.md), профили стендов: [`docs/DEV_STACK_PROFILES.md`](docs/DEV_STACK_PROFILES.md).

**Linux / CI / prod-like:** Docker Compose + Ansible — [`deploy/ansible/DEPLOY_QUICKSTART.md`](deploy/ansible/DEPLOY_QUICKSTART.md).

## Презентация продукта

Self-contained deck для заказчика и команды:

- Локально: [`docs/index.html`](docs/index.html)
- GitHub Pages: https://kindt.github.io/messenger/
- Пересборка: `python scripts/presentation/build.py` — см. [`scripts/presentation/README.md`](scripts/presentation/README.md)

## Документация

| | |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Обзор для разработчиков и AI-агентов |
| [`docs/README.md`](docs/README.md) | Индекс документации (deck, deploy, specs) |
| [`CHANGELOG.md`](CHANGELOG.md) | Журнал изменений |
| [`docs/ROADMAP_EPICS.md`](docs/ROADMAP_EPICS.md) | Дорожная карта |
| [`docs/ARCHITECTURE_CORE_PACKAGES.md`](docs/ARCHITECTURE_CORE_PACKAGES.md) | Hexagonal-слои core-api |
| [`tests/e2e-web/README.md`](tests/e2e-web/README.md) | Playwright inner/outer gate |
