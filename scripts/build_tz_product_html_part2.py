# Part 2: sections 11-18 and appendices — appended by build script

from html import escape

from tz_product_resources import render_appendix_i_html

# §18.7 — annual server cost breakdown (monthly × 12 from §18.2–18.3)
PILOT_SERVERS_MONTHLY = [
    ("Сервер приложений (16 ГБ)", 28_000, "#6366f1"),
    ("Web-шлюз (8 ГБ)", 15_000, "#3b82f6"),
    ("Диск SSD (hot DB)", 1_750, "#8b5cf6"),
    ("Диск HDD (файлы+архив)", 3_600, "#a855f7"),
    ("Канал 200 Мбит/с", 8_000, "#f59e0b"),
    ("Backup + мониторинг", 5_000, "#10b981"),
]

STANDARD_SERVERS_MONTHLY = [
    ("Серверы приложений ×3", 135_000, "#6366f1"),
    ("Web + LB ×2", 30_000, "#3b82f6"),
    ("PostgreSQL primary + replica", 90_000, "#2563eb"),
    ("Redis cluster", 25_000, "#dc2626"),
    ("Solr + ZK ×3", 75_000, "#7c3aed"),
    ("MinIO / файлы (~30 ТБ)", 24_000, "#059669"),
    ("NATS, Keycloak, workers", 45_000, "#0891b2"),
    ("Канал 1 Гбит/с", 35_000, "#f59e0b"),
    ("Ops (backup, monitoring)", 15_000, "#10b981"),
]

STANDARD_OPT_RATIO = 332_000 / 474_000  # §18.3 optimized vs baseline


def _fmt_rub(amount: int) -> str:
    return f"{amount:,}".replace(",", " ") + " ₽"


def _fmt_rub_short(amount: int) -> str:
    """Compact axis labels (avoid clipping in SVG)."""
    if amount >= 1_000_000:
        whole = amount // 1_000_000
        frac = round((amount % 1_000_000) / 100_000)
        if frac in (0, 10):
            return f"{whole} млн ₽" if frac == 0 else f"{whole + 1} млн ₽"
        return f"{whole},{frac} млн ₽"
    if amount >= 1_000:
        return f"{amount // 1_000} тыс ₽"
    return f"{amount} ₽"


def _stacked_bar_svg(
    items: list[tuple[str, int, str]],
    x: int,
    bar_w: int,
    base_y: int,
    bar_h: int,
) -> str:
    """Stacked bar scaled to bar_h; segment heights sum to bar_h."""
    parts: list[str] = []
    total = sum(m * 12 for _, m, _ in items)
    if total <= 0:
        return ""
    y = base_y
    for _name, monthly, color in items:
        annual = monthly * 12
        h = max(1, round(annual / total * bar_h))
        y -= h
        parts.append(
            f'<rect x="{x}" y="{y}" width="{bar_w}" height="{h}" fill="{color}" stroke="#fff" stroke-width="1"/>'
        )
    return "".join(parts)


def render_section_18_7() -> str:
    pilot_items = PILOT_SERVERS_MONTHLY
    std_items = [
        (name, round(monthly * STANDARD_OPT_RATIO), color)
        for name, monthly, color in STANDARD_SERVERS_MONTHLY
    ]
    pilot_total = sum(m * 12 for _, m, _ in pilot_items)
    std_total = sum(m * 12 for _, m, _ in std_items)
    std_baseline_total = sum(m * 12 for _, m, _ in STANDARD_SERVERS_MONTHLY)
    pilot_baseline_total = 96_000 * 12

    compare = [
        ("Pilot 10k", "без оптим.", pilot_baseline_total, "#fca5a5"),
        ("Pilot 10k", "оптимиз.", pilot_total, "#86efac"),
        ("Standard 100k", "без оптим.", std_baseline_total, "#fca5a5"),
        ("Standard 100k", "оптимиз.", std_total, "#86efac"),
    ]
    max_annual = max(v for _, _, v, _ in compare)

    # --- chart 1: totals ---
    bar_w, gap = 92, 28
    chart_left, chart_right = 78, 500
    base_y1, top_y1 = 268, 68
    chart_h1 = base_y1 - top_y1
    x0 = chart_left + 8

    y_ticks = []
    for i in range(5):
        val = max_annual * i // 4
        y = base_y1 - chart_h1 * i // 4
        y_ticks.append(
            f'<line x1="{chart_left}" y1="{y}" x2="{chart_right}" y2="{y}" stroke="#e5e7eb" stroke-width="1"/>'
            f'<text x="{chart_left - 8}" y="{y + 4}" text-anchor="end" font-size="9" fill="#4b5563">{_fmt_rub_short(val)}</text>'
        )

    compare_bars = []
    compare_labels = []
    for i, (profile, mode, annual, color) in enumerate(compare):
        x = x0 + i * (bar_w + gap)
        h = max(6, round(annual / max_annual * chart_h1))
        y = base_y1 - h
        compare_bars.append(
            f'<rect x="{x}" y="{y}" width="{bar_w}" height="{h}" fill="{color}" rx="3"/>'
            f'<text x="{x + bar_w // 2}" y="{y - 8}" text-anchor="middle" font-size="9" font-weight="600">{_fmt_rub(annual)}</text>'
        )
        compare_labels.append(
            f'<text x="{x + bar_w // 2}" y="{base_y1 + 14}" text-anchor="middle" font-size="9" font-weight="600">{escape(profile)}</text>'
            f'<text x="{x + bar_w // 2}" y="{base_y1 + 27}" text-anchor="middle" font-size="9" fill="#6b7280">{escape(mode)}</text>'
        )

    # --- chart 2: structure (optimized only) ---
    stack_h = 132
    stack_w = 100
    pilot_x, std_x = 200, 380
    base_y2 = 490
    pilot_bar_h = max(12, round(pilot_total / std_total * stack_h))
    stack_top = base_y2 - stack_h
    divider_y = stack_top - 28
    subtitle2_y = stack_top - 10
    profile_label_y1 = base_y2 + 16
    profile_label_y2 = base_y2 + 30

    legend_items = pilot_items + std_items
    seen: set[str] = set()
    unique_legend: list[tuple[str, str]] = []
    for name, _m, color in legend_items:
        if name not in seen:
            seen.add(name)
            unique_legend.append((name, color))
    legend_cols = 3
    legend_row_h = 17
    legend_y0 = profile_label_y2 + 18
    legend_svg = []
    for i, (name, color) in enumerate(unique_legend):
        col = i % legend_cols
        row = i // legend_cols
        lx = 36 + col * 220
        ly = legend_y0 + row * legend_row_h
        legend_svg.append(
            f'<rect x="{lx}" y="{ly - 10}" width="10" height="10" fill="{color}"/>'
            f'<text x="{lx + 14}" y="{ly}" font-size="9">{escape(name)}</text>'
        )
    svg_h = legend_y0 + ((len(unique_legend) + legend_cols - 1) // legend_cols) * legend_row_h + 16

    table_rows = []
    std_by_name = {n: m for n, m, _ in std_items}
    for name, monthly, _ in pilot_items:
        std_m = std_by_name.get(name)
        std_cell = f'<td class="money">{_fmt_rub(std_m * 12)}</td>' if std_m else "<td>—</td>"
        table_rows.append(
            f"<tr><td>{escape(name)}</td>"
            f'<td class="money">{_fmt_rub(monthly * 12)}</td>{std_cell}</tr>'
        )
    for name, monthly, _ in std_items:
        if name in {n for n, _, _ in pilot_items}:
            continue
        table_rows.append(
            f"<tr><td>{escape(name)}</td><td>—</td>"
            f'<td class="money">{_fmt_rub(monthly * 12)}</td></tr>'
        )
    table_rows.append(
        f'<tr><th>Итого infra / год</th>'
        f'<th class="money">{_fmt_rub(pilot_total)}</th>'
        f'<th class="money">{_fmt_rub(std_total)}</th></tr>'
    )

    return f"""
<h3 id="s18-7">18.7 Сравнение стоимости серверов за год</h3>
<p class="small">Все суммы — <b>только инфраструктура</b> (аренда/амортизация серверов, дисков, канала, ops).
  Расчёт: месячные ставки из §18.2–18.3 × 12. Standard «оптимиз.» — −30% к baseline (§12).</p>

<table>
  <tr><th>Сервер / статья</th><th>Pilot 10k ₽/год</th><th>Standard 100k (оптим.) ₽/год</th></tr>
  {''.join(table_rows)}
</table>

<figure class="fig">
<svg viewBox="0 0 720 {svg_h}" width="720" height="{svg_h}" xmlns="http://www.w3.org/2000/svg">
  <text x="360" y="28" text-anchor="middle" font-size="14" font-weight="bold">Сравнение: стоимость всех серверов за год (₽)</text>

  <text x="290" y="52" text-anchor="middle" font-size="11" font-weight="600" fill="#374151">Итог за 12 месяцев</text>
  <line x1="{chart_left}" y1="{base_y1}" x2="{chart_right}" y2="{base_y1}" stroke="#9ca3af"/>
  <line x1="{chart_left}" y1="{base_y1}" x2="{chart_left}" y2="{top_y1}" stroke="#9ca3af"/>
  {''.join(y_ticks)}
  {''.join(compare_bars)}
  {''.join(compare_labels)}
  <rect x="90" y="302" width="10" height="10" fill="#fca5a5"/><text x="106" y="311" font-size="9">без оптимизации</text>
  <rect x="210" y="302" width="10" height="10" fill="#86efac"/><text x="226" y="311" font-size="9">оптимизированный профиль</text>

  <line x1="40" y1="{divider_y}" x2="680" y2="{divider_y}" stroke="#d1d5db"/>
  <text x="360" y="{subtitle2_y}" text-anchor="middle" font-size="11" font-weight="600" fill="#374151">Состав годовой стоимости (оптимизированные профили)</text>

  <line x1="120" y1="{base_y2}" x2="480" y2="{base_y2}" stroke="#9ca3af"/>
  <line x1="120" y1="{stack_top}" x2="120" y2="{base_y2}" stroke="#9ca3af"/>
  {_stacked_bar_svg(pilot_items, pilot_x, stack_w, base_y2, pilot_bar_h)}
  {_stacked_bar_svg(std_items, std_x, stack_w, base_y2, stack_h)}
  <text x="{pilot_x + stack_w // 2}" y="{profile_label_y1}" text-anchor="middle" font-size="10">Pilot 10k</text>
  <text x="{pilot_x + stack_w // 2}" y="{profile_label_y2}" text-anchor="middle" font-size="9" fill="#6b7280">{_fmt_rub(pilot_total)}</text>
  <text x="{std_x + stack_w // 2}" y="{profile_label_y1}" text-anchor="middle" font-size="10">Standard 100k</text>
  <text x="{std_x + stack_w // 2}" y="{profile_label_y2}" text-anchor="middle" font-size="9" fill="#6b7280">{_fmt_rub(std_total)}</text>
  {''.join(legend_svg)}
</svg>
<figcaption class="fig-cap">Рис. 8. Сравнительная диаграмма годовой стоимости серверов (условные тарифы §18.1; не оферта)</figcaption>
</figure>

<div class="note">
  <div class="req">Как читать диаграмму</div>
  <div class="comment">
    <b>Верхний ряд</b> — итоговая стоимость infra за год по профилю (красный — без оптимизации §12, зелёный — целевой профиль).
    <b>Нижний ряд</b> — из чего складывается годовая сумма для оптимизированных Pilot и Standard (цветные сегменты = типы серверов, легенда под диаграммой).
    Лицензия ПО, внедрение и поддержка L2 — в §18.4 и §18.6.
  </div>
</div>
"""


def append_sections_11_18(parts, FIG_MSG, FIG_SLA, FIG_COST):
    parts.append(f"""
<hr/>
<h2 id="s11">11. Критерии приёмки продукта</h2>
<ol>
  <li><b>Пользователь</b> выполняет все основные сценарии из §5 через веб-браузер.</li>
  <li><b>Автотесты UI:</b> 27+ сценариев проходят на тестовом стенде.</li>
  <li><b>Администратор</b> управляет организациями, политиками хранения и аудитом через веб-консоль.</li>
  <li><b>Production:</b> HTTPS, секреты не в открытом виде, sign-off E2EE перед массовым включением.</li>
  <li><b>Экспорт и ретенция:</b> окончательное удаление только после прохождения export gate.</li>
  <li><b>Локализация:</b> интерфейс минимум на русском и английском.</li>
  <li><b>Инфраструктура (§12):</b> при оптимизации — достижение целевых показателей §10.4 после load test.</li>
  <li><b>Комплаенс (§14):</b> export с индикатором полноты; legal hold блокирует purge; audit admin-действий.</li>
  <li><b>SLA (§15):</b> согласованные RPO/RTO по профилю; сообщения не теряются при деградации P1.</li>
  <li><b>Профиль (§16):</b> ограничения Pilot доведены до заказчика до запуска.</li>
  <li><b>Интеграции (§13):</b> согласованы сценарии SSO и Bot API; реализованные каналы описаны для IT.</li>
</ol>

<hr/>
<h2 id="s12">12. Оптимизация инфраструктуры</h2>
<p><span class="tag tag-planned">Запланировано</span> — зафиксировано в дорожной карте продукта; реализация отложена.</p>
<h3>12.1 Зачем это нужно</h3>
<ol>
  <li><b>Пилот на modest hardware</b> — десятки ГБ RAM, а не сотни.</li>
  <li><b>Больше сотрудников</b> на том же сервере по мере роста.</li>
  <li><b>Длинная история</b> без линейного роста дисков (сжатие архива, dedup файлов).</li>
</ol>
<h3>12.2 Направления оптимизации</h3>
<table>
  <tr><th>№</th><th>Что должно быть достигнуто</th><th>Зачем бизнесу</th><th>Профиль</th></tr>
  <tr><td>01</td><td>Минимальный состав серверов Pilot (без поискового кластера)</td><td>−75% стоимость пилота</td><td>Pilot</td></tr>
  <tr><td>02</td><td>Аутентификация в production-режиме</td><td>Стабильный pilot</td><td>Pilot+</td></tr>
  <tr><td>03</td><td>Кэш частых чтений (список чатов)</td><td>Больше пользователей без новых серверов</td><td>Standard</td></tr>
  <tr><td>04</td><td>Масштабирование доставки сообщений</td><td>Рост онлайна</td><td>Standard+</td></tr>
  <tr><td>05</td><td>Разделение чтения и записи БД</td><td>Утренние рассылки без пиков</td><td>Standard+</td></tr>
  <tr><td>06</td><td>Сжатие архивных данных</td><td>−60% диска на старых сообщениях</td><td>Standard+</td></tr>
  <tr><td>07</td><td>Пакетная индексация поиска</td><td>Меньше CPU</td><td>Standard+</td></tr>
  <tr><td>08</td><td>Дедупликация одинаковых файлов</td><td>−35% диска на вложениях</td><td>Standard+</td></tr>
  <tr><td>09</td><td>Разделение данных крупных org (sharding)</td><td>Путь к 1M</td><td>Enterprise</td></tr>
</table>
<p><b>Ключевое правило:</b> доставка сообщения (P0) не зависит от поиска, push и архива (P1/P2) — при сбое второстепенных служб чаты продолжают работать.</p>

<hr/>
<h2 id="s13">13. Интеграции и экосистема</h2>
<p><span class="tag tag-partial">Частично</span></p>
<h3>13.1 Уже доступно</h3>
<table>
  <tr><th>Канал</th><th>Для кого</th><th>Что даёт</th></tr>
  <tr><td>Программный интерфейс (API)</td><td>IT, скрипты</td><td>Чаты, сообщения, файлы, admin</td></tr>
  <tr><td>Мгновенная доставка (WebSocket)</td><td>Клиенты</td><td>Realtime, «печатает…»</td></tr>
  <tr><td>Система входа (Keycloak)</td><td>IT</td><td>JWT, локальные пользователи</td></tr>
  <tr><td>Export / replay</td><td>Compliance</td><td>JSON/ZIP выгрузка</td></tr>
  <tr><td>Web Push</td><td>Пользователи</td><td>Уведомления в браузере</td></tr>
</table>
<h3>13.2 Запланировано</h3>
<table>
  <tr><th>Сценарий</th><th>Ожидание</th><th>Статус</th></tr>
  <tr><td>SSO Google / корпоративный портал</td><td>Единый вход без отдельного пароля</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
  <tr><td>LDAP / Active Directory</td><td>Учётки из AD</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
  <tr><td>Bot API</td><td>Service Desk, опросы, @mention</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
</table>
<p><b>Безопасность:</b> webhook только HTTPS; секреты не логируются; сбой бота не блокирует переписку людей.</p>

<hr/>
<h2 id="s14">14. Комплаенс и персональные данные</h2>
<p><span class="tag tag-partial">Частично</span> — техника есть; юридические политики согласует заказчик.</p>
<h3>14.1 Область применения</h3>
<p>Продукт обрабатывает: учётные данные, переписку и метаданные, файлы, журнал admin-действий. <b>Заказчик</b> — оператор персональных данных в своём контуре.</p>
<h3>14.2 Принципы</h3>
<table>
  <tr><th>Принцип</th><th>Что значит</th></tr>
  <tr><td>Прозрачность</td><td>Админ видит, кто менял политики и запускал export</td></tr>
  <tr><td>Минимизация</td><td>Сроки хранения и автоудаление (TTL, ретенция)</td></tr>
  <tr><td>Заморозка по закону</td><td>Legal hold останавливает удаление</td></tr>
  <tr><td>Выгрузка по запросу</td><td>Export для расследования или ответа субъекту ПДн</td></tr>
  <tr><td>Разделение org</td><td>Данные разных организаций изолированы</td></tr>
</table>
<h3>14.3 Сроки хранения</h3>
<table>
  <tr><th>Уровень</th><th>Описание</th><th>Кто настраивает</th></tr>
  <tr><td>Оперативный</td><td>Последние сообщения — быстрый доступ</td><td>Админ</td></tr>
  <tr><td>Архив метаданных</td><td>История «когда кто писал»</td><td>Админ + фоновые службы</td></tr>
  <tr><td>Глубокий архив</td><td>Сжатые старые тексты и файлы</td><td>Админ</td></tr>
  <tr><td>Legal hold</td><td>Ничего не удаляется автоматически</td><td>Админ по запросу юристов</td></tr>
</table>
<h3>14.4 Экспорт данных</h3>
<table>
  <tr><th>Компонент</th><th>Содержание</th><th>Статус</th></tr>
  <tr><td>export.json</td><td>Метаданные, сообщения, индикатор полноты</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>attachments/</td><td>Файлы (опционально)</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Блок полноты (GDPR)</td><td>Какие обязательные поля включены</td><td><span class="tag tag-partial">Частично</span></td></tr>
</table>
<h3>14.5 E2EE и расследования</h3>
<table>
  <tr><th>Ситуация</th><th>Что в export</th></tr>
  <tr><td>Обычный чат</td><td>Полный текст</td></tr>
  <tr><td>E2EE</td><td>Шифротекст + метаданные; расшифровка только на клиенте</td></tr>
</table>
<h3>14.6 Права субъекта ПДн</h3>
<table>
  <tr><th>Право</th><th>Как в продукте</th><th>Статус</th></tr>
  <tr><td>Доступ</td><td>Export своих чатов, профиль</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Исправление</td><td>Профиль, edit message</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Удаление</td><td>Soft-delete, TTL, ретенция</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Переносимость</td><td>Export JSON/ZIP</td><td><span class="tag tag-done">Реализовано</span></td></tr>
</table>
<p>Export-файлы на сервере хранятся ~30 дней (настраивается), затем удаляются по политике.</p>

<hr/>
<h2 id="s15">15. Доступность и SLA</h2>
{FIG_SLA}
{FIG_MSG}
<h3>15.1 Определения</h3>
<table>
  <tr><th>Термин</th><th>Простое определение</th></tr>
  <tr><td>Доступность</td><td>Доля времени, когда можно войти и отправить сообщение</td></tr>
  <tr><td>RPO</td><td>Максимально допустимая потеря данных при аварии</td></tr>
  <tr><td>RTO</td><td>Максимальное время восстановления сервиса</td></tr>
  <tr><td>Деградация</td><td>Частичная работа (например, без push или без полнотекстового поиска)</td></tr>
</table>
<h3>15.2 Доступность по профилю</h3>
<table>
  <tr><th>Профиль</th><th>Доступность*</th><th>RPO</th><th>RTO</th></tr>
  <tr><td>Pilot</td><td>99,0% / месяц</td><td>24 ч</td><td>4 ч</td></tr>
  <tr><td>Standard</td><td>99,5%</td><td>8 ч</td><td>2 ч</td></tr>
  <tr><td>Enterprise</td><td>99,9%</td><td>15 мин</td><td>15 мин</td></tr>
</table>
<p class="small">* Без учёта форс-мажора и сети заказчика.</p>
<h3>15.3 Деградация (сообщения продолжают идти)</h3>
<table>
  <tr><th>Отказ</th><th>Для пользователя</th></tr>
  <tr><td>Поисковый индекс</td><td>Поиск медленнее или недоступен; чаты работают</td></tr>
  <tr><td>Push</td><td>Нет push; сообщения при открытии приложения</td></tr>
  <tr><td>Export в очереди</td><td>Export отложен</td></tr>
  <tr><td>Основная база данных</td><td><b>Critical</b> — запись недоступна</td></tr>
</table>

<hr/>
<h2 id="s16">16. Профили и ограничения для пользователя</h2>
<table>
  <tr><th>Возможность</th><th>Pilot</th><th>Standard</th><th>Enterprise</th></tr>
  <tr><td>Чаты, файлы, realtime</td><td>✓</td><td>✓</td><td>✓</td></tr>
  <tr><td>«Хранилище», read receipts</td><td>✓</td><td>✓</td><td>✓</td></tr>
  <tr><td>Поиск по тексту сообщений</td><td>SQL, медленнее</td><td>Быстрый полнотекст</td><td>Кластер поиска</td></tr>
  <tr><td>Export, legal hold</td><td>✓ базовая</td><td>✓ полная</td><td>✓ + compliance pack</td></tr>
  <tr><td>Web Push</td><td>опционально</td><td>✓</td><td>✓ HA</td></tr>
  <tr><td>Видеозвонки</td><td>✓</td><td>✓</td><td>✓ + SFU (planned)</td></tr>
  <tr><td>Max пользователей</td><td><b>10 000</b></td><td><b>100 000</b></td><td><b>1 000 000</b></td></tr>
  <tr><td>Пик онлайн</td><td>~750</td><td>~5 000</td><td>~20 000+</td></tr>
</table>
<h3>Что сообщить сотрудникам при Pilot</h3>
<ol>
  <li>Поиск по очень старым сообщениям может быть медленнее.</li>
  <li>Массовые рассылки всем сразу — в пределах ~8–15 msg/s.</li>
  <li>E2EE в prod — после formal sign-off.</li>
</ol>
""")

    # §17 Appendices
    parts.append(f"""
<hr/>
<h2 id="s17">17. Приложения</h2>

<h3>Приложение A. Каталог функций (кратко)</h3>
<table>
  <tr><th>№</th><th>Функция</th><th>Статус</th></tr>
  <tr><td>A1–A6</td><td>Чаты 1:1 и группы, edit/delete/forward/pin/reactions</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>A7–A12</td><td>Reply, typing, unread, read receipts</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>A13–A16</td><td>Ban, mute, filter, block</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>A17–A20</td><td>Импорт контактов, поиск, public links, export</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>A21–A22</td><td>Видеозвонок, screen share</td><td><span class="tag tag-partial">Частично</span></td></tr>
  <tr><td>A23–A24</td><td>E2EE legacy / MLS</td><td><span class="tag tag-partial">Частично</span></td></tr>
  <tr><td>A25–A26</td><td>Web Push, PWA</td><td><span class="tag tag-partial">Частично</span></td></tr>
  <tr><td>A27–A31</td><td>Admin, ретенция, legal hold, audit, deep archive</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>A32–A34</td><td>Bot API, Live HLS, Mobile apps</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
</table>

<h3>Приложение B. Глоссарий для юристов</h3>
<table>
  <tr><th>Термин</th><th>Определение</th></tr>
  <tr><td>Оператор ПДн</td><td>Организация-заказчик, определяющая цели обработки</td></tr>
  <tr><td>Субъект ПДн</td><td>Сотрудник / пользователь мессенджера</td></tr>
  <tr><td>Legal hold</td><td>Запрет автоудаления по юридическому требованию</td></tr>
  <tr><td>Purge</td><td>Окончательное удаление по политике</td></tr>
  <tr><td>Completeness</td><td>Индикатор полноты export-пакета</td></tr>
  <tr><td>Multi-tenant</td><td>Изоляция данных разных организаций</td></tr>
</table>

<h3>Приложение F. Чеклист go-live (заказчик)</h3>
<table>
  <tr><th>#</th><th>Вопрос</th><th>Ответственный</th><th>☐</th></tr>
  <tr><td>F1</td><td>Утверждены сроки хранения?</td><td>Юрист + IT</td><td>☐</td></tr>
  <tr><td>F2</td><td>Политика export и место хранения файлов?</td><td>ИБ</td><td>☐</td></tr>
  <tr><td>F3</td><td>Legal hold документирован?</td><td>Юрист</td><td>☐</td></tr>
  <tr><td>F4</td><td>E2EE и export согласованы?</td><td>Security</td><td>☐</td></tr>
  <tr><td>F5</td><td>Выбран профиль Pilot/Standard/Enterprise?</td><td>CIO</td><td>☐</td></tr>
  <tr><td>F6</td><td>RPO/RTO в договоре?</td><td>CIO + ops</td><td>☐</td></tr>
  <tr><td>F7</td><td>Backup/restore test запланирован?</td><td>Ops</td><td>☐</td></tr>
  <tr><td>F8</td><td>Обязательные поля export?</td><td>Compliance</td><td>☐</td></tr>
</table>

<h3>Приложение G. Допущения и риски</h3>
<table>
  <tr><th>Риск</th><th>Вероятность</th><th>Влияние</th><th>Митигация</th></tr>
  <tr><td>Перегрузка при пике 100k+</td><td>Средняя</td><td>Высокое</td><td>§12 оптимизация, масштабирование</td></tr>
  <tr><td>Потеря данных Pilot</td><td>Средняя</td><td>Высокое</td><td>Backup §15; upgrade профиля</td></tr>
  <tr><td>Неполный export</td><td>Низкая</td><td>Критическое</td><td>Strict mode, F8</td></tr>
  <tr><td>E2EE блокирует расследование</td><td>Средняя</td><td>Среднее</td><td>Политика до enable</td></tr>
  <tr><td>Рост диска</td><td>Средняя</td><td>Среднее</td><td>Сжатие архива, ретенция</td></tr>
</table>
<p><b>Не входит в v1:</b> мобильные приложения; server-side расшифровка E2EE; SLA Enterprise на Pilot-железе.</p>

{render_appendix_i_html()}
""")

    # §18 Cost - the key section for sales/accounting
    parts.append(f"""
<hr/>
<h2 id="s18">18. Стоимость владения — примеры расчётов</h2>
{FIG_COST}

<div class="note">
  <div class="req">Для продаж и бухгалтерии</div>
  <div class="comment">
    Ниже — <b>учебные примеры</b> на условных тарифах (руб./мес.). Реальная стоимость зависит от: own hardware vs облако vs colocation,
    регион, скидки поставщика, НДС, трудозатрат внедрения и поддержки. Используйте таблицы как <b>шаблон для КП</b>, подставляя свои ставки в колонку «Цена за ед.».
  </div>
</div>

<h3>18.1 Условные тарифы (шаблон)</h3>
<table>
  <tr><th>Статья</th><th>Единица</th><th>Условная цена</th><th>Комментарий</th></tr>
  <tr><td>Сервер приложений</td><td>16 ГБ RAM, 8 vCPU</td><td class="money">28 000 ₽/мес</td><td>On-prem амортизация или облако</td></tr>
  <tr><td>Сервер приложений</td><td>32 ГБ RAM, 8 vCPU</td><td class="money">45 000 ₽/мес</td><td>Standard tier</td></tr>
  <tr><td>Веб + балансировщик</td><td>8 ГБ RAM, 4 vCPU</td><td class="money">15 000 ₽/мес</td><td>2 реплики для Pilot</td></tr>
  <tr><td>Диск SSD</td><td>1 ТБ</td><td class="money">3 500 ₽/мес</td><td>Оперативные данные</td></tr>
  <tr><td>Диск HDD (архив)</td><td>1 ТБ</td><td class="money">800 ₽/мес</td><td>Файлы и deep archive</td></tr>
  <tr><td>TURN (звонки)</td><td>4 vCPU</td><td class="money">12 000 ₽/мес</td><td>Опционально Pilot</td></tr>
  <tr><td>Мониторинг, backup</td><td>контур</td><td class="money">5 000 ₽/мес</td><td>Базовый ops</td></tr>
  <tr><td>Канал связи</td><td>200 Мбит/с</td><td class="money">8 000 ₽/мес</td><td>10k Pilot</td></tr>
  <tr><td>Канал связи</td><td>1 Гбит/с</td><td class="money">35 000 ₽/мес</td><td>100k Standard</td></tr>
</table>

<h3>18.2 Пример A — Pilot, 10 000 пользователей (оптимизированный профиль)</h3>
<div class="cost-box">
<p><b>Исходные:</b> 10 000 зарегистрированных; ~5 000 активных в день; ~750 одновременно онлайн; диск ~5 ТБ на первый год (§10.4).</p>
<table>
  <tr><th>Статья расходов</th><th>Кол-во</th><th>Цена/ед.</th><th>₽/мес</th></tr>
  <tr><td>Сервер приложений (16 ГБ, Pilot compose)</td><td>1</td><td>28 000</td><td class="money">28 000</td></tr>
  <tr><td>Веб-шлюз (8 ГБ)</td><td>1</td><td>15 000</td><td class="money">15 000</td></tr>
  <tr><td>Диск SSD (500 ГБ hot DB)</td><td>0,5 ТБ</td><td>3 500</td><td class="money">1 750</td></tr>
  <tr><td>Диск HDD (4,5 ТБ файлы+архив)</td><td>4,5 ТБ</td><td>800</td><td class="money">3 600</td></tr>
  <tr><td>Канал 200 Мбит/с</td><td>1</td><td>8 000</td><td class="money">8 000</td></tr>
  <tr><td>Backup + мониторинг</td><td>1</td><td>5 000</td><td class="money">5 000</td></tr>
  <tr><td>TURN (опционально)</td><td>1</td><td>12 000</td><td class="money">12 000</td></tr>
  <tr><th colspan="3">Итого infra (с TURN)</th><th class="money">73 350</th></tr>
  <tr><th colspan="3">Итого infra (без TURN)</th><th class="money">61 350</th></tr>
</table>
<p><b>На пользователя:</b> 61 350 ÷ 10 000 = <span class="money">~6,1 ₽/пользователь/мес</span> (только инфраструктура).</p>
<p><b>Сравнение с baseline (~64 ГБ без оптимизации):</b> потребовалось бы ~2 сервера по 32 ГБ + полный Solr ≈ <span class="money">~96 000 ₽/мес</span> — экономия Pilot-профиля <b>~36%</b>.</p>
</div>

<h3>18.3 Пример B — Standard, 100 000 пользователей</h3>
<div class="cost-box">
<table>
  <tr><th>Статья</th><th>Кол-во</th><th>₽/мес</th></tr>
  <tr><td>Серверы приложений 64 ГБ (×3)</td><td>3</td><td class="money">135 000</td></tr>
  <tr><td>Web + LB (×2)</td><td>2</td><td class="money">30 000</td></tr>
  <tr><td>PostgreSQL primary + replica</td><td>2</td><td class="money">90 000</td></tr>
  <tr><td>Redis cluster</td><td>1</td><td class="money">25 000</td></tr>
  <tr><td>Solr + ZK (×3)</td><td>3</td><td class="money">75 000</td></tr>
  <tr><td>MinIO / файлы (~30 ТБ HDD baseline)</td><td>30 ТБ</td><td class="money">24 000</td></tr>
  <tr><td>NATS, Keycloak, workers</td><td>набор</td><td class="money">45 000</td></tr>
  <tr><td>Канал 1 Гбит/с</td><td>1</td><td class="money">35 000</td></tr>
  <tr><td>Ops (backup, monitoring)</td><td>1</td><td class="money">15 000</td></tr>
  <tr><th>Итого baseline</th><th></th><th class="money">~474 000</th></tr>
  <tr><th>Итого после оптимизации (§12, −30% RAM+disk)</th><th></th><th class="money">~332 000</th></tr>
</table>
<p><b>На пользователя (оптимиз.):</b> 332 000 ÷ 100 000 = <span class="money">~3,3 ₽/пользователь/мес</span>.</p>
</div>

<h3>18.4 Пример C — TCO на 3 года (Pilot 10k)</h3>
<div class="cost-box">
<table>
  <tr><th>Статья</th><th>Разово</th><th>₽/мес × 36 мес</th><th>Итого 3 года</th></tr>
  <tr><td>Infra (без TURN)</td><td>—</td><td>61 350</td><td class="money">2 208 600</td></tr>
  <tr><td>Внедрение (развёртывание, обучение admin)</td><td class="money">600 000</td><td>—</td><td class="money">600 000</td></tr>
  <tr><td>Поддержка L2 (15% от infra/год)</td><td>—</td><td>~9 200</td><td class="money">331 200</td></tr>
  <tr><th colspan="3">TCO 3 года</th><th class="money">~3 140 000</th></tr>
</table>
<p><b>TCO на пользователя за 3 года:</b> 3 140 000 ÷ 10 000 = <span class="money">~314 ₽/пользователь</span> (~8,7 ₽/мес all-in с внедрением).</p>
</div>

<h3>18.5 Пример D — рост диска (бухгалтерия, 100k users)</h3>
<div class="cost-box">
<p><b>Формула:</b> Диск файлов/год ≈ Пользователи × ГБ на пользователя/год</p>
<p>100 000 × 0,4 ГБ = <b>40 ТБ новых файлов в год</b></p>
<p>При тарифе 800 ₽/ТБ/мес HDD: 40 × 800 = <span class="money">32 000 ₽/мес</span> <b>дополнительно</b> каждый год (если не сжимать архив).</p>
<p>С оптимизацией §12 (dedup −35%, zstd archive −60% на телах): ориентир <span class="money">~18 000 ₽/мес</span> прироста вместо 32 000.</p>
</div>

<h3>18.6 Строки для коммерческого предложения (шаблон)</h3>
<table>
  <tr><th>Позиция КП</th><th>Ед.</th><th>Пример цены</th></tr>
  <tr><td>Лицензия / право использования (perpetual или subscription)</td><td>пользователь/год</td><td>по прайсу вендора</td></tr>
  <tr><td>Внедрение Pilot</td><td>контур</td><td class="money">400 000 – 800 000 ₽</td></tr>
  <tr><td>Внедрение Standard</td><td>контур</td><td class="money">1 500 000 – 3 000 000 ₽</td></tr>
  <tr><td>Обучение администраторов (1 день)</td><td>сессия</td><td class="money">50 000 – 120 000 ₽</td></tr>
  <tr><td>Поддержка L2 (8×5)</td><td>% от infra или фикс</td><td class="money">10–18%</td></tr>
  <tr><td>Infra (см. примеры A–B)</td><td>мес</td><td>по §18.2–18.3</td></tr>
</table>

<div class="warn">
  <div class="req">Налоги и учёт</div>
  <div class="comment">Infra может учитываться как OPEX (облако/аренда) или амортизация CAPEX (собственные серверы). Лицензия ПО — по политике заказчика (немaterial актив или subscription). Уточняйте у бухгалтерии заказчика до фиксации КП.</div>
</div>

{render_section_18_7()}
""")

    parts.append("""
<hr/>
<p class="small" style="margin-top:40px;padding-top:20px;border-top:2px solid #e5e7eb;">
  <b>Korus Messenger (AvandocMsg)</b> — Продуктовая презентация v2.2, 15 июня 2026.<br/>
  Единый автономный документ для заказчика. Все цифры — ориентиры для планирования, не оферта.<br/>
  Статус продукта на дату документа: веб-клиент и сервер реализованы; mobile, Bot API, Live — в планах.
</p>
</body>
</html>
""")
