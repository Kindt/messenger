"""Honesty gate — denylist patterns with Block 0 negation allowlist."""

from __future__ import annotations

import re
from dataclasses import dataclass

BANNED_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("production-ready", re.compile(r"production[\s-]?ready", re.I)),
    ("enterprise-grade", re.compile(r"enterprise[\s-]?grade", re.I)),
    ("promyshlennaya", re.compile(r"готов\s+к\s+промышленной\s+эксплуатации", re.I)),
    ("best", re.compile(r"\b(лучший|единственный|№\s*1)\b", re.I)),
    ("sla_guarantee", re.compile(r"гарантируем\s+SLA", re.I)),
    ("fstec_cert", re.compile(r"ФСТЭК\s+сертифицирован", re.I)),
    ("registry_claim", re.compile(r"в\s+реестре\s+отечественного\s+ПО(?!\s*—|\s*в\s+процессе)", re.I)),
]

USER_JARGON = ("JWT", "Keycloak", "NATS", "Solr", "mesh")

BLOCK0_ID = "block-0"

ALLOWLIST_NEGATIONS = (
    re.compile(r"не\s+готов", re.I),
    re.compile(r"не\s+production", re.I),
    re.compile(r"не\s+готов\s+к\s+промышленной", re.I),
    re.compile(r"рабочий\s+прототип", re.I),
)


@dataclass(frozen=True)
class HonestyViolation:
    pattern: str
    snippet: str
    context: str


def _extract_block0(html: str) -> str:
    m = re.search(rf'<section[^>]*id="{BLOCK0_ID}"[^>]*>(.*?)</section>', html, re.S | re.I)
    return m.group(1) if m else ""


def _extract_user_tab(html: str) -> str:
    m = re.search(r'id="tab-user"[^>]*>(.*?)</main>', html, re.S | re.I)
    return m.group(1) if m else ""


def _allowed_in_block0(text: str, match: re.Match[str]) -> bool:
    start = max(0, match.start() - 40)
    window = text[start : match.end() + 40]
    return any(p.search(window) for p in ALLOWLIST_NEGATIONS)


def check_html(html: str) -> list[HonestyViolation]:
    violations: list[HonestyViolation] = []
    block0 = _extract_block0(html)
    outside = html.replace(block0, "")

    for name, pat in BANNED_PATTERNS:
        for m in pat.finditer(outside):
            violations.append(
                HonestyViolation(name, m.group(0), "outside block-0")
            )

    for name, pat in BANNED_PATTERNS:
        for m in pat.finditer(block0):
            if not _allowed_in_block0(block0, m):
                violations.append(
                    HonestyViolation(name, m.group(0), "block-0 without negation")
                )

    user = _extract_user_tab(html).lower()
    for token in USER_JARGON:
        if token.lower() in user:
            violations.append(HonestyViolation("user_jargon", token, "user tab"))

    if "PRODUCTION_READY = false" not in html and "production_ready" in html.lower():
        if not re.search(r"PRODUCTION_READY\s*=\s*False", html, re.I):
            pass  # checked via visible text

    if "рабочий прототип" not in html.lower():
        violations.append(
            HonestyViolation("missing_prototype", "рабочий прототип", "document")
        )

    return violations


def assert_honest(html: str) -> None:
    violations = check_html(html)
    if violations:
        msgs = "; ".join(f"{v.pattern} ({v.context}): {v.snippet!r}" for v in violations)
        raise SystemExit(f"Honesty gate failed: {msgs}")
