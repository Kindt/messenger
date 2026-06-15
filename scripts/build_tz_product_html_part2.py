# Part 2: sections 11-18 and appendices — appended by build script

from html import escape

from product_status import PLAYWRIGHT_DATE, PLAYWRIGHT_PASSED, PRODUCT_DATE, PRODUCT_VERSION  # noqa: E402
from tz_product_resources import render_appendix_i_html
from tz_product_pricing import (
    PILOT_PROFILE,
    PRICE_AS_OF,
    STANDARD_PROFILE,
    STANDARD_OPT_RATIO,
    cloud_pilot_monthly,
    fmt_rub,
    fmt_rub_short,
    pilot_fullstack_monolith_monthly,
    pilot_profile_monthly,
    render_legend_rate_table_html,
    render_price_methodology_html,
    render_section_18_examples_html,
    standard_profile_monthly,
)


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


def render_section_17_7() -> str:
    pilot_items = [(line.label, line.monthly, line.color) for line in PILOT_PROFILE]
    std_items = [
        (line.label, round(line.monthly * STANDARD_OPT_RATIO), line.color)
        for line in STANDARD_PROFILE
    ]
    pilot_total = pilot_profile_monthly() * 12
    std_total = standard_profile_monthly() * 12

    compare = [
        ("Pilot 10k", pilot_total, "#86efac"),
        ("Standard 100k", std_total, "#6366f1"),
    ]
    max_annual = max(v for _, v, _ in compare)

    bar_w, gap = 120, 80
    chart_left, chart_right = 120, 600
    base_y1, top_y1 = 268, 68
    chart_h1 = base_y1 - top_y1
    x0 = chart_left + 20

    y_ticks = []
    for i in range(5):
        val = max_annual * i // 4
        y = base_y1 - chart_h1 * i // 4
        y_ticks.append(
            f'<line x1="{chart_left}" y1="{y}" x2="{chart_right}" y2="{y}" stroke="#e5e7eb" stroke-width="1"/>'
            f'<text x="{chart_left - 8}" y="{y + 4}" text-anchor="end" font-size="9" fill="#4b5563">{fmt_rub_short(val)}</text>'
        )

    compare_bars = []
    compare_labels = []
    for i, (profile, annual, color) in enumerate(compare):
        x = x0 + i * (bar_w + gap)
        h = max(6, round(annual / max_annual * chart_h1))
        y = base_y1 - h
        compare_bars.append(
            f'<rect x="{x}" y="{y}" width="{bar_w}" height="{h}" fill="{color}" rx="3"/>'
            f'<text x="{x + bar_w // 2}" y="{y - 8}" text-anchor="middle" font-size="9" font-weight="600">{fmt_rub(annual)}</text>'
        )
        compare_labels.append(
            f'<text x="{x + bar_w // 2}" y="{base_y1 + 18}" text-anchor="middle" font-size="10" font-weight="600">{escape(profile)}</text>'
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

    legend_items = list(PILOT_PROFILE) + list(STANDARD_PROFILE)
    seen: set[str] = set()
    unique_legend: list[tuple[str, str]] = []
    for line in legend_items:
        if line.label not in seen:
            seen.add(line.label)
            unique_legend.append((line.label, line.color))
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
    std_by_name = {line.label: round(line.monthly * STANDARD_OPT_RATIO) for line in STANDARD_PROFILE}
    for line in PILOT_PROFILE:
        std_m = std_by_name.get(line.label)
        std_cell = f'<td class="money">{fmt_rub(std_m * 12)}</td>' if std_m else "<td>—</td>"
        table_rows.append(
            f"<tr><td>{escape(line.label)}</td>"
            f'<td class="money">{fmt_rub(line.monthly * 12)}</td>{std_cell}</tr>'
        )
    for line in STANDARD_PROFILE:
        if line.label in {ln.label for ln in PILOT_PROFILE}:
            continue
        std_m = round(line.monthly * STANDARD_OPT_RATIO)
        table_rows.append(
            f"<tr><td>{escape(line.label)}</td><td>—</td>"
            f'<td class="money">{fmt_rub(std_m * 12)}</td></tr>'
        )
    table_rows.append(
        f'<tr><th>Итого infra / год</th>'
        f'<th class="money">{fmt_rub(pilot_total)}</th>'
        f'<th class="money">{fmt_rub(std_total)}</th></tr>'
    )

    legend_rates_html = render_legend_rate_table_html(tuple(legend_items))

    monolith_annual = pilot_fullstack_monolith_monthly() * 12

    return f"""
<h3 id="s17-7">17.7 Сравнение стоимости серверов за год</h3>
<p class="small">Все суммы — <b>только инфраструктура</b> (аренда/амортизация серверов, дисков, канала, ops).
  Расчёт: <b>кол-во × ставка §17.1</b> (дата {PRICE_AS_OF}), затем × 12 для годовых столбцов.</p>

<table>
  <tr><th>Сервер / статья</th><th>Pilot 10k ₽/год</th><th>Standard 100k ₽/год</th></tr>
  {''.join(table_rows)}
</table>

<figure class="fig">
<svg viewBox="0 0 720 {svg_h}" width="720" height="{svg_h}" xmlns="http://www.w3.org/2000/svg">
  <text x="360" y="28" text-anchor="middle" font-size="14" font-weight="bold">Стоимость infra за год по профилю (₽)</text>

  <text x="360" y="52" text-anchor="middle" font-size="11" font-weight="600" fill="#374151">Итог за 12 месяцев</text>
  <line x1="{chart_left}" y1="{base_y1}" x2="{chart_right}" y2="{base_y1}" stroke="#9ca3af"/>
  <line x1="{chart_left}" y1="{base_y1}" x2="{chart_left}" y2="{top_y1}" stroke="#9ca3af"/>
  {''.join(y_ticks)}
  {''.join(compare_bars)}
  {''.join(compare_labels)}
  <text x="360" y="302" text-anchor="middle" font-size="9" fill="#6b7280">Справочно: full-stack monolith 10k — {fmt_rub(monolith_annual)}/год</text>

  <line x1="40" y1="{divider_y}" x2="680" y2="{divider_y}" stroke="#d1d5db"/>
  <text x="360" y="{subtitle2_y}" text-anchor="middle" font-size="11" font-weight="600" fill="#374151">Состав годовой стоимости</text>

  <line x1="120" y1="{base_y2}" x2="480" y2="{base_y2}" stroke="#9ca3af"/>
  <line x1="120" y1="{stack_top}" x2="120" y2="{base_y2}" stroke="#9ca3af"/>
  {_stacked_bar_svg(pilot_items, pilot_x, stack_w, base_y2, pilot_bar_h)}
  {_stacked_bar_svg(std_items, std_x, stack_w, base_y2, stack_h)}
  <text x="{pilot_x + stack_w // 2}" y="{profile_label_y1}" text-anchor="middle" font-size="10">Pilot 10k</text>
  <text x="{pilot_x + stack_w // 2}" y="{profile_label_y2}" text-anchor="middle" font-size="9" fill="#6b7280">{fmt_rub(pilot_total)}</text>
  <text x="{std_x + stack_w // 2}" y="{profile_label_y1}" text-anchor="middle" font-size="10">Standard 100k</text>
  <text x="{std_x + stack_w // 2}" y="{profile_label_y2}" text-anchor="middle" font-size="9" fill="#6b7280">{fmt_rub(std_total)}</text>
  {''.join(legend_svg)}
</svg>
<figcaption class="fig-cap">Рис. 8. Годовая стоимость infra: Σ(кол-во × §17.1) × 12; дата ставок {PRICE_AS_OF}. Не оферта.</figcaption>
</figure>

<h4>Ставки за единицу (легенда нижнего ряда)</h4>
<p class="small">Цвета сегментов на диаграмме соответствуют статьям §17.2–17.3. Сумма сегмента = кол-во × ставка из таблицы §17.1.</p>
{legend_rates_html}

<div class="note">
  <div class="req">Как читать диаграмму</div>
  <div class="comment">
    <b>Верхний ряд</b> — итоговая стоимость infra за год для профилей Pilot и Standard.
    <b>Нижний ряд</b> — состав годовой суммы по статьям; таблица выше — откуда взята каждая ставка за единицу.
    Лицензия ПО, внедрение и L2 — §17.4 и §17.6.
  </div>
</div>
"""


def append_sections_11_18(parts, FIG_MSG, FIG_SLA, FIG_COST):
    parts.append(f"""
<hr/>
<h2 id="s11">11. Критерии приёмки продукта</h2>
<ol>
  <li><b>Пользователь</b> выполняет все основные сценарии из §5 через веб-браузер.</li>
  <li><b>Автотесты UI:</b> {PLAYWRIGHT_PASSED}/{PLAYWRIGHT_PASSED} Playwright на QEMU ({PLAYWRIGHT_DATE}); см. runtime-gate-report spec 002.</li>
  <li><b>Администратор</b> управляет организациями, политиками хранения и аудитом через веб-консоль.</li>
  <li><b>Production:</b> HTTPS, секреты не в открытом виде, sign-off E2EE перед массовым включением.</li>
  <li><b>Экспорт и ретенция:</b> окончательное удаление только после прохождения export gate.</li>
  <li><b>Локализация:</b> интерфейс минимум на русском и английском.</li>
  <li><b>Развёртывание:</b> профиль Pilot или Standard разворачивается по документации; load test на stage при go-live.</li>
  <li><b>Комплаенс (§13):</b> export с индикатором полноты; legal hold блокирует purge; audit admin-действий.</li>
  <li><b>SLA (§14):</b> согласованные RPO/RTO по профилю; сообщения не теряются при деградации P1.</li>
  <li><b>Профиль (§15):</b> ограничения Pilot доведены до заказчика до запуска.</li>
  <li><b>Интеграции (§12):</b> REST API и WebSocket реализованы; SSO и публичный Bot API — в roadmap.</li>
</ol>

<hr/>
<h2 id="s12">12. Интеграции и экосистема</h2>
<p><span class="tag tag-partial">Частично</span></p>
<h3>12.1 Уже доступно</h3>
<table>
  <tr><th>Канал</th><th>Для кого</th><th>Что даёт</th><th>Статус</th></tr>
  <tr><td>Программный интерфейс (API)</td><td>IT, скрипты</td><td>Чаты, сообщения, файлы, admin</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Мгновенная доставка (WebSocket)</td><td>Клиенты</td><td>Realtime, «печатает…»</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Система входа (Keycloak)</td><td>IT</td><td>JWT, локальные пользователи</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Export / replay</td><td>Compliance</td><td>JSON/ZIP выгрузка</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Web Push</td><td>Пользователи</td><td>SW + push-worker; VAPID Ansible ✓</td><td><span class="tag tag-partial">Частично</span></td></tr>
</table>
<h3>12.2 Запланировано</h3>
<table>
  <tr><th>Сценарий</th><th>Ожидание</th><th>Статус</th></tr>
  <tr><td>SSO Google / корпоративный портал</td><td>Единый вход без отдельного пароля</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
  <tr><td>LDAP / Active Directory</td><td>Учётки из AD</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
  <tr><td>Bot API (REST / long-poll)</td><td>Service Desk, опросы, @mention</td><td><span class="tag tag-planned">Запланировано</span> — есть MVP webhook worker (<code>bot-delivery</code>), не публичный API</td></tr>
</table>
<p><b>Безопасность:</b> webhook только HTTPS; секреты не логируются; сбой бота не блокирует переписку людей.</p>

<hr/>
<h2 id="s13">13. Комплаенс и персональные данные</h2>
<p><span class="tag tag-partial">Частично</span> — техника есть; юридические политики согласует заказчик.</p>
<h3>13.1 Область применения</h3>
<p>Продукт обрабатывает: учётные данные, переписку и метаданные, файлы, журнал admin-действий. <b>Заказчик</b> — оператор персональных данных в своём контуре.</p>
<h3>13.2 Принципы</h3>
<table>
  <tr><th>Принцип</th><th>Что значит</th></tr>
  <tr><td>Прозрачность</td><td>Админ видит, кто менял политики и запускал export</td></tr>
  <tr><td>Минимизация</td><td>Сроки хранения и автоудаление (TTL, ретенция)</td></tr>
  <tr><td>Заморозка по закону</td><td>Legal hold останавливает удаление</td></tr>
  <tr><td>Выгрузка по запросу</td><td>Export для расследования или ответа субъекту ПДн</td></tr>
  <tr><td>Разделение org</td><td>Данные разных организаций изолированы</td></tr>
</table>
<h3>13.3 Сроки хранения</h3>
<table>
  <tr><th>Уровень</th><th>Описание</th><th>Кто настраивает</th></tr>
  <tr><td>Оперативный</td><td>Последние сообщения — быстрый доступ</td><td>Админ</td></tr>
  <tr><td>Архив метаданных</td><td>История «когда кто писал»</td><td>Админ + фоновые службы</td></tr>
  <tr><td>Глубокий архив</td><td>Сжатые старые тексты и файлы</td><td>Админ</td></tr>
  <tr><td>Legal hold</td><td>Ничего не удаляется автоматически</td><td>Админ по запросу юристов</td></tr>
</table>
<h3>13.4 Экспорт данных</h3>
<table>
  <tr><th>Компонент</th><th>Содержание</th><th>Статус</th></tr>
  <tr><td>export.json</td><td>Метаданные, сообщения, индикатор полноты</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>attachments/</td><td>Файлы (опционально)</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Блок полноты (GDPR)</td><td>Какие обязательные поля включены</td><td><span class="tag tag-partial">Частично</span></td></tr>
</table>
<h3>13.5 E2EE и расследования</h3>
<table>
  <tr><th>Ситуация</th><th>Что в export</th></tr>
  <tr><td>Обычный чат</td><td>Полный текст</td></tr>
  <tr><td>E2EE</td><td>Шифротекст + метаданные; расшифровка только на клиенте</td></tr>
</table>
<h3>13.6 Права субъекта ПДн</h3>
<table>
  <tr><th>Право</th><th>Как в продукте</th><th>Статус</th></tr>
  <tr><td>Доступ</td><td>Export своих чатов, профиль</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Исправление</td><td>Профиль, edit message</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Удаление</td><td>Soft-delete, TTL, ретенция</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Переносимость</td><td>Export JSON/ZIP</td><td><span class="tag tag-done">Реализовано</span></td></tr>
</table>
<p>Export-файлы на сервере хранятся ~30 дней (настраивается), затем удаляются по политике.</p>

<hr/>
<h2 id="s14">14. Доступность и SLA</h2>
{FIG_SLA}
{FIG_MSG}
<h3>14.1 Определения</h3>
<table>
  <tr><th>Термин</th><th>Простое определение</th></tr>
  <tr><td>Доступность</td><td>Доля времени, когда можно войти и отправить сообщение</td></tr>
  <tr><td>RPO</td><td>Максимально допустимая потеря данных при аварии</td></tr>
  <tr><td>RTO</td><td>Максимальное время восстановления сервиса</td></tr>
  <tr><td>Деградация</td><td>Частичная работа (например, без push или без полнотекстового поиска)</td></tr>
</table>
<h3>14.2 Доступность по профилю</h3>
<table>
  <tr><th>Профиль</th><th>Доступность*</th><th>RPO</th><th>RTO</th></tr>
  <tr><td>Pilot</td><td>99,0% / месяц</td><td>24 ч</td><td>4 ч</td></tr>
  <tr><td>Standard</td><td>99,5%</td><td>8 ч</td><td>2 ч</td></tr>
  <tr><td>Enterprise</td><td>99,9%</td><td>15 мин</td><td>15 мин</td></tr>
</table>
<p class="small">* Без учёта форс-мажора и сети заказчика.</p>
<h3>14.3 Деградация (сообщения продолжают идти)</h3>
<table>
  <tr><th>Отказ</th><th>Для пользователя</th></tr>
  <tr><td>Поисковый индекс</td><td>Поиск медленнее или недоступен; чаты работают</td></tr>
  <tr><td>Push</td><td>Нет push; сообщения при открытии приложения</td></tr>
  <tr><td>Export в очереди</td><td>Export отложен</td></tr>
  <tr><td>Основная база данных</td><td><b>Critical</b> — запись недоступна</td></tr>
</table>

<hr/>
<h2 id="s15">15. Профили и ограничения для пользователя</h2>
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

    # §16 Appendices
    parts.append(f"""
<hr/>
<h2 id="s16">16. Приложения</h2>

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
  <tr><td>Перегрузка при пике 100k+</td><td>Средняя</td><td>Высокое</td><td>Масштабирование Standard, load test</td></tr>
  <tr><td>Потеря данных Pilot</td><td>Средняя</td><td>Высокое</td><td>Backup §14; upgrade профиля</td></tr>
  <tr><td>Неполный export</td><td>Низкая</td><td>Критическое</td><td>Strict mode, F8</td></tr>
  <tr><td>E2EE блокирует расследование</td><td>Средняя</td><td>Среднее</td><td>Политика до enable</td></tr>
  <tr><td>Рост диска</td><td>Средняя</td><td>Среднее</td><td>Сжатие архива, ретенция</td></tr>
</table>
<p><b>Не входит в v1:</b> мобильные приложения; server-side расшифровка E2EE; SLA Enterprise на Pilot-железе.</p>

{render_appendix_i_html()}
""")

    # §17 Cost
    parts.append(f"""
<hr/>
<h2 id="s17">17. Стоимость владения — примеры расчётов</h2>
{FIG_COST}
<p class="small"><b>Рис. 7 — как считаются столбцы:</b> «Pilot» = Σ строк §17.2 ({fmt_rub(pilot_profile_monthly())}/мес);
  «Monolith (справ.)» = full-stack на одном хосте ({fmt_rub(pilot_fullstack_monolith_monthly())}/мес);
  «Облако (анalog)» = Σ §17.3.1 ({fmt_rub(cloud_pilot_monthly())}/мес). Ставки — §17.1, дата {PRICE_AS_OF}.</p>

{render_price_methodology_html()}
{render_section_18_examples_html()}

{render_section_17_7()}
""")

    parts.append(f"""
<hr/>
<p class="small" style="margin-top:40px;padding-top:20px;border-top:2px solid #e5e7eb;">
  <b>Korus Messenger (AvandocMsg)</b> — Продуктовая презентация v{PRODUCT_VERSION}, {PRODUCT_DATE}.<br/>
  Единый автономный документ для заказчика. Все цифры — ориентиры для планирования, не оферта.<br/>
  Статус на дату: веб-клиент и сервер реализованы; Playwright {PLAYWRIGHT_PASSED}/{PLAYWRIGHT_PASSED} ({PLAYWRIGHT_DATE});
  E2EE/звонки/push/TLS — частично (ops/sign-off); mobile, Live HLS, Bot API — planned/out.
</p>
</body>
</html>
""")
