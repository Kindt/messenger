"""Shared mock/live backend resolution for Python integration demos."""
from __future__ import annotations

import json
import os
import urllib.request


def backend_mode() -> str:
    return (os.environ.get("INTEGRATIONS_BACKEND_MODE") or "auto").strip().lower()


def mock_base() -> str:
    return (os.environ.get("MOCK_API_BASE") or "http://mock-apis:8080").rstrip("/")


def use_mock(live_configured: bool) -> bool:
    mode = backend_mode()
    if mode == "mock":
        return True
    if mode == "live":
        return False
    return not live_configured


def llm_mode_from_snapshot(snapshot: dict | None) -> str:
    if not snapshot:
        return "on_prem_only"
    return str(snapshot.get("org_llm_mode") or "on_prem_only")


def ocr_on_prem_only(snapshot: dict | None) -> bool:
    if not snapshot:
        return True
    value = snapshot.get("ocr_on_prem_only")
    if isinstance(value, bool):
        return value
    return str(value).lower() != "false"


def fetch_json(url: str, method: str = "GET", body: dict | None = None, headers: dict | None = None) -> dict:
    data = None
    req_headers = {"Accept": "application/json"}
    if headers:
        req_headers.update(headers)
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        req_headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=req_headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def is_cloud_host(url: str) -> bool:
    lower = url.lower()
    return any(
        token in lower
        for token in ("api.openai.com", "openai.azure.com", "anthropic.com", "googleapis.com")
    )
