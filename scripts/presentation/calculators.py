"""Pure Python calculators for deck UI — arbitrary RU input."""

from __future__ import annotations

from dataclasses import dataclass

from scripts.presentation import sizing_pricing as sp


@dataclass(frozen=True)
class SalesTcoResult:
    registered_users: int
    profile_picked: str
    monthly_rub: int
    yearly_rub: int
    per_user_monthly: float


@dataclass(frozen=True)
class TechCapacityResult:
    registered_users: int
    profile_picked: str
    total_ram_gb: int
    headroom_ru: int
    nodes: int


@dataclass(frozen=True)
class SupportCostResult:
    registered_users: int
    fte_monthly: float
    monthly_rub: int
    sla: str


@dataclass(frozen=True)
class UserScenarioResult:
    scenario_id: str
    title: str
    steps: tuple[str, ...]


def sales_tco(
    registered_users: int,
    profile: str = "auto",
    deployment: str = "on_prem",
) -> SalesTcoResult:
    del deployment  # v1: on-prem infra only
    prof = sp.pick_profile(registered_users) if profile == "auto" else sp._PROFILE_MAP[profile]
    monthly = sp.infra_monthly(prof)
    yearly = monthly * 12
    per_user = monthly / registered_users if registered_users else 0.0
    return SalesTcoResult(
        registered_users=registered_users,
        profile_picked=prof.id,
        monthly_rub=monthly,
        yearly_rub=yearly,
        per_user_monthly=round(per_user, 2),
    )


def tech_capacity(registered_users: int) -> TechCapacityResult:
    prof = sp.pick_profile(registered_users)
    nodes = 1 if prof.id == "pilot" else (3 if prof.id == "standard" else 6)
    headroom = prof.max_registered_users
    return TechCapacityResult(
        registered_users=registered_users,
        profile_picked=prof.id,
        total_ram_gb=prof.ram_gb,
        headroom_ru=headroom,
        nodes=nodes,
    )


def _base_fte(registered_users: int) -> float:
    fte = 0.15 + registered_users / 80_000
    return min(fte, 4.0)


def support_cost(
    registered_users: int,
    sla: str = "business",
    include_updates: bool = True,
) -> SupportCostResult:
    fte = _base_fte(registered_users)
    if include_updates:
        if registered_users < 5_000:
            fte += 0.1
        elif registered_users < 50_000:
            fte += 0.3
        else:
            fte += 0.8
    if sla == "24x7":
        fte *= 2.5
    monthly_rub = round(fte * 180_000)
    return SupportCostResult(
        registered_users=registered_users,
        fte_monthly=round(fte, 2),
        monthly_rub=monthly_rub,
        sla=sla,
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
