#!/usr/bin/env python3
"""Spec 014 P3: L3 AI triage demo (on-prem LLM mock)."""
from __future__ import annotations

import json
import os
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer


MOCK_BASE = os.environ.get("MOCK_API_BASE", "http://mock-apis:8080").rstrip("/")
PORT = int(os.environ.get("AI_BRIDGE_PORT", "8096"))


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
        try:
            event = json.loads(raw)
        except json.JSONDecodeError:
            event = {}
        text = (event.get("text") or "").strip()
        if text.lower() == "ping":
            self._json(200, {"messages": [{"text": "pong (ai-bridge)", "format": "markdown"}]})
            return
        if not text:
            self._json(
                200,
                {"messages": [{"text": "AI triage (L3). Отправьте текст треда или `/triage <text>`", "format": "markdown"}]},
            )
            return
        if text.lower().startswith("/triage "):
            text = text[8:].strip()
        try:
            body = json.dumps({"text": text}).encode("utf-8")
            req = urllib.request.Request(
                f"{MOCK_BASE}/ai/v1/triage.json",
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=8) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except Exception as exc:  # noqa: BLE001
            self._json(200, {"messages": [{"text": f"AI mock offline: {exc}", "format": "markdown"}]})
            return
        category = data.get("category", "general")
        priority = data.get("priority", "normal")
        draft = data.get("draft_ticket", {})
        msg = (
            f"**L3 triage (mock LLM)**\n"
            f"- Категория: **{category}**\n"
            f"- Приоритет: **{priority}**\n"
            f"- Черновик: {draft.get('title', '?')}"
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
