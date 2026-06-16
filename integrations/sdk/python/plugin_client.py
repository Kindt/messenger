"""Thin Plugin Runtime API client (spec 014)."""

from __future__ import annotations

import json
import urllib.request
from typing import Any


class PluginClient:
    def __init__(self, base_url: str) -> None:
        self._base_url = base_url.rstrip("/")

    def handle(self, event: dict[str, Any]) -> dict[str, Any]:
        body = json.dumps(event).encode("utf-8")
        req = urllib.request.Request(
            f"{self._base_url}/v1/plugin/handle",
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
