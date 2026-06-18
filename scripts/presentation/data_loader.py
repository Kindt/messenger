"""Load static JSON data for the presentation deck."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

_DATA_DIR = Path(__file__).resolve().parent / "data"


def _load_json(name: str) -> Any:
    path = _DATA_DIR / name
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def load_competitors() -> dict[str, Any]:
    return _load_json("competitors.json")


def load_offerings() -> list[dict[str, Any]]:
    data = _load_json("competitor_offerings.json")
    return data["competitor_offerings"]


def load_bibliography() -> list[dict[str, Any]]:
    data = _load_json("open_sources_bibliography.json")
    return data["products"]


def offering_by_id(offering_id: str) -> dict[str, Any]:
    for o in load_offerings():
        if o["id"] == offering_id:
            return o
    raise KeyError(offering_id)


def data_dir() -> Path:
    return _DATA_DIR
