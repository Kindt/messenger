"""Petal radar — score rationale and gap-to-5 explanations (Korus only)."""

from __future__ import annotations

from scripts.presentation.user_features import (
    LEVEL_BASIC,
    LEVEL_FULL,
    LEVEL_LABEL,
    LEVEL_NONE,
    LEVEL_PARTIAL,
    USER_FEATURE_GROUPS,
)

# Какие оговорки остаются до «уверенного 5+» по критериям ТЗ (честно, из product_status).
KORUS_GAP_TO_5: dict[str, str] = {
    "onprem": "Нужны нагрузочная проверка и формализованная приёмка в контуре заказчика.",
    "export": "Нужны строгие процедуры экспорта и согласованные комплаенс-регламенты.",
    "audit": "Полный комплаенс-пакет и эксплуатационные регламенты аудита.",
    "search": "Нужно подтвердить поисковый профиль для большого контура без оговорок «малый пилот».",
    "e2ee": "Нужна приёмка ИБ перед массовым включением сквозного шифрования.",
    "vks": "Нужно подтвердить масштаб конференций, запись live-сессий и работу в сложных сетях заказчика; запись личного звонка — lab.",
    "mobile": "Нативные iOS/Android — вне текущей поставки; сейчас браузер и ярлык «как приложение».",
    "superapp": "Есть каталог интеграций, витрина и встроенная панель; для 5/5 нужен готовый магазин богатых мини-приложений уровня SmartApps.",
    "fstec": "Реестр и экспертиза ФСТЭК — отдельный продуктовый и юридический процесс.",
    "sizing": "Нужна формальная нагрузочная проверка на стенде заказчика.",
    "pricing": "Публичный прайс лицензии вместо «только КП».",
    "sla": "Нужны подписанный SLA и подтверждённая отказоустойчивость на рабочем контуре.",
    "sso": "Нужно подключение к реальной системе входа заказчика; сейчас подготовлены политики и тестовый LDAP-сценарий.",
    "bots": "Нужно подтвердить эксплуатацию всех уровней интеграций, marketplace и customer connectors на контуре заказчика.",
    "retention": "Нужно согласовать политики хранения с юристами и ИБ заказчика.",
    "multitenant": "Нужно подтвердить сценарии нескольких организаций на рабочем контуре.",
    "federation": "Есть доверенные организации, каталог партнёров и защита участников как add-on; нужно подтвердить массовый межорганизационный поток на контуре заказчика.",
    "ops": "Нужны формальная нагрузочная проверка и эксплуатационные регламенты на рабочем стенде.",
}

KORUS_USER_GAP: dict[str, str] = {
    "profile": "Аватары и персонализация бренда есть в веб-клиенте; для максимальной зрелости нужны нативные мобильные клиенты и синхронизация настроек между устройствами.",
    "calls": "Запись личного звонка есть в lab; для максимальной зрелости нужны подтверждённый масштаб конференций, студийный интерфейс и настройка сложных сетей.",
    "organize": "Федерация и каталог партнёров есть как add-on; до 5+ нужен подтверждённый поток с контрагентами и холдинговым каталогом.",
    "integrations": "Каталог интеграций и встроенная панель есть; до 5+ нужны готовые формы/коннекторы под заказчика и приёмка с его системами.",
    "notify": "Для максимальной зрелости нужны push-уведомления без ручной настройки IT на каждом устройстве.",
    "live": "Для максимальной зрелости нужны студия вебинара, самостоятельный архив записей и интерактив для зрителя.",
}

_USER_GROUP_BY_ID = {g.id: g for g in USER_FEATURE_GROUPS}


def _korus_criteria_gap(criterion_id: str, score: float) -> str:
    if score >= 4.9:
        return KORUS_GAP_TO_5.get(
            criterion_id,
            "Подтверждение промышленной эксплуатации без оговорок «прототип / приёмка».",
        )
    return KORUS_GAP_TO_5.get(criterion_id, "Довести до статуса без оговорок и пройти приёмку.")


def explain_criteria_score(
    criterion_id: str,
    criterion_title: str,
    cell_text: str,
    score: float,
    product_id: str,
) -> tuple[str, str]:
    """Return (why_this_score, gap_to_confident_5). Gap only for Korus."""
    raw = (cell_text or "—").strip()
    title = criterion_title
    t_lower = raw.lower()

    if score >= 4.9:
        why = f"{title}: в реестре «{raw}» — требование закрыто без существенной оговорки (5/5)."
    elif score >= 3.8:
        why = (
            f"{title}: «{raw}» — функция или контур есть, но остаётся оговорка по масштабу, удобству интерфейса, ИБ или приёмке "
            f"(≈{score:.1f}/5)."
        )
    elif score >= 2.8:
        if "◐" in raw or "част" in t_lower:
            why = f"{title}: «{raw}» — покрыто частично или требует уточнения границ (≈{score:.1f}/5)."
        else:
            why = f"{title}: «{raw}» — базовая зрелость, не полный уровень приоритетного класса (≈{score:.1f}/5)."
    elif score >= 2.0:
        why = f"{title}: «{raw}» — в процессе, по запросу или roadmap (≈{score:.1f}/5)."
    else:
        why = f"{title}: «{raw}» — не заявлено или отсутствует (≈{score:.1f}/5)."

    if product_id != "korus":
        return why, ""
    return why, _korus_criteria_gap(criterion_id, score)


def explain_user_score(group_id: str, product_id: str, score: float) -> tuple[str, str]:
    """Rationale from user_features comparison cells. Gap only for Korus."""
    group = _USER_GROUP_BY_ID[group_id]
    cell = group.comparisons.get(product_id)
    if not cell:
        why = "Нет данных для сравнения."
        gap = "Заполнить сценарии в таблице функций." if product_id == "korus" else ""
        return why, gap

    level = LEVEL_LABEL.get(cell.level, cell.level)
    covered = "; ".join(cell.covered[:4])
    why = f"Уровень «{level}» ({score:.1f}/5): уже есть — {covered}."

    if product_id != "korus":
        return why, ""

    if cell.level == LEVEL_FULL and score >= 4.9:
        gap = (
            "Для «уверенного 5+» — стабильность без оговорок «прототип», "
            "mobile/push и edge-cases из таблицы выше."
        )
    else:
        gaps = "; ".join(cell.gaps[:4])
        gap = f"До уверенного 5+: {gaps}." if gaps else "Подтвердить сценарии без дополнительных оговорок."

    if cell.level in (LEVEL_PARTIAL, LEVEL_BASIC, LEVEL_NONE):
        extra = KORUS_USER_GAP.get(group_id)
        if extra:
            gap = f"{gap} {extra}"

    return why, gap
