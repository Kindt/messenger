#!/usr/bin/env python3
"""Spec 022 T02201: mock DLP L2 bridge — allow|quarantine|block on message.send."""
from __future__ import annotations

import json
import os
import re
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(os.environ.get("DLP_MOCK_PORT", "8098"))
BLOCK_PATTERNS = [
    re.compile(p, re.I)
    for p in (os.environ.get("DLP_BLOCK_REGEX") or r"password|secret|confidential|утечка").split("|")
    if p
]


def verdict_for_text(text: str) -> str:
    t = (text or "").strip()
    if not t:
        return "allow"
    for pat in BLOCK_PATTERNS:
        if pat.search(t):
            return "block"
    if len(t) > 4000:
        return "quarantine"
    return "allow"


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
        if event.get("type") == "message.send" or text:
            v = verdict_for_text(text)
        else:
            v = "allow"
        self._json(200, {"messages": [], "dlp_verdict": v})

    def _text(self, code: int, body: str) -> None:
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
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
