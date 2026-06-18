"""Deck section anchors — internal links only, no legacy §N.M refs."""

from __future__ import annotations

from html import escape

# Subsections (article id="{tab}-s{n}")
PM_CAPABILITIES = "pm-s1"
PM_SUPPORT = "pm-s4"
TECH_MATRIX = "tech-s3"
TECH_SIZING = "tech-s4"
SALES_TCO = "sales-s3"
SALES_CALC = "sales-s4"

# Blocks inside Tech tab
TECH_STACK = "tech-stack-nodes"
TECH_PLUGINS = "tech-plugins"
TECH_CALC_LOAD = "calc-tech-res"
PRICE_SOURCES = "price-sources"


def href(anchor: str) -> str:
    return f"#{anchor}"


def link(anchor: str, label: str) -> str:
    return f'<a href="{href(anchor)}">{escape(label)}</a>'
