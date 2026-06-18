"""Marketing layout wrappers — templates only, no new facts."""

from __future__ import annotations

from html import escape


def wrap_section(draft_html: str, template_id: str, headline: str = "") -> str:
    cls = f"section-wrap section-{escape(template_id)}"
    head = f'<h3 class="section-headline">{escape(headline)}</h3>' if headline else ""
    return f'<section class="{cls}">{head}<div class="section-body">{draft_html}</div></section>'


def wrap_callout(draft_html: str, variant: str = "info") -> str:
    return f'<div class="callout callout-{escape(variant)}">{draft_html}</div>'


def wrap_grid(items: list[str], columns: int = 2) -> str:
    cells = "".join(f'<div class="grid-cell">{item}</div>' for item in items)
    return f'<div class="grid grid-cols-{columns}">{cells}</div>'
