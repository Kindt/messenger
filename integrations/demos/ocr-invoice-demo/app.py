#!/usr/bin/env python3
"""Spec 014: on-prem OCR invoice demo (mock or live OCR_HTTP_URL)."""
from __future__ import annotations

import json
import os
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "_lib"))
import integration_backend as ib  # noqa: E402

PORT = int(os.environ.get("OCR_WORKER_PORT", "8095"))


def extract_fields(file_id: str, snapshot: dict | None) -> dict:
    live_url = (os.environ.get("OCR_HTTP_URL") or "").strip()
    if ib.use_mock(bool(live_url)) or (ib.ocr_on_prem_only(snapshot) and "azure.com" in live_url.lower()):
        fid = file_id or "invoice-demo.pdf"
        data = ib.fetch_json(f"{ib.mock_base()}/ocr/v1/extract.json?file_id={fid}")
        return data.get("fields") or {}
    data = ib.fetch_json(f"{live_url.rstrip('/')}/v1/extract", method="POST", body={"file_id": file_id})
    return data.get("fields") or {}


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
        text = (event.get("text") or "").strip().lower()
        if text == "ping":
            self._json(200, {"messages": [{"text": "pong (ocr-worker)", "format": "markdown"}]})
            return
        payload = event.get("payload") or {}
        file_id = payload.get("file_id") if isinstance(payload, dict) else None
        file_id = str(file_id or "invoice-demo.pdf")
        try:
            fields = extract_fields(file_id, event.get("config_snapshot"))
        except Exception as exc:  # noqa: BLE001
            self._json(200, {"messages": [{"text": f"OCR error: {exc}", "format": "markdown"}]})
            return
        msg = (
            f"**OCR** — `{file_id}`\n"
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
