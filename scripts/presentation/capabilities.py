"""Product capabilities — narrative groups for the deck (not raw feature counts)."""

from __future__ import annotations

from html import escape

from scripts.presentation import anchors as anc

CAPABILITY_GROUPS: tuple[tuple[str, str, str], ...] = (
    (
        "Переписка и совместная работа",
        "Личные и групповые чаты, каналы, треды, @mentions, голосовые сообщения, "
        "редактирование сообщений, фильтры «непрочитанные»/@mentions, "
        "отметки «доставлено / прочитано» с просмотром списка в групповых чатах, "
        "вложения и ответы. Работает в браузере, можно закрепить ярлык «как приложение»; интерфейс подготовлен на 6 языках.",
        "Реализовано",
    ),
    (
        "Состав продукта",
        "Можно начинать с базового контура: чат, файлы, онлайн-обновления, встроенный поиск и администрирование. "
        "Дополнительные возможности подключаются отдельно: уведомления, расширенный поиск, архив, экспорт, live, "
        "боты, интеграции, E2EE, DLP, федерация, импорт, опросы, напоминания и совместные доски.",
        "Реализовано",
    ),
    (
        "Новые сценарии для команд",
        "Опросы, отложенные сообщения, напоминания, стикеры/GIF, канбан и доска в чате реализованы "
        "как подключаемые возможности. В пилоте их включают выборочно — они не входят в базовый контур по умолчанию.",
        "Реализовано",
    ),
    (
        "AI и распознавание",
        "AI-помощник, распознавание речи и субтитры выделены в отдельную подключаемую возможность. Для демонстрации важны границы: "
        "какой сервер обработки используется, где хранятся данные и какие требования ИБ нужны перед включением.",
        "Частично",
    ),
    (
        "Поиск и файлы",
        "Поиск по тексту сообщений и вложениям; превью изображений; скачивание файлов из чата. "
        "Для небольшого пилота достаточно встроенного поиска; для большого контура включается отдельный поисковый сервис.",
        "Реализовано",
    ),
    (
        "Звонки из чата",
        "Аудио- и видеозвонки запускаются прямо из переписки; запись личного звонка доступна в lab "
        "при разрешении политики. Для больших групповых созвонов и сложных сетей нужна настройка медиасервера и сетевых правил у заказчика.",
        "Частично",
    ),
    (
        "Live-трансляции",
        "Корпоративные эфиры можно показать в прототипе: просмотр в браузере, модерация и запись. Массовая нагрузочная приёмка относится к отдельному этапу.",
        "Частично",
    ),
    (
        "Персонализация интерфейса",
        "Фирменные цвета, логотип, демо-палитры и варианты раскладки экрана входа — готовы к пилоту. "
        "Аватары пользователей и чатов (загрузка, обрезка) работают в lab; промышленная приёмка — на стенде заказчика.",
        "Частично",
    ),
    (
        "Администрирование",
        "Веб-консоль для IT и администраторов: организации, пользователи, аудит, политики хранения, "
        "импорт переписки, персонализация интерфейса, вход через корпоративные системы и управление подключёнными модулями. "
        "Интерфейс консоли подготовлен на 6 языках.",
        "Реализовано",
    ),
    (
        "Комплаенс и архив",
        "Выгрузка переписки, юридическое удержание, правила хранения и долгосрочный архив.",
        "Реализовано",
    ),
    (
        "Вход и безопасность",
        "Гибкий вход через пароль или корпоративную систему учётных записей. Сквозное шифрование технически подготовлено, но перед массовым включением требует приёмки ИБ.",
        "Частично",
    ),
    (
        "Боты и интеграции",
        "Боты и подключаемые сервисы помогают связать чат с 1С, почтой, календарём, хранилищами и внутренними системами. Каталог интеграций настраивает администратор.",
        "Частично",
    ),
    (
        "Кастомизируемый внешний стек",
        "Для внешних баз, хранилищ, входа, брокера сообщений, поиска и подключаемых возможностей есть манифест желаемого состояния, статус готовности, проверки подключения и экран администратора. "
        "Подключение реального внешнего стека заказчика и финальная эксплуатационная приёмка остаются отдельным этапом.",
        "Частично",
    ),
    (
        "Мобильные клиенты",
        "Нативные iOS/Android — вне текущей поставки; текущий сценарий — браузер и ярлык «как приложение».",
        "Не в поставке",
    ),
)

FOCUS_CRITERIA: tuple[tuple[str, str], ...] = (
    ("onprem", "Развёртывание в контуре"),
    ("export", "Экспорт / юридическое удержание"),
    ("retention", "Ретенция / архив"),
    ("search", "Поиск"),
    ("e2ee", "Сквозное шифрование"),
    ("bots", "Боты / API"),
    ("sso", "SSO / LDAP"),
    ("vks", "Звонки / ВКС"),
)

def matrix_legend() -> str:
    tco = anc.link(anc.SALES_TCO, "раздел «Бюджетный ориентир» на вкладке «Для продаж»")
    return f"""
<div class="matrix-legend callout callout-info">
  <p><strong>Как читать таблицу:</strong></p>
  <ul class="bullet-clean">
    <li><strong>✓</strong> — функция закрывает типовое требование.</li>
    <li><strong>◐</strong> — частично: зависит от тарифа, настройки или доработки.</li>
    <li><strong>—</strong> — в публичной поставке не заявлено.</li>
  </ul>
  <p class="small">Это сравнение по зрелости функций, не по цене. Источники — публичные сайты и документация конкурентов (см. {tco}).</p>
</div>
"""

# Явные выводы там, где одной галочки мало (зрелость vs «есть в roadmap»).
_EXTRA_KORUS: tuple[str, ...] = (
    "Развёртывание в контуре заказчика — против облачных Пачки и VK SaaS.",
    "Комплаенс-ядро: выгрузка, юридическое удержание и правила хранения в продукте, не только контроль утечек или облачные политики.",
    "Платформа ботов L0–L3 и прозрачный стек (Java, PostgreSQL, NATS) — проще кастомизация под ИБ.",
)

_EXTRA_PEER: tuple[str, ...] = (
    "eXpress: E2EE в поставке, мобильные клиенты (iOS/Android/Аврора), ВКС до 500 участников, SmartApps, запись в реестре ФСТЭК.",
    "Пачка: быстрый старт в облаке, публичный прайс, SLA 99,9%, готовые мобильные приложения.",
    "VK SaaS: зрелая ВКС и экосистема VK Workspace, мобильные клиенты, типовой облачный сервис для офиса без своего железа.",
    "Korus сейчас слабее по мобильным приложениям и промышленной приёмке E2EE (MLS — после приёмки ИБ).",
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


def _plain_feature_value(value: str) -> str:
    """Make generated comparison bullets readable outside the technical matrix."""
    text = str(value)
    replacements = (
        ("Solr / SQL", "поиск: встроенный или расширенный"),
        ("Keycloak", "корпоративный вход"),
        ("WebRTC", "звонки из браузера"),
        ("MLS (приёмка)", "шифрование в приёмке"),
        ("E2EE opt", "шифрование как опция"),
        ("dual-TTL", "политики хранения"),
        ("✓ ядро", "есть в ядре"),
        ("до 500", "до 500 участников"),
        ("плагины", "через расширения"),
        ("облако", "облачная модель"),
    )
    for src, dst in replacements:
        text = text.replace(src, dst)
    return text


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
            kv_plain = _plain_feature_value(kv)
            pv_plain = _plain_feature_value(pv)
            if verdict == "korus":
                korus_lines.append(
                    f"<li><strong>{escape(short)}:</strong> Korus ({escape(kv_plain)}) — "
                    f"шире, чем {escape(label)} ({escape(pv_plain)}).</li>"
                )
            elif verdict == "peer":
                peer_lines.append(
                    f"<li><strong>{escape(short)}:</strong> {escape(label)} ({escape(pv_plain)}) — "
                    f"сильнее Korus ({escape(kv_plain)}).</li>"
                )
            elif ks > ps:
                korus_lines.append(
                    f"<li><strong>{escape(short)}:</strong> Korus ({escape(kv_plain)}) vs "
                    f"{escape(label)} ({escape(pv_plain)}).</li>"
                )
            elif ps > ks:
                peer_lines.append(
                    f"<li><strong>{escape(short)}:</strong> {escape(label)} ({escape(pv_plain)}) vs "
                    f"Korus ({escape(kv_plain)}).</li>"
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

    def _preview_unique(items: list[str], limit: int) -> list[str]:
        seen: set[str] = set()
        out: list[str] = []
        for item in items:
            start = item.find("<strong>")
            end = item.find("</strong>")
            key = item[start:end] if start >= 0 and end > start else item
            if key in seen:
                continue
            seen.add(key)
            out.append(item)
            if len(out) >= limit:
                break
        if len(out) < limit:
            for item in items:
                if item not in out:
                    out.append(item)
                    if len(out) >= limit:
                        break
        return out

    n = len(ranked)
    korus_preview = _preview_unique(korus_lines, 6)
    peer_preview = _preview_unique(peer_lines, 6)
    korus_more = korus_lines[6:]
    peer_more = peer_lines[6:]
    more_html = ""
    if korus_more or peer_more:
        more_html = f"""
<details class="compare-more">
  <summary>Показать подробные строки сравнения</summary>
  <div class="delta-grid">
    <div class="delta-col delta-korus">
      <h4>Все преимущества Korus</h4>
      <ul class="bullet-clean">{''.join(korus_more or korus_lines)}</ul>
    </div>
    <div class="delta-col delta-peer">
      <h4>Все преимущества конкурентов</h4>
      <ul class="bullet-clean">{''.join(peer_more or peer_lines)}</ul>
    </div>
  </div>
</details>
"""
    return f"""
<div class="compare-summary callout callout-info">
  <h4>Короткий вывод</h4>
  <p>Korus сильнее там, где заказчику важны свой контур, архив, аудит и управляемые интеграции. Конкуренты чаще выигрывают в готовых мобильных клиентах, зрелых ВКС-сценариях, публичных прайсах и сертификационных статусах.</p>
</div>
<div class="delta-grid" id="compare-deltas">
  <div class="delta-col delta-korus">
    <h4>Где Korus сильнее</h4>
    <ul class="bullet-clean">{''.join(korus_preview)}</ul>
  </div>
  <div class="delta-col delta-peer">
    <h4>Где сильнее конкуренты</h4>
    <ul class="bullet-clean">{''.join(peer_preview)}</ul>
  </div>
</div>
{more_html}
<p class="small">Выводы — по таблице ниже (группы A–C, {n} продуктов); не рейтинг «кто выигрывает» по всем критериям сразу.</p>
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
        + matrix_legend()
        + f"<p class='small'>Матрица функций: все <strong>{len(ranked)}</strong> продуктов из реестра (A — приоритетный класс сравнения, B — альтернативы в контуре, C — рынок РФ).</p>"
        + "<div class='table-wrap matrix-focus matrix-focus-wide'>"
        "<table class='matrix-table matrix-compact'>"
        f"<thead><tr><th scope='col'>Критерий ТЗ</th>{header}</tr></thead>"
        f"<tbody>{''.join(rows)}</tbody></table></div>"
    )
