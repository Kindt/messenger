"""Product capabilities — narrative groups for the deck (not raw feature counts)."""

from __future__ import annotations

from html import escape

CAPABILITY_GROUPS: tuple[tuple[str, str, str], ...] = (
    (
        "Переписка и совместная работа",
        "Личные и групповые чаты, вложения, ответы на сообщения, история переписки. "
        "Работает в браузере, можно закрепить ярлык «как приложение».",
        "Реализовано",
    ),
    (
        "Поиск и файлы",
        "Поиск по тексту сообщений и вложениям; превью изображений; скачивание файлов из чата. "
        "До ~50 тыс. рег. — SQL-поиск; выше — Solr.",
        "Реализовано",
    ),
    (
        "Звонки из чата",
        "Аудио/видео звонок между участниками чата без отдельного клиента. "
        "Для работы через NAT в production нужен TURN — настраивает IT заказчика.",
        "Частично",
    ),
    (
        "Администрирование",
        "Веб-консоль /admin/: организации, пользователи, аудит действий, политики ретенции.",
        "Реализовано",
    ),
    (
        "Комплаенс и архив",
        "Экспорт переписки (JSON/ZIP), legal hold, dual-TTL ретенция, deep-archive.",
        "Реализовано",
    ),
    (
        "Вход и безопасность",
        "Keycloak, LDAP/SSO из админки. E2EE (MLS) — после sign-off ИБ.",
        "Частично",
    ),
    (
        "Боты и интеграции",
        "Bot API, платформа плагинов L0–L3, мосты к 1С, почте, хранилищам.",
        "Реализовано",
    ),
    (
        "Мобильные клиенты",
        "Нативные iOS/Android — вне текущей поставки; сценарии через браузер/PWA.",
        "Не в поставке",
    ),
)

FOCUS_CRITERIA: tuple[tuple[str, str], ...] = (
    ("onprem", "Развёртывание в контуре"),
    ("export", "Экспорт / legal hold"),
    ("retention", "Ретенция / архив"),
    ("search", "Поиск"),
    ("e2ee", "Сквозное шифрование"),
    ("bots", "Боты / API"),
    ("sso", "SSO / LDAP"),
    ("vks", "Звонки / ВКС"),
)

MATRIX_LEGEND = """
<div class="matrix-legend callout callout-info">
  <p><strong>Как читать таблицу</strong> (для архитектора / ИБ / закупки):</p>
  <ul class="bullet-clean">
    <li><strong>✓</strong> — функция заявлена и закрывает типовое требование ТЗ.</li>
    <li><strong>◐</strong> — частично, зависит от тарифа или доработки интеграции.</li>
    <li><strong>—</strong> — в публичной поставке не заявлено или только облако.</li>
  </ul>
  <p class="small">Сравнение по <em>зрелости функции</em>, не по цене. Источники — публичные сайты и документация конкурентов (см. вкладку «Продажная», <a href="#sales-s3">§ TCO</a>).</p>
</div>
"""

# Явные выводы там, где одной галочки мало (зрелость vs «есть в roadmap»).
_EXTRA_KORUS: tuple[str, ...] = (
    "Развёртывание в контуре заказчика — против облачных Пачки и VK SaaS.",
    "Комплаенс-ядро: export, legal hold и dual-TTL ретенция в продукте, не только DLP/облачные политики.",
    "Платформа ботов L0–L3 и прозрачный стек (Java, PostgreSQL, NATS) — проще кастомизация под ИБ.",
)

_EXTRA_PEER: tuple[str, ...] = (
    "eXpress: E2EE в поставке, мобильные клиенты (iOS/Android/Аврора), ВКС до 500 участников, SmartApps, запись в реестре ФСТЭК.",
    "Пачка: быстрый старт в облаке, публичный прайс, SLA 99,9%, готовые мобильные приложения.",
    "VK SaaS: зрелая ВКС и экосистема VK Workspace, мобильные клиенты, типовой SaaS для офиса без своего железа.",
    "Korus сейчас слабее по мобильным приложениям (roadmap) и промышленной приёмке E2EE (MLS — после sign-off ИБ).",
)


def _score(val: str) -> int:
    v = str(val).strip().lower()
    if v.startswith("✓"):
        return 2
    if any(x in v for x in ("◐", "част", "sql", "solr", "webrtc", "keycloak", "roadmap", "процесс", "приёмка")):
        return 1
    if v in ("—", "-", "нет", "✗", ""):
        return 0
    return 1


def _pair_verdict(cid: str, kv: str, pv: str) -> str | None:
    """korus | peer | tie — там, где важен смысл текста, не только ✓."""
    kl, pl = kv.lower(), pv.lower()
    if cid == "e2ee":
        if "e2ee" in pl or (pv.strip().startswith("✓") and "mls" not in pl):
            if "приёмка" in kl or "roadmap" in kl:
                return "peer"
        if pl in ("—", "-", "нет"):
            return "korus"
    if cid == "vks":
        if "до 500" in pv or (pv.strip() == "✓" and "vk" not in kl):
            if "webrtc" in kl:
                return "peer"
        if "до 10" in pv:
            return "korus"
    if cid == "export":
        if "ядро" in kl and ("облако" in pl or pl.startswith("api")):
            return "korus"
    if cid == "retention":
        if "dual-ttl" in kl and "облако" in pl:
            return "korus"
    if cid == "onprem":
        if _score(kv) > _score(pv):
            return "korus"
        if _score(pv) > _score(kv):
            return "peer"
    return None


def _sorted_products(products: list[dict]) -> list[dict]:
    order = {"A": 0, "B": 1, "C": 2}
    return sorted(products, key=lambda p: (order.get(str(p.get("tier", "Z")), 9), p.get("label", "")))


def render_comparison_deltas(products: list[dict]) -> str:
    ranked = _sorted_products(products)
    korus = next(p for p in ranked if p["id"] == "korus")
    peers = [p for p in ranked if p["id"] != "korus"]

    korus_lines: list[str] = []
    peer_lines: list[str] = []

    for cid, short in FOCUS_CRITERIA:
        kv = str(korus["features"].get(cid, "—"))
        ks = _score(kv)
        for p in peers:
            pv = str(p["features"].get(cid, "—"))
            ps = _score(pv)
            verdict = _pair_verdict(cid, kv, pv)
            label = p["label"]
            if verdict == "korus":
                korus_lines.append(
                    f"<li><strong>{escape(short)}:</strong> Korus ({escape(kv)}) — "
                    f"шире, чем {escape(label)} ({escape(pv)}).</li>"
                )
            elif verdict == "peer":
                peer_lines.append(
                    f"<li><strong>{escape(short)}:</strong> {escape(label)} ({escape(pv)}) — "
                    f"сильнее Korus ({escape(kv)}).</li>"
                )
            elif ks > ps:
                korus_lines.append(
                    f"<li><strong>{escape(short)}:</strong> Korus ({escape(kv)}) vs "
                    f"{escape(label)} ({escape(pv)}).</li>"
                )
            elif ps > ks:
                peer_lines.append(
                    f"<li><strong>{escape(short)}:</strong> {escape(label)} ({escape(pv)}) vs "
                    f"Korus ({escape(kv)}).</li>"
                )

    for line in _EXTRA_KORUS:
        korus_lines.append(f"<li>{escape(line)}</li>")
    for line in _EXTRA_PEER:
        peer_lines.append(f"<li>{escape(line)}</li>")

    # dedupe while preserving order
    def _dedupe(items: list[str]) -> list[str]:
        seen: set[str] = set()
        out: list[str] = []
        for it in items:
            if it not in seen:
                seen.add(it)
                out.append(it)
        return out

    korus_lines = _dedupe(korus_lines)
    peer_lines = _dedupe(peer_lines)

    n = len(ranked)
    return f"""
<div class="delta-grid" id="compare-deltas">
  <div class="delta-col delta-korus">
    <h4>Где Korus сильнее</h4>
    <ul class="bullet-clean">{''.join(korus_lines)}</ul>
  </div>
  <div class="delta-col delta-peer">
    <h4>Где сильнее конкуренты</h4>
    <ul class="bullet-clean">{''.join(peer_lines)}</ul>
  </div>
</div>
<p class="small">Выводы — по таблице ниже (tier A–C, {n} продуктов); не рейтинг «кто выигрывает» по всем критериям сразу.</p>
"""


def render_capability_cards() -> str:
    cards = []
    for title, body, status in CAPABILITY_GROUPS:
        cards.append(
            f'<article class="cap-card"><h4>{escape(title)}</h4>'
            f'<p class="cap-status">{escape(status)}</p>'
            f"<p>{escape(body)}</p></article>"
        )
    return f'<div class="cap-grid">{"".join(cards)}</div>'


def render_focus_matrix(products: list[dict]) -> str:
    ranked = _sorted_products(products)
    header = "".join(
        f"<th scope='col' title='Уровень {escape(str(p.get('tier', '')))} · {escape(p.get('deployment', ''))}'>"
        f"{escape(p['label'])}<br/><span class='tier-tag'>{escape(str(p.get('tier', '')))}</span></th>"
        for p in ranked
    )
    rows = []
    for cid, short in FOCUS_CRITERIA:
        cells = "".join(
            f"<td>{escape(str(p['features'].get(cid, '—')))}</td>" for p in ranked
        )
        rows.append(f"<tr><th scope='row'>{escape(short)}</th>{cells}</tr>")
    return (
        render_comparison_deltas(products)
        + MATRIX_LEGEND
        + f"<p class='small'>Матрица функций: все <strong>{len(ranked)}</strong> продуктов из реестра (A — пром., B — альтернативы on-prem, C — рынок РФ).</p>"
        + "<div class='table-wrap matrix-focus matrix-focus-wide'>"
        "<table class='matrix-table matrix-compact'>"
        f"<thead><tr><th scope='col'>Критерий ТЗ</th>{header}</tr></thead>"
        f"<tbody>{''.join(rows)}</tbody></table></div>"
    )
