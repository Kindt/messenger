"""Support / сопровождение FTE model — all factors, shared by Python and deck JSON."""

from __future__ import annotations

from dataclasses import dataclass

from scripts.presentation import sizing_engine as se

FTE_ADD_ORGS = {"one": 0.0, "few": 0.15, "many": 0.35}
FTE_ADD_L1 = {"none": 0.0, "partial": 0.25, "full": 0.6}
FTE_ADD_TRAINING = {"none": 0.0, "annual": 0.1, "quarterly": 0.25}
FTE_ADD_E2EE = {"off": 0.0, "roadmap": 0.15, "prod": 0.4}
FTE_ADD_COMPLIANCE = {"none": 0.0, "dlp": 0.2, "fstec": 0.5}
FTE_ADD_DR = {"none": 0.0, "backup": 0.25, "full": 0.5}
FTE_ADD_TOPOLOGY = {"compact": 0.0, "cluster": 0.3}
FTE_ADD_INTEGRATIONS = {"none": 0.0, "few": 0.2, "many": 0.5}
MULT_TEAM = {"shared": 1.0, "dedicated": 1.25}
MULT_SUPPORT = {"business": 1.0, "24x7": 2.5}
RATE_MULT_STAFFING = {"inhouse": 1.0, "outsource": 1.15}
RATE_MULT_REGION = {"msk": 1.0, "region": 0.88}
OVERHEAD_PCT = 0.05  # НДС/командировки — упрощённая надбавка к ₽


@dataclass(frozen=True)
class SupportLine:
    label: str
    fte: float


@dataclass(frozen=True)
class SupportCostResult:
    registered_users: int
    lines: tuple[SupportLine, ...]
    fte_subtotal: float
    support_multiplier: float
    team_multiplier: float
    fte_after_mode: float
    rate_rub_month: int
    overhead_pct: float
    monthly_rub: int
    yearly_rub: int


def _updates_fte(registered_users: int, include: bool) -> float:
    if not include:
        return 0.0
    if registered_users < 5_000:
        return 0.1
    if registered_users < 50_000:
        return 0.3
    return 0.8


def _base_fte(registered_users: int) -> float:
    return min(0.15 + registered_users / 80_000, 4.0)


def compute_support(
    registered_users: int,
    *,
    support_mode: str = "business",
    include_updates: bool = True,
    topology: str = "compact",
    integrations: str = "few",
    team: str = "shared",
    orgs: str = "one",
    l1: str = "partial",
    training: str = "annual",
    e2ee: str = "roadmap",
    compliance: str = "none",
    dr: str = "none",
    staffing: str = "inhouse",
    region: str = "msk",
    include_overhead: bool = True,
) -> SupportCostResult:
    lines = [
        SupportLine("База (масштаб RU)", _base_fte(registered_users)),
        SupportLine("Обновления релизов", _updates_fte(registered_users, include_updates)),
        SupportLine("Топология", FTE_ADD_TOPOLOGY.get(topology, 0.0)),
        SupportLine("Интеграции", FTE_ADD_INTEGRATIONS.get(integrations, 0.0)),
        SupportLine("Организации / филиалы", FTE_ADD_ORGS.get(orgs, 0.0)),
        SupportLine("L1 (первая линия)", FTE_ADD_L1.get(l1, 0.0)),
        SupportLine("Обучение пользователей", FTE_ADD_TRAINING.get(training, 0.0)),
        SupportLine("E2EE / MLS", FTE_ADD_E2EE.get(e2ee, 0.0)),
        SupportLine("ИБ / комплаенс", FTE_ADD_COMPLIANCE.get(compliance, 0.0)),
        SupportLine("Резерв / DR", FTE_ADD_DR.get(dr, 0.0)),
    ]
    subtotal = sum(ln.fte for ln in lines)
    mode_mult = MULT_SUPPORT.get(support_mode, 1.0)
    team_mult = MULT_TEAM.get(team, 1.0)
    fte_total = subtotal * mode_mult * team_mult
    base_rate = se.FTE_RATE_RUB_PER_MONTH
    rate = round(
        base_rate
        * RATE_MULT_STAFFING.get(staffing, 1.0)
        * RATE_MULT_REGION.get(region, 1.0)
    )
    overhead = OVERHEAD_PCT if include_overhead else 0.0
    monthly = round(fte_total * rate * (1 + overhead))
    return SupportCostResult(
        registered_users=registered_users,
        lines=lines,
        fte_subtotal=round(subtotal, 2),
        support_multiplier=mode_mult,
        team_multiplier=team_mult,
        fte_after_mode=round(fte_total, 2),
        rate_rub_month=rate,
        overhead_pct=overhead,
        monthly_rub=monthly,
        yearly_rub=monthly * 12,
    )


def support_model_json() -> dict:
    """Constants for client-side calculator (must match compute_support)."""
    return {
        "fte_base": {"base": 0.15, "divisor": 80000, "cap": 4.0},
        "updates_fte": {"lt5k": 0.1, "lt50k": 0.3, "gte50k": 0.8},
        "topology_fte": FTE_ADD_TOPOLOGY,
        "integrations_fte": FTE_ADD_INTEGRATIONS,
        "orgs_fte": FTE_ADD_ORGS,
        "l1_fte": FTE_ADD_L1,
        "training_fte": FTE_ADD_TRAINING,
        "e2ee_fte": FTE_ADD_E2EE,
        "compliance_fte": FTE_ADD_COMPLIANCE,
        "dr_fte": FTE_ADD_DR,
        "team_mult": MULT_TEAM,
        "support_mode_mult": MULT_SUPPORT,
        "staffing_rate_mult": RATE_MULT_STAFFING,
        "region_rate_mult": RATE_MULT_REGION,
        "overhead_pct": OVERHEAD_PCT,
        "base_rate_rub": se.FTE_RATE_RUB_PER_MONTH,
    }
