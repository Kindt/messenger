# Korus Messenger (AvandocMsg)

Корпоративный мессенджер: чаты, файлы, звонки, push, E2EE, ретенция и экспорт для комплаенса, админ-консоль, мультитенантность (организации).

**Версия:** `0.0.1-SNAPSHOT` — рабочая болванка для доработок, не релиз.  
**Статус:** рабочий прототип; промышленная эксплуатация не заявлена.

## Стек

Java 25 · Gradle · Tomcat + Jersey (не Spring Boot) · PostgreSQL · NATS · Redis · Solr · MinIO · Keycloak · vanilla JS webui · Docker Compose · Ansible

## Структура

```
modules/          core-api, web-client, ws-gateway, workers/*, common
deploy/           ansible, two-host
docker/           compose-профили (dev-min, full-server, …)
tests/e2e-web/    Playwright
scripts/          smoke, presentation deck
specs/            spec-kit: фичи, контракты, tasks
```

## Быстрый старт

**Сборка и тесты (CI gate):**

```powershell
.\gradlew.bat buildIntegrity
```

**Live stack (Linux / VM / stage):** Docker Compose + Ansible — [`deploy/ansible/DEPLOY_QUICKSTART.md`](deploy/ansible/DEPLOY_QUICKSTART.md).  
Профили стендов: [`docs/DEV_STACK_PROFILES.md`](docs/DEV_STACK_PROFILES.md).

**Playwright E2E:** [`tests/e2e-web/README.md`](tests/e2e-web/README.md) — нужен поднятый API/UI (env `PLAYWRIGHT_BASE_URL`, `KORUS_API_URL`).

> Локальный QEMU-стек Windows (`deploy/qemu/`, `scripts/qemu-*.ps1`) **не в репозитории** — только в `.gitignore`, может оставаться на машине разработчика.

## Презентация продукта

- Локально: [`docs/index.html`](docs/index.html)
- GitHub Pages: https://kindt.github.io/messenger/
- Пересборка: `python scripts/presentation/build.py` — [`scripts/presentation/README.md`](scripts/presentation/README.md)

## Документация

| | |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Обзор для разработчиков и AI-агентов |
| [`docs/README.md`](docs/README.md) | Индекс документации |
| [`CHANGELOG.md`](CHANGELOG.md) | Журнал изменений |
| [`docs/ROADMAP_EPICS.md`](docs/ROADMAP_EPICS.md) | Дорожная карта |
| [`docs/ARCHITECTURE_CORE_PACKAGES.md`](docs/ARCHITECTURE_CORE_PACKAGES.md) | Hexagonal-слои core-api |
| [`tests/e2e-web/README.md`](tests/e2e-web/README.md) | Playwright |
