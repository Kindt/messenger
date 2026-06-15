"""Unit rates and cost formulas for product presentation §17 (single source of truth)."""

from __future__ import annotations

from dataclasses import dataclass
from html import escape

# --- metadata (shown in presentation) ---

PRICE_AS_OF = "2026-06-15"
PRICE_REGION = "РФ (Москва / СПб, коммерческий сегмент)"
PRICE_VAT = "Без НДС"

PRICE_SOURCES: list[tuple[str, str]] = [
    (
        "VDS / dedicated",
        "Усреднённые публичные тарифы аренды VM (8–64 ГБ RAM, 4–8 vCPU), "
        "ориентир H1 2026; не прайс-лист конкретного провайдера.",
    ),
    (
        "Диски",
        "Block SSD и HDD archive tier, ₽/ТБ/мес; объёмы — из §10.3 презентации.",
    ),
    (
        "Канал",
        "Выделенный интернет / DIA 200 Мбит/с и 1 Гбит/с, без last-mile и без TURN.",
    ),
    (
        "Облако (аналог)",
        "Сводный IaaS-пакет (VM + managed DB + object storage + egress), "
        "эквивалентный ресурсам Pilot; не оферта Yandex/VK/MWS.",
    ),
    (
        "Ops",
        "Базовый backup + мониторинг (Pilot) или расширенный контур (Standard).",
    ),
]

PRICE_FORMULA = (
    "Сумма по строке = <b>Кол-во × Цена за ед.</b> (₽/мес). "
    "Годовые суммы на диаграммах = месячный итог × 12."
)

COMPOSE_NOTE = (
    "Состав профилей: <b>Pilot</b> — <code>docker-compose.pilot.yml</code> "
    "(8 hot-path контейнеров, без Solr; см. <code>deploy/qemu/RESOURCES.md</code>); "
    "<b>Standard</b> — <code>docker-compose.full-server.yml</code> с масштабированием "
    "(Solr+ZK, replica PG, кэш). "
    "Full-stack monolith на одном хосте (64 ГБ) — справочное сравнение для Pilot, не рекомендуемый профиль."
)


@dataclass(frozen=True)
class UnitRate:
    key: str
    label: str
    unit: str
    price: int
    comment: str
    source: str


UNIT_RATES: tuple[UnitRate, ...] = (
    UnitRate(
        "server_16",
        "Сервер приложений",
        "16 ГБ RAM, 8 vCPU, 1 VM",
        28_000,
        "Pilot hot-path (core-api, pipeline, PG hot)",
        "VDS / dedicated",
    ),
    UnitRate(
        "server_32",
        "Сервер приложений",
        "32 ГБ RAM, 8 vCPU, 1 VM",
        45_000,
        "Standard tier / хост two-host",
        "VDS / dedicated",
    ),
    UnitRate(
        "server_64",
        "Сервер (full stack)",
        "64 ГБ RAM, 16 vCPU, 1 VM",
        72_000,
        "Baseline §10.3 @10k: full-server monolith (справочно)",
        "VDS / dedicated",
    ),
    UnitRate(
        "web_8",
        "Веб + балансировщик",
        "8 ГБ RAM, 4 vCPU, 1 VM",
        15_000,
        "korus-web (2 реплики Tomcat + nginx lb)",
        "VDS / dedicated",
    ),
    UnitRate(
        "solr_overhead",
        "Solr + ZK (overhead full-server)",
        "доля JVM/RAM в baseline",
        3_000,
        "Доп. к Pilot: Solr+ZK в full-stack monolith",
        "VDS / dedicated",
    ),
    UnitRate(
        "pg_pair",
        "PostgreSQL primary + replica",
        "2 × 32 ГБ VM",
        90_000,
        "2 × 45 000 ₽ (выделенные узлы БД)",
        "VDS / dedicated",
    ),
    UnitRate(
        "redis_cluster",
        "Redis cluster",
        "3 узла managed",
        25_000,
        "Кэш + pub/sub Standard",
        "VDS / dedicated",
    ),
    UnitRate(
        "solr_zk_3",
        "Solr + ZK",
        "3 × 16 ГБ VM",
        75_000,
        "3 × 25 000 ₽/узел",
        "VDS / dedicated",
    ),
    UnitRate(
        "nats_kc_workers",
        "NATS, Keycloak, workers",
        "набор JVM на Standard",
        45_000,
        "Lump-sum: NATS, Keycloak HA-path, фоновые workers",
        "VDS / dedicated",
    ),
    UnitRate(
        "ssd_tb",
        "Диск SSD",
        "1 ТБ",
        3_500,
        "Hot DB / оперативные данные",
        "Диски",
    ),
    UnitRate(
        "hdd_tb",
        "Диск HDD (архив)",
        "1 ТБ",
        800,
        "MinIO, файлы, deep archive",
        "Диски",
    ),
    UnitRate(
        "turn",
        "TURN (звонки)",
        "4 vCPU VM",
        12_000,
        "Опционально Pilot",
        "VDS / dedicated",
    ),
    UnitRate(
        "ops_pilot",
        "Backup + мониторинг",
        "контур Pilot",
        5_000,
        "Базовый ops",
        "Ops",
    ),
    UnitRate(
        "ops_std",
        "Ops (backup, monitoring)",
        "контур Standard",
        15_000,
        "Расширенный ops",
        "Ops",
    ),
    UnitRate(
        "channel_200",
        "Канал связи",
        "200 Мбит/с",
        8_000,
        "Pilot / 10k",
        "Канал",
    ),
    UnitRate(
        "channel_1g",
        "Канал связи",
        "1 Гбит/с",
        35_000,
        "Standard / 100k",
        "Канал",
    ),
    UnitRate(
        "cloud_vm",
        "Облако: compute",
        "экв. 24 vCPU·GB Pilot",
        42_000,
        "IaaS VM bundle",
        "Облако (аналог)",
    ),
    UnitRate(
        "cloud_db",
        "Облако: managed DB",
        "500 ГБ",
        12_000,
        "Managed PostgreSQL",
        "Облако (аналог)",
    ),
    UnitRate(
        "cloud_storage",
        "Облако: object storage",
        "5 ТБ",
        8_500,
        "S3-compatible",
        "Облако (аналог)",
    ),
    UnitRate(
        "cloud_egress",
        "Облако: egress / канал",
        "200 Мбит/с экв.",
        9_000,
        "Исходящий трафик + IP",
        "Облако (анalog)",
    ),
    UnitRate(
        "cloud_monitor",
        "Облако: monitoring",
        "контур",
        6_500,
        "Logs + metrics SaaS",
        "Облако (анalog)",
    ),
)

_RATES = {r.key: r for r in UNIT_RATES}


@dataclass(frozen=True)
class CostLine:
    label: str
    qty: float
    rate_key: str
    color: str = "#6366f1"

    @property
    def unit_price(self) -> int:
        return _RATES[self.rate_key].price

    @property
    def monthly(self) -> int:
        return round(self.qty * self.unit_price)


def _sum_monthly(lines: tuple[CostLine, ...]) -> int:
    return sum(line.monthly for line in lines)


PILOT_PROFILE: tuple[CostLine, ...] = (
    CostLine("Сервер приложений (16 ГБ)", 1, "server_16", "#6366f1"),
    CostLine("Web-шлюз (8 ГБ)", 1, "web_8", "#3b82f6"),
    CostLine("Диск SSD (hot DB)", 0.5, "ssd_tb", "#8b5cf6"),
    CostLine("Диск HDD (файлы+архив)", 4.5, "hdd_tb", "#a855f7"),
    CostLine("Канал 200 Мбит/с", 1, "channel_200", "#f59e0b"),
    CostLine("Backup + мониторинг", 1, "ops_pilot", "#10b981"),
)

PILOT_FULLSTACK_MONOLITH: tuple[CostLine, ...] = (
    CostLine("Сервер full stack (64 ГБ, monolith)", 1, "server_64", "#fca5a5"),
    CostLine("Web-шлюз (8 ГБ)", 1, "web_8", "#f87171"),
    CostLine("Solr + ZK (overhead full-server)", 1, "solr_overhead", "#ef4444"),
    CostLine("Диск SSD (hot DB)", 0.5, "ssd_tb", "#c084fc"),
    CostLine("Диск HDD (monolith §10.3)", 6, "hdd_tb", "#a855f7"),
    CostLine("Канал 200 Мбит/с", 1, "channel_200", "#f59e0b"),
    CostLine("Backup + мониторинг", 1, "ops_pilot", "#10b981"),
)

STANDARD_PROFILE: tuple[CostLine, ...] = (
    CostLine("Серверы приложений ×3", 3, "server_32", "#6366f1"),
    CostLine("Web + LB ×2", 2, "web_8", "#3b82f6"),
    CostLine("PostgreSQL primary + replica", 1, "pg_pair", "#2563eb"),
    CostLine("Redis cluster", 1, "redis_cluster", "#dc2626"),
    CostLine("Solr + ZK ×3", 1, "solr_zk_3", "#7c3aed"),
    CostLine("MinIO / файлы (~30 ТБ)", 30, "hdd_tb", "#059669"),
    CostLine("NATS, Keycloak, workers", 1, "nats_kc_workers", "#0891b2"),
    CostLine("Канал 1 Гбит/с", 1, "channel_1g", "#f59e0b"),
    CostLine("Ops (backup, monitoring)", 1, "ops_std", "#10b981"),
)

CLOUD_PILOT_ANALOG: tuple[CostLine, ...] = (
    CostLine("Облако: compute", 1, "cloud_vm", "#93c5fd"),
    CostLine("Облако: managed DB", 1, "cloud_db", "#60a5fa"),
    CostLine("Облако: object storage", 1, "cloud_storage", "#3b82f6"),
    CostLine("Облако: egress / канал", 1, "cloud_egress", "#2563eb"),
    CostLine("Облако: monitoring", 1, "cloud_monitor", "#1d4ed8"),
)

PILOT_OPTIONAL_TURN = CostLine("TURN (опционально)", 1, "turn", "#f97316")

# Legacy aliases for build scripts
PILOT_OPTIMIZED = PILOT_PROFILE
PILOT_BASELINE = PILOT_FULLSTACK_MONOLITH
STANDARD_BASELINE = STANDARD_PROFILE

STANDARD_OPT_RATIO = 332_000 / 474_000  # sizing factor vs nominal line-item sum


def pilot_profile_monthly(*, with_turn: bool = False) -> int:
    total = _sum_monthly(PILOT_PROFILE)
    if with_turn:
        total += PILOT_OPTIONAL_TURN.monthly
    return total


def pilot_fullstack_monolith_monthly() -> int:
    return _sum_monthly(PILOT_FULLSTACK_MONOLITH)


def standard_profile_monthly() -> int:
    return round(_sum_monthly(STANDARD_PROFILE) * STANDARD_OPT_RATIO)


def pilot_optimized_monthly(*, with_turn: bool = False) -> int:
    return pilot_profile_monthly(with_turn=with_turn)


def pilot_baseline_monthly() -> int:
    return pilot_fullstack_monolith_monthly()


def standard_baseline_monthly() -> int:
    return _sum_monthly(STANDARD_PROFILE)


def standard_optimized_monthly() -> int:
    return standard_profile_monthly()


def cloud_pilot_monthly() -> int:
    return _sum_monthly(CLOUD_PILOT_ANALOG)


def fmt_rub(amount: int) -> str:
    return f"{amount:,}".replace(",", " ") + " ₽"


def fmt_rub_short(amount: int) -> str:
    if amount >= 1_000_000:
        whole = amount // 1_000_000
        frac = round((amount % 1_000_000) / 100_000)
        if frac in (0, 10):
            return f"{whole} млн ₽" if frac == 0 else f"{whole + 1} млн ₽"
        return f"{whole},{frac} млн ₽"
    if amount >= 1_000:
        return f"{amount // 1_000} тыс ₽"
    return f"{amount} ₽"


def fmt_qty(qty: float) -> str:
    if qty == int(qty):
        return str(int(qty))
    return str(qty).replace(".", ",")


def _rate_cell(rate_key: str) -> str:
    r = _RATES[rate_key]
    return f'{fmt_rub(r.price)} <span class="small">({escape(r.unit)})</span>'


def render_price_methodology_html() -> str:
    source_rows = "".join(
        f"<tr><td>{escape(name)}</td><td>{escape(desc)}</td></tr>"
        for name, desc in PRICE_SOURCES
    )
    rate_rows = "".join(
        f"<tr><td>{escape(r.label)}</td><td>{escape(r.unit)}</td>"
        f'<td class="money">{fmt_rub(r.price)}</td>'
        f"<td>{escape(r.comment)}</td><td>{escape(r.source)}</td></tr>"
        for r in UNIT_RATES
    )
    return f"""
<h3 id="s17-1">17.1 Условные тарифы и методика расчёта</h3>
<div class="note">
  <div class="req">Источник и дата ставок</div>
  <div class="comment">
    <b>Дата актуальности:</b> {PRICE_AS_OF}. <b>Регион:</b> {PRICE_REGION}. <b>НДС:</b> {PRICE_VAT}.
    Ставки — <b>учебный шаблон для КП</b>, не коммерческое предложение и не обязательство закупки.
    Перед подписанием договора подставьте прайс выбранного провайдера (on-prem / colocation / облако).
  </div>
</div>
<p class="small">{PRICE_FORMULA}</p>
<p class="small">{COMPOSE_NOTE}</p>
<table>
  <tr><th>Категория источника</th><th>Как интерпретировать</th></tr>
  {source_rows}
</table>
<table>
  <tr><th>Статья</th><th>Единица</th><th>Цена за ед.</th><th>Назначение</th><th>Источник</th></tr>
  {rate_rows}
</table>
"""


def _cost_table_rows(lines: tuple[CostLine, ...]) -> str:
    rows = []
    for line in lines:
        rows.append(
            f"<tr><td>{escape(line.label)}</td>"
            f"<td>{fmt_qty(line.qty)}</td>"
            f"<td>{_rate_cell(line.rate_key)}</td>"
            f'<td class="money">{line.monthly:,}</td></tr>'.replace(",", " ")
        )
    return "".join(rows)


def render_section_18_examples_html() -> str:
    pilot = pilot_profile_monthly()
    pilot_turn = pilot_profile_monthly(with_turn=True)
    monolith = pilot_fullstack_monolith_monthly()
    cloud = cloud_pilot_monthly()
    std = standard_profile_monthly()
    savings_pct = round((1 - pilot / monolith) * 100) if monolith else 0

    return f"""
<h3>17.2 Пример A — Pilot, 10 000 пользователей</h3>
<div class="cost-box">
<p><b>Исходные:</b> 10 000 зарегистрированных; ~5 000 DAU; ~750 peak online; диск ~5 ТБ на первый год (§10.3).
  Профиль: <code>docker-compose.pilot.yml</code>.</p>
<table>
  <tr><th>Статья расходов</th><th>Кол-во</th><th>Цена за ед. (§17.1)</th><th>₽/мес</th></tr>
  {_cost_table_rows(PILOT_PROFILE)}
  <tr><td>{escape(PILOT_OPTIONAL_TURN.label)}</td><td>1</td><td>{_rate_cell(PILOT_OPTIONAL_TURN.rate_key)}</td>
      <td class="money">{PILOT_OPTIONAL_TURN.monthly:,}</td></tr>
  <tr><th colspan="3">Итого infra (с TURN)</th><th class="money">{fmt_rub(pilot_turn)}</th></tr>
  <tr><th colspan="3">Итого infra (без TURN)</th><th class="money">{fmt_rub(pilot)}</th></tr>
</table>
<p><b>На пользователя:</b> {pilot:,} ÷ 10 000 = <span class="money">~{pilot / 10_000:.1f} ₽/пользователь/мес</span> (только infra).</p>
<p class="small"><b>Справочно:</b> full-stack monolith на одном сервере (64 ГБ, Solr+ZK) — <span class="money">{fmt_rub(monolith)}/мес</span>
  (~{savings_pct}% дороже Pilot-профиля; не рекомендуется для пилота).</p>
</div>

<h3>17.3 Пример B — Standard, 100 000 пользователей</h3>
<div class="cost-box">
<table>
  <tr><th>Статья</th><th>Кол-во</th><th>Цена за ед. (§17.1)</th><th>₽/мес</th></tr>
  {_cost_table_rows(STANDARD_PROFILE)}
  <tr><th colspan="3">Итого Standard-профиль</th><th class="money">{fmt_rub(std)}</th></tr>
</table>
<p><b>На пользователя:</b> {std:,} ÷ 100 000 = <span class="money">~{std / 100_000:.1f} ₽/пользователь/мес</span>.</p>
<p class="small">Итог учитывает рекомендуемый sizing §10.3 (кэш, replica, сжатие архива, пакетный Solr).</p>
</div>

<h3>17.3.1 Облако (анalog) — Pilot 10k для Рис. 7</h3>
<div class="cost-box">
<p class="small">Сводный IaaS-пакет с тем же порядком ресурсов, что Pilot on-prem; ставки §17.1, категория «Облако (анalog)».</p>
<table>
  <tr><th>Статья</th><th>Кол-во</th><th>Цена за ед.</th><th>₽/мес</th></tr>
  {_cost_table_rows(CLOUD_PILOT_ANALOG)}
  <tr><th colspan="3">Итого облако (анalog)</th><th class="money">{fmt_rub(cloud)}</th></tr>
</table>
</div>

<h3>17.4 Пример C — TCO на 3 года (Pilot 10k)</h3>
<div class="cost-box">
<table>
  <tr><th>Статья</th><th>Разово</th><th>₽/мес × 36 мес</th><th>Итого 3 года</th></tr>
  <tr><td>Infra (без TURN)</td><td>—</td><td>{pilot:,}</td><td class="money">{fmt_rub(pilot * 36)}</td></tr>
  <tr><td>Внедрение (развёртывание, обучение admin)</td><td class="money">600 000 ₽</td><td>—</td><td class="money">600 000 ₽</td></tr>
  <tr><td>Поддержка L2 (15% от infra/год)</td><td>—</td><td>~{round(pilot * 12 * 0.15 / 12):,}</td><td class="money">{fmt_rub(round(pilot * 12 * 0.15 * 3))}</td></tr>
  <tr><th colspan="3">TCO 3 года</th><th class="money">{fmt_rub(pilot * 36 + 600_000 + round(pilot * 12 * 0.15 * 3))}</th></tr>
</table>
</div>

<h3>17.5 Пример D — рост диска (бухгалтерия, 100k users)</h3>
<div class="cost-box">
<p><b>Формула:</b> Диск файлов/год ≈ Пользователи × ГБ на пользователя/год</p>
<p>100 000 × 0,4 ГБ = <b>40 ТБ новых файлов в год</b></p>
<p>При тарифе {_RATES['hdd_tb'].price} ₽/ТБ/мес (§17.1, {PRICE_AS_OF}): 40 × {_RATES['hdd_tb'].price} = <span class="money">{fmt_rub(40 * _RATES['hdd_tb'].price)}</span> <b>дополнительно</b> каждый год (если не сжимать архив).</p>
<p>Со сжатием deep-archive (zstd) и dedup файлов (roadmap Enterprise): ориентир <span class="money">~{fmt_rub(round(40 * _RATES['hdd_tb'].price * 0.65 * 0.7))}</span> прироста вместо {fmt_rub(40 * _RATES['hdd_tb'].price)}.</p>
</div>

<h3>17.6 Строки для коммерческого предложения (шаблон)</h3>
<table>
  <tr><th>Позиция КП</th><th>Ед.</th><th>Пример цены</th></tr>
  <tr><td>Лицензия / право использования</td><td>пользователь/год</td><td>по прайсу вендора</td></tr>
  <tr><td>Внедрение Pilot</td><td>контур</td><td class="money">400 000 – 800 000 ₽</td></tr>
  <tr><td>Внедрение Standard</td><td>контур</td><td class="money">1 500 000 – 3 000 000 ₽</td></tr>
  <tr><td>Обучение администраторов (1 день)</td><td>сессия</td><td class="money">50 000 – 120 000 ₽</td></tr>
  <tr><td>Поддержка L2 (8×5)</td><td>% от infra или фикс</td><td class="money">10–18%</td></tr>
  <tr><td>Infra (см. примеры §17.2–17.3)</td><td>мес</td><td>по таблице §17.1</td></tr>
</table>

<div class="warn">
  <div class="req">Налоги и учёт</div>
  <div class="comment">Infra — OPEX (облако/аренда) или амортизация CAPEX (собственные серверы). Лицензия ПО — по политике заказчика. Уточняйте у бухгалтерии до фиксации КП.</div>
</div>
"""


def render_fig_cost_monthly_svg() -> str:
    """Рис. 7 — monthly comparison at 10k Pilot."""
    pilot = pilot_profile_monthly()
    monolith = pilot_fullstack_monolith_monthly()
    cloud = cloud_pilot_monthly()
    max_v = max(monolith, pilot, cloud)
    chart_h = 144

    def bar_h(v: int) -> int:
        return max(8, round(v / max_v * chart_h))

    h_mono, h_pilot, h_cloud = bar_h(monolith), bar_h(pilot), bar_h(cloud)
    y_mono = 200 - h_mono
    y_pilot = 200 - h_pilot
    y_cloud = 200 - h_cloud

    return f"""<figure class="fig"><svg viewBox="0 0 560 260" width="560" height="260" xmlns="http://www.w3.org/2000/svg">
  <text x="280" y="24" text-anchor="middle" font-size="13" font-weight="bold">Сравнение: инфраструктура в месяц (10 000 пользователей, Pilot)</text>
  <line x1="64" y1="200" x2="500" y2="200" stroke="#9ca3af"/>
  <line x1="64" y1="200" x2="64" y2="48" stroke="#9ca3af"/>
  <line x1="64" y1="88" x2="500" y2="88" stroke="#e5e7eb"/>
  <line x1="64" y1="136" x2="500" y2="136" stroke="#e5e7eb"/>
  <text x="58" y="204" text-anchor="end" font-size="9">0</text>
  <text x="58" y="140" text-anchor="end" font-size="9">50k</text>
  <text x="58" y="92" text-anchor="end" font-size="9">100k</text>
  <rect x="92" y="{y_mono}" width="96" height="{h_mono}" fill="#fca5a5" rx="3"/>
  <text x="140" y="{y_mono - 8}" text-anchor="middle" font-size="11" font-weight="600">{fmt_rub(monolith)}</text>
  <text x="140" y="218" text-anchor="middle" font-size="10">Monolith (справ.)</text>
  <rect x="232" y="{y_pilot}" width="96" height="{h_pilot}" fill="#86efac" rx="3"/>
  <text x="280" y="{y_pilot - 8}" text-anchor="middle" font-size="11" font-weight="600">{fmt_rub(pilot)}</text>
  <text x="280" y="218" text-anchor="middle" font-size="10">Pilot</text>
  <rect x="372" y="{y_cloud}" width="96" height="{h_cloud}" fill="#93c5fd" rx="3"/>
  <text x="420" y="{y_cloud - 8}" text-anchor="middle" font-size="11" font-weight="600">{fmt_rub(cloud)}</text>
  <text x="420" y="218" text-anchor="middle" font-size="10">Облако (анalog)</text>
</svg><figcaption class="fig-cap">Рис. 7. Ежемесячные затраты infra (10k): Pilot-профиль, справочный full-stack monolith и облачный аналog. Ставки §17.1, дата {PRICE_AS_OF}; не оферта.</figcaption></figure>"""


def render_legend_rate_table_html(lines: tuple[CostLine, ...]) -> str:
    """HTML table: chart segment → unit rate (for §17.7 legend)."""
    seen: set[str] = set()
    rows = []
    for line in lines:
        if line.rate_key in seen:
            continue
        seen.add(line.rate_key)
        r = _RATES[line.rate_key]
        rows.append(
            f'<tr><td><span style="display:inline-block;width:10px;height:10px;background:{escape(line.color)}"></span></td>'
            f"<td>{escape(r.label)} — {escape(line.label)}</td>"
            f"<td>{escape(r.unit)}</td>"
            f'<td class="money">{fmt_rub(r.price)}</td>'
            f"<td>{escape(r.source)}</td></tr>"
        )
    return (
        '<table class="small"><tr><th></th><th>Сегмент диаграммы</th><th>Ед.</th>'
        f"<th>Ставка ({PRICE_AS_OF})</th><th>Источник</th></tr>{''.join(rows)}</table>"
    )
