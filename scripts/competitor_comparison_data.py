"""Data and SVG charts for competitor comparison presentation."""

from __future__ import annotations

import json
from dataclasses import dataclass
from html import escape
from pathlib import Path

from tz_product_pricing import (
    PRICE_AS_OF,
    _RATES,
    fmt_rub,
    fmt_rub_short,
    pilot_fullstack_monolith_monthly,
    pilot_profile_monthly,
    standard_profile_monthly,
)

from competitor_products import (  # noqa: E402
    COMPARISON_CRITERIA,
    LOOP_PRO_RUB_MONTH,
    COMPASS_ONPREM_RUB_MONTH,
    PRODUCT_COLUMNS,
    PRODUCT_FEATURES,
    PROS_CONS_BY_PRODUCT,
    RADAR_AXES,
    RADAR_MAX,
    RADAR_ONPREM,
    TIER_LABELS,
    heatmap_color,
    tier_b_tco_rows,
    tier_c_market_rows,
)

# --- Pricing constants (public sources) ---

EXPRESS_LICENSE_RUB_PER_USER_YEAR = 3_000
EXPRESS_SMARTAPPS_RUB_PER_USER_YEAR = 5_000
EXPRESS_LITE_RUB_PER_USER_MONTH = 200
EXPRESS_SA_RENEWAL_CORP = 1_500
EXPRESS_SA_RENEWAL_SMARTAPPS = 2_500

PACHKA_CORP_RUB_PER_USER_MONTH_YEAR = 399
PACHKA_COMPANY_RUB_PER_USER_MONTH_YEAR = 159
VK_SAAS_RUB_PER_USER_MONTH_YEAR = 207

# Unit rates shorthand (₽/month)
_R16 = _RATES["server_16"].price
_R32 = _RATES["server_32"].price
_RWEB = _RATES["web_8"].price
_RSSD = _RATES["ssd_tb"].price
_RHDD = _RATES["hdd_tb"].price
_RCH200 = _RATES["channel_200"].price
_RCH1G = _RATES["channel_1g"].price
_ROPS_P = _RATES["ops_pilot"].price
_ROPS_S = _RATES["ops_std"].price

_REPO_ROOT = Path(__file__).resolve().parents[1]
_NT_PATHS = (
    _REPO_ROOT / "deploy/qemu/run/load-presentation-summary.json",
    _REPO_ROOT / "docs/benchmarks/qemu-nt-baseline-2026-06-15.json",
)

# Проектные цели якоря S-10k (§10 PRODUCT_PRESENTATION) — для сравнения с замером НТ
_DESIGN_S10K_PEAK_MSG_S = 15
_DESIGN_PILOT_PEAK_MSG_S = 15


def _ru_profile(profile: str) -> str:
    return {
        "Standard": "Стандарт",
        "Enterprise": "Корпоративный",
        "Пробник": "Пробник",
    }.get(profile, profile)


def _tier_ru(tier: str) -> str:
    return {"A": "Уровень A", "B": "Уровень B", "C": "Уровень C"}.get(tier, tier)


def _ru_deployment(text: str) -> str:
    out = text
    for old, new in (
        ("on-prem / облако", "в контуре / облако"),
        ("SaaS / on-prem", "облако / в контуре"),
        ("on-prem / UC", "в контуре / UC"),
        ("on-prem", "в контуре"),
        ("SaaS", "облако"),
    ):
        out = out.replace(old, new)
    return out


# Размеры SVG: ширина под max-width страницы (~1240px), увеличенные шрифты для печати/PDF
_CHART_WIDE = 1040
_CHART_STD_H = 380
_CHART_STACKED_H = 420


def _fmt_reg_users(ru: int, *, compact: bool = False) -> str:
    """Подпись числа зарегистрированных пользователей (не путать с ₽)."""
    spaced = f"{ru:,}".replace(",", " ")
    if compact:
        if ru >= 1_000_000:
            return f"{ru // 1_000_000} млн рег."
        if ru >= 1_000:
            return f"{ru // 1_000} тыс. рег."
        return f"{ru} рег."
    return f"{spaced} рег. пользов."


def _fmt_chart_y_value(value: int, *, unit: str) -> str:
    """Формат значения на оси Y / над столбцом."""
    if not unit:
        return str(value)
    if unit in ("₽/год", "₽"):
        return fmt_rub_short(value)
    if unit == "ГБ":
        return f"{value} ГБ"
    if unit == "мс":
        return f"{value} мс"
    if unit == "запр/с":
        return f"{value} запр/с"
    if unit == "сообщ/с":
        return f"{value} сообщ/с"
    if unit == "%":
        return f"{value}%"
    return str(value)


def _fmt_per_user_month(rub: float) -> str:
    return f"{rub:.1f} ₽/рег. пользов./мес"


@dataclass(frozen=True)
class KorusAnchor:
    code: str
    profile: str
    ru: int
    peak_online: int
    peak_msg_s: int
    ram_gb: str
    infra_monthly: int
    infra_note: str = ""

    @property
    def infra_yearly(self) -> int:
        return self.infra_monthly * 12

    @property
    def infra_per_user_month(self) -> float:
        return self.infra_monthly / self.ru


def _pack_ram_monthly(ram_gb: int) -> int:
    n32 = ram_gb // 32
    rem = ram_gb - n32 * 32
    n16 = (rem + 15) // 16 if rem > 0 else 0
    return n32 * _R32 + n16 * _R16


def estimate_infra_monthly(
    *,
    ram_gb: int,
    ssd_tb: float,
    ru: int,
    web_nodes: int = 1,
    hdd_tb: float = 0,
) -> int:
    """Map vendor hardware totals to VM bill using shared unit rates."""
    vm = _pack_ram_monthly(ram_gb)
    web = _RWEB * web_nodes
    disk = round(ssd_tb * _RSSD + hdd_tb * _RHDD)
    channel = _RCH1G if ru >= 10_000 else _RCH200
    ops = _ROPS_S if ru >= 100_000 else _ROPS_P
    return vm + web + disk + channel + ops


# Enterprise infra — sizing estimates (mid-range)
_ENTERPRISE_500K_MONTHLY = 1_000_000
_ENTERPRISE_1M_MONTHLY = 2_000_000
_STANDARD_50K_MONTHLY = round((pilot_fullstack_monolith_monthly() + standard_profile_monthly()) / 2)

KORUS_ANCHORS: tuple[KorusAnchor, ...] = (
    KorusAnchor(
        "S-10k",
        "Standard",
        10_000,
        750,
        15,
        "~64 GB",
        pilot_fullstack_monolith_monthly(),
        "Полный функционал Standard, не пробник",
    ),
    KorusAnchor(
        "S-50k",
        "Standard",
        50_000,
        2_400,
        90,
        "~140 GB",
        _STANDARD_50K_MONTHLY,
        "Интерполяция между S-10k и S-100k",
    ),
    KorusAnchor(
        "S-100k",
        "Standard",
        100_000,
        4_800,
        120,
        "~140 GB",
        standard_profile_monthly(),
    ),
    KorusAnchor(
        "E-500k",
        "Enterprise",
        500_000,
        15_000,
        400,
        "~450 GB",
        _ENTERPRISE_500K_MONTHLY,
        "Оценка; НТ на stage",
    ),
    KorusAnchor(
        "E-1M",
        "Enterprise",
        1_000_000,
        20_000,
        600,
        "~0,9–1,2 TB",
        _ENTERPRISE_1M_MONTHLY,
        "Оценка; НТ на stage",
    ),
)

PILOT_TRIAL = KorusAnchor(
    "Pilot",
    "Пробник",
    10_000,
    750,
    15,
    "12–16 GB",
    pilot_profile_monthly(),
    "Только POC; вне матрицы сравнения",
)


@dataclass(frozen=True)
class ExpressTier:
    ru: int
    vcpu: int
    ram_gb: int
    ssd_tb: float
    in_korus_matrix: bool
    korus_anchor: str
    source: str
    components: tuple[tuple[str, int, int, float], ...] = ()

    @property
    def infra_monthly_est(self) -> int:
        return estimate_infra_monthly(ram_gb=self.ram_gb, ssd_tb=self.ssd_tb, ru=self.ru)

    @property
    def infra_yearly_est(self) -> int:
        return self.infra_monthly_est * 12

    @property
    def license_yearly(self) -> int:
        return self.ru * EXPRESS_LICENSE_RUB_PER_USER_YEAR


EXPRESS_TIERS: tuple[ExpressTier, ...] = (
    ExpressTier(
        100,
        11,
        17,
        0.431,
        False,
        "—",
        "docs.express.ms (официально при 100 рег. пользов.)",
        (
            ("Proxy", 1, 1, 0.045),
            ("Media", 3, 2, 0.045),
            ("Transcoding", 2, 4, 0.065),
            ("Back CTS", 4, 8, 0.211),
            ("Bot", 1, 2, 0.065),
        ),
    ),
    ExpressTier(
        500,
        37,
        42,
        1.097,
        False,
        "—",
        "Обзор eXpress 3.48 / таблицы вендора",
        (
            ("Front CTS", 2, 1, 0.045),
            ("Media", 19, 10, 0.045),
            ("Transcoding", 4, 4, 0.065),
            ("Back CTS", 8, 16, 0.797),
            ("Bot", 4, 7, 0.145),
        ),
    ),
    ExpressTier(
        1000,
        62,
        62,
        2.175,
        False,
        "—",
        "Обзор eXpress 3.48 / таблицы вендора",
        (
            ("Front CTS", 2, 2, 0.045),
            ("Media 1", 18, 10, 0.045),
            ("Media 2", 18, 10, 0.045),
            ("Transcoding", 6, 4, 0.065),
            ("Back CTS", 12, 24, 1.530),
            ("Bot", 6, 12, 0.245),
        ),
    ),
    ExpressTier(
        10_000,
        0,
        0,
        0,
        True,
        "S-10k",
        "Экстраполяция от @1k + media 10% online",
    ),
    ExpressTier(
        100_000,
        0,
        0,
        0,
        True,
        "S-100k",
        "Индивидуальный проект вендора (infra — оценка)",
    ),
)

# Manual overrides for tiers without public vCPU/RAM @10k+
EXPRESS_INFRA_YEARLY_OVERRIDE: dict[int, int] = {
    10_000: estimate_infra_monthly(ram_gb=120, ssd_tb=8, ru=10_000, web_nodes=2) * 12,
    100_000: 25_000_000,
    500_000: 80_000_000,
    1_000_000: 150_000_000,
}


@dataclass(frozen=True)
class CompetitorRow:
    name: str
    license_yearly: int | None
    infra_yearly: int | None
    license_note: str = ""
    infra_note: str = ""
    in_matrix: bool = True

    def total_yearly(self) -> int | None:
        if self.license_yearly is None and self.infra_yearly is None:
            return None
        lic = self.license_yearly or 0
        infra = self.infra_yearly or 0
        return lic + infra


def express_license_yearly(ru: int) -> int:
    return ru * EXPRESS_LICENSE_RUB_PER_USER_YEAR


def pachka_yearly(ru: int, *, corp: bool = True) -> int:
    rate = PACHKA_CORP_RUB_PER_USER_MONTH_YEAR if corp else PACHKA_COMPANY_RUB_PER_USER_MONTH_YEAR
    return ru * rate * 12


def vk_saas_yearly(ru: int) -> int:
    return ru * VK_SAAS_RUB_PER_USER_MONTH_YEAR * 12


def express_infra_yearly(ru: int) -> int | None:
    if ru in EXPRESS_INFRA_YEARLY_OVERRIDE:
        return EXPRESS_INFRA_YEARLY_OVERRIDE[ru]
    for t in EXPRESS_TIERS:
        if t.ru == ru and t.vcpu > 0:
            return t.infra_yearly_est
    return None


def competitors_at_anchor(anchor: KorusAnchor) -> tuple[CompetitorRow, ...]:
    ru = anchor.ru
    expr_infra = express_infra_yearly(ru)
    rows: list[CompetitorRow] = [
        CompetitorRow(
            "Korus Messenger",
            license_yearly=0,
            infra_yearly=anchor.infra_yearly,
            license_note="По политике вендора / КП",
            infra_note=anchor.infra_note or "Расчёт по ставкам infra",
        ),
        CompetitorRow(
            "eXpress Corporate",
            license_yearly=express_license_yearly(ru),
            infra_yearly=expr_infra,
            license_note="3 000 ₽/рег. пользов./год, в контуре заказчика",
            infra_note="Оценка" if expr_infra else "Индивидуальный проект",
        ),
    ]
    if ru <= 100_000:
        rows.extend(
            [
                CompetitorRow(
                    "Пачка (облако)",
                    license_yearly=pachka_yearly(ru),
                    infra_yearly=0,
                    license_note="Корпорация, 399 ₽/рег. пользов./мес (год)",
                ),
                CompetitorRow(
                    "VK WorkSpace SaaS",
                    license_yearly=vk_saas_yearly(ru),
                    infra_yearly=0,
                    license_note="Базовый, 207 ₽/рег. пользов./мес (год)",
                ),
            ]
        )
    return tuple(rows)


FEATURE_ROWS: tuple[tuple[str, str, str, str, str, str], ...] = (
    ("On-prem / изолированный контур", "✓", "✓", "—", "—", "✓"),
    ("Export / legal hold / dual-TTL", "✓ ядро", "DLP/политики", "API экспорта", "зависит", "плагины"),
    ("Полнотекстовый поиск", "Solr / SQL", "✓", "✓", "✓", "Elasticsearch"),
    ("E2EE", "MLS (приёмка)", "✓ E2EE", "—", "—", "опционально"),
    ("ВКС", "WebRTC mesh", "до 500 уч.", "до 10 уч.", "✓", "интеграции"),
    ("Мобильные клиенты", "дорожная карта", "iOS/Android/Аврора", "✓", "✓", "✓"),
    ("SmartApps / суперапп", "дорожная карта", "✓", "боты/API", "рабочее пространство", "приложения"),
    ("ФСТЭК / реестр РФ", "в процессе", "✓", "✓", "✓", "✓ (Loop)"),
    ("Публичный sizing", "✓ якоря", "частично", "—", "on-prem КП", "✓ до 2k"),
    ("Публичный прайс лицензии", "КП", "✓", "✓", "✓ SaaS", "КП EE"),
)

REFERENCE_SOLUTIONS: tuple[tuple[str, str, str, str], ...] = (
    (
        "Loop",
        "1–2 тыс. рег. пользов.: 2–4 ГБ RAM",
        "Enterprise по запросу",
        "Mattermost-fork, РФ",
    ),
    (
        "Mattermost EE",
        "15k concurrent (ref-arch)",
        "100k concurrent (ref-arch)",
        "одновременные сессии ≠ рег. пользов. (не смешивать)",
    ),
    (
        "Rocket.Chat",
        "≤500 concurrent: 4+4 ГБ",
        "Enterprise ≥500 concurrent",
        "MongoDB replica",
    ),
    (
        "VK Superapp on-prem",
        "1–2 тыс. рег.: 22 vCPU, 56 ГБ, 400 ГБ SSD",
        ">1,5k — инд. расчёт",
        "Почта+календарь, не только IM",
    ),
)

# --- Legacy / устаревшие платформы (вне production TCO-матрицы) ---

@dataclass(frozen=True)
class LegacySolution:
    name: str
    era: str
    protocol: str
    license_model: str
    small_scale: str
    large_scale: str
    infra_note: str
    status: str
    migration_angle: str


LEGACY_SOLUTIONS: tuple[LegacySolution, ...] = (
    LegacySolution(
        "XMPP / Jabber (протокол)",
        "2000–2010-е",
        "XMPP (RFC 6120/6121)",
        "Открытый код; клиенты и серверы разных вендоров",
        "1 узел 2–4 ГБ RAM, до ~2 тыс. рег. пользов.",
        "Кластер 3+ узлов; от 10 тыс. рег. — кастомная архитектура",
        "Нет единого sizing; зависит от сервера (ejabberd/Openfire/Prosody)",
        "Устарело; новые внедрения редки",
        "Миграция истории (MAM), roster, federated domains",
    ),
    LegacySolution(
        "ejabberd",
        "2000–2020-е",
        "XMPP + MQTT (опционально)",
        "GPL + коммерческая поддержка ProcessOne",
        "4 GB RAM, тысячи одновременных сессий",
        "Кластер: сотни тысяч рег. пользов. (док. вендора)",
        "Erlang-кластер; Mnesia/SQL для persistence",
        "Активен, но нишевый; enterprise — через интегратора",
        "Экспорт MAM/Roster; часто параллельный запуск с новым IM",
    ),
    LegacySolution(
        "Openfire (Spark)",
        "2000–2010-е",
        "XMPP",
        "Apache 2.0; Ignite Realtime / Community",
        "2–4 ГБ RAM, до ~5 тыс. рег. пользов. (один узел)",
        "Clustering plugin; от 10 тыс. рег. — не типовой сценарий",
        "Java/Tomcat; HSQLDB/PostgreSQL/MySQL",
        "Community поддерживается; enterprise — сторонние плагины",
        "Типичный «банковский Jabber» 2005–2015; замена клиентов",
    ),
    LegacySolution(
        "Prosody",
        "2010–2020-е",
        "XMPP",
        "MIT / ISC",
        "512 MB–2 GB RAM, малые команды",
        ">5 тыс. рег. пользов. — не ref-arch; federation only",
        "Lua; лёгкий один узел",
        "Активен для малых контуров и federation",
        "Часто не целевой масштаб для 10k+",
    ),
    LegacySolution(
        "IBM / HCL Sametime",
        "2000–2010-е",
        "Проприетарный + частично XMPP/SIP",
        "Коммерческая лицензия IBM → HCL",
        "Отдельный сервер приложений + DB",
        "Кластер Domino/Sametime; типично 10–100 тыс. рег. пользов.",
        "Тяжёлый Java/Domino-стек; инд. sizing",
        "Устарело; HCL поддерживает, но рынок смещён",
        "Compliance-архивы, интеграция с почтой/календарём",
    ),
    LegacySolution(
        "Microsoft Lync / Skype for Business",
        "2010–2020-е",
        "SIP / проприетарный (не XMPP)",
        "Microsoft EA / подписка",
        "Front End pool + SQL; от ~8 GB на роль",
        "Enterprise pool + Edge; 100 тыс.+ рег. пользов.",
        "Windows Server + SQL Always On",
        "Skype for Business → Teams migration path",
        "Голос/ВКС — отдельный контур; чат → Teams",
    ),
    LegacySolution(
        "Cisco Jabber (on-prem UC)",
        "2010–2020-е",
        "SIP/XMPP (зависит от CUCM/IMP)",
        "Cisco Smart Net + лицензии CUCM/IMP",
        "IMP + CUCM; от 16 GB RAM на кластер",
        "Крупный UC: CUCM cluster + IMP cluster",
        "Часть Unified Communications, не «только чат»",
        "Cisco уходит на Webex; on-prem — устарело",
        "UC-миграция vs выделенный корп. мессенджер",
    ),
)

# Infra оценки legacy @ production-якорях (минимальный HA, те же ставки)
def legacy_xmpp_infra_monthly(ru: int, *, ha: bool = True) -> int:
    """Оценка OPEX для типового XMPP-кластера (ejabberd/Openfire class)."""
    if ru <= 2_000:
        base = _R16 + _RWEB + 2 * _RSSD + _ROPS_P
        return base
    if ru <= 10_000:
        if ha:
            return 2 * _R16 + _R32 + _RWEB + 4 * _RSSD + _RCH200 + _ROPS_P
        return _R32 + _RWEB + 2 * _RSSD + _ROPS_P
    if ru <= 100_000:
        if ha:
            return 4 * _R32 + 2 * _R16 + _RWEB + 20 * _RSSD + _RCH1G + _ROPS_S
        return 2 * _R32 + _R16 + _RWEB + 8 * _RSSD + _RCH200 + _ROPS_S
    return 6 * _R32 + 2 * _R16 + _RWEB + 40 * _RSSD + _RCH1G + _ROPS_S


LEGACY_INFRA_ANCHORS: tuple[tuple[int, str, int, int], ...] = (
    (2_000, "типичный хвост Openfire/Prosody", legacy_xmpp_infra_monthly(2_000), legacy_xmpp_infra_monthly(2_000, ha=False)),
    (10_000, "S-10k (HA и один узел)", legacy_xmpp_infra_monthly(10_000), legacy_xmpp_infra_monthly(10_000, ha=False)),
    (100_000, "S-100k (HA и урезанный)", legacy_xmpp_infra_monthly(100_000), legacy_xmpp_infra_monthly(100_000, ha=False)),
)

LEGACY_FEATURE_ROWS: tuple[tuple[str, str, str, str, str], ...] = (
    ("On-prem в контуре", "✓", "✓ (OSS)", "✓", "✓ (Windows)"),
    ("Современные моб. клиенты", "дорожная карта", "устар./сторонние", "устар. мобил.", "переход на Teams"),
    ("Полнотекстовый поиск", "Solr / SQL", "MAM / ограниченно", "встроенный (устар.)", "индекс Exchange"),
    ("Export / legal hold / audit", "✓ ядро", "самостоятельно / плагины", "частично", "eDiscovery (Teams)"),
    ("E2EE", "MLS (приёмка)", "OMEMO (опц.)", "—", "—"),
    ("ВКС / голос", "WebRTC", "внешние / Jingle (редко)", "✓ (устар.)", "✓ (SIP/Skype)"),
    ("Федерация / multi-tenant org", "org-shard", "✓ XMPP federation", "domino domains", "лес AD"),
    ("Вендор / LTS", "Korus", "community / интегратор", "HCL Sametime", "Microsoft EoL → Teams"),
    ("Публичный sizing от 10 тыс. рег.", "✓ якоря", "нет единого", "инд. IBM/HCL", "ref-arch Microsoft"),
    ("Лицензия ПО", "КП", "0 (OSS) + интеграция", "коммерческая", "EA / подписка"),
    (
        "TCO при 10 тыс. рег. (инфра, HA)",
        fmt_rub_short(KORUS_ANCHORS[0].infra_yearly),
        fmt_rub_short(legacy_xmpp_infra_monthly(10_000) * 12),
        "по КП",
        "по КП",
    ),
)

LEGACY_MIGRATION_CASES: tuple[tuple[str, list[str]], ...] = (
    (
        "Jabber / Openfire → Korus",
        [
            "Экспорт roster и истории (MAM) — кастомный ETL или параллельный read-only архив",
            "Смена клиентов: XMPP-клиенты не совместимы с web/mobile Korus",
            "Federation и внешние домены — пересмотр политики безопасности",
            "Выигрыш: compliance (export, dual-TTL), поиск, единый вендор, путь к E2EE/MLS",
        ],
    ),
    (
        "Sametime / Lync → Korus",
        [
            "Отделение UC (голос/ВКС) от корпоративного чата — часто Teams остаётся для meeting",
            "Архивы Sametime/Domino — отдельный compliance-проект",
            "AD/LDAP интеграция — стандартный сценарий для Korus",
            "Выигрыш: современный UX, on-prem чат без привязки к Windows/Domino",
        ],
    ),
)


LEGACY_PROS_CONS: dict[str, tuple[list[str], list[str]]] = {
    "XMPP / Jabber (устар.)": (
        [
            "Лицензия сервера 0 (OSS): ejabberd, Openfire, Prosody",
            "Низкая инфра на малых масштабах (1–2 узла)",
            "Federation между доменами; открытый протокол",
            "Многие гос/банки уже имели контур — знакомая эксплуатация",
        ],
        [
            "Нет единого вендора, мобильный UX устарел",
            "Compliance (export, legal hold) — самодельные скрипты",
            "От 10 тыс. рег. HA — непрозрачный sizing; поиск слабый",
            "Нет типового пути к E2EE и современному ВКС",
        ],
    ),
    "IBM / HCL Sametime": (
        [
            "Зрелый корпоративный стек; интеграция с Domino/Notes",
            "Знаком крупным заказчикам 2000–2010-х",
        ],
        [
            "Устаревшие лицензии и тяжёлый стек",
            "Рынок смещён на Teams / отечественные мессенджеры",
            "TCO поддержки и миграции часто выше нового внедрения",
        ],
    ),
}


def _bar_chart_svg(
    title: str,
    series: list[tuple[str, int, str] | tuple[str, int, str, str]],
    *,
    width: int = _CHART_WIDE,
    height: int = _CHART_STD_H,
    caption: str,
    y_unit: str = "₽/год",
) -> str:
    if not series:
        return ""
    parsed: list[tuple[str, int, str, str]] = []
    for item in series:
        if len(item) == 4:
            parsed.append(item)  # type: ignore[misc]
        else:
            lbl, val, col = item  # type: ignore[misc]
            parsed.append((lbl, val, col, y_unit))
    max_v = max(v for _, v, _, _ in parsed) or 1
    margin_l, margin_b, margin_t = 96, 64, 52
    chart_h = height - margin_b - margin_t
    bar_w = min(96, (width - margin_l - 48) // max(len(series), 1) - 12)
    gap = 14
    parts = [
        f'<figure class="fig fig-wide"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
        f'xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="28" text-anchor="middle" font-size="16" font-weight="bold">{escape(title)}</text>',
    ]
    if y_unit:
        parts.append(
            f'<text x="18" y="{margin_t + chart_h // 2}" text-anchor="middle" font-size="11" fill="#6b7280" '
            f'transform="rotate(-90 18 {margin_t + chart_h // 2})">{escape(y_unit)}</text>'
        )
    parts.extend([
        f'<line x1="{margin_l}" y1="{margin_t + chart_h}" x2="{width - 20}" y2="{margin_t + chart_h}" stroke="#9ca3af"/>',
    ])
    for i in range(5):
        y = margin_t + chart_h - chart_h * i // 4
        val = max_v * i // 4
        parts.append(f'<line x1="{margin_l}" y1="{y}" x2="{width - 20}" y2="{y}" stroke="#e5e7eb"/>')
        parts.append(
            f'<text x="{margin_l - 8}" y="{y + 5}" text-anchor="end" font-size="11" fill="#4b5563">'
            f"{escape(_fmt_chart_y_value(val, unit=y_unit))}</text>"
        )
    x = margin_l + 12
    for label, value, color, item_unit in parsed:
        h = max(4, round(value / max_v * chart_h))
        y = margin_t + chart_h - h
        parts.append(f'<rect x="{x}" y="{y}" width="{bar_w}" height="{h}" fill="{color}" rx="3"/>')
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{y - 8}" text-anchor="middle" font-size="10" font-weight="600">'
            f"{escape(_fmt_chart_y_value(value, unit=item_unit))}</text>"
        )
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{margin_t + chart_h + 18}" text-anchor="middle" font-size="10">'
            f"{escape(label)}</text>"
        )
        x += bar_w + gap
    parts.append(f'</svg><figcaption class="fig-cap">{escape(caption)}</figcaption></figure>')
    return "".join(parts)


def _stacked_tco_svg(title: str, items: list[tuple[str, int, int]], caption: str) -> str:
    width, height = _CHART_WIDE, _CHART_STACKED_H
    margin_l, margin_t, margin_b = 108, 56, 72
    chart_h = height - margin_t - margin_b
    max_v = max(i + l for _, i, l in items) or 1
    bar_w, gap = 100, 20
    parts = [
        f'<figure class="fig fig-wide"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
        f'xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="28" text-anchor="middle" font-size="16" font-weight="bold">{escape(title)}</text>',
        f'<text x="18" y="{margin_t + chart_h // 2}" text-anchor="middle" font-size="11" fill="#6b7280" '
        f'transform="rotate(-90 18 {margin_t + chart_h // 2})">₽/год</text>',
        f'<rect x="{margin_l - 68}" y="{margin_t - 10}" width="14" height="14" fill="#86efac"/>'
        f'<text x="{margin_l - 50}" y="{margin_t + 2}" font-size="12">Инфра</text>',
        f'<rect x="{margin_l + 48}" y="{margin_t - 10}" width="14" height="14" fill="#6366f1"/>'
        f'<text x="{margin_l + 66}" y="{margin_t + 2}" font-size="12">Лицензия</text>',
        f'<line x1="{margin_l}" y1="{margin_t + chart_h}" x2="{width - 24}" y2="{margin_t + chart_h}" stroke="#9ca3af"/>',
    ]
    for i in range(5):
        y = margin_t + chart_h - chart_h * i // 4
        val = max_v * i // 4
        parts.append(f'<line x1="{margin_l}" y1="{y}" x2="{width - 24}" y2="{y}" stroke="#e5e7eb"/>')
        parts.append(
            f'<text x="{margin_l - 8}" y="{y + 5}" text-anchor="end" font-size="11" fill="#4b5563">'
            f"{escape(fmt_rub_short(val))}</text>"
        )
    x = margin_l + 8
    for label, infra, lic in items:
        total = infra + lic
        h_total = max(6, round(total / max_v * chart_h))
        h_lic = max(2, round(lic / total * h_total)) if total else 0
        h_infra = h_total - h_lic
        y_base = margin_t + chart_h
        parts.append(f'<rect x="{x}" y="{y_base - h_lic}" width="{bar_w}" height="{h_lic}" fill="#6366f1" rx="2"/>')
        parts.append(
            f'<rect x="{x}" y="{y_base - h_lic - h_infra}" width="{bar_w}" height="{h_infra}" fill="#86efac" rx="2"/>'
        )
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{y_base - h_lic - h_infra - 8}" text-anchor="middle" '
            f'font-size="10" font-weight="600">{escape(fmt_rub_short(total))}</text>'
        )
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{margin_t + chart_h + 20}" text-anchor="middle" font-size="10">'
            f"{escape(label)}</text>"
        )
        x += bar_w + gap
    parts.append(f'</svg><figcaption class="fig-cap">{escape(caption)}</figcaption></figure>')
    return "".join(parts)


def _tco_items_for_anchor(anchor: KorusAnchor) -> list[tuple[str, int, int]]:
    items = []
    for c in competitors_at_anchor(anchor):
        infra = c.infra_yearly or 0
        lic = c.license_yearly or 0
        short = (
            c.name.replace(" Messenger", "")
            .replace(" Corporate", "")
            .replace(" WorkSpace SaaS", " VK")
            .replace(" (облако)", "")
        )
        items.append((short, infra, lic))
    return items


def render_fig_profile_floors_svg() -> str:
    return """<figure class="fig fig-wide"><svg viewBox="0 0 1040 260" width="1040" height="260" xmlns="http://www.w3.org/2000/svg">
  <text x="520" y="28" text-anchor="middle" font-size="16" font-weight="bold">Профили Korus: пороги и якоря</text>
  <rect x="24" y="52" width="300" height="58" rx="8" fill="#fef3c7" stroke="#f59e0b"/>
  <text x="174" y="76" text-anchor="middle" font-size="13" font-weight="600">Пробник</text>
  <text x="174" y="96" text-anchor="middle" font-size="11" fill="#6b7280">до 10 тыс. рег. · вне матрицы TCO</text>
  <rect x="360" y="52" width="300" height="58" rx="8" fill="#dcfce7" stroke="#22c55e"/>
  <text x="510" y="76" text-anchor="middle" font-size="13" font-weight="600">Стандарт</text>
  <text x="510" y="96" text-anchor="middle" font-size="11" fill="#6b7280">порог 10 тыс. · S-10k · S-50k · S-100k</text>
  <rect x="696" y="52" width="320" height="58" rx="8" fill="#dbeafe" stroke="#3b82f6"/>
  <text x="856" y="76" text-anchor="middle" font-size="13" font-weight="600">Корпоративный</text>
  <text x="856" y="96" text-anchor="middle" font-size="11" fill="#6b7280">порог 100 тыс. · E-500k · E-1M</text>
  <line x1="360" y1="148" x2="660" y2="148" stroke="#22c55e" stroke-width="2"/>
  <circle cx="400" cy="148" r="6" fill="#22c55e"/><text x="400" y="172" text-anchor="middle" font-size="10">10 тыс. рег.</text>
  <circle cx="510" cy="148" r="6" fill="#22c55e"/><text x="510" y="172" text-anchor="middle" font-size="10">50 тыс. рег.</text>
  <circle cx="620" cy="148" r="6" fill="#22c55e"/><text x="620" y="172" text-anchor="middle" font-size="10">100 тыс. рег.</text>
  <line x1="696" y1="148" x2="1016" y2="148" stroke="#3b82f6" stroke-width="2"/>
  <circle cx="780" cy="148" r="6" fill="#3b82f6"/><text x="780" y="172" text-anchor="middle" font-size="10">500 тыс. рег.</text>
  <circle cx="920" cy="148" r="6" fill="#3b82f6"/><text x="920" y="172" text-anchor="middle" font-size="10">1 млн рег.</text>
  <text x="520" y="210" text-anchor="middle" font-size="11" fill="#6b7280">eXpress 100–1000 рег. пользов. — ниже порога «Стандарт» Korus (10 тыс.)</text>
  <text x="520" y="230" text-anchor="middle" font-size="11" fill="#6b7280">Media eXpress: ~0,3 vCPU × участник; ~10% пользователей в звонке</text>
</svg><figcaption class="fig-cap">Рис. 1. Промышленное сравнение только на якорях ≥10 тыс. (Стандарт) и ≥100 тыс. (Корпоративный).</figcaption></figure>"""


def render_fig_infra_by_anchor_svg() -> str:
    series = [(a.code, a.infra_yearly, "#86efac" if a.profile == "Standard" else "#6366f1") for a in KORUS_ANCHORS]
    return _bar_chart_svg(
        "Korus: инфраструктура, ₽/год по якорям",
        series,
        caption=f"Ось Y — ₽/год (OPEX). Ставки {PRICE_AS_OF}; не оферта.",
    )


def render_fig_express_infra_tiers_svg() -> str:
    series = []
    for t in EXPRESS_TIERS:
        if t.vcpu <= 0:
            continue
        series.append((_fmt_reg_users(t.ru, compact=True), t.infra_yearly_est, "#f59e0b"))
    return _bar_chart_svg(
        "eXpress: инфра ₽/год (публичные таблицы, 100–1000 рег. пользов.)",
        series,
        caption="Ось Y — ₽/год. Ось X — число рег. пользователей. Ниже 10 тыс. — не промышленный якорь Korus.",
    )


def render_fig_ram_compare_svg() -> str:
    """Grouped RAM at comparable public tiers."""
    width, height = _CHART_WIDE, 360
    groups = [
        (_fmt_reg_users(100, compact=True), [("eXpress", 17, "#f59e0b")]),
        (_fmt_reg_users(1_000, compact=True), [("eXpress", 62, "#f59e0b")]),
        (
            _fmt_reg_users(10_000, compact=True),
            [("Korus S-10k", 64, "#86efac"), ("eXpress*", 120, "#fcd34d")],
        ),
        (
            _fmt_reg_users(100_000, compact=True),
            [("Korus S-100k", 140, "#86efac"), ("eXpress*", 200, "#fcd34d")],
        ),
    ]
    max_ram = 200
    parts = [
        f'<figure class="fig fig-wide"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="28" text-anchor="middle" font-size="16" font-weight="bold">Суммарная RAM (ГБ) по масштабу</text>',
        f'<text x="22" y="180" text-anchor="middle" font-size="11" fill="#6b7280" transform="rotate(-90 22 180)">ГБ RAM</text>',
        f'<line x1="72" y1="280" x2="{width - 40}" y2="280" stroke="#9ca3af"/>',
    ]
    gx = 100
    step = (width - 160) // max(len(groups), 1)
    for label, bars in groups:
        parts.append(f'<text x="{gx + 48}" y="310" text-anchor="middle" font-size="11">{escape(label)}</text>')
        bx = gx
        for _name, ram, color in bars:
            h = max(10, round(ram / max_ram * 200))
            parts.append(f'<rect x="{bx}" y="{280 - h}" width="44" height="{h}" fill="{color}" rx="2"/>')
            parts.append(f'<text x="{bx + 22}" y="{280 - h - 6}" text-anchor="middle" font-size="10">{ram} ГБ</text>')
            bx += 52
        gx += step
    parts.append(
        '</svg><figcaption class="fig-cap">Ось Y — гигабайты RAM. * eXpress при 10/100 тыс. рег. — оценка модели; '
        "официальный sizing — инд. проект.</figcaption></figure>"
    )
    return "".join(parts)


def render_fig_tco_s10k_svg() -> str:
    return _stacked_tco_svg(
        f"TCO при {_fmt_reg_users(10_000)} (₽/год: инфра + лицензия)",
        _tco_items_for_anchor(KORUS_ANCHORS[0]),
        caption="Korus лицензия — строка КП. eXpress: 30 млн ₽/год лицензий.",
    )


def render_fig_tco_s100k_svg() -> str:
    return _stacked_tco_svg(
        f"TCO при {_fmt_reg_users(100_000)} (₽/год: инфра + лицензия)",
        _tco_items_for_anchor(KORUS_ANCHORS[2]),
        caption="Инфра eXpress — оценка 25 млн ₽/год.",
    )


def render_fig_tco_enterprise_svg() -> str:
    items = []
    for anchor in (KORUS_ANCHORS[3], KORUS_ANCHORS[4]):
        for c in competitors_at_anchor(anchor):
            if not c.name.startswith(("Korus", "eXpress")):
                continue
            short = f"{c.name[:5]} {anchor.code}"
            items.append((short, c.infra_yearly or 0, c.license_yearly or 0))
    return _stacked_tco_svg(
        "TCO корпоративный: E-500k и E-1M (₽/год, Korus и eXpress)",
        items,
        caption="Ось Y — ₽/год. Инфра eXpress @500k/1M — оценка; лицензия по публичному прайсу.",
    )


def render_fig_license_share_svg() -> str:
    """License as % of TCO @10k and @100k for eXpress."""
    width, height = 880, 280
    rows = [
        (_fmt_reg_users(10_000, compact=True), express_license_yearly(10_000), express_infra_yearly(10_000) or 0),
        (_fmt_reg_users(100_000, compact=True), express_license_yearly(100_000), express_infra_yearly(100_000) or 0),
    ]
    bar_max_w = 620
    parts = [
        f'<figure class="fig fig-wide"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="28" text-anchor="middle" font-size="16" font-weight="bold">eXpress: доля лицензии в TCO (₽/год)</text>',
    ]
    y = 72
    for label, lic, infra in rows:
        total = lic + infra
        pct = round(100 * lic / total) if total else 0
        w_lic = round(bar_max_w * lic / total) if total else 0
        w_inf = bar_max_w - w_lic
        parts.append(f'<text x="48" y="{y + 18}" font-size="12">{escape(label)}</text>')
        parts.append(f'<rect x="160" y="{y}" width="{w_lic}" height="28" fill="#6366f1"/>')
        parts.append(f'<rect x="{160 + w_lic}" y="{y}" width="{w_inf}" height="28" fill="#86efac"/>')
        parts.append(f'<text x="{820}" y="{y + 20}" font-size="12">{pct}% лиц.</text>')
        y += 52
    parts.append(
        '</svg><figcaption class="fig-cap">Фиолетовый — лицензия ₽/год; зелёный — инфра ₽/год (оценка).</figcaption></figure>'
    )
    return "".join(parts)


def render_fig_license_per_user_svg() -> str:
    ru = 10_000
    series = [
        ("eXpress лиц.", EXPRESS_LICENSE_RUB_PER_USER_YEAR // 12, "#6366f1"),
        ("Пачка", PACHKA_CORP_RUB_PER_USER_MONTH_YEAR, "#f59e0b"),
        ("VK облако", VK_SAAS_RUB_PER_USER_MONTH_YEAR, "#93c5fd"),
        ("Korus инфра", round(KORUS_ANCHORS[0].infra_per_user_month), "#86efac"),
    ]
    width, height = _CHART_WIDE, 340
    max_v = max(s[1] for s in series) or 1
    margin_l, margin_t, chart_h = 88, 48, 200
    parts = [
        f'<figure class="fig fig-wide"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="28" text-anchor="middle" font-size="16" font-weight="bold">'
        f"₽/рег. пользов./мес при {_fmt_reg_users(ru)}</text>".replace(",", " "),
        f'<line x1="{margin_l}" y1="{margin_t + chart_h}" x2="500" y2="{margin_t + chart_h}" stroke="#9ca3af"/>',
    ]
    x = margin_l + 16
    bar_w = min(120, (width - margin_l - 80) // len(series) - 24)
    gap = 28
    for label, value, color in series:
        h = max(10, round(value / max_v * chart_h))
        y = margin_t + chart_h - h
        parts.append(f'<rect x="{x}" y="{y}" width="{bar_w}" height="{h}" fill="{color}" rx="3"/>')
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{y - 8}" text-anchor="middle" font-size="11" font-weight="600">'
            f"{value} ₽/мес</text>"
        )
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{margin_t + chart_h + 22}" text-anchor="middle" font-size="11">'
            f"{escape(label)}</text>"
        )
        x += bar_w + gap
    parts.append(
        '</svg><figcaption class="fig-cap">eXpress/Пачка/VK — подписка; Korus — только эксплуатационные расходы (OPEX) инфраструктуры.</figcaption></figure>'
    )
    return "".join(parts)


def render_korus_anchor_table_html() -> str:
    rows = []
    for a in KORUS_ANCHORS:
        rows.append(
            f"<tr><td><b>{escape(a.code)}</b></td><td>{escape(_ru_profile(a.profile))}</td>"
            f"<td>{_fmt_reg_users(a.ru)}</td>"
            f"<td>{a.peak_online:,}".replace(",", " ")
            + f"</td><td>~{a.peak_msg_s}</td><td>{escape(a.ram_gb)}</td>"
            f'<td class="money">{fmt_rub(a.infra_yearly)}</td>'
            f"<td>~{_fmt_per_user_month(a.infra_per_user_month)}</td>"
            f'<td class="small">{escape(a.infra_note)}</td></tr>'
        )
    return f"""
<table>
  <tr><th>Якорь</th><th>Профиль</th><th>Рег. пользов.</th><th>Пик онлайн</th><th>Пик сообщ/с</th>
      <th>RAM</th><th>Инфра ₽/год</th><th>₽/рег. пользов./мес</th><th>Примечание</th></tr>
  {"".join(rows)}
</table>"""


def render_express_full_table_html() -> str:
    rows = []
    for t in EXPRESS_TIERS:
        if t.vcpu > 0:
            hw = f"{t.vcpu} / {t.ram_gb} ГБ / {t.ssd_tb:.2f} ТБ"
            infra = t.infra_yearly_est
        else:
            hw = "инд. проект"
            infra = express_infra_yearly(t.ru) or 0
        rows.append(
            f"<tr><td>{_fmt_reg_users(t.ru)}</td><td>{escape(hw)}</td>"
            f'<td class="money">{fmt_rub(infra)}</td>'
            f'<td class="money">{fmt_rub(t.license_yearly)}</td>'
            f'<td class="money"><b>{fmt_rub(infra + t.license_yearly)}</b></td>'
            f"<td>{escape(t.korus_anchor)}</td>"
            f'<td class="small">{escape(t.source)}</td></tr>'
        )
    comp_rows = []
    for t in EXPRESS_TIERS:
        if not t.components:
            continue
        for name, cpu, ram, disk in t.components:
            comp_rows.append(
                f"<tr><td>{_fmt_reg_users(t.ru)}</td><td>{escape(name)}</td><td>{cpu}</td><td>{ram} ГБ</td>"
                f"<td>{disk:.3f} TB</td></tr>"
            )
    return f"""<details class="express-details">
<summary><b>Таблицы eXpress</b> (sizing, роли, тарифы)</summary>
<h4>Сводка по масштабам eXpress</h4>
<table>
  <tr><th>Рег. пользов.</th><th>vCPU / RAM / SSD</th><th>Инфра ₽/год</th><th>Лицензия ₽/год</th><th>TCO ₽/год</th><th>Якорь Korus</th><th>Источник</th></tr>
  {"".join(rows)}
</table>
<h4>Детализация ролей eXpress (100–1000 рег. пользов.)</h4>
<table>
  <tr><th>Рег. пользов.</th><th>Роль</th><th>vCPU</th><th>RAM</th><th>SSD</th></tr>
  {"".join(comp_rows)}
</table>
<h4>Тарифы eXpress (публично)</h4>
<table>
  <tr><th>Тариф</th><th>Лицензия в контуре</th><th>Примечание</th></tr>
  <tr><td>Corporate</td><td class="money">3 000 ₽/рег. пользов./год</td><td>Без лимита CTS-серверов</td></tr>
  <tr><td>Corporate + SmartApps</td><td class="money">5 000 ₽/рег. пользов./год</td><td>Супераппы, интеграции</td></tr>
  <tr><td>Lite (облако)</td><td class="money">200 ₽/рег. пользов./мес</td><td>5–1000 рег. пользов., Вымпелком</td></tr>
  <tr><td>Продление SA Corporate</td><td class="money">1 500 ₽/рег. пользов./год</td><td>После бессрочной лицензии</td></tr>
  <tr><td>Стек</td><td colspan="2">Docker, PostgreSQL 14+, Kafka, etcd 3.5+, Redis 7.2+, CPU ≥3,6 GHz, сеть 1 Gbps</td></tr>
</table>
<p class="small">Диск eXpress: расчёт 1 ГБ журналов + 4 ГБ/рег. пользов./год × 4 года (может расти при активном использовании).</p>
</details>"""


def render_pricing_reference_html() -> str:
    return f"""
<table>
  <tr><th>Решение</th><th>Модель</th><th>₽/рег. пользов./мес</th><th>10 тыс. рег., ₽/год</th><th>100 тыс. рег., ₽/год</th></tr>
  <tr><td>Korus инфра</td><td>эксплуатация (OPEX)</td><td class="money">~{KORUS_ANCHORS[0].infra_per_user_month:.0f} / ~{KORUS_ANCHORS[2].infra_per_user_month:.1f}</td>
      <td class="money">{fmt_rub(KORUS_ANCHORS[0].infra_yearly)}</td>
      <td class="money">{fmt_rub(KORUS_ANCHORS[2].infra_yearly)}</td></tr>
  <tr><td>eXpress Corporate</td><td>лицензия + инфра</td><td class="money">250 + инфра</td>
      <td class="money">{fmt_rub(express_license_yearly(10_000) + (express_infra_yearly(10_000) or 0))}</td>
      <td class="money">{fmt_rub(express_license_yearly(100_000) + (express_infra_yearly(100_000) or 0))}</td></tr>
  <tr><td>Пачка Корпорация</td><td>облако</td><td class="money">399</td>
      <td class="money">{fmt_rub(pachka_yearly(10_000))}</td>
      <td class="money">{fmt_rub(pachka_yearly(100_000))}</td></tr>
  <tr><td>VK WorkSpace (облако)</td><td>облако</td><td class="money">207</td>
      <td class="money">{fmt_rub(vk_saas_yearly(10_000))}</td>
      <td class="money">{fmt_rub(vk_saas_yearly(100_000))}</td></tr>
  <tr><td>Пачка Компания</td><td>облако</td><td class="money">159</td>
      <td class="money">{fmt_rub(pachka_yearly(10_000, corp=False))}</td>
      <td class="money">{fmt_rub(pachka_yearly(100_000, corp=False))}</td></tr>
</table>
<h4>Уровни B / C — публичные прайсы (где есть)</h4>
<table>
  <tr><th>Решение</th><th>Уровень</th><th>₽/рег. пользов./мес</th><th>10 тыс. рег., ₽/год (лицензия)</th><th>Примечание</th></tr>
  <tr><td>Loop облако</td><td>B</td><td class="money">119–199</td>
      <td class="money">{fmt_rub(LOOP_PRO_RUB_MONTH * 12 * 10_000)}</td>
      <td class="small">loop.ru/pricing; в контуре «Корпоративный» — КП</td></tr>
  <tr><td>Compass облако</td><td>C</td><td class="money">390</td>
      <td class="money">{fmt_rub(390 * 12 * 10_000)}</td>
      <td class="small">getcompass.ru</td></tr>
  <tr><td>Compass в контуре</td><td>C</td><td class="money">{COMPASS_ONPREM_RUB_MONTH}</td>
      <td class="money">{fmt_rub(COMPASS_ONPREM_RUB_MONTH * 12 * 10_000)}</td>
      <td class="small">без infra; акции reseller возможны</td></tr>
  <tr><td>МТС Линк Чаты</td><td>C</td><td>по КП</td><td>—</td>
      <td class="small">mts-link.ru</td></tr>
  <tr><td>TrueConf Server</td><td>C</td><td>—</td><td>от 23 000 ₽/год</td>
      <td class="small">PRO/online; не ₽/reg</td></tr>
  <tr><td>Rocket.Chat / Mattermost EE</td><td>B</td><td>по КП</td><td>—</td>
      <td class="small">цена EE не публикуется</td></tr>
</table>
<p class="small">Ставки инфраструктуры уровня A: {PRICE_AS_OF}, {escape(_RATES['server_16'].source)}.</p>
"""


def render_tier_overview_html() -> str:
    rows = []
    for pid, label, tier, deploy in PRODUCT_COLUMNS:
        tco = "полный TCO @якорях" if tier == "A" else ("оценочный TCO @10k" if tier == "B" else "прайс/КП")
        row_cls = ' class="row-korus"' if pid == "korus" else ""
        rows.append(
            f"<tr{row_cls}><td><span class=\"tier-{tier.lower()}\">{escape(_tier_ru(tier))}</span></td>"
            f"<td><b>{escape(label)}</b></td><td>{escape(_ru_deployment(deploy))}</td>"
            f"<td>{escape(tco)}</td></tr>"
        )
    return f"""
<table>
  <tr><th>Уровень</th><th>Решение</th><th>Развёртывание</th><th>Участие в TCO</th></tr>
  {"".join(rows)}
</table>
<p class="small comment">{escape(TIER_LABELS["A"])}. {escape(TIER_LABELS["B"])}. {escape(TIER_LABELS["C"])}.</p>"""


def render_hero_html() -> str:
    s10 = KORUS_ANCHORS[0]
    s100 = KORUS_ANCHORS[2]
    ex_lic_10k = express_license_yearly(10_000)
    ex_infra_10k = express_infra_yearly(10_000) or 0
    ex_tco_10k = ex_lic_10k + ex_infra_10k
    lic_share = round(100 * ex_lic_10k / ex_tco_10k) if ex_tco_10k else 0
    return f"""
<div class="hero">
  <p class="hero-lead">Корпоративный мессенджер в контуре заказчика: <b>прозрачная экономика</b>,
  <b>комплаенс из коробки</b> и сравнение с рынком РФ по единой методике якорей.</p>
  <div class="hero-stats">
    <div class="stat-card stat-korus">
      <div class="stat-value">{_fmt_per_user_month(s100.infra_per_user_month)}</div>
      <div class="stat-label">OPEX инфра Korus при 100&nbsp;тыс. рег.<br/><span class="small">без лицензии per-user</span></div>
    </div>
    <div class="stat-card">
      <div class="stat-value">{fmt_rub_short(ex_lic_10k)}</div>
      <div class="stat-label">лицензия eXpress / год @10&nbsp;тыс. рег.<br/><span class="small">~{lic_share}% TCO — доминирует</span></div>
    </div>
    <div class="stat-card">
      <div class="stat-value">{_fmt_per_user_month(s10.infra_per_user_month)}</div>
      <div class="stat-label">OPEX инфра Korus @10&nbsp;тыс. рег.<br/><span class="small">полный «Стандарт», не пробник</span></div>
    </div>
  </div>
  <p class="hero-note small">Цифры — ориентиры для переговоров и КП; детали и дисклеймеры — §8. Сравнение честное: сильные стороны конкурентов не скрываем.</p>
</div>"""


def render_audience_nav_html() -> str:
    return """
<div class="audience-nav">
  <a class="audience-card" href="#summary"><span class="audience-role">Закупка / CFO</span>
    <span class="audience-hint">TCO, якоря 10k–1M, доля лицензии</span></a>
  <a class="audience-card" href="#s5"><span class="audience-role">ИБ / комплаенс</span>
    <span class="audience-hint">экспорт, legal hold, audit, E2EE</span></a>
  <a class="audience-card" href="#decision"><span class="audience-role">Продажи / presales</span>
    <span class="audience-hint">сценарии, дерево выбора, ± по продуктам</span></a>
  <a class="audience-card" href="#s2nt"><span class="audience-role">CTO / архитектор</span>
    <span class="audience-hint">sizing, НТ, infra по якорям</span></a>
</div>"""


def render_korus_positioning_html() -> str:
    s100 = KORUS_ANCHORS[2]
    return f"""
<div class="positioning" id="positioning">
  <div class="req">Позиционирование Korus Messenger</div>
  <div class="grid-2">
    <div>
      <p class="comment"><b>Korus — сильный выбор, когда:</b></p>
      <ul class="comment">
        <li>Нужен <b>изолированный контур</b> и предсказуемый OPEX без «налога» per-user лицензии.</li>
        <li>Критичны <b>экспорт, legal hold, dual-TTL, audit</b> — не плагины «на доработку».</li>
        <li>Масштаб от <b>10&nbsp;000 рег. пользов.</b> (Стандарт) до федерального контура (Корпоративный).</li>
        <li>Важна <b>мультитenant org-shard</b> и путь от пилота к production без смешения матриц.</li>
      </ul>
    </div>
    <div>
      <p class="comment"><b>Честно: рассмотрите альтернативу, если:</b></p>
      <ul class="comment">
        <li>Нужен <b>готовый суперапп</b> (почта, SmartApps, ВКС 500+) — eXpress / VK Superapp.</li>
        <li>Допустимо только <b>облако</b> и быстрый старт — Пачка, VK WorkSpace.</li>
        <li>Обязателен <b>сертификат ФСТЭК уже сегодня</b> — eXpress №4997 (у Korus — в процессе).</li>
        <li>Фокус <b>UC/ВКС</b>, а чат вторичен — TrueConf, МТС Линк.</li>
      </ul>
    </div>
  </div>
  <p class="positioning-tagline comment">Ключевое сообщение для рынка:
  <b>«Комплаенс и TCO в контуре — без сюрприза лицензии на каждого пользователя»</b>
  (infra ~{_fmt_per_user_month(s100.infra_per_user_month)} при 100&nbsp;тыс. рег. vs доминирующая лицензия eXpress).</p>
</div>"""


def render_cta_footer_html() -> str:
    return """
<div class="cta-box">
  <div class="req">Следующий шаг с заказчиком</div>
  <ol class="comment">
    <li><b>Квалификация</b> — контур (в контуре / облако), масштаб рег. пользов., требования ИБ (§ «Дерево выбора»).</li>
    <li><b>Пилот</b> — функциональная оценка на пробнике (вне TCO-матрицы); production — с якоря S-10k.</li>
    <li><b>КП</b> — infra по якорю + лицензия Korus; для конкурента — те же якоря (§3, §6).</li>
    <li><b>ИБ-воркшоп</b> — export gate, ретенция, roadmap E2EE/ФСТЭК.</li>
  </ol>
</div>"""


def render_executive_summary_html() -> str:
    return """
<div class="note" id="summary">
  <div class="req">Сценарии выбора — кому что предлагать</div>
  <p class="comment">Материал для КП, защиты тендера и переговоров. Все суммы — по единой методике якорей (от 10&nbsp;000 рег. пользов.).</p>
  <div class="grid-2">
    <div class="scenario scenario-korus">
      <h4>Контур заказчика + комплаенс</h4>
      <p class="comment">Экспорт, legal hold, audit, ретенция — «из коробки», а не через интеграторов.</p>
      <p class="scenario-rec"><b>Рекомендация:</b> <span class="rec-korus">Korus Messenger</span> — основной пitch;
      <b>eXpress</b> — если нужен ФСТЭК/суперапп и бюджет на лицензию.</p>
    </div>
    <div class="scenario">
      <h4>Облако, быстрый старт</h4>
      <p class="comment">Нет своего ЦОД, нужен прозрачный ₽/пользов./мес и SLA вендора.</p>
      <p class="scenario-rec"><b>Рекомендация:</b> <b>Пачка</b> (фокус на чат) или <b>VK WorkSpace</b> (workspace bundle).</p>
    </div>
    <div class="scenario">
      <h4>Миграция с Jabber / Sametime / Lync</h4>
      <p class="comment">Legacy дешевле по железу, но без современного UX, Solr и compliance-ядра.</p>
      <p class="scenario-rec"><b>Рекомендация:</b> <span class="rec-korus">Korus</span> или <b>eXpress</b>; облако — если политика допускает.</p>
    </div>
    <div class="scenario">
      <h4>Российский рынок, прайс на сайте</h4>
      <p class="comment">Compass, МТС Линк, TrueConf — альтернативы для shortlist и ценового давления.</p>
      <p class="scenario-rec"><b>Рекомендация:</b> сравнить с Korus по §5 (функции) и §3.1 (уровень C).</p>
    </div>
  </div>
</div>"""


def render_decision_tree_html() -> str:
    return """
<div class="cost-box" id="decision">
  <div class="req">Быстрый выбор решения</div>
  <div class="decision-cards">
    <div class="decision-card">
      <div class="decision-q">Нужен контур заказчика?</div>
      <div class="decision-a"><b>Да</b> → нужен export / legal hold «из коробки»?
        <ul>
          <li><b>Да</b> → <span class="rec-korus">Korus</span> · eXpress (ФСТЭК)</li>
          <li><b>Нет</b> → Loop · Rocket.Chat · Mattermost · VK Superapp*</li>
        </ul>
      </div>
    </div>
    <div class="decision-card">
      <div class="decision-q">Облако допустимо?</div>
      <div class="decision-a"><b>Да</b> → только чат?
        <ul>
          <li><b>Да</b> → Пачка · VK WorkSpace</li>
          <li><b>Нет</b> (UC/ВКС) → TrueConf · МТС Линк · Compass</li>
        </ul>
      </div>
    </div>
    <div class="decision-card decision-card-muted">
      <div class="decision-q">Миграция с Jabber / XMPP?</div>
      <div class="decision-a">§5.3 — справочно; целевой путь: <span class="rec-korus">Korus</span> или eXpress.</div>
    </div>
  </div>
  <p class="small comment">* VK Superapp — почта, календарь, диск; не «только мессенджер».</p>
</div>"""


def render_extended_feature_matrix_html() -> str:
    header = "<tr><th>Критерий</th>" + "".join(
        f"<th>{escape(label)}<br/><span class=\"small\">{escape(_tier_ru(tier))}</span></th>"
        for _pid, label, tier, _d in PRODUCT_COLUMNS
    ) + "</tr>"
    body_rows = []
    for cid, title in COMPARISON_CRITERIA:
        cells = "".join(
            f"<td>{escape(PRODUCT_FEATURES.get(pid, {}).get(cid, '—'))}</td>"
            for pid, _l, _t, _d in PRODUCT_COLUMNS
        )
        body_rows.append(f"<tr><td>{escape(title)}</td>{cells}</tr>")
    return f"""
<div class="matrix-scroll">
<table class="matrix-wide">
  {header}
  {"".join(body_rows)}
</table>
</div>
<p class="small comment">Уровни A/B/C — классы сравнения. ◐ — частично / плагины / зависит от тарифа.</p>"""


def render_feature_heatmap_svg() -> str:
    n_prod = len(PRODUCT_COLUMNS)
    n_crit = len(COMPARISON_CRITERIA)
    cell_w, cell_h = 58, 26
    label_w = 248
    header_h = 76
    width = label_w + n_prod * cell_w + 24
    height = header_h + n_crit * cell_h + 56
    parts = [
        f'<div class="fig-scroll"><figure class="fig fig-wide"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
        f'xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="24" text-anchor="middle" font-size="16" font-weight="bold">'
        "Тепловая карта: возможности по продуктам (уровни A / B / C)</text>",
    ]
    for i, (_pid, label, tier, _d) in enumerate(PRODUCT_COLUMNS):
        x = label_w + i * cell_w + cell_w // 2
        short = label if len(label) <= 12 else label[:11] + "…"
        parts.append(
            f'<text x="{x}" y="{header_h - 28}" text-anchor="middle" font-size="10" '
            f'transform="rotate(-40 {x} {header_h - 28})">{escape(short)}</text>'
        )
        parts.append(
            f'<text x="{x}" y="{header_h - 6}" text-anchor="middle" font-size="9" fill="#6366f1">'
            f"{escape(_tier_ru(tier))}</text>"
        )
    for j, (_cid, title) in enumerate(COMPARISON_CRITERIA):
        y = header_h + j * cell_h
        short_title = title if len(title) <= 30 else title[:28] + "…"
        parts.append(
            f'<text x="{label_w - 6}" y="{y + 17}" text-anchor="end" font-size="10">{escape(short_title)}</text>'
        )
        for i, (pid, _l, _t, _d) in enumerate(PRODUCT_COLUMNS):
            cell = PRODUCT_FEATURES.get(pid, {}).get(_cid, "—")
            color = heatmap_color(cell)
            x = label_w + i * cell_w
            parts.append(f'<rect x="{x + 1}" y="{y + 2}" width="{cell_w - 2}" height="{cell_h - 4}" '
                         f'fill="{color}" stroke="#d1d5db" stroke-width="0.5"/>')
    legend_y = height - 36
    parts.append(
        f'<rect x="20" y="{legend_y}" width="14" height="14" fill="#86efac"/>'
        f'<text x="40" y="{legend_y + 11}" font-size="11">сильно</text>'
        f'<rect x="110" y="{legend_y}" width="14" height="14" fill="#fcd34d"/>'
        f'<text x="130" y="{legend_y + 11}" font-size="11">частично</text>'
        f'<rect x="220" y="{legend_y}" width="14" height="14" fill="#fca5a5"/>'
        f'<text x="240" y="{legend_y + 11}" font-size="11">нет</text>'
        f'<rect x="300" y="{legend_y}" width="14" height="14" fill="#e0e7ff"/>'
        f'<text x="320" y="{legend_y + 11}" font-size="11">КП / уточнять</text>'
    )
    parts.append(
        '</svg><figcaption class="fig-cap">18 критериев × 11 продуктов. Детали — таблица §5.1.</figcaption></figure></div>'
    )
    return "".join(parts)


def render_fig_onprem_radar_svg() -> str:
    """Radar chart: on-prem @10k (экспертные оценки)."""
    import math

    n = len(RADAR_AXES)
    width, height = 720, 640
    cx, cy, r = width // 2, 300, 190
    parts = [
        f'<figure class="fig fig-wide"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
        f'xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="28" text-anchor="middle" font-size="16" font-weight="bold">'
        f"В контуре @10 тыс. рег.: лепестковая диаграмма (оценка 0–5)</text>",
    ]
    for level in range(1, RADAR_MAX + 1):
        rr = r * level / RADAR_MAX
        pts = []
        for i in range(n):
            ang = math.radians(-90 + i * 360 / n)
            pts.append(f"{cx + rr * math.cos(ang):.1f},{cy + rr * math.sin(ang):.1f}")
        parts.append(
            f'<polygon points="{" ".join(pts)}" fill="none" stroke="#e5e7eb" stroke-width="1"/>'
        )
    for i, (_aid, label) in enumerate(RADAR_AXES):
        ang = math.radians(-90 + i * 360 / n)
        x2 = cx + r * math.cos(ang)
        y2 = cy + r * math.sin(ang)
        parts.append(f'<line x1="{cx}" y1="{cy}" x2="{x2:.1f}" y2="{y2:.1f}" stroke="#d1d5db"/>')
        lx = cx + (r + 36) * math.cos(ang)
        ly = cy + (r + 36) * math.sin(ang)
        anchor = "middle"
        if math.cos(ang) > 0.3:
            anchor = "start"
        elif math.cos(ang) < -0.3:
            anchor = "end"
        parts.append(
            f'<text x="{lx:.1f}" y="{ly:.1f}" text-anchor="{anchor}" font-size="11">{escape(label)}</text>'
        )
    for _pid, name, color, scores in RADAR_ONPREM:
        pts = []
        for i, sc in enumerate(scores):
            ang = math.radians(-90 + i * 360 / n)
            rr = r * sc / RADAR_MAX
            pts.append(f"{cx + rr * math.cos(ang):.1f},{cy + rr * math.sin(ang):.1f}")
        parts.append(
            f'<polygon points="{" ".join(pts)}" fill="{color}" fill-opacity="0.15" '
            f'stroke="{color}" stroke-width="2.5"/>'
        )
    ly1, ly2 = height - 72, height - 44
    lx = 32
    col_w = 220
    for idx, (_pid, name, color, _scores) in enumerate(RADAR_ONPREM):
        row = idx // 3
        col = idx % 3
        x = lx + col * col_w
        y = ly1 if row == 0 else ly2
        parts.append(f'<rect x="{x}" y="{y - 12}" width="12" height="12" fill="{color}"/>')
        parts.append(f'<text x="{x + 18}" y="{y}" font-size="11">{escape(name)}</text>')
    parts.append(
        '</svg><figcaption class="fig-cap">Шкала 0–5: комплаенс, E2EE, экономика (прозрачность TCO), '
        "функции, sizing, реестр/ФСТЭК. Субъективная модель для переговоров, не рейтинг вендора.</figcaption></figure>"
    )
    return "".join(parts)


def render_tier_b_tco_html() -> str:
    rows = []
    for r in tier_b_tco_rows():
        lic = fmt_rub(r.license_yearly_10k) if r.license_yearly_10k else "по КП"
        infra = fmt_rub(r.infra_yearly_10k) if r.infra_yearly_10k else "—"
        total = ""
        if r.license_yearly_10k and r.infra_yearly_10k:
            total = fmt_rub(r.license_yearly_10k + r.infra_yearly_10k)
        rows.append(
            f"<tr><td><b>{escape(r.name)}</b></td>"
            f'<td class="money">{lic}</td><td class="money">{infra}</td>'
            f'<td class="money">{total or "—"}</td>'
            f'<td class="small">{escape(r.license_note)}</td>'
            f'<td class="small">{escape(r.infra_note)}</td></tr>'
        )
    return f"""
<div class="warn">
  <div class="req">Уровень B — оценочный TCO при {_fmt_reg_users(10_000)}</div>
  <div class="comment">Не оферта вендоров. Infra — модель по ставкам Korus ({PRICE_AS_OF}).</div>
</div>
<table>
  <tr><th>Решение</th><th>Лицензия ₽/год</th><th>Инфра ₽/год (оценка)</th><th>Итого (оценка)</th>
      <th>Лицензия</th><th>Infra</th></tr>
  {"".join(rows)}
</table>"""


def render_tier_c_market_html() -> str:
    rows = []
    for r in tier_c_market_rows():
        rows.append(
            f"<tr><td><b>{escape(r.name)}</b></td><td>{escape(r.deployment)}</td>"
            f"<td>{escape(r.pricing_public)}</td><td class=\"small\">{escape(r.tco_10k_note)}</td>"
            f"<td>{escape(r.registry)}</td><td class=\"small\">{escape(r.source)}</td></tr>"
        )
    return f"""
<h4>Уровень C — российский рынок (МТС Линк, Compass, TrueConf)</h4>
<table>
  <tr><th>Решение</th><th>Развёртывание</th><th>Публичный прайс</th><th>TCO @10k</th><th>Реестр</th><th>Источник</th></tr>
  {"".join(rows)}
</table>
<p class="small comment">Dialog (МТС) эволюционировал в линейку МТС Линк Чаты — уточнять актуальный бренд у вендора.</p>"""


def render_feature_matrix_html() -> str:
    return render_extended_feature_matrix_html()


def render_tier_b_pros_cons_html() -> str:
    names = ("Loop", "Rocket.Chat", "Mattermost EE", "VK Superapp on-prem")
    parts = ['<h4>Уровень B — альтернативы в контуре</h4>']
    for name in names:
        pros, cons = PROS_CONS_BY_PRODUCT[name]
        pl = "".join(f"<li>{escape(p)}</li>" for p in pros)
        cl = "".join(f"<li>{escape(c)}</li>" for c in cons)
        parts.append(
            f'<div class="case"><h4>{escape(name)}</h4>'
            f"<p><b>Плюсы:</b></p><ul>{pl}</ul><p><b>Минусы:</b></p><ul>{cl}</ul></div>"
        )
    return "".join(parts)


def render_tier_c_pros_cons_html() -> str:
    names = ("МТС Линк Чаты", "Compass", "TrueConf Server")
    parts = ['<h4>Уровень C — российский рынок</h4>']
    for name in names:
        pros, cons = PROS_CONS_BY_PRODUCT[name]
        pl = "".join(f"<li>{escape(p)}</li>" for p in pros)
        cl = "".join(f"<li>{escape(c)}</li>" for c in cons)
        parts.append(
            f'<div class="case"><h4>{escape(name)}</h4>'
            f"<p><b>Плюсы:</b></p><ul>{pl}</ul><p><b>Минусы:</b></p><ul>{cl}</ul></div>"
        )
    return "".join(parts)


def render_reference_solutions_html() -> str:
    rows = []
    for name, small, large, note in REFERENCE_SOLUTIONS:
        rows.append(
            f"<tr><td><b>{escape(name)}</b></td><td>{escape(small)}</td>"
            f"<td>{escape(large)}</td><td class=\"small\">{escape(note)}</td></tr>"
        )
    return f"""
<table>
  <tr><th>Решение</th><th>Малый масштаб</th><th>Крупный масштаб</th><th>Примечание</th></tr>
  {"".join(rows)}
</table>"""


def render_legacy_solutions_html() -> str:
    rows = []
    for s in LEGACY_SOLUTIONS:
        rows.append(
            f"<tr><td><b>{escape(s.name)}</b></td><td>{escape(s.era)}</td>"
            f"<td>{escape(s.protocol)}</td><td>{escape(s.license_model)}</td>"
            f"<td>{escape(s.small_scale)}</td><td>{escape(s.large_scale)}</td>"
            f'<td class="small">{escape(s.status)}</td></tr>'
        )
    return f"""
<table>
  <tr><th>Платформа</th><th>Эпоха</th><th>Протокол</th><th>Лицензия</th>
      <th>Малый масштаб</th><th>Крупный масштаб</th><th>Статус</th></tr>
  {"".join(rows)}
</table>
<p class="small comment">В промышленной TCO-матрице устаревшие платформы не участвуют — справочное и миграционное сравнение.</p>"""


def render_legacy_infra_table_html() -> str:
    rows = []
    for ru, label, ha_y, single_y in LEGACY_INFRA_ANCHORS:
        korus_y = next((a.infra_yearly for a in KORUS_ANCHORS if a.ru == ru), None)
        korus_s = fmt_rub(korus_y) if korus_y else "—"
        delta_ha = ""
        if korus_y and ha_y:
            pct = round(100 * (korus_y - ha_y) / korus_y)
            delta_ha = f" ({pct:+d}% к Korus)" if pct else ""
        rows.append(
            f"<tr><td>{_fmt_reg_users(ru)}</td><td>{escape(label)}</td>"
            f'<td class="money">{fmt_rub(ha_y)}/год</td>'
            f'<td class="money">{fmt_rub(single_y)}/год</td>'
            f'<td class="money">{korus_s}</td>'
            f'<td class="small">{delta_ha}</td></tr>'
        )
    return f"""
<h4>Только инфра: XMPP-кластер и Korus (те же ставки {PRICE_AS_OF})</h4>
<table>
  <tr><th>Рег. пользов.</th><th>Сценарий</th><th>XMPP HA ₽/год</th><th>XMPP 1 узел ₽/год</th>
      <th>Korus ₽/год</th><th>Δ HA</th></tr>
  {"".join(rows)}
</table>
<p class="small comment">Устаревший стек дешевле по инфраструктуре, но без Solr, export gate, workers, Keycloak-tier ops.
  Лицензия OSS = 0; скрытые затраты — интеграция, поддержка, миграция, compliance своими силами.</p>"""


def render_legacy_feature_matrix_html() -> str:
    rows = []
    for feat, k, x, s, m in LEGACY_FEATURE_ROWS:
        rows.append(
            f"<tr><td>{escape(feat)}</td><td>{escape(k)}</td><td>{escape(x)}</td>"
            f"<td>{escape(s)}</td><td>{escape(m)}</td></tr>"
        )
    return f"""
<table>
  <tr><th>Критерий</th><th>Korus</th><th>XMPP / Jabber</th><th>IBM Sametime</th><th>Lync / SfB</th></tr>
  {"".join(rows)}
</table>"""


def render_fig_legacy_timeline_svg() -> str:
    return """<figure class="fig fig-wide"><svg viewBox="0 0 1040 180" width="1040" height="180" xmlns="http://www.w3.org/2000/svg">
  <text x="520" y="26" text-anchor="middle" font-size="16" font-weight="bold">Эволюция корпоративного IM (упрощённо)</text>
  <line x1="48" y1="72" x2="992" y2="72" stroke="#9ca3af" stroke-width="2"/>
  <circle cx="96" cy="72" r="7" fill="#78716c"/><text x="96" y="98" text-anchor="middle" font-size="11">Jabber/XMPP</text>
  <text x="96" y="114" text-anchor="middle" font-size="10" fill="#6b7280">~2000</text>
  <circle cx="248" cy="72" r="7" fill="#78716c"/><text x="248" y="98" text-anchor="middle" font-size="11">Sametime</text>
  <circle cx="400" cy="72" r="7" fill="#78716c"/><text x="400" y="98" text-anchor="middle" font-size="11">Lync/SfB</text>
  <circle cx="552" cy="72" r="7" fill="#f59e0b"/><text x="552" y="98" text-anchor="middle" font-size="11">eXpress</text>
  <circle cx="704" cy="72" r="7" fill="#22c55e"/><text x="704" y="98" text-anchor="middle" font-size="11">Korus</text>
  <circle cx="856" cy="72" r="7" fill="#6366f1"/><text x="856" y="98" text-anchor="middle" font-size="11">VK / Пачка (облако)</text>
  <text x="520" y="156" text-anchor="middle" font-size="11" fill="#6b7280">Устаревшие — справочно и для миграции; не в промышленной TCO-матрице</text>
</svg><figcaption class="fig-cap">Рис. Устаревшие платформы и современные якоря Korus.</figcaption></figure>"""


def render_fig_legacy_infra_svg() -> str:
    series = []
    for ru, _label, ha_y, _single_y in LEGACY_INFRA_ANCHORS:
        if ru not in (10_000, 100_000):
            continue
        korus = next(a.infra_yearly for a in KORUS_ANCHORS if a.ru == ru)
        series.append((f"XMPP HA, {_fmt_reg_users(ru, compact=True)}", ha_y, "#78716c"))
        series.append((f"Korus, {_fmt_reg_users(ru, compact=True)}", korus, "#86efac"))
    return _bar_chart_svg(
        "Инфра ₽/год: XMPP (HA) и Korus при 10 и 100 тыс. рег. пользов.",
        series,
        caption="Только OPEX инфраструктуры. Устаревший стек без compliance; лицензия OSS = 0.",
    )


def render_legacy_migration_html() -> str:
    parts = []
    for title, bullets in LEGACY_MIGRATION_CASES:
        bl = "".join(f"<li>{escape(b)}</li>" for b in bullets)
        parts.append(f'<div class="case"><h4>{escape(title)}</h4><ul>{bl}</ul></div>')
    detail_rows = []
    for s in LEGACY_SOLUTIONS:
        detail_rows.append(
            f'<tr><td>{escape(s.name)}</td><td class="small">{escape(s.migration_angle)}</td></tr>'
        )
    parts.append(
        "<h4>Угол миграции по платформам</h4><table>"
        "<tr><th>Платформа</th><th>Типичный сценарий замены</th></tr>"
        + "".join(detail_rows)
        + "</table>"
    )
    return "".join(parts)


def render_legacy_pros_cons_html() -> str:
    parts = []
    for name, (pros, cons) in LEGACY_PROS_CONS.items():
        pl = "".join(f"<li>{escape(p)}</li>" for p in pros)
        cl = "".join(f"<li>{escape(c)}</li>" for c in cons)
        parts.append(
            f'<div class="case"><h4>{escape(name)}</h4>'
            f"<p><b>Плюсы (исторически):</b></p><ul>{pl}</ul>"
            f"<p><b>Минусы (сегодня):</b></p><ul>{cl}</ul></div>"
        )
    return "".join(parts)


def _matrix_block(anchor: KorusAnchor) -> str:
    rows = []
    for c in competitors_at_anchor(anchor):
        total = c.total_yearly()
        total_s = fmt_rub(total) if total is not None else "по КП"
        if c.license_yearly is not None and c.license_yearly > 0:
            lic_s = fmt_rub(c.license_yearly)
        elif c.license_yearly == 0 and c.name.startswith("Korus"):
            lic_s = "по КП"
        else:
            lic_s = c.license_note or "—"
        infra_s = fmt_rub(c.infra_yearly) if c.infra_yearly is not None else c.infra_note
        share = ""
        if total and c.license_yearly and total > 0:
            share = f" ({round(100 * c.license_yearly / total)}% лиц.)"
        rows.append(
            f"<tr><td>{escape(c.name)}</td>"
            f'<td class="money">{lic_s}</td><td class="money">{infra_s}</td>'
            f'<td class="money"><b>{total_s}</b>{share}</td></tr>'
        )
    return (
        f"<h3>Якорь {escape(anchor.code)} — {_fmt_reg_users(anchor.ru)}"
        + f" · {escape(_ru_profile(anchor.profile))}</h3>"
        + "<table><tr><th>Решение</th><th>Лицензия ₽/год</th><th>Инфра ₽/год</th><th>Итого TCO</th></tr>"
        + "".join(rows)
        + "</table>"
    )


def render_comparison_matrix_html() -> str:
    return "".join(_matrix_block(a) for a in KORUS_ANCHORS)


def load_nt_baseline() -> dict:
    for path in _NT_PATHS:
        if path.is_file():
            return json.loads(path.read_text(encoding="utf-8"))
    return {}


def _nt_scenario(data: dict, name: str) -> dict | None:
    for s in data.get("scenarios", []):
        if s.get("name") == name:
            return s
    return None


def render_fig_nt_latency_svg() -> str:
    data = load_nt_baseline()
    health = _nt_scenario(data, "parallel-health-sustained") or {}
    k6 = data.get("k6_health_fallback") or {}
    p50 = health.get("p50_ms")
    p95 = health.get("p95_ms")
    k6_p95 = k6.get("p95_ms")
    if not p95 and not k6_p95:
        return ""
    width, height = _CHART_WIDE, 340
    bars = []
    if p50 is not None:
        bars.append(("p50 /health", int(p50), "#86efac"))
    if p95 is not None:
        bars.append(("p95 /health", int(p95), "#22c55e"))
    if k6_p95 is not None:
        bars.append(("p95 проба", int(k6_p95), "#fcd34d"))
    bars.append(("порог k6", 500, "#fca5a5"))
    max_v = max(v for _, v, _ in bars) or 1
    margin_l, margin_t, margin_b = 72, 52, 64
    chart_h = height - margin_t - margin_b
    parts = [
        f'<figure class="fig fig-wide"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
        f'xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="28" text-anchor="middle" font-size="16" font-weight="bold">'
        "Задержка API (мс) — QEMU НТ</text>",
        f'<line x1="{margin_l}" y1="{margin_t + chart_h}" x2="{width - 32}" y2="{margin_t + chart_h}" stroke="#9ca3af"/>',
    ]
    bar_w = 88
    gap = 28
    x = margin_l + 20
    for label, val, color in bars:
        h = max(6, round(val / max_v * chart_h))
        y = margin_t + chart_h - h
        parts.append(f'<rect x="{x}" y="{y}" width="{bar_w}" height="{h}" fill="{color}" rx="3"/>')
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{y - 8}" text-anchor="middle" font-size="11">{val} мс</text>'
        )
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{margin_t + chart_h + 20}" text-anchor="middle" font-size="11">'
            f"{escape(label)}</text>"
        )
        x += bar_w + gap
    parts.append(
        '</svg><figcaption class="fig-cap">GET /health, 8 workers × 30 с. '
        "Порог k6 pilot-health: p95 &lt; 500 ms.</figcaption></figure>"
    )
    return "".join(parts)


def render_fig_nt_throughput_svg() -> str:
    data = load_nt_baseline()
    if not data:
        return ""
    health = _nt_scenario(data, "parallel-health-sustained") or {}
    read = _nt_scenario(data, "core-api-read-mixed") or {}
    burst = _nt_scenario(data, "message-pipeline-burst") or {}
    series = []
    if health.get("rps"):
        series.append(("Health", int(round(float(health["rps"]))), "#86efac", "запр/с"))
    rps = read.get("approx_rps")
    if rps:
        series.append(("REST", int(round(float(rps))), "#6366f1", "запр/с"))
    msg = burst.get("burst_msg_per_sec")
    if msg:
        series.append(("E2E", int(msg), "#f59e0b", "сообщ/с"))
    series.append(("Цель S-10k", _DESIGN_S10K_PEAK_MSG_S, "#e5e7eb", "сообщ/с"))
    return _bar_chart_svg(
        "Пропускная способность — замер и проектная цель S-10k",
        series,
        width=_CHART_WIDE,
        height=_CHART_STD_H,
        y_unit="",
        caption="Над столбцами — единицы (запр/с или сообщ/с). REST — auth + messages/ready. "
        "QEMU dev VM (~6–10 ГБ), не prod 64 ГБ.",
    )


def render_nt_baseline_html() -> str:
    data = load_nt_baseline()
    if not data:
        return '<p class="small comment">Артефакты НТ не найдены (docs/benchmarks/qemu-nt-baseline-2026-06-15.json).</p>'

    rows = []
    labels = {
        "parallel-health-sustained": "Устойчивая /health",
        "core-api-read-mixed": "REST: чтение (auth)",
        "rest-chat-list-burst": "Пакетный список чатов",
        "message-pipeline-burst": "Конвейер сообщений E2E",
        "messaging-e2e-load-rounds": "E2E мессенджинг + нагрузка",
    }
    for s in data.get("scenarios", []):
        name = labels.get(s.get("name", ""), s.get("name", ""))
        metric = ""
        if "p95_ms" in s:
            metric = f"p50 {s.get('p50_ms', '—')} мс · p95 {s['p95_ms']} мс · {s.get('rps', '—')} запр/с"
        elif "approx_rps" in s:
            metric = f"~{s['approx_rps']} запр/с · {s.get('requests_ok', '—')} усп. / {s.get('duration_sec', '—')} с"
        elif "burst_msg_per_sec" in s:
            metric = f"пик {s['burst_msg_per_sec']} сообщ/с · {s.get('burst_messages', '—')} сообщ. / {s.get('elapsed_sec', '—')} с"
        elif s.get("elapsed_ms") is not None and s.get("requests"):
            metric = f"~{s.get('approx_rps', '—')} запр/с · {s['elapsed_ms']} мс · {s['requests']} запр."
        elif s.get("result"):
            res = "ПРОЙДЕН" if str(s.get("result", "")).lower() == "pass" else str(s.get("result", "")).upper()
            metric = f"{res} · раундов={s.get('load_rounds', '—')}"
        fail = s.get("requests_fail", 0)
        ok_note = f", fail={fail}" if fail else ""
        rows.append(
            f"<tr><td>{escape(name)}</td><td>{escape(metric)}{ok_note}</td>"
            f'<td class="small">{escape(s.get("script", ""))}</td></tr>'
        )

    k6 = data.get("k6_health_fallback") or {}
    if k6:
        rows.append(
            f"<tr><td>Проба /health (один поток)</td><td>p95 {k6.get('p95_ms', '—')} мс · "
            f"{k6.get('requests', '—')} запр. / {k6.get('duration_sec', '—')} с</td>"
            f'<td class="small">{escape(k6.get("mode", ""))}</td></tr>'
        )

    notes_raw = data.get("presentation_notes", [])
    notes_ru = []
    for n in notes_raw:
        notes_ru.append(
            n.replace("Health p95 11 ms", "Health p95 11 мс")
            .replace("Burst messaging ~6 msg/s", "Пиковая рассылка ~6 сообщ/с")
            .replace("engineering smoke baseline", "инженерный smoke-baseline")
            .replace("formal soak — на stage", "формальный soak — на stage")
            .replace("не stage/prod soak", "не stage/prod soak-тест")
            .replace("REST с auth тяжелее", "REST с авторизацией тяжелее")
            .replace("не чистый API RPS", "не чистый API RPS")
        )
    notes = "".join(f"<li>{escape(n)}</li>" for n in notes_ru)
    env = escape(data.get("environment", ""))
    when = escape(data.get("generated_at_utc", ""))

    health = _nt_scenario(data, "parallel-health-sustained") or {}
    read = _nt_scenario(data, "core-api-read-mixed") or {}
    burst = _nt_scenario(data, "message-pipeline-burst") or {}
    design_rows = ""
    measured_msg = burst.get("burst_msg_per_sec")
    if measured_msg is not None:
        pct = round(100 * float(measured_msg) / _DESIGN_S10K_PEAK_MSG_S)
        design_rows = f"""
<table>
  <tr><th>Метрика</th><th>Замер (QEMU)</th><th>Проект S-10k</th><th>Комментарий</th></tr>
  <tr><td>E2E, сообщ/с</td><td><b>{measured_msg}</b></td><td>{_DESIGN_S10K_PEAK_MSG_S}</td>
      <td class="small">~{pct}% цели на dev VM; prod soak — stage</td></tr>
  <tr><td>Задержка /health p95</td><td><b>{health.get('p95_ms', '—')} мс</b></td><td>&lt; 500 мс (k6)</td>
      <td class="small">Запас по задержке</td></tr>
  <tr><td>REST, запр/с</td><td><b>{read.get('approx_rps', '—')}</b></td><td>—</td>
      <td class="small">Auth + messages + ready</td></tr>
</table>"""

    return f"""
<div class="note">
  <div class="req">Инженерный эталон НТ · {when}</div>
  <div class="comment">{env}</div>
</div>
<div class="fig-stack">
  <div>{render_fig_nt_latency_svg()}</div>
  <div>{render_fig_nt_throughput_svg()}</div>
</div>
<h4>Сценарии прогона</h4>
<table>
  <tr><th>Сценарий</th><th>Результат</th><th>Скрипт</th></tr>
  {"".join(rows)}
</table>
<h4>Замер и проектные цели</h4>
{design_rows}
<ul class="small comment">{notes}</ul>
<p class="small">Источник: <code>docs/benchmarks/qemu-nt-baseline-2026-06-15.json</code></p>"""


def render_pilot_footnote_html() -> str:
    p = PILOT_TRIAL
    return f"""
<div class="warn">
  <div class="req">Пробник — вне промышленной матрицы</div>
  <div class="comment">
    Pilot при {_fmt_reg_users(10_000, compact=True)}: инфра <span class="money">{fmt_rub(p.infra_yearly)}/год</span> (~{_fmt_per_user_month(p.infra_per_user_month)}),
    RAM {p.ram_gb}, без Solr. Для оценки функционала, не для TCO-сравнения с eXpress Corporate.
    Промышленное сравнение начинается с <b>Стандарт S-10k</b> (инфра <span class="money">{fmt_rub(KORUS_ANCHORS[0].infra_yearly)}/год</span>, полный функционал).
  </div>
</div>"""


def render_pros_cons_html() -> str:
    tier_a = ("Korus Messenger", "eXpress Corporate", "Пачка (облако)", "VK WorkSpace SaaS")
    parts = ['<h4>Уровень A — промышленная матрица</h4>']
    for name in tier_a:
        pros, cons = PROS_CONS_BY_PRODUCT[name]
        pl = "".join(f"<li>{escape(p)}</li>" for p in pros)
        cl = "".join(f"<li>{escape(c)}</li>" for c in cons)
        korus_cls = " case-korus" if name == "Korus Messenger" else ""
        parts.append(
            f'<div class="case{korus_cls}"><h4>{escape(name)}</h4>'
            f"<p><b>Плюсы:</b></p><ul>{pl}</ul>"
            f"<p><b>Минусы:</b></p><ul>{cl}</ul></div>"
        )
    parts.append(render_tier_b_pros_cons_html())
    parts.append(render_tier_c_pros_cons_html())
    return "".join(parts)


def render_pros_cons_brief_html() -> str:
    """One-pager: только Korus vs eXpress (основной конкурент в контуре)."""
    parts = ['<p class="comment">Ключевое сравнение для КП в контуре заказчика @10k+ рег. пользов.</p>']
    for name in ("Korus Messenger", "eXpress Corporate"):
        pros, cons = PROS_CONS_BY_PRODUCT[name]
        pl = "".join(f"<li>{escape(p)}</li>" for p in pros[:4])
        cl = "".join(f"<li>{escape(c)}</li>" for c in cons[:3])
        korus_cls = " case-korus" if name == "Korus Messenger" else ""
        parts.append(
            f'<div class="case{korus_cls}"><h4>{escape(name)}</h4>'
            f"<p><b>Плюсы:</b></p><ul>{pl}</ul>"
            f"<p><b>Ограничения:</b></p><ul>{cl}</ul></div>"
        )
    return "".join(parts)


def render_brief_disclaimers_html() -> str:
    return """
<div class="warn">
  <ul class="comment">
    <li>Цифры infra и TCO — ориентиры для переговоров; финальное КП — по sizing заказчика.</li>
    <li>eXpress @10/100 тыс. рег. — модельная оценка infra; лицензия — публичный прайс express.ms.</li>
    <li>Полная матрица (11 продуктов, legacy, НТ): см. <code>competitor_comparison.html</code>.</li>
  </ul>
</div>"""


def render_sources_html() -> str:
    return """
<ul class="small comment">
  <li><b>eXpress:</b> express.ms/prices, docs.express.ms (hardware @100, platform &lt;5k, architecture, DLPS)</li>
  <li><b>eXpress sizing @500/1000:</b> обзор Anti-Malware.ru (таблицы вендора), admin_guide_install.pdf</li>
  <li><b>Пачка:</b> pachca.com/prices, pachca.com/for-corporations</li>
  <li><b>VK WorkSpace:</b> workspace.vk.ru, docs on-prem Superapp @2k</li>
  <li><b>Loop:</b> docs.loop.ru (hardware requirements, Mattermost lineage)</li>
  <li><b>ejabberd:</b> docs.ejabberd.im (cluster, scalability)</li>
  <li><b>Openfire:</b> igniterealtime.org/docs/openfire (clustering, system requirements)</li>
  <li><b>Prosody:</b> prosody.im doc (lightweight XMPP)</li>
  <li><b>IBM/HCL Sametime:</b> документация HCL (устаревший enterprise)</li>
  <li><b>Microsoft:</b> руководства миграции Skype for Business → Teams</li>
  <li><b>Loop:</b> loop.ru/pricing, docs.loop.ru (hardware, install)</li>
  <li><b>Rocket.Chat:</b> docs.rocket.chat (deploy, EE)</li>
  <li><b>Mattermost:</b> mattermost.com (ref-arch concurrent)</li>
  <li><b>МТС Линк Чаты:</b> mts-link.ru/products/messenger/, business.mts.ru</li>
  <li><b>Compass:</b> getcompass.ru/pricing (390/490 ₽/мес, 2026)</li>
  <li><b>TrueConf:</b> trueconf.ru/products/server/, trueconf.ru/server/buy/</li>
  <li><b>Korus НТ QEMU:</b> docs/benchmarks/qemu-nt-baseline-2026-06-15.json (2026-06-15)</li>
  <li><b>Korus:</b> внутренний sizing Стандарт/Корпоративный + ставки инфра {date}</li>
</ul>
""".format(date=PRICE_AS_OF)
