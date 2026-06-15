"""Appendix I: user-facing pages and URL paths for tz_product (not source files)."""

from html import escape

PRODUCT_PAGES = [
    ("UI", "/", "Вход и мессенджер", "Единая страница: форма логина или чаты после авторизации"),
    ("API", "/admin/", "Админ-консоль", "Главная страница администрирования"),
]

ADMIN_SECTIONS = [
    ("Статистика сервера", "Обзор нагрузки и состояния"),
    ("Экспорт / GDPR", "Справка и compliance export"),
    ("Сессия (admin)", "Текущая admin-сессия"),
    ("Организации", "Tenant / org"),
    ("Пользователь → организация", "Привязка пользователя к org"),
    ("Аудит (последние)", "Журнал admin-действий"),
    ("Манифест консоли", "Список разделов SPI"),
    ("Read receipts", "Статистика прочтений"),
    ("E2EE / MLS", "Статус шифрования"),
    ("Ретенция (org / chat)", "Политики хранения"),
    ("Legal hold (extended)", "Заморозка удаления"),
    ("Purge status", "Статус фоновой очистки"),
]


def render_appendix_i_html() -> str:
    def rows_pages():
        for host, path, title, desc in PRODUCT_PAGES:
            yield (
                f"<tr><td>{escape(title)}</td>"
                f"<td>{escape(host)}</td>"
                f"<td><code>{escape(path)}</code></td>"
                f"<td>{escape(desc)}</td></tr>"
            )

    def rows_admin_sections():
        for title, desc in ADMIN_SECTIONS:
            yield f"<tr><td>{escape(title)}</td><td>{escape(desc)}</td></tr>"

    return f"""
<h3 id="app-i">Приложение I. Страницы и адреса</h3>
<p class="small">Каталог <b>страниц</b> продукта. Пути относительные: клиент — хост UI, админка — хост API.</p>

<h4>I.1 Страницы продукта</h4>
<table>
  <tr><th>Страница</th><th>Хост</th><th>Путь</th><th>Описание</th></tr>
  {''.join(rows_pages())}
</table>

<h4>I.2 Разделы админ-консоли (меню на <code>/admin/</code>)</h4>
<p class="small">Отдельных URL у разделов нет — выбор пункта меню на странице <code>/admin/</code>.</p>
<table>
  <tr><th>Раздел</th><th>Назначение</th></tr>
  {''.join(rows_admin_sections())}
</table>
"""


def render_appendix_i_md() -> str:
    lines = [
        "### Приложение I. Страницы и адреса",
        "",
        "#### I.1 Страницы продукта",
        "",
        "| Страница | Хост | Путь | Описание |",
        "|----------|------|------|----------|",
    ]
    for host, path, title, desc in PRODUCT_PAGES:
        lines.append(f"| {title} | {host} | `{path}` | {desc} |")
    lines += [
        "",
        "#### I.2 Разделы админ-консоли (меню на `/admin/`)",
        "",
        "| Раздел | Назначение |",
        "|--------|------------|",
    ]
    for title, desc in ADMIN_SECTIONS:
        lines.append(f"| {title} | {desc} |")
    lines.append("")
    return "\n".join(lines)
