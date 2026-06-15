"""Data and SVG charts for competitor comparison presentation."""

from __future__ import annotations

from dataclasses import dataclass
from html import escape

from tz_product_pricing import (
    PRICE_AS_OF,
    _RATES,
    fmt_rub,
    fmt_rub_short,
    pilot_fullstack_monolith_monthly,
    pilot_profile_monthly,
    standard_profile_monthly,
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
        "Оценка; load test на stage",
    ),
    KorusAnchor(
        "E-1M",
        "Enterprise",
        1_000_000,
        20_000,
        600,
        "~0,9–1,2 TB",
        _ENTERPRISE_1M_MONTHLY,
        "Оценка; load test на stage",
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
        "docs.express.ms (официально @100 RU)",
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
            license_note="3 000 ₽/user/год, on-prem",
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
                    license_note="Корпорация, 399 ₽/user/мес (год)",
                ),
                CompetitorRow(
                    "VK WorkSpace SaaS",
                    license_yearly=vk_saas_yearly(ru),
                    infra_yearly=0,
                    license_note="Базовый, 207 ₽/user/мес (год)",
                ),
            ]
        )
    return tuple(rows)


FEATURE_ROWS: tuple[tuple[str, str, str, str, str, str], ...] = (
    ("On-prem / изолированный контур", "✓", "✓", "—", "—", "✓"),
    ("Export / legal hold / dual-TTL", "✓ ядро", "DLP/политики", "export API", "зависит", "плагины"),
    ("Полнотекстовый поиск", "Solr / SQL", "✓", "✓", "✓", "Elasticsearch"),
    ("E2EE", "MLS (sign-off)", "✓ E2EE", "—", "—", "опционально"),
    ("ВКС", "WebRTC mesh", "до 500 уч.", "до 10 уч.", "✓", "интеграции"),
    ("Мобильные клиенты", "roadmap", "iOS/Android/Аврора", "✓", "✓", "✓"),
    ("SmartApps / суперапп", "roadmap", "✓", "боты/API", "workspace", "apps"),
    ("ФСТЭК / реестр РФ", "в процессе", "✓", "✓", "✓", "✓ (Loop)"),
    ("Публичный sizing", "✓ якоря", "частично", "—", "on-prem КП", "✓ до 2k"),
    ("Публичный прайс лицензии", "КП", "✓", "✓", "✓ SaaS", "КП EE"),
)

REFERENCE_SOLUTIONS: tuple[tuple[str, str, str, str], ...] = (
    (
        "Loop",
        "1–2k RU: 2–4 GB RAM",
        "Enterprise по запросу",
        "Mattermost-fork, РФ",
    ),
    (
        "Mattermost EE",
        "15k concurrent ref-arch",
        "100k concurrent ref-arch",
        "concurrent ≠ RU",
    ),
    (
        "Rocket.Chat",
        "≤500 concurrent: 4+4 GB",
        "Enterprise ≥500 concurrent",
        "MongoDB replica",
    ),
    (
        "VK Superapp on-prem",
        "1–2k: 22 vCPU, 56 GB, 400 GB SSD",
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
        "Open source; клиенты и серверы разных вендоров",
        "1 узел 2–4 GB RAM, до ~2k RU",
        "Кластер 3+ узлов; >10k — кастомная архитектура",
        "Нет единого sizing; зависит от сервера (ejabberd/Openfire/Prosody)",
        "Legacy; новые внедрения редки",
        "Миграция истории (MAM), roster, federated domains",
    ),
    LegacySolution(
        "ejabberd",
        "2000–2020-е",
        "XMPP + MQTT (опционально)",
        "GPL + коммерческая поддержка ProcessOne",
        "4 GB RAM, тысячи одновременных сессий",
        "Кластер: сотни тысяч RU (док. вендора)",
        "Erlang-кластер; Mnesia/SQL для persistence",
        "Активен, но нишевый; enterprise — через интегратора",
        "Экспорт MAM/Roster; часто параллельный запуск с новым IM",
    ),
    LegacySolution(
        "Openfire (Spark)",
        "2000–2010-е",
        "XMPP",
        "Apache 2.0; Ignite Realtime / Community",
        "2–4 GB RAM, до ~5k RU (single node)",
        "Clustering plugin; >10k — не типовой сценарий",
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
        ">5k RU — не ref-arch; federation only",
        "Lua; лёгкий single-node",
        "Активен для малых контуров и federation",
        "Часто не целевой масштаб для 10k+",
    ),
    LegacySolution(
        "IBM / HCL Sametime",
        "2000–2010-е",
        "Проприетарный + частично XMPP/SIP",
        "Коммерческая лицензия IBM → HCL",
        "Отдельный сервер приложений + DB",
        "Кластер Domino/Sametime; типично 10k–100k RU",
        "Тяжёлый Java/Domino-стек; инд. sizing",
        "Legacy; HCL поддерживает, но рынок смещён",
        "Compliance-архивы, интеграция с почтой/календарём",
    ),
    LegacySolution(
        "Microsoft Lync / Skype for Business",
        "2010–2020-е",
        "SIP / проприетарный (не XMPP)",
        "Microsoft EA / подписка",
        "Front End pool + SQL; от ~8 GB на роль",
        "Enterprise pool + Edge; 100k+ RU",
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
        "Cisco pushing Webex; on-prem — legacy",
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
    (10_000, "S-10k (HA vs single-node)", legacy_xmpp_infra_monthly(10_000), legacy_xmpp_infra_monthly(10_000, ha=False)),
    (100_000, "S-100k (HA vs урезанный)", legacy_xmpp_infra_monthly(100_000), legacy_xmpp_infra_monthly(100_000, ha=False)),
)

LEGACY_FEATURE_ROWS: tuple[tuple[str, str, str, str, str], ...] = (
    ("On-prem в контуре", "✓", "✓ (OSS)", "✓", "✓ (Windows)"),
    ("Современные моб. клиенты", "roadmap", "устар./сторонние", "legacy mobile", "Teams path"),
    ("Полнотекстовый поиск", "Solr / SQL", "MAM / ограниченно", "встроенный (legacy)", "Exchange index"),
    ("Export / legal hold / audit", "✓ ядро", "DIY / плагины", "частично", "eDiscovery (Teams)"),
    ("E2EE", "MLS (sign-off)", "OMEMO (опц.)", "—", "—"),
    ("ВКС / голос", "WebRTC", "внешние / Jingle (редко)", "✓ (legacy)", "✓ (SIP/Skype)"),
    ("Федерация / multi-tenant org", "org-shard", "✓ XMPP federation", "domino domains", "AD forest"),
    ("Вендор / LTS", "Korus", "community / integrator", "HCL Sametime", "Microsoft EoL → Teams"),
    ("Публичный sizing @10k+", "✓ якоря", "нет единого", "инд. IBM/HCL", "Microsoft ref-arch"),
    ("Лицензия ПО", "КП", "0 (OSS) + интеграция", "коммерческая", "EA / подписка"),
    ("TCO @10k (infra, HA)", fmt_rub_short(KORUS_ANCHORS[0].infra_yearly), fmt_rub_short(legacy_xmpp_infra_monthly(10_000) * 12), "по КП", "по КП"),
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
    "XMPP / Jabber (legacy)": (
        [
            "Лицензия сервера 0 (OSS): ejabberd, Openfire, Prosody",
            "Низкий infra на малых масштабах (1–2 узла)",
            "Federation между доменами; открытый протокол",
            "Многие гос/банки уже имели контур — знаком ops",
        ],
        [
            "Нет единого вендора, mobile UX устарел",
            "Compliance (export, legal hold) — самодельные скрипты",
            "@10k+ HA — непрозрачный sizing; поиск слабый",
            "Нет типового пути к E2EE и современному ВКС",
        ],
    ),
    "IBM / HCL Sametime": (
        [
            "Зрелый enterprise-стек; интеграция с Domino/Notes",
            "Знаком крупным заказчикам 2000–2010-х",
        ],
        [
            "Legacy-лицензии и тяжёлый стек",
            "Рынок смещён на Teams / отечественные мессенджеры",
            "TCO поддержки и миграции часто выше нового внедрения",
        ],
    ),
}

PROS_CONS: dict[str, tuple[list[str], list[str]]] = {
    "Korus Messenger": (
        [
            "Tiered infra: ~3,3 ₽/user/мес @100k (только OPEX)",
            "Compliance by design: export gate, legal hold, dual-TTL, audit",
            "On-prem, мультитenant org, путь Standard → Enterprise",
            "Pilot (пробник) отделён от production-матрицы",
        ],
        [
            "Меньше готового супераппа (SmartApps, почта, ВКС 500)",
            "Mobile native — в roadmap",
            "Formal E2EE prod sign-off pending",
        ],
    ),
    "eXpress Corporate": (
        [
            "ФСТЭК №4997, сертифицированный суперапп",
            "Публичный прайс 3 000 ₽/user/год; CTS без лимита",
            "ВКС до 500, SmartApps, федерация, iOS/Android/Аврора",
            "Детальные таблицы sizing до ~1k RU",
        ],
        [
            "Лицензия 83–99% TCO @10k–100k",
            "Sizing >1k — КП; @10k+ инд. проект",
            "Тяжёлый стек: Kafka, etcd, transcoding, media",
        ],
    ),
    "Пачка (облако)": (
        [
            "Нет своего железа; SLA 99,9%",
            "BYOK hybrid для корпораций",
            "399 ₽/user/мес (год), оплата за активных",
        ],
        [
            "Не on-prem в контуре заказчика",
            "TCO @100k ~480 млн ₽/год",
            "Нет server sizing",
        ],
    ),
    "VK WorkSpace SaaS": (
        [
            "Полный workspace: почта, диск, задачи, ВКС",
            "207 ₽/user/мес (год) — прозрачный SaaS",
            "On-prem Superapp @2k: 56 GB RAM (документировано)",
        ],
        [
            "On-prem мессенджer-only — инд. КП",
            "TCO SaaS @100k ~248 млн ₽/год",
            "Не сравнивать Superapp с «только чат» без оговорки",
        ],
    ),
}


def _bar_chart_svg(
    title: str,
    series: list[tuple[str, int, str]],
    *,
    width: int = 720,
    height: int = 320,
    caption: str,
) -> str:
    if not series:
        return ""
    max_v = max(v for _, v, _ in series) or 1
    margin_l, margin_b, margin_t = 72, 48, 40
    chart_h = height - margin_b - margin_t
    bar_w = min(72, (width - margin_l - 40) // max(len(series), 1) - 10)
    gap = 10
    parts = [
        f'<figure class="fig"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
        f'xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="24" text-anchor="middle" font-size="14" font-weight="bold">{escape(title)}</text>',
        f'<line x1="{margin_l}" y1="{margin_t + chart_h}" x2="{width - 20}" y2="{margin_t + chart_h}" stroke="#9ca3af"/>',
    ]
    for i in range(5):
        y = margin_t + chart_h - chart_h * i // 4
        val = max_v * i // 4
        parts.append(f'<line x1="{margin_l}" y1="{y}" x2="{width - 20}" y2="{y}" stroke="#e5e7eb"/>')
        parts.append(
            f'<text x="{margin_l - 6}" y="{y + 4}" text-anchor="end" font-size="9" fill="#4b5563">'
            f"{escape(fmt_rub_short(val))}</text>"
        )
    x = margin_l + 8
    for label, value, color in series:
        h = max(4, round(value / max_v * chart_h))
        y = margin_t + chart_h - h
        parts.append(f'<rect x="{x}" y="{y}" width="{bar_w}" height="{h}" fill="{color}" rx="3"/>')
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{y - 6}" text-anchor="middle" font-size="8" font-weight="600">'
            f"{escape(fmt_rub_short(value))}</text>"
        )
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{margin_t + chart_h + 14}" text-anchor="middle" font-size="8">'
            f"{escape(label)}</text>"
        )
        x += bar_w + gap
    parts.append(f'</svg><figcaption class="fig-cap">{escape(caption)}</figcaption></figure>')
    return "".join(parts)


def _stacked_tco_svg(title: str, items: list[tuple[str, int, int]], caption: str) -> str:
    width, height = 720, 340
    margin_l, margin_t, margin_b = 80, 44, 56
    chart_h = height - margin_t - margin_b
    max_v = max(i + l for _, i, l in items) or 1
    bar_w, gap = 88, 16
    parts = [
        f'<figure class="fig"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" '
        f'xmlns="http://www.w3.org/2000/svg">',
        f'<text x="{width // 2}" y="24" text-anchor="middle" font-size="14" font-weight="bold">{escape(title)}</text>',
        f'<rect x="{margin_l - 60}" y="{margin_t - 8}" width="12" height="12" fill="#86efac"/>'
        f'<text x="{margin_l - 44}" y="{margin_t + 2}" font-size="10">Infra</text>',
        f'<rect x="{margin_l + 40}" y="{margin_t - 8}" width="12" height="12" fill="#6366f1"/>'
        f'<text x="{margin_l + 56}" y="{margin_t + 2}" font-size="10">Лицензия</text>',
        f'<line x1="{margin_l}" y1="{margin_t + chart_h}" x2="{width - 24}" y2="{margin_t + chart_h}" stroke="#9ca3af"/>',
    ]
    x = margin_l + 4
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
            f'<text x="{x + bar_w // 2}" y="{y_base - h_lic - h_infra - 6}" text-anchor="middle" '
            f'font-size="8" font-weight="600">{escape(fmt_rub_short(total))}</text>'
        )
        parts.append(
            f'<text x="{x + bar_w // 2}" y="{margin_t + chart_h + 16}" text-anchor="middle" font-size="8">'
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
    return """<figure class="fig"><svg viewBox="0 0 720 210" width="720" height="210" xmlns="http://www.w3.org/2000/svg">
  <text x="360" y="22" text-anchor="middle" font-size="14" font-weight="bold">Профили Korus: пороги и якоря</text>
  <rect x="20" y="44" width="210" height="52" rx="8" fill="#fef3c7" stroke="#f59e0b"/>
  <text x="125" y="66" text-anchor="middle" font-size="12" font-weight="600">Pilot (пробник)</text>
  <text x="125" y="84" text-anchor="middle" font-size="10" fill="#6b7280">до 10k · вне TCO-матрицы</text>
  <rect x="250" y="44" width="210" height="52" rx="8" fill="#dcfce7" stroke="#22c55e"/>
  <text x="355" y="66" text-anchor="middle" font-size="12" font-weight="600">Standard</text>
  <text x="355" y="84" text-anchor="middle" font-size="10" fill="#6b7280">floor 10k · S-10k · S-50k · S-100k</text>
  <rect x="480" y="44" width="210" height="52" rx="8" fill="#dbeafe" stroke="#3b82f6"/>
  <text x="585" y="66" text-anchor="middle" font-size="12" font-weight="600">Enterprise</text>
  <text x="585" y="84" text-anchor="middle" font-size="10" fill="#6b7280">floor 100k · E-500k · E-1M</text>
  <line x1="250" y1="120" x2="460" y2="120" stroke="#22c55e" stroke-width="2"/>
  <circle cx="280" cy="120" r="5" fill="#22c55e"/><text x="280" y="140" text-anchor="middle" font-size="9">10k</text>
  <circle cx="355" cy="120" r="5" fill="#22c55e"/><text x="355" y="140" text-anchor="middle" font-size="9">50k</text>
  <circle cx="430" cy="120" r="5" fill="#22c55e"/><text x="430" y="140" text-anchor="middle" font-size="9">100k</text>
  <line x1="480" y1="120" x2="690" y2="120" stroke="#3b82f6" stroke-width="2"/>
  <circle cx="540" cy="120" r="5" fill="#3b82f6"/><text x="540" y="140" text-anchor="middle" font-size="9">500k</text>
  <circle cx="630" cy="120" r="5" fill="#3b82f6"/><text x="630" y="140" text-anchor="middle" font-size="9">1M</text>
  <text x="360" y="168" text-anchor="middle" font-size="10" fill="#6b7280">eXpress @100–1000 RU — ниже floor Standard Korus (10k)</text>
  <text x="360" y="184" text-anchor="middle" font-size="10" fill="#6b7280">Media eXpress: ~0,3 vCPU × участник; ~10% users в звонке одновременно</text>
</svg><figcaption class="fig-cap">Рис. 1. Production-сравнение только на якорях ≥10k (Standard) и ≥100k (Enterprise).</figcaption></figure>"""


def render_fig_infra_by_anchor_svg() -> str:
    series = [(a.code, a.infra_yearly, "#86efac" if a.profile == "Standard" else "#6366f1") for a in KORUS_ANCHORS]
    return _bar_chart_svg(
        "Infra Korus: годовая стоимость по якорям",
        series,
        caption=f"Только OPEX infra. Ставки {PRICE_AS_OF}; не оферта.",
    )


def render_fig_express_infra_tiers_svg() -> str:
    series = []
    for t in EXPRESS_TIERS:
        if t.vcpu <= 0:
            continue
        series.append((f"{t.ru} RU", t.infra_yearly_est, "#f59e0b"))
    return _bar_chart_svg(
        "eXpress: оценка infra ₽/год (публичные таблицы @100–1000)",
        series,
        caption="Упаковка vCPU/RAM/SSD в VM по тем же ставкам, что Korus. Ниже 10k — не production-якорь Korus.",
    )


def render_fig_ram_compare_svg() -> str:
    """Grouped RAM at comparable public tiers."""
    width, height = 640, 280
    groups = [
        ("100 RU", [("eXpress", 17, "#f59e0b")]),
        ("1k RU", [("eXpress", 62, "#f59e0b")]),
        ("10k RU", [("Korus S-10k", 64, "#86efac"), ("eXpress*", 120, "#fcd34d")]),
        ("100k RU", [("Korus S-100k", 140, "#86efac"), ("eXpress*", 200, "#fcd34d")]),
    ]
    max_ram = 200
    parts = [
        f'<figure class="fig"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" xmlns="http://www.w3.org/2000/svg">',
        '<text x="320" y="22" text-anchor="middle" font-size="13" font-weight="bold">Суммарная RAM (GB) — Korus vs eXpress</text>',
        f'<line x1="50" y1="220" x2="600" y2="220" stroke="#9ca3af"/>',
    ]
    gx = 70
    for label, bars in groups:
        parts.append(f'<text x="{gx + 40}" y="240" text-anchor="middle" font-size="9">{escape(label)}</text>')
        bx = gx
        for name, ram, color in bars:
            h = max(8, round(ram / max_ram * 160))
            parts.append(f'<rect x="{bx}" y="{220 - h}" width="36" height="{h}" fill="{color}" rx="2"/>')
            parts.append(f'<text x="{bx + 18}" y="{220 - h - 4}" text-anchor="middle" font-size="8">{ram}</text>')
            bx += 40
        gx += 130
    parts.append(
        '</svg><figcaption class="fig-cap">* eXpress @10k/100k — оценка infra-модели; '
        "официальный sizing — инд. проект.</figcaption></figure>"
    )
    return "".join(parts)


def render_fig_tco_s10k_svg() -> str:
    return _stacked_tco_svg(
        "TCO @ 10 000 RU (infra + лицензия)",
        _tco_items_for_anchor(KORUS_ANCHORS[0]),
        caption="Korus лицензия — строка КП. eXpress: 30 млн ₽/год лицензий.",
    )


def render_fig_tco_s100k_svg() -> str:
    return _stacked_tco_svg(
        "TCO @ 100 000 RU (infra + лицензия)",
        _tco_items_for_anchor(KORUS_ANCHORS[2]),
        caption="eXpress infra — оценка 25 млн ₽/год.",
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
        "TCO Enterprise: E-500k и E-1M (Korus vs eXpress)",
        items,
        caption="Infra eXpress @500k/1M — оценка; лицензия по публичному прайсу.",
    )


def render_fig_license_share_svg() -> str:
    """License as % of TCO @10k and @100k for eXpress."""
    width, height = 520, 220
    rows = [
        ("10k", express_license_yearly(10_000), express_infra_yearly(10_000) or 0),
        ("100k", express_license_yearly(100_000), express_infra_yearly(100_000) or 0),
    ]
    parts = [
        f'<figure class="fig"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" xmlns="http://www.w3.org/2000/svg">',
        '<text x="260" y="22" text-anchor="middle" font-size="13" font-weight="bold">eXpress: доля лицензии в TCO</text>',
    ]
    y = 50
    for label, lic, infra in rows:
        total = lic + infra
        pct = round(100 * lic / total) if total else 0
        w_lic = round(400 * lic / total) if total else 0
        w_inf = 400 - w_lic
        parts.append(f'<text x="40" y="{y + 14}" font-size="10">@{label} RU</text>')
        parts.append(f'<rect x="100" y="{y}" width="{w_lic}" height="22" fill="#6366f1"/>')
        parts.append(f'<rect x="{100 + w_lic}" y="{y}" width="{w_inf}" height="22" fill="#86efac"/>')
        parts.append(f'<text x="510" y="{y + 15}" font-size="10">{pct}% lic</text>')
        y += 36
    parts.append(
        '</svg><figcaption class="fig-cap">Фиолетовый — лицензия; зелёный — infra (оценка).</figcaption></figure>'
    )
    return "".join(parts)


def render_fig_license_per_user_svg() -> str:
    ru = 10_000
    series = [
        ("eXpress lic", EXPRESS_LICENSE_RUB_PER_USER_YEAR // 12, "#6366f1"),
        ("Пачка", PACHKA_CORP_RUB_PER_USER_MONTH_YEAR, "#f59e0b"),
        ("VK SaaS", VK_SAAS_RUB_PER_USER_MONTH_YEAR, "#93c5fd"),
        ("Korus infra", round(KORUS_ANCHORS[0].infra_per_user_month), "#86efac"),
    ]
    width, height = 560, 260
    max_v = max(s[1] for s in series) or 1
    margin_l, margin_t, chart_h = 64, 36, 160
    parts = [
        f'<figure class="fig"><svg viewBox="0 0 {width} {height}" width="{width}" height="{height}" xmlns="http://www.w3.org/2000/svg">',
        f'<text x="280" y="22" text-anchor="middle" font-size="13" font-weight="bold">₽/user/мес @ {ru:,} RU</text>'.replace(",", " "),
        f'<line x1="{margin_l}" y1="{margin_t + chart_h}" x2="500" y2="{margin_t + chart_h}" stroke="#9ca3af"/>',
    ]
    x = 72
    for label, value, color in series:
        h = max(8, round(value / max_v * chart_h))
        y = margin_t + chart_h - h
        parts.append(f'<rect x="{x}" y="{y}" width="72" height="{h}" fill="{color}" rx="3"/>')
        parts.append(f'<text x="{x + 36}" y="{y - 6}" text-anchor="middle" font-size="10" font-weight="600">{value} ₽</text>')
        parts.append(f'<text x="{x + 36}" y="{margin_t + chart_h + 18}" text-anchor="middle" font-size="9">{escape(label)}</text>')
        x += 96
    parts.append("</svg><figcaption class=\"fig-cap\">eXpress/Пачка/VK — подписка; Korus — только infra OPEX.</figcaption></figure>")
    return "".join(parts)


def render_korus_anchor_table_html() -> str:
    rows = []
    for a in KORUS_ANCHORS:
        rows.append(
            f"<tr><td><b>{escape(a.code)}</b></td><td>{escape(a.profile)}</td>"
            f"<td>{a.ru:,}".replace(",", " ")
            + f"</td><td>{a.peak_online:,}".replace(",", " ")
            + f"</td><td>~{a.peak_msg_s}</td><td>{escape(a.ram_gb)}</td>"
            f'<td class="money">{fmt_rub(a.infra_yearly)}</td>'
            f"<td>~{a.infra_per_user_month:.1f} ₽</td>"
            f'<td class="small">{escape(a.infra_note)}</td></tr>'
        )
    return f"""
<table>
  <tr><th>Якорь</th><th>Профиль</th><th>RU</th><th>Пик онлайн</th><th>Пик msg/s</th>
      <th>RAM</th><th>Infra ₽/год</th><th>₽/user/мес</th><th>Примечание</th></tr>
  {"".join(rows)}
</table>"""


def render_express_full_table_html() -> str:
    rows = []
    for t in EXPRESS_TIERS:
        if t.vcpu > 0:
            hw = f"{t.vcpu} / {t.ram_gb} GB / {t.ssd_tb:.2f} TB"
            infra = t.infra_yearly_est
        else:
            hw = "инд. проект"
            infra = express_infra_yearly(t.ru) or 0
        rows.append(
            f"<tr><td>{t.ru:,}".replace(",", " ")
            + f"</td><td>{escape(hw)}</td>"
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
                f"<tr><td>{t.ru}</td><td>{escape(name)}</td><td>{cpu}</td><td>{ram} GB</td>"
                f"<td>{disk:.3f} TB</td></tr>"
            )
    return f"""
<h4>Сводка по масштабам eXpress</h4>
<table>
  <tr><th>RU</th><th>vCPU / RAM / SSD</th><th>Infra ₽/год</th><th>Лицензия ₽/год</th><th>TCO</th><th>Якорь Korus</th><th>Источник</th></tr>
  {"".join(rows)}
</table>
<h4>Детализация ролей eXpress (@100–1000)</h4>
<table>
  <tr><th>RU</th><th>Роль</th><th>vCPU</th><th>RAM</th><th>SSD</th></tr>
  {"".join(comp_rows)}
</table>
<h4>Тарифы eXpress (публично)</h4>
<table>
  <tr><th>Тариф</th><th>On-prem лицензия</th><th>Примечание</th></tr>
  <tr><td>Corporate</td><td class="money">3 000 ₽/user/год</td><td>Без лимита CTS-серверов</td></tr>
  <tr><td>Corporate + SmartApps</td><td class="money">5 000 ₽/user/год</td><td>Супераппы, интеграции</td></tr>
  <tr><td>Lite (облако)</td><td class="money">200 ₽/user/мес</td><td>5–1000 users, Вымпелком</td></tr>
  <tr><td>Продление SA Corporate</td><td class="money">1 500 ₽/user/год</td><td>После бессрочной лицензии</td></tr>
  <tr><td>Стек</td><td colspan="2">Docker, PostgreSQL 14+, Kafka, etcd 3.5+, Redis 7.2+, CPU ≥3,6 GHz, сеть 1 Gbps</td></tr>
</table>
<p class="small">Диск eXpress: расчёт 1 GB журналов + 4 GB/user/год × 4 года (может расти при активном использовании).</p>
"""


def render_pricing_reference_html() -> str:
    return f"""
<table>
  <tr><th>Решение</th><th>Модель</th><th>₽/user/мес (ориентир)</th><th>@10k ₽/год</th><th>@100k ₽/год</th></tr>
  <tr><td>Korus infra</td><td>OPEX</td><td class="money">~{KORUS_ANCHORS[0].infra_per_user_month:.0f} / ~{KORUS_ANCHORS[2].infra_per_user_month:.1f}</td>
      <td class="money">{fmt_rub(KORUS_ANCHORS[0].infra_yearly)}</td>
      <td class="money">{fmt_rub(KORUS_ANCHORS[2].infra_yearly)}</td></tr>
  <tr><td>eXpress Corporate</td><td>лицензия + infra</td><td class="money">250 + infra</td>
      <td class="money">{fmt_rub(express_license_yearly(10_000) + (express_infra_yearly(10_000) or 0))}</td>
      <td class="money">{fmt_rub(express_license_yearly(100_000) + (express_infra_yearly(100_000) or 0))}</td></tr>
  <tr><td>Пачка Корпорация</td><td>SaaS</td><td class="money">399</td>
      <td class="money">{fmt_rub(pachka_yearly(10_000))}</td>
      <td class="money">{fmt_rub(pachka_yearly(100_000))}</td></tr>
  <tr><td>VK WorkSpace SaaS</td><td>SaaS</td><td class="money">207</td>
      <td class="money">{fmt_rub(vk_saas_yearly(10_000))}</td>
      <td class="money">{fmt_rub(vk_saas_yearly(100_000))}</td></tr>
  <tr><td>Пачка Компания</td><td>SaaS</td><td class="money">159</td>
      <td class="money">{fmt_rub(pachka_yearly(10_000, corp=False))}</td>
      <td class="money">{fmt_rub(pachka_yearly(100_000, corp=False))}</td></tr>
</table>
<p class="small">Ставки infra: {PRICE_AS_OF}, {escape(_RATES['server_16'].source)}.</p>
"""


def render_feature_matrix_html() -> str:
    rows = []
    for feat, k, e, p, v, l in FEATURE_ROWS:
        rows.append(
            f"<tr><td>{escape(feat)}</td><td>{escape(k)}</td><td>{escape(e)}</td>"
            f"<td>{escape(p)}</td><td>{escape(v)}</td><td>{escape(l)}</td></tr>"
        )
    return f"""
<table>
  <tr><th>Критерий</th><th>Korus</th><th>eXpress</th><th>Пачка</th><th>VK SaaS</th><th>Loop</th></tr>
  {"".join(rows)}
</table>"""


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
<p class="small comment">В production-матрице TCO legacy не участвуют — справочное и миграционное сравнение.</p>"""


def render_legacy_infra_table_html() -> str:
    rows = []
    for ru, label, ha_y, single_y in LEGACY_INFRA_ANCHORS:
        korus_y = next((a.infra_yearly for a in KORUS_ANCHORS if a.ru == ru), None)
        korus_s = fmt_rub(korus_y) if korus_y else "—"
        delta_ha = ""
        if korus_y and ha_y:
            pct = round(100 * (korus_y - ha_y) / korus_y)
            delta_ha = f" ({pct:+d}% vs Korus)" if pct else ""
        rows.append(
            f"<tr><td>{ru:,}".replace(",", " ")
            + f"</td><td>{escape(label)}</td>"
            f'<td class="money">{fmt_rub(ha_y)}/год</td>'
            f'<td class="money">{fmt_rub(single_y)}/год</td>'
            f'<td class="money">{korus_s}</td>'
            f'<td class="small">{delta_ha}</td></tr>'
        )
    return f"""
<h4>Infra-only: XMPP-кластер vs Korus (те же ставки {PRICE_AS_OF})</h4>
<table>
  <tr><th>RU</th><th>Сценарий</th><th>XMPP HA ₽/год</th><th>XMPP 1-node ₽/год</th>
      <th>Korus ₽/год</th><th>Δ HA</th></tr>
  {"".join(rows)}
</table>
<p class="small comment">Legacy дешевле по infra, но без Solr, export gate, workers, Keycloak-tier ops.
  Лицензия OSS = 0; скрытые costs — интеграция, поддержка, миграция, compliance DIY.</p>"""


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
    return """<figure class="fig"><svg viewBox="0 0 720 120" width="720" height="120" xmlns="http://www.w3.org/2000/svg">
  <text x="360" y="18" text-anchor="middle" font-size="13" font-weight="bold">Эволюция корпоративного IM (упрощённо)</text>
  <line x1="40" y1="60" x2="680" y2="60" stroke="#9ca3af" stroke-width="2"/>
  <circle cx="80" cy="60" r="6" fill="#78716c"/><text x="80" y="82" text-anchor="middle" font-size="8">Jabber/XMPP</text>
  <text x="80" y="94" text-anchor="middle" font-size="7" fill="#6b7280">~2000</text>
  <circle cx="200" cy="60" r="6" fill="#78716c"/><text x="200" y="82" text-anchor="middle" font-size="8">Sametime</text>
  <circle cx="320" cy="60" r="6" fill="#78716c"/><text x="320" y="82" text-anchor="middle" font-size="8">Lync/SfB</text>
  <circle cx="440" cy="60" r="6" fill="#f59e0b"/><text x="440" y="82" text-anchor="middle" font-size="8">eXpress</text>
  <circle cx="560" cy="60" r="6" fill="#22c55e"/><text x="560" y="82" text-anchor="middle" font-size="8">Korus</text>
  <circle cx="640" cy="60" r="6" fill="#6366f1"/><text x="640" y="82" text-anchor="middle" font-size="8">VK/Пачка SaaS</text>
  <text x="360" y="110" text-anchor="middle" font-size="9" fill="#6b7280">Legacy — справочно и для миграции; не в production TCO-матрице</text>
</svg><figcaption class="fig-cap">Рис. Legacy-платформы vs современные якоря Korus.</figcaption></figure>"""


def render_fig_legacy_infra_svg() -> str:
    series = []
    for ru, _label, ha_y, _single_y in LEGACY_INFRA_ANCHORS:
        if ru not in (10_000, 100_000):
            continue
        korus = next(a.infra_yearly for a in KORUS_ANCHORS if a.ru == ru)
        series.append((f"XMPP HA @{ru // 1000}k", ha_y, "#78716c"))
        series.append((f"Korus @{ru // 1000}k", korus, "#86efac"))
    return _bar_chart_svg(
        "Infra ₽/год: legacy XMPP (HA) vs Korus @10k / @100k",
        series,
        caption="Только OPEX infra. Legacy без compliance-стека; OSS license = 0.",
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
            share = f" ({round(100 * c.license_yearly / total)}% lic)"
        rows.append(
            f"<tr><td>{escape(c.name)}</td>"
            f'<td class="money">{lic_s}</td><td class="money">{infra_s}</td>'
            f'<td class="money"><b>{total_s}</b>{share}</td></tr>'
        )
    return (
        f"<h3>Якорь {escape(anchor.code)} — {anchor.ru:,} RU".replace(",", " ")
        + f" · {escape(anchor.profile)}</h3>"
        + "<table><tr><th>Решение</th><th>Лицензия ₽/год</th><th>Infra ₽/год</th><th>Итого TCO</th></tr>"
        + "".join(rows)
        + "</table>"
    )


def render_comparison_matrix_html() -> str:
    return "".join(_matrix_block(a) for a in KORUS_ANCHORS)


def render_pilot_footnote_html() -> str:
    p = PILOT_TRIAL
    return f"""
<div class="warn">
  <div class="req">Пробник (Pilot) — вне production-матрицы</div>
  <div class="comment">
    Pilot @10k: infra <span class="money">{fmt_rub(p.infra_yearly)}/год</span> (~{p.infra_per_user_month:.1f} ₽/user/мес),
    RAM {p.ram_gb}, без Solr. Для оценки функционала, не для TCO-battle с eXpress Corporate.
    Production-сравнение начинается с <b>Standard S-10k</b> (infra <span class="money">{fmt_rub(KORUS_ANCHORS[0].infra_yearly)}/год</span>, полный функционал).
  </div>
</div>"""


def render_pros_cons_html() -> str:
    parts = []
    for name, (pros, cons) in PROS_CONS.items():
        pl = "".join(f"<li>{escape(p)}</li>" for p in pros)
        cl = "".join(f"<li>{escape(c)}</li>" for c in cons)
        parts.append(
            f'<div class="case"><h4>{escape(name)}</h4>'
            f"<p><b>Плюсы:</b></p><ul>{pl}</ul>"
            f"<p><b>Минусы:</b></p><ul>{cl}</ul></div>"
        )
    return "".join(parts)


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
  <li><b>IBM/HCL Sametime:</b> HCL product docs (legacy enterprise)</li>
  <li><b>Microsoft:</b> Skype for Business → Teams migration guides</li>
  <li><b>Korus:</b> внутренний sizing Standard/Enterprise + ставки infra {date}</li>
</ul>
""".format(date=PRICE_AS_OF)
