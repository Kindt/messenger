#!/usr/bin/env python3
"""Export consolidated Korus gap registry (for deck / review)."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation import product_status as ps
from scripts.presentation.data_loader import load_competitors
from scripts.presentation.petal_radar import (
    LEVEL_TO_SCORE,
    TAB_PETAL_CRITERIA,
    feature_text_to_score,
)
from scripts.presentation.petal_scoring import (
    KORUS_GAP_TO_5,
    KORUS_USER_GAP,
    explain_criteria_score,
    explain_user_score,
)
from scripts.presentation.gaps_registry import (
    WORK_ENG_OPS,
    WORK_ENGINEERING,
    WORK_OPS,
    WORK_OUT,
    WORK_PRODUCT,
    _collect_rows,
)
from scripts.presentation.user_features import USER_FEATURE_GROUPS

OPS_MARKERS = (
    "ops",
    "stage",
    "prom",
    "sign-off",
    "ИБ",
    "TURN",
    "VAPID",
    "IdP",
    "LDAP",
    "live ",
    "контур",
    "заказчик",
    "ФСТЭК",
    "реестр",
    "soak",
    "LSO",
    "Sep 2026",
    "сентября",
    "КП",
    "прайс",
    "SLA",
    "legal strict",
)


def classify_work(gap: str, cid: str) -> str:
    g = gap.lower()
    if cid in ("fstec", "pricing", "mobile"):
        return "ops+product"
    if any(m.lower() in g for m in OPS_MARKERS):
        if cid in ("search", "bots", "superapp", "multitenant", "federation", "e2ee"):
            return "eng+ops"
        return "ops"
    return "engineering"


def proposal(cid: str, gap: str, work: str) -> str:
    proposals = {
        "onprem": "Stage Sep 2026+: formal load + ops sign-off (spec 007/015 LSO-001…)",
        "export": "Legal strict export checklist + ops ceremony на prom (LSO)",
        "audit": "Комплаенс-пакет: runbook аудита + экспорт журналов для ИБ",
        "fstec": "Charter → экспертиза → реестр (LSO-071, planned в deck)",
        "retention": "Sign-off dual-TTL политик с юристами заказчика",
        "multitenant": "Prod org-shard rollout ADR + soak multi-org",
        "sizing": "Единый sizing gate в deck + k6 baseline на stage (LSO)",
        "pricing": "Публичный прайс-лист или типовые КП-якоря в deck",
        "sla": "Подписанный SLA + HA ref-arch на stage (LSO)",
        "superapp": "G-SUPER-01–03: M365/Exchange bridges (014 policy); rich catalog — Phase 9",
        "federation": "Trust/directory add-on; подтвердить межорганизационный сценарий на пилоте",
        "vks": "TURN prod playbook + запись/SFU soak (LSO-060/072)",
        "ops": "k6 + ops runbooks на stage (LSO-004, Sep 2026+)",
        "e2ee": "ИБ sign-off 8/8 + prom OpenMLS binding (LSO E2EE gate)",
        "search": "Solr-only prom profile doc; SQL — dev-min only",
        "sso": "Live IdP smoke на контуре заказчика (LSO integrations)",
        "bots": "L0–L3 catalog UX + prom webhook SLA",
        "mobile": "Отдельный проект iOS/Android — out of scope deck",
        "pricing": "Коммерческая политика + публичные якоря TCO",
        "link_preview": "SSRF policy ADR + hardening preview worker",
        "smartapps_ui": "Marketplace/iframe launcher есть; развивать готовые rich mini-forms",
        "platform_modules": "Документировать add-ons в deck (done); Ansible KORUS_PRODUCT_ADDONS",
    }
    base = proposals.get(cid, "Закрыть gap из petal/user-fg; QEMU acceptance + unit/H2")
    if work == "ops":
        return f"{base} · только repo-local: smoke/H2 до Sep 2026"
    if work == "ops+product":
        return f"{base} · product charter + deferred ops"
    if work == "eng+ops":
        return f"{base} · engineering first, ops sign-off Sep 2026+"
    return base


def export_gaps_classified() -> dict[str, list[dict]]:
    """Classify deck gap rows for spec 021 design artifact."""
    buckets: dict[str, list[dict]] = {
        "now": [],
        "eng_tail": [],
        "defer_ops": [],
        "product": [],
        "out": [],
    }
    work_to_bucket = {
        WORK_ENGINEERING: "now",
        WORK_ENG_OPS: "eng_tail",
        WORK_OPS: "defer_ops",
        WORK_PRODUCT: "product",
        WORK_OUT: "out",
    }
    for r in _collect_rows():
        bucket = work_to_bucket.get(r["work"], "now")
        buckets[bucket].append(
            {
                "id": r["id"],
                "axis": r["axis"],
                "score": r["score"],
                "gap": r["gap"],
                "proposal": r["proposal"],
            }
        )
    for key in buckets:
        buckets[key].sort(key=lambda x: (float(x["score"]), x["id"]))
    return buckets


def main() -> int:
    data = load_competitors()
    products = {p["id"]: p for p in data["products"]}
    korus = products["korus"]
    criteria_meta = {c["id"]: c for c in data.get("criteria", [])}

    def crit_title(cid: str) -> str:
        c = criteria_meta.get(cid, {})
        return str(c.get("short") or c.get("title") or cid)

    by_cid: dict[str, dict] = {}

    for tab, cids in TAB_PETAL_CRITERIA.items():
        for cid in cids:
            cell = str(korus["features"].get(cid, "—"))
            score = feature_text_to_score(cell)
            _, gap = explain_criteria_score(cid, crit_title(cid), cell, score, "korus")
            gap_text = KORUS_GAP_TO_5.get(cid, gap)
            if cid not in by_cid:
                by_cid[cid] = {
                    "id": cid,
                    "axis": crit_title(cid),
                    "score": score,
                    "gap": gap_text,
                    "radar_tabs": [],
                    "source": "petal",
                }
            by_cid[cid]["radar_tabs"].append(tab)

    user_rows = []
    for g in USER_FEATURE_GROUPS:
        cell = g.comparisons["korus"]
        score = LEVEL_TO_SCORE.get(cell.level, 3.0)
        _, gap = explain_user_score(g.id, "korus", score)
        gaps_text = "; ".join(cell.gaps)
        extra = KORUS_USER_GAP.get(g.id, "")
        user_rows.append(
            {
                "id": g.id,
                "axis": g.title,
                "score": score,
                "level": cell.level,
                "gap": gaps_text,
                "gap_prom": gap,
                "extra": extra,
            }
        )

    module_rows = []
    for fid, name, status, note in ps.FEATURES:
        if status != "partial":
            continue
        module_rows.append({"id": fid, "axis": name, "gap": note})

    out = {
        "criteria": sorted(by_cid.values(), key=lambda r: (r["score"], r["id"])),
        "user": user_rows,
        "modules_partial": module_rows,
    }
    out_path = Path(__file__).resolve().parent / "gaps_export.json"
    out_path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {out_path}")

    classified_path = Path(__file__).resolve().parent / "gaps_classified.json"
    classified_path.write_text(
        json.dumps(export_gaps_classified(), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"Wrote {classified_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
