"""Sizing production full stack: от нагрузки или от состава модулей.

Профили Pilot/Standard/Enterprise в deck не используются.
Для разработки: dev-min (QEMU) vs prod-full (docker-compose.full-server) — см. AGENTS.md.
"""

from __future__ import annotations

import math
from dataclasses import dataclass

# Базовые модули production full (docker-compose.full-server.yml + web + integrations)
@dataclass(frozen=True)
class ModuleSpec:
    id: str
    label: str
    ram_gb: float
    vcpu: float
    required: bool = True
    per_plugin: bool = False  # +1 экз. на каждый L1+ плагин


PRODUCTION_MODULES: tuple[ModuleSpec, ...] = (
    ModuleSpec("postgres-hot", "PostgreSQL (hot)", 4.0, 2.0),
    ModuleSpec("postgres-archive", "PostgreSQL (archive)", 4.0, 2.0),
    ModuleSpec("redis", "Redis", 2.0, 1.0),
    ModuleSpec("nats", "NATS JetStream", 4.0, 2.0),
    ModuleSpec("minio", "MinIO", 4.0, 2.0),
    ModuleSpec("zookeeper", "ZooKeeper", 2.0, 1.0),
    ModuleSpec("solr", "Solr", 8.0, 2.0),
    ModuleSpec("keycloak", "Keycloak", 8.0, 4.0),
    ModuleSpec("core-api", "core-api", 8.0, 4.0),
    ModuleSpec("ws-gateway", "ws-gateway", 4.0, 2.0),
    ModuleSpec("workers", "Workers (pipeline, retention, export…)", 8.0, 4.0),
    ModuleSpec("web-lb", "Web / nginx LB", 8.0, 2.0),
    ModuleSpec("integrations", "Integrations node", 8.0, 2.0, required=False, per_plugin=True),
)

DEFAULT_MSGS_PER_USER_DAY = 40.0
DEFAULT_GB_FILES_PER_USER_YR = 0.5
DEFAULT_RETENTION_YEARS = 3
MSG_BYTES = 2048
PEAK_MSG_BURST = 3.5


@dataclass(frozen=True)
class ModuleInstance:
    module_id: str
    label: str
    count: int
    ram_gb: float
    vcpu: float


@dataclass(frozen=True)
class LoadInputs:
    registered_users: int
    peak_online: int | None = None
    peak_msg_s: float | None = None
    msgs_per_user_day: float = DEFAULT_MSGS_PER_USER_DAY
    gb_files_per_user_yr: float = DEFAULT_GB_FILES_PER_USER_YR
    retention_years: int = DEFAULT_RETENTION_YEARS
    ha: bool = False
    integration_plugins: int = 0


@dataclass(frozen=True)
class LoadEstimate:
    inputs: LoadInputs
    peak_online: int
    peak_msg_s: float
    dau: int
    modules: tuple[ModuleInstance, ...]
    total_ram_gb: int
    total_vcpu: int
    ssd_tb: float
    hdd_tb: float
    channel_mbps: int
    app_nodes: int
    web_nodes: int


@dataclass(frozen=True)
class CapacityEstimate:
    enabled_module_ids: tuple[str, ...]
    integration_plugins: int
    total_ram_gb: int
    total_vcpu: int
    ssd_tb: float
    hdd_tb: float
    max_registered_users: int
    max_peak_online: int
    max_peak_msg_s: float
    storage_years_at_10k_ru: float


def _activity_rate(ru: int) -> float:
    if ru <= 10_000:
        return 0.50
    if ru <= 100_000:
        return 0.45
    if ru <= 500_000:
        return 0.35
    return 0.25


def _peak_online_rate(ru: int) -> float:
    if ru <= 10_000:
        return 0.15
    if ru <= 100_000:
        return 0.12
    return 0.10


def derive_peak_online(ru: int, peak_online: int | None) -> int:
    if peak_online is not None and peak_online > 0:
        return peak_online
    return max(1, round(ru * _activity_rate(ru) * _peak_online_rate(ru)))


def derive_peak_msg_s(
    ru: int,
    peak_msg_s: float | None,
    msgs_per_user_day: float,
    peak_online: int,
) -> float:
    if peak_msg_s is not None and peak_msg_s > 0:
        return peak_msg_s
    dau = max(1, round(ru * _activity_rate(ru)))
    msgs_day = dau * msgs_per_user_day
    avg = msgs_day / 86400.0
    return max(0.5, avg * PEAK_MSG_BURST)


def _app_node_count(peak_msg_s: float, peak_online: int) -> int:
    n = 1
    if peak_msg_s > 30 or peak_online > 3_000:
        n = 2
    if peak_msg_s > 120 or peak_online > 12_000:
        n = 3
    if peak_msg_s > 400:
        n = max(n, 6)
    return n


def _web_node_count(peak_online: int, ha: bool) -> int:
    n = 1 if peak_online <= 5_000 else 2
    if peak_online > 20_000:
        n = max(n, 3)
    if ha:
        n = max(n, 2)
    return n


def _scale_module_ram(spec: ModuleSpec, ru: int, peak_online: int, peak_msg_s: float) -> float:
    base = spec.ram_gb
    if spec.id == "postgres-hot":
        return base + ru / 8_000 + peak_msg_s / 8
    if spec.id == "core-api":
        return base + peak_online / 400 + peak_msg_s / 1.5
    if spec.id == "ws-gateway":
        return base + peak_online / 800
    if spec.id == "workers":
        return base + peak_msg_s / 2.5
    if spec.id == "nats":
        return base + peak_msg_s / 20
    if spec.id == "solr":
        return base + ru / 15_000
    if spec.id == "minio":
        return base + ru / 20_000
    return base


def _storage_tb(
    ru: int,
    msgs_per_user_day: float,
    gb_files_per_user_yr: float,
    retention_years: int,
) -> tuple[float, float]:
    dau = max(1, round(ru * _activity_rate(ru)))
    msgs_day = dau * msgs_per_user_day
    msg_gb_yr = msgs_day * 365 * MSG_BYTES / (1024**3)
    files_gb_yr = ru * gb_files_per_user_yr
    total_gb = (msg_gb_yr + files_gb_yr) * retention_years
    ssd_tb = round(0.5 + total_gb * 0.15 / 1024, 2)
    hdd_tb = round(max(2.0, total_gb / 1024), 2)
    return ssd_tb, hdd_tb


def _build_modules(
    ru: int,
    peak_online: int,
    peak_msg_s: float,
    ha: bool,
    integration_plugins: int,
    enabled_ids: set[str] | None,
) -> tuple[ModuleInstance, ...]:
    app_nodes = _app_node_count(peak_msg_s, peak_online)
    web_nodes = _web_node_count(peak_online, ha)
    if ha:
        app_nodes = max(app_nodes, 2)

    instances: list[ModuleInstance] = []
    for spec in PRODUCTION_MODULES:
        if enabled_ids is not None and spec.id not in enabled_ids:
            continue
        if spec.per_plugin:
            count = max(0, integration_plugins)
            if count == 0 and not spec.required:
                continue
        elif spec.id == "core-api":
            count = app_nodes
        elif spec.id == "web-lb":
            count = web_nodes
        elif spec.id in ("postgres-hot", "keycloak", "redis", "nats") and ha:
            count = 2
        else:
            count = 1

        ram = _scale_module_ram(spec, ru, peak_online, peak_msg_s)
        if ha and spec.id in ("postgres-hot", "nats", "redis"):
            ram *= 1.1
        vcpu = spec.vcpu * count
        instances.append(
            ModuleInstance(
                spec.id,
                spec.label,
                count,
                round(ram * count, 1),
                round(vcpu, 1),
            )
        )
    return tuple(instances)


def estimate_from_load(inp: LoadInputs, enabled_ids: set[str] | None = None) -> LoadEstimate:
    """Production full: RU + онлайн + msg/s + хранение → состав серверов."""
    ru = max(1, inp.registered_users)
    peak_online = derive_peak_online(ru, inp.peak_online)
    peak_msg_s = derive_peak_msg_s(ru, inp.peak_msg_s, inp.msgs_per_user_day, peak_online)
    dau = max(1, round(ru * _activity_rate(ru)))
    modules = _build_modules(
        ru, peak_online, peak_msg_s, inp.ha, inp.integration_plugins, enabled_ids
    )
    total_ram = math.ceil(sum(m.ram_gb for m in modules))
    total_vcpu = math.ceil(sum(m.vcpu for m in modules))
    ssd_tb, hdd_tb = _storage_tb(
        ru, inp.msgs_per_user_day, inp.gb_files_per_user_yr, inp.retention_years
    )
    channel = 200 if peak_online < 8_000 else 1000
    app_nodes = sum(m.count for m in modules if m.module_id == "core-api")
    web_nodes = sum(m.count for m in modules if m.module_id == "web-lb")
    return LoadEstimate(
        inputs=inp,
        peak_online=peak_online,
        peak_msg_s=round(peak_msg_s, 1),
        dau=dau,
        modules=modules,
        total_ram_gb=total_ram,
        total_vcpu=total_vcpu,
        ssd_tb=ssd_tb,
        hdd_tb=hdd_tb,
        channel_mbps=channel,
        app_nodes=app_nodes,
        web_nodes=web_nodes,
    )


def estimate_capacity(
    enabled_module_ids: tuple[str, ...],
    integration_plugins: int = 0,
    ssd_tb: float = 2.0,
    hdd_tb: float = 5.0,
    ha: bool = False,
) -> CapacityEstimate:
    """Обратный расчёт: фиксированный состав модулей (без auto-scale) → предел нагрузки."""
    enabled = set(enabled_module_ids) if enabled_module_ids else {m.id for m in PRODUCTION_MODULES if m.required}
    ha_ids = {"postgres-hot", "redis", "nats", "keycloak"}

    instances: list[ModuleInstance] = []
    spec_map = {m.id: m for m in PRODUCTION_MODULES}
    for mid in enabled:
        spec = spec_map.get(mid)
        if not spec:
            continue
        if spec.per_plugin:
            count = max(0, integration_plugins)
            if count == 0:
                continue
        else:
            count = 2 if ha and mid in ha_ids else 1
        instances.append(
            ModuleInstance(mid, spec.label, count, round(spec.ram_gb * count, 1), round(spec.vcpu * count, 1))
        )

    total_ram = math.ceil(sum(m.ram_gb for m in instances))
    total_vcpu = math.ceil(sum(m.vcpu for m in instances))

    max_peak_online = 100_000
    max_peak_msg_s = 10_000.0
    max_ru = 1_000_000

    if "ws-gateway" in enabled:
        ws = spec_map["ws-gateway"].ram_gb
        max_peak_online = min(max_peak_online, max(100, int((ws - 4) * 800)))
    if "core-api" in enabled:
        cap = spec_map["core-api"].ram_gb
        max_peak_online = min(max_peak_online, max(100, int((cap - 8) * 400)))
        max_peak_msg_s = min(max_peak_msg_s, max(1.0, (cap - 8) * 1.5))
    if "workers" in enabled:
        w = spec_map["workers"].ram_gb
        max_peak_msg_s = min(max_peak_msg_s, max(1.0, (w - 8) * 2.5))
    if "postgres-hot" in enabled:
        pg = spec_map["postgres-hot"].ram_gb
        max_ru = min(max_ru, max(1_000, int((pg - 4) * 8_000)))

    _, need_hdd_10k = _storage_tb(
        10_000,
        DEFAULT_MSGS_PER_USER_DAY,
        DEFAULT_GB_FILES_PER_USER_YR,
        DEFAULT_RETENTION_YEARS,
    )
    storage_years = round((hdd_tb / need_hdd_10k * DEFAULT_RETENTION_YEARS) if need_hdd_10k > 0 else 0.0, 1)

    return CapacityEstimate(
        enabled_module_ids=tuple(sorted(enabled)),
        integration_plugins=integration_plugins,
        total_ram_gb=total_ram,
        total_vcpu=total_vcpu,
        ssd_tb=ssd_tb,
        hdd_tb=hdd_tb,
        max_registered_users=max_ru,
        max_peak_online=max_peak_online,
        max_peak_msg_s=round(max_peak_msg_s, 1),
        storage_years_at_10k_ru=storage_years,
    )


def modules_catalog_json() -> list[dict[str, object]]:
    return [
        {
            "id": m.id,
            "label": m.label,
            "ram_gb": m.ram_gb,
            "vcpu": m.vcpu,
            "required": m.required,
            "per_plugin": m.per_plugin,
        }
        for m in PRODUCTION_MODULES
    ]
