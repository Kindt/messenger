"""Tests for persona content drafts."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.content import (
    draft_pm_s1,
    draft_pm_s3,
    draft_pm_s4,
    draft_user_s1,
    draft_user_s2,
    draft_user_s3,
    draft_user_s4,
    draft_sales_s1,
    draft_sales_s3,
    draft_tech_s1,
    draft_tech_s2,
    draft_tech_s3,
    draft_tech_s4,
    executive_summary_cards,
    audience_route_cards,
    role_decision_board,
)
from scripts.presentation import module_sizing as ms
from scripts.presentation.gaps_registry import render_gaps_registry_html
from scripts.presentation.render import _calc_pm_html, _calc_tech_html, _deck_css, _deck_js, render_block0, render_competitor_list
from scripts.presentation.user_features import render_user_feature_groups_html


def test_user_tab_no_technical_jargon():
    for fn in (draft_user_s1, draft_user_s2, draft_user_s3, draft_user_s4):
        html = fn()
        for token in ("JWT", "Keycloak", "NATS", "Solr", "mesh"):
            assert token.lower() not in html.lower()


def test_user_tab_reading_level_short_sentences():
    html = draft_user_s1()
    assert html.count(".") >= 3


def test_sales_copy_has_buyer_facing_positioning():
    html = draft_sales_s1()
    assert "первая встреча" in html.lower()
    assert "контуре заказчика" in html.lower()
    assert "закупочная модель" in html.lower()
    assert "не коммерческое предложение" in html.lower()
    assert "как отвечать на первые возражения" in html.lower()
    assert "почему не облачный сервис" in html.lower()


def test_sales_tco_copy_excludes_active_users_from_tco():
    html = draft_sales_s3()
    assert "активных или одновременных пользователей" in html.lower()
    assert "не пересчитываются" in html.lower()
    assert "серверный ориентир" in html.lower()
    assert "что можно передать закупке" in html.lower()


def test_tech_tab_starts_with_plain_explanation():
    combined = "\n".join(
        (draft_tech_s1(), draft_tech_s2(), draft_tech_s3(), draft_tech_s4())
    )
    assert "какие серверные ресурсы нужны" in combined.lower()
    assert "цены по зарегистрированным, активным и одновременным пользователям" in combined.lower()
    assert "граница ответственности для пилота" in combined.lower()
    assert "что проверяет иб" in combined.lower()
    assert "правила включения" in combined.lower()
    assert "ограниченного режима" in combined.lower()
    assert "capabilities/gates" not in combined.lower()
    assert "selected → installing" not in combined.lower()
    assert "dev-стенде" not in combined.lower()
    assert "рабочая болванка" not in combined.lower()
    for token in ("Base + add-ons", "tier, deploy, offerings", "Concurrent vs registered users", "RAM-бар", "prod full", "смета infra"):
        assert token.lower() not in combined.lower()


def test_executive_summary_is_nontechnical():
    html = executive_summary_cards()
    assert "Корпоративный чат в контуре компании" in html
    assert "Пилотные сценарии" in html
    for token in ("JWT", "Keycloak", "NATS", "Solr", "mesh", "BYO", "LSO"):
        assert token.lower() not in html.lower()


def test_block0_uses_external_status_language():
    html = render_block0().lower()
    assert "лабораторном стенде" in html
    assert "версия для пилота и доработок" in html
    assert "dev-стенд" not in html
    assert "рабочая болванка" not in html


def test_audience_routes_are_plain_language():
    html = audience_route_cards()
    assert "Руководителю и аналитику" in html
    assert "Продажам" in html
    assert "IT и ИБ" in html
    assert "Сотруднику и HR" in html
    assert html.count("data-tab-link=") == 4
    assert "HR и внедрению" not in html
    assert "РП" not in html


def test_role_decision_board_covers_business_specialists():
    html = role_decision_board()
    for token in ("CEO", "CFO", "CIO / ИБ", "Продажи / закупки", "Внедрение / HR"):
        assert token in html
    assert "бюджетный коридор" in html.lower()


def test_role_decision_board_avoids_orphan_card_layout():
    css = _deck_css()
    assert ".role-board-grid" in css
    assert "grid-template-columns: repeat(5, minmax(0, 1fr))" in css
    assert "role-board-grid { grid-template-columns: 1fr; }" in css
    assert "repeat(auto-fit, minmax(190px, 1fr))" not in css


def test_deck_uses_korus_consulting_brand_palette():
    css = _deck_css()
    assert "--brand: #0b2347" in css
    assert "--accent: #e31e24" in css
    assert "--korus-purple: #6d3fd1" in css
    assert "--korus-yellow: #ffd33d" in css
    assert "linear-gradient(135deg, #081a38 0%, #0b2347" in css
    assert "background: var(--accent); color: #fff" in css


def test_competitor_list_explains_public_price_rows():
    html = render_competitor_list()
    assert "Публичных тарифов в расчёте" in html
    assert "сколько публичных тарифов" in html
    assert "Публичных строк" not in html


def test_pm_short_version_has_positioning_and_pilot_outcome():
    assert "Позиционирование" in draft_pm_s1()
    assert "не “ещё один облачный чат”" in draft_pm_s1()
    assert "разрешить ли ограниченный пилот" in draft_pm_s1().lower()
    assert "реалистичный бюджет владения" in draft_pm_s1().lower()
    assert "базовый контур + подключаемые возможности" in draft_pm_s1().lower()
    assert "что проверяют ключевые роли" in draft_pm_s1().lower()
    assert "пилотная хартия" in draft_pm_s4().lower()
    assert "критерии успешного пилота" in draft_pm_s4().lower()
    assert "Что должно получиться после пилота" in draft_pm_s4()


def test_sales_copy_avoids_internal_market_jargon():
    html = "\n".join((draft_sales_s1(), draft_sales_s3())).lower()
    for token in ("pitch", "shortlist", "on-prem", "saas"):
        assert token not in html
    assert "короткого списка" in html
    assert "облачный сервис" in html


def test_pm_budget_copy_separates_budget_layers():
    html = draft_pm_s3()
    assert "финансовую модель нужно читать в три слоя" in html.lower()
    assert "не полный бюджет владения" in html.lower()
    assert "коммерческая часть" in html.lower()


def test_server_calculator_exposes_custom_quote_inputs():
    html = _calc_tech_html()
    assert "Прайс серверов" in html
    assert "Свой прайс" in html
    assert "RAM, ₽/ГБ/мес" in html
    assert "vCPU, ₽/мес" in html


def test_product_addons_catalog_uses_runtime_v2_phase5_addons():
    assert ms.PRODUCT_CATALOG_PATH.as_posix().endswith("modules/core-api/src/main/resources/product-modules.yaml")
    ids = {a["id"] for a in ms.product_addons_catalog_json()}
    for aid in ("addon-productivity", "addon-collaboration", "addon-ai", "addon-dlp", "addon-federation", "addon-migration-import"):
        assert aid in ids


def test_server_calculator_js_uses_custom_provider_quote():
    js = _deck_js()
    assert "customProviderFromForm" in js
    assert "quotesForPrefix" in js
    assert "quotesForFixedResources" in js
    assert "publicQuotes" in js
    assert "Пользовательский прайс" in js


def test_calculators_are_labeled_as_estimates():
    assert "оценочная модель" in _calc_tech_html().lower()
    assert "оценка занятости команды" in _calc_pm_html().lower()


def test_user_adoption_block_is_plain_language():
    html = draft_user_s4()
    assert "внедрения и внутренних коммуникаций" in html.lower()
    assert "обратную связь" in html.lower()
    assert "канал поддержки пилота" in html.lower()
    assert "если у сотрудника нет кнопки" in html.lower()


def test_user_feature_gaps_do_not_hide_existing_phase5_work():
    html = render_user_feature_groups_html()
    assert "Что требует оговорки" in html
    assert "Что не покрыто" not in html
    assert "Федерация — доверенные организации и каталог партнёров" in html
    assert "Федерация — чаты с сотрудниками партнёрских компаний" not in html
    assert "Федерация — переписка с контрагентами в другой организации" not in html
    assert "Встроенный каталог мини-приложений, как SmartApps" not in html
    assert "Каталог интеграций и встроенная панель" in html
    assert "Каталог мини-приложений с богатым UI, как SmartApps eXpress" not in html


def test_calls_gaps_reflect_live_recording_and_reminders_progress():
    html = render_user_feature_groups_html()
    assert "Live-сессии, гостевой вход, ожидание допуска и запись" in html
    assert "Запланированные сообщения и напоминания по сообщениям" in html
    assert "Сотни участников и студийные вебинары" not in html
    assert "Запись разговора для участника «в один клик»" not in html
    assert "Календарь встреч и напоминания внутри клиента" not in html
    assert "Подтверждённые сотни участников на контуре заказчика" in html
    assert "Самостоятельный доступ зрителя к архиву записей" in html
    assert "Запись эфира для зрителя в один клик" not in html


def test_files_gap_reflects_dlp_addon_progress():
    html = render_user_feature_groups_html()
    assert "DLP-проверки отправки как подключаемый контур ИБ" in html
    assert "Пользовательские тексты DLP-решений от реального вендора заказчика" in html
    assert "Подсказки DLP при отправке" not in html


def test_gap_registry_reflects_federation_and_integrations_progress():
    html = render_gaps_registry_html()
    assert "доверенные организации, каталог партнёров" in html
    assert "Каталог интеграций и встроенная панель" in html
    assert "Подробный реестр оговорок и проверок" in html
    assert "Оговорка / что подтвердить" in html
    assert "federation/holding — eng roadmap" not in html


def test_user_intro_reassures_about_corporate_access():
    html = draft_user_s1()
    assert "рабочие обсуждения" in html.lower()
    assert "не для повседневного чтения личных разговоров" in html.lower()
    assert "администратор может посмотреть" not in html.lower()
