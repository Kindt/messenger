"""Sizing production full stack: от нагрузки или от состава модулей.

Профили Pilot/Standard/Enterprise в deck не используются.
Для разработки: dev-min (QEMU) vs prod-full (docker-compose.full-server) — см. AGENTS.md.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

# replica_mode: active — каждая реплика добавляет пропускную способность;
# passive — зеркало/standby, RAM×N, нагрузка как у одного primary;
# cluster — кворум (Redis/NATS/ZK), RAM×N, нагрузка ~как у одного узла.
@dataclass(frozen=True)
class ModuleSpec:
    id: str
    label: str
    ram_gb: float
    vcpu: float
    required: bool = True
    default_enabled: bool = True  # для optional: включён в baseline prod full
    requires: tuple[str, ...] = ()  # id модулей, без которых эта строка не имеет смысла
    per_plugin: bool = False
    max_replicas: int = 1
    replica_mode: str = "passive"  # active | passive | cluster


# required — без модуля prod не стартует (compose base, без profile).
# optional + default_enabled — часть baseline prod full (--profile full), но можно снять в sizing.
PRODUCTION_MODULES: tuple[ModuleSpec, ...] = (
    ModuleSpec("postgres-hot", "PostgreSQL (hot)", 4.0, 2.0, max_replicas=2, replica_mode="passive"),
    ModuleSpec(
        "postgres-archive",
        "PostgreSQL (archive)",
        4.0,
        2.0,
        required=False,
        default_enabled=True,
        max_replicas=2,
        replica_mode="passive",
    ),
    ModuleSpec("redis", "Redis", 2.0, 1.0, max_replicas=3, replica_mode="cluster"),
    ModuleSpec("nats", "NATS JetStream", 4.0, 2.0, max_replicas=3, replica_mode="cluster"),
    ModuleSpec("minio", "MinIO", 4.0, 2.0, max_replicas=4, replica_mode="cluster"),
    ModuleSpec(
        "zookeeper",
        "ZooKeeper",
        2.0,
        1.0,
        required=False,
        default_enabled=True,
        requires=("solr",),
        max_replicas=3,
        replica_mode="cluster",
    ),
    ModuleSpec(
        "solr",
        "Solr",
        8.0,
        2.0,
        required=False,
        default_enabled=True,
        max_replicas=3,
        replica_mode="cluster",
    ),
    ModuleSpec("keycloak", "Keycloak", 8.0, 4.0, max_replicas=2, replica_mode="passive"),
    ModuleSpec("core-api", "core-api", 8.0, 4.0, max_replicas=6, replica_mode="active"),
    ModuleSpec("ws-gateway", "ws-gateway", 4.0, 2.0, max_replicas=4, replica_mode="active"),
    ModuleSpec("workers", "Workers (pipeline, retention…)", 8.0, 4.0, max_replicas=4, replica_mode="active"),
    ModuleSpec("web-lb", "Web / nginx LB", 8.0, 2.0, max_replicas=4, replica_mode="active"),
    ModuleSpec(
        "integrations",
        "Integrations node",
        8.0,
        2.0,
        required=False,
        default_enabled=False,
        per_plugin=True,
        max_replicas=8,
        replica_mode="active",
    ),
)

BACKUP_PROFILES: dict[str, tuple[float, float, float, str]] = {
    # disk_mult, ram_gb_extra, ops_mult, label
    "none": (1.0, 0.0, 1.0, "Без отдельного бэка-контура"),
    "standard": (1.35, 2.0, 1.4, "Снапшоты + daily offsite"),
    "dr": (2.0, 8.0, 2.0, "DR: второй ЦОД + регулярные учения"),
}

DEFAULT_MSGS_PER_USER_DAY = 40.0
DEFAULT_GB_FILES_PER_USER_YR = 0.5
DEFAULT_RETENTION_YEARS = 3
MSG_BYTES = 2048
PEAK_MSG_BURST = 3.5

SPEC_MAP = {m.id: m for m in PRODUCTION_MODULES}


def prod_full_default_enabled() -> set[str]:
    return {m.id for m in PRODUCTION_MODULES if m.required or m.default_enabled}


def normalize_enabled(enabled: set[str]) -> set[str]:
    """Ядро prod + зависимости (ZooKeeper только с Solr)."""
    out = set(enabled) | {m.id for m in PRODUCTION_MODULES if m.required}
    if "solr" not in out:
        out.discard("zookeeper")
    for spec in PRODUCTION_MODULES:
        if spec.id in out and spec.requires and not all(r in out for r in spec.requires):
            out.discard(spec.id)
    return out


@dataclass(frozen=True)
class ModuleInstance:
    module_id: str
    label: str
    count: int
    ram_gb: float
    vcpu: float
    replica_mode: str = "passive"


@dataclass(frozen=True)
class LoadInputs:
    registered_users: int
    peak_online: int | None = None
    peak_msg_s: float | None = None
    msgs_per_user_day: float = DEFAULT_MSGS_PER_USER_DAY
    gb_files_per_user_yr: float = DEFAULT_GB_FILES_PER_USER_YR
    retention_years: int = DEFAULT_RETENTION_YEARS
    ha: bool = False  # legacy shortcut → реплики datastore
    integration_plugins: int = 0
    module_replicas: dict[str, int] = field(default_factory=dict)
    backup: str = "none"


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
    backup_ram_gb: float
    backup_disk_mult: float


@dataclass(frozen=True)
class CapacityEstimate:
    enabled_module_ids: tuple[str, ...]
    module_replicas: dict[str, int]
    integration_plugins: int
    backup: str
    total_ram_gb: int
    total_vcpu: int
    ssd_tb: float
    hdd_tb: float
    max_registered_users: int
    max_peak_online: int
    max_peak_msg_s: float
    storage_years_at_10k_ru: float
    backup_ram_gb: float
    bottleneck: str = ""  # ram | vcpu | mixed


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


def _resolve_replicas(
    spec: ModuleSpec,
    replicas: dict[str, int],
    *,
    ha: bool,
    app_nodes: int,
    web_nodes: int,
    plugins: int,
) -> int:
    if spec.per_plugin:
        return max(0, replicas.get(spec.id, plugins))
    if spec.id in replicas:
        return max(1, min(spec.max_replicas, replicas[spec.id]))
    if spec.id == "core-api":
        return max(1, min(spec.max_replicas, app_nodes))
    if spec.id == "web-lb":
        return max(1, min(spec.max_replicas, web_nodes))
    if spec.id == "workers":
        return max(1, min(spec.max_replicas, replicas.get("workers", app_nodes)))
    if ha and spec.id in ("postgres-hot", "postgres-archive", "redis", "nats", "keycloak"):
        return min(spec.max_replicas, 2 if spec.max_replicas >= 2 else 1)
    if spec.id == "zookeeper" and replicas.get("solr", 1) >= 3:
        return 3
    if spec.id == "solr" and replicas.get("solr", 1) >= 3:
        return 3
    return 1


def _app_node_count(peak_msg_s: float, peak_online: int, ha: bool) -> int:
    n = 1
    if peak_msg_s > 30 or peak_online > 3_000:
        n = 2
    if peak_msg_s > 120 or peak_online > 12_000:
        n = 3
    if peak_msg_s > 400:
        n = max(n, 6)
    if ha:
        n = max(n, 2)
    return min(n, 6)


def _web_node_count(peak_online: int, ha: bool) -> int:
    n = 1 if peak_online <= 5_000 else 2
    if peak_online > 20_000:
        n = max(n, 3)
    if ha:
        n = max(n, 2)
    return min(n, 4)


def _scale_module_vcpu(spec: ModuleSpec, ru: int, peak_online: int, peak_msg_s: float) -> float:
    """vCPU растёт с нагрузкой на CPU-bound модулях (prod full / docker limits)."""
    base = spec.vcpu
    if spec.id == "core-api":
        return max(base, 2.0 + peak_msg_s / 12.0 + peak_online / 2_500.0)
    if spec.id == "workers":
        return max(base, 2.0 + peak_msg_s / 18.0)
    if spec.id == "ws-gateway":
        return max(base, 1.0 + peak_online / 2_000.0)
    if spec.id == "nats":
        return max(base, 1.0 + peak_msg_s / 30.0)
    if spec.id == "postgres-hot":
        return max(base, 1.0 + peak_msg_s / 25.0 + ru / 12_000.0)
    if spec.id == "solr":
        return max(base, 1.0 + ru / 20_000.0)
    return base


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
    backup: str,
) -> tuple[float, float, float]:
    dau = max(1, round(ru * _activity_rate(ru)))
    msgs_day = dau * msgs_per_user_day
    msg_gb_yr = msgs_day * 365 * MSG_BYTES / (1024**3)
    files_gb_yr = ru * gb_files_per_user_yr
    total_gb = (msg_gb_yr + files_gb_yr) * retention_years
    disk_mult, _, _, _ = BACKUP_PROFILES.get(backup, BACKUP_PROFILES["none"])
    ssd_tb = round((0.5 + total_gb * 0.15 / 1024) * disk_mult, 2)
    hdd_tb = round(max(2.0, total_gb / 1024) * disk_mult, 2)
    return ssd_tb, hdd_tb, disk_mult


def _build_modules(
    ru: int,
    peak_online: int,
    peak_msg_s: float,
    inp: LoadInputs,
    enabled_ids: set[str] | None,
) -> tuple[ModuleInstance, ...]:
    app_nodes = _app_node_count(peak_msg_s, peak_online, inp.ha)
    web_nodes = _web_node_count(peak_online, inp.ha)
    replicas = dict(inp.module_replicas)

    instances: list[ModuleInstance] = []
    for spec in PRODUCTION_MODULES:
        if enabled_ids is not None and spec.id not in enabled_ids:
            continue
        count = _resolve_replicas(
            spec, replicas, ha=inp.ha, app_nodes=app_nodes, web_nodes=web_nodes, plugins=inp.integration_plugins
        )
        if spec.per_plugin and count == 0:
            continue

        ram_unit = _scale_module_ram(spec, ru, peak_online, peak_msg_s)
        vcpu_unit = _scale_module_vcpu(spec, ru, peak_online, peak_msg_s)
        if spec.replica_mode == "cluster" and count > 1:
            ram_unit *= 1.05  # overhead кворума
        ram_total = round(ram_unit * count, 1)
        vcpu_total = round(vcpu_unit * count, 1)
        instances.append(
            ModuleInstance(spec.id, spec.label, count, ram_total, vcpu_total, spec.replica_mode)
        )
    return tuple(instances)


def _backup_overhead(backup: str) -> tuple[float, float, float]:
    return BACKUP_PROFILES.get(backup, BACKUP_PROFILES["none"])


def estimate_from_load(inp: LoadInputs, enabled_ids: set[str] | None = None) -> LoadEstimate:
    ru = max(1, inp.registered_users)
    peak_online = derive_peak_online(ru, inp.peak_online)
    peak_msg_s = derive_peak_msg_s(ru, inp.peak_msg_s, inp.msgs_per_user_day, peak_online)
    dau = max(1, round(ru * _activity_rate(ru)))
    effective = normalize_enabled(enabled_ids if enabled_ids is not None else prod_full_default_enabled())
    modules = _build_modules(ru, peak_online, peak_msg_s, inp, effective)
    _, backup_ram, _, _ = _backup_overhead(inp.backup)
    total_ram = math.ceil(sum(m.ram_gb for m in modules) + backup_ram)
    total_vcpu = math.ceil(sum(m.vcpu for m in modules))
    ssd_tb, hdd_tb, disk_mult = _storage_tb(
        ru, inp.msgs_per_user_day, inp.gb_files_per_user_yr, inp.retention_years, inp.backup
    )
    channel = 200 if peak_online < 8_000 else 1000
    if inp.backup == "dr":
        channel = max(channel, 400)
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
        backup_ram_gb=backup_ram,
        backup_disk_mult=disk_mult,
    )


def _capacity_from_ram(spec: ModuleSpec, count: int, ram_unit: float) -> tuple[int, float, int]:
    mult = count if spec.replica_mode == "active" else 1
    if spec.id == "ws-gateway":
        return max(100, int((ram_unit - 4) * 800)) * mult, 0.0, 0
    if spec.id == "core-api":
        po = max(100, int((ram_unit - 8) * 400)) * mult
        pms = max(1.0, (ram_unit - 8) * 1.5) * mult
        return po, pms, 0
    if spec.id == "workers":
        return 0, max(1.0, (ram_unit - 8) * 2.5) * mult, 0
    if spec.id == "web-lb":
        return max(100, int((ram_unit - 4) * 500)) * mult, 0.0, 0
    if spec.id == "postgres-hot":
        return 0, 0.0, max(1_000, int((ram_unit - 4) * 8_000))
    return 0, 0.0, 0


def _capacity_from_vcpu(spec: ModuleSpec, count: int, vcpu_unit: float) -> tuple[int, float, int]:
    mult = count if spec.replica_mode == "active" else 1
    if spec.id == "ws-gateway":
        return max(100, int((vcpu_unit - 1) * 400)) * mult, 0.0, 0
    if spec.id == "core-api":
        po = max(100, int((vcpu_unit - 1) * 250)) * mult
        pms = max(1.0, (vcpu_unit - 1) * 12.0) * mult
        return po, pms, 0
    if spec.id == "workers":
        return 0, max(1.0, (vcpu_unit - 1) * 15.0) * mult, 0
    if spec.id == "web-lb":
        return max(100, int((vcpu_unit - 1) * 350)) * mult, 0.0, 0
    if spec.id == "postgres-hot":
        return 0, 0.0, max(1_000, int((vcpu_unit - 1) * 10_000))
    if spec.id == "nats":
        return 0, max(1.0, (vcpu_unit - 1) * 20.0) * mult, 0
    return 0, 0.0, 0


def _capacity_from_module(spec: ModuleSpec, count: int, ram_unit: float | None = None, vcpu_unit: float | None = None) -> tuple[int, float, int, str]:
    """Предел нагрузки: min(RAM, vCPU) по модулю."""
    ram = ram_unit if ram_unit is not None else spec.ram_gb
    vcpu = vcpu_unit if vcpu_unit is not None else spec.vcpu
    po_r, pms_r, ru_r = _capacity_from_ram(spec, count, ram)
    po_c, pms_c, ru_c = _capacity_from_vcpu(spec, count, vcpu)
    po = po_r
    if po_c and (not po_r or po_c < po_r):
        po = po_c
    pms = pms_r
    if pms_c and (not pms_r or pms_c < pms_r):
        pms = pms_c
    ru = ru_r
    if ru_c and (not ru_r or ru_c < ru_r):
        ru = ru_c
    bind = "mixed"
    if po_r and po_c and po_c < po_r:
        bind = "vcpu"
    elif pms_r and pms_c and pms_c < pms_r:
        bind = "vcpu"
    elif ru_r and ru_c and ru_c < ru_r:
        bind = "vcpu"
    elif po_r or pms_r or ru_r:
        bind = "ram"
    return po, pms, ru, bind


def estimate_capacity(
    enabled_module_ids: tuple[str, ...],
    integration_plugins: int = 0,
    ssd_tb: float = 2.0,
    hdd_tb: float = 5.0,
    ha: bool = False,
    module_replicas: dict[str, int] | None = None,
    backup: str = "none",
) -> CapacityEstimate:
    enabled = normalize_enabled(set(enabled_module_ids) if enabled_module_ids else prod_full_default_enabled())
    replicas = dict(module_replicas or {})

    instances: list[ModuleInstance] = []
    max_peak_online = 100_000
    max_peak_msg_s = 10_000.0
    max_ru = 1_000_000
    bottlenecks: list[str] = []

    for spec in PRODUCTION_MODULES:
        if spec.id not in enabled:
            continue
        count = _resolve_replicas(
            spec, replicas, ha=ha, app_nodes=1, web_nodes=1, plugins=integration_plugins
        )
        if spec.per_plugin and count == 0:
            continue
        ram_unit = spec.ram_gb
        vcpu_unit = spec.vcpu
        if spec.replica_mode == "cluster" and count > 1:
            ram_unit *= 1.05
        ram_total = round(ram_unit * count, 1)
        vcpu_total = round(vcpu_unit * count, 1)
        instances.append(
            ModuleInstance(spec.id, spec.label, count, ram_total, vcpu_total, spec.replica_mode)
        )
        po, pms, ru_cap, bind = _capacity_from_module(spec, count, ram_unit, vcpu_unit)
        if po:
            max_peak_online = min(max_peak_online, po)
        if pms:
            max_peak_msg_s = min(max_peak_msg_s, pms)
        if ru_cap:
            max_ru = min(max_ru, int(ru_cap))
        if bind == "vcpu":
            bottlenecks.append(spec.label)

    _, backup_ram, _, _ = _backup_overhead(backup)
    total_ram = math.ceil(sum(m.ram_gb for m in instances) + backup_ram)
    total_vcpu = math.ceil(sum(m.vcpu for m in instances))
    _, _, disk_mult, _ = BACKUP_PROFILES.get(backup, BACKUP_PROFILES["none"])
    eff_hdd = hdd_tb / disk_mult if disk_mult else hdd_tb

    _, need_hdd_10k, _ = _storage_tb(
        10_000, DEFAULT_MSGS_PER_USER_DAY, DEFAULT_GB_FILES_PER_USER_YR, DEFAULT_RETENTION_YEARS, "none"
    )
    storage_years = round((eff_hdd / need_hdd_10k * DEFAULT_RETENTION_YEARS) if need_hdd_10k > 0 else 0.0, 1)
    bn = ", ".join(bottlenecks[:3]) if bottlenecks else "RAM"

    return CapacityEstimate(
        enabled_module_ids=tuple(sorted(enabled)),
        module_replicas={k: v for k, v in replicas.items()},
        integration_plugins=integration_plugins,
        backup=backup,
        total_ram_gb=total_ram,
        total_vcpu=total_vcpu,
        ssd_tb=round(ssd_tb * disk_mult, 2),
        hdd_tb=round(hdd_tb, 2),
        max_registered_users=max_ru,
        max_peak_online=max_peak_online,
        max_peak_msg_s=round(max_peak_msg_s, 1),
        storage_years_at_10k_ru=storage_years,
        backup_ram_gb=backup_ram,
        bottleneck=f"vCPU ({bn})" if bottlenecks else "RAM",
    )


def modules_catalog_json() -> list[dict[str, object]]:
    return [
        {
            "id": m.id,
            "label": m.label,
            "ram_gb": m.ram_gb,
            "vcpu": m.vcpu,
            "required": m.required,
            "default_enabled": m.default_enabled,
            "requires": list(m.requires),
            "per_plugin": m.per_plugin,
            "max_replicas": m.max_replicas,
            "replica_mode": m.replica_mode,
        }
        for m in PRODUCTION_MODULES
    ]
