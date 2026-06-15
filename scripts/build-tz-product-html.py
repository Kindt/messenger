#!/usr/bin/env python3
"""Generate product_presentation.html v2.3 — product presentation for customer (non-technical)."""
from pathlib import Path
import sys

SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))
from build_tz_product_html_part2 import append_sections_11_18  # noqa: E402
from tz_product_pricing import render_fig_cost_monthly_svg  # noqa: E402

OUT = Path(__file__).resolve().parents[1] / "product_presentation.html"
LEGACY_OUT = Path(__file__).resolve().parents[1] / "tz_product.html"

CSS = """
    body { font-family: system-ui, -apple-system, Segoe UI, Roboto, Arial, sans-serif; line-height: 1.45; margin: 24px auto; color: #111; max-width: 980px; padding: 0 16px; }
    h1 { font-size: 24px; margin-top: 0; color: #1e3a5f; }
    h2 { font-size: 19px; border-top: 2px solid #6366f1; padding-top: 18px; margin-top: 32px; color: #1e3a5f; }
    h3 { font-size: 16px; margin-top: 20px; color: #374151; }
    h4 { font-size: 14px; margin: 8px 0 4px; color: #4b5563; }
    table { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 14px; }
    th, td { border: 1px solid #d1d5db; padding: 8px 10px; vertical-align: top; }
    th { background: #eef2ff; text-align: left; font-weight: 600; }
    tr:nth-child(even) { background: #f9fafb; }
    .comment { color: #374151; }
    .req { font-weight: 600; color: #1e3a5f; }
    .note { background: #eff6ff; border: 1px solid #93c5fd; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .warn { background: #fff7ed; border: 1px solid #fdba74; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .cost-box { background: #f0fdf4; border: 1px solid #86efac; padding: 14px; border-radius: 8px; margin: 14px 0; }
    .small { font-size: 12px; color: #6b7280; }
    .toc a { text-decoration: none; color: #4338ca; }
    .toc a:hover { text-decoration: underline; }
    .tag { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: 11px; font-weight: 600; }
    .tag-done { background: #dcfce7; color: #166534; }
    .tag-partial { background: #fef9c3; color: #854d0e; }
    .tag-planned { background: #e0e7ff; color: #3730a3; }
    .tag-out { background: #f3f4f6; color: #4b5563; }
    .case { margin: 10px 0; padding: 12px 14px; background: #fafafa; border-left: 4px solid #6366f1; border-radius: 0 8px 8px 0; }
    .case h4 { margin: 0 0 6px; color: #111; }
    .meta { color: #6b7280; font-size: 13px; margin-bottom: 24px; padding-bottom: 12px; border-bottom: 1px solid #e5e7eb; }
    .fig { margin: 20px 0; text-align: center; }
    .fig + .fig { margin-top: 36px; }
    .fig svg { max-width: 100%; height: auto; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; }
    .fig-cap { font-size: 13px; color: #6b7280; margin-top: 8px; }
    .fig-legend { font-size: 12px; color: #374151; margin-top: 12px; text-align: left; max-width: 820px; margin-left: auto; margin-right: auto; }
    .fig-key { list-style: none; padding: 12px 16px; margin: 12px auto 0; max-width: 820px; background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 8px; font-size: 13px; }
    .fig-key li { display: flex; align-items: center; gap: 12px; margin: 8px 0; line-height: 1.35; }
    .fig-key .key-swatch { flex-shrink: 0; width: 40px; height: 0; border-top-width: 3px; border-top-style: solid; }
    .fig-key .key-main { border-top-color: #2563eb; }
    .fig-key .key-opt { border-top-color: #9ca3af; border-top-style: dashed; }
    .fig-key .key-admin { border-top-color: #db2777; border-top-style: dashed; }
    .fig-sub { font-size: 14px; font-weight: 600; color: #374151; margin: 20px 0 8px; text-align: center; }
    .fig-lc { font-size: 15px; font-weight: 700; color: #1e3a5f; margin: 28px 0 4px; text-align: center; }
    .fig-step { font-size: 13px; color: #6b7280; margin: 10px 0 6px; text-align: center; }
    .fig-row { margin-bottom: 4px; }
    .fig-rule { max-width: 820px; margin: 10px auto 0; font-size: 13px; color: #374151; text-align: center; }
    .arrow-main { stroke: #2563eb; stroke-width: 2; fill: none; }
    .arrow-optional { stroke: #9ca3af; stroke-width: 1.5; fill: none; stroke-dasharray: 5 4; }
    .arrow-admin { stroke: #db2777; stroke-width: 1.5; fill: none; stroke-dasharray: 4 3; }
    .formula { background: #f3f4f6; padding: 12px 16px; border-radius: 8px; font-family: ui-monospace, monospace; font-size: 13px; margin: 10px 0; }
    .money { font-weight: 600; color: #047857; }
    @media print { body { margin: 12px; max-width: none; } .note, .warn, .cost-box, .fig { break-inside: avoid; } }
"""

FIG_ARCH = """<figure class="fig">
<p class="fig-sub">Рис. 1 — два жизненных цикла (единая схема на каждую роль)</p>

<p class="fig-lc">1А. Жизненный цикл пользователя (сотрудник)</p>
<div class="fig-row"><svg viewBox="0 0 860 300" width="860" height="300" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <marker id="um" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="#2563eb"/></marker>
    <marker id="uo" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="#9ca3af"/></marker>
  </defs>
  <rect x="0" y="0" width="860" height="300" rx="10" fill="#fafbff" stroke="#e5e7eb"/>
  <text x="16" y="20" font-size="10" fill="#6b7280">① вход</text>
  <text x="300" y="20" font-size="10" fill="#2563eb">② отправка и сохранение</text>
  <text x="620" y="292" font-size="10" fill="#2563eb">③ уведомление</text>
  <text x="16" y="292" font-size="10" fill="#6b7280">④ вложения и архив</text>
  <!-- login (above gateway, own row) -->
  <rect x="228" y="34" width="120" height="40" rx="6" fill="#f3f4f6" stroke="#9ca3af"/>
  <text x="288" y="59" text-anchor="middle" font-size="11">Система входа</text>
  <line x1="288" y1="74" x2="288" y2="92" class="arrow-optional" marker-end="url(#uo)"/>
  <text x="304" y="86" font-size="9" fill="#6b7280">логин</text>
  <!-- main row y=92 -->
  <rect x="20" y="92" width="92" height="48" rx="6" fill="#dbeafe" stroke="#3b82f6"/>
  <text x="66" y="121" text-anchor="middle" font-size="11">Сотрудник</text>
  <rect x="128" y="92" width="92" height="48" rx="6" fill="#bfdbfe" stroke="#2563eb"/>
  <text x="174" y="121" text-anchor="middle" font-size="11">Браузер</text>
  <rect x="236" y="92" width="92" height="48" rx="6" fill="#e0e7ff" stroke="#6366f1"/>
  <text x="282" y="121" text-anchor="middle" font-size="11">Веб-шлюз</text>
  <rect x="356" y="92" width="92" height="48" rx="6" fill="#e0e7ff" stroke="#6366f1"/>
  <text x="402" y="121" text-anchor="middle" font-size="11">Сервер</text>
  <rect x="464" y="92" width="104" height="48" rx="6" fill="#fef3c7" stroke="#f59e0b"/>
  <text x="516" y="121" text-anchor="middle" font-size="11">База данных</text>
  <line x1="112" y1="116" x2="128" y2="116" class="arrow-main" marker-end="url(#um)"/>
  <line x1="220" y1="116" x2="236" y2="116" class="arrow-main" marker-end="url(#um)"/>
  <line x1="328" y1="116" x2="356" y2="116" class="arrow-main" marker-end="url(#um)"/>
  <line x1="448" y1="116" x2="464" y2="116" class="arrow-main" marker-end="url(#um)"/>
  <text x="582" y="121" font-size="10" fill="#047857">сохранено</text>
  <!-- corridor y=178, bottom row y=220 (all aligned) -->
  <rect x="20" y="220" width="92" height="48" rx="6" fill="#fde68a" stroke="#d97706"/>
  <text x="66" y="249" text-anchor="middle" font-size="11">Архив</text>
  <rect x="142" y="220" width="152" height="48" rx="6" fill="#fef3c7" stroke="#f59e0b"/>
  <text x="218" y="249" text-anchor="middle" font-size="11">Хранилище файлов</text>
  <rect x="510" y="220" width="120" height="48" rx="6" fill="#c7d2fe" stroke="#6366f1"/>
  <text x="570" y="249" text-anchor="middle" font-size="11">Мгнов. доставка</text>
  <rect x="662" y="220" width="130" height="48" rx="6" fill="#bfdbfe" stroke="#2563eb"/>
  <text x="727" y="249" text-anchor="middle" font-size="11">Браузер получателя</text>
  <!-- files branch -->
  <path d="M402 140 L402 178 L218 178 L218 220" class="arrow-optional" fill="none" marker-end="url(#uo)"/>
  <rect x="268" y="166" width="58" height="14" rx="3" fill="#fafbff"/>
  <text x="297" y="177" text-anchor="middle" font-size="9" fill="#6b7280">вложения</text>
  <line x1="142" y1="244" x2="112" y2="244" class="arrow-optional" marker-end="url(#uo)"/>
  <rect x="108" y="204" width="58" height="14" rx="3" fill="#fafbff"/>
  <text x="127" y="215" text-anchor="middle" font-size="9" fill="#6b7280">по срокам</text>
  <!-- notify branch -->
  <path d="M402 140 L402 178 L570 178 L570 220" class="arrow-main" fill="none" marker-end="url(#um)"/>
  <rect x="468" y="166" width="92" height="14" rx="3" fill="#fafbff"/>
  <text x="514" y="177" text-anchor="middle" font-size="9" fill="#2563eb">новое сообщение</text>
  <line x1="630" y1="244" x2="662" y2="244" class="arrow-main" marker-end="url(#um)"/>
</svg></div>

<p class="fig-lc">1Б. Жизненный цикл администратора</p>
<div class="fig-row"><svg viewBox="0 0 920 380" width="920" height="380" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <marker id="ag" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="#9ca3af"/></marker>
    <marker id="ap" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0,0 L8,4 L0,8 Z" fill="#db2777"/></marker>
  </defs>
  <rect x="0" y="0" width="920" height="380" rx="10" fill="#fffafb" stroke="#e5e7eb"/>
  <text x="16" y="18" font-size="10" fill="#6b7280">① вход</text>
  <text x="16" y="372" font-size="10" fill="#db2777">② управление — четыре ветки</text>
  <!-- top row: wider gaps, labels above row -->
  <rect x="20" y="44" width="100" height="40" rx="6" fill="#fce7f3" stroke="#ec4899"/>
  <text x="70" y="69" text-anchor="middle" font-size="11">Администратор</text>
  <rect x="144" y="44" width="110" height="40" rx="6" fill="#f3f4f6" stroke="#9ca3af"/>
  <text x="199" y="69" text-anchor="middle" font-size="11">Система входа</text>
  <rect x="278" y="44" width="150" height="40" rx="6" fill="#bfdbfe" stroke="#2563eb"/>
  <text x="353" y="69" text-anchor="middle" font-size="11">Браузер (админ UI)</text>
  <line x1="120" y1="64" x2="144" y2="64" class="arrow-optional" marker-end="url(#ag)"/>
  <rect x="114" y="28" width="36" height="14" rx="3" fill="#fffafb"/>
  <text x="132" y="39" text-anchor="middle" font-size="9" fill="#6b7280">логин</text>
  <line x1="254" y1="64" x2="278" y2="64" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="248" y="28" width="52" height="14" rx="3" fill="#fffafb"/>
  <text x="274" y="39" text-anchor="middle" font-size="9" fill="#db2777">admin UI</text>
  <!-- hub -->
  <rect x="320" y="104" width="280" height="40" rx="6" fill="#fce7f3" stroke="#ec4899"/>
  <text x="460" y="129" text-anchor="middle" font-size="11">Админ-панель</text>
  <path d="M353 84 L353 94 L460 94 L460 104" class="arrow-admin" fill="none" marker-end="url(#ap)"/>
  <rect x="392" y="86" width="52" height="14" rx="3" fill="#fffafb"/>
  <text x="418" y="97" text-anchor="middle" font-size="9" fill="#db2777">консоль</text>
  <!-- fan -->
  <line x1="460" y1="144" x2="460" y2="158" class="arrow-admin"/>
  <line x1="110" y1="158" x2="810" y2="158" class="arrow-admin"/>
  <!-- column template: scenario 174-222, gap, server 240-286, gap, bottom 304-350 -->
  <!-- col1 -->
  <line x1="110" y1="158" x2="110" y2="174" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="21" y="174" width="178" height="48" rx="6" fill="#fce7f3" stroke="#ec4899"/>
  <text x="110" y="202" text-anchor="middle" font-size="11">Политики и организации</text>
  <line x1="110" y1="222" x2="110" y2="240" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="66" y="240" width="88" height="46" rx="6" fill="#e0e7ff" stroke="#6366f1"/>
  <text x="110" y="268" text-anchor="middle" font-size="11">Сервер</text>
  <line x1="110" y1="286" x2="110" y2="304" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="66" y="304" width="88" height="46" rx="6" fill="#fef3c7" stroke="#f59e0b"/>
  <text x="110" y="332" text-anchor="middle" font-size="11">База</text>
  <rect x="84" y="288" width="52" height="14" rx="3" fill="#fffafb"/>
  <text x="110" y="299" text-anchor="middle" font-size="9" fill="#db2777">сроки</text>
  <!-- col2 -->
  <line x1="330" y1="158" x2="330" y2="174" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="241" y="174" width="178" height="48" rx="6" fill="#fce7f3" stroke="#ec4899"/>
  <text x="330" y="202" text-anchor="middle" font-size="11">Legal hold</text>
  <line x1="330" y1="222" x2="330" y2="240" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="286" y="240" width="88" height="46" rx="6" fill="#e0e7ff" stroke="#6366f1"/>
  <text x="330" y="268" text-anchor="middle" font-size="11">Сервер</text>
  <line x1="330" y1="286" x2="330" y2="304" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="266" y="304" width="128" height="46" rx="6" fill="#fef3c7" stroke="#f59e0b"/>
  <text x="330" y="332" text-anchor="middle" font-size="11">Purge заморожен</text>
  <rect x="296" y="288" width="68" height="14" rx="3" fill="#fffafb"/>
  <text x="330" y="299" text-anchor="middle" font-size="9" fill="#db2777">блок purge</text>
  <!-- col3 -->
  <line x1="550" y1="158" x2="550" y2="170" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="461" y="170" width="178" height="52" rx="6" fill="#fce7f3" stroke="#ec4899"/>
  <text x="550" y="192" text-anchor="middle" font-size="10">Ошибочное удаление</text>
  <text x="550" y="206" text-anchor="middle" font-size="9" fill="#6b7280">скрыто в UI · данные в системе</text>
  <line x1="550" y1="222" x2="550" y2="240" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="484" y="240" width="132" height="46" rx="6" fill="#e0e7ff" stroke="#6366f1"/>
  <text x="550" y="268" text-anchor="middle" font-size="11">Export / Сервер</text>
  <line x1="550" y1="286" x2="550" y2="304" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="484" y="304" width="132" height="46" rx="6" fill="#fde68a" stroke="#d97706"/>
  <text x="550" y="332" text-anchor="middle" font-size="11">Архив → ZIP</text>
  <rect x="511" y="288" width="78" height="14" rx="3" fill="#fffafb"/>
  <text x="550" y="299" text-anchor="middle" font-size="9" fill="#db2777">export</text>
  <!-- col4 -->
  <line x1="770" y1="158" x2="770" y2="174" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="681" y="174" width="178" height="48" rx="6" fill="#fce7f3" stroke="#ec4899"/>
  <text x="770" y="202" text-anchor="middle" font-size="11">Расследование</text>
  <line x1="770" y1="222" x2="770" y2="240" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="726" y="240" width="88" height="46" rx="6" fill="#e0e7ff" stroke="#6366f1"/>
  <text x="770" y="268" text-anchor="middle" font-size="11">Журнал аудита</text>
  <line x1="770" y1="286" x2="770" y2="304" class="arrow-admin" marker-end="url(#ap)"/>
  <rect x="706" y="304" width="128" height="46" rx="6" fill="#fef3c7" stroke="#f59e0b"/>
  <text x="770" y="332" text-anchor="middle" font-size="11">Статус purge</text>
  <rect x="752" y="224" width="36" height="14" rx="3" fill="#fffafb"/>
  <text x="770" y="235" text-anchor="middle" font-size="9" fill="#db2777">кто?</text>
  <rect x="736" y="288" width="68" height="14" rx="3" fill="#fffafb"/>
  <text x="770" y="299" text-anchor="middle" font-size="9" fill="#db2777">мониторинг</text>
  <text x="460" y="362" text-anchor="middle" font-size="10" fill="#6b7280">Все ветки фиксируются в журнале аудита · восстановление в чат — через export и процедуру заказчика</text>
</svg></div>

<ul class="fig-key">
  <li><span class="key-swatch key-main" aria-hidden="true"></span><span><b>Синяя сплошная</b> — переписка: сохранение и уведомление</span></li>
  <li><span class="key-swatch key-opt" aria-hidden="true"></span><span><b>Серая пунктирная</b> — вход, файлы, архив (можно временно отключить — чаты часто работают)</span></li>
  <li><span class="key-swatch key-admin" aria-hidden="true"></span><span><b>Розовая пунктирная</b> — администрирование</span></li>
</ul>
<p class="fig-rule"><b>Главное правило:</b> для сотрудника критична центральная линия (Шлюз → Сервер → База). Админ управляет политиками, hold, export и аудитом — переписка при этом не останавливается.</p>
<figcaption class="fig-cap">Два независимых жизненных цикла: стрелки обходят блоки по «коридорам», не пересекая их.</figcaption>
<div class="fig-legend">
  <table>
    <tr><th>Если отключится…</th><th>Что почувствует пользователь</th><th>Критичность</th></tr>
    <tr><td>База или сервер (ЖЦ пользователя, центр)</td><td>Нельзя отправить/прочитать сообщения</td><td><b>Критично</b></td></tr>
    <tr><td>Веб-шлюз</td><td>Мессенджер недоступен</td><td><b>Критично</b></td></tr>
    <tr><td>Мгнов. доставка</td><td>Обновить страницу, чтобы увидеть новое</td><td>Средне</td></tr>
    <tr><td>Система входа</td><td>Нельзя войти заново</td><td>Критично для новых входов</td></tr>
    <tr><td>Хранилище файлов</td><td>Файлы не грузятся; текст работает</td><td>Средне</td></tr>
    <tr><td>Архив</td><td>Старые данные или экспорт недоступны</td><td>Низко–средне</td></tr>
    <tr><td>Админ-панель</td><td>Сотрудники работают; политики не меняются</td><td>Низко</td></tr>
    <tr><td>Export / архив (ветка восстановления)</td><td>Нельзя выгрузить данные для расследования</td><td>Средне–высоко для комплаенса</td></tr>
    <tr><td>Legal hold</td><td>Риск преждевременного удаления по TTL</td><td>Высоко для юристов</td></tr>
  </table>
</div>
</figure>"""

ARCH_FLOW = """
<h3>Как это работает — два жизненных цикла (см. рис. 1)</h3>

<h4>1А. Пользователь (сотрудник)</h4>
<p>На одной схеме: <b>① вход</b> (система входа → веб-шлюз) → <b>② отправка</b> (сотрудник → браузер → шлюз → сервер → база) → параллельно <b>③ уведомление</b> получателю и <b>④ файлы</b> (сервер → хранилище → архив по срокам).</p>

<h4>1Б. Администратор</h4>
<p>Единая схема с <b>ветвлением</b> от админ-панели — четыре типовых сценария:</p>
<ul>
  <li><b>Политики и организации</b> — сроки хранения, привязка пользователей к org.</li>
  <li><b>Legal hold</b> — заморозка автоматического удаления по запросу юристов.</li>
  <li><b>Ошибочное удаление</b> — сообщение скрыто в UI (soft-delete), но данные остаются в базе/архиве; админ заказывает <b>export (ZIP)</b> для восстановления по процедуре заказчика (не «кнопка отмены» в чате).</li>
  <li><b>Расследование</b> — журнал аудита (кто менял политики, запускал export) и мониторинг фоновой очистки (purge).</li>
</ul>
<p class="small">Сбой админ-цикла не блокирует переписку сотрудников. Для аварийного отката на уровне всей системы — backup/restore по runbook (§15, профиль Pilot/Standard/Enterprise).</p>
"""

FIG_RETENTION = """<figure class="fig"><svg viewBox="0 0 500 200" width="500" height="200" xmlns="http://www.w3.org/2000/svg">
  <rect x="150" y="140" width="200" height="45" rx="4" fill="#bbf7d0" stroke="#22c55e"/><text x="250" y="168" text-anchor="middle" font-size="13">Оперативный слой — быстрый доступ</text>
  <rect x="120" y="85" width="260" height="45" rx="4" fill="#fde68a" stroke="#f59e0b"/><text x="250" y="113" text-anchor="middle" font-size="13">Архив метаданных — история «кто когда писал»</text>
  <rect x="90" y="30" width="320" height="45" rx="4" fill="#bfdbfe" stroke="#3b82f6"/><text x="250" y="58" text-anchor="middle" font-size="13">Глубокий архив — старые тексты и файлы (сжатие)</text>
  <text x="30" y="165" font-size="11" fill="#6b7280">новые</text><text x="30" y="55" font-size="11" fill="#6b7280">старые</text>
  <path d="M60 165 L60 55" stroke="#9ca3af" stroke-dasharray="4"/>
</svg><figcaption class="fig-cap">Рис. 2. Три «этажа» хранения переписки (аналогия: картотека → архив → склад)</figcaption></figure>"""

FIG_PROFILES = """<figure class="fig"><svg viewBox="0 0 620 180" width="620" height="180" xmlns="http://www.w3.org/2000/svg">
  <rect x="30" y="60" width="160" height="100" rx="8" fill="#dcfce7" stroke="#22c55e"/>
  <text x="110" y="95" text-anchor="middle" font-size="14" font-weight="bold">Pilot</text>
  <text x="110" y="115" text-anchor="middle" font-size="11">до 10 000</text>
  <text x="110" y="135" text-anchor="middle" font-size="11">12–16 ГБ RAM</text>
  <rect x="230" y="40" width="160" height="120" rx="8" fill="#dbeafe" stroke="#3b82f6"/>
  <text x="310" y="80" text-anchor="middle" font-size="14" font-weight="bold">Standard</text>
  <text x="310" y="100" text-anchor="middle" font-size="11">до 100 000</text>
  <text x="310" y="120" text-anchor="middle" font-size="11">120–160 ГБ RAM</text>
  <rect x="430" y="20" width="160" height="140" rx="8" fill="#e0e7ff" stroke="#6366f1"/>
  <text x="510" y="65" text-anchor="middle" font-size="14" font-weight="bold">Enterprise</text>
  <text x="510" y="85" text-anchor="middle" font-size="11">до 1 000 000</text>
  <text x="510" y="105" text-anchor="middle" font-size="11">900 ГБ–1,2 ТБ RAM</text>
  <text x="310" y="175" text-anchor="middle" font-size="12" fill="#6b7280">Рост масштаба → больше отказоустойчивости и скорости поиска</text>
</svg><figcaption class="fig-cap">Рис. 3. Три профиля развёртывания — платите за нужный масштаб</figcaption></figure>"""

FIG_COST = render_fig_cost_monthly_svg()

FIG_RAM = """<figure class="fig"><svg viewBox="0 0 560 240" width="560" height="240" xmlns="http://www.w3.org/2000/svg">
  <text x="300" y="22" text-anchor="middle" font-size="13" font-weight="bold">Оперативная память (ГБ) — без оптимизации и цель §12</text>
  <line x1="58" y1="200" x2="500" y2="200" stroke="#9ca3af"/>
  <line x1="58" y1="200" x2="58" y2="40" stroke="#9ca3af"/>
  <text x="52" y="204" text-anchor="end" font-size="9">0</text>
  <text x="52" y="44" text-anchor="end" font-size="9">2 TB</text>
  <text x="100" y="218" text-anchor="middle" font-size="11">10k</text>
  <text x="220" y="218" text-anchor="middle" font-size="11">100k</text>
  <text x="340" y="218" text-anchor="middle" font-size="11">500k</text>
  <text x="460" y="218" text-anchor="middle" font-size="11">1M</text>
  <rect x="78" y="128" width="28" height="72" fill="#fca5a5"/><rect x="110" y="188" width="28" height="12" fill="#86efac"/>
  <rect x="198" y="72" width="28" height="128" fill="#fca5a5"/><rect x="230" y="112" width="28" height="88" fill="#86efac"/>
  <rect x="318" y="48" width="28" height="152" fill="#fca5a5"/><rect x="350" y="98" width="28" height="102" fill="#86efac"/>
  <rect x="438" y="32" width="28" height="168" fill="#fca5a5"/><rect x="470" y="78" width="28" height="122" fill="#86efac"/>
  <rect x="510" y="48" width="12" height="12" fill="#fca5a5"/><text x="528" y="57" font-size="10">без оптим.</text>
  <rect x="510" y="68" width="12" height="12" fill="#86efac"/><text x="528" y="77" font-size="10">цель §12</text>
</svg><figcaption class="fig-cap">Рис. 4. Снижение потребления RAM при оптимизированных профилях (§12)</figcaption></figure>"""

FIG_MSG = """<figure class="fig"><svg viewBox="0 0 600 100" width="600" height="100" xmlns="http://www.w3.org/2000/svg">
  <rect x="10" y="30" width="80" height="40" rx="6" fill="#dbeafe" stroke="#3b82f6"/><text x="50" y="55" text-anchor="middle" font-size="11">Отправитель</text>
  <rect x="130" y="30" width="80" height="40" rx="6" fill="#e0e7ff" stroke="#6366f1"/><text x="170" y="55" text-anchor="middle" font-size="11">Сервер</text>
  <rect x="250" y="30" width="80" height="40" rx="6" fill="#fef3c7" stroke="#f59e0b"/><text x="290" y="55" text-anchor="middle" font-size="11">База</text>
  <rect x="370" y="30" width="80" height="40" rx="6" fill="#e0e7ff" stroke="#6366f1"/><text x="410" y="55" text-anchor="middle" font-size="11">Доставка</text>
  <rect x="490" y="30" width="90" height="40" rx="6" fill="#dbeafe" stroke="#3b82f6"/><text x="535" y="55" text-anchor="middle" font-size="11">Получатель</text>
  <path d="M90 50 H130 M210 50 H250 M330 50 H370 M450 50 H490" stroke="#64748b" marker-end="url(#b)"/>
  <defs><marker id="b" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto"><path d="M0,0 L6,3 L0,6" fill="#64748b"/></marker></defs>
  <text x="300" y="90" text-anchor="middle" font-size="11" fill="#6b7280">Сообщение сохраняется до подтверждения доставки</text>
</svg><figcaption class="fig-cap">Рис. 6. Путь сообщения: не теряется после подтверждения отправителю</figcaption></figure>"""

FIG_SLA = """<figure class="fig"><svg viewBox="0 0 520 140" width="520" height="140" xmlns="http://www.w3.org/2000/svg">
  <rect x="20" y="50" width="140" height="60" rx="6" fill="#fef9c3" stroke="#eab308"/><text x="90" y="75" text-anchor="middle" font-size="12" font-weight="bold">Pilot 99,0%</text><text x="90" y="95" text-anchor="middle" font-size="10">RPO 24ч / RTO 4ч</text>
  <rect x="190" y="35" width="140" height="75" rx="6" fill="#dbeafe" stroke="#3b82f6"/><text x="260" y="65" text-anchor="middle" font-size="12" font-weight="bold">Standard 99,5%</text><text x="260" y="85" text-anchor="middle" font-size="10">RPO 8ч / RTO 2ч</text>
  <rect x="360" y="20" width="140" height="90" rx="6" fill="#dcfce7" stroke="#22c55e"/><text x="430" y="55" text-anchor="middle" font-size="12" font-weight="bold">Enterprise 99,9%</text><text x="430" y="75" text-anchor="middle" font-size="10">RPO 15м / RTO 15м</text>
  <text x="260" y="130" text-anchor="middle" font-size="11" fill="#6b7280">Доступность и скорость восстановления растут с профилем</text>
</svg><figcaption class="fig-cap">Рис. 5. Уровни доступности по профилям (ориентиры для договора)</figcaption></figure>"""


def case(title, status, situation="", actions="", result=""):
    tag = {"done": "tag-done", "partial": "tag-partial", "planned": "tag-planned"}.get(status, "tag-out")
    label = {"done": "Реализовано", "partial": "Частично", "planned": "Запланировано"}.get(status, status)
    body = []
    if situation:
        body.append(f"<b>Ситуация:</b> {situation}")
    if actions:
        body.append(f"<b>Действия:</b> {actions}")
    if result:
        body.append(f"<b>Результат:</b> {result}")
    inner = "<br/>".join(body) if body else ""
    return f'<div class="case"><h4>{title} <span class="tag {tag}">{label}</span></h4><p>{inner}</p></div>\n'


def main():
    parts = []
    parts.append(f"""<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>Продуктовая презентация: Korus Messenger (AvandocMsg)</title>
  <style>{CSS}</style>
</head>
<body>

<h1>Продуктовая презентация<br/>Korus Messenger (AvandocMsg)</h1>
<div class="meta">
  <b>Версия:</b> 2.3 &nbsp;|&nbsp; <b>Дата:</b> 15 июня 2026 &nbsp;|&nbsp;
  <b>Аудитория:</b> руководители, юристы, продажи, бухгалтерия, операторы, служба безопасности<br/>
  <b>Формат:</b> презентация продукта для заказчика; приложение I — каталог <b>страниц</b> клиента и админки
</div>

<div class="note">
  <div class="req">О презентации</div>
  <div class="comment">
    Это <b>продуктовая презентация</b> корпоративного мессенджера простым языком — для людей без технического бэкграунда.
    Указано, что уже работает, что частично готово и что запланировано. Цифры по серверам и стоимости — <b>ориентиры для планирования и переговоров</b>, не коммерческое предложение.
    Полное техническое задание для разработки — отдельный документ (см. §8).
  </div>
  <div class="req" style="margin-top:12px;">Обозначения статусов</div>
  <div class="comment">
    <span class="tag tag-done">Реализовано</span> — доступно сейчас &nbsp;
    <span class="tag tag-partial">Частично</span> — есть, но с ограничениями &nbsp;
    <span class="tag tag-planned">Запланировано</span> — в планах развития &nbsp;
    <span class="tag tag-out">Вне текущей поставки</span> — отдельный продукт
  </div>
</div>

<h2 id="toc">Содержание</h2>
<ol class="toc">
  <li><a href="#s1">1. Резюме для руководства</a></li>
  <li><a href="#s2">2. Для кого этот продукт</a></li>
  <li><a href="#s3">3. Словарь простыми словами</a></li>
  <li><a href="#s4">4. Возможности системы</a></li>
  <li><a href="#s5">5. Кейсы использования (27 сценариев)</a></li>
  <li><a href="#s6">6. Роли и права</a></li>
  <li><a href="#s7">7. Безопасность и соответствие</a></li>
  <li><a href="#s8">8. Сравнение с исходным техническим ТЗ</a></li>
  <li><a href="#s9">9. Возможности развития</a></li>
  <li><a href="#s10">10. Ресурсы серверов и нагрузка</a></li>
  <li><a href="#s11">11. Критерии приёмки</a></li>
  <li><a href="#s12">12. Оптимизация инфраструктуры</a></li>
  <li><a href="#s13">13. Интеграции</a></li>
  <li><a href="#s14">14. Комплаенс и персональные данные</a></li>
  <li><a href="#s15">15. Доступность и SLA</a></li>
  <li><a href="#s16">16. Профили и ограничения для пользователя</a></li>
  <li><a href="#s17">17. Приложения</a> (<a href="#app-i">I — страницы и URL</a>)</li>
  <li><a href="#s18">18. Стоимость владения — примеры расчётов</a> (<a href="#s18-7">диаграмма за год</a>)</li>
</ol>
""")

    # §1
    parts.append(f"""
<hr/>
<h2 id="s1">1. Резюме для руководства</h2>
{FIG_ARCH}
{ARCH_FLOW}
<p><span class="req">Korus Messenger (AvandocMsg)</span> — корпоративный мессенджер для организаций: переписка, файлы, видеозвонки, управление сроками хранения данных и инструменты для юридического и внутреннего контроля. Работает в браузере, может устанавливаться как приложение (PWA). Предназначен для изолированных контуров — на серверах заказчика или в частном облаке.</p>

<h3>Что уже работает</h3>
<ul>
  <li>Вход, регистрация, безопасный выход и автоматическое продление сессии</li>
  <li>Личные и групповые чаты: отправка, редактирование, удаление, пересылка, реакции, закрепление, ответы, сообщения с автоудалением</li>
  <li>Мгновенная доставка в браузере; восстановление после обрыва связи</li>
  <li>Контакты, поиск коллег и поиск по тексту переписки</li>
  <li>Файлы: загрузка, просмотр, скачивание, публичные ссылки с отзывом</li>
  <li>Личное «Хранилище» для сохранённых сообщений</li>
  <li>Экспорт переписки в архив (JSON/ZIP)</li>
  <li>Видеозвонки и демонстрация экрана из чата</li>
  <li>Админ-панель: организации, политики хранения, legal hold, журнал аудита</li>
  <li>Интерфейс на 6 языках (русский, английский и др.)</li>
  <li>Сквозное шифрование — технически готово; массовое включение в промышленной среде после согласования с безопасностью</li>
</ul>

<h3>Что нужно до промышленного запуска</h3>
<ul>
  <li>Защищённое соединение (HTTPS) для всех пользователей</li>
  <li>Формальное одобрение усиленного шифрования (Product / Security / Ops)</li>
  <li>Production-уведомления в браузере (Web Push)</li>
  <li>Сервер для стабильных звонков за корпоративным firewall (TURN)</li>
  <li>Согласованная с юристами политика полноты выгрузок для комплаенса</li>
</ul>
""")

    # §2-3
    parts.append("""
<hr/>
<h2 id="s2">2. Для кого этот продукт</h2>
<h3>Целевые организации</h3>
<ul>
  <li>Корпорации и холдинги с требованиями к хранению переписки</li>
  <li>Государственные и окологосударственные структуры</li>
  <li>Распределённые команды и удалённая работа</li>
  <li>Организации, которым важен контроль данных на своих серверах</li>
</ul>
<table>
  <tr><th>Роль</th><th>Задачи</th></tr>
  <tr><td>Сотрудник</td><td>Переписка, файлы, звонки, настройки приватности</td></tr>
  <tr><td>Руководитель</td><td>Группы, модерация, встречи</td></tr>
  <tr><td>IT-администратор</td><td>Организации, политики, пользователи</td></tr>
  <tr><td>Безопасность / комплаенс</td><td>Аудит, экспорт, legal hold</td></tr>
  <tr><td>Оператор инфраструктуры</td><td>Развёртывание, резервные копии, масштабирование</td></tr>
  <tr><td>Продажи / закупки</td><td>Оценка стоимости и профиля (см. §18)</td></tr>
</table>
<h3>Границы текущей поставки</h3>
<table>
  <tr><th>Компонент</th><th>Статус</th></tr>
  <tr><td>Веб-клиент (браузер, PWA)</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Сервер и фоновые службы</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Мобильные приложения iOS/Android</td><td><span class="tag tag-out">Вне текущей поставки</span></td></tr>
  <tr><td>Desktop-клиент</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
  <tr><td>Bot API (боты и интеграции)</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
  <tr><td>Прямые эфиры (all-hands на сотни зрителей)</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
</table>

<hr/>
<h2 id="s3">3. Словарь простыми словами</h2>
<table>
  <tr><th>Термин</th><th>Объяснение</th></tr>
  <tr><td>Чат / Группа</td><td>Переписка один на один или с несколькими участниками</td></tr>
  <tr><td>«Хранилище»</td><td>Личный «склад» важных сообщений и файлов</td></tr>
  <tr><td>Read receipt</td><td>Отметка «кто прочитал» (если разрешено настройками)</td></tr>
  <tr><td>Ретенция</td><td>Правила: как долго хранить переписку и когда удалять</td></tr>
  <tr><td>Legal hold</td><td>Заморозка удаления по требованию юристов</td></tr>
  <tr><td>Экспорт (export)</td><td>Выгрузка переписки в файл для расследования или архива</td></tr>
  <tr><td>E2EE (сквозное шифрование)</td><td>Текст шифруется на устройстве; сервер не видит содержание</td></tr>
  <tr><td>Организация (tenant)</td><td>Логическое подразделение — одна компания или ведомство</td></tr>
  <tr><td>TTL сообщения</td><td>Автоудаление сообщения через заданное время</td></tr>
  <tr><td>SLA</td><td>Договорённость о доступности сервиса и времени восстановления</td></tr>
  <tr><td>RPO / RTO</td><td>Допустимая потеря данных / время восстановления после сбоя</td></tr>
  <tr><td>Pilot / Standard / Enterprise</td><td>Три профиля масштаба — от пилота до крупного контура</td></tr>
</table>
""")

    # §4
    parts.append("""
<hr/>
<h2 id="s4">4. Возможности системы</h2>
<table>
  <tr><th>Область</th><th>Примеры</th><th>Статус</th></tr>
  <tr><td>Вход и сессия</td><td>Регистрация, вход, выход, продление сессии</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Чаты и сообщения</td><td>1:1, группы, edit/delete/forward/pin/reactions/TTL</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Мгновенная доставка</td><td>Без обновления страницы, «печатает…»</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Контакты и поиск</td><td>Список, импорт, поиск людей и сообщений</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Файлы</td><td>Загрузка, превью, публичные ссылки</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Экспорт чата</td><td>JSON/ZIP архив</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Звонки</td><td>Видео, комната по ссылке, screen share</td><td><span class="tag tag-partial">Частично</span></td></tr>
  <tr><td>E2EE</td><td>Шифрование в браузере</td><td><span class="tag tag-partial">Частично</span></td></tr>
  <tr><td>Push / PWA</td><td>Уведомления, установка как приложение</td><td><span class="tag tag-partial">Частично</span></td></tr>
  <tr><td>Админка</td><td>Org, ретенция, legal hold, аудит</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Ретенция и архив</td><td>Многоуровневое хранение, автоочистка</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Локализация</td><td>ru, en, be, kk, zh, ko</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Bot API</td><td>Боты для Service Desk и автоматизации</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
  <tr><td>Live-streaming</td><td>All-hands, HLS-трансляции</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
  <tr><td>Prod HTTPS</td><td>Защищённый доступ</td><td><span class="tag tag-partial">Частично</span></td></tr>
</table>
""")

    # §5 cases
    parts.append('<hr/><h2 id="s5">5. Кейсы использования</h2>\n<p>Формат: <b>ситуация → действия → результат</b>. Всего 27 сценариев.</p>\n<h3>5.1 Сотрудник</h3>\n')
    cases_emp = [
        ("КУ-01: Переписка с коллегой", "done", "Нужно обсудить задачу с коллегой из другого отдела.", "Найти коллегу → открыть чат → написать.", "Сообщение доставлено мгновенно."),
        ("КУ-02: Проектная группа", "done", "Запуск проекта на 8 человек.", "Создать группу → пригласить участников.", "Общая лента и обмен файлами."),
        ("КУ-03: Файл внешнему партнёру", "done", "Передать PDF контрагенту без аккаунта.", "Прикрепить файл → публичная ссылка → отправить.", "Партнёр скачивает; ссылку можно отозвать."),
        ("КУ-04: «Хранилище»", "done", "Сохранить регламент из группового чата.", "Переслать сообщение в «Хранилище».", "Копия сохраняется лично."),
        ("КУ-05: Сообщение с автоудалением", "done", "Передать одноразовый код.", "Отправить с таймером (TTL).", "После времени сообщение исчезает."),
        ("КУ-06: Read receipts", "done", "Проверить, кто прочитал объявление.", "Открыть сообщение → список прочитавших.", "Видно, кто ознакомился."),
        ("КУ-07: Обрыв Wi‑Fi", "done", "Связь пропала в метро и восстановилась.", "Открыть мессенджер.", "Сессия и сообщения восстанавливаются."),
        ("КУ-08: Видеозвонок", "partial", "Обсудить голосом и показать экран.", "«Звонок» в группе → камера/экран.", "Конференция; за firewall может нужен TURN."),
        ("КУ-09: Смена языка", "done", "Предпочитаемый язык интерфейса.", "Настройки → язык.", "Интерфейс переключается."),
        ("КУ-10: Блокировка пользователя", "done", "Нежелательные сообщения.", "Настройки → заблокировать.", "Заблокированный не может писать."),
    ]
    for c in cases_emp:
        parts.append(case(*c))

    parts.append("<h3>5.2 Руководитель команды</h3>\n")
    for c in [
        ("КУ-11: Закрепить регламент", "done", "Все должны видеть правила.", "Закрепить сообщение.", "Отображается в шапке чата."),
        ("КУ-12: Исключить нарушителя", "done", "Участник нарушает правила.", "Ban с указанием причины.", "Не может писать и читать группу."),
        ("КУ-13: Встреча по ссылке", "done", "Совещание с подрядчиками без аккаунтов.", "Создать конференцию → ссылка.", "Гостевой вход по ссылке."),
        ("КУ-14: «Не беспокоить»", "done", "Активная группа, но не нужны уведомления ночью.", "Mute для чата.", "Сообщения приходят без звука."),
    ]:
        parts.append(case(*c))

    parts.append("<h3>5.3 IT-администратор</h3>\n")
    for c in [
        ("КУ-15: Организация и пользователи", "done", "Новое подразделение.", "Админка → создать org → назначить пользователей.", "Политики org применяются."),
        ("КУ-16: Срок хранения", "done", "Регламент: 3 года.", "Админка → ретенция → политика.", "Фоновые службы архивируют и очищают."),
        ("КУ-17: Legal hold", "done", "Расследование — нельзя удалять чат.", "Включить legal hold.", "Автоудаление заморожено."),
        ("КУ-18: Журнал аудита", "done", "Кто менял политики?", "Админка → аудит → фильтры.", "Хронология действий."),
        ("КУ-19: Мониторинг очистки", "done", "Purge выполняется штатно?", "Панель ретенции → статус.", "Видны этапы и ошибки."),
        ("КУ-20: Миграция на E2EE", "partial", "Переход на усиленное шифрование.", "E2EE dashboard → batch migrate.", "Prod — после sign-off."),
    ]:
        parts.append(case(*c))

    parts.append("<h3>5.4 Служба безопасности / комплаенс</h3>\n")
    for c in [
        ("КУ-21: Export для расследования", "partial", "Запрос переписки по инциденту.", "Заказать export → скачать ZIP.", "Архив с сообщениями; полнота GDPR — в согласовании."),
        ("КУ-22: Проверка удаления", "done", "Данные по TTL удалены?", "Проверить чат и purge status.", "В UI не отображаются."),
        ("КУ-23: Аудит админов", "done", "Несанкционированный доступ.", "Журнал аудита.", "Кто, когда, что изменил."),
        ("КУ-24: Prod HTTPS", "partial", "Перед выводом в интернет.", "Checklist DNS, сертификаты.", "Пользователи по HTTPS."),
    ]:
        parts.append(case(*c))

    parts.append("<h3>5.5 Запланированные сценарии</h3>\n")
    for c in [
        ("КУ-25: Bot Service Desk", "planned", "Заявка через чат-бота.", "Написать боту.", "Тикет во внешней системе."),
        ("КУ-26: All-hands 500+", "planned", "Выступление гендиректора онлайн.", "Live-стрим → HLS.", "Массовая трансляция."),
        ("КУ-27: SSO Google/LDAP", "planned", "Единый вход без отдельного пароля.", "«Войти через корпоративный портал».", "Единый вход через IdP."),
    ]:
        parts.append(case(*c))

    # §6-8
    parts.append(f"""
<hr/>
<h2 id="s6">6. Роли и права</h2>
<table>
  <tr><th>Действие</th><th>Пользователь</th><th>Модератор</th><th>Админ org</th><th>Системный админ</th></tr>
  <tr><td>Писать в чатах</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td></tr>
  <tr><td>Создавать группы</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td></tr>
  <tr><td>Закреплять сообщения</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td></tr>
  <tr><td>Исключать (ban) из группы</td><td>—</td><td>✓</td><td>✓</td><td>✓</td></tr>
  <tr><td>Mute / personal filter</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td></tr>
  <tr><td>Экспорт своего чата</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td></tr>
  <tr><td>Org, политики, audit</td><td>—</td><td>—</td><td>—</td><td>✓</td></tr>
  <tr><td>Legal hold, purge</td><td>—</td><td>—</td><td>—</td><td>✓</td></tr>
  <tr><td>E2EE batch migrate</td><td>—</td><td>—</td><td>—</td><td>✓</td></tr>
</table>

<hr/>
<h2 id="s7">7. Безопасность и соответствие</h2>
{FIG_RETENTION}
<h3>Разделение данных</h3>
<p>Пользователи организованы по <b>организациям</b>. Политики хранения и legal hold задаются на уровне организации и при необходимости — отдельного чата.</p>
<h3>Журнал аудита</h3>
<p>Все значимые административные действия (смена политик, экспорт, legal hold) фиксируются: кто, когда, что сделал.</p>
<h3>Защита от злоупотреблений</h3>
<ul>
  <li>Ограничение частоты попыток входа (защита от перебора паролей)</li>
  <li>Доступ к чатам только участникам</li>
  <li>Блокировки учитываются при поиске и доставке</li>
</ul>
<h3>Сквозное шифрование (E2EE)</h3>
<table>
  <tr><th>Режим</th><th>Что видит сервер</th></tr>
  <tr><td>Без E2EE</td><td>Текст сообщений (хранится на сервере организации)</td></tr>
  <tr><td>С E2EE</td><td>Только «конверт» — расшифровка на устройстве пользователя</td></tr>
</table>
<p><span class="tag tag-partial">Частично</span> — работает в тестовой среде; промышленное включение после согласования с безопасностью.</p>

<hr/>
<h2 id="s8">8. Сравнение с исходным техническим ТЗ</h2>
<p>Исходное полное техническое задание описывает продукт для разработчиков. <b>Эта презентация</b> — продуктовый слой для руководства, юристов и закупок.</p>
<table>
  <tr><th>Критерий</th><th>Исходное ТЗ</th><th>Эта презентация</th></tr>
  <tr><td>Аудитория</td><td>Разработчики</td><td>Руководство, юристы, продажи</td></tr>
  <tr><td>Язык</td><td>Технический</td><td>Бизнес + статусы</td></tr>
  <tr><td>Клиенты</td><td>Web + mobile + desktop</td><td>Web реализован; mobile — planned</td></tr>
  <tr><td>Кейсы</td><td>Требования списком</td><td>27 сценариев (§5)</td></tr>
  <tr><td>Sizing и стоимость</td><td>Формулы</td><td>Таблицы + примеры ₽ (§10, §18)</td></tr>
  <tr><td>Комплаенс</td><td>Разрозненно</td><td>§14 + чеклист</td></tr>
  <tr><td>SLA</td><td>Кратко</td><td>§15 по профилям</td></tr>
  <tr><td>Статус фич</td><td>Все как требования</td><td>Реализовано / Частично / Planned</td></tr>
</table>
<table>
  <tr><th>Тема исходного ТЗ</th><th>Статус в продукте</th></tr>
  <tr><td>Вход, чаты, сообщения, TTL</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Hot/Archive/Deep, поиск, файлы</td><td><span class="tag tag-done">Реализовано</span></td></tr>
  <tr><td>Bot API</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
  <tr><td>Push-уведомления</td><td><span class="tag tag-partial">Частично</span></td></tr>
  <tr><td>Live-streaming</td><td><span class="tag tag-planned">Запланировано</span></td></tr>
  <tr><td>Звонки</td><td><span class="tag tag-partial">Частично</span></td></tr>
  <tr><td>Оптимизация infra (§12, FR-OPT)</td><td><span class="tag tag-done">Реализовано</span> (01–07 ✓; 08–09 + load test — roadmap)</td></tr>
  <tr><td>Sizing 1M пользователей</td><td>§10 — ориентиры</td></tr>
</table>
""")

    # §9
    parts.append("""
<hr/>
<h2 id="s9">9. Возможности развития</h2>
<h3>Ближайшие 3–6 месяцев</h3>
<table><tr><th>Направление</th><th>Ценность для бизнеса</th></tr>
<tr><td>Prod HTTPS + защита секретов</td><td>Безопасный доступ</td></tr>
<tr><td>E2EE prod enable</td><td>Конфиденциальность переписки</td></tr>
<tr><td>Web Push, TURN</td><td>Уведомления и звонки за firewall</td></tr>
<tr><td>GDPR export policy</td><td>Юридически полная выгрузка</td></tr>
</table>
<h3>6–12 месяцев</h3>
<table><tr><th>Направление</th><th>Ценность</th></tr>
<tr><td>Bot API</td><td>Service Desk, автоматизация</td></tr>
<tr><td>SSO Google/LDAP</td><td>Enterprise-вход</td></tr>
<tr><td>FR-OPT-08 dedup, FR-OPT-09 sharding</td><td>Enterprise tier, §12 волна 4</td></tr>
<tr><td>Formal load test (k6)</td><td>Подтверждение §10.4 на stage при go-live</td></tr>
</table>
<h3>Уже в коде (spec 006, волны 1–3)</h3>
<table><tr><th>Направление</th><th>Ценность</th></tr>
<tr><td>Pilot compose + Keycloak prod (FR-OPT-01/02)</td><td>Пилот на 12–16 ГБ RAM</td></tr>
<tr><td>Read cache, scale WS/pipeline, replica (03–05)</td><td>Standard без лишних серверов</td></tr>
<tr><td>zstd archive + batch Solr (06–07)</td><td>Меньше диска и CPU на поиске</td></tr>
</table>
<h3>12+ месяцев</h3>
<ul><li>Live-streaming (all-hands)</li><li>Мобильные клиенты</li><li>SFU для звонков &gt;20 участников</li></ul>
""")

    # §10
    parts.append(f"""
<hr/>
<h2 id="s10">10. Ресурсы серверов и нагрузка</h2>
{FIG_PROFILES}
<p class="small" style="margin-top:8px;">Следующий рисунок — насколько оптимизация §12 снижает потребление RAM относительно базовой конфигурации §10.2.</p>
{FIG_RAM}
<div class="warn"><div class="req">Важно</div><div class="comment">Все цифры — <b>ориентиры для планирования</b>. Перед промышленным запуском рекомендуется нагрузочное тестирование на 10–20% целевой нагрузки. Активные видеозвонки и прямые эфиры могут удвоить требования к сети и процессору.</div></div>

<h3>10.1 Как считается нагрузка (простыми словами)</h3>
<p>Для оценки берём число <b>зарегистрированных пользователей</b> и типичные коэффициенты активности корпоративного мессенджера:</p>
<div class="formula">
DAU (активных в день) = Пользователи × доля активных<br/>
Пик онлайн = DAU × доля одновременно в сети<br/>
Сообщений в день = DAU × сообщений на человека<br/>
Пик сообщений/сек ≈ (сообщений в день ÷ 86400) × 3,5
</div>
<table>
  <tr><th>Метрика</th><th>10 000</th><th>100 000</th><th>500 000</th><th>1 000 000</th></tr>
  <tr><td>Активных в день (DAU)</td><td>5 000</td><td>40 000</td><td>150 000</td><td>250 000</td></tr>
  <tr><td>Пик онлайн</td><td>750</td><td>4 800</td><td>15 000</td><td>20 000</td></tr>
  <tr><td>Сообщений / день</td><td>200 000</td><td>1 400 000</td><td>4 500 000</td><td>6 250 000</td></tr>
  <tr><td>Пик сообщений / сек</td><td>~8</td><td>~57</td><td>~182</td><td>~253</td></tr>
</table>

<h3>10.2 Профили Pilot / Standard / Enterprise</h3>
<table>
  <tr><th>Профиль</th><th>Масштаб</th><th>RAM (цель)</th><th>Для кого</th></tr>
  <tr><td><b>Pilot</b></td><td>до 10 000</td><td>12–16 ГБ</td><td>Пилот, филиал, MVP</td></tr>
  <tr><td><b>Standard</b></td><td>10k–100k</td><td>120–160 ГБ</td><td>Типовая корпорация</td></tr>
  <tr><td><b>Enterprise</b></td><td>100k–1M</td><td>900 ГБ–1,2 ТБ</td><td>Крупный / федеральный контур</td></tr>
</table>

<h3>10.3 Сравнительная таблица конфигураций (baseline)</h3>
<table>
  <tr><th>Параметр</th><th>10k</th><th>100k</th><th>500k</th><th>1M</th></tr>
  <tr><td>Архитектура</td><td>2 сервера</td><td>Малый кластер</td><td>Средний кластер</td><td>Распределённый</td></tr>
  <tr><td>Суммарно RAM</td><td><b>~64 ГБ</b></td><td><b>~256 ГБ</b></td><td><b>~768 ГБ</b></td><td><b>~1,5–2 ТБ</b></td></tr>
  <tr><td>Диск (1 год с файлами)</td><td><b>~8 ТБ</b></td><td><b>~50 ТБ</b></td><td><b>~180 ТБ</b></td><td><b>~350 ТБ+</b></td></tr>
  <tr><td>Сеть (пик)</td><td>200 Мбит/с</td><td>1 Гбит/с</td><td>5 Гбит/с</td><td>10 Гбит/с+</td></tr>
  <tr><td>RPO / RTO</td><td>24ч / 4ч</td><td>8ч / 2ч</td><td>1ч / 30м</td><td>15м / 15м</td></tr>
</table>

<h3>10.4 Целевые конфигурации после оптимизации (§12)</h3>
<table>
  <tr><th>Масштаб</th><th>RAM baseline</th><th>RAM цель</th><th>Диск 1 год baseline</th><th>Диск цель</th></tr>
  <tr><td>10k Pilot</td><td>~64 ГБ</td><td><b>~14 ГБ</b></td><td>~8 ТБ</td><td><b>~5 ТБ</b></td></tr>
  <tr><td>100k Standard</td><td>~256 ГБ</td><td><b>~140 ГБ</b></td><td>~50 ТБ</td><td><b>~30 ТБ</b></td></tr>
  <tr><td>500k</td><td>~768 ГБ</td><td><b>~450 ГБ</b></td><td>~180 ТБ</td><td><b>~110 ТБ</b></td></tr>
  <tr><td>1M Enterprise</td><td>~1,5–2 ТБ</td><td><b>~900 ГБ–1,2 ТБ</b></td><td>~350 ТБ</td><td><b>~200 ТБ</b></td></tr>
</table>

<h3>10.5 Примеры сценариев</h3>
<p><b>A — «Тихий офис» (10k):</b> небольшая компания, backup раз в сутки, профиль Pilot.</p>
<p><b>B — «Распределённая компания» (100k):</b> утренний пик рассылок, replica БД, полнотекстовый поиск.</p>
<p><b>C — «Федеральный масштаб» (1M):</b> dedicated ops 24/7, обязателен load test.</p>
""")

    append_sections_11_18(parts, FIG_MSG, FIG_SLA, FIG_COST)

    content = "".join(parts)
    OUT.write_text(content, encoding="utf-8")
    LEGACY_OUT.write_text(
        """<!doctype html>
<html lang="ru"><head><meta charset="utf-8"/>
<meta http-equiv="refresh" content="0; url=product_presentation.html"/>
<title>Перенаправление</title></head>
<body><p>Документ переименован: <a href="product_presentation.html">Продуктовая презентация</a>.</p></body></html>
""",
        encoding="utf-8",
    )
    print(f"Wrote {OUT} ({OUT.stat().st_size} bytes)")
    print(f"Legacy redirect: {LEGACY_OUT}")


if __name__ == "__main__":
    main()
