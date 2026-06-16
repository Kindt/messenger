#!/usr/bin/env python3
"""Spec 014 P2: on-prem OCR invoice demo (mock extraction)."""
from __future__ import annotations

import json
import os
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer


MOCK_BASE = os.environ.get("MOCK_API_BASE", "http://mock-apis:8080").rstrip("/")
PORT = int(os.environ.get("OCR_WORKER_PORT", "8095"))


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
        text = (event.get("text") or "").strip().lower()
        if text == "ping":
            self._json(200, {"messages": [{"text": "pong (ocr-worker)", "format": "markdown"}]})
            return
        file_id = "invoice-demo.pdf"
        payload = event.get("payload") or {}
        if isinstance(payload, dict) and payload.get("file_id"):
            file_id = str(payload["file_id"])
        try:
            with urllib.request.urlopen(f"{MOCK_BASE}/ocr/v1/extract.json?file_id={file_id}", timeout=8) as resp:
                data = json.loads(resp.read().decode("utf-8"))
        except Exception as exc:  # noqa: BLE001
            self._json(200, {"messages": [{"text": f"OCR mock offline: {exc}", "format": "markdown"}]})
            return
        fields = data.get("fields") or {}
        msg = (
            f"**OCR (on-prem mock)** — `{file_id}`\n"
            f"- Поставщик: **{fields.get('vendor', '?')}**\n"
            f"- Сумма: **{fields.get('amount', '?')}** {fields.get('currency', 'RUB')}\n"
            f"- Дата: {fields.get('date', '?')}"
        )
        self._json(
            200,
            {
                "messages": [{"text": msg, "format": "markdown"}],
                "cards": [
                    {
                        "title": "Действия",
                        "buttons": [
                            {"id": "send_1c", "label": "Отправить в 1С (mock)"},
                            {"id": "reject", "label": "Отклонить"},
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
