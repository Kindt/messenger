#!/usr/bin/env python3
"""Build large Korus Messenger user guides as Markdown, HTML and PDF.

The generator intentionally keeps the content data-driven:
- product modules are read from the runtime catalog;
- every generated guide contains a coverage matrix;
- PDF pages are created directly with PyMuPDF to avoid external browser deps.
"""

from __future__ import annotations

import argparse
import html
import re
import sys
import textwrap
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Iterable

import fitz
import yaml
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs"
IMAGES = DOCS / "images" / "guides"
THUMBS = IMAGES / "thumbs"
CATALOG_PATH = ROOT / "modules" / "core-api" / "src" / "main" / "resources" / "product-modules.yaml"
TODAY = date.today().isoformat()
PRODUCT_VERSION = "0.0.1-SNAPSHOT"


@dataclass(frozen=True)
class Chapter:
    title: str
    goal: str
    scenarios: tuple[str, ...]
    settings: tuple[str, ...]
    checks: tuple[str, ...]
    depth_notes: tuple[str, ...] = ()


@dataclass(frozen=True)
class Guide:
    key: str
    title: str
    audience: str
    md_name: str
    pdf_name: str
    html_name: str
    quick_start: tuple[str, ...]
    chapters: tuple[Chapter, ...]
    screenshots: tuple[str, ...]


DEMO = {
    "orgs": ["Korus Demo", "Northwind филиал", "External Partner"],
    "users": [
        "Анна Смирнова — обычный пользователь",
        "Иван Петров — администратор чата",
        "Мария Волкова — владелец чата",
        "Сергей Орлов — администратор приложения",
        "Ольга Ким — инфраструктурный администратор",
        "HR FAQ Bot — бот для примеров",
    ],
    "chats": [
        "Проект Альфа",
        "HR объявления",
        "Поддержка сотрудников",
        "Внешний проект с партнёром",
        "Личные заметки",
    ],
    "files": ["policy-demo.pdf", "meeting-notes.txt", "diagram.png", "export-sample.zip"],
}


COMMON_TERMS = [
    ("Base", "Функция входит в базовую поставку и доступна при исправном сервере."),
    ("Add-on", "Дополнительный модуль. Его можно включить, отключить или получить состояние degraded."),
    ("Disabled", "Модуль отключён. Интерфейс скрывает действие или API возвращает контролируемый отказ."),
    ("Degraded", "Модуль включён, но работает ограниченно из-за зависимости, секрета или сервиса."),
    ("Ограниченная реализация", "Функция есть как заготовка или инженерный контур; это явно отмечается в тексте."),
]


def user_chapters() -> tuple[Chapter, ...]:
    return (
        Chapter(
            "Вход, профиль и первые настройки",
            "Помочь пользователю безопасно начать работу и понять, какие функции доступны в его организации.",
            (
                "Открыть web UI, войти в свою учётную запись и проверить имя профиля.",
                "Выбрать язык интерфейса, статус присутствия и режим «Не беспокоить».",
                "Понять, почему у коллег может отличаться набор кнопок и пунктов меню.",
            ),
            (
                "Язык интерфейса, тема, статус, read receipts privacy и DND относятся к персональным настройкам.",
                "Если сессия истекла, пользователь повторно входит через штатную страницу авторизации.",
                "Набор функций зависит от Base/add-ons и политик организации.",
            ),
            (
                "После входа виден список чатов и профиль пользователя.",
                "Изменение языка сохраняется и применяется без ручной правки конфигурации.",
                "Недоступная функция объясняется сообщением интерфейса, а не скрытой ошибкой.",
            ),
        ),
        Chapter(
            "Контакты, поиск людей и блокировки",
            "Объяснить, как находить коллег, управлять контактами и защищать своё пространство.",
            (
                "Найти Анну или Ивана через поиск пользователей.",
                "Добавить коллегу в контакты и удалить контакт, когда общение завершено.",
                "Заблокировать пользователя и понять, что блокировка меняет видимость и поиск.",
            ),
            (
                "Контакты и блокировки входят в Base.",
                "Блокировка убирает контактную связь и ограничивает пары в поиске.",
                "Импорт по phone hashes зависит от того, как организация настроила идентификаторы.",
            ),
            (
                "Поиск возвращает только допустимые результаты.",
                "Заблокированный пользователь не появляется как обычный контакт.",
                "Разблокировка возвращает возможность повторного контакта по правилам организации.",
            ),
        ),
        Chapter(
            "Чаты, группы, каналы и роли",
            "Показать повседневную работу с диалогами, группами и каналами.",
            (
                "Создать чат «Проект Альфа» и добавить участников.",
                "Назначить Ивана администратором чата, а Марию оставить владельцем.",
                "Перенести чат в папку work, отключить уведомления и затем архивировать чат.",
            ),
            (
                "Участник читает и пишет, администратор помогает управлять чатом, владелец меняет критичные параметры.",
                "Каналы подходят для объявлений, группы — для обсуждений, личные чаты — для прямого общения.",
                "Mute, archive и folders являются пользовательскими способами организовать рабочий поток.",
            ),
            (
                "Участники видят чат после добавления.",
                "Роль отображается в списке участников.",
                "Архивированный чат не мешает основному списку, но остаётся доступным.",
            ),
        ),
        Chapter(
            "Сообщения, реакции, треды и упоминания",
            "Разобрать все основные действия с сообщениями без администраторских терминов.",
            (
                "Отправить сообщение в «Проект Альфа», добавить реакцию и ответить в тред.",
                "Упомянуть Марию через @mention, закрепить важное сообщение и переслать его в другой чат.",
                "Отредактировать своё сообщение, посмотреть версию и удалить сообщение при ошибке.",
            ),
            (
                "Редактировать и удалять своё сообщение может автор; правила для чужих сообщений задаются ролью чата.",
                "Треды помогают не смешивать обсуждения, упоминания привлекают внимание конкретного участника.",
                "Read receipts и typing indicators показывают состояние общения, если приватность их не скрывает.",
            ),
            (
                "После отправки сообщение видно в чате и realtime-события доставляются участникам.",
                "Удаление и редактирование отображаются как состояние сообщения, а не как исчезнувший контекст.",
                "Закрепление доступно пользователям с нужной ролью.",
            ),
        ),
        Chapter(
            "Файлы, изображения и публичные ссылки",
            "Научить безопасно обмениваться файлами и понимать риски ссылок.",
            (
                "Прикрепить `policy-demo.pdf` к сообщению.",
                "Скачать вложение и открыть метаданные файла.",
                "Создать публичную ссылку, ссылку с авторизацией или паролем, затем отозвать её.",
            ),
            (
                "Файлы, вложения, metadata, image resize и public links входят в Base.",
                "Ссылки могут быть открытыми, авторизованными или защищёнными паролем.",
                "Владелец файла отвечает за отзыв ссылок, если доступ больше не нужен.",
            ),
            (
                "Файл загружается и появляется в сообщении.",
                "Ссылка открывается только в ожидаемом режиме доступа.",
                "После отзыва ссылка больше не даёт доступ к файлу.",
            ),
        ),
        Chapter(
            "Поиск, присутствие и realtime",
            "Показать, как пользователь находит информацию и понимает состояние коллег.",
            (
                "Найти сообщение по слову из обсуждения.",
                "Найти пользователя в организации.",
                "Понять статусы online, away, dnd и offline.",
            ),
            (
                "SQL-поиск входит в Base.",
                "Полнотекстовый поиск является add-on и может работать через fallback.",
                "Presence и realtime зависят от WebSocket-соединения и состояния клиента.",
            ),
            (
                "Поиск возвращает ожидаемые сообщения или показывает ограничение режима.",
                "Статус коллеги обновляется без ручной перезагрузки страницы.",
                "Если realtime недоступен, пользователь может обновить список вручную.",
            ),
        ),
        Chapter(
            "Конференции, звонки и эфиры",
            "Разделить базовые конференции и расширенные live-возможности.",
            (
                "Создать ссылку на конференцию из чата.",
                "Присоединиться к встрече, выйти и завершить встречу при наличии прав.",
                "Понять, когда доступны записи, гости, waiting room, breakout rooms и SIP.",
            ),
            (
                "Базовые conference link/Jitsi/mesh hooks входят в Base.",
                "SFU, broadcast, ingress, DVR, recordings и guest links относятся к `addon-live`.",
                "LiveKit URL/key/secret и media/TURN настраивает инфраструктурный администратор.",
            ),
            (
                "Кнопка конференции видна в чате, если функция доступна.",
                "Расширенные live-кнопки скрыты или показывают controlled unavailable state при недоступном add-on.",
                "Гость попадает в waiting room только при включённом соответствующем сценарии.",
            ),
        ),
        Chapter(
            "Продуктивность и совместная работа",
            "Собрать пользовательские add-ons, которые расширяют рабочий чат.",
            (
                "Создать опрос, проголосовать и закрыть его.",
                "Запланировать сообщение и отменить его до отправки.",
                "Создать напоминание, использовать стикер/GIF, открыть kanban или whiteboard.",
            ),
            (
                "Опросы, scheduled messages, reminders, stickers и GIF входят в `addon-productivity`.",
                "Kanban и whiteboard входят в `addon-collaboration`.",
                "Если модуль отключён, пользователь не должен видеть рабочую кнопку как доступную.",
            ),
            (
                "Опрос виден участникам чата.",
                "Запланированное сообщение не отправляется раньше срока.",
                "При disabled add-on интерфейс скрывает действие или объясняет недоступность.",
            ),
        ),
        Chapter(
            "Уведомления, PWA и link previews",
            "Объяснить настройки браузерного приложения и уведомлений.",
            (
                "Установить web UI как PWA.",
                "Разрешить push-уведомления в браузере.",
                "Понять, когда ссылка в сообщении получает preview.",
            ),
            (
                "Push и link previews относятся к `addon-engage`.",
                "Для push нужны разрешение браузера и корректная VAPID-конфигурация.",
                "Offline cache помогает открыть оболочку, но не заменяет серверную доставку сообщений.",
            ),
            (
                "Настройка push видна только при доступной capability.",
                "Браузер показывает запрос разрешения.",
                "При missing secrets администратор видит degraded причину, а пользователь — недоступность.",
            ),
        ),
        Chapter(
            "Шифрование, DLP, федерация и корпоративные ограничения",
            "Показать видимые пользователю эффекты сложных корпоративных функций.",
            (
                "Понять, что означает E2EE/MLS-индикатор в чате.",
                "Увидеть, как DLP может остановить сообщение или вложение.",
                "Понять, когда federation позволяет работать с доверенным партнёром.",
            ),
            (
                "E2EE/MLS в текущей версии имеет честные ограничения и не должен описываться как завершённая production MLS-реализация.",
                "DLP-политики настраивает администратор приложения; пользователь видит только результат проверки.",
                "Federation trust задаёт администратор приложения, а серверные зависимости проверяет инфраструктура.",
            ),
            (
                "Пользователь получает понятное сообщение о блокировке DLP.",
                "Функции внешнего партнёра видны только при наличии доверия.",
                "E2EE-статус не обещает больше, чем реально включено в capabilities.",
            ),
        ),
        Chapter(
            "Боты, интеграции и marketplace",
            "Объяснить пользователю, как работают боты и подключаемые интеграции.",
            (
                "Создать или подключить своего бота, если add-on доступен.",
                "Настроить webhook или обновить token с учётом прав владельца бота.",
                "Открыть marketplace, найти интеграцию, подключить и отключить connection.",
            ),
            (
                "Bots и integrations являются add-ons.",
                "Bot token даёт технический доступ и должен храниться как секрет.",
                "Marketplace, bridge exchange/storage и 1C bridge зависят от интеграционного контура.",
            ),
            (
                "Бот отправляет сообщение только в подписанный чат.",
                "Отключенная интеграция не должна оставлять активные пользовательские действия.",
                "При degraded endpoint пользователь видит controlled unavailable state.",
            ),
        ),
        Chapter(
            "Частые проблемы и куда обращаться",
            "Дать пользователю понятную диагностику без серверных команд.",
            (
                "Функция пропала из интерфейса.",
                "Нет прав на действие в чате.",
                "Сессия истекла, файл не открывается или ссылка отозвана.",
            ),
            (
                "Если функция не видна, сначала проверить свою роль и организационную политику.",
                "Если модуль отключён или degraded, нужен администратор приложения.",
                "Если есть серверный сбой, администратор приложения передаст проблему инфраструктуре.",
            ),
            (
                "Пользователь может описать шаги, экран и сообщение ошибки.",
                "Нет необходимости передавать пароль или token в поддержку.",
                "Скриншот ошибки не должен содержать приватные сообщения.",
            ),
        ),
    )


def admin_chapters() -> tuple[Chapter, ...]:
    return (
        Chapter(
            "Роль администратора приложения и вход в консоль",
            "Определить границы ответственности администратора приложения.",
            (
                "Войти в `/admin/` и проверить текущую сессию.",
                "Понять realm role `admin` и текущий `org_id`.",
                "Выйти из консоли и очистить сессию на общем рабочем месте.",
            ),
            (
                "Администратор приложения управляет организациями, пользователями, политиками и модулями.",
                "Серверные secrets, Docker/Ansible и storage обслуживает инфраструктурная роль.",
                "Токен администратора нельзя вставлять в документы и скриншоты.",
            ),
            (
                "Session panel показывает пользователя и роли.",
                "После logout токен исчезает из sessionStorage.",
                "Недостаточные права не дают открыть защищённые admin endpoints.",
            ),
        ),
        Chapter(
            "Организации, пользователи и жизненный цикл доступа",
            "Научить создавать организации и сопровождать пользователей.",
            (
                "Создать `Korus Demo` и проверить список организаций.",
                "Назначить Анну в организацию и проверить аудит.",
                "Описать offboarding: деактивация, смена организации, проверка контактов и прав.",
            ),
            (
                "Удаление организации допустимо только если она не используется.",
                "Назначение пользователя влияет на видимость, routing и политики.",
                "Onboarding должен завершаться проверкой входа и доступных capabilities.",
            ),
            (
                "Audit содержит `organization.create` и `user.organization.set`.",
                "Пользователь видит только функции своей организации.",
                "Ошибки назначения не скрываются как успешная операция.",
            ),
        ),
        Chapter(
            "Auth policy, providers, directory sync и SCIM",
            "Покрыть enterprise auth без инфраструктурных деталей.",
            (
                "Настроить local login/self-registration.",
                "Задать providers JSON и выполнить provider test.",
                "Запустить directory sync, создать SCIM user/group и понять delete semantics.",
            ),
            (
                "Enterprise auth является add-on и зависит от политики организации.",
                "SCIM может использовать admin JWT или SCIM bearer token.",
                "LDAP bind secret не должен попадать в UI-документацию.",
            ),
            (
                "Provider test возвращает понятный результат.",
                "Directory sync показывает latest status.",
                "SCIM delete деактивирует пользователя по принятой модели, а не обязательно удаляет историю.",
            ),
        ),
        Chapter(
            "Product Modules и capabilities",
            "Сделать каталог модулей операционным инструментом администратора приложения.",
            (
                "Открыть Product Modules и прочитать effective state.",
                "Временно отключить add-on без удаления данных.",
                "Понять selected, installed, schema_installed, runtime_ready и admin_enabled.",
            ),
            (
                "Base всегда обязателен, add-ons могут быть disabled/degraded/installing.",
                "Missing secrets должен отображаться как degraded/disabled reason.",
                "Public capabilities показывают короткую картину, admin view — операционные детали.",
            ),
            (
                "UI/API скрывают disabled controls.",
                "Admin override меняет effective state.",
                "Install requests и optional migrations не выполняются молча в пользовательском runtime.",
            ),
        ),
        Chapter(
            "Безопасность организации: IP allowlist, passkeys и federation trust",
            "Описать политики, влияющие на доступ и доверенные контуры.",
            (
                "Настроить IP allowlist и объяснить enforcement flag.",
                "Просмотреть и зарегистрировать passkeys там, где это включено.",
                "Создать federation trust с `External Partner` и проверить federation status.",
            ),
            (
                "IP allowlist ограничивает доступ по сетевым правилам организации.",
                "Passkeys являются частью enterprise auth и зависят от политики входа.",
                "Federation trust должен быть осознанным действием с понятным риском.",
            ),
            (
                "Список CIDR сохраняется и отображается.",
                "Passkey flow не раскрывает секреты в UI.",
                "Без trust внешний участник не проходит member guard.",
            ),
        ),
        Chapter(
            "Экспорт, аудит, retention, legal hold и purge",
            "Собрать compliance-процедуры администратора приложения.",
            (
                "Открыть compliance guide и prep checklist.",
                "Создать export job, проверить status, cancel и download.",
                "Настроить retention policy, включить legal hold и проверить purge status.",
            ),
            (
                "Export и retention являются add-ons с отдельными workers и storage-зависимостями.",
                "Legal hold защищает данные от удаления в рамках политики.",
                "Audit filters помогают ответить кто, когда и что изменил.",
            ),
            (
                "Export bundle содержит ожидаемые файлы и attachments manifest.",
                "Purge не должен обходить legal hold.",
                "Audit cookbook помогает расследовать действия администратора.",
            ),
        ),
        Chapter(
            "E2EE/MLS и migration status",
            "Честно описать криптографический статус для администратора приложения.",
            (
                "Открыть E2EE status и посмотреть pending migrations.",
                "Запустить batch migration только после проверки ограничений.",
                "Понять различие legacy, KMLS wire и OpenMLS wire profile.",
            ),
            (
                "Текущий статус `0.0.1-SNAPSHOT` не является обещанием полной production RFC 9420 реализации.",
                "Migrate-batch влияет на существующие чаты и требует контроля результата.",
                "Инфраструктурные runtime constraints описываются в отдельном руководстве.",
            ),
            (
                "Admin status показывает group count и pending migrations.",
                "Ошибки миграции видны до объявления успеха.",
                "Пользователям показываются только честные capabilities.",
            ),
        ),
        Chapter(
            "Плагины, L0 FAQ bot, bots и integrations",
            "Показать администраторскую сторону расширений и ботов.",
            (
                "Создать `HR FAQ Bot` через L0 wizard.",
                "Настроить plugin preset/instance/org policy.",
                "Проверить marketplace, user connections, outbound configure и test invoke.",
            ),
            (
                "Plugin platform является substrate; user-facing возможности идут через add-ons.",
                "Bot token и outbound endpoints являются секретными данными.",
                "Integrations bridge может быть degraded при недоступном endpoint.",
            ),
            (
                "L0 bot отвечает на slash commands.",
                "Test invoke возвращает проверяемый результат.",
                "Policy объясняет, какие интеграции доступны организации.",
            ),
        ),
        Chapter(
            "DLP, migration import и advanced live controls",
            "Добавить специализированные админские add-ons.",
            (
                "Настроить DLP policy и проверить decision audit.",
                "Создать migration import job, проверить status, process и result.",
                "Настроить guest links, waiting room, breakout rooms, recordings и SIP status/configure.",
            ),
            (
                "DLP блокирует или разрешает отправку сообщений/вложений по policy.",
                "Migration import зависит от DB/object storage и должен иметь понятный результат.",
                "Advanced live controls доступны только при включённом `addon-live`.",
            ),
            (
                "Пользователь получает объяснимую DLP-блокировку.",
                "Import job не теряется между create/status/process.",
                "Live controls не показываются как доступные при disabled/degraded add-on.",
            ),
        ),
        Chapter(
            "Инциденты, диагностика и handoff инфраструктуре",
            "Дать администратору приложения cookbook без серверных runbook-деталей.",
            (
                "Пользователь скомпрометирован: ограничить доступ и проверить audit.",
                "Функция degraded: прочитать Product Modules reason и передать инфраструктуре.",
                "Нужно сохранить данные: включить legal hold и проверить scope.",
            ),
            (
                "Администратор приложения фиксирует бизнес-симптом, affected org/user/chat и UI/API evidence.",
                "Секреты и серверные логи не запрашиваются у пользователя.",
                "Если причина missing secret или service health, задача передаётся инфраструктуре.",
            ),
            (
                "Инцидент содержит воспроизводимые шаги и timestamp.",
                "Audit cookbook отвечает на ключевые вопросы расследования.",
                "Handoff не содержит пароли и bearer tokens.",
            ),
        ),
    )


def infra_chapters() -> tuple[Chapter, ...]:
    return (
        Chapter(
            "Роль инфраструктурного администратора и карта окружений",
            "Определить, что значит обслуживать сервер Korus Messenger.",
            (
                "Разделить dev, test, stage и prod по назначению.",
                "Понять, где находятся server, web, IdP, DB, cache, messaging и object storage.",
                "Определить границы между администратором приложения и инфраструктурой.",
            ),
            (
                "Инфраструктура отвечает за deploy, env/vault, health, smokes, backup и upgrade.",
                "Пользовательские сценарии не должны превращаться в серверные инструкции.",
                "Стенд для документации описывается как обычный сервер Korus Messenger.",
            ),
            (
                "Для каждого окружения есть URL API, web, admin, health и metrics.",
                "Ответственный знает, какие данные нельзя публиковать в документах.",
                "Runbook не требует host Docker на Windows.",
            ),
        ),
        Chapter(
            "Архитектура компонентов и сетевые потоки",
            "Собрать карту server/web и внешних зависимостей.",
            (
                "Нарисовать поток web -> API -> DB/Redis/NATS/MinIO/Keycloak.",
                "Разобрать ws-gateway, worker-message-pipeline и web edge.",
                "Понять, какие add-ons добавляют Solr, LiveKit, workers или external endpoints.",
            ),
            (
                "Base использует Postgres, Redis, NATS, MinIO, Keycloak, core-api, ws-gateway и web-lb.",
                "External stack profiles описывают желаемые компоненты и compatibility.",
                "Порты и CORS/origins должны быть согласованы между web и API.",
            ),
            (
                "Health/ready показывают состояние основных зависимостей.",
                "Web UI использует правильный API upstream.",
                "Metrics помогают отличить сетевую ошибку от деградации сервиса.",
            ),
        ),
        Chapter(
            "Deploy single-host и two-host",
            "Описать серверную установку и обновление без внутренних лабораторных терминов.",
            (
                "Выполнить single-host deploy через Ansible inventory local.",
                "Подготовить two-host inventory для server и web.",
                "Запустить smoke после deploy и сохранить результат.",
            ),
            (
                "Single-host подходит для CI/малого стенда.",
                "Two-host разделяет server и web, поэтому критичны CORS/origins и upstream URL.",
                "Runtime команды выполняются в Linux/CI/серверном контуре.",
            ),
            (
                "API health отвечает 200.",
                "Web health отвечает 200.",
                "Smoke tags завершаются без критичных ошибок.",
            ),
        ),
        Chapter(
            "Конфигурация: env, vault, TLS, CORS и product add-ons",
            "Сделать настройки воспроизводимыми и безопасными.",
            (
                "Настроить `group_vars`, server/web env templates и vault placeholders.",
                "Выбрать Base-only, Standard-like или Enterprise-like набор add-ons.",
                "Настроить TLS, CORS/origins и публичные URL.",
            ),
            (
                "Секреты не попадают в PDF и скриншоты.",
                "Legacy deploy profile является convenience shim; продуктовая модель — Base + add-ons.",
                "TLS и CORS должны соответствовать реальному URL стенда.",
            ),
            (
                "Product Modules показывает ожидаемый набор selected add-ons.",
                "Missing secrets отражаются как degraded/disabled reasons.",
                "Web UI работает с настроенным API origin.",
            ),
        ),
        Chapter(
            "Health, readiness, metrics, logs и smoke checks",
            "Дать ежедневный observability runbook.",
            (
                "Проверить API health, web health, ready и metrics.",
                "Запустить smoke-ready, smoke-deploy-acceptance и web smoke.",
                "Собрать симптомы деградации DB, Redis, NATS, MinIO и IdP.",
            ),
            (
                "Health отвечает на вопрос «процесс жив», readiness — «можно обслуживать запросы».",
                "Smoke checks подтверждают пользовательский поток, а не только порт.",
                "Логи и metrics не должны публиковать secrets.",
            ),
            (
                "Smoke output имеет exit code 0.",
                "Metrics доступны оператору.",
                "Ошибки dependency health не скрываются как успешный deploy.",
            ),
        ),
        Chapter(
            "Add-on infrastructure details",
            "Разложить add-ons по сервисам, worker-ам, секретам и health checks.",
            (
                "Проверить `addon-search`: Solr, ZK, indexer и SQL fallback.",
                "Проверить `addon-live`: LiveKit URL/key/secret, media/TURN, room join и recordings.",
                "Проверить `addon-ai`, integrations, federation, DLP и migration import.",
            ),
            (
                "Каждый add-on имеет degradation mode, runtime dependencies и acceptance checks.",
                "Workers должны pause/drop/drain/stop согласно catalog policy.",
                "Optional migrations выполняются через deploy/pre-migration lifecycle, а не молча в runtime.",
            ),
            (
                "Product Modules effective state совпадает с реальным состоянием сервисов.",
                "Degraded reason объясняет missing secret или unavailable health check.",
                "Disabled add-on не запускает пользовательские операции.",
            ),
        ),
        Chapter(
            "Compliance storage: export, retention, archive и deep archive",
            "Показать, какие инфраструктурные гарантии нужны для хранения и удаления данных.",
            (
                "Проверить export worker, object storage и export queue.",
                "Проверить retention worker, purge safety и legal hold protection.",
                "Проверить archive DB, archiver worker, snapshot store и inventory.",
            ),
            (
                "Export bundle и attachments требуют object storage и срок хранения artifacts.",
                "Retention не должен удалять данные под legal hold.",
                "Archive/deep archive добавляют отдельные storage и worker-контуры.",
            ),
            (
                "Export smoke создаёт и скачивает ожидаемый bundle.",
                "Purge safety не пропускает защищённые данные.",
                "Archive health показывает состояние archive DB/snapshot store.",
            ),
        ),
        Chapter(
            "Backup, restore, upgrade и rollback",
            "Собрать жизненный цикл изменения серверного стенда.",
            (
                "Сделать backup DB, object storage, env/vault и критичных configs.",
                "Выполнить upgrade с миграциями и smoke после deploy.",
                "Подготовить rollback-подход и список данных, которые нельзя потерять.",
            ),
            (
                "Backup считается рабочим только после проверенного restore или restore rehearsal.",
                "Upgrade не завершается без smoke и проверки Product Modules.",
                "Rollback plan должен учитывать миграции схемы и object storage.",
            ),
            (
                "После restore health/ready и пользовательские smokes проходят.",
                "Версия приложения и схема согласованы.",
                "Нет скрытых pending migrations.",
            ),
        ),
        Chapter(
            "Troubleshooting по симптомам",
            "Дать оператору быстрые деревья решений.",
            (
                "Web открыт, но API недоступен.",
                "Логин не работает из-за IdP или CORS.",
                "Файлы не грузятся, поиск ушёл в fallback или add-on degraded.",
            ),
            (
                "Начинать с symptoms -> health -> ready -> logs -> metrics -> smoke reproduction.",
                "Не просить у пользователя пароль или token.",
                "Если проблема пользовательской роли, вернуть задачу администратору приложения.",
            ),
            (
                "Для каждого симптома есть команда проверки и expected result.",
                "Ошибки классифицированы по DB/Redis/NATS/MinIO/IdP/web upstream/CORS/secrets.",
                "Документ показывает, куда эскалировать проблему.",
            ),
        ),
        Chapter(
            "Security hardening и disaster recovery",
            "Закрыть эксплуатационные риски перед передачей стенда.",
            (
                "Проверить TLS, firewall, CORS/origins и least privilege.",
                "Проверить vault и ротацию секретов.",
                "Подготовить порядок действий при падении DB, object storage, IdP, web или API.",
            ),
            (
                "Служебные endpoints не публикуются наружу без необходимости.",
                "Secrets не хранятся в markdown, PDF, скриншотах и логах.",
                "Disaster recovery checklist должен быть коротким и исполнимым под стрессом.",
            ),
            (
                "Hardening checklist закрыт перед передачей стенда.",
                "DR checklist содержит владельца действия, команду проверки и критерий восстановления.",
                "После аварии выполняется post-incident review.",
            ),
        ),
    )


GUIDES = (
    Guide(
        "user",
        "Korus Messenger. Руководство пользователя",
        "Пользователи и роли внутри чатов, кроме администраторов приложения",
        "USER_GUIDE.md",
        "USER_GUIDE.pdf",
        "USER_GUIDE.html",
        (
            "Войти в web UI и проверить профиль.",
            "Найти коллегу и создать чат.",
            "Отправить сообщение, файл и публичную ссылку.",
            "Настроить уведомления, приватность и язык.",
            "Понять, какие функции зависят от add-ons.",
        ),
        user_chapters(),
        (
            "user-login.png",
            "user-chat-list.png",
            "user-chat-thread.png",
            "user-profile-settings.png",
            "user-file-link.png",
            "user-capabilities.png",
        ),
    ),
    Guide(
        "admin",
        "Korus Messenger. Руководство администратора приложения",
        "Администраторы приложения, управляющие организациями, пользователями, политиками и модулями",
        "APP_ADMIN_GUIDE.md",
        "APP_ADMIN_GUIDE.pdf",
        "APP_ADMIN_GUIDE.html",
        (
            "Войти в admin console и проверить session.",
            "Создать организацию и назначить пользователя.",
            "Проверить Product Modules и degraded reasons.",
            "Настроить auth policy, SCIM, retention и export.",
            "Передать инфраструктуре только серверные симптомы.",
        ),
        admin_chapters(),
        (
            "app-admin-login.png",
            "app-admin-session.png",
            "app-admin-organizations.png",
            "app-admin-product-modules.png",
            "app-admin-retention.png",
            "app-admin-plugins.png",
        ),
    ),
    Guide(
        "infra",
        "Korus Messenger. Руководство администратора инфраструктуры",
        "Инфраструктурные администраторы, отвечающие за сервер, deploy, конфигурацию, секреты и проверки",
        "INFRA_ADMIN_GUIDE.md",
        "INFRA_ADMIN_GUIDE.pdf",
        "INFRA_ADMIN_GUIDE.html",
        (
            "Проверить API/web health и readiness.",
            "Выполнить deploy и smoke проверки.",
            "Настроить env/vault, TLS, CORS и add-ons.",
            "Проверить workers, storage и external stack.",
            "Подготовить backup, upgrade, rollback и DR checklist.",
        ),
        infra_chapters(),
        (
            "infra-server-health.png",
            "infra-web-health.png",
            "infra-admin-stats.png",
            "infra-external-stack.png",
            "infra-product-modules.png",
            "infra-smoke-result.png",
        ),
    ),
)


def load_catalog() -> dict:
    with CATALOG_PATH.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def catalog_feature_rows(catalog: dict) -> list[dict]:
    rows: list[dict] = []
    for feature in catalog["base"]["features"]:
        rows.append({"module": "Base", **feature})
    for addon in catalog["addons"]:
        for feature in addon.get("features", []):
            rows.append({"module": addon["id"], "module_label": addon["label"], **feature})
    return rows


def addon_summary(catalog: dict) -> list[dict]:
    rows = []
    for addon in catalog["addons"]:
        runtime = addon.get("runtime", {})
        rows.append(
            {
                "id": addon["id"],
                "label": addon["label"],
                "degradation": addon.get("degradation_mode", ""),
                "disabled": addon.get("disabled_behavior", ""),
                "degraded": addon.get("degraded_behavior", ""),
                "services": ", ".join(runtime.get("services", [])) or "нет",
                "workers": ", ".join(runtime.get("workers", [])) or "нет",
                "secrets": ", ".join(runtime.get("required_secrets", [])) or "нет",
                "health": ", ".join(runtime.get("health_checks", [])) or "нет",
            }
        )
    return rows


def md_table(headers: list[str], rows: Iterable[Iterable[str]]) -> str:
    out = ["| " + " | ".join(headers) + " |", "| " + " | ".join(["---"] * len(headers)) + " |"]
    for row in rows:
        out.append("| " + " | ".join(str(x).replace("\n", " ") for x in row) + " |")
    return "\n".join(out)


def scenario_block(chapter: Chapter, index: int) -> str:
    steps = "\n".join(f"{i}. {step}" for i, step in enumerate(chapter.scenarios, 1))
    settings = "\n".join(f"- {item}" for item in chapter.settings)
    checks = "\n".join(f"- {item}" for item in chapter.checks)
    notes = "\n".join(f"- {item}" for item in chapter.depth_notes)
    if notes:
        notes = "\n\n**Связанные ограничения**\n\n" + notes
    return f"""
### {index}. Практический сценарий

**Цель:** {chapter.goal}

**Кто выполняет:** читатель этого руководства в своей роли.

**Что нужно заранее:** доступ к серверу Korus Messenger, рабочая учётная запись и включённые функции, которые описаны в сценарии.

**Шаги**

{steps}

**Настройки и тонкости**

{settings}

**Проверка результата**

{checks}{notes}
"""


def shared_intro(guide: Guide) -> str:
    demo_rows = [(k, ", ".join(v)) for k, v in DEMO.items()]
    terms = md_table(["Термин", "Значение"], COMMON_TERMS)
    return f"""# {guide.title}

**Версия продукта:** `{PRODUCT_VERSION}`

**Версия документа:** `1.0-draft`

**Дата сборки:** `{TODAY}`

**Аудитория:** {guide.audience}

Это руководство написано простым языком и описывает работу с сервером Korus Messenger. Документ использует единый демонстрационный набор данных, чтобы примеры, скриншоты и сценарии были согласованы между руководствами.

## Как читать руководство

- Раздел «Быстрый старт» помогает выполнить первые действия без чтения всего документа.
- Разделы с практическими сценариями показывают действия по шагам.
- Справочные блоки объясняют настройки, ограничения, роли и проверки.
- Если функция помечена как add-on, она доступна только при включённом модуле.
- Если функция помечена как ограниченная реализация, документ честно описывает текущие границы версии `{PRODUCT_VERSION}`.

## Быстрый старт

{chr(10).join(f"- {item}" for item in guide.quick_start)}

## Единые обозначения

{terms}

## Демонстрационные данные

{md_table(["Тип", "Значения"], demo_rows)}

## История изменений документа

{md_table(["Дата", "Версия", "Изменение"], [(TODAY, "1.0-draft", "Первая локальная сборка руководства")])}
"""


def coverage_matrix(catalog: dict, guide_key: str) -> str:
    base_rows = catalog["base"]["features"]
    addons = addon_summary(catalog)
    if guide_key == "user":
        depth = {
            "Base": "Deep для пользовательских сценариев",
            "addon-productivity": "Deep/Medium",
            "addon-collaboration": "Medium",
            "addon-engage": "Medium",
            "addon-search": "Medium",
            "addon-live": "Medium",
            "addon-e2ee": "Medium с ограничениями",
            "addon-bots": "Medium",
            "addon-integrations": "Medium",
        }
        default = "Mention как видимый эффект"
    elif guide_key == "admin":
        depth = {
            "Base": "Deep для basic admin",
            "addon-enterprise-auth": "Deep",
            "addon-export": "Deep",
            "addon-retention": "Deep",
            "addon-e2ee": "Deep для статуса/миграций",
            "addon-bots": "Deep",
            "addon-integrations": "Deep",
            "addon-federation": "Deep",
            "addon-dlp": "Deep",
            "addon-migration-import": "Deep",
        }
        default = "Medium для управления модулем"
    else:
        depth = {
            "Base": "Deep для инфраструктуры",
            "addon-search": "Deep",
            "addon-live": "Deep",
            "addon-retention": "Deep",
            "addon-archive": "Deep",
            "addon-deep-archive": "Deep",
            "addon-export": "Deep",
            "addon-integrations": "Deep",
            "addon-ai": "Deep",
        }
        default = "Medium для dependencies/health"
    rows = [("Base", "Базовая поставка", len(base_rows), depth.get("Base", default), "Всегда доступна при исправном сервере")]
    for addon in addons:
        rows.append(
            (
                addon["id"],
                addon["label"],
                sum(1 for f in catalog_feature_rows(catalog) if f.get("module") == addon["id"]),
                depth.get(addon["id"], default),
                f"disabled={addon['disabled']}; degraded={addon['degraded']}",
            )
        )
    return "## Матрица покрытия функций\n\n" + md_table(
        ["Модуль", "Название", "Функций", "Глубина в этом руководстве", "Поведение"],
        rows,
    )


def screenshot_section(guide: Guide) -> str:
    rows = []
    blocks = ["## Список скриншотов\n"]
    for name in guide.screenshots:
        path = IMAGES / name
        status = "снят" if path.exists() else "ожидает съёмки"
        rows.append((name, status, f"`docs/images/guides/{name}`"))
        if path.exists():
            blocks.append(f"\n![{name}](images/guides/{name})\n\n*Скриншот: {name}.*\n")
    blocks.insert(1, md_table(["Файл", "Статус", "Путь"], rows))
    return "\n".join(blocks)


def chapter_md(chapter: Chapter, chapter_no: int, guide: Guide) -> str:
    out = [f"# Глава {chapter_no}. {chapter.title}\n\n{chapter.goal}\n"]
    out.append(scenario_block(chapter, chapter_no))
    out.append("\n### Справочник раздела\n")
    for i, item in enumerate(chapter.settings, 1):
        out.append(f"\n#### {chapter_no}.{i}. {item}\n\n")
        out.append(
            "Этот пункт описан с точки зрения роли читателя. Если действие требует другой роли, рядом указано, к кому обращаться. "
            "Все примеры используют демонстрационные данные и не содержат реальных секретов или персональных данных.\n\n"
        )
        out.append("**Пример использования**\n\n")
        out.append(
            f"- В демонстрационном контуре используется организация `{DEMO['orgs'][0]}` и чат `{DEMO['chats'][0]}`.\n"
            "- Если функция недоступна, проверьте права, состояние модуля и сообщение интерфейса.\n"
            "- Для add-on функций дополнительно проверяется состояние capabilities или Product Modules.\n"
        )
        out.append("\n**Типовые ошибки**\n\n")
        out.append(
            "- Действие выполняет пользователь без нужной роли.\n"
            "- Модуль отключён или находится в degraded-состоянии.\n"
            "- Настройка организации отличается от примера.\n"
        )
        out.append("\n**Готово, если**\n\n")
        for check in chapter.checks:
            out.append(f"- {check}\n")
    return "".join(out)


def build_markdown(guide: Guide, catalog: dict) -> str:
    parts = [shared_intro(guide), screenshot_section(guide), coverage_matrix(catalog, guide.key)]
    parts.append("\n## Сводка add-ons\n\n")
    parts.append(
        md_table(
            ["Add-on", "Название", "Сервисы", "Workers", "Secrets", "Health"],
            ((a["id"], a["label"], a["services"], a["workers"], a["secrets"], a["health"]) for a in addon_summary(catalog)),
        )
    )
    for idx, chapter in enumerate(guide.chapters, 1):
        parts.append(chapter_md(chapter, idx, guide))
    parts.append(detailed_feature_reference(guide, catalog))
    parts.append(appendix(guide, catalog))
    return "\n\n".join(parts).strip() + "\n"


def detailed_feature_reference(guide: Guide, catalog: dict) -> str:
    """Generate substantive role-specific content for every catalog feature.

    This section is intentionally verbose: it is the main body that turns the
    guides into real manuals instead of a short outline.
    """
    blocks: list[str] = [
        "# Подробный справочник возможностей\n\n"
        "Этот раздел раскрывает каждую возможность каталога с точки зрения аудитории руководства. "
        "Он не заменяет практические главы выше, а дополняет их: здесь можно быстро найти конкретную "
        "функцию, понять её роль, ограничения, типовые ошибки и способ проверки.\n"
    ]
    blocks.append(module_reference_block(guide, "Base", "Базовая поставка", catalog["base"].get("features", []), catalog["base"]))
    for addon in catalog.get("addons", []):
        blocks.append(module_reference_block(guide, addon["id"], addon.get("label", addon["id"]), addon.get("features", []), addon))
    blocks.append(product_profile_reference(guide, catalog))
    return "\n\n".join(blocks)


def module_reference_block(guide: Guide, module_id: str, module_label: str, features: list[dict], module: dict) -> str:
    runtime = module.get("runtime", {})
    services = ", ".join(runtime.get("services", [])) or "нет отдельных сервисов"
    workers = ", ".join(runtime.get("workers", [])) or "нет отдельных workers"
    secrets = ", ".join(runtime.get("required_secrets", [])) or "нет обязательных секретов"
    health = ", ".join(runtime.get("health_checks", [])) or "нет отдельных health checks"
    behavior = []
    if module_id != "Base":
        behavior.extend(
            [
                f"degradation_mode: `{module.get('degradation_mode', 'не задан')}`",
                f"disabled_behavior: `{module.get('disabled_behavior', 'не задан')}`",
                f"degraded_behavior: `{module.get('degraded_behavior', 'не задан')}`",
                f"ui_behavior: `{module.get('ui_behavior', 'не задан')}`",
            ]
        )
    else:
        behavior.append("Base включён всегда, если сервер и обязательные компоненты исправны.")

    out = [
        f"# Модуль: {module_label} ({module_id})\n",
        f"Модуль содержит {len(features)} функций. В этом руководстве он рассматривается с точки зрения роли: **{guide.audience}**.\n",
        "## Операционная карточка модуля\n",
        f"- Сервисы: {services}.",
        f"- Workers: {workers}.",
        f"- Секреты и параметры доступа: {secrets}.",
        f"- Health checks: {health}.",
    ]
    out.extend(f"- {item}." for item in behavior)
    out.append("\n## Функции модуля\n")
    for idx, feature in enumerate(features, 1):
        out.append(feature_card(guide, module_id, module_label, feature, idx, module))
    return "\n".join(out)


def screenshot_for_feature(guide: Guide, module_id: str, key: str, ui_behavior: str) -> str | None:
    """Return the closest real UI screenshot for a feature, when it has a visual surface."""
    if ui_behavior == "internal":
        return None

    if guide.key == "user":
        if key.startswith(("auth.", "identity.")):
            return "user-login.png"
        if key.startswith("settings."):
            return "user-profile-settings.png"
        if key.startswith(("contacts.", "blocks.")):
            return "user-chat-list.png"
        if key.startswith("chat."):
            return "user-chat-list.png"
        if key.startswith(("message.", "realtime.")):
            return "user-chat-thread.png"
        if key.startswith("file."):
            return "user-file-link.png"
        if key.startswith(("search.", "platform.")):
            return "user-capabilities.png"
        if key.startswith(("conference.", "live.")):
            return "user-chat-thread.png"
        if module_id in {"addon-productivity", "addon-collaboration", "addon-engage"}:
            return "user-chat-thread.png" if module_id != "addon-engage" else "user-profile-settings.png"
        if module_id in {"addon-e2ee", "addon-bots", "addon-integrations", "addon-federation", "addon-dlp", "addon-migration-import", "addon-archive", "addon-deep-archive", "addon-export", "addon-retention", "addon-ai", "addon-live", "addon-enterprise-auth"}:
            return "user-capabilities.png"
        return "user-chat-thread.png"

    if guide.key == "admin":
        if key.startswith("admin.product_modules") or module_id in {"addon-productivity", "addon-collaboration", "addon-engage", "addon-search", "addon-live", "addon-ai"}:
            return "app-admin-product-modules.png"
        if key.startswith(("admin.organizations", "admin.users", "identity.", "enterprise.auth_policy", "enterprise.directory_sync", "enterprise.scim", "enterprise.ip_allowlist", "enterprise.passkeys", "federation.")):
            return "app-admin-organizations.png"
        if module_id in {"addon-retention", "addon-export", "addon-archive", "addon-deep-archive"} or key.startswith(("retention.", "export.", "archive.", "deep_archive.")):
            return "app-admin-retention.png"
        if module_id in {"addon-bots", "addon-integrations"} or key.startswith(("bots.", "integrations.", "substrate.plugin")):
            return "app-admin-plugins.png"
        if module_id in {"addon-e2ee", "addon-dlp", "addon-migration-import"} or key.startswith(("e2ee.", "dlp.", "migration_import.")):
            return "app-admin-product-modules.png"
        if key.startswith(("observability.", "platform.external_stack")):
            return "infra-admin-stats.png"
        return "app-admin-session.png"

    if key.startswith(("observability.health", "observability.ready")):
        return "infra-server-health.png"
    if key.startswith("platform.external_stack"):
        return "infra-external-stack.png"
    if key.startswith(("admin.product_modules", "platform.capabilities")):
        return "infra-product-modules.png"
    if key.startswith(("observability.", "admin.server_stats", "admin.fleet_stats")):
        return "infra-admin-stats.png"
    return None


def evidence_markdown(guide: Guide, module_id: str, key: str, label: str, ui_behavior: str, module: dict) -> str:
    shot = screenshot_for_feature(guide, module_id, key, ui_behavior)
    if not shot:
        if guide.key == "infra":
            return infra_command_verification_markdown(module_id, key, label, module)
        return (
            "\n**Скриншот функции.** Не требуется: функция помечена как внутренняя "
            "и не предполагает отдельного пользовательского экрана. Её состояние проверяется через связанные "
            "разделы статуса, Product Modules, health checks или audit.\n"
        )
    path = IMAGES / shot
    if not path.exists():
        return (
            f"\n**Скриншот функции.** Ожидается файл `docs/images/guides/{shot}`. "
            "До вставки в финальный PDF скриншот должен быть снят с рабочего интерфейса.\n"
        )
    rel_path = ensure_thumbnail(path)
    if guide.key == "infra":
        return (
            "\n**Визуальная проверка результата**\n\n"
            f"![{label} — {key}]({rel_path})\n\n"
            f"*Экран для функции `{key}`. Скриншот нужен здесь, потому что у функции есть визуальная проверка: "
            "страница состояния, Product Modules, внешний стек или административная статистика.*\n"
        )
    return (
        "\n**Скриншот функции**\n\n"
        f"![{label} — {key}]({rel_path})\n\n"
        f"*Экран для функции `{key}`. Скриншот расположен непосредственно в описании функции и показывает ближайшую "
        "реальную UI-поверхность, через которую пользователь или администратор видит эту возможность.*\n"
    )


def infra_command_verification_markdown(module_id: str, key: str, label: str, module: dict) -> str:
    runtime = module.get("runtime", {})
    services = runtime.get("services", [])
    workers = runtime.get("workers", [])
    health_checks = runtime.get("health_checks", [])
    service = services[0] if services else "korus-core-api"
    worker = workers[0] if workers else None
    health = health_checks[0] if health_checks else "/api/v1/health"
    commands = [
        f"curl -fsS http://<server>:8080{health}",
        f"curl -fsS http://<server>:8080/api/v1/product-modules | findstr /i \"{module_id}\"",
        f"docker compose ps {service}",
    ]
    if worker:
        commands.append(f"docker compose ps {worker}")
    expected = (
        f"`{health}` отвечает без 5xx; каталог Product Modules содержит `{module_id}`; "
        f"сервис `{service}` находится в состоянии `running` или `healthy`."
    )
    if worker:
        expected += f" Worker `{worker}` также запущен и не перезапускается циклически."
    return (
        "\n**Проверка без скриншота**\n\n"
        f"Для функции `{key}` отдельный экран не обязателен: это серверная возможность, dependency или режим поставки. "
        "В руководстве по развертыванию рядом с такой функцией фиксируются команда, ожидаемый вывод и критерий успеха.\n\n"
        "Команды проверки:\n\n"
        "```powershell\n"
        + "\n".join(commands)
        + "\n```\n\n"
        f"Ожидаемый вывод: {expected}\n\n"
        f"Критерий успеха: после развертывания функция «{label}» не переводит модуль в degraded/disabled, "
        "а пользовательская или административная поверхность не показывает ошибку из-за отсутствующей инфраструктурной зависимости.\n"
    )


def ensure_thumbnail(path: Path) -> str:
    """Create a compact JPEG thumbnail for repeated inline use in PDFs."""
    THUMBS.mkdir(parents=True, exist_ok=True)
    out = THUMBS / (path.stem + ".jpg")
    if not out.exists() or out.stat().st_mtime < path.stat().st_mtime:
        with Image.open(path) as im:
            im = im.convert("RGB")
            im.thumbnail((900, 620))
            im.save(out, "JPEG", quality=62, optimize=True)
    return f"images/guides/thumbs/{out.name}"


def feature_card(guide: Guide, module_id: str, module_label: str, feature: dict, idx: int, module: dict) -> str:
    key = feature.get("key", f"{module_id}.{idx}")
    label = feature.get("label", key)
    ui_behavior = feature.get("ui_behavior", "не задан")
    owner = feature.get("owner", module_id)
    api_behavior = feature.get("api_behavior", {})
    api_mode = api_behavior.get("mode") if isinstance(api_behavior, dict) else None
    demo_user = DEMO["users"][0].split(" — ")[0]
    demo_admin = DEMO["users"][3].split(" — ")[0]
    demo_infra = DEMO["users"][4].split(" — ")[0]
    demo_chat = DEMO["chats"][0]
    demo_org = DEMO["orgs"][0]

    if guide.key == "user":
        role = (
            f"Для пользователя функция `{key}` означает конкретное действие или видимое состояние в интерфейсе. "
            f"В демонстрационном сценарии {demo_user} работает в организации `{demo_org}` и использует чат `{demo_chat}`. "
            "Если элемент скрыт, пользователь не должен искать обходной путь: нужно проверить роль, настройки организации "
            "или обратиться к администратору приложения."
        )
        scenario = (
            f"Практический пример: пользователь открывает соответствующий экран, находит действие «{label}» и выполняет его "
            "только если интерфейс показывает действие как доступное. Если действие относится к add-on, рядом в тексте "
            "руководства указывается, что оно может отсутствовать в другой организации или профиле поставки."
        )
        settings = (
            "Пользователь не меняет серверные параметры этой функции. Он может управлять только личными настройками, "
            "видимостью, уведомлениями, приватностью и собственными объектами: сообщениями, файлами, ссылками или ботами, "
            "если роль и включённые модули это разрешают."
        )
        checks = (
            "Проверка для пользователя простая: действие отображается без ошибки, результат виден в текущем чате или профиле, "
            "а при недоступности интерфейс показывает понятное состояние. Не нужно передавать в поддержку пароль, token или "
            "другие секреты."
        )
        errors = (
            "Типовые проблемы: нет прав в чате, функция отключена как add-on, организация использует другой набор модулей, "
            "сессия истекла или объект уже удалён/отозван. Пользователь фиксирует экран и текст ошибки, но не выполняет "
            "серверные команды."
        )
    elif guide.key == "admin":
        role = (
            f"Для администратора приложения функция `{key}` относится к управлению поведением пользователей, организаций, "
            f"политик или модулей. В демонстрационном сценарии {demo_admin} проверяет, как функция влияет на `{demo_org}`, "
            "и заранее понимает, кого затронет изменение."
        )
        scenario = (
            f"Практический пример: администратор открывает admin console, проверяет Product Modules или профильный раздел, "
            f"после чего настраивает «{label}» только при понятном результате отката. Если функция скрыта или disabled, "
            "администратор проверяет effective state и degraded reason."
        )
        settings = (
            f"Важные параметры: owner `{owner}`, UI behavior `{ui_behavior}`"
            + (f", API mode `{api_mode}`" if api_mode else "")
            + ". Для add-on функций нужно учитывать disabled/degraded/installing поведение, а также то, удаляет ли действие "
            "данные или только меняет доступность интерфейса."
        )
        checks = (
            "Проверка администратора: admin UI показывает ожидаемое состояние, audit фиксирует изменение, пользовательский "
            "интерфейс отражает новую политику, а при ошибке администратор видит понятную причину. Для опасных действий "
            "нужно проверить scope, affected users/chats и возможность отката."
        )
        errors = (
            "Типовые проблемы: нет admin role, выбран неверный org_id, missing secret требует инфраструктурного вмешательства, "
            "функция принадлежит отключённому add-on или администратор пытается решить серверную проблему через UI. В таких "
            "случаях фиксируется handoff инфраструктурному администратору."
        )
    else:
        runtime = module.get("runtime", {})
        services = ", ".join(runtime.get("services", [])) or "без отдельного сервиса"
        workers = ", ".join(runtime.get("workers", [])) or "без отдельного worker"
        secrets = ", ".join(runtime.get("required_secrets", [])) or "без обязательного секрета"
        checks_list = ", ".join(runtime.get("health_checks", [])) or "общий health/ready"
        role = (
            f"Для инфраструктурного администратора функция `{key}` важна как часть серверной поверхности. "
            f"В демонстрационном сценарии {demo_infra} проверяет, какие сервисы, workers, секреты, миграции и health checks "
            "нужны, чтобы функция не выглядела доступной при неготовой инфраструктуре."
        )
        scenario = (
            f"Практический пример: перед передачей стенда администратор сверяет «{label}» с Product Modules, проверяет "
            "runtime dependencies и запускает smoke или targeted health check. Если функция относится к add-on, её нельзя "
            "считать готовой только потому, что код развернут."
        )
        settings = (
            f"Инфраструктурные параметры: services — {services}; workers — {workers}; secrets — {secrets}; health — {checks_list}. "
            "Если required secret отсутствует, функция должна попасть в degraded/disabled состояние, а не ломаться на стороне пользователя."
        )
        checks = (
            "Проверка инфраструктуры: health/ready отвечают ожидаемо, Product Modules показывает корректный effective state, "
            "smoke не падает, логи не содержат скрытых ошибок, а external stack и storage доступны для функций, которые от них зависят."
        )
        errors = (
            "Типовые проблемы: неверный CORS/origin, недоступен IdP, DB, Redis, NATS или MinIO, отсутствует секрет, worker остановлен, "
            "optional migration не установлена или endpoint защищён и требует корректного admin/token контекста. Исправление начинается "
            "с диагностики зависимости, а не с изменения пользовательского UI."
        )

    return f"""
### {idx}. {label} (`{key}`)

**Назначение.** {role}

**Как применяется.** {scenario}

{evidence_markdown(guide, module_id, key, label, ui_behavior, module)}

**Настройки и ограничения.** {settings}

**Проверка результата.** {checks}

**Типовые ошибки и диагностика.** {errors}

**Связь с каталогом.** Модуль: `{module_id}`; owner: `{owner}`; ui_behavior: `{ui_behavior}`.
"""


def product_profile_reference(guide: Guide, catalog: dict) -> str:
    legacy = catalog.get("legacy_deploy_profile_map", {})
    rows = []
    for name, data in legacy.items():
        rows.append((name, ", ".join(data.get("addons", [])) or "Base-only", str(data.get("scale", {}))))
    if not rows:
        return ""
    if guide.key == "user":
        note = (
            "Пользователь не выбирает профиль поставки, но профиль объясняет, почему в одной организации видны только базовые "
            "функции, а в другой доступны live, integrations, export или enterprise auth."
        )
    elif guide.key == "admin":
        note = (
            "Администратор приложения использует эту таблицу как ориентир, но управляет фактической доступностью через Product "
            "Modules и политики организации, а не через ручные ожидания пользователя."
        )
    else:
        note = (
            "Инфраструктурный администратор сверяет профиль с deploy variables, workers, required secrets и storage. Legacy profile "
            "не должен скрывать явный список `korus_product_addons`."
        )
    return "\n".join(
        [
            "# Справочник профилей поставки",
            note,
            md_table(["Профиль", "Add-ons", "Scale"], rows),
        ]
    )


def appendix(guide: Guide, catalog: dict) -> str:
    rows = []
    for feature in catalog_feature_rows(catalog):
        owner = feature.get("owner", feature.get("module", ""))
        rows.append((feature.get("module", ""), feature["key"], feature.get("label", ""), owner, feature.get("ui_behavior", "")))
    return f"""
# Приложение. Полный каталог функций

Этот раздел нужен для контроля покрытия. Он помогает проверить, что функция не потерялась между пользовательским, админским и инфраструктурным руководством.

{md_table(["Модуль", "Feature key", "Название", "Owner", "UI"], rows)}

# Финальный чеклист чтения

- В документе нет реальных секретов, токенов, приватных ключей и боевых vault-данных.
- Скриншоты соответствуют тексту рядом с ними.
- Все add-ons имеют честную пометку Base/add-on/disabled/degraded/ограничение.
- Нет случайных черновых пометок.
- Роли не смешаны: руководство отвечает на вопросы своей аудитории.
"""


def guide_css() -> str:
    return """
body{font-family:"Segoe UI",Arial,sans-serif;line-height:1.45;color:#182033;margin:0;max-width:none}
.page-title{padding:32px 38px 24px;background:linear-gradient(135deg,#241044,#4f2ad8);color:white;border-radius:18px;margin-bottom:24px}
.page-title h1{color:white;border:0;margin:0 0 10px 0;break-before:auto}
.page-title p{margin:4px 0;color:#ece8ff}
h1{break-before:page;color:#1a2a6c;border-bottom:2px solid #d6def8;padding-bottom:8px;margin-top:24px}
h1:first-of-type{break-before:auto}
h2{margin-top:28px;color:#233876;border-left:5px solid #6b46f2;padding-left:10px}
h3{color:#36509a;margin-top:22px}
h4{color:#253858;margin:18px 0 8px}
p{margin:8px 0}
a{color:#3659d8;text-decoration:none}
code{font-family:"Cascadia Mono",Consolas,monospace;background:#f5f7fb;border:1px solid #e1e6f4;border-radius:4px;padding:1px 4px}
pre{font-family:"Cascadia Mono",Consolas,monospace;background:#f5f7fb;border:1px solid #dce4f5;padding:12px;border-radius:8px;white-space:pre-wrap}
ul,ol{margin:8px 0 12px 22px;padding:0}
li{margin:4px 0}
table{border-collapse:collapse;width:100%;margin:12px 0 18px;font-size:9.5pt;break-inside:auto}
thead{background:#eef2ff;color:#1d2d65}
th,td{border:1px solid #cfd8ea;padding:5px 6px;vertical-align:top}
tr:nth-child(even) td{background:#fafbff}
img{max-width:100%;border:1px solid #d8deea;border-radius:10px;margin:8px 0}
figure{break-inside:avoid;margin:16px 0}
figcaption{font-size:9pt;color:#5a6478;margin-top:4px}
.callout{background:#fff8e6;border-left:5px solid #e0a700;padding:10px 12px;border-radius:8px;margin:12px 0}
.checklist{background:#f3fbf6;border-left:5px solid #28a745;padding:10px 12px;border-radius:8px;margin:12px 0}
@page{size:A4;margin:17mm 14mm 18mm}
"""


def simple_markdown_to_html(md: str, title: str) -> str:
    lines = md.splitlines()
    out = []
    list_stack: list[str] = []
    in_code = False
    i = 0

    def close_lists() -> None:
        nonlocal list_stack
        while list_stack:
            out.append(f"</{list_stack.pop()}>")

    while i < len(lines):
        line = lines[i]
        if line.startswith("```"):
            if not in_code:
                close_lists()
                out.append("<pre><code>")
                in_code = True
            else:
                out.append("</code></pre>")
                in_code = False
            i += 1
            continue
        if in_code:
            out.append(html.escape(line))
            i += 1
            continue
        if line.startswith("| ") and i + 1 < len(lines) and lines[i + 1].startswith("| ") and set(lines[i + 1].replace("|", "").replace(" ", "")) <= {"-", ":"}:
            close_lists()
            table_lines = []
            while i < len(lines) and lines[i].startswith("| "):
                table_lines.append(lines[i])
                i += 1
            out.append(markdown_table_to_html(table_lines))
            continue
        image_match = re.match(r"!\[([^\]]*)\]\(([^)]+)\)", line.strip())
        if image_match:
            close_lists()
            alt, src = image_match.groups()
            out.append(f"<figure><img src='{html.escape(src)}' alt='{html.escape(alt)}'><figcaption>{html.escape(alt)}</figcaption></figure>")
            i += 1
            continue
        if line.startswith("#"):
            close_lists()
            level = min(len(line) - len(line.lstrip("#")), 4)
            text = line[level:].strip()
            anchor = slug(text)
            out.append(f"<h{level}>{html.escape(text)}</h{level}>")
        elif line.startswith("- "):
            if list_stack != ["ul"]:
                close_lists()
                out.append("<ul>")
                list_stack = ["ul"]
            out.append(f"<li>{inline_md(line[2:])}</li>")
        elif re.match(r"\d+\. ", line):
            if list_stack != ["ol"]:
                close_lists()
                out.append("<ol>")
                list_stack = ["ol"]
            out.append(f"<li>{inline_md(re.sub(r'^\d+\\.\\s+', '', line))}</li>")
        elif line.strip() == "":
            close_lists()
        else:
            close_lists()
            out.append(f"<p>{inline_md(line)}</p>")
        i += 1
    close_lists()
    cover = f"<section class='page-title'><h1>{html.escape(title)}</h1><p>Korus Messenger · {PRODUCT_VERSION}</p><p>Локальная сборка: {TODAY}</p></section>"
    return f"<!doctype html><html lang='ru'><head><meta charset='utf-8'><title>{html.escape(title)}</title><style>{guide_css()}</style></head><body>{cover}{''.join(out)}</body></html>"


def markdown_table_to_html(table_lines: list[str]) -> str:
    rows = []
    for raw in table_lines:
        cells = [cell.strip() for cell in raw.strip().strip("|").split("|")]
        rows.append(cells)
    if len(rows) < 2:
        return ""
    header = rows[0]
    body = rows[2:]
    head_html = "".join(f"<th>{inline_md(cell)}</th>" for cell in header)
    body_html = []
    for row in body:
        if len(row) < len(header):
            row = row + [""] * (len(header) - len(row))
        body_html.append("<tr>" + "".join(f"<td>{inline_md(cell)}</td>" for cell in row[: len(header)]) + "</tr>")
    return "<table><thead><tr>" + head_html + "</tr></thead><tbody>" + "".join(body_html) + "</tbody></table>"


def slug(text: str) -> str:
    value = re.sub(r"[^a-zA-Z0-9а-яА-ЯёЁ]+", "-", text.strip().lower()).strip("-")
    return value or "section"


def inline_md(text: str) -> str:
    escaped = html.escape(text)
    escaped = re.sub(r"`([^`]+)`", r"<code>\1</code>", escaped)
    escaped = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", escaped)
    escaped = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r"<a href=\"\2\">\1</a>", escaped)
    return escaped


def font_file() -> str | None:
    candidates = [
        Path("C:/Windows/Fonts/segoeui.ttf"),
        Path("C:/Windows/Fonts/arial.ttf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return str(candidate)
    return None


def split_for_pdf(md: str) -> list[str]:
    chunks = []
    current = []
    for line in md.splitlines():
        if line.startswith("# ") and current:
            chunks.append("\n".join(current))
            current = [line]
        else:
            current.append(line)
    if current:
        chunks.append("\n".join(current))
    return chunks


def wrap_text(text: str, width: int = 86) -> list[str]:
    out: list[str] = []
    for raw in text.splitlines():
        raw = raw.strip()
        if not raw:
            out.append("")
            continue
        if raw.startswith("|"):
            out.extend(textwrap.wrap(raw, width=width, break_long_words=False) or [""])
            continue
        prefix = ""
        if raw.startswith("- "):
            prefix = "• "
            raw = raw[2:]
        elif re.match(r"\d+\. ", raw):
            m = re.match(r"(\d+\. )(.+)", raw)
            if m:
                prefix = m.group(1)
                raw = m.group(2)
        lines = textwrap.wrap(raw.replace("**", "").replace("`", ""), width=width, initial_indent=prefix, subsequent_indent=" " * len(prefix))
        out.extend(lines or [""])
    return out


def build_pdf(guide: Guide, md: str) -> int:
    tmp = DOCS / f".{guide.pdf_name}.tmp"
    html_doc = simple_markdown_to_html(md, guide.title)
    writer = fitz.DocumentWriter(str(tmp))
    story = fitz.Story(html_doc, archive=fitz.Archive(str(DOCS)), em=11)

    def rectfn(rect_num: int, filled: fitz.Rect) -> tuple[fitz.Rect, fitz.Rect, fitz.Matrix]:
        return fitz.Rect(0, 0, 595, 842), fitz.Rect(42, 42, 553, 800), fitz.Matrix(1, 1)

    story.write(writer, rectfn)
    writer.close()

    doc = fitz.open(tmp)
    font = font_file()
    for i, page in enumerate(doc, 1):
        footer = f"{guide.title} · {PRODUCT_VERSION} · стр. {i}"
        footer_font = "helv"
        if font:
            try:
                page.insert_font(fontname="GuideFooter", fontfile=font)
                footer_font = "GuideFooter"
            except Exception:
                footer_font = "helv"
        page.insert_text((42, 820), footer, fontsize=8, fontname=footer_font, color=(0.35, 0.35, 0.35))
    out = DOCS / guide.pdf_name
    doc.save(out)
    pages = doc.page_count
    doc.close()
    try:
        tmp.unlink(missing_ok=True)
    except PermissionError:
        # Windows can keep the temporary writer handle alive for a short time.
        # The file is hidden and overwritten on the next build.
        pass
    return pages


def add_image_page(doc: fitz.Document, title: str, image_path: Path, font: str | None) -> None:
    page = doc.new_page(width=595, height=842)
    fontname = "helv"
    if font:
        page.insert_font(fontname="GuideFont", fontfile=font)
        fontname = "GuideFont"
    page.insert_text((42, 42), f"Скриншот: {image_path.name}", fontsize=18, fontname=fontname, color=(0.08, 0.16, 0.42))
    page.insert_text(
        (42, 70),
        "Изображение снято с рабочего сервера Korus Messenger и используется как визуальная привязка к инструкциям.",
        fontsize=9.5,
        fontname=fontname,
        color=(0.15, 0.15, 0.15),
    )
    rect = fitz.Rect(42, 100, 553, 760)
    page.insert_image(rect, filename=str(image_path), keep_proportion=True)


def add_pdf_page(doc: fitz.Document, title: str, lines: list[str], font: str | None) -> None:
    page = doc.new_page(width=595, height=842)
    fontname = "helv"
    if font:
        page.insert_font(fontname="GuideFont", fontfile=font)
        fontname = "GuideFont"
    y = 42
    for idx, line in enumerate(lines):
        if line.startswith("# "):
            text = line[2:]
            size = 18
            color = (0.08, 0.16, 0.42)
        elif line.startswith("## "):
            text = line[3:]
            size = 15
            color = (0.12, 0.22, 0.50)
        elif line.startswith("### "):
            text = line[4:]
            size = 13
            color = (0.18, 0.30, 0.58)
        elif line.startswith("#### "):
            text = line[5:]
            size = 11
            color = (0.20, 0.20, 0.20)
        else:
            text = line
            size = 9.5
            color = (0.10, 0.10, 0.10)
        if idx == 0 and not line.startswith("# "):
            page.insert_text((42, 28), title, fontsize=8, fontname=fontname, color=(0.35, 0.35, 0.35))
        page.insert_text((42, y), text, fontsize=size, fontname=fontname, color=color)
        y += 18 if size >= 15 else 14


def validate_outputs(guide: Guide, md: str, pages: int) -> list[str]:
    errors: list[str] = []
    forbidden = ["TODO", "TBD", "production vault", "приватный ключ"]
    for marker in forbidden:
        if marker.lower() in md.lower():
            errors.append(f"{guide.md_name}: forbidden marker {marker}")
    if "сервер стартовал" in md.lower():
        errors.append(f"{guide.md_name}: mentions how screenshot server was started")
    if pages < 120:
        errors.append(f"{guide.pdf_name}: expected at least 120 pages, got {pages}")
    for shot in guide.screenshots:
        if not (IMAGES / shot).exists():
            errors.append(f"{guide.md_name}: screenshot pending {shot}")
    return errors


def write_screenshot_inventory(guides: Iterable[Guide]) -> None:
    rows = []
    for guide in guides:
        for shot in guide.screenshots:
            path = IMAGES / shot
            rows.append((guide.title, shot, "снят" if path.exists() else "ожидает съёмки", f"docs/images/guides/{shot}"))
    content = "# Screenshot inventory\n\n" + md_table(["Руководство", "Файл", "Статус", "Путь"], rows) + "\n"
    (DOCS / "GUIDE_SCREENSHOTS.md").write_text(content, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-pdf", action="store_true")
    parser.add_argument("--strict-screenshots", action="store_true")
    args = parser.parse_args()

    DOCS.mkdir(parents=True, exist_ok=True)
    IMAGES.mkdir(parents=True, exist_ok=True)
    catalog = load_catalog()
    write_screenshot_inventory(GUIDES)

    all_errors: list[str] = []
    for guide in GUIDES:
        md = build_markdown(guide, catalog)
        (DOCS / guide.md_name).write_text(md, encoding="utf-8")
        (DOCS / guide.html_name).write_text(simple_markdown_to_html(md, guide.title), encoding="utf-8")
        pages = 0
        if not args.skip_pdf:
            pages = build_pdf(guide, md)
            print(f"Wrote {guide.pdf_name}: {pages} pages")
        errors = validate_outputs(guide, md, pages or 120)
        if not args.strict_screenshots:
            errors = [e for e in errors if "screenshot pending" not in e]
        all_errors.extend(errors)
        print(f"Wrote {guide.md_name} and {guide.html_name}")

    if all_errors:
        print("Guide verification found issues:", file=sys.stderr)
        for error in all_errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("Guide build completed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
