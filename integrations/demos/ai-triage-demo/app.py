#!/usr/bin/env python3
"""Spec 014: L3 AI triage demo (mock or OpenAI-compatible live LLM)."""
from __future__ import annotations

import json
import os
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "_lib"))
import integration_backend as ib  # noqa: E402

PORT = int(os.environ.get("AI_BRIDGE_PORT", "8096"))


def triage(text: str, snapshot: dict | None) -> dict:
    on_prem = (os.environ.get("LLM_ON_PREM_URL") or "").strip()
    cloud = (os.environ.get("LLM_BASE_URL") or "").strip()
    live = bool(on_prem or cloud)
    if ib.use_mock(live):
        return ib.fetch_json(f"{ib.mock_base()}/ai/v1/triage.json", method="POST", body={"text": text})
    mode = ib.llm_mode_from_snapshot(snapshot)
    base = on_prem or cloud
    if mode == "on_prem_only" and ib.is_cloud_host(base):
        return ib.fetch_json(f"{ib.mock_base()}/ai/v1/triage.json", method="POST", body={"text": text})
    if not base:
        return ib.fetch_json(f"{ib.mock_base()}/ai/v1/triage.json", method="POST", body={"text": text})
    headers = {}
    api_key = (os.environ.get("LLM_API_KEY") or "").strip()
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    model = (os.environ.get("LLM_MODEL") or "gpt-4o-mini").strip()
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": "Classify support thread. Reply JSON: category,priority,draft_title"},
            {"role": "user", "content": text},
        ],
    }
    resp = ib.fetch_json(f"{base.rstrip('/')}/v1/chat/completions", method="POST", body=payload, headers=headers)
    content = resp.get("choices", [{}])[0].get("message", {}).get("content", "")
    try:
        return json.loads(content)
    except json.JSONDecodeError:
        return {"category": "general", "priority": "normal", "draft_title": content[:120]}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):  # noqa: D401
        return

    def do_GET(self):  # noqa: N802
        if self.path == "/health":
            self._text(200, "ok")
            return
        self._json(404, {"error": "not_found"})

    def do_POST(self):  # noqa: N802
        if self.path != "/v1/plugin/handle":
            self._json(404, {"error": "not_found"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length).decode("utf-8") if length else "{}"
        event = json.loads(raw) if raw else {}
        text = (event.get("text") or "").strip()
        if text.lower() == "ping":
            self._json(200, {"messages": [{"text": "pong (ai-bridge)", "format": "markdown"}]})
            return
        if not text:
            self._json(200, {"messages": [{"text": "AI triage (L3). `/triage <text>`", "format": "markdown"}]})
            return
        if text.lower().startswith("/triage "):
            text = text[8:].strip()
        try:
            data = triage(text, event.get("config_snapshot"))
        except Exception as exc:  # noqa: BLE001
            self._json(200, {"messages": [{"text": f"AI error: {exc}", "format": "markdown"}]})
            return
        draft = data.get("draft_ticket") or {}
        title = draft.get("title") if isinstance(draft, dict) else data.get("draft_title", "?")
        msg = (
            f"**L3 triage**\n"
            f"- Категория: **{data.get('category', 'general')}**\n"
            f"- Приоритет: **{data.get('priority', 'normal')}**\n"
            f"- Черновик: {title}"
        )
        self._json(
            200,
            {
                "messages": [{"text": msg, "format": "markdown"}],
                "cards": [
                    {
                        "title": "Следующий шаг",
                        "buttons": [
                            {"id": "create_ticket", "label": "Создать заявку Naumen"},
                            {"id": "assign_hr", "label": "Эскалация HR"},
                        ],
                    }
                ],
            },
        )

    def _text(self, code: int, body: str) -> None:
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _json(self, code: int, obj: dict) -> None:
        data = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
