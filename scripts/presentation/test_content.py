"""Tests for persona content drafts."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.content import (
    draft_user_s1,
    draft_user_s2,
    draft_user_s3,
    draft_user_s4,
)


def test_user_tab_no_technical_jargon():
    for fn in (draft_user_s1, draft_user_s2, draft_user_s3, draft_user_s4):
        html = fn()
        for token in ("JWT", "Keycloak", "NATS", "Solr", "mesh"):
            assert token.lower() not in html.lower()


def test_user_tab_reading_level_short_sentences():
    html = draft_user_s1()
    assert html.count(".") >= 3
