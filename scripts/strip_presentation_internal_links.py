"""One-off helper: remove internal repo links from docs/PRODUCT_PRESENTATION.md."""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
p = ROOT / "docs" / "PRODUCT_PRESENTATION.md"
text = p.read_text(encoding="utf-8")

c_start = text.find("### Приложение C. Технические документы для IT")
c_end = text.find("### Приложение D.", c_start)
if c_start != -1 and c_end != -1:
    text = text[:c_start] + text[c_end:]

e_start = text.find("### Приложение E.")
e_end = text.find("### Приложение F.", e_start)
if e_start != -1 and e_end != -1:
    text = (
        text[:e_start]
        + "### Приложение E. Техническая документация для IT\n\n"
        "Руководства по развёртыванию, эксплуатации, API и комплаенсу передаются заказчику "
        "отдельным комплектом при внедрении (не входят в эту презентацию).\n\n"
        + text[e_end:]
    )


def strip_link(m: re.Match[str]) -> str:
    label, url = m.group(1), m.group(2)
    if "product_presentation.html" in url:
        return f"[{label}]({url})"
    return label


text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", strip_link, text)
text = re.sub(
    r"`[^`]*(?:docs/|specs/|deploy/|scripts/|plans/|benchmarks/|parity/)[^`]*`",
    "",
    text,
)

subs = [
    ("Техническая детализация —  (внутренний design-doc).", ""),
    (" (внутренний design-doc)", ""),
    (
        "**Engineering baseline НТ** на QEMU (2026-06-15) зафиксирован в  и competitor_comparison.html §2.1. ",
        "**Лабораторный baseline** на тестовом стенде (2026-06-15). ",
    ),
    ("Артефакт: .", ""),
    ("Полная хронология: .", ""),
    ("Генератор: `scripts/tz_product_pricing.py`, `python scripts/build-tz-product-html.py`.", ""),
    ("OpenAPI и ", ""),
    ("OpenAPI", "REST API"),
    ("; см. `docs/parity/runtime-gate-report.md`.", "."),
    ("; см. `docs/parity/runtime-gate-report.md`", ""),
    ("[`tz_revision_proposal.md`](../tz_revision_proposal.md)", "дорожная карта auth"),
    ("[`2026-06-15-infra-optimization-design.md`](plans/2026-06-15-infra-optimization-design.md)", ""),
    ("**v2.5**", "**v2.5.1**"),
    ("**Версия документа:** 1.7", "**Версия документа:** 1.8"),
]
for a, b in subs:
    text = text.replace(a, b)

text = re.sub(r"  +", " ", text)
text = re.sub(r"\n{3,}", "\n\n", text)
p.write_text(text, encoding="utf-8")
print(f"Updated {p}")
