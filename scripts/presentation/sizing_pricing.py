"""Infra sizing profiles and cost formulas — no comparison anchors."""

from __future__ import annotations

from dataclasses import dataclass

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
        "Ops",
        "Базовый backup + мониторинг (Pilot) или расширенный контур (Standard).",
    ),
]


@dataclass(frozen=True)
class InfraProfile:
    id: str
    label: str
    ram_gb: int
    max_registered_users: int
    monthly_rub: int


PROFILES: tuple[InfraProfile, ...] = (
    InfraProfile("pilot", "Pilot", 14, 10_000, 61_350),
    InfraProfile("standard", "Standard", 140, 100_000, 332_000),
    InfraProfile("enterprise", "Enterprise", 450, 500_000, 980_000),
)

_PROFILE_MAP = {p.id: p for p in PROFILES}


def pick_profile(registered_users: int) -> InfraProfile:
    """Minimum profile covering registered_users."""
    for profile in PROFILES:
        if registered_users <= profile.max_registered_users:
            return profile
    return PROFILES[-1]


def infra_monthly(profile_id: str | InfraProfile) -> int:
    if isinstance(profile_id, InfraProfile):
        return profile_id.monthly_rub
    return _PROFILE_MAP[profile_id].monthly_rub


def infra_yearly(profile_id: str | InfraProfile) -> int:
    return infra_monthly(profile_id) * 12


def headroom_ru(profile: InfraProfile, at_ru: int) -> int | None:
    if profile.max_registered_users > at_ru:
        return profile.max_registered_users
    return None


def fmt_rub(amount: int) -> str:
    return f"{amount:,}".replace(",", " ") + " ₽"
