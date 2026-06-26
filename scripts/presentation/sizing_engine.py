"""RU-based infra sizing — production full stack via module_sizing."""

from __future__ import annotations

import math
from dataclasses import dataclass

from scripts.presentation import module_sizing as ms

PRICE_AS_OF = "2026-06-26"
PRICE_VAT = "Без НДС"

# Якоря RAM (log interp) — для headroom badge и RAM-bar, согласованы с load-моделью
RAM_ANCHORS: tuple[tuple[int, int], ...] = (
    (1_000, 32),
    (10_000, 64),
    (100_000, 160),
    (500_000, 480),
    (1_000_000, 960),
)

VM_RAM_TIERS: tuple[int, ...] = (8, 16, 32, 64, 128, 256, 512, 1024)

FTE_RATE_RUB_PER_MONTH = 180_000


@dataclass(frozen=True)
class ProviderRate:
    id: str
    label: str
    pricing_url: str
    source_note: str
    rub_per_gb_ram_month: int
    rub_per_vcpu_month: int
    rub_per_tb_ssd_month: int
    rub_per_tb_hdd_month: int
    rub_channel_200_mbps: int
    rub_channel_1gbps: int
    rub_ops_base_month: int


@dataclass(frozen=True)
class ResourceEstimate:
    registered_users: int
    ram_gb_raw: int
    ram_gb_billed: int
    vcpu: int
    app_nodes: int
    web_nodes: int
    ssd_tb: float
    hdd_tb: float
    channel_mbps: int
    fulltext_search: str  # prod full → solr
    peak_online: int = 0
    peak_msg_s: float = 0.0


@dataclass(frozen=True)
class QuoteLine:
    label: str
    amount_rub_month: int
    detail: str
    source_url: str


@dataclass(frozen=True)
class ProviderQuote:
    provider_id: str
    provider_label: str
    pricing_url: str
    monthly_rub: int
    yearly_rub: int
    lines: tuple[QuoteLine, ...]
    estimate: ResourceEstimate


PROVIDERS: tuple[ProviderRate, ...] = (
    ProviderRate(
        "regru",
        "REG.RU",
        "https://www.reg.ru/vps/tariffs",
        "VPS/VDS: тарифы «Эконом» и «SSD» на reg.ru/vps/tariffs, снимок 2026-06-26",
        rub_per_gb_ram_month=1_750,
        rub_per_vcpu_month=2_200,
        rub_per_tb_ssd_month=3_500,
        rub_per_tb_hdd_month=800,
        rub_channel_200_mbps=8_000,
        rub_channel_1gbps=35_000,
        rub_ops_base_month=5_000,
    ),
    ProviderRate(
        "yandex",
        "Yandex Cloud",
        "https://cloud.yandex.ru/docs/compute/pricing",
        "Compute: standard-v3, RAM и vCPU по прайсу cloud.yandex.ru, снимок 2026-06-26",
        rub_per_gb_ram_month=720,
        rub_per_vcpu_month=2_040,
        rub_per_tb_ssd_month=4_200,
        rub_per_tb_hdd_month=1_100,
        rub_channel_200_mbps=9_500,
        rub_channel_1gbps=38_000,
        rub_ops_base_month=6_500,
    ),
    ProviderRate(
        "timeweb",
        "Timeweb Cloud",
        "https://timeweb.cloud/prices",
        "Облачные серверы timeweb.cloud/prices — бюджетный сегмент, снимок 2026-06-26",
        rub_per_gb_ram_month=1_120,
        rub_per_vcpu_month=1_680,
        rub_per_tb_ssd_month=2_900,
        rub_per_tb_hdd_month=650,
        rub_channel_200_mbps=6_500,
        rub_channel_1gbps=28_000,
        rub_ops_base_month=4_200,
    ),
)

_PROVIDER_MAP = {p.id: p for p in PROVIDERS}


def fmt_rub(amount: int) -> str:
    return f"{amount:,}".replace(",", " ") + " ₽"


def _log_interp(ru: int, r0: int, g0: int, r1: int, g1: int) -> int:
    if ru <= r0:
        return g0
    if ru >= r1:
        return g1
    t = (math.log(ru) - math.log(r0)) / (math.log(r1) - math.log(r0))
    return max(g0, round(g0 + t * (g1 - g0)))


def estimate_ram_gb(ru: int) -> int:
    """Якорная оценка суммарной RAM (prod full) для headroom / charts."""
    if ru <= RAM_ANCHORS[0][0]:
        return RAM_ANCHORS[0][1]
    for i in range(len(RAM_ANCHORS) - 1):
        r0, g0 = RAM_ANCHORS[i]
        r1, g1 = RAM_ANCHORS[i + 1]
        if ru <= r1:
            return _log_interp(ru, r0, g0, r1, g1)
    return RAM_ANCHORS[-1][1]


def round_vm_tier(ram_gb: int) -> int:
    for tier in VM_RAM_TIERS:
        if ram_gb <= tier:
            return tier
    max_tier = VM_RAM_TIERS[-1]
    return math.ceil(ram_gb / max_tier) * max_tier


def _load_to_resource(le: ms.LoadEstimate) -> ResourceEstimate:
    raw = le.total_ram_gb
    return ResourceEstimate(
        registered_users=le.inputs.registered_users,
        ram_gb_raw=raw,
        ram_gb_billed=round_vm_tier(raw),
        vcpu=le.total_vcpu,
        app_nodes=le.app_nodes,
        web_nodes=le.web_nodes,
        ssd_tb=le.ssd_tb,
        hdd_tb=le.hdd_tb,
        channel_mbps=le.channel_mbps,
        fulltext_search="solr",
        peak_online=le.peak_online,
        peak_msg_s=le.peak_msg_s,
    )


def estimate_resources(
    ru: int,
    *,
    peak_online: int | None = None,
    peak_msg_s: float | None = None,
    msgs_per_user_day: float = ms.DEFAULT_MSGS_PER_USER_DAY,
    gb_files_per_user_yr: float = ms.DEFAULT_GB_FILES_PER_USER_YR,
    retention_years: int = ms.DEFAULT_RETENTION_YEARS,
    ha: bool = False,
    integration_plugins: int = 0,
    module_replicas: dict[str, int] | None = None,
    backup: str = "none",
) -> ResourceEstimate:
    inp = ms.LoadInputs(
        registered_users=ru,
        peak_online=peak_online,
        peak_msg_s=peak_msg_s,
        msgs_per_user_day=msgs_per_user_day,
        gb_files_per_user_yr=gb_files_per_user_yr,
        retention_years=retention_years,
        ha=ha,
        integration_plugins=integration_plugins,
        module_replicas=module_replicas or {},
        backup=backup,
    )
    return _load_to_resource(ms.estimate_from_load(inp))


def quote_provider(
    ru: int,
    provider_id: str,
    *,
    peak_online: int | None = None,
    peak_msg_s: float | None = None,
    ha: bool = False,
    integration_plugins: int = 0,
    retention_years: int = ms.DEFAULT_RETENTION_YEARS,
    gb_files_per_user_yr: float = ms.DEFAULT_GB_FILES_PER_USER_YR,
    msgs_per_user_day: float = ms.DEFAULT_MSGS_PER_USER_DAY,
) -> ProviderQuote:
    p = _PROVIDER_MAP[provider_id]
    e = estimate_resources(
        ru,
        peak_online=peak_online,
        peak_msg_s=peak_msg_s,
        ha=ha,
        integration_plugins=integration_plugins,
        retention_years=retention_years,
        gb_files_per_user_yr=gb_files_per_user_yr,
        msgs_per_user_day=msgs_per_user_day,
    )
    vm_compute = e.ram_gb_billed * p.rub_per_gb_ram_month + e.vcpu * p.rub_per_vcpu_month
    disk = round(e.ssd_tb * p.rub_per_tb_ssd_month + e.hdd_tb * p.rub_per_tb_hdd_month)
    channel = p.rub_channel_200_mbps if e.channel_mbps <= 200 else p.rub_channel_1gbps
    ops_mult = 1 if ru < 50_000 else 2
    ops = p.rub_ops_base_month * ops_mult
    lines = (
        QuoteLine(
            "Серверы (prod full)",
            vm_compute,
            f"{e.ram_gb_billed} ГБ RAM, {e.vcpu} vCPU · app×{e.app_nodes} web×{e.web_nodes}",
            p.pricing_url,
        ),
        QuoteLine(
            "Диски",
            disk,
            f"SSD {e.ssd_tb} ТБ + HDD {e.hdd_tb} ТБ",
            p.pricing_url,
        ),
        QuoteLine(
            "Канал",
            channel,
            f"{e.channel_mbps} Мбит/с (DIA, без TURN/VKS)",
            p.pricing_url,
        ),
        QuoteLine(
            "Backup и мониторинг",
            ops,
            "базовый ops-контур",
            p.pricing_url,
        ),
    )
    monthly = sum(ln.amount_rub_month for ln in lines)
    return ProviderQuote(
        provider_id=p.id,
        provider_label=p.label,
        pricing_url=p.pricing_url,
        monthly_rub=monthly,
        yearly_rub=monthly * 12,
        lines=lines,
        estimate=e,
    )


def quote_all_providers(ru: int, **kwargs: object) -> tuple[ProviderQuote, ...]:
    return tuple(quote_provider(ru, p.id, **kwargs) for p in PROVIDERS)


def median_monthly(ru: int, **kwargs: object) -> int:
    quotes = quote_all_providers(ru, **kwargs)
    vals = sorted(q.monthly_rub for q in quotes)
    mid = len(vals) // 2
    if len(vals) % 2:
        return vals[mid]
    return round((vals[mid - 1] + vals[mid]) / 2)


def median_yearly(ru: int, **kwargs: object) -> int:
    return median_monthly(ru, **kwargs) * 12


def load_summary(ru: int) -> str:
    po = ms.derive_peak_online(ru, None)
    ps = ms.derive_peak_msg_s(ru, None, ms.DEFAULT_MSGS_PER_USER_DAY, po)
    return f"пик онлайн ~{po:,} · пик msg/s ~{ps:.1f}".replace(",", " ")


def headroom_ru(at_ru: int) -> int | None:
    tier = estimate_resources(at_ru).ram_gb_billed
    lo, hi = at_ru, RAM_ANCHORS[-1][0]
    best = at_ru
    while lo <= hi:
        mid = (lo + hi) // 2
        if estimate_resources(mid).ram_gb_billed <= tier:
            best = mid
            lo = mid + 1
        else:
            hi = mid - 1
    return best if best > at_ru else None
