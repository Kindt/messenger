"""Customer-facing footnotes: synthetic / pre-ops data that updates after ops sign-off."""

import json
from html import escape
from pathlib import Path

_REPO = Path(__file__).resolve().parents[1]
_NT_JSON = _REPO / "docs" / "benchmarks" / "qemu-nt-baseline-2026-06-15.json"

# Marker → (short label for legend, footnote body HTML)
OPS_SYNTHETIC_FOOTNOTES: dict[str, tuple[str, str]] = {
    "dagger": (
        "Модель нагрузки и sizing",
        "Таблицы DAU, пик сообщ./с, RAM, диск и сеть в <b>§10</b> — "
        "<b>аналитическая модель</b> (коэффициенты активности корпоративного мессенджера), "
        "а не замеры на железе заказчика. "
        "До промышленного запуска нет stage/prod контура с полным sizing — цифры нужны для планирования КП и профиля Pilot/Standard. "
        "<b>После этапа ops sign-off</b> (развёртывание stage, formal load test на 10–20% целевой нагрузки): "
        "таблицы §10 и диаграммы RAM/TCO могут быть <b>уточнены</b> по фактическим p95 и пропускной способности.",
    ),
    "ddagger": (
        "Лабораторные замеры НТ",
        "Строки «лабораторный baseline» и графики пропускной способности — "
        "<b>замеры на тестовом стенде разработки</b> (ограниченный объём RAM, не конфигурация Enterprise). "
        "Они подтверждают работоспособность кода, но <b>не заменяют</b> нагрузочную приёмку на stage. "
        "<b>После ops sign-off:</b> повторные прогоны k6/Locust на stage iron; в презентации публикуются "
        "измеренные p50/p95, RPS и msg/s вместо ориентиров.",
    ),
    "section": (
        "RPO / RTO и SLA",
        "Recovery Point/Time Objective в <b>§10.3</b> и <b>§14</b> — "
        "<b>целевые ориентиры</b> для договора и профиля (Pilot / Standard / Enterprise), "
        "не результат учений восстановления из backup. "
        "<b>После ops sign-off:</b> проверка backup/restore на контуре заказчика, HTTPS на prod, "
        "фиксация согласованных RPO/RTO в договоре и матрице приёмки эксплуатации.",
    ),
    "pilcrow": (
        "Стоимость инфраструктуры",
        "Суммы в <b>§17</b> и в матрице сравнения с конкурентами — "
        "<b>ориентиры рынка VDS/dedicated</b> на дату презентации; "
        "<b>не являются офертой</b> и не включают лицензию, внедрение и L2-поддержку. "
        "<b>После ops sign-off:</b> уточнение по итогам sizing на stage (число VM, реплики API, Solr, узел интеграций).",
    ),
    "num": (
        "Статусы «Частично» (TLS, E2EE, Push, TURN)",
        "Строки §4 с пометкой <b>Частично</b>: функция <b>реализована и проверена инженерами</b> на тестовом стенде "
        "(автотесты UI, security-gate). "
        "Prod-включение — работы IT заказчика: сертификаты HTTPS, ключи VAPID, сервер ретрансляции видео (TURN), "
        "формальная приёмка E2EE (Security / Product). "
        "<b>После ops sign-off:</b> статус в §4 может быть повышен до «Реализовано» без изменения кода продукта.",
    ),
    "oplus": (
        "Sizing узла интеграций",
        "RAM и vCPU на экземпляр бота (§10.7, §12) — <b>расчёт по классам L0–L3</b>; "
        "проверено на тестовом стенде с mock/auto backends. "
        "Реальные API (1С, Exchange, OCR) на контуре заказчика могут потребовать больший запас. "
        "<b>После ops sign-off:</b> уточнение RAM VM интеграций по мониторингу prod.",
    ),
}

MARKERS = {
    "dagger": "†",
    "ddagger": "‡",
    "section": "§",
    "pilcrow": "¶",
    "num": "#",
    "oplus": "⊕",
}


def fn(key: str) -> str:
    """Inline superscript footnote reference."""
    sym = MARKERS.get(key, "*")
    return f'<sup class="fn-ref"><a href="#fn-{key}" title="{escape(OPS_SYNTHETIC_FOOTNOTES[key][0])}">{sym}</a></sup>'


def render_ops_synthetic_legend_html() -> str:
    items = "".join(
        f"<li><b>{escape(MARKERS[k])}</b> — {escape(OPS_SYNTHETIC_FOOTNOTES[k][0])}</li>"
        for k in OPS_SYNTHETIC_FOOTNOTES
    )
    return f"""
<div class="note" id="fn-legend">
  <div class="req">Сноски: данные до ops sign-off</div>
  <div class="comment">
    В презентации помечены блоки, где цифры или статусы <b>синтетические / расчётные</b> —
    они нужны для планирования и переговоров, но <b>могут измениться</b> после этапа
    <b>ops sign-off</b> (развёртывание stage/prod, TLS, нагрузочные прогоны, backup drill,
    формальная приёмка E2EE и push). Легенда:
    <ul class="small">{items}</ul>
    Подробные пояснения — в блоке <a href="#fn-list">«Сноски (расшифровка)»</a> в конце документа.
  </div>
</div>"""


def render_ops_synthetic_footnotes_html() -> str:
    rows = []
    for key, (title, body) in OPS_SYNTHETIC_FOOTNOTES.items():
        sym = MARKERS[key]
        rows.append(
            f'<li id="fn-{key}"><b>{escape(sym)} {escape(title)}.</b> {body}</li>'
        )
    return f"""
<hr/>
<h2 id="fn-list">Сноски (расшифровка)</h2>
<p class="small comment">
  Обновление презентации после ops sign-off: замена ориентиров на <b>измеренные</b> метрики stage,
  согласованные SLA/RPO/RTO и актуальные статусы §4 — без обязательного изменения функционала продукта.
</p>
<ol class="fn-list">{"".join(rows)}</ol>"""


def render_ops_synthetic_warn_compact_html() -> str:
    """Short warn for competitor pages."""
    return """
<div class="warn">
  <div class="req">Ориентиры до ops sign-off</div>
  <div class="comment">
    Infra/TCO, sizing RAM и лабораторные замеры НТ — <b>синтетические или расчётные</b> данные для переговоров.
    После развёртывания stage и formal load test цифры уточняются. См. сноски
    <b>† ‡ § ¶</b> в продуктовой презентации (блок «Сноски»).
  </div>
</div>"""


def render_product_lab_baseline_html() -> str:
    """§10.5 — customer-safe lab NT table (no internal paths)."""
    if not _NT_JSON.is_file():
        return (
            '<p class="small comment">Лабораторный baseline будет опубликован после очередного '
            "прогона на тестовом стенде.</p>"
        )
    data = json.loads(_NT_JSON.read_text(encoding="utf-8"))
    labels = {
        "parallel-health-sustained": "Устойчивая проверка /health",
        "core-api-read-mixed": "REST: чтение (auth + ready)",
        "message-pipeline-burst": "Конвейер сообщений E2E",
        "messaging-e2e-load-rounds": "E2E мессенджинг + нагрузка",
    }
    rows = []
    for s in data.get("scenarios", []):
        name = labels.get(s.get("name", ""), s.get("name", ""))
        if "p95_ms" in s:
            metric = f"p50 {s.get('p50_ms', '—')} мс · p95 {s['p95_ms']} мс · ~{s.get('rps', '—')} запр/с"
        elif "burst_msg_per_sec" in s:
            metric = (
                f"пик ~{s['burst_msg_per_sec']} сообщ/с · "
                f"{s.get('burst_messages', '—')} сообщ. за {s.get('elapsed_sec', '—')} с"
            )
        elif s.get("result"):
            res = "ПРОЙДЕН" if str(s.get("result", "")).lower() == "pass" else str(s.get("result", ""))
            metric = f"{res} · раундов={s.get('load_rounds', '—')}"
        else:
            metric = "—"
        rows.append(f"<tr><td>{escape(name)}</td><td>{escape(metric)}</td></tr>")
    body = "".join(rows) or "<tr><td colspan=\"2\">—</td></tr>"
    return f"""
<p class="small comment">
  Стенд: тестовая VM разработки (ограниченный RAM), не конфигурация Enterprise.
  Сравнение с целью Pilot (~15 сообщ./с пик): замер E2E burst часто ниже — ожидаемо для лабораторного железа.
  {fn("ddagger")}
</p>
<table>
  <tr><th>Сценарий</th><th>Результат (2026-06-15)</th></tr>
  {body}
</table>
<p class="small"><b>После ops sign-off:</b> эта таблица заменяется измерениями stage (formal load test).</p>"""
