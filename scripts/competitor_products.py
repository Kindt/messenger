"""Реестр конкурентов из scripts/competitors/registry.json (v2.2)."""

from __future__ import annotations

from dataclasses import dataclass

from competitor_registry_loader import Tier, load_registry

_REG = load_registry()

PRODUCT_COLUMNS = _REG.product_columns
COMPARISON_CRITERIA = _REG.comparison_criteria
PRODUCT_FEATURES = _REG.product_features
PROS_CONS_BY_PRODUCT = _REG.pros_cons_by_product
TIER_LABELS = _REG.tier_labels
RADAR_AXES = _REG.radar_axes
RADAR_ONPREM = _REG.radar_onprem
RADAR_MAX = _REG.radar_max
LOOP_PRO_RUB_MONTH = _REG.loop_pro_rub_month
COMPASS_ONPREM_RUB_MONTH = _REG.compass_onprem_rub_month
KORUS_S10K_INFRA = _REG.korus_s10k_infra


@dataclass(frozen=True)
class TierBTcoRow:
    name: str
    license_yearly_10k: int | None
    infra_yearly_10k: int | None
    license_note: str
    infra_note: str
    estimate: bool = True


def tier_b_tco_rows() -> tuple[TierBTcoRow, ...]:
    loop_lic = LOOP_PRO_RUB_MONTH * 12 * 10_000
    compass_lic = COMPASS_ONPREM_RUB_MONTH * 12 * 10_000
    return (
        TierBTcoRow(
            "Loop",
            loop_lic,
            KORUS_S10K_INFRA // 2,
            "199 ₽/акт. пользов./мес облако (loop.ru); on-prem — тариф «Корпоративный», КП",
            "Модель: ~½ infra Korus S-10k (2 ГБ min VM + PostgreSQL)",
        ),
        TierBTcoRow(
            "Rocket.Chat EE",
            None,
            KORUS_S10K_INFRA,
            "Лицензия EE — по КП",
            "Модель: infra как Korus S-10k (MongoDB replica)",
        ),
        TierBTcoRow(
            "Mattermost EE",
            None,
            KORUS_S10K_INFRA,
            "Лицензия EE — по КП; concurrent ≠ RU",
            "Модель: infra как Korus S-10k",
        ),
        TierBTcoRow(
            "VK Superapp on-prem",
            None,
            int(KORUS_S10K_INFRA * 1.2),
            "Лицензия workspace — по КП",
            "Экстраполяция от 56 ГБ @2k (док. VK)",
        ),
    )


@dataclass(frozen=True)
class TierCMarketRow:
    name: str
    deployment: str
    pricing_public: str
    tco_10k_note: str
    registry: str
    source: str


def tier_c_market_rows() -> tuple[TierCMarketRow, ...]:
    compass_y = COMPASS_ONPREM_RUB_MONTH * 12 * 10_000
    return (
        TierCMarketRow(
            "МТС Линк Чаты",
            "SaaS + on-prem",
            "По КП (business.mts.ru, mts-link.ru)",
            "TCO @10k — инд. расчёт; облачные тарифы «Коммуникации» отдельно",
            "Реестр РФ",
            "mts-link.ru/products/messenger/",
        ),
        TierCMarketRow(
            "Compass",
            "SaaS + on-prem",
            "390 ₽/мес облако; 490 ₽/мес on-prem (getcompass.ru)",
            f"Лицензия on-prem @10k ≈ {compass_y:,} ₽/год (без infra)".replace(",", " "),
            "Уточнять реестр",
            "getcompass.ru/pricing",
        ),
        TierCMarketRow(
            "TrueConf Server",
            "On-prem UC + чат",
            "От 23 000 ₽/год; PRO/online/guest (trueconf.ru/server/buy/)",
            "Не linear ₽/reg; калькулятор + договор",
            "Реестр РФ",
            "trueconf.ru/products/server/",
        ),
    )


def heatmap_color(cell: str) -> str:
    c = cell.strip()
    if c.startswith("✓") and "◐" not in c:
        return "#86efac"
    if c.startswith("◐") or "opt" in c.lower() or "плагин" in c.lower() or "зависит" in c.lower():
        return "#fcd34d"
    if c in ("—", "–", "N/A", "нет"):
        return "#fca5a5"
    if any(x in c for x in ("КП", "ref", "дорож", "процесс", "concurrent", "док.")):
        return "#e0e7ff"
    return "#f3f4f6"


def validate_product_registry() -> list[str]:
    """Проверка полноты данных; пустой список = OK."""
    errors: list[str] = []
    crit_ids = [c[0] for c in COMPARISON_CRITERIA]
    for pid, _label, _tier, _dep in PRODUCT_COLUMNS:
        feats = PRODUCT_FEATURES.get(pid)
        if not feats:
            errors.append(f"missing PRODUCT_FEATURES[{pid!r}]")
            continue
        for cid in crit_ids:
            if cid not in feats:
                errors.append(f"{pid}: missing criterion {cid!r}")
    for name, (pros, cons) in PROS_CONS_BY_PRODUCT.items():
        if len(pros) < 3:
            errors.append(f"{name}: fewer than 3 pros")
        if len(cons) < 3:
            errors.append(f"{name}: fewer than 3 cons")
    if RADAR_ONPREM and len(RADAR_AXES) != len(RADAR_ONPREM[0][3]):
        errors.append("RADAR axis count mismatch")
    return errors
