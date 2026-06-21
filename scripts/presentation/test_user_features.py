"""Tests for user-facing feature groups."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation import content as cnt
from scripts.presentation.user_features import USER_FEATURE_GROUPS, render_user_feature_groups_html


def test_eight_user_feature_groups():
    assert len(USER_FEATURE_GROUPS) == 8
    ids = {g.id for g in USER_FEATURE_GROUPS}
    assert ids == {
        "chat",
        "profile",
        "calls",
        "files_search",
        "organize",
        "integrations",
        "notify",
        "live",
    }


def test_user_feature_html_has_compare_table():
    html = render_user_feature_groups_html()
    assert "user-fg-chat" in html
    assert "user-fg-integrations" in html
    assert "user-miniapps-vs-bots" in html
    assert "eXpress" in html or "express" in html.lower()
    assert "Korus Messenger" in html
    assert html.count("user-fg-compare") == 8
    assert "Что уже есть" in html
    assert "Что требует оговорки" in html
    assert "Что не покрыто" not in html
    assert "Выгода:" in html
    assert "Отличие:" in html
    assert "Мини-приложения" in html


def test_each_group_has_full_comparisons():
    for group in USER_FEATURE_GROUPS:
        for pid in ("korus", "express", "pachka", "vk_saas"):
            cell = group.comparisons[pid]
            assert cell.covered
            if not (pid == "korus" and group.id == "chat"):
                assert cell.gaps
            assert cell.distinction.strip()


def test_user_drafts_no_jargon():
    for fn in (cnt.draft_user_s1, cnt.draft_user_s2, cnt.draft_user_s3, cnt.draft_user_s4):
        text = fn().lower()
        for token in cnt.USER_JARGON_DENY:
            assert token.lower() not in text


def test_user_feature_groups_avoid_internal_integration_jargon():
    html = render_user_feature_groups_html().lower()
    for token in ("policy 014", "014 policy", "iframe", "sandbox", "webhook", "on-prem", "self-service", "ux"):
        assert token not in html
    assert "всё сразу в saas" not in html
    assert "как в saas" not in html
    assert "встроенная защищённая панель" in html
    assert "уведомления от внешних систем" in html
    assert "облачном сервисе" in html
