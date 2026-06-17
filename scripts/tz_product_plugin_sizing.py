"""§10.7 — sizing узла интеграций (боты/плагины), модель «1 экземпляр = 1 бот».

Источник норм: deploy/qemu/RESOURCES.md (korus-integrations), spec 014 design.
"""

from __future__ import annotations

from dataclasses import dataclass
from html import escape

# База узла интеграций: gateway + connector-runtime + ОС/Docker (МБ)
INTEGRATIONS_BASE_MB = 1200

# Доп. RAM на один экземпляр бота (1 instance = 1 bot)
MB_PER_CLASS: dict[str, int] = {
    "L0": 0,  # JSON-меню в connector-runtime, без отдельного контейнера
    "L1_sidecar": 192,  # PHP/Go/Python sidecar
    "L1_connector": 0,  # REST mapping в connector-runtime (shared)
    "L2_bridge": 384,  # exchange, storage, 1c, naumen, bitrix-bridge
    "L3_ai_ocr": 768,  # ai-bridge + ocr-worker (on-prem)
}

VCPU_PER_CLASS: dict[str, float] = {
    "L0": 0.0,
    "L1_sidecar": 0.1,
    "L1_connector": 0.05,
    "L2_bridge": 0.25,
    "L3_ai_ocr": 0.5,
}


@dataclass(frozen=True)
class BotFleet:
    """Количество экземпляров ботов по классам (каждый считается отдельно)."""

    l0: int = 0
    l1_sidecar: int = 0
    l1_connector: int = 0
    l2_bridge: int = 0
    l3_ai_ocr: int = 0

    @property
    def total_instances(self) -> int:
        return self.l0 + self.l1_sidecar + self.l1_connector + self.l2_bridge + self.l3_ai_ocr


def estimate_integrations_ram_mb(fleet: BotFleet) -> int:
    extra = (
        fleet.l0 * MB_PER_CLASS["L0"]
        + fleet.l1_sidecar * MB_PER_CLASS["L1_sidecar"]
        + fleet.l1_connector * MB_PER_CLASS["L1_connector"]
        + fleet.l2_bridge * MB_PER_CLASS["L2_bridge"]
        + fleet.l3_ai_ocr * MB_PER_CLASS["L3_ai_ocr"]
    )
    return INTEGRATIONS_BASE_MB + extra


def estimate_integrations_vcpu(fleet: BotFleet) -> float:
    base = 1.0  # gateway + connector-runtime
    extra = (
        fleet.l0 * VCPU_PER_CLASS["L0"]
        + fleet.l1_sidecar * VCPU_PER_CLASS["L1_sidecar"]
        + fleet.l1_connector * VCPU_PER_CLASS["L1_connector"]
        + fleet.l2_bridge * VCPU_PER_CLASS["L2_bridge"]
        + fleet.l3_ai_ocr * VCPU_PER_CLASS["L3_ai_ocr"]
    )
    return round(base + extra, 1)


def recommend_integrations_ram_gb(fleet: BotFleet, *, reserve_pct: float = 0.25) -> int:
    mb = estimate_integrations_ram_mb(fleet)
    gb = mb * (1.0 + reserve_pct) / 1024
    return max(4, int(gb + 0.99))


# Типовые профили для презентации (1 экземпляр на бота)
EXAMPLE_FLEETS: tuple[tuple[str, str, BotFleet], ...] = (
    (
        "Pilot — FAQ + 2 интеграции",
        "L0 FAQ, sidecar PHP, bridge 1С",
        BotFleet(l0=1, l1_sidecar=1, l2_bridge=1),
    ),
    (
        "Standard — Service Desk",
        "Naumen L2, Jira L1, storage L2, echo sidecar",
        BotFleet(l1_connector=1, l1_sidecar=1, l2_bridge=2),
    ),
    (
        "Standard — документооборот",
        "OCR+AI L3, exchange L2, 2× FAQ L0",
        BotFleet(l0=2, l2_bridge=1, l3_ai_ocr=1),
    ),
    (
        "Расширенный витрина",
        "6× L2 bridge + 2 sidecar + 1 L3",
        BotFleet(l1_sidecar=2, l2_bridge=6, l3_ai_ocr=1),
    ),
)


from presentation_ops_footnotes import fn  # noqa: E402


def render_plugin_sizing_table_html() -> str:
    rows = []
    for title, desc, fleet in EXAMPLE_FLEETS:
        ram_mb = estimate_integrations_ram_mb(fleet)
        ram_gb = recommend_integrations_ram_gb(fleet)
        vcpu = estimate_integrations_vcpu(fleet)
        ram_mb_str = f"{ram_mb:,}".replace(",", " ")
        rows.append(
            f"<tr><td>{escape(title)}</td>"
            f"<td>{escape(desc)}</td>"
            f"<td>{fleet.total_instances}</td>"
            f"<td>{ram_mb_str} МБ</td>"
            f"<td><b>{ram_gb} ГБ</b></td><td>{vcpu}</td></tr>"
        )
    return f"""
<h3 id="s10-7">10.7 Узел ботов и плагинов (1 экземпляр = 1 бот){fn("oplus")}</h3>
<div class="note">
  <div class="req">Модель ресурсов</div>
  <div class="comment">
    Исполнение ботов и плагинов вынесено на <b>отдельный узел интеграций</b> (не на сервер чатов).
    Код плагинов <b>не</b> загружается в JVM core-api — только HTTP-маршрутизация и политики org.
    Каждый зарегистрированный бот = <b>один экземпляр</b> (L0…L3). L0 (FAQ-кнопки) не требует отдельного контейнера.
  </div>
</div>
<table>
  <tr><th>Класс</th><th>Примеры</th><th>RAM / экз.</th><th>vCPU / экз.</th></tr>
  <tr><td><b>L0</b></td><td>FAQ, телефонный справочник, ссылки</td><td>0 (конфиг)</td><td>0</td></tr>
  <tr><td><b>L1</b> sidecar</td><td>PHP/Go/Python echo, Bitrix sidecar</td><td>~192 МБ</td><td>0,1</td></tr>
  <tr><td><b>L1</b> connector</td><td>Jira/Confluence read-only mapping</td><td>shared runtime</td><td>0,05</td></tr>
  <tr><td><b>L2</b> bridge</td><td>Exchange, 1С, Naumen, storage, outbound</td><td>~384 МБ</td><td>0,25</td></tr>
  <tr><td><b>L3</b> + OCR</td><td>AI triage + on-prem OCR</td><td>~768 МБ</td><td>0,5</td></tr>
</table>
<p class="small">База узла: gateway + connector-runtime + ОС ≈ <b>{INTEGRATIONS_BASE_MB // 1024} ГБ</b> RAM.
Рекомендуемая RAM VM = расчёт + 25% запас (минимум 4 ГБ для профиля Pilot).</p>
<table>
  <tr><th>Профиль заказчика</th><th>Состав (экз.)</th><th>Ботов</th><th>RAM расч.</th><th>RAM VM</th><th>vCPU</th></tr>
  {''.join(rows)}
</table>
<p class="small"><b>Сравнение с eXpress @1k рег.:</b> кластер Bot ~6 vCPU / 12 ГБ RAM (shared).
У Korus при 6 L2-bridge: ~{recommend_integrations_ram_gb(BotFleet(l2_bridge=6))} ГБ на узле интеграций — линейно по числу экземпляров, без per-user лицензии на бота.</p>
"""
