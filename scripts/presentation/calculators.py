"""Pure Python calculators — RU-based sizing, multi-provider infra, support FTE."""

from __future__ import annotations

from dataclasses import dataclass

from scripts.presentation import sizing_engine as se
from scripts.presentation import sizing_pricing as sp


@dataclass(frozen=True)
class BreakdownLine:
    label: str
    amount_rub_month: int
    source_url: str = ""


@dataclass(frozen=True)
class ProviderInfraResult:
    provider_id: str
    provider_label: str
    pricing_url: str
    monthly_rub: int
    yearly_rub: int
    breakdown: tuple[BreakdownLine, ...]


@dataclass(frozen=True)
class SalesTcoResult:
    registered_users: int
    ram_gb_raw: int
    ram_gb_billed: int
    vcpu: int
    app_nodes: int
    monthly_rub_median: int
    yearly_rub_median: int
    per_user_monthly_rub: float
    providers: tuple[ProviderInfraResult, ...]
    breakdown_median: tuple[BreakdownLine, ...]


@dataclass(frozen=True)
class TechCapacityResult:
    registered_users: int
    ram_gb_raw: int
    ram_gb_billed: int
    vcpu: int
    app_nodes: int
    web_nodes: int
    headroom_ru: int | None
    fulltext_search: str
    channel_mbps: int
    providers: tuple[ProviderInfraResult, ...]


from scripts.presentation import support_model as sm

SupportCostResult = sm.SupportCostResult


@dataclass(frozen=True)
class UserScenarioResult:
    scenario_id: str
    title: str
    steps: tuple[str, ...]


def _provider_results(ru: int, **kwargs: object) -> tuple[ProviderInfraResult, ...]:
    out = []
    for q in se.quote_all_providers(ru, **kwargs):
        out.append(
            ProviderInfraResult(
                provider_id=q.provider_id,
                provider_label=q.provider_label,
                pricing_url=q.pricing_url,
                monthly_rub=q.monthly_rub,
                yearly_rub=q.yearly_rub,
                breakdown=tuple(
                    BreakdownLine(ln.label, ln.amount_rub_month, ln.source_url) for ln in q.lines
                ),
            )
        )
    return tuple(out)


def sales_tco(
    registered_users: int,
    profile: str = "auto",
    deployment: str = "on_prem",
) -> SalesTcoResult:
    del profile, deployment
    est = se.estimate_resources(registered_users)
    providers = _provider_results(registered_users)
    monthly = se.median_monthly(registered_users)
    breakdown = tuple(
        BreakdownLine(label, amt) for label, amt in sp.infra_breakdown_lines(registered_users)
    )
    per_user = monthly / registered_users if registered_users else 0.0
    return SalesTcoResult(
        registered_users=registered_users,
        ram_gb_raw=est.ram_gb_raw,
        ram_gb_billed=est.ram_gb_billed,
        vcpu=est.vcpu,
        app_nodes=est.app_nodes,
        monthly_rub_median=monthly,
        yearly_rub_median=monthly * 12,
        per_user_monthly_rub=round(per_user, 2),
        providers=providers,
        breakdown_median=breakdown,
    )


def tech_capacity(
    registered_users: int,
    peak_online: int | None = None,
    peak_msg_s: float | None = None,
    msgs_per_user_day: float = 40.0,
    gb_files_per_user_yr: float = 0.5,
    retention_years: int = 3,
    ha: bool = False,
    integration_plugins: int = 0,
) -> TechCapacityResult:
    kw = dict(
        peak_online=peak_online,
        peak_msg_s=peak_msg_s,
        msgs_per_user_day=msgs_per_user_day,
        gb_files_per_user_yr=gb_files_per_user_yr,
        retention_years=retention_years,
        ha=ha,
        integration_plugins=integration_plugins,
    )
    est = se.estimate_resources(registered_users, **kw)
    hr = se.headroom_ru(registered_users)
    return TechCapacityResult(
        registered_users=registered_users,
        ram_gb_raw=est.ram_gb_raw,
        ram_gb_billed=est.ram_gb_billed,
        vcpu=est.vcpu,
        app_nodes=est.app_nodes,
        web_nodes=est.web_nodes,
        headroom_ru=hr,
        fulltext_search=est.fulltext_search,
        channel_mbps=est.channel_mbps,
        providers=_provider_results(registered_users, **kw),
    )


def support_cost(
    registered_users: int,
    sla: str = "business",
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
    return sm.compute_support(
        registered_users,
        support_mode=sla,
        include_updates=include_updates,
        topology=topology,
        integrations=integrations,
        team=team,
        orgs=orgs,
        l1=l1,
        training=training,
        e2ee=e2ee,
        compliance=compliance,
        dr=dr,
        staffing=staffing,
        region=region,
        include_overhead=include_overhead,
    )


_USER_SCENARIOS: dict[str, tuple[str, tuple[str, ...]]] = {
    "message": (
        "Написать коллеге",
        (
            "Откройте чат в браузере.",
            "Выберите коллегу или группу.",
            "Напишите сообщение и приложите файл при необходимости.",
        ),
    ),
    "search": (
        "Найти старый файл",
        (
            "Введите ключевые слова в поиск.",
            "Отфильтруйте по чату или дате.",
            "Откройте найденное вложение.",
        ),
    ),
    "call": (
        "Позвонить из чата",
        (
            "Откройте нужный чат.",
            "Нажмите кнопку звонка.",
            "Разрешите доступ к микрофону в браузере.",
        ),
    ),
}


def user_scenario(scenario_id: str) -> UserScenarioResult:
    title, steps = _USER_SCENARIOS[scenario_id]
    return UserScenarioResult(scenario_id=scenario_id, title=title, steps=steps)
